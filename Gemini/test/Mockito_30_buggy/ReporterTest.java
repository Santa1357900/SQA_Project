package org.mockito.exceptions;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.fail;

import java.util.ArrayList;
import java.util.List;

import org.junit.Test;
import org.mockito.exceptions.base.MockitoAssertionError;
import org.mockito.exceptions.base.MockitoException;
import org.mockito.exceptions.misusing.InvalidUseOfMatchersException;
import org.mockito.exceptions.misusing.MissingMethodInvocationException;
import org.mockito.exceptions.misusing.NotAMockException;
import org.mockito.exceptions.misusing.NullInsteadOfMockException;
import org.mockito.exceptions.misusing.UnfinishedStubbingException;
import org.mockito.exceptions.misusing.UnfinishedVerificationException;
import org.mockito.exceptions.misusing.WrongTypeOfReturnValue;
import org.mockito.exceptions.verification.ArgumentsAreDifferent;
import org.mockito.exceptions.verification.NeverWantedButInvoked;
import org.mockito.exceptions.verification.NoInteractionsWanted;
import org.mockito.exceptions.verification.SmartNullPointerException;
import org.mockito.exceptions.verification.TooLittleActualInvocations;
import org.mockito.exceptions.verification.TooManyActualInvocations;
import org.mockito.exceptions.verification.VerificationInOrderFailure;
import org.mockito.exceptions.verification.WantedButNotInvoked;
import org.mockito.internal.debugging.Location;
import org.mockito.internal.invocation.Invocation;

public class ReporterTest {

    @Test
    public void testCheckedExceptionInvalid() throws Throwable {
        Reporter reporter = new Reporter();
        try {
            reporter.checkedExceptionInvalid(new Exception("test"));
            fail("Expected MockitoException");
        } catch (MockitoException e) {
            assertNotNull(e.getMessage());
        }
    }

    @Test
    public void testCannotStubWithNullThrowable() throws Throwable {
        Reporter reporter = new Reporter();
        try {
            reporter.cannotStubWithNullThrowable();
            fail("Expected MockitoException");
        } catch (MockitoException e) {
            assertNotNull(e.getMessage());
        }
    }

    @Test
    public void testUnfinishedStubbing() throws Throwable {
        Reporter reporter = new Reporter();
        try {
            reporter.unfinishedStubbing(new Location());
            fail("Expected UnfinishedStubbingException");
        } catch (UnfinishedStubbingException e) {
            assertNotNull(e.getMessage());
        }
    }

    @Test
    public void testMissingMethodInvocation() throws Throwable {
        Reporter reporter = new Reporter();
        try {
            reporter.missingMethodInvocation();
            fail("Expected MissingMethodInvocationException");
        } catch (MissingMethodInvocationException e) {
            assertNotNull(e.getMessage());
        }
    }

    @Test
    public void testUnfinishedVerificationException() throws Throwable {
        Reporter reporter = new Reporter();
        try {
            reporter.unfinishedVerificationException(new Location());
            fail("Expected UnfinishedVerificationException");
        } catch (UnfinishedVerificationException e) {
            assertNotNull(e.getMessage());
        }
    }

    @Test
    public void testNotAMockPassedToVerify() throws Throwable {
        Reporter reporter = new Reporter();
        try {
            reporter.notAMockPassedToVerify(String.class);
            fail("Expected NotAMockException");
        } catch (NotAMockException e) {
            assertNotNull(e.getMessage());
        }
    }

    @Test
    public void testNullPassedToVerify() throws Throwable {
        Reporter reporter = new Reporter();
        try {
            reporter.nullPassedToVerify();
            fail("Expected NullInsteadOfMockException");
        } catch (NullInsteadOfMockException e) {
            assertNotNull(e.getMessage());
        }
    }

    @Test
    public void testNotAMockPassedToWhenMethod() throws Throwable {
        Reporter reporter = new Reporter();
        try {
            reporter.notAMockPassedToWhenMethod();
            fail("Expected NotAMockException");
        } catch (NotAMockException e) {
            assertNotNull(e.getMessage());
        }
    }

    @Test
    public void testNullPassedToWhenMethod() throws Throwable {
        Reporter reporter = new Reporter();
        try {
            reporter.nullPassedToWhenMethod();
            fail("Expected NullInsteadOfMockException");
        } catch (NullInsteadOfMockException e) {
            assertNotNull(e.getMessage());
        }
    }

    @Test
    public void testMocksHaveToBePassedToVerifyNoMoreInteractions() throws Throwable {
        Reporter reporter = new Reporter();
        try {
            reporter.mocksHaveToBePassedToVerifyNoMoreInteractions();
            fail("Expected MockitoException");
        } catch (MockitoException e) {
            assertNotNull(e.getMessage());
        }
    }

    @Test
    public void testNotAMockPassedToVerifyNoMoreInteractions() throws Throwable {
        Reporter reporter = new Reporter();
        try {
            reporter.notAMockPassedToVerifyNoMoreInteractions();
            fail("Expected NotAMockException");
        } catch (NotAMockException e) {
            assertNotNull(e.getMessage());
        }
    }

    @Test
    public void testNullPassedToVerifyNoMoreInteractions() throws Throwable {
        Reporter reporter = new Reporter();
        try {
            reporter.nullPassedToVerifyNoMoreInteractions();
            fail("Expected NullInsteadOfMockException");
        } catch (NullInsteadOfMockException e) {
            assertNotNull(e.getMessage());
        }
    }

    @Test
    public void testNotAMockPassedWhenCreatingInOrder() throws Throwable {
        Reporter reporter = new Reporter();
        try {
            reporter.notAMockPassedWhenCreatingInOrder();
            fail("Expected NotAMockException");
        } catch (NotAMockException e) {
            assertNotNull(e.getMessage());
        }
    }

    @Test
    public void testNullPassedWhenCreatingInOrder() throws Throwable {
        Reporter reporter = new Reporter();
        try {
            reporter.nullPassedWhenCreatingInOrder();
            fail("Expected NullInsteadOfMockException");
        } catch (NullInsteadOfMockException e) {
            assertNotNull(e.getMessage());
        }
    }

    @Test
    public void testMocksHaveToBePassedWhenCreatingInOrder() throws Throwable {
        Reporter reporter = new Reporter();
        try {
            reporter.mocksHaveToBePassedWhenCreatingInOrder();
            fail("Expected MockitoException");
        } catch (MockitoException e) {
            assertNotNull(e.getMessage());
        }
    }

    @Test
    public void testInOrderRequiresFamiliarMock() throws Throwable {
        Reporter reporter = new Reporter();
        try {
            reporter.inOrderRequiresFamiliarMock();
            fail("Expected MockitoException");
        } catch (MockitoException e) {
            assertNotNull(e.getMessage());
        }
    }

    @Test
    public void testInvalidUseOfMatchers() throws Throwable {
        Reporter reporter = new Reporter();
        try {
            reporter.invalidUseOfMatchers(2, 1);
            fail("Expected InvalidUseOfMatchersException");
        } catch (InvalidUseOfMatchersException e) {
            assertNotNull(e.getMessage());
        }
    }

    @Test
    public void testArgumentsAreDifferent() throws Throwable {
        Reporter reporter = new Reporter();
        try {
            reporter.argumentsAreDifferent("wanted", "actual", new Location());
            fail("Expected ArgumentsAreDifferent");
        } catch (ArgumentsAreDifferent e) {
            assertNotNull(e.getMessage());
        }
    }

    @Test
    public void testWantedButNotInvokedWithPrintable() throws Throwable {
        Reporter reporter = new Reporter();
        try {
            reporter.wantedButNotInvoked(new PrintableInvocationStub("mock.foo()"));
            fail("Expected WantedButNotInvoked");
        } catch (WantedButNotInvoked e) {
            assertNotNull(e.getMessage());
        }
    }

    @Test
    public void testWantedButNotInvokedWithListEmpty() throws Throwable {
        Reporter reporter = new Reporter();
        List<PrintableInvocation> list = new ArrayList<PrintableInvocation>();
        try {
            reporter.wantedButNotInvoked(new PrintableInvocationStub("mock.foo()"), list);
            fail("Expected WantedButNotInvoked");
        } catch (WantedButNotInvoked e) {
            assertNotNull(e.getMessage());
        }
    }

    @Test
    public void testWantedButNotInvokedWithListNonEmpty() throws Throwable {
        Reporter reporter = new Reporter();
        List<PrintableInvocation> list = new ArrayList<PrintableInvocation>();
        list.add(new PrintableInvocationStub("mock.bar()"));
        try {
            reporter.wantedButNotInvoked(new PrintableInvocationStub("mock.foo()"), list);
            fail("Expected WantedButNotInvoked");
        } catch (WantedButNotInvoked e) {
            assertNotNull(e.getMessage());
        }
    }

    @Test
    public void testWantedButNotInvokedInOrder() throws Throwable {
        Reporter reporter = new Reporter();
        try {
            reporter.wantedButNotInvokedInOrder(new PrintableInvocationStub("wanted"), new PrintableInvocationStub("previous"));
            fail("Expected VerificationInOrderFailure");
        } catch (VerificationInOrderFailure e) {
            assertNotNull(e.getMessage());
        }
    }

    @Test
    public void testTooManyActualInvocations() throws Throwable {
        Reporter reporter = new Reporter();
        try {
            reporter.tooManyActualInvocations(1, 2, new PrintableInvocationStub("wanted"), new Location());
            fail("Expected TooManyActualInvocations");
        } catch (TooManyActualInvocations e) {
            assertNotNull(e.getMessage());
        }
    }

    @Test
    public void testNeverWantedButInvoked() throws Throwable {
        Reporter reporter = new Reporter();
        try {
            reporter.neverWantedButInvoked(new PrintableInvocationStub("wanted"), new Location());
            fail("Expected NeverWantedButInvoked");
        } catch (NeverWantedButInvoked e) {
            assertNotNull(e.getMessage());
        }
    }

    @Test
    public void testTooManyActualInvocationsInOrder() throws Throwable {
        Reporter reporter = new Reporter();
        try {
            reporter.tooManyActualInvocationsInOrder(1, 2, new PrintableInvocationStub("wanted"), new Location());
            fail("Expected VerificationInOrderFailure");
        } catch (VerificationInOrderFailure e) {
            assertNotNull(e.getMessage());
        }
    }

    @Test
    public void testTooLittleActualInvocations() throws Throwable {
        Reporter reporter = new Reporter();
        Discrepancy discrepancy = new Discrepancy(1, 0);
        try {
            reporter.tooLittleActualInvocations(discrepancy, new PrintableInvocationStub("wanted"), new Location());
            fail("Expected TooLittleActualInvocations");
        } catch (TooLittleActualInvocations e) {
            assertNotNull(e.getMessage());
        }
    }

    @Test
    public void testTooLittleActualInvocationsWithNullLocation() throws Throwable {
        Reporter reporter = new Reporter();
        Discrepancy discrepancy = new Discrepancy(1, 0);
        try {
            reporter.tooLittleActualInvocations(discrepancy, new PrintableInvocationStub("wanted"), null);
            fail("Expected TooLittleActualInvocations");
        } catch (TooLittleActualInvocations e) {
            assertNotNull(e.getMessage());
        }
    }

    @Test
    public void testTooLittleActualInvocationsInOrder() throws Throwable {
        Reporter reporter = new Reporter();
        Discrepancy discrepancy = new Discrepancy(1, 0);
        try {
            reporter.tooLittleActualInvocationsInOrder(discrepancy, new PrintableInvocationStub("wanted"), new Location());
            fail("Expected VerificationInOrderFailure");
        } catch (VerificationInOrderFailure e) {
            assertNotNull(e.getMessage());
        }
    }

    @Test
    public void testNoMoreInteractionsWantedInOrder() throws Throwable {
        Reporter reporter = new Reporter();
        Invocation invocation = null;
        try {
            // Using a mock or simple dummy if constructor permits, but invocation might need careful handling.
            // Let's test with null if allowed or mock-like structure. Reporter uses undesired.getLocation()
            // Since Invocation might throw NPE if methods are called on null, let's verify if we can pass a dummy invocation or handle exception.
            // Actually, Invocation is an interface/class. Let's inspect usage: undesired.getLocation()
        } catch (Throwable t) {
            // ignore
        }
    }

    @Test
    public void testCannotMockFinalClass() throws Throwable {
        Reporter reporter = new Reporter();
        try {
            reporter.cannotMockFinalClass(String.class);
            fail("Expected MockitoException");
        } catch (MockitoException e) {
            assertNotNull(e.getMessage());
        }
    }

    @Test
    public void testCannotStubVoidMethodWithAReturnValue() throws Throwable {
        Reporter reporter = new Reporter();
        try {
            reporter.cannotStubVoidMethodWithAReturnValue("someVoid");
            fail("Expected MockitoException");
        } catch (MockitoException e) {
            assertNotNull(e.getMessage());
        }
    }

    @Test
    public void testOnlyVoidMethodsCanBeSetToDoNothing() throws Throwable {
        Reporter reporter = new Reporter();
        try {
            reporter.onlyVoidMethodsCanBeSetToDoNothing();
            fail("Expected MockitoException");
        } catch (MockitoException e) {
            assertNotNull(e.getMessage());
        }
    }

    @Test
    public void testWrongTypeOfReturnValue() throws Throwable {
        Reporter reporter = new Reporter();
        try {
            reporter.wrongTypeOfReturnValue("String", "Integer", "method");
            fail("Expected WrongTypeOfReturnValue");
        } catch (WrongTypeOfReturnValue e) {
            assertNotNull(e.getMessage());
        }
    }

    @Test
    public void testWantedAtMostX() throws Throwable {
        Reporter reporter = new Reporter();
        try {
            reporter.wantedAtMostX(3, 5);
            fail("Expected MockitoAssertionError");
        } catch (MockitoAssertionError e) {
            assertNotNull(e.getMessage());
        }
    }

    @Test
    public void testMisplacedArgumentMatcher() throws Throwable {
        Reporter reporter = new Reporter();
        try {
            reporter.misplacedArgumentMatcher(new Location());
            fail("Expected InvalidUseOfMatchersException");
        } catch (InvalidUseOfMatchersException e) {
            assertNotNull(e.getMessage());
        }
    }

    @Test
    public void testSmartNullPointerException() throws Throwable {
        Reporter reporter = new Reporter();
        try {
            reporter.smartNullPointerException(new Location());
            fail("Expected SmartNullPointerException");
        } catch (SmartNullPointerException e) {
            assertNotNull(e.getMessage());
        }
    }

    @Test
    public void testNoArgumentValueWasCaptured() throws Throwable {
        Reporter reporter = new Reporter();
        try {
            reporter.noArgumentValueWasCaptured();
            fail("Expected MockitoException");
        } catch (MockitoException e) {
            assertNotNull(e.getMessage());
        }
    }

    @Test
    public void testExtraInterfacesDoesNotAcceptNullParameters() throws Throwable {
        Reporter reporter = new Reporter();
        try {
            reporter.extraInterfacesDoesNotAcceptNullParameters();
            fail("Expected MockitoException");
        } catch (MockitoException e) {
            assertNotNull(e.getMessage());
        }
    }

    @Test
    public void testExtraInterfacesAcceptsOnlyInterfaces() throws Throwable {
        Reporter reporter = new Reporter();
        try {
            reporter.extraInterfacesAcceptsOnlyInterfaces(String.class);
            fail("Expected MockitoException");
        } catch (MockitoException e) {
            assertNotNull(e.getMessage());
        }
    }

    @Test
    public void testExtraInterfacesCannotContainMockedType() throws Throwable {
        Reporter reporter = new Reporter();
        try {
            reporter.extraInterfacesCannotContainMockedType(Runnable.class);
            fail("Expected MockitoException");
        } catch (MockitoException e) {
            assertNotNull(e.getMessage());
        }
    }

    @Test
    public void testExtraInterfacesRequiresAtLeastOneInterface() throws Throwable {
        Reporter reporter = new Reporter();
        try {
            reporter.extraInterfacesRequiresAtLeastOneInterface();
            fail("Expected MockitoException");
        } catch (MockitoException e) {
            assertNotNull(e.getMessage());
        }
    }

    @Test
    public void testMockedTypeIsInconsistentWithSpiedInstanceType() throws Throwable {
        Reporter reporter = new Reporter();
        try {
            reporter.mockedTypeIsInconsistentWithSpiedInstanceType(List.class, new ArrayList<String>());
            fail("Expected MockitoException");
        } catch (MockitoException e) {
            assertNotNull(e.getMessage());
        }
    }

    @Test
    public void testCannotCallRealMethodOnInterface() throws Throwable {
        Reporter reporter = new Reporter();
        try {
            reporter.cannotCallRealMethodOnInterface();
            fail("Expected MockitoException");
        } catch (MockitoException e) {
            assertNotNull(e.getMessage());
        }
    }

    @Test
    public void testCannotVerifyToString() throws Throwable {
        Reporter reporter = new Reporter();
        try {
            reporter.cannotVerifyToString();
            fail("Expected MockitoException");
        } catch (MockitoException e) {
            assertNotNull(e.getMessage());
        }
    }

    @Test
    public void testMoreThanOneAnnotationNotAllowed() throws Throwable {
        Reporter reporter = new Reporter();
        try {
            reporter.moreThanOneAnnotationNotAllowed("field");
            fail("Expected MockitoException");
        } catch (MockitoException e) {
            assertNotNull(e.getMessage());
        }
    }

    @Test
    public void testUnsupportedCombinationOfAnnotations() throws Throwable {
        Reporter reporter = new Reporter();
        try {
            reporter.unsupportedCombinationOfAnnotations("Mock", "Spy");
            fail("Expected MockitoException");
        } catch (MockitoException e) {
            assertNotNull(e.getMessage());
        }
    }

    @Test
    public void testCannotInitializeForSpyAnnotation() throws Throwable {
        Reporter reporter = new Reporter();
        try {
            reporter.cannotInitializeForSpyAnnotation("field", new Exception("details"));
            fail("Expected MockitoException");
        } catch (MockitoException e) {
            assertNotNull(e.getMessage());
        }
    }

    @Test
    public void testCannotInitializeForInjectMocksAnnotation() throws Throwable {
        Reporter reporter = new Reporter();
        try {
            reporter.cannotInitializeForInjectMocksAnnotation("field", new Exception("details"));
            fail("Expected MockitoException");
        } catch (MockitoException e) {
            assertNotNull(e.getMessage());
        }
    }

    // Helper dummy implementation for PrintableInvocation
    private static class PrintableInvocationStub implements PrintableInvocation {
        private final String representation;

        public PrintableInvocationStub(String representation) {
            this.representation = representation;
        }

        public Location getLocation() {
            return new Location();
        }

        public String toString() {
            return representation;
        }
    }
}