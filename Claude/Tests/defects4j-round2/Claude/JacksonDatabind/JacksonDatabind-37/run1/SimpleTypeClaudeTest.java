package com.fasterxml.jackson.databind.type;

import org.junit.Test;
import static org.junit.Assert.*;

import java.util.Map;
import java.util.HashMap;
import java.util.Collection;
import java.util.ArrayList;

import com.fasterxml.jackson.databind.JavaType;

public class SimpleTypeClaudeTest
{
    // constructUnsafe: simple class -> not a container type, class field set correctly
    @Test
    public void testConstructUnsafe_simpleClass_notContainerType() throws Throwable {
        SimpleType t = SimpleType.constructUnsafe(String.class);
        assertFalse(t.isContainerType());
        assertEquals(String.class, t._class);
    }

    // constructUnsafe: no validation performed, array class accepted without throwing
    @Test
    public void testConstructUnsafe_arrayClass_noValidation() throws Throwable {
        SimpleType t = SimpleType.constructUnsafe(int[].class);
        assertEquals(int[].class, t._class);
    }

    // construct: normal non-container class succeeds
    @Test
    public void testConstruct_normalClass_success() throws Throwable {
        SimpleType t = SimpleType.construct(Integer.class);
        assertEquals(Integer.class, t._class);
    }

    // construct: Map.class throws IllegalArgumentException mentioning Map
    @Test
    public void testConstruct_mapClass_throwsIllegalArgumentException() throws Throwable {
        try {
            SimpleType.construct(Map.class);
            fail("expected IllegalArgumentException");
        } catch (IllegalArgumentException expected) {
            assertTrue(expected.getMessage().contains("Map"));
        }
    }

    // construct: Map subclass also throws (isAssignableFrom branch)
    @Test
    public void testConstruct_mapSubclass_throwsIllegalArgumentException() throws Throwable {
        try {
            SimpleType.construct(HashMap.class);
            fail("expected IllegalArgumentException");
        } catch (IllegalArgumentException expected) {
            assertTrue(expected.getMessage().contains("Map"));
        }
    }

    // construct: Collection.class throws IllegalArgumentException mentioning Collection
    @Test
    public void testConstruct_collectionClass_throwsIllegalArgumentException() throws Throwable {
        try {
            SimpleType.construct(Collection.class);
            fail("expected IllegalArgumentException");
        } catch (IllegalArgumentException expected) {
            assertTrue(expected.getMessage().contains("Collection"));
        }
    }

    // construct: Collection subclass also throws (isAssignableFrom branch)
    @Test
    public void testConstruct_collectionSubclass_throwsIllegalArgumentException() throws Throwable {
        try {
            SimpleType.construct(ArrayList.class);
            fail("expected IllegalArgumentException");
        } catch (IllegalArgumentException expected) {
            assertTrue(expected.getMessage().contains("Collection"));
        }
    }

    // construct: primitive array class throws IllegalArgumentException mentioning array
    @Test
    public void testConstruct_primitiveArrayClass_throwsIllegalArgumentException() throws Throwable {
        try {
            SimpleType.construct(int[].class);
            fail("expected IllegalArgumentException");
        } catch (IllegalArgumentException expected) {
            assertTrue(expected.getMessage().contains("array"));
        }
    }

    // construct: object array class also throws (isArray branch)
    @Test
    public void testConstruct_objectArrayClass_throwsIllegalArgumentException() throws Throwable {
        try {
            SimpleType.construct(String[].class);
            fail("expected IllegalArgumentException");
        } catch (IllegalArgumentException expected) {
            assertTrue(expected.getMessage().contains("array"));
        }
    }

    // _narrow: same class returns identical instance (this)
    @Test
    public void testNarrow_sameClass_returnsSameInstance() throws Throwable {
        SimpleType t = new SimpleType(Integer.class, TypeBindings.emptyBindings(), null, null);
        JavaType narrowed = t._narrow(Integer.class);
        assertSame(t, narrowed);
    }

    // _narrow: different class returns a new instance with the narrowed class
    @Test
    public void testNarrow_differentClass_returnsNewInstanceWithNewClass() throws Throwable {
        SimpleType t = new SimpleType(Number.class, TypeBindings.emptyBindings(), null, null);
        JavaType narrowed = t._narrow(Integer.class);
        assertNotSame(t, narrowed);
        assertTrue(narrowed instanceof SimpleType);
        assertEquals(Integer.class, ((SimpleType) narrowed)._class);
    }

    // withContentType: always throws IllegalArgumentException, simple types have no content type
    @Test
    public void testWithContentType_alwaysThrows() throws Throwable {
        SimpleType t = new SimpleType(String.class, TypeBindings.emptyBindings(), null, null);
        try {
            t.withContentType(null);
            fail("expected IllegalArgumentException");
        } catch (IllegalArgumentException expected) {
            assertTrue(expected.getMessage().contains("content types"));
        }
    }

    // withTypeHandler: same (null) handler as current returns same instance
    @Test
    public void testWithTypeHandler_sameNullHandler_returnsSameInstance() throws Throwable {
        SimpleType t = new SimpleType(String.class, TypeBindings.emptyBindings(), null, null);
        SimpleType result = t.withTypeHandler(null);
        assertSame(t, result);
    }

    // withTypeHandler: different (non-null) handler creates new instance with handler set
    @Test
    public void testWithTypeHandler_differentHandler_returnsNewInstanceWithHandlerSet() throws Throwable {
        SimpleType t = new SimpleType(String.class, TypeBindings.emptyBindings(), null, null);
        Object handler = new Object();
        SimpleType result = t.withTypeHandler(handler);
        assertNotSame(t, result);
        assertSame(handler, result._typeHandler);
        assertNull(t._typeHandler);
    }

    // withTypeHandler: same non-null handler as current returns same instance
    @Test
    public void testWithTypeHandler_sameNonNullHandler_returnsSameInstance() throws Throwable {
        SimpleType t = new SimpleType(String.class, TypeBindings.emptyBindings(), null, null);
        Object handler = new Object();
        SimpleType t2 = t.withTypeHandler(handler);
        SimpleType t3 = t2.withTypeHandler(handler);
        assertSame(t2, t3);
    }

    // withContentTypeHandler: always throws IllegalArgumentException
    @Test
    public void testWithContentTypeHandler_alwaysThrows() throws Throwable {
        SimpleType t = new SimpleType(String.class, TypeBindings.emptyBindings(), null, null);
        try {
            t.withContentTypeHandler(new Object());
            fail("expected IllegalArgumentException");
        } catch (IllegalArgumentException expected) {
            assertTrue(expected.getMessage().contains("content types"));
        }
    }

    // withValueHandler: same (null) handler as current returns same instance
    @Test
    public void testWithValueHandler_sameNullHandler_returnsSameInstance() throws Throwable {
        SimpleType t = new SimpleType(String.class, TypeBindings.emptyBindings(), null, null);
        SimpleType result = t.withValueHandler(null);
        assertSame(t, result);
    }

    // withValueHandler: different (non-null) handler creates new instance with handler set
    @Test
    public void testWithValueHandler_differentHandler_returnsNewInstanceWithHandlerSet() throws Throwable {
        SimpleType t = new SimpleType(String.class, TypeBindings.emptyBindings(), null, null);
        Object handler = new Object();
        SimpleType result = t.withValueHandler(handler);
        assertNotSame(t, result);
        assertSame(handler, result._valueHandler);
        assertNull(t._valueHandler);
    }

    // withContentValueHandler: always throws IllegalArgumentException
    @Test
    public void testWithContentValueHandler_alwaysThrows() throws Throwable {
        SimpleType t = new SimpleType(String.class, TypeBindings.emptyBindings(), null, null);
        try {
            t.withContentValueHandler(new Object());
            fail("expected IllegalArgumentException");
        } catch (IllegalArgumentException expected) {
            assertTrue(expected.getMessage().contains("content"));
        }
    }

    // withStaticTyping: not yet static, creates a new instance with asStatic true
    @Test
    public void testWithStaticTyping_notStatic_returnsNewStaticInstance() throws Throwable {
        SimpleType t = new SimpleType(String.class, TypeBindings.emptyBindings(), null, null);
        assertFalse(t._asStatic);
        SimpleType result = t.withStaticTyping();
        assertNotSame(t, result);
        assertTrue(result._asStatic);
    }

    // withStaticTyping: already static, returns same instance
    @Test
    public void testWithStaticTyping_alreadyStatic_returnsSameInstance() throws Throwable {
        SimpleType t = new SimpleType(String.class, TypeBindings.emptyBindings(), null, null);
        SimpleType staticType = t.withStaticTyping();
        SimpleType result = staticType.withStaticTyping();
        assertSame(staticType, result);
    }

    // refine: always returns null regardless of arguments (SimpleType is not specialized)
    @Test
    public void testRefine_alwaysReturnsNull() throws Throwable {
        SimpleType t = new SimpleType(String.class, TypeBindings.emptyBindings(), null, null);
        JavaType result = t.refine(String.class, TypeBindings.emptyBindings(), null, null);
        assertNull(result);
    }

    // buildCanonicalName via toString: simple class name reflected in output
    @Test
    public void testBuildCanonicalName_viaToString_simpleClass() throws Throwable {
        SimpleType t = new SimpleType(Integer.class, TypeBindings.emptyBindings(), null, null);
        assertEquals("[simple type, class java.lang.Integer]", t.toString());
    }

    // isContainerType: always false for SimpleType
    @Test
    public void testIsContainerType_alwaysFalse() throws Throwable {
        SimpleType t = new SimpleType(java.util.List.class, TypeBindings.emptyBindings(), null, null);
        assertFalse(t.isContainerType());
    }

    // getErasedSignature: produces a non-empty signature for the underlying class
    @Test
    public void testGetErasedSignature_notNullAndNonEmpty() throws Throwable {
        SimpleType t = new SimpleType(String.class, TypeBindings.emptyBindings(), null, null);
        StringBuilder sb = new StringBuilder();
        StringBuilder result = t.getErasedSignature(sb);
        assertNotNull(result);
        assertTrue(result.length() > 0);
    }

    // getGenericSignature: always terminates with ';' per JVM signature convention
    @Test
    public void testGetGenericSignature_endsWithSemicolon() throws Throwable {
        SimpleType t = new SimpleType(String.class, TypeBindings.emptyBindings(), null, null);
        StringBuilder sb = new StringBuilder();
        StringBuilder result = t.getGenericSignature(sb);
        assertTrue(result.toString().endsWith(";"));
    }

    // equals: same instance reference returns true (o == this branch)
    @Test
    public void testEquals_sameInstance_true() throws Throwable {
        SimpleType t = new SimpleType(String.class, TypeBindings.emptyBindings(), null, null);
        assertTrue(t.equals(t));
    }

    // equals: comparing against null returns false (o == null branch)
    @Test
    public void testEquals_null_false() throws Throwable {
        SimpleType t = new SimpleType(String.class, TypeBindings.emptyBindings(), null, null);
        assertFalse(t.equals(null));
    }

    // equals: comparing against a different runtime type returns false
    @Test
    public void testEquals_differentType_false() throws Throwable {
        SimpleType t = new SimpleType(String.class, TypeBindings.emptyBindings(), null, null);
        assertFalse(t.equals("not a SimpleType"));
    }

    // equals: same underlying class and equal (empty) bindings returns true
    @Test
    public void testEquals_sameClassAndBindings_true() throws Throwable {
        SimpleType t1 = new SimpleType(String.class, TypeBindings.emptyBindings(), null, null);
        SimpleType t2 = new SimpleType(String.class, TypeBindings.emptyBindings(), null, null);
        assertTrue(t1.equals(t2));
    }

    // equals: different underlying class returns false
    @Test
    public void testEquals_differentClass_false() throws Throwable {
        SimpleType t1 = new SimpleType(String.class, TypeBindings.emptyBindings(), null, null);
        SimpleType t2 = new SimpleType(Integer.class, TypeBindings.emptyBindings(), null, null);
        assertFalse(t1.equals(t2));
    }

    // toString: format check for another simple class to cross-validate canonical name building
    @Test
    public void testToString_format() throws Throwable {
        SimpleType t = new SimpleType(String.class, TypeBindings.emptyBindings(), null, null);
        assertEquals("[simple type, class java.lang.String]", t.toString());
    }
}
