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

import static com.google.inject.internal.Preconditions.checkNotNull;

import com.google.inject.Binder;
import com.google.inject.JitProvider;

/**
 * Just-in-time binding backed by an instance of a just-in-time provider.
 *
 * @author pascal@kaching.com (Pascal-Louis Perez)
 * @since 3.0?
 */
public final class JitProviderInstanceBinding<T> extends JitBindingImpl<T> {
  private final JitProvider<T> jitProvider;

  public JitProviderInstanceBinding(
      Object source, JitProvider<T> jitProvider) {
    super(source);
    this.jitProvider = checkNotNull(jitProvider, "jit provider");
  }

  public JitProvider<T> getJitProvider(InjectorImpl injector, Errors errors) {
    return jitProvider;
  }

  public void applyTo(Binder binder) {
    binder.withSource(getSource()).bindJitProvider(jitProvider);
  }
}
