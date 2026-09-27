package org.mockito.internal.creation;

import org.junit.Test;
import static org.junit.Assert.*;

import java.lang.reflect.Method;

public class DelegatingMethodTest {

    public void sampleMethod() throws Exception {
    }

    @Test
    public void testDelegatingMethodCreationAndGetters() throws Throwable {
        Method javaMethod = DelegatingMethodTest.class.getMethod("sampleMethod", new Class[0]);
        DelegatingMethod delegatingMethod = new DelegatingMethod(javaMethod);

        assertNotNull(delegatingMethod);
        assertEquals(javaMethod, delegatingMethod.getJavaMethod());
        assertEquals("sampleMethod", delegatingMethod.getName());
        assertEquals(javaMethod.getReturnType(), delegatingMethod.getReturnType());
        assertArrayEquals(javaMethod.getParameterTypes(), delegatingMethod.getParameterTypes());
        assertArrayEquals(javaMethod.getExceptionTypes(), delegatingMethod.getExceptionTypes());
        assertEquals(javaMethod.isVarArgs(), delegatingMethod.isVarArgs());
        assertEquals(false, delegatingMethod.isAbstract());
    }

    @Test
    public void testEqualsAndHashCode() throws Throwable {
        Method javaMethod1 = DelegatingMethodTest.class.getMethod("sampleMethod", new Class[0]);
        Method javaMethod2 = String.class.getMethod("toString", new Class[0]);

        DelegatingMethod delegatingMethod1 = new DelegatingMethod(javaMethod1);
        DelegatingMethod delegatingMethod2 = new DelegatingMethod(javaMethod1);
        DelegatingMethod delegatingMethod3 = new DelegatingMethod(javaMethod2);

        assertTrue(delegatingMethod1.equals(delegatingMethod1));
        assertTrue(delegatingMethod1.equals(delegatingMethod2));
        assertTrue(delegatingMethod1.equals(javaMethod1));
        
        assertFalse(delegatingMethod1.equals(delegatingMethod3));
        assertFalse(delegatingMethod1.equals(javaMethod2));
        assertFalse(delegatingMethod1.equals(null));
        assertFalse(delegatingMethod1.equals("some string"));

        assertEquals(1, delegatingMethod1.hashCode());
        assertEquals(1, delegatingMethod3.hashCode());
    }

    @Test(expected = AssertionError.class)
    public void testNullMethodConstructor() throws Throwable {
        new DelegatingMethod(null);
    }
}