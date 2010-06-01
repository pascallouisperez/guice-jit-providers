package com.google.inject;

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
        bindJitProvider(FactoryJitProvider.class).in(Singleton.class);
      }
    });
  }

  public void testExplicitJitProviderBindingByClassScopingByInstance() {
    assertExplicitJitProviderBinding(new AbstractModule() {
      @Override
      protected void configure() {
        bindJitProvider(FactoryJitProvider.class).in(Scopes.SINGLETON);
      }
    });
  }

  public void testExplicitJitProviderBindingByInstanceScopingByAnnotation() {
    assertExplicitJitProviderBinding(new AbstractModule() {
      @Override
      protected void configure() {
        bindJitProvider(new FactoryJitProvider()).in(Singleton.class);
      }
    });
  }

  public void testExplicitJitProviderBindingByInstanceScopingByInstance() {
    assertExplicitJitProviderBinding(new AbstractModule() {
      @Override
      protected void configure() {
        bindJitProvider(new FactoryJitProvider()).in(Scopes.SINGLETON);
      }
    });
  }

  private void assertExplicitJitProviderBinding(AbstractModule module) {
    Injector injector = new InjectorBuilder().addModules(module).build();
    Key<Factory<String>> key = Key.get(new TypeLiteral<Factory<String>>() {});

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
        bindJitProvider(FactoryJitProvider.class).in(Singleton.class);
      }
    }).build();
    Key<FactoryInterface<String>> key = Key.get(new TypeLiteral<FactoryInterface<String>>() {});

    check(injector, key);
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
    public boolean canProvide(Key<?> key) {
      return FactoryInterface.class.isAssignableFrom(key.getTypeLiteral().getRawType());
    }
    @SuppressWarnings("unchecked")
    public Factory<?> get(Key<Factory<?>> key) {
      TypeLiteral<?> typeLiteral = key.getTypeLiteral();
      ParameterizedType parametrizedType = (ParameterizedType) typeLiteral.getType();
      Class klass = (Class) parametrizedType.getActualTypeArguments()[0];
      return new AnnotatedFactory(klass);
    }
  }

}
