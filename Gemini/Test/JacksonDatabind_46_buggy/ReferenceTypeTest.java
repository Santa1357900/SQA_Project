package com.fasterxml.jackson.databind.type;

import org.junit.Test;
import static org.junit.Assert.*;

import com.fasterxml.jackson.databind.JavaType;

public class ReferenceTypeTest {

    @Test
    public void testConstructAndGetters() throws Throwable {
        TypeFactory tf = TypeFactory.defaultInstance();
        JavaType refTarget = tf.constructType(String.class);
        ReferenceType refType = ReferenceType.construct(java.util.concurrent.atomic.AtomicReference.class, refTarget, null, null);

        assertNotNull(refType);
        assertEquals(refTarget, refType.getReferencedType());
        assertTrue(refType.isReferenceType());
        assertEquals(1, refType.containedTypeCount());
        assertEquals(refTarget, refType.containedType(0));
        assertNull(refType.containedType(1));
        assertNull(refType.containedType(-1));
        assertEquals("T", refType.containedTypeName(0));
        assertNull(refType.containedTypeName(1));
        assertNull(refType.containedTypeName(-1));
        assertEquals(java.util.concurrent.atomic.AtomicReference.class, refType.getParameterSource());
    }

    @Test
    public void testWithHandlers() throws Throwable {
        TypeFactory tf = TypeFactory.defaultInstance();
        JavaType refTarget = tf.constructType(String.class);
        ReferenceType refType = ReferenceType.construct(java.util.concurrent.atomic.AtomicReference.class, refTarget, null, null);

        Object valueHandler = new Object();
        Object typeHandler = new Object();
        Object contentTypeHandler = new Object();
        Object contentValueHandler = new Object();

        // withValueHandler
        ReferenceType t1 = refType.withValueHandler(null);
        assertSame(refType, t1);
        ReferenceType t2 = refType.withValueHandler(valueHandler);
        assertNotSame(refType, t2);
        assertSame(valueHandler, t2.getValueHandler());
        ReferenceType t3 = t2.withValueHandler(valueHandler);
        assertSame(t2, t3);

        // withTypeHandler
        ReferenceType t4 = refType.withTypeHandler(null);
        assertSame(refType, t4);
        ReferenceType t5 = refType.withTypeHandler(typeHandler);
        assertNotSame(refType, t5);
        assertSame(typeHandler, t5.getTypeHandler());
        ReferenceType t6 = t5.withTypeHandler(typeHandler);
        assertSame(t5, t6);

        // withContentTypeHandler
        ReferenceType t7 = refType.withContentTypeHandler(contentTypeHandler);
        assertNotNull(t7);
        ReferenceType t8 = t7.withContentTypeHandler(contentTypeHandler);
        assertSame(t7, t8);

        // withContentValueHandler
        ReferenceType t9 = refType.withContentValueHandler(contentValueHandler);
        assertNotNull(t9);
        ReferenceType t10 = t9.withContentValueHandler(contentValueHandler);
        assertSame(t9, t10);
    }

    @Test
    public void testWithStaticTyping() throws Throwable {
        TypeFactory tf = TypeFactory.defaultInstance();
        JavaType refTarget = tf.constructType(String.class);
        ReferenceType refType = ReferenceType.construct(java.util.concurrent.atomic.AtomicReference.class, refTarget, null, null);

        assertFalse(refType.useStaticType());
        ReferenceType staticRef = refType.withStaticTyping();
        assertTrue(staticRef.useStaticType());
        ReferenceType staticRefAgain = staticRef.withStaticTyping();
        assertSame(staticRef, staticRefAgain);
    }

    @Test
    public void testNarrow() throws Throwable {
        TypeFactory tf = TypeFactory.defaultInstance();
        JavaType refTarget = tf.constructType(String.class);
        ReferenceType refType = ReferenceType.construct(java.util.concurrent.atomic.AtomicReference.class, refTarget, null, null);

        JavaType narrowed = refType._narrow(java.util.concurrent.atomic.AtomicReference.class);
        assertNotNull(narrowed);
        assertTrue(narrowed instanceof ReferenceType);
        assertEquals(java.util.concurrent.atomic.AtomicReference.class, narrowed.getRawClass());
    }

    @Test
    public void testSignaturesAndCanonicalName() throws Throwable {
        TypeFactory tf = TypeFactory.defaultInstance();
        JavaType refTarget = tf.constructType(String.class);
        ReferenceType refType = ReferenceType.construct(java.util.concurrent.atomic.AtomicReference.class, refTarget, null, null);

        StringBuilder sb = new StringBuilder();
        StringBuilder erased = refType.getErasedSignature(sb);
        assertNotNull(erased);

        StringBuilder sb2 = new StringBuilder();
        StringBuilder generic = refType.getGenericSignature(sb2);
        assertNotNull(generic);

        String toStringVal = refType.toString();
        assertNotNull(toStringVal);
        assertTrue(toStringVal.contains("reference type"));
    }

    @Test
    public void testEquals() throws Throwable {
        TypeFactory tf = TypeFactory.defaultInstance();
        JavaType refTarget1 = tf.constructType(String.class);
        JavaType refTarget2 = tf.constructType(Integer.class);

        ReferenceType ref1 = ReferenceType.construct(java.util.concurrent.atomic.AtomicReference.class, refTarget1, null, null);
        ReferenceType ref2 = ReferenceType.construct(java.util.concurrent.atomic.AtomicReference.class, refTarget1, null, null);
        ReferenceType ref3 = ReferenceType.construct(java.util.concurrent.atomic.AtomicReference.class, refTarget2, null, null);
        ReferenceType ref4 = ReferenceType.construct(java.util.List.class, refTarget1, null, null);

        assertEquals(ref1, ref1);
        assertEquals(ref1, ref2);
        assertFalse(ref1.equals(null));
        assertFalse(ref1.equals("some string"));
        assertFalse(ref1.equals(ref3));
        assertFalse(ref1.equals(ref4));
    }
}