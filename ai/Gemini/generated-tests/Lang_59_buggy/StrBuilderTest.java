package org.apache.commons.lang.text;

import org.junit.Test;
import static org.junit.Assert.*;

import java.io.Reader;
import java.io.Writer;
import java.util.ArrayList;
import java.util.List;

public class StrBuilderTest {

    @Test
    public void testConstructorsAndCapacities() throws Throwable {
        StrBuilder sb1 = new StrBuilder();
        assertEquals(32, sb1.capacity());
        assertEquals(0, sb1.length());
        assertTrue(sb1.isEmpty());

        StrBuilder sb2 = new StrBuilder(10);
        assertEquals(10, sb2.capacity());

        StrBuilder sb3 = new StrBuilder(-5);
        assertEquals(32, sb3.capacity());

        StrBuilder sb4 = new StrBuilder("test");
        assertEquals("test", sb4.toString());
        assertEquals(36, sb4.capacity());

        StrBuilder sb5 = new StrBuilder(null);
        assertEquals(32, sb5.capacity());
    }

    @Test
    public void testGetAndSetNewLineText() throws Throwable {
        StrBuilder sb = new StrBuilder();
        assertNull(sb.getNewLineText());
        sb.setNewLineText("\n");
        assertEquals("\n", sb.getNewLineText());
    }

    @Test
    public void testGetAndSetNullText() throws Throwable {
        StrBuilder sb = new StrBuilder();
        assertNull(sb.getNullText());
        sb.setNullText("NULL");
        assertEquals("NULL", sb.getNullText());
        sb.setNullText("");
        assertNull(sb.getNullText());
    }

    @Test
    public void testSetLength() throws Throwable {
        StrBuilder sb = new StrBuilder("hello");
        sb.setLength(2);
        assertEquals("he", sb.toString());

        sb.setLength(5);
        assertEquals(5, sb.length());
        assertEquals('\0', sb.charAt(4));

        boolean exceptionThrown = false;
        try {
            sb.setLength(-1);
        } catch (StringIndexOutOfBoundsException e) {
            exceptionThrown = true;
        }
        assertTrue(exceptionThrown);
    }

    @Test
    public void testEnsureCapacityAndMinimize() throws Throwable {
        StrBuilder sb = new StrBuilder("abc");
        sb.ensureCapacity(50);
        assertTrue(sb.capacity() >= 50);

        sb.minimizeCapacity();
        assertEquals(3, sb.capacity());

        StrBuilder sbEmpty = new StrBuilder();
        sbEmpty.minimizeCapacity();
        assertEquals(32, sbEmpty.capacity());
    }

    @Test
    public void testClear() throws Throwable {
        StrBuilder sb = new StrBuilder("abc");
        sb.clear();
        assertTrue(sb.isEmpty());
        assertEquals(0, sb.length());
    }

    @Test
    public void testCharAtAndSetCharAt() throws Throwable {
        StrBuilder sb = new StrBuilder("abc");
        assertEquals('b', sb.charAt(1));

        sb.setCharAt(1, 'z');
        assertEquals("azc", sb.toString());

        boolean ex1 = false;
        try {
            sb.charAt(-1);
        } catch (StringIndexOutOfBoundsException e) {
            ex1 = true;
        }
        assertTrue(ex1);

        boolean ex2 = false;
        try {
            sb.charAt(3);
        } catch (StringIndexOutOfBoundsException e) {
            ex2 = true;
        }
        assertTrue(ex2);

        boolean ex3 = false;
        try {
            sb.setCharAt(3, 'x');
        } catch (StringIndexOutOfBoundsException e) {
            ex3 = true;
        }
        assertTrue(ex3);
    }

    @Test
    public void testDeleteCharAt() throws Throwable {
        StrBuilder sb = new StrBuilder("abc");
        sb.deleteCharAt(1);
        assertEquals("ac", sb.toString());

        boolean ex = false;
        try {
            sb.deleteCharAt(5);
        } catch (StringIndexOutOfBoundsException e) {
            ex = true;
        }
        assertTrue(ex);
    }

    @Test
    public void testToCharArrayAndGetChars() throws Throwable {
        StrBuilder sb = new StrBuilder("hello");
        char[] arr1 = sb.toCharArray();
        assertArrayEquals(new char[]{'h', 'e', 'l', 'l', 'o'}, arr1);

        StrBuilder sbEmpty = new StrBuilder();
        assertArrayEquals(new char[0], sbEmpty.toCharArray());

        char[] arr2 = sb.toCharArray(1, 4);
        assertArrayEquals(new char[]{'e', 'l', 'l'}, arr2);

        char[] dest = new char[5];
        char[] res = sb.getChars(dest);
        assertSame(dest, res);

        char[] destSmall = new char[2];
        char[] resNew = sb.getChars(destSmall);
        assertEquals(5, resNew.length);

        char[] destSpecific = new char[10];
        sb.getChars(1, 4, destSpecific, 2);
        assertEquals('e', destSpecific[2]);
        assertEquals('l', destSpecific[3]);
        assertEquals('l', destSpecific[4]);

        boolean exRange = false;
        try {
            sb.getChars(-1, 2, destSpecific, 0);
        } catch (StringIndexOutOfBoundsException e) {
            exRange = true;
        }
        assertTrue(exRange);

        boolean exEnd = false;
        try {
            sb.getChars(0, 10, destSpecific, 0);
        } catch (StringIndexOutOfBoundsException e) {
            exEnd = true;
        }
        assertTrue(exEnd);

        boolean exOrder = false;
        try {
            sb.getChars(3, 1, destSpecific, 0);
        } catch (StringIndexOutOfBoundsException e) {
            exOrder = true;
        }
        assertTrue(exOrder);
    }

    @Test
    public void testAppendMethods() throws Throwable {
        StrBuilder sb = new StrBuilder();
        sb.append(true).append(false);
        assertEquals("truefalse", sb.toString());

        sb.clear();
        sb.append('a');
        assertEquals("a", sb.toString());

        sb.clear();
        sb.append(123).append(456L).append(1.1f).append(2.2d);
        assertEquals("1234561.12.2", sb.toString());

        sb.clear();
        sb.append((Object) null);
        assertEquals("", sb.toString());

        sb.setNullText("NULL");
        sb.clear();
        sb.append((Object) null);
        assertEquals("NULL", sb.toString());

        sb.clear();
        sb.append("test", 1, 2);
        assertEquals("es", sb.toString());

        boolean exStrStart = false;
        try {
            sb.append("test", -1, 1);
        } catch (StringIndexOutOfBoundsException e) {
            exStrStart = true;
        }
        assertTrue(exStrStart);

        boolean exStrLen = false;
        try {
            sb.append("test", 0, 10);
        } catch (StringIndexOutOfBoundsException e) {
            exStrLen = true;
        }
        assertTrue(exStrLen);

        sb.clear();
        StringBuffer sbuf = new StringBuffer("sbuf");
        sb.append(sbuf);
        sb.append((StringBuffer) null);
        sb.append(sbuf, 1, 2);
        assertEquals("sbufsbuibu", sb.toString());

        boolean exSBufStart = false;
        try {
            sb.append(sbuf, -1, 1);
        } catch (StringIndexOutOfBoundsException e) {
            exSBufStart = true;
        }
        assertTrue(exSBufStart);

        boolean exSBufLen = false;
        try {
            sb.append(sbuf, 0, 10);
        } catch (StringIndexOutOfBoundsException e) {
            exSBufLen = true;
        }
        assertTrue(exSBufLen);

        StrBuilder sbOther = new StrBuilder("other");
        sb.clear();
        sb.append(sbOther);
        sb.append((StrBuilder) null);
        sb.append(sbOther, 1, 3);
        assertEquals("otheroth", sb.toString());

        boolean exSbStart = false;
        try {
            sb.append(sbOther, -1, 1);
        } catch (StringIndexOutOfBoundsException e) {
            exSbStart = true;
        }
        assertTrue(exSbStart);

        boolean exSbLen = false;
        try {
            sb.append(sbOther, 0, 10);
        } catch (StringIndexOutOfBoundsException e) {
            exSbLen = true;
        }
        assertTrue(exSbLen);

        char[] chars = new char[]{'c', 'h', 'a', 'r', 's'};
        sb.clear();
        sb.append(chars);
        sb.append((char[]) null);
        sb.append(chars, 1, 3);
        assertEquals("charsphar", sb.toString());

        boolean exCharStart = false;
        try {
            sb.append(chars, -1, 1);
        } catch (StringIndexOutOfBoundsException e) {
            exCharStart = true;
        }
        assertTrue(exCharStart);

        boolean exCharLen = false;
        try {
            sb.append(chars, 0, 10);
        } catch (StringIndexOutOfBoundsException e) {
            exCharLen = true;
        }
        assertTrue(exCharLen);
    }

    @Test
    public void testAppendNewLineAndNull() throws Throwable {
        StrBuilder sb = new StrBuilder();
        sb.appendNull();
        assertEquals(0, sb.length());

        sb.setNullText("N");
        sb.appendNull();
        assertEquals("N", sb.toString());

        sb.clear();
        sb.appendNewLine();
        assertTrue(sb.length() > 0);

        sb.setNewLineText("\n");
        sb.clear();
        sb.appendNewLine();
        assertEquals("\n", sb.toString());
    }

    @Test
    public void testAppendWithSeparators() throws Throwable {
        StrBuilder sb = new StrBuilder();
        Object[] arr = new Object[]{"a", "b", "c"};
        sb.appendWithSeparators(arr, ",");
        assertEquals("a,b,c", sb.toString());

        sb.clear();
        sb.appendWithSeparators(arr, null);
        assertEquals("abc", sb.toString());

        sb.clear();
        sb.appendWithSeparators((Object[]) null, ",");
        assertEquals(0, sb.length());

        List<String> list = new ArrayList<String>();
        list.add("x");
        list.add("y");
        sb.clear();
        sb.appendWithSeparators(list, "-");
        assertEquals("x-y", sb.toString());

        sb.clear();
        sb.appendWithSeparators((Collection) null, "-");
        assertEquals(0, sb.length());

        sb.clear();
        sb.appendWithSeparators(list.iterator(), "|");
        assertEquals("x|y", sb.toString());

        sb.clear();
        sb.appendWithSeparators((java.util.Iterator) null, "|");
        assertEquals(0, sb.length());
    }

    @Test
    public void testAppendPaddingAndFixedLength() throws Throwable {
        StrBuilder sb = new StrBuilder();
        sb.appendPadding(3, 'x');
        assertEquals("xxx", sb.toString());

        sb.appendPadding(-1, 'x'); // negative does nothing

        sb.clear();
        sb.appendFixedWidthPadLeft("abc", 5, '0');
        assertEquals("00abc", sb.toString());

        sb.clear();
        sb.appendFixedWidthPadLeft("abcdef", 4, '0');
        assertEquals("cdef", sb.toString());

        sb.clear();
        sb.appendFixedWidthPadLeft(5, 3, '0');
        assertEquals("005", sb.toString());

        sb.clear();
        sb.appendFixedWidthPadRight("abc", 5, '0');
        assertEquals("abc00", sb.toString());

        sb.clear();
        sb.appendFixedWidthPadRight("abcdef", 4, '0');
        assertEquals("abcd", sb.toString());

        sb.clear();
        sb.appendFixedWidthPadRight(5, 3, '0');
        assertEquals("500", sb.toString());

        sb.clear();
        sb.setNullText("NULL");
        sb.appendFixedWidthPadLeft(null, 6, ' ');
        assertEquals("  NULL", sb.toString());
    }

    @Test
    public void testInsertMethods() throws Throwable {
        StrBuilder sb = new StrBuilder("ac");
        sb.insert(1, "b");
        assertEquals("abc", sb.toString());

        sb.insert(0, (Object) null);
        sb.insert(sb.length(), (String) null);
        sb.insert(1, new char[]{'x'});
        sb.insert(2, new char[]{'y', 'z', 'w'}, 1, 2);
        sb.insert(0, true);
        sb.insert(sb.length(), false);
        sb.insert(0, 'm');
        sb.insert(0, 10);
        sb.insert(0, 20L);
        sb.insert(0, 1.5f);
        sb.insert(0, 2.5d);

        boolean exOffset = false;
        try {
            sb.insert(1, new char[]{'a'}, -1, 1);
        } catch (StringIndexOutOfBoundsException e) {
            exOffset = true;
        }
        assertTrue(exOffset);

        boolean exLen = false;
        try {
            sb.insert(1, new char[]{'a'}, 0, 5);
        } catch (StringIndexOutOfBoundsException e) {
            exLen = true;
        }
        assertTrue(exLen);
    }

    @Test
    public void testDeleteAndReplaceMethods() throws Throwable {
        StrBuilder sb = new StrBuilder("hello world");
        sb.delete(5, 11);
        assertEquals("hello", sb.toString());

        sb = new StrBuilder("banana");
        sb.deleteAll('a');
        assertEquals("bnn", sb.toString());

        sb = new StrBuilder("banana");
        sb.deleteFirst('a');
        assertEquals("bnana", sb.toString());

        sb = new StrBuilder("foo bar foo");
        sb.deleteAll("foo");
        assertEquals(" bar ", sb.toString());
        sb.deleteAll((String) null);

        sb = new StrBuilder("foo bar foo");
        sb.deleteFirst("foo");
        assertEquals(" bar foo", sb.toString());
        sb.deleteFirst((String) null);

        StrMatcher matcher = StrMatcher.charMatcher('a');
        sb = new StrBuilder("banana");
        sb.deleteAll(matcher);
        assertEquals("bnn", sb.toString());

        sb = new StrBuilder("banana");
        sb.deleteFirst(matcher);
        assertEquals("bnana", sb.toString());

        sb = new StrBuilder("hello");
        sb.replace(1, 3, "ip");
        assertEquals("hiplo", sb.toString());
        sb.replace(0, 2, (String) null);

        sb = new StrBuilder("banana");
        sb.replaceAll('a', 'o');
        assertEquals("bonono", sb.toString());
        sb.replaceAll('o', 'o'); // same char

        sb = new StrBuilder("banana");
        sb.replaceFirst('a', 'o');
        assertEquals("bonana", sb.toString());
        sb.replaceFirst('o', 'o'); // same char

        sb = new StrBuilder("foo bar foo");
        sb.replaceAll("foo", "baz");
        assertEquals("baz bar baz", sb.toString());
        sb.replaceAll((String) null, "x");

        sb = new StrBuilder("foo bar foo");
        sb.replaceFirst("foo", "baz");
        assertEquals("baz bar foo", sb.toString());
        sb.replaceFirst((String) null, "x");

        sb = new StrBuilder("banana");
        sb.replaceAll(matcher, "X");
        assertEquals("bXnXnX", sb.toString());
        sb.replaceAll((StrMatcher) null, "X");
        sb.replaceAll(matcher, null);

        sb = new StrBuilder("banana");
        sb.replaceFirst(matcher, "X");
        assertEquals("bXnana", sb.toString());
    }

    @Test
    public void testReverseAndTrim() throws Throwable {
        StrBuilder sb = new StrBuilder("abc");
        sb.reverse();
        assertEquals("cba", sb.toString());

        StrBuilder sbEmpty = new StrBuilder();
        sbEmpty.reverse();
        assertTrue(sbEmpty.isEmpty());

        sb = new StrBuilder("   hello   ");
        sb.trim();
        assertEquals("hello", sb.toString());

        StrBuilder sbNoTrim = new StrBuilder("hello");
        sbNoTrim.trim();
        assertEquals("hello", sbNoTrim.toString());

        StrBuilder sbAllSpace = new StrBuilder("   ");
        sbAllSpace.trim();
        assertTrue(sbAllSpace.isEmpty());
    }

    @Test
    public void testStartsWithAndEndsWith() throws Throwable {
        StrBuilder sb = new StrBuilder("abcdef");
        assertTrue(sb.startsWith("abc"));
        assertFalse(sb.startsWith("def"));
        assertFalse(sb.startsWith("abcdefg"));
        assertFalse(sb.startsWith(null));
        assertTrue(sb.startsWith(""));

        assertTrue(sb.endsWith("def"));
        assertFalse(sb.endsWith("abc"));
        assertFalse(sb.endsWith("zabcdef"));
        assertFalse(sb.endsWith(null));
        assertTrue(sb.endsWith(""));
    }

    @Test
    public void testSubstringAndExtraction() throws Throwable {
        StrBuilder sb = new StrBuilder("abcdef");
        assertEquals("cdef", sb.substring(2));
        assertEquals("cd", sb.substring(2, 4));
        assertEquals("abcdef", sb.substring(0, 10)); // too large index handled

        assertEquals("abc", sb.leftString(3));
        assertEquals("", sb.leftString(-1));
        assertEquals("abcdef", sb.leftString(10));

        assertEquals("def", sb.rightString(3));
        assertEquals("", sb.rightString(-1));
        assertEquals("abcdef", sb.rightString(10));

        assertEquals("cde", sb.midString(2, 3));
        assertEquals("abcdef", sb.midString(-5, 10));
        assertEquals("", sb.midString(10, 3));
        assertEquals("", sb.midString(2, -1));
        assertEquals("ef", sb.midString(4, 10));
    }

    @Test
    public void testContainsAndIndexSearch() throws Throwable {
        StrBuilder sb = new StrBuilder("hello world");
        assertTrue(sb.contains('e'));
        assertFalse(sb.contains('z'));
        assertTrue(sb.contains("world"));
        assertFalse(sb.contains("java"));
        assertTrue(sb.contains(StrMatcher.charMatcher('w')));

        assertEquals(1, sb.indexOf('e'));
        assertEquals(4, sb.indexOf('e', 2));
        assertEquals(-1, sb.indexOf('e', 10));
        assertEquals(-1, sb.indexOf('z'));

        assertEquals(6, sb.indexOf("world"));
        assertEquals(6, sb.indexOf("world", 0));
        assertEquals(-1, sb.indexOf((String) null));
        assertEquals(0, sb.indexOf(""));
        assertEquals(-1, sb.indexOf("toolongstring"));
        assertEquals(1, sb.indexOf("e")); // len == 1 branch
        assertEquals(-1, sb.indexOf("xyz"));

        assertEquals(6, sb.indexOf(StrMatcher.charMatcher('w')));
        assertEquals(-1, sb.indexOf((StrMatcher) null));
        assertEquals(-1, sb.indexOf(StrMatcher.charMatcher('w'), 10));

        assertEquals(4, sb.lastIndexOf('o'));
        assertEquals(7, sb.lastIndexOf('o', 6));
        assertEquals(-1, sb.lastIndexOf('o', -1));
        assertEquals(-1, sb.lastIndexOf('z'));

        assertEquals(6, sb.lastIndexOf("world"));
        assertEquals(6, sb.lastIndexOf("world", 10));
        assertEquals(-1, sb.lastIndexOf((String) null));
        assertEquals(-1, sb.lastIndexOf("world", -1));
        assertEquals(4, sb.lastIndexOf("o")); // len == 1 branch
        assertEquals(10, sb.lastIndexOf(""));
        assertEquals(-1, sb.lastIndexOf("toolong"));
        assertEquals(-1, sb.lastIndexOf("xyz"));

        assertEquals(6, sb.lastIndexOf(StrMatcher.charMatcher('w')));
        assertEquals(-1, sb.lastIndexOf((StrMatcher) null));
        assertEquals(-1, sb.lastIndexOf(StrMatcher.charMatcher('w'), -1));
    }

    @Test
    public void testEqualsAndHashCode() throws Throwable {
        StrBuilder sb1 = new StrBuilder("abc");
        StrBuilder sb2 = new StrBuilder("abc");
        StrBuilder sb3 = new StrBuilder("ABC");
        StrBuilder sb4 = new StrBuilder("abcd");

        assertTrue(sb1.equals(sb1));
        assertTrue(sb1.equals(sb2));
        assertFalse(sb1.equals(sb4));
        assertFalse(sb1.equals("abc"));
        assertFalse(sb1.equals(null));

        assertTrue(sb1.equalsIgnoreCase(sb3));
        assertFalse(sb1.equalsIgnoreCase(sb4));
        assertFalse(sb1.equalsIgnoreCase(new StrBuilder("xyz")));

        assertEquals(sb1.hashCode(), sb2.hashCode());
        assertTrue(sb1.hashCode() != 0);
    }

    @Test
    public void testToStringAndStringBuffer() throws Throwable {
        StrBuilder sb = new StrBuilder("test");
        assertEquals("test", sb.toString());
        assertEquals("test", sb.toStringBuffer().toString());
    }

    @Test
    public void testTokenizerView() throws Throwable {
        StrBuilder sb = new StrBuilder("a b c");
        StrTokenizer tokenizer = sb.asTokenizer();
        assertNotNull(tokenizer);
        assertEquals("a b c", tokenizer.getContent());
        String[] tokens = tokenizer.getTokenArray();
        assertEquals(3, tokens.length);

        StrTokenizer tokenizerNull = sb.asTokenizer();
        assertEquals(3, tokenizerNull.tokenize(null, 0, 0).size());
    }

    @Test
    public void testReaderView() throws Throwable {
        StrBuilder sb = new StrBuilder("hello");
        Reader reader = sb.asReader();
        assertNotNull(reader);
        assertTrue(reader.markSupported());
        assertEquals('h', reader.read());

        char[] buf = new char[3];
        int readCount = reader.read(buf, 0, 3);
        assertEquals(3, readCount);

        assertEquals('o', reader.read());
        assertEquals(-1, reader.read());

        reader.reset(); // goes to mark (0)
        assertEquals('h', reader.read());

        reader.mark(1);
        long skipped = reader.skip(2);
        assertEquals(2, skipped);
        reader.skip(-5); // negative skip

        boolean exRead = false;
        try {
            reader.read(buf, -1, 2);
        } catch (IndexOutOfBoundsException e) {
            exRead = true;
        }
        assertTrue(exRead);

        assertEquals(0, reader.read(buf, 0, 0));

        reader.close();
    }

    @Test
    public void testWriterView() throws Throwable {
        StrBuilder sb = new StrBuilder();
        Writer writer = sb.asWriter();
        assertNotNull(writer);
        writer.write('a');
        writer.write(new char[]{'b', 'c'});
        writer.write(new char[]{'d', 'e', 'f'}, 1, 2);
        writer.write("gh");
        writer.write("ijk", 1, 2);
        writer.flush();
        writer.close();
        assertEquals("abcdeffghjk", sb.toString());
    }

}