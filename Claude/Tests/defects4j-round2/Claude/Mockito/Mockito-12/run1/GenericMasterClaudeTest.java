package org.mockito.internal.util.reflection;

import java.lang.reflect.Field;
import java.util.List;
import java.util.ArrayList;
import java.util.Map;
import java.util.HashMap;
import java.util.Set;

import org.junit.Before;
import org.junit.Test;
import static org.junit.Assert.*;

public class GenericMasterClaudeTest {

    private GenericMaster genericMaster;

    @Before
    public void setUp() throws Throwable {
        genericMaster = new GenericMaster();
    }

    private static class Box<T> {
        private T value;
    }

    private static class Sample {
        private List<String> stringListField;
        private List rawListField;
        private int intField;
        private String stringField;
        private int[] intArrayField;
        private Map<String, Integer> stringIntMapField;
        private List<Integer> integerListField;
        private Set<Double> doubleSetField;
        private List<List<String>> nestedListField;
        private List<?> wildcardListField;
        private Comparable<String> comparableField;
        private Box<String> boxOfStringField;
        private ArrayList<String> arrayListOfStringField;
        private HashMap<Long, String> hashMapOfLongStringField;
        private Set<String> stringSetField;
        private List<Boolean> booleanListField;
        private Object objectField;
    }

    // Covers: generic is ParameterizedType, actual type argument is a Class -> List<String> returns String.class
    @Test
    public void testGetGenericType_parameterizedListOfString_returnsStringClass() throws Throwable {
        Field field = Sample.class.getDeclaredField("stringListField");
        Class result = genericMaster.getGenericType(field);
        assertEquals(String.class, result);
    }

    // Covers: ParameterizedType branch with List<Integer>
    @Test
    public void testGetGenericType_parameterizedListOfInteger_returnsIntegerClass() throws Throwable {
        Field field = Sample.class.getDeclaredField("integerListField");
        Class result = genericMaster.getGenericType(field);
        assertEquals(Integer.class, result);
    }

    // Covers: ParameterizedType branch with Set<Double>
    @Test
    public void testGetGenericType_parameterizedSetOfDouble_returnsDoubleClass() throws Throwable {
        Field field = Sample.class.getDeclaredField("doubleSetField");
        Class result = genericMaster.getGenericType(field);
        assertEquals(Double.class, result);
    }

    // Covers: ParameterizedType with two type parameters, index 0 taken -> Map<String,Integer> returns key type String.class
    @Test
    public void testGetGenericType_parameterizedMapTwoTypeArguments_returnsFirstTypeArgumentClass() throws Throwable {
        Field field = Sample.class.getDeclaredField("stringIntMapField");
        Class result = genericMaster.getGenericType(field);
        assertEquals(String.class, result);
    }

    // Covers: not ParameterizedType branch (raw type usage of a generic class) -> Object.class
    @Test
    public void testGetGenericType_rawListTypeNoGenerics_returnsObjectClass() throws Throwable {
        Field field = Sample.class.getDeclaredField("rawListField");
        Class result = genericMaster.getGenericType(field);
        assertEquals(Object.class, result);
    }

    // Covers: not ParameterizedType branch (primitive field) -> Object.class
    @Test
    public void testGetGenericType_primitiveIntField_returnsObjectClass() throws Throwable {
        Field field = Sample.class.getDeclaredField("intField");
        Class result = genericMaster.getGenericType(field);
        assertEquals(Object.class, result);
    }

    // Covers: not ParameterizedType branch (non-generic reference type) -> Object.class
    @Test
    public void testGetGenericType_nonGenericStringField_returnsObjectClass() throws Throwable {
        Field field = Sample.class.getDeclaredField("stringField");
        Class result = genericMaster.getGenericType(field);
        assertEquals(Object.class, result);
    }

    // Covers: not ParameterizedType branch (primitive array field) -> Object.class
    @Test
    public void testGetGenericType_primitiveArrayField_returnsObjectClass() throws Throwable {
        Field field = Sample.class.getDeclaredField("intArrayField");
        Class result = genericMaster.getGenericType(field);
        assertEquals(Object.class, result);
    }

    // Covers: not ParameterizedType branch (TypeVariable from enclosing generic class) -> Object.class
    @Test
    public void testGetGenericType_typeVariableField_returnsObjectClass() throws Throwable {
        Field field = Box.class.getDeclaredField("value");
        Class result = genericMaster.getGenericType(field);
        assertEquals(Object.class, result);
    }

    // Covers: ParameterizedType branch with a user-defined generic class Box<String>
    @Test
    public void testGetGenericType_customParameterizedClass_returnsTypeArgumentClass() throws Throwable {
        Field field = Sample.class.getDeclaredField("boxOfStringField");
        Class result = genericMaster.getGenericType(field);
        assertEquals(String.class, result);
    }

    // Covers: ParameterizedType branch on a generic interface (Comparable<String>)
    @Test
    public void testGetGenericType_parameterizedInterfaceField_returnsTypeArgumentClass() throws Throwable {
        Field field = Sample.class.getDeclaredField("comparableField");
        Class result = genericMaster.getGenericType(field);
        assertEquals(String.class, result);
    }

    // Bug-catching test: nested generics (List<List<String>>) - per the code's own comment
    // "in case of nested generics we don't go deep" the method must fall back gracefully to Object.class,
    // not throw ClassCastException when casting a ParameterizedType actual argument to Class.
    @Test
    public void testGetGenericType_nestedGenericListArgument_fallsBackToObjectClassInsteadOfThrowing() throws Throwable {
        Field field = Sample.class.getDeclaredField("nestedListField");
        Class result = genericMaster.getGenericType(field);
        assertEquals(Object.class, result);
    }

    // Bug-catching test: wildcard type argument (List<?>) is not a Class either, so per the "don't go deep"
    // contract it must fall back to Object.class instead of throwing ClassCastException.
    @Test
    public void testGetGenericType_wildcardTypeArgument_fallsBackToObjectClassInsteadOfThrowing() throws Throwable {
        Field field = Sample.class.getDeclaredField("wildcardListField");
        Class result = genericMaster.getGenericType(field);
        assertEquals(Object.class, result);
    }

    // Covers: ParameterizedType branch with a concrete generic collection class (ArrayList<String>)
    @Test
    public void testGetGenericType_concreteArrayListOfString_returnsStringClass() throws Throwable {
        Field field = Sample.class.getDeclaredField("arrayListOfStringField");
        Class result = genericMaster.getGenericType(field);
        assertEquals(String.class, result);
    }

    // Covers: ParameterizedType branch with HashMap<Long, String>, first type argument is the key type
    @Test
    public void testGetGenericType_hashMapOfLongAndString_returnsLongClass() throws Throwable {
        Field field = Sample.class.getDeclaredField("hashMapOfLongStringField");
        Class result = genericMaster.getGenericType(field);
        assertEquals(Long.class, result);
    }

    // Covers: ParameterizedType branch with Set<String>
    @Test
    public void testGetGenericType_setOfString_returnsStringClass() throws Throwable {
        Field field = Sample.class.getDeclaredField("stringSetField");
        Class result = genericMaster.getGenericType(field);
        assertEquals(String.class, result);
    }

    // Covers: ParameterizedType branch with List<Boolean>
    @Test
    public void testGetGenericType_listOfBoolean_returnsBooleanClass() throws Throwable {
        Field field = Sample.class.getDeclaredField("booleanListField");
        Class result = genericMaster.getGenericType(field);
        assertEquals(Boolean.class, result);
    }

    // Covers: not ParameterizedType branch for a plain Object-typed field -> Object.class
    @Test
    public void testGetGenericType_objectTypedField_returnsObjectClass() throws Throwable {
        Field field = Sample.class.getDeclaredField("objectField");
        Class result = genericMaster.getGenericType(field);
        assertEquals(Object.class, result);
    }

    // Covers: method is stateless / deterministic across repeated calls on the same field
    @Test
    public void testGetGenericType_sameFieldCalledTwice_returnsConsistentResult() throws Throwable {
        Field field = Sample.class.getDeclaredField("integerListField");
        Class first = genericMaster.getGenericType(field);
        Class second = genericMaster.getGenericType(field);
        assertEquals(Integer.class, first);
        assertEquals(Integer.class, second);
    }

    // Covers: results are independent per field (no shared mutable state across calls)
    @Test
    public void testGetGenericType_differentFieldsProduceIndependentResults() throws Throwable {
        Field listField = Sample.class.getDeclaredField("stringListField");
        Field mapField = Sample.class.getDeclaredField("stringIntMapField");
        Class listResult = genericMaster.getGenericType(listField);
        Class mapResult = genericMaster.getGenericType(mapField);
        assertEquals(String.class, listResult);
        assertEquals(String.class, mapResult);
    }

    // Covers: behavior is consistent across different GenericMaster instances (no instance state)
    @Test
    public void testGetGenericType_multipleInstances_returnSameResultForSameField() throws Throwable {
        Field field = Sample.class.getDeclaredField("doubleSetField");
        GenericMaster anotherMaster = new GenericMaster();
        Class result1 = genericMaster.getGenericType(field);
        Class result2 = anotherMaster.getGenericType(field);
        assertEquals(Double.class, result1);
        assertEquals(Double.class, result2);
    }
}
