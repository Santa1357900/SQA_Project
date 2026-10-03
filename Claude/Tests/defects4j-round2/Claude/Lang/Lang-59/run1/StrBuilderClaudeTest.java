package org.apache.commons.lang.text;

import static org.junit.Assert.*;

import java.util.ArrayList;
import java.util.List;

import org.junit.Test;

import org.apache.commons.lang.SystemUtils;

public class StrBuilderClaudeTest {

    // Covers: default ctor capacity=32; ctor(int<=0) defaults to 32; ctor(String null) -> empty buffer
    @Test
    public void testConstructors_defaultCapacityNonPositiveAndStringNull() throws Throwable {
        StrBuilder sb1 = new StrBuilder();
        assertEquals(32, sb1.capacity());
        assertEquals(0, sb1.length());
        StrBuilder sb2 = new StrBuilder(0);
        assertEquals(32, sb2.capacity());
        StrBuilder sb3 = new StrBuilder((String) null);
        assertEquals(0, sb3.length());
    }

    // Covers: ctor(String) non-null copies content and reserves extra capacity
    @Test
    public void testStringConstructor_nonNull_copiesContent() throws Throwable {
        StrBuilder sb = new StrBuilder("abc");
        assertEquals("abc", sb.toString());
        assertEquals(3, sb.length());
        assertTrue(sb.capacity() >= 3);
    }

    // Covers: setNewLineText/getNewLineText chaining and null default
    @Test
    public void testNewLineText_setAndGet() throws Throwable {
        StrBuilder sb = new StrBuilder();
        assertNull(sb.getNewLineText());
        StrBuilder result = sb.setNewLineText("\n");
        assertSame(sb, result);
        assertEquals("\n", sb.getNewLineText());
    }

    // Covers: setNullText("") converts to null; setNullText(normal) stores it
    @Test
    public void testNullText_emptyConvertsToNull_andNormalValue() throws Throwable {
        StrBuilder sb = new StrBuilder();
        sb.setNullText("");
        assertNull(sb.getNullText());
        sb.setNullText("NULL");
        assertEquals("NULL", sb.getNullText());
    }

    // Covers: setLength shrink (length<size) and grow (length>size) pads with '\0'
    @Test
    public void testSetLength_shrinkAndGrow() throws Throwable {
        StrBuilder sb = new StrBuilder("abcdef");
        sb.setLength(3);
        assertEquals("abc", sb.toString());
        sb.setLength(5);
        assertEquals(5, sb.length());
        assertEquals('\0', sb.charAt(4));
    }

    // Covers: setLength(negative) throws StringIndexOutOfBoundsException
    @Test
    public void testSetLength_negative_throws() throws Throwable {
        StrBuilder sb = new StrBuilder();
        try {
            sb.setLength(-1);
            fail("expected StringIndexOutOfBoundsException");
        } catch (StringIndexOutOfBoundsException expected) {
        }
    }

    // Covers: ensureCapacity grows buffer when needed; minimizeCapacity shrinks to length
    @Test
    public void testEnsureCapacityAndMinimizeCapacity() throws Throwable {
        StrBuilder sb = new StrBuilder(2);
        sb.ensureCapacity(100);
        assertTrue(sb.capacity() >= 100);
        sb.append("ab");
        sb.minimizeCapacity();
        assertEquals(2, sb.capacity());
    }

    // Covers: size()/isEmpty()/clear()
    @Test
    public void testSizeIsEmptyClear() throws Throwable {
        StrBuilder sb = new StrBuilder();
        assertTrue(sb.isEmpty());
        sb.append("x");
        assertEquals(1, sb.size());
        assertFalse(sb.isEmpty());
        sb.clear();
        assertEquals(0, sb.size());
        assertTrue(sb.isEmpty());
    }

    // Covers: charAt valid index and invalid index (>=length) throws
    @Test
    public void testCharAt_validAndInvalid_throws() throws Throwable {
        StrBuilder sb = new StrBuilder("abc");
        assertEquals('b', sb.charAt(1));
        try {
            sb.charAt(3);
            fail("expected StringIndexOutOfBoundsException");
        } catch (StringIndexOutOfBoundsException expected) {
        }
    }

    // Covers: setCharAt valid index updates char; invalid index throws
    @Test
    public void testSetCharAt_validAndInvalid_throws() throws Throwable {
        StrBuilder sb = new StrBuilder("abc");
        sb.setCharAt(0, 'z');
        assertEquals("zbc", sb.toString());
        try {
            sb.setCharAt(-1, 'y');
            fail("expected StringIndexOutOfBoundsException");
        } catch (StringIndexOutOfBoundsException expected) {
        }
    }

    // Covers: deleteCharAt valid index removes char; invalid index throws
    @Test
    public void testDeleteCharAt_validAndInvalid_throws() throws Throwable {
        StrBuilder sb = new StrBuilder("abc");
        sb.deleteCharAt(1);
        assertEquals("ac", sb.toString());
        try {
            sb.deleteCharAt(10);
            fail("expected StringIndexOutOfBoundsException");
        } catch (StringIndexOutOfBoundsException expected) {
        }
    }

    // Covers: toCharArray() empty+non-empty, toCharArray(start,end) range
    @Test
    public void testToCharArray_bothOverloads() throws Throwable {
        StrBuilder empty = new StrBuilder();
        assertEquals(0, empty.toCharArray().length);
        StrBuilder sb = new StrBuilder("abcdef");
        char[] all = sb.toCharArray();
        assertEquals(6, all.length);
        char[] part = sb.toCharArray(1, 3);
        assertEquals(2, part.length);
        assertEquals('b', part[0]);
        assertEquals('c', part[1]);
    }

    // Covers: getChars(null dest) creates array; getChars(range) throws on invalid startIndex
    @Test
    public void testGetChars_destinationAndRangeExceptions() throws Throwable {
        StrBuilder sb = new StrBuilder("abc");
        char[] dest = sb.getChars(null);
        assertEquals(3, dest.length);
        try {
            sb.getChars(-1, 2, new char[5], 0);
            fail("expected StringIndexOutOfBoundsException");
        } catch (StringIndexOutOfBoundsException expected) {
        }
    }

    // Covers: appendNewLine uses system default when newLine null; uses custom text otherwise
    @Test
    public void testAppendNewLine_defaultAndCustom() throws Throwable {
        StrBuilder sb = new StrBuilder();
        sb.appendNewLine();
        assertEquals(SystemUtils.LINE_SEPARATOR, sb.toString());
        StrBuilder sb2 = new StrBuilder();
        sb2.setNewLineText("|");
        sb2.appendNewLine();
        assertEquals("|", sb2.toString());
    }

    // Covers: appendNull no-op when nullText null; appends nullText when set
    @Test
    public void testAppendNull_withAndWithoutNullText() throws Throwable {
        StrBuilder sb = new StrBuilder();
        sb.appendNull();
        assertEquals("", sb.toString());
        sb.setNullText("NULL");
        sb.appendNull();
        assertEquals("NULL", sb.toString());
    }

    // Covers: append(Object) with null delegates to appendNull using nullText
    @Test
    public void testAppendObject_null_delegatesToAppendNull() throws Throwable {
        StrBuilder sb = new StrBuilder();
        sb.setNullText("N");
        sb.append((Object) null);
        assertEquals("N", sb.toString());
        sb.append(new Integer(5));
        assertEquals("N5", sb.toString());
    }

    // Covers: append(String) null delegates to appendNull; non-null appends content
    @Test
    public void testAppendString_nullAndNonNull() throws Throwable {
        StrBuilder sb = new StrBuilder();
        sb.append((String) null);
        assertEquals("", sb.toString());
        sb.append("hi");
        assertEquals("hi", sb.toString());
    }

    // Covers: append(String,start,len) invalid startIndex throws
    @Test
    public void testAppendStringRange_invalidStartIndex_throws() throws Throwable {
        StrBuilder sb = new StrBuilder();
        try {
            sb.append("abc", -1, 1);
            fail("expected StringIndexOutOfBoundsException");
        } catch (StringIndexOutOfBoundsException expected) {
        }
    }

    // Covers: append(String,start,len) invalid length throws; valid range appends substring
    @Test
    public void testAppendStringRange_invalidLength_throws() throws Throwable {
        StrBuilder sb = new StrBuilder();
        sb.append("abcdef", 1, 3);
        assertEquals("bcd", sb.toString());
        try {
            sb.append("abcdef", 1, 100);
            fail("expected StringIndexOutOfBoundsException");
        } catch (StringIndexOutOfBoundsException expected) {
        }
    }

    // Covers: append(char[]) null delegates to appendNull; non-null appends all chars
    @Test
    public void testAppendCharArray_null_delegatesToAppendNull() throws Throwable {
        StrBuilder sb = new StrBuilder();
        sb.append((char[]) null);
        assertEquals("", sb.toString());
        sb.append(new char[] {'x', 'y'});
        assertEquals("xy", sb.toString());
    }

    // Covers: append(boolean) true writes "true"; false writes "false"
    @Test
    public void testAppendBoolean_trueAndFalse() throws Throwable {
        StrBuilder sb = new StrBuilder();
        sb.append(true);
        assertEquals("true", sb.toString());
        sb.append(false);
        assertEquals("truefalse", sb.toString());
    }

    // Covers: append(int)/(long)/(float)/(double) use String.valueOf
    @Test
    public void testAppendNumericPrimitives() throws Throwable {
        StrBuilder sb = new StrBuilder();
        sb.append(42);
        sb.append(3L);
        sb.append(1.5f);
        sb.append(2.5d);
        String expected = String.valueOf(42) + String.valueOf(3L) + String.valueOf(1.5f) + String.valueOf(2.5d);
        assertEquals(expected, sb.toString());
    }

    // Covers: appendWithSeparators(Object[],sep) null array no-op, normal join; appendWithSeparators(Collection,sep)
    @Test
    public void testAppendWithSeparators_objectArrayAndCollection() throws Throwable {
        StrBuilder sb1 = new StrBuilder();
        sb1.appendWithSeparators((Object[]) null, ",");
        assertEquals("", sb1.toString());
        sb1.appendWithSeparators(new Object[] {"a", "b", "c"}, ",");
        assertEquals("a,b,c", sb1.toString());
        List list = new ArrayList();
        list.add("x");
        list.add("y");
        StrBuilder sb2 = new StrBuilder();
        sb2.appendWithSeparators(list, "-");
        assertEquals("x-y", sb2.toString());
    }

    // Covers: appendPadding negative => no effect; positive appends padChar repeated
    @Test
    public void testAppendPadding_negativeNoEffect_positiveAppends() throws Throwable {
        StrBuilder sb = new StrBuilder();
        sb.appendPadding(-1, '*');
        assertEquals(0, sb.length());
        sb.appendPadding(3, '*');
        assertEquals("***", sb.toString());
    }

    // Covers: appendFixedWidthPadLeft truncates left side when obj longer; pads left when shorter
    @Test
    public void testAppendFixedWidthPadLeft_largerAndSmaller() throws Throwable {
        StrBuilder sb1 = new StrBuilder();
        sb1.appendFixedWidthPadLeft("ABCDEF", 3, '-');
        assertEquals("DEF", sb1.toString());
        StrBuilder sb2 = new StrBuilder();
        sb2.appendFixedWidthPadLeft("AB", 5, '-');
        assertEquals("---AB", sb2.toString());
    }

    // Bug-hunting: Javadoc says right side is lost when obj longer than width; buggy impl copies full
    // string ignoring width, overflowing a tightly-sized buffer instead of truncating to width chars
    @Test
    public void testAppendFixedWidthPadRight_objectLongerThanWidth_truncatesRightSide() throws Throwable {
        StrBuilder sb = new StrBuilder(3);
        sb.appendFixedWidthPadRight("ABCDEF", 3, '-');
        assertEquals("ABC", sb.toString());
        assertEquals(3, sb.length());
    }

    // Covers: appendFixedWidthPadRight pads right when obj shorter than width
    @Test
    public void testAppendFixedWidthPadRight_smallerObject_padsRight() throws Throwable {
        StrBuilder sb = new StrBuilder();
        sb.appendFixedWidthPadRight("AB", 5, '-');
        assertEquals("AB---", sb.toString());
    }

    // Covers: insert(index,Object) null uses nullText; invalid index throws
    @Test
    public void testInsertObject_nullUsesNullText_andInvalidIndexThrows() throws Throwable {
        StrBuilder sb = new StrBuilder("ac");
        sb.setNullText("NULL");
        sb.insert(1, (Object) null);
        assertEquals("aNULLc", sb.toString());
        try {
            sb.insert(-1, 'z');
            fail("expected StringIndexOutOfBoundsException");
        } catch (StringIndexOutOfBoundsException expected) {
        }
    }

    // Covers: delete(start,end) removes characters in range; endIndex>size treated as size
    @Test
    public void testDeleteRange_removesSubstring() throws Throwable {
        StrBuilder sb = new StrBuilder("abcdef");
        sb.delete(1, 3);
        assertEquals("adef", sb.toString());
        sb.delete(1, 100);
        assertEquals("a", sb.toString());
    }

    // Covers: deleteAll(char) removes every occurrence including consecutive runs
    @Test
    public void testDeleteAllChar_removesAllOccurrences() throws Throwable {
        StrBuilder sb = new StrBuilder("aabaa");
        sb.deleteAll('a');
        assertEquals("b", sb.toString());
    }

    // Covers: deleteFirst(char) removes only the first occurrence
    @Test
    public void testDeleteFirstChar_removesOnlyFirst() throws Throwable {
        StrBuilder sb = new StrBuilder("banana");
        sb.deleteFirst('a');
        assertEquals("bnana", sb.toString());
    }

    // Covers: deleteAll(String) removes every occurrence; null string causes no action
    @Test
    public void testDeleteAllString_removesAllOccurrences() throws Throwable {
        StrBuilder sb = new StrBuilder("ababab");
        sb.deleteAll("ab");
        assertEquals("", sb.toString());
        StrBuilder sb2 = new StrBuilder("xyz");
        sb2.deleteAll((String) null);
        assertEquals("xyz", sb2.toString());
    }

    // Covers: replaceAll(String,String) replaces every match; null searchStr no action
    @Test
    public void testReplaceAllString_replacesAllOccurrences() throws Throwable {
        StrBuilder sb = new StrBuilder("a-a-a");
        sb.replaceAll("-", "+");
        assertEquals("a+a+a", sb.toString());
        StrBuilder sb2 = new StrBuilder("abc");
        sb2.replaceAll((String) null, "x");
        assertEquals("abc", sb2.toString());
    }

    // Covers: replaceAll(char,char) replaces all matches; replaceFirst(char,char) only first
    @Test
    public void testReplaceAllChar_andReplaceFirstChar() throws Throwable {
        StrBuilder sb = new StrBuilder("aaa");
        sb.replaceFirst('a', 'b');
        assertEquals("baa", sb.toString());
        sb.replaceAll('a', 'c');
        assertEquals("bcc", sb.toString());
    }

    // Covers: reverse() swaps characters symmetrically; empty builder no-op
    @Test
    public void testReverse_reversesContent() throws Throwable {
        StrBuilder sb = new StrBuilder("abcd");
        sb.reverse();
        assertEquals("dcba", sb.toString());
        StrBuilder empty = new StrBuilder();
        empty.reverse();
        assertEquals(0, empty.length());
    }

    // Covers: trim() removes chars <= ' ' from both ends
    @Test
    public void testTrim_removesLeadingAndTrailingWhitespace() throws Throwable {
        StrBuilder sb = new StrBuilder("  abc  ");
        sb.trim();
        assertEquals("abc", sb.toString());
    }

    // Covers: startsWith/endsWith true/false and null-safe behavior
    @Test
    public void testStartsWithAndEndsWith() throws Throwable {
        StrBuilder sb = new StrBuilder("hello");
        assertTrue(sb.startsWith("he"));
        assertFalse(sb.startsWith("xy"));
        assertFalse(sb.startsWith(null));
        assertTrue(sb.endsWith("lo"));
        assertFalse(sb.endsWith(null));
    }

    // Covers: substring(start) and substring(start,end) with endIndex beyond length treated as size
    @Test
    public void testSubstring_bothOverloads() throws Throwable {
        StrBuilder sb = new StrBuilder("abcdef");
        assertEquals("cdef", sb.substring(2));
        assertEquals("cd", sb.substring(2, 4));
        assertEquals("cdef", sb.substring(2, 100));
    }

    // Covers: leftString/rightString/midString edge handling (negative length, overflow length)
    @Test
    public void testLeftRightMidString() throws Throwable {
        StrBuilder sb = new StrBuilder("abcdef");
        assertEquals("", sb.leftString(-1));
        assertEquals("abc", sb.leftString(3));
        assertEquals("abcdef", sb.leftString(100));
        assertEquals("def", sb.rightString(3));
        assertEquals("cd", sb.midString(2, 2));
        assertEquals("", sb.midString(2, -1));
    }

    // Covers: contains(char) and contains(String) true/false
    @Test
    public void testContainsCharAndString() throws Throwable {
        StrBuilder sb = new StrBuilder("hello");
        assertTrue(sb.contains('e'));
        assertFalse(sb.contains('z'));
        assertTrue(sb.contains("ell"));
        assertFalse(sb.contains("xyz"));
    }

    // Covers: indexOf(char), indexOf(char,start), indexOf(String), indexOf(String,start)
    @Test
    public void testIndexOfCharAndString() throws Throwable {
        StrBuilder sb = new StrBuilder("abcabc");
        assertEquals(0, sb.indexOf('a'));
        assertEquals(3, sb.indexOf('a', 1));
        assertEquals(0, sb.indexOf("abc"));
        assertEquals(3, sb.indexOf("abc", 1));
        assertEquals(-1, sb.indexOf("xyz"));
    }

    // Covers: lastIndexOf(char), lastIndexOf(char,start), lastIndexOf(String), lastIndexOf(String,start)
    @Test
    public void testLastIndexOfCharAndString() throws Throwable {
        StrBuilder sb = new StrBuilder("abcabc");
        assertEquals(3, sb.lastIndexOf('a'));
        assertEquals(0, sb.lastIndexOf('a', 2));
        assertEquals(3, sb.lastIndexOf("abc"));
        assertEquals(-1, sb.lastIndexOf("xyz"));
    }

    // Covers: equals(StrBuilder)/equals(Object) same & different content; equalsIgnoreCase case-insensitive match
    @Test
    public void testEqualsAndEqualsIgnoreCase() throws Throwable {
        StrBuilder sb1 = new StrBuilder("abc");
        StrBuilder sb2 = new StrBuilder("abc");
        StrBuilder sb3 = new StrBuilder("ABC");
        assertTrue(sb1.equals((Object) sb2));
        assertFalse(sb1.equals((Object) "abc"));
        assertTrue(sb1.equalsIgnoreCase(sb3));
        assertFalse(sb1.equals(new StrBuilder("abcd")));
    }

    // Covers: hashCode produces same value for equal content, consistent across calls
    @Test
    public void testHashCode_consistentForEqualContent() throws Throwable {
        StrBuilder sb1 = new StrBuilder("abc");
        StrBuilder sb2 = new StrBuilder("abc");
        assertEquals(sb1.hashCode(), sb2.hashCode());
        assertEquals(sb1.hashCode(), sb1.hashCode());
    }

    // Covers: toString() returns independent copy; toStringBuffer() contains same content
    @Test
    public void testToStringAndToStringBuffer() throws Throwable {
        StrBuilder sb = new StrBuilder("abc");
        assertEquals("abc", sb.toString());
        StringBuffer buf = sb.toStringBuffer();
        assertEquals("abc", buf.toString());
    }
}
