package com.google.gson;

import org.junit.Test;
import static org.junit.Assert.*;

import java.lang.reflect.Field;
import java.lang.reflect.Type;
import java.util.List;
import java.util.Collection;

public class TypeInfoFactoryTest {

  // Dummy class for field reflection tests
  private static class DummyClass<T> {
    T genericField;
    List<T> parameterizedField;
    String[] arrayField;
    String normalField;
  }

  private static class SubDummyClass extends DummyClass<Integer> {
  }

  @Test
  public void testGetTypeInfoForArrayValid() throws Throwable {
    Type arrayType = String[].class;
    TypeInfoArray typeInfoArray = TypeInfoFactory.getTypeInfoForArray(arrayType);
    assertNotNull(typeInfoArray);
  }

  @Test
  public void testGetTypeInfoForArrayInvalid() throws Throwable {
    try {
      TypeInfoFactory.getTypeInfoForArray(String.class);
      fail("Expected IllegalArgumentException");
    } catch (IllegalArgumentException e) {
      assertTrue(true);
    }
  }

  @Test
  public void testGetTypeInfoForFieldNormal() throws Throwable {
    Field field = DummyClass.class.getDeclaredField("normalField");
    TypeInfo typeInfo = TypeInfoFactory.getTypeInfoForField(field, DummyClass.class);
    assertNotNull(typeInfo);
    assertEquals(String.class, typeInfo.getType());
  }

  @Test
  public void testGetTypeInfoForFieldArray() throws Throwable {
    Field field = DummyClass.class.getDeclaredField("arrayField");
    TypeInfo typeInfo = TypeInfoFactory.getTypeInfoForField(field, DummyClass.class);
    assertNotNull(typeInfo);
  }

  @Test
  public void testGetTypeInfoForFieldTypeVariableWithParameterizedParent() throws Throwable {
    Field field = DummyClass.class.getDeclaredField("genericField");
    Type parentType = SubDummyClass.class.getGenericSuperclass();
    TypeInfo typeInfo = TypeInfoFactory.getTypeInfoForField(field, parentType);
    assertNotNull(typeInfo);
    assertEquals(Integer.class, typeInfo.getType());
  }

  @Test
  public void testGetTypeInfoForFieldTypeVariableWithoutParameterizedParent() throws Throwable {
    Field field = DummyClass.class.getDeclaredField("genericField");
    try {
      TypeInfoFactory.getTypeInfoForField(field, DummyClass.class);
      fail("Expected UnsupportedOperationException");
    } catch (UnsupportedOperationException e) {
      assertTrue(e.getMessage().contains("Expecting parameterized type"));
    }
  }

  @Test
  public void testGetTypeInfoForFieldParameterized() throws Throwable {
    Field field = DummyClass.class.getDeclaredField("parameterizedField");
    Type parentType = SubDummyClass.class.getGenericSuperclass();
    TypeInfo typeInfo = TypeInfoFactory.getTypeInfoForField(field, parentType);
    assertNotNull(typeInfo);
  }

  @Test
  public void testGetTypeInfoForFieldWildcard() throws Throwable {
    // Defining a dummy class with wildcard field
    class WildcardDummy {
      Collection<? extends Number> wildcardField;
    }
    Field field = WildcardDummy.class.getDeclaredField("wildcardField");
    TypeInfo typeInfo = TypeInfoFactory.getTypeInfoForField(field, WildcardDummy.class);
    assertNotNull(typeInfo);
  }

  @Test
  public void testGetTypeInfoForFieldGenericArray() throws Throwable {
    class GenericArrayDummy<T> {
      T[] genericArrayField;
    }
    Field field = GenericArrayDummy.class.getDeclaredField("genericArrayField");
    try {
      TypeInfoFactory.getTypeInfoForField(field, GenericArrayDummy.class);
      fail("Expected UnsupportedOperationException or IllegalArgumentException");
    } catch (RuntimeException e) {
      assertNotNull(e);
    }
  }
}