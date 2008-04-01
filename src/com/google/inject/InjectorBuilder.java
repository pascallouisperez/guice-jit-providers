/**
 * Copyright (C) 2006 Google Inc.
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

import static com.google.inject.Scopes.SINGLETON;
import com.google.inject.commands.Command;
import com.google.inject.commands.CommandRecorder;
import com.google.inject.commands.FutureInjector;
import com.google.inject.internal.Objects;
import com.google.inject.internal.Stopwatch;
import com.google.inject.spi.Message;
import com.google.inject.spi.SourceProviders;

import java.lang.reflect.Member;
import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedList;
import java.util.List;
import java.util.logging.Logger;

/**
 * Builds a dependency injection {@link Injector}.
 *
 * @author crazybob@google.com (Bob Lee)
 * @author jessewilson@google.com (Jesse Wilson)
 */
class InjectorBuilder {

  private final Stopwatch stopwatch = new Stopwatch();

  private Injector parent;
  private Stage stage;
  private final List<Module> modules = new LinkedList<Module>();

  private final ConfigurationErrorHandler configurationErrorHandler
      = new ConfigurationErrorHandler();

  private InjectorImpl injector;

  private final FutureInjector futureInjector = new FutureInjector();
  private final List<Command> commands = new ArrayList<Command>();

  private BindCommandProcessor bindCommandProcesor;
  private RequestStaticInjectionCommandProcessor requestStaticInjectionCommandProcessor;

  /**
   * @param stage we're running in. If the stage is {@link Stage#PRODUCTION},
   *  we will eagerly load singletons.
   */
  InjectorBuilder stage(Stage stage) {
    this.stage = stage;
    return this;
  }

  InjectorBuilder parentInjector(Injector parent) {
    this.parent = parent;
    return this;
  }

  InjectorBuilder addModules(Iterable<? extends Module> modules) {
    for (Module module : modules) {
      this.modules.add(module);
    }
    return this;
  }

  Injector build() {
    if (injector != null) {
      throw new AssertionError("Already built, builders are not reusable.");
    }

    injector = new InjectorImpl(parent);
    injector.setErrorHandler(configurationErrorHandler);

    modules.add(0, new BuiltInModule(injector, stage));

    CommandRecorder commandRecorder = new CommandRecorder(futureInjector);
    commandRecorder.setCurrentStage(stage);
    commands.addAll(commandRecorder.recordCommands(modules));

    buildCoreInjector();

    validate();

    injector.setErrorHandler(RuntimeErrorHandler.INSTANCE);

    // If we're in the tool stage, stop here. Don't eagerly inject or load
    // anything.
    if (stage == Stage.TOOL) {
      // TODO: Wrap this and prevent usage of anything besides getBindings().
      return injector;
    }

    fulfillInjectionRequests();

    if (!commands.isEmpty()) {
      throw new AssertionError("Failed to execute " + commands);
    }

    return injector;
  }

  /**
   * Builds the injector.
   */
  private void buildCoreInjector() {
    new ErrorsCommandProcessor()
        .processCommands(commands, configurationErrorHandler);

    BindInterceptorCommandProcessor bindInterceptorCommandProcessor
        = new BindInterceptorCommandProcessor();
    bindInterceptorCommandProcessor.processCommands(commands, configurationErrorHandler);
    injector.constructionProxyFactory = bindInterceptorCommandProcessor.createProxyFactory();
    stopwatch.resetAndLog("Interceptors creation");

    new ScopesCommandProcessor(injector.scopes)
        .processCommands(commands, configurationErrorHandler);
    stopwatch.resetAndLog("Scopes creation");

    new ConvertToTypesCommandProcessor(injector.converters)
        .processCommands(commands, configurationErrorHandler);
    stopwatch.resetAndLog("Converters creation");

    bindCommandProcesor = new BindCommandProcessor(
        injector, injector.scopes, stage, injector.explicitBindings,
        injector.outstandingInjections);
    bindCommandProcesor.processCommands(commands, configurationErrorHandler);
    bindCommandProcesor.createUntargettedBindings();
    stopwatch.resetAndLog("Binding creation");

    injector.index();
    stopwatch.resetAndLog("Binding indexing");

    requestStaticInjectionCommandProcessor = new RequestStaticInjectionCommandProcessor();
    requestStaticInjectionCommandProcessor
        .processCommands(commands, configurationErrorHandler);
    stopwatch.resetAndLog("Static injection");
  }

  /**
   * Validate everything that we can validate now that the injector is ready
   * for use.
   */
  private void validate() {
    bindCommandProcesor.runCreationListeners(injector);
    stopwatch.resetAndLog("Validation");

    requestStaticInjectionCommandProcessor.validate(injector);
    stopwatch.resetAndLog("Static validation");

    injector.validateOustandingInjections();
    stopwatch.resetAndLog("Instance member validation");

    new GetProviderProcessor(injector)
        .processCommands(commands, configurationErrorHandler);
    stopwatch.resetAndLog("Provider verification");

    configurationErrorHandler.blowUpIfErrorsExist();
  }

  /**
   * Inject everything that can be injected. This uses runtime error handling.
   */
  private void fulfillInjectionRequests() {
    futureInjector.initialize(injector);

    requestStaticInjectionCommandProcessor.injectMembers(injector);
    stopwatch.resetAndLog("Static member injection");
    injector.fulfillOutstandingInjections();
    stopwatch.resetAndLog("Instance injection");

    bindCommandProcesor.createEagerSingletons(injector);
    stopwatch.resetAndLog("Preloading");
  }

  private static class BuiltInModule extends AbstractModule {
    final Injector injector;
    final Stage stage;

    private BuiltInModule(Injector injector, Stage stage) {
      this.injector = Objects.nonNull(injector, "injector");
      this.stage = Objects.nonNull(stage, "stage");
    }

    protected void configure() {
      SourceProviders.withDefault(SourceProviders.UNKNOWN_SOURCE, new Runnable() {
        public void run() {
          // TODO(jessewilson): use a real logger
          // bind(Logger.class).toInternalFactory(new LoggerFactory());
          bind(Logger.class).toInstance(Logger.getLogger(""));
          bind(Stage.class).toInstance(stage);
          bindScope(Singleton.class, SINGLETON);
          // Create default bindings.
          // We use toProvider() instead of toInstance() to avoid infinite recursion
          // in toString().
          bind(Injector.class).toProvider(new InjectorProvider(injector));

        }
      });
    }

    class InjectorProvider implements Provider<Injector> {
      final Injector injector;

      InjectorProvider(Injector injector) {
        this.injector = injector;
      }

      public Injector get() {
        return injector;
      }

      public String toString() {
        return "Provider<Injector>";
      }
    }

    class LoggerFactory implements InternalFactory<Logger> {
      public Logger get(InternalContext context, InjectionPoint<?> injectionPoint) {
        Member member = injectionPoint.getMember();
        return member == null
            ? Logger.getAnonymousLogger()
            : Logger.getLogger(member.getDeclaringClass().getName());
      }

      public String toString() {
        return "Provider<Logger>";
      }
    }
  }

  /**
   * Handles errors while the injector is being created.
   */
  private class ConfigurationErrorHandler extends AbstractErrorHandler {
    final Collection<Message> errorMessages = new ArrayList<Message>();

    public void handle(Object source, String message) {
      errorMessages.add(new Message(source, message));
    }

    void blowUpIfErrorsExist() {
      if (!errorMessages.isEmpty()) {
        throw new CreationException(errorMessages);
      }
    }
  }

  /**
   * Handles errors after the injector is created.
   */
  private static class RuntimeErrorHandler extends AbstractErrorHandler {
    static ErrorHandler INSTANCE = new RuntimeErrorHandler();

    public void handle(Object source, String message) {
      throw new ConfigurationException("Error at " + source + " " + message);
    }
  }
}
