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

import com.google.inject.Scope;
import com.google.inject.binder.SimplifiedScopedBindingBuilder;

/**
 * Bind a just-in-time provider.
 *
 * @author pascal@kaching.com (Pascal-Louis Perez)
 * @since 3.0?
 */
public class JitBindingBuilder implements SimplifiedScopedBindingBuilder {

  private final JitBindingImpl<?> binding;

  public JitBindingBuilder(JitBindingImpl<?> binding) {
    this.binding = binding;
  }

  public void in(Class<? extends Annotation> scopeAnnotation) {
    binding.withScoping(Scoping.forAnnotation(scopeAnnotation));
  }

  public void in(Scope scope) {
    binding.withScoping(Scoping.forInstance(scope));
  }

}
