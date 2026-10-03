package com.fasterxml.jackson.databind.type;

import org.junit.Test;
import static org.junit.Assert.*;

import java.util.Map;
import java.util.Collection;
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
        SimpleType type = SimpleType.construct(String.class);
        assertNotNull(type);
        assertEquals(String.class, type.getRawClass());
    }

    @Test
    public void testConstructMapThrowsException() throws Throwable {
        boolean thrown = false;
        try {
            SimpleType.construct(Map.class);
        } catch (IllegalArgumentException e) {
            thrown = true;
            assertTrue(e.getMessage().contains("Can not construct SimpleType for a Map"));
        }
        assertTrue(thrown);
    }

    @Test
    public void testConstructCollectionThrowsException() throws Throwable {
        boolean thrown = false;
        try {
            SimpleType.construct(Collection.class);
        } catch (IllegalArgumentException e) {
            thrown = true;
            assertTrue(e.getMessage().contains("Can not construct SimpleType for a Collection"));
        }
        assertTrue(thrown);
    }

    @Test
    public void testConstructArrayThrowsException() throws Throwable {
        boolean thrown = false;
        try {
            SimpleType.construct(String[].class);
        } catch (IllegalArgumentException e) {
            thrown = true;
            assertTrue(e.getMessage().contains("Can not construct SimpleType for an array"));
        }
        assertTrue(thrown);
    }

    @Test
    public void testNarrowSameClass() throws Throwable {
        SimpleType type = SimpleType.construct(String.class);
        JavaType narrowed = type._narrow(String.class);
        assertSame(type, narrowed);
    }

    @Test
    public void testNarrowSubclass() throws Throwable {
        SimpleType type = SimpleType.construct(Number.class);
        JavaType narrowed = type._narrow(Integer.class);
        assertNotNull(narrowed);
        assertEquals(Integer.class, narrowed.getRawClass());
    }

    @Test
    public void testWithContentTypeThrowsException() throws Throwable {
        SimpleType type = SimpleType.construct(String.class);
        boolean thrown = false;
        try {
            type.withContentType(type);
        } catch (IllegalArgumentException e) {
            thrown = true;
            assertTrue(e.getMessage().contains("Simple types have no content types"));
        }
        assertTrue(thrown);
    }

    @Test
    public void testWithTypeHandler() throws Throwable {
        SimpleType type = SimpleType.construct(String.class);
        Object handler = new Object();
        SimpleType updated = type.withTypeHandler(handler);
        assertNotNull(updated);
        assertSame(handler, updated.getTypeHandler());
        assertSame(updated, updated.withTypeHandler(handler));
    }

    @Test
    public void testWithContentTypeHandlerThrowsException() throws Throwable {
        SimpleType type = SimpleType.construct(String.class);
        boolean thrown = false;
        try {
            type.withContentTypeHandler(new Object());
        } catch (IllegalArgumentException e) {
            thrown = true;
            assertTrue(e.getMessage().contains("Simple types have no content types"));
        }
        assertTrue(thrown);
    }

    @Test
    public void testWithValueHandler() throws Throwable {
        SimpleType type = SimpleType.construct(String.class);
        Object handler = new Object();
        SimpleType updated = type.withValueHandler(handler);
        assertNotNull(updated);
        assertSame(handler, updated.getValueHandler());
        assertSame(updated, updated.withValueHandler(handler));
    }

    @Test
    public void testWithContentValueHandlerThrowsException() throws Throwable {
        SimpleType type = SimpleType.construct(String.class);
        boolean thrown = false;
        try {
            type.withContentValueHandler(new Object());
        } catch (IllegalArgumentException e) {
            thrown = true;
            assertTrue(e.getMessage().contains("Simple types have no content types"));
        }
        assertTrue(thrown);
    }

    @Test
    public void testWithStaticTyping() throws Throwable {
        SimpleType type = SimpleType.construct(String.class);
        SimpleType staticType = type.withStaticTyping();
        assertNotNull(staticType);
        assertTrue(staticType.isStaticType());
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

        StringBuilder sbGen = new StringBuilder();
        assertNotNull(type.getGenericSignature(sbGen));
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
        assertFalse(type1.equals("Some String"));
        assertTrue(type1.equals(type2));
        assertFalse(type1.equals(type3));
    }
}