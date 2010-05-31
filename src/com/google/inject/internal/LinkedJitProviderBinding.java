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
import com.google.inject.Key;
import com.google.inject.Provider;

/**
 * TODO(pascal): clean documentation.
 *
 * @author pascal@kaching.com (Pascal-Louis Perez)
 * @since 3.0?
 */
public final class LinkedJitProviderBinding<T> extends JitBindingImpl<T> {
  private final Key<? extends JitProvider<T>> jitProviderKey;

  public LinkedJitProviderBinding(
      Object source, Key<? extends JitProvider<T>> jitProviderKey) {
    super(source);
    this.jitProviderKey = checkNotNull(jitProviderKey, "jit provider key");
  }

  public JitProvider<T> getJitProvider(InjectorImpl injector, Errors errors) {
    try {
      Provider<? extends JitProvider<T>> provider = injector.getProviderOrThrow(jitProviderKey, errors);
      return provider == null ? null : provider.get();
    } catch (ErrorsException e) {
      return null;
    }
  }

  public void applyTo(Binder binder) {
    binder.withSource(getSource()).bindJitProvider(jitProviderKey);
  }
}
