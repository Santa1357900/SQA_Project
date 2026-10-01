package com.fasterxml.jackson.databind.util;

import org.junit.Test;
import static org.junit.Assert.*;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.math.BigDecimal;
import java.math.BigInteger;

import com.fasterxml.jackson.core.Base64Variant;
import com.fasterxml.jackson.core.Base64Variants;
import com.fasterxml.jackson.core.JsonGenerator;
import com.fasterxml.jackson.core.JsonLocation;
import com.fasterxml.jackson.core.JsonParseException;
import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.core.JsonToken;
import com.fasterxml.jackson.core.ObjectCodec;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;

public class TokenBufferClaudeTest {

    // Constructors: deprecated 1-arg, 2-arg with native ids, from JsonParser
    @Test
    public void testConstructors_variousForms_setExpectedNativeIdFlagsAndCodec() throws Throwable {
        TokenBuffer deprecatedCtor = new TokenBuffer((ObjectCodec) null);
        assertFalse(deprecatedCtor.canWriteTypeId());
        assertFalse(deprecatedCtor.canWriteObjectId());

        TokenBuffer withIds = new TokenBuffer((ObjectCodec) null, true);
        assertTrue(withIds.canWriteTypeId());
        assertTrue(withIds.canWriteObjectId());

        JsonParser srcParser = withIds.asParser();
        TokenBuffer fromParser = new TokenBuffer(srcParser);
        assertTrue(fromParser.canWriteTypeId());
        assertTrue(fromParser.canWriteObjectId());
        assertNull(fromParser.getCodec());
    }

    // version() must return a non-null Version
    @Test
    public void testVersion_returnsNonNullVersion() throws Throwable {
        TokenBuffer buf = new TokenBuffer((ObjectCodec) null, false);
        assertNotNull(buf.version());
    }

    // firstToken() on empty buffer branch: _first.type(0) with no tokens set
    @Test
    public void testFirstToken_emptyBuffer_returnsNull() throws Throwable {
        TokenBuffer buf = new TokenBuffer((ObjectCodec) null, false);
        assertNull(buf.firstToken());
    }

    // firstToken() after a write reflects first appended token
    @Test
    public void testFirstToken_afterWrite_returnsFirstTokenType() throws Throwable {
        TokenBuffer buf = new TokenBuffer((ObjectCodec) null, false);
        buf.writeStartObject();
        buf.writeEndObject();
        assertEquals(JsonToken.START_OBJECT, buf.firstToken());
    }

    // asParser(ObjectCodec) uses the explicitly given codec
    @Test
    public void testAsParserWithExplicitCodec_usesGivenCodec() throws Throwable {
        TokenBuffer buf = new TokenBuffer((ObjectCodec) null, false);
        ObjectMapper mapper = new ObjectMapper();
        JsonParser p = buf.asParser(mapper);
        assertSame(mapper, p.getCodec());
    }

    // asParser(JsonParser) copies token location from source parser
    @Test
    public void testAsParserWithSourceParser_copiesLocationFromSource() throws Throwable {
        TokenBuffer srcBuf = new TokenBuffer((ObjectCodec) null, false);
        srcBuf.writeString("s");
        JsonParser srcParser = srcBuf.asParser();
        srcParser.nextToken();
        TokenBuffer buf = new TokenBuffer((ObjectCodec) null, false);
        buf.writeString("v");
        JsonParser p = buf.asParser(srcParser);
        assertEquals(JsonLocation.NA, p.getTokenLocation());
    }

    // append(TokenBuffer) appends the other buffer's contents in order after own contents
    @Test
    public void testAppend_appendsOtherBufferContentsInOrder() throws Throwable {
        TokenBuffer src = new TokenBuffer((ObjectCodec) null, false);
        src.writeStartArray();
        src.writeNumber(1);
        src.writeEndArray();
        TokenBuffer dest = new TokenBuffer((ObjectCodec) null, false);
        dest.writeString("x");
        dest.append(src);
        JsonParser p = dest.asParser();
        assertEquals(JsonToken.VALUE_STRING, p.nextToken());
        assertEquals(JsonToken.START_ARRAY, p.nextToken());
        assertEquals(JsonToken.VALUE_NUMBER_INT, p.nextToken());
        assertEquals(1, p.getIntValue());
        assertEquals(JsonToken.END_ARRAY, p.nextToken());
        assertNull(p.nextToken());
    }

    // serialize(JsonGenerator) replays all tokens onto given generator
    @Test
    public void testSerialize_writesAllTokensToAnotherGenerator() throws Throwable {
        TokenBuffer src = new TokenBuffer((ObjectCodec) null, false);
        src.writeStartObject();
        src.writeFieldName("a");
        src.writeNumber(1);
        src.writeEndObject();
        TokenBuffer dest = new TokenBuffer((ObjectCodec) null, false);
        src.serialize(dest);
        JsonParser p = dest.asParser();
        assertEquals(JsonToken.START_OBJECT, p.nextToken());
        assertEquals(JsonToken.FIELD_NAME, p.nextToken());
        assertEquals("a", p.getCurrentName());
        assertEquals(JsonToken.VALUE_NUMBER_INT, p.nextToken());
        assertEquals(1, p.getIntValue());
        assertEquals(JsonToken.END_OBJECT, p.nextToken());
    }

    // toString() prefix and token name presence
    @Test
    public void testToString_containsPrefixAndTokenNames() throws Throwable {
        TokenBuffer buf = new TokenBuffer((ObjectCodec) null, false);
        buf.writeStartArray();
        buf.writeNumber(1);
        buf.writeEndArray();
        String s = buf.toString();
        assertTrue(s.startsWith("[TokenBuffer: "));
        assertTrue(s.indexOf("START_ARRAY") >= 0);
        assertTrue(s.indexOf("END_ARRAY") >= 0);
    }

    // enable/disable/isEnabled and setFeatureMask/getFeatureMask
    @Test
    public void testFeatureFlagsEnableDisableAndMask() throws Throwable {
        TokenBuffer buf = new TokenBuffer((ObjectCodec) null, false);
        JsonGenerator.Feature f = JsonGenerator.Feature.values()[0];
        buf.disable(f);
        assertFalse(buf.isEnabled(f));
        buf.enable(f);
        assertTrue(buf.isEnabled(f));
        buf.setFeatureMask(0);
        assertEquals(0, buf.getFeatureMask());
    }

    // useDefaultPrettyPrinter returns same instance; setCodec/getCodec round trip
    @Test
    public void testConfigurationMethods_prettyPrinterAndCodecRoundTrip() throws Throwable {
        TokenBuffer buf = new TokenBuffer((ObjectCodec) null, false);
        assertNull(buf.getCodec());
        JsonGenerator ret = buf.useDefaultPrettyPrinter();
        assertSame(buf, ret);
        ObjectMapper mapper = new ObjectMapper();
        buf.setCodec(mapper);
        assertSame(mapper, buf.getCodec());
    }

    // canWriteBinaryNatively, flush no-op, close/isClosed lifecycle
    @Test
    public void testBasicLifecycleAndCapability_flushCloseCanWriteBinary() throws Throwable {
        TokenBuffer buf = new TokenBuffer((ObjectCodec) null, false);
        assertTrue(buf.canWriteBinaryNatively());
        buf.flush();
        assertFalse(buf.isClosed());
        buf.close();
        assertTrue(buf.isClosed());
    }

    // writeStartArray/writeEndArray and writeStartObject/writeEndObject update output context nesting
    @Test
    public void testWriteStartEndArrayAndObject_updatesContextNesting() throws Throwable {
        TokenBuffer buf = new TokenBuffer((ObjectCodec) null, false);
        buf.writeStartArray();
        assertNotNull(buf.getOutputContext().getParent());
        buf.writeEndArray();
        assertNull(buf.getOutputContext().getParent());
        buf.writeStartObject();
        assertNotNull(buf.getOutputContext().getParent());
        buf.writeEndObject();
        assertNull(buf.getOutputContext().getParent());
    }

    // writeEndArray at root (unbalanced) must not throw and keeps root context
    @Test
    public void testWriteEndArray_unbalancedAtRoot_doesNotThrowAndKeepsRootContext() throws Throwable {
        TokenBuffer buf = new TokenBuffer((ObjectCodec) null, false);
        buf.writeEndArray();
        assertNull(buf.getOutputContext().getParent());
        assertEquals(JsonToken.END_ARRAY, buf.firstToken());
    }

    // writeFieldName(String) updates parsing context current name
    @Test
    public void testWriteFieldName_updatesParserCurrentName() throws Throwable {
        TokenBuffer buf = new TokenBuffer((ObjectCodec) null, false);
        buf.writeStartObject();
        buf.writeFieldName("key1");
        buf.writeString("v");
        buf.writeEndObject();
        JsonParser p = buf.asParser();
        p.nextToken();
        assertEquals(JsonToken.FIELD_NAME, p.nextToken());
        assertEquals("key1", p.getCurrentName());
    }

    // writeString(null) delegates to writeNull()
    @Test
    public void testWriteStringNull_writesNullToken() throws Throwable {
        TokenBuffer buf = new TokenBuffer((ObjectCodec) null, false);
        buf.writeString((String) null);
        JsonParser p = buf.asParser();
        assertEquals(JsonToken.VALUE_NULL, p.nextToken());
    }

    // writeString(char[], offset, len) produces correct substring text
    @Test
    public void testWriteStringCharArray_producesExpectedText() throws Throwable {
        TokenBuffer buf = new TokenBuffer((ObjectCodec) null, false);
        char[] chars = new char[] {'h', 'e', 'l', 'l', 'o', 'X'};
        buf.writeString(chars, 0, 5);
        JsonParser p = buf.asParser();
        p.nextToken();
        assertEquals("hello", p.getText());
    }

    // writeRaw*/writeRawValue*/writeRawUTF8String/writeUTF8String all throw UnsupportedOperationException
    @Test
    public void testUnsupportedOperations_allThrowUnsupportedOperationException() throws Throwable {
        TokenBuffer buf = new TokenBuffer((ObjectCodec) null, false);
        char[] chars = new char[] {'a', 'b'};
        byte[] bytes = new byte[] {1, 2};
        int failures = 0;
        try { buf.writeRaw("x"); } catch (UnsupportedOperationException e) { failures++; }
        try { buf.writeRaw("x", 0, 1); } catch (UnsupportedOperationException e) { failures++; }
        try { buf.writeRaw('c'); } catch (UnsupportedOperationException e) { failures++; }
        try { buf.writeRaw(chars, 0, 2); } catch (UnsupportedOperationException e) { failures++; }
        try { buf.writeRawValue("x"); } catch (UnsupportedOperationException e) { failures++; }
        try { buf.writeRawValue("x", 0, 1); } catch (UnsupportedOperationException e) { failures++; }
        try { buf.writeRawValue(chars, 0, 2); } catch (UnsupportedOperationException e) { failures++; }
        try { buf.writeRawUTF8String(bytes, 0, 2); } catch (UnsupportedOperationException e) { failures++; }
        try { buf.writeUTF8String(bytes, 0, 2); } catch (UnsupportedOperationException e) { failures++; }
        assertEquals(9, failures);
    }

    // writeNumber(short/int/long) stores correct primitive values
    @Test
    public void testWriteNumberShortIntLong_storesCorrectValues() throws Throwable {
        TokenBuffer buf = new TokenBuffer((ObjectCodec) null, false);
        buf.writeNumber((short) 5);
        buf.writeNumber(100);
        buf.writeNumber(123456789012L);
        JsonParser p = buf.asParser();
        p.nextToken(); assertEquals(5, p.getIntValue());
        p.nextToken(); assertEquals(100, p.getIntValue());
        p.nextToken(); assertEquals(123456789012L, p.getLongValue());
    }

    // writeNumber(double/float) stores correct floating point values
    @Test
    public void testWriteNumberDoubleFloat_storesCorrectValues() throws Throwable {
        TokenBuffer buf = new TokenBuffer((ObjectCodec) null, false);
        buf.writeNumber(3.5d);
        buf.writeNumber(2.25f);
        JsonParser p = buf.asParser();
        p.nextToken(); assertEquals(3.5d, p.getDoubleValue(), 1e-9);
        p.nextToken(); assertEquals(2.25f, p.getFloatValue(), 1e-6f);
    }

    // writeNumber(null BigDecimal/BigInteger) writes null; non-null round trips correctly
    @Test
    public void testWriteNumberBigDecimalAndBigInteger_nullAndValueRoundTrip() throws Throwable {
        TokenBuffer nullsBuf = new TokenBuffer((ObjectCodec) null, false);
        nullsBuf.writeNumber((BigDecimal) null);
        nullsBuf.writeNumber((BigInteger) null);
        JsonParser np = nullsBuf.asParser();
        assertEquals(JsonToken.VALUE_NULL, np.nextToken());
        assertEquals(JsonToken.VALUE_NULL, np.nextToken());

        TokenBuffer buf = new TokenBuffer((ObjectCodec) null, false);
        buf.writeNumber(new BigDecimal("1.5"));
        buf.writeNumber(BigInteger.valueOf(999));
        JsonParser p = buf.asParser();
        p.nextToken(); assertEquals(new BigDecimal("1.5"), p.getDecimalValue());
        p.nextToken(); assertEquals(BigInteger.valueOf(999), p.getBigIntegerValue());
    }

    // writeNumber(String) without a dot: encoded value parsed as Long by getNumberValue
    @Test
    public void testWriteNumberEncodedStringWithoutDot_parsedAsLong() throws Throwable {
        TokenBuffer buf = new TokenBuffer((ObjectCodec) null, false);
        buf.writeNumber("123");
        JsonParser p = buf.asParser();
        assertEquals(JsonToken.VALUE_NUMBER_FLOAT, p.nextToken());
        assertEquals(123L, p.getLongValue());
    }

    // writeNumber(String) with a dot: encoded value parsed as Double by getNumberValue
    @Test
    public void testWriteNumberEncodedStringWithDot_parsedAsDouble() throws Throwable {
        TokenBuffer buf = new TokenBuffer((ObjectCodec) null, false);
        buf.writeNumber("12.5");
        JsonParser p = buf.asParser();
        p.nextToken();
        assertEquals(12.5d, p.getDoubleValue(), 1e-9);
    }

    // writeBoolean(true/false) produces matching tokens
    @Test
    public void testWriteBoolean_trueFalseProduceCorrectTokens() throws Throwable {
        TokenBuffer buf = new TokenBuffer((ObjectCodec) null, false);
        buf.writeBoolean(true);
        buf.writeBoolean(false);
        JsonParser p = buf.asParser();
        assertEquals(JsonToken.VALUE_TRUE, p.nextToken());
        assertEquals(JsonToken.VALUE_FALSE, p.nextToken());
    }

    // writeObject(null) and writeTree(null) both delegate to writeNull()
    @Test
    public void testWriteObjectAndTreeNull_writeNullToken() throws Throwable {
        TokenBuffer buf = new TokenBuffer((ObjectCodec) null, false);
        buf.writeObject(null);
        buf.writeTree(null);
        JsonParser p = buf.asParser();
        assertEquals(JsonToken.VALUE_NULL, p.nextToken());
        assertEquals(JsonToken.VALUE_NULL, p.nextToken());
    }

    // writeObject(byte[]) always embeds the array directly regardless of codec
    @Test
    public void testWriteObjectByteArray_embedsArrayDirectly() throws Throwable {
        TokenBuffer buf = new TokenBuffer((ObjectCodec) null, false);
        byte[] data = new byte[] {9, 8, 7};
        buf.writeObject(data);
        JsonParser p = buf.asParser();
        assertEquals(JsonToken.VALUE_EMBEDDED_OBJECT, p.nextToken());
        assertArrayEquals(data, (byte[]) p.getEmbeddedObject());
    }

    // writeObject with null codec embeds the object directly
    @Test
    public void testWriteObjectWithoutCodec_embedsObjectDirectly() throws Throwable {
        TokenBuffer buf = new TokenBuffer((ObjectCodec) null, false);
        Integer val = Integer.valueOf(42);
        buf.writeObject(val);
        JsonParser p = buf.asParser();
        assertEquals(JsonToken.VALUE_EMBEDDED_OBJECT, p.nextToken());
        assertEquals(val, p.getEmbeddedObject());
    }

    // writeObject with non-null codec delegates to codec.writeValue producing real JSON tokens
    @Test
    public void testWriteObjectWithCodec_delegatesToCodecAndProducesJsonTokens() throws Throwable {
        TokenBuffer buf = new TokenBuffer(new ObjectMapper(), false);
        buf.writeObject(Integer.valueOf(7));
        JsonParser p = buf.asParser();
        assertEquals(JsonToken.VALUE_NUMBER_INT, p.nextToken());
        assertEquals(7, p.getIntValue());
    }

    // writeTree with null codec embeds the TreeNode instance directly
    @Test
    public void testWriteTreeWithoutCodec_embedsNodeDirectly() throws Throwable {
        ObjectMapper mapper = new ObjectMapper();
        ObjectNode node = mapper.createObjectNode();
        TokenBuffer buf = new TokenBuffer((ObjectCodec) null, false);
        buf.writeTree(node);
        JsonParser p = buf.asParser();
        assertEquals(JsonToken.VALUE_EMBEDDED_OBJECT, p.nextToken());
        assertSame(node, p.getEmbeddedObject());
    }

    // writeTree with non-null codec delegates to codec.writeTree producing structural tokens
    @Test
    public voidpublic void testWriteTreeWithCodec_delegatesToCodecAndProducesObjectTokens() throws Throwable {
        ObjectMapper mapper = new ObjectMapper();
        ObjectNode node = mapper.createObjectNode();
        TokenBuffer buf = new TokenBuffer(mapper, false);
        buf.writeTree(node);
        JsonParser p = buf.asParser();
        assertEquals(JsonToken.START_OBJECT, p.nextToken());
        assertEquals(JsonToken.END_OBJECT, p.nextToken());
    }

    // writeBinary(Base64Variant, byte[], offset, len) embeds a copy of the requested slice
    @Test
    public void testWriteBinary_embedsCopyOfByteArray() throws Throwable {
        TokenBuffer buf = new TokenBuffer((ObjectCodec) null, false);
        byte[] data = new byte[] {1, 2, 3, 4, 5};
        Base64Variant variant = Base64Variants.MIME;
        buf.writeBinary(variant, data, 1, 3);
        JsonParser p = buf.asParser();
        p.nextToken();
        byte[] embedded = (byte[]) p.getEmbeddedObject();
        assertArrayEquals(new byte[] {2, 3, 4}, embedded);
    }

    // writeBinary(Base64Variant, InputStream, int) is documented as unsupported
    @Test
    public void testWriteBinaryStream_throwsUnsupportedOperationException() throws Throwable {
        TokenBuffer buf = new TokenBuffer((ObjectCodec) null, false);
        Base64Variant variant = Base64Variants.MIME;
        ByteArrayInputStream in = new ByteArrayInputStream(new byte[] {1});
        try {
            buf.writeBinary(variant, in, 1);
            fail("expected UnsupportedOperationException");
        } catch (UnsupportedOperationException expected) { }
    }

    // BUG CHECK: writeTypeId's id is documented to apply only to the following value,
    // not to leak into subsequent tokens written afterwards.
    @Test
    public void testWriteTypeId_appliesOnlyToImmediateNextToken() throws Throwable {
        TokenBuffer buf = new TokenBuffer((ObjectCodec) null, true);
        buf.writeTypeId("TID");
        buf.writeString("first");
        buf.writeString("second");
        JsonParser p = buf.asParser();
        assertEquals(JsonToken.VALUE_STRING, p.nextToken());
        assertEquals("TID", p.getTypeId());
        assertEquals(JsonToken.VALUE_STRING, p.nextToken());
        assertNull(p.getTypeId());
    }

    // BUG CHECK: same contract for writeObjectId - must not leak to subsequent tokens
    @Test
    public void testWriteObjectId_appliesOnlyToImmediateNextToken() throws Throwable {
        TokenBuffer buf = new TokenBuffer((ObjectCodec) null, true);
        buf.writeObjectId("OID");
        buf.writeNumber(1);
        buf.writeNumber(2);
        JsonParser p = buf.asParser();
        p.nextToken();
        assertEquals("OID", p.getObjectId());
        p.nextToken();
        assertNull(p.getObjectId());
    }

    // copyCurrentEvent copies simple scalar tokens (boolean/null/string) correctly
    @Test
    public void testCopyCurrentEvent_simpleTokens_copiedCorrectly() throws Throwable {
        TokenBuffer src = new TokenBuffer((ObjectCodec) null, false);
        src.writeBoolean(true);
        src.writeBoolean(false);
        src.writeNull();
        src.writeString("hello");
        JsonParser srcParser = src.asParser();
        TokenBuffer dest = new TokenBuffer((ObjectCodec) null, false);
        while (srcParser.nextToken() != null) {
            dest.copyCurrentEvent(srcParser);
        }
        JsonParser p = dest.asParser();
        assertEquals(JsonToken.VALUE_TRUE, p.nextToken());
        assertEquals(JsonToken.VALUE_FALSE, p.nextToken());
        assertEquals(JsonToken.VALUE_NULL, p.nextToken());
        assertEquals(JsonToken.VALUE_STRING, p.nextToken());
        assertEquals("hello", p.getText());
    }

    // copyCurrentEvent copies BIG_INTEGER/DOUBLE/BIG_DECIMAL numeric sub-branches correctly
    @Test
    public void testCopyCurrentEvent_numericTypes_copiedCorrectly() throws Throwable {
        TokenBuffer src = new TokenBuffer((ObjectCodec) null, false);
        src.writeNumber(BigInteger.valueOf(100));
        src.writeNumber(3.14d);
        src.writeNumber(new BigDecimal("1.23"));
        JsonParser srcParser = src.asParser();
        TokenBuffer dest = new TokenBuffer((ObjectCodec) null, false);
        while (srcParser.nextToken() != null) {
            dest.copyCurrentEvent(srcParser);
        }
        JsonParser p = dest.asParser();
        p.nextToken(); assertEquals(BigInteger.valueOf(100), p.getBigIntegerValue());
        p.nextToken(); assertEquals(3.14d, p.getDoubleValue(), 1e-9);
        p.nextToken(); assertEquals(new BigDecimal("1.23"), p.getDecimalValue());
    }

    // copyCurrentStructure recursively copies nested object/array/field-name structure
    @Test
    public void testCopyCurrentStructure_nestedObjectAndArray_copiedCorrectly() throws Throwable {
        TokenBuffer src = new TokenBuffer((ObjectCodec) null, false);
        src.writeStartObject();
        src.writeFieldName("arr");
        src.writeStartArray();
        src.writeNumber(1);
        src.writeNumber(2);
        src.writeEndArray();
        src.writeEndObject();
        JsonParser srcParser = src.asParser();
        srcParser.nextToken();
        TokenBuffer dest = new TokenBuffer((ObjectCodec) null, false);
        dest.copyCurrentStructure(srcParser);
        JsonParser p = dest.asParser();
        assertEquals(JsonToken.START_OBJECT, p.nextToken());
        assertEquals(JsonToken.FIELD_NAME, p.nextToken());
        assertEquals("arr", p.getCurrentName());
        assertEquals(JsonToken.START_ARRAY, p.nextToken());
        assertEquals(JsonToken.VALUE_NUMBER_INT, p.nextToken());
        assertEquals(JsonToken.VALUE_NUMBER_INT, p.nextToken());
        assertEquals(JsonToken.END_ARRAY, p.nextToken());
        assertEquals(JsonToken.END_OBJECT, p.nextToken());
        assertNull(p.nextToken());
    }

    // TokenBuffer.Parser.peekNextToken looks ahead without consuming the token
    @Test
    public void testParserPeekNextToken_looksAheadWithoutConsuming() throws Throwable {
        TokenBuffer buf = new TokenBuffer((ObjectCodec) null, false);
        buf.writeString("a");
        buf.writeString("b");
        TokenBuffer.Parser p = (TokenBuffer.Parser) buf.asParser();
        assertEquals(JsonToken.VALUE_STRING, p.peekNextToken());
        p.nextToken();
        assertEquals("a", p.getText());
        p.nextToken();
        assertEquals("b", p.getText());
        assertNull(p.peekNextToken());
    }

    // overrideCurrentName changes the reported current field name
    @Test
    public void testParserOverrideCurrentName_changesFieldName() throws Throwable {
        TokenBuffer buf = new TokenBuffer((ObjectCodec) null, false);
        buf.writeStartObject();
        buf.writeFieldName("orig");
        buf.writeString("v");
        buf.writeEndObject();
        JsonParser p = buf.asParser();
        p.nextToken();
        p.nextToken();
        p.overrideCurrentName("changed");
        assertEquals("changed", p.getCurrentName());
    }

    // getBinaryValue on wrong token type and getDoubleValue on non-numeric token both throw JsonParseException
    @Test
    public void testParserErrorPaths_throwJsonParseException() throws Throwable {
        TokenBuffer buf = new TokenBuffer((ObjectCodec) null, false);
        buf.writeNumber(5);
        buf.writeString("abc");
        JsonParser p = buf.asParser();
        p.nextToken();
        Base64Variant variant = Base64Variants.MIME;
        try { p.getBinaryValue(variant); fail("expected JsonParseException"); }
        catch (JsonParseException expected) { }
        p.nextToken();
        try { p.getDoubleValue(); fail("expected JsonParseException"); }
        catch (JsonParseException expected) { }
    }

    // getBinaryValue returns the embedded byte[] directly when token is VALUE_EMBEDDED_OBJECT
    @Test
    public void testParserGetBinaryValue_embeddedByteArray_returnsSameArray() throws Throwable {
        TokenBuffer buf = new TokenBuffer((ObjectCodec) null, false);
        byte[] data = new byte[] {5, 6, 7};
        buf.writeObject(data);
        JsonParser p = buf.asParser();
        p.nextToken();
        Base64Variant variant = Base64Variants.MIME;
        assertArrayEquals(data, p.getBinaryValue(variant));
    }

    // readBinaryValue writes the binary content to the given OutputStream and returns its length
    @Test
    public void testParserReadBinaryValue_writesBytesToOutputStream() throws Throwable {
        TokenBuffer buf = new TokenBuffer((ObjectCodec) null, false);
        byte[] data = new byte[] {5, 6, 7};
        buf.writeObject(data);
        JsonParser p = buf.asParser();
        p.nextToken();
        Base64Variant variant = Base64Variants.MIME;
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        int n = p.readBinaryValue(variant, out);
        assertEquals(3, n);
        assertArrayEquals(data, out.toByteArray());
    }

    // getParsingContext is always available; close()/isClosed() lifecycle and post-close nextToken()==null
    @Test
    public void testParserCloseAndParsingContext() throws Throwable {
        TokenBuffer buf = new TokenBuffer((ObjectCodec) null, false);
        buf.writeString("x");
        JsonParser p = buf.asParser();
        assertNotNull(p.getParsingContext());
        assertFalse(p.isClosed());
        p.close();
        assertTrue(p.isClosed());
        assertNull(p.nextToken());
    }

    // getBigIntegerValue/getDecimalValue correctly convert from an int-backed VALUE_NUMBER_INT token
    @Test
    public void testParserGetBigIntegerAndDecimalValue_fromIntToken() throws Throwable {
        TokenBuffer buf = new TokenBuffer((ObjectCodec) null, false);
        buf.writeNumber(5);
        JsonParser p = buf.asParser();
        p.nextToken();
        assertEquals(BigInteger.valueOf(5), p.getBigIntegerValue());
        assertEquals(BigDecimal.valueOf(5), p.getDecimalValue());
    }

    // Segment overflow: writing more than TOKENS_PER_SEGMENT tokens spans multiple segments correctly
    @Test
    public void testWriteNumber_acrossMultipleSegments_allTokensPreserved() throws Throwable {
        TokenBuffer buf = new TokenBuffer((ObjectCodec) null, false);
        buf.writeStartArray();
        for (int i = 0; i < 20; i++) {
            buf.writeNumber(i);
        }
        buf.writeEndArray();
        JsonParser p = buf.asParser();
        assertEquals(JsonToken.START_ARRAY, p.nextToken());
        for (int i = 0; i < 20; i++) {
            assertEquals(JsonToken.VALUE_NUMBER_INT, p.nextToken());
            assertEquals(i, p.getIntValue());
        }
        assertEquals(JsonToken.END_ARRAY, p.nextToken());
        assertNull(p.nextToken());
    }
}
