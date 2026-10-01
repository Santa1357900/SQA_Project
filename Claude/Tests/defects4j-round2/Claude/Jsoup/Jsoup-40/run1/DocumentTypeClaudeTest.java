package org.jsoup.nodes;

import org.junit.Test;
import static org.junit.Assert.*;

import org.jsoup.Jsoup;

public class DocumentTypeClaudeTest {

    // Constructor: valid name is stored under "name" attribute
    @Test
    public void testConstructor_validName_setsNameAttribute() throws Throwable {
        DocumentType dt = new DocumentType("html", "", "", "");
        assertEquals("html", dt.attr("name"));
    }

    // Constructor: valid publicId is stored under "publicId" attribute
    @Test
    public void testConstructor_validPublicId_setsPublicIdAttribute() throws Throwable {
        DocumentType dt = new DocumentType("html", "myPublicId", "", "");
        assertEquals("myPublicId", dt.attr("publicId"));
    }

    // Constructor: valid systemId is stored under "systemId" attribute
    @Test
    public void testConstructor_validSystemId_setsSystemIdAttribute() throws Throwable {
        DocumentType dt = new DocumentType("html", "", "mySystemId", "");
        assertEquals("mySystemId", dt.attr("systemId"));
    }

    // Constructor: null name violates Validate.notEmpty contract
    @Test
    public void testConstructor_nullName_throwsIllegalArgumentException() throws Throwable {
        try {
            new DocumentType(null, "", "", "");
            fail("expected IllegalArgumentException for null name");
        } catch (IllegalArgumentException expected) {
        }
    }

    // Constructor: empty name violates Validate.notEmpty contract
    @Test
    public void testConstructor_emptyName_throwsIllegalArgumentException() throws Throwable {
        try {
            new DocumentType("", "", "", "");
            fail("expected IllegalArgumentException for empty name");
        } catch (IllegalArgumentException expected) {
        }
    }

    // Constructor: empty publicId/systemId are allowed (not validated), no exception
    @Test
    public void testConstructor_emptyPublicIdAndSystemId_allowedNoException() throws Throwable {
        DocumentType dt = new DocumentType("html", "", "", "");
        assertEquals("", dt.attr("publicId"));
        assertEquals("", dt.attr("systemId"));
    }

    // Constructor: unicode characters in name preserved exactly
    @Test
    public void testConstructor_unicodeName_preservedExactly() throws Throwable {
        DocumentType dt = new DocumentType("html\u00e9", "", "", "");
        assertEquals("html\u00e9", dt.attr("name"));
    }

    // nodeName(): always returns the fixed "#doctype" constant
    @Test
    public void testNodeName_always_returnsDoctypeConstant() throws Throwable {
        DocumentType dt = new DocumentType("html", "", "", "");
        assertEquals("#doctype", dt.nodeName());
    }

    // nodeName(): remains "#doctype" regardless of public/system id presence
    @Test
    public void testNodeName_withPublicAndSystemId_returnsDoctypeConstant() throws Throwable {
        DocumentType dt = new DocumentType("html", "pub", "sys", "");
        assertEquals("#doctype", dt.nodeName());
    }

    // outerHtmlHead: only name present -> "<!DOCTYPE name>"
    @Test
    public void testOuterHtmlHead_nameOnly_rendersBareNameDoctype() throws Throwable {
        DocumentType dt = new DocumentType("html", "", "", "");
        StringBuilder accum = new StringBuilder();
        dt.outerHtmlHead(accum, 0, null);
        assertEquals("<!DOCTYPE html>", accum.toString());
    }

    // outerHtmlHead: publicId present, systemId absent -> "PUBLIC \"id\"" only
    @Test
    public void testOuterHtmlHead_publicIdOnly_rendersPublicKeyword() throws Throwable {
        DocumentType dt = new DocumentType("html", "pub", "", "");
        StringBuilder accum = new StringBuilder();
        dt.outerHtmlHead(accum, 0, null);
        assertEquals("<!DOCTYPE html PUBLIC \"pub\">", accum.toString());
    }

    // outerHtmlHead: both publicId and systemId present -> "PUBLIC \"pub\" \"sys\""
    @Test
    public void testOuterHtmlHead_publicAndSystemId_rendersBothQuoted() throws Throwable {
        DocumentType dt = new DocumentType("html", "pub", "sys", "");
        StringBuilder accum = new StringBuilder();
        dt.outerHtmlHead(accum, 0, null);
        assertEquals("<!DOCTYPE html PUBLIC \"pub\" \"sys\">", accum.toString());
    }

    // BUG TEST: systemId present without publicId must render the "SYSTEM" keyword per DOCTYPE syntax
    @Test
    public void testOuterHtmlHead_systemIdOnly_rendersSystemKeyword() throws Throwable {
        DocumentType dt = new DocumentType("html", "", "bar", "");
        StringBuilder accum = new StringBuilder();
        dt.outerHtmlHead(accum, 0, null);
        assertEquals("<!DOCTYPE html SYSTEM \"bar\">", accum.toString());
    }

    // outerHtmlHead: blank (whitespace-only) name is treated as absent and skipped
    @Test
    public void testOuterHtmlHead_blankName_skipsNameToken() throws Throwable {
        DocumentType dt = new DocumentType(" ", "", "", "");
        StringBuilder accum = new StringBuilder();
        dt.outerHtmlHead(accum, 0, null);
        assertEquals("<!DOCTYPE>", accum.toString());
    }

    // BUG TEST variant: blank publicId must be treated as absent, still emitting SYSTEM keyword for systemId
    @Test
    public void testOuterHtmlHead_blankPublicIdWithSystemId_rendersSystemKeyword() throws Throwable {
        DocumentType dt = new DocumentType("html", " ", "bar", "");
        StringBuilder accum = new StringBuilder();
        dt.outerHtmlHead(accum, 0, null);
        assertEquals("<!DOCTYPE html SYSTEM \"bar\">", accum.toString());
    }

    // outerHtmlHead: all fields blank -> bare "<!DOCTYPE>" tag
    @Test
    public void testOuterHtmlHead_allBlank_rendersBareDoctypeTag() throws Throwable {
        DocumentType dt = new DocumentType(" ", " ", " ", "");
        StringBuilder accum = new StringBuilder();
        dt.outerHtmlHead(accum, 0, null);
        assertEquals("<!DOCTYPE>", accum.toString());
    }

    // outerHtmlHead: appends after existing accum content, preserving prefix
    @Test
    public void testOuterHtmlHead_appendsAfterExistingContent_preservesPrefix() throws Throwable {
        DocumentType dt = new DocumentType("html", "", "", "");
        StringBuilder accum = new StringBuilder("PREFIX");
        dt.outerHtmlHead(accum, 0, null);
        assertEquals("PREFIX<!DOCTYPE html>", accum.toString());
    }

    // outerHtmlHead: depth parameter is not used in rendering, output identical regardless of depth
    @Test
    public void testOuterHtmlHead_depthParameterIgnored_sameOutput() throws Throwable {
        DocumentType dt = new DocumentType("html", "", "", "");
        StringBuilder accum1 = new StringBuilder();
        StringBuilder accum2 = new StringBuilder();
        dt.outerHtmlHead(accum1, 0, null);
        dt.outerHtmlHead(accum2, 5, null);
        assertEquals(accum1.toString(), accum2.toString());
    }

    // outerHtmlHead: calling twice on same accum accumulates both renderings
    @Test
    public void testOuterHtmlHead_calledTwice_accumulatesOutput() throws Throwable {
        DocumentType dt = new DocumentType("html", "", "", "");
        StringBuilder accum = new StringBuilder();
        dt.outerHtmlHead(accum, 0, null);
        dt.outerHtmlHead(accum, 0, null);
        assertEquals("<!DOCTYPE html><!DOCTYPE html>", accum.toString());
    }

    // outerHtmlTail: is a no-op, leaves accum content unchanged
    @Test
    public void testOuterHtmlTail_always_noOpLeavesAccumUnchanged() throws Throwable {
        DocumentType dt = new DocumentType("html", "", "", "");
        StringBuilder accum = new StringBuilder("abc");
        dt.outerHtmlTail(accum, 0, null);
        assertEquals("abc", accum.toString());
    }

    // BUG TEST via full parse round-trip: SYSTEM-only doctype must keep the SYSTEM keyword when re-rendered
    @Test
    public void testJsoupParse_systemOnlyDoctype_rendersSystemKeyword() throws Throwable {
        Document doc = Jsoup.parse("<!DOCTYPE html SYSTEM \"about:legacy-compat\">");
        String html = doc.outerHtml();
        assertTrue(html.contains("SYSTEM"));
        assertTrue(html.contains("about:legacy-compat"));
    }

    // Full parse round-trip: bare doctype re-renders without any identifiers
    @Test
    public void testJsoupParse_bareDoctype_rendersBareDoctype() throws Throwable {
        Document doc = Jsoup.parse("<!DOCTYPE html>");
        String html = doc.outerHtml();
        assertTrue(html.contains("<!DOCTYPE html>"));
    }

    // Full parse round-trip: PUBLIC + SYSTEM doctype preserves both identifiers with PUBLIC keyword
    @Test
    public void testJsoupParse_publicAndSystemDoctype_rendersBothIdentifiers() throws Throwable {
        Document doc = Jsoup.parse("<!DOCTYPE html PUBLIC \"-//W3C//DTD XHTML 1.0 Strict//EN\" \"http://www.w3.org/TR/xhtml1/DTD/xhtml1-strict.dtd\">");
        String html = doc.outerHtml();
        assertTrue(html.contains("PUBLIC"));
        assertTrue(html.contains("-//W3C//DTD XHTML 1.0 Strict//EN"));
        assertTrue(html.contains("http://www.w3.org/TR/xhtml1/DTD/xhtml1-strict.dtd"));
    }

    // Getter check: attr("systemId") reflects exact value passed to constructor
    @Test
    public void testConstructor_attrGetter_returnsExactSystemIdWhenSet() throws Throwable {
        DocumentType dt = new DocumentType("html", "", "http://example.com/dtd", "");
        assertEquals("http://example.com/dtd", dt.attr("systemId"));
    }

    // outerHtmlHead: publicId with special (slash) characters preserved verbatim in output
    @Test
    public void testOuterHtmlHead_publicIdWithSpecialCharacters_preservedInOutput() throws Throwable {
        DocumentType dt = new DocumentType("html", "-//W3C//DTD XHTML 1.0 Strict//EN", "", "");
        StringBuilder accum = new StringBuilder();
        dt.outerHtmlHead(accum, 0, null);
        assertEquals("<!DOCTYPE html PUBLIC \"-//W3C//DTD XHTML 1.0 Strict//EN\">", accum.toString());
    }
}
