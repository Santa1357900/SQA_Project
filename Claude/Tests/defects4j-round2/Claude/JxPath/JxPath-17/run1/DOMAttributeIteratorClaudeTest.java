package org.apache.commons.jxpath.ri.model.dom;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;

import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;

import org.apache.commons.jxpath.JXPathContext;
import org.junit.Before;
import org.junit.Test;
import org.w3c.dom.Document;
import org.w3c.dom.Element;

import static org.junit.Assert.*;

public class DOMAttributeIteratorClaudeTest {

    private static final String XMLNS_NS = "http://www.w3.org/2000/xmlns/";
    private static final String XML_NS = "http://www.w3.org/XML/1998/namespace";

    private Document doc;

    @Before
    public void setUp() throws Throwable {
        DocumentBuilderFactory dbf = DocumentBuilderFactory.newInstance();
        dbf.setNamespaceAware(true);
        DocumentBuilder db = dbf.newDocumentBuilder();
        doc = db.newDocument();
    }

    private Element newRoot() {
        Element root = doc.createElement("root");
        doc.appendChild(root);
        return root;
    }

    private List collect(Iterator it) {
        List result = new ArrayList<Object>();
        while (it.hasNext()) {
            result.add(it.next());
        }
        return result;
    }

    // exact-name lookup: attr exists -> attributes list has one item, value returned
    @Test
    public void testConstructor_namedAttributeExists_returnsValue() throws Throwable {
        Element root = newRoot();
        root.setAttribute("foo", "bar");
        JXPathContext ctx = JXPathContext.newContext(root);
        assertEquals("bar", ctx.getValue("@foo"));
    }

    // exact-name lookup: attr missing among other attrs -> attr==null branch, empty iteration
    @Test
    public void testConstructor_namedAttributeMissingAmongOthers_noPointerIterated() throws Throwable {
        Element root = newRoot();
        root.setAttribute("bar", "baz");
        JXPathContext ctx = JXPathContext.newContext(root);
        Iterator it = ctx.iteratePointers("@foo");
        assertFalse(it.hasNext());
    }

    // exact-name lookup on element with zero attributes -> NamedNodeMap empty, no match
    @Test
    public void testConstructor_namedAttributeOnElementWithNoAttributes_noPointerIterated() throws Throwable {
        Element root = newRoot();
        JXPathContext ctx = JXPathContext.newContext(root);
        Iterator it = ctx.iteratePointers("@foo");
        assertFalse(it.hasNext());
    }

    // edge value: empty string attribute value must be preserved exactly
    @Test
    public void testConstructor_namedAttributeEmptyValue_returnsEmptyString() throws Throwable {
        Element root = newRoot();
        root.setAttribute("empty", "");
        JXPathContext ctx = JXPathContext.newContext(root);
        assertEquals("", ctx.getValue("@empty"));
    }

    // unicode value must pass through unmodified
    @Test
    public void testConstructor_namedAttributeUnicodeValue_returnsExactValue() throws Throwable {
        Element root = newRoot();
        root.setAttribute("name", "h\u00e9llo-\u4e2d\u6587");
        JXPathContext ctx = JXPathContext.newContext(root);
        assertEquals("h\u00e9llo-\u4e2d\u6587", ctx.getValue("@name"));
    }

    // special character value (angle bracket) preserved as literal character data
    @Test
    public void testConstructor_namedAttributeSpecialCharacterValue_returnsExactValue() throws Throwable {
        Element root = newRoot();
        root.setAttribute("tag", "<x>&");
        JXPathContext ctx = JXPathContext.newContext(root);
        assertEquals("<x>&", ctx.getValue("@tag"));
    }

    // exact-name lookup on a descendant element's parent pointer (not the context root)
    @Test
    public void testConstructor_namedAttributeOnChildElement_returnsValue() throws Throwable {
        Element root = newRoot();
        Element child = doc.createElement("child");
        child.setAttribute("id", "42");
        root.appendChild(child);
        JXPathContext ctx = JXPathContext.newContext(root);
        assertEquals("42", ctx.getValue("child/@id"));
    }

    // wildcard "*": element has zero attributes -> for loop runs 0 times
    @Test
    public void testConstructor_wildcardNoAttributes_emptyIteration() throws Throwable {
        Element root = newRoot();
        JXPathContext ctx = JXPathContext.newContext(root);
        Iterator it = ctx.iterate("@*");
        assertFalse(it.hasNext());
    }

    // wildcard "*": element has exactly one attribute -> for loop runs once, testAttr passes
    @Test
    public void testConstructor_wildcardSingleAttribute_oneResult() throws Throwable {
        Element root = newRoot();
        root.setAttribute("foo", "bar");
        JXPathContext ctx = JXPathContext.newContext(root);
        List values = collect(ctx.iterate("@*"));
        assertEquals(1, values.size());
        assertEquals("bar", values.get(0));
    }

    // wildcard "*": multiple attributes -> for loop runs multiple times, all collected
    @Test
    public void testConstructor_wildcardMultipleAttributes_allValuesPresentAndCountCorrect() throws Throwable {
        Element root = newRoot();
        root.setAttribute("a1", "v1");
        root.setAttribute("a2", "v2");
        root.setAttribute("a3", "v3");
        JXPathContext ctx = JXPathContext.newContext(root);
        List values = collect(ctx.iterate("@*"));
        assertEquals(3, values.size());
        assertTrue(values.contains("v1"));
        assertTrue(values.contains("v2"));
        assertTrue(values.contains("v3"));
    }

    // testAttr: default xmlns declaration (prefix null, localName "xmlns") must be excluded
    @Test
    public void testConstructor_wildcardExcludesDefaultXmlnsDeclaration() throws Throwable {
        Element root = newRoot();
        root.setAttributeNS(XMLNS_NS, "xmlns", "http://example.com/default");
        root.setAttribute("foo", "bar");
        JXPathContext ctx = JXPathContext.newContext(root);
        List values = collect(ctx.iterate("@*"));
        assertEquals(1, values.size());
        assertEquals("bar", values.get(0));
    }

    // testAttr: prefixed xmlns:ns declaration (prefix "xmlns") must be excluded
    @Test
    public void testConstructor_wildcardExcludesPrefixedXmlnsDeclaration() throws Throwable {
        Element root = newRoot();
        root.setAttributeNS(XMLNS_NS, "xmlns:ns", "http://example.com/ns");
        root.setAttribute("foo", "bar");
        JXPathContext ctx = JXPathContext.newContext(root);
        List values = collect(ctx.iterate("@*"));
        assertEquals(1, values.size());
        assertEquals("bar", values.get(0));
    }

    // boundary: prefix "xml" (not "xmlns") must NOT be excluded by the xmlns filter
    @Test
    public void testConstructor_wildcardDoesNotExcludeXmlPrefixedAttribute() throws Throwable {
        Element root = newRoot();
        root.setAttributeNS(XML_NS, "xml:lang", "en");
        JXPathContext ctx = JXPathContext.newContext(root);
        List values = collect(ctx.iterate("@*"));
        assertEquals(1, values.size());
        assertEquals("en", values.get(0));
    }

    // boundary: local name "xmlnsfoo" (not exactly "xmlns") must NOT be excluded
    @Test
    public void testConstructor_wildcardDoesNotExcludeAttributeNameStartingWithXmlns() throws Throwable {
        Element root = newRoot();
        root.setAttribute("xmlnsfoo", "val1");
        JXPathContext ctx = JXPathContext.newContext(root);
        List values = collect(ctx.iterate("@*"));
        assertEquals(1, values.size());
        assertEquals("val1", values.get(0));
    }

    // constructor: node is not an ELEMENT_NODE (text node) -> attributes list stays empty
    @Test
    public void testConstructor_nonElementTextNode_noAttributesIterated() throws Throwable {
        Element root = newRoot();
        root.appendChild(doc.createTextNode("hello"));
        JXPathContext ctx = JXPathContext.newContext(root);
        Iterator it = ctx.iteratePointers("text()/@*");
        assertFalse(it.hasNext());
    }

    // getAttribute: prefix resolves to a namespace URI and attributeNodeNS lookup succeeds
    @Test
    public void testGetAttribute_namedAttributeWithMatchingNamespace_returnsValue() throws Throwable {
        Element root = newRoot();
        root.setAttributeNS(XMLNS_NS, "xmlns:ns", "http://example.com/ns");
        root.setAttributeNS("http://example.com/ns", "ns:attr", "nsval");
        JXPathContext ctx = JXPathContext.newContext(root);
        ctx.registerNamespace("ns", "http://example.com/ns");
        assertEquals("nsval", ctx.getValue("@ns:attr"));
    }

    // testAttr/getAttribute: resolved namespace does not match any attribute's actual namespace
    @Test
    public void testTestAttr_namedAttributeWithMismatchedNamespace_noResult() throws Throwable {
        Element root = newRoot();
        root.setAttributeNS(XMLNS_NS, "xmlns:ns", "http://example.com/ns");
        root.setAttributeNS("http://example.com/ns", "ns:attr", "nsval");
        JXPathContext ctx = JXPathContext.newContext(root);
        ctx.registerNamespace("other", "http://example.com/other");
        Iterator it = ctx.iteratePointers("@other:attr");
        assertFalse(it.hasNext());
    }

    // wildcard: a genuine namespaced attribute is included, its xmlns:ns declaration is excluded
    @Test
    public void testConstructor_wildcardIncludesNamespacedAttributeExcludesNsDeclaration() throws Throwable {
        Element root = newRoot();
        root.setAttributeNS(XMLNS_NS, "xmlns:ns", "http://example.com/ns");
        root.setAttributeNS("http://example.com/ns", "ns:attr", "nsval");
        root.setAttribute("foo", "bar");
        JXPathContext ctx = JXPathContext.newContext(root);
        List values = collect(ctx.iterate("@*"));
        assertEquals(2, values.size());
        assertTrue(values.contains("nsval"));
        assertTrue(values.contains("bar"));
    }

    // cross-check: iterate() value count equals iteratePointers() count for same wildcard query
    @Test
    public void testIteratePointersAndIterate_countsConsistentForWildcard() throws Throwable {
        Element root = newRoot();
        root.setAttribute("a1", "v1");
        root.setAttribute("a2", "v2");
        JXPathContext ctx = JXPathContext.newContext(root);
        List values = collect(ctx.iterate("@*"));
        List pointers = collect(ctx.iteratePointers("@*"));
        assertEquals(values.size(), pointers.size());
        assertEquals(2, pointers.size());
    }

    // attribute value with surrounding whitespace preserved exactly (no trimming)
    @Test
    public void testConstructor_wildcardAttributeValueWithWhitespace_preserved() throws Throwable {
        Element root = newRoot();
        root.setAttribute("spaced", "  padded value  ");
        JXPathContext ctx = JXPathContext.newContext(root);
        List values = collect(ctx.iterate("@*"));
        assertEquals(1, values.size());
        assertEquals("  padded value  ", values.get(0));
    }

    // XML attribute names are case-sensitive: differently-cased query must not match
    @Test
    public void testConstructor_namedAttributeCaseSensitiveMatch_noMatchForDifferentCase() throws Throwable {
        Element root = newRoot();
        root.setAttribute("foo", "bar");
        JXPathContext ctx = JXPathContext.newContext(root);
        Iterator it = ctx.iteratePointers("@FOO");
        assertFalse(it.hasNext());
    }

    // wildcard on root must not pick up a child element's own attributes
    @Test
    public void testConstructor_rootWildcardDoesNotIncludeChildAttributes() throws Throwable {
        Element root = newRoot();
        root.setAttribute("rootAttr", "rv");
        Element child = doc.createElement("child");
        child.setAttribute("childAttr", "cv");
        root.appendChild(child);
        JXPathContext ctx = JXPathContext.newContext(root);
        List values = collect(ctx.iterate("@*"));
        assertEquals(1, values.size());
        assertEquals("rv", values.get(0));
    }

    // repeated evaluation of the same named-attribute query yields the same stable result
    @Test
    public void testConstructor_namedAttributeQueryTwiceSameContext_consistentResult() throws Throwable {
        Element root = newRoot();
        root.setAttribute("foo", "bar");
        JXPathContext ctx = JXPathContext.newContext(root);
        assertEquals("bar", ctx.getValue("@foo"));
        assertEquals("bar", ctx.getValue("@foo"));
    }

    // getPointer on an existing attribute returns a non-null pointer whose value matches
    @Test
    public void testGetPointer_namedAttributeExists_pointerValueMatches() throws Throwable {
        Element root = newRoot();
        root.setAttribute("foo", "bar");
        JXPathContext ctx = JXPathContext.newContext(root);
        Object pointer = ctx.getPointer("@foo");
        assertNotNull(pointer);
        assertEquals("bar", pointer.toString().contains("bar") ? "bar" : pointer.toString());
    }
}
