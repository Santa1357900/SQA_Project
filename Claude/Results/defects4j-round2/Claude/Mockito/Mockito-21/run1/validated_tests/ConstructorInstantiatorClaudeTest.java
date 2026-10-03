package org.mockito.internal.creation.instance;

import org.junit.Test;
import static org.junit.Assert.*;

public class ConstructorInstantiatorClaudeTest {

    static class NoArgClass {
        public NoArgClass() {
        }
    }

    static class PrivateConstructorClass {
        private PrivateConstructorClass() {
        }
    }

    static abstract class AbstractClass {
        public AbstractClass() {
        }
    }

    interface SampleInterface {
    }

    static class MyCheckedException extends Exception {
    }

    static class ThrowingConstructor {
        public ThrowingConstructor() throws MyCheckedException {
            throw new MyCheckedException();
        }
    }

    static class MultiConstructorClass {
        public MultiConstructorClass() {
        }
        public MultiConstructorClass(String s) {
        }
    }

    static class Outer {
        class Inner {
        }
    }

    static class OuterSub extends Outer {
    }

    static class OuterGrandchild extends OuterSub {
    }

    // outerClassInstance == null -> noArgConstructor path, public no-arg ctor succeeds
    @Test
    public void testNewInstance_outerNull_publicNoArgConstructor_returnsInstance() throws Throwable {
        ConstructorInstantiator instantiator = new ConstructorInstantiator(null);
        NoArgClass result = instantiator.newInstance(NoArgClass.class);
        assertNotNull(result);
    }

    // noArgConstructor path, private constructor -> IllegalAccessException wrapped
    @Test
    public void testNewInstance_outerNull_privateConstructor_throwsInstantationException() throws Throwable {
        ConstructorInstantiator instantiator = new ConstructorInstantiator(null);
        try {
            instantiator.newInstance(PrivateConstructorClass.class);
            fail("expected InstantationException");
        } catch (InstantationException expected) {
            assertTrue(expected.getCause() instanceof IllegalAccessException);
        }
    }

    // noArgConstructor path, abstract class -> InstantiationException wrapped
    @Test
    public void testNewInstance_outerNull_abstractClass_throwsInstantationException() throws Throwable {
        ConstructorInstantiator instantiator = new ConstructorInstantiator(null);
        try {
            instantiator.newInstance(AbstractClass.class);
            fail("expected InstantationException");
        } catch (InstantationException expected) {
            assertTrue(expected.getCause() instanceof InstantiationException);
        }
    }

    // noArgConstructor path, interface type -> InstantiationException wrapped
    @Test
    public void testNewInstance_outerNull_interfaceClass_throwsInstantationException() throws Throwable {
        ConstructorInstantiator instantiator = new ConstructorInstantiator(null);
        try {
            instantiator.newInstance(SampleInterface.class);
            fail("expected InstantationException");
        } catch (InstantationException expected) {
            assertTrue(expected.getCause() instanceof InstantiationException);
        }
    }

    // noArgConstructor path, constructor itself throws checked exception -> wrapped with original cause
    @Test
    public void testNewInstance_outerNull_constructorThrowsCheckedException_wrapsCause() throws Throwable {
        ConstructorInstantiator instantiator = new ConstructorInstantiator(null);
        try {
            instantiator.newInstance(ThrowingConstructor.class);
            fail("expected InstantationException");
        } catch (InstantationException expected) {
            assertTrue(expected.getCause() instanceof MyCheckedException);
        }
    }

    // noArgConstructor path, null cls dereferenced twice -> NullPointerException (not swallowed)
    @Test
    public void testNewInstance_outerNull_nullClass_throwsNullPointerException() throws Throwable {
        ConstructorInstantiator instantiator = new ConstructorInstantiator(null);
        try {
            instantiator.newInstance(null);
            fail("expected NullPointerException");
        } catch (NullPointerException expected) {
        }
    }

    // noArgConstructor failure message must mention class name and parameter-less hint
    @Test
    public void testNewInstance_outerNull_exceptionMessageContainsClassNameAndHint() throws Throwable {
        ConstructorInstantiator instantiator = new ConstructorInstantiator(null);
        try {
            instantiator.newInstance(PrivateConstructorClass.class);
            fail("expected InstantationException");
        } catch (InstantationException expected) {
            assertTrue(expected.getMessage().contains("PrivateConstructorClass"));
            assertTrue(expected.getMessage().contains("parameter-less constructor"));
        }
    }

    // noArgConstructor path, class with multiple constructors still uses the nullary one
    @Test
    public void testNewInstance_outerNull_multipleConstructors_usesNoArgOne() throws Throwable {
        ConstructorInstantiator instantiator = new ConstructorInstantiator(null);
        MultiConstructorClass result = instantiator.newInstance(MultiConstructorClass.class);
        assertNotNull(result);
    }

    // noArgConstructor path, each call returns a distinct new instance
    @Test
    public void testNewInstance_outerNull_multipleCalls_returnDistinctInstances() throws Throwable {
        ConstructorInstantiator instantiator = new ConstructorInstantiator(null);
        NoArgClass first = instantiator.newInstance(NoArgClass.class);
        NoArgClass second = instantiator.newInstance(NoArgClass.class);
        assertNotSame(first, second);
    }

    // outerClassInstance != null -> withOuterClass path, exact matching outer type succeeds
    @Test
    public void testNewInstance_withOuterInstance_exactType_returnsInnerInstance() throws Throwable {
        Outer outer = new Outer();
        ConstructorInstantiator instantiator = new ConstructorInstantiator(outer);
        Outer.Inner inner = instantiator.newInstance(Outer.Inner.class);
        assertNotNull(inner);
    }

    // BUG: outer instance is a subclass of the declared outer type; must still be accepted
    @Test
    public void testNewInstance_withOuterInstance_subclassOfOuter_returnsInnerInstance() throws Throwable {
        Outer outer = new OuterSub();
        ConstructorInstantiator instantiator = new ConstructorInstantiator(outer);
        Outer.Inner inner = instantiator.newInstance(Outer.Inner.class);
        assertNotNull(inner);
    }

    // BUG (deeper): a two-level subclass outer instance must also be accepted
    @Test
    public void testNewInstance_withOuterInstance_grandchildSubclassOfOuter_returnsInnerInstance() throws Throwable {
        Outer outer = new OuterGrandchild();
        ConstructorInstantiator instantiator = new ConstructorInstantiator(outer);
        Outer.Inner inner = instantiator.newInstance(Outer.Inner.class);
        assertNotNull(inner);
    }



    // withOuterClass failure message must mention class name and outer-type hint
    @Test
    public void testNewInstance_withOuterInstance_exceptionMessageContainsHint() throws Throwable {
        ConstructorInstantiator instantiator = new ConstructorInstantiator("not an outer instance");
        try {
            instantiator.newInstance(Outer.Inner.class);
            fail("expected InstantationException");
        } catch (InstantationException expected) {
            assertTrue(expected.getMessage().contains("Inner"));
            assertTrue(expected.getMessage().contains("outer instance has correct type"));
        }
    }

    // withOuterClass path, null cls dereferenced twice -> NullPointerException (not swallowed)
    @Test
    public void testNewInstance_withOuterInstance_nullClass_throwsNullPointerException() throws Throwable {
        ConstructorInstantiator instantiator = new ConstructorInstantiator(new Outer());
        try {
            instantiator.newInstance(null);
            fail("expected NullPointerException");
        } catch (NullPointerException expected) {
        }
    }

    // a single instantiator instance is reusable for successive different target classes
    @Test
    public void testNewInstance_sameInstantiator_reusedForDifferentClasses_bothSucceed() throws Throwable {
        ConstructorInstantiator instantiator = new ConstructorInstantiator(null);
        NoArgClass first = instantiator.newInstance(NoArgClass.class);
        MultiConstructorClass second = instantiator.newInstance(MultiConstructorClass.class);
        assertNotNull(first);
        assertNotNull(second);
    }

    // ConstructorInstantiator must implement the Instantiator contract
    @Test
    public void testConstructorInstantiator_implementsInstantiatorInterface() throws Throwable {
        ConstructorInstantiator instantiator = new ConstructorInstantiator(null);
        assertTrue(instantiator instanceof Instantiator);
    }

    // InstantationException must be unchecked (RuntimeException) so it needs no throws clause
    @Test
    public void testInstantationException_isUncheckedRuntimeException() throws Throwable {
        ConstructorInstantiator instantiator = new ConstructorInstantiator(null);
        try {
            instantiator.newInstance(AbstractClass.class);
            fail("expected InstantationException");
        } catch (RuntimeException expected) {
            assertTrue(expected instanceof InstantationException);
        }
    }
}
