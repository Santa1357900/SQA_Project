package org.jsoup.helper;

import org.junit.Test;
import static org.junit.Assert.*;

import org.w3c.dom.Document;
import org.w3c.dom.Element;

public class W3CDomTest {

    @Test
    public void testFromJsoupNull() throws Throwable {
        W3CDom w3cDom = new W3CDom();
        try {
            w3cDom.fromJsoup(null);
            fail("Expected exception for null Jsoup document");
        } catch (IllegalArgumentException e) {
            // Expected
        }
    }

    @Test
    public void testConversionWithLocationAndElements() throws Throwable {
        org.jsoup.nodes.Document jsoupDoc = org.jsoup.nodes.Document.createShell("http://example.com");
        jsoupDoc.head().append("<title>Hello</title>");
        jsoupDoc.body().append("<div id='test' class='content' xmlns:foo='http://foo.com' foo:attr='bar'>Text &amp; more</div><!-- comment -->");
        jsoupDoc.body().append("<script>data content here</script>");

        W3CDom w3cDom = new W3CDom();
        Document w3cDoc = w3cDom.fromJsoup(jsoupDoc);

        assertNotNull(w3cDoc);
        assertEquals("http://example.com", w3cDoc.getDocumentURI());

        String serialized = w3cDom.asString(w3cDoc);
        assertNotNull(serialized);
        assertTrue(serialized.contains("Hello"));
        assertTrue(serialized.contains("test"));
        assertTrue(serialized.contains("comment"));
        assertTrue(serialized.contains("data content here"));
    }

    @Test
    public void testNamespacesAndAttributes() throws Throwable {
        org.jsoup.nodes.Document jsoupDoc = org.jsoup.nodes.Document.createShell("");
        // Test default namespace and prefix namespace, invalid attribute names filtering
        jsoupDoc.body().append("<root xmlns='http://default.com' xmlns:ns='http://ns.com' invalid attr=\"val\" ns:sub=\"val2\"></root>");

        W3CDom w3cDom = new W3CDom();
        Document w3cDoc = w3cDom.fromJsoup(jsoupDoc);

        assertNotNull(w3cDoc);
        Element root = w3cDoc.getDocumentElement();
        assertNotNull(root);
    }

    @Test
    public void testAsStringExceptionHandling() throws Throwable {
        W3CDom w3cDom = new W3CDom();
        // Passing null to DOMSource will cause Transformer.transform to throw an exception
        try {
            w3cDom.asString(null);
            fail("Expected IllegalStateException");
        } catch (IllegalStateException e) {
            // Expected
        }
    }
}