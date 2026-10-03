package org.mockito.internal;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import static org.junit.Assert.*;

import java.util.List;

import org.mockito.InOrder;
import org.mockito.exceptions.misusing.NotAMockException;
import org.mockito.internal.creation.MockSettingsImpl;
import org.mockito.internal.invocation.Invocation;
import org.mockito.internal.progress.IOngoingStubbing;
import org.mockito.internal.util.MockUtil;
import org.mockito.internal.verification.api.VerificationMode;
import org.mockito.stubbing.DeprecatedOngoingStubbing;
import org.mockito.stubbing.OngoingStubbing;
import org.mockito.stubbing.Stubber;
import org.mockito.stubbing.VoidMethodStubbable;

public class MockitoCoreClaudeTest {

    private MockitoCore mc;
    private MockUtil mockUtil;

    @Before
    public void setUp() throws Throwable {
        mc = new MockitoCore();
        mockUtil = new MockUtil();
        // defensively clear any leftover global mocking-progress state from a previous test
        try { mc.validateMockitoUsage(); } catch (Throwable ignore) { }
        try { mc.reset(); } catch (Throwable ignore) { }
    }

    @After
    public void tearDown() throws Throwable {
        try { mc.validateMockitoUsage(); } catch (Throwable ignore) { }
        try { mc.reset(); } catch (Throwable ignore) { }
    }

    // mock(Class, MockSettings): returns a real, recognizable mock of the requested type
    @Test
    public void testMock_returnsInstanceRecognizedAsMock() throws Throwable {
        Object mockObj = mc.mock(List.class, new MockSettingsImpl());
        assertNotNull(mockObj);
        assertTrue(mockUtil.isMock(mockObj));
        assertTrue(mockObj instanceof List);
    }

    // mock(Class, MockSettings, boolean): three-arg overload also returns a recognizable mock
    @Test
    public void testMock_threeArg_returnsInstanceRecognizedAsMock() throws Throwable {
        Object mockObj = mc.mock(List.class, new MockSettingsImpl(), true);
        assertNotNull(mockObj);
        assertTrue(mockUtil.isMock(mockObj));
    }

    // two-arg mock() always resets ongoing stubbing before creating the new mock
    @Test
    public void testMock_twoArg_alwaysResetsOngoingStubbing() throws Throwable {
        List mock1 = (List) mc.mock(List.class, new MockSettingsImpl());
        mock1.size();
        mc.mock(List.class, new MockSettingsImpl());
        try {
            mc.stub();
            fail("expected missing method invocation exception");
        } catch (RuntimeException expected) { }
    }

    // mock(Class, MockSettings, true): shouldResetOngoingStubbing=true must reset ongoing stubbing
    @Test
    public void testMock_shouldResetOngoingStubbingTrue_resetsOngoingStubbing() throws Throwable {
        List mock1 = (List) mc.mock(List.class, new MockSettingsImpl());
        mock1.size();
        mc.mock(List.class, new MockSettingsImpl(), true);
        try {
            mc.stub();
            fail("expected missing method invocation exception");
        } catch (RuntimeException expected) { }
    }

    // BUG HUNT: mock(Class, MockSettings, false) must PRESERVE ongoing stubbing per its contract
    @Test
    public void testMock_shouldResetOngoingStubbingFalse_preservesOngoingStubbing() throws Throwable {
        List mock1 = (List) mc.mock(List.class, new MockSettingsImpl());
        mock1.size();
        mc.mock(List.class, new MockSettingsImpl(), false);
        IOngoingStubbing stubbing = mc.stub();
        assertNotNull(stubbing);
    }

    // stub(): no ongoing stubbing registered -> missing method invocation exception
    @Test
    public void testStub_noOngoingStubbing_throws() throws Throwable {
        try {
            mc.stub();
            fail("expected missing method invocation exception");
        } catch (RuntimeException expected) { }
    }

    // stub(): ongoing stubbing registered via a real invocation -> returns non-null stubbing
    @Test
    public void testStub_withOngoingStubbing_returnsNonNull() throws Throwable {
        List mock1 = (List) mc.mock(List.class, new MockSettingsImpl());
        mock1.size();
        IOngoingStubbing stubbing = mc.stub();
        assertNotNull(stubbing);
    }

    // deprecated stub(T): no ongoing stubbing -> missing method invocation exception
    @Test
    public void testDeprecatedStub_noOngoingStubbing_throws() throws Throwable {
        try {
            mc.stub(new Object());
            fail("expected missing method invocation exception");
        } catch (RuntimeException expected) { }
    }

    // deprecated stub(T): with ongoing stubbing -> returns non-null stubbing
    @Test
    public void testDeprecatedStub_withOngoingStubbing_returnsNonNull() throws Throwable {
        List mock1 = (List) mc.mock(List.class, new MockSettingsImpl());
        mock1.size();
        DeprecatedOngoingStubbing stubbing = mc.stub(mock1);
        assertNotNull(stubbing);
    }

    // when(T): no ongoing stubbing -> missing method invocation exception
    @Test
    public void testWhen_noOngoingStubbing_throws() throws Throwable {
        try {
            mc.when(new Object());
            fail("expected missing method invocation exception");
        } catch (RuntimeException expected) { }
    }

    // when(T): with ongoing stubbing from a real invocation -> returns non-null OngoingStubbing
    @Test
    public void testWhen_withOngoingStubbing_returnsNonNull() throws Throwable {
        List mock1 = (List) mc.mock(List.class, new MockSettingsImpl());
        OngoingStubbing stubbing = mc.when(mock1.size());
        assertNotNull(stubbing);
    }

    // verify(T, mode): mock == null branch -> exception before verificationStarted is reached
    @Test
    public void testVerify_nullMock_throws() throws Throwable {
        try {
            mc.verify((Object) null, (VerificationMode) null);
            fail("expected exception for null mock");
        } catch (RuntimeException expected) { }
    }

    // verify(T, mode): !isMock(mock) branch -> exception before verificationStarted is reached
    @Test
    public void testVerify_notAMock_throws() throws Throwable {
        try {
            mc.verify((Object) "notAMock", (VerificationMode) null);
            fail("expected exception for non-mock object");
        } catch (RuntimeException expected) { }
    }

    // reset(): zero mocks (0-iteration loop) still resets ongoing stubbing
    @Test
    public void testReset_zeroMocks_resetsOngoingStubbing() throws Throwable {
        List mock1 = (List) mc.mock(List.class, new MockSettingsImpl());
        mock1.size();
        mc.reset();
        try {
            mc.stub();
            fail("expected missing method invocation exception after reset");
        } catch (RuntimeException expected) { }
    }

    // reset(mock): 1-iteration loop resets the mock but the object remains a mock
    @Test
    public void testReset_withMock_mockRemainsAMock() throws Throwable {
        List mock1 = (List) mc.mock(List.class, new MockSettingsImpl());
        mc.reset(mock1);
        assertTrue(mockUtil.isMock(mock1));
    }

    // verifyNoMoreInteractions(): empty array -> mocksHaveToBePassed exception
    @Test
    public void testVerifyNoMoreInteractions_emptyArray_throws() throws Throwable {
        try {
            mc.verifyNoMoreInteractions(new Object[0]);
            fail("expected exception for empty mocks array");
        } catch (RuntimeException expected) { }
    }

    // verifyNoMoreInteractions(): null element in array -> nullPassed exception
    @Test
    public void testVerifyNoMoreInteractions_nullElement_throws() throws Throwable {
        try {
            mc.verifyNoMoreInteractions(new Object[] { null });
            fail("expected exception for null mock element");
        } catch (RuntimeException expected) { }
    }

    // verifyNoMoreInteractions(): non-mock element -> NotAMockException caught, notAMockPassed exception
    @Test
    public void testVerifyNoMoreInteractions_notAMockElement_throws() throws Throwable {
        try {
            mc.verifyNoMoreInteractions(new Object[] { "notAMock" });
            fail("expected exception for non-mock element");
        } catch (RuntimeException expected) { }
    }

    // verifyNoMoreInteractions(): real mock with no interactions -> succeeds, still a mock
    @Test
    public void testVerifyNoMoreInteractions_realMockNoInteractions_doesNotThrow() throws Throwable {
        List mock1 = (List) mc.mock(List.class, new MockSettingsImpl());
        mc.verifyNoMoreInteractions(new Object[] { mock1 });
        assertTrue(mockUtil.isMock(mock1));
    }

    // inOrder(): empty array -> mocksHaveToBePassedWhenCreatingInOrder exception
    @Test
    public void testInOrder_emptyArray_throws() throws Throwable {
        try {
            mc.inOrder(new Object[0]);
            fail("expected exception for empty mocks array");
        } catch (RuntimeException expected) { }
    }

    // inOrder(): null element -> nullPassedWhenCreatingInOrder exception
    @Test
    public void testInOrder_nullElement_throws() throws Throwable {
        try {
            mc.inOrder(new Object[] { null });
            fail("expected exception for null element");
        } catch (RuntimeException expected) { }
    }

    // inOrder(): non-mock element -> notAMockPassedWhenCreatingInOrder exception
    @Test
    public void testInOrder_notAMockElement_throws() throws Throwable {
        try {
            mc.inOrder(new Object[] { "notAMock" });
            fail("expected exception for non-mock element");
        } catch (RuntimeException expected) { }
    }

    // inOrder(): real mock -> returns a usable InOrder instance
    @Test
    public void testInOrder_realMock_returnsInOrderInstance() throws Throwable {
        List mock1 = (List) mc.mock(List.class, new MockSettingsImpl());
        InOrder inOrder = mc.inOrder(new Object[] { mock1 });
        assertNotNull(inOrder);
    }

    // doAnswer(): returns a Stubber and resets ongoing stubbing unconditionally
    @Test
    public void testDoAnswer_returnsStubberAndResetsOngoingStubbing() throws Throwable {
        List mock1 = (List) mc.mock(List.class, new MockSettingsImpl());
        mock1.size();
        Stubber stubber = mc.doAnswer(null);
        assertNotNull(stubber);
        try {
            mc.stub();
            fail("expected missing method invocation exception after doAnswer reset");
        } catch (RuntimeException expected) { }
    }

    // stubVoid(): real mock -> returns a non-null VoidMethodStubbable
    @Test
    public void testStubVoid_realMock_returnsVoidMethodStubbable() throws Throwable {
        List mock1 = (List) mc.mock(List.class, new MockSettingsImpl());
        VoidMethodStubbable stubbable = mc.stubVoid(mock1);
        assertNotNull(stubbable);
    }

    // stubVoid(): non-mock object -> getMockHandler throws NotAMockException (uncaught here)
    @Test
    public void testStubVoid_notAMock_throwsNotAMockException() throws Throwable {
        try {
            mc.stubVoid("notAMock");
            fail("expected NotAMockException");
        } catch (NotAMockException expected) { }
    }

    // validateMockitoUsage(): unfinished stubbing (when() without completion) must be detected
    @Test
    public void testValidateMockitoUsage_unfinishedStubbing_throws() throws Throwable {
        List mock1 = (List) mc.mock(List.class, new MockSettingsImpl());
        mc.when(mock1.size());
        try {
            mc.validateMockitoUsage();
            fail("expected exception for unfinished stubbing");
        } catch (RuntimeException expected) { }
    }

    // getLastInvocation(): ongoing stubbing present -> returns the registered invocation
    @Test
    public void testGetLastInvocation_withOngoingStubbing_returnsInvocation() throws Throwable {
        List mock1 = (List) mc.mock(List.class, new MockSettingsImpl());
        mock1.size();
        Invocation invocation = mc.getLastInvocation();
        assertNotNull(invocation);
    }

    // getLastInvocation(): no ongoing stubbing -> null dereference causes NullPointerException
    @Test
    public void testGetLastInvocation_noOngoingStubbing_throwsNullPointerException() throws Throwable {
        try {
            mc.getLastInvocation();
            fail("expected NullPointerException when no ongoing stubbing present");
        } catch (NullPointerException expected) { }
    }
}
