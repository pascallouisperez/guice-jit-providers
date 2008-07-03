/**
 * Copyright (C) 2008 Google Inc.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.google.inject.internal;

import static com.google.inject.internal.ReferenceType.WEAK;
import java.lang.reflect.Member;
import java.lang.reflect.Modifier;
import java.security.AccessController;
import java.security.PrivilegedAction;
import java.util.logging.Logger;
import net.sf.cglib.core.DefaultNamingPolicy;
import net.sf.cglib.core.NamingPolicy;
import net.sf.cglib.proxy.Enhancer;
import net.sf.cglib.reflect.FastClass;

/**
 * Utility methods for runtime code generation and class loading. We use this stuff for faster
 * reflection ({@link FastClass}), method interceptors ({@link Enhancer}) and to proxy circular
 * dependencies.
 *
 * <p>When loading classes, we need to be careful of:
 * <ul>
 *   <li><strong>Memory leaks.</strong> Generated classes need to be garbage collected in long-lived
 *       applications. Once an injector and any instances it created can be garbage collected, the
 *       corresponding generated classes should be collectable.
 *   <li><strong>Visibility.</strong> Containers like <code>OSGi</code> use class loader boundaries
 *       to enforce modularity at runtime.
 * </ul>
 *
 * <p>For each generated class, there's multiple class loaders involved:
 * <ul>
 *    <li><strong>The related class's class loader.</strong> Every generated class services exactly
 *        one user-supplied class. This class loader must be used to access members with private and
 *        package visibility.
 *    <li><strong>Guice's class loader.</strong>
 *    <li><strong>Our bridge class loader.</strong> This is a child of the user's class loader. It
 *        selectively delegates to either the user's class loader (for user classes) or the Guice
 *        class loader (for internal classes that are used by the generated classes). This class
 *        loader that owns the classes generated by Guice.
 * </ul>
 *
 * @author mcculls@gmail.com (Stuart McCulloch)
 * @author jessewilson@google.com (Jesse Wilson)
 */
public final class BytecodeGen {

  private static final Logger logger = Logger.getLogger(BytecodeGen.class.getName());

  static final ClassLoader GUICE_CLASS_LOADER = BytecodeGen.class.getClassLoader();

  /** ie. "com.google.inject.internal" */
  private static final String GUICE_INTERNAL_PACKAGE
      = BytecodeGen.class.getName().replaceFirst("\\.internal\\..*$", ".internal");

  /** either "net.sf.cglib", or "com.google.inject.internal.cglib" */
  private static final String CGLIB_PACKAGE
      = Enhancer.class.getName().replaceFirst("\\.cglib\\..*$", ".cglib");

  static final NamingPolicy NAMING_POLICY = new DefaultNamingPolicy() {
    @Override protected String getTag() {
      return "ByGuice";
    }
  };

  /** Use "-Dguice.custom.loader=false" to disable custom classloading. */
  static final boolean HOOK_ENABLED
      = "true".equals(System.getProperty("guice.custom.loader", "true"));

  /**
   * Weak cache of bridge class loaders that make the Guice implementation
   * classes visible to various code-generated proxies of client classes.
   */
  private static final ReferenceCache<ClassLoader, ClassLoader> CLASS_LOADER_CACHE
      = new ReferenceCache<ClassLoader, ClassLoader>(WEAK, WEAK) {
        @Override protected ClassLoader create(final ClassLoader typeClassLoader) {
          // Don't bother bridging existing bridge classloaders
          if (typeClassLoader instanceof BridgeClassLoader) {
            return typeClassLoader;
          }

          logger.fine("Creating a bridge ClassLoader for " + typeClassLoader);
          return AccessController.doPrivileged(new PrivilegedAction<ClassLoader>() {
            public ClassLoader run() {
              return new BridgeClassLoader(typeClassLoader);
            }
          });
        }
      };

  /**
   * For class loaders, {@code null}, is always an alias to the
   * {@link ClassLoader#getSystemClassLoader() system class loader}.
   */
  private static ClassLoader canonicalize(ClassLoader classLoader) {
    return classLoader != null
        ? classLoader
        : ClassLoader.getSystemClassLoader();
  }

  /**
   * Returns the class loader to host generated classes for {@code type}.
   */
  public static ClassLoader getClassLoader(Class<?> type) {
    return getClassLoader(type, type.getClassLoader());
  }

  private static ClassLoader getClassLoader(Class<?> type, ClassLoader delegate) {
    delegate = canonicalize(delegate);

    if (HOOK_ENABLED && Visibility.forType(type) == Visibility.PUBLIC) {
      return CLASS_LOADER_CACHE.get(delegate);
    }

    return delegate;
  }

  public static FastClass newFastClass(Class<?> type, Visibility visibility) {
    FastClass.Generator generator = new FastClass.Generator();
    generator.setType(type);
    if (visibility == Visibility.PUBLIC) {
      generator.setClassLoader(getClassLoader(type));
    }
    generator.setNamingPolicy(NAMING_POLICY);
    logger.fine("Loading " + type + " FastClass with " + generator.getClassLoader());
    return generator.create();
  }

  public static Enhancer newEnhancer(Class<?> type, Visibility visibility) {
    Enhancer enhancer = new Enhancer();
    enhancer.setSuperclass(type);
    enhancer.setUseCache(false); // We do enough caching.
    enhancer.setUseFactory(false);
    if (visibility == Visibility.PUBLIC) {
      enhancer.setClassLoader(getClassLoader(type));
    }
    enhancer.setNamingPolicy(NAMING_POLICY);
    logger.fine("Loading " + type + " Enhancer with " + enhancer.getClassLoader());
    return enhancer;
  }

  /**
   * The required visibility of a user's class from a Guice-generated class. Visibility of
   * package-private members depends on the loading classloader: only if two classes were loaded by
   * the same classloader can they see each other's package-private members. We need to be careful
   * when choosing which classloader to use for generated classes. We prefer our bridge classloader,
   * since it's OSGi-safe and doesn't leak permgen space. But often we cannot due to visibility.
   */
  public enum Visibility {

    /**
     * Indicates that Guice-generated classes only need to call and override public members of the
     * target class. These generated classes may be loaded by our bridge classloader.
     */
    PUBLIC {
      public Visibility and(Visibility that) {
        return that;
      }
    },

    /**
     * Indicates that Guice-generated classes need to call or override package-private members.
     * These generated classes must be loaded in the same classloader as the target class. They
     * won't work with OSGi, and won't get garbage collected until the target class' classloader is
     * garbage collected.
     */
    SAME_PACKAGE {
      public Visibility and(Visibility that) {
        return this;
      }
    };

    public static Visibility forMember(Member member) {
      return (member.getModifiers() & (Modifier.PROTECTED | Modifier.PUBLIC)) != 0
          ? PUBLIC
          : SAME_PACKAGE;
    }

    public static Visibility forType(Class<?> type) {
      return (type.getModifiers() & (Modifier.PROTECTED | Modifier.PUBLIC)) != 0
          ? PUBLIC
          : SAME_PACKAGE;
    }

    public abstract Visibility and(Visibility that);
  }

  /**
   * Loader for Guice-generated classes. For referenced classes, this delegates to either either the
   * user's classloader (which is the parent of this classloader) or Guice's class loader.
   */
  private static class BridgeClassLoader extends ClassLoader {

    public BridgeClassLoader(ClassLoader usersClassLoader) {
      super(usersClassLoader);
    }

    @Override protected Class<?> loadClass(String name, boolean resolve)
        throws ClassNotFoundException {

      // delegate internal requests to Guice class space
      if (name.startsWith(GUICE_INTERNAL_PACKAGE) || name.startsWith(CGLIB_PACKAGE)) {
        try {
          Class<?> clazz = GUICE_CLASS_LOADER.loadClass(name);
          if (resolve) {
            resolveClass(clazz);
          }
          return clazz;
        } catch (Exception e) {
          // fall back to classic delegation
        }
      }

      return super.loadClass(name, resolve);
    }
  }
}
