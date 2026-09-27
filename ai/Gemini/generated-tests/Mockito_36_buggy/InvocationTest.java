package org.mockito.internal.invocation;

import org.junit.Test;
import static org.junit.Assert.*;

import java.lang.reflect.Method;
import java.util.List;
import java.util.ArrayList;

import org.mockito.internal.invocation.realmethod.RealMethod;
import org.mockito.internal.reporting.PrintSettings;
import org.hamcrest.Matcher;

public class InvocationTest {

    private static class DummyMockitoMethod implements MockitoMethod {
        private final boolean isVarArgs;
        private final Class<?> returnType;
        private final String name;
        private final Class<?>[] exceptionTypes;

        public DummyMockitoMethod(boolean isVarArgs, Class<?> returnType, String name, Class<?>[] exceptionTypes) {
            this.isVarArgs = isVarArgs;
            this.returnType = returnType;
            this.name = name;
            this.exceptionTypes = exceptionTypes;
        }

        public boolean isVarArgs() {
            return isVarArgs;
        }

        public Class<?> getReturnType() {
            return returnType;
        }

        public String getName() {
            return name;
        }

        public Class<?>[] getExceptionTypes() {
            return exceptionTypes;
        }
    }

    private static class DummyRealMethod implements RealMethod {
        private final Object result;
        private final Throwable throwable;

        public DummyRealMethod(Object result, Throwable throwable) {
            this.result = result;
            this.throwable = throwable;
        }

        public Object invoke(Object target, Object[] arguments) throws Throwable {
            if (throwable != null) {
                throw throwable;
            }
            return result;
        }
    }

    @Test
    public void testConstructorAndBasicGetters() throws Throwable {
        Object mock = new Object();
        MockitoMethod method = new DummyMockitoMethod(false, String.class, "toString", new Class<?>[0]);
        Object[] args = new Object[] { "arg1" };
        RealMethod realMethod = new DummyRealMethod("res", null);

        Invocation invocation = new Invocation(mock, method, args, 5, realMethod);

        assertEquals(mock, invocation.getMock());
        assertEquals(method, invocation.getMethod());
        assertArrayEquals(args, invocation.getArguments());
        assertArrayEquals(args, invocation.getRawArguments());
        assertEquals(Integer.valueOf(5), invocation.getSequenceNumber());
        assertEquals("toString", invocation.getMethodName());
        assertEquals("String", invocation.printMethodReturnType());
        assertNotNull(invocation.getLocation());
        assertEquals(1, invocation.getArgumentsCount());
        assertFalse(invocation.isVerified());
        assertFalse(invocation.isVerifiedInOrder());
    }

    @Test
    public void testVarArgsExpansionNullLastArg() throws Throwable {
        Object mock = new Object();
        MockitoMethod method = new DummyMockitoMethod(true, void.class, "varMethod", new Class<?>[0]);
        // Last argument is null, not an array
        Object[] args = new Object[] { "prefix", null };

        Invocation invocation = new Invocation(mock, method, args, 1, null);
        Object[] expanded = invocation.getArguments();
        assertEquals(2, expanded.length);
        assertEquals("prefix", expanded[0]);
        assertNull(expanded[1]);
    }

    @Test
    public void testVarArgsExpansionWithArray() throws Throwable {
        Object mock = new Object();
        MockitoMethod method = new DummyMockitoMethod(true, void.class, "varMethod", new Class<?>[0]);
        String[] varArgArray = new String[] { "a", "b" };
        Object[] args = new Object[] { "prefix", varArgArray };

        Invocation invocation = new Invocation(mock, method, args, 1, null);
        Object[] expanded = invocation.getArguments();
        assertEquals(3, expanded.length);
        assertEquals("prefix", expanded[0]);
        assertEquals("a", expanded[1]);
        assertEquals("b", expanded[2]);
    }

    @Test
    public void testVarArgsExpansionNullVarArgArray() throws Throwable {
        Object mock = new Object();
        MockitoMethod method = new DummyMockitoMethod(true, void.class, "varMethod", new Class<?>[0]);
        // isVarArgs is true, but args is null
        Object[] args = null;

        Invocation invocation = new Invocation(mock, method, args, 1, null);
        assertEquals(0, invocation.getArguments().length);
    }

    @Test
    public void testVerificationState() throws Throwable {
        Object mock = new Object();
        MockitoMethod method = new DummyMockitoMethod(false, void.class, "dummy", new Class<?>[0]);
        Invocation invocation = new Invocation(mock, method, new Object[0], 1, null);

        assertFalse(invocation.isVerified());
        assertFalse(invocation.isVerifiedInOrder());

        invocation.markVerified();
        assertTrue(invocation.isVerified());
        assertFalse(invocation.isVerifiedInOrder());

        Invocation invocation2 = new Invocation(mock, method, new Object[0], 2, null);
        invocation2.markVerifiedInOrder();
        assertTrue(invocation2.isVerified());
        assertTrue(invocation2.isVerifiedInOrder());
    }

    @Test
    public void testEqualsAndHashCode() throws Throwable {
        Object mock1 = new Object();
        Object mock2 = new Object();
        MockitoMethod method1 = new DummyMockitoMethod(false, void.class, "m", new Class<?>[0]);
        MockitoMethod method2 = new DummyMockitoMethod(false, void.class, "m2", new Class<?>[0]);

        Invocation inv1 = new Invocation(mock1, method1, new Object[] { "a" }, 1, null);
        Invocation inv2 = new Invocation(mock1, method1, new Object[] { "a" }, 2, null); // different sequence number, but same mock/method/args
        Invocation invDiffMock = new Invocation(mock2, method1, new Object[] { "a" }, 1, null);
        Invocation invDiffMethod = new Invocation(mock1, method2, new Object[] { "a" }, 1, null);
        Invocation invDiffArgs = new Invocation(mock1, method1, new Object[] { "b" }, 1, null);

        assertTrue(inv1.equals(inv1));
        assertTrue(inv1.equals(inv2));
        assertFalse(inv1.equals(null));
        assertFalse(inv1.equals("some string"));
        assertFalse(inv1.equals(invDiffMock));
        assertFalse(inv1.equals(invDiffMethod));
        assertFalse(inv1.equals(invDiffArgs));

        boolean exceptionThrown = false;
        try {
            inv1.hashCode();
        } catch (RuntimeException e) {
            exceptionThrown = true;
        }
        assertTrue(exceptionThrown);
    }

    @Test
    public void testArrayArgumentEquals() throws Throwable {
        Object mock = new Object();
        MockitoMethod method = new DummyMockitoMethod(false, void.class, "m", new Class<?>[0]);
        int[] arr1 = new int[] { 1, 2 };
        int[] arr2 = new int[] { 1, 2 };
        int[] arr3 = new int[] { 1, 3 };

        Invocation inv1 = new Invocation(mock, method, new Object[] { arr1 }, 1, null);
        Invocation inv2 = new Invocation(mock, method, new Object[] { arr2 }, 1, null);
        Invocation inv3 = new Invocation(mock, method, new Object[] { arr3 }, 1, null);

        assertTrue(inv1.equals(inv2));
        assertFalse(inv1.equals(inv3));
    }

    @Test
    public void testIsValidException() throws Throwable {
        Object mock = new Object();
        MockitoMethod method = new DummyMockitoMethod(false, void.class, "m", new Class<?>[] { Exception.class });
        Invocation invocation = new Invocation(mock, method, new Object[0], 1, null);

        assertTrue(invocation.isValidException(new Exception()));
        assertTrue(invocation.isValidException(new RuntimeException()));
        assertFalse(invocation.isValidException(new Error()));
    }

    @Test
    public void testIsValidReturnType() throws Throwable {
        Object mock = new Object();
        MockitoMethod methodInt = new DummyMockitoMethod(false, int.class, "m1", new Class<?>[0]);
        MockitoMethod methodObj = new DummyMockitoMethod(false, String.class, "m2", new Class<?>[0]);

        Invocation invInt = new Invocation(mock, methodInt, new Object[0], 1, null);
        Invocation invObj = new Invocation(mock, methodObj, new Object[0], 1, null);

        assertTrue(invInt.isValidReturnType(Integer.class));
        assertFalse(invInt.isValidReturnType(String.class));

        assertTrue(invObj.isValidReturnType(String.class));
        assertTrue(invObj.isValidReturnType(Object.class));
        assertFalse(invObj.isValidReturnType(Integer.class));
    }

    @Test
    public void testIsVoidAndReturnsPrimitive() throws Throwable {
        Object mock = new Object();
        MockitoMethod methodVoid = new DummyMockitoMethod(false, void.class, "m1", new Class<?>[0]);
        MockitoMethod methodInt = new DummyMockitoMethod(false, int.class, "m2", new Class<?>[0]);

        Invocation invVoid = new Invocation(mock, methodVoid, new Object[0], 1, null);
        Invocation invInt = new Invocation(mock, methodInt, new Object[0], 1, null);

        assertTrue(invVoid.isVoid());
        assertFalse(invVoid.returnsPrimitive());

        assertFalse(invInt.isVoid());
        assertTrue(invInt.returnsPrimitive());
    }

    @Test
    public void testCallRealMethod() throws Throwable {
        Object mock = new Object();
        MockitoMethod method = new DummyMockitoMethod(false, Object.class, "m", new Class<?>[0]);
        RealMethod realMethod = new DummyRealMethod("success", null);
        Invocation invocation = new Invocation(mock, method, new Object[0], 1, realMethod);

        Object result = invocation.callRealMethod();
        assertEquals("success", result);
    }

    @Test(expected = RuntimeException.class)
    public void testCallRealMethodThrows() throws Throwable {
        Object mock = new Object();
        MockitoMethod method = new DummyMockitoMethod(false, Object.class, "m", new Class<?>[0]);
        RealMethod realMethod = new DummyRealMethod(null, new RuntimeException("fail"));
        Invocation invocation = new Invocation(mock, method, new Object[0], 1, realMethod);

        invocation.callRealMethod();
    }

    @Test
    public void testArgumentsToMatchersAndToString() throws Throwable {
        Object mock = new Object();
        MockitoMethod method = new DummyMockitoMethod(false, void.class, "toString", new Class<?>[0]);
        Object[] args = new Object[] { "testArg", new int[] { 1, 2 } };
        Invocation invocation = new Invocation(mock, method, args, 1, null);

        List<Matcher> matchers = invocation.argumentsToMatchers();
        assertEquals(2, matchers.size());

        PrintSettings settings = new PrintSettings();
        String str1 = invocation.toString(settings);
        assertNotNull(str1);

        settings.setMultiline(true);
        String str2 = invocation.toString(settings);
        assertNotNull(str2);
    }

    @Test
    public void testIsToString() throws Throwable {
        MockitoMethod toStringMethod = new DummyMockitoMethod(false, String.class, "toString", new Class<?>[0]);
        MockitoMethod otherMethod = new DummyMockitoMethod(false, String.class, "hashCode", new Class<?>[0]);

        Object mock = new Object();
        Invocation inv1 = new Invocation(mock, toStringMethod, new Object[0], 1, null);
        Invocation inv2 = new Invocation(mock, otherMethod, new Object[0], 1, null);

        assertTrue(Invocation.isToString(inv1));
        assertFalse(Invocation.isToString(inv2));
    }
}