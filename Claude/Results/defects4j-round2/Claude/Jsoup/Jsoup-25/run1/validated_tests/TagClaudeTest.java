package org.jsoup.parser;

import org.junit.Test;
import static org.junit.Assert.*;

public class TagClaudeTest {

    // valueOf: known tag "div" should be a block tag
    @Test
    public void testValueOf_knownBlockTag_isBlockTrue() throws Throwable {
        Tag div = Tag.valueOf("div");
        assertTrue(div.isBlock());
    }

    // valueOf: same known tag name (case-insensitive) returns identical (==) instance
    @Test
    public void testValueOf_caseInsensitiveKnownTag_sameInstance() throws Throwable {
        Tag lower = Tag.valueOf("div");
        Tag upper = Tag.valueOf("DIV");
        assertSame(lower, upper);
    }

    // valueOf: trims surrounding whitespace before lookup
    @Test
    public void testValueOf_trimsWhitespace_returnsTrimmedName() throws Throwable {
        Tag p = Tag.valueOf(" p ");
        assertEquals("p", p.getName());
    }

    // valueOf: unknown tag creates generic tag that is not block and can contain block
    @Test
    public void testValueOf_unknownTag_createsGenericInlineContainerTag() throws Throwable {
        Tag custom = Tag.valueOf("customtagxyz");
        assertFalse(custom.isBlock());
        assertTrue(custom.canContainBlock());
        assertFalse(custom.isKnownTag());
    }

    // valueOf: null tagName throws exception
    @Test
    public void testValueOf_null_throwsException() throws Throwable {
        try {
            Tag.valueOf(null);
            fail("expected exception for null tagName");
        } catch (IllegalArgumentException expected) {
        }
    }

    // valueOf: empty string throws exception
    @Test
    public void testValueOf_emptyString_throwsException() throws Throwable {
        try {
            Tag.valueOf("");
            fail("expected exception for empty tagName");
        } catch (IllegalArgumentException expected) {
        }
    }

    // valueOf: whitespace-only string becomes empty after trim, throws exception
    @Test
    public void testValueOf_whitespaceOnly_throwsExceptionAfterTrim() throws Throwable {
        try {
            Tag.valueOf("   ");
            fail("expected exception for whitespace-only tagName");
        } catch (IllegalArgumentException expected) {
        }
    }

    // valueOf: two calls for same unknown tag are equal but not the same instance (not registered)
    @Test
    public void testValueOf_unknownTagRepeated_equalButNotSameInstance() throws Throwable {
        Tag t1 = Tag.valueOf("mycustomtag");
        Tag t2 = Tag.valueOf("mycustomtag");
        assertNotSame(t1, t2);
        assertEquals(t1, t2);
    }

    // getName: returns lower-cased tag name regardless of input case
    @Test
    public void testGetName_returnsLowerCaseName() throws Throwable {
        Tag t = Tag.valueOf("SPAN");
        assertEquals("span", t.getName());
    }

    // isBlock: inline tag returns false
    @Test
    public void testIsBlock_inlineTag_false() throws Throwable {
        Tag span = Tag.valueOf("span");
        assertFalse(span.isBlock());
    }

    // isBlock: block tag that is also empty (meta) still reports isBlock true
    @Test
    public void testIsBlock_blockAndEmptyTag_true() throws Throwable {
        Tag meta = Tag.valueOf("meta");
        assertTrue(meta.isBlock());
        assertTrue(meta.isEmpty());
    }

    // formatAsBlock: tag listed in formatAsInlineTags returns false
    @Test
    public void testFormatAsBlock_pTag_false() throws Throwable {
        Tag p = Tag.valueOf("p");
        assertFalse(p.formatAsBlock());
    }

    // formatAsBlock: plain block tag not in formatAsInlineTags returns true
    @Test
    public void testFormatAsBlock_divTag_true() throws Throwable {
        Tag div = Tag.valueOf("div");
        assertTrue(div.formatAsBlock());
    }

    // canContainBlock: inline tag cannot contain block tags
    @Test
    public void testCanContainBlock_inlineTag_false() throws Throwable {
        Tag span = Tag.valueOf("span");
        assertFalse(span.canContainBlock());
    }

    // canContainBlock: plain block tag can contain block tags
    @Test
    public void testCanContainBlock_divTag_true() throws Throwable {
        Tag div = Tag.valueOf("div");
        assertTrue(div.canContainBlock());
    }

    // isInline: negation of isBlock for a block tag
    @Test
    public void testIsInline_blockTag_false() throws Throwable {
        Tag div = Tag.valueOf("div");
        assertFalse(div.isInline());
    }

    // isInline: negation of isBlock for an inline tag
    @Test
    public void testIsInline_inlineTag_true() throws Throwable {
        Tag span = Tag.valueOf("span");
        assertTrue(span.isInline());
    }

    // isData: known tag never satisfies both !canContainInline and !isEmpty simultaneously
    @Test
    public void testIsData_divTag_false() throws Throwable {
        Tag div = Tag.valueOf("div");
        assertFalse(div.isData());
    }

    // isEmpty: img is a defined empty tag
    @Test
    public void testIsEmpty_imgTag_true() throws Throwable {
        Tag img = Tag.valueOf("img");
        assertTrue(img.isEmpty());
    }

    // isEmpty: div is not an empty tag
    @Test
    public void testIsEmpty_divTag_false() throws Throwable {
        Tag div = Tag.valueOf("div");
        assertFalse(div.isEmpty());
    }

    // isSelfClosing: empty tag reports self closing true via empty flag
    @Test
    public void testIsSelfClosing_emptyTag_true() throws Throwable {
        Tag br = Tag.valueOf("br");
        assertTrue(br.isSelfClosing());
    }

    // isSelfClosing: non-empty, non-self-closing tag reports false
    @Test
    public void testIsSelfClosing_regularBlockTag_false() throws Throwable {
        Tag div = Tag.valueOf("div");
        assertFalse(div.isSelfClosing());
    }

    // isKnownTag (instance): known predefined tag returns true
    @Test
    public void testIsKnownTag_instance_knownTag_true() throws Throwable {
        Tag div = Tag.valueOf("div");
        assertTrue(div.isKnownTag());
    }

    // isKnownTag (instance): unknown tag returns false
    @Test
    public void testIsKnownTag_instance_unknownTag_false() throws Throwable {
        Tag custom = Tag.valueOf("notarealtagname");
        assertFalse(custom.isKnownTag());
    }

    // isKnownTag (static): known lower-case tag name returns true
    @Test
    public void testIsKnownTag_static_knownLowerCaseName_true() throws Throwable {
        assertTrue(Tag.isKnownTag("div"));
    }

    // isKnownTag (static): does not normalize case, so upper-case known tag returns false
    @Test
    public void testIsKnownTag_static_upperCaseName_false() throws Throwable {
        assertFalse(Tag.isKnownTag("DIV"));
    }

    // isKnownTag (static): unknown tag name returns false
    @Test
    public void testIsKnownTag_static_unknownTag_false() throws Throwable {
        assertFalse(Tag.isKnownTag("notarealtagname"));
    }

    // preserveWhitespace: "pre" is explicitly listed and returns true
    @Test
    public void testPreserveWhitespace_preTag_true() throws Throwable {
        Tag pre = Tag.valueOf("pre");
        assertTrue(pre.preserveWhitespace());
    }

    // preserveWhitespace: field javadoc/comment states textarea should preserve whitespace like pre/script
    @Test
    public void testPreserveWhitespace_textareaTag_true() throws Throwable {
        Tag textarea = Tag.valueOf("textarea");
        assertTrue(textarea.preserveWhitespace());
    }

    // preserveWhitespace: regular block tag does not preserve whitespace
    @Test
    public void testPreserveWhitespace_divTag_false() throws Throwable {
        Tag div = Tag.valueOf("div");
        assertFalse(div.preserveWhitespace());
    }

    // equals: same instance compares equal (identity branch)
    @Test
    public void testEquals_sameInstance_true() throws Throwable {
        Tag div = Tag.valueOf("div");
        assertTrue(div.equals(div));
    }

    // equals: comparing against null returns false
    @Test
    public void testEquals_null_false() throws Throwable {
        Tag div = Tag.valueOf("div");
        assertFalse(div.equals(null));
    }

    // equals: comparing against a different type returns false
    @Test
    public void testEquals_differentType_false() throws Throwable {
        Tag div = Tag.valueOf("div");
        assertFalse(div.equals("div"));
    }

    // equals: tags with different tag names are not equal
    @Test
    public void testEquals_differentTagName_false() throws Throwable {
        Tag div = Tag.valueOf("div");
        Tag span = Tag.valueOf("span");
        assertFalse(div.equals(span));
    }

    // equals: two unknown tags created separately with same name are equal by field values
    @Test
    public void testEquals_unknownTagsSameName_true() throws Throwable {
        Tag t1 = Tag.valueOf("customequaltag");
        Tag t2 = Tag.valueOf("customequaltag");
        assertTrue(t1.equals(t2));
    }

    // hashCode: two logically equal unknown tags produce the same hash code
    @Test
    public void testHashCode_equalUnknownTags_sameHashCode() throws Throwable {
        Tag t1 = Tag.valueOf("hashcustomtag");
        Tag t2 = Tag.valueOf("hashcustomtag");
        assertEquals(t1.hashCode(), t2.hashCode());
    }

    // hashCode: differing tag names produce different hash codes (not guaranteed universally, but true for these values)
    @Test
    public void testHashCode_differentTagNames_differentHashCode() throws Throwable {
        Tag div = Tag.valueOf("div");
        Tag span = Tag.valueOf("span");
        assertFalse(div.hashCode() == span.hashCode());
    }

    // toString: returns the tag name
    @Test
    public void testToString_returnsTagName() throws Throwable {
        Tag div = Tag.valueOf("div");
        assertEquals("div", div.toString());
    }
}
