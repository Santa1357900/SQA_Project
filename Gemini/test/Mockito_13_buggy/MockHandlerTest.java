package org.mockito.internal;

import org.junit.Test;
import static org.junit.Assert.*;

import org.mockito.internal.creation.MockSettingsImpl;
import org.mockito.internal.stubbing.InvocationContainer;
import org.mockito.stubbing.Answer;
import org.mockito.stubbing.VoidMethodStubbable;

import java.util.ArrayList;
import java.util.List;

public class MockHandlerTest {

    @Test
    public void testDefaultConstructor() throws Throwable {
        MockHandler<Object> handler = new MockHandler<Object>();
        assertNotNull(handler.getMockSettings());
        assertNotNull(handler.getInvocationContainer());
    }

    @Test
    public void testMockSettingsConstructor() throws Throwable {
        MockSettingsImpl settings = new MockSettingsImpl();
        MockHandler<Object> handler = new MockHandler<Object>(settings);
        assertEquals(settings, handler.getMockSettings());
        assertNotNull(handler.getInvocationContainer());
    }

    @Test
    public void testCopyConstructor() throws Throwable {
        MockSettingsImpl settings = new MockSettingsImpl();
        MockHandler<Object> oldHandler = new MockHandler<Object>(settings);
        MockHandler<Object> newHandler = new MockHandler<Object>(oldHandler);
        
        assertNotNull(newHandler.getMockSettings());
        assertNotNull(newHandler.getInvocationContainer());
    }

    @Test
    public void testSetAnswersForStubbing() throws Throwable {
        MockHandler<Object> handler = new MockHandler<Object>();
        List<Answer> answers = new ArrayList<Answer>();
        
        handler.setAnswersForStubbing(answers);
        InvocationContainer container = handler.getInvocationContainer();
        assertNotNull(container);
    }

    @Test
    public void testVoidMethodStubbable() throws Throwable {
        MockHandler<Object> handler = new MockHandler<Object>();
        Object mock = new Object();
        
        VoidMethodStubbable<Object> stubbable = handler.voidMethodStubbable(mock);
        assertNotNull(stubbable);
    }
}