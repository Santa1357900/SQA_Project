package com.fasterxml.jackson.databind;

import org.junit.Test;
import static org.junit.Assert.*;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.NoSuchElementException;

import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.core.JsonToken;

public class MappingIteratorTest {

    // Concrete subclass of MappingIterator to test protected constructor and methods
    private static class TestMappingIterator<T> extends MappingIterator<T> {
        public TestMappingIterator(JavaType type, JsonParser p, DeserializationContext ctxt,
                JsonDeserializer<?> deser, boolean managedParser, Object valueToUpdate) {
            super(type, p, ctxt, deser, managedParser, valueToUpdate);
        }

        public T throwNoSuchElementCustom() {
            return _throwNoSuchElement();
        }

        public Object handleMappingExceptionCustom(JsonMappingException e) {
            return _handleMappingException(e);
        }

        public Object handleIOExceptionCustom(IOException e) {
            return _handleIOException(e);
        }
    }

    // Simple Dummy JsonParser for testing behavior
    private static class DummyJsonParser extends JsonParser {
        private JsonToken currentToken;
        private boolean closed = false;
        private boolean expectedStartArray = false;

        public DummyJsonParser(JsonToken initialToken) {
            this.currentToken = initialToken;
        }

        public void setExpectedStartArray(boolean val) {
            this.expectedStartArray = val;
        }

        @Override
        public JsonToken getCurrentToken() {
            return currentToken;
        }

        @Override
        public JsonToken nextToken() throws IOException {
            return null;
        }

        @Override
        public boolean isExpectedStartArrayToken() {
            return expectedStartArray;
        }

        @Override
        public void clearCurrentToken() {
            currentToken = null;
        }

        @Override
        public void close() throws IOException {
            closed = true;
        }

        @Override public ObjectCodec getCodec() { return null; }
        @Override public void setCodec(ObjectCodec c) { }
        @Override public Version version() { return null; }
        @Override public String getCurrentName() throws IOException { return null; }
        @Override public void overrideCurrentName(String name) { }
        @Override public String getText() throws IOException { return null; }
        @Override public char[] getTextCharacters() throws IOException { return null; }
        @Override public int getTextLength() throws IOException { return 0; }
        @Override public int getTextOffset() throws IOException { return 0; }
        @Override public boolean hasTextCharacters() { return false; }
        @Override public Number getNumberValue() throws IOException { return null; }
        @Override public NumberType getNumberType() throws IOException { return null; }
        @Override public int getIntValue() throws IOException { return 0; }
        @Override public long getLongValue() throws IOException { return 0L; }
        @Override public float getFloatValue() throws IOException { return 0f; }
        @Override public double getDoubleValue() throws IOException { return 0d; }
        @Override public com.fasterxml.jackson.core.JsonLocation getTokenLocation() { return null; }
        @Override public com.fasterxml.jackson.core.JsonLocation getCurrentLocation() { return null; }
        @Override public boolean isClosed() { return closed; }
        @Override public boolean hasCurrentToken() { return currentToken != null; }
    }

    @Test
    public void testEmptyIterator() throws Throwable {
        MappingIterator<Object> it = MappingIterator.emptyIterator();
        assertNotNull(it);
        assertFalse(it.hasNext());
        assertNull(it.getParser());
    }

    @Test
    public void testManagedParserStartArray() throws Throwable {
        DummyJsonParser parser = new DummyJsonParser(JsonToken.START_ARRAY);
        parser.setExpectedStartArray(true);
        
        TestMappingIterator<String> it = new TestMappingIterator<String>(
                null, parser, null, null, true, null);
        
        assertNull(parser.getCurrentToken());
    }

    @Test
    public void testHasNextWhenParserNull() throws Throwable {
        TestMappingIterator<String> it = new TestMappingIterator<String>(
                null, null, null, null, false, null);
        assertFalse(it.hasNext());
        assertFalse(it.hasNextValue());
    }

    @Test
    public void testCloseParser() throws Throwable {
        DummyJsonParser parser = new DummyJsonParser(JsonToken.VALUE_STRING);
        TestMappingIterator<String> it = new TestMappingIterator<String>(
                null, parser, null, null, true, null);
        it.close();
        assertTrue(parser.isClosed());
    }

    @Test
    public void testRemoveUnsupported() throws Throwable {
        MappingIterator<Object> it = MappingIterator.emptyIterator();
        boolean caught = false;
        try {
            it.remove();
        } catch (UnsupportedOperationException e) {
            caught = true;
        }
        assertTrue(caught);
    }

    @Test
    public void testThrowNoSuchElement() throws Throwable {
        TestMappingIterator<String> it = new TestMappingIterator<String>(
                null, null, null, null, false, null);
        boolean caught = false;
        try {
            it.throwNoSuchElementCustom();
        } catch (NoSuchElementException e) {
            caught = true;
        }
        assertTrue(caught);
    }

    @Test
    public void testHandleMappingException() throws Throwable {
        TestMappingIterator<String> it = new TestMappingIterator<String>(
                null, null, null, null, false, null);
        JsonMappingException ex = new JsonMappingException("test mapping exception");
        boolean caught = false;
        try {
            it.handleMappingExceptionCustom(ex);
        } catch (RuntimeJsonMappingException e) {
            caught = true;
            assertTrue(e.getMessage().contains("test mapping exception"));
        }
        assertTrue(caught);
    }

    @Test
    public void testHandleIOException() throws Throwable {
        TestMappingIterator<String> it = new TestMappingIterator<String>(
                null, null, null, null, false, null);
        IOException ex = new IOException("test io exception");
        boolean caught = false;
        try {
            it.handleIOExceptionCustom(ex);
        } catch (RuntimeException e) {
            caught = true;
            assertTrue(e.getMessage().contains("test io exception"));
        }
        assertTrue(caught);
    }

    @Test
    public void testReadAllWithCollection() throws Throwable {
        TestMappingIterator<String> it = new TestMappingIterator<String>(
                null, null, null, null, false, null);
        List<String> list = new ArrayList<String>();
        Collection<String> result = it.readAll(list);
        assertNotNull(result);
        assertTrue(result.isEmpty());
    }
}