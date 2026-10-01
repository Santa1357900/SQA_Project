package org.mockito.internal.stubbing.answers;

import java.io.FileNotFoundException;
import java.io.IOException;
import java.lang.reflect.Method;

import org.junit.Before;
import org.junit.Test;
import static org.junit.Assert.*;

import org.mockito.exceptions.base.MockitoException;
import org.mockito.internal.invocation.Invocation;
import org.mockito.internal.invocation.MockitoMethod;
import org.mockito.internal.invocation.RealMethod;
import org.mockito.internal.invocation.SerializableMethod;
import org.mockito.invocation.InvocationOnMock;
import org.mockito.stubbing.Answer;

public class AnswersValidatorClaudeTest {

    private AnswersValidator validator;

    public static class Sample {
        public void voidMethod() {
        }

        public int intMethod() {
            return 0;
        }

        public boolean booleanMethod() {
            return false;
        }

        public String stringMethod() {
            return null;
        }

        public Object objectMethod() {
            return null;
        }

        public void checkedExceptionMethod() throws IOException {
        }
    }

    @Before
    public void setUp() throws Throwable {
        validator = new AnswersValidator();
    }

    private Invocation createInvocation(String methodName) throws Throwable {
        Method method = Sample.class.getMethod(methodName, new Class[0]);
        MockitoMethod mockitoMethod = new SerializableMethod(method);
        RealMethod realMethod = new RealMethod() {
            public Object invoke(Object target, Object[] arguments) {
                return null;
            }
        };
        return new Invocation(new Sample(), mockitoMethod, new Object[0], 1, realMethod);
    }

    // validateException: throwable instanceof RuntimeException -> always valid, no exception
    @Test
    public void testValidate_throwsExceptionWithRuntimeException_doesNotThrow() throws Throwable {
        Invocation invocation = createInvocation("voidMethod");
        validator.validate(new ThrowsException(new RuntimeException("boom")), invocation);
        assertEquals("voidMethod", invocation.getMethodName());
    }

    // validateException: throwable instanceof Error -> always valid, no exception
    @Test
    public void testValidate_throwsExceptionWithError_doesNotThrow() throws Throwable {
        Invocation invocation = createInvocation("voidMethod");
        validator.validate(new ThrowsException(new Error("boom")), invocation);
        assertEquals("voidMethod", invocation.getMethodName());
    }

    // validateException: throwable == null -> cannotStubWithNullThrowable
    @Test
    public void testValidate_throwsExceptionWithNullThrowable_throwsMockitoException() throws Throwable {
        Invocation invocation = createInvocation("voidMethod");
        try {
            validator.validate(new ThrowsException(null), invocation);
            fail("expected MockitoException");
        } catch (MockitoException expected) {
        }
    }

    // validateException: checked exception declared by method -> isValidException true, no exception
    @Test
    public void testValidate_throwsExceptionWithDeclaredCheckedException_doesNotThrow() throws Throwable {
        Invocation invocation = createInvocation("checkedExceptionMethod");
        validator.validate(new ThrowsException(new IOException("io")), invocation);
        assertEquals("checkedExceptionMethod", invocation.getMethodName());
    }

    // validateException: checked exception NOT declared by method -> checkedExceptionInvalid
    @Test
    public void testValidate_throwsExceptionWithUndeclaredCheckedException_throwsMockitoException() throws Throwable {
        Invocation invocation = createInvocation("voidMethod");
        try {
            validator.validate(new ThrowsException(new IOException("io")), invocation);
            fail("expected MockitoException");
        } catch (MockitoException expected) {
        }
    }

    // validateException: checked exception subclass of declared exception -> still valid
    @Test
    public void testValidate_throwsExceptionWithSubclassOfDeclaredCheckedException_doesNotThrow() throws Throwable {
        Invocation invocation = createInvocation("checkedExceptionMethod");
        validator.validate(new ThrowsException(new FileNotFoundException("missing")), invocation);
        assertEquals("checkedExceptionMethod", invocation.getMethodName());
    }

    // validateReturnValue: invocation.isVoid() true with non-null value -> cannotStubVoidMethodWithAReturnValue
    @Test
    public void testValidate_returnsOnVoidMethod_throwsMockitoException() throws Throwable {
        Invocation invocation = createInvocation("voidMethod");
        try {
            validator.validate(new Returns("x"), invocation);
            fail("expected MockitoException");
        } catch (MockitoException expected) {
        }
    }

    // validateReturnValue: invocation.isVoid() true even with null value -> still invalid (stubbing a return value on void)
    @Test
    public void testValidate_returnsNullOnVoidMethod_throwsMockitoException() throws Throwable {
        Invocation invocation = createInvocation("voidMethod");
        try {
            validator.validate(new Returns(null), invocation);
            fail("expected MockitoException");
        } catch (MockitoException expected) {
        }
    }

    // validateReturnValue: answer.returnsNull() && invocation.returnsPrimitive() -> wrongTypeOfReturnValue
    @Test
    public void testValidate_returnsNullOnPrimitiveReturnType_throwsMockitoException() throws Throwable {
        Invocation invocation = createInvocation("intMethod");
        try {
            validator.validate(new Returns(null), invocation);
            fail("expected MockitoException");
        } catch (MockitoException expected) {
        }
    }

    // validateReturnValue: answer.returnsNull() on non-primitive return type -> valid (null allowed for reference types)
    @Test
    public void testValidate_returnsNullOnObjectReturnType_doesNotThrow() throws Throwable {
        Invocation invocation = createInvocation("objectMethod");
        validator.validate(new Returns(null), invocation);
        assertEquals("objectMethod", invocation.getMethodName());
    }

    // validateReturnValue: !answer.returnsNull() && !invocation.isValidReturnType -> wrongTypeOfReturnValue
    @Test
    public void testValidate_returnsIncompatibleTypeOnNonVoidMethod_throwsMockitoException() throws Throwable {
        Invocation invocation = createInvocation("stringMethod");
        try {
            validator.validate(new Returns(Integer.valueOf(5)), invocation);
            fail("expected MockitoException");
        } catch (MockitoException expected) {
        }
    }

    // validateReturnValue: compatible reference type -> no exception
    @Test
    public void testValidate_returnsCompatibleTypeOnNonVoidMethod_doesNotThrow() throws Throwable {
        Invocation invocation = createInvocation("stringMethod");
        validator.validate(new Returns("hello"), invocation);
        assertEquals("stringMethod", invocation.getMethodName());
    }

    // validateReturnValue: boxed boolean is a valid return value for a boolean primitive method
    @Test
    public void testValidate_returnsBooleanBoxedCompatibleType_doesNotThrow() throws Throwable {
        Invocation invocation = createInvocation("booleanMethod");
        validator.validate(new Returns(Boolean.TRUE), invocation);
        assertEquals("booleanMethod", invocation.getMethodName());
    }

    // validateReturnValue: any object is a valid subtype of Object return type
    @Test
    public void testValidate_returnsSubtypeAssignableToObjectReturnType_doesNotThrow() throws Throwable {
        Invocation invocation = createInvocation("objectMethod");
        validator.validate(new Returns(new StringBuilder("v")), invocation);
        assertEquals("objectMethod", invocation.getMethodName());
    }

    // validateReturnValue: boxed int is a valid return value for an int primitive method
    @Test
    public void testValidate_returnsIntBoxedCompatibleType_doesNotThrow() throws Throwable {
        Invocation invocation = createInvocation("intMethod");
        validator.validate(new Returns(Integer.valueOf(7)), invocation);
        assertEquals("intMethod", invocation.getMethodName());
    }

    // validateReturnValue: null is invalid for boolean primitive return type too
    @Test
    public void testValidate_returnsNullOnBooleanPrimitiveReturnType_throwsMockitoException() throws Throwable {
        Invocation invocation = createInvocation("booleanMethod");
        try {
            validator.validate(new Returns(null), invocation);
            fail("expected MockitoException");
        } catch (MockitoException expected) {
        }
    }

    // validateDoNothing: invocation.isVoid() true -> no exception
    @Test
    public void testValidate_doesNothingOnVoidMethod_doesNotThrow() throws Throwable {
        Invocation invocation = createInvocation("voidMethod");
        validator.validate(new DoesNothing(), invocation);
        assertEquals("voidMethod", invocation.getMethodName());
    }

    // validateDoNothing: !invocation.isVoid() -> onlyVoidMethodsCanBeSetToDoNothing
    @Test
    public void testValidate_doesNothingOnNonVoidMethod_throwsMockitoException() throws Throwable {
        Invocation invocation = createInvocation("intMethod");
        try {
            validator.validate(new DoesNothing(), invocation);
            fail("expected MockitoException");
        } catch (MockitoException expected) {
        }
    }

    // validate: answer not instanceof ThrowsException/Returns/DoesNothing -> no branch triggered, no exception
    @Test
    public void testValidate_customAnswerNotMatchingAnyKnownType_doesNotThrow() throws Throwable {
        Invocation invocation = createInvocation("intMethod");
        Answer<Object> customAnswer = new Answer<Object>() {
            public Object answer(InvocationOnMock inv) throws Throwable {
                return Integer.valueOf(1);
            }
        };
        validator.validate(customAnswer, invocation);
        assertEquals("intMethod", invocation.getMethodName());
    }

    // validate: null answer -> none of the instanceof checks match, no exception
    @Test
    public void testValidate_nullAnswer_doesNotThrow() throws Throwable {
        Invocation invocation = createInvocation("voidMethod");
        validator.validate(null, invocation);
        assertEquals("voidMethod", invocation.getMethodName());
    }

    // validateException: RuntimeException subclass valid regardless of method's declared exceptions
    @Test
    public void testValidate_throwsExceptionWithRuntimeExceptionSubclassOnMethodWithNoDeclaredExceptions_doesNotThrow() throws Throwable {
        Invocation invocation = createInvocation("voidMethod");
        validator.validate(new ThrowsException(new IllegalStateException("bad state")), invocation);
        assertEquals("voidMethod", invocation.getMethodName());
    }

    // validateException: null throwable check is independent of invocation being void or not
    @Test
    public void testValidate_throwsExceptionWithNullThrowableOnNonVoidMethod_throwsMockitoException() throws Throwable {
        Invocation invocation = createInvocation("intMethod");
        try {
            validator.validate(new ThrowsException(null), invocation);
            fail("expected MockitoException");
        } catch (MockitoException expected) {
        }
    }

    // validateException: Error subclass valid regardless of method's declared exceptions
    @Test
    public void testValidate_throwsExceptionWithErrorSubclassOnMethodWithNoDeclaredExceptions_doesNotThrow() throws Throwable {
        Invocation invocation = createInvocation("voidMethod");
        validator.validate(new ThrowsException(new AssertionError("assert failed")), invocation);
        assertEquals("voidMethod", invocation.getMethodName());
    }
}
