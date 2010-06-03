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

package com.google.inject.util;

import static java.lang.String.format;

import java.lang.reflect.GenericArrayType;
import java.lang.reflect.ParameterizedType;
import java.lang.reflect.Type;

public class TypeSchemes {
  
  static Matcher getMatcher(Type type) {
    if (type instanceof Class<?>) {
      return new InvariantMatcher((Class<?>) type);
    } else if (type instanceof GenericArrayType) {
      return new InvariantMatcher(type);
    } else if (type instanceof ParameterizedType) {
      ParameterizedType parameterizedType = (ParameterizedType) type;
      Class<?> rawType = (Class<?>) parameterizedType.getRawType();
      Type[] actualTypeArguments = parameterizedType.getActualTypeArguments();
      Matcher[] actualTypeArgumentMatchers = new Matcher[actualTypeArguments.length];
      for (int i = 0; i < actualTypeArguments.length; i++) {
        actualTypeArgumentMatchers[i] = getMatcher(actualTypeArguments[i]);
      }
      
      return new ParameterizedTypeMatcher(type, rawType, actualTypeArgumentMatchers);
    }
    throw new IllegalStateException(format("unrecognized type %s", type));
  }
  
  private static abstract class AbstractMatcher implements Matcher {
    protected final Type type;

    private AbstractMatcher(Type type) {
      this.type = type;
    }
    
    public String toString() {
      return type.toString();
    }
  }

  private static class InvariantMatcher extends AbstractMatcher {
    private InvariantMatcher(Type type) {
      super(type);
    }
    
    public boolean matches(Type otherType) {
      return type.equals(otherType);
    }
  }

  private static class ParameterizedTypeMatcher extends AbstractMatcher {
    private final Class<?> rawType;
    private final Matcher[] actualTypeArgumentMatchers;

    private ParameterizedTypeMatcher(
        Type type, Class<?> rawType,
        Matcher[] actualTypeArgumentMatchers) {
      super(type);
      this.rawType = rawType;
      this.actualTypeArgumentMatchers = actualTypeArgumentMatchers;
    }
    
    public boolean matches(Type that) {
      if (that instanceof Class<?>) {
        return rawType.equals(that);
      } else if (that instanceof ParameterizedType) {
        ParameterizedType thatParameterizedType = (ParameterizedType) that;
        Type[] thatActualTypeArguments = thatParameterizedType.getActualTypeArguments();
        if (actualTypeArgumentMatchers.length != thatActualTypeArguments.length ||
            !rawType.equals(thatParameterizedType.getRawType())) return false;
        for (int i = 0; i < actualTypeArgumentMatchers.length; i++) {
          if (!actualTypeArgumentMatchers[i].matches(thatActualTypeArguments[i])) {
            return false;
          }
        }
        return true;
      }
      return false;
    }
  }
  
  public interface Matcher {
    boolean matches(Type type);
  }
  
}
