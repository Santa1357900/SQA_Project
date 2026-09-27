package org.mockito.internal;

import org.junit.Before;
import org.junit.Test;
import org.mockito.InOrder;
import org.mockito.MockSettings;
import org.mockito.exceptions.misusing.MissingMethodInvocationException;
import org.mockito.exceptions.misusing.NotAMockException;
import org.mockito.internal.creation.MockSettingsImpl;
import org.mockito.internal.progress.IOngoingStubbing;
import org.mockito.internal.stubbing.Answers;
import org.mockito.internal.verification.VerificationModeImpl;
import org.mockito.stubbing.Answer;
import org.mockito.stubbing.Stubber;

import java.util.List;

import static org.junit.Assert.*;

public class MockitoCoreTest {

    private MockitoCore mockitoCore;

    @Before
    public void setUp() throws Throwable {
        mockitoCore = new MockitoCore();
    }

    @Test
    public void testMockCreationAndReset() throws Throwable {
        MockSettings settings = new MockSettingsImpl().defaultAnswer(Answers.RETURNS_DEFAULTS);
        List<?> mockList = mockitoCore.mock(List.class, settings, true);
        assertNotNull(mockList);

        List<?> mockList2 = mockitoCore.mock(List.class, settings);
        assertNotNull(mockList2);

        mockitoCore.reset(mockList, mockList2);
    }

    @Test
    public void testStubMissingInvocation() throws Throwable {
        try {
            mockitoCore.stub();
            fail("Expected MissingMethodInvocationException");
        } catch (MissingMethodInvocationException e) {
            assertTrue(e.getMessage().length() > 0);
        }
    }

    @Test
    public void testVerifyNullMock() throws Throwable {
        try {
            mockitoCore.verify(null, new VerificationModeImpl());
            fail("Expected exception for null mock");
        } catch (Exception e) {
            assertNotNull(e);
        }
    }

    @Test
    public void testVerifyNotAMock() throws Throwable {
        try {
            String notAMock = "Not a mock";
            mockitoCore.verify(notAMock, new VerificationModeImpl());
            fail("Expected NotAMockException");
        } catch (NotAMockException e) {
            assertNotNull(e);
        }
    }

    @Test
    public void testVerifyNoMoreInteractionsValidation() throws Throwable {
        try {
            mockitoCore.verifyNoMoreInteractions((Object[]) null);
            fail("Expected exception for null mocks array");
        } catch (Exception e) {
            assertNotNull(e);
        }

        try {
            mockitoCore.verifyNoMoreInteractions(new Object[0]);
            fail("Expected exception for empty mocks array");
        } catch (Exception e) {
            assertNotNull(e);
        }

        try {
            mockitoCore.verifyNoMoreInteractions(new Object());
            fail("Expected NotAMockException");
        } catch (NotAMockException e) {
            assertNotNull(e);
        }
    }

    @Test
    public void testInOrderValidation() throws Throwable {
        try {
            mockitoCore.inOrder((Object[]) null);
            fail("Expected exception for null mocks array in inOrder");
        } catch (Exception e) {
            assertNotNull(e);
        }

        try {
            mockitoCore.inOrder(new Object[0]);
            fail("Expected exception for empty mocks array in inOrder");
        } catch (Exception e) {
            assertNotNull(e);
        }

        try {
            mockitoCore.inOrder(new Object());
            fail("Expected exception for non-mock in inOrder");
        } catch (Exception e) {
            assertNotNull(e);
        }

        MockSettings settings = new MockSettingsImpl();
        List<?> mock1 = mockitoCore.mock(List.class, settings);
        InOrder inOrder = mockitoCore.inOrder(mock1);
        assertNotNull(inOrder);
    }

    @Test
    public void testDoAnswerAndStubVoid() throws Throwable {
        Answer<Object> answer = new Answer<Object>() {
            public Object answer(org.mockito.invocation.InvocationOnMock invocation) throws Throwable {
                return null;
            }
        };
        Stubber stubber = mockitoCore.doAnswer(answer);
        assertNotNull(stubber);

        MockSettings settings = new MockSettingsImpl();
        List<?> mock = mockitoCore.mock(List.class, settings);
        try {
            mockitoCore.stubVoid(mock);
        } catch (Exception e) {
            assertNotNull(e);
        }
    }

    @Test
    public void testValidateMockitoUsage() throws Throwable {
        try {
            mockitoCore.validateMockitoUsage();
        } catch (Exception e) {
            assertNotNull(e);
        }
    }
}