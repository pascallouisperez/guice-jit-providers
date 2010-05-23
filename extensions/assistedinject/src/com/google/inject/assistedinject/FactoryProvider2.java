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

package com.google.inject.assistedinject;

import com.google.inject.AbstractModule;
import com.google.inject.Binder;
import com.google.inject.Binding;
import com.google.inject.ConfigurationException;
import com.google.inject.Inject;
import com.google.inject.Injector;
import com.google.inject.Key;
import com.google.inject.Module;
import com.google.inject.Provider;
import com.google.inject.ProvisionException;
import com.google.inject.TypeLiteral;
import static com.google.inject.internal.Annotations.getKey;
import com.google.inject.internal.BytecodeGen;
import com.google.inject.internal.Errors;
import com.google.inject.internal.ErrorsException;
import com.google.inject.internal.ImmutableList;
import com.google.inject.internal.ImmutableMap;
import com.google.inject.internal.Iterables;
import com.google.inject.internal.ToStringBuilder;

import static com.google.inject.internal.Iterables.getOnlyElement;
import com.google.inject.internal.Lists;
import static com.google.inject.internal.Preconditions.checkState;

import com.google.inject.spi.Dependency;
import com.google.inject.spi.InjectionPoint;
import com.google.inject.spi.Message;
import com.google.inject.spi.Toolable;
import com.google.inject.util.Providers;
import java.lang.annotation.Annotation;
import java.lang.reflect.Constructor;
import java.lang.reflect.InvocationHandler;
import java.lang.reflect.Method;
import java.lang.reflect.Proxy;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.Map;

/**
 * The newer implementation of factory provider. This implementation uses a child injector to
 * create values.
 *
 * @author jessewilson@google.com (Jesse Wilson)
 * @author dtm@google.com (Daniel Martin)
 * @author schmitt@google.com (Peter Schmitt)
 * @author sameb@google.com (Sam Berlin)
 */
final class FactoryProvider2<F> implements InvocationHandler, Provider<F> {

  /** if a factory method parameter isn't annotated, it gets this annotation. */
  static final Assisted DEFAULT_ANNOTATION = new Assisted() {
    public String value() {
      return "";
    }

    public Class<? extends Annotation> annotationType() {
      return Assisted.class;
    }

    @Override public boolean equals(Object o) {
      return o instanceof Assisted
          && ((Assisted) o).value().equals("");
    }

    @Override public int hashCode() {
      return 127 * "value".hashCode() ^ "".hashCode();
    }

    @Override public String toString() {
      return "@" + Assisted.class.getName() + "(value=)";
    }
  };
  
  /** All the data necessary to perform an assisted inject. */
  private static class AssistData {
    /** the constructor the implementation is constructed with. */
    final Constructor<?> constructor;
    /** the return type in the factory method that the constructor is bound to. */
    final Key<?> returnType;
    /** the parameters in the factory method associated with this data. */
    final ImmutableList<Key<?>> paramTypes;
    
    /** true if {@link #validForOptimizedAssistedInject} returned true. */
    final boolean optimized;
    /** the list of optimized providers, empty if not optimized. */
    final List<ThreadLocalProvider> providers;
    /** used to perform optimized factory creations. */
    volatile Binding<?> cachedBinding; // TODO: volatile necessary?
    
    AssistData(Constructor<?> constructor, Key<?> returnType,
        ImmutableList<Key<?>> paramTypes, boolean optimized,
        List<ThreadLocalProvider> providers) {
      this.constructor = constructor;
      this.returnType = returnType;
      this.paramTypes = paramTypes;
      this.optimized = optimized;
      this.providers = providers;
    }

    @Override
    public String toString() {
      return new ToStringBuilder(getClass())
        .add("ctor", constructor)
        .add("return type", returnType)
        .add("param type", paramTypes)
        .add("optimized", optimized)
        .add("providers", providers)
        .add("cached binding", cachedBinding)
        .toString();
      
    }
  }

  /** the produced type, or null if all methods return concrete types */
  private final BindingCollector collector;
  private final ImmutableMap<Method, AssistData> assistDataByMethod;  

  /** the hosting injector, or null if we haven't been initialized yet */
  private Injector injector;

  /** the factory interface, implemented and provided */
  private final F factory;

  /**
   * @param factoryType a Java interface that defines one or more create methods.
   * @param collector binding configuration that maps method return types to
   *    implementation types.
   */
  FactoryProvider2(TypeLiteral<F> factoryType, BindingCollector collector) {
    this.collector = collector;

    Errors errors = new Errors();

    @SuppressWarnings("unchecked") // we imprecisely treat the class literal of T as a Class<T>
    Class<F> factoryRawType = (Class) factoryType.getRawType();

    try {
      ImmutableMap.Builder<Method, AssistData> assistDataBuilder = ImmutableMap.builder();
      // TODO: also grab methods from superinterfaces
      for (Method method : factoryRawType.getMethods()) {
        TypeLiteral<?> returnTypeLiteral = factoryType.getReturnType(method);
        Key<?> returnType;
        try {
          returnType = getKey(returnTypeLiteral, method, method.getAnnotations(), errors);
        } catch(ConfigurationException ce) {
          // If this was an error due to returnTypeLiteral not being specified, rephrase
          // it as our factory not being specified, so it makes more sense to users.
          if(isTypeNotSpecified(returnTypeLiteral, ce)) {
            throw errors.keyNotFullySpecified(TypeLiteral.get(factoryRawType)).toException();
          } else {
            throw ce;
          }
        }
        List<TypeLiteral<?>> params = factoryType.getParameterTypes(method);
        Annotation[][] paramAnnotations = method.getParameterAnnotations();
        int p = 0;
        List<Key<?>> keys = Lists.newArrayList();
        for (TypeLiteral<?> param : params) {
          Key<?> paramKey = getKey(param, method, paramAnnotations[p++], errors);
          keys.add(assistKey(method, paramKey, errors));
        }
        ImmutableList<Key<?>> immutableParamList = ImmutableList.copyOf(keys);
        
        // try to match up the method to the constructor
        TypeLiteral<?> implementation = collector.getBindings().get(returnType);
        if(implementation == null) {
          implementation = returnType.getTypeLiteral();
        }
        InjectionPoint ctorInjectionPoint = findMatchingConstructorInjectionPoint(method, implementation, immutableParamList, errors);
        Constructor<?> constructor = null;
        if(ctorInjectionPoint != null) {
          // There are three reasons this could be null:
          // 1) The implementation is an interface and is forwarded (explicitly or implicitly)
          //    to another binding (see FactoryModuleBuildTest.test[Implicit|Explicit]ForwardingAssistedBinding.
          //    In this case, things are OK and the exception is right to be ignored.
          // 2) The implementation has something wrong with its constructors (two @injects, invalid ctor, etc..)
          //    In this case, by having a null constructor we let the proper exception be recreated later on.
          // 3) findMatchingConstructor added errors to the Errors object and returned null, in which case
          //    this method will appropriately throw an exception later on.
          constructor = (Constructor)ctorInjectionPoint.getMember();
        }
        
        List<ThreadLocalProvider> providers = Collections.emptyList();
        boolean optimized = false;
        // Now go through all dependencies of the implementation and see if it is OK to
        // use an optimized form of assistedinject2.  The optimized form requires that
        // all injections directly inject the object itself (and not a Provider of the object,
        // or an Injector), because it caches a single child injector and mutates the Provider
        // of the arguments in a ThreadLocal.
        if(validForOptimizedAssistedInject(ctorInjectionPoint, implementation)) {
          ImmutableList.Builder<ThreadLocalProvider> providerListBuilder = ImmutableList.builder();
          for(int i = 0; i < params.size(); i++) {
            providerListBuilder.add(new ThreadLocalProvider());
          }
          providers = providerListBuilder.build();
          optimized = true;
        }
        assistDataBuilder.put(method, new AssistData(constructor, returnType, immutableParamList, optimized, providers));
      }

      // If we generated any errors (from finding matching constructors, for instance), throw an exception.
      if(errors.hasErrors()) {
        throw errors.toException();
      }

      assistDataByMethod = assistDataBuilder.build();
    } catch (ErrorsException e) {
      throw new ConfigurationException(e.getErrors().getMessages());
    }
    
    factory = factoryRawType.cast(Proxy.newProxyInstance(BytecodeGen.getClassLoader(factoryRawType),
        new Class[] { factoryRawType }, this));
  }

  public F get() {
    return factory;
  }

  /**
   * Returns true if the ConfigurationException is due to an error of TypeLiteral not being fully
   * specified.
   */
  private boolean isTypeNotSpecified(TypeLiteral typeLiteral, ConfigurationException ce) {
    Collection<Message> messages = ce.getErrorMessages();
    if (messages.size() == 1) {
      Message msg = Iterables.getOnlyElement(
          new Errors().keyNotFullySpecified(typeLiteral).getMessages());
      return msg.getMessage().equals(Iterables.getOnlyElement(messages).getMessage());
    } else {
      return false;
    }
  }

  /**
   * Finds a constructor suitable for the method.  If the implementation contained any constructors
   * marked with {@link AssistedInject}, this requires all {@link Assisted} parameters to exactly
   * match the parameters (in any order) listed in the method.  Otherwise, if no
   * {@link AssistedInject} constructors exist, this will default to looking for an
   * {@literal @}{@link Inject} constructor.
   */
  private InjectionPoint findMatchingConstructorInjectionPoint(Method method,
      TypeLiteral<?> implementation, List<Key<?>> paramList, Errors errors) throws ErrorsException {
    Constructor<?> matchingConstructor = null;
    boolean anyAssistedInjectConstructors = false;
    // Look for AssistedInject constructors...
    for (Constructor<?> constructor : implementation.getRawType().getDeclaredConstructors()) {
      if (constructor.isAnnotationPresent(AssistedInject.class)) {
        anyAssistedInjectConstructors = true;
        if (constructorHasMatchingParams(implementation, constructor, paramList, errors)) {
          if (matchingConstructor != null) {
            errors
                .addMessage(
                    "%s has more than one constructor annotated with @AssistedInject"
                        + " that matches the parameters in method %s.  Unable to create AssistedInject factory.",
                    implementation, method);
            return null;
          } else {
            matchingConstructor = constructor;
          }
        }
      }
    }
    
    if(!anyAssistedInjectConstructors) {
      // If none existed, use @Inject.
      try {
        return InjectionPoint.forConstructorOf(implementation);
      } catch(ConfigurationException ignored) {
        // This will be handled later more appropriately (with a good message).
        return null;
      }
    } else {
      // Otherwise, use it or fail with a good error message.
      if(matchingConstructor != null) {
          // safe because we got the constructor from this implementation.
          @SuppressWarnings("unchecked")
          InjectionPoint ip = InjectionPoint.forConstructor(
              (Constructor)matchingConstructor, implementation);
          return ip;
      } else {
        errors.addMessage(
            "%s has @AssistedInject constructors, but none of them match the"
            + " parameters in method %s.  Unable to create AssistedInject factory.",
            implementation, method);
        return null;
      }
    }
  }

  /**
   * Matching logic for constructors annotated with AssistedInject.
   * This returns true if and only if all @Assisted parameters in the
   * constructor exactly match (in any order) all @Assisted parameters
   * the method's parameter.
   */
  private boolean constructorHasMatchingParams(TypeLiteral<?> type,
      Constructor<?> constructor, List<Key<?>> paramList, Errors errors)
      throws ErrorsException {
    List<TypeLiteral<?>> params = type.getParameterTypes(constructor);
    Annotation[][] paramAnnotations = constructor.getParameterAnnotations();
    int p = 0;
    List<Key<?>> constructorKeys = Lists.newArrayList();
    for (TypeLiteral<?> param : params) {
      Key<?> paramKey = getKey(param, constructor, paramAnnotations[p++],
          errors);
      constructorKeys.add(paramKey);
    }
    // Require that every key exist in the constructor to match up exactly.
    for (Key<?> key : paramList) {
      // If it didn't exist in the constructor set, we can't use it.
      if (!constructorKeys.remove(key)) {
        return false;
      }
    }
    // If any keys remain and their annotation is Assisted, we can't use it.
    for (Key<?> key : constructorKeys) {
      if (key.getAnnotationType() == Assisted.class) {
        return false;
      }
    }
    // All @Assisted params match up to the method's parameters.
    return true;
  }
  
  /**
   * Returns true if the implementation & constructor are suitable for an
   * optimized version of AssistedInject. The optimized version caches the
   * binding & uses a ThreadLocal Provider, so can only be applied if the
   * assisted bindings are immediately provided. This looks for hints that the
   * values may be lazily retrieved, by looking for injections of Injector or a
   * Provider for the assisted values.
   */
  private boolean validForOptimizedAssistedInject(InjectionPoint ctorPoint, TypeLiteral<?> implementation) {
    if(ctorPoint != null) {
      for(Dependency<?> dep : ctorPoint.getDependencies()) {
        if(isInjectorOrAssistedProvider(dep)) {
          return false;
        }
      }
    }
    if(!implementation.getRawType().isInterface()) {
      for(InjectionPoint ip : InjectionPoint.forInstanceMethodsAndFields(implementation)) {
        for(Dependency<?> dep : ip.getDependencies()) {
          if(isInjectorOrAssistedProvider(dep)) {
            return false;
          }        
        }
      }
    }
    return true;
  }
  
  /**
   * Returns true if the dependency is for {@link Injector} or if the dependency
   * is a {@link Provider} for a parameter that is {@literal @}{@link Assisted}.
   */
  private boolean isInjectorOrAssistedProvider(Dependency<?> dependency) {
    Class annotationType = dependency.getKey().getAnnotationType();
    if (annotationType != null && annotationType.equals(Assisted.class)) { // If it's assisted..
      if (dependency.getKey().getTypeLiteral().getRawType().equals(Provider.class)) { // And a Provider...
        return true;
      }
    } else if (dependency.getKey().getTypeLiteral().getRawType().equals(Injector.class)) { // If it's the Injector...
      return true;
    }
    return false;
  }
  
  /**
   * Returns a key similar to {@code key}, but with an {@literal @}Assisted binding annotation.
   * This fails if another binding annotation is clobbered in the process. If the key already has
   * the {@literal @}Assisted annotation, it is returned as-is to preserve any String value.
   */
  private <T> Key<T> assistKey(Method method, Key<T> key, Errors errors) throws ErrorsException {
    if (key.getAnnotationType() == null) {
      return Key.get(key.getTypeLiteral(), DEFAULT_ANNOTATION);
    } else if (key.getAnnotationType() == Assisted.class) {
      return key;
    } else {
      errors.withSource(method).addMessage(
          "Only @Assisted is allowed for factory parameters, but found @%s",
          key.getAnnotationType());
      throw errors.toException();
    }
  }

  /**
   * At injector-creation time, we initialize the invocation handler. At this time we make sure
   * all factory methods will be able to build the target types.
   */
  @Inject @Toolable
  void initialize(Injector injector) {
    if (this.injector != null) {
      throw new ConfigurationException(ImmutableList.of(new Message(FactoryProvider2.class,
          "Factories.create() factories may only be used in one Injector!")));
    }

    this.injector = injector;

    for (Map.Entry<Method, AssistData> entry : assistDataByMethod.entrySet()) {
      Method method = entry.getKey();
      AssistData data = entry.getValue();
      Object[] args;
      if(!data.optimized) {
        args = new Object[method.getParameterTypes().length];
        Arrays.fill(args, "dummy object for validating Factories");
      } else {
        args = null; // won't be used -- instead will bind to data.providers.
      }
      getBindingFromNewInjector(method, args, data); // throws if the binding isn't properly configured
    }
  }

  /**
   * Creates a child injector that binds the args, and returns the binding for the method's result.
   */
  public Binding<?> getBindingFromNewInjector(final Method method, final Object[] args, final AssistData data) {
    checkState(injector != null,
        "Factories.create() factories cannot be used until they're initialized by Guice.");

    final Key<?> returnType = data.returnType;

    // We ignore any pre-existing binding annotation.
    final Key<?> assistedReturnType = Key.get(returnType.getTypeLiteral(), Assisted.class);

    Module assistedModule = new AbstractModule() {
      @SuppressWarnings("unchecked") // raw keys are necessary for the args array and return value
      protected void configure() {
        Binder binder = binder().withSource(method);

        int p = 0;
        if(!data.optimized) {
          for (Key<?> paramKey : data.paramTypes) {
            // Wrap in a Provider to cover null, and to prevent Guice from injecting the parameter
            binder.bind((Key) paramKey).toProvider(Providers.of(args[p++]));
          }
        } else {
          for (Key<?> paramKey : data.paramTypes) {
            // Bind to our ThreadLocalProviders.
            binder.bind((Key) paramKey).toProvider(data.providers.get(p++));
          }
        }

                
        Constructor<?> constructor = data.constructor;
        // If the injector already has a binding for the return type, don't
        // bother binding to a specific constructor. Otherwise, there could be
        // bugs where an implicit binding isn't used (or an explicitly forwarded
        // binding isn't used)
        if (injector.getExistingBinding(returnType) != null) {
          constructor = null;
        }
        
        TypeLiteral<?> implementation = collector.getBindings().get(returnType);
        if (implementation != null) {
          if(constructor == null) {
            binder.bind(assistedReturnType).to((TypeLiteral)implementation);
          } else {
            binder.bind(assistedReturnType).toConstructor((Constructor)constructor, (TypeLiteral)implementation);
          }
        } else {
          // no implementation, but need to bind from assisted key to actual key.
          if(constructor == null) {
            binder.bind(assistedReturnType).to((Key)returnType);
          } else {
            binder.bind(assistedReturnType).toConstructor((Constructor)constructor, (TypeLiteral)returnType.getTypeLiteral());
          }
        }
      }
    };

    Injector forCreate = injector.createChildInjector(assistedModule);
    Binding binding = forCreate.getBinding(assistedReturnType);
    // If we have providers cached in data, cache the binding for future optimizations.
    if(data.optimized) {
      data.cachedBinding = binding; 
    }
    return binding;
  }

  /**
   * When a factory method is invoked, we create a child injector that binds all parameters, then
   * use that to get an instance of the return type.
   */
  public Object invoke(Object proxy, final Method method, final Object[] args) throws Throwable {
    if (method.getDeclaringClass() == Object.class) {
      return method.invoke(this, args);
    }

    AssistData data = assistDataByMethod.get(method);
    Provider<?> provider;
    if(data.cachedBinding != null) { // Try to get optimized form...
      provider = data.cachedBinding.getProvider();
    } else {
      provider = getBindingFromNewInjector(method, args, data).getProvider();
    }
    try {
      int p = 0;
      for(ThreadLocalProvider tlp : data.providers) {
        tlp.set(args[p++]);
      }
      return provider.get();
    } catch (ProvisionException e) {
      // if this is an exception declared by the factory method, throw it as-is
      if (e.getErrorMessages().size() == 1) {
        Message onlyError = getOnlyElement(e.getErrorMessages());
        Throwable cause = onlyError.getCause();
        if (cause != null && canRethrow(method, cause)) {
          throw cause;
        }
      }
      throw e;
    } finally {
      for(ThreadLocalProvider tlp : data.providers) {
        tlp.remove();
      }
    }
  }

  @Override public String toString() {
    return factory.getClass().getInterfaces()[0].getName();
  }

  @Override public boolean equals(Object o) {
    return o == this || o == factory;
  }

  /** Returns true if {@code thrown} can be thrown by {@code invoked} without wrapping. */
  static boolean canRethrow(Method invoked, Throwable thrown) {
    if (thrown instanceof Error || thrown instanceof RuntimeException) {
      return true;
    }

    for (Class<?> declared : invoked.getExceptionTypes()) {
      if (declared.isInstance(thrown)) {
        return true;
      }
    }

    return false;
  }
  
  // not <T> because we'll never know and this is easier than suppressing warnings.
  private static class ThreadLocalProvider extends ThreadLocal<Object> implements Provider<Object> {    
    @Override
    protected Object initialValue() {
      throw new IllegalStateException(
          "Cannot use optimized @Assisted provider outside the scope of the constructor."
              + " (This should never happen.  If it does, please report it.)");
    }
  }
}
