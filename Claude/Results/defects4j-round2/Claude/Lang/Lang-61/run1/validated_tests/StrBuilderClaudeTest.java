package org.apache.commons.lang.text;

import org.junit.Test;
import static org.junit.Assert.*;

import java.io.Reader;
import java.io.Writer;
import java.util.ArrayList;
import java.util.List;

public class StrBuilderClaudeTest {

    // Branch: default constructor gives capacity 32 and empty content
    @Test
    public void testConstructorDefault_initialState() throws Throwable {
        StrBuilder sb = new StrBuilder();
        assertEquals(32, sb.capacity());
        assertEquals(0, sb.length());
        assertTrue(sb.isEmpty());
    }

    // Branch: non-positive capacity falls back to 32; null string treated as blank
    @Test
    public void testConstructorEdgeCases_capacityAndNullString() throws Throwable {
        StrBuilder sb1 = new StrBuilder(0);
        assertEquals(32, sb1.capacity());
        StrBuilder sb2 = new StrBuilder(-5);
        assertEquals(32, sb2.capacity());
        StrBuilder sb3 = new StrBuilder((String) null);
        assertEquals(0, sb3.length());
        assertEquals(32, sb3.capacity());
    }

    // Branch: newLine getter/setter chaining and appendNewLine using custom text; nullText empty->null
    @Test
    public void testNewLineAndNullText_setGetBehavior() throws Throwable {
        StrBuilder sb = new StrBuilder();
        assertNull(sb.getNewLineText());
        StrBuilder ret = sb.setNewLineText("\n");
        assertSame(sb, ret);
        assertEquals("\n", sb.getNewLineText());
        sb.appendNewLine();
        assertEquals("\n", sb.toString());
        sb.setNullText("");
        assertNull(sb.getNullText());
    }

    // Branch: appendNull() no-op when nullText null, appends nullText when set
    @Test
    public void testAppendNull_withAndWithoutNullText() throws Throwable {
        StrBuilder sb = new StrBuilder();
        sb.appendNull();
        assertEquals("", sb.toString());
        sb.setNullText("NULL");
        sb.appendNull();
        assertEquals("NULL", sb.toString());
    }

    // Branch: setLength(negative) throws StringIndexOutOfBoundsException
    @Test
    public void testSetLength_negativeThrows() throws Throwable {
        StrBuilder sb = new StrBuilder();
        try {
            sb.setLength(-1);
            fail("expected exception");
        } catch (StringIndexOutOfBoundsException expected) {
        }
    }

    // Branch: setLength shrinks (truncate) and grows (pad with '\0')
    @Test
    public void testSetLength_truncateAndPad() throws Throwable {
        StrBuilder sb = new StrBuilder();
        sb.append("hello");
        sb.setLength(3);
        assertEquals("hel", sb.toString());
        sb.setLength(5);
        assertEquals(5, sb.length());
        assertEquals('\0', sb.charAt(3));
        assertEquals('\0', sb.charAt(4));
    }

    // Branch: ensureCapacity grows buffer, minimizeCapacity shrinks it to length
    @Test
    public void testEnsureCapacityAndMinimizeCapacity() throws Throwable {
        StrBuilder sb = new StrBuilder(5);
        assertEquals(5, sb.capacity());
        sb.ensureCapacity(50);
        assertTrue(sb.capacity() >= 50);
        sb.append("hi");
        sb.minimizeCapacity();
        assertEquals(2, sb.capacity());
    }

    // Branch: isEmpty true/false and clear resets size to zero
    @Test
    public void testIsEmptyAndClear() throws Throwable {
        StrBuilder sb = new StrBuilder();
        assertTrue(sb.isEmpty());
        sb.append("x");
        assertFalse(sb.isEmpty());
        sb.clear();
        assertTrue(sb.isEmpty());
        assertEquals(0, sb.size());
    }

    // Branch: charAt/setCharAt throw on invalid index
    @Test
    public void testCharAtAndSetCharAt_invalidIndexThrows() throws Throwable {
        StrBuilder sb = new StrBuilder("ab");
        try {
            sb.charAt(5);
            fail("expected exception");
        } catch (StringIndexOutOfBoundsException expected) {
        }
        try {
            sb.setCharAt(-1, 'x');
            fail("expected exception");
        } catch (StringIndexOutOfBoundsException expected) {
        }
    }

    // Branch: deleteCharAt removes the character at the given index
    @Test
    public void testDeleteCharAt_removesCharacter() throws Throwable {
        StrBuilder sb = new StrBuilder("abc");
        sb.deleteCharAt(1);
        assertEquals("ac", sb.toString());
    }

    // Branch: toCharArray empty returns empty array; ranged version returns subrange
    @Test
    public void testToCharArray_emptyAndRange() throws Throwable {
        StrBuilder sb = new StrBuilder();
        assertEquals(0, sb.toCharArray().length);
        sb.append("abcdef");
        char[] arr = sb.toCharArray(1, 4);
        assertEquals(3, arr.length);
        assertEquals('b', arr[0]);
        assertEquals('d', arr[2]);
    }

    // Branch: getChars(null) allocates array; invalid range (end<start) throws
    @Test
    public void testGetChars_destinationNullAndInvalidRange() throws Throwable {
        StrBuilder sb = new StrBuilder("abc");
        char[] dest = sb.getChars(null);
        assertEquals(3, dest.length);
        assertEquals('a', dest[0]);
        try {
            sb.getChars(2, 1, new char[5], 0);
            fail("expected exception");
        } catch (StringIndexOutOfBoundsException expected) {
        }
    }

    // Branch: append(Object) null delegates to appendNull()
    @Test
    public void testAppendObject_nullUsesNullText() throws Throwable {
        StrBuilder sb = new StrBuilder();
        sb.append((Object) null);
        assertEquals("", sb.toString());
        sb.setNullText("NIL");
        sb.append((Object) null);
        assertEquals("NIL", sb.toString());
    }

    // Branch: append(String) null appends nothing by default; non-null appends content
    @Test
    public void testAppendString_nullAppendsNothing() throws Throwable {
        StrBuilder sb = new StrBuilder();
        sb.append((String) null);
        assertEquals("", sb.toString());
        sb.append("abc");
        assertEquals("abc", sb.toString());
    }

    // Branch: append(String,start,length) invalid startIndex and invalid length both throw
    @Test
    public void testAppendStringRange_invalidStartThrows() throws Throwable {
        StrBuilder sb = new StrBuilder();
        try {
            sb.append("hello", -1, 2);
            fail("expected exception");
        } catch (StringIndexOutOfBoundsException expected) {
        }
        try {
            sb.append("hello", 1, 100);
            fail("expected exception");
        } catch (StringIndexOutOfBoundsException expected) {
        }
    }

    // Branch: append(char[],start,length) invalid length throws; valid range appends subrange
    @Test
    public void testAppendCharArrayRange_invalidLengthThrows() throws Throwable {
        StrBuilder sb = new StrBuilder();
        char[] chars = new char[] {'a', 'b', 'c'};
        try {
            sb.append(chars, 1, 10);
            fail("expected exception");
        } catch (StringIndexOutOfBoundsException expected) {
        }
        sb.append(chars, 1, 2);
        assertEquals("bc", sb.toString());
    }

    // Branch: append boolean true/false and primitive int/long via String.valueOf
    @Test
    public void testAppendPrimitives_booleanCharIntLong() throws Throwable {
        StrBuilder sb = new StrBuilder();
        sb.append(true);
        sb.append(' ');
        sb.append(false);
        sb.append(123);
        sb.append(45L);
        assertEquals("true false12345", sb.toString());
    }

    // Branch: appendWithSeparators(Object[]) null separator becomes empty; null array is no-op
    @Test
    public void testAppendWithSeparatorsObjectArray_nullAndGivenSeparator() throws Throwable {
        Object[] arr = new Object[] {"a", "b", "c"};
        StrBuilder sb1 = new StrBuilder();
        sb1.appendWithSeparators(arr, null);
        assertEquals("abc", sb1.toString());
        StrBuilder sb2 = new StrBuilder();
        sb2.appendWithSeparators(arr, ",");
        assertEquals("a,b,c", sb2.toString());
        StrBuilder sb3 = new StrBuilder();
        sb3.appendWithSeparators((Object[]) null, ",");
        assertEquals("", sb3.toString());
    }

    // Branch: appendWithSeparators(Collection) joins elements with separator
    @Test
    public void testAppendWithSeparatorsCollection_joinsWithSeparator() throws Throwable {
        List list = new ArrayList();
        list.add("x");
        list.add("y");
        StrBuilder sb = new StrBuilder();
        sb.appendWithSeparators(list, "-");
        assertEquals("x-y", sb.toString());
    }

    // Branch: appendPadding negative length no-op, positive length appends padChar repeated
    @Test
    public void testAppendPadding_negativeAndPositive() throws Throwable {
        StrBuilder sb = new StrBuilder();
        sb.append("ab");
        sb.appendPadding(-1, 'x');
        assertEquals("ab", sb.toString());
        sb.appendPadding(3, 'x');
        assertEquals("abxxx", sb.toString());
    }

    // Branch: appendFixedWidthPadLeft truncates left side when value longer than width
    @Test
    public void testAppendFixedWidthPadLeft_truncatesWhenTooLong() throws Throwable {
        StrBuilder sb = new StrBuilder();
        sb.appendFixedWidthPadLeft("HelloWorld", 5, ' ');
        assertEquals("World", sb.toString());
        StrBuilder sb2 = new StrBuilder();
        sb2.appendFixedWidthPadLeft("Hi", 5, '*');
        assertEquals("***Hi", sb2.toString());
    }

    // Branch: appendFixedWidthPadRight pads on the right when value shorter than width
    @Test
    public void testAppendFixedWidthPadRight_padsWhenShorterThanWidth() throws Throwable {
        StrBuilder sb = new StrBuilder();
        sb.appendFixedWidthPadRight("Hi", 5, '*');
        assertEquals("Hi***", sb.toString());
    }



    // Branch: insert at invalid index (negative or beyond size) throws
    @Test
    public void testInsert_invalidIndexThrows() throws Throwable {
        StrBuilder sb = new StrBuilder("abc");
        try {
            sb.insert(10, "x");
            fail("expected exception");
        } catch (StringIndexOutOfBoundsException expected) {
        }
        try {
            sb.insert(-1, "x");
            fail("expected exception");
        } catch (StringIndexOutOfBoundsException expected) {
        }
    }

    // Branch: insert(String) at valid index shifts content; null string with no nullText is no-op
    @Test
    public void testInsertString_atValidIndex() throws Throwable {
        StrBuilder sb = new StrBuilder("ac");
        sb.insert(1, "b");
        assertEquals("abc", sb.toString());
        sb.insert(0, (String) null);
        assertEquals("abc", sb.toString());
    }

    // Branch: delete(start,end) removes range; end beyond size is treated as size
    @Test
    public void testDeleteRange_removesSubstring() throws Throwable {
        StrBuilder sb = new StrBuilder("abcdef");
        sb.delete(1, 3);
        assertEquals("adef", sb.toString());
        sb.delete(2, 100);
        assertEquals("ad", sb.toString());
    }

    // Branch: deleteAll(char) removes every occurrence including consecutive runs
    @Test
    public void testDeleteAllChar_removesAllOccurrences() throws Throwable {
        StrBuilder sb = new StrBuilder("banana");
        sb.deleteAll('a');
        assertEquals("bnn", sb.toString());
    }

    // Branch: deleteFirst(char) removes only the first occurrence
    @Test
    public void testDeleteFirstChar_removesOnlyFirst() throws Throwable {
        StrBuilder sb = new StrBuilder("banana");
        sb.deleteFirst('a');
        assertEquals("bnana", sb.toString());
    }

    // Branch: deleteAll(String) removes all matches; null string causes no action
    @Test
    public void testDeleteAllString_removesAllOccurrences() throws Throwable {
        StrBuilder sb = new StrBuilder("one two one two");
        sb.deleteAll("one ");
        assertEquals("two two", sb.toString());
        StrBuilder sb2 = new StrBuilder("abc");
        sb2.deleteAll((String) null);
        assertEquals("abc", sb2.toString());
    }

    // Branch: replace(start,end,str) substitutes the given range with replacement text
    @Test
    public void testReplaceRange_replacesSubstring() throws Throwable {
        StrBuilder sb = new StrBuilder("Hello World");
        sb.replace(6, 11, "Java");
        assertEquals("Hello Java", sb.toString());
    }

    // Branch: replaceAll(char,char) replaces every matching character
    @Test
    public void testReplaceAllChar_replacesAllOccurrences() throws Throwable {
        StrBuilder sb = new StrBuilder("mississippi");
        sb.replaceAll('s', 'z');
        assertEquals("mizzizzippi", sb.toString());
    }

    // Branch: replaceFirst(String,String) replaces only the first match
    @Test
    public void testReplaceFirstString_replacesFirstOccurrence() throws Throwable {
        StrBuilder sb = new StrBuilder("cat cat cat");
        sb.replaceFirst("cat", "dog");
        assertEquals("dog cat cat", sb.toString());
    }

    // Branch: reverse() swaps characters; empty builder stays empty
    @Test
    public void testReverse_reversesContent() throws Throwable {
        StrBuilder sb = new StrBuilder("abcd");
        sb.reverse();
        assertEquals("dcba", sb.toString());
        StrBuilder empty = new StrBuilder();
        empty.reverse();
        assertEquals("", empty.toString());
    }

    // Branch: trim() removes leading/trailing chars <= space
    @Test
    public void testTrim_removesLeadingTrailingWhitespace() throws Throwable {
        StrBuilder sb = new StrBuilder("  hi there  ");
        sb.trim();
        assertEquals("hi there", sb.toString());
    }

    // Branch: startsWith/endsWith handle null (false), empty (true) and normal matches
    @Test
    public void testStartsWithEndsWith_nullAndEmptyAndNormal() throws Throwable {
        StrBuilder sb = new StrBuilder("abc");
        assertFalse(sb.startsWith(null));
        assertTrue(sb.startsWith(""));
        assertTrue(sb.startsWith("ab"));
        assertFalse(sb.startsWith("abcd"));
        assertFalse(sb.endsWith(null));
        assertTrue(sb.endsWith("bc"));
        assertFalse(sb.endsWith("abcd"));
    }

    // Branch: substring end beyond size is treated as end; negative start throws
    @Test
    public void testSubstring_endIndexBeyondSizeTreatedAsEnd() throws Throwable {
        StrBuilder sb = new StrBuilder("abcdef");
        assertEquals("cdef", sb.substring(2, 100));
        assertEquals("abcdef", sb.substring(0));
        try {
            sb.substring(-1, 3);
            fail("expected exception");
        } catch (StringIndexOutOfBoundsException expected) {
        }
    }

    // Branch: leftString/rightString/midString edge cases (negative length/index, overflow length)
    @Test
    public void testLeftRightMidString_edgeCases() throws Throwable {
        StrBuilder sb = new StrBuilder("abcdef");
        assertEquals("", sb.leftString(-1));
        assertEquals("abc", sb.leftString(3));
        assertEquals("abcdef", sb.leftString(100));
        assertEquals("def", sb.rightString(3));
        assertEquals("abcdef", sb.rightString(100));
        assertEquals("abc", sb.midString(-5, 3));
        assertEquals("", sb.midString(2, -1));
        assertEquals("ef", sb.midString(4, 10));
    }

    // Branch: contains(char)/contains(String) normal found/not-found cases
    @Test
    public void testContainsCharAndString_normalCases() throws Throwable {
        StrBuilder sb = new StrBuilder("hello");
        assertTrue(sb.contains('e'));
        assertFalse(sb.contains('z'));
        assertTrue(sb.contains("ell"));
        assertFalse(sb.contains("xyz"));
    }

    // Branch: indexOf(char) and indexOf(char,startIndex) with found/not-found cases
    @Test
    public void testIndexOfChar_withStartIndex() throws Throwable {
        StrBuilder sb = new StrBuilder("ababab");
        assertEquals(0, sb.indexOf('a'));
        assertEquals(2, sb.indexOf('a', 1));
        assertEquals(-1, sb.indexOf('z'));
    }

    // Branch: indexOf(String) null returns -1; normal match returns index
    @Test
    public void testIndexOfString_nullReturnsNegativeOne() throws Throwable {
        StrBuilder sb = new StrBuilder("abc");
        assertEquals(-1, sb.indexOf((String) null));
        assertEquals(1, sb.indexOf("bc"));
        assertEquals(-1, sb.indexOf("xyz"));
    }

    // Branch: lastIndexOf(char) and lastIndexOf(String,startIndex) found/not-found
    @Test
    public void testLastIndexOf_charAndString() throws Throwable {
        StrBuilder sb = new StrBuilder("ababab");
        assertEquals(4, sb.lastIndexOf('a'));
        assertEquals(2, sb.lastIndexOf("ab", 4));
        assertEquals(-1, sb.lastIndexOf("xyz"));
    }

    // Branch: equals(Object) false for non-StrBuilder/null; equals(StrBuilder) true for same content
    @Test
    public void testEquals_variousCases() throws Throwable {
        StrBuilder sb1 = new StrBuilder("abc");
        StrBuilder sb2 = new StrBuilder("abc");
        assertFalse(sb1.equals("abc"));
        assertFalse(sb1.equals((Object) null));
        assertTrue(sb1.equals(sb2));
        assertEquals(sb1.hashCode(), sb2.hashCode());
    }

    // Branch: equalsIgnoreCase true for different case, equals (case-sensitive) false
    @Test
    public void testEqualsIgnoreCase_differentCase() throws Throwable {
        StrBuilder sb1 = new StrBuilder("ABC");
        StrBuilder sb2 = new StrBuilder("abc");
        assertTrue(sb1.equalsIgnoreCase(sb2));
        assertFalse(sb1.equals(sb2));
    }

    // Branch: toString returns independent copy; toStringBuffer mirrors current content
    @Test
    public void testToStringAndToStringBuffer() throws Throwable {
        StrBuilder sb = new StrBuilder("abc");
        String s1 = sb.toString();
        sb.append("d");
        assertEquals("abc", s1);
        assertEquals("abcd", sb.toString());
        StringBuffer buf = sb.toStringBuffer();
        assertEquals("abcd", buf.toString());
    }

    // Branch: asReader reads appended content then returns -1 at end; asWriter writes into builder
    @Test
    public void testAsReaderAndAsWriter() throws Throwable {
        StrBuilder sb = new StrBuilder("ab");
        Reader r = sb.asReader();
        assertEquals('a', (char) r.read());
        assertEquals('b', (char) r.read());
        assertEquals(-1, r.read());
        Writer w = sb.asWriter();
        w.write("cd");
        assertEquals("abcd", sb.toString());
    }
}
