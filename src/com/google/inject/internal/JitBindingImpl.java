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

import com.google.inject.JitBinding;
import com.google.inject.JitProvider;
import com.google.inject.Key;
import com.google.inject.spi.BindingScopingVisitor;
import com.google.inject.spi.ElementVisitor;

/**
 * Default implementation of a just-in-time binding.
 *
 */
public abstract class JitBindingImpl<T> implements JitBinding<T> {
  private final Object source;
  private final Type typeScheme;
  private Scoping scoping;

  protected JitBindingImpl(Object source, Type typeScheme) {
    this.typeScheme = typeScheme;
    this.source = checkNotNull(source, "source");
    this.scoping = Scoping.UNSCOPED;
  }
  
  @Override
  public boolean canProvide(Key<?> key) {
    return MoreTypes.isInstance(typeScheme, key.getTypeLiteral().getType());
  }

  public abstract JitProvider<? extends T> getJitProvider(InjectorImpl injector, Errors errors);

  public void withScoping(Scoping scoping) {
    this.scoping = scoping;
  }

  Scoping getScoping() {
    return scoping;
  }

  public Object getSource() {
    return source;
  }

  public <V> V acceptScopingVisitor(BindingScopingVisitor<V> visitor) {
    return scoping.acceptVisitor(visitor);
  }

  public <V> V acceptVisitor(ElementVisitor<V> visitor) {
    return visitor.visit(this);
  }
}
