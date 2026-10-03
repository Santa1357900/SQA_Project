package com.google.gson.internal;

import org.junit.Test;
import static org.junit.Assert.*;

import java.io.Serializable;
import java.lang.reflect.GenericArrayType;
import java.lang.reflect.ParameterizedType;
import java.lang.reflect.Type;
import java.lang.reflect.WildcardType;
import java.util.*;

public class $Gson$TypesTest {

  @Test
  public void testArrayOf() throws Throwable {
    GenericArrayType arrayType = $Gson$Types.arrayOf(String.class);
    assertNotNull(arrayType);
    assertEquals(String.class, arrayType.getGenericComponentType());
  }

  @Test
  public void testSubtypeOf() throws Throwable {
    WildcardType wildcard = $Gson$Types.subtypeOf(String.class);
    assertNotNull(wildcard);
    assertEquals(1, wildcard.getUpperBounds().length);
    assertEquals(String.class, wildcard.getUpperBounds()[0]);
    assertEquals(0, wildcard.getLowerBounds().length);

    WildcardType nestedWildcard = $Gson$Types.subtypeOf(wildcard);
    assertNotNull(nestedWildcard);
  }

  @Test
  public void testSupertypeOf() throws Throwable {
    WildcardType wildcard = $Gson$Types.supertypeOf(String.class);
    assertNotNull(wildcard);
    assertEquals(1, wildcard.getLowerBounds().length);
    assertEquals(String.class, wildcard.getLowerBounds()[0]);
    assertEquals(1, wildcard.getUpperBounds().length);
    assertEquals(Object.class, wildcard.getUpperBounds()[0]);

    WildcardType nestedWildcard = $Gson$Types.supertypeOf(wildcard);
    assertNotNull(nestedWildcard);
  }

  @Test
  public void testCanonicalize() throws Throwable {
    Class<?> clazz = String.class;
    assertEquals(clazz, $Gson$Types.canonicalize(clazz));

    Class<?> arrayClass = String[].class;
    Type canonicalArray = $Gson$Types.canonicalize(arrayClass);
    assertTrue(canonicalArray instanceof GenericArrayType);

    ParameterizedType paramType = $Gson$Types.newParameterizedTypeWithOwner(null, List.class, String.class);
    assertEquals(paramType, $Gson$Types.canonicalize(paramType));

    GenericArrayType genericArray = $Gson$Types.arrayOf(String.class);
    assertEquals(genericArray, $Gson$Types.canonicalize(genericArray));

    WildcardType wildcard = $Gson$Types.subtypeOf(String.class);
    assertEquals(wildcard, $Gson$Types.canonicalize(wildcard));

    assertEquals(Integer.valueOf(5), $Gson$Types.canonicalize(Integer.valueOf(5)));
  }

  @Test
  public void testGetRawType() throws Throwable {
    assertEquals(String.class, $Gson$Types.getRawType(String.class));

    ParameterizedType paramType = $Gson$Types.newParameterizedTypeWithOwner(null, List.class, String.class);
    assertEquals(List.class, $Gson$Types.getRawType(paramType));

    GenericArrayType genericArray = $Gson$Types.arrayOf(String.class);
    assertEquals(String[].class, $Gson$Types.getRawType(genericArray));

    WildcardType wildcard = $Gson$Types.subtypeOf(String.class);
    assertEquals(String.class, $Gson$Types.getRawType(wildcard));
  }

  @Test(expected = IllegalArgumentException.class)
  public void testGetRawTypeInvalid() throws Throwable {
    $Gson$Types.getRawType(null);
  }

  @Test(expected = IllegalArgumentException.class)
  public void testGetRawTypeUnsupported() throws Throwable {
    $Gson$Types.getRawType(Integer.valueOf(5));
  }

  @Test
  public void testEqualsTypes() throws Throwable {
    Type t1 = String.class;
    Type t2 = String.class;
    Type t3 = Integer.class;

    assertTrue($Gson$Types.equals(t1, t2));
    assertFalse($Gson$Types.equals(t1, t3));
    assertTrue($Gson$Types.equals(null, null));
    assertFalse($Gson$Types.equals(t1, null));
    assertFalse($Gson$Types.equals(null, t1));

    ParameterizedType p1 = $Gson$Types.newParameterizedTypeWithOwner(null, List.class, String.class);
    ParameterizedType p2 = $Gson$Types.newParameterizedTypeWithOwner(null, List.class, String.class);
    ParameterizedType p3 = $Gson$Types.newParameterizedTypeWithOwner(null, List.class, Integer.class);

    assertTrue($Gson$Types.equals(p1, p2));
    assertFalse($Gson$Types.equals(p1, p3));
    assertFalse($Gson$Types.equals(p1, t1));

    GenericArrayType g1 = $Gson$Types.arrayOf(String.class);
    GenericArrayType g2 = $Gson$Types.arrayOf(String.class);
    GenericArrayType g3 = $Gson$Types.arrayOf(Integer.class);

    assertTrue($Gson$Types.equals(g1, g2));
    assertFalse($Gson$Types.equals(g1, g3));
    assertFalse($Gson$Types.equals(g1, t1));

    WildcardType w1 = $Gson$Types.subtypeOf(String.class);
    WildcardType w2 = $Gson$Types.subtypeOf(String.class);
    WildcardType w3 = $Gson$Types.subtypeOf(Integer.class);

    assertTrue($Gson$Types.equals(w1, w2));
    assertFalse($Gson$Types.equals(w1, w3));
    assertFalse($Gson$Types.equals(w1, t1));

    assertFalse($Gson$Types.equals(Integer.valueOf(1), Integer.valueOf(2)));
  }

  @Test
  public void testTypeToString() throws Throwable {
    assertEquals("java.lang.String", $Gson$Types.typeToString(String.class));
    ParameterizedType p1 = $Gson$Types.newParameterizedTypeWithOwner(null, List.class, String.class);
    assertEquals(p1.toString(), $Gson$Types.typeToString(p1));
  }

  @Test
  public void testGetArrayComponentType() throws Throwable {
    GenericArrayType g1 = $Gson$Types.arrayOf(String.class);
    assertEquals(String.class, $Gson$Types.getArrayComponentType(g1));
    assertEquals(String.class, $Gson$Types.getArrayComponentType(String[].class));
  }

  @Test(expected = ClassCastException.class)
  public void testGetArrayComponentTypeInvalid() throws Throwable {
    $Gson$Types.getArrayComponentType(String.class);
  }

  @Test
  public void testGetCollectionElementType() throws Throwable {
    ParameterizedType listType = $Gson$Types.newParameterizedTypeWithOwner(null, List.class, String.class);
    assertEquals(String.class, $Gson$Types.getCollectionElementType(listType, List.class));

    WildcardType wildcardList = $Gson$Types.subtypeOf(listType);
    assertEquals(String.class, $Gson$Types.getCollectionElementType(wildcardList, List.class));

    assertEquals(Object.class, $Gson$Types.getCollectionElementType(Collection.class, Collection.class));
  }

  @Test
  public void testGetMapKeyAndValueTypes() throws Throwable {
    ParameterizedType mapType = $Gson$Types.newParameterizedTypeWithOwner(null, Map.class, String.class, Integer.class);
    Type[] keyValues = $Gson$Types.getMapKeyAndValueTypes(mapType, Map.class);
    assertEquals(2, keyValues.length);
    assertEquals(String.class, keyValues[0]);
    assertEquals(Integer.class, keyValues[1]);

    Type[] propValues = $Gson$Types.getMapKeyAndValueTypes(Properties.class, Properties.class);
    assertEquals(2, propValues.length);
    assertEquals(String.class, propValues[0]);
    assertEquals(String.class, propValues[1]);

    Type[] objValues = $Gson$Types.getMapKeyAndValueTypes(Object.class, Object.class);
    assertEquals(2, objValues.length);
    assertEquals(Object.class, objValues[0]);
    assertEquals(Object.class, objValues[1]);
  }

  @Test
  public void testResolve() throws Throwable {
    Type resolved = $Gson$Types.resolve(String.class, String.class, String.class);
    assertEquals(String.class, resolved);

    GenericArrayType gArray = $Gson$Types.arrayOf(String.class);
    assertEquals(gArray, $Gson$Types.resolve(String.class, String.class, gArray));

    ParameterizedType pType = $Gson$Types.newParameterizedTypeWithOwner(null, List.class, String.class);
    assertEquals(pType, $Gson$Types.resolve(String.class, String.class, pType));

    WildcardType wType = $Gson$Types.subtypeOf(String.class);
    assertEquals(wType, $Gson$Types.resolve(String.class, String.class, wType));

    WildcardType wSuperType = $Gson$Types.supertypeOf(String.class);
    assertEquals(wSuperType, $Gson$Types.resolve(String.class, String.class, wSuperType));
  }

  @Test
  public void testParameterizedTypeImplMethods() throws Throwable {
    ParameterizedType pType = $Gson$Types.newParameterizedTypeWithOwner(null, List.class, String.class);
    assertNotNull(pType.getActualTypeArguments());
    assertNotNull(pType.getRawType());
    assertNull(pType.getOwnerType());
    assertTrue(pType.equals(pType));
    assertFalse(pType.equals(null));
    assertTrue(pType.hashCode() != 0);
  }

  @Test(expected = IllegalArgumentException.class)
  public void testParameterizedTypeImplInvalidOwner() throws Throwable {
    $Gson$Types.newParameterizedTypeWithOwner(null, Map.Entry.class, String.class, String.class);
  }

  @Test(expected = NullPointerException.class)
  public void testParameterizedTypeImplNullArg() throws Throwable {
    $Gson$Types.newParameterizedTypeWithOwner(null, List.class, (Type) null);
  }

  @Test(expected = IllegalArgumentException.class)
  public void testParameterizedTypeImplPrimitiveArg() throws Throwable {
    $Gson$Types.newParameterizedTypeWithOwner(null, List.class, int.class);
  }

  @Test
  public void testGenericArrayTypeImplMethods() throws Throwable {
    GenericArrayType gType = $Gson$Types.arrayOf(String.class);
    assertNotNull(gType.getGenericComponentType());
    assertTrue(gType.equals(gType));
    assertFalse(gType.equals(null));
    assertTrue(gType.hashCode() != 0);
    assertNotNull(gType.toString());
  }

  @Test(expected = IllegalArgumentException.class)
  public void testWildcardTypeImplInvalidBounds() throws Throwable {
    $Gson$Types.subtypeOf(int.class);
  }

  @Test(expected = IllegalArgumentException.class)
  public void testWildcardTypeImplMultipleLowerBounds() throws Throwable {
    Type[] upper = new Type[] { Object.class };
    Type[] lower = new Type[] { String.class, Integer.class };
    new $Gson$Types.WildcardTypeImpl(upper, lower);
  }

  @Test(expected = IllegalArgumentException.class)
  public void testWildcardTypeImplZeroUpperBounds() throws Throwable {
    Type[] upper = new Type[] {};
    Type[] lower = new Type[] {};
    new $Gson$Types.WildcardTypeImpl(upper, lower);
  }

  @Test
  public void testWildcardTypeImplMethods() throws Throwable {
    WildcardType wType = $Gson$Types.subtypeOf(String.class);
    assertTrue(wType.equals(wType));
    assertFalse(wType.equals(null));
    assertTrue(wType.hashCode() != 0);
    assertNotNull(wType.toString());

    WildcardType wSuper = $Gson$Types.supertypeOf(String.class);
    assertNotNull(wSuper.toString());
  }

  @Test
  public void testCheckNotPrimitive() throws Throwable {
    $Gson$Types.checkNotPrimitive(String.class);
  }

  @Test(expected = IllegalArgumentException.class)
  public void testCheckNotPrimitiveFailure() throws Throwable {
    $Gson$Types.checkNotPrimitive(int.class);
  }
}