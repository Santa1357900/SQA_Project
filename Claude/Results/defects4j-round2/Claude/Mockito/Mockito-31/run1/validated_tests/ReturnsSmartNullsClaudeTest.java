package org.mockito.internal.stubbing.defaultanswers;

import java.util.List;
import java.util.Map;
import java.util.Set;

import org.junit.Before;
import org.junit.Test;
import static org.junit.Assert.*;

import org.mockito.Mockito;

public class ReturnsSmartNullsClaudeTest {

    public static class Target {
        public void doStuff() {
        }
    }

    public static final class FinalTarget {
    }

    public interface Collaborator {
        Target unstubbedTarget();
        Target anotherUnstubbedTarget();
        FinalTarget unstubbedFinal();
        String unstubbedString();
        int unstubbedInt();
        boolean unstubbedBoolean();
        long unstubbedLong();
        double unstubbedDouble();
        Object unstubbedObject();
        List<String> unstubbedList();
        Set<String> unstubbedSet();
        Map<String, String> unstubbedMap();
        Target stubbableTarget();
    }

    private Collaborator collaborator;

    @Before
    public void setUp() throws Throwable {
        collaborator = Mockito.mock(Collaborator.class, new ReturnsSmartNulls());
    }

    // branch: delegate returns null and type is imposterisable concrete class -> smart null proxy of declared type
    @Test
    public void testAnswer_unstubbedConcreteReturnType_returnsNonNullSmartNullInstanceOfDeclaredType() throws Throwable {
        Target result = collaborator.unstubbedTarget();
        assertNotNull(result);
        assertTrue(result instanceof Target);
    }

    // branch: each unstubbed call creates a fresh answer invocation, no caching of smart null instances
    @Test
    public void testAnswer_sameUnstubbedMethodCalledTwice_returnsDistinctSmartNullInstances() throws Throwable {
        Target first = collaborator.unstubbedTarget();
        Target second = collaborator.unstubbedTarget();
        assertNotSame(first, second);
    }

    // branch: intercept() with isToString(method) == true -> friendly message naming the unstubbed method
    @Test
    public void testAnswer_smartNullToString_describesUnstubbedMethodName() throws Throwable {
        Target result = collaborator.unstubbedTarget();
        String message = result.toString();
        assertTrue(message.contains("SmartNull"));
        assertTrue(message.contains("unstubbedTarget()"));
    }

    // branch: intercept() with isToString(method) == false -> Reporter.smartNullPointerException is thrown
    @Test
    public void testAnswer_smartNullNonToStringMethodCall_throwsRuntimeException() throws Throwable {
        Target result = collaborator.unstubbedTarget();
        try {
            result.doStuff();
            fail("expected an exception when calling a method on a SmartNull");
        } catch (RuntimeException expected) {
            assertNotNull(expected);
        }
    }

    // branch: the throwing (non-toString) path is re-entered on every subsequent call, not only the first time
    @Test
    public void testAnswer_smartNullMethodCalledRepeatedly_throwsEveryTime() throws Throwable {
        Target result = collaborator.unstubbedTarget();
        try {
            result.doStuff();
            fail("expected an exception on first call");
        } catch (RuntimeException expected) { }
        try {
            result.doStuff();
            fail("expected an exception on second call");
        } catch (RuntimeException expected) { }
    }

    // branch: hashCode() is not toString(), so it also follows the throwing path
    @Test
    public void testAnswer_smartNullHashCodeCall_throwsRuntimeException() throws Throwable {
        Target result = collaborator.unstubbedTarget();
        try {
            result.hashCode();
            fail("expected an exception when calling hashCode on a SmartNull");
        } catch (RuntimeException expected) {
            assertNotNull(expected);
        }
    }

    // branch: return type not imposterisable (final class) -> ordinary null is returned
    @Test
    public void testAnswer_finalReturnType_returnsOrdinaryNull() throws Throwable {
        FinalTarget result = collaborator.unstubbedFinal();
        assertNull(result);
    }

    // branch: delegate already supplies a non-null default (primitive int) -> smart null path is skipped
    @Test
    public void testAnswer_primitiveIntReturnType_returnsZeroNotSmartNull() throws Throwable {
        int result = collaborator.unstubbedInt();
        assertEquals(0, result);
    }

    // branch: delegate already supplies a non-null default (primitive boolean)
    @Test
    public void testAnswer_primitiveBooleanReturnType_returnsFalseNotSmartNull() throws Throwable {
        boolean result = collaborator.unstubbedBoolean();
        assertFalse(result);
    }

    // branch: delegate already supplies a non-null default (primitive long)
    @Test
    public void testAnswer_primitiveLongReturnType_returnsZeroNotSmartNull() throws Throwable {
        long result = collaborator.unstubbedLong();
        assertEquals(0L, result);
    }

    // branch: delegate already supplies a non-null default (primitive double)
    @Test
    public void testAnswer_primitiveDoubleReturnType_returnsZeroNotSmartNull() throws Throwable {
        double result = collaborator.unstubbedDouble();
        assertEquals(0.0, result, 1e-9);
    }

    // branch: delegate already supplies a non-null default (empty String) for String return type
    @Test
    public void testAnswer_stringReturnType_returnsEmptyStringNotSmartNull() throws Throwable {
        String result = collaborator.unstubbedString();
        assertEquals("", result);
    }

    // branch: delegate already supplies a non-null default (empty List) for List return type
    @Test
    public void testAnswer_listReturnType_returnsEmptyListNotSmartNull() throws Throwable {
        List<String> result = collaborator.unstubbedList();
        assertNotNull(result);
        assertTrue(result.isEmpty());
    }

    // branch: delegate already supplies a non-null default (empty Set) for Set return type
    @Test
    public void testAnswer_setReturnType_returnsEmptySetNotSmartNull() throws Throwable {
        Set<String> result = collaborator.unstubbedSet();
        assertNotNull(result);
        assertTrue(result.isEmpty());
    }

    // branch: delegate already supplies a non-null default (empty Map) for Map return type
    @Test
    public void testAnswer_mapReturnType_returnsEmptyMapNotSmartNull() throws Throwable {
        Map<String, String> result = collaborator.unstubbedMap();
        assertNotNull(result);
        assertTrue(result.isEmpty());
    }

    // branch: Object.class is imposterisable -> smart null proxy created even for plain Object return type
    @Test
    public void testAnswer_objectReturnType_returnsSmartNullProxy() throws Throwable {
        Object result = collaborator.unstubbedObject();
        assertNotNull(result);
        assertTrue(result.toString().contains("SmartNull"));
    }

    // branch: formatMethodCall() uses the invocation captured at call time, so distinct methods get distinct messages
    @Test
    public void testAnswer_distinctUnstubbedMethods_messageReflectsEachMethodName() throws Throwable {
        String firstMessage = collaborator.unstubbedTarget().toString();
        String secondMessage = collaborator.anotherUnstubbedTarget().toString();
        assertTrue(firstMessage.contains("unstubbedTarget()"));
        assertTrue(secondMessage.contains("anotherUnstubbedTarget()"));
    }

    // branch: once a method is stubbed, the default Answer is bypassed and the stubbed value is returned
    @Test
    public void testAnswer_stubbedMethod_bypassesSmartNullAndReturnsStubbedValue() throws Throwable {
        Target stub = new Target();
        Mockito.when(collaborator.stubbableTarget()).thenReturn(stub);
        Target result = collaborator.stubbableTarget();
        assertSame(stub, result);
    }

    // branch: calling toString() more than once on the same smart null is stable and keeps naming the method
    @Test
    public void testAnswer_toStringCalledMultipleTimesOnSameSmartNull_returnsConsistentMessage() throws Throwable {
        Target result = collaborator.unstubbedTarget();
        String first = result.toString();
        String second = result.toString();
        assertEquals(first, second);
        assertTrue(second.contains("unstubbedTarget()"));
    }

    // branch: a second, independently constructed ReturnsSmartNulls instance behaves the same way (no shared state)
    @Test
    public void testAnswer_secondAnswerInstance_behavesIndependently() throws Throwable {
        Collaborator other = Mockito.mock(Collaborator.class, new ReturnsSmartNulls());
        Target result = other.unstubbedTarget();
        assertNotNull(result);
        assertTrue(result.toString().contains("SmartNull"));
    }
}
