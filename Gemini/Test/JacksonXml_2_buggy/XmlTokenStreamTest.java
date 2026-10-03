package com.fasterxml.jackson.dataformat.xml.deser;

import org.junit.Test;
import static org.junit.Assert.*;

import java.io.StringReader;
import javax.xml.stream.XMLInputFactory;
import javax.xml.stream.XMLStreamReader;
import javax.xml.stream.XMLStreamConstants;

public class XmlTokenStreamTest {

    @Test
    public void testConstructorWithInvalidEventType() throws Throwable {
        XMLInputFactory f = XMLInputFactory.newFactory();
        XMLStreamReader sr = f.createXMLStreamReader(new StringReader("<!-- comment --><a>text</a>"));
        
        // Advance past START_ELEMENT to test IllegalArgumentException
        while (sr.hasNext()) {
            int event = sr.next();
            if (event == XMLStreamConstants.CHARACTERS || event == XMLStreamConstants.COMMENT) {
                break;
            }
        }

        try {
            new XmlTokenStream(sr, "sourceRef");
            fail("Expected IllegalArgumentException for invalid starting event");
        } catch (IllegalArgumentException e) {
            assertTrue(e.getMessage().contains("Invalid XMLStreamReader passed"));
        }
    }

    @Test
    public void testBasicParsingAndGetters() throws Throwable {
        XMLInputFactory f = XMLInputFactory.newFactory();
        XMLStreamReader sr = f.createXMLStreamReader(new StringReader("<root attr1=\"val1\">hello</root>"));
        
        XmlTokenStream stream = new XmlTokenStream(sr, "testRef");
        
        assertEquals(XmlTokenStream.XML_START_ELEMENT, stream.getCurrentToken());
        assertEquals("root", stream.getLocalName());
        assertNotNull(stream.getNamespaceURI());
        assertTrue(stream.hasAttributes());
        assertNotNull(stream.getXmlReader());
        assertNotNull(stream.getCurrentLocation());
        assertNotNull(stream.getTokenLocation());
        assertNotNull(stream.toString());

        // Next token: Attribute Name
        int token1 = stream.next();
        assertEquals(XmlTokenStream.XML_ATTRIBUTE_NAME, token1);
        assertEquals("attr1", stream.getLocalName());
        assertFalse(stream.hasAttributes());

        // Next token: Attribute Value
        int token2 = stream.next();
        assertEquals(XmlTokenStream.XML_ATTRIBUTE_VALUE, token2);
        assertEquals("val1", stream.getText());

        // Next token: Text inside root
        int token3 = stream.next();
        assertEquals(XmlTokenStream.XML_TEXT, token3);
        assertEquals("hello", stream.getText());

        // Next token: End Element
        int token4 = stream.next();
        assertEquals(XmlTokenStream.XML_END_ELEMENT, token4);

        // Next token: End Document
        int token5 = stream.next();
        assertEquals(XmlTokenStream.XML_END, token5);

        stream.close();
    }

    @Test
    public void testSkipAttributesAndEndElement() throws Throwable {
        XMLInputFactory f = XMLInputFactory.newFactory();
        XMLStreamReader sr = f.createXMLStreamReader(new StringReader("<root attr1=\"val1\"><child/></root>"));
        
        XmlTokenStream stream = new XmlTokenStream(sr, null);
        assertEquals(XmlTokenStream.XML_START_ELEMENT, stream.getCurrentToken());
        
        // Test skipAttributes when state is START_ELEMENT with attributes
        stream.skipAttributes();
        assertEquals(XmlTokenStream.XML_START_ELEMENT, stream.getCurrentToken());

        // Move to child start element
        int token = stream.next();
        assertEquals(XmlTokenStream.XML_START_ELEMENT, token);
        assertEquals("child", stream.getLocalName());

        // Next should be child end element
        token = stream.next();
        assertEquals(XmlTokenStream.XML_END_ELEMENT, token);

        // Test skipEndElement
        // Now root end element is next
        stream.skipEndElement();
        
        stream.closeCompletely();
    }

    @Test
    public void testSkipEndElementFailure() throws Throwable {
        XMLInputFactory f = XMLInputFactory.newFactory();
        XMLStreamReader sr = f.createXMLStreamReader(new StringReader("<root>hello</root>"));
        
        XmlTokenStream stream = new XmlTokenStream(sr, null);
        
        try {
            // Trying to skip end element when text is next should throw IOException
            stream.skipEndElement();
            fail("Expected IOException");
        } catch (java.io.IOException e) {
            assertTrue(e.getMessage().contains("Expected END_ELEMENT"));
        }
        
        stream.close();
    }

    @Test
    public void testConvertToString() throws Throwable {
        XMLInputFactory f = XMLInputFactory.newFactory();
        XMLStreamReader sr = f.createXMLStreamReader(new StringReader("<root attr=\"value\"/>"));
        
        XmlTokenStream stream = new XmlTokenStream(sr, null);
        // At START_ELEMENT, convertToString returns null because state is not XML_ATTRIBUTE_NAME
        assertNull(stream.convertToString());

        // Move to attribute name
        stream.next();
        assertEquals(XmlTokenStream.XML_ATTRIBUTE_NAME, stream.getCurrentToken());

        // Now convertToString should work on empty/self-closing tag with attribute
        String converted = stream.convertToString();
        assertEquals("", converted);
        assertEquals(XmlTokenStream.XML_TEXT, stream.getCurrentToken());
        assertEquals("", stream.getText());

        stream.close();
    }

    @Test
    public void testSkipAttributesEdgeCases() throws Throwable {
        XMLInputFactory f = XMLInputFactory.newFactory();
        XMLStreamReader sr = f.createXMLStreamReader(new StringReader("<root><child>text</child></root>"));
        
        XmlTokenStream stream = new XmlTokenStream(sr, null);
        // START_ELEMENT without attributes, skipAttributes should do nothing safely
        stream.skipAttributes();

        // Move to XML_TEXT via child/text
        stream.next(); // child start
        stream.next(); // child text
        assertEquals(XmlTokenStream.XML_TEXT, stream.getCurrentToken());

        // skipAttributes on XML_TEXT should do nothing safely
        stream.skipAttributes();

        stream.close();
    }

    @Test
    public void testIllegalStateOnSkipAttributes() throws Throwable {
        XMLInputFactory f = XMLInputFactory.newFactory();
        XMLStreamReader sr = f.createXMLStreamReader(new StringReader("<root/>"));
        
        XmlTokenStream stream = new XmlTokenStream(sr, null);
        // Force state to XML_END
        while (stream.getCurrentToken() != XmlTokenStream.XML_END) {
            stream.next();
        }

        try {
            stream.skipAttributes();
            fail("Expected IllegalStateException");
        } catch (IllegalStateException e) {
            assertTrue(e.getMessage().contains("Current state not XML_START_ELEMENT or XML_ATTRIBUTE_NAME"));
        }

        stream.close();
    }
}