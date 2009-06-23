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
package com.google.inject.servlet;

import com.google.inject.Injector;
import com.google.inject.Key;
import com.google.inject.Scopes;
import com.google.inject.internal.Iterators;
import java.io.IOException;
import java.util.Collections;
import java.util.Enumeration;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.atomic.AtomicReference;
import javax.servlet.Filter;
import javax.servlet.FilterConfig;
import javax.servlet.ServletContext;
import javax.servlet.ServletException;
import javax.servlet.ServletRequest;
import javax.servlet.ServletResponse;
import javax.servlet.http.HttpServletRequest;

/**
 * An internal representation of a filter definition against a particular URI pattern.
 *
 * @author dhanji@gmail.com (Dhanji R. Prasanna)
 */
class FilterDefinition {
  private final String pattern;
  private final Key<? extends Filter> filterKey;
  private final UriPatternMatcher patternMatcher;
  private final Map<String, String> initParams;

  private final AtomicReference<Filter> filter = new AtomicReference<Filter>();

  public FilterDefinition(String pattern, Key<? extends Filter> filterKey,
      UriPatternMatcher patternMatcher, Map<String, String> initParams) {
    this.pattern = pattern;
    this.filterKey = filterKey;
    this.patternMatcher = patternMatcher;
    this.initParams = Collections.unmodifiableMap(new HashMap<String, String>(initParams));
  }

  private boolean shouldFilter(String uri) {
    return patternMatcher.matches(uri);
  }

  public void init(final ServletContext servletContext, Injector injector,
      Set<Filter> initializedSoFar) throws ServletException {

    // This absolutely must be a singleton, and so is only initialized once.
    if (!Scopes.isSingleton(injector.getBinding(filterKey))) {
      throw new ServletException("Filters must be bound as singletons. "
        + filterKey + " was not bound in singleton scope.");
    }

    Filter filter = injector.getInstance(filterKey);
    this.filter.set(filter);

    // Only fire init() if this Singleton filter has not already appeared earlier
    // in the filter chain.
    if (initializedSoFar.contains(filter)) {
      return;
    }

    //initialize our filter with the configured context params and servlet context
    filter.init(new FilterConfig() {
      public String getFilterName() {
        return filterKey.toString();
      }

      public ServletContext getServletContext() {
        return servletContext;
      }

      public String getInitParameter(String s) {
        return initParams.get(s);
      }

      public Enumeration getInitParameterNames() {
        return Iterators.asEnumeration(initParams.keySet().iterator());
      }
    });

    initializedSoFar.add(filter);
  }

  public void destroy(Set<Filter> destroyedSoFar) {
    // filters are always singletons
    Filter reference = filter.get();
    
    // Do nothing if this Filter was invalid (usually due to not being scoped
    // properly), or was already destroyed. According to Servlet Spec: it is
    // "out of service", and does not need to be destroyed.
    // Also prevent duplicate destroys to the same singleton that may appear
    // more than once on the filter chain.
    if (null == reference || destroyedSoFar.contains(reference)) {
      return;
    }

    try {
      reference.destroy();
    } finally {
      destroyedSoFar.add(reference);
    }
  }

  public void doFilter(ServletRequest servletRequest,
      ServletResponse servletResponse, FilterChainInvocation filterChainInvocation)
      throws IOException, ServletException {

    final String path = ((HttpServletRequest) servletRequest).getServletPath();

    if (shouldFilter(path)) {
      filter.get()
            .doFilter(servletRequest, servletResponse, filterChainInvocation);

    } else {
      //otherwise proceed down chain anyway
      filterChainInvocation.doFilter(servletRequest, servletResponse);
    }
  }

  //VisibleForTesting
  Filter getFilter() {
    return filter.get();
  }
}
