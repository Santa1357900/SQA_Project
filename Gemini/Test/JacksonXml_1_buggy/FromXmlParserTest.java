package com.fasterxml.jackson.dataformat.xml.deser;

import org.junit.Test;
import static org.junit.Assert.*;

import java.io.StringReader;
import java.util.HashSet;
import java.util.Set;

import javax.xml.stream.XMLInputFactory;
import javax.xml.stream.XMLStreamReader;

import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.core.JsonToken;
import com.fasterxml.jackson.core.io.IOContext;
import com.fasterxml.jackson.core.util.BufferRecycler;

public class FromXmlParserTest {

    @Test
    public void testFeatureCollectDefaults() throws Throwable {
        int defaults = FromXmlParser.Feature.collectDefaults();
        assertEquals(0, defaults);
    }

    @Test
    public void testLifecycleAndConfig() throws Throwable {
        XMLInputFactory f = XMLInputFactory.newInstance();
        XMLStreamReader sr = f.createXMLStreamReader(new StringReader("<root><item>text</item></root>"));
        IOContext ctxt = new IOContext(new BufferRecycler(), sr, false);
        
        FromXmlParser parser = new FromXmlParser(ctxt, 0, 0, null, sr);
        
        assertNotNull(parser.version());
        assertNull(parser.getCodec());
        parser.setCodec(null);
        assertTrue(parser.requiresCustomCodec());
        
        assertFalse(parser.isClosed());
        parser.setXMLTextElementName("customValue");
        
        parser.enable(FromXmlParser.Feature.values().length == 0 ? null : FromXmlParser.Feature.values()); // dummy feature check or direct configuration
        parser.configure(null, true);
        
        assertEquals(0, parser.getFormatFeatures());
        assertSame(parser, parser.overrideFormatFeatures(1, 1));
        
        assertNotNull(parser.getStaxReader());
        
        parser.close();
        assertTrue(parser.isClosed());
        
        // idempotent close
        parser.close();
    }

    @Test
    public void testFeatureMethods() throws Throwable {
        XMLInputFactory f = XMLInputFactory.newInstance();
        XMLStreamReader sr = f.createXMLStreamReader(new StringReader("<root/>"));
        IOContext ctxt = new IOContext(new BufferRecycler(), sr, false);
        FromXmlParser parser = new FromXmlParser(ctxt, 0, 0, null, sr);

        // Feature enum is empty, but we can verify methods if any or cover branches
        assertEquals(0, parser.getFormatFeatures());
    }

    @Test
    public void testNavigationAndTokens() throws Throwable {
        XMLInputFactory f = XMLInputFactory.newInstance();
        XMLStreamReader sr = f.createXMLStreamReader(new StringReader("<root attr=\"val\">hello</root>"));
        IOContext ctxt = new IOContext(new BufferRecycler(), sr, false);
        FromXmlParser parser = new FromXmlParser(ctxt, 0, 0, null, sr);

        JsonToken t = parser.nextToken();
        assertEquals(JsonToken.START_OBJECT, t);
        
        t = parser.nextToken();
        assertEquals(JsonToken.FIELD_NAME, t);
        assertEquals("attr", parser.getCurrentName());
        
        t = parser.nextToken();
        assertEquals(JsonToken.VALUE_STRING, t);
        assertEquals("val", parser.getText());
        
        t = parser.nextToken();
        // Next should handle text
        assertNotNull(t);
        
        parser.close();
    }

    @Test
    public void testAddVirtualWrapping() throws Throwable {
        XMLInputFactory f = XMLInputFactory.newInstance();
        XMLStreamReader sr = f.createXMLStreamReader(new StringReader("<root><item>a</item></root>"));
        IOContext ctxt = new IOContext(new BufferRecycler(), sr, false);
        FromXmlParser parser = new FromXmlParser(ctxt, 0, 0, null, sr);

        Set<String> wrap = new HashSet<String>();
        wrap.add("item");
        parser.addVirtualWrapping(wrap);
        
        parser.close();
    }

    @Test
    public void testGetTextMethods() throws Throwable {
        XMLInputFactory f = XMLInputFactory.newInstance();
        XMLStreamReader sr = f.createXMLStreamReader(new StringReader("<root>content</root>"));
        IOContext ctxt = new IOContext(new BufferRecycler(), sr, false);
        FromXmlParser parser = new FromXmlParser(ctxt, 0, 0, null, sr);

        assertNull(parser.getText());
        assertNull(parser.getTextCharacters());
        assertEquals(0, parser.getTextLength());
        assertEquals(0, parser.getTextOffset());
        assertFalse(parser.hasTextCharacters());
        
        parser.close();
    }

    @Test
    public void testNumericAndBinaryAccessors() throws Throwable {
        XMLInputFactory f = XMLInputFactory.newInstance();
        XMLStreamReader sr = f.createXMLStreamReader(new StringReader("<root>123</root>"));
        IOContext ctxt = new IOContext(new BufferRecycler(), sr, false);
        FromXmlParser parser = new FromXmlParser(ctxt, 0, 0, null, sr);

        assertNull(parser.getBigIntegerValue());
        assertNull(parser.getDecimalValue());
        assertEquals(0.0, parser.getDoubleValue(), 0.0);
        assertEquals(0.0f, parser.getFloatValue(), 0.0f);
        assertEquals(0, parser.getIntValue());
        assertEquals(0L, parser.getLongValue());
        assertNull(parser.getNumberType());
        assertNull(parser.getNumberValue());
        assertNull(parser.getEmbeddedObject());
        
        parser.close();
    }

    @Test
    public void testEmptyCheckAndInternal() throws Throwable {
        XMLInputFactory f = XMLInputFactory.newInstance();
        XMLStreamReader sr = f.createXMLStreamReader(new StringReader("<root/>"));
        IOContext ctxt = new IOContext(new BufferRecycler(), sr, false);
        FromXmlParser parser = new FromXmlParser(ctxt, 0, 0, null, sr);

        // Test getValueAsString
        assertNull(parser.getValueAsString());
        assertNull(parser.getValueAsString("default"));

        parser.close();
    }

    @Test
    public void testIsExpectedStartArrayToken() throws Throwable {
        XMLInputFactory f = XMLInputFactory.newInstance();
        XMLStreamReader sr = f.createXMLStreamReader(new StringReader("<root/>"));
        IOContext ctxt = new IOContext(new BufferRecycler(), sr, false);
        FromXmlParser parser = new FromXmlParser(ctxt, 0, 0, null, sr);

        assertFalse(parser.isExpectedStartArrayToken());
        parser.close();
    }

    @Test
    public void testNextTextValue() throws Throwable {
        XMLInputFactory f = XMLInputFactory.newInstance();
        XMLStreamReader sr = f.createXMLStreamReader(new StringReader("<root>text</root>"));
        IOContext ctxt = new IOContext(new BufferRecycler(), sr, false);
        FromXmlParser parser = new FromXmlParser(ctxt, 0, 0, null, sr);

        assertNull(parser.nextTextValue());
        parser.close();
    }

    @Test
    public void testOverrideCurrentName() throws Throwable {
        XMLInputFactory f = XMLInputFactory.newInstance();
        XMLStreamReader sr = f.createXMLStreamReader(new StringReader("<root/>"));
        IOContext ctxt = new IOContext(new BufferRecycler(), sr, false);
        FromXmlParser parser = new FromXmlParser(ctxt, 0, 0, null, sr);

        parser.overrideCurrentName("newName");
        parser.close();
    }
}