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

    Factory<String> instance1 = injector.getInstance(key);
    Factory<String> instance2 = injector.getInstance(key);

    // correctness
    assertEquals(String.class, instance1.klass);
    assertEquals(String.class, instance2.klass);

    // scoping
    assertTrue("scoping is incorrect", instance1 == instance2);
  }

  public void testImplicitJitProviderBinding() {
    Injector injector = new InjectorBuilder().build();

    assertEquals(
        String.class,
        injector.getInstance(Key.get(new TypeLiteral<AnnotatedFactory<String>>() {})).klass);
  }

  static class Factory<T> {
    protected final Class<T> klass;
    Factory(Class<T> klass) {
      this.klass = klass;
    }
  }

//  @ProvidedJustInTimeBy(FactoryJitProvider.class)
  static class AnnotatedFactory<T> extends Factory<T> {
    AnnotatedFactory(Class<T> klass) {
      super(klass);
    }
  }

  static class FactoryJitProvider implements JitProvider<Factory<?>> {
    public boolean canProvide(Key<?> key) {
      return key.getTypeLiteral().getRawType().equals(Factory.class);
    }
    @SuppressWarnings("unchecked")
    public Factory<?> get(Key<?> key) {
      TypeLiteral<?> typeLiteral = key.getTypeLiteral();
      return new AnnotatedFactory(
          (Class) ((ParameterizedType) typeLiteral.getType()).getActualTypeArguments()[0]);
    }
  }

}
