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

package com.google.inject.spi;

import static com.google.inject.internal.Preconditions.checkNotNull;

import com.google.inject.Binder;
import com.google.inject.JitProvider;
import com.google.inject.internal.JitBindingImpl;

/**
 * TODO(pascal): clean documentation.
 *
 * @author pascal@kaching.com (Pascal-Louis Perez)
 * @since 3.0?
 */
public final class JitProviderInstanceBinding<T> extends JitBindingImpl<T> {
  private final Object source;
  private final JitProvider<T> jitProvider;

  JitProviderInstanceBinding(
      Object source, JitProvider<T> jitProvider) {
    this.source = checkNotNull(source, "source");
    this.jitProvider = checkNotNull(jitProvider, "jit provider");
  }

  public Object getSource() {
    return source;
  }

  public JitProvider<T> getJitProvider() {
    return jitProvider;
  }

  public void applyTo(Binder binder) {
    binder.withSource(getSource()).bindJitProvider(jitProvider);
  }

  public <V> V acceptVisitor(ElementVisitor<V> visitor) {
    return visitor.visit(this);
  }
}
