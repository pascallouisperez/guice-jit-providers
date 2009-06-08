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

package com.google.inject.spi;

import com.google.inject.AbstractModule;
import static com.google.inject.Asserts.assertContains;
import com.google.inject.Binding;
import com.google.inject.Guice;
import com.google.inject.Inject;
import com.google.inject.Injector;
import com.google.inject.Key;
import com.google.inject.Module;
import com.google.inject.Provider;
import com.google.inject.Scope;
import com.google.inject.Scopes;
import com.google.inject.Singleton;
import com.google.inject.Stage;
import com.google.inject.internal.ImmutableSet;
import com.google.inject.internal.Lists;
import com.google.inject.name.Names;
import java.lang.reflect.Constructor;
import java.util.Collections;
import java.util.Comparator;
import java.util.Iterator;
import java.util.List;
import java.util.logging.Logger;
import junit.framework.TestCase;

/**
 * @author jessewilson@google.com (Jesse Wilson)
 */
public class SpiBindingsTest extends TestCase {

  public void testBindConstant() {
    checkInjector(
        new AbstractModule() {
          protected void configure() {
            bindConstant().annotatedWith(Names.named("one")).to(1);
          }
        },

        new FailingElementVisitor() {
          @Override public <T> Void visit(Binding<T> binding) {
            assertTrue(binding instanceof InstanceBinding);
            assertEquals(Key.get(Integer.class, Names.named("one")), binding.getKey());
            return null;
          }
        }
    );
  }

  public void testToInstanceBinding() {
    checkInjector(
        new AbstractModule() {
          protected void configure() {
            bind(String.class).toInstance("A");
          }
        },

        new FailingElementVisitor() {
          @Override public <T> Void visit(Binding<T> binding) {
            assertTrue(binding instanceof InstanceBinding);
            assertContains(binding.getSource().toString(), "SpiBindingsTest.java");
            assertEquals(Key.get(String.class), binding.getKey());
            binding.acceptTargetVisitor(new FailingTargetVisitor<T>() {
              @Override public Void visit(InstanceBinding<? extends T> binding) {
                assertEquals("A", binding.getInstance());
                return null;
              }
            });
            binding.acceptScopingVisitor(new FailingBindingScopingVisitor() {
              public Void visitEagerSingleton() {
                return null;
              }
            });
            return null;
          }
        }
    );
  }

  public void testToProviderBinding() {
    final Provider<String> stringProvider = new StringProvider();

    checkInjector(
        new AbstractModule() {
          protected void configure() {
            bind(String.class).toProvider(stringProvider);
          }
        },

        new FailingElementVisitor() {
          @Override public <T> Void visit(Binding<T> binding) {
            assertTrue(binding instanceof ProviderInstanceBinding);
            assertContains(binding.getSource().toString(), "SpiBindingsTest.java");
            assertEquals(Key.get(String.class), binding.getKey());
            binding.acceptTargetVisitor(new FailingTargetVisitor<T>() {
              @Override public Void visit(
                  ProviderInstanceBinding<? extends T> binding) {
                assertSame(stringProvider, binding.getProviderInstance());
                return null;
              }
            });
            return null;
          }
        }
    );
  }

  public void testToProviderKeyBinding() {
    checkInjector(
        new AbstractModule() {
          protected void configure() {
            bind(String.class).toProvider(StringProvider.class);
          }
        },

        new FailingElementVisitor() {
          @Override public <T> Void visit(Binding<T> binding) {
            assertTrue(binding instanceof ProviderKeyBinding);
            assertContains(binding.getSource().toString(), "SpiBindingsTest.java");
            assertEquals(Key.get(String.class), binding.getKey());
            binding.acceptTargetVisitor(new FailingTargetVisitor<T>() {
              @Override public Void visit(ProviderKeyBinding<? extends T> binding) {
                assertEquals(Key.get(StringProvider.class), binding.getProviderKey());
                return null;
              }
            });
            return null;
          }
        }
    );
  }

  public void testToKeyBinding() {
    final Key<String> aKey = Key.get(String.class, Names.named("a"));
    final Key<String> bKey = Key.get(String.class, Names.named("b"));

    checkInjector(
        new AbstractModule() {
          protected void configure() {
            bind(aKey).to(bKey);
            bind(bKey).toInstance("B");
          }
        },

        new FailingElementVisitor() {
          @Override public <T> Void visit(Binding<T> binding) {
            assertTrue(binding instanceof LinkedKeyBinding);
            assertContains(binding.getSource().toString(), "SpiBindingsTest.java");
            assertEquals(aKey, binding.getKey());
            binding.acceptTargetVisitor(new FailingTargetVisitor<T>() {
              @Override public Void visit(LinkedKeyBinding<? extends T> binding) {
                assertEquals(bKey, binding.getLinkedKey());
                return null;
              }
            });
            return null;
          }
        },

        new FailingElementVisitor() {
          @Override public <T> Void visit(Binding<T> binding) {
            assertEquals(bKey, binding.getKey());
            return null;
          }
        }
    );
  }

  public void testToConstructorBinding() {
    checkInjector(
        new AbstractModule() {
          protected void configure() {
            bind(D.class);
          }
        },

        new FailingElementVisitor() {
          @Override public <T> Void visit(Binding<T> binding) {
            assertTrue(binding instanceof ConstructorBinding);
            assertContains(binding.getSource().toString(), "SpiBindingsTest.java");
            assertEquals(Key.get(D.class), binding.getKey());
            binding.acceptTargetVisitor(new FailingTargetVisitor<T>() {
              @Override public Void visit(ConstructorBinding<? extends T> binding) {
                Constructor<?> expected = D.class.getDeclaredConstructors()[0];
                assertEquals(expected, binding.getConstructor().getMember());
                assertEquals(ImmutableSet.<InjectionPoint>of(), binding.getInjectableMembers());
                return null;
              }
            });
            return null;
          }
        }
    );
  }

  public void testConstantBinding() {
    checkInjector(
        new AbstractModule() {
          protected void configure() {
            bindConstant().annotatedWith(Names.named("one")).to(1);
          }
        },

        new FailingElementVisitor() {
          @Override public <T> Void visit(Binding<T> binding) {
            assertTrue(binding instanceof InstanceBinding);
            assertContains(binding.getSource().toString(), "SpiBindingsTest.java");
            assertEquals(Key.get(Integer.class, Names.named("one")), binding.getKey());
            binding.acceptTargetVisitor(new FailingTargetVisitor<T>() {
              @Override public Void visit(InstanceBinding<? extends T> binding) {
                assertEquals(1, binding.getInstance());
                return null;
              }
            });
            return null;
          }
        }
    );
  }

  public void testConvertedConstantBinding() {
    Injector injector = Guice.createInjector(new AbstractModule() {
      protected void configure() {
        bindConstant().annotatedWith(Names.named("one")).to("1");
      }
    });

    Binding<Integer> binding = injector.getBinding(Key.get(Integer.class, Names.named("one")));
    assertEquals(Key.get(Integer.class, Names.named("one")), binding.getKey());
    assertContains(binding.getSource().toString(), "SpiBindingsTest.java");
    assertTrue(binding instanceof ConvertedConstantBinding);
    binding.acceptTargetVisitor(new FailingTargetVisitor<Integer>() {
      @Override public Void visit(
          ConvertedConstantBinding<? extends Integer> binding) {
        assertEquals((Integer) 1, binding.getValue());
        assertEquals(Key.get(String.class, Names.named("one")), binding.getSourceKey());
        return null;
      }
    });
  }

  public void testProviderBinding() {
    Injector injector = Guice.createInjector(new AbstractModule() {
      protected void configure() {
        bind(String.class).toInstance("A");
      }
    });

    Key<Provider<String>> providerOfStringKey = new Key<Provider<String>>() {};
    Binding<Provider<String>> binding = injector.getBinding(providerOfStringKey);
    assertEquals(providerOfStringKey, binding.getKey());
    assertContains(binding.getSource().toString(), "SpiBindingsTest.java");
    assertTrue(binding instanceof ProviderBinding);
    binding.acceptTargetVisitor(new FailingTargetVisitor<Provider<String>>() {
      @Override public Void visit(
          ProviderBinding<? extends Provider<String>> binding) {
        assertEquals(Key.get(String.class), binding.getProvidedKey());
        return null;
      }
    });
  }

  public void testScopes() {
    checkInjector(
        new AbstractModule() {
          protected void configure() {
            bind(String.class).annotatedWith(Names.named("a"))
                .toProvider(StringProvider.class).in(Singleton.class);
            bind(String.class).annotatedWith(Names.named("b"))
                .toProvider(StringProvider.class).in(Scopes.SINGLETON);
            bind(String.class).annotatedWith(Names.named("c"))
                .toProvider(StringProvider.class).asEagerSingleton();
            bind(String.class).annotatedWith(Names.named("d"))
                .toProvider(StringProvider.class);
          }
        },

        new FailingElementVisitor() {
          @Override public <T> Void visit(Binding<T> command) {
            assertEquals(Key.get(String.class, Names.named("a")), command.getKey());
            command.acceptScopingVisitor(new FailingBindingScopingVisitor() {
              @Override public Void visitScope(Scope scope) {
                // even though we bound with an annotation, the injector always uses instances
                assertSame(Scopes.SINGLETON, scope);
                return null;
              }
            });
            return null;
          }
        },

        new FailingElementVisitor() {
          @Override public <T> Void visit(Binding<T> command) {
            assertEquals(Key.get(String.class, Names.named("b")), command.getKey());
            command.acceptScopingVisitor(new FailingBindingScopingVisitor() {
              @Override public Void visitScope(Scope scope) {
                assertSame(Scopes.SINGLETON, scope);
                return null;
              }
            });
            return null;
          }
        },

        new FailingElementVisitor() {
          @Override public <T> Void visit(Binding<T> command) {
            assertEquals(Key.get(String.class, Names.named("c")), command.getKey());
            command.acceptScopingVisitor(new FailingBindingScopingVisitor() {
              @Override public Void visitEagerSingleton() {
                return null;
              }
            });
            return null;
          }
        },

        new FailingElementVisitor() {
          @Override public <T> Void visit(Binding<T> command) {
            assertEquals(Key.get(String.class, Names.named("d")), command.getKey());
            command.acceptScopingVisitor(new FailingBindingScopingVisitor() {
              @Override public Void visitNoScoping() {
                return null;
              }
            });
            return null;
          }
        }
    );
  }

  public void checkInjector(Module module, ElementVisitor<?>... visitors) {
    Injector injector = Guice.createInjector(module);

    List<Binding<?>> bindings = Lists.newArrayList(injector.getBindings().values());
    for (Iterator<Binding<?>> i = bindings.iterator(); i.hasNext(); ) {
      if (BUILT_IN_BINDINGS.contains(i.next().getKey())) {
        i.remove();
      }
    }

    Collections.sort(bindings, orderByKey);

    assertEquals(bindings.size(), visitors.length);

    for (int i = 0; i < visitors.length; i++) {
      ElementVisitor<?> visitor = visitors[i];
      Binding<?> binding = bindings.get(i);
      binding.acceptVisitor(visitor);
    }
  }

  private final ImmutableSet<Key<?>> BUILT_IN_BINDINGS = ImmutableSet.of(
      Key.get(Injector.class), Key.get(Stage.class), Key.get(Logger.class));

  private final Comparator<Binding<?>> orderByKey = new Comparator<Binding<?>>() {
    public int compare(Binding<?> a, Binding<?> b) {
      return a.getKey().toString().compareTo(b.getKey().toString());
    }
  };

  private static class StringProvider implements Provider<String> {
    public String get() {
      return "A";
    }
  }

  private static class C { }

  private static class D extends C {
    @Inject public D(Injector unused) { }
  }
}
