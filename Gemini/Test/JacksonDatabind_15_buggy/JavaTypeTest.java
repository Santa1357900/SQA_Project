package com.fasterxml.jackson.databind;

import org.junit.Test;
import static org.junit.Assert.*;

import java.io.Serializable;
import java.util.Collection;
import java.util.Map;

public class JavaTest_GeneratedTest {

    private static class DummyJavaType extends JavaType {
        private static final long serialVersionUID = 1L;

        public DummyJavaType(Class<?> raw, int additionalHash, Object valueHandler, Object typeHandler, boolean asStatic) {
            super(raw, additionalHash, valueHandler, typeHandler, asStatic);
        }

        @Override
        public JavaType withTypeHandler(Object h) {
            return new DummyJavaType(_class, _hash, _valueHandler, h, _asStatic);
        }

        @Override
        public JavaType withContentTypeHandler(Object h) {
            return this;
        }

        @Override
        public JavaType withValueHandler(Object h) {
            return new DummyJavaType(_class, _hash, h, _typeHandler, _asStatic);
        }

        @Override
        public JavaType withContentValueHandler(Object h) {
            return this;
        }

        @Override
        public JavaType withStaticTyping() {
            return new DummyJavaType(_class, _hash, _valueHandler, _typeHandler, true);
        }

        @Override
        protected JavaType _narrow(Class<?> subclass) {
            return new DummyJavaType(subclass, 0, _valueHandler, _typeHandler, _asStatic);
        }

        @Override
        public JavaType narrowContentsBy(Class<?> contentClass) {
            return this;
        }

        @Override
        public JavaType widenContentsBy(Class<?> contentClass) {
            return this;
        }

        @Override
        public boolean isContainerType() {
            return false;
        }

        @Override
        public Class<?> getParameterSource() {
            return null;
        }

        @Override
        public StringBuilder getGenericSignature(StringBuilder sb) {
            return sb.append("DummySig");
        }

        @Override
        public StringBuilder getErasedSignature(StringBuilder sb) {
            return sb.append("DummyErasedSig");
        }

        @Override
        public String toString() {
            return "DummyType:" + _class.getName();
        }

        @Override
        public boolean equals(Object o) {
            if (o == this) return true;
            if (o == null || !(o instanceof DummyJavaType)) return false;
            return ((DummyJavaType) o)._class == _class;
        }
    }

    @Test
    public void testBasicProperties() throws Throwable {
        DummyJavaType type = new DummyJavaType(String.valueOf("abc").getClass(), 10, "valHandler", "typeHandler", true);
        
        assertEquals(String.class, type.getRawClass());
        assertTrue(type.hasRawClass(String.class));
        assertFalse(type.hasRawClass(Integer.class));
        
        assertFalse(type.isAbstract());
        assertTrue(type.isConcrete());
        assertFalse(type.isThrowable());
        assertFalse(type.isArrayType());
        assertFalse(type.isEnumType());
        assertFalse(type.isInterface());
        assertFalse(type.isPrimitive());
        assertFalse(type.isFinal()); // String is final, butModifier.isFinal check works on modifiers
        assertTrue(type.useStaticType());

        assertFalse(type.hasGenericTypes());
        assertNull(type.getKeyType());
        assertNull(type.getContentType());
        assertEquals(0, type.containedTypeCount());
        assertNull(type.containedType(0));
        assertNull(type.containedTypeName(0));
        assertNull(type.getParameterSource());

        assertEquals("valHandler", type.getValueHandler());
        assertEquals("typeHandler", type.getTypeHandler());
        assertEquals(String.class.getName().hashCode() + 10, type.hashCode());
    }

    @Test
    public void testPrimitiveAndSpecialTypes() throws Throwable {
        DummyJavaType intType = new DummyJavaType(int.class, 0, null, null, false);
        assertTrue(intType.isPrimitive());
        assertTrue(intType.isConcrete());
        assertFalse(intType.isAbstract());

        DummyJavaType throwableType = new DummyJavaType(Exception.class, 0, null, null, false);
        assertTrue(throwableType.isThrowable());

        DummyJavaType enumType = new DummyJavaType(java.math.RoundingMode.class, 0, null, null, false);
        assertTrue(enumType.isEnumType());

        DummyJavaType interfaceType = new DummyJavaType(Serializable.class, 0, null, null, false);
        assertTrue(interfaceType.isInterface());
        assertFalse(interfaceType.isConcrete());
        assertTrue(interfaceType.isAbstract());
    }

    @Test
    public void testNarrowBy() throws Throwable {
        DummyJavaType type = new DummyJavaType(Number.class, 0, "val", "type", false);
        
        // Same class returns this
        JavaType same = type.narrowBy(Number.class);
        assertSame(type, same);

        // Valid subclass
        JavaType narrowed = type.narrowBy(Integer.class);
        assertEquals(Integer.class, narrowed.getRawClass());
        assertEquals("val", narrowed.getValueHandler());
        assertEquals("type", narrowed.getTypeHandler());

        // Invalid subclass should throw IllegalArgumentException
        try {
            type.narrowBy(String.class);
            fail("Should have thrown IllegalArgumentException");
        } catch (IllegalArgumentException e) {
            assertTrue(e.getMessage().contains("is not assignable to"));
        }
    }

    @Test
    public void testForcedNarrowBy() throws Throwable {
        DummyJavaType type = new DummyJavaType(Number.class, 0, "val", "type", false);
        
        JavaType same = type.forcedNarrowBy(Number.class);
        assertSame(type, same);

        JavaType narrowed = type.forcedNarrowBy(Long.class);
        assertEquals(Long.class, narrowed.getRawClass());
    }

    @Test
    public void testWidenBy() throws Throwable {
        DummyJavaType type = new DummyJavaType(Integer.class, 0, null, null, false);
        
        JavaType same = type.widenBy(Integer.class);
        assertSame(type, same);

        JavaType widened = type.widenBy(Number.class);
        assertEquals(Number.class, widened.getRawClass());

        try {
            type.widenBy(String.class);
            fail("Should have thrown IllegalArgumentException");
        } catch (IllegalArgumentException e) {
            assertTrue(e.getMessage().contains("is not assignable to"));
        }
    }

    @Test
    public void testContainedTypeOrUnknown() throws Throwable {
        DummyJavaType type = new DummyJavaType(Object.class, 0, null, null, false);
        JavaType unknown = type.containedTypeOrUnknown(0);
        assertNotNull(unknown);
    }

    @Test
    public void testSignatures() throws Throwable {
        DummyJavaType type = new DummyJavaType(Object.class, 0, null, null, false);
        assertEquals("DummySig", type.getGenericSignature());
        assertEquals("DummyErasedSig", type.getErasedSignature());
    }

    @Test
    public void testHandlersAndFluentMethods() throws Throwable {
        DummyJavaType type = new DummyJavaType(Object.class, 0, null, null, false);
        JavaType t1 = type.withTypeHandler("THandler");
        assertEquals("THandler", t1.getTypeHandler());

        JavaType t2 = type.withValueHandler("VHandler");
        assertEquals("VHandler", t2.getValueHandler());

        JavaType t3 = type.withStaticTyping();
        assertTrue(t3.useStaticType());

        // Additional trivial method checks
        assertSame(type, type.withContentTypeHandler(null));
        assertSame(type, type.withContentValueHandler(null));
        assertSame(type, type.widenContentsBy(null));
        assertSame(type, type.narrowContentsBy(null));
        assertFalse(type.isCollectionLikeType());
        assertFalse(type.isMapLikeType());
    }

    @Test
    public void testEqualsAndToString() throws Throwable {
        DummyJavaType type1 = new DummyJavaType(String.class, 0, null, null, false);
        DummyJavaType type2 = new DummyJavaType(String.class, 0, null, null, false);
        DummyJavaType type3 = new DummyJavaType(Integer.class, 0, null, null, false);

        assertTrue(type1.equals(type1));
        assertTrue(type1.equals(type2));
        assertFalse(type1.equals(type3));
        assertFalse(type1.equals(null));
        assertFalse(type1.equals("some string"));

        assertTrue(type1.toString().contains("String"));
    }
}