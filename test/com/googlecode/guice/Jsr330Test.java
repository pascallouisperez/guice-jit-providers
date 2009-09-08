/**
 * Copyright (C) 2009 Google Inc.
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

package com.googlecode.guice;

import com.google.inject.AbstractModule;
import static com.google.inject.Asserts.assertContains;
import com.google.inject.CreationException;
import com.google.inject.Guice;
import com.google.inject.Injector;
import com.google.inject.Key;
import com.google.inject.Scope;
import com.google.inject.Scopes;
import com.google.inject.Stage;
import com.google.inject.util.Jsr330;
import java.lang.annotation.Annotation;
import java.lang.annotation.Retention;
import static java.lang.annotation.RetentionPolicy.RUNTIME;
import javax.inject.Inject;
import javax.inject.Named;
import javax.inject.Provider;
import javax.inject.Qualifier;
import javax.inject.Singleton;
import junit.framework.TestCase;

public class Jsr330Test extends TestCase {

  private final B b = new B();
  private final C c = new C();
  private final D d = new D();
  private final E e = new E();

  @Override protected void setUp() throws Exception {
    J.nextInstanceId = 0;
    K.nextInstanceId = 0;
  }

  public void testInject() {
    Injector injector = Guice.createInjector(new AbstractModule() {
      protected void configure() {
        bind(B.class).toInstance(b);
        bind(C.class).toInstance(c);
        bind(D.class).toInstance(d);
        bind(E.class).toInstance(e);
        bind(A.class);
      }
    });

    A a = injector.getInstance(A.class);
    assertSame(b, a.b);
    assertSame(c, a.c);
    assertSame(d, a.d);
    assertSame(e, a.e);
  }

  public void testQualifiedInject() {
    Injector injector = Guice.createInjector(new AbstractModule() {
      protected void configure() {
        bind(B.class).annotatedWith(Jsr330.named("jodie")).toInstance(b);
        bind(C.class).annotatedWith(Red.class).toInstance(c);
        bind(D.class).annotatedWith(RED).toInstance(d);
        bind(E.class).annotatedWith(Jsr330.named("jesse")).toInstance(e);
        bind(F.class);
      }
    });

    F f = injector.getInstance(F.class);
    assertSame(b, f.b);
    assertSame(c, f.c);
    assertSame(d, f.d);
    assertSame(e, f.e);
  }

  public void testProviderInject() {
    Injector injector = Guice.createInjector(new AbstractModule() {
      protected void configure() {
        bind(B.class).annotatedWith(Jsr330.named("jodie")).toInstance(b);
        bind(C.class).toInstance(c);
        bind(D.class).annotatedWith(RED).toInstance(d);
        bind(E.class).toInstance(e);
        bind(G.class);
      }
    });

    G g = injector.getInstance(G.class);
    assertSame(b, g.bProvider.get());
    assertSame(c, g.cProvider.get());
    assertSame(d, g.dProvider.get());
    assertSame(e, g.eProvider.get());
  }

  public void testScopeAnnotation() {
    final TestScope scope = new TestScope();

    Injector injector = Guice.createInjector(new AbstractModule() {
      protected void configure() {
        bind(B.class).in(scope);
        bind(C.class).in(TestScoped.class);
        bindScope(TestScoped.class, scope);
      }
    });

    B b = injector.getInstance(B.class);
    assertSame(b, injector.getInstance(B.class));
    assertSame(b, injector.getInstance(B.class));

    C c = injector.getInstance(C.class);
    assertSame(c, injector.getInstance(C.class));
    assertSame(c, injector.getInstance(C.class));

    H h = injector.getInstance(H.class);
    assertSame(h, injector.getInstance(H.class));
    assertSame(h, injector.getInstance(H.class));

    scope.reset();

    assertNotSame(b, injector.getInstance(B.class));
    assertNotSame(c, injector.getInstance(C.class));
    assertNotSame(h, injector.getInstance(H.class));
  }
  
  public void testSingleton() {
    Injector injector = Guice.createInjector(new AbstractModule() {
      protected void configure() {
        bind(B.class).in(Singleton.class);
      }
    });

    B b = injector.getInstance(B.class);
    assertSame(b, injector.getInstance(B.class));
    assertSame(b, injector.getInstance(B.class));

    J j = injector.getInstance(J.class);
    assertSame(j, injector.getInstance(J.class));
    assertSame(j, injector.getInstance(J.class));
  }

  public void testEagerSingleton() {
    Guice.createInjector(Stage.PRODUCTION, new AbstractModule() {
      protected void configure() {
        bind(J.class);
        bind(K.class).in(Singleton.class);
      }
    });

    assertEquals(1, J.nextInstanceId);
    assertEquals(1, K.nextInstanceId);
  }
  
  public void testScopesIsSingleton() {
    Injector injector = Guice.createInjector(new AbstractModule() {
      protected void configure() {
        bind(J.class);
        bind(K.class).in(Singleton.class);
      }
    });

    assertTrue(Scopes.isSingleton(injector.getBinding(J.class)));
    assertTrue(Scopes.isSingleton(injector.getBinding(K.class)));
  }

  public void testInjectingFinalFieldsIsForbidden() {
    try {
      Guice.createInjector(new AbstractModule() {
        protected void configure() {
          bind(L.class);
        }
      });
      fail();
    } catch (CreationException expected) {
      assertContains(expected.getMessage(),
          "1) Injected field " + L.class.getName() + ".b cannot be final.");
    }
  }

  public void testInjectingAbstractMethodsIsForbidden() {
    try {
      Guice.createInjector(new AbstractModule() {
        protected void configure() {
          bind(M.class);
        }
      });
      fail();
    } catch (CreationException expected) {
      assertContains(expected.getMessage(),
          "1) Injected method " + AbstractM.class.getName() + ".setB() cannot be abstract.");
    }
  }

  public void testInjectingMethodsWithTypeParametersIsForbidden() {
    try {
      Guice.createInjector(new AbstractModule() {
        protected void configure() {
          bind(N.class);
        }
      });
      fail();
    } catch (CreationException expected) {
      assertContains(expected.getMessage(), "1) Injected method " + N.class.getName()
          + ".setB() cannot declare type parameters of its own.");
    }
  }

  public void testInjectingMethodsWithNonVoidReturnTypes() {
    try {
      Guice.createInjector(new AbstractModule() {
        protected void configure() {
          bind(P.class);
        }
      });
      fail();
    } catch (CreationException expected) {
      assertContains(expected.getMessage(),
          "1) Injected method " + P.class.getName() + ".setB() must return void.");
    }
  }

  static class A {
    final B b;
    @Inject C c;
    D d;
    E e;

    @Inject A(B b) {
      this.b = b;
    }

    @Inject void injectD(D d, E e) {
      this.d = d;
      this.e = e;
    }
  }

  static class B {}
  static class C {}
  static class D {}
  static class E {}

  static class F {
    final B b;
    @Inject @Red C c;
    D d;
    E e;

    @Inject F(@Named("jodie") B b) {
      this.b = b;
    }

    @Inject void injectD(@Red D d, @Named("jesse") E e) {
      this.d = d;
      this.e = e;
    }
  }

  @Qualifier @Retention(RUNTIME)
  @interface Red {}

  public static final Red RED = new Red() {
    public Class<? extends Annotation> annotationType() {
      return Red.class;
    }

    @Override public boolean equals(Object obj) {
      return obj instanceof Red;
    }

    @Override public int hashCode() {
      return 0;
    }
  };

  static class G {
    final Provider<B> bProvider;
    @Inject Provider<C> cProvider;
    Provider<D> dProvider;
    Provider<E> eProvider;

    @Inject G(@Named("jodie") Provider<B> bProvider) {
      this.bProvider = bProvider;
    }

    @Inject void injectD(@Red Provider<D> dProvider, Provider<E> eProvider) {
      this.dProvider = dProvider;
      this.eProvider = eProvider;
    }
  }

  @javax.inject.Scope @Retention(RUNTIME)
  @interface TestScoped {}

  static class TestScope implements Scope {
    private int now = 0;

    public <T> com.google.inject.Provider<T> scope(Key<T> key,
        final com.google.inject.Provider<T> unscoped) {
      return new com.google.inject.Provider<T>() {
        private T value;
        private int snapshotTime = -1;

        public T get() {
          if (snapshotTime != now) {
            value = unscoped.get();
            snapshotTime = now;
          }
          return value;
        }
      };
    }

    public void reset() {
      now++;
    }
  }

  @TestScoped
  static class H {}

  @Singleton
  static class J {
    static int nextInstanceId = 0;
    int instanceId = nextInstanceId++;
  }

  static class K {
    static int nextInstanceId = 0;
    int instanceId = nextInstanceId++;
  }

  static class L {
    @Inject final B b = null;
  }

  static abstract class AbstractM {
    @Inject abstract void setB(B b);
  }

  static class M extends AbstractM {
    void setB(B b) {}
  }

  static class N {
    @Inject <T> void setB(B b) {}
  }

  static class P {
    @Inject B setB(B b) {
      return b;
    }
  }
}
