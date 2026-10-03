package com.fasterxml.jackson.databind;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.junit.Before;
import org.junit.Test;
import static org.junit.Assert.*;

import com.fasterxml.jackson.databind.type.TypeFactory;

public class JavaTypeClaudeTest {

    private TypeFactory factory;

    public static class SamplePojo {
        private String value;
        public String getValue() { return value; }
        public void setValue(String value) { this.value = value; }
    }

    public enum SampleEnum { A, B }

    @Before
    public void setUp() throws Throwable {
        factory = TypeFactory.defaultInstance();
    }

    // covers getRawClass(): returns exact erased class passed to factory
    @Test
    public void testGetRawClass_returnsGivenClass() throws Throwable {
        JavaType t = factory.constructType(String.class);
        assertEquals(String.class, t.getRawClass());
    }

    // covers hasRawClass(): true for same class, false for different class
    @Test
    public void testHasRawClass_sameAndDifferentClass() throws Throwable {
        JavaType t = factory.constructType(String.class);
        assertTrue(t.hasRawClass(String.class));
        assertFalse(t.hasRawClass(Integer.class));
    }

    // covers hasContentType(): per javadoc must equal isContainerType()||isReferenceType(); simple type -> false
    @Test
    public void testHasContentType_simpleType_isFalsePerContract() throws Throwable {
        JavaType t = factory.constructType(String.class);
        assertFalse(t.isContainerType());
        assertFalse(t.hasContentType());
    }

    // covers hasContentType(): container type override -> true
    @Test
    public void testHasContentType_containerType_isTrue() throws Throwable {
        JavaType t = factory.constructCollectionType(ArrayList.class, String.class);
        assertTrue(t.isContainerType());
        assertTrue(t.hasContentType());
    }

    // covers isTypeOrSubTypeOf(): same-class branch, assignable branch, unrelated false branch
    @Test
    public void testIsTypeOrSubTypeOf_variousClasses() throws Throwable {
        JavaType t = factory.constructCollectionType(ArrayList.class, String.class);
        assertTrue(t.isTypeOrSubTypeOf(ArrayList.class));
        assertTrue(t.isTypeOrSubTypeOf(List.class));
        assertFalse(t.isTypeOrSubTypeOf(Map.class));
    }

    // covers isAbstract(): concrete class false, abstract class true
    @Test
    public void testIsAbstract_concreteAndAbstractClasses() throws Throwable {
        assertFalse(factory.constructType(String.class).isAbstract());
        assertTrue(factory.constructType(Number.class).isAbstract());
    }

    // covers isConcrete(): concrete true, abstract false, interface false, primitive true
    @Test
    public void testIsConcrete_variousKinds() throws Throwable {
        assertTrue(factory.constructType(String.class).isConcrete());
        assertFalse(factory.constructType(Number.class).isConcrete());
        assertFalse(factory.constructType(List.class).isConcrete());
        assertTrue(factory.constructType(int.class).isConcrete());
    }

    // covers isThrowable(): true for Throwable subtype, false otherwise
    @Test
    public void testIsThrowable_trueAndFalse() throws Throwable {
        assertTrue(factory.constructType(Exception.class).isThrowable());
        assertFalse(factory.constructType(String.class).isThrowable());
    }

    // covers isArrayType(): default false, true for array type
    @Test
    public void testIsArrayType_defaultFalseAndArrayTrue() throws Throwable {
        assertFalse(factory.constructType(String.class).isArrayType());
        assertTrue(factory.constructArrayType(String.class).isArrayType());
    }

    // covers isEnumType(): true for enum, false otherwise
    @Test
    public void testIsEnumType_trueAndFalse() throws Throwable {
        assertTrue(factory.constructType(SampleEnum.class).isEnumType());
        assertFalse(factory.constructType(String.class).isEnumType());
    }

    // covers isInterface(): true for interface, false for class
    @Test
    public void testIsInterface_trueAndFalse() throws Throwable {
        assertTrue(factory.constructType(List.class).isInterface());
        assertFalse(factory.constructType(String.class).isInterface());
    }

    // covers isPrimitive(): true for primitive, false for wrapper
    @Test
    public void testIsPrimitive_trueAndFalse() throws Throwable {
        assertTrue(factory.constructType(int.class).isPrimitive());
        assertFalse(factory.constructType(Integer.class).isPrimitive());
    }

    // covers isFinal(): true for final class, false for non-final class
    @Test
    public void testIsFinal_trueAndFalse() throws Throwable {
        assertTrue(factory.constructType(String.class).isFinal());
        assertFalse(factory.constructType(Number.class).isFinal());
    }

    // covers isContainerType(): false for simple type, true for collection type
    @Test
    public void testIsContainerType_falseAndTrue() throws Throwable {
        assertFalse(factory.constructType(String.class).isContainerType());
        assertTrue(factory.constructCollectionType(ArrayList.class, String.class).isContainerType());
    }

    // covers isCollectionLikeType(): default false, true for List type
    @Test
    public void testIsCollectionLikeType_defaultFalseAndListTrue() throws Throwable {
        assertFalse(factory.constructType(String.class).isCollectionLikeType());
        assertTrue(factory.constructCollectionType(ArrayList.class, String.class).isCollectionLikeType());
    }

    // covers isMapLikeType(): default false, true for Map type
    @Test
    public void testIsMapLikeType_defaultFalseAndMapTrue() throws Throwable {
        assertFalse(factory.constructType(String.class).isMapLikeType());
        assertTrue(factory.constructMapType(HashMap.class, String.class, Integer.class).isMapLikeType());
    }

    // covers isJavaLangObject(): true for Object, false otherwise
    @Test
    public void testIsJavaLangObject_trueAndFalse() throws Throwable {
        assertTrue(factory.constructType(Object.class).isJavaLangObject());
        assertFalse(factory.constructType(String.class).isJavaLangObject());
    }

    // covers useStaticType(): false by default, true after withStaticTyping(), original untouched
    @Test
    public void testUseStaticType_defaultFalseAndAfterWithStaticTyping() throws Throwable {
        JavaType t = factory.constructType(String.class);
        assertFalse(t.useStaticType());
        JavaType st = t.withStaticTyping();
        assertTrue(st.useStaticType());
        assertFalse(t.useStaticType());
    }

    // covers hasGenericTypes(): false when containedTypeCount==0, true when >0
    @Test
    public void testHasGenericTypes_zeroAndNonZero() throws Throwable {
        assertFalse(factory.constructType(String.class).hasGenericTypes());
        assertTrue(factory.constructCollectionType(ArrayList.class, String.class).hasGenericTypes());
    }

    // covers getKeyType(): default null for non-map, non-null for map type
    @Test
    public void testGetKeyType_defaultNullAndMapType() throws Throwable {
        assertNull(factory.constructCollectionType(ArrayList.class, String.class).getKeyType());
        JavaType mapType = factory.constructMapType(HashMap.class, String.class, Integer.class);
        assertEquals(String.class, mapType.getKeyType().getRawClass());
    }

    // covers getContentType(): default null for simple type, non-null for collection/map type
    @Test
    public void testGetContentType_defaultNullAndContainerTypes() throws Throwable {
        assertNull(factory.constructType(String.class).getContentType());
        assertEquals(String.class, factory.constructCollectionType(ArrayList.class, String.class).getContentType().getRawClass());
        assertEquals(Integer.class, factory.constructMapType(HashMap.class, String.class, Integer.class).getContentType().getRawClass());
    }

    // covers getReferencedType(): default null
    @Test
    public void testGetReferencedType_defaultNull() throws Throwable {
        assertNull(factory.constructType(String.class).getReferencedType());
    }

    // covers containedTypeOrUnknown(): null contained type maps to unknown (java.lang.Object) type
    @Test
    public void testContainedTypeOrUnknown_nullMapsToUnknownType() throws Throwable {
        JavaType t = factory.constructType(String.class);
        assertNull(t.containedType(0));
        JavaType unknown = t.containedTypeOrUnknown(0);
        assertTrue(unknown.isJavaLangObject());
    }

    // covers containedTypeOrUnknown(): non-null contained type returned as-is
    @Test
    public void testContainedTypeOrUnknown_nonNullReturnsSameContainedType() throws Throwable {
        JavaType t = factory.constructCollectionType(ArrayList.class, String.class);
        JavaType contained = t.containedType(0);
        assertNotNull(contained);
        assertEquals(String.class, t.containedTypeOrUnknown(0).getRawClass());
    }

    // covers getBindings(): accessor does not throw and returns a value
    @Test
    public void testGetBindings_returnsNonNull() throws Throwable {
        JavaType t = factory.constructCollectionType(ArrayList.class, String.class);
        assertNotNull(t.getBindings());
    }

    // covers findSuperType(): Object is always a supertype; unrelated type yields null
    @Test
    public void testFindSuperType_objectFoundAndUnrelatedNull() throws Throwable {
        JavaType t = factory.constructType(String.class);
        JavaType superType = t.findSuperType(Object.class);
        assertNotNull(superType);
        assertEquals(Object.class, superType.getRawClass());
        assertNull(t.findSuperType(List.class));
    }

    // covers getSuperClass(): non-null for a normal class, null for java.lang.Object
    @Test
    public void testGetSuperClass_nonNullAndObjectHasNoSuperClass() throws Throwable {
        JavaType stringSuper = factory.constructType(String.class).getSuperClass();
        assertNotNull(stringSuper);
        assertEquals(Object.class, stringSuper.getRawClass());
        assertNull(factory.constructType(Object.class).getSuperClass());
    }

    // covers getInterfaces(): empty for a plain POJO, non-empty for String
    @Test
    public void testGetInterfaces_emptyAndNonEmpty() throws Throwable {
        List<JavaType> pojoInterfaces = factory.constructType(SamplePojo.class).getInterfaces();
        assertNotNull(pojoInterfaces);
        assertEquals(0, pojoInterfaces.size());
        List<JavaType> stringInterfaces = factory.constructType(String.class).getInterfaces();
        assertTrue(stringInterfaces.size() > 0);
    }

    // covers findTypeParameters(): resolves generic element type of List for constructed collection type
    @Test
    public void testFindTypeParameters_resolvesElementType() throws Throwable {
        JavaType t = factory.constructCollectionType(ArrayList.class, String.class);
        JavaType[] params = t.findTypeParameters(List.class);
        assertEquals(1, params.length);
        assertEquals(String.class, params[0].getRawClass());
    }

    // covers withValueHandler()/getValueHandler(): immutable copy carries handler, original unaffected
    @Test
    public void testWithValueHandler_setsHandlerImmutably() throws Throwable {
        JavaType original = factory.constructType(String.class);
        Object handler = new Object();
        JavaType withHandler = original.withValueHandler(handler);
        Object got = withHandler.getValueHandler();
        assertEquals(handler, got);
        assertNull(original.getValueHandler());
    }

    // covers withTypeHandler()/getTypeHandler(): immutable copy carries handler, original unaffected
    @Test
    public void testWithTypeHandler_setsHandlerImmutably() throws Throwable {
        JavaType original = factory.constructType(String.class);
        Object handler = new Object();
        JavaType withHandler = original.withTypeHandler(handler);
        Object got = withHandler.getTypeHandler();
        assertEquals(handler, got);
        assertNull(original.getTypeHandler());
    }

    // covers withContentTypeHandler()/withContentValueHandler(): stored on container type, immutable
    @Test
    public void testWithContentHandlers_onContainerType() throws Throwable {
        JavaType arrayType = factory.constructArrayType(String.class);
        Object typeHandler = new Object();
        Object valueHandler = new Object();
        JavaType withTypeH = arrayType.withContentTypeHandler(typeHandler);
        JavaType withValueH = arrayType.withContentValueHandler(valueHandler);
        assertEquals(typeHandler, withTypeH.getContentTypeHandler());
        assertEquals(valueHandler, withValueH.getContentValueHandler());
        assertNull(arrayType.getContentTypeHandler());
    }

    // covers hasValueHandler(): false by default, true once value handler is set
    @Test
    public void testHasValueHandler_defaultFalseThenTrue() throws Throwable {
        JavaType original = factory.constructType(String.class);
        assertFalse(original.hasValueHandler());
        assertTrue(original.withValueHandler(new Object()).hasValueHandler());
    }

    // covers hasHandlers(): false by default, true when either handler is present
    @Test
    public void testHasHandlers_defaultFalseThenTrueForEitherHandler() throws Throwable {
        JavaType original = factory.constructType(String.class);
        assertFalse(original.hasHandlers());
        assertTrue(original.withTypeHandler(new Object()).hasHandlers());
        assertTrue(original.withValueHandler(new Object()).hasHandlers());
    }

    // covers withContentType(): throws IllegalArgumentException for a type without content type
    @Test
    public void testWithContentType_simpleTypeThrows() throws Throwable {
        JavaType simple = factory.constructType(String.class);
        JavaType newContent = factory.constructType(Integer.class);
        try {
            simple.withContentType(newContent);
            fail("expected IllegalArgumentException");
        } catch (IllegalArgumentException expected) { }
    }

    // covers withContentType(): container type gets new content type; same content type returns identical instance
    @Test
    public void testWithContentType_containerTypeChangesOrKeepsContent() throws Throwable {
        JavaType arrayType = factory.constructArrayType(String.class);
        JavaType sameAgain = arrayType.withContentType(factory.constructType(String.class));
        assertSame(arrayType, sameAgain);
        JavaType changed = arrayType.withContentType(factory.constructType(Integer.class));
        assertEquals(Integer.class, changed.getContentType().getRawClass());
    }

    // covers forcedNarrowBy(): returns this when subclass equals raw class (optimization branch)
    @Test
    public void testForcedNarrowBy_sameClassReturnsThis() throws Throwable {
        JavaType t = factory.constructType(String.class);
        JavaType narrowed = t.forcedNarrowBy(String.class);
        assertSame(t, narrowed);
    }

    // covers getContentValueHandler()/getContentTypeHandler(): default null on simple type
    @Test
    public void testDefaultContentHandlers_areNull() throws Throwable {
        JavaType t = factory.constructType(String.class);
        assertNull(t.getContentValueHandler());
        assertNull(t.getContentTypeHandler());
    }

    // covers getParameterSource(): deprecated accessor default null
    @Test
    public void testGetParameterSource_defaultNull() throws Throwable {
        assertNull(factory.constructType(String.class).getParameterSource());
    }

    // covers getGenericSignature(): produces a non-empty signature
    @Test
    public void testGetGenericSignature_nonEmpty() throws Throwable {
        String sig = factory.constructType(String.class).getGenericSignature();
        assertNotNull(sig);
        assertTrue(sig.length() > 0);
    }

    // covers getErasedSignature(): primitive uses JVM descriptor "I"; reference type contains class name
    @Test
    public void testGetErasedSignature_primitiveAndReferenceType() throws Throwable {
        assertEquals("I", factory.constructType(int.class).getErasedSignature());
        String sig = factory.constructType(String.class).getErasedSignature();
        assertTrue(sig.indexOf("String") >= 0);
    }

    // covers equals()/hashCode(): same type equal with equal hash, different type not equal
    @Test
    public void testEqualsAndHashCode_sameAndDifferentTypes() throws Throwable {
        JavaType t1 = factory.constructType(String.class);
        JavaType t2 = factory.constructType(String.class);
        JavaType t3 = factory.constructType(Integer.class);
        assertTrue(t1.equals(t2));
        assertEquals(t1.hashCode(), t2.hashCode());
        assertFalse(t1.equals(t3));
    }

    // covers toString(): returns non-empty representation
    @Test
    public void testToString_nonEmpty() throws Throwable {
        String s = factory.constructType(String.class).toString();
        assertNotNull(s);
        assertTrue(s.length() > 0);
    }
}
