package org.mockito.exceptions;

import static org.junit.Assert.*;

import java.util.ArrayList;
import java.util.List;

import org.junit.Before;
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
import org.mockito.exceptions.verification.SmartNullPointerException;
import org.mockito.internal.debugging.Location;

public class ReporterClaudeTest {

    private Reporter reporter;

    @Before
    public void setUp() throws Throwable {
        reporter = new Reporter();
    }

    // checkedExceptionInvalid: message must contain the throwable info passed in
    @Test
    public void testCheckedExceptionInvalid_throwsMockitoException_containsThrowableInfo() throws Throwable {
        Throwable t = new IllegalStateException("boom123");
        try {
            reporter.checkedExceptionInvalid(t);
            fail("expected MockitoException");
        } catch (MockitoException e) {
            assertTrue(e.getMessage().contains("boom123"));
            assertTrue(e.getMessage().contains("Checked exception is invalid"));
        }
    }

    // cannotStubWithNullThrowable: simple no-arg throw path
    @Test
    public void testCannotStubWithNullThrowable_throwsMockitoException() throws Throwable {
        try {
            reporter.cannotStubWithNullThrowable();
            fail("expected MockitoException");
        } catch (MockitoException e) {
            assertTrue(e.getMessage().contains("Cannot stub with null throwable"));
        }
    }

    // unfinishedStubbing: location param path
    @Test
    public void testUnfinishedStubbing_throwsUnfinishedStubbingException() throws Throwable {
        Location loc = new Location();
        try {
            reporter.unfinishedStubbing(loc);
            fail("expected UnfinishedStubbingException");
        } catch (UnfinishedStubbingException e) {
            assertTrue(e.getMessage().contains("Unfinished stubbing detected here"));
            assertTrue(e.getMessage().contains("thenReturn()"));
        }
    }

    // missingMethodInvocation: no-arg throw path
    @Test
    public void testMissingMethodInvocation_throwsMissingMethodInvocationException() throws Throwable {
        try {
            reporter.missingMethodInvocation();
            fail("expected MissingMethodInvocationException");
        } catch (MissingMethodInvocationException e) {
            assertTrue(e.getMessage().contains("when() requires an argument"));
        }
    }

    // unfinishedVerificationException: object constructed then thrown
    @Test
    public void testUnfinishedVerificationException_throwsUnfinishedVerificationException() throws Throwable {
        Location loc = new Location();
        try {
            reporter.unfinishedVerificationException(loc);
            fail("expected UnfinishedVerificationException");
        } catch (UnfinishedVerificationException e) {
            assertTrue(e.getMessage().contains("Missing method call for verify(mock)"));
        }
    }

    // notAMockPassedToVerify: class simple name must appear in message
    @Test
    public void testNotAMockPassedToVerify_containsSimpleClassName() throws Throwable {
        try {
            reporter.notAMockPassedToVerify(Integer.class);
            fail("expected NotAMockException");
        } catch (NotAMockException e) {
            assertTrue(e.getMessage().contains("is of type Integer and is not a mock!"));
        }
    }

    // nullPassedToVerify: no-arg throw path
    @Test
    public void testNullPassedToVerify_throwsNullInsteadOfMockException() throws Throwable {
        try {
            reporter.nullPassedToVerify();
            fail("expected NullInsteadOfMockException");
        } catch (NullInsteadOfMockException e) {
            assertTrue(e.getMessage().contains("Argument passed to verify() should be a mock but is null!"));
        }
    }

    // notAMockPassedToWhenMethod: no-arg throw path
    @Test
    public void testNotAMockPassedToWhenMethod_throwsNotAMockException() throws Throwable {
        try {
            reporter.notAMockPassedToWhenMethod();
            fail("expected NotAMockException");
        } catch (NotAMockException e) {
            assertTrue(e.getMessage().contains("Argument passed to when() is not a mock!"));
        }
    }

    // nullPassedToWhenMethod: no-arg throw path
    @Test
    public void testNullPassedToWhenMethod_throwsNullInsteadOfMockException() throws Throwable {
        try {
            reporter.nullPassedToWhenMethod();
            fail("expected NullInsteadOfMockException");
        } catch (NullInsteadOfMockException e) {
            assertTrue(e.getMessage().contains("Argument passed to when() is null!"));
        }
    }

    // mocksHaveToBePassedToVerifyNoMoreInteractions: no-arg throw path
    @Test
    public void testMocksHaveToBePassedToVerifyNoMoreInteractions_throwsMockitoException() throws Throwable {
        try {
            reporter.mocksHaveToBePassedToVerifyNoMoreInteractions();
            fail("expected MockitoException");
        } catch (MockitoException e) {
            assertTrue(e.getMessage().contains("Method requires argument(s)!"));
        }
    }

    // notAMockPassedToVerifyNoMoreInteractions: no-arg throw path
    @Test
    public void testNotAMockPassedToVerifyNoMoreInteractions_throwsNotAMockException() throws Throwable {
        try {
            reporter.notAMockPassedToVerifyNoMoreInteractions();
            fail("expected NotAMockException");
        } catch (NotAMockException e) {
            assertTrue(e.getMessage().contains("Argument(s) passed is not a mock!"));
        }
    }

    // nullPassedToVerifyNoMoreInteractions: no-arg throw path
    @Test
    public void testNullPassedToVerifyNoMoreInteractions_throwsNullInsteadOfMockException() throws Throwable {
        try {
            reporter.nullPassedToVerifyNoMoreInteractions();
            fail("expected NullInsteadOfMockException");
        } catch (NullInsteadOfMockException e) {
            assertTrue(e.getMessage().contains("Argument(s) passed is null!"));
        }
    }

    // notAMockPassedWhenCreatingInOrder: no-arg throw path
    @Test
    public void testNotAMockPassedWhenCreatingInOrder_throwsNotAMockException() throws Throwable {
        try {
            reporter.notAMockPassedWhenCreatingInOrder();
            fail("expected NotAMockException");
        } catch (NotAMockException e) {
            assertTrue(e.getMessage().contains("Pass mocks that require verification in order"));
        }
    }

    // nullPassedWhenCreatingInOrder: no-arg throw path
    @Test
    public void testNullPassedWhenCreatingInOrder_throwsNullInsteadOfMockException() throws Throwable {
        try {
            reporter.nullPassedWhenCreatingInOrder();
            fail("expected NullInsteadOfMockException");
        } catch (NullInsteadOfMockException e) {
            assertTrue(e.getMessage().contains("Argument(s) passed is null!"));
        }
    }

    // mocksHaveToBePassedWhenCreatingInOrder: no-arg throw path
    @Test
    public void testMocksHaveToBePassedWhenCreatingInOrder_throwsMockitoException() throws Throwable {
        try {
            reporter.mocksHaveToBePassedWhenCreatingInOrder();
            fail("expected MockitoException");
        } catch (MockitoException e) {
            assertTrue(e.getMessage().contains("Method requires argument(s)!"));
        }
    }

    // inOrderRequiresFamiliarMock: no-arg throw path
    @Test
    public void testInOrderRequiresFamiliarMock_throwsMockitoException() throws Throwable {
        try {
            reporter.inOrderRequiresFamiliarMock();
            fail("expected MockitoException");
        } catch (MockitoException e) {
            assertTrue(e.getMessage().contains("InOrder can only verify mocks"));
        }
    }

    // invalidUseOfMatchers: expectedMatchersCount must precede recordedMatchersCount in message (param order)
    @Test
    public void testInvalidUseOfMatchers_typicalCounts_exactMessage() throws Throwable {
        try {
            reporter.invalidUseOfMatchers(3, 7);
            fail("expected InvalidUseOfMatchersException");
        } catch (InvalidUseOfMatchersException e) {
            assertTrue(e.getMessage().contains("3 matchers expected, 7 recorded."));
        }
    }

    // invalidUseOfMatchers: boundary zero counts
    @Test
    public void testInvalidUseOfMatchers_zeroCounts_exactMessage() throws Throwable {
        try {
            reporter.invalidUseOfMatchers(0, 0);
            fail("expected InvalidUseOfMatchersException");
        } catch (InvalidUseOfMatchersException e) {
            assertTrue(e.getMessage().contains("0 matchers expected, 0 recorded."));
        }
    }

    // argumentsAreDifferent: message must contain both wanted and actual strings
    @Test
    public void testArgumentsAreDifferent_messageContainsWantedAndActual() throws Throwable {
        Location loc = new Location();
        try {
            reporter.argumentsAreDifferent("WANTED_VALUE_X", "ACTUAL_VALUE_Y", loc);
            fail("expected AssertionError");
        } catch (AssertionError e) {
            assertTrue(e.getMessage().contains("WANTED_VALUE_X"));
            assertTrue(e.getMessage().contains("ACTUAL_VALUE_Y"));
        }
    }

    // cannotMockFinalClass: message must contain the class info
    @Test
    public void testCannotMockFinalClass_containsClassInfo() throws Throwable {
        try {
            reporter.cannotMockFinalClass(String.class);
            fail("expected MockitoException");
        } catch (MockitoException e) {
            assertTrue(e.getMessage().contains("java.lang.String"));
            assertTrue(e.getMessage().contains("final classes"));
        }
    }

    // cannotStubVoidMethodWithAReturnValue: methodName must appear quoted in message
    @Test
    public void testCannotStubVoidMethodWithAReturnValue_containsMethodName() throws Throwable {
        try {
            reporter.cannotStubVoidMethodWithAReturnValue("doSomething");
            fail("expected MockitoException");
        } catch (MockitoException e) {
            assertTrue(e.getMessage().contains("'doSomething' is a *void method*"));
        }
    }

    // onlyVoidMethodsCanBeSetToDoNothing: no-arg throw path
    @Test
    public void testOnlyVoidMethodsCanBeSetToDoNothing_throwsMockitoException() throws Throwable {
        try {
            reporter.onlyVoidMethodsCanBeSetToDoNothing();
            fail("expected MockitoException");
        } catch (MockitoException e) {
            assertTrue(e.getMessage().contains("Only void methods can doNothing()!"));
        }
    }

    // wrongTypeOfReturnValue: expectedType/actualType/methodName must be placed per their names
    @Test
    public void testWrongTypeOfReturnValue_exactMessageOrder() throws Throwable {
        try {
            reporter.wrongTypeOfReturnValue("String", "Integer", "getName");
            fail("expected WrongTypeOfReturnValue");
        } catch (WrongTypeOfReturnValue e) {
            assertTrue(e.getMessage().contains("Integer cannot be returned by getName()"));
            assertTrue(e.getMessage().contains("getName() should return String"));
        }
    }

    // wantedAtMostX: foundSize value must appear in the "but was" clause
    @Test
    public void testWantedAtMostX_containsFoundSize() throws Throwable {
        try {
            reporter.wantedAtMostX(1, 5);
            fail("expected MockitoAssertionError");
        } catch (MockitoAssertionError e) {
            assertTrue(e.getMessage().contains("Wanted at most"));
            assertTrue(e.getMessage().contains("but was 5"));
        }
    }

    // misplacedArgumentMatcher: location-bearing path
    @Test
    public void testMisplacedArgumentMatcher_throwsInvalidUseOfMatchersException() throws Throwable {
        Location loc = new Location();
        try {
            reporter.misplacedArgumentMatcher(loc);
            fail("expected InvalidUseOfMatchersException");
        } catch (InvalidUseOfMatchersException e) {
            assertTrue(e.getMessage().contains("Misplaced argument matcher detected here"));
        }
    }

    // smartNullPointerException: location-bearing path
    @Test
    public void testSmartNullPointerException_throwsSmartNullPointerException() throws Throwable {
        Location loc = new Location();
        try {
            reporter.smartNullPointerException(loc);
            fail("expected SmartNullPointerException");
        } catch (SmartNullPointerException e) {
            assertTrue(e.getMessage().contains("You have a NullPointerException here"));
            assertTrue(e.getMessage().contains("stubbed correctly"));
        }
    }

    // noArgumentValueWasCaptured: no-arg throw path
    @Test
    public void testNoArgumentValueWasCaptured_throwsMockitoException() throws Throwable {
        try {
            reporter.noArgumentValueWasCaptured();
            fail("expected MockitoException");
        } catch (MockitoException e) {
            assertTrue(e.getMessage().contains("No argument value was captured!"));
        }
    }

    // extraInterfacesDoesNotAcceptNullParameters: no-arg throw path
    @Test
    public void testExtraInterfacesDoesNotAcceptNullParameters_throwsMockitoException() throws Throwable {
        try {
            reporter.extraInterfacesDoesNotAcceptNullParameters();
            fail("expected MockitoException");
        } catch (MockitoException e) {
            assertTrue(e.getMessage().contains("extraInterfaces() does not accept null parameters."));
        }
    }

    // extraInterfacesAcceptsOnlyInterfaces: wrongType simple name must appear
    @Test
    public void testExtraInterfacesAcceptsOnlyInterfaces_exactMessage() throws Throwable {
        try {
            reporter.extraInterfacesAcceptsOnlyInterfaces(String.class);
            fail("expected MockitoException");
        } catch (MockitoException e) {
            assertTrue(e.getMessage().contains("You passed following type: String which is not an interface."));
        }
    }

    // extraInterfacesCannotContainMockedType: wrongType simple name must appear
    @Test
    public void testExtraInterfacesCannotContainMockedType_exactMessage() throws Throwable {
        try {
            reporter.extraInterfacesCannotContainMockedType(Integer.class);
            fail("expected MockitoException");
        } catch (MockitoException e) {
            assertTrue(e.getMessage().contains("You mocked following type: Integer"));
        }
    }

    // extraInterfacesRequiresAtLeastOneInterface: no-arg throw path
    @Test
    public void testExtraInterfacesRequiresAtLeastOneInterface_throwsMockitoException() throws Throwable {
        try {
            reporter.extraInterfacesRequiresAtLeastOneInterface();
            fail("expected MockitoException");
        } catch (MockitoException e) {
            assertTrue(e.getMessage().contains("extraInterfaces() requires at least one interface."));
        }
    }

    // mockedTypeIsInconsistentWithSpiedInstanceType: "must be" must use spiedInstance's class, "but is" must use mockedType
    @Test
    public void testMockedTypeIsInconsistentWithSpiedInstanceType_exactMessage() throws Throwable {
        List<Object> spiedInstance = new ArrayList<Object>();
        try {
            reporter.mockedTypeIsInconsistentWithSpiedInstanceType(List.class, spiedInstance);
            fail("expected MockitoException");
        } catch (MockitoException e) {
            assertTrue(e.getMessage().contains("must be: ArrayList"));
            assertTrue(e.getMessage().contains("but is: List"));
        }
    }

    // cannotCallRealMethodOnInterface: no-arg throw path
    @Test
    public void testCannotCallRealMethodOnInterface_throwsMockitoException() throws Throwable {
        try {
            reporter.cannotCallRealMethodOnInterface();
            fail("expected MockitoException");
        } catch (MockitoException e) {
            assertTrue(e.getMessage().contains("Cannot call real method on java interface"));
        }
    }

    // cannotVerifyToString: no-arg throw path
    @Test
    public void testCannotVerifyToString_throwsMockitoException() throws Throwable {
        try {
            reporter.cannotVerifyToString();
            fail("expected MockitoException");
        } catch (MockitoException e) {
            assertTrue(e.getMessage().contains("Mockito cannot verify toString()"));
        }
    }

    // moreThanOneAnnotationNotAllowed: fieldName must appear quoted in message
    @Test
    public void testMoreThanOneAnnotationNotAllowed_containsFieldName() throws Throwable {
        try {
            reporter.moreThanOneAnnotationNotAllowed("myField");
            fail("expected MockitoException");
        } catch (MockitoException e) {
            assertTrue(e.getMessage().contains("'myField'"));
        }
    }

    // unsupportedCombinationOfAnnotations: param order must be preserved as "@one and @two"
    @Test
    public void testUnsupportedCombinationOfAnnotations_exactOrder() throws Throwable {
        try {
            reporter.unsupportedCombinationOfAnnotations("Mock", "Spy");
            fail("expected MockitoException");
        } catch (MockitoException e) {
            assertTrue(e.getMessage().contains("@Mock and @Spy"));
        }
    }

    // cannotInitializeForSpyAnnotation: fieldName and details message/cause must be reflected
    @Test
    public void testCannotInitializeForSpyAnnotation_containsFieldAndCause() throws Throwable {
        Exception details = new Exception("spy-detail-msg");
        try {
            reporter.cannotInitializeForSpyAnnotation("myField", details);
            fail("expected MockitoException");
        } catch (MockitoException e) {
            assertTrue(e.getMessage().contains("myField"));
            assertTrue(e.getMessage().contains("spy-detail-msg"));
            assertTrue(e.getMessage().contains("@Spy"));
            assertSame(details, e.getCause());
        }
    }

    // cannotInitializeForInjectMocksAnnotation: fieldName and details message/cause must be reflected
    @Test
    public void testCannotInitializeForInjectMocksAnnotation_containsFieldAndCause() throws Throwable {
        Exception details = new Exception("inject-detail-msg");
        try {
            reporter.cannotInitializeForInjectMocksAnnotation("myField", details);
            fail("expected MockitoException");
        } catch (MockitoException e) {
            assertTrue(e.getMessage().contains("myField"));
            assertTrue(e.getMessage().contains("inject-detail-msg"));
            assertTrue(e.getMessage().contains("@InjectMocks"));
            assertSame(details, e.getCause());
        }
    }
}
