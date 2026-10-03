package org.mockito.internal.invocation;

import java.io.FileNotFoundException;
import java.io.IOException;
import java.util.List;

import org.hamcrest.Matcher;
import org.junit.Before;
import org.junit.Test;
import static org.junit.Assert.*;

import org.mockito.internal.debugging.Location;
import org.mockito.internal.invocation.realmethod.RealMethod;
import org.mockito.internal.matchers.ArrayEquals;
import org.mockito.internal.matchers.Equals;

public class InvocationClaudeTest {

    private RealMethod dummyRealMethod;

    private static class SampleMethods {
        public void noArgs() {}
        public void varArgs(Object... args) {}
        public int intMethod() { return 0; }
        public String stringMethod() { return null; }
        public void ioExceptionMethod() throws IOException {}
    }

    @Before
    public void setUp() throws Throwable {
        dummyRealMethod = new RealMethod() {
            public Object invoke(Object target, Object[] arguments) throws Throwable {
                return "real-return";
            }
        };
    }

    private MockitoMethod noArgsMethod() throws Throwable {
        return new DelegatingMethod(SampleMethods.class.getMethod("noArgs"));
    }

    private MockitoMethod varArgsMethod() throws Throwable {
        return new DelegatingMethod(SampleMethods.class.getMethod("varArgs", Object[].class));
    }

    private MockitoMethod intMethod() throws Throwable {
        return new DelegatingMethod(SampleMethods.class.getMethod("intMethod"));
    }

    private MockitoMethod stringMethod() throws Throwable {
        return new DelegatingMethod(SampleMethods.class.getMethod("stringMethod"));
    }

    private MockitoMethod ioExceptionMethod() throws Throwable {
        return new DelegatingMethod(SampleMethods.class.getMethod("ioExceptionMethod"));
    }

    private MockitoMethod toStringMethod() throws Throwable {
        return new DelegatingMethod(Object.class.getMethod("toString"));
    }

    // isVarArgs=false: arguments array must equal the raw args content, unexpanded
    @Test
    public void testConstructor_nonVarArgs_preservesArguments() throws Throwable {
        Object[] args = new Object[] { "a", "b" };
        Invocation invocation = new Invocation(new Object(), noArgsMethod(), args, 1, dummyRealMethod);
        assertArrayEquals(args, invocation.getArguments());
    }

    // isVarArgs=false branch with args==null must yield an empty arguments array
    @Test
    public void testConstructor_nonVarArgs_nullArgs_returnsEmptyArguments() throws Throwable {
        Invocation invocation = new Invocation(new Object(), noArgsMethod(), null, 1, dummyRealMethod);
        assertEquals(0, invocation.getArguments().length);
    }

    // isVarArgs=true, last argument is an array: must be expanded element-wise
    @Test
    public void testConstructor_varArgs_lastArgArray_expandsElements() throws Throwable {
        Object[] args = new Object[] { Integer.valueOf(1), new String[] { "a", "b" } };
        Invocation invocation = new Invocation(new Object(), varArgsMethod(), args, 1, dummyRealMethod);
        Object[] result = invocation.getArguments();
        assertEquals(3, result.length);
        assertEquals(Integer.valueOf(1), result[0]);
        assertEquals("a", result[1]);
        assertEquals("b", result[2]);
    }

    // isVarArgs=true, last argument is an empty array: expands to zero extra elements
    @Test
    public void testConstructor_varArgs_lastArgEmptyArray_noExtraElements() throws Throwable {
        Object[] args = new Object[] { "x", new Object[0] };
        Invocation invocation = new Invocation(new Object(), varArgsMethod(), args, 1, dummyRealMethod);
        Object[] result = invocation.getArguments();
        assertEquals(1, result.length);
        assertEquals("x", result[0]);
    }

    // isVarArgs=true, last argument is null: treated as a single-element null varArg array
    @Test
    public void testConstructor_varArgs_lastArgNull_singleNullElement() throws Throwable {
        Object[] args = new Object[] { "x", null };
        Invocation invocation = new Invocation(new Object(), varArgsMethod(), args, 1, dummyRealMethod);
        Object[] result = invocation.getArguments();
        assertEquals(2, result.length);
        assertEquals("x", result[0]);
        assertNull(result[1]);
    }

    // isVarArgs=true, last argument is neither null nor an array: no expansion performed
    @Test
    public void testConstructor_varArgs_lastArgNotArrayNotNull_noExpansion() throws Throwable {
        Object[] args = new Object[] { "a", "b", "c" };
        Invocation invocation = new Invocation(new Object(), varArgsMethod(), args, 1, dummyRealMethod);
        Object[] result = invocation.getArguments();
        assertEquals(3, result.length);
        assertEquals("c", result[2]);
    }

    // isVarArgs=true with only the varArg array present (no fixed args): expands fully
    @Test
    public void testConstructor_varArgs_onlyVarArgsParam_expandsAll() throws Throwable {
        Object[] args = new Object[] { new String[] { "x", "y" } };
        Invocation invocation = new Invocation(new Object(), varArgsMethod(), args, 1, dummyRealMethod);
        Object[] result = invocation.getArguments();
        assertEquals(2, result.length);
        assertEquals("x", result[0]);
        assertEquals("y", result[1]);
    }

    // getMock must return exactly the mock instance passed to the constructor
    @Test
    public void testGetMock_returnsSameReference() throws Throwable {
        Object mock = new Object();
        Invocation invocation = new Invocation(mock, noArgsMethod(), new Object[0], 1, dummyRealMethod);
        assertSame(mock, invocation.getMock());
    }

    // getMethod must return exactly the MockitoMethod instance passed to the constructor
    @Test
    public void testGetMethod_returnsSameReference() throws Throwable {
        MockitoMethod m = noArgsMethod();
        Invocation invocation = new Invocation(new Object(), m, new Object[0], 1, dummyRealMethod);
        assertSame(m, invocation.getMethod());
    }

    // getSequenceNumber must return exactly the sequence number passed to the constructor
    @Test
    public void testGetSequenceNumber_returnsGivenValue() throws Throwable {
        Invocation invocation = new Invocation(new Object(), noArgsMethod(), new Object[0], 42, dummyRealMethod);
        assertEquals(Integer.valueOf(42), invocation.getSequenceNumber());
    }

    // a freshly constructed invocation is not verified
    @Test
    public void testIsVerified_initiallyFalse() throws Throwable {
        Invocation invocation = new Invocation(new Object(), noArgsMethod(), new Object[0], 1, dummyRealMethod);
        assertFalse(invocation.isVerified());
    }

    // a freshly constructed invocation is not verified in order
    @Test
    public void testIsVerifiedInOrder_initiallyFalse() throws Throwable {
        Invocation invocation = new Invocation(new Object(), noArgsMethod(), new Object[0], 1, dummyRealMethod);
        assertFalse(invocation.isVerifiedInOrder());
    }

    // markVerified must flip isVerified to true without affecting isVerifiedInOrder
    @Test
    public void testMarkVerified_setsVerifiedTrue() throws Throwable {
        Invocation invocation = new Invocation(new Object(), noArgsMethod(), new Object[0], 1, dummyRealMethod);
        invocation.markVerified();
        assertTrue(invocation.isVerified());
        assertFalse(invocation.isVerifiedInOrder());
    }

    // markVerifiedInOrder must flip both isVerified and isVerifiedInOrder to true
    @Test
    public void testMarkVerifiedInOrder_setsBothFlags() throws Throwable {
        Invocation invocation = new Invocation(new Object(), noArgsMethod(), new Object[0], 1, dummyRealMethod);
        invocation.markVerifiedInOrder();
        assertTrue(invocation.isVerified());
        assertTrue(invocation.isVerifiedInOrder());
    }

    // equals: same mock, same method instance, equal-content arguments -> true
    @Test
    public void testEquals_sameMockMethodArgs_true() throws Throwable {
        Object mock = new Object();
        MockitoMethod m = noArgsMethod();
        Invocation i1 = new Invocation(mock, m, new Object[] { "x", "y" }, 1, dummyRealMethod);
        Invocation i2 = new Invocation(mock, m, new Object[] { "x", "y" }, 2, dummyRealMethod);
        assertTrue(i1.equals(i2));
    }

    // equals: different mock instances -> false
    @Test
    public void testEquals_differentMock_false() throws Throwable {
        MockitoMethod m = noArgsMethod();
        Invocation i1 = new Invocation(new Object(), m, new Object[0], 1, dummyRealMethod);
        Invocation i2 = new Invocation(new Object(), m, new Object[0], 1, dummyRealMethod);
        assertFalse(i1.equals(i2));
    }

    // equals: different methods -> false
    @Test
    public void testEquals_differentMethod_false() throws Throwable {
        Object mock = new Object();
        Invocation i1 = new Invocation(mock, noArgsMethod(), new Object[0], 1, dummyRealMethod);
        Invocation i2 = new Invocation(mock, intMethod(), new Object[0], 1, dummyRealMethod);
        assertFalse(i1.equals(i2));
    }

    // equals: equal mock and method but different argument content -> false
    @Test
    public void testEquals_differentArguments_false() throws Throwable {
        Object mock = new Object();
        MockitoMethod m = noArgsMethod();
        Invocation i1 = new Invocation(mock, m, new Object[] { "a", "b" }, 1, dummyRealMethod);
        Invocation i2 = new Invocation(mock, m, new Object[] { "a", "c" }, 1, dummyRealMethod);
        assertFalse(i1.equals(i2));
    }

    // equals against null must return false, never throw
    @Test
    public void testEquals_null_false() throws Throwable {
        Invocation invocation = new Invocation(new Object(), noArgsMethod(), new Object[0], 1, dummyRealMethod);
        assertFalse(invocation.equals(null));
    }

    // equals against an object of a different class must return false
    @Test
    public void testEquals_differentClass_false() throws Throwable {
        Invocation invocation = new Invocation(new Object(), noArgsMethod(), new Object[0], 1, dummyRealMethod);
        assertFalse(invocation.equals("not an invocation"));
    }

    // hashCode is explicitly unsupported and must throw RuntimeException
    @Test
    public void testHashCode_throwsRuntimeException() throws Throwable {
        Invocation invocation = new Invocation(new Object(), noArgsMethod(), new Object[0], 1, dummyRealMethod);
        try {
            invocation.hashCode();
            fail("expected RuntimeException");
        } catch (RuntimeException expected) {
        }
    }

    // isValidException: exactly the declared exception type -> true
    @Test
    public void testIsValidException_declaredException_true() throws Throwable {
        Invocation invocation = new Invocation(new Object(), ioExceptionMethod(), new Object[0], 1, dummyRealMethod);
        assertTrue(invocation.isValidException(new IOException()));
    }

    // isValidException: subclass of declared exception type -> true (polymorphism)
    @Test
    public void testIsValidException_subclassOfDeclared_true() throws Throwable {
        Invocation invocation = new Invocation(new Object(), ioExceptionMethod(), new Object[0], 1, dummyRealMethod);
        assertTrue(invocation.isValidException(new FileNotFoundException()));
    }

    // isValidException: exception type not declared by the method -> false
    @Test
    public void testIsValidException_notDeclared_false() throws Throwable {
        Invocation invocation = new Invocation(new Object(), ioExceptionMethod(), new Object[0], 1, dummyRealMethod);
        assertFalse(invocation.isValidException(new IllegalStateException()));
    }

    // isValidReturnType: primitive-returning method matches its wrapper class
    @Test
    public void testIsValidReturnType_primitiveMatch_true() throws Throwable {
        Invocation invocation = new Invocation(new Object(), intMethod(), new Object[0], 1, dummyRealMethod);
        assertTrue(invocation.isValidReturnType(Integer.class));
    }

    // isValidReturnType: primitive-returning method does not match an unrelated class
    @Test
    public void testIsValidReturnType_primitiveMismatch_false() throws Throwable {
        Invocation invocation = new Invocation(new Object(), intMethod(), new Object[0], 1, dummyRealMethod);
        assertFalse(invocation.isValidReturnType(String.class));
    }

    // isValidReturnType: non-primitive return type assignable from same type -> true
    @Test
    public void testIsValidReturnType_nonPrimitiveAssignable_true() throws Throwable {
        Invocation invocation = new Invocation(new Object(), stringMethod(), new Object[0], 1, dummyRealMethod);
        assertTrue(invocation.isValidReturnType(String.class));
    }

    // isValidReturnType: non-primitive return type not assignable from unrelated supertype -> false
    @Test
    public void testIsValidReturnType_nonPrimitiveNotAssignable_false() throws Throwable {
        Invocation invocation = new Invocation(new Object(), stringMethod(), new Object[0], 1, dummyRealMethod);
        assertFalse(invocation.isValidReturnType(Object.class));
    }

    // isVoid: true for a method whose return type is void
    @Test
    public void testIsVoid_trueForVoidMethod() throws Throwable {
        Invocation invocation = new Invocation(new Object(), noArgsMethod(), new Object[0], 1, dummyRealMethod);
        assertTrue(invocation.isVoid());
    }

    // isVoid: false for a method with a non-void return type
    @Test
    public void testIsVoid_falseForNonVoidMethod() throws Throwable {
        Invocation invocation = new Invocation(new Object(), intMethod(), new Object[0], 1, dummyRealMethod);
        assertFalse(invocation.isVoid());
    }

    // printMethodReturnType must return the simple name of the return type
    @Test
    public void testPrintMethodReturnType_returnsSimpleName() throws Throwable {
        Invocation invocation = new Invocation(new Object(), stringMethod(), new Object[0], 1, dummyRealMethod);
        assertEquals("String", invocation.printMethodReturnType());
    }

    // getMethodName must return the underlying method's name
    @Test
    public void testGetMethodName_returnsName() throws Throwable {
        Invocation invocation = new Invocation(new Object(), noArgsMethod(), new Object[0], 1, dummyRealMethod);
        assertEquals("noArgs", invocation.getMethodName());
    }

    // returnsPrimitive: true when the method's return type is primitive
    @Test
    public void testReturnsPrimitive_trueForPrimitiveReturn() throws Throwable {
        Invocation invocation = new Invocation(new Object(), intMethod(), new Object[0], 1, dummyRealMethod);
        assertTrue(invocation.returnsPrimitive());
    }

    // returnsPrimitive: false when the method's return type is a reference type
    @Test
    public void testReturnsPrimitive_falseForNonPrimitiveReturn() throws Throwable {
        Invocation invocation = new Invocation(new Object(), stringMethod(), new Object[0], 1, dummyRealMethod);
        assertFalse(invocation.returnsPrimitive());
    }

    // getLocation must never be null; a Location is captured at construction time
    @Test
    public void testGetLocation_returnsNonNullLocation() throws Throwable {
        Invocation invocation = new Invocation(new Object(), noArgsMethod(), new Object[0], 1, dummyRealMethod);
        Location location = invocation.getLocation();
        assertNotNull(location);
    }

    // getArgumentsCount must reflect the length of the expanded arguments array
    @Test
    public void testGetArgumentsCount_matchesExpandedLength() throws Throwable {
        Object[] args = new Object[] { "a", new String[] { "b", "c" } };
        Invocation invocation = new Invocation(new Object(), varArgsMethod(), args, 1, dummyRealMethod);
        assertEquals(3, invocation.getArgumentsCount());
    }

    // getRawArguments must return the original, unexpanded array even after varArg expansion
    @Test
    public void testGetRawArguments_returnsOriginalUnexpandedArray() throws Throwable {
        Object[] args = new Object[] { "a", new String[] { "b", "c" } };
        Invocation invocation = new Invocation(new Object(), varArgsMethod(), args, 1, dummyRealMethod);
        assertEquals(2, invocation.getRawArguments().length);
        assertEquals(3, invocation.getArguments().length);
    }

    // callRealMethod must delegate to the RealMethod and return its result
    @Test
    public void testCallRealMethod_returnsRealMethodResult() throws Throwable {
        Invocation invocation = new Invocation(new Object(), noArgsMethod(), new Object[0], 1, dummyRealMethod);
        assertEquals("real-return", invocation.callRealMethod());
    }

    // callRealMethod must propagate exceptions thrown by the underlying RealMethod
    @Test
    public void testCallRealMethod_propagatesException() throws Throwable {
        RealMethod throwing = new RealMethod() {
            public Object invoke(Object target, Object[] arguments) throws Throwable {
                throw new IllegalStateException("boom");
            }
        };
        Invocation invocation = new Invocation(new Object(), noArgsMethod(), new Object[0], 1, throwing);
        try {
            invocation.callRealMethod();
            fail("expected IllegalStateException");
        } catch (IllegalStateException expected) {
        }
    }

    // isToString: must recognize an Object.toString() style invocation
    @Test
    public void testIsToString_trueForToStringMethod() throws Throwable {
        Invocation invocation = new Invocation(new Object(), toStringMethod(), new Object[0], 1, dummyRealMethod);
        assertTrue(Invocation.isToString(invocation));
    }

    // isToString: must return false for an unrelated method
    @Test
    public void testIsToString_falseForOtherMethod() throws Throwable {
        Invocation invocation = new Invocation(new Object(), noArgsMethod(), new Object[0], 1, dummyRealMethod);
        assertFalse(Invocation.isToString(invocation));
    }

    // argumentsToMatchers: array argument must be wrapped with ArrayEquals, scalar with Equals
    @Test
    public void testArgumentsToMatchers_mixedArgs_correctMatcherTypes() throws Throwable {
        Object[] args = new Object[] { "hello", new int[] { 1, 2 } };
        Invocation invocation = new Invocation(new Object(), noArgsMethod(), args, 1, dummyRealMethod);
        List<Matcher> matchers = invocation.argumentsToMatchers();
        assertEquals(2, matchers.size());
        assertTrue(matchers.get(0) instanceof Equals);
        assertTrue(matchers.get(1) instanceof ArrayEquals);
    }

    // argumentsToMatchers: a null argument must still be wrapped with Equals, not throw
    @Test
    public void testArgumentsToMatchers_nullArg_usesEquals() throws Throwable {
        Object[] args = new Object[] { null };
        Invocation invocation = new Invocation(new Object(), noArgsMethod(), args, 1, dummyRealMethod);
        List<Matcher> matchers = invocation.argumentsToMatchers();
        assertEquals(1, matchers.size());
        assertTrue(matchers.get(0) instanceof Equals);
    }
}
