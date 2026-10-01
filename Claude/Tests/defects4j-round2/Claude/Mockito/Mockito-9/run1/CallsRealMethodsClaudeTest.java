package org.mockito.internal.stubbing.answers;

import org.junit.Before;
import org.junit.Test;
import static org.junit.Assert.*;
import static org.mockito.Mockito.*;

import org.mockito.invocation.InvocationOnMock;
import org.mockito.stubbing.Answer;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.io.Serializable;

public class CallsRealMethodsClaudeTest {

    private CallsRealMethods callsRealMethods;
    private InvocationOnMock invocation;

    @Before
    public void setUp() throws Throwable {
        callsRealMethods = new CallsRealMethods();
        invocation = mock(InvocationOnMock.class);
    }

    // answer() must return exactly what callRealMethod() returns (String case)
    @Test
    public void testAnswer_callRealMethodReturnsString_returnsSameString() throws Throwable {
        when(invocation.callRealMethod()).thenReturn("hello");
        Object result = callsRealMethods.answer(invocation);
        assertEquals("hello", result);
    }

    // answer() must propagate a null return value from callRealMethod()
    @Test
    public void testAnswer_callRealMethodReturnsNull_returnsNull() throws Throwable {
        when(invocation.callRealMethod()).thenReturn(null);
        Object result = callsRealMethods.answer(invocation);
        assertNull(result);
    }

    // boxed Integer return value must be preserved exactly
    @Test
    public void testAnswer_callRealMethodReturnsInteger_returnsSameInteger() throws Throwable {
        when(invocation.callRealMethod()).thenReturn(Integer.valueOf(42));
        Object result = callsRealMethods.answer(invocation);
        assertEquals(Integer.valueOf(42), result);
    }

    // Boolean return value must be preserved exactly
    @Test
    public void testAnswer_callRealMethodReturnsBooleanFalse_returnsSameBoolean() throws Throwable {
        when(invocation.callRealMethod()).thenReturn(Boolean.FALSE);
        Object result = callsRealMethods.answer(invocation);
        assertEquals(Boolean.FALSE, result);
    }

    // array reference identity must be preserved (no copying/wrapping)
    @Test
    public void testAnswer_callRealMethodReturnsArray_returnsSameArrayInstance() throws Throwable {
        int[] array = new int[] {1, 2, 3};
        when(invocation.callRealMethod()).thenReturn(array);
        Object result = callsRealMethods.answer(invocation);
        assertSame(array, result);
    }

    // arbitrary custom object identity must be preserved
    @Test
    public void testAnswer_callRealMethodReturnsCustomObject_returnsSameInstance() throws Throwable {
        Object custom = new Object();
        when(invocation.callRealMethod()).thenReturn(custom);
        Object result = callsRealMethods.answer(invocation);
        assertSame(custom, result);
    }

    // empty string edge case must not be converted to null or something else
    @Test
    public void testAnswer_callRealMethodReturnsEmptyString_returnsEmptyString() throws Throwable {
        when(invocation.callRealMethod()).thenReturn("");
        Object result = callsRealMethods.answer(invocation);
        assertEquals("", result);
    }

    // long value type/identity must survive autoboxing round trip
    @Test
    public void testAnswer_callRealMethodReturnsLong_preservesTypeAndValue() throws Throwable {
        when(invocation.callRealMethod()).thenReturn(Long.valueOf(123456789L));
        Object result = callsRealMethods.answer(invocation);
        assertTrue(result instanceof Long);
        assertEquals(123456789L, ((Long) result).longValue());
    }

    // double value must survive with delta-based comparison
    @Test
    public void testAnswer_callRealMethodReturnsDouble_preservesValue() throws Throwable {
        when(invocation.callRealMethod()).thenReturn(Double.valueOf(3.14));
        Object result = callsRealMethods.answer(invocation);
        Double d = (Double) result;
        assertEquals(3.14, d.doubleValue(), 1e-9);
    }

    // interaction: answer() must delegate by calling callRealMethod() exactly once
    @Test
    public void testAnswer_invokesCallRealMethodExactlyOnce() throws Throwable {
        when(invocation.callRealMethod()).thenReturn("value");
        callsRealMethods.answer(invocation);
        verify(invocation, times(1)).callRealMethod();
    }

    // calling answer() twice on same invocation must call callRealMethod() twice
    @Test
    public void testAnswer_calledTwice_invokesCallRealMethodTwice() throws Throwable {
        when(invocation.callRealMethod()).thenReturn("v1", "v2");
        callsRealMethods.answer(invocation);
        callsRealMethods.answer(invocation);
        verify(invocation, times(2)).callRealMethod();
    }

    // sequential stubbing must yield values in the exact order they were returned
    @Test
    public void testAnswer_sequentialStubbing_returnsValuesInOrder() throws Throwable {
        when(invocation.callRealMethod()).thenReturn("first", "second");
        Object r1 = callsRealMethods.answer(invocation);
        Object r2 = callsRealMethods.answer(invocation);
        assertEquals("first", r1);
        assertEquals("second", r2);
    }

    // RuntimeException thrown by the real method must propagate unchanged (same instance)
    @Test
    public void testAnswer_callRealMethodThrowsRuntimeException_propagatesSameInstance() throws Throwable {
        RuntimeException runtime = new RuntimeException("boom");
        when(invocation.callRealMethod()).thenThrow(runtime);
        try {
            callsRealMethods.answer(invocation);
            fail("expected RuntimeException to be propagated");
        } catch (RuntimeException e) {
            assertSame(runtime, e);
        }
    }

    // checked Exception thrown by the real method must propagate unchanged (not wrapped)
    @Test
    public void testAnswer_callRealMethodThrowsCheckedException_propagatesSameInstance() throws Throwable {
        Exception checked = new Exception("checked failure");
        when(invocation.callRealMethod()).thenThrow(checked);
        try {
            callsRealMethods.answer(invocation);
            fail("expected Exception to be propagated");
        } catch (Exception e) {
            assertSame(checked, e);
        }
    }

    // Error thrown by the real method must propagate unchanged
    @Test
    public void testAnswer_callRealMethodThrowsError_propagatesSameInstance() throws Throwable {
        Error error = new Error("fatal");
        when(invocation.callRealMethod()).thenThrow(error);
        try {
            callsRealMethods.answer(invocation);
            fail("expected Error to be propagated");
        } catch (Error e) {
            assertSame(error, e);
        }
    }

    // raw Throwable (neither Exception nor Error) must propagate unchanged
    @Test
    public void testAnswer_callRealMethodThrowsThrowable_propagatesSameInstance() throws Throwable {
        Throwable throwable = new Throwable("raw throwable");
        try {
            when(invocation.callRealMethod()).thenThrow(throwable);
            callsRealMethods.answer(invocation);
            fail("expected Throwable to be propagated");
        } catch (Throwable t) {
            assertSame(throwable, t);
        }
    }

    // exception message/keyword must not be altered or swallowed by the delegation
    @Test
    public void testAnswer_exceptionMessageKeywordPreserved() throws Throwable {
        when(invocation.callRealMethod()).thenThrow(new IllegalStateException("specific-reason"));
        try {
            callsRealMethods.answer(invocation);
            fail("expected IllegalStateException");
        } catch (IllegalStateException e) {
            assertTrue(e.getMessage().contains("specific-reason"));
        }
    }

    // custom checked exception subtype must still propagate as-is
    @Test
    public void testAnswer_callRealMethodThrowsCustomCheckedException_propagatesSameType() throws Throwable {
        java.io.IOException ioEx = new java.io.IOException("io problem");
        when(invocation.callRealMethod()).thenThrow(ioEx);
        try {
            callsRealMethods.answer(invocation);
            fail("expected IOException to be propagated");
        } catch (java.io.IOException e) {
            assertSame(ioEx, e);
        }
    }

    // CallsRealMethods must implement the Answer<Object> contract type
    @Test
    public void testCallsRealMethods_isInstanceOfAnswer() throws Throwable {
        assertTrue(callsRealMethods instanceof Answer);
    }

    // CallsRealMethods must implement Serializable as declared
    @Test
    public void testCallsRealMethods_isInstanceOfSerializable() throws Throwable {
        assertTrue(callsRealMethods instanceof Serializable);
    }

    // instance must be serializable/deserializable without error (supports its Serializable contract)
    @Test
    public void testCallsRealMethods_serializationRoundTrip_succeeds() throws Throwable {
        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        ObjectOutputStream oos = new ObjectOutputStream(baos);
        oos.writeObject(callsRealMethods);
        oos.close();
        ByteArrayInputStream bais = new ByteArrayInputStream(baos.toByteArray());
        ObjectInputStream ois = new ObjectInputStream(bais);
        Object deserialized = ois.readObject();
        ois.close();
        assertTrue(deserialized instanceof CallsRealMethods);
    }

    // separate invocation instances must be delegated to independently, not mixed up
    @Test
    public void testAnswer_withDifferentInvocationInstances_delegatesIndependently() throws Throwable {
        InvocationOnMock otherInvocation = mock(InvocationOnMock.class);
        when(invocation.callRealMethod()).thenReturn("fromFirst");
        when(otherInvocation.callRealMethod()).thenReturn("fromSecond");
        assertEquals("fromFirst", callsRealMethods.answer(invocation));
        assertEquals("fromSecond", callsRealMethods.answer(otherInvocation));
    }

    // a freshly constructed instance must behave identically (stateless answer logic)
    @Test
    public void testAnswer_newInstance_behavesConsistently() throws Throwable {
        CallsRealMethods another = new CallsRealMethods();
        when(invocation.callRealMethod()).thenReturn("consistent");
        assertEquals("consistent", another.answer(invocation));
    }
}
