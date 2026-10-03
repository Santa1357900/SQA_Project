package org.mockito.internal.util.reflection;

import org.junit.Test;
import static org.junit.Assert.*;

import java.lang.reflect.Field;
import java.util.List;
import java.util.Map;

public class GenericMasterTest {

    private List<String> genericListField;
    private List genericRawField;
    private String nonGenericField;
    private Map<String, Integer> genericMapField;
    private List<List<String>> nestedGenericField;

    @Test
    public void testGetGenericTypeWithParameterizedType() throws Throwable {
        GenericMaster master = new GenericMaster();
        Field field = GenericMasterTest.class.getDeclaredField("genericListField");
        Class<?> result = master.getGenericType(field);
        assertEquals(String.class, result);
    }

    @Test
    public void testGetGenericTypeWithRawType() throws Throwable {
        GenericMaster master = new GenericMaster();
        Field field = GenericMasterTest.class.getDeclaredField("genericRawField");
        Class<?> result = master.getGenericType(field);
        assertEquals(Object.class, result);
    }

    @Test
    public void testGetGenericTypeWithNonGenericField() throws Throwable {
        GenericMaster master = new GenericMaster();
        Field field = GenericMasterTest.class.getDeclaredField("nonGenericField");
        Class<?> result = master.getGenericType(field);
        assertEquals(Object.class, result);
    }

    @Test
    public void testGetGenericTypeWithMultipleTypeArguments() throws Throwable {
        GenericMaster master = new GenericMaster();
        Field field = GenericMasterTest.class.getDeclaredField("genericMapField");
        Class<?> result = master.getGenericType(field);
        assertEquals(String.class, result);
    }

    @Test
    public void testGetGenericTypeWithNestedGenerics() throws Throwable {
        GenericMaster master = new GenericMaster();
        Field field = GenericMasterTest.class.getDeclaredField("nestedGenericField");
        Class<?> result = master.getGenericType(field);
        assertEquals(List.class, result);
    }

    @Test(expected = NullPointerException.class)
    public void testGetGenericTypeWithNullField() throws Throwable {
        GenericMaster master = new GenericMaster();
        master.getGenericType(null);
    }
}