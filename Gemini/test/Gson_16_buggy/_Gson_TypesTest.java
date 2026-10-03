package com.google.gson.internal;

import org.junit.Test;

import java.io.Serializable;
import java.lang.reflect.Array;
import java.lang.reflect.GenericArrayType;
import java.lang.reflect.ParameterizedType;
import java.lang.reflect.Type;
import java.lang.reflect.TypeVariable;
import java.lang.reflect.WildcardType;
import java.util.*;

import static org.junit.Assert.*;

public class $Gson$TypesTest {

  // Dummy interface with TypeVariable for testing resolveTypeVariable and declaringClassOf
  private static interface GenericInterface<T> {
    T get();
  }

  private static class GenericClass<T> implements GenericInterface<T> {
    public T get() {
      return null;
    }
  }

  @Test
  public void testNewParameterizedTypeWithOwner() throws Throwable {
    ParameterizedType pt = $Gson$Types.newParameterizedTypeWithOwner(null, List.class, String.class);
    assertNotNull(pt);
    assertNull(pt.getOwnerType());
    assertEquals(List.class, pt.getRawType());
    assertEquals(1, pt.getActualTypeArguments().length);
    assertEquals(String.class, pt.getActualTypeArguments()[0]);
  }

  @Test(expected = IllegalArgumentException.class)
  public void testNewParameterizedTypeWithOwnerPrimitiveArgument() throws Throwable {
    $Gson$Types.newParameterizedTypeWithOwner(null, List.class, int.class);
  }

  @Test
  public void testArrayOf() throws Throwable {
    GenericArrayType gat = $Gson$Types.arrayOf(String.class);
    assertNotNull(gat);
    assertEquals(String.class, gat.getGenericComponentType());
  }

  @Test
  public void testSubtypeOf() throws Throwable {
    WildcardType wt = $Gson$Types.subtypeOf(String.class);
    assertNotNull(wt);
    assertEquals(1, wt.getUpperBounds().length);
    assertEquals(String.class, wt.getUpperBounds()[0]);
    assertEquals(0, wt.getLowerBounds().length);
  }

  @Test
  public void testSubtypeOfWildcard() throws Throwable {
    WildcardType inner = $Gson$Types.subtypeOf(String.class);
    WildcardType wt = $Gson$Types.subtypeOf(inner);
    assertNotNull(wt);
    assertEquals(1, wt.getUpperBounds().length);
    assertEquals(String.class, wt.getUpperBounds()[0]);
  }

  @Test
  public void testSupertypeOf() throws Throwable {
    WildcardType wt = $Gson$Types.supertypeOf(String.class);
    assertNotNull(wt);
    assertEquals(1, wt.getUpperBounds().length);
    assertEquals(Object.class, wt.getUpperBounds()[0]);
    assertEquals(1, wt.getLowerBounds().length);
    assertEquals(String.class, wt.getLowerBounds()[0]);
  }

  @Test
  public void testSupertypeOfWildcard() throws Throwable {
    WildcardType inner = $Gson$Types.supertypeOf(String.class);
    WildcardType wt = $Gson$Types.supertypeOf(inner);
    assertNotNull(wt);
    assertEquals(1, wt.getLowerBounds().length);
    assertEquals(String.class, wt.getLowerBounds()[0]);
  }

  @Test
  public void testCanonicalize() throws Throwable {
    assertEquals(String.class, $Gson$Types.canonicalize(String.class));

    Class<?> stringArrayClass = String[].class;
    Type canonicalArray = $Gson$Types.canonicalize(stringArrayClass);
    assertTrue(canonicalArray instanceof GenericArrayType);

    ParameterizedType pt = $Gson$Types.newParameterizedTypeWithOwner(null, List.class, String.class);
    assertEquals(pt, $Gson$Types.canonicalize(pt));

    GenericArrayType gat = $Gson$Types.arrayOf(String.class);
    assertEquals(gat, $Gson$Types.canonicalize(gat));

    WildcardType wt = $Gson$Types.subtypeOf(String.class);
    assertEquals(wt, $Gson$Types.canonicalize(wt));

    Type serializableType = Serializable.class;
    assertEquals(serializableType, $Gson$Types.canonicalize(serializableType));
  }

  @Test
  public void testGetRawType() throws Throwable {
    assertEquals(String.class, $Gson$Types.getRawType(String.class));

    ParameterizedType pt = $Gson$Types.newParameterizedTypeWithOwner(null, List.class, String.class);
    assertEquals(List.class, $Gson$Types.getRawType(pt));

    GenericArrayType gat = $Gson$Types.arrayOf(String.class);
    assertEquals(String[].class, $Gson$Types.getRawType(gat));

    TypeVariable<?> tv = GenericClass.class.getTypeParameters()[0];
    assertEquals(Object.class, $Gson$Types.getRawType(tv));

    WildcardType wt = $Gson$Types.subtypeOf(String.class);
    assertEquals(String.class, $Gson$Types.getRawType(wt));
  }

  @Test(expected = IllegalArgumentException.class)
  public void testGetRawTypeInvalid() throws Throwable {
    $Gson$Types.getRawType($Gson$Types.supertypeOf(String.class));
  }

  @Test(expected = IllegalArgumentException.class)
  public void testGetRawTypeNull() throws Throwable {
    $Gson$Types.getRawType(null);
  }

  @Test
  public void testEqualsType() throws Throwable {
    assertTrue($Gson$Types.equals(null, null));
    assertTrue($Gson$Types.equals(String.class, String.class));
    assertFalse($Gson$Types.equals(String.class, Integer.class));

    ParameterizedType pt1 = $Gson$Types.newParameterizedTypeWithOwner(null, List.class, String.class);
    ParameterizedType pt2 = $Gson$Types.newParameterizedTypeWithOwner(null, List.class, String.class);
    ParameterizedType pt3 = $Gson$Types.newParameterizedTypeWithOwner(null, List.class, Integer.class);
    assertTrue($Gson$Types.equals(pt1, pt2));
    assertFalse($Gson$Types.equals(pt1, pt3));
    assertFalse($Gson$Types.equals(pt1, String.class));

    GenericArrayType gat1 = $Gson$Types.arrayOf(String.class);
    GenericArrayType gat2 = $Gson$Types.arrayOf(String.class);
    GenericArrayType gat3 = $Gson$Types.arrayOf(Integer.class);
    assertTrue($Gson$Types.equals(gat1, gat2));
    assertFalse($Gson$Types.equals(gat1, gat3));
    assertFalse($Gson$Types.equals(gat1, String.class));

    WildcardType wt1 = $Gson$Types.subtypeOf(String.class);
    WildcardType wt2 = $Gson$Types.subtypeOf(String.class);
    WildcardType wt3 = $Gson$Types.subtypeOf(Integer.class);
    assertTrue($Gson$Types.equals(wt1, wt2));
    assertFalse($Gson$Types.equals(wt1, wt3));
    assertFalse($Gson$Types.equals(wt1, String.class));

    TypeVariable<?> tv1 = GenericClass.class.getTypeParameters()[0];
    TypeVariable<?> tv2 = GenericClass.class.getTypeParameters()[0];
    assertTrue($Gson$Types.equals(tv1, tv2));
    assertFalse($Gson$Types.equals(tv1, String.class));

    assertFalse($Gson$Types.equals(tv1, new TypeVariable<Class<String>>() {
      public Type[] getBounds() { return new Type[0]; }
      public GenericDeclaration getGenericDeclaration() { return String.class; }
      public String getName() { return "T"; }
      public <T extends java.lang.annotation.Annotation> T getAnnotation(Class<T> annotationClass) { return null; }
      public java.lang.annotation.Annotation[] getAnnotations() { return new java.lang.annotation.Annotation[0]; }
      public java.lang.annotation.Annotation[] getDeclaredAnnotations() { return new java.lang.annotation.Annotation[0]; }
    }));

    assertFalse($Gson$Types.equals(String.class, new Type() {}));
  }

  @Test
  public void testTypeToString() throws Throwable {
    assertEquals("java.lang.String", $Gson$Types.typeToString(String.class));
    ParameterizedType pt = $Gson$Types.newParameterizedTypeWithOwner(null, List.class, String.class);
    assertEquals("java.util.List<java.lang.String>", $Gson$Types.typeToString(pt));
  }

  @Test
  public void testGetArrayComponentType() throws Throwable {
    GenericArrayType gat = $Gson$Types.arrayOf(String.class);
    assertEquals(String.class, $Gson$Types.getArrayComponentType(gat));
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

    WildcardType wt = $Gson$Types.subtypeOf(listType);
    assertEquals(String.class, $Gson$Types.getCollectionElementType(wt, Collection.class));

    assertEquals(Object.class, $Gson$Types.getCollectionElementType(Collection.class, Collection.class));
  }

  @Test
  public void testGetMapKeyAndValueTypes() throws Throwable {
    ParameterizedType mapType = $Gson$Types.newParameterizedTypeWithOwner(null, Map.class, String.class, Integer.class);
    Type[] keyAndVal = $Gson$Types.getMapKeyAndValueTypes(mapType, Map.class);
    assertEquals(2, keyAndVal.length);
    assertEquals(String.class, keyAndVal[0]);
    assertEquals(Integer.class, keyAndVal[1]);

    Type[] propTypes = $Gson$Types.getMapKeyAndValueTypes(Properties.class, Properties.class);
    assertEquals(2, propTypes.length);
    assertEquals(String.class, propTypes[0]);
    assertEquals(String.class, propTypes[1]);

    Type[] objTypes = $Gson$Types.getMapKeyAndValueTypes(Object.class, Object.class);
    assertEquals(2, objTypes.length);
    assertEquals(Object.class, objTypes[0]);
    assertEquals(Object.class, objTypes[1]);
  }

  @Test
  public void testResolve() throws Throwable {
    TypeVariable<?> tv = GenericClass.class.getTypeParameters()[0];
    ParameterizedType context = $Gson$Types.newParameterizedTypeWithOwner(null, GenericClass.class, String.class);
    
    Type resolved = $Gson$Types.resolve(context, GenericClass.class, tv);
    assertEquals(String.class, resolved);

    // Test resolving Class array
    Class<?> arrayClass = String[].class;
    assertEquals(String[].class, $Gson$Types.resolve(context, GenericClass.class, arrayClass));

    // Test resolving GenericArrayType
    GenericArrayType gat = $Gson$Types.arrayOf(tv);
    Type resolvedGat = $Gson$Types.resolve(context, GenericClass.class, gat);
    assertTrue(resolvedGat instanceof GenericArrayType);
    assertEquals(String[].class, $Gson$Types.getRawType(resolvedGat));

    // Test resolving ParameterizedType with owner and args change
    ParameterizedType pt = $Gson$Types.newParameterizedTypeWithOwner(null, List.class, tv);
    Type resolvedPt = $Gson$Types.resolve(context, GenericClass.class, pt);
    assertTrue(resolvedPt instanceof ParameterizedType);
    assertEquals(String.class, ((ParameterizedType) resolvedPt).getActualTypeArguments()[0]);

    // Test resolving WildcardType lower bound / upper bound
    WildcardType wtLower = $Gson$Types.supertypeOf(tv);
    Type resolvedWtLower = $Gson$Types.resolve(context, GenericClass.class, wtLower);
    assertTrue(resolvedWtLower instanceof WildcardType);
    assertEquals(String.class, ((WildcardType) resolvedWtLower).getLowerBounds()[0]);

    WildcardType wtUpper = $Gson$Types.subtypeOf(tv);
    Type resolvedWtUpper = $Gson$Types.resolve(context, GenericClass.class, wtUpper);
    assertTrue(resolvedWtUpper instanceof WildcardType);
    assertEquals(String.class, ((WildcardType) resolvedWtUpper).getUpperBounds()[0]);

    // Unhandled type / base case
    assertEquals(Object.class, $Gson$Types.resolve(context, GenericClass.class, Object.class));
  }

  @Test
  public void testResolveTypeVariableNoDeclaration() throws Throwable {
    TypeVariable<?> fakeTv = new TypeVariable<GenericDeclaration>() {
      public Type[] getBounds() { return new Type[0]; }
      public GenericDeclaration getGenericDeclaration() { return null; }
      public String getName() { return "X"; }
      public <T extends java.lang.annotation.Annotation> T getAnnotation(Class<T> annotationClass) { return null; }
      public java.lang.annotation.Annotation[] getAnnotations() { return new java.lang.annotation.Annotation[0]; }
      public java.lang.annotation.Annotation[] getDeclaredAnnotations() { return new java.lang.annotation.Annotation[0]; }
    };
    assertEquals(fakeTv, $Gson$Types.resolve(Object.class, Object.class, fakeTv));
  }

  @Test
  public void testCheckNotPrimitive() throws Throwable {
    $Gson$Types.checkNotPrimitive(String.class);
    // Should not throw for non-class types either
    $Gson$Types.checkNotPrimitive($Gson$Types.subtypeOf(String.class));
  }

  @Test(expected = IllegalArgumentException.class)
  public void testCheckNotPrimitiveWithPrimitive() throws Throwable {
    $Gson$Types.checkNotPrimitive(int.class);
  }

  @Test
  public void testParameterizedTypeImplMethods() throws Throwable {
    ParameterizedType pt = $Gson$Types.newParameterizedTypeWithOwner(null, Map.class, String.class, Integer.class);
    assertNotNull(pt.getOwnerType()); // Map is static or top-level, but let's check ownerType handling
    assertNotNull(pt.getRawType());
    assertNotNull(pt.getActualTypeArguments());
    assertNotEquals(0, pt.hashCode());
    assertNotNull(pt.toString());

    // Test parameterized type with owner
    Class<?> enclosing = Map.class;
    ParameterizedType ptWithOwner = $Gson$Types.newParameterizedTypeWithOwner(enclosing, Map.Entry.class, String.class, String.class);
    assertNotNull(ptWithOwner.getOwnerType());
    assertEquals(Map.Entry.class, ptWithOwner.getRawType());
    assertEquals(2, ptWithOwner.getActualTypeArguments().length);
  }

  @Test(expected = IllegalArgumentException.class)
  public void testParameterizedTypeImplInvalidOwner() throws Throwable {
    // Non-static inner class requiring an owner type but none provided
    class Inner {
    }
    // Passing null owner for non-static inner class
    new $Gson$Types.ParameterizedTypeImpl(null, Inner.class);
  }

  @Test
  public void testGenericArrayTypeImplMethods() throws Throwable {
    GenericArrayType gat = $Gson$Types.arrayOf(String.class);
    assertEquals(gat, gat);
    assertFalse(gat.equals(null));
    assertFalse(gat.equals(String.class));
    assertEquals(gat.hashCode(), gat.hashCode());
    assertEquals("java.lang.String[]", gat.toString());
  }

  @Test
  public void testWildcardTypeImplMethods() throws Throwable {
    WildcardType wt = $Gson$Types.subtypeOf(String.class);
    assertEquals(wt, wt);
    assertFalse(wt.equals(null));
    assertFalse(wt.equals(String.class));
    assertNotEquals(0, wt.hashCode());
    assertEquals("? extends java.lang.String", wt.toString());

    WildcardType wtSuper = $Gson$Types.supertypeOf(String.class);
    assertEquals("? super java.lang.String", wtSuper.toString());

    WildcardType wtObj = $Gson$Types.subtypeOf(Object.class);
    assertEquals("?", wtObj.toString());
  }

  @Test(expected = IllegalArgumentException.class)
  public void testWildcardTypeImplTooManyLowerBounds() throws Throwable {
    new $Gson$Types.WildcardTypeImpl(new Type[] { Object.class }, new Type[] { String.class, Integer.class });
  }

  @Test(expected = IllegalArgumentException.class)
  public void testWildcardTypeImplZeroUpperBounds() throws Throwable {
    new $Gson$Types.WildcardTypeImpl(new Type[] {}, new Type[] {});
  }

  @Test(expected = IllegalArgumentException.class)
  public void testWildcardTypeImplLowerBoundWithNonObjectUpper() throws Throwable {
    new $Gson$Types.WildcardTypeImpl(new Type[] { String.class }, new Type[] { Integer.class });
  }
}