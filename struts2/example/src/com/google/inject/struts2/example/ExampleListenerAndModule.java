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

package com.google.inject.struts2.example;

import com.google.inject.Guice;
import com.google.inject.Injector;
import com.google.inject.Singleton;
import com.google.inject.servlet.GuiceServletContextListener;
import com.google.inject.servlet.ServletModule;
import org.apache.struts2.dispatcher.FilterDispatcher;

/**
 * Example application module.
 *
 * @author crazybob@google.com (Bob Lee)
 */
public class ExampleListenerAndModule extends GuiceServletContextListener {

  protected Injector getInjector() {
    return Guice.createInjector(new ServletModule() {
      @Override
      protected void configureServlets() {
        bind(Service.class).to(ServiceImpl.class);

        bind(FilterDispatcher.class).in(Singleton.class);
        filter("/*").through(org.apache.struts2.dispatcher.FilterDispatcher.class);
      }
    });
  }
}
