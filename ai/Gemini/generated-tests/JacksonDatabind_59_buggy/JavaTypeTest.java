package com.fasterxml.jackson.databind;

import org.junit.Test;
import static org.junit.Assert.*;

import java.util.List;
import com.fasterxml.jackson.databind.type.TypeBindings;
import com.fasterxml.jackson.databind.type.TypeFactory;

public class JavaTypeTest {

    private static class DummyJavaType extends JavaType {
        private static final long serialVersionUID = 1L;

        public DummyJavaType(Class<?> raw) {
            super(raw, 0, null, null, false);
        }

        public DummyJavaType(Class<?> raw, Object valueHandler, Object typeHandler, boolean asStatic) {
            super(raw, 0, valueHandler, typeHandler, asStatic);
        }

        public DummyJavaType(JavaType base) {
            super(base);
        }

        @Override
        public JavaType withTypeHandler(Object h) {
            return new DummyJavaType(_class, _valueHandler, h, _asStatic);
        }

        @Override
        public JavaType withContentTypeHandler(Object h) {
            return this;
        }

        @Override
        public JavaType withValueHandler(Object h) {
            return new DummyJavaType(_class, h, _typeHandler, _asStatic);
        }

        @Override
        public JavaType withContentValueHandler(Object h) {
            return this;
        }

        @Override
        public JavaType withContentType(JavaType contentType) {
            return this;
        }

        @Override
        public JavaType withStaticTyping() {
            return new DummyJavaType(_class, _valueHandler, _typeHandler, true);
        }

        @Override
        public JavaType refine(Class<?> rawType, TypeBindings bindings, JavaType superClass, JavaType[] superInterfaces) {
            return null;
        }

        @Override
        protected JavaType _narrow(Class<?> subclass) {
            return new DummyJavaType(subclass);
        }

        @Override
        public boolean isContainerType() {
            return false;
        }

        @Override
        public int containedTypeCount() {
            return 0;
        }

        @Override
        public JavaType containedType(int index) {
            return null;
        }

        @Override
        public String containedTypeName(int index) {
            return null;
        }

        @Override
        public TypeBindings getBindings() {
            return null;
        }

        @Override
        public JavaType findSuperType(Class<?> erasedTarget) {
            return null;
        }

        @Override
        public JavaType getSuperClass() {
            return null;
        }

        @Override
        public List<JavaType> getInterfaces() {
            return null;
        }

        @Override
        public JavaType[] findTypeParameters(Class<?> expType) {
            return null;
        }

        @Override
        public StringBuilder getGenericSignature(StringBuilder sb) {
            return sb.append("Dummy");
        }

        @Override
        public StringBuilder getErasedSignature(StringBuilder sb) {
            return sb.append("Dummy");
        }

        @Override
        public String toString() {
            return "DummyJavaType";
        }

        @Override
        public boolean equals(Object o) {
            if (o == this) return true;
            if (o == null) return false;
            if (o.getClass() != getClass()) return false;
            DummyJavaType other = (DummyJavaType) o;
            return other._class == _class;
        }
    }

    @Test
    public void testGettersAndBasicProperties() throws Throwable {
        DummyJavaType type = new DummyJavaType(String.class);
        assertEquals(String.class, type.getRawClass());
        assertTrue(type.hasRawClass(String.class));
        assertFalse(type.hasRawClass(Integer.class));
        assertTrue(type.hasContentType());
        assertTrue(type.isTypeOrSubTypeOf(Object.class));
        assertFalse(type.isAbstract());
        assertTrue(type.isConcrete());
        assertFalse(type.isThrowable());
        assertFalse(type.isArrayType());
        assertFalse(type.isEnumType());
        assertFalse(type.isInterface());
        assertFalse(type.isPrimitive());
        assertFalse(type.isFinal());
        assertFalse(type.isCollectionLikeType());
        assertFalse(type.isMapLikeType());
        assertFalse(type.isJavaLangObject());
        assertFalse(type.useStaticType());
        assertFalse(type.hasGenericTypes());
        assertNull(type.getKeyType());
        assertNull(type.getContentType());
        assertNull(type.getReferencedType());
        assertNull(type.getParameterSource());
    }

    @Test
    public void testPrimitiveAndAbstractProperties() throws Throwable {
        DummyJavaType primitiveType = new DummyJavaType(int.class);
        assertTrue(primitiveType.isPrimitive());
        assertTrue(primitiveType.isConcrete());

        DummyJavaType abstractType = new DummyJavaType(List.class);
        assertTrue(abstractType.isAbstract());
        assertFalse(abstractType.isConcrete());

        DummyJavaType enumType = new DummyJavaType(java.math.RoundingMode.class);
        assertTrue(enumType.isEnumType());

        DummyJavaType interfaceType = new DummyJavaType(Runnable.class);
        assertTrue(interfaceType.isInterface());

        DummyJavaType throwableType = new DummyJavaType(Exception.class);
        assertTrue(throwableType.isThrowable());

        DummyJavaType objectType = new DummyJavaType(Object.class);
        assertTrue(objectType.isJavaLangObject());
    }

    @Test
    public void testContainedTypeOrUnknown() throws Throwable {
        DummyJavaType type = new DummyJavaType(String.class);
        JavaType resolved = type.containedTypeOrUnknown(0);
        assertNotNull(resolved);
    }

    @Test
    public void testHandlersAndFluentMethods() throws Throwable {
        Object valueHandler = new Object();
        Object typeHandler = new Object();
        DummyJavaType type = new DummyJavaType(String.class, null, null, false);
        assertFalse(type.hasValueHandler());
        assertFalse(type.hasHandlers());

        JavaType withVal = type.withValueHandler(valueHandler);
        assertTrue(withVal.hasValueHandler());
        assertTrue(withVal.hasHandlers());
        assertSame(valueHandler, withVal.getValueHandler());

        JavaType withType = type.withTypeHandler(typeHandler);
        assertTrue(withType.hasHandlers());
        assertSame(typeHandler, withType.getTypeHandler());

        JavaType staticType = type.withStaticTyping();
        assertTrue(staticType.useStaticType());

        DummyJavaType base = new DummyJavaType(String.class);
        DummyJavaType copy = new DummyJavaType(base);
        assertEquals(base.getRawClass(), copy.getRawClass());
    }

    @Test
    public void testForcedNarrowBy() throws Throwable {
        DummyJavaType type = new DummyJavaType(Object.class);
        JavaType narrowed = type.forcedNarrowBy(String.class);
        assertEquals(String.class, narrowed.getRawClass());

        JavaType same = type.forcedNarrowBy(Object.class);
        assertSame(type, same);
    }

    @Test
    public void testSignaturesAndMisc() throws Throwable {
        DummyJavaType type = new DummyJavaType(String.class);
        assertNotNull(type.getGenericSignature());
        assertNotNull(type.getErasedSignature());
        assertEquals(type.hashCode(), type.hashCode());
        assertNull(type.getContentValueHandler());
        assertNull(type.getContentTypeHandler());
    }
}