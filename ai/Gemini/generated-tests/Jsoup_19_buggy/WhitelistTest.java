package org.jsoup.safety;

import org.junit.Test;
import static org.junit.Assert.*;

import org.jsoup.nodes.Attribute;
import org.jsoup.nodes.Attributes;
import org.jsoup.nodes.Element;
import org.jsoup.parser.Tag;

public class WhitelistTest {

    @Test
    public void testNoneWhitelist() throws Throwable {
        Whitelist whitelist = Whitelist.none();
        assertFalse(whitelist.isSafeTag("p"));
        assertFalse(whitelist.isSafeTag("b"));
    }

    @Test
    public void testSimpleTextWhitelist() throws Throwable {
        Whitelist whitelist = Whitelist.simpleText();
        assertTrue(whitelist.isSafeTag("b"));
        assertTrue(whitelist.isSafeTag("em"));
        assertTrue(whitelist.isSafeTag("i"));
        assertTrue(whitelist.isSafeTag("strong"));
        assertTrue(whitelist.isSafeTag("u"));
        assertFalse(whitelist.isSafeTag("a"));
        assertFalse(whitelist.isSafeTag("p"));
    }

    @Test
    public void testBasicWhitelist() throws Throwable {
        Whitelist whitelist = Whitelist.basic();
        assertTrue(whitelist.isSafeTag("a"));
        assertTrue(whitelist.isSafeTag("p"));
        assertTrue(whitelist.isSafeTag("blockquote"));
        
        Element el = new Element(Tag.valueOf("a"), "http://example.com");
        Attribute attr = new Attribute("href", "http://example.com/path");
        assertTrue(whitelist.isSafeAttribute("a", el, attr));

        Attributes enforced = whitelist.getEnforcedAttributes("a");
        assertEquals("nofollow", enforced.get("rel"));
    }

    @Test
    public void testBasicWithImagesWhitelist() throws Throwable {
        Whitelist whitelist = Whitelist.basicWithImages();
        assertTrue(whitelist.isSafeTag("img"));
        
        Element el = new Element(Tag.valueOf("img"), "http://example.com");
        Attribute attr = new Attribute("src", "https://example.com/image.png");
        assertTrue(whitelist.isSafeAttribute("img", el, attr));
    }

    @Test
    public void testRelaxedWhitelist() throws Throwable {
        Whitelist whitelist = Whitelist.relaxed();
        assertTrue(whitelist.isSafeTag("table"));
        assertTrue(whitelist.isSafeTag("tr"));
        assertTrue(whitelist.isSafeTag("td"));
        assertTrue(whitelist.isSafeTag("h1"));
        
        Element el = new Element(Tag.valueOf("a"), "http://example.com");
        Attribute attr = new Attribute("title", "Example Title");
        assertTrue(whitelist.isSafeAttribute("a", el, attr));
    }

    @Test
    public void testAddTagsAndCustomRules() throws Throwable {
        Whitelist whitelist = new Whitelist();
        whitelist.addTags("customtag");
        assertTrue(whitelist.isSafeTag("customtag"));

        whitelist.addAttributes("customtag", "data-id", "class");
        whitelist.addEnforcedAttribute("customtag", "data-enforced", "true");
        whitelist.preserveRelativeLinks(true);

        Element el = new Element(Tag.valueOf("customtag"), "http://example.com");
        Attribute attr1 = new Attribute("data-id", "123");
        assertTrue(whitelist.isSafeAttribute("customtag", el, attr1));

        Attributes enforced = whitelist.getEnforcedAttributes("customtag");
        assertEquals("true", enforced.get("data-enforced"));
    }

    @Test
    public void testAddProtocols() throws Throwable {
        Whitelist whitelist = new Whitelist();
        whitelist.addTags("a");
        whitelist.addAttributes("a", "href");
        whitelist.addProtocols("a", "href", "http", "https");

        Element el = new Element(Tag.valueOf("a"), "http://example.com");
        Attribute attrValid = new Attribute("href", "http://example.com");
        Attribute attrInvalid = new Attribute("href", "javascript:alert(1)");

        assertTrue(whitelist.isSafeAttribute("a", el, attrValid));
        assertFalse(whitelist.isSafeAttribute("a", el, attrInvalid));
    }

    @Test
    public void testAllTagAttributesFallback() throws Throwable {
        Whitelist whitelist = new Whitelist();
        whitelist.addTags("p");
        whitelist.addAttributes(":all", "class");

        Element el = new Element(Tag.valueOf("p"), "http://example.com");
        Attribute attr = new Attribute("class", "my-class");
        assertTrue(whitelist.isSafeAttribute("p", el, attr));
    }

    @Test
    public void testUnsafeTagAndAttribute() throws Throwable {
        Whitelist whitelist = Whitelist.basic();
        assertFalse(whitelist.isSafeTag("script"));

        Element el = new Element(Tag.valueOf("a"), "http://example.com");
        Attribute attr = new Attribute("onclick", "alert(1)");
        assertFalse(whitelist.isSafeAttribute("a", el, attr));
    }

    @Test
    public void testTypedValueEqualsAndHashCode() throws Throwable {
        Whitelist.TagName tag1 = Whitelist.TagName.valueOf("p");
        Whitelist.TagName tag2 = Whitelist.TagName.valueOf("p");
        Whitelist.TagName tag3 = Whitelist.TagName.valueOf("div");

        assertTrue(tag1.equals(tag2));
        assertFalse(tag1.equals(tag3));
        assertFalse(tag1.equals(null));
        assertFalse(tag1.equals("p"));
        assertEquals(tag1.hashCode(), tag2.hashCode());
        assertEquals("p", tag1.toString());
    }

    @Test(expected = IllegalArgumentException.class)
    public void testAddTagsNullValidation() throws Throwable {
        Whitelist whitelist = new Whitelist();
        whitelist.addTags((String[]) null);
    }

    @Test(expected = IllegalArgumentException.class)
    public void testAddAttributesNullTagValidation() throws Throwable {
        Whitelist whitelist = new Whitelist();
        whitelist.addAttributes(null, "href");
    }

    @Test(expected = IllegalArgumentException.class)
    public void testAddEnforcedAttributeEmptyValue() throws Throwable {
        Whitelist whitelist = new Whitelist();
        whitelist.addEnforcedAttribute("a", "rel", "");
    }
}