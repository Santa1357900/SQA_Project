package com.fasterxml.jackson.core.json.async;

import org.junit.Test;
import static org.junit.Assert.*;

import java.io.ByteArrayOutputStream;
import java.io.IOException;

import com.fasterxml.jackson.core.JsonToken;
import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.core.io.IOContext;
import com.fasterxml.jackson.core.sym.ByteQuadsCanonicalizer;
import com.fasterxml.jackson.core.util.BufferRecycler;

public class NonBlockingJsonParserTest {

    private NonBlockingJsonParser createParser() {
        BufferRecycler br = new BufferRecycler();
        IOContext ctxt = new IOContext(br, null, false);
        ByteQuadsCanonicalizer sym = ByteQuadsCanonicalizer.createRoot();
        return new NonBlockingJsonParser(ctxt, 0, sym);
    }

    @Test
    public void testNeedMoreInput() throws Throwable {
        NonBlockingJsonParser parser = createParser();
        assertTrue(parser.needMoreInput());
        
        parser.feedInput(new byte[] { ' ' }, 0, 1);
        assertFalse(parser.needMoreInput());
        
        parser.endOfInput();
        assertFalse(parser.needMoreInput());
    }

    @Test
    public void testFeedInputValidation() throws Throwable {
        NonBlockingJsonParser parser = createParser();
        byte[] buf = new byte[] { '{', '}' };
        
        // Feed initial input
        parser.feedInput(buf, 0, 2);
        
        // Try feeding when still having undecoded bytes
        try {
            parser.feedInput(buf, 0, 2);
            fail("Should have thrown exception for undecoded bytes");
        } catch (IOException e) {
            assertTrue(e.getMessage().contains("Still have"));
        }
    }

    @Test
    public void testFeedInputEndBeforeStart() throws Throwable {
        NonBlockingJsonParser parser = createParser();
        byte[] buf = new byte[] { '{', '}' };
        try {
            parser.feedInput(buf, 2, 1);
            fail("Should have thrown exception when end < start");
        } catch (IOException e) {
            assertTrue(e.getMessage().contains("Input end"));
        }
    }

    @Test
    public void testFeedInputAfterClosed() throws Throwable {
        NonBlockingJsonParser parser = createParser();
        parser.endOfInput();
        byte[] buf = new byte[] { '{', '}' };
        try {
            parser.feedInput(buf, 0, 2);
            fail("Should have thrown exception when closed");
        } catch (IOException e) {
            assertTrue(e.getMessage().contains("Already closed"));
        }
    }

    @Test
    public void testReleaseBuffered() throws Throwable {
        NonBlockingJsonParser parser = createParser();
        byte[] buf = new byte[] { 'h', 'e', 'l', 'l', 'o' };
        parser.feedInput(buf, 1, 4); // "ell"
        
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        int released = parser.releaseBuffered(out);
        assertEquals(3, released);
        assertArrayEquals(new byte[] { 'e', 'l', 'l' }, out.toByteArray());
    }

    @Test
    public void testDecodeEscapedUnsupported() throws Throwable {
        NonBlockingJsonParser parser = createParser();
        try {
            java.lang.reflect.Method m = NonBlockingJsonParser.class.getDeclaredMethod("_decodeEscaped");
            m.setAccessible(true);
            m.invoke(parser);
            fail("Should throw internal error");
        } catch (Exception e) {
            // Expected internal exception wrapper
            assertNotNull(e);
        }
    }

    @Test
    public void testNextTokenClosed() throws Throwable {
        NonBlockingJsonParser parser = createParser();
        // Close parser via internal flag if accessible, or test EOF behavior
        parser.endOfInput();
        JsonToken token = parser.nextToken();
        assertEquals(JsonToken.NOT_AVAILABLE, token);
    }

    @Test
    public void testStartDocumentWithBOM() throws Throwable {
        NonBlockingJsonParser parser = createParser();
        byte[] bom = new byte[] { (byte)0xEF, (byte)0xBB, (byte)0xBF, 't', 'r', 'u', 'e' };
        parser.feedInput(bom, 0, bom.length);
        
        // Initially NOT_AVAILABLE or handles BOM
        JsonToken t = parser.nextToken();
        // Feed more bytes if needed, but let's test basic execution flow
        assertNotNull(parser.getNonBlockingInputFeeder());
    }

    @Test
    public void testStartDocumentWhitespace() throws Throwable {
        NonBlockingJsonParser parser = createParser();
        byte[] data = new byte[] { ' ', '\n', '\r', '\t', '1', '2', '3' };
        parser.feedInput(data, 0, data.length);
        JsonToken t = parser.nextToken();
        assertEquals(JsonToken.NOT_AVAILABLE, t);
    }

    @Test
    public void testSimpleTrueToken() throws Throwable {
        NonBlockingJsonParser parser = createParser();
        byte[] data = new byte[] { 't', 'r', 'u', 'e' };
        parser.feedInput(data, 0, data.length);
        JsonToken token = parser.nextToken();
        assertEquals(JsonToken.VALUE_TRUE, token);
    }

    @Test
    public void testSimpleFalseToken() throws Throwable {
        NonBlockingJsonParser parser = createParser();
        byte[] data = new byte[] { 'f', 'a', 'l', 's', 'e' };
        parser.feedInput(data, 0, data.length);
        JsonToken token = parser.nextToken();
        assertEquals(JsonToken.VALUE_FALSE, token);
    }

    @Test
    public void testSimpleNullToken() throws Throwable {
        NonBlockingJsonParser parser = createParser();
        byte[] data = new byte[] { 'n', 'u', 'l', 'l' };
        parser.feedInput(data, 0, data.length);
        JsonToken token = parser.nextToken();
        assertEquals(JsonToken.VALUE_NULL, token);
    }

    @Test
    public void testPositiveIntegerNumber() throws Throwable {
        NonBlockingJsonParser parser = createParser();
        byte[] data = new byte[] { '4', '2' };
        parser.feedInput(data, 0, data.length);
        JsonToken token = parser.nextToken();
        assertEquals(JsonToken.VALUE_NUMBER_INT, token);
        assertEquals(42, parser.getIntValue());
    }

    @Test
    public void testNegativeIntegerNumber() throws Throwable {
        NonBlockingJsonParser parser = createParser();
        byte[] data = new byte[] { '-', '9', '9' };
        parser.feedInput(data, 0, data.length);
        JsonToken token = parser.nextToken();
        assertEquals(JsonToken.VALUE_NUMBER_INT, token);
        assertEquals(-99, parser.getIntValue());
    }

    @Test
    public void testNumberLeadingZero() throws Throwable {
        NonBlockingJsonParser parser = createParser();
        byte[] data = new byte[] { '0' };
        parser.feedInput(data, 0, data.length);
        JsonToken token = parser.nextToken();
        assertEquals(JsonToken.VALUE_NUMBER_INT, token);
        assertEquals(0, parser.getIntValue());
    }

    @Test
    public void testStringToken() throws Throwable {
        NonBlockingJsonParser parser = createParser();
        byte[] data = new byte[] { '"', 'h', 'i', '"' };
        parser.feedInput(data, 0, data.length);
        JsonToken token = parser.nextToken();
        assertEquals(JsonToken.VALUE_STRING, token);
        assertEquals("hi", parser.getText());
    }

    @Test
    public void testArrayScope() throws Throwable {
        NonBlockingJsonParser parser = createParser();
        byte[] data = new byte[] { '[', ']' };
        parser.feedInput(data, 0, data.length);
        assertEquals(JsonToken.START_ARRAY, parser.nextToken());
        assertEquals(JsonToken.END_ARRAY, parser.nextToken());
    }

    @Test
    public void testObjectScope() throws Throwable {
        NonBlockingJsonParser parser = createParser();
        byte[] data = new byte[] { '{', '}' };
        parser.feedInput(data, 0, data.length);
        assertEquals(JsonToken.START_OBJECT, parser.nextToken());
        assertEquals(JsonToken.END_OBJECT, parser.nextToken());
    }

}