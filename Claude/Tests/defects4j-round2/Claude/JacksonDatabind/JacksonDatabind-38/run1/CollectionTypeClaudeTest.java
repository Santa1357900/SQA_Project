package com.fasterxml.jackson.databind.type;

import java.util.List;
import java.util.ArrayList;
import java.util.HashSet;

import org.junit.Before;
import org.junit.Test;
import static org.junit.Assert.*;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.JavaType;

public class CollectionTypeClaudeTest {

    private TypeFactory typeFactory;

    @Before
    public void setUp() throws Throwable {
        typeFactory = new ObjectMapper().getTypeFactory();
    }

    // covers construct(Class, TypeBindings, JavaType, JavaType[], JavaType): raw type and element type set correctly
    @Test
    public void testConstruct_withBindingsAndSuper_fieldsSetCorrectly() throws Throwable {
        CollectionType base = typeFactory.constructCollectionType(List.class, String.class);
        JavaType newElem = typeFactory.constructType(Integer.class);
        CollectionType built = CollectionType.construct(ArrayList.class, base._bindings,
                base._superClass, base._superInterfaces, newElem);
        assertEquals(ArrayList.class, built._class);
        assertSame(newElem, built._elementType);
    }

    // covers construct(...): handlers default to null and asStatic defaults to false
    @Test
    public void testConstruct_withBindingsAndSuper_handlersNullAndNotStatic() throws Throwable {
        CollectionType base = typeFactory.constructCollectionType(List.class, String.class);
        JavaType newElem = typeFactory.constructType(Integer.class);
        CollectionType built = CollectionType.construct(ArrayList.class, base._bindings,
                base._superClass, base._superInterfaces, newElem);
        assertNull(built._valueHandler);
        assertNull(built._typeHandler);
        assertFalse(built._asStatic);
    }

    // covers construct(...): supplied bindings/superClass/superInterfaces references are stored as-is
    @Test
    public void testConstruct_preservesExactBindingsAndSuperClassReferences() throws Throwable {
        CollectionType base = typeFactory.constructCollectionType(List.class, String.class);
        JavaType newElem = typeFactory.constructType(Integer.class);
        CollectionType built = CollectionType.construct(ArrayList.class, base._bindings,
                base._superClass, base._superInterfaces, newElem);
        assertSame(base._bindings, built._bindings);
        assertSame(base._superClass, built._superClass);
        assertSame(base._superInterfaces, built._superInterfaces);
    }

    // covers deprecated construct(Class, JavaType): raw type and element type set, handlers null, not static
    @Test
    public void testConstructDeprecated_twoArg_fieldsSetCorrectly() throws Throwable {
        JavaType elemT = typeFactory.constructType(String.class);
        CollectionType built = CollectionType.construct(List.class, elemT);
        assertEquals(List.class, built._class);
        assertSame(elemT, built._elementType);
        assertNull(built._valueHandler);
        assertNull(built._typeHandler);
        assertFalse(built._asStatic);
    }

    // covers deprecated construct(Class, JavaType): bindings null, bogus superclass fabricated, superInterfaces null
    @Test
    public void testConstructDeprecated_twoArg_bindingsNullSuperClassNotNull() throws Throwable {
        JavaType elemT = typeFactory.constructType(String.class);
        CollectionType built = CollectionType.construct(List.class, elemT);
        assertNull(built._bindings);
        assertNotNull(built._superClass);
        assertNull(built._superInterfaces);
    }

    // covers _narrow(Class): narrowing raw class must preserve element type and existing handlers
    @Test
    public void testNarrow_changesRawClass_preservesElementTypeAndHandlers() throws Throwable {
        CollectionType base = typeFactory.constructCollectionType(List.class, String.class)
                .withValueHandler("VH").withTypeHandler("TH");
        JavaType narrowed = base._narrow(ArrayList.class);
        CollectionType nct = (CollectionType) narrowed;
        assertEquals(ArrayList.class, nct._class);
        assertSame(base._elementType, nct._elementType);
        assertEquals("VH", nct._valueHandler);
        assertEquals("TH", nct._typeHandler);
    }

    // covers _narrow(Class): result is always a new instance, never the original
    @Test
    public void testNarrow_returnsNewInstance_notSameAsOriginal() throws Throwable {
        CollectionType base = typeFactory.constructCollectionType(List.class, String.class);
        JavaType narrowed = base._narrow(ArrayList.class);
        assertNotSame(base, narrowed);
        assertEquals(ArrayList.class, ((CollectionType) narrowed)._class);
    }

    // covers withContentType: identity branch when contentType reference equals current elementType
    @Test
    public void testWithContentType_sameElementType_returnsSameInstance() throws Throwable {
        CollectionType base = typeFactory.constructCollectionType(List.class, String.class);
        JavaType same = base._elementType;
        JavaType result = base.withContentType(same);
        assertSame(base, result);
    }

    // covers withContentType: different element type produces new instance with updated content, class/asStatic preserved
    @Test
    public void testWithContentType_differentElementType_returnsNewInstanceWithUpdatedContent() throws Throwable {
        CollectionType base = typeFactory.constructCollectionType(List.class, String.class).withStaticTyping();
        JavaType newElem = typeFactory.constructType(Integer.class);
        JavaType result = base.withContentType(newElem);
        assertNotSame(base, result);
        CollectionType rct = (CollectionType) result;
        assertSame(newElem, rct._elementType);
        assertEquals(base._class, rct._class);
        assertEquals(base._asStatic, rct._asStatic);
    }

    // covers withContentType: calling again with the now-current element type hits the identity branch
    @Test
    public void testWithContentType_calledTwiceWithSameNewType_returnsSameInstanceSecondTime() throws Throwable {
        CollectionType base = typeFactory.constructCollectionType(List.class, String.class);
        JavaType newElem = typeFactory.constructType(Integer.class);
        JavaType first = base.withContentType(newElem);
        JavaType second = first.withContentType(newElem);
        assertSame(first, second);
    }

    // covers withTypeHandler: sets typeHandler while preserving valueHandler
    @Test
    public void testWithTypeHandler_setsTypeHandlerPreservesValueHandler() throws Throwable {
        CollectionType base = typeFactory.constructCollectionType(List.class, String.class).withValueHandler("VH");
        CollectionType result = base.withTypeHandler("TH");
        assertEquals("TH", result._typeHandler);
        assertEquals("VH", result._valueHandler);
    }

    // covers withTypeHandler: always constructs a new instance (no shortcut check)
    @Test
    public void testWithTypeHandler_alwaysReturnsNewInstance() throws Throwable {
        CollectionType base = typeFactory.constructCollectionType(List.class, String.class);
        CollectionType result = base.withTypeHandler("TH");
        assertNotSame(base, result);
    }

    // covers withContentTypeHandler: own valueHandler/typeHandler unchanged, new instance produced
    @Test
    public void testWithContentTypeHandler_ownHandlersUnchanged_newInstanceCreated() throws Throwable {
        CollectionType base = typeFactory.constructCollectionType(List.class, String.class)
                .withValueHandler("VH").withTypeHandler("TH");
        CollectionType result = base.withContentTypeHandler("CTH");
        assertNotSame(base, result);
        assertEquals("VH", result._valueHandler);
        assertEquals("TH", result._typeHandler);
    }

    // covers withValueHandler: sets valueHandler while preserving typeHandler
    @Test
    public void testWithValueHandler_setsValueHandlerPreservesTypeHandler() throws Throwable {
        CollectionType base = typeFactory.constructCollectionType(List.class, String.class).withTypeHandler("TH");
        CollectionType result = base.withValueHandler("VH");
        assertEquals("VH", result._valueHandler);
        assertEquals("TH", result._typeHandler);
    }

    // covers withValueHandler: always constructs a new instance
    @Test
    public void testWithValueHandler_alwaysReturnsNewInstance() throws Throwable {
        CollectionType base = typeFactory.constructCollectionType(List.class, String.class);
        CollectionType result = base.withValueHandler("VH");
        assertNotSame(base, result);
    }

    // covers withContentValueHandler: own valueHandler/typeHandler unchanged, new instance produced
    @Test
    public void testWithContentValueHandler_ownHandlersUnchanged_newInstanceCreated() throws Throwable {
        CollectionType base = typeFactory.constructCollectionType(List.class, String.class)
                .withValueHandler("VH").withTypeHandler("TH");
        CollectionType result = base.withContentValueHandler("CVH");
        assertNotSame(base, result);
        assertEquals("VH", result._valueHandler);
        assertEquals("TH", result._typeHandler);
    }

    // covers withStaticTyping: identity branch when already static
    @Test
    public void testWithStaticTyping_alreadyStatic_returnsSameInstance() throws Throwable {
        CollectionType staticBase = typeFactory.constructCollectionType(List.class, String.class).withStaticTyping();
        assertTrue(staticBase._asStatic);
        CollectionType result = staticBase.withStaticTyping();
        assertSame(staticBase, result);
    }

    // covers withStaticTyping: not static -> new instance with asStatic true
    @Test
    public void testWithStaticTyping_notStatic_returnsNewInstanceWithAsStaticTrue() throws Throwable {
        CollectionType base = typeFactory.constructCollectionType(List.class, String.class);
        assertFalse(base._asStatic);
        CollectionType result = base.withStaticTyping();
        assertNotSame(base, result);
        assertTrue(result._asStatic);
    }

    // covers withStaticTyping: handlers and raw class preserved across the transformation
    @Test
    public void testWithStaticTyping_preservesHandlersAndClass() throws Throwable {
        CollectionType base = typeFactory.constructCollectionType(List.class, String.class)
                .withValueHandler("VH").withTypeHandler("TH");
        CollectionType result = base.withStaticTyping();
        assertEquals("VH", result._valueHandler);
        assertEquals("TH", result._typeHandler);
        assertEquals(base._class, result._class);
    }

    // covers refine(Class, TypeBindings, JavaType, JavaType[]): raw type updated, element type and handlers preserved
    @Test
    public void testRefine_changesRawTypeBindingsSuper_preservesElementTypeAndHandlers() throws Throwable {
        CollectionType base = typeFactory.constructCollectionType(List.class, String.class)
                .withValueHandler("VH").withTypeHandler("TH");
        JavaType refined = base.refine(ArrayList.class, base._bindings, base._superClass, base._superInterfaces);
        CollectionType rct = (CollectionType) refined;
        assertEquals(ArrayList.class, rct._class);
        assertSame(base._elementType, rct._elementType);
        assertEquals("VH", rct._valueHandler);
        assertEquals("TH", rct._typeHandler);
    }

    // covers refine(...): null superInterfaces argument is stored as null
    @Test
    public void testRefine_withNullSuperInterfaces_fieldSetToNull() throws Throwable {
        CollectionType base = typeFactory.constructCollectionType(List.class, String.class);
        JavaType[] nullArray = null;
        JavaType refined = base.refine(ArrayList.class, base._bindings, base._superClass, nullArray);
        CollectionType rct = (CollectionType) refined;
        assertNull(rct._superInterfaces);
        assertEquals(ArrayList.class, rct._class);
    }

    // covers toString(): format contains raw class name and "contains" section, wrapped in brackets
    @Test
    public void testToString_containsClassNameAndElementType() throws Throwable {
        CollectionType type = typeFactory.constructCollectionType(List.class, String.class);
        String s = type.toString();
        assertTrue(s.startsWith("[collection type; class " + List.class.getName() + ", contains "));
        assertTrue(s.endsWith("]"));
    }

    // covers toString(): different raw class is reflected in the produced string
    @Test
    public void testToString_differentRawClass_reflectsClassName() throws Throwable {
        CollectionType type = typeFactory.constructCollectionType(ArrayList.class, Integer.class);
        String s = type.toString();
        assertTrue(s.contains(ArrayList.class.getName()));
    }

    // covers constructing a Set-based CollectionType: raw class recorded, element type present
    @Test
    public void testConstructCollectionType_setOfLong_rawClassMatches() throws Throwable {
        CollectionType type = typeFactory.constructCollectionType(HashSet.class, Long.class);
        assertEquals(HashSet.class, type._class);
        assertNotNull(type._elementType);
    }

    // covers constructing nested collection type: element type reference is exactly the inner JavaType supplied
    @Test
    public void testConstructCollectionType_nestedList_elementTypeIsSameReference() throws Throwable {
        JavaType innerListType = typeFactory.constructCollectionType(List.class, String.class);
        CollectionType outer = typeFactory.constructCollectionType(List.class, innerListType);
        assertSame(innerListType, outer._elementType);
        assertEquals(List.class, outer._class);
    }

    // covers chained with* calls: each transformation's effect composes independently and correctly
    @Test
    public void testChainedWithMethods_preserveIndependentState() throws Throwable {
        CollectionType base = typeFactory.constructCollectionType(List.class, String.class);
        CollectionType chained = base.withValueHandler("VH").withTypeHandler("TH").withStaticTyping();
        assertEquals("VH", chained._valueHandler);
        assertEquals("TH", chained._typeHandler);
        assertTrue(chained._asStatic);
        assertEquals(base._class, chained._class);
    }
}
