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

package com.google.inject.spi;

import com.google.inject.ConfigurationException;
import com.google.inject.Inject;
import com.google.inject.Key;
import com.google.inject.TypeLiteral;
import com.google.inject.internal.Annotations;
import com.google.inject.internal.Errors;
import com.google.inject.internal.ErrorsException;
import com.google.inject.internal.ImmutableList;
import com.google.inject.internal.ImmutableSet;
import com.google.inject.internal.Lists;
import com.google.inject.internal.MoreTypes;
import static com.google.inject.internal.MoreTypes.getRawType;
import com.google.inject.internal.Nullability;
import java.lang.annotation.Annotation;
import java.lang.reflect.AnnotatedElement;
import java.lang.reflect.Constructor;
import java.lang.reflect.Field;
import java.lang.reflect.Member;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.util.Arrays;
import java.util.Collection;
import java.util.Iterator;
import java.util.List;
import java.util.Set;

/**
 * A constructor, field or method that can receive injections. Typically this is a member with the
 * {@literal @}{@link Inject} annotation. For non-private, no argument constructors, the member may
 * omit the annotation. 
 *
 * @author crazybob@google.com (Bob Lee)
 * @since 2.0
 */
public final class InjectionPoint {

  private final boolean optional;
  private final Member member;
  private final TypeLiteral<?> declaringType;
  private final ImmutableList<Dependency<?>> dependencies;

  InjectionPoint(TypeLiteral<?> declaringType, Method method) {
    this.member = method;
    this.declaringType = declaringType;

    Inject inject = method.getAnnotation(Inject.class);
    this.optional = inject.optional();

    this.dependencies = forMember(method, declaringType, method.getParameterAnnotations());
  }

  InjectionPoint(TypeLiteral<?> declaringType, Constructor<?> constructor) {
    this.member = constructor;
    this.declaringType = declaringType;
    this.optional = false;
    this.dependencies = forMember(
        constructor, declaringType, constructor.getParameterAnnotations());
  }

  InjectionPoint(TypeLiteral<?> declaringType, Field field) {
    this.member = field;
    this.declaringType = declaringType;

    Inject inject = field.getAnnotation(Inject.class);
    this.optional = inject.optional();

    Annotation[] annotations = field.getAnnotations();

    Errors errors = new Errors(field);
    Key<?> key = null;
    try {
      key = Annotations.getKey(declaringType.getFieldType(field), field, annotations, errors);
    } catch (ErrorsException e) {
      errors.merge(e.getErrors());
    }
    errors.throwConfigurationExceptionIfErrorsExist();

    this.dependencies = ImmutableList.<Dependency<?>>of(
        newDependency(key, Nullability.allowsNull(annotations), -1));
  }

  private ImmutableList<Dependency<?>> forMember(Member member, TypeLiteral<?> type,
      Annotation[][] paramterAnnotations) {
    Errors errors = new Errors(member);
    Iterator<Annotation[]> annotationsIterator = Arrays.asList(paramterAnnotations).iterator();

    List<Dependency<?>> dependencies = Lists.newArrayList();
    int index = 0;

    for (TypeLiteral<?> parameterType : type.getParameterTypes(member)) {
      try {
        Annotation[] parameterAnnotations = annotationsIterator.next();
        Key<?> key = Annotations.getKey(parameterType, member, parameterAnnotations, errors);
        dependencies.add(newDependency(key, Nullability.allowsNull(parameterAnnotations), index));
        index++;
      } catch (ErrorsException e) {
        errors.merge(e.getErrors());
      }
    }

    errors.throwConfigurationExceptionIfErrorsExist();
    return ImmutableList.copyOf(dependencies);
  }

  // This metohd is necessary to create a Dependency<T> with proper generic type information
  private <T> Dependency<T> newDependency(Key<T> key, boolean allowsNull, int parameterIndex) {
    return new Dependency<T>(this, key, allowsNull, parameterIndex);
  }

  /**
   * Returns the injected constructor, field, or method.
   */
  public Member getMember() {
    return member;
  }

  /**
   * Returns the dependencies for this injection point. If the injection point is for a method or
   * constructor, the dependencies will correspond to that member's parameters. Field injection
   * points always have a single dependency for the field itself.
   *
   * @return a possibly-empty list
   */
  public List<Dependency<?>> getDependencies() {
    return dependencies;
  }

  /**
   * Returns true if this injection point shall be skipped if the injector cannot resolve bindings
   * for all required dependencies. Both explicit bindings (as specified in a module), and implicit
   * bindings ({@literal @}{@link com.google.inject.ImplementedBy ImplementedBy}, default
   * constructors etc.) may be used to satisfy optional injection points.
   */
  public boolean isOptional() {
    return optional;
  }

  /**
   * Returns the generic type that defines this injection point. If the member exists on a
   * parameterized type, the result will include more type information than the member's {@link
   * Member#getDeclaringClass() raw declaring class}.
   */
  public TypeLiteral<?> getDeclaringType() {
    return declaringType;
  }

  @Override public boolean equals(Object o) {
    return o instanceof InjectionPoint
        && member.equals(((InjectionPoint) o).member);
  }

  @Override public int hashCode() {
    return member.hashCode();
  }

  @Override public String toString() {
    return MoreTypes.toString(member);
  }

  /**
   * Returns a new injection point for the specified constructor. If the declaring type of {@code
   * constructor} is parameterized (such as {@code List<T>}), prefer the overload that includes a
   * type literal.
   *
   * @param constructor any single constructor present on {@code type}.
   */
  public static <T> InjectionPoint forConstructor(Constructor<T> constructor) {
    return new InjectionPoint(TypeLiteral.get(constructor.getDeclaringClass()), constructor);
  }

  /**
   * Returns a new injection point for the specified constructor of {@code type}.
   *
   * @param constructor any single constructor present on {@code type}.
   * @param type the concrete type that defines {@code constructor}.
   */
  public static <T> InjectionPoint forConstructor(
      Constructor<T> constructor, TypeLiteral<? extends T> type) {
    if (type.getRawType() != constructor.getDeclaringClass()) {
      new Errors(type)
          .constructorNotDefinedByType(constructor, type)
          .throwConfigurationExceptionIfErrorsExist();
    }

    return new InjectionPoint(type, constructor);
  }

  /**
   * Returns a new injection point for the injectable constructor of {@code type}.
   *
   * @param type a concrete type with exactly one constructor annotated {@literal @}{@link Inject},
   *     or a no-arguments constructor that is not private.
   * @throws ConfigurationException if there is no injectable constructor, more than one injectable
   *     constructor, or if parameters of the injectable constructor are malformed, such as a
   *     parameter with multiple binding annotations.
   */
  public static InjectionPoint forConstructorOf(TypeLiteral<?> type) {
    Class<?> rawType = getRawType(type.getType());
    Errors errors = new Errors(rawType);

    Constructor<?> injectableConstructor = null;
    for (Constructor<?> constructor : rawType.getDeclaredConstructors()) {
      Inject inject = constructor.getAnnotation(Inject.class);
      if (inject != null) {
        if (inject.optional()) {
          errors.optionalConstructor(constructor);
        }

        if (injectableConstructor != null) {
          errors.tooManyConstructors(rawType);
        }

        injectableConstructor = constructor;
        checkForMisplacedBindingAnnotations(injectableConstructor, errors);
      }
    }

    errors.throwConfigurationExceptionIfErrorsExist();

    if (injectableConstructor != null) {
      return new InjectionPoint(type, injectableConstructor);
    }

    // If no annotated constructor is found, look for a no-arg constructor instead.
    try {
      Constructor<?> noArgConstructor = rawType.getDeclaredConstructor();

      // Disallow private constructors on non-private classes (unless they have @Inject)
      if (Modifier.isPrivate(noArgConstructor.getModifiers())
          && !Modifier.isPrivate(rawType.getModifiers())) {
        errors.missingConstructor(rawType);
        throw new ConfigurationException(errors.getMessages());
      }

      checkForMisplacedBindingAnnotations(noArgConstructor, errors);
      return new InjectionPoint(type, noArgConstructor);
    } catch (NoSuchMethodException e) {
      errors.missingConstructor(rawType);
      throw new ConfigurationException(errors.getMessages());
    }
  }

  /**
   * Returns a new injection point for the injectable constructor of {@code type}.
   *
   * @param type a concrete type with exactly one constructor annotated {@literal @}{@link Inject},
   *     or a no-arguments constructor that is not private.
   * @throws ConfigurationException if there is no injectable constructor, more than one injectable
   *     constructor, or if parameters of the injectable constructor are malformed, such as a
   *     parameter with multiple binding annotations.
   */
  public static InjectionPoint forConstructorOf(Class<?> type) {
    return forConstructorOf(TypeLiteral.get(type));
  }

  /**
   * Returns all static method and field injection points on {@code type}.
   *
   * @return a possibly empty set of injection points. The set has a specified iteration order. All
   *      fields are returned and then all methods. Within the fields, supertype fields are returned
   *      before subtype fields. Similarly, supertype methods are returned before subtype methods.
   * @throws ConfigurationException if there is a malformed injection point on {@code type}, such as
   *      a field with multiple binding annotations. The exception's {@link
   *      ConfigurationException#getPartialValue() partial value} is a {@code Set<InjectionPoint>}
   *      of the valid injection points.
   */
  public static Set<InjectionPoint> forStaticMethodsAndFields(TypeLiteral type) {
    List<InjectionPoint> sink = Lists.newArrayList();
    Errors errors = new Errors();

    addInjectionPoints(type, Factory.FIELDS, true, sink, errors);
    addInjectionPoints(type, Factory.METHODS, true, sink, errors);

    ImmutableSet<InjectionPoint> result = ImmutableSet.copyOf(sink);
    if (errors.hasErrors()) {
      throw new ConfigurationException(errors.getMessages()).withPartialValue(result);
    }
    return result;
  }

  /**
   * Returns all static method and field injection points on {@code type}.
   *
   * @return a possibly empty set of injection points. The set has a specified iteration order. All
   *      fields are returned and then all methods. Within the fields, supertype fields are returned
   *      before subtype fields. Similarly, supertype methods are returned before subtype methods.
   * @throws ConfigurationException if there is a malformed injection point on {@code type}, such as
   *      a field with multiple binding annotations. The exception's {@link
   *      ConfigurationException#getPartialValue() partial value} is a {@code Set<InjectionPoint>}
   *      of the valid injection points.
   */
  public static Set<InjectionPoint> forStaticMethodsAndFields(Class<?> type) {
    return forStaticMethodsAndFields(TypeLiteral.get(type));
  }

  /**
   * Returns all instance method and field injection points on {@code type}.
   *
   * @return a possibly empty set of injection points. The set has a specified iteration order. All
   *      fields are returned and then all methods. Within the fields, supertype fields are returned
   *      before subtype fields. Similarly, supertype methods are returned before subtype methods.
   * @throws ConfigurationException if there is a malformed injection point on {@code type}, such as
   *      a field with multiple binding annotations. The exception's {@link
   *      ConfigurationException#getPartialValue() partial value} is a {@code Set<InjectionPoint>}
   *      of the valid injection points.
   */
  public static Set<InjectionPoint> forInstanceMethodsAndFields(TypeLiteral<?> type) {
    List<InjectionPoint> sink = Lists.newArrayList();
    Errors errors = new Errors();

    // TODO (crazybob): Filter out overridden members.
    addInjectionPoints(type, Factory.FIELDS, false, sink, errors);
    addInjectionPoints(type, Factory.METHODS, false, sink, errors);

    ImmutableSet<InjectionPoint> result = ImmutableSet.copyOf(sink);
    if (errors.hasErrors()) {
      throw new ConfigurationException(errors.getMessages()).withPartialValue(result);
    }
    return result;
  }

  /**
   * Returns all instance method and field injection points on {@code type}.
   *
   * @return a possibly empty set of injection points. The set has a specified iteration order. All
   *      fields are returned and then all methods. Within the fields, supertype fields are returned
   *      before subtype fields. Similarly, supertype methods are returned before subtype methods.
   * @throws ConfigurationException if there is a malformed injection point on {@code type}, such as
   *      a field with multiple binding annotations. The exception's {@link
   *      ConfigurationException#getPartialValue() partial value} is a {@code Set<InjectionPoint>}
   *      of the valid injection points.
   */
  public static Set<InjectionPoint> forInstanceMethodsAndFields(Class<?> type) {
    return forInstanceMethodsAndFields(TypeLiteral.get(type));
  }

  private static void checkForMisplacedBindingAnnotations(Member member, Errors errors) {
    Annotation misplacedBindingAnnotation = Annotations.findBindingAnnotation(
        errors, member, ((AnnotatedElement) member).getAnnotations());
    if (misplacedBindingAnnotation == null) {
      return;
    }

    // don't warn about misplaced binding annotations on methods when there's a field with the same
    // name. In Scala, fields always get accessor methods (that we need to ignore). See bug 242.
    if (member instanceof Method) {
      try {
        if (member.getDeclaringClass().getDeclaredField(member.getName()) != null) {
          return;
        }
      } catch (NoSuchFieldException ignore) {
      }
    }

    errors.misplacedBindingAnnotation(member, misplacedBindingAnnotation);
  }

  private static <M extends Member & AnnotatedElement> void addInjectionPoints(TypeLiteral<?> type,
      Factory<M> factory, boolean statics, Collection<InjectionPoint> injectionPoints,
      Errors errors) {
    if (type.getType() == Object.class) {
      return;
    }

    // Add injectors for superclass first.
    TypeLiteral<?> superType = type.getSupertype(type.getRawType().getSuperclass());
    addInjectionPoints(superType, factory, statics, injectionPoints, errors);

    // Add injectors for all members next
    addInjectorsForMembers(type, factory, statics, injectionPoints, errors);
  }

  private static <M extends Member & AnnotatedElement> void addInjectorsForMembers(
      TypeLiteral<?> typeLiteral, Factory<M> factory, boolean statics,
      Collection<InjectionPoint> injectionPoints, Errors errors) {
    for (M member : factory.getMembers(getRawType(typeLiteral.getType()))) {
      if (isStatic(member) != statics) {
        continue;
      }

      Inject inject = member.getAnnotation(Inject.class);
      if (inject == null) {
        continue;
      }

      try {
        injectionPoints.add(factory.create(typeLiteral, member, errors));
      } catch (ConfigurationException ignorable) {
        if (!inject.optional()) {
          errors.merge(ignorable.getErrorMessages());
        }
      }
    }
  }

  private static boolean isStatic(Member member) {
    return Modifier.isStatic(member.getModifiers());
  }

  private interface Factory<M extends Member & AnnotatedElement> {
    Factory<Field> FIELDS = new Factory<Field>() {
      public Field[] getMembers(Class<?> type) {
        return type.getDeclaredFields();
      }
      public InjectionPoint create(TypeLiteral<?> typeLiteral, Field member, Errors errors) {
        return new InjectionPoint(typeLiteral, member);
      }
    };

    Factory<Method> METHODS = new Factory<Method>() {
      public Method[] getMembers(Class<?> type) {
        return type.getDeclaredMethods();
      }
      public InjectionPoint create(TypeLiteral<?> typeLiteral, Method member, Errors errors) {
        checkForMisplacedBindingAnnotations(member, errors);
        return new InjectionPoint(typeLiteral, member);
      }
    };

    M[] getMembers(Class<?> type);
    InjectionPoint create(TypeLiteral<?> typeLiteral, M member, Errors errors);
  }
}
