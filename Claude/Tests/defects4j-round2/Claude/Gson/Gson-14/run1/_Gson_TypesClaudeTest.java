package com.google.gson.internal;

import org.junit.Test;
import static org.junit.Assert.*;

import java.lang.reflect.GenericArrayType;
import java.lang.reflect.ParameterizedType;
import java.lang.reflect.Type;
import java.lang.reflect.TypeVariable;
import java.lang.reflect.WildcardType;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Properties;

import com.google.gson.reflect.TypeToken;

public class _Gson_TypesClaudeTest {

  // non-static inner class: used to trigger the "owner type required" branch
  class NonStaticInner {
  }

  static class StringArrayList extends ArrayList<String> {
    private static final long serialVersionUID = 1L;
  }

  // branch: rawType is Class, static/top-level -> no owner required, args applied correctly
  @Test
  public void testNewParameterizedTypeWithOwner_basic() throws Throwable {
    ParameterizedType pt = $Gson$Types.newParameterizedTypeWithOwner(null, List.class, String.class);
    assertEquals(List.class, pt.getRawType());
    assertNull(pt.getOwnerType());
    assertEquals(String.class, pt.getActualTypeArguments()[0]);
  }

  // branch: rawType is a non-static inner class without owner -> checkArgument fails
  @Test
  public void testNewParameterizedTypeWithOwner_nonStaticInnerWithoutOwner_throws() throws Throwable {
    try {
      $Gson$Types.newParameterizedTypeWithOwner(null, NonStaticInner.class, new Type[0]);
      fail("expected IllegalArgumentException");
    } catch (IllegalArgumentException expected) {
    }
  }

  // branch: checkNotPrimitive on a type argument inside constructor loop
  @Test
  public void testNewParameterizedTypeWithOwner_primitiveTypeArgument_throws() throws Throwable {
    try {
      $Gson$Types.newParameterizedTypeWithOwner(null, List.class, int.class);
      fail("expected IllegalArgumentException");
    } catch (IllegalArgumentException expected) {
    }
  }

  // branch: checkNotNull on a null type argument inside constructor loop
  @Test
  public void testNewParameterizedTypeWithOwner_nullTypeArgument_throws() throws Throwable {
    try {
      $Gson$Types.newParameterizedTypeWithOwner(null, List.class, (Type) null);
      fail("expected NullPointerException");
    } catch (NullPointerException expected) {
    }
  }

  // arrayOf: returns GenericArrayType wrapping the given component
  @Test
  public void testArrayOf_returnsGenericArrayTypeWithComponent() throws Throwable {
    GenericArrayType t = $Gson$Types.arrayOf(String.class);
    assertEquals(String.class, t.getGenericComponentType());
  }

  // subtypeOf with plain Class bound -> upper bound equals class, no lower bounds
  @Test
  public void testSubtypeOf_withClassBound() throws Throwable {
    WildcardType w = $Gson$Types.subtypeOf(CharSequence.class);
    assertEquals(CharSequence.class, w.getUpperBounds()[0]);
    assertEquals(0, w.getLowerBounds().length);
  }

  // subtypeOf with Object.class bound -> toString shorthand "?"
  @Test
  public void testSubtypeOf_withObjectBound_toStringQuestionMark() throws Throwable {
    WildcardType w = $Gson$Types.subtypeOf(Object.class);
    assertEquals("?", w.toString());
  }

  // BUG-CATCHING: subtypeOf given an existing WildcardType bound must unwrap it,
  // not nest it as a new wildcard around the wildcard.
  @Test
  public void testSubtypeOf_withWildcardBound_unwrapsToOriginalBound() throws Throwable {
    WildcardType inner = $Gson$Types.subtypeOf(String.class);
    WildcardType outer = $Gson$Types.subtypeOf(inner);
    assertEquals(String.class, outer.getUpperBounds()[0]);
  }

  // supertypeOf with plain Class bound -> lower bound equals class, upper bound Object
  @Test
  public void testSupertypeOf_withClassBound() throws Throwable {
    WildcardType w = $Gson$Types.supertypeOf(String.class);
    assertEquals(String.class, w.getLowerBounds()[0]);
    assertEquals(Object.class, w.getUpperBounds()[0]);
  }

  // BUG-CATCHING: supertypeOf given an existing WildcardType bound must unwrap it,
  // not nest it as a new wildcard around the wildcard.
  @Test
  public void testSupertypeOf_withWildcardBound_unwrapsToOriginalBound() throws Throwable {
    WildcardType inner = $Gson$Types.supertypeOf(String.class);
    WildcardType outer = $Gson$Types.supertypeOf(inner);
    assertEquals(String.class, outer.getLowerBounds()[0]);
  }

  // canonicalize: array class becomes GenericArrayType with same component
  @Test
  public void testCanonicalize_classArray_returnsGenericArrayType() throws Throwable {
    Type t = $Gson$Types.canonicalize(String[].class);
    assertTrue(t instanceof GenericArrayType);
    assertEquals(String.class, ((GenericArrayType) t).getGenericComponentType());
  }

  // canonicalize: non-array Class is returned as-is
  @Test
  public void testCanonicalize_nonArrayClass_returnsSameClass() throws Throwable {
    assertEquals(String.class, $Gson$Types.canonicalize(String.class));
  }

  // canonicalize: ParameterizedType produces an equal (per $Gson$Types.equals) type
  @Test
  public void testCanonicalize_parameterizedType_returnsEqualType() throws Throwable {
    Type pt = new TypeToken<List<String>>() {}.getType();
    Type c = $Gson$Types.canonicalize(pt);
    assertTrue($Gson$Types.equals(pt, c));
  }

  // getRawType: Class instance returns itself
  @Test
  public void testGetRawType_class_returnsSameClass() throws Throwable {
    assertEquals(String.class, $Gson$Types.getRawType(String.class));
  }

  // getRawType: ParameterizedType returns its raw Class
  @Test
  public void testGetRawType_parameterizedType_returnsRawClass() throws Throwable {
    Type pt = new TypeToken<List<String>>() {}.getType();
    assertEquals(List.class, $Gson$Types.getRawType(pt));
  }

  // getRawType: GenericArrayType returns corresponding array class
  @Test
  public void testGetRawType_genericArrayType_returnsArrayClass() throws Throwable {
    GenericArrayType gat = $Gson$Types.arrayOf(String.class);
    assertEquals(String[].class, $Gson$Types.getRawType(gat));
  }

  // getRawType: TypeVariable falls back to Object.class
  @Test
  public void testGetRawType_typeVariable_returnsObjectClass() throws Throwable {
    TypeVariable<?> tv = List.class.getTypeParameters()[0];
    assertEquals(Object.class, $Gson$Types.getRawType(tv));
  }

  // getRawType: WildcardType resolves via its first upper bound
  @Test
  public void testGetRawType_wildcardType_returnsUpperBoundRawType() throws Throwable {
    WildcardType w = $Gson$Types.subtypeOf(String.class);
    assertEquals(String.class, $Gson$Types.getRawType(w));
  }

  // getRawType: unsupported/null type throws IllegalArgumentException mentioning "null"
  @Test
  public void testGetRawType_null_throwsIllegalArgumentException() throws Throwable {
    try {
      $Gson$Types.getRawType(null);
      fail("expected IllegalArgumentException");
    } catch (IllegalArgumentException expected) {
      assertTrue(expected.getMessage().contains("null"));
    }
  }

  // equal(Object,Object): handles both-null and one-null cases
  @Test
  public void testEqual_nullHandling() throws Throwable {
    assertTrue($Gson$Types.equal(null, null));
    assertFalse($Gson$Types.equal(null, "x"));
    assertTrue($Gson$Types.equal("x", "x"));
  }

  // equals(Type,Type): Class branch
  @Test
  public void testEquals_sameClass_true() throws Throwable {
    assertTrue($Gson$Types.equals(String.class, String.class));
  }

  @Test
  public void testEquals_differentClasses_false() throws Throwable {
    assertFalse($Gson$Types.equals(String.class, Integer.class));
  }

  // equals(Type,Type): ParameterizedType branch, equal case
  @Test
  public void testEquals_parameterizedTypesEqual_true() throws Throwable {
    Type pt1 = new TypeToken<List<String>>() {}.getType();
    Type pt2 = new TypeToken<List<String>>() {}.getType();
    assertTrue($Gson$Types.equals(pt1, pt2));
  }

  // equals(Type,Type): ParameterizedType vs non-ParameterizedType -> false
  @Test
  public void testEquals_parameterizedVsClass_false() throws Throwable {
    Type pt1 = new TypeToken<List<String>>() {}.getType();
    assertFalse($Gson$Types.equals(pt1, String.class));
  }

  // equals(Type,Type): GenericArrayType branch
  @Test
  public void testEquals_genericArrayTypesEqual_true() throws Throwable {
    GenericArrayType g1 = $Gson$Types.arrayOf(String.class);
    GenericArrayType g2 = $Gson$Types.arrayOf(String.class);
    assertTrue($Gson$Types.equals(g1, g2));
  }

  // equals(Type,Type): WildcardType branch
  @Test
  public void testEquals_wildcardTypesEqual_true() throws Throwable {
    WildcardType w1 = $Gson$Types.subtypeOf(String.class);
    WildcardType w2 = $Gson$Types.subtypeOf(String.class);
    assertTrue($Gson$Types.equals(w1, w2));
  }

  // equals(Type,Type): TypeVariable branch, same declaration and name
  @Test
  public void testEquals_typeVariablesSameDeclarationAndName_true() throws Throwable {
    TypeVariable<?> tv1 = List.class.getTypeParameters()[0];
    TypeVariable<?> tv2 = List.class.getTypeParameters()[0];
    assertTrue($Gson$Types.equals(tv1, tv2));
  }

  // hashCodeOrZero: null returns 0, non-null returns the object's hashCode
  @Test
  public void testHashCodeOrZero_variousInputs() throws Throwable {
    assertEquals(0, $Gson$Types.hashCodeOrZero(null));
    Object o = "hello";
    assertEquals(o.hashCode(), $Gson$Types.hashCodeOrZero(o));
  }

  // typeToString: Class branch returns getName()
  @Test
  public void testTypeToString_classType_returnsClassName() throws Throwable {
    assertEquals("java.lang.String", $Gson$Types.typeToString(String.class));
  }

  // typeToString: non-Class branch returns type.toString() (ParameterizedType format)
  @Test
  public void testTypeToString_parameterizedType_returnsFormattedString() throws Throwable {
    Type pt = $Gson$Types.newParameterizedTypeWithOwner(null, List.class, String.class);
    assertEquals("java.util.List<java.lang.String>", $Gson$Types.typeToString(pt));
  }

  // getGenericSupertype: toResolve == rawType returns context unchanged
  @Test
  public void testGetGenericSupertype_toResolveEqualsRawType_returnsContext() throws Throwable {
    Type result = $Gson$Types.getGenericSupertype(String.class, String.class, String.class);
    assertSame(String.class, result);
  }

  // getGenericSupertype: walks superclass chain to find matching raw supertype
  @Test
  public void testGetGenericSupertype_directSuperclass_returnsParameterizedSuperclass() throws Throwable {
    Type result = $Gson$Types.getGenericSupertype(StringArrayList.class, StringArrayList.class, ArrayList.class);
    assertTrue(result instanceof ParameterizedType);
    assertEquals(String.class, ((ParameterizedType) result).getActualTypeArguments()[0]);
  }

  // getGenericSupertype: interface branch finds matching implemented interface
  @Test
  public void testGetGenericSupertype_interfaceMatch_returnsGenericInterface() throws Throwable {
    Type result = $Gson$Types.getGenericSupertype(ArrayList.class, ArrayList.class, List.class);
    assertTrue(result instanceof ParameterizedType);
    assertEquals(List.class, ((ParameterizedType) result).getRawType());
  }

  // getGenericSupertype: unrelated interface, cannot resolve further -> returns toResolve
  @Test
  public void testGetGenericSupertype_unrelatedInterface_returnsUnchanged() throws Throwable {
    Type result = $Gson$Types.getGenericSupertype(Object.class, Object.class, Runnable.class);
    assertEquals(Runnable.class, result);
  }

  // getSupertype: List<String> resolved as supertype Collection -> Collection<String>
  @Test
  public void testGetSupertype_listToCollection_returnsParameterizedCollection() throws Throwable {
    Type context = new TypeToken<List<String>>() {}.getType();
    Type result = $Gson$Types.getSupertype(context, List.class, Collection.class);
    assertTrue(result instanceof ParameterizedType);
    assertEquals(String.class, ((ParameterizedType) result).getActualTypeArguments()[0]);
  }

  // getSupertype: supertype not assignable from contextRawType -> checkArgument throws
  @Test
  public void testGetSupertype_notAssignable_throws() throws Throwable {
    try {
      $Gson$Types.getSupertype(String.class, String.class, List.class);
      fail("expected IllegalArgumentException");
    } catch (IllegalArgumentException expected) {
    }
  }

  // getArrayComponentType: Class array branch
  @Test
  public void testGetArrayComponentType_classArray_returnsComponent() throws Throwable {
    assertEquals(String.class, $Gson$Types.getArrayComponentType(String[].class));
  }

  // getArrayComponentType: GenericArrayType branch
  @Test
  public void testGetArrayComponentType_genericArrayType_returnsComponent() throws Throwable {
    GenericArrayType g = $Gson$Types.arrayOf(String.class);
    assertEquals(String.class, $Gson$Types.getArrayComponentType(g));
  }

  // getCollectionElementType: List<String> element type resolves to String.class
  @Test
  public void testGetCollectionElementType_listOfString_returnsStringClass() throws Throwable {
    Type context = new TypeToken<List<String>>() {}.getType();
    Type elementType = $Gson$Types.getCollectionElementType(context, List.class);
    assertEquals(String.class, elementType);
  }

  // getMapKeyAndValueTypes: Properties workaround branch
  @Test
  public void testGetMapKeyAndValueTypes_properties_returnsStringString() throws Throwable {
    Type[] types = $Gson$Types.getMapKeyAndValueTypes(Properties.class, Properties.class);
    assertEquals(String.class, types[0]);
    assertEquals(String.class, types[1]);
  }

  // getMapKeyAndValueTypes: Map<String,Integer> resolves both key and value types
  @Test
  public void testGetMapKeyAndValueTypes_mapOfStringInteger_returnsKeyValueTypes() throws Throwable {
    Type context = new TypeToken<Map<String, Integer>>() {}.getType();
    Type[] types = $Gson$Types.getMapKeyAndValueTypes(context, Map.class);
    assertEquals(String.class, types[0]);
    assertEquals(Integer.class, types[1]);
  }

  // resolve: plain non-array Class falls through to final else branch, unchanged
  @Test
  public void testResolve_nonGenericType_returnsSameType() throws Throwable {
    Type result = $Gson$Types.resolve(Object.class, Object.class, String.class);
    assertSame(String.class, result);
  }

  // resolve: array Class whose component doesn't change -> returns original array class
  @Test
  public void testResolve_arrayClass_returnsSameArrayClass() throws Throwable {
    Type result = $Gson$Types.resolve(Object.class, Object.class, String[].class);
    assertEquals(String[].class, result);
  }

  // resolveTypeVariable: type variable declared by a class resolves to actual type argument
  @Test
  public void testResolveTypeVariable_declaredByContextClass_resolvesActualArgument() throws Throwable {
    Type context = new TypeToken<List<String>>() {}.getType();
    TypeVariable<?> unknown = List.class.getTypeParameters()[0];
    Type result = $Gson$Types.resolveTypeVariable(context, List.class, unknown);
    assertEquals(String.class, result);
  }

  // checkNotPrimitive: primitive Class triggers checkArgument failure
  @Test
  public void testCheckNotPrimitive_primitiveClass_throwsIllegalArgumentException() throws Throwable {
    try {
      $Gson$Types.checkNotPrimitive(int.class);
      fail("expected IllegalArgumentException");
    } catch (IllegalArgumentException expected) {
    }
  }
}
