package org.mockito.internal;

import java.util.ArrayList;
import java.util.List;

import org.junit.Test;
import static org.junit.Assert.*;

import org.mockito.internal.creation.MockSettingsImpl;
import org.mockito.stubbing.Answer;
import org.mockito.stubbing.VoidMethodStubbable;

public class MockHandlerClaudeTest {

    // covers constructor(MockSettingsImpl) initializing all three collaborator fields
    @Test
    public void testConstructorWithMockSettings_initializesContainerBinderProgress_nonNull() throws Throwable {
        MockHandler<Object> handler = new MockHandler<Object>(new MockSettingsImpl());
        assertNotNull(handler.invocationContainerImpl);
        assertNotNull(handler.matchersBinder);
        assertNotNull(handler.mockingProgress);
    }

    // covers mockSettings field assignment in constructor(MockSettingsImpl)
    @Test
    public void testConstructorWithMockSettings_storesGivenSettings_sameInstance() throws Throwable {
        MockSettingsImpl settings = new MockSettingsImpl();
        MockHandler<Object> handler = new MockHandler<Object>(settings);
        assertSame(settings, handler.getMockSettings());
    }

    // covers constructor(MockSettingsImpl) branch where settings is null - no validation performed
    @Test
    public void testConstructorWithNullMockSettings_doesNotThrow_getMockSettingsReturnsNull() throws Throwable {
        MockHandler<Object> handler = new MockHandler<Object>((MockSettingsImpl) null);
        assertNull(handler.getMockSettings());
    }

    // covers package-private no-arg constructor delegating to this(new MockSettingsImpl())
    @Test
    public void testDefaultConstructor_createsNonNullMockSettings() throws Throwable {
        MockHandler<Object> handler = new MockHandler<Object>();
        assertNotNull(handler.getMockSettings());
    }

    // covers default constructor creating a fresh InvocationContainerImpl per instance
    @Test
    public void testDefaultConstructor_createsFreshContainerEachTime() throws Throwable {
        MockHandler<Object> handler1 = new MockHandler<Object>();
        MockHandler<Object> handler2 = new MockHandler<Object>();
        assertNotSame(handler1.invocationContainerImpl, handler2.invocationContainerImpl);
    }

    // covers MockHandler(MockHandlerInterface) delegating to this(oldMockHandler.getMockSettings())
    @Test
    public void testConstructorFromOldHandler_copiesMockSettingsReference() throws Throwable {
        MockSettingsImpl settings = new MockSettingsImpl();
        MockHandler<Object> original = new MockHandler<Object>(settings);
        MockHandler<Object> copy = new MockHandler<Object>(original);
        assertSame(settings, copy.getMockSettings());
    }

    // covers MockHandler(MockHandlerInterface) building a brand new InvocationContainerImpl
    @Test
    public void testConstructorFromOldHandler_createsFreshInvocationContainer() throws Throwable {
        MockHandler<Object> original = new MockHandler<Object>(new MockSettingsImpl());
        MockHandler<Object> copy = new MockHandler<Object>(original);
        assertNotSame(original.invocationContainerImpl, copy.invocationContainerImpl);
    }

    // covers constructor branch where oldMockHandler.getMockSettings() is invoked on a null reference
    @Test
    public void testConstructorFromNullOldHandler_throwsNullPointerException() throws Throwable {
        try {
            new MockHandler<Object>((MockHandlerInterface<Object>) null);
            fail("expected NullPointerException");
        } catch (NullPointerException expected) {
        }
    }

    // covers getMockSettings() returning the same final field reference on repeated calls
    @Test
    public void testGetMockSettings_returnsSameInstanceAcrossCalls() throws Throwable {
        MockHandler<Object> handler = new MockHandler<Object>(new MockSettingsImpl());
        assertSame(handler.getMockSettings(), handler.getMockSettings());
    }

    // covers each handler retaining its own constructor-provided settings instance
    @Test
    public void testGetMockSettings_distinctHandlersHaveDistinctSettingsInstances() throws Throwable {
        MockHandler<Object> handler1 = new MockHandler<Object>(new MockSettingsImpl());
        MockHandler<Object> handler2 = new MockHandler<Object>(new MockSettingsImpl());
        assertNotSame(handler1.getMockSettings(), handler2.getMockSettings());
    }

    // covers getInvocationContainer() returning the same reference on repeated calls
    @Test
    public void testGetInvocationContainer_returnsSameInstanceAcrossCalls() throws Throwable {
        MockHandler<Object> handler = new MockHandler<Object>(new MockSettingsImpl());
        assertSame(handler.getInvocationContainer(), handler.getInvocationContainer());
    }

    // covers invocationContainerImpl being initialized by the public constructor
    @Test
    public void testGetInvocationContainer_notNullAfterConstruction() throws Throwable {
        MockHandler<Object> handler = new MockHandler<Object>(new MockSettingsImpl());
        assertNotNull(handler.getInvocationContainer());
    }

    // covers getInvocationContainer() exposing exactly the internal invocationContainerImpl field
    @Test
    public void testGetInvocationContainer_matchesPackagePrivateField() throws Throwable {
        MockHandler<Object> handler = new MockHandler<Object>(new MockSettingsImpl());
        assertSame(handler.invocationContainerImpl, handler.getInvocationContainer());
    }

    // covers voidMethodStubbable(T) returning a non-null stubbable wrapper for a regular object
    @Test
    public void testVoidMethodStubbable_returnsNonNullForNonNullMock() throws Throwable {
        MockHandler<Object> handler = new MockHandler<Object>(new MockSettingsImpl());
        VoidMethodStubbable<Object> stubbable = handler.voidMethodStubbable(new Object());
        assertNotNull(stubbable);
    }

    // covers voidMethodStubbable(T) branch where the mock argument is null - no null-check present
    @Test
    public void testVoidMethodStubbable_returnsNonNullForNullMock() throws Throwable {
        MockHandler<Object> handler = new MockHandler<Object>(new MockSettingsImpl());
        VoidMethodStubbable<Object> stubbable = handler.voidMethodStubbable(null);
        assertNotNull(stubbable);
    }

    // covers each invocation of voidMethodStubbable constructing a fresh stubbable instance
    @Test
    public void testVoidMethodStubbable_returnsNewInstanceEachCall() throws Throwable {
        MockHandler<Object> handler = new MockHandler<Object>(new MockSettingsImpl());
        Object mock = new Object();
        VoidMethodStubbable<Object> first = handler.voidMethodStubbable(mock);
        VoidMethodStubbable<Object> second = handler.voidMethodStubbable(mock);
        assertNotSame(first, second);
    }

    // covers generic type parameter T bound to String for voidMethodStubbable
    @Test
    public void testVoidMethodStubbable_worksForStringGenericType() throws Throwable {
        MockHandler<String> handler = new MockHandler<String>(new MockSettingsImpl());
        VoidMethodStubbable<String> stubbable = handler.voidMethodStubbable("mockedString");
        assertNotNull(stubbable);
    }

    // covers InvocationContainerImpl default state before any stubbing has been registered
    @Test
    public void testInvocationContainer_initialState_hasAnswersForStubbingFalse() throws Throwable {
        MockHandler<Object> handler = new MockHandler<Object>(new MockSettingsImpl());
        assertFalse(handler.invocationContainerImpl.hasAnswersForStubbing());
    }

    // covers setAnswersForStubbing(List) marking the container as having pending answers
    @Test
    public void testSetAnswersForStubbing_withNonEmptyList_hasAnswersForStubbingTrue() throws Throwable {
        MockHandler<Object> handler = new MockHandler<Object>(new MockSettingsImpl());
        List<Answer> answers = new ArrayList<Answer>();
        answers.add(null);
        handler.setAnswersForStubbing(answers);
        assertTrue(handler.invocationContainerImpl.hasAnswersForStubbing());
    }

    // covers boundary where an empty list is supplied for stubbing
    @Test
    public void testSetAnswersForStubbing_withEmptyList_hasAnswersForStubbingFalse() throws Throwable {
        MockHandler<Object> handler = new MockHandler<Object>(new MockSettingsImpl());
        List<Answer> answers = new ArrayList<Answer>();
        handler.setAnswersForStubbing(answers);
        assertFalse(handler.invocationContainerImpl.hasAnswersForStubbing());
    }

    // covers InvocationContainerImpl starting with no recorded invocations
    @Test
    public void testInvocationContainer_initialState_getInvocationsIsEmpty() throws Throwable {
        MockHandler<Object> handler = new MockHandler<Object>(new MockSettingsImpl());
        assertTrue(handler.invocationContainerImpl.getInvocations().isEmpty());
    }

    // covers matchersBinder field being initialized by the public constructor
    @Test
    public void testConstructorWithMockSettings_matchersBinderNotNull() throws Throwable {
        MockHandler<Object> handler = new MockHandler<Object>(new MockSettingsImpl());
        assertNotNull(handler.matchersBinder);
    }

    // covers mockingProgress field being initialized by the public constructor
    @Test
    public void testConstructorWithMockSettings_mockingProgressNotNull() throws Throwable {
        MockHandler<Object> handler = new MockHandler<Object>(new MockSettingsImpl());
        assertNotNull(handler.mockingProgress);
    }

    // covers each constructor call creating its own MatchersBinder instance
    @Test
    public void testTwoHandlers_haveDistinctMatchersBinderInstances() throws Throwable {
        MockHandler<Object> handler1 = new MockHandler<Object>(new MockSettingsImpl());
        MockHandler<Object> handler2 = new MockHandler<Object>(new MockSettingsImpl());
        assertNotSame(handler1.matchersBinder, handler2.matchersBinder);
    }

    // covers each constructor call creating its own ThreadSafeMockingProgress instance
    @Test
    public void testTwoHandlers_haveDistinctMockingProgressInstances() throws Throwable {
        MockHandler<Object> handler1 = new MockHandler<Object>(new MockSettingsImpl());
        MockHandler<Object> handler2 = new MockHandler<Object>(new MockSettingsImpl());
        assertNotSame(handler1.mockingProgress, handler2.mockingProgress);
    }

    // covers generic type parameter T bound to Integer across constructor and accessor
    @Test
    public void testConstructor_withIntegerGenericType_getMockSettingsConsistent() throws Throwable {
        MockSettingsImpl settings = new MockSettingsImpl();
        MockHandler<Integer> handler = new MockHandler<Integer>(settings);
        assertSame(settings, handler.getMockSettings());
    }
}
