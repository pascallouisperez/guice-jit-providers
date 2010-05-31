/**
 * Copyright (C) 2010 Google Inc.
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

import com.google.inject.spi.BindingScopingVisitor;
import com.google.inject.spi.Element;

/**
 * Just-in-time binding.
 *
 * @author pascal@kaching.com (Pascal-Louis Perez)
 */
public interface JitBinding<T> extends Element {

  /**
   * Accepts a scoping visitor. Invokes the visitor method specific to this binding's scoping.
   *
   * @param visitor to call back on
   * @since 2.0
   */
  <V> V acceptScopingVisitor(BindingScopingVisitor<V> visitor);
}
