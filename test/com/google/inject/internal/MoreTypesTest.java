/**
 * Copyright (C) tring2010 Google Inc.
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
import com.google.inject.internal.MoreTypes.WildcardTypeImpl;
import com.google.inject.util.Types;

import java.io.Serializable;
import java.lang.reflect.GenericArrayType;
import java.lang.reflect.GenericDeclaration;
import java.lang.reflect.Type;
import java.lang.reflect.TypeVariable;
import java.lang.reflect.WildcardType;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;

import junit.framework.TestCase;

/**
 * @author schmitt@google.com (Peter Schmitt)
 * @author pascal@kaching.com (Pascal-Louis Perez)
 */
public class MoreTypesTest extends TestCase {

  public void testParameterizedTypeToString() {
    TypeLiteral<Inner<String>> innerString = new TypeLiteral<Inner<String>>() {
    };
    assertEquals(
        "com.google.inject.internal.MoreTypesTest$Inner<java.lang.String>",
        MoreTypes.typeToString(innerString.getType()));

    TypeLiteral<Set<Inner<Integer>>> mapInnerInteger = new TypeLiteral<Set<Inner<Integer>>>() {
    };
    assertEquals(
        "java.util.Set<com.google.inject.internal.MoreTypesTest$Inner<java.lang.Integer>>",
        MoreTypes.typeToString(mapInnerInteger.getType()));

    TypeLiteral<Map<Inner<Long>, Set<Inner<Long>>>> mapInnerLongToSetInnerLong = new TypeLiteral<Map<Inner<Long>, Set<Inner<Long>>>>() {
    };
    assertEquals(
        "java.util.Map<com.google.inject.internal.MoreTypesTest$Inner<java.lang.Long>, "
            + "java.util.Set<com.google.inject.internal.MoreTypesTest$Inner<java.lang.Long>>>",
        MoreTypes.typeToString(mapInnerLongToSetInnerLong.getType()));
  }

  public static class Inner<T> {
  }

  public void testIsInstance() {
    // class
    assertTrue(MoreTypes.isInstance(String.class, String.class));
    assertTrue(MoreTypes.isInstance(boolean[].class, boolean[].class));
    assertFalse(MoreTypes.isInstance(List.class, String.class));
    
    // generic array type
    assertTrue(MoreTypes.isInstance(
        new TypeLiteral<List<?>[]>() {}.getType(),
        new TypeLiteral<List<Integer>[]>() {}.getType()));
    
    // parameterized type
    assertTrue(MoreTypes.isInstance(
        Types.listOf(Types.subtypeOf(Object.class)), Types.listOf(String.class)));
    
    // type variable
    assertTrue(MoreTypes.isInstance(new TypeVariableImpl(), String.class));
    assertTrue(MoreTypes.isInstance(new TypeVariableImpl(Serializable.class), String.class));
    assertFalse(MoreTypes.isInstance(new TypeVariableImpl(Collection.class), String.class));
    
    // wildcard type
    assertTrue(MoreTypes.isInstance(
        Types.subtypeOf(Object.class), Types.listOf(Boolean.class)));
    assertTrue(MoreTypes.isInstance(
        Types.subtypeOf(Collection.class), Types.listOf(Boolean.class)));
    
    assertFalse(MoreTypes.isInstance(
        Types.subtypeOf(Collection.class), Serializable.class));
    assertFalse(MoreTypes.isInstance(
        Types.supertypeOf(Set.class), TreeSet.class));
  }

  public void testIsAssignableFrom() {
    // class
    assertTrue(MoreTypes.isAssignableFrom(String.class, String.class));
    assertTrue(MoreTypes.isAssignableFrom(Serializable.class, String.class));
    assertTrue(MoreTypes.isAssignableFrom(Set.class, HashSet.class));
    assertTrue(MoreTypes.isAssignableFrom(Object.class, Types.listOf(String.class)));
    assertTrue(MoreTypes.isAssignableFrom(
        List[].class, new TypeLiteral<List<?>[]>() {}.getType()));
    assertTrue(MoreTypes.isAssignableFrom(
        ArrayList[].class, new TypeLiteral<ArrayList<?>[]>() {}.getType()));

    assertFalse(MoreTypes.isAssignableFrom(Set.class, List.class));
    assertFalse(MoreTypes.isAssignableFrom(
        ArrayList[].class, new TypeLiteral<List<?>[]>() {}.getType()));

    // generic array type
    assertTrue(MoreTypes.isAssignableFrom(
        new TypeLiteral<List<?>[]>() {}.getType(),
        new TypeLiteral<List<Integer>[]>() {}.getType()));
    assertTrue(MoreTypes.isAssignableFrom(
        new TypeLiteral<List<Integer>[]>() {}.getType(),
        new TypeLiteral<ArrayList<Integer>[]>() {}.getType()));
    assertTrue(MoreTypes.isAssignableFrom(
        new TypeLiteral<List<? extends Set<?>>[]>() {}.getType(),
        new TypeLiteral<ArrayList<TreeSet<Double>>[]>() {}.getType()));

    assertFalse(MoreTypes.isAssignableFrom(
        new TypeLiteral<List<?>[]>() {}.getType(), boolean[].class));
    assertFalse(MoreTypes.isAssignableFrom(
        new TypeLiteral<List<?>[]>() {}.getType(), Collection[].class));
    assertFalse(MoreTypes.isAssignableFrom(
        new TypeLiteral<List<?>[]>() {}.getType(), List[].class));

    // parameterized type
    assertTrue(MoreTypes.isAssignableFrom(Types.listOf(String.class),
        List.class));
    assertTrue(MoreTypes.isAssignableFrom(Types.listOf(String.class), Types
        .listOf(String.class)));
    assertTrue(MoreTypes.isAssignableFrom(Types.listOf(String.class), Types
        .newParameterizedType(ArrayList.class, String.class)));
    assertTrue(MoreTypes.isAssignableFrom(Types.listOf(Types
        .subtypeOf(Serializable.class)), Types.newParameterizedType(
        ArrayList.class, String.class)));

    assertFalse(MoreTypes.isAssignableFrom(Types.listOf(String.class), Types
        .setOf(String.class)));
    assertFalse(MoreTypes.isAssignableFrom(Types.listOf(Serializable.class),
        Types.listOf(String.class)));
    assertFalse(MoreTypes.isAssignableFrom(Types.listOf(String.class), Types
        .listOf(Integer.class)));

    // type variable
    assertTrue(MoreTypes.isAssignableFrom(new TypeVariableImpl(), String.class));
    assertTrue(MoreTypes.isAssignableFrom(new TypeVariableImpl(String.class),
        String.class));
    assertTrue(MoreTypes.isAssignableFrom(new TypeVariableImpl(String.class,
        Serializable.class, Comparable.class), String.class));

    assertFalse(MoreTypes.isAssignableFrom(new TypeVariableImpl(List.class,
        Set.class), TreeSet.class));
  }

  static class TypeVariableImpl implements TypeVariable<GenericDeclaration> {
    private final Type[] bounds;

    TypeVariableImpl() {
      this(new Type[0]);
    }

    TypeVariableImpl(Type... bounds) {
      this.bounds = bounds;
    }

    @Override
    public Type[] getBounds() {
      return bounds;
    }

    @Override
    public GenericDeclaration getGenericDeclaration() {
      throw new UnsupportedOperationException();
    }

    @Override
    public String getName() {
      throw new UnsupportedOperationException();
    }
  }
}
