package com.google.inject;

import static com.google.inject.internal.Preconditions.checkNotNull;
import static java.lang.annotation.RetentionPolicy.RUNTIME;

import java.lang.annotation.Retention;
import java.lang.reflect.ParameterizedType;

import junit.framework.TestCase;

/**
 * Tests for {@link JitProvider} support.
 *
 * @author pascal@kaching.com (Pascal-Louis Perez)
 */
public class JitProvidersTest extends TestCase {

  public void testExplicitJitProviderBindingByClassScopingByAnnotation() {
    assertExplicitJitProviderBinding(new AbstractModule() {
      @Override
      protected void configure() {
        bindJit(new TypeLiteral<Factory<?>>() {})
            .toProvider(FactoryJitProvider.class)
            .in(Singleton.class);
      }
    });
  }

  public void testExplicitJitProviderBindingByClassScopingByInstance() {
    assertExplicitJitProviderBinding(new AbstractModule() {
      @Override
      protected void configure() {
        bindJit(new TypeLiteral<Factory<?>>() {})
            .toProvider(FactoryJitProvider.class)
            .in(Scopes.SINGLETON);
      }
    });
  }

  public void testExplicitJitProviderBindingByInstanceScopingByAnnotation() {
    assertExplicitJitProviderBinding(new AbstractModule() {
      @Override
      protected void configure() {
        bindJit(new TypeLiteral<Factory<?>>() {})
            .toProvider(FactoryJitProvider.class)
            .in(Singleton.class);
      }
    });
  }

  public void testExplicitJitProviderBindingByInstanceScopingByInstance() {
    assertExplicitJitProviderBinding(new AbstractModule() {
      @Override
      protected void configure() {
        bindJit(new TypeLiteral<Factory<?>>() {})
            .toProvider(new FactoryJitProvider())
            .in(Scopes.SINGLETON);
      }
    });
  }

  private void assertExplicitJitProviderBinding(AbstractModule module) {
    Injector injector = new InjectorBuilder().addModules(module).build();
    Key<Factory<String>> key = Key.get(new TypeLiteral<Factory<String>>() {});

    check(injector, key);
  }
  
  public void failingExplicitAnnotatedJitProviderBinding() throws Exception {
    Injector injector = new InjectorBuilder().addModules(new AbstractModule() {
      @Override
      protected void configure() {
        bindJit(new TypeLiteral<Factory<?>>() {})
            .annotatedWith(AnAnnotation.class)
            .toProvider(new FactoryJitProvider());
      }
    }).build();
    Key<Factory<String>> key = Key.get(
        new TypeLiteral<Factory<String>>() {}, AnAnnotation.class);

    check(injector, key);
  }

  public void testImplicitJitProviderBinding() {
    Injector injector = new InjectorBuilder().build();
    Key<AnnotatedFactory<String>> key = Key.get(new TypeLiteral<AnnotatedFactory<String>>() {});

    check(injector, key);
  }

  public void testImplicitJitProviderBindingInContainer() {
    assertEquals(
        String.class,
        new InjectorBuilder().build().getInstance(Container.class).factory.klass);
  }

  public void testJitProviderUsingKeyOfInterface() {
    Injector injector = new InjectorBuilder().addModules(new AbstractModule() {
      @Override
      protected void configure() {
        bindJit(new TypeLiteral<FactoryInterface<?>>() {})
            .toProvider(FactoryJitProvider.class)
            .in(Singleton.class);
      }
    }).build();
    Key<FactoryInterface<String>> key = Key.get(new TypeLiteral<FactoryInterface<String>>() {});

    check(injector, key);
  }

  public void testJitProviderRequiringInjection() {
    assertExplicitJitProviderBinding(new AbstractModule() {
      @Override
      protected void configure() {
        bindJit(new TypeLiteral<Factory<?>>() {})
            .toProvider(JitProviderRequiringInjection.class)
            .in(Singleton.class);
      }
    });
  }

  private void check(Injector injector, Key<? extends FactoryInterface<String>> key) {
    FactoryInterface<String> instance1 = injector.getInstance(key);
    FactoryInterface<String> instance2 = injector.getInstance(key);

    // correctness
    assertEquals(String.class, instance1.getKlass());
    assertEquals(String.class, instance2.getKlass());

    // scoping
    assertTrue("scoping is incorrect", instance1 == instance2);
  }

  interface FactoryInterface<T> {
    Class<T> getKlass();
  }

  static class Factory<T> implements FactoryInterface<T> {
    protected final Class<T> klass;
    Factory(Class<T> klass) {
      this.klass = klass;
    }
    public Class<T> getKlass() {
      return klass;
    }
  }

  @Singleton @ProvidedJustInTimeBy(FactoryJitProvider.class)
  static class AnnotatedFactory<T> extends Factory<T> {
    AnnotatedFactory(Class<T> klass) {
      super(klass);
    }
  }

  static class Container {
    private final AnnotatedFactory<String> factory;
    @Inject Container(AnnotatedFactory<String> factory) {
      this.factory = factory;
    }
  }

  static class FactoryJitProvider implements JitProvider<Factory<?>> {
    @Inject Injector injector;
    @SuppressWarnings("unchecked")
    public Factory<?> get(Key<Factory<?>> key) {
      checkNotNull(injector, "must have field injection in instantiated JIT Providers");
      TypeLiteral<?> typeLiteral = key.getTypeLiteral();
      ParameterizedType parametrizedType = (ParameterizedType) typeLiteral.getType();
      Class klass = (Class) parametrizedType.getActualTypeArguments()[0];
      return new AnnotatedFactory(klass);
    }
  }
  
  static class JitProviderRequiringInjection extends FactoryJitProvider {
    @Inject Injector injector;
  }

  @BindingAnnotation
  @Retention(RUNTIME)
  static @interface AnAnnotation {
  }

}
