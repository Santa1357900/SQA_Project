package org.mockito.internal.creation.instance;

import org.junit.Test;
import static org.junit.Assert.*;

public class ConstructorInstantiatorTest {

    public static class InnerClass {
        public InnerClass() {
        }
    }

    public static class InnerClassWithOuter {
        public InnerClassWithOuter() {
        }
    }

    public static class ClassWithoutNoArg {
        public ClassWithoutNoArg(String s) {
        }
    }

    public static class InnerWithParams {
        public InnerWithParams(ConstructorInstantiatorTest outer) {
        }
    }

    @Test
    public void testNoArgConstructorSuccess() throws Throwable {
        ConstructorInstantiator instantiator = new ConstructorInstantiator(null);
        InnerClass instance = instantiator.newInstance(InnerClass.class);
        assertNotNull(instance);
    }

    @Test
    public void testNoArgConstructorFailure() throws Throwable {
        ConstructorInstantiator instantiator = new ConstructorInstantiator(null);
        try {
            instantiator.newInstance(ClassWithoutNoArg.class);
            fail("Should have thrown InstantationException");
        } catch (InstantationException e) {
            assertTrue(e.getMessage().contains("Unable to create mock instance of 'ClassWithoutNoArg'"));
            assertNotNull(e.getCause());
        }
    }

    @Test
    public void testWithOuterClassSuccess() throws Throwable {
        ConstructorInstantiatorTest outer = new ConstructorInstantiatorTest();
        ConstructorInstantiator instantiator = new ConstructorInstantiator(outer);
        InnerWithParams instance = instantiator.newInstance(InnerWithParams.class);
        assertNotNull(instance);
    }

    @Test
    public void testWithOuterClassFailure() throws Throwable {
        ConstructorInstantiatorTest outer = new ConstructorInstantiatorTest();
        ConstructorInstantiator instantiator = new ConstructorInstantiator(outer);
        try {
            instantiator.newInstance(InnerClass.class);
            fail("Should have thrown InstantationException");
        } catch (InstantationException e) {
            assertTrue(e.getMessage().contains("Unable to create mock instance of 'InnerClass'"));
            assertNotNull(e.getCause());
        }
    }
}