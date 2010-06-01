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

/**
 * An object capable of dynamically determining if it can provide an instance
 * for a specified {@link Key}, and, if possible, provides such instance.
 *
 * @author pascal@kaching.com (Pascal-Louis Perez)
 * @since 3.0?
 */
public interface JitProvider<T> {

  /**
   * Returns whether an instance of the give {@code key} can be provided by
   * {@link #get}. If this method returns {@code true} then {@link #get} must
   * be capable of producing an instance for the {@code key}.
   */
  boolean canProvide(Key<?> key);

  /**
   * Provides an instance of {@code T}. May not return {@code null}.
   *
   * @throws OutOfScopeException when an attempt is made to access a scoped object while the scope
   *     in question is not currently active
   */
  T get(Key<T> key);

}
