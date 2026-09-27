package org.apache.commons.lang.text;

import org.junit.Test;
import static org.junit.Assert.*;

import java.io.Reader;
import java.io.Writer;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Iterator;
import java.util.List;

public class StrBuilderTest {

    @Test
    public void testConstructorsAndCapacity() throws Throwable {
        StrBuilder sb1 = new StrBuilder();
        assertEquals(0, sb1.length());
        assertTrue(sb1.isEmpty());
        assertTrue(sb1.capacity() >= 32);

        StrBuilder sb2 = new StrBuilder(10);
        assertEquals(10, sb2.capacity());

        StrBuilder sb3 = new StrBuilder(-5);
        assertTrue(sb3.capacity() >= 32);

        StrBuilder sb4 = new StrBuilder((String) null);
        assertEquals(0, sb4.length());

        StrBuilder sb5 = new StrBuilder("hello");
        assertEquals("hello", sb5.toString());
        assertEquals(5 + StrBuilder.CAPACITY, sb5.capacity());
    }

    @Test
    public void testGetSetNewLineAndNullText() throws Throwable {
        StrBuilder sb = new StrBuilder();
        assertNull(sb.getNewLineText());
        sb.setNewLineText("\n");
        assertEquals("\n", sb.getNewLineText());

        assertNull(sb.getNullText());
        sb.setNullText("NULL");
        assertEquals("NULL", sb.getNullText());

        sb.setNullText("");
        assertNull(sb.getNullText());
    }

    @Test
    public void testSetLengthAndEnsureCapacity() throws Throwable {
        StrBuilder sb = new StrBuilder("test");
        sb.setLength(2);
        assertEquals("te", sb.toString());

        sb.setLength(6);
        assertEquals(6, sb.length());
        assertEquals('\0', sb.charAt(4));

        boolean thrown = false;
        try {
            sb.setLength(-1);
        } catch (IndexOutOfBoundsException e) {
            thrown = true;
        }
        assertTrue(thrown);

        sb.ensureCapacity(100);
        assertTrue(sb.capacity() >= 100);

        sb.minimizeCapacity();
        assertEquals(sb.length(), sb.capacity());
    }

    @Test
    public void testClearAndCharAccess() throws Throwable {
        StrBuilder sb = new StrBuilder("abcdef");
        assertEquals('c', sb.charAt(2));

        sb.setCharAt(2, 'x');
        assertEquals("abxdef", sb.toString());

        boolean thrown = false;
        try {
            sb.charAt(-1);
        } catch (IndexOutOfBoundsException e) {
            thrown = true;
        }
        assertTrue(thrown);

        thrown = false;
        try {
            sb.setCharAt(10, 'y');
        } catch (IndexOutOfBoundsException e) {
            thrown = true;
        }
        assertTrue(thrown);

        sb.deleteCharAt(1);
        assertEquals("axdef", sb.toString());

        thrown = false;
        try {
            sb.deleteCharAt(10);
        } catch (IndexOutOfBoundsException e) {
            thrown = true;
        }
        assertTrue(thrown);

        sb.clear();
        assertTrue(sb.isEmpty());
        assertEquals(0, sb.length());
    }

    @Test
    public void testToCharArrayAndGetChars() throws Throwable {
        StrBuilder sb = new StrBuilder();
        char[] emptyArr = sb.toCharArray();
        assertNotNull(emptyArr);
        assertEquals(0, emptyArr.length);

        sb.append("hello");
        char[] arr1 = sb.toCharArray();
        assertArrayEquals("hello".toCharArray(), arr1);

        char[] arr2 = sb.toCharArray(1, 4);
        assertArrayEquals("ell".toCharArray(), arr2);

        char[] dest = new char[3];
        char[] res1 = sb.getChars(dest);
        assertSame(dest, res1);

        char[] smallDest = new char[2];
        char[] res2 = sb.getChars(smallDest);
        assertNotSame(smallDest, res2);
        assertArrayEquals("hello".toCharArray(), res2);

        char[] target = new char[10];
        sb.getChars(1, 4, target, 2);
        assertEquals('\0', target[0]);
        assertEquals('e', target[2]);
        assertEquals('l', target[3]);
        assertEquals('l', target[4]);

        boolean thrown = false;
        try {
            sb.getChars(-1, 2, target, 0);
        } catch (IndexOutOfBoundsException e) {
            thrown = true;
        }
        assertTrue(thrown);

        thrown = false;
        try {
            sb.getChars(1, 10, target, 0);
        } catch (IndexOutOfBoundsException e) {
            thrown = true;
        }
        assertTrue(thrown);

        thrown = false;
        try {
            sb.getChars(3, 1, target, 0);
        } catch (IndexOutOfBoundsException e) {
            thrown = true;
        }
        assertTrue(thrown);
    }

    @Test
    public void testAppendVariousTypes() throws Throwable {
        StrBuilder sb = new StrBuilder();
        sb.setNullText("NULL");

        sb.append((Object) null);
        sb.append((Object) "Obj");
        sb.append((String) null);
        sb.append("Str");
        sb.append("SubstringTest", 3, 6);
        sb.append((StringBuffer) null);
        sb.append(new StringBuffer("Buf"));
        sb.append(new StringBuffer("BufferSub"), 3, 3);
        sb.append((StrBuilder) null);
        sb.append(new StrBuilder("SB"));
        sb.append(new StrBuilder("SBSub"), 2, 3);
        sb.append((char[]) null);
        sb.append(new char[] {'a', 'b'});
        sb.append(new char[] {'c', 'd', 'e'}, 1, 2);
        sb.append(true);
        sb.append(false);
        sb.append('Z');
        sb.append(123);
        sb.append(456L);
        sb.append(1.5f);
        sb.append(2.5d);

        assertEquals("NULLObjNULLStrringBufferSBSBSubabdeftruefalseZ1234561.52.5", sb.toString());
    }

    @Test
    public void testAppendEdgeCases() throws Throwable {
        StrBuilder sb = new StrBuilder();
        
        boolean thrown = false;
        try {
            sb.append("test", -1, 2);
        } catch (IndexOutOfBoundsException e) {
            thrown = true;
        }
        assertTrue(thrown);

        thrown = false;
        try {
            sb.append("test", 1, 10);
        } catch (IndexOutOfBoundsException e) {
            thrown = true;
        }
        assertTrue(thrown);

        thrown = false;
        try {
            sb.append(new StringBuffer("test"), -1, 2);
        } catch (IndexOutOfBoundsException e) {
            thrown = true;
        }
        assertTrue(thrown);

        thrown = false;
        try {
            sb.append(new StrBuilder("test"), -1, 2);
        } catch (IndexOutOfBoundsException e) {
            thrown = true;
        }
        assertTrue(thrown);

        thrown = false;
        try {
            sb.append(new char[] {'a'}, -1, 1);
        } catch (IndexOutOfBoundsException e) {
            thrown = true;
        }
        assertTrue(thrown);

        thrown = false;
        try {
            sb.append(new char[] {'a'}, 0, 5);
        } catch (IndexOutOfBoundsException e) {
            thrown = true;
        }
        assertTrue(thrown);
    }

    @Test
    public void testAppendln() throws Throwable {
        StrBuilder sb = new StrBuilder();
        sb.setNewLineText("\n");

        sb.appendln("line1");
        sb.appendln((Object) "line2");
        sb.appendln("line3", 1, 4);
        sb.appendln(new StringBuffer("line4"));
        sb.appendln(new StringBuffer("line5"), 1, 4);
        sb.appendln(new StrBuilder("line6"));
        sb.appendln(new StrBuilder("line7"), 1, 4);
        sb.appendln(new char[] {'a', 'b'});
        sb.appendln(new char[] {'c', 'd'}, 0, 1);
        sb.appendln(true);
        sb.appendln('X');
        sb.appendln(99);
        sb.appendln(999L);
        sb.appendln(3.14f);
        sb.appendln(2.718d);

        assertNotNull(sb.toString());
    }

    @Test
    public void testAppendAllAndSeparators() throws Throwable {
        StrBuilder sb = new StrBuilder();
        sb.appendAll((Object[]) null);
        sb.appendAll(new Object[] {"A", "B"});
        sb.appendAll((Collection) null);
        
        List<String> list = new ArrayList<String>();
        list.add("C");
        list.add("D");
        sb.appendAll(list);

        sb.appendAll((Iterator) null);
        sb.appendAll(list.iterator());

        assertEquals("ABCDCD", sb.toString());

        StrBuilder sb2 = new StrBuilder();
        sb2.appendWithSeparators((Object[]) null, ",");
        sb2.appendWithSeparators(new Object[] {"1", "2"}, null);
        sb2.append("-");
        sb2.appendWithSeparators(new Object[] {"3", "4"}, ",");

        StrBuilder sb3 = new StrBuilder();
        sb3.appendWithSeparators((Collection) null, ",");
        sb3.appendWithSeparators(list, null);
        sb3.append("-");
        sb3.appendWithSeparators(list.iterator(), "|");

        StrBuilder sb4 = new StrBuilder();
        sb4.appendSeparator("sep");
        sb4.append("first");
        sb4.appendSeparator("sep");
        sb4.append("second");
        sb4.appendSeparator('c');
        sb4.appendSeparator("sep", 0);
        sb4.appendSeparator("sep", 1);
        sb4.appendSeparator('d', 1);

        assertNotNull(sb4.toString());
    }

    @Test
    public void testAppendPaddingAndFixedWidth() throws Throwable {
        StrBuilder sb = new StrBuilder();
        sb.appendPadding(-1, 'x');
        sb.appendPadding(3, 'x');
        assertEquals("xxx", sb.toString());

        sb.clear();
        sb.appendFixedWidthPadLeft("abc", 5, '0');
        assertEquals("00abc", sb.toString());

        sb.clear();
        sb.appendFixedWidthPadLeft("abcdef", 4, '0');
        assertEquals("cdef", sb.toString());

        sb.clear();
        sb.appendFixedWidthPadLeft(7, 3, '0');
        assertEquals("007", sb.toString());

        sb.clear();
        sb.appendFixedWidthPadRight("abc", 5, '0');
        assertEquals("abc00", sb.toString());

        sb.clear();
        sb.appendFixedWidthPadRight("abcdef", 4, '0');
        assertEquals("abcd", sb.toString());

        sb.clear();
        sb.appendFixedWidthPadRight(7, 3, '0');
        assertEquals("700", sb.toString());
        
        sb.clear();
        sb.appendFixedWidthPadLeft((Object) null, 3, 'x');
        sb.appendFixedWidthPadRight((Object) null, 3, 'x');
        assertNotNull(sb.toString());
    }

    @Test
    public void testInsertMethods() throws Throwable {
        StrBuilder sb = new StrBuilder("12345");
        sb.insert(2, (Object) "XYZ");
        assertEquals("12XYZ345", sb.toString());

        sb.clear().append("12345");
        sb.insert(2, (String) null);
        sb.insert(2, "ABC");
        sb.insert(0, (char[]) null);
        sb.insert(1, new char[] {'a', 'b'});
        sb.insert(1, new char[] {'x', 'y', 'z'}, 1, 2);

        sb.insert(2, true);
        sb.insert(2, false);
        sb.insert(2, 'A');
        sb.insert(2, 10);
        sb.insert(2, 20L);
        sb.insert(2, 30.0f);
        sb.insert(2, 40.0d);

        boolean thrown = false;
        try {
            sb.insert(2, new char[] {'a'}, -1, 1);
        } catch (IndexOutOfBoundsException e) {
            thrown = true;
        }
        assertTrue(thrown);

        thrown = false;
        try {
            sb.insert(2, new char[] {'a'}, 0, 5);
        } catch (IndexOutOfBoundsException e) {
            thrown = true;
        }
        assertTrue(thrown);
    }

    @Test
    public void testDeleteAndReplaceMethods() throws Throwable {
        StrBuilder sb = new StrBuilder("abcdefabcdef");
        sb.deleteAll('a');
        assertEquals("bcdefbcdef", sb.toString());

        sb.deleteFirst('b');
        assertEquals("cdefbcdef", sb.toString());

        sb.clear().append("abc xyz abc xyz");
        sb.deleteAll("xyz");
        assertEquals("abc   abc ", sb.toString());

        sb.clear().append("abc xyz abc xyz");
        sb.deleteFirst("xyz");
        assertEquals("abc  abc xyz", sb.toString());

        sb.clear().append("hello world");
        sb.delete(2, 5);
        assertEquals("he world", sb.toString());

        sb.clear().append("hello world");
        sb.replaceAll('l', 'x');
        assertEquals("hexxo worxd", sb.toString());

        sb.replaceFirst('x', 'y');
        assertEquals("heyxo worxd", sb.toString());

        sb.clear().append("foo bar foo bar");
        sb.replaceAll("foo", "baz");
        assertEquals("baz bar baz bar", sb.toString());

        sb.replaceFirst("baz", "qux");
        assertEquals("qux bar baz bar", sb.toString());

        sb.replaceAll((StrMatcher) null, "test");
        sb.replaceFirst((StrMatcher) null, "test");
        sb.replace(0, 2, "test");

        assertNotNull(sb.toString());
    }

    @Test
    public void testReverseAndTrim() throws Throwable {
        StrBuilder sb = new StrBuilder();
        sb.reverse(); // size 0

        sb.append("abcde");
        sb.reverse();
        assertEquals("edcba", sb.toString());

        sb.clear().append("   abc   ");
        sb.trim();
        assertEquals("abc", sb.toString());

        sb.clear().append("      ");
        sb.trim();
        assertEquals("", sb.toString());

        sb.clear().append("abc");
        sb.trim();
        assertEquals("abc", sb.toString());
    }

    @Test
    public void testStartsWithAndEndsWith() throws Throwable {
        StrBuilder sb = new StrBuilder("hello world");
        assertFalse(sb.startsWith((String) null));
        assertTrue(sb.startsWith(""));
        assertTrue(sb.startsWith("hello"));
        assertFalse(sb.startsWith("world"));
        assertFalse(sb.startsWith("hello world extra"));

        assertFalse(sb.endsWith((String) null));
        assertTrue(sb.endsWith(""));
        assertTrue(sb.endsWith("world"));
        assertFalse(sb.endsWith("hello"));
        assertFalse(sb.endsWith("extra hello world"));
    }

    @Test
    public void testSubstringAndExtraction() throws Throwable {
        StrBuilder sb = new StrBuilder("0123456789");
        assertEquals("23456789", sb.substring(2));
        assertEquals("2345", sb.substring(2, 6));
        assertEquals("0123456789", sb.substring(2, 20)); // endIndex > size

        assertEquals("", sb.leftString(-1));
        assertEquals("0123456789", sb.leftString(15));
        assertEquals("012", sb.leftString(3));

        assertEquals("", sb.rightString(-1));
        assertEquals("0123456789", sb.rightString(15));
        assertEquals("789", sb.rightString(3));

        assertEquals("", sb.midString(-5, 3));
        assertEquals("", sb.midString(15, 3));
        assertEquals("", sb.midString(2, -1));
        assertEquals("2345", sb.midString(2, 4));
        assertEquals("89", sb.midString(8, 5));
    }

    @Test
    public void testContainsAndIndexOfAndLastIndexOf() throws Throwable {
        StrBuilder sb = new StrBuilder("abracadabra");
        assertTrue(sb.contains('r'));
        assertFalse(sb.contains('z'));
        assertTrue(sb.contains("cad"));
        assertFalse(sb.contains("xyz"));
        assertTrue(sb.contains((StrMatcher) null)); // returns false, but safe

        assertEquals(2, sb.indexOf('r'));
        assertEquals(5, sb.indexOf('r', 3));
        assertEquals(-1, sb.indexOf('r', 10));
        assertEquals(-1, sb.indexOf('z', -5));

        assertEquals(3, sb.indexOf("aca"));
        assertEquals(-1, sb.indexOf((String) null));
        assertEquals(2, sb.indexOf("aca", 1));
        assertEquals(2, sb.indexOf("r", -5));
        assertEquals(2, sb.indexOf(""));
        assertEquals(-1, sb.indexOf("toolongstring"));

        assertEquals(7, sb.lastIndexOf('a'));
        assertEquals(0, sb.lastIndexOf('a', 1));
        assertEquals(-1, sb.lastIndexOf('a', -1));
        assertEquals(7, sb.lastIndexOf('a', 20));

        assertEquals(7, sb.lastIndexOf("abr"));
        assertEquals(-1, sb.lastIndexOf((String) null));
        assertEquals(7, sb.lastIndexOf("abr", 10));
        assertEquals(10, sb.lastIndexOf(""));
        assertEquals(-1, sb.lastIndexOf("toolongstring"));
        assertEquals(2, sb.lastIndexOf("r"));

        assertEquals(-1, sb.indexOf((StrMatcher) null));
        assertEquals(-1, sb.indexOf((StrMatcher) null, 0));
        assertEquals(-1, sb.lastIndexOf((StrMatcher) null));
        assertEquals(-1, sb.lastIndexOf((StrMatcher) null, 5));
    }

    @Test
    public void testTokenizerReaderAndWriter() throws Throwable {
        StrBuilder sb = new StrBuilder("a b c");
        StrTokenizer tokenizer = sb.asTokenizer();
        assertNotNull(tokenizer);
        assertNotNull(tokenizer.getContent());

        StrBuilderTokenizer sbt = sb.new StrBuilderTokenizer();
        assertNotNull(sbt.tokenize(null, 0, 0));
        assertNotNull(sbt.tokenize(new char[] {'x'}, 0, 1));
        assertNotNull(sbt.getContent());

        Reader reader = sb.asReader();
        assertTrue(reader.markSupported());
        assertEquals('a', reader.read());
        char[] buf = new char[3];
        assertEquals(3, reader.read(buf, 0, 3));
        
        boolean thrown = false;
        try {
            reader.read(buf, -1, 1);
        } catch (IndexOutOfBoundsException e) {
            thrown = true;
        }
        assertTrue(thrown);

        assertEquals(1, reader.skip(5));
        assertFalse(reader.ready());
        reader.mark(10);
        reader.reset();
        reader.close();

        Writer writer = sb.asWriter();
        writer.write(97);
        writer.write(new char[] {'z'});
        writer.write(new char[] {'z'}, 0, 1);
        writer.write("test");
        writer.write("test", 0, 4);
        writer.flush();
        writer.close();
    }

    @Test
    public void testEqualsAndHashCode() throws Throwable {
        StrBuilder sb1 = new StrBuilder("test");
        StrBuilder sb2 = new StrBuilder("test");
        StrBuilder sb3 = new StrBuilder("diff");
        StrBuilder sb4 = new StrBuilder("TEST");

        assertTrue(sb1.equals(sb1));
        assertTrue(sb1.equals(sb2));
        assertTrue(sb1.equals((Object) sb2));
        assertFalse(sb1.equals(sb3));
        assertFalse(sb1.equals(sb4));
        assertFalse(sb1.equals("test"));

        assertTrue(sb1.equalsIgnoreCase(sb4));
        assertFalse(sb1.equalsIgnoreCase(sb3));
        assertTrue(sb1.equalsIgnoreCase(sb1));

        assertEquals(sb1.hashCode(), sb2.hashCode());
        assertNotNull(sb1.toStringBuffer());
    }
}