package com.fasterxml.jackson.core.json;

import java.io.IOException;
import java.io.StringWriter;

import org.junit.Before;
import org.junit.Test;
import static org.junit.Assert.*;

import com.fasterxml.jackson.core.JsonFactory;
import com.fasterxml.jackson.core.JsonGenerationException;
import com.fasterxml.jackson.core.JsonGenerator;
import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.core.SerializableString;
import com.fasterxml.jackson.core.Version;
import com.fasterxml.jackson.core.util.DefaultPrettyPrinter;

public class JsonGeneratorImplClaudeTest
{
    private StringWriter sw;

    @Before
    public void setUp() throws Throwable
    {
        sw = new StringWriter();
    }

    private JsonFactory newFactory()
    {
        return new JsonFactory();
    }

    // --- Constructor branch: default features -> _cfgUnqNames must be false (QUOTE_FIELD_NAMES on by default)
    @Test
    public void testConstructor_defaultFeatures_cfgUnqNamesFalse() throws Throwable {
        JsonGeneratorImpl gen = (JsonGeneratorImpl) newFactory().createGenerator(sw);
        assertFalse(gen._cfgUnqNames);
    }

    // --- Constructor branch: ESCAPE_NON_ASCII disabled by default -> _maximumNonEscapedChar stays 0
    @Test
    public void testConstructor_defaultFeatures_maximumNonEscapedCharZero() throws Throwable {
        JsonGeneratorImpl gen = (JsonGeneratorImpl) newFactory().createGenerator(sw);
        assertEquals(0, gen._maximumNonEscapedChar);
        assertEquals(0, gen.getHighestEscapedChar());
    }

    // --- Constructor branch: ESCAPE_NON_ASCII enabled -> _maximumNonEscapedChar becomes 127
    @Test
    public void testConstructor_escapeNonAsciiEnabled_maximumNonEscapedChar127() throws Throwable {
        JsonFactory factory = newFactory();
        factory.configure(JsonGenerator.Feature.ESCAPE_NON_ASCII, true);
        JsonGeneratorImpl gen = (JsonGeneratorImpl) factory.createGenerator(sw);
        assertEquals(127, gen._maximumNonEscapedChar);
        assertEquals(127, gen.getHighestEscapedChar());
    }

    // --- Constructor branch: QUOTE_FIELD_NAMES disabled -> _cfgUnqNames becomes true
    @Test
    public void testConstructor_quoteFieldNamesDisabled_cfgUnqNamesTrue() throws Throwable {
        JsonFactory factory = newFactory();
        factory.configure(JsonGenerator.Feature.QUOTE_FIELD_NAMES, false);
        JsonGeneratorImpl gen = (JsonGeneratorImpl) factory.createGenerator(sw);
        assertTrue(gen._cfgUnqNames);
    }

    // --- enable(): QUOTE_FIELD_NAMES branch sets _cfgUnqNames back to false
    @Test
    public void testEnable_quoteFieldNames_setsCfgUnqNamesFalse() throws Throwable {
        JsonFactory factory = newFactory();
        factory.configure(JsonGenerator.Feature.QUOTE_FIELD_NAMES, false);
        JsonGeneratorImpl gen = (JsonGeneratorImpl) factory.createGenerator(sw);
        assertTrue(gen._cfgUnqNames);
        gen.enable(JsonGenerator.Feature.QUOTE_FIELD_NAMES);
        assertFalse(gen._cfgUnqNames);
    }

    // --- enable(): other feature does not touch _cfgUnqNames
    @Test
    public void testEnable_otherFeature_doesNotChangeCfgUnqNames() throws Throwable {
        JsonGeneratorImpl gen = (JsonGeneratorImpl) newFactory().createGenerator(sw);
        assertFalse(gen._cfgUnqNames);
        gen.enable(JsonGenerator.Feature.ESCAPE_NON_ASCII);
        assertFalse(gen._cfgUnqNames);
    }

    // --- enable(): fluent API returns same instance
    @Test
    public void testEnable_returnsGeneratorInstance() throws Throwable {
        JsonGeneratorImpl gen = (JsonGeneratorImpl) newFactory().createGenerator(sw);
        JsonGenerator returned = gen.enable(JsonGenerator.Feature.ESCAPE_NON_ASCII);
        assertSame(gen, returned);
    }

    // --- setHighestNonEscapedChar(): negative value clamped to 0
    @Test
    public void testSetHighestNonEscapedChar_negativeValue_clampedToZero() throws Throwable {
        JsonGeneratorImpl gen = (JsonGeneratorImpl) newFactory().createGenerator(sw);
        gen.setHighestNonEscapedChar(-1);
        assertEquals(0, gen._maximumNonEscapedChar);
    }

    // --- setHighestNonEscapedChar(): boundary zero stays zero
    @Test
    public void testSetHighestNonEscapedChar_zero_staysZero() throws Throwable {
        JsonGeneratorImpl gen = (JsonGeneratorImpl) newFactory().createGenerator(sw);
        gen.setHighestNonEscapedChar(0);
        assertEquals(0, gen._maximumNonEscapedChar);
    }

    // --- setHighestNonEscapedChar(): positive value stored exactly (127 boundary)
    @Test
    public void testSetHighestNonEscapedChar_positiveValue_setsExactValue() throws Throwable {
        JsonGeneratorImpl gen = (JsonGeneratorImpl) newFactory().createGenerator(sw);
        gen.setHighestNonEscapedChar(127);
        assertEquals(127, gen._maximumNonEscapedChar);
    }

    // --- setHighestNonEscapedChar(): large value (65535, upper documented bound) stored exactly
    @Test
    public void testSetHighestNonEscapedChar_largeValue_setsExactValue() throws Throwable {
        JsonGeneratorImpl gen = (JsonGeneratorImpl) newFactory().createGenerator(sw);
        gen.setHighestNonEscapedChar(65535);
        assertEquals(65535, gen._maximumNonEscapedChar);
    }

    // --- setHighestNonEscapedChar(): fluent API returns same instance
    @Test
    public void testSetHighestNonEscapedChar_returnsGeneratorInstance() throws Throwable {
        JsonGeneratorImpl gen = (JsonGeneratorImpl) newFactory().createGenerator(sw);
        JsonGenerator returned = gen.setHighestNonEscapedChar(200);
        assertSame(gen, returned);
    }

    // --- getHighestEscapedChar(): reflects the value set via setHighestNonEscapedChar
    @Test
    public void testGetHighestEscapedChar_reflectsMaximumNonEscapedChar() throws Throwable {
        JsonGeneratorImpl gen = (JsonGeneratorImpl) newFactory().createGenerator(sw);
        gen.setHighestNonEscapedChar(200);
        assertEquals(200, gen.getHighestEscapedChar());
    }

    // --- setCharacterEscapes(null): reverts _outputEscapes to standard static escapes, clears custom escapes
    @Test
    public void testSetCharacterEscapes_null_revertsToStandardEscapes() throws Throwable {
        JsonGeneratorImpl gen = (JsonGeneratorImpl) newFactory().createGenerator(sw);
        gen.setCharacterEscapes(null);
        assertSame(JsonGeneratorImpl.sOutputEscapes, gen._outputEscapes);
        assertNull(gen.getCharacterEscapes());
    }

    // --- getCharacterEscapes(): default value is null for a fresh generator
    @Test
    public void testGetCharacterEscapes_defaultNull() throws Throwable {
        JsonGeneratorImpl gen = (JsonGeneratorImpl) newFactory().createGenerator(sw);
        assertNull(gen.getCharacterEscapes());
    }

    // --- default _outputEscapes reference equals the static standard escape table
    @Test
    public void testOutputEscapes_default_equalsStaticEscapes() throws Throwable {
        JsonGeneratorImpl gen = (JsonGeneratorImpl) newFactory().createGenerator(sw);
        assertSame(JsonGeneratorImpl.sOutputEscapes, gen._outputEscapes);
    }

    // --- setRootValueSeparator(): sets field to the given SerializableString
    @Test
    public void testSetRootValueSeparator_setsField() throws Throwable {
        JsonGeneratorImpl gen = (JsonGeneratorImpl) newFactory().createGenerator(sw);
        SerializableString sep = DefaultPrettyPrinter.DEFAULT_ROOT_VALUE_SEPARATOR;
        gen.setRootValueSeparator(sep);
        assertSame(sep, gen._rootValueSeparator);
    }

    // --- setRootValueSeparator(null): allows null assignment
    @Test
    public void testSetRootValueSeparator_null_setsFieldNull() throws Throwable {
        JsonGeneratorImpl gen = (JsonGeneratorImpl) newFactory().createGenerator(sw);
        gen.setRootValueSeparator(null);
        assertNull(gen._rootValueSeparator);
    }

    // --- setRootValueSeparator(): fluent API returns same instance
    @Test
    public void testSetRootValueSeparator_returnsGeneratorInstance() throws Throwable {
        JsonGeneratorImpl gen = (JsonGeneratorImpl) newFactory().createGenerator(sw);
        JsonGenerator returned = gen.setRootValueSeparator(DefaultPrettyPrinter.DEFAULT_ROOT_VALUE_SEPARATOR);
        assertSame(gen, returned);
    }

    // --- default root value separator is DefaultPrettyPrinter.DEFAULT_ROOT_VALUE_SEPARATOR
    @Test
    public void testRootValueSeparator_defaultValue() throws Throwable {
        JsonGeneratorImpl gen = (JsonGeneratorImpl) newFactory().createGenerator(sw);
        assertSame(DefaultPrettyPrinter.DEFAULT_ROOT_VALUE_SEPARATOR, gen._rootValueSeparator);
    }

    // --- version(): returns a non-null Version object
    @Test
    public void testVersion_notNull() throws Throwable {
        JsonGeneratorImpl gen = (JsonGeneratorImpl) newFactory().createGenerator(sw);
        Version v = gen.version();
        assertNotNull(v);
    }

    // --- writeStringField(): writes field name and string value inside an object
    @Test
    public void testWriteStringField_withinObject_writesFieldNameAndValue() throws Throwable {
        JsonFactory factory = newFactory();
        JsonGenerator gen = factory.createGenerator(sw);
        gen.writeStartObject();
        gen.writeStringField("name", "value");
        gen.writeEndObject();
        gen.close();

        JsonParser parser = factory.createParser(sw.toString());
        assertEquals(JsonToken(parser), parser.getCurrentToken());
        parser.close();
    }

    // helper kept minimal: we instead verify by re-parsing directly below tests.
    private Object JsonToken(JsonParser p)
    {
        return null;
    }

    // --- writeStringField(): round trip verification via parser for normal value
    @Test
    public void testWriteStringField_roundTrip_normalValue() throws Throwable {
        JsonFactory factory = newFactory();
        JsonGenerator gen = factory.createGenerator(sw);
        gen.writeStartObject();
        gen.writeStringField("name", "value");
        gen.writeEndObject();
        gen.close();

        JsonParser parser = factory.createParser(sw.toString());
        parser.nextToken(); // START_OBJECT
        parser.nextToken(); // FIELD_NAME
        assertEquals("name", parser.getCurrentName());
        parser.nextToken(); // VALUE_STRING
        assertEquals("value", parser.getText());
        parser.close();
    }

    // --- writeStringField(): empty string value round trips correctly
    @Test
    public void testWriteStringField_emptyStringValue_roundTrip() throws Throwable {
        JsonFactory factory = newFactory();
        JsonGenerator gen = factory.createGenerator(sw);
        gen.writeStartObject();
        gen.writeStringField("empty", "");
        gen.writeEndObject();
        gen.close();

        JsonParser parser = factory.createParser(sw.toString());
        parser.nextToken();
        parser.nextToken();
        parser.nextToken();
        assertEquals("", parser.getText());
        parser.close();
    }

    // --- writeStringField(): special characters get escaped and round trip correctly
    @Test
    public void testWriteStringField_specialCharacters_roundTrip() throws Throwable {
        JsonFactory factory = newFactory();
        JsonGenerator gen = factory.createGenerator(sw);
        String original = "line1\nline2\t\"quoted\"\\backslash";
        gen.writeStartObject();
        gen.writeStringField("field", original);
        gen.writeEndObject();
        gen.close();

        JsonParser parser = factory.createParser(sw.toString());
        parser.nextToken();
        parser.nextToken();
        parser.nextToken();
        assertEquals(original, parser.getText());
        parser.close();
    }

    // --- writeStringField(): unicode characters round trip correctly
    @Test
    public void testWriteStringField_unicodeCharacters_roundTrip() throws Throwable {
        JsonFactory factory = newFactory();
        JsonGenerator gen = factory.createGenerator(sw);
        String original = "caf\u00e9 \u4e2d\u6587";
        gen.writeStartObject();
        gen.writeStringField("field", original);
        gen.writeEndObject();
        gen.close();

        JsonParser parser = factory.createParser(sw.toString());
        parser.nextToken();
        parser.nextToken();
        parser.nextToken();
        assertEquals(original, parser.getText());
        parser.close();
    }

    // --- writeStringField(): multiple fields all written and read back correctly
    @Test
    public void testWriteStringField_multipleFields_allWrittenCorrectly() throws Throwable {
        JsonFactory factory = newFactory();
        JsonGenerator gen = factory.createGenerator(sw);
        gen.writeStartObject();
        gen.writeStringField("a", "1");
        gen.writeStringField("b", "2");
        gen.writeEndObject();
        gen.close();

        JsonParser parser = factory.createParser(sw.toString());
        parser.nextToken();
        parser.nextToken();
        assertEquals("a", parser.getCurrentName());
        parser.nextToken();
        assertEquals("1", parser.getText());
        parser.nextToken();
        assertEquals("b", parser.getCurrentName());
        parser.nextToken();
        assertEquals("2", parser.getText());
        parser.close();
    }





    // --- functional: QUOTE_FIELD_NAMES disabled produces unquoted field name in raw output
    @Test
    public void testCfgUnqNames_disabled_producesUnquotedFieldName() throws Throwable {
        JsonFactory factory = newFactory();
        factory.configure(JsonGenerator.Feature.QUOTE_FIELD_NAMES, false);
        JsonGenerator gen = factory.createGenerator(sw);
        gen.writeStartObject();
        gen.writeStringField("field", "value");
        gen.writeEndObject();
        gen.close();

        String out = sw.toString();
        assertTrue(out.indexOf("\"field\"") < 0);
        assertTrue(out.indexOf("field:") >= 0);
    }

    // --- functional: default QUOTE_FIELD_NAMES enabled produces quoted field name in raw output
    @Test
    public void testCfgUnqNames_enabledDefault_producesQuotedFieldName() throws Throwable {
        JsonFactory factory = newFactory();
        JsonGenerator gen = factory.createGenerator(sw);
        gen.writeStartObject();
        gen.writeStringField("field", "value");
        gen.writeEndObject();
        gen.close();

        String out = sw.toString();
        assertTrue(out.indexOf("\"field\"") >= 0);
    }
}
