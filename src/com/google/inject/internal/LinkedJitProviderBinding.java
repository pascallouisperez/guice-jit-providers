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

import java.lang.reflect.Type;

import com.google.inject.Binder;
import com.google.inject.JitProvider;
import com.google.inject.Key;
import com.google.inject.Provider;

/**
 * Just-in-time binding backed by a key of a just-in-time provider.
 *
 * @author pascal@kaching.com (Pascal-Louis Perez)
 * @since 3.0?
 */
public final class LinkedJitProviderBinding<T> extends JitBindingImpl<T> {
  private final Key<T> key;
  private final Key<? extends JitProvider<? extends T>> jitProviderKey;

  public LinkedJitProviderBinding(
      Object source, Key<T> key, Key<? extends JitProvider<? extends T>> jitProviderKey) {
    super(source, key);
    this.key = key;
    this.jitProviderKey = checkNotNull(jitProviderKey, "jit provider key");
  }

  public JitProvider<? extends T> getJitProvider(InjectorImpl injector, Errors errors) {
    try {
      Provider<? extends JitProvider<? extends T>> provider = injector.getProviderOrThrow(jitProviderKey, errors);
      return provider == null ? null : provider.get();
    } catch (ErrorsException e) {
      return null;
    }
  }

  public void applyTo(Binder binder) {
    getScoping().applyTo(binder.withSource(getSource()).bindJit(key).toProvider(jitProviderKey));
  }
}
