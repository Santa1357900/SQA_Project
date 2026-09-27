package org.mockito.internal.stubbing.answers;

import org.junit.Test;
import org.mockito.invocation.InvocationOnMock;
import static org.junit.Assert.*;

public class CallsRealMethodsTest {

    @Test
    public void testAnswerCallsRealMethod() throws Throwable {
        CallsRealMethods callsRealMethods = new CallsRealMethods();
        
        DummyInvocation mockInvocation = new DummyInvocation("expectedResult");
        
        Object result = callsRealMethods.answer(mockInvocation);
        
        assertEquals("expectedResult", result);
        assertTrue(mockInvocation.isCallRealMethodInvoked());
    }

    @Test(expected = RuntimeException.class)
    public void testAnswerPropagatesException() throws Throwable {
        CallsRealMethods callsRealMethods = new CallsRealMethods();
        
        DummyInvocation mockInvocation = new DummyInvocation(new RuntimeException("Real method exception"));
        
        callsRealMethods.answer(mockInvocation);
    }

    @Test
    public void testSerializable() throws Throwable {
        CallsRealMethods callsRealMethods = new CallsRealMethods();
        assertNotNull(callsRealMethods);
    }

    private static class DummyInvocation implements InvocationOnMock {
        private static final long serialVersionUID = 1L;
        
        private final Object returnValue;
        private final RuntimeException exceptionToThrow;
        private boolean callRealMethodInvoked = false;

        public DummyInvocation(Object returnValue) {
            this.returnValue = returnValue;
            this.exceptionToThrow = null;
        }

        public DummyInvocation(RuntimeException exceptionToThrow) {
            this.returnValue = null;
            this.exceptionToThrow = exceptionToThrow;
        }

        public Object callRealMethod() throws Throwable {
            this.callRealMethodInvoked = true;
            if (exceptionToThrow != null) {
                throw exceptionToThrow;
            }
            return returnValue;
        }

        public boolean isCallRealMethodInvoked() {
            return callRealMethodInvoked;
        }

        public Object getMock() {
            return null;
        }

        public java.lang.reflect.Method getMethod() {
            return null;
        }

        public Object[] getArguments() {
            return new Object[0];
        }

        public <T> T getArgumentAt(int index, Class<T> clazz) {
            return null;
        }
    }
}