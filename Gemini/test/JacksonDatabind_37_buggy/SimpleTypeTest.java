package com.fasterxml.jackson.databind.type;

import org.junit.Test;
import static org.junit.Assert.*;

import java.util.Map;
import java.util.Collection;
import java.util.HashMap;
import java.util.ArrayList;

import com.fasterxml.jackson.databind.JavaType;

public class SimpleTypeTest {

    @Test
    public void testConstructUnsafe() throws Throwable {
        SimpleType type = SimpleType.constructUnsafe(String.class);
        assertNotNull(type);
        assertEquals(String.class, type.getRawClass());
        assertFalse(type.isContainerType());
    }

    @Test
    public void testConstructValid() throws Throwable {
        SimpleType type = SimpleType.construct(Integer.class);
        assertNotNull(type);
        assertEquals(Integer.class, type.getRawClass());
    }

    @Test
    public void testConstructMapThrows() throws Throwable {
        try {
            SimpleType.construct(Map.class);
            fail("Expected IllegalArgumentException for Map class");
        } catch (IllegalArgumentException e) {
            assertTrue(e.getMessage().contains("Can not construct SimpleType for a Map"));
        }
    }

    @Test
    public void testConstructCollectionThrows() throws Throwable {
        try {
            SimpleType.construct(Collection.class);
            fail("Expected IllegalArgumentException for Collection class");
        } catch (IllegalArgumentException e) {
            assertTrue(e.getMessage().contains("Can not construct SimpleType for a Collection"));
        }
    }

    @Test
    public void testConstructArrayThrows() throws Throwable {
        try {
            SimpleType.construct(String[].class);
            fail("Expected IllegalArgumentException for array class");
        } catch (IllegalArgumentException e) {
            assertTrue(e.getMessage().contains("Can not construct SimpleType for an array"));
        }
    }

    @Test
    public void testNarrowSameClass() throws Throwable {
        SimpleType type = SimpleType.construct(String.class);
        JavaType narrowed = type._narrow(String.class);
        assertSame(type, narrowed);
    }

    @Test
    public void testNarrowSubClass() throws Throwable {
        SimpleType type = SimpleType.construct(Number.class);
        JavaType narrowed = type._narrow(Integer.class);
        assertNotNull(narrowed);
        assertEquals(Integer.class, narrowed.getRawClass());
    }

    @Test
    public void testWithContentTypeThrows() throws Throwable {
        SimpleType type = SimpleType.construct(String.class);
        try {
            type.withContentType(SimpleType.construct(Integer.class));
            fail("Expected IllegalArgumentException");
        } catch (IllegalArgumentException e) {
            assertTrue(e.getMessage().contains("Simple types have no content types"));
        }
    }

    @Test
    public void testWithContentTypeHandlerThrows() throws Throwable {
        SimpleType type = SimpleType.construct(String.class);
        try {
            type.withContentTypeHandler(new Object());
            fail("Expected IllegalArgumentException");
        } catch (IllegalArgumentException e) {
            assertTrue(e.getMessage().contains("Simple types have no content types"));
        }
    }

    @Test
    public void testWithContentValueHandlerThrows() throws Throwable {
        SimpleType type = SimpleType.construct(String.class);
        try {
            type.withContentValueHandler(new Object());
            fail("Expected IllegalArgumentException");
        } catch (IllegalArgumentException e) {
            assertTrue(e.getMessage().contains("Simple types have no content types"));
        }
    }

    @Test
    public void testWithTypeHandler() throws Throwable {
        SimpleType type = SimpleType.construct(String.class);
        Object handler = new Object();
        SimpleType updated = type.withTypeHandler(handler);
        assertNotNull(updated);
        assertSame(type, type.withTypeHandler(null)); // assuming default null handler or same reference check
    }

    @Test
    public void testWithValueHandler() throws Throwable {
        SimpleType type = SimpleType.construct(String.class);
        Object handler = new Object();
        SimpleType updated = type.withValueHandler(handler);
        assertNotNull(updated);
        assertSame(type, type.withValueHandler(null));
    }

    @Test
    public void testWithStaticTyping() throws Throwable {
        SimpleType type = SimpleType.construct(String.class);
        SimpleType staticType = type.withStaticTyping();
        assertNotNull(staticType);
        assertSame(staticType, staticType.withStaticTyping());
    }

    @Test
    public void testRefine() throws Throwable {
        SimpleType type = SimpleType.construct(String.class);
        JavaType refined = type.refine(String.class, TypeBindings.emptyBindings(), null, null);
        assertNull(refined);
    }

    @Test
    public void testBuildCanonicalNameAndSignatures() throws Throwable {
        SimpleType type = SimpleType.construct(String.class);
        String canonical = type.toCanonical();
        assertNotNull(canonical);
        assertTrue(canonical.contains("java.lang.String"));

        StringBuilder sb = new StringBuilder();
        assertNotNull(type.getErasedSignature(sb));

        StringBuilder sb2 = new StringBuilder();
        assertNotNull(type.getGenericSignature(sb2));
    }

    @Test
    public void testToString() throws Throwable {
        SimpleType type = SimpleType.construct(String.class);
        String str = type.toString();
        assertNotNull(str);
        assertTrue(str.contains("simple type"));
    }

    @Test
    public void testEqualsAndHashCode() throws Throwable {
        SimpleType type1 = SimpleType.construct(String.class);
        SimpleType type2 = SimpleType.construct(String.class);
        SimpleType type3 = SimpleType.construct(Integer.class);

        assertTrue(type1.equals(type1));
        assertFalse(type1.equals(null));
        assertFalse(type1.equals("some string"));
        assertTrue(type1.equals(type2));
        assertFalse(type1.equals(type3));
    }
}