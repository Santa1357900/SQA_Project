package com.fasterxml.jackson.dataformat.xml.deser;

import java.io.IOException;
import java.io.StringReader;
import java.util.ArrayList;
import java.util.List;

import javax.xml.stream.XMLInputFactory;
import javax.xml.stream.XMLStreamConstants;
import javax.xml.stream.XMLStreamException;
import javax.xml.stream.XMLStreamReader;

import org.codehaus.stax2.XMLStreamReader2;

import com.fasterxml.jackson.core.JsonLocation;

import org.junit.Test;
import static org.junit.Assert.*;

public class XmlTokenStreamClaudeTest
{
    private XMLStreamReader newReaderAtStart(String xml) throws XMLStreamException
    {
        XMLInputFactory f = XMLInputFactory.newInstance();
        XMLStreamReader r = f.createXMLStreamReader(new StringReader(xml));
        while (r.getEventType() != XMLStreamConstants.START_ELEMENT && r.hasNext()) {
            r.next();
        }
        return r;
    }

    // Constructor: reader not positioned at START_ELEMENT -> IllegalArgumentException
    @Test
    public void testConstructor_readerNotAtStartElement_throwsIllegalArgumentException() throws Throwable {
        XMLInputFactory f = XMLInputFactory.newInstance();
        XMLStreamReader r = f.createXMLStreamReader(new StringReader("<root/>"));
        try {
            new XmlTokenStream(r, "src");
            fail("expected IllegalArgumentException");
        } catch (IllegalArgumentException expected) {
            assertTrue(expected.getMessage().contains("START_ELEMENT"));
        }
    }

    // Constructor: reader at START_ELEMENT -> initializes localName/state correctly
    @Test
    public void testConstructor_readerAtStartElement_initializesState() throws Throwable {
        XMLStreamReader r = newReaderAtStart("<root/>");
        XmlTokenStream stream = new XmlTokenStream(r, "src");
        assertEquals(XmlTokenStream.XML_START_ELEMENT, stream.getCurrentToken());
        assertEquals("root", stream.getLocalName());
        assertFalse(stream.hasAttributes());
    }

    // getXmlReader: returns wrapped reader usable for basic queries
    @Test
    public void testGetXmlReader_returnsWrappedReaderWithSameLocalName() throws Throwable {
        XMLStreamReader r = newReaderAtStart("<root/>");
        XmlTokenStream stream = new XmlTokenStream(r, "src");
        XMLStreamReader2 wrapped = stream.getXmlReader();
        assertNotNull(wrapped);
        assertEquals("root", wrapped.getLocalName());
    }

    // next(): no attributes, no text -> END_ELEMENT then END
    @Test
    public void testNext_noAttributesNoText_returnsEndElementThenEnd() throws Throwable {
        XMLStreamReader r = newReaderAtStart("<root></root>");
        XmlTokenStream stream = new XmlTokenStream(r, "src");
        assertEquals(XmlTokenStream.XML_END_ELEMENT, stream.next());
        assertEquals(XmlTokenStream.XML_END, stream.next());
    }

    // next(): single attribute path: ATTRIBUTE_NAME -> ATTRIBUTE_VALUE -> END_ELEMENT
    @Test
    public void testNext_singleAttribute_returnsAttributeNameThenValueThenEnd() throws Throwable {
        XMLStreamReader r = newReaderAtStart("<root attr=\"val\"/>");
        XmlTokenStream stream = new XmlTokenStream(r, "src");
        assertEquals(XmlTokenStream.XML_ATTRIBUTE_NAME, stream.next());
        assertEquals("attr", stream.getLocalName());
        assertEquals("val", stream.getText());
        assertEquals(XmlTokenStream.XML_ATTRIBUTE_VALUE, stream.next());
        assertEquals(XmlTokenStream.XML_END_ELEMENT, stream.next());
    }

    // next(): multiple attributes iterate in document order
    @Test
    public void testNext_multipleAttributes_iteratesInOrder() throws Throwable {
        XMLStreamReader r = newReaderAtStart("<root a=\"1\" b=\"2\"/>");
        XmlTokenStream stream = new XmlTokenStream(r, "src");
        assertEquals(XmlTokenStream.XML_ATTRIBUTE_NAME, stream.next());
        assertEquals("a", stream.getLocalName());
        assertEquals("1", stream.getText());
        assertEquals(XmlTokenStream.XML_ATTRIBUTE_VALUE, stream.next());
        assertEquals(XmlTokenStream.XML_ATTRIBUTE_NAME, stream.next());
        assertEquals("b", stream.getLocalName());
        assertEquals("2", stream.getText());
        assertEquals(XmlTokenStream.XML_ATTRIBUTE_VALUE, stream.next());
        assertEquals(XmlTokenStream.XML_END_ELEMENT, stream.next());
    }

    // next(): pure text content followed by END_ELEMENT
    @Test
    public void testNext_textContent_returnsTextThenEndElement() throws Throwable {
        XMLStreamReader r = newReaderAtStart("<root>hello</root>");
        XmlTokenStream stream = new XmlTokenStream(r, "src");
        assertEquals(XmlTokenStream.XML_TEXT, stream.next());
        assertEquals("hello", stream.getText());
        assertEquals(XmlTokenStream.XML_END_ELEMENT, stream.next());
    }

    // next(): whitespace-only text immediately before END_ELEMENT is preserved verbatim
    @Test
    public void testNext_whitespaceOnlyTextBeforeEndElement_preservesRawText() throws Throwable {
        XMLStreamReader r = newReaderAtStart("<root>   </root>");
        XmlTokenStream stream = new XmlTokenStream(r, "src");
        assertEquals(XmlTokenStream.XML_TEXT, stream.next());
        assertEquals("   ", stream.getText());
    }

    // Bug-hunt: non-whitespace mixed text before a nested START_ELEMENT must not be silently dropped
    @Test
    public void testNext_mixedContentTextBeforeChildElement_preservesNonWhitespaceText() throws Throwable {
        XMLStreamReader r = newReaderAtStart("<root>value1<child/></root>");
        XmlTokenStream stream = new XmlTokenStream(r, "src");
        List<String> textsSeen = new ArrayList<String>();
        int type = stream.getCurrentToken();
        int guard = 0;
        while (type != XmlTokenStream.XML_END && guard < 50) {
            if (type == XmlTokenStream.XML_TEXT) {
                textsSeen.add(stream.getText());
            }
            type = stream.next();
            guard++;
        }
        assertTrue("mixed text should be preserved, saw: " + textsSeen, textsSeen.contains("value1"));
    }

    // next(): once XML_END reached, repeated calls keep returning XML_END
    @Test
    public void testNext_afterEnd_repeatedlyReturnsEnd() throws Throwable {
        XMLStreamReader r = newReaderAtStart("<root/>");
        XmlTokenStream stream = new XmlTokenStream(r, "src");
        stream.next(); // END_ELEMENT
        assertEquals(XmlTokenStream.XML_END, stream.next());
        assertEquals(XmlTokenStream.XML_END, stream.next());
    }

    // skipEndElement(): succeeds when next token is END_ELEMENT
    @Test
    public void testSkipEndElement_onEndElementToken_succeeds() throws Throwable {
        XMLStreamReader r = newReaderAtStart("<root></root>");
        XmlTokenStream stream = new XmlTokenStream(r, "src");
        stream.skipEndElement();
        assertEquals(XmlTokenStream.XML_END_ELEMENT, stream.getCurrentToken());
    }

    // skipEndElement(): throws IOException when next token is not END_ELEMENT
    @Test
    public void testSkipEndElement_onNonEndElementToken_throwsIOException() throws Throwable {
        XMLStreamReader r = newReaderAtStart("<root attr=\"1\"/>");
        XmlTokenStream stream = new XmlTokenStream(r, "src");
        try {
            stream.skipEndElement();
            fail("expected IOException");
        } catch (IOException expected) { }
    }

    // getCurrentToken(): right after construction equals XML_START_ELEMENT
    @Test
    public void testGetCurrentToken_afterConstruct_returnsStartElement() throws Throwable {
        XMLStreamReader r = newReaderAtStart("<root/>");
        XmlTokenStream stream = new XmlTokenStream(r, "src");
        assertEquals(XmlTokenStream.XML_START_ELEMENT, stream.getCurrentToken());
    }

    // getText(): before any attribute/text token is produced, returns null
    @Test
    public void testGetText_afterConstructBeforeAnyToken_returnsNull() throws Throwable {
        XMLStreamReader r = newReaderAtStart("<root/>");
        XmlTokenStream stream = new XmlTokenStream(r, "src");
        assertNull(stream.getText());
    }

    // getLocalName(): returns the current element's local name
    @Test
    public void testGetLocalName_afterConstruct_returnsElementName() throws Throwable {
        XMLStreamReader r = newReaderAtStart("<myElement/>");
        XmlTokenStream stream = new XmlTokenStream(r, "src");
        assertEquals("myElement", stream.getLocalName());
    }



    // hasAttributes(): true when at START_ELEMENT with attributeCount > 0
    @Test
    public void testHasAttributes_withAttributes_true() throws Throwable {
        XMLStreamReader r = newReaderAtStart("<root attr=\"1\"/>");
        XmlTokenStream stream = new XmlTokenStream(r, "src");
        assertTrue(stream.hasAttributes());
    }

    // hasAttributes(): false when at START_ELEMENT with no attributes
    @Test
    public void testHasAttributes_withoutAttributes_false() throws Throwable {
        XMLStreamReader r = newReaderAtStart("<root/>");
        XmlTokenStream stream = new XmlTokenStream(r, "src");
        assertFalse(stream.hasAttributes());
    }

    // hasAttributes(): false once state moves past START_ELEMENT (to ATTRIBUTE_NAME)
    @Test
    public void testHasAttributes_afterMovingPastStartElement_false() throws Throwable {
        XMLStreamReader r = newReaderAtStart("<root attr=\"1\"/>");
        XmlTokenStream stream = new XmlTokenStream(r, "src");
        stream.next();
        assertFalse(stream.hasAttributes());
    }

    // close(): does not alter the current parsing state fields
    @Test
    public void testClose_doesNotAlterCurrentState() throws Throwable {
        XMLStreamReader r = newReaderAtStart("<root attr=\"1\"/>");
        XmlTokenStream stream = new XmlTokenStream(r, "src");
        stream.next();
        int before = stream.getCurrentToken();
        stream.close();
        assertEquals(before, stream.getCurrentToken());
    }

    // closeCompletely(): does not alter the current parsing state fields
    @Test
    public void testCloseCompletely_doesNotAlterCurrentState() throws Throwable {
        XMLStreamReader r = newReaderAtStart("<root attr=\"1\"/>");
        XmlTokenStream stream = new XmlTokenStream(r, "src");
        stream.next();
        int before = stream.getCurrentToken();
        stream.closeCompletely();
        assertEquals(before, stream.getCurrentToken());
    }

    // getCurrentLocation(): returns non-null JsonLocation carrying the supplied source reference
    @Test
    public void testGetCurrentLocation_returnsLocationWithSourceRef() throws Throwable {
        XMLStreamReader r = newReaderAtStart("<root/>");
        XmlTokenStream stream = new XmlTokenStream(r, "mySource");
        JsonLocation loc = stream.getCurrentLocation();
        assertNotNull(loc);
        assertEquals("mySource", loc.getSourceRef());
    }

    // getTokenLocation(): returns non-null JsonLocation carrying the supplied source reference
    @Test
    public void testGetTokenLocation_returnsLocationWithSourceRef() throws Throwable {
        XMLStreamReader r = newReaderAtStart("<root/>");
        XmlTokenStream stream = new XmlTokenStream(r, "mySource");
        JsonLocation loc = stream.getTokenLocation();
        assertNotNull(loc);
        assertEquals("mySource", loc.getSourceRef());
    }

    // convertToString(): not applicable when current state isn't ATTRIBUTE_NAME -> null
    @Test
    public void testConvertToString_whenNotAttributeNameState_returnsNull() throws Throwable {
        XMLStreamReader r = newReaderAtStart("<root attr=\"v\"/>");
        XmlTokenStream stream = new XmlTokenStream(r, "src");
        assertNull(stream.convertToString());
    }

    // convertToString(): not applicable when attribute index isn't 0 -> null
    @Test
    public void testConvertToString_whenAttributeIndexNonZero_returnsNull() throws Throwable {
        XMLStreamReader r = newReaderAtStart("<root a=\"1\" b=\"2\">text</root>");
        XmlTokenStream stream = new XmlTokenStream(r, "src");
        stream.next();
        stream.next();
        stream.next();
        assertNull(stream.convertToString());
    }

    // convertToString(): valid case collapses remaining attribute-tag body into a TEXT value
    @Test
    public void testConvertToString_validCase_returnsTextAndSetsTextState() throws Throwable {
        XMLStreamReader r = newReaderAtStart("<root attr=\"aVal\">bodyText</root>");
        XmlTokenStream stream = new XmlTokenStream(r, "src");
        stream.next();
        String result = stream.convertToString();
        assertEquals("bodyText", result);
        assertEquals(XmlTokenStream.XML_TEXT, stream.getCurrentToken());
        assertEquals("bodyText", stream.getText());
    }

    // convertToString(): empty element after attribute yields empty string, not null
    @Test
    public void testConvertToString_emptyElementAfterAttribute_returnsEmptyString() throws Throwable {
        XMLStreamReader r = newReaderAtStart("<root attr=\"aVal\"></root>");
        XmlTokenStream stream = new XmlTokenStream(r, "src");
        stream.next();
        String result = stream.convertToString();
        assertEquals("", result);
        assertEquals(XmlTokenStream.XML_TEXT, stream.getCurrentToken());
    }

    // skipAttributes(): from ATTRIBUTE_NAME resets attribute count and returns to START_ELEMENT
    @Test
    public void testSkipAttributes_fromAttributeNameState_resetsAttributeCount() throws Throwable {
        XMLStreamReader r = newReaderAtStart("<root a=\"1\" b=\"2\"/>");
        XmlTokenStream stream = new XmlTokenStream(r, "src");
        stream.next();
        stream.skipAttributes();
        assertEquals(XmlTokenStream.XML_START_ELEMENT, stream.getCurrentToken());
        assertFalse(stream.hasAttributes());
    }

    // skipAttributes(): from START_ELEMENT is a no-op per documented [#47] behavior
    @Test
    public void testSkipAttributes_fromStartElementState_noOp() throws Throwable {
        XMLStreamReader r = newReaderAtStart("<root attr=\"1\"/>");
        XmlTokenStream stream = new XmlTokenStream(r, "src");
        stream.skipAttributes();
        assertTrue(stream.hasAttributes());
        assertEquals(XmlTokenStream.XML_START_ELEMENT, stream.getCurrentToken());
    }

    // skipAttributes(): from XML_TEXT state is a no-op
    @Test
    public void testSkipAttributes_fromTextState_noOp() throws Throwable {
        XMLStreamReader r = newReaderAtStart("<root>hi</root>");
        XmlTokenStream stream = new XmlTokenStream(r, "src");
        stream.next();
        stream.skipAttributes();
        assertEquals(XmlTokenStream.XML_TEXT, stream.getCurrentToken());
        assertEquals("hi", stream.getText());
    }

    // skipAttributes(): from an unsupported state (END_ELEMENT) throws IllegalStateException
    @Test
    public void testSkipAttributes_fromEndElementState_throwsIllegalStateException() throws Throwable {
        XMLStreamReader r = newReaderAtStart("<root></root>");
        XmlTokenStream stream = new XmlTokenStream(r, "src");
        stream.next();
        try {
            stream.skipAttributes();
            fail("expected IllegalStateException");
        } catch (IllegalStateException expected) { }
    }

    // repeatStartElement(): requires current state to be XML_START_ELEMENT, else throws
    @Test
    public void testRepeatStartElement_fromNonStartElementState_throwsIllegalStateException() throws Throwable {
        XMLStreamReader r = newReaderAtStart("<root>hi</root>");
        XmlTokenStream stream = new XmlTokenStream(r, "src");
        stream.next();
        try {
            stream.repeatStartElement();
            fail("expected IllegalStateException");
        } catch (IllegalStateException expected) { }
    }

    // repeatStartElement(): from START_ELEMENT causes next() to re-emit a duplicate START_ELEMENT
    @Test
    public void testRepeatStartElement_fromStartElementState_duplicatesStartElement() throws Throwable {
        XMLStreamReader r = newReaderAtStart("<root><child/></root>");
        XmlTokenStream stream = new XmlTokenStream(r, "src");
        stream.repeatStartElement();
        int type = stream.next();
        assertEquals(XmlTokenStream.XML_START_ELEMENT, type);
        assertEquals("root", stream.getLocalName());
    }
}
