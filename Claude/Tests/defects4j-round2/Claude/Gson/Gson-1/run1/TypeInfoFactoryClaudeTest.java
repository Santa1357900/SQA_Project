package com.google.gson;

import java.lang.reflect.Field;
import java.lang.reflect.Type;
import java.util.List;
import java.util.Map;

import org.junit.Test;
import static org.junit.Assert.*;

import com.google.gson.reflect.TypeToken;

public class TypeInfoFactoryClaudeTest {

  static class SimpleHolder {
    public String stringField;
    public int intField;
    public String[] stringArrayField;
  }

  static class ListHolder {
    public List<String> stringList;
    public Map<String, Integer> mapField;
    public Map<String, List<Integer>> nestedField;
    public List<? extends Number> wildcardExtendsField;
    public List<? super Integer> wildcardSuperField;
  }

  static class GenericHolder<T> {
    public T tField;
    public List<T> listOfT;
    public T[] arrayField;
  }

  static class TwoTypeVarHolder<A, B> {
    public B second;
  }

  static class BoundedHolder<T extends Number> {
    public T boundedField;
  }

  static class Base<A> {
    public A value;
  }

  static class Derived<B> extends Base<B> {
  }

  static class ArrayOfParamTypeHolder {
    public List<String>[] listArr;
  }

  // getTypeInfoForArray: Class array (Object array) -> success, non-null
  @Test
  public void testGetTypeInfoForArray_withObjectArrayClass_returnsNonNullTypeInfoArray() throws Throwable {
    TypeInfoArray result = TypeInfoFactory.getTypeInfoForArray(String[].class);
    assertNotNull(result);
  }

  // getTypeInfoForArray: primitive array -> success, non-null
  @Test
  public void testGetTypeInfoForArray_withPrimitiveArrayClass_returnsNonNullTypeInfoArray() throws Throwable {
    TypeInfoArray result = TypeInfoFactory.getTypeInfoForArray(int[].class);
    assertNotNull(result);
  }

  // getTypeInfoForArray: multi-dimensional array -> success, non-null
  @Test
  public void testGetTypeInfoForArray_withMultiDimensionalArrayClass_returnsNonNullTypeInfoArray() throws Throwable {
    TypeInfoArray result = TypeInfoFactory.getTypeInfoForArray(int[][].class);
    assertNotNull(result);
  }

  // getTypeInfoForArray: Preconditions.checkArgument branch false -> IllegalArgumentException
  @Test
  public void testGetTypeInfoForArray_withNonArrayClass_throwsIllegalArgumentException() throws Throwable {
    try {
      TypeInfoFactory.getTypeInfoForArray(String.class);
      fail("expected IllegalArgumentException");
    } catch (IllegalArgumentException expected) {
    }
  }

  // getTypeInfoForArray: primitive non-array type -> IllegalArgumentException
  @Test
  public void testGetTypeInfoForArray_withPrimitiveNonArrayType_throwsIllegalArgumentException() throws Throwable {
    try {
      TypeInfoFactory.getTypeInfoForArray(int.class);
      fail("expected IllegalArgumentException");
    } catch (IllegalArgumentException expected) {
    }
  }

  // getActualType: typeToEvaluate instanceof Class branch (String field)
  @Test
  public void testGetTypeInfoForField_withStringField_returnsNonNullTypeInfo() throws Throwable {
    Field f = SimpleHolder.class.getField("stringField");
    TypeInfo result = TypeInfoFactory.getTypeInfoForField(f, SimpleHolder.class);
    assertNotNull(result);
  }

  // getActualType: Class branch with primitive int field
  @Test
  public void testGetTypeInfoForField_withPrimitiveIntField_returnsNonNullTypeInfo() throws Throwable {
    Field f = SimpleHolder.class.getField("intField");
    TypeInfo result = TypeInfoFactory.getTypeInfoForField(f, SimpleHolder.class);
    assertNotNull(result);
  }

  // getActualType: Class branch with array-typed field (String[] is a Class, not GenericArrayType)
  @Test
  public void testGetTypeInfoForField_withArrayClassField_returnsNonNullTypeInfo() throws Throwable {
    Field f = SimpleHolder.class.getField("stringArrayField");
    TypeInfo result = TypeInfoFactory.getTypeInfoForField(f, SimpleHolder.class);
    assertNotNull(result);
  }

  // getActualType: ParameterizedType branch, single type argument (Class)
  @Test
  public void testGetTypeInfoForField_withParameterizedListField_returnsNonNullTypeInfo() throws Throwable {
    Field f = ListHolder.class.getField("stringList");
    TypeInfo result = TypeInfoFactory.getTypeInfoForField(f, ListHolder.class);
    assertNotNull(result);
  }

  // getActualType: ParameterizedType branch, loop over 2 actual type arguments (extractRealTypes)
  @Test
  public void testGetTypeInfoForField_withParameterizedMapField_returnsNonNullTypeInfo() throws Throwable {
    Field f = ListHolder.class.getField("mapField");
    TypeInfo result = TypeInfoFactory.getTypeInfoForField(f, ListHolder.class);
    assertNotNull(result);
  }

  // getActualType: nested ParameterizedType inside actual type arguments (recursive extractRealTypes)
  @Test
  public void testGetTypeInfoForField_withNestedParameterizedField_returnsNonNullTypeInfo() throws Throwable {
    Field f = ListHolder.class.getField("nestedField");
    TypeInfo result = TypeInfoFactory.getTypeInfoForField(f, ListHolder.class);
    assertNotNull(result);
  }

  // getActualType: GenericArrayType branch, component resolves via TypeVariable to a Class -> wrapWithArray path
  @Test
  public void testGetTypeInfoForField_withGenericArrayFieldResolvingToClass_returnsNonNullTypeInfo() throws Throwable {
    Field f = GenericHolder.class.getField("arrayField");
    Type parentType = new TypeToken<GenericHolder<String>>(){}.getType();
    TypeInfo result = TypeInfoFactory.getTypeInfoForField(f, parentType);
    assertNotNull(result);
  }

  // getActualType: GenericArrayType branch, component is already a ParameterizedType (no type variable)
  @Test
  public void testGetTypeInfoForField_withGenericArrayOfParameterizedComponent_returnsNonNullTypeInfo() throws Throwable {
    Field f = ArrayOfParamTypeHolder.class.getField("listArr");
    TypeInfo result = TypeInfoFactory.getTypeInfoForField(f, ArrayOfParamTypeHolder.class);
    assertNotNull(result);
  }

  // getActualType: TypeVariable branch, parentType is ParameterizedType, direct declaration -> resolves successfully
  @Test
  public void testGetTypeInfoForField_withTypeVariableFieldAndParameterizedParent_returnsNonNullTypeInfo() throws Throwable {
    Field f = GenericHolder.class.getField("tField");
    Type parentType = new TypeToken<GenericHolder<String>>(){}.getType();
    TypeInfo result = TypeInfoFactory.getTypeInfoForField(f, parentType);
    assertNotNull(result);
  }

  // getActualType: TypeVariable branch, parentType NOT ParameterizedType -> UnsupportedOperationException
  @Test
  public void testGetTypeInfoForField_withTypeVariableFieldAndRawParent_throwsUnsupportedOperationException() throws Throwable {
    Field f = GenericHolder.class.getField("tField");
    try {
      TypeInfoFactory.getTypeInfoForField(f, GenericHolder.class);
      fail("expected UnsupportedOperationException");
    } catch (UnsupportedOperationException expected) {
    }
  }

  // getIndex: loop iterates past first element before finding a match (second type variable)
  @Test
  public void testGetTypeInfoForField_withSecondTypeVariableIndex_returnsNonNullTypeInfo() throws Throwable {
    Field f = TwoTypeVarHolder.class.getField("second");
    Type parentType = new TypeToken<TwoTypeVarHolder<String, Integer>>(){}.getType();
    TypeInfo result = TypeInfoFactory.getTypeInfoForField(f, parentType);
    assertNotNull(result);
  }

  // getActualType: TypeVariable with an upper bound (T extends Number) resolves via actual type argument
  @Test
  public void testGetTypeInfoForField_withBoundedTypeVariableField_returnsNonNullTypeInfo() throws Throwable {
    Field f = BoundedHolder.class.getField("boundedField");
    Type parentType = new TypeToken<BoundedHolder<Integer>>(){}.getType();
    TypeInfo result = TypeInfoFactory.getTypeInfoForField(f, parentType);
    assertNotNull(result);
  }

  // getActualType: ParameterizedType containing a TypeVariable actual type argument (List<T>)
  @Test
  public void testGetTypeInfoForField_withListOfTypeVariableField_returnsNonNullTypeInfo() throws Throwable {
    Field f = GenericHolder.class.getField("listOfT");
    Type parentType = new TypeToken<GenericHolder<String>>(){}.getType();
    TypeInfo result = TypeInfoFactory.getTypeInfoForField(f, parentType);
    assertNotNull(result);
  }

  // getActualType: WildcardType branch with "extends" bound resolves via getUpperBounds()[0]
  @Test
  public void testGetTypeInfoForField_withWildcardExtendsField_returnsNonNullTypeInfo() throws Throwable {
    Field f = ListHolder.class.getField("wildcardExtendsField");
    TypeInfo result = TypeInfoFactory.getTypeInfoForField(f, ListHolder.class);
    assertNotNull(result);
  }

  // getActualType: WildcardType branch with "super" bound (upper bound defaults to Object)
  @Test
  public void testGetTypeInfoForField_withWildcardSuperField_returnsNonNullTypeInfo() throws Throwable {
    Field f = ListHolder.class.getField("wildcardSuperField");
    TypeInfo result = TypeInfoFactory.getTypeInfoForField(f, ListHolder.class);
    assertNotNull(result);
  }

  // Bug hunt: TypeVariable declared in a generic superclass must still resolve correctly
  // when the concrete parent type is the parameterized subclass (per javadoc contract:
  // "it evaluates the real type" for any TypeVariable-based field).
  @Test
  public void testGetTypeInfoForField_typeVariableDeclaredInSuperclass_resolvesWithoutException() throws Throwable {
    Field f = Base.class.getField("value");
    Type parentType = new TypeToken<Derived<String>>(){}.getType();
    TypeInfo result = TypeInfoFactory.getTypeInfoForField(f, parentType);
    assertNotNull(result);
  }

  // getTypeInfoForField: null Field parameter -> NullPointerException on f.getGenericType()
  @Test
  public void testGetTypeInfoForField_withNullField_throwsNullPointerException() throws Throwable {
    try {
      TypeInfoFactory.getTypeInfoForField(null, SimpleHolder.class);
      fail("expected NullPointerException");
    } catch (NullPointerException expected) {
    }
  }
}
