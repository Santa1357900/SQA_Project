package com.fasterxml.jackson.databind.type;

import junit.framework.TestCase;
import com.fasterxml.jackson.databind.JavaType;

public class ReferenceTypeTest extends TestCase {

    private TypeFactory typeFactory;

    @Override
    protected void setUp() throws Exception {
        super.setUp();
        typeFactory = TypeFactory.defaultInstance();
    }

    public void testUpgradeFromValid() throws Throwable {
        JavaType base = typeFactory.constructType(String.class);
        JavaType ref = typeFactory.constructType(Integer.class);
        
        ReferenceType refType = ReferenceType.upgradeFrom(base, ref);
        assertNotNull(refType);
        assertTrue(refType.isReferenceType());
        assertTrue(refType.hasContentType());
        assertEquals(ref, refType.getContentType());
        assertEquals(ref, refType.getReferencedType());
        assertEquals(refType, refType.getAnchorType());
        assertTrue(refType.isAnchorType());
    }

    public void testUpgradeFromNullRef() throws Throwable {
        JavaType base = typeFactory.constructType(String.class);
        try {
            ReferenceType.upgradeFrom(base, null);
            fail("Should have thrown IllegalArgumentException");
        } catch (IllegalArgumentException e) {
            assertTrue(e.getMessage().contains("Missing referencedType"));
        }
    }

    public void testUpgradeFromInvalidBase() throws Throwable {
        // Construct a non-TypeBase JavaType or use a mock/dummy if possible, 
        // but since JavaType is abstract, we can create a custom trivial subclass of JavaType that is NOT a TypeBase.
        JavaType nonTypeBase = new JavaType(String.class, 1, null, null, false) {
            private static final long serialVersionUID = 1L;
            @Override
            protected JavaType _narrow(Class<?> subclass) { return this; }
            @Override
            public JavaType narrowContentsBy(Class<?> contentClass) { return this; }
            @Override
            public JavaType widenContentsBy(Class<?> contentClass) { return this; }
            @Override
            public JavaType withContentType(JavaType contentType) { return this; }
            @Override
            public JavaType withTypeHandler(Object h) { return this; }
            @Override
            public JavaType withContentTypeHandler(Object h) { return this; }
            @Override
            public JavaType withValueHandler(Object h) { return this; }
            @Override
            public JavaType withContentValueHandler(Object h) { return this; }
            @Override
            public JavaType withStaticTyping() { return this; }
            @Override
            public boolean isContainerType() { return false; }
            @Override
            public String toString() { return "Dummy"; }
            @Override
            public boolean equals(Object o) { return o == this; }
            @Override
            public int hashCode() { return 1; }
            @Override
            public StringBuilder getErasedSignature(StringBuilder sb) { return sb; }
            @Override
            public StringBuilder getGenericSignature(StringBuilder sb) { return sb; }
        };

        JavaType ref = typeFactory.constructType(Integer.class);
        try {
            ReferenceType.upgradeFrom(nonTypeBase, ref);
            fail("Should have thrown IllegalArgumentException");
        } catch (IllegalArgumentException e) {
            assertTrue(e.getMessage().contains("Can not upgrade from an instance of"));
        }
    }

    public void testConstructorsAndFactories() throws Throwable {
        JavaType ref = typeFactory.constructType(String.class);
        ReferenceType t1 = ReferenceType.construct(java.util.concurrent.atomic.AtomicReference.class, TypeBindings.emptyBindings(), null, null, ref);
        assertNotNull(t1);
        assertEquals(ref, t1.getContentType());

        @SuppressWarnings("deprecation")
        ReferenceType t2 = ReferenceType.construct(java.util.concurrent.atomic.AtomicReference.class, ref);
        assertNotNull(t2);
        assertEquals(ref, t2.getContentType());
    }

    public void testWithContentType() throws Throwable {
        JavaType ref1 = typeFactory.constructType(String.class);
        JavaType ref2 = typeFactory.constructType(Integer.class);
        ReferenceType type = ReferenceType.upgradeFrom(typeFactory.constructType(java.util.concurrent.atomic.AtomicReference.class), ref1);

        // Same content type
        JavaType same = type.withContentType(ref1);
        assertSame(type, same);

        // Different content type
        JavaType diff = type.withContentType(ref2);
        assertNotNull(diff);
        assertEquals(ref2, diff.getContentType());
    }

    public void testWithTypeHandler() throws Throwable {
        JavaType ref = typeFactory.constructType(String.class);
        ReferenceType type = ReferenceType.upgradeFrom(typeFactory.constructType(java.util.concurrent.atomic.AtomicReference.class), ref);

        Object handler = new Object();
        JavaType same = type.withTypeHandler(handler);
        // first time sets it
        assertNotNull(same);
        JavaType sameAgain = same.withTypeHandler(handler);
        assertSame(same, sameAgain);

        JavaType diff = type.withTypeHandler(new Object());
        assertNotNull(diff);
    }

    public void testWithContentTypeHandler() throws Throwable {
        JavaType ref = typeFactory.constructType(String.class);
        ReferenceType type = ReferenceType.upgradeFrom(typeFactory.constructType(java.util.concurrent.atomic.AtomicReference.class), ref);

        Object handler = new Object();
        JavaType res1 = type.withContentTypeHandler(handler);
        assertNotNull(res1);
        
        // Calling again with same content type handler
        JavaType res2 = res1.withContentTypeHandler(handler);
        assertNotNull(res2);
    }

    public void testWithValueHandler() throws Throwable {
        JavaType ref = typeFactory.constructType(String.class);
        ReferenceType type = ReferenceType.upgradeFrom(typeFactory.constructType(java.util.concurrent.atomic.AtomicReference.class), ref);

        Object handler = new Object();
        JavaType same = type.withValueHandler(handler);
        assertNotNull(same);
        JavaType sameAgain = same.withValueHandler(handler);
        assertSame(same, sameAgain);

        JavaType diff = type.withValueHandler(new Object());
        assertNotNull(diff);
    }

    public void testWithContentValueHandler() throws Throwable {
        JavaType ref = typeFactory.constructType(String.class);
        ReferenceType type = ReferenceType.upgradeFrom(typeFactory.constructType(java.util.concurrent.atomic.AtomicReference.class), ref);

        Object handler = new Object();
        JavaType res1 = type.withContentValueHandler(handler);
        assertNotNull(res1);
        
        JavaType res2 = res1.withContentValueHandler(handler);
        assertNotNull(res2);
    }

    public void testWithStaticTyping() throws Throwable {
        JavaType ref = typeFactory.constructType(String.class);
        ReferenceType type = ReferenceType.upgradeFrom(typeFactory.constructType(java.util.concurrent.atomic.AtomicReference.class), ref);

        JavaType static1 = type.withStaticTyping();
        assertNotNull(static1);
        JavaType static2 = static1.withStaticTyping();
        assertSame(static1, static2);
    }

    public void testRefine() throws Throwable {
        JavaType ref = typeFactory.constructType(String.class);
        ReferenceType type = ReferenceType.upgradeFrom(typeFactory.constructType(java.util.concurrent.atomic.AtomicReference.class), ref);

        JavaType refined = type.refine(java.util.concurrent.atomic.AtomicReference.class, TypeBindings.emptyBindings(), null, null);
        assertNotNull(refined);
        assertTrue(refined instanceof ReferenceType);
    }

    public void testSignaturesAndCanonicalName() throws Throwable {
        JavaType ref = typeFactory.constructType(String.class);
        ReferenceType type = ReferenceType.upgradeFrom(typeFactory.constructType(java.util.concurrent.atomic.AtomicReference.class), ref);

        StringBuilder sb = new StringBuilder();
        StringBuilder resSb = type.getErasedSignature(sb);
        assertNotNull(resSb);

        StringBuilder sb2 = new StringBuilder();
        StringBuilder genSb = type.getGenericSignature(sb2);
        assertNotNull(genSb);

        String toStringVal = type.toString();
        assertNotNull(toStringVal);
        assertTrue(toStringVal.contains("reference type"));
    }

    @SuppressWarnings("deprecation")
    public void testNarrow() throws Throwable {
        JavaType ref = typeFactory.constructType(String.class);
        ReferenceType type = ReferenceType.upgradeFrom(typeFactory.constructType(java.util.concurrent.atomic.AtomicReference.class), ref);

        JavaType narrowed = type._narrow(java.util.concurrent.atomic.AtomicReference.class);
        assertNotNull(narrowed);
    }

    public void testEquals() throws Throwable {
        JavaType ref1 = typeFactory.constructType(String.class);
        JavaType ref2 = typeFactory.constructType(Integer.class);

        ReferenceType type1 = ReferenceType.upgradeFrom(typeFactory.constructType(java.util.concurrent.atomic.AtomicReference.class), ref1);
        ReferenceType type2 = ReferenceType.upgradeFrom(typeFactory.constructType(java.util.concurrent.atomic.AtomicReference.class), ref1);
        ReferenceType type3 = ReferenceType.upgradeFrom(typeFactory.constructType(java.util.concurrent.atomic.AtomicReference.class), ref2);
        SimpleType simpleType = SimpleType.constructUnsafe(String.class);

        assertEquals(type1, type1);
        assertEquals(type1, type2);
        assertFalse(type1.equals(null));
        assertFalse(type1.equals(simpleType));
        assertFalse(type1.equals(type3));
        
        // Different raw class
        ReferenceType type4 = ReferenceType.upgradeFrom(typeFactory.constructType(java.util.Optional.class), ref1);
        assertFalse(type1.equals(type4));
    }
}