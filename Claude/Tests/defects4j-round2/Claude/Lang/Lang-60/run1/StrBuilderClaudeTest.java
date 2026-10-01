package org.apache.commons.lang.text;

import static org.junit.Assert.*;
import org.junit.Test;

import java.io.Reader;
import java.io.Writer;
import java.util.ArrayList;
import java.util.List;
import java.util.Iterator;

import org.apache.commons.lang.SystemUtils;

public class StrBuilderClaudeTest {

    // constructor(): default capacity is CAPACITY=32, length 0
    @Test
    public void testConstructorDefault_capacity32() throws Throwable {
        StrBuilder sb = new StrBuilder();
        assertEquals(0, sb.length());
        assertEquals(32, sb.capacity());
    }

    // constructor(int): non-positive initialCapacity falls back to 32
    @Test
    public void testConstructorInitialCapacity_nonPositive_usesDefault32() throws Throwable {
        StrBuilder sb1 = new StrBuilder(0);
        StrBuilder sb2 = new StrBuilder(-5);
        assertEquals(32, sb1.capacity());
        assertEquals(32, sb2.capacity());
    }

    // constructor(int): positive initialCapacity is used exactly
    @Test
    public void testConstructorInitialCapacity_positive_usesGiven() throws Throwable {
        StrBuilder sb = new StrBuilder(10);
        assertEquals(10, sb.capacity());
    }

    // constructor(String): null is treated as blank string
    @Test
    public void testConstructorString_null_treatedAsBlank() throws Throwable {
        StrBuilder sb = new StrBuilder((String) null);
        assertEquals(0, sb.length());
        assertEquals(32, sb.capacity());
    }

    // constructor(String): non-null content is appended, capacity = len+32
    @Test
    public void testConstructorString_nonNull_appendsAndAddsCapacity() throws Throwable {
        StrBuilder sb = new StrBuilder("ab");
        assertEquals("ab", sb.toString());
        assertEquals(34, sb.capacity());
    }

    // setNewLineText/getNewLineText round trip, used by appendNewLine
    @Test
    public void testSetNewLineText_andAppendNewLine_usesCustomText() throws Throwable {
        StrBuilder sb = new StrBuilder();
        sb.setNewLineText("X");
        assertEquals("X", sb.getNewLineText());
        sb.appendNewLine();
        assertEquals("X", sb.toString());
    }

    // appendNewLine: null newLine text falls back to system line separator
    @Test
    public void testAppendNewLine_nullNewLineText_usesSystemLineSeparator() throws Throwable {
        StrBuilder sb = new StrBuilder();
        sb.appendNewLine();
        assertEquals(SystemUtils.LINE_SEPARATOR, sb.toString());
    }

    // setNullText: empty string is normalized to null
    @Test
    public void testSetNullText_emptyString_convertsToNull() throws Throwable {
        StrBuilder sb = new StrBuilder();
        sb.setNullText("");
        assertNull(sb.getNullText());
    }

    // setNullText: non-empty value is stored as-is
    @Test
    public void testSetNullText_nonEmpty_setsValue() throws Throwable {
        StrBuilder sb = new StrBuilder();
        sb.setNullText("N/A");
        assertEquals("N/A", sb.getNullText());
    }

    // appendNull: with nullText set, it is appended
    @Test
    public void testAppendNull_withNullTextSet_appendsNullText() throws Throwable {
        StrBuilder sb = new StrBuilder();
        sb.setNullText("NULL");
        sb.appendNull();
        assertEquals("NULL", sb.toString());
    }

    // appendNull: with no nullText set, nothing is appended
    @Test
    public void testAppendNull_withoutNullTextSet_appendsNothing() throws Throwable {
        StrBuilder sb = new StrBuilder();
        sb.appendNull();
        assertEquals("", sb.toString());
    }

    // append(Object): null delegates to appendNull()
    @Test
    public void testAppendObject_null_delegatesToAppendNull() throws Throwable {
        StrBuilder sb = new StrBuilder();
        sb.setNullText("N");
        sb.append((Object) null);
        assertEquals("N", sb.toString());
    }

    // append(Object): non-null uses toString()
    @Test
    public void testAppendObject_nonNull_usesToString() throws Throwable {
        StrBuilder sb = new StrBuilder();
        sb.append(new Integer(5));
        assertEquals("5", sb.toString());
    }

    // append(String): null appends nothing by default
    @Test
    public void testAppendString_null_appendsNothingByDefault() throws Throwable {
        StrBuilder sb = new StrBuilder();
        sb.append((String) null);
        assertEquals("", sb.toString());
    }

    // append(String,start,length): invalid startIndex throws
    @Test
    public void testAppendStringStartLength_invalidStartIndex_throws() throws Throwable {
        StrBuilder sb = new StrBuilder();
        try {
            sb.append("abc", -1, 2);
            fail("expected StringIndexOutOfBoundsException");
        } catch (StringIndexOutOfBoundsException expected) {
        }
    }

    // append(String,start,length): invalid length throws
    @Test
    public void testAppendStringStartLength_invalidLength_throws() throws Throwable {
        StrBuilder sb = new StrBuilder();
        try {
            sb.append("abc", 1, 5);
            fail("expected StringIndexOutOfBoundsException");
        } catch (StringIndexOutOfBoundsException expected) {
        }
    }

    // append(String,start,length): valid range appends substring
    @Test
    public void testAppendStringStartLength_valid_appendsSubstring() throws Throwable {
        StrBuilder sb = new StrBuilder();
        sb.append("abcdef", 1, 3);
        assertEquals("bcd", sb.toString());
    }

    // append(char[]): null appends nothing by default
    @Test
    public void testAppendCharArray_null_appendsNothing() throws Throwable {
        StrBuilder sb = new StrBuilder();
        sb.append((char[]) null);
        assertEquals("", sb.toString());
    }

    // append(char[],offset,length): valid range appends subset
    @Test
    public void testAppendCharArrayRange_valid() throws Throwable {
        StrBuilder sb = new StrBuilder();
        char[] c = new char[] {'a', 'b', 'c', 'd'};
        sb.append(c, 1, 2);
        assertEquals("bc", sb.toString());
    }

    // append(boolean): true/false produce "true"/"false"
    @Test
    public void testAppendBoolean_trueAndFalse() throws Throwable {
        StrBuilder sb = new StrBuilder();
        sb.append(true);
        sb.append(false);
        assertEquals("truefalse", sb.toString());
    }

    // append(int,long,float,double): all use String.valueOf
    @Test
    public void testAppendPrimitiveNumbers_usesStringValueOf() throws Throwable {
        StrBuilder sb = new StrBuilder();
        sb.append(1).append(2L).append(1.5f).append(2.5d);
        String expected = String.valueOf(1) + String.valueOf(2L) + String.valueOf(1.5f) + String.valueOf(2.5d);
        assertEquals(expected, sb.toString());
    }

    // appendWithSeparators(Object[]): null array has no effect
    @Test
    public void testAppendWithSeparatorsObjectArray_nullArray_noEffect() throws Throwable {
        StrBuilder sb = new StrBuilder();
        sb.appendWithSeparators((Object[]) null, ",");
        assertEquals("", sb.toString());
    }

    // appendWithSeparators(Object[]): separator placed only between elements
    @Test
    public void testAppendWithSeparatorsObjectArray_multipleElements() throws Throwable {
        StrBuilder sb = new StrBuilder();
        sb.appendWithSeparators(new Object[] {"a", "b", "c"}, "-");
        assertEquals("a-b-c", sb.toString());
    }

    // appendWithSeparators(Collection): empty collection has no effect
    @Test
    public void testAppendWithSeparatorsCollection_emptyCollection_noEffect() throws Throwable {
        StrBuilder sb = new StrBuilder();
        List<String> list = new ArrayList<String>();
        sb.appendWithSeparators(list, ",");
        assertEquals("", sb.toString());
    }

    // appendWithSeparators(Iterator): single element has no trailing separator
    @Test
    public void testAppendWithSeparatorsIterator_singleElement_noSeparator() throws Throwable {
        StrBuilder sb = new StrBuilder();
        List<String> list = new ArrayList<String>();
        list.add("only");
        Iterator it = list.iterator();
        sb.appendWithSeparators(it, ",");
        assertEquals("only", sb.toString());
    }

    // appendPadding: negative length has no effect
    @Test
    public void testAppendPadding_negativeLength_noEffect() throws Throwable {
        StrBuilder sb = new StrBuilder();
        sb.appendPadding(-1, 'x');
        assertEquals(0, sb.length());
    }

    // appendPadding: positive length appends that many pad chars
    @Test
    public void testAppendPadding_positiveLength_appendsChars() throws Throwable {
        StrBuilder sb = new StrBuilder();
        sb.appendPadding(3, '*');
        assertEquals("***", sb.toString());
    }

    // appendFixedWidthPadLeft: object larger than width loses the LEFT side (per javadoc)
    @Test
    public void testAppendFixedWidthPadLeft_objLargerThanWidth_losesLeftSide() throws Throwable {
        StrBuilder sb = new StrBuilder();
        sb.appendFixedWidthPadLeft("abcdef", 3, ' ');
        assertEquals("def", sb.toString());
    }

    // appendFixedWidthPadLeft: object smaller than width pads on the left
    @Test
    public void testAppendFixedWidthPadLeft_objSmallerThanWidth_padsLeft() throws Throwable {
        StrBuilder sb = new StrBuilder();
        sb.appendFixedWidthPadLeft("ab", 5, '0');
        assertEquals("000ab", sb.toString());
    }

    // appendFixedWidthPadLeft(int): uses String.valueOf of the int
    @Test
    public void testAppendFixedWidthPadLeftInt_usesStringValueOf() throws Throwable {
        StrBuilder sb = new StrBuilder();
        sb.appendFixedWidthPadLeft(42, 4, '0');
        assertEquals("0042", sb.toString());
    }

    // BUG (Lang-60): appendFixedWidthPadRight must lose the RIGHT side when obj is
    // larger than width (per javadoc), not copy the whole string and overflow.
    @Test
    public void testAppendFixedWidthPadRight_objLargerThanWidth_losesRightSide() throws Throwable {
        StrBuilder sb = new StrBuilder(2);
        sb.appendFixedWidthPadRight("abcdef", 2, ' ');
        assertEquals("ab", sb.toString());
    }

    // appendFixedWidthPadRight: object smaller than width pads on the right
    @Test
    public void testAppendFixedWidthPadRight_objSmallerThanWidth_padsRight() throws Throwable {
        StrBuilder sb = new StrBuilder();
        sb.appendFixedWidthPadRight("ab", 5, '-');
        assertEquals("ab---", sb.toString());
    }

    // appendFixedWidthPadRight(int): uses String.valueOf of the int
    @Test
    public void testAppendFixedWidthPadRightInt_usesStringValueOf() throws Throwable {
        StrBuilder sb = new StrBuilder();
        sb.appendFixedWidthPadRight(7, 3, '0');
        assertEquals("700", sb.toString());
    }

    // insert(index, Object): null with default (null) nullText inserts nothing
    @Test
    public void testInsertObject_null_withoutNullText_insertsNothing() throws Throwable {
        StrBuilder sb = new StrBuilder("ac");
        sb.insert(1, (Object) null);
        assertEquals("ac", sb.toString());
    }

    // insert(index, String): valid index inserts at that position
    @Test
    public void testInsertString_validIndex_insertsAtPosition() throws Throwable {
        StrBuilder sb = new StrBuilder("ac");
        sb.insert(1, "b");
        assertEquals("abc", sb.toString());
    }

    // insert(index, String): invalid index throws
    @Test
    public void testInsertString_invalidIndex_throws() throws Throwable {
        StrBuilder sb = new StrBuilder("ac");
        try {
            sb.insert(-1, "x");
            fail("expected StringIndexOutOfBoundsException");
        } catch (StringIndexOutOfBoundsException expected) {
        }
    }

    // insert(index, char[]): null with default nullText inserts nothing
    @Test
    public void testInsertCharArray_null_insertsNothing() throws Throwable {
        StrBuilder sb = new StrBuilder("ac");
        sb.insert(1, (char[]) null);
        assertEquals("ac", sb.toString());
    }

    // insert(index, char[]): non-null inserts the characters
    @Test
    public void testInsertCharArray_nonNull_insertsChars() throws Throwable {
        StrBuilder sb = new StrBuilder("ac");
        sb.insert(1, new char[] {'b'});
        assertEquals("abc", sb.toString());
    }

    // insert(index, boolean): true/false insert at front, pushing prior content right
    @Test
    public void testInsertBoolean_trueAndFalse() throws Throwable {
        StrBuilder sb = new StrBuilder("|");
        sb.insert(0, true);
        sb.insert(0, false);
        assertEquals("falsetrue|", sb.toString());
    }

    // insert(index, char): inserts single char at position
    @Test
    public void testInsertChar_validIndex() throws Throwable {
        StrBuilder sb = new StrBuilder("ac");
        sb.insert(1, 'b');
        assertEquals("abc", sb.toString());
    }

    // insert(index, int): uses String.valueOf of the int
    @Test
    public void testInsertInt_usesStringValueOf() throws Throwable {
        StrBuilder sb = new StrBuilder("ac");
        sb.insert(1, 9);
        assertEquals("a9c", sb.toString());
    }

    // deleteCharAt: valid index removes char; out-of-range index throws
    @Test
    public void testDeleteCharAt_validAndInvalidIndex() throws Throwable {
        StrBuilder sb = new StrBuilder("abc");
        sb.deleteCharAt(1);
        assertEquals("ac", sb.toString());
        try {
            sb.deleteCharAt(10);
            fail("expected StringIndexOutOfBoundsException");
        } catch (StringIndexOutOfBoundsException expected) {
        }
    }

    // delete(start,end): endIndex greater than size is clamped to size
    @Test
    public void testDelete_endIndexTooLarge_clampedToSize() throws Throwable {
        StrBuilder sb = new StrBuilder("abcdef");
        sb.delete(2, 100);
        assertEquals("ab", sb.toString());
    }

    // deleteAll(char): removes all occurrences
    @Test
    public void testDeleteAllChar_multipleOccurrences() throws Throwable {
        StrBuilder sb = new StrBuilder("ababab");
        sb.deleteAll('a');
        assertEquals("bbb", sb.toString());
    }

    // deleteFirst(char): removes only the first occurrence
    @Test
    public void testDeleteFirstChar_firstOccurrenceOnly() throws Throwable {
        StrBuilder sb = new StrBuilder("ababab");
        sb.deleteFirst('a');
        assertEquals("babab", sb.toString());
    }

    // deleteAll(String): removes all occurrences of the substring
    @Test
    public void testDeleteAllString_multipleOccurrences() throws Throwable {
        StrBuilder sb = new StrBuilder("xxabxxabxx");
        sb.deleteAll("ab");
        assertEquals("xxxxxx", sb.toString());
    }

    // deleteFirst(String): removes only the first occurrence
    @Test
    public void testDeleteFirstString_firstOccurrenceOnly() throws Throwable {
        StrBuilder sb = new StrBuilder("ababab");
        sb.deleteFirst("ab");
        assertEquals("abab", sb.toString());
    }

    // replace(start,end,str): replaces a range, lengths may differ
    @Test
    public void testReplace_rangeReplacement() throws Throwable {
        StrBuilder sb = new StrBuilder("abcdef");
        sb.replace(1, 3, "XYZ");
        assertEquals("aXYZdef", sb.toString());
    }

    // replaceAll(char,char): replaces every matching character
    @Test
    public void testReplaceAllChar_replacesAllOccurrences() throws Throwable {
        StrBuilder sb = new StrBuilder("banana");
        sb.replaceAll('a', 'o');
        assertEquals("bonono", sb.toString());
    }

    // replaceFirst(char,char): replaces only the first match
    @Test
    public void testReplaceFirstChar_replacesOnlyFirst() throws Throwable {
        StrBuilder sb = new StrBuilder("banana");
        sb.replaceFirst('a', 'o');
        assertEquals("bonana", sb.toString());
    }

    // replaceAll(String,String): replaces every occurrence of the search string
    @Test
    public void testReplaceAllString_replacesAllOccurrences() throws Throwable {
        StrBuilder sb = new StrBuilder("one two two three");
        sb.replaceAll("two", "2");
        assertEquals("one 2 2 three", sb.toString());
    }

    // replaceFirst(String,String): replaces only the first occurrence
    @Test
    public void testReplaceFirstString_replacesOnlyFirst() throws Throwable {
        StrBuilder sb = new StrBuilder("aa bb aa");
        sb.replaceFirst("aa", "X");
        assertEquals("X bb aa", sb.toString());
    }

    // reverse: empty builder stays empty; non-empty is reversed
    @Test
    public void testReverse_emptyAndNonEmpty() throws Throwable {
        StrBuilder empty = new StrBuilder();
        empty.reverse();
        assertEquals("", empty.toString());
        StrBuilder sb = new StrBuilder("abc");
        sb.reverse();
        assertEquals("cba", sb.toString());
    }

    // trim: removes leading and trailing whitespace (chars <= space)
    @Test
    public void testTrim_removesLeadingAndTrailingWhitespace() throws Throwable {
        StrBuilder sb = new StrBuilder("  hi  ");
        sb.trim();
        assertEquals("hi", sb.toString());
    }

    // startsWith: matching prefix true, non-matching false, null false
    @Test
    public void testStartsWith_variousCases() throws Throwable {
        StrBuilder sb = new StrBuilder("hello");
        assertTrue(sb.startsWith("he"));
        assertFalse(sb.startsWith("lo"));
        assertFalse(sb.startsWith(null));
    }

    // endsWith: matching suffix true, non-matching false, null false
    @Test
    public void testEndsWith_variousCases() throws Throwable {
        StrBuilder sb = new StrBuilder("hello");
        assertTrue(sb.endsWith("lo"));
        assertFalse(sb.endsWith("he"));
        assertFalse(sb.endsWith(null));
    }

    // substring(start): returns tail from start to end
    @Test
    public void testSubstringOneArg_returnsTail() throws Throwable {
        StrBuilder sb = new StrBuilder("hello");
        assertEquals("llo", sb.substring(2));
    }

    // substring(start,end): endIndex too large is clamped to size, no exception
    @Test
    public void testSubstringTwoArgs_endIndexTooLarge_clampedToSize() throws Throwable {
        StrBuilder sb = new StrBuilder("hello");
        assertEquals("llo", sb.substring(2, 100));
    }

    // leftString: negative returns empty, too-large returns whole string
    @Test
    public void testLeftString_variousLengths() throws Throwable {
        StrBuilder sb = new StrBuilder("hello");
        assertEquals("he", sb.leftString(2));
        assertEquals("", sb.leftString(-1));
        assertEquals("hello", sb.leftString(100));
    }

    // rightString: zero returns empty, too-large returns whole string
    @Test
    public void testRightString_variousLengths() throws Throwable {
        StrBuilder sb = new StrBuilder("hello");
        assertEquals("lo", sb.rightString(2));
        assertEquals("", sb.rightString(0));
        assertEquals("hello", sb.rightString(100));
    }

    // midString: negative index clamped to 0, length overflow clamped to end
    @Test
    public void testMidString_variousCases() throws Throwable {
        StrBuilder sb = new StrBuilder("hello");
        assertEquals("ell", sb.midString(1, 3));
        assertEquals("", sb.midString(-1, 0));
        assertEquals("llo", sb.midString(2, 100));
    }

    // contains(char): true when present, false otherwise
    @Test
    public void testContainsChar_trueAndFalse() throws Throwable {
        StrBuilder sb = new StrBuilder("hello");
        assertTrue(sb.contains('e'));
        assertFalse(sb.contains('z'));
    }

    // contains(String): true when substring present, false otherwise
    @Test
    public void testContainsString_trueAndFalse() throws Throwable {
        StrBuilder sb = new StrBuilder("hello");
        assertTrue(sb.contains("ell"));
        assertFalse(sb.contains("xyz"));
    }

    // indexOf(char): found returns index, not found returns -1
    @Test
    public void testIndexOfChar_foundAndNotFound() throws Throwable {
        StrBuilder sb = new StrBuilder("hello");
        assertEquals(1, sb.indexOf('e'));
        assertEquals(-1, sb.indexOf('z'));
    }

    // indexOf(char,startIndex): negative startIndex is rounded to zero
    @Test
    public void testIndexOfCharWithStart_negativeStartClampedToZero() throws Throwable {
        StrBuilder sb = new StrBuilder("ababab");
        assertEquals(2, sb.indexOf('a', 1));
        assertEquals(0, sb.indexOf('a', -5));
    }

    // indexOf(String): found, not found, and empty-string edge cases
    @Test
    public void testIndexOfString_foundNotFoundAndEmptyString() throws Throwable {
        StrBuilder sb = new StrBuilder("hello");
        assertEquals(1, sb.indexOf("ell"));
        assertEquals(-1, sb.indexOf("xyz"));
        assertEquals(0, sb.indexOf(""));
    }

    // indexOf(String): null search string returns -1 (unlike JDK which throws)
    @Test
    public void testIndexOfString_null_returnsMinusOne() throws Throwable {
        StrBuilder sb = new StrBuilder("hello");
        assertEquals(-1, sb.indexOf((String) null));
    }

    // lastIndexOf(char): found returns last index, not found returns -1
    @Test
    public void testLastIndexOfChar_foundAndNotFound() throws Throwable {
        StrBuilder sb = new StrBuilder("ababab");
        assertEquals(4, sb.lastIndexOf('a'));
        assertEquals(-1, sb.lastIndexOf('z'));
    }

    // lastIndexOf(String): found and null returns -1
    @Test
    public void testLastIndexOfString_foundAndNull() throws Throwable {
        StrBuilder sb = new StrBuilder("abcabc");
        assertEquals(3, sb.lastIndexOf("abc"));
        assertEquals(-1, sb.lastIndexOf((String) null));
    }

    // setLength: shrinking truncates, growing pads with unicode zero
    @Test
    public void testSetLength_growAndShrink() throws Throwable {
        StrBuilder sb = new StrBuilder("hello");
        sb.setLength(3);
        assertEquals("hel", sb.toString());
        sb.setLength(5);
        assertEquals(5, sb.length());
        assertEquals('\0', sb.charAt(4));
    }

    // setLength: negative length throws
    @Test
    public void testSetLength_negative_throws() throws Throwable {
        StrBuilder sb = new StrBuilder();
        try {
            sb.setLength(-1);
            fail("expected StringIndexOutOfBoundsException");
        } catch (StringIndexOutOfBoundsException expected) {
        }
    }

    // capacity/ensureCapacity: growth to a larger explicit capacity
    @Test
    public void testEnsureCapacity_growsToRequestedSize() throws Throwable {
        StrBuilder sb = new StrBuilder(5);
        assertEquals(5, sb.capacity());
        sb.ensureCapacity(20);
        assertEquals(20, sb.capacity());
    }

    // minimizeCapacity: reduces capacity down to current length
    @Test
    public void testMinimizeCapacity_reducesToLength() throws Throwable {
        StrBuilder sb = new StrBuilder(50);
        sb.append("abc");
        sb.minimizeCapacity();
        assertEquals(3, sb.capacity());
    }

    // size/isEmpty/clear: collections-style API
    @Test
    public void testSizeIsEmptyClear() throws Throwable {
        StrBuilder sb = new StrBuilder();
        assertTrue(sb.isEmpty());
        sb.append("x");
        assertFalse(sb.isEmpty());
        assertEquals(1, sb.size());
        sb.clear();
        assertEquals(0, sb.size());
    }

    // charAt/setCharAt: valid access and out-of-range exception
    @Test
    public void testCharAtAndSetCharAt_validAndInvalid() throws Throwable {
        StrBuilder sb = new StrBuilder("abc");
        assertEquals('b', sb.charAt(1));
        sb.setCharAt(0, 'z');
        assertEquals("zbc", sb.toString());
        try {
            sb.charAt(10);
            fail("expected StringIndexOutOfBoundsException");
        } catch (StringIndexOutOfBoundsException expected) {
        }
    }

    // toCharArray(): empty builder returns zero-length array, non-empty copies content
    @Test
    public void testToCharArray_emptyAndNonEmpty() throws Throwable {
        StrBuilder empty = new StrBuilder();
        assertEquals(0, empty.toCharArray().length);
        StrBuilder sb = new StrBuilder("abc");
        char[] arr = sb.toCharArray();
        assertEquals(3, arr.length);
        assertEquals('a', arr[0]);
    }

    // toCharArray(start,end): valid range copies the requested slice
    @Test
    public void testToCharArrayRange_valid() throws Throwable {
        StrBuilder sb = new StrBuilder("abcdef");
        char[] arr = sb.toCharArray(1, 4);
        assertEquals(3, arr.length);
        assertEquals('b', arr[0]);
        assertEquals('d', arr[2]);
    }

    // getChars(destination): null destination causes a new array to be created
    @Test
    public void testGetCharsDestinationNull_createsArray() throws Throwable {
        StrBuilder sb = new StrBuilder("abc");
        char[] result = sb.getChars(null);
        assertEquals(3, result.length);
        assertEquals('a', result[0]);
    }

    // getChars(start,end,dest,destIndex): copies requested slice into destination
    @Test
    public void testGetCharsRange_copiesIntoDestination() throws Throwable {
        StrBuilder sb = new StrBuilder("abcdef");
        char[] dest = new char[10];
        sb.getChars(1, 4, dest, 0);
        assertEquals('b', dest[0]);
        assertEquals('c', dest[1]);
        assertEquals('d', dest[2]);
    }

    // equalsIgnoreCase: case-insensitive comparison of content
    @Test
    public void testEqualsIgnoreCase_trueAndFalse() throws Throwable {
        StrBuilder a = new StrBuilder("ABC");
        StrBuilder b = new StrBuilder("abc");
        StrBuilder c = new StrBuilder("abd");
        assertTrue(a.equalsIgnoreCase(b));
        assertFalse(a.equalsIgnoreCase(c));
    }

    // equals(Object): non-StrBuilder argument returns false, matching content returns true
    @Test
    public void testEqualsObject_nonStrBuilderAndMatchingContent() throws Throwable {
        StrBuilder a = new StrBuilder("abc");
        StrBuilder b = new StrBuilder("abc");
        assertFalse(a.equals("abc"));
        assertTrue(a.equals((Object) b));
    }

    // hashCode: equal content produces equal hash codes
    @Test
    public void testHashCode_consistentForEqualContent() throws Throwable {
        StrBuilder a = new StrBuilder("abc");
        StrBuilder b = new StrBuilder("abc");
        assertEquals(a.hashCode(), b.hashCode());
    }

    // toStringBuffer: returns a StringBuffer with equivalent content
    @Test
    public void testToStringBuffer_returnsEquivalentContent() throws Throwable {
        StrBuilder sb = new StrBuilder("abc");
        StringBuffer buf = sb.toStringBuffer();
        assertEquals("abc", buf.toString());
    }

    // validateRange (protected, same package): negative start throws, oversized end is clamped
    @Test
    public void testValidateRange_invalidStartAndClampedEnd() throws Throwable {
        StrBuilder sb = new StrBuilder("abc");
        assertEquals(3, sb.validateRange(0, 100));
        try {
            sb.validateRange(-1, 2);
            fail("expected StringIndexOutOfBoundsException");
        } catch (StringIndexOutOfBoundsException expected) {
        }
    }

    // validateIndex (protected, same package): out-of-range index throws
    @Test
    public void testValidateIndex_invalidIndex_throws() throws Throwable {
        StrBuilder sb = new StrBuilder("abc");
        try {
            sb.validateIndex(5);
            fail("expected StringIndexOutOfBoundsException");
        } catch (StringIndexOutOfBoundsException expected) {
        }
    }

    // asReader: returned Reader shares the builder's content and supports read()
    @Test
    public void testAsReader_readsBuilderContent() throws Throwable {
        StrBuilder sb = new StrBuilder("ab");
        Reader r = sb.asReader();
        assertEquals('a', (char) r.read());
        assertEquals('b', (char) r.read());
        assertEquals(-1, r.read());
    }

    // asWriter: writing through the Writer mutates the underlying builder
    @Test
    public void testAsWriter_writesIntoBuilder() throws Throwable {
        StrBuilder sb = new StrBuilder();
        Writer w = sb.asWriter();
        w.write("hi");
        assertEquals("hi", sb.toString());
    }

    // asTokenizer: returns a non-null tokenizer linked to this builder
    @Test
    public void testAsTokenizer_returnsNonNullTokenizer() throws Throwable {
        StrBuilder sb = new StrBuilder("a b");
        StrTokenizer tok = sb.asTokenizer();
        assertNotNull(tok);
    }
}
