package com.fasterxml.jackson.databind;

import org.junit.Test;
import static org.junit.Assert.*;

import java.io.Closeable;
import java.io.IOException;
import java.util.List;

public class JsonMappingExceptionTest {

    @Test
    public void testReferenceConstructorsAndGetters() throws Throwable {
        Object fromObj = new Object();
        JsonMappingException.Reference ref1 = new JsonMappingException.Reference();
        ref1.setFieldName("field1");
        ref1.setIndex(5);
        ref1.setDescription("customDesc");
        assertEquals("customDesc", ref1.getDescription());
        assertEquals("field1", ref1.getFieldName());
        assertEquals(5, ref1.getIndex());
        assertNull(ref1.getFrom());

        JsonMappingException.Reference ref2 = new JsonMappingException.Reference(fromObj);
        assertEquals(fromObj, ref2.getFrom());
        assertEquals(-1, ref2.getIndex());
        assertNull(ref2.getFieldName());

        JsonMappingException.Reference ref3 = new JsonMappingException.Reference(fromObj, "myField");
        assertEquals("myField", ref3.getFieldName());
        assertEquals(fromObj, ref3.getFrom());
        assertEquals(-1, ref3.getIndex());

        JsonMappingException.Reference ref4 = new JsonMappingException.Reference(fromObj, 2);
        assertEquals(2, ref4.getIndex());
        assertEquals(fromObj, ref4.getFrom());
        assertNull(ref4.getFieldName());
    }

    @Test
    public void testReferenceNullFieldName() throws Throwable {
        try {
            new JsonMappingException.Reference(new Object(), null);
            fail("Should have thrown NullPointerException");
        } catch (NullPointerException e) {
            assertTrue(e.getMessage().contains("Can not pass null fieldName"));
        }
    }

    @Test
    public void testReferenceGetDescriptionClassAndUnknown() throws Throwable {
        JsonMappingException.Reference refNullFrom = new JsonMappingException.Reference(null, "someField");
        String descNull = refNullFrom.getDescription();
        assertTrue(descNull.contains("UNKNOWN"));

        JsonMappingException.Reference refClassFrom = new JsonMappingException.Reference(String.class, 0);
        String descClass = refClassFrom.getDescription();
        assertTrue(descClass.contains("String"));
        assertTrue(descClass.contains("[0]"));
    }

    @Test
    public void testReferenceToStringAndWriteReplace() throws Throwable {
        JsonMappingException.Reference ref = new JsonMappingException.Reference("testFrom", "testField");
        assertEquals(ref.getDescription(), ref.toString());
        Object replaced = ref.writeReplace();
        assertEquals(ref, replaced);
    }

    @Test
    public void testConstructorsAndPathManagement() throws Throwable {
        JsonMappingException ex1 = new JsonMappingException("test msg");
        assertEquals("test msg", ex1.getMessage());
        assertNull(ex1.getProcessor());
        assertTrue(ex1.getPath().isEmpty());
        assertEquals("com.fasterxml.jackson.databind.JsonMappingException: test msg", ex1.toString());

        JsonMappingException ex2 = new JsonMappingException("test msg", new Throwable("root"));
        assertEquals("test msg", ex2.getMessage());

        JsonMappingException ex3 = new JsonMappingException("test msg", (JsonLocation) null);
        assertEquals("test msg", ex3.getMessage());

        JsonMappingException ex4 = new JsonMappingException("test msg", null, new Throwable("root"));
        assertEquals("test msg", ex4.getMessage());

        Closeable dummyProc = new Closeable() {
            public void close() throws IOException {}
        };
        JsonMappingException ex5 = new JsonMappingException(dummyProc, "msg with proc");
        assertEquals(dummyProc, ex5.getProcessor());

        JsonMappingException ex6 = new JsonMappingException(dummyProc, "msg with proc and cause", new Throwable("cause"));
        assertEquals(dummyProc, ex6.getProcessor());

        JsonMappingException ex7 = new JsonMappingException(dummyProc, "msg with loc", (JsonLocation) null);
        assertEquals(dummyProc, ex7.getProcessor());

        ex1.prependPath(new Object(), "fieldA");
        ex1.prependPath("referrerObj", 1);
        List<JsonMappingException.Reference> path = ex1.getPath();
        assertEquals(2, path.size());
        assertEquals(1, path.get(0).getIndex());
        assertEquals("fieldA", path.get(1).getFieldName());

        StringBuilder sb = new StringBuilder();
        ex1.getPathReference(sb);
        assertTrue(sb.length() > 0);
        assertEquals(sb.toString(), ex1.getPathReference());
    }

    @Test
    public void testFactoryMethodsFrom() throws Throwable {
        JsonMappingException ex1 = JsonMappingException.fromUnexpectedIOE(new IOException("IO error"));
        assertTrue(ex1.getMessage().contains("Unexpected IOException"));

        JsonMappingException exWrapped = JsonMappingException.wrapWithPath(new RuntimeException("runtime err"), "fromObj", "fieldName");
        assertTrue(exWrapped.getMessage().contains("(was java.lang.RuntimeException)"));
        assertEquals(1, exWrapped.getPath().size());

        JsonMappingException exWrapped2 = JsonMappingException.wrapWithPath(exWrapped, new JsonMappingException.Reference("anotherFrom", 3));
        assertEquals(2, exWrapped2.getPath().size());
    }

    @Test
    public void testGetLocalizedMessage() throws Throwable {
        JsonMappingException ex = new JsonMappingException("localized test");
        assertEquals(ex.getMessage(), ex.getLocalizedMessage());
    }

    @Test
    public void testMaxRefsToList() throws Throwable {
        JsonMappingException ex = new JsonMappingException("overflow test");
        for (int i = 0; i < JsonMappingException.MAX_REFS_TO_LIST + 10; i++) {
            ex.prependPath(new Object(), i);
        }
        assertEquals(JsonMappingException.MAX_REFS_TO_LIST, ex.getPath().size());
    }
}