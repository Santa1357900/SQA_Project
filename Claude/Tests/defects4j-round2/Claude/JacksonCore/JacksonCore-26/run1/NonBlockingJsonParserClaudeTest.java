package com.fasterxml.jackson.core.json.async;

import java.io.ByteArrayOutputStream;
import java.io.IOException;

import org.junit.Before;
import org.junit.Test;
import static org.junit.Assert.*;

import com.fasterxml.jackson.core.JsonFactory;
import com.fasterxml.jackson.core.JsonParseException;
import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.core.JsonToken;
import com.fasterxml.jackson.core.async.ByteArrayFeeder;

public class NonBlockingJsonParserClaudeTest {

    private JsonFactory factory;

    @Before
    public void setUp() throws Throwable {
        factory = new JsonFactory();
    }

    private NonBlockingJsonParser createParser() throws IOException {
        return (NonBlockingJsonParser) factory.createNonBlockingByteArrayParser();
    }

    private NonBlockingJsonParser createParserWithFeature(JsonParser.Feature feature) throws IOException {
        JsonFactory f = new JsonFactory();
        f.enable(feature);
        return (NonBlockingJsonParser) f.createNonBlockingByteArrayParser();
    }

    private byte[] utf8(String s) throws IOException {
        return s.getBytes("UTF-8");
    }

    // Helper: repeatedly call nextToken() to flush pending EOF-bound tokens
    private JsonToken nt(NonBlockingJsonParser p) throws IOException {
        JsonToken t = p.nextToken();
        int guard = 0;
        while (t == JsonToken.NOT_AVAILABLE && guard < 5) {
            t = p.nextToken();
            guard++;
        }
        return t;
    }

    // getNonBlockingInputFeeder(): must return "this" per implementation contract
    @Test
    public void testGetNonBlockingInputFeeder_returnsSameInstance() throws Throwable {
        NonBlockingJsonParser parser = createParser();
        ByteArrayFeeder feeder = parser.getNonBlockingInputFeeder();
        assertSame(parser, feeder);
    }

    // needMoreInput(): true before any input fed
    @Test
    public void testNeedMoreInput_beforeAnyInput_true() throws Throwable {
        NonBlockingJsonParser parser = createParser();
        assertTrue(parser.needMoreInput());
    }

    // needMoreInput(): false once data is buffered and unconsumed
    @Test
    public void testNeedMoreInput_afterFeedWithDataAvailable_false() throws Throwable {
        NonBlockingJsonParser parser = createParser();
        byte[] data = utf8("1");
        parser.feedInput(data, 0, data.length);
        assertFalse(parser.needMoreInput());
    }

    // needMoreInput(): true after buffer fully consumed without EOF signaled
    @Test
    public void testNeedMoreInput_afterFeedFullyConsumedNoEOF_true() throws Throwable {
        NonBlockingJsonParser parser = createParser();
        byte[] data = utf8("1");
        parser.feedInput(data, 0, data.length);
        parser.nextToken();
        assertTrue(parser.needMoreInput());
    }

    // needMoreInput(): false once endOfInput() flag set, regardless of buffer state
    @Test
    public void testNeedMoreInput_afterEndOfInput_false() throws Throwable {
        NonBlockingJsonParser parser = createParser();
        parser.endOfInput();
        assertFalse(parser.needMoreInput());
    }

    // feedInput(): normal call followed by successful parsing
    @Test
    public void testFeedInput_normalData_parsesSuccessfully() throws Throwable {
        NonBlockingJsonParser parser = createParser();
        byte[] data = utf8("1");
        parser.feedInput(data, 0, data.length);
        parser.endOfInput();
        assertEquals(JsonToken.VALUE_NUMBER_INT, nt(parser));
        assertEquals(1, parser.getIntValue());
    }

    // feedInput(): calling again while previous bytes not fully consumed throws
    @Test
    public void testFeedInput_stillHaveUndecodedBytes_throwsException() throws Throwable {
        NonBlockingJsonParser parser = createParser();
        byte[] data = utf8("12");
        parser.feedInput(data, 0, data.length);
        try {
            parser.feedInput(data, 0, data.length);
            fail("expected JsonParseException");
        } catch (JsonParseException e) {
            assertTrue(e.getMessage().contains("undecoded"));
        }
    }

    // feedInput(): end < start throws
    @Test
    public void testFeedInput_endBeforeStart_throwsException() throws Throwable {
        NonBlockingJsonParser parser = createParser();
        byte[] data = new byte[5];
        try {
            parser.feedInput(data, 3, 1);
            fail("expected JsonParseException");
        } catch (JsonParseException e) {
            assertTrue(e.getMessage().contains("before"));
        }
    }

    // feedInput(): calling after endOfInput() throws
    @Test
    public void testFeedInput_afterEndOfInput_throwsException() throws Throwable {
        NonBlockingJsonParser parser = createParser();
        parser.endOfInput();
        byte[] data = utf8("1");
        try {
            parser.feedInput(data, 0, data.length);
            fail("expected JsonParseException");
        } catch (JsonParseException e) {
            assertTrue(e.getMessage().contains("closed"));
        }
    }

    // endOfInput(): after a complete top-level scalar, next call yields null (EOF)
    @Test
    public void testEndOfInput_afterCompleteValue_nextTokenReturnsNull() throws Throwable {
        NonBlockingJsonParser parser = createParser();
        byte[] data = utf8("42");
        parser.feedInput(data, 0, data.length);
        parser.endOfInput();
        assertEquals(JsonToken.VALUE_NUMBER_INT, nt(parser));
        assertNull(parser.nextToken());
    }

    // releaseBuffered(): returns and writes out unconsumed trailing bytes
    @Test
    public void testReleaseBuffered_withRemainingBytes_returnsCorrectData() throws Throwable {
        NonBlockingJsonParser parser = createParser();
        byte[] data = utf8("{\"a\":1}extra");
        parser.feedInput(data, 0, data.length);
        nt(parser); nt(parser); nt(parser); nt(parser);
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        int avail = parser.releaseBuffered(out);
        assertEquals(5, avail);
        assertEquals("extra", new String(out.toByteArray(), "UTF-8"));
    }

    // releaseBuffered(): returns zero when nothing left buffered
    @Test
    public void testReleaseBuffered_noRemainingBytes_returnsZero() throws Throwable {
        NonBlockingJsonParser parser = createParser();
        byte[] data = utf8("1");
        parser.feedInput(data, 0, data.length);
        parser.endOfInput();
        nt(parser);
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        assertEquals(0, parser.releaseBuffered(out));
    }

    // nextToken(): closed parser short-circuits to null
    @Test
    public void testNextToken_closedParser_returnsNull() throws Throwable {
        NonBlockingJsonParser parser = createParser();
        parser.close();
        assertNull(parser.nextToken());
    }

    // nextToken(): no input fed at all returns NOT_AVAILABLE
    @Test
    public void testNextToken_noInputFed_returnsNotAvailable() throws Throwable {
        NonBlockingJsonParser parser = createParser();
        assertEquals(JsonToken.NOT_AVAILABLE, parser.nextToken());
    }

    // nextToken(): full object parsing of a single int field
    @Test
    public void testNextToken_simpleObjectWithIntField() throws Throwable {
        NonBlockingJsonParser parser = createParser();
        byte[] data = utf8("{\"a\":1}");
        parser.feedInput(data, 0, data.length);
        parser.endOfInput();
        assertEquals(JsonToken.START_OBJECT, nt(parser));
        assertEquals(JsonToken.FIELD_NAME, nt(parser));
        assertEquals("a", parser.getCurrentName());
        assertEquals(JsonToken.VALUE_NUMBER_INT, nt(parser));
        assertEquals(1, parser.getIntValue());
        assertEquals(JsonToken.END_OBJECT, nt(parser));
    }

    // nextToken(): array with boolean and null literal values
    @Test
    public void testNextToken_arrayWithBooleanAndNullValues() throws Throwable {
        NonBlockingJsonParser parser = createParser();
        byte[] data = utf8("[true,false,null]");
        parser.feedInput(data, 0, data.length);
        parser.endOfInput();
        assertEquals(JsonToken.START_ARRAY, nt(parser));
        assertEquals(JsonToken.VALUE_TRUE, nt(parser));
        assertEquals(JsonToken.VALUE_FALSE, nt(parser));
        assertEquals(JsonToken.VALUE_NULL, nt(parser));
        assertEquals(JsonToken.END_ARRAY, nt(parser));
    }

    // nextToken(): nested array inside an object field
    @Test
    public void testNextToken_nestedArrayInsideObject() throws Throwable {
        NonBlockingJsonParser parser = createParser();
        byte[] data = utf8("{\"arr\":[1,2]}");
        parser.feedInput(data, 0, data.length);
        parser.endOfInput();
        assertEquals(JsonToken.START_OBJECT, nt(parser));
        assertEquals(JsonToken.FIELD_NAME, nt(parser));
        assertEquals(JsonToken.START_ARRAY, nt(parser));
        assertEquals(JsonToken.VALUE_NUMBER_INT, nt(parser));
        assertEquals(1, parser.getIntValue());
        assertEquals(JsonToken.VALUE_NUMBER_INT, nt(parser));
        assertEquals(2, parser.getIntValue());
        assertEquals(JsonToken.END_ARRAY, nt(parser));
        assertEquals(JsonToken.END_OBJECT, nt(parser));
    }

    // nextToken(): integer split across two feedInput() calls
    @Test
    public void testNextToken_numberSplitAcrossMultipleFeeds() throws Throwable {
        NonBlockingJsonParser parser = createParser();
        byte[] part1 = utf8("12");
        parser.feedInput(part1, 0, part1.length);
        assertEquals(JsonToken.NOT_AVAILABLE, parser.nextToken());
        byte[] part2 = utf8("34");
        parser.feedInput(part2, 0, part2.length);
        parser.endOfInput();
        assertEquals(JsonToken.VALUE_NUMBER_INT, nt(parser));
        assertEquals(1234, parser.getIntValue());
    }

    // nextToken(): string split across feeds on a plain ascii boundary
    @Test
    public void testNextToken_stringSplitAcrossFeeds() throws Throwable {
        NonBlockingJsonParser parser = createParser();
        byte[] part1 = utf8("\"hel");
        parser.feedInput(part1, 0, part1.length);
        assertEquals(JsonToken.NOT_AVAILABLE, parser.nextToken());
        byte[] part2 = utf8("lo\"");
        parser.feedInput(part2, 0, part2.length);
        assertEquals(JsonToken.VALUE_STRING, nt(parser));
        assertEquals("hello", parser.getText());
    }

    // nextToken(): escape sequence split right after backslash
    @Test
    public void testNextToken_stringEscapeSplitAcrossFeeds() throws Throwable {
        NonBlockingJsonParser parser = createParser();
        byte[] part1 = utf8("\"a\\");
        parser.feedInput(part1, 0, part1.length);
        assertEquals(JsonToken.NOT_AVAILABLE, parser.nextToken());
        byte[] part2 = utf8("nb\"");
        parser.feedInput(part2, 0, part2.length);
        assertEquals(JsonToken.VALUE_STRING, nt(parser));
        assertEquals("a\nb", parser.getText());
    }

    // nextToken(): 2-byte UTF-8 char split right after its lead byte
    @Test
    public void testNextToken_utf8TwoByteCharSplitAcrossFeeds() throws Throwable {
        NonBlockingJsonParser parser = createParser();
        byte[] part1 = new byte[] { '"', (byte) 0xC3 };
        parser.feedInput(part1, 0, part1.length);
        assertEquals(JsonToken.NOT_AVAILABLE, parser.nextToken());
        byte[] part2 = new byte[] { (byte) 0xA9, '"' };
        parser.feedInput(part2, 0, part2.length);
        assertEquals(JsonToken.VALUE_STRING, nt(parser));
        assertEquals("\u00e9", parser.getText());
    }

    // nextToken(): 3-byte UTF-8 char split after its lead byte
    @Test
    public void testNextToken_utf8ThreeByteCharSplitAcrossFeeds() throws Throwable {
        NonBlockingJsonParser parser = createParser();
        byte[] part1 = new byte[] { '"', (byte) 0xE2 };
        parser.feedInput(part1, 0, part1.length);
        assertEquals(JsonToken.NOT_AVAILABLE, parser.nextToken());
        byte[] part2 = new byte[] { (byte) 0x82, (byte) 0xAC, '"' };
        parser.feedInput(part2, 0, part2.length);
        assertEquals(JsonToken.VALUE_STRING, nt(parser));
        assertEquals("\u20ac", parser.getText());
    }

    // nextToken(): leading zero disallowed by default -> exception
    @Test
    public void testNextToken_leadingZeroDisallowedByDefault_throwsException() throws Throwable {
        NonBlockingJsonParser parser = createParser();
        byte[] data = utf8("01");
        parser.feedInput(data, 0, data.length);
        try {
            nt(parser);
            fail("expected JsonParseException");
        } catch (JsonParseException e) {
            assertTrue(e.getMessage().contains("Leading zero"));
        }
    }

    // nextToken(): leading zero allowed once feature enabled, leading zeros dropped
    @Test
    public void testNextToken_leadingZeroAllowedWithFeatureEnabled() throws Throwable {
        NonBlockingJsonParser parser = createParserWithFeature(JsonParser.Feature.ALLOW_NUMERIC_LEADING_ZEROS);
        byte[] data = utf8("007");
        parser.feedInput(data, 0, data.length);
        parser.endOfInput();
        assertEquals(JsonToken.VALUE_NUMBER_INT, nt(parser));
        assertEquals(7, parser.getIntValue());
    }

    // nextToken(): negative integer number
    @Test
    public void testNextToken_negativeNumber() throws Throwable {
        NonBlockingJsonParser parser = createParser();
        byte[] data = utf8("-123");
        parser.feedInput(data, 0, data.length);
        parser.endOfInput();
        assertEquals(JsonToken.VALUE_NUMBER_INT, nt(parser));
        assertEquals(-123, parser.getIntValue());
    }

    // nextToken(): "-0" resolves to integer zero
    @Test
    public void testNextToken_negativeZero() throws Throwable {
        NonBlockingJsonParser parser = createParser();
        byte[] data = utf8("-0");
        parser.feedInput(data, 0, data.length);
        parser.endOfInput();
        assertEquals(JsonToken.VALUE_NUMBER_INT, nt(parser));
        assertEquals(0, parser.getIntValue());
    }

    // nextToken(): floating point number with exponent
    @Test
    public void testNextToken_floatWithExponent() throws Throwable {
        NonBlockingJsonParser parser = createParser();
        byte[] data = utf8("1.5e2");
        parser.feedInput(data, 0, data.length);
        parser.endOfInput();
        assertEquals(JsonToken.VALUE_NUMBER_FLOAT, nt(parser));
        assertEquals(150.0, parser.getDoubleValue(), 0.0001);
    }

    // nextToken(): comments disallowed by default -> exception referencing feature name
    @Test
    public void testNextToken_commentsDisabledByDefault_throwsException() throws Throwable {
        NonBlockingJsonParser parser = createParser();
        byte[] data = utf8("[1/*c*/,2]");
        parser.feedInput(data, 0, data.length);
        parser.endOfInput();
        assertEquals(JsonToken.START_ARRAY, nt(parser));
        assertEquals(JsonToken.VALUE_NUMBER_INT, nt(parser));
        try {
            nt(parser);
            fail("expected JsonParseException");
        } catch (JsonParseException e) {
            assertTrue(e.getMessage().contains("ALLOW_COMMENTS"));
        }
    }

    // nextToken(): C-style comment skipped when feature enabled
    @Test
    public void testNextToken_cStyleCommentEnabled() throws Throwable {
        NonBlockingJsonParser parser = createParserWithFeature(JsonParser.Feature.ALLOW_COMMENTS);
        byte[] data = utf8("[1/*comment*/,2]");
        parser.feedInput(data, 0, data.length);
        parser.endOfInput();
        assertEquals(JsonToken.START_ARRAY, nt(parser));
        assertEquals(1, nt(parser) == JsonToken.VALUE_NUMBER_INT ? parser.getIntValue() : -1);
        assertEquals(2, nt(parser) == JsonToken.VALUE_NUMBER_INT ? parser.getIntValue() : -1);
    }

    // nextToken(): C++-style comment skipped when feature enabled
    @Test
    public void testNextToken_cppStyleCommentEnabled() throws Throwable {
        NonBlockingJsonParser parser = createParserWithFeature(JsonParser.Feature.ALLOW_COMMENTS);
        byte[] data = utf8("[1//comment\n,2]");
        parser.feedInput(data, 0, data.length);
        parser.endOfInput();
        assertEquals(JsonToken.START_ARRAY, nt(parser));
        assertEquals(JsonToken.VALUE_NUMBER_INT, nt(parser));
        assertEquals(JsonToken.VALUE_NUMBER_INT, nt(parser));
        assertEquals(2, parser.getIntValue());
    }

    // nextToken(): YAML/hash-style comment skipped when feature enabled
    @Test
    public void testNextToken_yamlCommentEnabled() throws Throwable {
        NonBlockingJsonParser parser = createParserWithFeature(JsonParser.Feature.ALLOW_YAML_COMMENTS);
        byte[] data = utf8("[1#comment\n,2]");
        parser.feedInput(data, 0, data.length);
        parser.endOfInput();
        assertEquals(JsonToken.START_ARRAY, nt(parser));
        assertEquals(JsonToken.VALUE_NUMBER_INT, nt(parser));
        assertEquals(JsonToken.VALUE_NUMBER_INT, nt(parser));
        assertEquals(2, parser.getIntValue());
    }

    // nextToken(): single-quoted string values allowed when feature enabled
    @Test
    public void testNextToken_singleQuotesEnabled() throws Throwable {
        NonBlockingJsonParser parser = createParserWithFeature(JsonParser.Feature.ALLOW_SINGLE_QUOTES);
        byte[] data = utf8("['abc']");
        parser.feedInput(data, 0, data.length);
        parser.endOfInput();
        assertEquals(JsonToken.START_ARRAY, nt(parser));
        assertEquals(JsonToken.VALUE_STRING, nt(parser));
        assertEquals("abc", parser.getText());
        assertEquals(JsonToken.END_ARRAY, nt(parser));
    }

    // nextToken(): unquoted field names allowed when feature enabled
    @Test
    public void testNextToken_unquotedFieldNamesEnabled() throws Throwable {
        NonBlockingJsonParser parser = createParserWithFeature(JsonParser.Feature.ALLOW_UNQUOTED_FIELD_NAMES);
        byte[] data = utf8("{abc:1}");
        parser.feedInput(data, 0, data.length);
        parser.endOfInput();
        assertEquals(JsonToken.START_OBJECT, nt(parser));
        assertEquals(JsonToken.FIELD_NAME, nt(parser));
        assertEquals("abc", parser.getCurrentName());
        assertEquals(JsonToken.VALUE_NUMBER_INT, nt(parser));
    }

    // nextToken(): trailing comma in array allowed when feature enabled
    @Test
    public void testNextToken_trailingCommaInArrayEnabled() throws Throwable {
        NonBlockingJsonParser parser = createParserWithFeature(JsonParser.Feature.ALLOW_TRAILING_COMMA);
        byte[] data = utf8("[1,2,]");
        parser.feedInput(data, 0, data.length);
        parser.endOfInput();
        assertEquals(JsonToken.START_ARRAY, nt(parser));
        assertEquals(JsonToken.VALUE_NUMBER_INT, nt(parser));
        assertEquals(JsonToken.VALUE_NUMBER_INT, nt(parser));
        assertEquals(JsonToken.END_ARRAY, nt(parser));
    }

    // nextToken(): missing (elided) values become null when feature enabled
    @Test
    public void testNextToken_missingValuesEnabled() throws Throwable {
        NonBlockingJsonParser parser = createParserWithFeature(JsonParser.Feature.ALLOW_MISSING_VALUES);
        byte[] data = utf8("[1,,3]");
        parser.feedInput(data, 0, data.length);
        parser.endOfInput();
        assertEquals(JsonToken.START_ARRAY, nt(parser));
        assertEquals(JsonToken.VALUE_NUMBER_INT, nt(parser));
        assertEquals(JsonToken.VALUE_NULL, nt(parser));
        assertEquals(JsonToken.VALUE_NUMBER_INT, nt(parser));
        assertEquals(3, parser.getIntValue());
    }

    // nextToken(): unrecognized keyword-like token throws with descriptive message
    @Test
    public void testNextToken_invalidKeywordToken_throwsException() throws Throwable {
        NonBlockingJsonParser parser = createParser();
        byte[] data = utf8("nule");
        parser.feedInput(data, 0, data.length);
        parser.endOfInput();
        try {
            nt(parser);
            fail("expected JsonParseException");
        } catch (JsonParseException e) {
            assertTrue(e.getMessage().contains("Unrecognized token"));
        }
    }

    // nextToken(): unexpected character as a value throws descriptive error
    @Test
    public void testNextToken_unexpectedCharacterValue_throwsException() throws Throwable {
        NonBlockingJsonParser parser = createParser();
        byte[] data = utf8("@");
        parser.feedInput(data, 0, data.length);
        parser.endOfInput();
        try {
            nt(parser);
            fail("expected JsonParseException");
        } catch (JsonParseException e) {
            assertTrue(e.getMessage().contains("expected a valid value"));
        }
    }

    // nextToken(): UTF-8 BOM at start of document is skipped
    @Test
    public void testNextToken_bomAtStartOfDocument() throws Throwable {
        NonBlockingJsonParser parser = createParser();
        byte[] data = new byte[] { (byte) 0xEF, (byte) 0xBB, (byte) 0xBF, '1' };
        parser.feedInput(data, 0, data.length);
        parser.endOfInput();
        assertEquals(JsonToken.VALUE_NUMBER_INT, nt(parser));
        assertEquals(1, parser.getIntValue());
    }

    // nextToken(): BOM bytes split across separate feedInput() calls
    @Test
    public void testNextToken_bomSplitAcrossFeeds() throws Throwable {
        NonBlockingJsonParser parser = createParser();
        byte[] part1 = new byte[] { (byte) 0xEF };
        parser.feedInput(part1, 0, part1.length);
        assertEquals(JsonToken.NOT_AVAILABLE, parser.nextToken());
        byte[] part2 = new byte[] { (byte) 0xBB, (byte) 0xBF, '7' };
        parser.feedInput(part2, 0, part2.length);
        parser.endOfInput();
        assertEquals(JsonToken.VALUE_NUMBER_INT, nt(parser));
        assertEquals(7, parser.getIntValue());
    }

    // nextToken(): \\u hex escape fully contained in a single feed
    @Test
    public void testNextToken_unicodeEscapeInString() throws Throwable {
        NonBlockingJsonParser parser = createParser();
        byte[] data = utf8("\"\\u0041\"");
        parser.feedInput(data, 0, data.length);
        parser.endOfInput();
        assertEquals(JsonToken.VALUE_STRING, nt(parser));
        assertEquals("A", parser.getText());
    }

    // nextToken(): \\u hex escape digits split across feeds
    @Test
    public void testNextToken_unicodeEscapeSplitAcrossFeeds() throws Throwable {
        NonBlockingJsonParser parser = createParser();
        byte[] part1 = utf8("\"\\u00");
        parser.feedInput(part1, 0, part1.length);
        assertEquals(JsonToken.NOT_AVAILABLE, parser.nextToken());
        byte[] part2 = utf8("41\"");
        parser.feedInput(part2, 0, part2.length);
        assertEquals(JsonToken.VALUE_STRING, nt(parser));
        assertEquals("A", parser.getText());
    }

    // nextToken(): field name containing an escape sequence
    @Test
    public void testNextToken_fieldNameWithEscape() throws Throwable {
        NonBlockingJsonParser parser = createParser();
        byte[] data = utf8("{\"a\\tb\":1}");
        parser.feedInput(data, 0, data.length);
        parser.endOfInput();
        assertEquals(JsonToken.START_OBJECT, nt(parser));
        assertEquals(JsonToken.FIELD_NAME, nt(parser));
        assertEquals("a\tb", parser.getCurrentName());
    }

    // nextToken(): mismatched closing '}' while inside an array must be rejected
    @Test
    public void testNextToken_mismatchedArrayCloseThrowsException() throws Throwable {
        NonBlockingJsonParser parser = createParser();
        byte[] data = utf8("[1}");
        parser.feedInput(data, 0, data.length);
        parser.endOfInput();
        assertEquals(JsonToken.START_ARRAY, nt(parser));
        assertEquals(JsonToken.VALUE_NUMBER_INT, nt(parser));
        try {
            nt(parser);
            fail("expected JsonParseException for mismatched '}'");
        } catch (JsonParseException e) {
            assertNotNull(e.getMessage());
        }
    }

    // nextToken(): mismatched closing ']' while inside an object must be rejected
    @Test
    public void testNextToken_mismatchedObjectCloseThrowsException() throws Throwable {
        NonBlockingJsonParser parser = createParser();
        byte[] data = utf8("{\"a\":1]");
        parser.feedInput(data, 0, data.length);
        parser.endOfInput();
        assertEquals(JsonToken.START_OBJECT, nt(parser));
        assertEquals(JsonToken.FIELD_NAME, nt(parser));
        assertEquals(JsonToken.VALUE_NUMBER_INT, nt(parser));
        try {
            nt(parser);
            fail("expected JsonParseException for mismatched ']'");
        } catch (JsonParseException e) {
            assertNotNull(e.getMessage());
        }
    }
}
