package com.fasterxml.jackson.databind.type;

import java.util.concurrent.atomic.AtomicReference;

import org.junit.Before;
import org.junit.Test;
import static org.junit.Assert.*;

import com.fasterxml.jackson.databind.JavaType;

public class ReferenceTypeClaudeTest
{
    private TypeFactory typeFactory;
    private JavaType stringType;
    private JavaType intType;
    private JavaType objectType;

    @Before
    public void setUp() throws Throwable {
        typeFactory = TypeFactory.defaultInstance();
        stringType = typeFactory.constructType(String.class);
        intType = typeFactory.constructType(Integer.class);
        objectType = typeFactory.constructType(Object.class);
    }

    private ReferenceType buildBase(JavaType refType) {
        return ReferenceType.construct(AtomicReference.class, TypeBindings.emptyBindings(),
                objectType, new JavaType[0], refType);
    }

    // Covers upgradeFrom(): valid TypeBase baseType is upgraded; new instance becomes its own anchor
    @Test
    public void testUpgradeFrom_validBaseType_setsAnchorAndReferencedType() throws Throwable {
        ReferenceType base = buildBase(stringType);
        ReferenceType upgraded = ReferenceType.upgradeFrom(base, intType);
        assertEquals(intType, upgraded.getReferencedType());
        assertTrue(upgraded.isAnchorType());
    }

    // Covers upgradeFrom(): null refdType branch throws IllegalArgumentException
    @Test
    public void testUpgradeFrom_nullRefdType_throwsIllegalArgumentException() throws Throwable {
        ReferenceType base = buildBase(stringType);
        try {
            ReferenceType.upgradeFrom(base, null);
            fail("expected IllegalArgumentException");
        } catch (IllegalArgumentException expected) { }
    }

    // Covers construct(2.7): result is anchor type with given referenced type
    @Test
    public void testConstruct27_setsReferencedTypeAndIsAnchor() throws Throwable {
        ReferenceType rt = buildBase(stringType);
        assertEquals(stringType, rt.getReferencedType());
        assertTrue(rt.isAnchorType());
    }

    // Covers construct(2.7): hasContentType/isReferenceType/getContentType
    @Test
    public void testConstruct27_hasContentTypeAndIsReferenceType() throws Throwable {
        ReferenceType rt = buildBase(stringType);
        assertTrue(rt.hasContentType());
        assertTrue(rt.isReferenceType());
        assertEquals(stringType, rt.getContentType());
    }

    // BUG-catching: deprecated construct(Class, JavaType) must set referencedType correctly
    @Test
    public void testConstructDeprecated_setsReferencedTypeCorrectly() throws Throwable {
        ReferenceType rt = ReferenceType.construct(AtomicReference.class, stringType);
        assertEquals(stringType, rt.getReferencedType());
    }

    // BUG-catching: deprecated construct(Class, JavaType) result should be its own anchor type
    @Test
    public void testConstructDeprecated_resultIsAnchorType() throws Throwable {
        ReferenceType rt = ReferenceType.construct(AtomicReference.class, intType);
        assertTrue(rt.isAnchorType());
    }

    // Covers withContentType(): same reference content type returns same instance
    @Test
    public void testWithContentType_sameType_returnsSameInstance() throws Throwable {
        ReferenceType rt = buildBase(stringType);
        JavaType result = rt.withContentType(stringType);
        assertSame(rt, result);
    }

    // Covers withContentType(): different content type creates new instance, original unchanged
    @Test
    public void testWithContentType_differentType_returnsNewInstanceWithUpdatedContent() throws Throwable {
        ReferenceType rt = buildBase(stringType);
        JavaType result = rt.withContentType(intType);
        assertNotSame(rt, result);
        assertEquals(intType, result.getContentType());
        assertEquals(stringType, rt.getContentType());
    }

    // Covers withTypeHandler(): same handler (null) returns same instance
    @Test
    public void testWithTypeHandler_sameHandler_returnsSameInstance() throws Throwable {
        ReferenceType rt = buildBase(stringType);
        ReferenceType result = rt.withTypeHandler(null);
        assertSame(rt, result);
    }

    // Covers withTypeHandler(): different handler creates new instance with handler stored
    @Test
    public void testWithTypeHandler_differentHandler_returnsNewInstanceWithHandlerSet() throws Throwable {
        ReferenceType rt = buildBase(stringType);
        Object handler = new Object();
        ReferenceType result = rt.withTypeHandler(handler);
        assertNotSame(rt, result);
        assertSame(handler, result._typeHandler);
    }

    // Covers withContentTypeHandler(): same handler (null) returns same instance
    @Test
    public void testWithContentTypeHandler_sameHandler_returnsSameInstance() throws Throwable {
        ReferenceType rt = buildBase(stringType);
        ReferenceType result = rt.withContentTypeHandler(null);
        assertSame(rt, result);
    }

    // Covers withContentTypeHandler(): different handler creates new instance with new content type
    @Test
    public void testWithContentTypeHandler_differentHandler_returnsNewInstance() throws Throwable {
        ReferenceType rt = buildBase(stringType);
        Object handler = new Object();
        ReferenceType result = rt.withContentTypeHandler(handler);
        assertNotSame(rt, result);
        assertNotSame(rt.getContentType(), result.getContentType());
    }

    // Covers withValueHandler(): same handler (null) returns same instance
    @Test
    public void testWithValueHandler_sameHandler_returnsSameInstance() throws Throwable {
        ReferenceType rt = buildBase(stringType);
        ReferenceType result = rt.withValueHandler(null);
        assertSame(rt, result);
    }

    // Covers withValueHandler(): different handler creates new instance with handler stored
    @Test
    public void testWithValueHandler_differentHandler_returnsNewInstanceWithHandlerSet() throws Throwable {
        ReferenceType rt = buildBase(stringType);
        Object handler = new Object();
        ReferenceType result = rt.withValueHandler(handler);
        assertNotSame(rt, result);
        assertSame(handler, result._valueHandler);
    }

    // Covers withContentValueHandler(): same handler (null) returns same instance
    @Test
    public void testWithContentValueHandler_sameHandler_returnsSameInstance() throws Throwable {
        ReferenceType rt = buildBase(stringType);
        ReferenceType result = rt.withContentValueHandler(null);
        assertSame(rt, result);
    }

    // Covers withContentValueHandler(): different handler creates new instance with new content type
    @Test
    public void testWithContentValueHandler_differentHandler_returnsNewInstance() throws Throwable {
        ReferenceType rt = buildBase(stringType);
        Object handler = new Object();
        ReferenceType result = rt.withContentValueHandler(handler);
        assertNotSame(rt, result);
        assertNotSame(rt.getContentType(), result.getContentType());
    }

    // Covers withStaticTyping(): already static returns same instance
    @Test
    public void testWithStaticTyping_alreadyStatic_returnsSameInstance() throws Throwable {
        ReferenceType rt = buildBase(stringType);
        ReferenceType staticOne = rt.withStaticTyping();
        ReferenceType result = staticOne.withStaticTyping();
        assertSame(staticOne, result);
    }

    // Covers withStaticTyping(): not static creates new instance with _asStatic true
    @Test
    public void testWithStaticTyping_notStatic_returnsNewInstanceWithStaticTrue() throws Throwable {
        ReferenceType rt = buildBase(stringType);
        ReferenceType result = rt.withStaticTyping();
        assertNotSame(rt, result);
        assertTrue(result._asStatic);
        assertFalse(rt._asStatic);
    }

    // Covers refine(): updates raw class while keeping referenced type and anchor
    @Test
    public void testRefine_updatesRawClassKeepsReferencedTypeAndAnchor() throws Throwable {
        ReferenceType rt = buildBase(stringType);
        ReferenceType refined = (ReferenceType) rt.refine(Object.class, TypeBindings.emptyBindings(),
                objectType, new JavaType[0]);
        assertEquals(Object.class, refined._class);
        assertEquals(stringType, refined.getContentType());
        assertSame(rt.getAnchorType(), refined.getAnchorType());
    }

    // Covers buildCanonicalName(): contains raw class name and referenced type's canonical name
    @Test
    public void testBuildCanonicalName_containsClassAndReferencedTypeCanonical() throws Throwable {
        ReferenceType rt = buildBase(stringType);
        String canonical = rt.buildCanonicalName();
        assertTrue(canonical.startsWith(AtomicReference.class.getName()));
        assertTrue(canonical.contains(stringType.toCanonical()));
    }

    // Covers deprecated _narrow(): creates new instance with given subclass, keeps content type
    @Test
    public void testNarrow_returnsNewInstanceWithGivenSubclass() throws Throwable {
        ReferenceType rt = buildBase(stringType);
        ReferenceType narrowed = (ReferenceType) rt._narrow(Object.class);
        assertEquals(Object.class, narrowed._class);
        assertEquals(stringType, narrowed.getContentType());
    }

    // Covers getErasedSignature(): returns same StringBuilder instance with non-empty content
    @Test
    public void testGetErasedSignature_returnsSameBuilderNonEmpty() throws Throwable {
        ReferenceType rt = buildBase(stringType);
        StringBuilder sb = new StringBuilder();
        StringBuilder result = rt.getErasedSignature(sb);
        assertSame(sb, result);
        assertTrue(result.length() > 0);
    }

    // Covers getGenericSignature(): ends with closing angle bracket and semicolon as coded
    @Test
    public void testGetGenericSignature_endsWithClosingAngleBracketSemicolon() throws Throwable {
        ReferenceType rt = buildBase(stringType);
        StringBuilder sb = new StringBuilder();
        StringBuilder result = rt.getGenericSignature(sb);
        assertTrue(result.toString().endsWith(">;"));
    }

    // Covers getAnchorType(): base (freshly constructed) instance is its own anchor
    @Test
    public void testGetAnchorType_forBaseInstance_returnsSelf() throws Throwable {
        ReferenceType rt = buildBase(stringType);
        assertSame(rt, rt.getAnchorType());
    }

    // Covers isAnchorType(): derived instance (via withContentType) is not the anchor itself
    @Test
    public void testIsAnchorType_derivedInstanceViaWithContentType_isFalse() throws Throwable {
        ReferenceType rt = buildBase(stringType);
        ReferenceType derived = (ReferenceType) rt.withContentType(intType);
        assertFalse(derived.isAnchorType());
        assertSame(rt, derived.getAnchorType());
    }

    // Covers toString(): expected wrapper format
    @Test
    public void testToString_containsReferenceTypeMarkerAndClassName() throws Throwable {
        ReferenceType rt = buildBase(stringType);
        String s = rt.toString();
        assertTrue(s.startsWith("[reference type, class "));
        assertTrue(s.endsWith("]"));
    }

    // Covers equals(): same reference returns true
    @Test
    public void testEquals_sameReference_returnsTrue() throws Throwable {
        ReferenceType rt = buildBase(stringType);
        assertTrue(rt.equals(rt));
    }

    // Covers equals(): null argument returns false
    @Test
    public void testEquals_null_returnsFalse() throws Throwable {
        ReferenceType rt = buildBase(stringType);
        assertFalse(rt.equals(null));
    }

    // Covers equals(): different runtime class returns false
    @Test
    public void testEquals_differentClassType_returnsFalse() throws Throwable {
        ReferenceType rt = buildBase(stringType);
        assertFalse(rt.equals("not a reference type"));
    }

    // Covers equals(): same class but different raw class field returns false
    @Test
    public void testEquals_differentRawClass_returnsFalse() throws Throwable {
        ReferenceType rt1 = buildBase(stringType);
        ReferenceType rt2 = (ReferenceType) rt1.refine(Object.class, TypeBindings.emptyBindings(),
                objectType, new JavaType[0]);
        assertFalse(rt1.equals(rt2));
    }

    // Covers equals(): same raw class but different referenced type returns false
    @Test
    public void testEquals_sameRawClassDifferentReferencedType_returnsFalse() throws Throwable {
        ReferenceType rt1 = buildBase(stringType);
        ReferenceType rt2 = buildBase(intType);
        assertFalse(rt1.equals(rt2));
    }

    // Covers equals(): same raw class and equal referenced type returns true
    @Test
    public void testEquals_sameRawClassSameReferencedType_returnsTrue() throws Throwable {
        ReferenceType rt1 = buildBase(stringType);
        ReferenceType rt2 = buildBase(stringType);
        assertTrue(rt1.equals(rt2));
    }
}
