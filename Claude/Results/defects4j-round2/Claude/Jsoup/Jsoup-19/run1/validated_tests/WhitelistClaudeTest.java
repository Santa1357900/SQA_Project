package org.jsoup.safety;

import org.junit.Before;
import org.junit.Test;
import static org.junit.Assert.*;

import org.jsoup.Jsoup;
import org.jsoup.nodes.Attribute;
import org.jsoup.nodes.Attributes;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;

public class WhitelistClaudeTest {

    private Element simpleElement;

    @Before
    public void setUp() throws Throwable {
        simpleElement = Jsoup.parse("<p></p>").body().child(0);
    }

    // none(): no tags allowed at all
    @Test
    public void testNone_defaultWhitelist_noTagsAllowed() throws Throwable {
        Whitelist wl = Whitelist.none();
        assertFalse(wl.isSafeTag("p"));
        assertFalse(wl.isSafeTag("b"));
    }

    // simpleText(): only b, em, i, strong, u allowed, others not
    @Test
    public void testSimpleText_onlyTextFormattingTagsAllowed() throws Throwable {
        Whitelist wl = Whitelist.simpleText();
        assertTrue(wl.isSafeTag("b"));
        assertTrue(wl.isSafeTag("em"));
        assertTrue(wl.isSafeTag("u"));
        assertFalse(wl.isSafeTag("p"));
    }

    // basic(): tags allowed, img not allowed, href protocol check
    @Test
    public void testBasic_tagsAndProtocolAllowed() throws Throwable {
        Whitelist wl = Whitelist.basic();
        assertTrue(wl.isSafeTag("a"));
        assertTrue(wl.isSafeTag("blockquote"));
        assertFalse(wl.isSafeTag("img"));
        Element el = Jsoup.parse("<a href=\"http://example.com\"></a>", "http://example.com").body().child(0);
        Attribute attr = new Attribute("href", "http://example.com");
        assertTrue(wl.isSafeAttribute("a", el, attr));
    }

    // basic(): enforced rel=nofollow attribute for a tag
    @Test
    public void testBasic_enforcedRelNofollowOnAnchor() throws Throwable {
        Whitelist wl = Whitelist.basic();
        Attributes attrs = wl.getEnforcedAttributes("a");
        assertEquals("nofollow", attrs.get("rel"));
    }

    // basicWithImages(): inherits basic and adds img tag
    @Test
    public void testBasicWithImages_includesImgAndBasicTags() throws Throwable {
        Whitelist wl = Whitelist.basicWithImages();
        assertTrue(wl.isSafeTag("img"));
        assertTrue(wl.isSafeTag("a"));
    }

    // relaxed(): structural tags like table allowed
    @Test
    public void testRelaxed_structuralTagsAllowed() throws Throwable {
        Whitelist wl = Whitelist.relaxed();
        assertTrue(wl.isSafeTag("table"));
        assertTrue(wl.isSafeTag("blockquote"));
        assertTrue(wl.isSafeTag("img"));
    }

    // addTags(): multiple tags added, one not added stays unsafe
    @Test
    public void testAddTags_multipleTags_allowedAndDisallowed() throws Throwable {
        Whitelist wl = new Whitelist().addTags("p", "div");
        assertTrue(wl.isSafeTag("p"));
        assertTrue(wl.isSafeTag("div"));
        assertFalse(wl.isSafeTag("span"));
    }

    // addTags(): null varargs array throws IllegalArgumentException via Validate.notNull
    @Test
    public void testAddTags_nullArray_throwsIllegalArgumentException() throws Throwable {
        try {
            new Whitelist().addTags((String[]) null);
            fail("expected IllegalArgumentException");
        } catch (IllegalArgumentException expected) { }
    }

    // addTags(): empty tag name throws IllegalArgumentException via Validate.notEmpty
    @Test
    public void testAddTags_emptyTagName_throwsIllegalArgumentException() throws Throwable {
        try {
            new Whitelist().addTags("");
            fail("expected IllegalArgumentException");
        } catch (IllegalArgumentException expected) { }
    }

    // addAttributes(): allowed attribute passes isSafeAttribute
    @Test
    public void testAddAttributes_allowedAttribute_isSafe() throws Throwable {
        Whitelist wl = new Whitelist().addAttributes("p", "class", "id");
        Attribute attr = new Attribute("class", "x");
        assertTrue(wl.isSafeAttribute("p", simpleElement, attr));
    }

    // addAttributes(): attribute not in the configured set is unsafe
    @Test
    public void testAddAttributes_notConfiguredAttribute_isUnsafe() throws Throwable {
        Whitelist wl = new Whitelist().addAttributes("p", "class");
        Attribute attr = new Attribute("style", "x");
        assertFalse(wl.isSafeAttribute("p", simpleElement, attr));
    }

    // addAttributes(): calling twice merges attribute sets for same tag
    @Test
    public void testAddAttributes_calledTwice_mergesAttributeSets() throws Throwable {
        Whitelist wl = new Whitelist().addAttributes("p", "class");
        wl.addAttributes("p", "id");
        assertTrue(wl.isSafeAttribute("p", simpleElement, new Attribute("class", "x")));
        assertTrue(wl.isSafeAttribute("p", simpleElement, new Attribute("id", "y")));
    }

    // addAttributes(): null keys array throws IllegalArgumentException
    @Test
    public void testAddAttributes_nullKeys_throwsIllegalArgumentException() throws Throwable {
        try {
            new Whitelist().addAttributes("p", (String[]) null);
            fail("expected IllegalArgumentException");
        } catch (IllegalArgumentException expected) { }
    }

    // addAttributes(): empty tag throws IllegalArgumentException
    @Test
    public void testAddAttributes_emptyTag_throwsIllegalArgumentException() throws Throwable {
        try {
            new Whitelist().addAttributes("", "class");
            fail("expected IllegalArgumentException");
        } catch (IllegalArgumentException expected) { }
    }

    // addAttributes(): empty key throws IllegalArgumentException
    @Test
    public void testAddAttributes_emptyKey_throwsIllegalArgumentException() throws Throwable {
        try {
            new Whitelist().addAttributes("p", "");
            fail("expected IllegalArgumentException");
        } catch (IllegalArgumentException expected) { }
    }

    // addEnforcedAttribute(): basic enforcement is retrievable
    @Test
    public void testAddEnforcedAttribute_basic_isRetrievable() throws Throwable {
        Whitelist wl = new Whitelist().addEnforcedAttribute("a", "target", "_blank");
        Attributes attrs = wl.getEnforcedAttributes("a");
        assertEquals("_blank", attrs.get("target"));
    }

    // addEnforcedAttribute(): calling again for same key overrides previous value
    @Test
    public void testAddEnforcedAttribute_calledAgain_overridesValue() throws Throwable {
        Whitelist wl = new Whitelist().addEnforcedAttribute("a", "target", "_blank");
        wl.addEnforcedAttribute("a", "target", "_self");
        Attributes attrs = wl.getEnforcedAttributes("a");
        assertEquals("_self", attrs.get("target"));
    }

    // addEnforcedAttribute(): empty tag throws IllegalArgumentException
    @Test
    public void testAddEnforcedAttribute_emptyTag_throwsIllegalArgumentException() throws Throwable {
        try {
            new Whitelist().addEnforcedAttribute("", "rel", "nofollow");
            fail("expected IllegalArgumentException");
        } catch (IllegalArgumentException expected) { }
    }

    // addEnforcedAttribute(): empty key throws IllegalArgumentException
    @Test
    public void testAddEnforcedAttribute_emptyKey_throwsIllegalArgumentException() throws Throwable {
        try {
            new Whitelist().addEnforcedAttribute("a", "", "nofollow");
            fail("expected IllegalArgumentException");
        } catch (IllegalArgumentException expected) { }
    }

    // addEnforcedAttribute(): empty value throws IllegalArgumentException
    @Test
    public void testAddEnforcedAttribute_emptyValue_throwsIllegalArgumentException() throws Throwable {
        try {
            new Whitelist().addEnforcedAttribute("a", "rel", "");
            fail("expected IllegalArgumentException");
        } catch (IllegalArgumentException expected) { }
    }

    // preserveRelativeLinks(): returns same instance for chaining
    @Test
    public void testPreserveRelativeLinks_returnsSameInstanceForChaining() throws Throwable {
        Whitelist wl = new Whitelist();
        Whitelist returned = wl.preserveRelativeLinks(true);
        assertSame(wl, returned);
    }

    // addProtocols(): matching protocol allows attribute through isSafeAttribute
    @Test
    public void testAddProtocols_matchingProtocol_isSafe() throws Throwable {
        Whitelist wl = new Whitelist().addAttributes("a", "href").addProtocols("a", "href", "http");
        Element el = Jsoup.parse("<a href=\"http://example.com\"></a>", "http://example.com").body().child(0);
        Attribute attr = new Attribute("href", "http://example.com");
        assertTrue(wl.isSafeAttribute("a", el, attr));
    }

    // addProtocols(): calling twice merges protocol sets for same tag/key
    @Test
    public void testAddProtocols_calledTwice_mergesProtocolSets() throws Throwable {
        Whitelist wl = new Whitelist().addAttributes("a", "href").addProtocols("a", "href", "http");
        wl.addProtocols("a", "href", "https");
        Element el = Jsoup.parse("<a href=\"https://example.com\"></a>", "https://example.com").body().child(0);
        Attribute attr = new Attribute("href", "https://example.com");
        assertTrue(wl.isSafeAttribute("a", el, attr));
    }

    // addProtocols(): null protocols array throws IllegalArgumentException
    @Test
    public void testAddProtocols_nullArray_throwsIllegalArgumentException() throws Throwable {
        try {
            new Whitelist().addProtocols("a", "href", (String[]) null);
            fail("expected IllegalArgumentException");
        } catch (IllegalArgumentException expected) { }
    }

    // addProtocols(): empty tag throws IllegalArgumentException
    @Test
    public void testAddProtocols_emptyTag_throwsIllegalArgumentException() throws Throwable {
        try {
            new Whitelist().addProtocols("", "href", "http");
            fail("expected IllegalArgumentException");
        } catch (IllegalArgumentException expected) { }
    }

    // addProtocols(): empty key throws IllegalArgumentException
    @Test
    public void testAddProtocols_emptyKey_throwsIllegalArgumentException() throws Throwable {
        try {
            new Whitelist().addProtocols("a", "", "http");
            fail("expected IllegalArgumentException");
        } catch (IllegalArgumentException expected) { }
    }

    // addProtocols(): empty protocol string throws IllegalArgumentException
    @Test
    public void testAddProtocols_emptyProtocol_throwsIllegalArgumentException() throws Throwable {
        try {
            new Whitelist().addProtocols("a", "href", "");
            fail("expected IllegalArgumentException");
        } catch (IllegalArgumentException expected) { }
    }

    // isSafeTag(): exact string match required (case-sensitive per TypedValue.equals)
    @Test
    public void testIsSafeTag_caseSensitiveMatch() throws Throwable {
        Whitelist wl = new Whitelist().addTags("p");
        assertTrue(wl.isSafeTag("p"));
        assertFalse(wl.isSafeTag("P"));
    }



    // isSafeAttribute(): ":all" pseudo-tag applies when tag is not configured at all
    @Test
    public void testIsSafeAttribute_allPseudoTag_appliesWhenTagAbsent() throws Throwable {
        Whitelist wl = new Whitelist().addAttributes(":all", "class");
        Attribute attr = new Attribute("class", "test");
        assertTrue(wl.isSafeAttribute("div", simpleElement, attr));
    }

    // isSafeAttribute(): attribute allowed and no protocol restriction configured -> safe
    @Test
    public void testIsSafeAttribute_noProtocolsDefined_isSafe() throws Throwable {
        Whitelist wl = new Whitelist().addAttributes("p", "class");
        Attribute attr = new Attribute("class", "test");
        assertTrue(wl.isSafeAttribute("p", simpleElement, attr));
    }

    // isSafeAttribute(): tag configured but attribute key not part of its set (and no :all) -> unsafe
    @Test
    public void testIsSafeAttribute_attributeNotInTagSet_isUnsafe() throws Throwable {
        Whitelist wl = new Whitelist().addAttributes("p", "id");
        Attribute attr = new Attribute("class", "test");
        assertFalse(wl.isSafeAttribute("p", simpleElement, attr));
    }

    // isSafeAttribute(): tag absent and no ":all" configured -> unsafe
    @Test
    public void testIsSafeAttribute_tagAbsentNoAllTag_isUnsafe() throws Throwable {
        Whitelist wl = new Whitelist().addAttributes("div", "class");
        Attribute attr = new Attribute("class", "test");
        assertFalse(wl.isSafeAttribute("p", simpleElement, attr));
    }

    // testValidProtocol (via isSafeAttribute): preserveRelativeLinks=false resolves attr value to absolute URL
    @Test
    public void testIsSafeAttribute_preserveRelativeLinksFalse_updatesAttrToAbsolute() throws Throwable {
        Whitelist wl = new Whitelist().addAttributes("a", "href").addProtocols("a", "href", "http");
        Document doc = Jsoup.parse("<a href=\"page.html\"></a>", "http://example.com/");
        Element el = doc.body().child(0);
        Attribute attr = new Attribute("href", "page.html");
        boolean result = wl.isSafeAttribute("a", el, attr);
        assertTrue(result);
        assertEquals("http://example.com/page.html", attr.getValue());
    }

    // testValidProtocol (via isSafeAttribute): preserveRelativeLinks=true keeps original attr value
    @Test
    public void testIsSafeAttribute_preserveRelativeLinksTrue_keepsOriginalValue() throws Throwable {
        Whitelist wl = new Whitelist().addAttributes("a", "href").addProtocols("a", "href", "http");
        wl.preserveRelativeLinks(true);
        Document doc = Jsoup.parse("<a href=\"page.html\"></a>", "http://example.com/");
        Element el = doc.body().child(0);
        Attribute attr = new Attribute("href", "page.html");
        boolean result = wl.isSafeAttribute("a", el, attr);
        assertTrue(result);
        assertEquals("page.html", attr.getValue());
    }

    // testValidProtocol (via isSafeAttribute): resolved protocol not in allowed set -> unsafe
    @Test
    public void testIsSafeAttribute_protocolMismatch_isUnsafe() throws Throwable {
        Whitelist wl = new Whitelist().addAttributes("a", "href").addProtocols("a", "href", "https");
        Document doc = Jsoup.parse("<a href=\"http://example.com\"></a>", "http://example.com");
        Element el = doc.body().child(0);
        Attribute attr = new Attribute("href", "http://example.com");
        assertFalse(wl.isSafeAttribute("a", el, attr));
    }

    // getEnforcedAttributes(): tag with no enforced attributes returns empty result for a key
    @Test
    public void testGetEnforcedAttributes_tagNotConfigured_returnsEmptyValue() throws Throwable {
        Whitelist wl = new Whitelist();
        Attributes attrs = wl.getEnforcedAttributes("a");
        assertEquals("", attrs.get("rel"));
    }

    // getEnforcedAttributes(): multiple enforced attributes for same tag are all present
    @Test
    public void testGetEnforcedAttributes_multipleAttributes_allPresent() throws Throwable {
        Whitelist wl = new Whitelist()
                .addEnforcedAttribute("a", "rel", "nofollow")
                .addEnforcedAttribute("a", "target", "_blank");
        Attributes attrs = wl.getEnforcedAttributes("a");
        assertEquals("nofollow", attrs.get("rel"));
        assertEquals("_blank", attrs.get("target"));
    }
}
