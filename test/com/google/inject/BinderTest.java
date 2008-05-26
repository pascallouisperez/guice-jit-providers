/**
 * Copyright (C) 2007 Google Inc.
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

import static com.google.inject.Asserts.assertContains;
import com.google.inject.name.Names;
import junit.framework.TestCase;

import java.util.List;

/**
 * @author crazybob@google.com (Bob Lee)
 */
public class BinderTest extends TestCase {

  Provider<Foo> fooProvider;

  public void testProviderFromBinder() {
    Guice.createInjector(new Module() {
      public void configure(Binder binder) {
        fooProvider = binder.getProvider(Foo.class);

        try {
          fooProvider.get();
        } catch (IllegalStateException e) { /* expected */ }
      }
    });

    assertTrue(fooProvider.get() instanceof Foo);
  }

  static class Foo {}

  public void testInvalidProviderFromBinder() {
    try {
      Guice.createInjector(new Module() {
        public void configure(Binder binder) {
          binder.getProvider(Runnable.class);
        }
      });
    }
    catch (CreationException e) {
      assertEquals(1, e.getErrorMessages().size());
    }
  }

  public void testDanglingConstantBinding() {
    try {
      Guice.createInjector(new AbstractModule() {
        @Override public void configure() {
          bindConstant();
        }
      });
      fail();
    } catch (CreationException expected) {
      assertContains(expected.getMessage(), "Missing constant value.");
    }
  }

  public void testToStringOnBinderApi() {
    try {
      Guice.createInjector(new AbstractModule() {
        @Override public void configure() {
          assertEquals("Binder", binder().toString());
          assertEquals("Provider<java.lang.Integer>", getProvider(Integer.class).toString());
          assertEquals("Provider<java.util.List<java.lang.String>>",
              getProvider(Key.get(new TypeLiteral<List<String>>() {})).toString());

          assertEquals("AnnotatedBindingBuilder<java.lang.Integer>",
              bind(Integer.class).toString());
          assertEquals("LinkedBindingBuilder<java.lang.Integer>",
              bind(Integer.class).annotatedWith(Names.named("a")).toString());
          assertEquals("AnnotatedConstantBindingBuilder", bindConstant().toString());
          assertEquals("ConstantBindingBuilder",
              bindConstant().annotatedWith(Names.named("b")).toString());
        }
      });
      fail();
    } catch (CreationException ignored) {
    }
  }

//  public void testBindInterfaceWithoutImplementation() {
//    Guice.createInjector(new AbstractModule() {
//      protected void configure() {
//        bind(Runnable.class);
//      }
//    }).getInstance(Runnable.class);
//  }
}
