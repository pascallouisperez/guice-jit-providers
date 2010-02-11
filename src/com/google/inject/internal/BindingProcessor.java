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

import com.google.inject.AbstractModule;
import com.google.inject.Binder;
import com.google.inject.Binding;
import com.google.inject.Injector;
import com.google.inject.Key;
import com.google.inject.MembersInjector;
import com.google.inject.Module;
import com.google.inject.Provider;
import com.google.inject.Scope;
import com.google.inject.TypeLiteral;
import com.google.inject.spi.BindingTargetVisitor;
import com.google.inject.spi.ConstructorBinding;
import com.google.inject.spi.ConvertedConstantBinding;
import com.google.inject.spi.ExposedBinding;
import com.google.inject.spi.InjectionPoint;
import com.google.inject.spi.InstanceBinding;
import com.google.inject.spi.LinkedKeyBinding;
import com.google.inject.spi.PrivateElements;
import com.google.inject.spi.ProviderBinding;
import com.google.inject.spi.ProviderInstanceBinding;
import com.google.inject.spi.ProviderKeyBinding;
import com.google.inject.spi.UntargettedBinding;
import java.util.List;
import java.util.Set;

/**
 * Handles {@link Binder#bind} and {@link Binder#bindConstant} elements.
 *
 * @author crazybob@google.com (Bob Lee)
 * @author jessewilson@google.com (Jesse Wilson)
 */
final class BindingProcessor extends AbstractProcessor {

  private final List<CreationListener> creationListeners = Lists.newArrayList();
  private final Initializer initializer;
  private final List<Runnable> uninitializedBindings = Lists.newArrayList();

  BindingProcessor(Errors errors, Initializer initializer) {
    super(errors);
    this.initializer = initializer;
  }

  @Override public <T> Boolean visit(Binding<T> command) {
    final Object source = command.getSource();

    if (Void.class.equals(command.getKey().getTypeLiteral().getRawType())) {
      if (command instanceof ProviderInstanceBinding
          && ((ProviderInstanceBinding) command).getProviderInstance() instanceof ProviderMethod) {
        errors.voidProviderMethod();
      } else {
        errors.missingConstantValues();
      }
      return true;
    }

    final Key<T> key = command.getKey();
    Class<? super T> rawType = key.getTypeLiteral().getRawType();

    if (rawType == Provider.class) {
      errors.bindingToProvider();
      return true;
    }

    validateKey(command.getSource(), command.getKey());

    final Scoping scoping = Scoping.makeInjectable(
        ((BindingImpl<?>) command).getScoping(), injector, errors);

    command.acceptTargetVisitor(new BindingTargetVisitor<T, Void>() {
      public Void visit(ConstructorBinding<? extends T> binding) {
        try {
          ConstructorBindingImpl<T> onInjector = ConstructorBindingImpl.create(injector, key, 
              binding.getConstructor(), source, scoping, errors, false);
          scheduleInitialization(onInjector);
          putBinding(onInjector);
        } catch (ErrorsException e) {
          errors.merge(e.getErrors());
          putBinding(invalidBinding(injector, key, source));
        }
        return null;
      }

      public Void visit(InstanceBinding<? extends T> binding) {
        Set<InjectionPoint> injectionPoints = binding.getInjectionPoints();
        T instance = binding.getInstance();
        Initializable<T> ref = initializer.requestInjection(
            injector, instance, source, injectionPoints);
        ConstantFactory<? extends T> factory = new ConstantFactory<T>(ref);
        InternalFactory<? extends T> scopedFactory
            = Scoping.scope(key, injector, factory, source, scoping);
        putBinding(new InstanceBindingImpl<T>(injector, key, source, scopedFactory, injectionPoints,
            instance));
        return null;
      }

      public Void visit(ProviderInstanceBinding<? extends T> binding) {
        Provider<? extends T> provider = binding.getProviderInstance();
        Set<InjectionPoint> injectionPoints = binding.getInjectionPoints();
        Initializable<Provider<? extends T>> initializable = initializer
            .<Provider<? extends T>>requestInjection(injector, provider, source, injectionPoints);
        InternalFactory<T> factory = new InternalFactoryToProviderAdapter<T>(initializable, source);
        InternalFactory<? extends T> scopedFactory
            = Scoping.scope(key, injector, factory, source, scoping);
        putBinding(new ProviderInstanceBindingImpl<T>(injector, key, source, scopedFactory, scoping,
            provider, injectionPoints));
        return null;
      }

      public Void visit(ProviderKeyBinding<? extends T> binding) {
        Key<? extends javax.inject.Provider<? extends T>> providerKey = binding.getProviderKey();
        BoundProviderFactory<T> boundProviderFactory
            = new BoundProviderFactory<T>(injector, providerKey, source);
        creationListeners.add(boundProviderFactory);
        InternalFactory<? extends T> scopedFactory = Scoping.scope(
            key, injector, (InternalFactory<? extends T>) boundProviderFactory, source, scoping);
        putBinding(new LinkedProviderBindingImpl<T>(
            injector, key, source, scopedFactory, scoping, providerKey));
        return null;
      }

      public Void visit(LinkedKeyBinding<? extends T> binding) {
        Key<? extends T> linkedKey = binding.getLinkedKey();
        if (key.equals(linkedKey)) {
          errors.recursiveBinding();
        }

        FactoryProxy<T> factory = new FactoryProxy<T>(injector, key, linkedKey, source);
        creationListeners.add(factory);
        InternalFactory<? extends T> scopedFactory
            = Scoping.scope(key, injector, factory, source, scoping);
        putBinding(
            new LinkedBindingImpl<T>(injector, key, source, scopedFactory, scoping, linkedKey));
        return null;
      }

      public Void visit(UntargettedBinding<? extends T> untargetted) {
        // Error: Missing implementation.
        // Example: bind(Date.class).annotatedWith(Red.class);
        // We can't assume abstract types aren't injectable. They may have an
        // @ImplementedBy annotation or something.
        if (key.getAnnotationType() != null) {
          errors.missingImplementation(key);
          putBinding(invalidBinding(injector, key, source));
          return null;
        }

        // This cast is safe after the preceeding check.
        try {
          BindingImpl<T> binding = injector.createUninitializedBinding(
              key, scoping, source, errors, false);
          scheduleInitialization(binding);
          putBinding(binding);
        } catch (ErrorsException e) {
          errors.merge(e.getErrors());
          putBinding(invalidBinding(injector, key, source));
        }

        return null;
      }

      public Void visit(ExposedBinding<? extends T> binding) {
        throw new IllegalArgumentException("Cannot apply a non-module element");
      }

      public Void visit(ConvertedConstantBinding<? extends T> binding) {
        throw new IllegalArgumentException("Cannot apply a non-module element");
      }

      public Void visit(ProviderBinding<? extends T> binding) {
        throw new IllegalArgumentException("Cannot apply a non-module element");
      }

      private void scheduleInitialization(final BindingImpl<?> binding) {
        uninitializedBindings.add(new Runnable() {
          public void run() {
            try {
              binding.getInjector().initializeBinding(binding, errors.withSource(source));
            } catch (ErrorsException e) {
              errors.merge(e.getErrors());
            }
          }
        });
      }
    });

    return true;
  }

  @Override public Boolean visit(PrivateElements privateElements) {
    for (Key<?> key : privateElements.getExposedKeys()) {
      bindExposed(privateElements, key);
    }
    return false; // leave the private elements for the PrivateElementsProcessor to handle
  }

  private <T> void bindExposed(PrivateElements privateElements, Key<T> key) {
    ExposedKeyFactory<T> exposedKeyFactory = new ExposedKeyFactory<T>(key, privateElements);
    creationListeners.add(exposedKeyFactory);
    putBinding(new ExposedBindingImpl<T>(
        injector, privateElements.getExposedSource(key), key, exposedKeyFactory, privateElements));
  }

  private <T> void validateKey(Object source, Key<T> key) {
    Annotations.checkForMisplacedScopeAnnotations(
        key.getTypeLiteral().getRawType(), source, errors);
  }

  <T> UntargettedBindingImpl<T> invalidBinding(InjectorImpl injector, Key<T> key, Object source) {
    return new UntargettedBindingImpl<T>(injector, key, source);
  }

  public void initializeBindings() {
    for (Runnable initializer : uninitializedBindings) {
      initializer.run();
    }
  }

  public void runCreationListeners() {
    for (CreationListener creationListener : creationListeners) {
      creationListener.notify(errors);
    }
  }

  private void putBinding(BindingImpl<?> binding) {
    Key<?> key = binding.getKey();

    Class<?> rawType = key.getTypeLiteral().getRawType();
    if (FORBIDDEN_TYPES.contains(rawType)) {
      errors.cannotBindToGuiceType(rawType.getSimpleName());
      return;
    }

    Binding<?> original = injector.state.getExplicitBinding(key);
    if (original != null && !isOkayDuplicate(original, binding)) {
      errors.bindingAlreadySet(key, original.getSource());
      return;
    }

    // prevent the parent from creating a JIT binding for this key
    injector.state.parent().blacklist(key);
    injector.state.putBinding(key, binding);
  }

  /**
   * We tolerate duplicate bindings only if one exposes the other.
   *
   * @param original the binding in the parent injector (candidate for an exposing binding)
   * @param binding the binding to check (candidate for the exposed binding)
   */
  private boolean isOkayDuplicate(Binding<?> original, BindingImpl<?> binding) {
    if (original instanceof ExposedBindingImpl) {
      ExposedBindingImpl exposed = (ExposedBindingImpl) original;
      InjectorImpl exposedFrom = (InjectorImpl) exposed.getPrivateElements().getInjector();
      return (exposedFrom == binding.getInjector());
    }
    return false;
  }

  // It's unfortunate that we have to maintain a blacklist of specific
  // classes, but we can't easily block the whole package because of
  // all our unit tests.
  private static final Set<Class<?>> FORBIDDEN_TYPES = ImmutableSet.of(
      AbstractModule.class,
      Binder.class,
      Binding.class,
      Injector.class,
      Key.class,
      MembersInjector.class,
      Module.class,
      Provider.class,
      Scope.class,
      TypeLiteral.class);
  // TODO(jessewilson): fix BuiltInModule, then add Stage

  interface CreationListener {
    void notify(Errors errors);
  }
}
