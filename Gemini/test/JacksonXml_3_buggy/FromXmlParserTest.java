package com.fasterxml.jackson.dataformat.xml.deser;

import org.junit.Test;
import static org.junit.Assert.*;

import java.io.ByteArrayInputStream;
import java.io.InputStream;
import java.nio.charset.Charset;
import java.util.HashSet;
import java.util.Set;

import javax.xml.stream.XMLInputFactory;
import javax.xml.stream.XMLStreamReader;

import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.core.JsonToken;
import com.fasterxml.jackson.core.io.IOContext;
import com.fasterxml.jackson.core.util.BufferRecycler;
import com.fasterxml.jackson.dataformat.xml.XmlMapper;

public class FromXmlParserTest {

    private FromXmlParser createParser(String xml) throws Throwable {
        XMLInputFactory f = XMLInputFactory.newFactory();
        InputStream in = new ByteArrayInputStream(xml.getBytes(Charset.forName("UTF-8")));
        XMLStreamReader sr = f.createXMLStreamReader(in);
        IOContext ctxt = new IOContext(new BufferRecycler(), sr, false);
        return new FromXmlParser(ctxt, 0, 0, new XmlMapper(), sr);
    }

    @Test
    public void testFeatureCollectDefaults() throws Throwable {
        int defaults = FromXmlParser.Feature.collectDefaults();
        assertEquals(0, defaults);
    }

    @Test
    public void testFeatureMethods() throws Throwable {
        FromXmlParser.Feature f = null;
        // Feature has no enum constants currently, but we can verify class loading
        assertNotNull(FromXmlParser.DEFAULT_UNNAMED_TEXT_PROPERTY);
    }

    @Test
    public void testLifecycleAndConfiguration() throws Throwable {
        FromXmlParser parser = createParser("<root></root>");
        
        assertNotNull(parser.version());
        assertNotNull(parser.getCodec());
        parser.setCodec(parser.getCodec());
        
        assertTrue(parser.requiresCustomCodec());
        
        parser.setXMLTextElementName("customValue");
        
        assertFalse(parser.isClosed());
        assertNotNull(parser.getStaxReader());
        
        // Format features
        assertEquals(0, parser.getFormatFeatures());
        parser.overrideFormatFeatures(1, 1);
        assertEquals(1, parser.getFormatFeatures());
        
        parser.close();
        assertTrue(parser.isClosed());
        
        // Double close should be safe
        parser.close();
    }

    @Test
    public void testVirtualWrapping() throws Throwable {
        FromXmlParser parser = createParser("<root><item>a</item></root>");
        Set<String> wrap = new HashSet<String>();
        wrap.add("item");
        parser.addVirtualWrapping(wrap);
        
        parser.close();
    }

    @Test
    public void testGetTextMethods() throws Throwable {
        FromXmlParser parser = createParser("<root>text</root>");
        assertNull(parser.getText());
        assertNull(parser.getTextCharacters());
        assertEquals(0, parser.getTextLength());
        assertEquals(0, parser.getTextOffset());
        assertFalse(parser.hasTextCharacters());
        
        parser.close();
    }

    @Test
    public void testEmbeddedObjectAndBinary() throws Throwable {
        FromXmlParser parser = createParser("<root>dGVzdA==</root>");
        assertNull(parser.getEmbeddedObject());
        
        try {
            parser.getBinaryValue(null);
            fail("Expected exception for token not being VALUE_STRING");
        } catch (com.fasterxml.jackson.core.JsonParseException e) {
            assertTrue(e.getMessage().contains("not VALUE_STRING"));
        }
        
        parser.close();
    }

    @Test
    public void testNumericAccessorsStubs() throws Throwable {
        FromXmlParser parser = createParser("<root>123</root>");
        assertNull(parser.getBigIntegerValue());
        assertNull(parser.getDecimalValue());
        assertEquals(0.0, parser.getDoubleValue(), 0.0);
        assertEquals(0.0f, parser.getFloatValue(), 0.0f);
        assertEquals(0, parser.getIntValue());
        assertEquals(0L, parser.getLongValue());
        assertNull(parser.getNumberType());
        assertNull(parser.getNumberValue());
        parser.close();
    }

    @Test
    public void testHandleEOF() throws Throwable {
        FromXmlParser parser = createParser("<root></root>");
        try {
            parser._handleEOF();
            // Root context is in root, so no exception expected
        } catch (Throwable t) {
            fail("Should not throw EOF exception in root context");
        }
        parser.close();
    }

    @Test
    public void testIsEmptyHelper() throws Throwable {
        FromXmlParser parser = createParser("<root/>");
        // Accessing via subclass or public behaviors if possible, or test parser parsing
        assertNotNull(parser);
        parser.close();
    }
}