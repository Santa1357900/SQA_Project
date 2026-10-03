package com.fasterxml.jackson.databind.deser.impl;

import java.io.IOException;
import java.lang.reflect.Constructor;

import org.junit.Test;
import junit.framework.TestCase;

import com.fasterxml.jackson.databind.JsonDeserializer;
import com.fasterxml.jackson.databind.PropertyName;
import com.fasterxml.jackson.databind.deser.SettableBeanProperty;
import com.fasterxml.jackson.databind.introspect.AnnotatedConstructor;

public class InnerClassPropertyTest extends TestCase {

    static class DummyOuter {
        class DummyInner {
            public DummyInner() { }
        }
    }

    @Test
    public void testConstructorsAndDelegation() throws Throwable {
        Constructor<?> ctor = DummyOuter.DummyInner.class.getConstructor(new Class[] { DummyOuter.class });
        
        SettableBeanProperty delegate = new SettableBeanProperty.Bogus();
        InnerClassProperty prop = new InnerClassProperty(delegate, ctor);
        
        assertNotNull(prop.withName(new PropertyName("newName")));
        assertNotNull(prop.withValueDeserializer(null));
        
        prop.assignIndex(5);
        assertEquals(delegate.getPropertyIndex(), prop.getPropertyIndex());
        
        assertNull(prop.getAnnotation(null));
        assertNull(prop.getMember());
    }

    @Test
    public void testSerializationConstructors() throws Throwable {
        Constructor<?> ctor = DummyOuter.DummyInner.class.getConstructor(new Class[] { DummyOuter.class });
        SettableBeanProperty delegate = new SettableBeanProperty.Bogus();
        InnerClassProperty prop = new InnerClassProperty(delegate, ctor);

        AnnotatedConstructor ann = new AnnotatedConstructor(null, ctor, null, null);
        
        InnerClassProperty prop2 = new InnerClassProperty(prop, ann);
        assertNotNull(prop2);

        Object replaced = prop.writeReplace();
        assertNotNull(replaced);

        Object resolved = prop2.readResolve();
        assertNotNull(resolved);
    }

    @Test
    public void testSerializationConstructorException() throws Throwable {
        SettableBeanProperty delegate = new SettableBeanProperty.Bogus();
        InnerClassProperty prop = new InnerClassProperty(delegate, (Constructor<?>) null);

        try {
            new InnerClassProperty(prop, (AnnotatedConstructor) null);
            fail("Should have thrown IllegalArgumentException");
        } catch (IllegalArgumentException e) {
            assertTrue(e.getMessage().contains("Missing constructor"));
        }
    }
}