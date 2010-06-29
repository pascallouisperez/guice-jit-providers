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

package com.google.inject.binder;

import com.google.inject.JitProvider;
import com.google.inject.Key;
import com.google.inject.TypeLiteral;

/**
 * See the EDSL examples at {@link com.google.inject.Binder}.
 *
 * @author pascal@kaching.com (Pascal-Louis Perez)
 * @since 3.0?
 */
public interface LinkedJitBindingBuilder<T> extends SimplifiedScopedBindingBuilder {

  /**
   * See the EDSL examples at {@link com.google.inject.Binder}.
   */
  SimplifiedScopedBindingBuilder toJitProvider(
      JitProvider<? extends T> jitProvider);

  /**
   * See the EDSL examples at {@link com.google.inject.Binder}.
   */
  SimplifiedScopedBindingBuilder toJitProvider(
      Class<? extends JitProvider<? extends T>> jitProviderType);

  /**
   * See the EDSL examples at {@link com.google.inject.Binder}.
   */
  SimplifiedScopedBindingBuilder toJitProvider(
      TypeLiteral<? extends JitProvider<? extends T>> jitProviderType);

  /**
   * See the EDSL examples at {@link com.google.inject.Binder}.
   */
  SimplifiedScopedBindingBuilder toJitProvider(
      Key<? extends JitProvider<? extends T>> jitProviderType);
  
}
