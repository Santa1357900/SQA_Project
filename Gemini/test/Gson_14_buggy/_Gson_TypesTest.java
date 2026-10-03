package com.google.gson.internal;

import org.junit.Test;

import java.io.Serializable;
import java.lang.reflect.GenericArrayType;
import java.lang.reflect.ParameterizedType;
import java.lang.reflect.Type;
import java.lang.reflect.WildcardType;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Properties;
import java.util.Set;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

public class $Gson$TypesTest {

  private static class SampleClass<T> {
    public T sampleField;
  }

  private static class SubSampleClass extends SampleClass<String> {
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
    assertEquals("? extends java.lang.String", wt.toString());
  }

  @Test
  public void testSupertypeOf() throws Throwable {
    WildcardType wt = $Gson$Types.supertypeOf(String.class);
    assertNotNull(wt);
    assertEquals(1, wt.getUpperBounds().length);
    assertEquals(Object.class, wt.getUpperBounds()[0]);
    assertEquals(1, wt.getLowerBounds().length);
    assertEquals(String.class, wt.getLowerBounds()[0]);
    assertEquals("? super java.lang.String", wt.toString());
  }

  @Test
  public void testCanonicalize() throws Throwable {
    Class<?> clazz = String.class;
    assertEquals(clazz, $Gson$Types.canonicalize(clazz));

    Class<?> arrayClass = String[].class;
    Type canonicalArray = $Gson$Types.canonicalize(arrayClass);
    assertTrue(canonicalArray instanceof GenericArrayType);

    ParameterizedType pt = $Gson$Types.newParameterizedTypeWithOwner(null, List.class, String.class);
    assertEquals(pt, $Gson$Types.canonicalize(pt));

    GenericArrayType gat = $Gson$Types.arrayOf(String.class);
    assertEquals(gat, $Gson$Types.canonicalize(gat));

    WildcardType wt = $Gson$Types.subtypeOf(String.class);
    assertEquals(wt, $Gson$Types.canonicalize(wt));

    Type otherType = new Type() {
      @Override
      public String toString() {
        return "OtherType";
      }
    };
    assertEquals(otherType, $Gson$Types.canonicalize(otherType));
  }

  @Test
  public void testGetRawType() throws Throwable {
    assertEquals(String.class, $Gson$Types.getRawType(String.class));

    ParameterizedType pt = $Gson$Types.newParameterizedTypeWithOwner(null, List.class, String.class);
    assertEquals(List.class, $Gson$Types.getRawType(pt));

    GenericArrayType gat = $Gson$Types.arrayOf(String.class);
    assertEquals(String[].class, $Gson$Types.getRawType(gat));

    WildcardType wt = $Gson$Types.subtypeOf(String.class);
    assertEquals(String.class, $Gson$Types.getRawType(wt));

    TypeVariable<?> typeVar = SampleClass.class.getTypeParameters()[0];
    assertEquals(Object.class, $Gson$Types.getRawType(typeVar));

    try {
      $Gson$Types.getRawType(null);
      fail("Expected IllegalArgumentException");
    } catch (IllegalArgumentException e) {
      assertTrue(e.getMessage().contains("Expected a Class"));
    }

    try {
      $Gson$Types.getRawType(new Type() {
        @Override
        public String toString() {
          return "UnknownType";
        }
      });
      fail("Expected IllegalArgumentException");
    } catch (IllegalArgumentException e) {
      assertTrue(e.getMessage().contains("Expected a Class"));
    }
  }

  @Test
  public void testEqual() throws Throwable {
    assertTrue($Gson$Types.equal(null, null));
    String s = "test";
    assertTrue($Gson$Types.equal(s, s));
    assertTrue($Gson$Types.equal("test", "test"));
    assertFalse($Gson$Types.equal("test", "other"));
    assertFalse($Gson$Types.equal("test", null));
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

    ParameterizedType pt1 = $Gson$Types.newParameterizedTypeWithOwner(null, List.class, String.class);
    ParameterizedType pt2 = $Gson$Types.newParameterizedTypeWithOwner(null, List.class, String.class);
    ParameterizedType pt3 = $Gson$Types.newParameterizedTypeWithOwner(null, List.class, Integer.class);

    assertTrue($Gson$Types.equals(pt1, pt2));
    assertFalse($Gson$Types.equals(pt1, pt3));
    assertFalse($Gson$Types.equals(pt1, t1));

    GenericArrayType gat1 = $Gson$Types.arrayOf(String.class);
    GenericArrayType gat2 = $Gson$Types.arrayOf(String.class);
    GenericArrayType gat3 = $Gson$Types.arrayOf(Integer.class);

    assertTrue($Gson$Types.equals(gat1, gat2));
    assertFalse($Gson$Types.equals(gat1, gat3));
    assertFalse($Gson$Types.equals(gat1, t1));

    WildcardType wt1 = $Gson$Types.subtypeOf(String.class);
    WildcardType wt2 = $Gson$Types.subtypeOf(String.class);
    WildcardType wt3 = $Gson$Types.subtypeOf(Integer.class);

    assertTrue($Gson$Types.equals(wt1, wt2));
    assertFalse($Gson$Types.equals(wt1, wt3));
    assertFalse($Gson$Types.equals(wt1, t1));

    TypeVariable<?> tv1 = SampleClass.class.getTypeParameters()[0];
    TypeVariable<?> tv2 = SampleClass.class.getTypeParameters()[0];
    TypeVariable<?> tv3 = SubSampleClass.class.getTypeParameters().length > 0 ? SubSampleClass.class.getTypeParameters()[0] : tv1;

    assertTrue($Gson$Types.equals(tv1, tv2));
    assertFalse($Gson$Types.equals(tv1, t1));

    Type unsupported1 = new Type() {
      @Override public String toString() { return "A"; }
    };
    Type unsupported2 = new Type() {
      @Override public String toString() { return "B"; }
    };
    assertFalse($Gson$Types.equals(unsupported1, unsupported2));
  }

  @Test
  public void testHashCodeOrZero() throws Throwable {
    assertEquals(0, $Gson$Types.hashCodeOrZero(null));
    String s = "hello";
    assertEquals(s.hashCode(), $Gson$Types.hashCodeOrZero(s));
  }

  @Test
  public void testTypeToString() throws Throwable {
    assertEquals("java.lang.String", $Gson$Types.typeToString(String.class));
    ParameterizedType pt = $Gson$Types.newParameterizedTypeWithOwner(null, List.class, String.class);
    assertEquals(pt.toString(), $Gson$Types.typeToString(pt));
  }

  @Test
  public void testGetCollectionElementType() throws Throwable {
    ParameterizedType listType = $Gson$Types.newParameterizedTypeWithOwner(null, List.class, String.class);
    Type elemType = $Gson$Types.getCollectionElementType(listType, List.class);
    assertEquals(String.class, elemType);

    Type plainColl = Collection.class;
    Type plainElem = $Gson$Types.getCollectionElementType(plainColl, Collection.class);
    assertEquals(Object.class, plainElem);
  }

  @Test
  public void testGetMapKeyAndValueTypes() throws Throwable {
    Type propsType = Properties.class;
    Type[] kvProps = $Gson$Types.getMapKeyAndValueTypes(propsType, Properties.class);
    assertEquals(2, kvProps.length);
    assertEquals(String.class, kvProps[0]);
    assertEquals(String.class, kvProps[1]);

    ParameterizedType mapType = $Gson$Types.newParameterizedTypeWithOwner(null, Map.class, String.class, Integer.class);
    Type[] kvMap = $Gson$Types.getMapKeyAndValueTypes(mapType, Map.class);
    assertEquals(2, kvMap.length);
    assertEquals(String.class, kvMap[0]);
    assertEquals(Integer.class, kvMap[1]);

    Type plainMap = Map.class;
    Type[] kvPlain = $Gson$Types.getMapKeyAndValueTypes(plainMap, Map.class);
    assertEquals(2, kvPlain.length);
    assertEquals(Object.class, kvPlain[0]);
    assertEquals(Object.class, kvPlain[1]);
  }

  @Test
  public void testResolve() throws Throwable {
    TypeVariable<?> tv = SampleClass.class.getTypeParameters()[0];
    ParameterizedType context = $Gson$Types.newParameterizedTypeWithOwner(null, SampleClass.class, String.class);
    Type resolved = $Gson$Types.resolve(context, SampleClass.class, tv);
    assertEquals(String.class, resolved);

    Type unresolvedTv = new TypeVariable<Class<SampleClass>>() {
      @Override public Type[] getBounds() { return new Type[] { Object.class }; }
      @Override public Class<SampleClass> getGenericDeclaration() { return SampleClass.class; }
      @Override public String getName() { return "UNKNOWN"; }
      @Override public <T extends java.lang.annotation.Annotation> T getAnnotation(Class<T> annotationClass) { return null; }
      @Override public java.lang.annotation.Annotation[] getAnnotations() { return new java.lang.annotation.Annotation[0]; }
      @Override public java.lang.annotation.Annotation[] getDeclaredAnnotations() { return new java.lang.annotation.Annotation[0]; }
    };
    assertEquals(unresolvedTv, $Gson$Types.resolve(context, SampleClass.class, unresolvedTv));

    Class<?> arrayClass = String[].class;
    assertEquals(String[].class, $Gson$Types.resolve(context, SampleClass.class, arrayClass));

    GenericArrayType gat = $Gson$Types.arrayOf(String.class);
    assertEquals(gat, $Gson$Types.resolve(context, SampleClass.class, gat));

    WildcardType wtLower = $Gson$Types.supertypeOf(String.class);
    assertEquals(wtLower, $Gson$Types.resolve(context, SampleClass.class, wtLower));

    WildcardType wtUpper = $Gson$Types.subtypeOf(String.class);
    assertEquals(wtUpper, $Gson$Types.resolve(context, SampleClass.class, wtUpper));

    assertEquals(Integer.class, $Gson$Types.resolve(context, SampleClass.class, Integer.class));
  }

  @Test
  public void testCheckNotPrimitive() throws Throwable {
    $Gson$Types.checkNotPrimitive(String.class);
    try {
      $Gson$Types.checkNotPrimitive(int.class);
      fail("Expected IllegalArgumentException");
    } catch (IllegalArgumentException e) {
      // Expected
    }
  }

  @Test
  public void testParameterizedTypeImplEdgeCases() throws Throwable {
    try {
      $Gson$Types.newParameterizedTypeWithOwner(null, Map.class);
      fail("Expected IllegalArgumentException due to missing owner for non-static inner/raw type requirements or checkArgument");
    } catch (IllegalArgumentException e) {
      // Expected
    }

    try {
      $Gson$Types.newParameterizedTypeWithOwner(null, List.class, (Type) null);
      fail("Expected NullPointerException or IllegalArgumentException");
    } catch (NullPointerException e) {
      // Expected
    }

    try {
      $Gson$Types.newParameterizedTypeWithOwner(null, List.class, int.class);
      fail("Expected IllegalArgumentException");
    } catch (IllegalArgumentException e) {
      // Expected
    }

    ParameterizedType pt = $Gson$Types.newParameterizedTypeWithOwner(null, List.class, String.class);
    assertNotNull(pt.hashCode());
    assertNotNull(pt.toString());
    assertFalse(pt.equals("not a parameterized type"));
  }

  @Test
  public void testWildcardTypeImplEdgeCases() throws Throwable {
    WildcardType wt = $Gson$Types.subtypeOf(String.class);
    assertNotNull(wt.hashCode());
    assertNotNull(wt.toString());
    assertFalse(wt.equals("not a wildcard type"));

    WildcardType wtSuper = $Gson$Types.supertypeOf(String.class);
    assertNotNull(wtSuper.hashCode());
    assertNotNull(wtSuper.toString());

    WildcardType wtPlain = $Gson$Types.subtypeOf(Object.class);
    assertEquals("?", wtPlain.toString());
  }

  @Test
  public void testGenericArrayTypeImplEdgeCases() throws Throwable {
    GenericArrayType gat = $Gson$Types.arrayOf(String.class);
    assertNotNull(gat.hashCode());
    assertNotNull(gat.toString());
    assertFalse(gat.equals("not a generic array type"));
  }

  @Test
  public void testGetArrayComponentType() throws Throwable {
    assertEquals(String.class, $Gson$Types.getArrayComponentType(String[].class));
    GenericArrayType gat = $Gson$Types.arrayOf(String.class);
    assertEquals(String.class, $Gson$Types.getArrayComponentType(gat));
  }

  @Test
  public void testGetSupertype() throws Throwable {
    Type supertype = $Gson$Types.getSupertype(List.class, List.class, Collection.class);
    assertNotNull(supertype);
  }
}