/**
 * Copyright (C) 2010 Google Inc.
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

import java.lang.annotation.Annotation;
import java.util.List;

import com.google.inject.JitProvider;
import com.google.inject.Key;
import com.google.inject.Scope;
import com.google.inject.TypeLiteral;
import com.google.inject.binder.AnnotatedJitBindingBuilder;
import com.google.inject.binder.LinkedJitBindingBuilder;
import com.google.inject.binder.SimplifiedScopedBindingBuilder;
import com.google.inject.spi.Element;

/**
 * Bind a just-in-time provider.
 *
 * @author pascal@kaching.com (Pascal-Louis Perez)
 * @since 3.0?
 */
public class JitBindingBuilder<T> implements AnnotatedJitBindingBuilder<T> {

  private Key<T> key;
  private JitBindingImpl<?> binding;
  
  private final List<Element> elements;
  private final Object source;

  public JitBindingBuilder(TypeLiteral<T> typeLiteral, List<Element> elements, Object source) {
    this.source = source;
    this.key = Key.get(typeLiteral);
    this.elements = elements;
  }

  public JitBindingBuilder(Key<T> key, List<Element> elements, Object source) {
    this.key = key;
    this.elements = elements;
    this.source = source;
  }

  public LinkedJitBindingBuilder<T> annotatedWith(Annotation annotation) {
    this.key = Key.get(key.getTypeLiteral(), annotation);
    return this;
  }
  
  public LinkedJitBindingBuilder<T> annotatedWith(
      Class<? extends Annotation> annotationType) {
    this.key = Key.get(key.getTypeLiteral(), annotationType);
    return this;
  }

  public SimplifiedScopedBindingBuilder toJitProvider(
      JitProvider<? extends T> jitProvider) {
    elements.add(binding = new JitProviderInstanceBinding<T>(source, key, jitProvider));
    return this;
  }

  public SimplifiedScopedBindingBuilder toJitProvider(
      Class<? extends JitProvider<? extends T>> jitProviderType) {
    return toJitProvider(Key.get(jitProviderType));
  }

  public SimplifiedScopedBindingBuilder toJitProvider(
      TypeLiteral<? extends JitProvider<? extends T>> jitProviderType) {
    return toJitProvider(Key.get(jitProviderType));
  }

  public SimplifiedScopedBindingBuilder toJitProvider(
      Key<? extends JitProvider<? extends T>> jitProviderType) {
    elements.add(binding = new LinkedJitProviderBinding<T>(source, key, jitProviderType));
    return this;
  }

  public void in(Class<? extends Annotation> scopeAnnotation) {
    binding.withScoping(Scoping.forAnnotation(scopeAnnotation));
  }

  public void in(Scope scope) {
    binding.withScoping(Scoping.forInstance(scope));
  }

}
