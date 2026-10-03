package com.fasterxml.jackson.dataformat.xml.deser;

import java.io.IOException;

import org.junit.Before;
import org.junit.Test;
import static org.junit.Assert.*;

import com.fasterxml.jackson.core.Base64Variants;
import com.fasterxml.jackson.core.JsonParseException;
import com.fasterxml.jackson.core.JsonToken;
import com.fasterxml.jackson.dataformat.xml.XmlMapper;

public class FromXmlParserClaudeTest
{
    private XmlMapper mapper;

    @Before
    public void setUp() throws Throwable {
        mapper = new XmlMapper();
    }

    private FromXmlParser _parser(String xml) throws IOException {
        return (FromXmlParser) mapper.getFactory().createParser(xml);
    }

    // nextToken(): simple element with single text child -> START_OBJECT/FIELD_NAME/VALUE_STRING/END_OBJECT/null
    @Test
    public void testNextToken_simpleTextChild_returnsExpectedSequence() throws Throwable {
        FromXmlParser p = _parser("<root><a>1</a></root>");
        assertEquals(JsonToken.START_OBJECT, p.nextToken());
        assertEquals(JsonToken.FIELD_NAME, p.nextToken());
        assertEquals("a", p.getCurrentName());
        assertEquals(JsonToken.VALUE_STRING, p.nextToken());
        assertEquals("1", p.getText());
        assertEquals(JsonToken.END_OBJECT, p.nextToken());
        assertNull(p.nextToken());
    }

    // getCurrentName(): at the very first START_OBJECT (root) parent has no name -> IllegalStateException
    @Test
    public void testGetCurrentName_atRootStartObject_throwsIllegalStateException() throws Throwable {
        FromXmlParser p = _parser("<root><a>1</a></root>");
        p.nextToken();
        try {
            p.getCurrentName();
            fail("expected IllegalStateException");
        } catch (IllegalStateException expected) { }
    }

    // overrideCurrentName(): at START_OBJECT token name is set on parent context (off-by-one handling)
    @Test
    public void testOverrideCurrentName_atStartObject_updatesParentContext() throws Throwable {
        FromXmlParser p = _parser("<root><a>1</a></root>");
        p.nextToken();
        p.overrideCurrentName("newname");
        assertEquals("newname", p.getCurrentName());
    }

    // overrideCurrentName(): at FIELD_NAME token, updates current context directly
    @Test
    public void testOverrideCurrentName_atFieldName_updatesCurrentContext() throws Throwable {
        FromXmlParser p = _parser("<root><a>1</a></root>");
        p.nextToken();
        p.nextToken();
        p.overrideCurrentName("zzz");
        assertEquals("zzz", p.getCurrentName());
    }

    // version(): must return a non-null Version object
    @Test
    public void testVersion_returnsNonNullVersion() throws Throwable {
        FromXmlParser p = _parser("<root/>");
        assertNotNull(p.version());
    }

    // getCodec()/setCodec(): value set is the value returned
    @Test
    public void testGetCodec_setCodec_returnsConfiguredCodec() throws Throwable {
        FromXmlParser p = _parser("<root/>");
        XmlMapper other = new XmlMapper();
        p.setCodec(other);
        assertSame(other, p.getCodec());
    }

    // requiresCustomCodec(): always true for XML format
    @Test
    public void testRequiresCustomCodec_returnsTrue() throws Throwable {
        FromXmlParser p = _parser("<root/>");
        assertTrue(p.requiresCustomCodec());
    }

    // setXMLTextElementName(): overrides default "" pseudo-property name used for text content
    @Test
    public void testSetXMLTextElementName_changesDefaultTextPropertyName() throws Throwable {
        FromXmlParser p = _parser("<root>text</root>");
        p.setXMLTextElementName("value");
        p.nextToken(); // START_OBJECT
        assertEquals(JsonToken.FIELD_NAME, p.nextToken());
        assertEquals("value", p.getCurrentName());
    }

    // getFormatFeatures(): default value is 0 since no Feature constants are defined
    @Test
    public void testGetFormatFeatures_defaultIsZero() throws Throwable {
        FromXmlParser p = _parser("<root/>");
        assertEquals(0, p.getFormatFeatures());
    }

    // overrideFormatFeatures(): bit mask update logic
    @Test
    public void testOverrideFormatFeatures_updatesBitsCorrectly() throws Throwable {
        FromXmlParser p = _parser("<root/>");
        p.overrideFormatFeatures(1, 1);
        assertEquals(1, p.getFormatFeatures());
        p.overrideFormatFeatures(0, 1);
        assertEquals(0, p.getFormatFeatures());
    }

    // getStaxReader(): must expose underlying stream reader
    @Test
    public void testGetStaxReader_returnsNonNull() throws Throwable {
        FromXmlParser p = _parser("<root/>");
        assertNotNull(p.getStaxReader());
    }

    // isExpectedStartArrayToken(): converts START_OBJECT into START_ARRAY, array elements skip FIELD_NAME
    @Test
    public void testIsExpectedStartArrayToken_onStartObject_convertsToArrayAndElements() throws Throwable {
        FromXmlParser p = _parser("<root><a>1</a></root>");
        p.nextToken();
        assertTrue(p.isExpectedStartArrayToken());
        assertTrue(p.getParsingContext().inArray());
        assertEquals(JsonToken.VALUE_STRING, p.nextToken());
        assertEquals("1", p.getText());
        assertEquals(JsonToken.END_ARRAY, p.nextToken());
    }

    // isExpectedStartArrayToken(): non start-object/array token returns false
    @Test
    public void testIsExpectedStartArrayToken_onFieldName_returnsFalse() throws Throwable {
        FromXmlParser p = _parser("<root><a>1</a></root>");
        p.nextToken();
        p.nextToken(); // FIELD_NAME
        assertFalse(p.isExpectedStartArrayToken());
    }

    // close(): sets closed flag, and is idempotent
    @Test
    public void testClose_setsClosedTrueAndIdempotent() throws Throwable {
        FromXmlParser p = _parser("<root/>");
        p.close();
        assertTrue(p.isClosed());
        p.close();
        assertTrue(p.isClosed());
    }

    // isClosed(): false before close is invoked
    @Test
    public void testIsClosed_initiallyFalse() throws Throwable {
        FromXmlParser p = _parser("<root/>");
        assertFalse(p.isClosed());
    }

    // getParsingContext(): starts out as root context before any token is consumed
    @Test
    public void testGetParsingContext_initiallyInRoot() throws Throwable {
        FromXmlParser p = _parser("<root/>");
        assertTrue(p.getParsingContext().inRoot());
    }

    // getTokenLocation(): must be non-null
    @Test
    public void testGetTokenLocation_returnsNonNull() throws Throwable {
        FromXmlParser p = _parser("<root/>");
        p.nextToken();
        assertNotNull(p.getTokenLocation());
    }

    // getCurrentLocation(): must be non-null
    @Test
    public void testGetCurrentLocation_returnsNonNull() throws Throwable {
        FromXmlParser p = _parser("<root/>");
        p.nextToken();
        assertNotNull(p.getCurrentLocation());
    }

    // nextToken(): empty child element (no text/attrs) is exposed as VALUE_NULL when not in array
    @Test
    public void testNextToken_emptyChildElement_producesValueNull() throws Throwable {
        FromXmlParser p = _parser("<root><a></a></root>");
        p.nextToken(); // START_OBJECT
        assertEquals(JsonToken.FIELD_NAME, p.nextToken());
        assertEquals(JsonToken.VALUE_NULL, p.nextToken());
        assertEquals(JsonToken.END_OBJECT, p.nextToken());
        assertNull(p.nextToken());
    }

    // nextToken(): attribute-only element exposes attribute as a field/value pair
    @Test
    public void testNextToken_attributeOnlyElement_producesFieldAndValue() throws Throwable {
        FromXmlParser p = _parser("<root attr=\"v\"/>");
        p.nextToken(); // START_OBJECT
        assertEquals(JsonToken.FIELD_NAME, p.nextToken());
        assertEquals("attr", p.getCurrentName());
        assertEquals(JsonToken.VALUE_STRING, p.nextToken());
        assertEquals("v", p.getText());
        assertEquals(JsonToken.END_OBJECT, p.nextToken());
    }

    // nextToken(): element having both attribute and text creates a bogus pseudo-property for the text
    @Test
    public void testNextToken_attributeWithText_createsBogusTextProperty() throws Throwable {
        FromXmlParser p = _parser("<root attr=\"v\">text</root>");
        p.nextToken(); // START_OBJECT
        p.nextToken(); // FIELD_NAME "attr"
        p.nextToken(); // VALUE_STRING "v"
        assertEquals(JsonToken.FIELD_NAME, p.nextToken());
        assertEquals(FromXmlParser.DEFAULT_UNNAMED_TEXT_PROPERTY, p.getCurrentName());
        assertEquals(JsonToken.VALUE_STRING, p.nextToken());
        assertEquals("text", p.getText());
    }

    // nextTextValue(): returns null while positioned on a FIELD_NAME, then the actual text directly
    @Test
    public void testNextTextValue_sequenceForTextChild() throws Throwable {
        FromXmlParser p = _parser("<root><a>1</a></root>");
        p.nextToken(); // START_OBJECT
        assertNull(p.nextTextValue());
        assertEquals(JsonToken.FIELD_NAME, p.getCurrentToken());
        assertEquals("a", p.getCurrentName());
        assertEquals("1", p.nextTextValue());
        assertEquals(JsonToken.VALUE_STRING, p.getCurrentToken());
    }

    // nextTextValue(): empty element returns "" (differs from nextToken()'s VALUE_NULL behavior)
    @Test
    public void testNextTextValue_emptyElement_returnsEmptyString() throws Throwable {
        FromXmlParser p = _parser("<root><a></a></root>");
        p.nextToken(); // START_OBJECT
        p.nextTextValue(); // FIELD_NAME "a"
        assertEquals("", p.nextTextValue());
        assertEquals(JsonToken.VALUE_STRING, p.getCurrentToken());
    }

    // getText(): for structural tokens falls back to JsonToken.asString()
    @Test
    public void testGetText_atStartObject_returnsBraceString() throws Throwable {
        FromXmlParser p = _parser("<root/>");
        p.nextToken();
        assertEquals("{", p.getText());
    }

    // getText(): returns null once current token is null (EOF)
    @Test
    public void testGetText_atEof_returnsNull() throws Throwable {
        FromXmlParser p = _parser("<root/>");
        while (p.nextToken() != null) { }
        assertNull(p.getText());
    }

    // getValueAsString(): on FIELD_NAME returns current field name
    @Test
    public void testGetValueAsString_atFieldName_returnsCurrentName() throws Throwable {
        FromXmlParser p = _parser("<root><a>1</a></root>");
        p.nextToken();
        p.nextToken(); // FIELD_NAME "a"
        assertEquals("a", p.getValueAsString());
    }

    // getValueAsString(): on VALUE_STRING returns the text content
    @Test
    public void testGetValueAsString_atValueString_returnsText() throws Throwable {
        FromXmlParser p = _parser("<root><a>1</a></root>");
        p.nextToken();
        p.nextToken();
        p.nextToken(); // VALUE_STRING
        assertEquals("1", p.getValueAsString());
    }

    // getTextCharacters(): matches characters of getText()
    @Test
    public void testGetTextCharacters_matchesText() throws Throwable {
        FromXmlParser p = _parser("<root><a>1</a></root>");
        p.nextToken();
        p.nextToken();
        p.nextToken(); // VALUE_STRING "1"
        assertEquals("1", new String(p.getTextCharacters()));
    }

    // getTextLength(): matches length of current text
    @Test
    public void testGetTextLength_matchesTextLength() throws Throwable {
        FromXmlParser p = _parser("<root>text</root>");
        p.nextToken(); // START_OBJECT
        p.nextToken(); // FIELD_NAME ""
        p.nextToken(); // VALUE_STRING "text"
        assertEquals(4, p.getTextLength());
    }

    // getTextOffset(): always 0, text cannot be exposed as a partial segment
    @Test
    public void testGetTextOffset_alwaysZero() throws Throwable {
        FromXmlParser p = _parser("<root><a>1</a></root>");
        p.nextToken();
        assertEquals(0, p.getTextOffset());
    }

    // hasTextCharacters(): always false since text cannot be exposed as raw char array segment
    @Test
    public void testHasTextCharacters_alwaysFalse() throws Throwable {
        FromXmlParser p = _parser("<root><a>1</a></root>");
        p.nextToken();
        assertFalse(p.hasTextCharacters());
    }

    // getEmbeddedObject(): always null, no POJO embedding support
    @Test
    public void testGetEmbeddedObject_returnsNull() throws Throwable {
        FromXmlParser p = _parser("<root/>");
        p.nextToken();
        assertNull(p.getEmbeddedObject());
    }

    // getBinaryValue(): decodes base64-encoded text content correctly
    @Test
    public void testGetBinaryValue_decodesBase64Text() throws Throwable {
        FromXmlParser p = _parser("<root><a>aGVsbG8=</a></root>");
        p.nextToken();
        p.nextToken();
        p.nextToken(); // VALUE_STRING "aGVsbG8="
        byte[] result = p.getBinaryValue(Base64Variants.getDefaultVariant());
        assertEquals("hello", new String(result, "UTF-8"));
    }

    // getBinaryValue(): on non-string token reports an error
    @Test
    public void testGetBinaryValue_onNonStringToken_throwsJsonParseException() throws Throwable {
        FromXmlParser p = _parser("<root/>");
        p.nextToken(); // START_OBJECT
        try {
            p.getBinaryValue(Base64Variants.getDefaultVariant());
            fail("expected JsonParseException");
        } catch (JsonParseException expected) { }
    }

    // numeric accessors: all are stub implementations returning default/zero/null values
    @Test
    public void testNumericStubs_returnDefaultValues() throws Throwable {
        FromXmlParser p = _parser("<root/>");
        assertNull(p.getBigIntegerValue());
        assertNull(p.getDecimalValue());
        assertEquals(0.0, p.getDoubleValue(), 0.0);
        assertEquals(0.0f, p.getFloatValue(), 0.0f);
        assertEquals(0, p.getIntValue());
        assertEquals(0L, p.getLongValue());
        assertNull(p.getNumberType());
        assertNull(p.getNumberValue());
    }
}
