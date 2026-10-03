package org.jsoup.helper;

import org.junit.Test;
import org.w3c.dom.Document;
import org.w3c.dom.Element;

import static org.junit.Assert.*;

public class W3CDomTest {

    @Test(expected = IllegalArgumentException.class)
    public void testFromJsoupNull() throws Throwable {
        W3CDom w3cDom = new W3CDom();
        w3cDom.fromJsoup(null);
    }

    @Test
    public void testFromJsoupBasic() throws Throwable {
        org.jsoup.nodes.Document jsoupDoc = org.jsoup.nodes.Document.createShell("http://example.com");
        jsoupDoc.body().text("Hello World");
        jsoupDoc.body().append("<div id='test' class='sample'><span>Child</span></div>");
        jsoupDoc.body().append("<!-- A comment -->");

        W3CDom w3cDom = new W3CDom();
        Document w3cDoc = w3cDom.fromJsoup(jsoupDoc);

        assertNotNull(w3cDoc);
        assertEquals("http://example.com", w3cDoc.getDocumentURI());

        String asString = w3cDom.asString(w3cDoc);
        assertNotNull(asString);
        assertTrue(asString.contains("Hello World"));
        assertTrue(asString.contains("test"));
        assertTrue(asString.contains("sample"));
        assertTrue(asString.contains("A comment"));
    }

    @Test
    public void testConvertWithoutLocation() throws Throwable {
        org.jsoup.nodes.Document jsoupDoc = new org.jsoup.nodes.Document("");
        jsoupDoc.appendElement("html").appendElement("body").text("No location");

        W3CDom w3cDom = new W3CDom();
        Document w3cDoc = w3cDom.fromJsoup(jsoupDoc);

        assertNotNull(w3cDoc);
        assertEquals("", w3cDoc.getDocumentURI());
    }

    @Test
    public void testNamespacesAndAttributes() throws Throwable {
        org.jsoup.nodes.Document jsoupDoc = new org.jsoup.nodes.Document("");
        org.jsoup.nodes.Element root = jsoupDoc.appendElement("root");
        root.attr("xmlns", "http://default.ns");
        root.attr("xmlns:m", "http://math.ns");
        root.attr("invalid_attr!", "value");
        root.attr("valid-attr.1", "ok");

        org.jsoup.nodes.Element child = root.appendElement("m:math");
        child.text("1 + 1");

        W3CDom w3cDom = new W3CDom();
        Document w3cDoc = w3cDom.fromJsoup(jsoupDoc);

        assertNotNull(w3cDoc);
        String xml = w3cDom.asString(w3cDoc);
        assertTrue(xml.contains("http://default.ns"));
        assertTrue(xml.contains("http://math.ns"));
        assertTrue(xml.contains("valid-attr.1"));
        assertFalse(xml.contains("invalid_attr!"));
    }

    @Test
    public void testDataNodeHandling() throws Throwable {
        org.jsoup.nodes.Document jsoupDoc = new org.jsoup.nodes.Document("");
        org.jsoup.nodes.Element root = jsoupDoc.appendElement("script");
        root.appendChild(new org.jsoup.nodes.DataNode("var a = 1;"));

        W3CDom w3cDom = new W3CDom();
        Document w3cDoc = w3cDom.fromJsoup(jsoupDoc);

        assertNotNull(w3cDoc);
        String xml = w3cDom.asString(w3cDoc);
        assertTrue(xml.contains("var a = 1;"));
    }
}