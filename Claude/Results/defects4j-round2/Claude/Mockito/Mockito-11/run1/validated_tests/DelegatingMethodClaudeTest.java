package org.mockito.internal.creation;

import org.junit.Before;
import org.junit.Test;
import static org.junit.Assert.*;

import java.io.IOException;
import java.lang.reflect.Method;

public class DelegatingMethodClaudeTest {

    static class SampleClass {
        public void simpleMethod() {
        }

        public int returnInt(String s) {
            return 0;
        }

        public void throwsMethod() throws IOException {
        }

        public void varargsMethod(String... args) {
        }

        public void anotherMethod() {
        }
    }

    interface SampleInterface {
        void abstractMethod();
    }

    private Method simpleMethod;
    private Method returnIntMethod;
    private Method throwsMethod;
    private Method varargsMethod;
    private Method abstractMethod;
    private Method anotherMethod;

    @Before
    public void setUp() throws Throwable {
        simpleMethod = SampleClass.class.getMethod("simpleMethod");
        returnIntMethod = SampleClass.class.getMethod("returnInt", String.class);
        throwsMethod = SampleClass.class.getMethod("throwsMethod");
        varargsMethod = SampleClass.class.getMethod("varargsMethod", String[].class);
        abstractMethod = SampleInterface.class.getMethod("abstractMethod");
        anotherMethod = SampleClass.class.getMethod("anotherMethod");
    }

    // constructor: valid method is stored and retrievable via getJavaMethod
    @Test
    public void testConstructor_validMethod_createsInstanceHoldingMethod() throws Throwable {
        DelegatingMethod dm = new DelegatingMethod(simpleMethod);
        assertNotNull(dm);
        assertSame(simpleMethod, dm.getJavaMethod());
    }

    // getExceptionTypes: method declares no checked exceptions -> empty array
    @Test
    public void testGetExceptionTypes_noDeclaredExceptions_returnsEmptyArray() throws Throwable {
        DelegatingMethod dm = new DelegatingMethod(simpleMethod);
        assertEquals(0, dm.getExceptionTypes().length);
    }

    // getExceptionTypes: method declares one checked exception -> array contains it
    @Test
    public void testGetExceptionTypes_withDeclaredException_returnsCorrectType() throws Throwable {
        DelegatingMethod dm = new DelegatingMethod(throwsMethod);
        Class<?>[] exceptions = dm.getExceptionTypes();
        assertEquals(1, exceptions.length);
        assertEquals(IOException.class, exceptions[0]);
    }

    // getJavaMethod: returns exact same Method reference passed to constructor
    @Test
    public void testGetJavaMethod_returnsSameMethodReference() throws Throwable {
        DelegatingMethod dm = new DelegatingMethod(returnIntMethod);
        assertSame(returnIntMethod, dm.getJavaMethod());
    }

    // getName: delegates to underlying method's name
    @Test
    public void testGetName_returnsUnderlyingMethodName() throws Throwable {
        DelegatingMethod dm = new DelegatingMethod(simpleMethod);
        assertEquals("simpleMethod", dm.getName());
    }

    // getName: works correctly for a different method as well
    @Test
    public void testGetName_forDifferentMethod_returnsThatMethodName() throws Throwable {
        DelegatingMethod dm = new DelegatingMethod(returnIntMethod);
        assertEquals("returnInt", dm.getName());
    }

    // getParameterTypes: no-arg method returns empty array
    @Test
    public void testGetParameterTypes_noParameters_returnsEmptyArray() throws Throwable {
        DelegatingMethod dm = new DelegatingMethod(simpleMethod);
        assertEquals(0, dm.getParameterTypes().length);
    }

    // getParameterTypes: single-arg method returns array with that type
    @Test
    public void testGetParameterTypes_oneParameter_returnsCorrectType() throws Throwable {
        DelegatingMethod dm = new DelegatingMethod(returnIntMethod);
        Class<?>[] params = dm.getParameterTypes();
        assertEquals(1, params.length);
        assertEquals(String.class, params[0]);
    }

    // getReturnType: void method returns void.class
    @Test
    public void testGetReturnType_voidMethod_returnsVoidType() throws Throwable {
        DelegatingMethod dm = new DelegatingMethod(simpleMethod);
        assertEquals(Void.TYPE, dm.getReturnType());
    }

    // getReturnType: non-void method returns correct primitive return type
    @Test
    public void testGetReturnType_intMethod_returnsIntType() throws Throwable {
        DelegatingMethod dm = new DelegatingMethod(returnIntMethod);
        assertEquals(Integer.TYPE, dm.getReturnType());
    }

    // isVarArgs: variadic method returns true
    @Test
    public void testIsVarArgs_variadicMethod_returnsTrue() throws Throwable {
        DelegatingMethod dm = new DelegatingMethod(varargsMethod);
        assertTrue(dm.isVarArgs());
    }

    // isVarArgs: non-variadic method returns false
    @Test
    public void testIsVarArgs_nonVariadicMethod_returnsFalse() throws Throwable {
        DelegatingMethod dm = new DelegatingMethod(simpleMethod);
        assertFalse(dm.isVarArgs());
    }

    // isAbstract: interface method (implicitly abstract) returns true
    @Test
    public void testIsAbstract_interfaceMethod_returnsTrue() throws Throwable {
        DelegatingMethod dm = new DelegatingMethod(abstractMethod);
        assertTrue(dm.isAbstract());
    }

    // isAbstract: concrete method returns false
    @Test
    public void testIsAbstract_concreteMethod_returnsFalse() throws Throwable {
        DelegatingMethod dm = new DelegatingMethod(simpleMethod);
        assertFalse(dm.isAbstract());
    }

    // equals: comparing against the exact same Method instance delegates to Method.equals -> true
    @Test
    public void testEquals_sameMethodInstanceAsArgument_returnsTrue() throws Throwable {
        DelegatingMethod dm = new DelegatingMethod(simpleMethod);
        assertTrue(dm.equals(simpleMethod));
    }

    // equals: comparing against a different Method instance -> false
    @Test
    public void testEquals_differentMethodInstanceAsArgument_returnsFalse() throws Throwable {
        DelegatingMethod dm = new DelegatingMethod(simpleMethod);
        assertFalse(dm.equals(anotherMethod));
    }

    // equals: comparing against null -> false (Method.equals handles null safely)
    @Test
    public void testEquals_null_returnsFalse() throws Throwable {
        DelegatingMethod dm = new DelegatingMethod(simpleMethod);
        assertFalse(dm.equals(null));
    }

    // equals: comparing against an unrelated object type -> false
    @Test
    public void testEquals_unrelatedObjectType_returnsFalse() throws Throwable {
        DelegatingMethod dm = new DelegatingMethod(simpleMethod);
        assertFalse(dm.equals("not a method"));
    }

    // equals contract (per Javadoc): two DelegatingMethod wrapping the SAME internal Method must be equal
    @Test
    public void testEquals_twoDelegatingMethodsWithSameUnderlyingMethod_returnsTrue() throws Throwable {
        DelegatingMethod dm1 = new DelegatingMethod(simpleMethod);
        DelegatingMethod dm2 = new DelegatingMethod(simpleMethod);
        assertTrue(dm1.equals(dm2));
    }

    // equals contract: two DelegatingMethod wrapping DIFFERENT underlying methods must not be equal
    @Test
    public void testEquals_twoDelegatingMethodsWithDifferentUnderlyingMethod_returnsFalse() throws Throwable {
        DelegatingMethod dm1 = new DelegatingMethod(simpleMethod);
        DelegatingMethod dm2 = new DelegatingMethod(anotherMethod);
        assertFalse(dm1.equals(dm2));
    }




}
