package org.mockito.exceptions;

import org.junit.Test;
import org.mockito.exceptions.base.MockitoAssertionError;
import org.mockito.exceptions.base.MockitoException;
import org.mockito.exceptions.misusing.*;
import org.mockito.exceptions.verification.*;
import org.mockito.internal.matchers.LocalizedMatcher;
import org.mockito.internal.reporting.Discrepancy;
import org.mockito.invocation.DescribedInvocation;
import org.mockito.invocation.Invocation;
import org.mockito.invocation.InvocationOnMock;
import org.mockito.listeners.InvocationListener;
import org.mockito.mock.SerializableMode;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.List;

import static org.junit.Assert.*;

public class ReporterTest {

    @Test
    public void testCheckedExceptionInvalid() throws Throwable {
        Reporter reporter = new Reporter();
        try {
            reporter.checkedExceptionInvalid(new Exception("test"));
            fail("Expected MockitoException");
        } catch (MockitoException e) {
            assertTrue(e.getMessage().contains("Checked exception is invalid for this method!"));
        }
    }

    @Test
    public void testCannotStubWithNullThrowable() throws Throwable {
        Reporter reporter = new Reporter();
        try {
            reporter.cannotStubWithNullThrowable();
            fail("Expected MockitoException");
        } catch (MockitoException e) {
            assertTrue(e.getMessage().contains("Cannot stub with null throwable!"));
        }
    }

    @Test
    public void testUnfinishedStubbing() throws Throwable {
        Reporter reporter = new Reporter();
        try {
            reporter.unfinishedStubbing(null);
            fail("Expected UnfinishedStubbingException");
        } catch (UnfinishedStubbingException e) {
            assertTrue(e.getMessage().contains("Unfinished stubbing detected here:"));
        }
    }

    @Test
    public void testIncorrectUseOfApi() throws Throwable {
        Reporter reporter = new Reporter();
        try {
            reporter.incorrectUseOfApi();
            fail("Expected MockitoException");
        } catch (MockitoException e) {
            assertTrue(e.getMessage().contains("Incorrect use of API detected here:"));
        }
    }

    @Test
    public void testMissingMethodInvocation() throws Throwable {
        Reporter reporter = new Reporter();
        try {
            reporter.missingMethodInvocation();
            fail("Expected MissingMethodInvocationException");
        } catch (MissingMethodInvocationException e) {
            assertTrue(e.getMessage().contains("when() requires an argument"));
        }
    }

    @Test
    public void testUnfinishedVerificationException() throws Throwable {
        Reporter reporter = new Reporter();
        try {
            reporter.unfinishedVerificationException(null);
            fail("Expected UnfinishedVerificationException");
        } catch (UnfinishedVerificationException e) {
            assertTrue(e.getMessage().contains("Missing method call for verify(mock) here:"));
        }
    }

    @Test
    public void testNotAMockPassedToVerify() throws Throwable {
        Reporter reporter = new Reporter();
        try {
            reporter.notAMockPassedToVerify(String.class);
            fail("Expected NotAMockException");
        } catch (NotAMockException e) {
            assertTrue(e.getMessage().contains("is not a mock!"));
        }
    }

    @Test
    public void testNullPassedToVerify() throws Throwable {
        Reporter reporter = new Reporter();
        try {
            reporter.nullPassedToVerify();
            fail("Expected NullInsteadOfMockException");
        } catch (NullInsteadOfMockException e) {
            assertTrue(e.getMessage().contains("should be a mock but is null!"));
        }
    }

    @Test
    public void testNotAMockPassedToWhenMethod() throws Throwable {
        Reporter reporter = new Reporter();
        try {
            reporter.notAMockPassedToWhenMethod();
            fail("Expected NotAMockException");
        } catch (NotAMockException e) {
            assertTrue(e.getMessage().contains("Argument passed to when() is not a mock!"));
        }
    }

    @Test
    public void testNullPassedToWhenMethod() throws Throwable {
        Reporter reporter = new Reporter();
        try {
            reporter.nullPassedToWhenMethod();
            fail("Expected NullInsteadOfMockException");
        } catch (NullInsteadOfMockException e) {
            assertTrue(e.getMessage().contains("Argument passed to when() is null!"));
        }
    }

    @Test
    public void testMocksHaveToBePassedToVerifyNoMoreInteractions() throws Throwable {
        Reporter reporter = new Reporter();
        try {
            reporter.mocksHaveToBePassedToVerifyNoMoreInteractions();
            fail("Expected MockitoException");
        } catch (MockitoException e) {
            assertTrue(e.getMessage().contains("Method requires argument(s)!"));
        }
    }

    @Test
    public void testNotAMockPassedToVerifyNoMoreInteractions() throws Throwable {
        Reporter reporter = new Reporter();
        try {
            reporter.notAMockPassedToVerifyNoMoreInteractions();
            fail("Expected NotAMockException");
        } catch (NotAMockException e) {
            assertTrue(e.getMessage().contains("Argument(s) passed is not a mock!"));
        }
    }

    @Test
    public void testNullPassedToVerifyNoMoreInteractions() throws Throwable {
        Reporter reporter = new Reporter();
        try {
            reporter.nullPassedToVerifyNoMoreInteractions();
            fail("Expected NullInsteadOfMockException");
        } catch (NullInsteadOfMockException e) {
            assertTrue(e.getMessage().contains("Argument(s) passed is null!"));
        }
    }

    @Test
    public void testNotAMockPassedWhenCreatingInOrder() throws Throwable {
        Reporter reporter = new Reporter();
        try {
            reporter.notAMockPassedWhenCreatingInOrder();
            fail("Expected NotAMockException");
        } catch (NotAMockException e) {
            assertTrue(e.getMessage().contains("Argument(s) passed is not a mock!"));
        }
    }

    @Test
    public void testNullPassedWhenCreatingInOrder() throws Throwable {
        Reporter reporter = new Reporter();
        try {
            reporter.nullPassedWhenCreatingInOrder();
            fail("Expected NullInsteadOfMockException");
        } catch (NullInsteadOfMockException e) {
            assertTrue(e.getMessage().contains("Argument(s) passed is null!"));
        }
    }

    @Test
    public void testMocksHaveToBePassedWhenCreatingInOrder() throws Throwable {
        Reporter reporter = new Reporter();
        try {
            reporter.mocksHaveToBePassedWhenCreatingInOrder();
            fail("Expected MockitoException");
        } catch (MockitoException e) {
            assertTrue(e.getMessage().contains("Method requires argument(s)!"));
        }
    }

    @Test
    public void testInOrderRequiresFamiliarMock() throws Throwable {
        Reporter reporter = new Reporter();
        try {
            reporter.inOrderRequiresFamiliarMock();
            fail("Expected MockitoException");
        } catch (MockitoException e) {
            assertTrue(e.getMessage().contains("InOrder can only verify mocks"));
        }
    }

    @Test
    public void testInvalidUseOfMatchers() throws Throwable {
        Reporter reporter = new Reporter();
        List<LocalizedMatcher> matchers = new ArrayList<LocalizedMatcher>();
        try {
            reporter.invalidUseOfMatchers(2, matchers);
            fail("Expected InvalidUseOfMatchersException");
        } catch (InvalidUseOfMatchersException e) {
            assertTrue(e.getMessage().contains("Invalid use of argument matchers!"));
        }
    }

    @Test
    public void testIncorrectUseOfAdditionalMatchers() throws Throwable {
        Reporter reporter = new Reporter();
        List<LocalizedMatcher> matchers = new ArrayList<LocalizedMatcher>();
        try {
            reporter.incorrectUseOfAdditionalMatchers("and", 2, matchers);
            fail("Expected InvalidUseOfMatchersException");
        } catch (InvalidUseOfMatchersException e) {
            assertTrue(e.getMessage().contains("Invalid use of argument matchers inside additional matcher"));
        }
    }

    @Test
    public void testStubPassedToVerify() throws Throwable {
        Reporter reporter = new Reporter();
        try {
            reporter.stubPassedToVerify();
            fail("Expected CannotVerifyStubOnlyMock");
        } catch (CannotVerifyStubOnlyMock e) {
            assertTrue(e.getMessage().contains("is a stubOnly() mock"));
        }
    }

    @Test
    public void testReportNoSubMatchersFound() throws Throwable {
        Reporter reporter = new Reporter();
        try {
            reporter.reportNoSubMatchersFound("and");
            fail("Expected InvalidUseOfMatchersException");
        } catch (InvalidUseOfMatchersException e) {
            assertTrue(e.getMessage().contains("No matchers found for additional matcher"));
        }
    }

    @Test
    public void testWantedButNotInvoked_Single() throws Throwable {
        Reporter reporter = new Reporter();
        DescribedInvocation wanted = null;
        try {
            // using a dummy mock or null if allowed, but wait, wanted.toString() is called in createWantedButNotInvokedMessage.
            // Let's create an anonymous DescribedInvocation or pass null if it throws NPE before. 
            // Since we must avoid big interfaces, let's use a simple anonymous implementation.
            DescribedInvocation dummy = new DescribedInvocation() {
                public String toString() { return "dummyWantedMethod()"; }
                public org.mockito.invocation.Location getLocation() { return null; }
                public Class<?>[] getArgumentTypes() { return new Class[0]; }
                public List<org.mockito.internal.matchers.CapturingMatcher> getCapturingMatchers() { return null; }
                public Method getMethod() { return null; }
                public Object getMock() { return null; }
                public Object[] getArguments() { return new Object[0]; }
                public org.mockito.invocation.InvocationOnMock ignoreArgumentDecoding() { return null; }
            };
            reporter.wantedButNotInvoked(dummy);
            fail("Expected WantedButNotInvoked");
        } catch (WantedButNotInvoked e) {
            assertTrue(e.getMessage().contains("Wanted but not invoked:"));
        }
    }

    @Test
    public void testWantedButNotInvoked_WithList() throws Throwable {
        Reporter reporter = new Reporter();
        DescribedInvocation dummy = new DescribedInvocation() {
            public String toString() { return "dummyWantedMethod()"; }
            public org.mockito.invocation.Location getLocation() { return null; }
            public Class<?>[] getArgumentTypes() { return new Class[0]; }
            public List<org.mockito.internal.matchers.CapturingMatcher> getCapturingMatchers() { return null; }
            public Method getMethod() { return null; }
            public Object getMock() { return null; }
            public Object[] getArguments() { return new Object[0]; }
            public org.mockito.invocation.InvocationOnMock ignoreArgumentDecoding() { return null; }
        };
        List<DescribedInvocation> invocations = new ArrayList<DescribedInvocation>();
        try {
            reporter.wantedButNotInvoked(dummy, invocations);
            fail("Expected WantedButNotInvoked");
        } catch (WantedButNotInvoked e) {
            assertTrue(e.getMessage().contains("zero interactions"));
        }

        invocations.add(dummy);
        try {
            reporter.wantedButNotInvoked(dummy, invocations);
            fail("Expected WantedButNotInvoked");
        } catch (WantedButNotInvoked e) {
            assertTrue(e.getMessage().contains("other interactions"));
        }
    }

    @Test
    public void testWantedButNotInvokedInOrder() throws Throwable {
        Reporter reporter = new Reporter();
        DescribedInvocation dummy = new DescribedInvocation() {
            public String toString() { return "dummyWantedMethod()"; }
            public org.mockito.invocation.Location getLocation() { return null; }
            public Class<?>[] getArgumentTypes() { return new Class[0]; }
            public List<org.mockito.internal.matchers.CapturingMatcher> getCapturingMatchers() { return null; }
            public Method getMethod() { return null; }
            public Object getMock() { return null; }
            public Object[] getArguments() { return new Object[0]; }
            public org.mockito.invocation.InvocationOnMock ignoreArgumentDecoding() { return null; }
        };
        try {
            reporter.wantedButNotInvokedInOrder(dummy, dummy);
            fail("Expected VerificationInOrderFailure");
        } catch (VerificationInOrderFailure e) {
            assertTrue(e.getMessage().contains("Verification in order failure"));
        }
    }

    @Test
    public void testTooManyActualInvocations() throws Throwable {
        Reporter reporter = new Reporter();
        DescribedInvocation dummy = new DescribedInvocation() {
            public String toString() { return "dummyWantedMethod()"; }
            public org.mockito.invocation.Location getLocation() { return null; }
            public Class<?>[] getArgumentTypes() { return new Class[0]; }
            public List<org.mockito.internal.matchers.CapturingMatcher> getCapturingMatchers() { return null; }
            public Method getMethod() { return null; }
            public Object getMock() { return null; }
            public Object[] getArguments() { return new Object[0]; }
            public org.mockito.invocation.InvocationOnMock ignoreArgumentDecoding() { return null; }
        };
        try {
            reporter.tooManyActualInvocations(1, 2, dummy, null);
            fail("Expected TooManyActualInvocations");
        } catch (TooManyActualInvocations e) {
            assertTrue(e.getMessage().contains("Wanted"));
        }
    }

    @Test
    public void testNeverWantedButInvoked() throws Throwable {
        Reporter reporter = new Reporter();
        DescribedInvocation dummy = new DescribedInvocation() {
            public String toString() { return "dummyWantedMethod()"; }
            public org.mockito.invocation.Location getLocation() { return null; }
            public Class<?>[] getArgumentTypes() { return new Class[0]; }
            public List<org.mockito.internal.matchers.CapturingMatcher> getCapturingMatchers() { return null; }
            public Method getMethod() { return null; }
            public Object getMock() { return null; }
            public Object[] getArguments() { return new Object[0]; }
            public org.mockito.invocation.InvocationOnMock ignoreArgumentDecoding() { return null; }
        };
        try {
            reporter.neverWantedButInvoked(dummy, null);
            fail("Expected NeverWantedButInvoked");
        } catch (NeverWantedButInvoked e) {
            assertTrue(e.getMessage().contains("Never wanted here:"));
        }
    }

    @Test
    public void testTooManyActualInvocationsInOrder() throws Throwable {
        Reporter reporter = new Reporter();
        DescribedInvocation dummy = new DescribedInvocation() {
            public String toString() { return "dummyWantedMethod()"; }
            public org.mockito.invocation.Location getLocation() { return null; }
            public Class<?>[] getArgumentTypes() { return new Class[0]; }
            public List<org.mockito.internal.matchers.CapturingMatcher> getCapturingMatchers() { return null; }
            public Method getMethod() { return null; }
            public Object getMock() { return null; }
            public Object[] getArguments() { return new Object[0]; }
            public org.mockito.invocation.InvocationOnMock ignoreArgumentDecoding() { return null; }
        };
        try {
            reporter.tooManyActualInvocationsInOrder(1, 2, dummy, null);
            fail("Expected VerificationInOrderFailure");
        } catch (VerificationInOrderFailure e) {
            assertTrue(e.getMessage().contains("Verification in order failure"));
        }
    }

    @Test
    public void testTooLittleActualInvocations() throws Throwable {
        Reporter reporter = new Reporter();
        Discrepancy discrepancy = new Discrepancy(1, 2);
        DescribedInvocation dummy = new DescribedInvocation() {
            public String toString() { return "dummyWantedMethod()"; }
            public org.mockito.invocation.Location getLocation() { return null; }
            public Class<?>[] getArgumentTypes() { return new Class[0]; }
            public List<org.mockito.internal.matchers.CapturingMatcher> getCapturingMatchers() { return null; }
            public Method getMethod() { return null; }
            public Object getMock() { return null; }
            public Object[] getArguments() { return new Object[0]; }
            public org.mockito.invocation.InvocationOnMock ignoreArgumentDecoding() { return null; }
        };
        try {
            reporter.tooLittleActualInvocations(discrepancy, dummy, null);
            fail("Expected TooLittleActualInvocations");
        } catch (TooLittleActualInvocations e) {
            assertTrue(e.getMessage().contains("Wanted"));
        }
    }

    @Test
    public void testTooLittleActualInvocationsInOrder() throws Throwable {
        Reporter reporter = new Reporter();
        Discrepancy discrepancy = new Discrepancy(1, 2);
        DescribedInvocation dummy = new DescribedInvocation() {
            public String toString() { return "dummyWantedMethod()"; }
            public org.mockito.invocation.Location getLocation() { return null; }
            public Class<?>[] getArgumentTypes() { return new Class[0]; }
            public List<org.mockito.internal.matchers.CapturingMatcher> getCapturingMatchers() { return null; }
            public Method getMethod() { return null; }
            public Object getMock() { return null; }
            public Object[] getArguments() { return new Object[0]; }
            public org.mockito.invocation.InvocationOnMock ignoreArgumentDecoding() { return null; }
        };
        try {
            reporter.tooLittleActualInvocationsInOrder(discrepancy, dummy, null);
            fail("Expected VerificationInOrderFailure");
        } catch (VerificationInOrderFailure e) {
            assertTrue(e.getMessage().contains("Verification in order failure"));
        }
    }

    @Test
    public void testCannotMockFinalClass() throws Throwable {
        Reporter reporter = new Reporter();
        try {
            reporter.cannotMockFinalClass(String.class);
            fail("Expected MockitoException");
        } catch (MockitoException e) {
            assertTrue(e.getMessage().contains("Cannot mock/spy"));
        }
    }

    @Test
    public void testCannotStubVoidMethodWithAReturnValue() throws Throwable {
        Reporter reporter = new Reporter();
        try {
            reporter.cannotStubVoidMethodWithAReturnValue("voidMethod");
            fail("Expected CannotStubVoidMethodWithReturnValue");
        } catch (CannotStubVoidMethodWithReturnValue e) {
            assertTrue(e.getMessage().contains("is a *void method*"));
        }
    }

    @Test
    public void testOnlyVoidMethodsCanBeSetToDoNothing() throws Throwable {
        Reporter reporter = new Reporter();
        try {
            reporter.onlyVoidMethodsCanBeSetToDoNothing();
            fail("Expected MockitoException");
        } catch (MockitoException e) {
            assertTrue(e.getMessage().contains("Only void methods can doNothing()!"));
        }
    }

    @Test
    public void testWrongTypeOfReturnValue() throws Throwable {
        Reporter reporter = new Reporter();
        try {
            reporter.wrongTypeOfReturnValue("String", "Integer", "method");
            fail("Expected WrongTypeOfReturnValue");
        } catch (WrongTypeOfReturnValue e) {
            assertTrue(e.getMessage().contains("cannot be returned by"));
        }
    }

    @Test
    public void testWantedAtMostX() throws Throwable {
        Reporter reporter = new Reporter();
        try {
            reporter.wantedAtMostX(2, 3);
            fail("Expected MockitoAssertionError");
        } catch (MockitoAssertionError e) {
            assertTrue(e.getMessage().contains("Wanted at most"));
        }
    }

    @Test
    public void testMisplacedArgumentMatcher() throws Throwable {
        Reporter reporter = new Reporter();
        List<LocalizedMatcher> matchers = new ArrayList<LocalizedMatcher>();
        try {
            reporter.misplacedArgumentMatcher(matchers);
            fail("Expected InvalidUseOfMatchersException");
        } catch (InvalidUseOfMatchersException e) {
            assertTrue(e.getMessage().contains("Misplaced argument matcher detected here:"));
        }
    }

    @Test
    public void testSmartNullPointerException() throws Throwable {
        Reporter reporter = new Reporter();
        try {
            reporter.smartNullPointerException("invocation", null);
            fail("Expected SmartNullPointerException");
        } catch (SmartNullPointerException e) {
            assertTrue(e.getMessage().contains("You have a NullPointerException here:"));
        }
    }

    @Test
    public void testNoArgumentValueWasCaptured() throws Throwable {
        Reporter reporter = new Reporter();
        try {
            reporter.noArgumentValueWasCaptured();
            fail("Expected MockitoException");
        } catch (MockitoException e) {
            assertTrue(e.getMessage().contains("No argument value was captured!"));
        }
    }

    @Test
    public void testExtraInterfacesDoesNotAcceptNullParameters() throws Throwable {
        Reporter reporter = new Reporter();
        try {
            reporter.extraInterfacesDoesNotAcceptNullParameters();
            fail("Expected MockitoException");
        } catch (MockitoException e) {
            assertTrue(e.getMessage().contains("extraInterfaces() does not accept null parameters."));
        }
    }

    @Test
    public void testExtraInterfacesAcceptsOnlyInterfaces() throws Throwable {
        Reporter reporter = new Reporter();
        try {
            reporter.extraInterfacesAcceptsOnlyInterfaces(String.class);
            fail("Expected MockitoException");
        } catch (MockitoException e) {
            assertTrue(e.getMessage().contains("extraInterfaces() accepts only interfaces."));
        }
    }

    @Test
    public void testExtraInterfacesCannotContainMockedType() throws Throwable {
        Reporter reporter = new Reporter();
        try {
            reporter.extraInterfacesCannotContainMockedType(String.class);
            fail("Expected MockitoException");
        } catch (MockitoException e) {
            assertTrue(e.getMessage().contains("extraInterfaces() does not accept the same type"));
        }
    }

    @Test
    public void testExtraInterfacesRequiresAtLeastOneInterface() throws Throwable {
        Reporter reporter = new Reporter();
        try {
            reporter.extraInterfacesRequiresAtLeastOneInterface();
            fail("Expected MockitoException");
        } catch (MockitoException e) {
            assertTrue(e.getMessage().contains("extraInterfaces() requires at least one interface."));
        }
    }

    @Test
    public void testMockedTypeIsInconsistentWithSpiedInstanceType() throws Throwable {
        Reporter reporter = new Reporter();
        try {
            reporter.mockedTypeIsInconsistentWithSpiedInstanceType(String.class, new ArrayList<String>());
            fail("Expected MockitoException");
        } catch (MockitoException e) {
            assertTrue(e.getMessage().contains("Mocked type must be the same as the type of your spied instance."));
        }
    }

    @Test
    public void testCannotCallAbstractRealMethod() throws Throwable {
        Reporter reporter = new Reporter();
        try {
            reporter.cannotCallAbstractRealMethod();
            fail("Expected MockitoException");
        } catch (MockitoException e) {
            assertTrue(e.getMessage().contains("Cannot call abstract real method on java object!"));
        }
    }

    @Test
    public void testCannotVerifyToString() throws Throwable {
        Reporter reporter = new Reporter();
        try {
            reporter.cannotVerifyToString();
            fail("Expected MockitoException");
        } catch (MockitoException e) {
            assertTrue(e.getMessage().contains("Mockito cannot verify toString()"));
        }
    }

    @Test
    public void testMoreThanOneAnnotationNotAllowed() throws Throwable {
        Reporter reporter = new Reporter();
        try {
            reporter.moreThanOneAnnotationNotAllowed("field");
            fail("Expected MockitoException");
        } catch (MockitoException e) {
            assertTrue(e.getMessage().contains("You cannot have more than one Mockito annotation"));
        }
    }

    @Test
    public void testUnsupportedCombinationOfAnnotations() throws Throwable {
        Reporter reporter = new Reporter();
        try {
            reporter.unsupportedCombinationOfAnnotations("Mock", "Spy");
            fail("Expected MockitoException");
        } catch (MockitoException e) {
            assertTrue(e.getMessage().contains("This combination of annotations is not permitted"));
        }
    }

    @Test
    public void testCannotInitializeForSpyAnnotation() throws Throwable {
        Reporter reporter = new Reporter();
        try {
            reporter.cannotInitializeForSpyAnnotation("field", new Exception("details"));
            fail("Expected MockitoException");
        } catch (MockitoException e) {
            assertTrue(e.getMessage().contains("Cannot instantiate a @Spy"));
        }
    }

    @Test
    public void testCannotInitializeForInjectMocksAnnotation() throws Throwable {
        Reporter reporter = new Reporter();
        try {
            reporter.cannotInitializeForInjectMocksAnnotation("field", new Exception("details"));
            fail("Expected MockitoException");
        } catch (MockitoException e) {
            assertTrue(e.getMessage().contains("Cannot instantiate @InjectMocks field"));
        }
    }

    @Test
    public void testAtMostAndNeverShouldNotBeUsedWithTimeout() throws Throwable {
        Reporter reporter = new Reporter();
        try {
            reporter.atMostAndNeverShouldNotBeUsedWithTimeout();
            fail("Expected FriendlyReminderException");
        } catch (FriendlyReminderException e) {
            assertTrue(e.getMessage().contains("Don't panic!"));
        }
    }

    @Test
    public void testInvocationListenerDoesNotAcceptNullParameters() throws Throwable {
        Reporter reporter = new Reporter();
        try {
            reporter.invocationListenerDoesNotAcceptNullParameters();
            fail("Expected MockitoException");
        } catch (MockitoException e) {
            assertTrue(e.getMessage().contains("invocationListeners() does not accept null parameters"));
        }
    }

    @Test
    public void testInvocationListenersRequiresAtLeastOneListener() throws Throwable {
        Reporter reporter = new Reporter();
        try {
            reporter.invocationListenersRequiresAtLeastOneListener();
            fail("Expected MockitoException");
        } catch (MockitoException e) {
            assertTrue(e.getMessage().contains("invocationListeners() requires at least one listener"));
        }
    }

    @Test
    public void testInvocationListenerThrewException() throws Throwable {
        Reporter reporter = new Reporter();
        InvocationListener listener = new InvocationListener() {
            public void onInvocation(org.mockito.listeners.MethodInvocationReport report) {}
        };
        try {
            reporter.invocationListenerThrewException(listener, new RuntimeException("fail"));
            fail("Expected MockitoException");
        } catch (MockitoException e) {
            assertTrue(e.getMessage().contains("The invocation listener with type"));
        }
    }

    @Test
    public void testMockedTypeIsInconsistentWithDelegatedInstanceType() throws Throwable {
        Reporter reporter = new Reporter();
        try {
            reporter.mockedTypeIsInconsistentWithDelegatedInstanceType(String.class, new ArrayList<String>());
            fail("Expected MockitoException");
        } catch (MockitoException e) {
            assertTrue(e.getMessage().contains("Mocked type must be the same as the type of your delegated instance."));
        }
    }

    @Test
    public void testSpyAndDelegateAreMutuallyExclusive() throws Throwable {
        Reporter reporter = new Reporter();
        try {
            reporter.spyAndDelegateAreMutuallyExclusive();
            fail("Expected MockitoException");
        } catch (MockitoException e) {
            assertTrue(e.getMessage().contains("Settings should not define a spy instance and a delegated instance"));
        }
    }

    @Test
    public void testInvalidArgumentRangeAtIdentityAnswerCreationTime() throws Throwable {
        Reporter reporter = new Reporter();
        try {
            reporter.invalidArgumentRangeAtIdentityAnswerCreationTime();
            fail("Expected MockitoException");
        } catch (MockitoException e) {
            assertTrue(e.getMessage().contains("Invalid argument index."));
        }
    }

    @Test
    public void testDefaultAnswerDoesNotAcceptNullParameter() throws Throwable {
        Reporter reporter = new Reporter();
        try {
            reporter.defaultAnswerDoesNotAcceptNullParameter();
            fail("Expected MockitoException");
        } catch (MockitoException e) {
            assertTrue(e.getMessage().contains("defaultAnswer() does not accept null parameter"));
        }
    }

    @Test
    public void testSerializableWontWorkForObjectsThatDontImplementSerializable() throws Throwable {
        Reporter reporter = new Reporter();
        try {
            reporter.serializableWontWorkForObjectsThatDontImplementSerializable(String.class);
            fail("Expected MockitoException");
        } catch (MockitoException e) {
            assertTrue(e.getMessage().contains("You are using the setting 'withSettings().serializable()'"));
        }
    }

    @Test
    public void testUsingConstructorWithFancySerializable() throws Throwable {
        Reporter reporter = new Reporter();
        try {
            reporter.usingConstructorWithFancySerializable(SerializableMode.BASIC);
            fail("Expected MockitoException");
        } catch (MockitoException e) {
            assertTrue(e.getMessage().contains("Mocks instantiated with constructor cannot be combined"));
        }
    }
}