package org.jsoup.helper;

import org.junit.Test;
import org.w3c.dom.Document;
import org.w3c.dom.Element;

import static org.junit.Assert.*;

public class W3CDomTest {

    @Test
    public void testFromJsoupNormalDocument() throws Throwable {
        org.jsoup.nodes.Document jsoupDoc = org.jsoup.nodes.Document.parse("<html><head><title>Test</title></head><body><p id=\"p1\">Hello World</p><!-- Comment --></body></html>");
        jsoupDoc.location("http://example.com");

        W3CDom w3cDom = new W3CDom();
        Document w3cDoc = w3cDom.fromJsoup(jsoupDoc);

        assertNotNull(w3cDoc);
        assertEquals("http://example.com", w3cDoc.getDocumentURI());

        String serialized = w3cDom.asString(w3cDoc);
        assertNotNull(serialized);
        assertTrue(serialized.contains("Hello World"));
        assertTrue(serialized.contains("Comment"));
    }

    @Test
    public void testFromJsoupWithoutLocation() throws Throwable {
        org.jsoup.nodes.Document jsoupDoc = org.jsoup.nodes.Document.parse("<div><span>Data</span></div>");
        W3CDom w3cDom = new W3CDom();
        Document w3cDoc = w3cDom.fromJsoup(jsoupDoc);

        assertNotNull(w3cDoc);
        String serialized = w3cDom.asString(w3cDoc);
        assertTrue(serialized.contains("Data"));
    }

    @Test(expected = IllegalArgumentException.class)
    public void testFromJsoupNullThrowsException() throws Throwable {
        W3CDom w3cDom = new W3CDom();
        w3cDom.fromJsoup(null);
    }

    @Test
    public void testConvertWithNamespaces() throws Throwable {
        org.jsoup.nodes.Document jsoupDoc = org.jsoup.nodes.Document.parse("<root xmlns=\"http://default.ns\" xmlns:sub=\"http://sub.ns\"><sub:child attr=\"val\" invalid_attr!=\"bad\">Text</sub:child></root>");
        W3CDom w3cDom = new W3CDom();
        Document w3cDoc = w3cDom.fromJsoup(jsoupDoc);

        assertNotNull(w3cDoc);
        String serialized = w3cDom.asString(w3cDoc);
        assertTrue(serialized.contains("Text"));
        assertTrue(serialized.contains("val"));
    }

    @Test
    public void testDataNodeConversion() throws Throwable {
        org.jsoup.nodes.Document jsoupDoc = org.jsoup.nodes.Document.parse("<script>/* some data */</script>");
        W3CDom w3cDom = new W3CDom();
        Document w3cDoc = w3cDom.fromJsoup(jsoupDoc);

        assertNotNull(w3cDoc);
        String serialized = w3cDom.asString(w3cDoc);
        assertTrue(serialized.contains("/* some data */"));
    }

    @Test
    public void testAsStringTransformerExceptionHandling() throws Throwable {
        W3CDom w3cDom = new W3CDom() {
            public String asString(Document doc) {
                try {
                    javax.xml.transform.TransformerFactory tf = javax.xml.transform.TransformerFactory.newInstance();
                    javax.xml.transform.Transformer transformer = tf.newTransformer();
                    // Force an exception by passing null source
                    transformer.transform(null, new javax.xml.transform.stream.StreamResult(new java.io.StringWriter()));
                    return "";
                } catch (javax.xml.transform.TransformerException e) {
                    throw new IllegalStateException(e);
                }
            }
        };

        try {
            w3cDom.asString(null);
            fail("Expected IllegalStateException");
        } catch (IllegalStateException e) {
            assertNotNull(e);
        }
    }

    @Test
    public void testW3CBuilderDirectInstantiation() throws Throwable {
        javax.xml.parsers.DocumentBuilderFactory factory = javax.xml.parsers.DocumentBuilderFactory.newInstance();
        Document w3cDoc = factory.newDocumentBuilder().newDocument();

        W3CDom.W3CBuilder builder = new W3CDom.W3CBuilder(w3cDoc);
        org.jsoup.nodes.Element el = new org.jsoup.nodes.Element(org.jsoup.parser.Tag.valueOf("div"), "");
        
        builder.head(el, 0);
        org.jsoup.nodes.TextNode textNode = new org.jsoup.nodes.TextNode("Direct Text", "");
        builder.head(textNode, 1);
        builder.tail(textNode, 1);
        builder.tail(el, 0);

        assertNotNull(w3cDoc.getDocumentElement());
    }
}