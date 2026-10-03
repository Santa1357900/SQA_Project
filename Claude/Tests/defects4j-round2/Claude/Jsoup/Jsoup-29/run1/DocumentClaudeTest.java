package org.jsoup.nodes;

import org.junit.Test;
import static org.junit.Assert.*;
import org.jsoup.Jsoup;
import org.jsoup.select.Elements;
import java.nio.charset.Charset;

public class DocumentClaudeTest {

    // covers constructor Document(String baseUri) setting nodeName and baseUri
    @Test
    public void testConstructor_setsNodeNameAndBaseUri() throws Throwable {
        Document doc = new Document("http://example.com/");
        assertEquals("#document", doc.nodeName());
        assertEquals("http://example.com/", doc.baseUri());
    }

    // covers createShell -> Validate.notNull(baseUri) throw branch
    @Test
    public void testCreateShell_nullBaseUri_throwsException() throws Throwable {
        try {
            Document.createShell(null);
            fail("expected IllegalArgumentException");
        } catch (IllegalArgumentException expected) {
        }
    }

    // covers createShell normal path creating html/head/body structure
    @Test
    public void testCreateShell_validBaseUri_createsHtmlHeadBody() throws Throwable {
        Document doc = Document.createShell("http://example.com/");
        assertNotNull(doc.head());
        assertNotNull(doc.body());
        assertEquals("html", doc.head().parent().nodeName());
        assertEquals("html", doc.body().parent().nodeName());
    }

    // covers head() found branch via findFirstElementByTagName
    @Test
    public void testHead_whenPresent_returnsHeadElement() throws Throwable {
        Document doc = Document.createShell("http://example.com/");
        assertEquals("head", doc.head().nodeName());
    }

    // covers head() not-found branch returning null
    @Test
    public void testHead_whenAbsent_returnsNull() throws Throwable {
        Document doc = new Document("http://example.com/");
        assertNull(doc.head());
    }

    // covers body() found branch
    @Test
    public void testBody_whenPresent_returnsBodyElement() throws Throwable {
        Document doc = Document.createShell("http://example.com/");
        assertEquals("body", doc.body().nodeName());
    }

    // covers body() not-found branch returning null
    @Test
    public void testBody_whenAbsent_returnsNull() throws Throwable {
        Document doc = new Document("http://example.com/");
        assertNull(doc.body());
    }

    // covers title() ternary false branch (no title element) returning ""
    @Test
    public void testTitle_noTitleElement_returnsEmptyString() throws Throwable {
        Document doc = Document.createShell("http://example.com/");
        assertEquals("", doc.title());
    }

    // covers title() ternary true branch with trimming of surrounding whitespace
    @Test
    public void testTitle_withTitleElement_returnsTrimmedText() throws Throwable {
        Document doc = Document.createShell("http://example.com/");
        doc.head().appendElement("title").text("  My Title  ");
        assertEquals("My Title", doc.title());
    }

    // covers title() trim() reducing whitespace-only content to empty string
    @Test
    public void testTitle_whitespaceOnlyTitle_returnsEmptyString() throws Throwable {
        Document doc = Document.createShell("http://example.com/");
        doc.head().appendElement("title").text("   ");
        assertEquals("", doc.title());
    }

    // covers title(String) -> Validate.notNull(title) throw branch
    @Test
    public void testTitleSetter_nullTitle_throwsException() throws Throwable {
        Document doc = Document.createShell("http://example.com/");
        try {
            doc.title(null);
            fail("expected IllegalArgumentException");
        } catch (IllegalArgumentException expected) {
        }
    }

    // covers title(String) else branch: updating existing title element
    @Test
    public void testTitleSetter_existingTitle_updatesText() throws Throwable {
        Document doc = Document.createShell("http://example.com/");
        doc.head().appendElement("title").text("Old");
        doc.title("New");
        assertEquals("New", doc.title());
    }

    // covers title(String) if branch: creating title in head when absent
    @Test
    public void testTitleSetter_noTitleElement_createsInHead() throws Throwable {
        Document doc = Document.createShell("http://example.com/");
        doc.title("Created");
        assertEquals("Created", doc.title());
        assertEquals("title", doc.head().getElementsByTag("title").first().nodeName());
    }

    // covers createElement() producing detached element sharing document's baseUri
    @Test
    public void testCreateElement_returnsElementWithDocBaseUriAndNoParent() throws Throwable {
        Document doc = new Document("http://example.com/");
        Element el = doc.createElement("div");
        assertEquals("div", el.nodeName());
        assertEquals("http://example.com/", el.baseUri());
        assertNull(el.parent());
    }

    // covers normalise() when no html/head/body exist at all
    @Test
    public void testNormalise_noHtmlNoHeadNoBody_createsAll() throws Throwable {
        Document doc = new Document("http://example.com/");
        doc.normalise();
        assertNotNull(doc.head());
        assertNotNull(doc.body());
    }

    // covers normalise() when html exists but head/body are missing
    @Test
    public void testNormalise_missingHeadAndBody_createsThem() throws Throwable {
        Document doc = new Document("http://example.com/");
        doc.appendElement("html");
        doc.normalise();
        assertNotNull(doc.head());
        assertNotNull(doc.body());
    }

    // covers normaliseStructure merging duplicate <body> elements into one master
    @Test
    public void testNormalise_duplicateBody_mergesContent() throws Throwable {
        Document doc = new Document("http://example.com/");
        Element html = doc.appendElement("html");
        html.appendElement("head");
        Element body1 = html.appendElement("body");
        body1.appendElement("p").text("One");
        Element body2 = html.appendElement("body");
        body2.appendElement("p").text("Two");
        doc.normalise();
        assertEquals(1, doc.getElementsByTag("body").size());
        Elements paras = doc.body().getElementsByTag("p");
        assertEquals(2, paras.size());
    }

    // covers normaliseStructure merging duplicate <head> elements into one master
    @Test
    public void testNormalise_duplicateHead_mergesContent() throws Throwable {
        Document doc = new Document("http://example.com/");
        Element html = doc.appendElement("html");
        Element head1 = html.appendElement("head");
        head1.appendElement("title").text("First");
        html.appendElement("body");
        Element head2 = html.appendElement("head");
        head2.appendElement("title").text("Second");
        doc.normalise();
        assertEquals(1, doc.getElementsByTag("head").size());
        Elements titles = doc.head().getElementsByTag("title");
        assertEquals(2, titles.size());
    }

    // covers normaliseTextNodes moving stray non-blank text under <html> into <body>
    @Test
    public void testNormalise_strayTextUnderHtml_movesToBody() throws Throwable {
        Document doc = new Document("http://example.com/");
        Element html = doc.appendElement("html");
        html.appendElement("head");
        html.appendElement("body").appendElement("p").text("existing");
        html.appendChild(new TextNode("stray", ""));
        doc.normalise();
        String bodyText = doc.body().text();
        assertTrue(bodyText.contains("stray"));
        assertTrue(bodyText.contains("existing"));
    }

    // covers outerHtml() not including the "#document" wrapper
    @Test
    public void testOuterHtml_noDocumentWrapper() throws Throwable {
        Document doc = Document.createShell("http://example.com/");
        String html = doc.outerHtml();
        assertFalse(html.contains("#document"));
        assertTrue(html.contains("<html"));
    }

    // covers text(String) setting body content while preserving doc structure
    @Test
    public void testTextSetter_setsBodyTextPreservingStructure() throws Throwable {
        Document doc = Document.createShell("http://example.com/");
        doc.text("Hello World");
        assertEquals("Hello World", doc.body().text());
        assertNotNull(doc.head());
    }

    // covers nodeName() always returning "#document"
    @Test
    public void testNodeName_returnsDocumentTag() throws Throwable {
        Document doc = new Document("http://example.com/");
        assertEquals("#document", doc.nodeName());
    }

    // covers clone() producing independent OutputSettings instance
    @Test
    public void testClone_createsIndependentOutputSettings() throws Throwable {
        Document doc = Document.createShell("http://example.com/");
        Document clone = doc.clone();
        clone.outputSettings().prettyPrint(false);
        assertTrue(doc.outputSettings().prettyPrint());
        assertFalse(clone.outputSettings().prettyPrint());
    }

    // covers clone() preserving title/content while creating a distinct object
    @Test
    public void testClone_preservesTitleAndStructure() throws Throwable {
        Document doc = Document.createShell("http://example.com/");
        doc.title("Original");
        Document clone = doc.clone();
        assertEquals("Original", clone.title());
        assertNotSame(doc, clone);
    }

    // covers default OutputSettings values per Javadoc contract
    @Test
    public void testOutputSettings_defaultValues() throws Throwable {
        Document doc = new Document("http://example.com/");
        Document.OutputSettings settings = doc.outputSettings();
        assertEquals(Entities.EscapeMode.base, settings.escapeMode());
        assertTrue(settings.prettyPrint());
        assertEquals(1, settings.indentAmount());
        assertEquals("UTF-8", settings.charset().name());
    }

    // covers outputSettings(OutputSettings) -> Validate.notNull throw branch
    @Test
    public void testOutputSettingsSetter_nullSettings_throwsException() throws Throwable {
        Document doc = new Document("http://example.com/");
        try {
            doc.outputSettings(null);
            fail("expected IllegalArgumentException");
        } catch (IllegalArgumentException expected) {
        }
    }

    // covers outputSettings(OutputSettings) setting and returning same document instance
    @Test
    public void testOutputSettingsSetter_setsAndReturnsSameInstance() throws Throwable {
        Document doc = new Document("http://example.com/");
        Document.OutputSettings settings = new Document.OutputSettings();
        settings.prettyPrint(false);
        Document result = doc.outputSettings(settings);
        assertSame(doc, result);
        assertFalse(doc.outputSettings().prettyPrint());
    }

    // covers OutputSettings.escapeMode() getter/setter chaining
    @Test
    public void testOutputSettingsEscapeMode_getSet() throws Throwable {
        Document.OutputSettings settings = new Document.OutputSettings();
        Document.OutputSettings result = settings.escapeMode(Entities.EscapeMode.extended);
        assertEquals(Entities.EscapeMode.extended, settings.escapeMode());
        assertSame(settings, result);
    }

    // covers OutputSettings.charset(Charset) updating charset and encoder
    @Test
    public void testOutputSettingsCharset_setByCharsetObject() throws Throwable {
        Document.OutputSettings settings = new Document.OutputSettings();
        settings.charset(Charset.forName("ISO-8859-1"));
        assertEquals("ISO-8859-1", settings.charset().name());
    }

    // covers OutputSettings.charset(String) resolving by charset name
    @Test
    public void testOutputSettingsCharset_setByName() throws Throwable {
        Document.OutputSettings settings = new Document.OutputSettings();
        settings.charset("UTF-16");
        assertEquals("UTF-16", settings.charset().name());
    }

    // covers OutputSettings.prettyPrint() getter/setter toggle both ways
    @Test
    public void testOutputSettingsPrettyPrint_getSet() throws Throwable {
        Document.OutputSettings settings = new Document.OutputSettings();
        settings.prettyPrint(false);
        assertFalse(settings.prettyPrint());
        settings.prettyPrint(true);
        assertTrue(settings.prettyPrint());
    }

    // covers OutputSettings.indentAmount(int) valid boundary values (0 and positive)
    @Test
    public void testOutputSettingsIndentAmount_validValue() throws Throwable {
        Document.OutputSettings settings = new Document.OutputSettings();
        settings.indentAmount(4);
        assertEquals(4, settings.indentAmount());
        settings.indentAmount(0);
        assertEquals(0, settings.indentAmount());
    }

    // covers OutputSettings.indentAmount(int) -> Validate.isTrue(>=0) throw branch
    @Test
    public void testOutputSettingsIndentAmount_negativeValue_throwsException() throws Throwable {
        Document.OutputSettings settings = new Document.OutputSettings();
        try {
            settings.indentAmount(-1);
            fail("expected IllegalArgumentException");
        } catch (IllegalArgumentException expected) {
        }
    }

    // covers OutputSettings.clone() producing an independently mutable copy
    @Test
    public void testOutputSettingsClone_independentCopy() throws Throwable {
        Document.OutputSettings settings = new Document.OutputSettings();
        settings.escapeMode(Entities.EscapeMode.extended);
        settings.charset("ISO-8859-1");
        Document.OutputSettings clone = settings.clone();
        clone.escapeMode(Entities.EscapeMode.base);
        assertEquals(Entities.EscapeMode.extended, settings.escapeMode());
        assertEquals(Entities.EscapeMode.base, clone.escapeMode());
        assertEquals("ISO-8859-1", clone.charset().name());
    }

    // covers quirksMode() default value and quirksMode(QuirksMode) setter chaining
    @Test
    public void testQuirksMode_defaultAndSetter() throws Throwable {
        Document doc = new Document("http://example.com/");
        assertEquals(Document.QuirksMode.noQuirks, doc.quirksMode());
        Document result = doc.quirksMode(Document.QuirksMode.quirks);
        assertEquals(Document.QuirksMode.quirks, doc.quirksMode());
        assertSame(doc, result);
    }

    // covers head()/body()/title() through a real parsed document end-to-end
    @Test
    public void testHeadBodyTitle_fromParsedDocument() throws Throwable {
        Document doc = Jsoup.parse("<html><head><title>T</title></head><body><p>Hi</p></body></html>");
        assertEquals("head", doc.head().nodeName());
        assertEquals("body", doc.body().nodeName());
        assertEquals("T", doc.title());
    }
}
