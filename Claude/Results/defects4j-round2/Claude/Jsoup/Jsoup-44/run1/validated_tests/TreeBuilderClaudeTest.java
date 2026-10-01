package org.jsoup.parser;

import org.junit.Test;
import static org.junit.Assert.*;

import java.util.ArrayList;

import org.jsoup.Jsoup;
import org.jsoup.nodes.Attributes;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.jsoup.select.Elements;

public class TreeBuilderClaudeTest {

    // Minimal concrete subclass to exercise TreeBuilder's package/protected API.
    private static class TestTreeBuilder extends TreeBuilder {
        final ArrayList<Token> processedTokens = new ArrayList<Token>();
        boolean returnValue = true;

        protected boolean process(Token token) {
            processedTokens.add(token);
            return returnValue;
        }
    }

    // covers: Validate.notNull(input) throw branch inside initialiseParse
    @Test
    public void testInitialiseParse_nullInput_throwsIllegalArgumentException() throws Throwable {
        TestTreeBuilder tb = new TestTreeBuilder();
        try {
            tb.initialiseParse(null, "http://example.com/", ParseErrorList.noTracking());
            fail("expected exception for null input");
        } catch (IllegalArgumentException expected) {
        }
    }

    // covers: Validate.notNull(baseUri) throw branch inside initialiseParse
    @Test
    public void testInitialiseParse_nullBaseUri_throwsIllegalArgumentException() throws Throwable {
        TestTreeBuilder tb = new TestTreeBuilder();
        try {
            tb.initialiseParse("<p>Hi</p>", null, ParseErrorList.noTracking());
            fail("expected exception for null baseUri");
        } catch (IllegalArgumentException expected) {
        }
    }

    // covers: normal path of initialiseParse, all fields assigned
    @Test
    public void testInitialiseParse_validInput_setsFieldsCorrectly() throws Throwable {
        TestTreeBuilder tb = new TestTreeBuilder();
        ParseErrorList errors = ParseErrorList.noTracking();
        tb.initialiseParse("<p>Hi</p>", "http://example.com/", errors);

        assertNotNull(tb.doc);
        assertEquals("http://example.com/", tb.baseUri);
        assertNotNull(tb.stack);
        assertEquals(0, tb.stack.size());
        assertSame(errors, tb.errors);
        assertNotNull(tb.reader);
        assertNotNull(tb.tokeniser);
    }

    // covers: parse(String, String) delegates to initialiseParse + runParser and returns doc
    @Test(timeout = 5000)
    public void testParse_twoArgOverload_returnsSameDocInstanceAndProcessesTokens() throws Throwable {
        TestTreeBuilder tb = new TestTreeBuilder();
        Document result = tb.parse("<p>Hi</p>", "http://example.com/");
        assertNotNull(result);
        assertSame(tb.doc, result);
        assertTrue(tb.processedTokens.size() > 0);
    }

    // covers: parse(String, String, ParseErrorList) uses the provided errors list reference
    @Test(timeout = 5000)
    public void testParse_threeArgOverload_assignsProvidedErrorsList() throws Throwable {
        TestTreeBuilder tb = new TestTreeBuilder();
        ParseErrorList errors = ParseErrorList.noTracking();
        Document result = tb.parse("hello", "http://x/", errors);
        assertNotNull(result);
        assertSame(errors, tb.errors);
    }

    // covers: runParser loop with immediate EOF (0 content tokens before EOF)
    @Test(timeout = 5000)
    public void testRunParser_emptyInput_processesSingleEofToken() throws Throwable {
        TestTreeBuilder tb = new TestTreeBuilder();
        tb.parse("", "http://example.com/");
        assertEquals(1, tb.processedTokens.size());
    }

    // covers: runParser loop with multiple tokens before EOF is reached
    @Test(timeout = 5000)
    public void testRunParser_nonEmptyInput_processesMultipleTokensIncludingEof() throws Throwable {
        TestTreeBuilder tb = new TestTreeBuilder();
        tb.parse("<a>", "http://example.com/");
        assertTrue(tb.processedTokens.size() > 1);
    }

    // covers: protected processStartTag(String) builds a StartTag token and returns process() result
    @Test
    public void testProcessStartTag_name_invokesProcessWithStartTagTokenAndReturnsTrue() throws Throwable {
        TestTreeBuilder tb = new TestTreeBuilder();
        tb.returnValue = true;
        boolean result = tb.processStartTag("div");
        assertTrue(result);
        assertEquals(1, tb.processedTokens.size());
        assertTrue(tb.processedTokens.get(0) instanceof Token.StartTag);
    }

    // covers: processStartTag(String) propagates false return value from process()
    @Test
    public void testProcessStartTag_processReturnsFalse_propagatesFalse() throws Throwable {
        TestTreeBuilder tb = new TestTreeBuilder();
        tb.returnValue = false;
        boolean result = tb.processStartTag("span");
        assertFalse(result);
    }

    // covers: public processStartTag(String, Attributes) builds a StartTag token
    @Test
    public void testProcessStartTagWithAttributes_invokesProcessWithStartTagToken() throws Throwable {
        TestTreeBuilder tb = new TestTreeBuilder();
        Attributes attrs = new Attributes();
        boolean result = tb.processStartTag("div", attrs);
        assertTrue(result);
        assertEquals(1, tb.processedTokens.size());
        assertTrue(tb.processedTokens.get(0) instanceof Token.StartTag);
    }

    // covers: processEndTag(String) builds an EndTag token and returns process() result
    @Test
    public void testProcessEndTag_name_invokesProcessWithEndTagTokenAndReturnsTrue() throws Throwable {
        TestTreeBuilder tb = new TestTreeBuilder();
        tb.returnValue = true;
        boolean result = tb.processEndTag("div");
        assertTrue(result);
        assertEquals(1, tb.processedTokens.size());
        assertTrue(tb.processedTokens.get(0) instanceof Token.EndTag);
    }

    // covers: reused start-tag field across successive calls (same singleton instance)
    @Test
    public void testProcessStartTag_multipleCalls_reuseSameTokenInstance() throws Throwable {
        TestTreeBuilder tb = new TestTreeBuilder();
        tb.processStartTag("div");
        tb.processStartTag("span");
        assertEquals(2, tb.processedTokens.size());
        assertSame(tb.processedTokens.get(0), tb.processedTokens.get(1));
    }

    // covers: currentElement() ternary false branch (empty stack -> null)
    @Test
    public void testCurrentElement_emptyStack_returnsNull() throws Throwable {
        TestTreeBuilder tb = new TestTreeBuilder();
        tb.initialiseParse("", "http://example.com/", ParseErrorList.noTracking());
        assertNull(tb.currentElement());
    }

    // covers: currentElement() ternary true branch with a single element
    @Test
    public void testCurrentElement_singleElementInStack_returnsThatElement() throws Throwable {
        TestTreeBuilder tb = new TestTreeBuilder();
        tb.initialiseParse("", "http://example.com/", ParseErrorList.noTracking());
        Document el = new Document("http://example.com/");
        tb.stack.add(el);
        assertSame(el, tb.currentElement());
    }

    // covers: currentElement() returns the top (last) element, not the bottom (off-by-one check)
    @Test
    public void testCurrentElement_multipleElements_returnsTopOfStackNotBottom() throws Throwable {
        TestTreeBuilder tb = new TestTreeBuilder();
        tb.initialiseParse("", "http://example.com/", ParseErrorList.noTracking());
        Document first = new Document("http://example.com/a");
        Document second = new Document("http://example.com/b");
        tb.stack.add(first);
        tb.stack.add(second);
        assertSame(second, tb.currentElement());
        assertNotSame(first, tb.currentElement());
    }

    // covers: two-arg parse() overload uses a non-null error list (noTracking default)
    @Test(timeout = 5000)
    public void testParse_twoArgOverload_usesNonNullErrorsList() throws Throwable {
        TestTreeBuilder tb = new TestTreeBuilder();
        tb.parse("<a>", "http://example.com/");
        assertNotNull(tb.errors);
    }

    // covers full pipeline: Jsoup.parse normalizes structure, head/title/body exist
    @Test(timeout = 5000)
    public void testJsoupParse_simpleHtml_createsHeadAndBody() throws Throwable {
        Document doc = Jsoup.parse("<html><head><title>T</title></head><body><p>Hi</p></body></html>");
        assertNotNull(doc.head());
        assertNotNull(doc.body());
        assertEquals("T", doc.title());
    }

    // covers: HTML5 rule - unclosed <p> is auto-closed when a new <p> starts (2 sibling paragraphs)
    @Test(timeout = 5000)
    public void testJsoupParse_unclosedParagraphs_autoClosesIntoSiblings() throws Throwable {
        Document doc = Jsoup.parse("<p>One<p>Two");
        Elements paragraphs = doc.select("p");
        assertEquals(2, paragraphs.size());
        assertEquals("One", paragraphs.get(0).text());
        assertEquals("Two", paragraphs.get(1).text());
    }

    // covers: attribute tokens are parsed and stored correctly on elements
    @Test(timeout = 5000)
    public void testJsoupParse_attributesParsedCorrectly() throws Throwable {
        Document doc = Jsoup.parse("<div id=\"main\" class=\"a b\"></div>");
        Element div = doc.select("div").get(0);
        assertEquals("main", div.attr("id"));
        assertEquals("a b", div.attr("class"));
    }

    // covers: text() concatenates nested inline element text without spurious spaces
    @Test(timeout = 5000)
    public void testJsoupParse_textExtraction_concatenatesNestedText() throws Throwable {
        Document doc = Jsoup.parse("<div>Hello <b>World</b>!</div>");
        Element div = doc.select("div").get(0);
        assertEquals("Hello World!", div.text());
    }

    // covers: Jsoup.parseBodyFragment entry point wraps fragment content into a body
    @Test(timeout = 5000)
    public void testJsoupParseBodyFragment_wrapsFragmentTextInBody() throws Throwable {
        Document doc = Jsoup.parseBodyFragment("<p>Hello</p>");
        assertEquals("Hello", doc.body().text());
    }

    // covers: void element (br) never has child nodes, regardless of following content
    @Test(timeout = 5000)
    public void testJsoupParse_voidElement_brHasNoChildNodes() throws Throwable {
        Document doc = Jsoup.parse("<p>Line1<br>Line2</p>");
        Element br = doc.select("br").get(0);
        assertEquals(0, br.childNodeSize());
    }

    // covers: comment nodes are excluded from text()
    @Test(timeout = 5000)
    public void testJsoupParse_commentNode_excludedFromText() throws Throwable {
        Document doc = Jsoup.parse("<div><!-- comment -->Visible</div>");
        Element div = doc.select("div").get(0);
        assertEquals("Visible", div.text());
    }

    // covers: empty input string produces a document with an empty body
    @Test(timeout = 5000)
    public void testJsoupParse_emptyString_bodyHasNoText() throws Throwable {
        Document doc = Jsoup.parse("");
        assertEquals("", doc.body().text());
    }

    // covers: nested elements retain correct parent/child hierarchy
    @Test(timeout = 5000)
    public void testJsoupParse_nestedElements_maintainsParentChildHierarchy() throws Throwable {
        Document doc = Jsoup.parse("<div><span>Inner</span></div>");
        Element div = doc.select("div").get(0);
        Elements spans = div.select("span");
        assertEquals(1, spans.size());
        assertEquals("Inner", spans.get(0).text());
    }

    // covers: tag names are normalized to lowercase by default
    @Test(timeout = 5000)
    public void testJsoupParse_upperCaseTagName_normalizedToLowercase() throws Throwable {
        Document doc = Jsoup.parse("<DIV>Content</DIV>");
        Element div = doc.select("div").get(0);
        assertEquals("div", div.tagName());
    }

    // covers: boolean attributes (no value) are parsed with an empty string value
    @Test(timeout = 5000)
    public void testJsoupParse_booleanAttribute_hasEmptyValue() throws Throwable {
        Document doc = Jsoup.parse("<input disabled>");
        Element input = doc.select("input").get(0);
        assertTrue(input.hasAttr("disabled"));
        assertEquals("", input.attr("disabled"));
    }
}
