package org.mockito.internal.stubbing.answers;

import org.junit.Before;
import org.junit.Test;
import static org.junit.Assert.*;
import static org.mockito.Mockito.*;

import org.mockito.invocation.InvocationOnMock;
import org.mockito.stubbing.Answer;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.io.Serializable;

public class CallsRealMethodsClaudeTest {

    private CallsRealMethods callsRealMethods;
    private InvocationOnMock invocation;

    @Before
    public void setUp() throws Throwable {
        callsRealMethods = new CallsRealMethods();
        invocation = mock(InvocationOnMock.class);
    }





































    // CallsRealMethods must implement the Answer<Object> contract type
    @Test
    public void testCallsRealMethods_isInstanceOfAnswer() throws Throwable {
        assertTrue(callsRealMethods instanceof Answer);
    }

    // CallsRealMethods must implement Serializable as declared
    @Test
    public void testCallsRealMethods_isInstanceOfSerializable() throws Throwable {
        assertTrue(callsRealMethods instanceof Serializable);
    }

    // instance must be serializable/deserializable without error (supports its Serializable contract)
    @Test
    public void testCallsRealMethods_serializationRoundTrip_succeeds() throws Throwable {
        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        ObjectOutputStream oos = new ObjectOutputStream(baos);
        oos.writeObject(callsRealMethods);
        oos.close();
        ByteArrayInputStream bais = new ByteArrayInputStream(baos.toByteArray());
        ObjectInputStream ois = new ObjectInputStream(bais);
        Object deserialized = ois.readObject();
        ois.close();
        assertTrue(deserialized instanceof CallsRealMethods);
    }




}
