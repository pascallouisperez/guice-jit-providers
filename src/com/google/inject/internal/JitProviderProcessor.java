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

package com.google.inject.internal;

import static java.lang.Boolean.TRUE;

import com.google.inject.JitBinding;

/**
 * Handles {@code Binder.bindJitProvider} commands.
 *
 * @author pascal@kaching.com (Pascal-Louis Perez)
 * @since 3.0?
 */
final class JitProviderProcessor extends AbstractProcessor {

  JitProviderProcessor(Errors errors) {
    super(errors);
  }

  public <T> Boolean visit(JitBinding<T> binding) {
    injector.state.addJitBinding((JitBindingImpl<?>) binding);
    return TRUE;
  }

}
