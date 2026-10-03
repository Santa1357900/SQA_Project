package org.apache.commons.lang.text;

import org.apache.commons.lang.SystemUtils;
import org.junit.Test;
import static org.junit.Assert.*;

public class StrBuilderClaudeTest {

    // Default constructor must allocate the documented CAPACITY
    @Test
    public void testDefaultConstructor_hasCapacity32() throws Throwable {
        StrBuilder sb = new StrBuilder();
        assertEquals(StrBuilder.CAPACITY, sb.capacity());
        assertEquals(0, sb.length());
    }

    // initialCapacity <= 0 branch defaults to CAPACITY
    @Test
    public void testIntConstructor_nonPositiveDefaultsToCapacity32() throws Throwable {
        StrBuilder sb1 = new StrBuilder(0);
        StrBuilder sb2 = new StrBuilder(-5);
        assertEquals(StrBuilder.CAPACITY, sb1.capacity());
        assertEquals(StrBuilder.CAPACITY, sb2.capacity());
    }

    // initialCapacity > 0 branch uses given value
    @Test
    public void testIntConstructor_positiveUsesGivenCapacity() throws Throwable {
        StrBuilder sb = new StrBuilder(50);
        assertEquals(50, sb.capacity());
    }

    // String constructor with null str branch
    @Test
    public void testStringConstructor_nullProducesEmptyBuilder() throws Throwable {
        StrBuilder sb = new StrBuilder((String) null);
        assertEquals(0, sb.length());
        assertEquals("", sb.toString());
    }

    // String constructor with non-null str appends content
    @Test
    public void testStringConstructor_nonNullAppendsContent() throws Throwable {
        StrBuilder sb = new StrBuilder("abc");
        assertEquals("abc", sb.toString());
        assertEquals(3 + StrBuilder.CAPACITY, sb.capacity());
    }

    // newLine default (null) uses system separator; custom value is used when set
    @Test
    public void testNewLineText_defaultAndCustomValue() throws Throwable {
        StrBuilder sb = new StrBuilder();
        assertNull(sb.getNewLineText());
        sb.appendNewLine();
        assertEquals(SystemUtils.LINE_SEPARATOR, sb.toString());

        StrBuilder sb2 = new StrBuilder();
        sb2.setNewLineText("<br/>");
        assertEquals("<br/>", sb2.getNewLineText());
        sb2.appendNewLine();
        assertEquals("<br/>", sb2.toString());
    }

    // setNullText("") must normalize to null per contract
    @Test
    public void testNullText_emptyStringNormalizedToNull() throws Throwable {
        StrBuilder sb = new StrBuilder();
        sb.setNullText("");
        assertNull(sb.getNullText());
        sb.setNullText("NULL");
        assertEquals("NULL", sb.getNullText());
    }

    // appendNull branch: nullText configured is appended
    @Test
    public void testAppendNull_withNullTextConfigured() throws Throwable {
        StrBuilder sb = new StrBuilder();
        sb.setNullText("NULL");
        sb.appendNull();
        assertEquals("NULL", sb.toString());
    }

    // append(Object) with null delegates to appendNull(), both with and without nullText
    @Test
    public void testAppendObject_nullDelegatesToAppendNull() throws Throwable {
        StrBuilder sb = new StrBuilder();
        sb.append((Object) null);
        assertEquals("", sb.toString());
        sb.setNullText("N");
        sb.append((Object) null);
        assertEquals("N", sb.toString());
    }

    // setLength negative throws per contract
    @Test
    public void testSetLength_negativeThrows() throws Throwable {
        StrBuilder sb = new StrBuilder();
        try {
            sb.setLength(-1);
            fail("expected StringIndexOutOfBoundsException");
        } catch (StringIndexOutOfBoundsException expected) {
        }
    }

    // setLength increase branch fills new slots with unicode zero
    @Test
    public void testSetLength_increaseFillsWithNulChar() throws Throwable {
        StrBuilder sb = new StrBuilder();
        sb.append("ab");
        sb.setLength(4);
        assertEquals(4, sb.length());
        assertEquals('\0', sb.charAt(2));
        assertEquals('\0', sb.charAt(3));
    }

    // setLength decrease branch truncates content
    @Test
    public void testSetLength_decreaseTruncates() throws Throwable {
        StrBuilder sb = new StrBuilder();
        sb.append("abcde");
        sb.setLength(2);
        assertEquals(2, sb.length());
        assertEquals("ab", sb.toString());
    }

    // ensureCapacity only grows when requested capacity exceeds current
    @Test
    public void testEnsureCapacity_growsOnlyWhenNeeded() throws Throwable {
        StrBuilder sb = new StrBuilder(10);
        sb.ensureCapacity(5);
        assertEquals(10, sb.capacity());
        sb.ensureCapacity(100);
        assertEquals(100, sb.capacity());
    }

    // minimizeCapacity shrinks the buffer to exact size
    @Test
    public void testMinimizeCapacity_shrinksToSize() throws Throwable {
        StrBuilder sb = new StrBuilder(50);
        sb.append("ab");
        sb.minimizeCapacity();
        assertEquals(2, sb.capacity());
    }

    // size/isEmpty/clear collections-style API
    @Test
    public void testSizeIsEmptyClear() throws Throwable {
        StrBuilder sb = new StrBuilder();
        assertTrue(sb.isEmpty());
        sb.append("x");
        assertFalse(sb.isEmpty());
        assertEquals(1, sb.size());
        sb.clear();
        assertTrue(sb.isEmpty());
        assertEquals(0, sb.size());
    }

    // charAt valid index and invalid (>= length) throwing branch
    @Test
    public void testCharAt_validAndInvalidIndex() throws Throwable {
        StrBuilder sb = new StrBuilder();
        sb.append("abc");
        assertEquals('a', sb.charAt(0));
        assertEquals('c', sb.charAt(2));
        try {
            sb.charAt(3);
            fail("expected StringIndexOutOfBoundsException");
        } catch (StringIndexOutOfBoundsException expected) {
        }
    }

    // setCharAt valid write and invalid negative index throws
    @Test
    public void testSetCharAt_invalidIndexThrows() throws Throwable {
        StrBuilder sb = new StrBuilder();
        sb.append("abc");
        sb.setCharAt(1, 'Z');
        assertEquals("aZc", sb.toString());
        try {
            sb.setCharAt(-1, 'Y');
            fail("expected StringIndexOutOfBoundsException");
        } catch (StringIndexOutOfBoundsException expected) {
        }
    }

    // deleteCharAt removes exactly one character
    @Test
    public void testDeleteCharAt_removesCharacter() throws Throwable {
        StrBuilder sb = new StrBuilder();
        sb.append("abc");
        sb.deleteCharAt(1);
        assertEquals("ac", sb.toString());
    }

    // toCharArray: empty builder returns empty array; invalid range throws
    @Test
    public void testToCharArray_emptyAndRangeInvalid() throws Throwable {
        StrBuilder sb = new StrBuilder();
        char[] empty = sb.toCharArray();
        assertEquals(0, empty.length);
        sb.append("abcdef");
        char[] part = sb.toCharArray(1, 4);
        assertEquals(3, part.length);
        assertEquals('b', part[0]);
        try {
            sb.toCharArray(-1, 3);
            fail("expected StringIndexOutOfBoundsException");
        } catch (StringIndexOutOfBoundsException expected) {
        }
    }

    // getChars(4-arg): valid copy, plus negative start and end<start throwing branches
    @Test
    public void testGetChars_fourArg_invalidIndicesThrow() throws Throwable {
        StrBuilder sb = new StrBuilder();
        sb.append("abcdef");
        char[] dest = new char[10];
        sb.getChars(1, 4, dest, 0);
        assertEquals('b', dest[0]);
        try {
            sb.getChars(-1, 3, dest, 0);
            fail("expected StringIndexOutOfBoundsException");
        } catch (StringIndexOutOfBoundsException expected) {
        }
        try {
            sb.getChars(3, 1, dest, 0);
            fail("expected StringIndexOutOfBoundsException");
        } catch (StringIndexOutOfBoundsException expected) {
        }
    }

    // append(String,start,length) valid part, and invalid length throws
    @Test
    public void testAppendStringPart_invalidLengthThrows() throws Throwable {
        StrBuilder sb = new StrBuilder();
        sb.append("abcdef", 1, 3);
        assertEquals("bcd", sb.toString());
        try {
            sb.append("abcdef", 1, 10);
            fail("expected StringIndexOutOfBoundsException");
        } catch (StringIndexOutOfBoundsException expected) {
        }
    }

    // append(char[], start, length) partial append branch
    @Test
    public void testAppendCharArrayPart() throws Throwable {
        StrBuilder sb = new StrBuilder();
        char[] chars = new char[] {'a', 'b', 'c', 'd'};
        sb.append(chars, 1, 2);
        assertEquals("bc", sb.toString());
    }

    // append(boolean) true and false branches
    @Test
    public void testAppendBoolean_trueAndFalse() throws Throwable {
        StrBuilder sb = new StrBuilder();
        sb.append(true);
        sb.append(false);
        assertEquals("truefalse", sb.toString());
    }

    // appendWithSeparators(Object[],String): null separator treated as empty string
    @Test
    public void testAppendWithSeparatorsArray_nullSeparatorTreatedAsEmpty() throws Throwable {
        StrBuilder sb = new StrBuilder();
        Object[] arr = new Object[] {"a", "b", "c"};
        sb.appendWithSeparators(arr, null);
        assertEquals("abc", sb.toString());
    }

    // appendSeparator(String): only appended when builder is currently non-empty
    @Test
    public void testAppendSeparatorString_onlyWhenNonEmpty() throws Throwable {
        StrBuilder sb = new StrBuilder();
        sb.appendSeparator(",");
        assertEquals("", sb.toString());
        sb.append("a");
        sb.appendSeparator(",");
        sb.append("b");
        assertEquals("a,b", sb.toString());
    }

    // appendSeparator(char,loopIndex): only appended when loopIndex > 0
    @Test
    public void testAppendSeparatorChar_loopIndexBased() throws Throwable {
        StrBuilder sb = new StrBuilder();
        sb.appendSeparator(',', 0);
        sb.append("a");
        sb.appendSeparator(',', 1);
        sb.append("b");
        assertEquals("a,b", sb.toString());
    }

    // appendPadding: negative length no-op, positive length appends pad chars
    @Test
    public void testAppendPadding_negativeNoOpPositiveAppends() throws Throwable {
        StrBuilder sb = new StrBuilder();
        sb.appendPadding(-1, '*');
        assertEquals(0, sb.length());
        sb.appendPadding(3, '*');
        assertEquals("***", sb.toString());
    }

    // appendFixedWidthPadLeft: value longer than width keeps rightmost chars; shorter is left-padded
    @Test
    public void testAppendFixedWidthPadLeft_truncatesFromLeft() throws Throwable {
        StrBuilder sb = new StrBuilder();
        sb.appendFixedWidthPadLeft("HelloWorld", 5, '*');
        assertEquals("World", sb.toString());
        StrBuilder sb2 = new StrBuilder();
        sb2.appendFixedWidthPadLeft("Hi", 5, '*');
        assertEquals("***Hi", sb2.toString());
    }

    // appendFixedWidthPadRight: value longer than width keeps leftmost chars; shorter is right-padded
    @Test
    public void testAppendFixedWidthPadRight_truncatesFromRight() throws Throwable {
        StrBuilder sb = new StrBuilder();
        sb.appendFixedWidthPadRight("HelloWorld", 5, '*');
        assertEquals("Hello", sb.toString());
        StrBuilder sb2 = new StrBuilder();
        sb2.appendFixedWidthPadRight("Hi", 5, '*');
        assertEquals("Hi***", sb2.toString());
    }

    // insert(index,String) valid insert, and invalid negative index throws
    @Test
    public void testInsertString_invalidIndexThrows() throws Throwable {
        StrBuilder sb = new StrBuilder();
        sb.append("abc");
        sb.insert(1, "XY");
        assertEquals("aXYbc", sb.toString());
        try {
            sb.insert(-1, "Z");
            fail("expected StringIndexOutOfBoundsException");
        } catch (StringIndexOutOfBoundsException expected) {
        }
    }

    // delete(start,end) removes the specified range
    @Test
    public void testDeleteRange_removesSubstring() throws Throwable {
        StrBuilder sb = new StrBuilder();
        sb.append("abcdef");
        sb.delete(1, 3);
        assertEquals("adef", sb.toString());
    }

    // deleteAll(char) removes every occurrence, including consecutive runs
    @Test
    public void testDeleteAllChar_removesAllOccurrences() throws Throwable {
        StrBuilder sb = new StrBuilder();
        sb.append("abcabc");
        sb.deleteAll('a');
        assertEquals("bcbc", sb.toString());
    }

    // deleteFirst(String) removes only the first match, not later ones
    @Test
    public void testDeleteFirstString_removesOnlyFirstMatch() throws Throwable {
        StrBuilder sb = new StrBuilder();
        sb.append("abcabc");
        sb.deleteFirst("abc");
        assertEquals("abc", sb.toString());
    }

    // replace(start,end,str) with a replacement longer than the removed range
    @Test
    public void testReplaceRange_replacesWithLongerString() throws Throwable {
        StrBuilder sb = new StrBuilder();
        sb.append("abcdef");
        sb.replace(1, 3, "XYZ");
        assertEquals("aXYZdef", sb.toString());
    }

    // reverse() on odd-length content swaps characters around the middle pivot
    @Test
    public void testReverse_oddLength() throws Throwable {
        StrBuilder sb = new StrBuilder();
        sb.append("abcde");
        sb.reverse();
        assertEquals("edcba", sb.toString());
    }

    // trim() removes leading and trailing whitespace (chars <= ' ')
    @Test
    public void testTrim_removesLeadingAndTrailingWhitespace() throws Throwable {
        StrBuilder sb = new StrBuilder();
        sb.append("  abc  ");
        sb.trim();
        assertEquals("abc", sb.toString());
    }

    // startsWith/endsWith true, false and null-safe branches
    @Test
    public void testStartsWithEndsWith() throws Throwable {
        StrBuilder sb = new StrBuilder();
        sb.append("abcdef");
        assertTrue(sb.startsWith("abc"));
        assertFalse(sb.startsWith("xyz"));
        assertFalse(sb.startsWith(null));
        assertTrue(sb.endsWith("def"));
        assertFalse(sb.endsWith(null));
    }

    // substring(start,end) clamps an endIndex beyond length; substring(start) delegates
    @Test
    public void testSubstring_endIndexBeyondLengthClamped() throws Throwable {
        StrBuilder sb = new StrBuilder();
        sb.append("abcdef");
        assertEquals("cdef", sb.substring(2, 100));
        assertEquals("cdef", sb.substring(2));
    }

    // leftString/rightString/midString edge cases: negative/zero length and mid range
    @Test
    public void testLeftRightMidString() throws Throwable {
        StrBuilder sb = new StrBuilder();
        sb.append("abcdef");
        assertEquals("abc", sb.leftString(3));
        assertEquals("", sb.leftString(-1));
        assertEquals("def", sb.rightString(3));
        assertEquals("", sb.rightString(0));
        assertEquals("cd", sb.midString(2, 2));
        assertEquals("", sb.midString(2, -1));
    }

    // indexOf(String,startIndex): empty search string returns startIndex; normal and not-found cases
    @Test
    public void testIndexOfString_emptySearchReturnsStartIndex() throws Throwable {
        StrBuilder sb = new StrBuilder();
        sb.append("abcdef");
        assertEquals(2, sb.indexOf("", 2));
        assertEquals(2, sb.indexOf("cd"));
        assertEquals(-1, sb.indexOf("xyz"));
    }

    // lastIndexOf(String) finds the rightmost match; null string returns -1
    @Test
    public void testLastIndexOfString_basic() throws Throwable {
        StrBuilder sb = new StrBuilder();
        sb.append("abcabc");
        assertEquals(3, sb.lastIndexOf("abc"));
        assertEquals(-1, sb.lastIndexOf((String) null));
    }

    // equalsIgnoreCase: same letters different case are equal, different content is not
    @Test
    public void testEqualsIgnoreCase_trueForDifferentCase() throws Throwable {
        StrBuilder sb1 = new StrBuilder();
        sb1.append("ABC");
        StrBuilder sb2 = new StrBuilder();
        sb2.append("abc");
        assertTrue(sb1.equalsIgnoreCase(sb2));
        StrBuilder sb3 = new StrBuilder();
        sb3.append("abcd");
        assertFalse(sb1.equalsIgnoreCase(sb3));
    }

    // Javadoc contract: "@param other the object to check, null returns false" -
    // equals(StrBuilder) must return false for a null argument instead of throwing.
    @Test
    public void testEquals_withNullOtherBuilder_shouldReturnFalsePerContract() throws Throwable {
        StrBuilder sb = new StrBuilder();
        sb.append("abc");
        StrBuilder other = null;
        assertFalse(sb.equals(other));
    }

    // hashCode is consistent for builders with equal content, matching equals()
    @Test
    public void testHashCode_equalForEqualContent() throws Throwable {
        StrBuilder sb1 = new StrBuilder();
        sb1.append("abc");
        StrBuilder sb2 = new StrBuilder();
        sb2.append("abc");
        assertEquals(sb1.hashCode(), sb2.hashCode());
        assertTrue(sb1.equals(sb2));
    }

    // toString() and toStringBuffer() both reflect current content
    @Test
    public void testToString_and_ToStringBuffer() throws Throwable {
        StrBuilder sb = new StrBuilder();
        sb.append("abc");
        assertEquals("abc", sb.toString());
        StringBuffer buf = sb.toStringBuffer();
        assertEquals("abc", buf.toString());
    }
}
