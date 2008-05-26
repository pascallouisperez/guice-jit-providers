// Copyright 2007 Google Inc. All Rights Reserved.

package com.google.inject;

import static com.google.inject.Asserts.assertContains;
import junit.framework.TestCase;

/**
 * Tests the error messages produced by Guice.
 *
 * @author Kevin Bourrillion
 */
public class ErrorMessagesTest extends TestCase {

  private class InnerClass {}

  public void testInjectInnerClass() throws Exception {
    Injector injector = Guice.createInjector();
    try {
      injector.getInstance(InnerClass.class);
      fail();
    } catch (Exception expected) {
      // TODO(kevinb): why does the source come out as unknown??
      assertContains(expected.getMessage(), "Injecting into inner classes is not supported.");
    }
  }

  public void testInjectLocalClass() throws Exception {
    class LocalClass {}

    Injector injector = Guice.createInjector();
    try {
      injector.getInstance(LocalClass.class);
      fail();
    } catch (Exception expected) {
      // TODO(kevinb): why does the source come out as unknown??
      assertContains(expected.getMessage(), "Injecting into inner classes is not supported.");
    }
  }

  public void testExplicitBindingOfAnAbstractClass() {
    try {
      Guice.createInjector(new AbstractModule() {
        protected void configure() {
          bind(AbstractClass.class);
        }
      });
      fail();
    } catch(CreationException expected) {
      assertContains(expected.getMessage(), "Injecting into abstract types is not supported.");
    }
  }
  
  public void testGetInstanceOfAnAbstractClass() {
    Injector injector = Guice.createInjector();
    try {
      injector.getInstance(AbstractClass.class);
      fail();
    } catch(ConfigurationException expected) {
      assertContains(expected.getMessage(), "Injecting into abstract types is not supported.");
    }
  }

  static abstract class AbstractClass {
    @Inject AbstractClass() { }
  }

  // TODO(kevinb): many many more

}
