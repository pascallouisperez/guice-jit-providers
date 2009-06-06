/*
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

package com.google.inject.internal;

import com.google.inject.TypeLiteral;
import static com.google.inject.internal.Preconditions.checkNotNull;
import com.google.inject.matcher.Matcher;
import com.google.inject.spi.TypeConverter;

/**
 * @author crazybob@google.com (Bob Lee)
 */
final class MatcherAndConverter {

  private final Matcher<? super TypeLiteral<?>> typeMatcher;
  private final TypeConverter typeConverter;
  private final Object source;

  public MatcherAndConverter(Matcher<? super TypeLiteral<?>> typeMatcher,
      TypeConverter typeConverter, Object source) {
    this.typeMatcher = checkNotNull(typeMatcher, "type matcher");
    this.typeConverter = checkNotNull(typeConverter, "converter");
    this.source = source;
  }

  public TypeConverter getTypeConverter() {
    return typeConverter;
  }

  public Matcher<? super TypeLiteral<?>> getTypeMatcher() {
    return typeMatcher;
  }

  public Object getSource() {
    return source;
  }

  @Override public String toString() {
    return typeConverter + " which matches " + typeMatcher
        + " (bound at " + source + ")";
  }
}
