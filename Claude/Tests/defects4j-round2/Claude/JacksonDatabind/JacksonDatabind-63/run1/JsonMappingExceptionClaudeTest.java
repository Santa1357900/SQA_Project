package com.fasterxml.jackson.databind;

import static org.junit.Assert.*;
import org.junit.Test;

import java.io.Closeable;
import java.io.IOException;
import java.io.StringWriter;
import java.util.List;

import com.fasterxml.jackson.core.JsonFactory;
import com.fasterxml.jackson.core.JsonGenerator;
import com.fasterxml.jackson.core.JsonLocation;
import com.fasterxml.jackson.core.JsonParser;

public class JsonMappingExceptionClaudeTest
{
    public static class SamplePojo
    {
        private String name;
        public String getName() { return name; }
        public void setName(String n) { this.name = n; }
    }

    // covers deprecated String-only constructor path
    @Test
    public void testDeprecatedStringConstructor_setsMessage() throws Throwable {
        JsonMappingException jme = new JsonMappingException("plainMsg");
        assertEquals("plainMsg", jme.getMessage());
        assertNull(jme.getProcessor());
    }

    // covers deprecated String+Throwable constructor, sets cause
    @Test
    public void testDeprecatedStringThrowableConstructor_setsMessageAndCause() throws Throwable {
        Throwable cause = new RuntimeException("rootCause");
        JsonMappingException jme = new JsonMappingException("msgWithCause", cause);
        assertEquals("msgWithCause", jme.getMessage());
        assertSame(cause, jme.getCause());
    }

    // covers (Closeable,String) constructor with null processor branch
    @Test
    public void testProcessorMsgConstructor_nullProcessor() throws Throwable {
        JsonMappingException jme = new JsonMappingException((Closeable) null, "hello");
        assertEquals("hello", jme.getMessage());
        assertNull(jme.getProcessor());
    }

    // covers (Closeable,String) constructor with real JsonParser, processor instanceof branch true
    @Test
    public void testProcessorMsgConstructor_withRealJsonParser_setsProcessor() throws Throwable {
        JsonFactory factory = new JsonFactory();
        JsonParser p = factory.createParser("{}");
        JsonMappingException jme = new JsonMappingException(p, "parserMsg");
        assertSame(p, jme.getProcessor());
        assertTrue(jme.getMessage().contains("parserMsg"));
    }

    // covers (Closeable,String,Throwable) constructor, cause is stored
    @Test
    public void testProcessorMsgThrowableConstructor_setsCause() throws Throwable {
        Throwable cause = new IllegalStateException("bad state");
        JsonMappingException jme = new JsonMappingException((Closeable) null, "withCause", cause);
        assertSame(cause, jme.getCause());
        assertTrue(jme.getMessage().contains("withCause"));
        assertNull(jme.getProcessor());
    }

    // covers (Closeable,String,JsonLocation) constructor
    @Test
    public void testProcessorMsgLocationConstructor_setsProcessorAndLocation() throws Throwable {
        JsonFactory factory = new JsonFactory();
        JsonParser p = factory.createParser("{}");
        p.nextToken();
        JsonLocation loc = p.getTokenLocation();
        JsonMappingException jme = new JsonMappingException((Closeable) null, "locMsg", loc);
        assertNull(jme.getProcessor());
        assertTrue(jme.getMessage().contains("locMsg"));
    }

    // covers static factory from(JsonParser,String)
    @Test
    public void testFromJsonParserMsg() throws Throwable {
        JsonFactory factory = new JsonFactory();
        JsonParser p = factory.createParser("{}");
        JsonMappingException jme = JsonMappingException.from(p, "fromParserMsg");
        assertSame(p, jme.getProcessor());
        assertTrue(jme.getMessage().contains("fromParserMsg"));
    }

    // covers static factory from(JsonParser,String,Throwable)
    @Test
    public void testFromJsonParserMsgThrowable() throws Throwable {
        JsonFactory factory = new JsonFactory();
        JsonParser p = factory.createParser("{}");
        Throwable cause = new RuntimeException("innerCause");
        JsonMappingException jme = JsonMappingException.from(p, "fromParserMsg2", cause);
        assertSame(cause, jme.getCause());
        assertTrue(jme.getMessage().contains("fromParserMsg2"));
    }

    // covers static factory from(JsonGenerator,String) -> uses null Throwable internally
    @Test
    public void testFromJsonGeneratorMsg_nullCause() throws Throwable {
        JsonFactory factory = new JsonFactory();
        StringWriter sw = new StringWriter();
        JsonGenerator g = factory.createGenerator(sw);
        JsonMappingException jme = JsonMappingException.from(g, "genMsg");
        assertSame(g, jme.getProcessor());
        assertNull(jme.getCause());
        assertTrue(jme.getMessage().contains("genMsg"));
    }

    // covers static factory from(JsonGenerator,String,Throwable)
    @Test
    public void testFromJsonGeneratorMsgThrowable() throws Throwable {
        JsonFactory factory = new JsonFactory();
        StringWriter sw = new StringWriter();
        JsonGenerator g = factory.createGenerator(sw);
        Throwable cause = new RuntimeException("genCause");
        JsonMappingException jme = JsonMappingException.from(g, "genMsg2", cause);
        assertSame(g, jme.getProcessor());
        assertSame(cause, jme.getCause());
    }

    // covers fromUnexpectedIOE exact message format per Javadoc/String.format contract
    @Test
    public void testFromUnexpectedIOE_exactMessageFormat() throws Throwable {
        IOException ioe = new IOException("disk fail");
        JsonMappingException jme = JsonMappingException.fromUnexpectedIOE(ioe);
        String expected = String.format("Unexpected IOException (of type %s): %s",
                ioe.getClass().getName(), ioe.getMessage());
        assertEquals(expected, jme.getMessage());
    }

    // covers wrapWithPath creating new exception, non-null/non-empty source message
    @Test
    public void testWrapWithPath_newException_withMessage() throws Throwable {
        RuntimeException src = new RuntimeException("original error");
        JsonMappingException result = JsonMappingException.wrapWithPath(src, "fromObj", "fieldX");
        assertNotSame(src, result);
        assertSame(src, result.getCause());
        assertTrue(result.getMessage().contains("original error"));
        assertEquals(1, result.getPath().size());
        assertEquals("fieldX", result.getPath().get(0).getFieldName());
    }

    // covers wrapWithPath placeholder message branch when src message is null
    @Test
    public void testWrapWithPath_newException_nullMessage_usesPlaceholder() throws Throwable {
        RuntimeException src = new RuntimeException();
        JsonMappingException result = JsonMappingException.wrapWithPath(src, "fromObj2", 3);
        assertTrue(result.getMessage().contains("(was java.lang.RuntimeException)"));
        assertEquals(3, result.getPath().get(0).getIndex());
    }

    // covers wrapWithPath augmenting an existing JsonMappingException (same instance returned)
    @Test
    public void testWrapWithPath_existingJsonMappingException_sameInstanceAugmented() throws Throwable {
        JsonMappingException existing = new JsonMappingException("existingMsg");
        JsonMappingException.Reference ref = new JsonMappingException.Reference(new Object(), "outer");
        JsonMappingException result = JsonMappingException.wrapWithPath(existing, ref);
        assertSame(existing, result);
        assertEquals(1, result.getPath().size());
        assertEquals("outer", result.getPath().get(0).getFieldName());
    }

    // covers wrapWithPath(Throwable,Object,int) overload directly
    @Test
    public void testWrapWithPath_indexOverload() throws Throwable {
        RuntimeException src = new RuntimeException("idxErr");
        JsonMappingException result = JsonMappingException.wrapWithPath(src, "fromObj3", 9);
        assertEquals(9, result.getPath().get(0).getIndex());
        assertNull(result.getPath().get(0).getFieldName());
    }

    // covers getPath() returning empty list when no path set
    @Test
    public void testGetPath_emptyWhenNoPath() throws Throwable {
        JsonMappingException jme = new JsonMappingException("noPath");
        assertTrue(jme.getPath().isEmpty());
    }

    // covers getPath() returns unmodifiable list once path exists
    @Test
    public void testGetPath_unmodifiable() throws Throwable {
        JsonMappingException jme = new JsonMappingException("m");
        jme.prependPath(new Object(), "a");
        List<JsonMappingException.Reference> path = jme.getPath();
        try {
            path.add(new JsonMappingException.Reference(new Object()));
            fail("expected UnsupportedOperationException");
        } catch (UnsupportedOperationException expected) { }
    }

    // covers getPathReference(StringBuilder) appends to existing builder and returns same instance
    @Test
    public void testGetPathReference_stringBuilderOverload_appendsAndReturnsSame() throws Throwable {
        JsonMappingException jme = new JsonMappingException("m");
        jme.prependPath(new Object(), "a");
        jme.prependPath(new Object(), "b");
        StringBuilder sb = new StringBuilder("PRE:");
        StringBuilder result = jme.getPathReference(sb);
        assertSame(sb, result);
        String s = sb.toString();
        assertTrue(s.startsWith("PRE:"));
        assertTrue(s.contains("\"b\"->\"a\""));
    }

    // covers getPathReference() no-arg overload
    @Test
    public void testGetPathReference_noArgOverload() throws Throwable {
        JsonMappingException jme = new JsonMappingException("m");
        jme.prependPath(new Object(), "a");
        String ref = jme.getPathReference();
        assertTrue(ref.contains("\"a\""));
    }

    // covers prependPath(Object,String) overload
    @Test
    public void testPrependPath_fieldNameOverload() throws Throwable {
        JsonMappingException jme = new JsonMappingException("m");
        jme.prependPath(new Object(), "fld");
        assertEquals("fld", jme.getPath().get(0).getFieldName());
        assertEquals(-1, jme.getPath().get(0).getIndex());
    }

    // covers prependPath(Object,int) overload
    @Test
    public void testPrependPath_indexOverload() throws Throwable {
        JsonMappingException jme = new JsonMappingException("m");
        jme.prependPath(new Object(), 5);
        assertEquals(5, jme.getPath().get(0).getIndex());
        assertNull(jme.getPath().get(0).getFieldName());
    }

    // covers multiple prependPath calls build reverse-of-call-order chain via addFirst
    @Test
    public void testPrependPath_referenceOverload_multipleCalls_orderReversed() throws Throwable {
        JsonMappingException jme = new JsonMappingException("m");
        jme.prependPath(new Object(), "first");
        jme.prependPath(new Object(), "second");
        jme.prependPath(new Object(), "third");
        List<JsonMappingException.Reference> path = jme.getPath();
        assertEquals(3, path.size());
        assertEquals("third", path.get(0).getFieldName());
        assertEquals("second", path.get(1).getFieldName());
        assertEquals("first", path.get(2).getFieldName());
    }

    // covers MAX_REFS_TO_LIST bound: path length must be capped exactly at MAX_REFS_TO_LIST
    @Test
    public void testPrependPath_boundsAtMaxRefsToList() throws Throwable {
        JsonMappingException jme = new JsonMappingException("m");
        int total = JsonMappingException.MAX_REFS_TO_LIST + 5;
        for (int i = 0; i < total; i++) {
            jme.prependPath(new JsonMappingException.Reference(new Object(), i));
        }
        List<JsonMappingException.Reference> path = jme.getPath();
        assertEquals(JsonMappingException.MAX_REFS_TO_LIST, path.size());
        assertEquals(JsonMappingException.MAX_REFS_TO_LIST - 1, path.get(0).getIndex());
        assertEquals(0, path.get(path.size() - 1).getIndex());
    }

    // covers getProcessor() default null when no processor-bearing constructor used
    @Test
    public void testGetProcessor_defaultNull() throws Throwable {
        JsonMappingException jme = new JsonMappingException("m2");
        assertNull(jme.getProcessor());
    }

    // covers getMessage() when _path is null returns original message unchanged
    @Test
    public void testGetMessage_noPath_returnsOriginal() throws Throwable {
        JsonMappingException jme = new JsonMappingException("plainMsg2");
        assertEquals("plainMsg2", jme.getMessage());
    }

    // covers getMessage() when path set appends reference chain suffix
    @Test
    public void testGetMessage_withPath_appendsChain() throws Throwable {
        JsonMappingException jme = new JsonMappingException("baseMsg");
        jme.prependPath(new Object(), "field1");
        String msg = jme.getMessage();
        assertTrue(msg.startsWith("baseMsg (through reference chain: "));
        assertTrue(msg.endsWith(")"));
        assertTrue(msg.contains("\"field1\""));
    }

    // covers getLocalizedMessage() delegates to same built message as getMessage()
    @Test
    public void testGetLocalizedMessage_matchesGetMessage() throws Throwable {
        JsonMappingException jme = new JsonMappingException("baseMsg2");
        jme.prependPath(new Object(), "f2");
        assertEquals(jme.getMessage(), jme.getLocalizedMessage());
    }

    // covers toString() includes class name and message
    @Test
    public void testToString_includesClassNameAndMessage() throws Throwable {
        JsonMappingException jme = new JsonMappingException("plainMsg3");
        String ts = jme.toString();
        assertTrue(ts.startsWith(jme.getClass().getName() + ": "));
        assertTrue(ts.contains("plainMsg3"));
    }

    // covers Reference(Object) single-arg constructor defaults
    @Test
    public void testReferenceSingleArgConstructor_defaults() throws Throwable {
        JsonMappingException.Reference ref = new JsonMappingException.Reference("fromObj");
        assertEquals("fromObj", ref.getFrom());
        assertNull(ref.getFieldName());
        assertEquals(-1, ref.getIndex());
    }

    // covers Reference(Object,String) null fieldName throws NullPointerException
    @Test
    public void testReferenceFieldNameConstructor_nullFieldName_throwsNPE() throws Throwable {
        try {
            new JsonMappingException.Reference(new Object(), (String) null);
            fail("expected NullPointerException");
        } catch (NullPointerException expected) { }
    }

    // covers Reference(Object,int) constructor stores index, fieldName stays null
    @Test
    public void testReferenceIndexConstructor() throws Throwable {
        JsonMappingException.Reference ref = new JsonMappingException.Reference("fromObj2", 4);
        assertEquals(4, ref.getIndex());
        assertNull(ref.getFieldName());
    }

    // covers getDescription() when fieldName present
    @Test
    public void testReferenceGetDescription_withFieldName() throws Throwable {
        SamplePojo from = new SamplePojo();
        JsonMappingException.Reference ref = new JsonMappingException.Reference(from, "myField");
        String desc = ref.getDescription();
        assertTrue(desc.contains("SamplePojo"));
        assertTrue(desc.contains("\"myField\""));
        assertTrue(desc.endsWith("]"));
    }

    // covers getDescription() when index present (fieldName null, index>=0)
    @Test
    public void testReferenceGetDescription_withIndex() throws Throwable {
        SamplePojo from = new SamplePojo();
        JsonMappingException.Reference ref = new JsonMappingException.Reference(from, 7);
        String desc = ref.getDescription();
        assertTrue(desc.endsWith("[7]"));
    }

    // covers getDescription() when neither fieldName nor valid index present -> "?"
    @Test
    public void testReferenceGetDescription_neitherFieldNorIndex() throws Throwable {
        SamplePojo from = new SamplePojo();
        JsonMappingException.Reference ref = new JsonMappingException.Reference(from);
        String desc = ref.getDescription();
        assertTrue(desc.endsWith("[?]"));
    }

    // covers getDescription() when from is null -> "UNKNOWN" literal
    @Test
    public void testReferenceGetDescription_nullFrom() throws Throwable {
        JsonMappingException.Reference ref = new JsonMappingException.Reference(null, "f");
        assertEquals("UNKNOWN[\"f\"]", ref.getDescription());
    }

    // covers getDescription() when from is a Class instance (instanceof Class branch)
    @Test
    public void testReferenceGetDescription_classFrom() throws Throwable {
        JsonMappingException.Reference ref = new JsonMappingException.Reference(String.class, "field2");
        String desc = ref.getDescription();
        assertTrue(desc.contains("String"));
        assertTrue(desc.contains("\"field2\""));
    }

    // covers toString() delegates to getDescription()
    @Test
    public void testReferenceToString_matchesGetDescription() throws Throwable {
        SamplePojo from = new SamplePojo();
        JsonMappingException.Reference ref = new JsonMappingException.Reference(from, "zField");
        assertEquals(ref.getDescription(), ref.toString());
    }
}
