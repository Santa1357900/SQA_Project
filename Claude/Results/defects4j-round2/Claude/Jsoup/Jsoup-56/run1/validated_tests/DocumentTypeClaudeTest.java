package org.jsoup.nodes;

import org.junit.Test;
import static org.junit.Assert.*;
import org.jsoup.Jsoup;

public class DocumentTypeClaudeTest {

    // Constructor: name value is stored under the "name" attribute
    @Test
    public void testConstructor_setsNameAttribute() throws Throwable {
        DocumentType dt = new DocumentType("html", "", "", "");
        assertEquals("html", dt.attr("name"));
    }

    // Constructor: publicId value is stored under the "publicId" attribute
    @Test
    public void testConstructor_setsPublicIdAttribute() throws Throwable {
        DocumentType dt = new DocumentType("html", "-//W3C//DTD HTML 4.01//EN", "", "");
        assertEquals("-//W3C//DTD HTML 4.01//EN", dt.attr("publicId"));
    }

    // Constructor: systemId value is stored under the "systemId" attribute
    @Test
    public void testConstructor_setsSystemIdAttribute() throws Throwable {
        DocumentType dt = new DocumentType("html", "", "about:legacy-compat", "");
        assertEquals("about:legacy-compat", dt.attr("systemId"));
    }

    // Constructor: baseUri is propagated to Node
    @Test
    public void testConstructor_setsBaseUri() throws Throwable {
        DocumentType dt = new DocumentType("html", "", "", "http://example.com/");
        assertEquals("http://example.com/", dt.baseUri());
    }

    // Constructor: empty strings for all fields must not throw and store empty name
    @Test
    public void testConstructor_emptyValues_doesNotThrow() throws Throwable {
        DocumentType dt = new DocumentType("", "", "", "");
        assertEquals("", dt.attr("name"));
    }

    // nodeName() must always return the "#doctype" constant per contract
    @Test
    public void testNodeName_returnsDoctypeConstant() throws Throwable {
        DocumentType dt = new DocumentType("html", "", "", "");
        assertEquals("#doctype", dt.nodeName());
    }

    // Public constants PUBLIC_KEY / SYSTEM_KEY must hold the DOCTYPE grammar keywords
    @Test
    public void testConstants_values() throws Throwable {
        assertEquals("PUBLIC", DocumentType.PUBLIC_KEY);
        assertEquals("SYSTEM", DocumentType.SYSTEM_KEY);
    }

    // outerHtmlHead: html syntax, no public/system ids -> lowercase html5 doctype
    @Test
    public void testOuterHtmlHead_htmlSyntaxNoPublicNoSystem_lowercaseDoctype() throws Throwable {
        DocumentType dt = new DocumentType("html", "", "", "");
        Document.OutputSettings settings = new Document.OutputSettings();
        settings.syntax(Document.OutputSettings.Syntax.html);
        StringBuilder sb = new StringBuilder();
        dt.outerHtmlHead(sb, 0, settings);
        assertEquals("<!doctype html>", sb.toString());
    }

    // outerHtmlHead: html syntax but publicId present -> uppercase DOCTYPE branch
    @Test
    public void testOuterHtmlHead_htmlSyntaxWithPublicId_uppercaseDoctype() throws Throwable {
        DocumentType dt = new DocumentType("html", "-//W3C//DTD XHTML 1.0 Strict//EN", "", "");
        Document.OutputSettings settings = new Document.OutputSettings();
        settings.syntax(Document.OutputSettings.Syntax.html);
        StringBuilder sb = new StringBuilder();
        dt.outerHtmlHead(sb, 0, settings);
        assertTrue(sb.toString().startsWith("<!DOCTYPE"));
    }

    // outerHtmlHead: html syntax but systemId present -> uppercase DOCTYPE branch
    @Test
    public void testOuterHtmlHead_htmlSyntaxWithSystemId_uppercaseDoctype() throws Throwable {
        DocumentType dt = new DocumentType("html", "", "about:legacy-compat", "");
        Document.OutputSettings settings = new Document.OutputSettings();
        settings.syntax(Document.OutputSettings.Syntax.html);
        StringBuilder sb = new StringBuilder();
        dt.outerHtmlHead(sb, 0, settings);
        assertTrue(sb.toString().startsWith("<!DOCTYPE"));
    }

    // outerHtmlHead: xml syntax -> always uppercase DOCTYPE regardless of public/system
    @Test
    public void testOuterHtmlHead_xmlSyntax_alwaysUppercase() throws Throwable {
        DocumentType dt = new DocumentType("html", "", "", "");
        Document.OutputSettings settings = new Document.OutputSettings();
        settings.syntax(Document.OutputSettings.Syntax.xml);
        StringBuilder sb = new StringBuilder();
        dt.outerHtmlHead(sb, 0, settings);
        assertTrue(sb.toString().startsWith("<!DOCTYPE"));
    }

    // outerHtmlHead: blank name omits the name segment entirely
    @Test
    public void testOuterHtmlHead_nameBlank_omitsNamePart() throws Throwable {
        DocumentType dt = new DocumentType("", "", "", "");
        Document.OutputSettings settings = new Document.OutputSettings();
        settings.syntax(Document.OutputSettings.Syntax.html);
        StringBuilder sb = new StringBuilder();
        dt.outerHtmlHead(sb, 0, settings);
        assertEquals("<!doctype>", sb.toString());
    }

    // outerHtmlHead: non-blank name includes the name segment
    @Test
    public void testOuterHtmlHead_nameNonBlank_includesNamePart() throws Throwable {
        DocumentType dt = new DocumentType("html", "", "", "");
        Document.OutputSettings settings = new Document.OutputSettings();
        settings.syntax(Document.OutputSettings.Syntax.html);
        StringBuilder sb = new StringBuilder();
        dt.outerHtmlHead(sb, 0, settings);
        assertTrue(sb.toString().contains(" html"));
    }

    // outerHtmlHead: blank publicId omits the PUBLIC segment
    @Test
    public void testOuterHtmlHead_publicIdBlank_omitsPublicPart() throws Throwable {
        DocumentType dt = new DocumentType("html", "", "", "");
        Document.OutputSettings settings = new Document.OutputSettings();
        settings.syntax(Document.OutputSettings.Syntax.html);
        StringBuilder sb = new StringBuilder();
        dt.outerHtmlHead(sb, 0, settings);
        assertFalse(sb.toString().contains("PUBLIC"));
    }

    // outerHtmlHead: present publicId includes PUBLIC keyword with quoted value
    @Test
    public void testOuterHtmlHead_publicIdPresent_includesPublicKeyword() throws Throwable {
        DocumentType dt = new DocumentType("html", "-//W3C//DTD HTML 4.01//EN", "", "");
        Document.OutputSettings settings = new Document.OutputSettings();
        settings.syntax(Document.OutputSettings.Syntax.html);
        StringBuilder sb = new StringBuilder();
        dt.outerHtmlHead(sb, 0, settings);
        assertEquals("<!DOCTYPE html PUBLIC \"-//W3C//DTD HTML 4.01//EN\">", sb.toString());
    }

    // outerHtmlHead: blank systemId omits the system literal
    @Test
    public void testOuterHtmlHead_systemIdBlank_omitsSystemPart() throws Throwable {
        DocumentType dt = new DocumentType("html", "-//W3C//DTD HTML 4.01//EN", "", "");
        Document.OutputSettings settings = new Document.OutputSettings();
        settings.syntax(Document.OutputSettings.Syntax.html);
        StringBuilder sb = new StringBuilder();
        dt.outerHtmlHead(sb, 0, settings);
        assertFalse(sb.toString().contains("SYSTEM"));
    }



    // outerHtmlHead: both publicId and systemId present -> combined correct format
    @Test
    public void testOuterHtmlHead_bothPublicAndSystemId_correctFormat() throws Throwable {
        DocumentType dt = new DocumentType("html", "-//W3C//DTD XHTML 1.0 Strict//EN",
                "http://www.w3.org/TR/xhtml1/DTD/xhtml1-strict.dtd", "");
        Document.OutputSettings settings = new Document.OutputSettings();
        settings.syntax(Document.OutputSettings.Syntax.html);
        StringBuilder sb = new StringBuilder();
        dt.outerHtmlHead(sb, 0, settings);
        String out = sb.toString();
        assertTrue(out.contains("PUBLIC \"-//W3C//DTD XHTML 1.0 Strict//EN\""));
        assertTrue(out.endsWith("xhtml1-strict.dtd\">"));
    }

    // outerHtmlHead: output always terminates with the closing '>' character
    @Test
    public void testOuterHtmlHead_endsWithClosingBracket() throws Throwable {
        DocumentType dt = new DocumentType("html", "", "", "");
        Document.OutputSettings settings = new Document.OutputSettings();
        settings.syntax(Document.OutputSettings.Syntax.html);
        StringBuilder sb = new StringBuilder();
        dt.outerHtmlHead(sb, 0, settings);
        assertTrue(sb.toString().endsWith(">"));
    }

    // outerHtmlHead: whitespace-only publicId is treated as blank (StringUtil.isBlank)
    @Test
    public void testOuterHtmlHead_publicIdWhitespaceOnly_treatedAsBlank() throws Throwable {
        DocumentType dt = new DocumentType("html", " ", "", "");
        Document.OutputSettings settings = new Document.OutputSettings();
        settings.syntax(Document.OutputSettings.Syntax.html);
        StringBuilder sb = new StringBuilder();
        dt.outerHtmlHead(sb, 0, settings);
        assertEquals("<!doctype html>", sb.toString());
    }

    // outerHtmlHead: whitespace-only systemId is treated as blank (StringUtil.isBlank)
    @Test
    public void testOuterHtmlHead_systemIdWhitespaceOnly_treatedAsBlank() throws Throwable {
        DocumentType dt = new DocumentType("html", "", "   ", "");
        Document.OutputSettings settings = new Document.OutputSettings();
        settings.syntax(Document.OutputSettings.Syntax.html);
        StringBuilder sb = new StringBuilder();
        dt.outerHtmlHead(sb, 0, settings);
        assertEquals("<!doctype html>", sb.toString());
    }

    // outerHtmlTail: must not append anything to the accumulator
    @Test
    public void testOuterHtmlTail_doesNothing() throws Throwable {
        DocumentType dt = new DocumentType("html", "", "", "");
        Document.OutputSettings settings = new Document.OutputSettings();
        StringBuilder sb = new StringBuilder("before");
        dt.outerHtmlTail(sb, 0, settings);
        assertEquals("before", sb.toString());
    }

    // Integration: Jsoup.parse renders a plain html5 doctype in lowercase form
    @Test
    public void testJsoupParse_html5Doctype_outputsLowercase() throws Throwable {
        Document doc = Jsoup.parse("<!DOCTYPE html><html><head></head><body></body></html>");
        String html = doc.outerHtml();
        assertTrue(html.contains("<!doctype html>"));
    }

    // Integration BUG TEST: doctype with only SYSTEM identifier must keep the SYSTEM keyword
    @Test
    public void testJsoupParse_doctypeWithSystemIdOnly_includesSystemKeyword() throws Throwable {
        Document doc = Jsoup.parse("<!DOCTYPE html SYSTEM \"about:legacy-compat\"><html><head></head><body></body></html>");
        String html = doc.outerHtml();
        assertTrue(html.contains("SYSTEM"));
    }

    // Integration: doctype with PUBLIC and SYSTEM identifiers renders correctly (not the bug path)
    @Test
    public void testJsoupParse_doctypeWithPublicAndSystem_correctOutput() throws Throwable {
        Document doc = Jsoup.parse("<!DOCTYPE html PUBLIC \"-//W3C//DTD XHTML 1.0 Strict//EN\" \"http://www.w3.org/TR/xhtml1/DTD/xhtml1-strict.dtd\"><html><head></head><body></body></html>");
        String html = doc.outerHtml();
        assertTrue(html.contains("PUBLIC \"-//W3C//DTD XHTML 1.0 Strict//EN\""));
    }
}
