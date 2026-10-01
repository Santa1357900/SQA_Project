package org.jsoup.safety;

import org.junit.Test;
import static org.junit.Assert.*;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.jsoup.select.Elements;

public class CleanerClaudeTest {

    // covers Validate.notNull(whitelist) throw path in constructor
    @Test
    public void testConstructor_nullWhitelist_throwsIllegalArgumentException() throws Throwable {
        try {
            new Cleaner(null);
            fail("expected IllegalArgumentException");
        } catch (IllegalArgumentException expected) {
        }
    }

    // covers Validate.notNull(dirtyDocument) throw path in clean()
    @Test
    public void testClean_nullDocument_throwsIllegalArgumentException() throws Throwable {
        Cleaner cleaner = new Cleaner(Whitelist.none());
        try {
            cleaner.clean(null);
            fail("expected IllegalArgumentException");
        } catch (IllegalArgumentException expected) {
        }
    }

    // covers Validate.notNull(dirtyDocument) throw path in isValid()
    @Test
    public void testIsValid_nullDocument_throwsIllegalArgumentException() throws Throwable {
        Cleaner cleaner = new Cleaner(Whitelist.none());
        try {
            cleaner.isValid(null);
            fail("expected IllegalArgumentException");
        } catch (IllegalArgumentException expected) {
        }
    }

    // covers copySafeNodes else-branch (unsafe tag discarded, text kept) with Whitelist.none()
    @Test
    public void testClean_whitelistNone_stripsAllTagsKeepsText() throws Throwable {
        Document dirty = Jsoup.parse("<p>Hello <b>World</b></p>");
        Cleaner cleaner = new Cleaner(Whitelist.none());
        Document clean = cleaner.clean(dirty);
        assertEquals("Hello World", clean.body().text());
        assertEquals(0, clean.body().children().size());
    }

    // covers copySafeNodes loop with zero child nodes
    @Test
    public void testClean_whitelistNone_emptyBody_producesEmptyBody() throws Throwable {
        Document dirty = Jsoup.parse("");
        Cleaner cleaner = new Cleaner(Whitelist.none());
        Document clean = cleaner.clean(dirty);
        assertEquals(0, clean.body().childNodeSize());
    }

    // covers copySafeNodes if-branch (safe tag copied) with simpleText whitelist
    @Test
    public void testClean_simpleText_allowsFormattingTags() throws Throwable {
        Document dirty = Jsoup.parse("<b>Bold</b>");
        Cleaner cleaner = new Cleaner(Whitelist.simpleText());
        Document clean = cleaner.clean(dirty);
        Elements bTags = clean.select("b");
        assertEquals(1, bTags.size());
        assertEquals("Bold", bTags.first().text());
    }

    // covers else-branch recursion: unsafe wrapper tag removed but safe child kept
    @Test
    public void testClean_simpleText_disallowedTagUnwrapped_keepsChildContent() throws Throwable {
        Document dirty = Jsoup.parse("<div><b>Bold</b></div>");
        Cleaner cleaner = new Cleaner(Whitelist.simpleText());
        Document clean = cleaner.clean(dirty);
        assertEquals(0, clean.select("div").size());
        assertEquals(1, clean.select("b").size());
    }

    // covers recursive copySafeNodes through multiple nested unsafe elements
    @Test
    public void testClean_simpleText_nestedDisallowedTags_multipleLevels() throws Throwable {
        Document dirty = Jsoup.parse("<div><span><i>Italic</i></span></div>");
        Cleaner cleaner = new Cleaner(Whitelist.simpleText());
        Document clean = cleaner.clean(dirty);
        assertEquals(0, clean.select("div").size());
        assertEquals(0, clean.select("span").size());
        assertEquals(1, clean.select("i").size());
    }

    // covers createSafeElement attribute loop: safe vs unsafe attribute branches
    @Test
    public void testClean_basic_disallowedAttributeRemoved_allowedAttributeKept() throws Throwable {
        Document dirty = Jsoup.parse("<a href=\"http://example.com\" onclick=\"alert(1)\">Link</a>");
        Cleaner cleaner = new Cleaner(Whitelist.basic());
        Document clean = cleaner.clean(dirty);
        Element a = clean.select("a").first();
        assertEquals("http://example.com", a.attr("href"));
        assertFalse(a.hasAttr("onclick"));
    }

    // covers createSafeElement enforced attributes addAll branch
    @Test
    public void testClean_basic_enforcedRelNofollowAdded() throws Throwable {
        Document dirty = Jsoup.parse("<a href=\"http://example.com\">Link</a>");
        Cleaner cleaner = new Cleaner(Whitelist.basic());
        Document clean = cleaner.clean(dirty);
        Element a = clean.select("a").first();
        assertEquals("nofollow", a.attr("rel"));
    }

    // covers else-branch tag discard for a tag not in basic whitelist
    @Test
    public void testClean_basic_disallowedTagFullyRemoved() throws Throwable {
        Document dirty = Jsoup.parse("<script>alert(1)</script><p>Safe</p>");
        Cleaner cleaner = new Cleaner(Whitelist.basic());
        Document clean = cleaner.clean(dirty);
        assertEquals(0, clean.select("script").size());
        assertEquals("Safe", clean.select("p").first().text());
    }

    // covers custom whitelist enforced attribute path
    @Test
    public void testClean_customWhitelist_enforcedAttributeAdded() throws Throwable {
        Whitelist whitelist = new Whitelist().addTags("p").addEnforcedAttribute("p", "class", "clean");
        Document dirty = Jsoup.parse("<p>Text</p>");
        Cleaner cleaner = new Cleaner(whitelist);
        Document clean = cleaner.clean(dirty);
        assertEquals("clean", clean.select("p").first().attr("class"));
    }

    // covers attribute loop with multiple iterations, mixed safe/unsafe attributes
    @Test
    public void testClean_customWhitelist_multipleAttributesOnOneTag() throws Throwable {
        Whitelist whitelist = new Whitelist().addTags("p").addAttributes("p", "id");
        Document dirty = Jsoup.parse("<p id=\"one\" style=\"color:red\" title=\"t\">Text</p>");
        Cleaner cleaner = new Cleaner(whitelist);
        Document clean = cleaner.clean(dirty);
        Element p = clean.select("p").first();
        assertEquals("one", p.attr("id"));
        assertFalse(p.hasAttr("style"));
        assertFalse(p.hasAttr("title"));
    }

    // covers copySafeNodes loop with many (>1) sibling nodes
    @Test
    public void testClean_multipleSiblingElements_allProcessed() throws Throwable {
        Document dirty = Jsoup.parse("<p>One</p><p>Two</p><p>Three</p>");
        Cleaner cleaner = new Cleaner(Whitelist.basic());
        Document clean = cleaner.clean(dirty);
        assertEquals(3, clean.select("p").size());
    }

    // ensures clean() does not mutate the source document
    @Test
    public void testClean_originalDocumentUnmodified() throws Throwable {
        Document dirty = Jsoup.parse("<div><script>bad()</script><p>Safe</p></div>");
        Cleaner cleaner = new Cleaner(Whitelist.basic());
        cleaner.clean(dirty);
        assertEquals(1, dirty.select("script").size());
        assertEquals(1, dirty.select("div").size());
    }

    // covers Element construction preserving sourceEl.baseUri()
    @Test
    public void testClean_baseUriPreservedOnCleanElement() throws Throwable {
        Document dirty = Jsoup.parse("<p>Text</p>", "http://example.com/");
        Cleaner cleaner = new Cleaner(Whitelist.basic());
        Document clean = cleaner.clean(dirty);
        Element p = clean.select("p").first();
        assertEquals("http://example.com/", p.baseUri());
    }

    // covers TextNode copy branch, verifying decoded entity text is preserved
    @Test
    public void testClean_textWithSpecialCharactersPreserved() throws Throwable {
        Document dirty = Jsoup.parse("<p>Caf&eacute; &amp; Co</p>");
        Cleaner cleaner = new Cleaner(Whitelist.basic());
        Document clean = cleaner.clean(dirty);
        assertEquals("Café & Co", clean.select("p").first().text());
    }

    // covers numDiscarded == 0 -> isValid true branch
    @Test
    public void testIsValid_trueForFullyCompliantDocument() throws Throwable {
        Document dirty = Jsoup.parse("<p>Hello</p>");
        Cleaner cleaner = new Cleaner(Whitelist.basic());
        assertTrue(cleaner.isValid(dirty));
    }

    // covers numDiscarded > 0 -> isValid false branch, unsafe tag
    @Test
    public void testIsValid_falseForDisallowedTag() throws Throwable {
        Document dirty = Jsoup.parse("<script>alert(1)</script>");
        Cleaner cleaner = new Cleaner(Whitelist.basic());
        assertFalse(cleaner.isValid(dirty));
    }

    // covers numDiscarded > 0 -> isValid false branch, unsafe attribute
    @Test
    public void testIsValid_falseForDisallowedAttribute() throws Throwable {
        Document dirty = Jsoup.parse("<a href=\"http://example.com\" onclick=\"x()\">Link</a>");
        Cleaner cleaner = new Cleaner(Whitelist.basic());
        assertFalse(cleaner.isValid(dirty));
    }

    // covers text-only body with Whitelist.none(): nothing to discard
    @Test
    public void testIsValid_trueForPlainTextOnly() throws Throwable {
        Document dirty = Jsoup.parse("Just plain text");
        Cleaner cleaner = new Cleaner(Whitelist.none());
        assertTrue(cleaner.isValid(dirty));
    }

    // covers loop with zero child nodes in isValid path
    @Test
    public void testIsValid_trueForEmptyDocument() throws Throwable {
        Document dirty = Jsoup.parse("");
        Cleaner cleaner = new Cleaner(Whitelist.none());
        assertTrue(cleaner.isValid(dirty));
    }

    // covers safe tag + safe attribute path with Whitelist.relaxed()
    @Test
    public void testClean_relaxed_imgTagAndSrcPreserved() throws Throwable {
        Document dirty = Jsoup.parse("<img src=\"http://example.com/a.png\" onerror=\"x()\">");
        Cleaner cleaner = new Cleaner(Whitelist.relaxed());
        Document clean = cleaner.clean(dirty);
        Element img = clean.select("img").first();
        assertEquals("http://example.com/a.png", img.attr("src"));
        assertFalse(img.hasAttr("onerror"));
    }

    // covers isSafeAttribute protocol restriction via Whitelist.relaxed() img src
    @Test
    public void testClean_relaxed_disallowedProtocolAttributeDiscarded() throws Throwable {
        Document dirty = Jsoup.parse("<img src=\"javascript:alert(1)\">");
        Cleaner cleaner = new Cleaner(Whitelist.relaxed());
        Document clean = cleaner.clean(dirty);
        assertFalse(clean.select("img").first().hasAttr("src"));
    }

    // covers Whitelist.removeTags customization turning a previously safe tag unsafe
    @Test
    public void testClean_removeTagsCustomization() throws Throwable {
        Whitelist whitelist = Whitelist.basic().removeTags("a");
        Document dirty = Jsoup.parse("<a href=\"http://example.com\">Link</a>");
        Cleaner cleaner = new Cleaner(whitelist);
        Document clean = cleaner.clean(dirty);
        assertEquals(0, clean.select("a").size());
        assertEquals("Link", clean.body().text());
    }

    // covers recursive copySafeNodes creating new safe destChild and recursing into it
    @Test
    public void testClean_deeplyNestedSafeElements_preservesHierarchy() throws Throwable {
        Document dirty = Jsoup.parse("<ol><li>Item <b>One</b></li></ol>");
        Cleaner cleaner = new Cleaner(Whitelist.basic());
        Document clean = cleaner.clean(dirty);
        Element li = clean.select("ol > li").first();
        assertNotNull(li);
        assertEquals(1, li.select("b").size());
    }

    // covers safe void element handling
    @Test
    public void testClean_voidElementBrPreserved() throws Throwable {
        Document dirty = Jsoup.parse("<p>Line1<br>Line2</p>");
        Cleaner cleaner = new Cleaner(Whitelist.basic());
        Document clean = cleaner.clean(dirty);
        assertEquals(1, clean.select("br").size());
    }

    // covers clean() using a body-fragment parsed document as input
    @Test
    public void testClean_parseBodyFragment_inputUsed() throws Throwable {
        Document dirty = Jsoup.parseBodyFragment("<p>Fragment <script>bad()</script>text</p>");
        Cleaner cleaner = new Cleaner(Whitelist.basic());
        Document clean = cleaner.clean(dirty);
        assertEquals(0, clean.select("script").size());
        assertTrue(clean.select("p").first().text().indexOf("Fragment") >= 0);
    }

    // covers loop accumulating numDiscarded across multiple unsafe elements
    @Test
    public void testIsValid_multipleDisallowedNodes_stillFalse() throws Throwable {
        Document dirty = Jsoup.parse("<script>a()</script><style>b{}</style><p>Ok</p>");
        Cleaner cleaner = new Cleaner(Whitelist.basic());
        assertFalse(cleaner.isValid(dirty));
    }

    // ensures clean() still produces sanitized output even when isValid() is false
    @Test
    public void testClean_whitelistNone_isValidFalseButCleanSucceeds() throws Throwable {
        Document dirty = Jsoup.parse("<p>Hello</p>");
        Cleaner cleaner = new Cleaner(Whitelist.none());
        assertFalse(cleaner.isValid(dirty));
        Document clean = cleaner.clean(dirty);
        assertEquals("Hello", clean.body().text());
    }
}
