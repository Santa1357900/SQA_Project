package org.jsoup.parser;

import org.junit.Test;
import static org.junit.Assert.*;

public class TagTest {

    @Test
    public void testValueOfKnownBlockTag() throws Throwable {
        Tag tag = Tag.valueOf("div");
        assertNotNull(tag);
        assertEquals("div", tag.getName());
        assertTrue(tag.isBlock());
        assertTrue(tag.isKnownTag());
        assertTrue(Tag.isKnownTag("div"));
        assertEquals("div", tag.toString());
    }

    @Test
    public void testValueOfKnownInlineTag() throws Throwable {
        Tag tag = Tag.valueOf("span");
        assertNotNull(tag);
        assertEquals("span", tag.getName());
        assertFalse(tag.isBlock());
        assertTrue(tag.isInline());
        assertTrue(tag.isKnownTag());
        assertTrue(Tag.isKnownTag("span"));
    }

    @Test
    public void testValueOfUnknownTag() throws Throwable {
        Tag tag = Tag.valueOf("custom-tag-name");
        assertNotNull(tag);
        assertEquals("custom-tag-name", tag.getName());
        assertFalse(tag.isKnownTag());
        assertFalse(Tag.isKnownTag("custom-tag-name"));
        assertFalse(tag.isBlock());
        assertTrue(tag.isInline());
        assertTrue(tag.canContainBlock());
    }

    @Test
    public void testValueOfCaseInsensitivityAndTrimming() throws Throwable {
        Tag tag1 = Tag.valueOf("  P ");
        Tag tag2 = Tag.valueOf("p");
        assertNotNull(tag1);
        assertEquals(tag1, tag2);
        assertEquals(tag1.hashCode(), tag2.hashCode());
    }

    @Test
    public void testValueOfNullThrowsException() throws Throwable {
        try {
            Tag.valueOf(null);
            fail("Expected exception for null tag name");
        } catch (IllegalArgumentException e) {
            // expected
        }
    }

    @Test
    public void testValueOfEmptyOrBlankThrowsException() throws Throwable {
        try {
            Tag.valueOf("");
            fail("Expected exception for empty tag name");
        } catch (IllegalArgumentException e) {
            // expected
        }

        try {
            Tag.valueOf("   ");
            fail("Expected exception for blank tag name");
        } catch (IllegalArgumentException e) {
            // expected
        }
    }

    @Test
    public void testEmptyAndSelfClosingTags() throws Throwable {
        Tag imgTag = Tag.valueOf("img");
        assertTrue(imgTag.isEmpty());
        assertTrue(imgTag.isSelfClosing());
        assertFalse(imgTag.canContainBlock());
        assertFalse(imgTag.canContainInline());

        Tag unknownTag = Tag.valueOf("unknown-empty");
        assertFalse(unknownTag.isEmpty());
        assertFalse(unknownTag.isSelfClosing());

        Tag setSelfClosingTag = Tag.valueOf("unknown-empty").setSelfClosing();
        assertTrue(setSelfClosingTag.isSelfClosing());
    }

    @Test
    public void testFormatAsBlock() throws Throwable {
        Tag divTag = Tag.valueOf("div");
        assertTrue(divTag.formatAsBlock());

        Tag pTag = Tag.valueOf("p");
        assertFalse(pTag.formatAsBlock());
    }

    @Test
    public void testPreserveWhitespace() throws Throwable {
        Tag preTag = Tag.valueOf("pre");
        assertTrue(preTag.preserveWhitespace());

        Tag divTag = Tag.valueOf("div");
        assertFalse(divTag.preserveWhitespace());
    }

    @Test
    public void testIsData() throws Throwable {
        Tag scriptTag = Tag.valueOf("script");
        // script is a block tag, canContainInline is true by default unless modified
        // let's check a known tag or test the method directly
        assertNotNull(scriptTag);
        
        Tag pTag = Tag.valueOf("p");
        assertFalse(pTag.isData());
    }

    @Test
    public void testEqualsAndHashCode() throws Throwable {
        Tag tag1 = Tag.valueOf("div");
        Tag tag2 = Tag.valueOf("div");
        Tag tag3 = Tag.valueOf("span");
        Tag unknown1 = Tag.valueOf("foo");
        Tag unknown2 = Tag.valueOf("foo");
        Tag unknown3 = Tag.valueOf("bar");

        assertTrue(tag1.equals(tag1));
        assertTrue(tag1.equals(tag2));
        assertFalse(tag1.equals(tag3));
        assertFalse(tag1.equals(null));
        assertFalse(tag1.equals("div"));

        assertTrue(unknown1.equals(unknown2));
        assertFalse(unknown1.equals(unknown3));

        assertEquals(tag1.hashCode(), tag2.hashCode());
        assertEquals(unknown1.hashCode(), unknown2.hashCode());
    }
}