/**
 * Copyright (C) 2006 Google Inc.
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

import com.google.inject.BindingAnnotation;
import com.google.inject.Key;
import com.google.inject.ScopeAnnotation;
import com.google.inject.TypeLiteral;
import com.google.inject.name.Named;
import com.google.inject.name.Names;

import java.lang.annotation.Annotation;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.reflect.Member;
import javax.inject.Qualifier;

/**
 * Annotation utilities.
 *
 * @author crazybob@google.com (Bob Lee)
 */
public class Annotations {

  /**
   * Returns true if the given annotation is retained at runtime.
   */
  public static boolean isRetainedAtRuntime(Class<? extends Annotation> annotationType) {
    Retention retention = annotationType.getAnnotation(Retention.class);
    return retention != null && retention.value() == RetentionPolicy.RUNTIME;
  }

  /** Returns the scope annotation on {@code type}, or null if none is specified. */
  public static Class<? extends Annotation> findScopeAnnotation(
      Errors errors, Class<?> implementation) {
    return findScopeAnnotation(errors, implementation.getAnnotations());
  }

  /** Returns the scoping annotation, or null if there isn't one. */
  public static Class<? extends Annotation> findScopeAnnotation(Errors errors, Annotation[] annotations) {
    Class<? extends Annotation> found = null;

    for (Annotation annotation : annotations) {
      Class<? extends Annotation> annotationType = annotation.annotationType();
      if (isScopeAnnotation(annotationType)) {
        if (found != null) {
          errors.duplicateScopeAnnotations(found, annotationType);
        } else {
          found = annotationType;
        }
      }
    }

    return found;
  }

  public static boolean isScopeAnnotation(Class<? extends Annotation> annotationType) {
    return annotationType.isAnnotationPresent(ScopeAnnotation.class) 
        || annotationType.isAnnotationPresent(javax.inject.Scope.class);
  }

  /**
   * Adds an error if there is a misplaced annotations on {@code type}. Scoping
   * annotations are not allowed on abstract classes or interfaces.
   */
  public static void checkForMisplacedScopeAnnotations(
      Class<?> type, Object source, Errors errors) {
    if (Classes.isConcrete(type)) {
      return;
    }

    Class<? extends Annotation> scopeAnnotation = findScopeAnnotation(errors, type);
    if (scopeAnnotation != null) {
      errors.withSource(type).scopeAnnotationOnAbstractType(scopeAnnotation, type, source);
    }
  }

  /** Gets a key for the given type, member and annotations. */
  public static Key<?> getKey(TypeLiteral<?> type, Member member, Annotation[] annotations,
      Errors errors) throws ErrorsException {
    int numErrorsBefore = errors.size();
    Annotation found = findBindingAnnotation(errors, member, annotations);
    errors.throwIfNewErrors(numErrorsBefore);
    return found == null ? Key.get(type) : Key.get(type, found);
  }

  /**
   * Returns the binding annotation on {@code member}, or null if there isn't one.
   */
  public static Annotation findBindingAnnotation(
      Errors errors, Member member, Annotation[] annotations) {
    Annotation found = null;

    for (Annotation annotation : annotations) {
      Class<? extends Annotation> annotationType = annotation.annotationType();
      if (isBindingAnnotation(annotationType)) {
        if (found != null) {
          errors.duplicateBindingAnnotations(member, found.annotationType(), annotationType);
        } else {
          found = annotation;
        }
      }
    }

    return found;
  }

  /**
   * Returns true if annotations of the specified type are binding annotations.
   */
  public static boolean isBindingAnnotation(Class<? extends Annotation> annotationType) {
    return annotationType.isAnnotationPresent(BindingAnnotation.class) 
          || annotationType.isAnnotationPresent(Qualifier.class);
  }

  /**
   * If the annotation is an instance of {@code javax.inject.Named} or {@code
   * com.google.inject.name.Named}, return a canonicalized instance that will be equal to instances
   * of either that have the same value. Returns the given annotation otherwise.
   */
  public static Annotation canonicalizeIfNamed(Annotation annotation) {
    if(annotation instanceof javax.inject.Named) {
      return Names.named(((javax.inject.Named)annotation).value());       
    } else {
      return annotation;
    }
  }
}
