package org.mockito.internal;

import java.util.ArrayList;
import java.util.List;

import org.junit.Test;
import static org.junit.Assert.*;

import org.mockito.internal.creation.MockSettingsImpl;
import org.mockito.internal.stubbing.InvocationContainerImpl;
import org.mockito.internal.stubbing.VoidMethodStubbableImpl;
import org.mockito.internal.progress.ThreadSafeMockingProgress;
import org.mockito.stubbing.Answer;

public class MockHandlerClaudeTest {

    // default ctor: getMockSettings() must not be null (delegates to new MockSettingsImpl())
    @Test
    public void testDefaultConstructor_mockSettingsNotNull() throws Throwable {
        MockHandler<Object> handler = new MockHandler<Object>();
        assertNotNull(handler.getMockSettings());
    }

    // default ctor: each instance creates its own fresh MockSettingsImpl (new MockSettingsImpl() each time)
    @Test
    public void testDefaultConstructor_eachInstanceGetsDistinctMockSettings() throws Throwable {
        MockHandler<Object> handlerA = new MockHandler<Object>();
        MockHandler<Object> handlerB = new MockHandler<Object>();
        assertNotSame(handlerA.getMockSettings(), handlerB.getMockSettings());
    }

    // default ctor: invocationContainerImpl field must be initialized (not null)
    @Test
    public void testDefaultConstructor_invocationContainerNotNull() throws Throwable {
        MockHandler<Object> handler = new MockHandler<Object>();
        assertNotNull(handler.invocationContainerImpl);
    }

    // default ctor: matchersBinder field must be initialized (not null)
    @Test
    public void testDefaultConstructor_matchersBinderNotNull() throws Throwable {
        MockHandler<Object> handler = new MockHandler<Object>();
        assertNotNull(handler.matchersBinder);
    }

    // default ctor: mockingProgress must be a ThreadSafeMockingProgress as assigned in ctor body
    @Test
    public void testDefaultConstructor_mockingProgressIsThreadSafeMockingProgress() throws Throwable {
        MockHandler<Object> handler = new MockHandler<Object>();
        assertTrue(handler.mockingProgress instanceof ThreadSafeMockingProgress);
    }

    // ctor(MockSettingsImpl): stores the exact given instance (this.mockSettings = mockSettings)
    @Test
    public void testConstructorWithSettings_storesExactInstance() throws Throwable {
        MockSettingsImpl settings = new MockSettingsImpl();
        MockHandler<Object> handler = new MockHandler<Object>(settings);
        assertSame(settings, handler.getMockSettings());
    }

    // ctor(MockSettingsImpl): invocationContainerImpl is created (not null)
    @Test
    public void testConstructorWithSettings_createsNonNullInvocationContainer() throws Throwable {
        MockSettingsImpl settings = new MockSettingsImpl();
        MockHandler<Object> handler = new MockHandler<Object>(settings);
        assertNotNull(handler.invocationContainerImpl);
    }

    // ctor(MockSettingsImpl): matchersBinder is created (not null)
    @Test
    public void testConstructorWithSettings_createsNonNullMatchersBinder() throws Throwable {
        MockSettingsImpl settings = new MockSettingsImpl();
        MockHandler<Object> handler = new MockHandler<Object>(settings);
        assertNotNull(handler.matchersBinder);
    }

    // ctor(MockSettingsImpl): passing null is allowed (reference type field, no validation in source)
    @Test
    public void testConstructorWithSettings_nullSettingsAllowed_getMockSettingsReturnsNull() throws Throwable {
        MockHandler<Object> handler = new MockHandler<Object>((MockSettingsImpl) null);
        assertNull(handler.getMockSettings());
    }

    // ctor(MockSettingsImpl): two instances constructed independently have distinct invocation containers
    @Test
    public void testConstructorWithSettings_distinctInstancesHaveDistinctContainers() throws Throwable {
        MockHandler<Object> handlerA = new MockHandler<Object>(new MockSettingsImpl());
        MockHandler<Object> handlerB = new MockHandler<Object>(new MockSettingsImpl());
        assertNotSame(handlerA.getInvocationContainer(), handlerB.getInvocationContainer());
    }

    // ctor(MockHandlerInterface): new handler shares exact same mockSettings instance as the old one
    @Test
    public void testCopyConstructor_sharesMockSettingsInstance() throws Throwable {
        MockSettingsImpl settings = new MockSettingsImpl();
        MockHandler<Object> oldHandler = new MockHandler<Object>(settings);
        MockHandler<Object> newHandler = new MockHandler<Object>(oldHandler);
        assertSame(oldHandler.getMockSettings(), newHandler.getMockSettings());
    }

    // ctor(MockHandlerInterface): a fresh invocationContainerImpl is created, distinct from the old one
    @Test
    public void testCopyConstructor_createsDistinctInvocationContainer() throws Throwable {
        MockHandler<Object> oldHandler = new MockHandler<Object>(new MockSettingsImpl());
        MockHandler<Object> newHandler = new MockHandler<Object>(oldHandler);
        assertNotSame(oldHandler.getInvocationContainer(), newHandler.getInvocationContainer());
    }

    // ctor(MockHandlerInterface): a fresh matchersBinder is created, distinct from the old one
    @Test
    public void testCopyConstructor_createsDistinctMatchersBinder() throws Throwable {
        MockHandler<Object> oldHandler = new MockHandler<Object>(new MockSettingsImpl());
        MockHandler<Object> newHandler = new MockHandler<Object>(oldHandler);
        assertNotSame(oldHandler.matchersBinder, newHandler.matchersBinder);
    }

    // ctor(MockHandlerInterface): a fresh mockingProgress is created, distinct from the old one
    @Test
    public void testCopyConstructor_createsDistinctMockingProgress() throws Throwable {
        MockHandler<Object> oldHandler = new MockHandler<Object>(new MockSettingsImpl());
        MockHandler<Object> newHandler = new MockHandler<Object>(oldHandler);
        assertNotSame(oldHandler.mockingProgress, newHandler.mockingProgress);
    }

    // getMockSettings(): returns a stable reference across repeated calls (simple field getter)
    @Test
    public void testGetMockSettings_stableAcrossMultipleCalls() throws Throwable {
        MockHandler<Object> handler = new MockHandler<Object>();
        assertSame(handler.getMockSettings(), handler.getMockSettings());
    }

    // getInvocationContainer(): returned object is an InvocationContainerImpl instance
    @Test
    public void testGetInvocationContainer_isInvocationContainerImplInstance() throws Throwable {
        MockHandler<Object> handler = new MockHandler<Object>();
        assertTrue(handler.getInvocationContainer() instanceof InvocationContainerImpl);
    }

    // getInvocationContainer(): returns a stable reference across repeated calls
    @Test
    public void testGetInvocationContainer_stableAcrossMultipleCalls() throws Throwable {
        MockHandler<Object> handler = new MockHandler<Object>();
        assertSame(handler.getInvocationContainer(), handler.getInvocationContainer());
    }

    // getInvocationContainer(): returns exactly the invocationContainerImpl field value
    @Test
    public void testGetInvocationContainer_matchesInternalField() throws Throwable {
        MockHandler<Object> handler = new MockHandler<Object>();
        assertSame(handler.invocationContainerImpl, handler.getInvocationContainer());
    }

    // voidMethodStubbable(mock): returns a non-null stubbable object
    @Test
    public void testVoidMethodStubbable_returnsNonNullInstance() throws Throwable {
        MockHandler<Object> handler = new MockHandler<Object>();
        Object stub = handler.voidMethodStubbable(new Object());
        assertNotNull(stub);
    }

    // voidMethodStubbable(mock): returned object is a VoidMethodStubbableImpl instance
    @Test
    public void testVoidMethodStubbable_returnsVoidMethodStubbableImplInstance() throws Throwable {
        MockHandler<Object> handler = new MockHandler<Object>();
        Object stub = handler.voidMethodStubbable(new Object());
        assertTrue(stub instanceof VoidMethodStubbableImpl);
    }

    // voidMethodStubbable(mock): two calls produce two distinct stubbable instances (not cached)
    @Test
    public void testVoidMethodStubbable_distinctCallsProduceDistinctInstances() throws Throwable {
        MockHandler<Object> handler = new MockHandler<Object>();
        Object stub1 = handler.voidMethodStubbable(new Object());
        Object stub2 = handler.voidMethodStubbable(new Object());
        assertNotSame(stub1, stub2);
    }

    // voidMethodStubbable(mock): edge case null mock still yields a usable non-null stubbable instance
    @Test
    public void testVoidMethodStubbable_nullMockReturnsNonNullInstance() throws Throwable {
        MockHandler<Object> handler = new MockHandler<Object>();
        Object stub = handler.voidMethodStubbable(null);
        assertNotNull(stub);
        assertTrue(stub instanceof VoidMethodStubbableImpl);
    }

    // setAnswersForStubbing(empty list): delegates without replacing the invocationContainerImpl field
    @Test
    public void testSetAnswersForStubbing_emptyList_doesNotReplaceContainerReference() throws Throwable {
        MockHandler<Object> handler = new MockHandler<Object>();
        Object before = handler.getInvocationContainer();
        List<Answer> answers = new ArrayList<Answer>();
        handler.setAnswersForStubbing(answers);
        assertSame(before, handler.getInvocationContainer());
    }

    // setAnswersForStubbing(empty list): calling it repeatedly keeps the same container reference stable
    @Test
    public void testSetAnswersForStubbing_calledTwice_containerReferenceRemainsStable() throws Throwable {
        MockHandler<Object> handler = new MockHandler<Object>();
        List<Answer> answers = new ArrayList<Answer>();
        handler.setAnswersForStubbing(answers);
        Object afterFirst = handler.getInvocationContainer();
        handler.setAnswersForStubbing(answers);
        assertSame(afterFirst, handler.getInvocationContainer());
    }

    // class contract: MockHandler must implement MockHandlerInterface as declared
    @Test
    public void testMockHandler_implementsMockHandlerInterface() throws Throwable {
        MockHandler<Object> handler = new MockHandler<Object>();
        assertTrue(handler instanceof MockHandlerInterface);
    }

    // class contract: MockHandler must implement MockitoInvocationHandler as declared
    @Test
    public void testMockHandler_implementsMockitoInvocationHandler() throws Throwable {
        MockHandler<Object> handler = new MockHandler<Object>();
        assertTrue(handler instanceof MockitoInvocationHandler);
    }

    // getMockSettings(): different handler instances (built with distinct settings) must not share settings
    @Test
    public void testGetMockSettings_distinctInstancesHaveDistinctSettingsByDefault() throws Throwable {
        MockHandler<Object> handlerA = new MockHandler<Object>();
        MockHandler<Object> handlerB = new MockHandler<Object>();
        assertFalse(handlerA.getMockSettings() == handlerB.getMockSettings()
                        && handlerA.getMockSettings() == null);
        assertNotSame(handlerA.getMockSettings(), handlerB.getMockSettings());
    }
}
