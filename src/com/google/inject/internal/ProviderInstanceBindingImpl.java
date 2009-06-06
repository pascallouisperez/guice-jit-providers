/*
Copyright (C) 2007 Google Inc.

Licensed under the Apache License, Version 2.0 (the "License");
you may not use this file except in compliance with the License.
You may obtain a copy of the License at

     http://www.apache.org/licenses/LICENSE-2.0

Unless required by applicable law or agreed to in writing, software
distributed under the License is distributed on an "AS IS" BASIS,
WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
See the License for the specific language governing permissions and
limitations under the License.
*/

package com.google.inject.internal;

import com.google.inject.Binder;
import com.google.inject.Key;
import com.google.inject.Provider;
import com.google.inject.spi.BindingTargetVisitor;
import com.google.inject.spi.Dependency;
import com.google.inject.spi.HasDependencies;
import com.google.inject.spi.InjectionPoint;
import com.google.inject.spi.ProviderInstanceBinding;
import java.util.Set;

final class ProviderInstanceBindingImpl<T> extends BindingImpl<T>
    implements ProviderInstanceBinding<T> {

  final Provider<? extends T> providerInstance;
  final ImmutableSet<InjectionPoint> injectionPoints;

  public ProviderInstanceBindingImpl(InjectorImpl injector, Key<T> key,
      Object source, InternalFactory<? extends T> internalFactory, Scoping scoping,
      Provider<? extends T> providerInstance,
      Set<InjectionPoint> injectionPoints) {
    super(injector, key, source, internalFactory, scoping);
    this.providerInstance = providerInstance;
    this.injectionPoints = ImmutableSet.copyOf(injectionPoints);
  }

  public ProviderInstanceBindingImpl(Object source, Key<T> key, Scoping scoping,
      Set<InjectionPoint> injectionPoints, Provider<? extends T> providerInstance) {
    super(source, key, scoping);
    this.injectionPoints = ImmutableSet.copyOf(injectionPoints);
    this.providerInstance = providerInstance;
  }

  public <V> V acceptTargetVisitor(BindingTargetVisitor<? super T, V> visitor) {
    return visitor.visit(this);
  }

  public Provider<? extends T> getProviderInstance() {
    return providerInstance;
  }

  public Set<InjectionPoint> getInjectionPoints() {
    return injectionPoints;
  }

  public Set<Dependency<?>> getDependencies() {
    return providerInstance instanceof HasDependencies
        ? ImmutableSet.copyOf(((HasDependencies) providerInstance).getDependencies())
        : Dependency.forInjectionPoints(injectionPoints);
  }

  public BindingImpl<T> withScoping(Scoping scoping) {
    return new ProviderInstanceBindingImpl<T>(
        getSource(), getKey(), scoping, injectionPoints, providerInstance);
  }

  public BindingImpl<T> withKey(Key<T> key) {
    return new ProviderInstanceBindingImpl<T>(
        getSource(), key, getScoping(), injectionPoints, providerInstance);
  }

  public void applyTo(Binder binder) {
    getScoping().applyTo(
        binder.withSource(getSource()).bind(getKey()).toProvider(getProviderInstance()));
  }

  @Override
  public String toString() {
    return new ToStringBuilder(ProviderInstanceBinding.class)
        .add("key", getKey())
        .add("source", getSource())
        .add("scope", getScoping())
        .add("provider", providerInstance)
        .toString();
  }
}
