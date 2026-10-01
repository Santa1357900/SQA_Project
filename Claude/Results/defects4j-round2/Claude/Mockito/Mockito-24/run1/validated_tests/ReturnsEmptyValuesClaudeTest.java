package org.mockito.internal.stubbing.defaultanswers;

import org.junit.Before;
import org.junit.Test;
import static org.junit.Assert.*;

import org.mockito.invocation.InvocationOnMock;

import java.lang.reflect.InvocationHandler;
import java.lang.reflect.Method;
import java.lang.reflect.Proxy;

import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.SortedMap;
import java.util.SortedSet;
import java.util.TreeMap;
import java.util.TreeSet;

public class ReturnsEmptyValuesClaudeTest {

    private ReturnsEmptyValues returnsEmptyValues;

    private interface SampleMethods {
        int getInt();
        List getList();
        Map getMap();
        String getString();
    }

    @Before
    public void setUp() throws Throwable {
        returnsEmptyValues = new ReturnsEmptyValues();
    }

    private InvocationOnMock createInvocation(final Method method, final Object mock, final Object[] arguments) {
        return (InvocationOnMock) Proxy.newProxyInstance(
                InvocationOnMock.class.getClassLoader(),
                new Class[] { InvocationOnMock.class },
                new InvocationHandler() {
                    public Object invoke(Object proxy, Method calledMethod, Object[] args) throws Throwable {
                        String name = calledMethod.getName();
                        if ("getMethod".equals(name)) {
                            return method;
                        } else if ("getMock".equals(name)) {
                            return mock;
                        } else if ("getArguments".equals(name)) {
                            return arguments;
                        } else if ("hashCode".equals(name)) {
                            return Integer.valueOf(System.identityHashCode(proxy));
                        } else if ("equals".equals(name)) {
                            return Boolean.valueOf(proxy == args[0]);
                        } else if ("toString".equals(name)) {
                            return "InvocationOnMockStub";
                        }
                        return null;
                    }
                }
        );
    }

    // fields initialized by default constructor (used by both answer() and returnValueFor())
    @Test
    public void testFields_defaultConstructor_methodsGuruAndMockUtilAreInitialized() throws Throwable {
        assertNotNull(returnsEmptyValues.methodsGuru);
        assertNotNull(returnsEmptyValues.mockUtil);
    }

    // answer(): isCompareToMethod branch, same reference must yield 0 per javadoc contract (issue 184)
    @Test
    public void testAnswer_compareToMethodWithSameReference_returnsZeroPerJavadocContract() throws Throwable {
        Method compareTo = Comparable.class.getMethod("compareTo", Object.class);
        Object mock = new Object();
        InvocationOnMock invocation = createInvocation(compareTo, mock, new Object[] { mock });
        Object result = returnsEmptyValues.answer(invocation);
        assertEquals(Integer.valueOf(0), result);
    }



    // answer(): default branch delegates to returnValueFor for primitive int return type
    @Test
    public void testAnswer_regularMethodReturningInt_returnsZero() throws Throwable {
        Method m = SampleMethods.class.getMethod("getInt");
        InvocationOnMock invocation = createInvocation(m, new Object(), new Object[0]);
        Object result = returnsEmptyValues.answer(invocation);
        assertEquals(Integer.valueOf(0), result);
    }

    // answer(): default branch delegates to returnValueFor for List return type
    @Test
    public void testAnswer_regularMethodReturningList_returnsEmptyLinkedList() throws Throwable {
        Method m = SampleMethods.class.getMethod("getList");
        InvocationOnMock invocation = createInvocation(m, new Object(), new Object[0]);
        Object result = returnsEmptyValues.answer(invocation);
        assertTrue(result instanceof LinkedList);
        assertTrue(((List) result).isEmpty());
    }

    // answer(): default branch delegates to returnValueFor for Map return type
    @Test
    public void testAnswer_regularMethodReturningMap_returnsEmptyHashMap() throws Throwable {
        Method m = SampleMethods.class.getMethod("getMap");
        InvocationOnMock invocation = createInvocation(m, new Object(), new Object[0]);
        Object result = returnsEmptyValues.answer(invocation);
        assertTrue(result instanceof HashMap);
        assertTrue(((Map) result).isEmpty());
    }

    // answer(): default branch, unsupported return type (String) falls through to null
    @Test
    public void testAnswer_regularMethodReturningString_returnsNull() throws Throwable {
        Method m = SampleMethods.class.getMethod("getString");
        InvocationOnMock invocation = createInvocation(m, new Object(), new Object[0]);
        Object result = returnsEmptyValues.answer(invocation);
        assertNull(result);
    }

    // returnValueFor: primitive boolean/Boolean must be consistent default false
    @Test
    public void testReturnValueFor_booleanAndBooleanWrapper_returnsFalse() throws Throwable {
        Object primitiveResult = returnsEmptyValues.returnValueFor(boolean.class);
        Object wrapperResult = returnsEmptyValues.returnValueFor(Boolean.class);
        assertEquals(Boolean.FALSE, primitiveResult);
        assertEquals(Boolean.FALSE, wrapperResult);
    }

    // returnValueFor: primitive byte/Byte must be consistent default 0
    @Test
    public void testReturnValueFor_byteAndByteWrapper_returnsZero() throws Throwable {
        Object primitiveResult = returnsEmptyValues.returnValueFor(byte.class);
        Object wrapperResult = returnsEmptyValues.returnValueFor(Byte.class);
        assertEquals(Byte.valueOf((byte) 0), primitiveResult);
        assertEquals(Byte.valueOf((byte) 0), wrapperResult);
    }

    // returnValueFor: primitive short/Short must be consistent default 0
    @Test
    public void testReturnValueFor_shortAndShortWrapper_returnsZero() throws Throwable {
        Object primitiveResult = returnsEmptyValues.returnValueFor(short.class);
        Object wrapperResult = returnsEmptyValues.returnValueFor(Short.class);
        assertEquals(Short.valueOf((short) 0), primitiveResult);
        assertEquals(Short.valueOf((short) 0), wrapperResult);
    }

    // returnValueFor: primitive int/Integer must be consistent default 0
    @Test
    public void testReturnValueFor_intAndIntegerWrapper_returnsZero() throws Throwable {
        Object primitiveResult = returnsEmptyValues.returnValueFor(int.class);
        Object wrapperResult = returnsEmptyValues.returnValueFor(Integer.class);
        assertEquals(Integer.valueOf(0), primitiveResult);
        assertEquals(Integer.valueOf(0), wrapperResult);
    }

    // returnValueFor: primitive long/Long must be consistent default 0
    @Test
    public void testReturnValueFor_longAndLongWrapper_returnsZero() throws Throwable {
        Object primitiveResult = returnsEmptyValues.returnValueFor(long.class);
        Object wrapperResult = returnsEmptyValues.returnValueFor(Long.class);
        assertEquals(Long.valueOf(0L), primitiveResult);
        assertEquals(Long.valueOf(0L), wrapperResult);
    }

    // returnValueFor: primitive float/Float must be consistent default 0.0
    @Test
    public void testReturnValueFor_floatAndFloatWrapper_returnsZero() throws Throwable {
        Float primitiveResult = (Float) returnsEmptyValues.returnValueFor(float.class);
        Float wrapperResult = (Float) returnsEmptyValues.returnValueFor(Float.class);
        assertEquals(0.0, primitiveResult.doubleValue(), 1e-9);
        assertEquals(0.0, wrapperResult.doubleValue(), 1e-9);
    }

    // returnValueFor: primitive double/Double must be consistent default 0.0
    @Test
    public void testReturnValueFor_doubleAndDoubleWrapper_returnsZero() throws Throwable {
        Double primitiveResult = (Double) returnsEmptyValues.returnValueFor(double.class);
        Double wrapperResult = (Double) returnsEmptyValues.returnValueFor(Double.class);
        assertEquals(0.0, primitiveResult.doubleValue(), 1e-9);
        assertEquals(0.0, wrapperResult.doubleValue(), 1e-9);
    }

    // returnValueFor: primitive char/Character must be consistent default '\u0000'
    @Test
    public void testReturnValueFor_charAndCharacterWrapper_returnsNullChar() throws Throwable {
        Character primitiveResult = (Character) returnsEmptyValues.returnValueFor(char.class);
        Character wrapperResult = (Character) returnsEmptyValues.returnValueFor(Character.class);
        assertEquals(Character.valueOf('\u0000'), primitiveResult);
        assertEquals(Character.valueOf('\u0000'), wrapperResult);
    }

    // returnValueFor: Collection.class branch returns empty mutable LinkedList
    @Test
    public void testReturnValueFor_CollectionClass_returnsEmptyMutableLinkedList() throws Throwable {
        Object result = returnsEmptyValues.returnValueFor(Collection.class);
        assertTrue(result instanceof LinkedList);
        Collection collection = (Collection) result;
        assertTrue(collection.isEmpty());
        collection.add("x");
        assertEquals(1, collection.size());
    }

    // returnValueFor: Set.class branch returns empty HashSet
    @Test
    public void testReturnValueFor_SetClass_returnsEmptyHashSet() throws Throwable {
        Object result = returnsEmptyValues.returnValueFor(Set.class);
        assertTrue(result instanceof HashSet);
        assertTrue(((Set) result).isEmpty());
    }

    // returnValueFor: HashSet.class branch returns empty HashSet
    @Test
    public void testReturnValueFor_HashSetClass_returnsEmptyHashSet() throws Throwable {
        Object result = returnsEmptyValues.returnValueFor(HashSet.class);
        assertTrue(result instanceof HashSet);
        assertTrue(((Set) result).isEmpty());
    }

    // returnValueFor: SortedSet.class branch returns empty TreeSet
    @Test
    public void testReturnValueFor_SortedSetClass_returnsEmptyTreeSet() throws Throwable {
        Object result = returnsEmptyValues.returnValueFor(SortedSet.class);
        assertTrue(result instanceof TreeSet);
        assertTrue(((SortedSet) result).isEmpty());
    }

    // returnValueFor: TreeSet.class branch returns empty TreeSet
    @Test
    public void testReturnValueFor_TreeSetClass_returnsEmptyTreeSet() throws Throwable {
        Object result = returnsEmptyValues.returnValueFor(TreeSet.class);
        assertTrue(result instanceof TreeSet);
        assertTrue(((TreeSet) result).isEmpty());
    }

    // returnValueFor: LinkedHashSet.class branch returns empty LinkedHashSet
    @Test
    public void testReturnValueFor_LinkedHashSetClass_returnsEmptyLinkedHashSet() throws Throwable {
        Object result = returnsEmptyValues.returnValueFor(LinkedHashSet.class);
        assertTrue(result instanceof LinkedHashSet);
        assertTrue(((Set) result).isEmpty());
    }

    // returnValueFor: List.class branch returns empty LinkedList
    @Test
    public void testReturnValueFor_ListClass_returnsEmptyLinkedList() throws Throwable {
        Object result = returnsEmptyValues.returnValueFor(List.class);
        assertTrue(result instanceof LinkedList);
        assertTrue(((List) result).isEmpty());
    }

    // returnValueFor: LinkedList.class branch returns empty LinkedList
    @Test
    public void testReturnValueFor_LinkedListClass_returnsEmptyLinkedList() throws Throwable {
        Object result = returnsEmptyValues.returnValueFor(LinkedList.class);
        assertTrue(result instanceof LinkedList);
        assertTrue(((List) result).isEmpty());
    }

    // returnValueFor: ArrayList.class branch returns empty ArrayList
    @Test
    public void testReturnValueFor_ArrayListClass_returnsEmptyArrayList() throws Throwable {
        Object result = returnsEmptyValues.returnValueFor(ArrayList.class);
        assertTrue(result instanceof ArrayList);
        assertTrue(((List) result).isEmpty());
    }

    // returnValueFor: Map.class branch returns empty HashMap
    @Test
    public void testReturnValueFor_MapClass_returnsEmptyHashMap() throws Throwable {
        Object result = returnsEmptyValues.returnValueFor(Map.class);
        assertTrue(result instanceof HashMap);
        assertTrue(((Map) result).isEmpty());
    }

    // returnValueFor: HashMap.class branch returns empty HashMap
    @Test
    public void testReturnValueFor_HashMapClass_returnsEmptyHashMap() throws Throwable {
        Object result = returnsEmptyValues.returnValueFor(HashMap.class);
        assertTrue(result instanceof HashMap);
        assertTrue(((Map) result).isEmpty());
    }

    // returnValueFor: SortedMap.class branch returns empty TreeMap
    @Test
    public void testReturnValueFor_SortedMapClass_returnsEmptyTreeMap() throws Throwable {
        Object result = returnsEmptyValues.returnValueFor(SortedMap.class);
        assertTrue(result instanceof TreeMap);
        assertTrue(((SortedMap) result).isEmpty());
    }

    // returnValueFor: TreeMap.class branch returns empty TreeMap
    @Test
    public void testReturnValueFor_TreeMapClass_returnsEmptyTreeMap() throws Throwable {
        Object result = returnsEmptyValues.returnValueFor(TreeMap.class);
        assertTrue(result instanceof TreeMap);
        assertTrue(((TreeMap) result).isEmpty());
    }

    // returnValueFor: LinkedHashMap.class branch returns empty LinkedHashMap
    @Test
    public void testReturnValueFor_LinkedHashMapClass_returnsEmptyLinkedHashMap() throws Throwable {
        Object result = returnsEmptyValues.returnValueFor(LinkedHashMap.class);
        assertTrue(result instanceof LinkedHashMap);
        assertTrue(((Map) result).isEmpty());
    }

    // returnValueFor: unsupported types fall through to final else branch returning null
    @Test
    public void testReturnValueFor_UnsupportedTypes_returnsNull() throws Throwable {
        assertNull(returnsEmptyValues.returnValueFor(String.class));
        assertNull(returnsEmptyValues.returnValueFor(Object.class));
    }

    // returnValueFor: Map result must be mutable (avoid UnsupportedOperationException per javadoc)
    @Test
    public void testReturnValueFor_MapResult_isMutableNotImmutable() throws Throwable {
        Object result = returnsEmptyValues.returnValueFor(HashMap.class);
        Map map = (Map) result;
        map.put("k", "v");
        assertEquals(1, map.size());
    }
}
