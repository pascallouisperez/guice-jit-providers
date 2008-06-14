/**
 * Copyright (C) 2008 Google Inc.
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

package com.google.inject;

import com.google.common.collect.Lists;
import com.google.common.collect.Sets;
import com.google.inject.commands.BindCommand;
import com.google.inject.commands.BindConstantCommand;
import com.google.inject.commands.BindScoping.Visitor;
import com.google.inject.commands.BindTarget;
import com.google.inject.internal.Annotations;
import com.google.inject.internal.ErrorMessage;
import com.google.inject.internal.ResolveFailedException;
import com.google.inject.internal.StackTraceElements;
import java.lang.annotation.Annotation;
import java.lang.reflect.Type;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Handles {@link Binder#bind} and {@link Binder#bindConstant} commands.
 *
 * @author crazybob@google.com (Bob Lee)
 * @author jessewilson@google.com (Jesse Wilson)
 */
class BindCommandProcessor extends CommandProcessor {

  private final InjectorImpl injector;
  private final Map<Class<? extends Annotation>, Scope> scopes;
  private final List<CreationListener> creationListeners = Lists.newArrayList();
  private final Map<Key<?>, BindingImpl<?>> bindings;
  private final Set<Object> outstandingInjections;
  private final List<Runnable> untargettedBindings = Lists.newArrayList();

  BindCommandProcessor(InjectorImpl injector,
      Map<Class<? extends Annotation>, Scope> scopes,
      Map<Key<?>, BindingImpl<?>> bindings,
      Set<Object> outstandingInjections) {
    super(injector.errorHandler);
    this.injector = injector;
    this.scopes = scopes;
    this.bindings = bindings;
    this.outstandingInjections = outstandingInjections;
  }

  @Override public <T> Boolean visitBind(BindCommand<T> command) {
    final Object source = command.getSource();

    final Key<T> key = command.getKey();
    Class<? super T> rawType = key.getTypeLiteral().getRawType();

    if (rawType == Provider.class) {
      addError(source, ErrorMessage.bindingToProvider());
      return true;
    }

    validateKey(command.getSource(), command.getKey());

    final LoadStrategy loadStrategy = command.getScoping().isEagerSingleton()
        ? LoadStrategy.EAGER
        : LoadStrategy.LAZY;
    final Scope scope = command.getScoping().acceptVisitor(new Visitor<Scope>() {
      public Scope visitEagerSingleton() {
        return Scopes.SINGLETON;
      }

      public Scope visitScope(Scope scope) {
        return scope;
      }

      public Scope visitScopeAnnotation(Class<? extends Annotation> scopeAnnotation) {
        Scope scope = scopes.get(scopeAnnotation);
        if (scope != null) {
          return scope;
        } else {
          addError(source, ErrorMessage.scopeNotFound(
              "@" + scopeAnnotation.getSimpleName()));
          return Scopes.NO_SCOPE;
        }
      }

      public Scope visitNoScoping() {
        return null;
      }
    });

    command.getTarget().acceptVisitor(new BindTarget.Visitor<T, Void>() {
      public Void visitToInstance(T instance) {
        ConstantFactory<? extends T> factory = new ConstantFactory<T>(instance);
        outstandingInjections.add(instance);
        InternalFactory<? extends T> scopedFactory
            = Scopes.scope(key, injector, factory, scope);
        putBinding(new InstanceBindingImpl<T>(
                injector, key, source, scopedFactory, instance));
        return null;
      }

      public Void visitToProvider(Provider<? extends T> provider) {
        InternalFactoryToProviderAdapter<? extends T> factory
            = new InternalFactoryToProviderAdapter<T>(provider, source);
        outstandingInjections.add(provider);
        InternalFactory<? extends T> scopedFactory
            = Scopes.scope(key, injector, factory, scope);
        putBinding(new ProviderInstanceBindingImpl<T>(
                injector, key, source, scopedFactory, scope, provider, loadStrategy));
        return null;
      }

      public Void visitToProviderKey(Key<? extends Provider<? extends T>> providerKey) {
        final BoundProviderFactory<T> boundProviderFactory =
            new BoundProviderFactory<T>(providerKey, source);
        creationListeners.add(boundProviderFactory);
        InternalFactory<? extends T> scopedFactory = Scopes.scope(
            key, injector, (InternalFactory<? extends T>) boundProviderFactory, scope);
        putBinding(new LinkedProviderBindingImpl<T>(
                injector, key, source, scopedFactory, scope, providerKey, loadStrategy));
        return null;
      }

      public Void visitToKey(Key<? extends T> targetKey) {
        if (key.equals(targetKey)) {
          addError(source, ErrorMessage.recursiveBinding());
        }

        FactoryProxy<T> factory = new FactoryProxy<T>(key, targetKey, source);
        creationListeners.add(factory);
        InternalFactory<? extends T> scopedFactory
            = Scopes.scope(key, injector, factory, scope);
        putBinding(new LinkedBindingImpl<T>(
                injector, key, source, scopedFactory, scope, targetKey, loadStrategy));
        return null;
      }

      public Void visitUntargetted() {
        final Type type = key.getTypeLiteral().getType();

        // Error: Missing implementation.
        // Example: bind(Date.class).annotatedWith(Red.class);
        // We can't assume abstract types aren't injectable. They may have an
        // @ImplementedBy annotation or something.
        if (key.hasAnnotationType() || !(type instanceof Class<?>)) {
          addError(source, ErrorMessage.missingImplementation());
          putBinding(invalidBinding(injector, key, source));
          return null;
        }

        // This cast is safe after the preceeding check.
        @SuppressWarnings("unchecked")
        Class<T> clazz = (Class<T>) type;
        final BindingImpl<T> binding;
        try {
          binding = injector.createUnitializedBinding(clazz, scope, source, loadStrategy);
          putBinding(binding);
        } catch (ResolveFailedException e) {
          injector.errorHandler.handle(e.getMessage(source));
          putBinding(invalidBinding(injector, key, source));
          return null;
        }

        untargettedBindings.add(new Runnable() {
          public void run() {
            try {
              injector.initializeBinding(binding);
            } catch (ResolveFailedException e) {
              injector.errorHandler.handle(e.getMessage(source));
            }
          }
        });

        return null;
      }
    });

    return true;
  }

  private <T> void validateKey(Object source, Key<T> key) {
    if (key.hasAnnotationType()) {
      Class<? extends Annotation> annotationType = key.getAnnotationType();

      if (!Annotations.isRetainedAtRuntime(annotationType)) {
        addError(StackTraceElements.forType(annotationType),
            ErrorMessage.missingRuntimeRetention(source));
      }

      if (!Key.isBindingAnnotation(annotationType)) {
        addError(StackTraceElements.forType(annotationType),
            ErrorMessage.missingBindingAnnotation(source));
      }
    }
  }

  <T> InvalidBindingImpl<T> invalidBinding(InjectorImpl injector, Key<T> key, Object source) {
    return new InvalidBindingImpl<T>(injector, key, source);
  }

  @Override public Boolean visitBindConstant(BindConstantCommand command) {
    BindTarget<?> target = command.getTarget();
    if (target == null) {
      addError(command.getSource(), ErrorMessage.missingConstantValues());
      return true;
    }

    Object value = target.get();
    validateKey(command.getSource(), command.getKey());
    ConstantFactory<Object> factory = new ConstantFactory<Object>(value);
    putBinding(new ConstantBindingImpl<Object>(
        injector, command.getKey(), command.getSource(), factory, value));

    return true;
  }

  public void createUntargettedBindings() {
    for (Runnable untargettedBinding : untargettedBindings) {
      untargettedBinding.run();
    }
  }

  public void runCreationListeners(InjectorImpl injector) {
    for (CreationListener creationListener : creationListeners) {
      creationListener.notify(injector);
    }
  }

  private void putBinding(BindingImpl<?> binding) {
    Key<?> key = binding.getKey();
    Binding<?> original = bindings.get(key);

    Class<?> rawType = key.getRawType();
    if (FORBIDDEN_TYPES.contains(rawType)) {
      addError(binding.getSource(), ErrorMessage.cannotBindToGuiceType(
          rawType.getSimpleName()));
      return;
    }

    if (bindings.containsKey(key)) {
      addError(binding.getSource(), ErrorMessage.bindingAlreadySet(key,
          original.getSource()));
    } else {
      bindings.put(key, binding);
    }
  }

  private static Set<Class<?>> FORBIDDEN_TYPES = forbiddenTypes();

  @SuppressWarnings("unchecked") // For generic array creation.
  private static Set<Class<?>> forbiddenTypes() {
    Set<Class<?>> set = Sets.newHashSet();

    Collections.addAll(set,

        // It's unfortunate that we have to maintain a blacklist of specific
        // classes, but we can't easily block the whole package because of
        // all our unit tests.

        AbstractModule.class,
        Binder.class,
        Binding.class,
        Key.class,
        Module.class,
        Provider.class,
        Scope.class,
        TypeLiteral.class);
    return Collections.unmodifiableSet(set);
  }

  interface CreationListener {
    void notify(InjectorImpl injector);
  }
}
