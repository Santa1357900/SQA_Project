package com.fasterxml.jackson.dataformat.xml.deser;

import java.io.IOException;

import org.junit.Before;
import org.junit.Test;
import static org.junit.Assert.*;

import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.core.JsonToken;
import com.fasterxml.jackson.core.JsonParseException;
import com.fasterxml.jackson.core.Base64Variants;
import com.fasterxml.jackson.core.Version;
import com.fasterxml.jackson.dataformat.xml.XmlMapper;

public class FromXmlParserClaudeTest
{
    private XmlMapper mapper;

    @Before
    public void setUp() throws Throwable
    {
        mapper = new XmlMapper();
    }

    private JsonParser createParser(String xml) throws IOException
    {
        return mapper.getFactory().createParser(xml);
    }

    // version() must return a real, known Version instance
    @Test
    public void testVersion_notNull() throws Throwable {
        JsonParser p = createParser("<root/>");
        Version v = ((FromXmlParser) p).version();
        assertNotNull(v);
        assertFalse(v.isUnknownVersion());
    }

    // getCodec()/setCodec() simple round trip
    @Test
    public void testSetCodecGetCodec_roundTrip() throws Throwable {
        JsonParser p = createParser("<root/>");
        p.setCodec(mapper);
        assertSame(mapper, p.getCodec());
        p.setCodec(null);
        assertNull(p.getCodec());
    }

    // requiresCustomCodec() must always return true
    @Test
    public void testRequiresCustomCodec_true() throws Throwable {
        JsonParser p = createParser("<root/>");
        assertTrue(((FromXmlParser) p).requiresCustomCodec());
    }

    // setXMLTextElementName() changes property name used for text segments
    @Test
    public void testSetXMLTextElementName_changesTextFieldName() throws Throwable {
        JsonParser p = createParser("<root>hello</root>");
        ((FromXmlParser) p).setXMLTextElementName("value");
        assertEquals(JsonToken.START_OBJECT, p.nextToken());
        assertEquals(JsonToken.FIELD_NAME, p.nextToken());
        assertEquals("value", p.getCurrentName());
    }

    // overrideFormatFeatures: values/mask bit computation
    @Test
    public void testOverrideFormatFeatures_updatesFlags() throws Throwable {
        JsonParser p = createParser("<root/>");
        FromXmlParser fp = (FromXmlParser) p;
        fp.overrideFormatFeatures(5, 7);
        assertEquals(5, fp.getFormatFeatures() & 7);
    }

    // getStaxReader() must expose underlying reader
    @Test
    public void testGetStaxReader_notNull() throws Throwable {
        JsonParser p = createParser("<root/>");
        assertNotNull(((FromXmlParser) p).getStaxReader());
    }

    // getCurrentName() throws IllegalStateException when no name available
    @Test
    public void testGetCurrentName_throwsIllegalStateWhenNoCurrentToken() throws Throwable {
        JsonParser p = createParser("<root/>");
        try {
            p.getCurrentName();
            fail("expected IllegalStateException");
        } catch (IllegalStateException expected) { }
    }

    // getCurrentName() after FIELD_NAME token returns element local name
    @Test
    public void testGetCurrentName_returnsNameAfterFieldName() throws Throwable {
        JsonParser p = createParser("<root><a>1</a></root>");
        p.nextToken();
        p.nextToken();
        assertEquals("a", p.getCurrentName());
    }

    // overrideCurrentName() replaces current field name
    @Test
    public void testOverrideCurrentName_afterFieldName() throws Throwable {
        JsonParser p = createParser("<root><a>1</a></root>");
        p.nextToken();
        p.nextToken();
        p.overrideCurrentName("b");
        assertEquals("b", p.getCurrentName());
    }

    // close() sets closed flag and is idempotent
    @Test
    public void testClose_setsClosedTrueAndIdempotent() throws Throwable {
        JsonParser p = createParser("<root/>");
        p.close();
        assertTrue(p.isClosed());
        p.close();
        assertTrue(p.isClosed());
    }

    // getParsingContext() reflects object state after entering root object
    @Test
    public void testGetParsingContext_reflectsObjectState() throws Throwable {
        JsonParser p = createParser("<root><a>1</a></root>");
        p.nextToken();
        assertTrue(p.getParsingContext().inObject());
    }

    // getTokenLocation()/getCurrentLocation() must be non-null after a token
    @Test
    public void testTokenAndCurrentLocation_notNull() throws Throwable {
        JsonParser p = createParser("<root>1</root>");
        p.nextToken();
        assertNotNull(p.getTokenLocation());
        assertNotNull(p.getCurrentLocation());
    }

    // isExpectedStartArrayToken(): converts START_OBJECT into START_ARRAY
    @Test
    public void testIsExpectedStartArrayToken_convertsStartObject() throws Throwable {
        JsonParser p = createParser("<root><item><x>1</x></item></root>");
        p.nextToken();
        p.nextToken();
        p.nextToken();
        FromXmlParser fp = (FromXmlParser) p;
        assertTrue(fp.isExpectedStartArrayToken());
        assertEquals(JsonToken.START_ARRAY, p.getCurrentToken());
        assertTrue(p.getParsingContext().inArray());
    }

    // isExpectedStartArrayToken(): false and unchanged for scalar token
    @Test
    public void testIsExpectedStartArrayToken_falseForScalarToken() throws Throwable {
        JsonParser p = createParser("<root>1</root>");
        p.nextToken();
        p.nextToken();
        p.nextToken();
        FromXmlParser fp = (FromXmlParser) p;
        assertFalse(fp.isExpectedStartArrayToken());
        assertEquals(JsonToken.VALUE_STRING, p.getCurrentToken());
    }

    // nextToken(): first call always returns START_OBJECT (root wrapper)
    @Test
    public void testNextToken_initialTokenIsStartObject() throws Throwable {
        JsonParser p = createParser("<root/>");
        assertEquals(JsonToken.START_OBJECT, p.nextToken());
    }

    // nextToken(): simple text leaf full sequence
    @Test
    public void testNextToken_simpleTextLeafSequence() throws Throwable {
        JsonParser p = createParser("<root>hello</root>");
        assertEquals(JsonToken.START_OBJECT, p.nextToken());
        assertEquals(JsonToken.FIELD_NAME, p.nextToken());
        assertEquals("", p.getCurrentName());
        assertEquals(JsonToken.VALUE_STRING, p.nextToken());
        assertEquals("hello", p.getText());
        assertEquals(JsonToken.END_OBJECT, p.nextToken());
        assertNull(p.nextToken());
    }



    // nextToken(): self-closed root element with no children/attributes
    @Test
    public void testNextToken_selfClosedRootElement() throws Throwable {
        JsonParser p = createParser("<root/>");
        assertEquals(JsonToken.START_OBJECT, p.nextToken());
        assertEquals(JsonToken.END_OBJECT, p.nextToken());
        assertNull(p.nextToken());
    }

    // nextToken(): attribute name/value sequence on root element
    @Test
    public void testNextToken_attributeSequence() throws Throwable {
        JsonParser p = createParser("<root attr=\"val\"></root>");
        assertEquals(JsonToken.START_OBJECT, p.nextToken());
        assertEquals(JsonToken.FIELD_NAME, p.nextToken());
        assertEquals(JsonToken.VALUE_STRING, p.nextToken());
        assertEquals("val", p.getText());
        assertEquals(JsonToken.END_OBJECT, p.nextToken());
        assertNull(p.nextToken());
    }

    // nextTextValue(): returns null while advancing to FIELD_NAME, then text
    @Test
    public void testNextTextValue_returnsNullThenTextForSimpleLeaf() throws Throwable {
        JsonParser p = createParser("<root>hello</root>");
        assertEquals(JsonToken.START_OBJECT, p.nextToken());
        assertNull(p.nextTextValue());
        assertEquals(JsonToken.FIELD_NAME, p.getCurrentToken());
        assertEquals("hello", p.nextTextValue());
        assertEquals(JsonToken.VALUE_STRING, p.getCurrentToken());
    }

    // nextTextValue(): on immediate end element of a leaf, returns "" (documented)
    @Test
    public void testNextTextValue_onEndElementReturnsEmptyString() throws Throwable {
        JsonParser p = createParser("<root><a></a></root>");
        p.nextToken();
        p.nextToken();
        String txt = p.nextTextValue();
        assertEquals("", txt);
        assertEquals(JsonToken.VALUE_STRING, p.getCurrentToken());
    }

    // getText(): null when no current token yet
    @Test
    public void testGetText_nullBeforeAnyToken() throws Throwable {
        JsonParser p = createParser("<root/>");
        assertNull(p.getText());
    }

    // getText(): for FIELD_NAME equals current name
    @Test
    public void testGetText_fieldNameEqualsCurrentName() throws Throwable {
        JsonParser p = createParser("<root><a>1</a></root>");
        p.nextToken();
        p.nextToken();
        assertEquals("a", p.getText());
    }

    // getText(): for VALUE_STRING equals decoded text
    @Test
    public void testGetText_valueStringEqualsCurrText() throws Throwable {
        JsonParser p = createParser("<root>hello</root>");
        p.nextToken();
        p.nextToken();
        p.nextToken();
        assertEquals("hello", p.getText());
    }

    // getText(): default branch uses token.asString()
    @Test
    public void testGetText_defaultUsesTokenAsString() throws Throwable {
        JsonParser p = createParser("<root/>");
        p.nextToken();
        p.nextToken();
        assertEquals(JsonToken.END_OBJECT.asString(), p.getText());
    }

    // getValueAsString(): null when no current token
    @Test
    public void testGetValueAsString_nullWhenNoCurrentToken() throws Throwable {
        JsonParser p = createParser("<root/>");
        assertNull(p.getValueAsString());
    }

    // getValueAsString(): FIELD_NAME returns current name
    @Test
    public void testGetValueAsString_fieldNameReturnsName() throws Throwable {
        JsonParser p = createParser("<root><a>1</a></root>");
        p.nextToken();
        p.nextToken();
        assertEquals("a", p.getValueAsString());
        assertEquals("a", p.getValueAsString("default"));
    }

    // getValueAsString(defValue): non-scalar START_ARRAY falls back to defValue
    @Test
    public void testGetValueAsString_defaultValueForNonScalarStartArray() throws Throwable {
        JsonParser p = createParser("<root><item><x>1</x></item></root>");
        p.nextToken();
        p.nextToken();
        p.nextToken();
        FromXmlParser fp = (FromXmlParser) p;
        fp.isExpectedStartArrayToken();
        assertEquals("default", p.getValueAsString("default"));
    }

    // getTextCharacters/getTextLength/getTextOffset/hasTextCharacters for VALUE_STRING
    @Test
    public void testTextCharactersLengthOffsetHasChars_forValueString() throws Throwable {
        JsonParser p = createParser("<root>hi</root>");
        p.nextToken();
        p.nextToken();
        p.nextToken();
        assertEquals("hi", new String(p.getTextCharacters()));
        assertEquals(2, p.getTextLength());
        assertEquals(0, p.getTextOffset());
        assertFalse(p.hasTextCharacters());
    }

    // getTextCharacters()/getTextLength() when text is null
    @Test
    public void testGetTextCharacters_nullWhenTextNull() throws Throwable {
        JsonParser p = createParser("<root/>");
        assertNull(p.getTextCharacters());
        assertEquals(0, p.getTextLength());
    }

    // getEmbeddedObject() always returns null
    @Test
    public void testGetEmbeddedObject_isNull() throws Throwable {
        JsonParser p = createParser("<root>1</root>");
        p.nextToken();
        assertNull(p.getEmbeddedObject());
    }

    // getBinaryValue(): decodes base64 text of VALUE_STRING correctly
    @Test
    public void testGetBinaryValue_decodesBase64Correctly() throws Throwable {
        JsonParser p = createParser("<root>aGVsbG8=</root>");
        p.nextToken();
        p.nextToken();
        p.nextToken();
        byte[] bytes = p.getBinaryValue(Base64Variants.getDefaultVariant());
        assertEquals("hello", new String(bytes));
    }

    // getBinaryValue(): throws JsonParseException when current token is not a string
    @Test
    public void testGetBinaryValue_throwsWhenNotValueString() throws Throwable {
        JsonParser p = createParser("<root><a>1</a></root>");
        p.nextToken();
        try {
            p.getBinaryValue(Base64Variants.getDefaultVariant());
            fail("expected JsonParseException");
        } catch (JsonParseException expected) { }
    }

    // numeric accessors are documented stubs returning defaults regardless of token
    @Test
    public void testNumericStubAccessors_returnDefaults() throws Throwable {
        JsonParser p = createParser("<root>1</root>");
        p.nextToken();
        p.nextToken();
        p.nextToken();
        assertNull(p.getBigIntegerValue());
        assertNull(p.getDecimalValue());
        assertEquals(0.0, p.getDoubleValue(), 1e-9);
        assertEquals(0.0f, p.getFloatValue(), 1e-9f);
        assertEquals(0, p.getIntValue());
        assertEquals(0L, p.getLongValue());
        assertNull(p.getNumberType());
        assertNull(p.getNumberValue());
    }

    // _handleEOF(): does not throw while parsing context is still root
    @Test
    public void test_handleEOF_inRoot_doesNotThrow() throws Throwable {
        JsonParser pj = createParser("<root/>");
        FromXmlParser p = (FromXmlParser) pj;
        p._handleEOF();
        assertTrue(p.getParsingContext().inRoot());
    }

    // _handleEOF(): throws JsonParseException when an object/array is still open
    @Test
    public void test_handleEOF_notInRoot_throwsJsonParseException() throws Throwable {
        JsonParser pj = createParser("<root><a>1</a></root>");
        pj.nextToken();
        FromXmlParser p = (FromXmlParser) pj;
        try {
            p._handleEOF();
            fail("expected JsonParseException");
        } catch (JsonParseException expected) { }
    }

    // _isEmpty(): null, empty and whitespace-only strings are considered empty
    @Test
    public void test_isEmpty_trueForNullEmptyOrWhitespace() throws Throwable {
        JsonParser pj = createParser("<root/>");
        FromXmlParser p = (FromXmlParser) pj;
        assertTrue(p._isEmpty(null));
        assertTrue(p._isEmpty(""));
        assertTrue(p._isEmpty("   "));
    }

    // _isEmpty(): strings with non-whitespace characters are not empty
    @Test
    public void test_isEmpty_falseForNonWhitespace() throws Throwable {
        JsonParser pj = createParser("<root/>");
        FromXmlParser p = (FromXmlParser) pj;
        assertFalse(p._isEmpty("a"));
        assertFalse(p._isEmpty(" a "));
    }
}
