package org.apache.commons.lang.text;

import org.junit.Test;
import static org.junit.Assert.*;

import java.io.Reader;
import java.io.Writer;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Iterator;
import java.util.List;

public class StrBuilderTest {

    @Test
    public void testConstructors() throws Throwable {
        StrBuilder sb1 = new StrBuilder();
        assertEquals(32, sb1.capacity());
        assertEquals(0, sb1.length());
        assertTrue(sb1.isEmpty());

        StrBuilder sb2 = new StrBuilder(10);
        assertEquals(10, sb2.capacity());

        StrBuilder sb3 = new StrBuilder(-5);
        assertEquals(32, sb3.capacity());

        StrBuilder sb4 = new StrBuilder("hello");
        assertEquals("hello", sb4.toString());

        StrBuilder sb5 = new StrBuilder((String) null);
        assertEquals(32, sb5.capacity());
        assertEquals("", sb5.toString());
    }

    @Test
    public void testNewLineAndNullText() throws Throwable {
        StrBuilder sb = new StrBuilder();
        assertNull(sb.getNewLineText());
        sb.setNewLineText("\n");
        assertEquals("\n", sb.getNewLineText());

        assertNull(sb.getNullText());
        sb.setNullText("");
        assertNull(sb.getNullText());

        sb.setNullText("NULL");
        assertEquals("NULL", sb.getNullText());
        
        sb.appendNull();
        assertEquals("NULL", sb.toString());
    }

    @Test
    public void testCapacityAndLength() throws Throwable {
        StrBuilder sb = new StrBuilder("test");
        assertEquals(4, sb.length());
        
        sb.setLength(2);
        assertEquals(2, sb.toString());
        
        sb.setLength(6);
        assertEquals(6, sb.length());
        assertEquals('\0', sb.charAt(4));

        try {
            sb.setLength(-1);
            fail("Expected StringIndexOutOfBoundsException");
        } catch (StringIndexOutOfBoundsException e) {
            // expected
        }

        sb.ensureCapacity(50);
        assertTrue(sb.capacity() >= 50);

        sb.minimizeCapacity();
        assertEquals(sb.length(), sb.capacity());

        sb.clear();
        assertTrue(sb.isEmpty());
        assertEquals(0, sb.size());
    }

    @Test
    public void testCharAccessAndModification() throws Throwable {
        StrBuilder sb = new StrBuilder("abc");
        assertEquals('a', sb.charAt(0));
        assertEquals('b', sb.charAt(1));
        assertEquals('c', sb.charAt(2));

        try {
            sb.charAt(-1);
            fail();
        } catch (StringIndexOutOfBoundsException e) {}

        try {
            sb.charAt(3);
            fail();
        } catch (StringIndexOutOfBoundsException e) {}

        sb.setCharAt(1, 'z');
        assertEquals("azc", sb.toString());

        try {
            sb.setCharAt(-1, 'x');
            fail();
        } catch (StringIndexOutOfBoundsException e) {}

        try {
            sb.setCharAt(3, 'x');
            fail();
        } catch (StringIndexOutOfBoundsException e) {}

        sb.deleteCharAt(1);
        assertEquals("ac", sb.toString());

        try {
            sb.deleteCharAt(2);
            fail();
        } catch (StringIndexOutOfBoundsException e) {}
    }

    @Test
    public void testCopyingChars() throws Throwable {
        StrBuilder sb = new StrBuilder("abcdef");
        char[] arr1 = sb.toCharArray();
        assertArrayEquals(new char[]{'a','b','c','d','e','f'}, arr1);

        StrBuilder emptySb = new StrBuilder();
        assertArrayEquals(new char[0], emptySb.toCharArray());

        char[] arr2 = sb.toCharArray(1, 4);
        assertArrayEquals(new char[]{'b','c','d'}, arr2);

        char[] arr3 = sb.toCharArray(2, 10); // end > size
        assertArrayEquals(new char[]{'c','d','e','f'}, arr3);

        char[] dest = new char[10];
        char[] res1 = sb.getChars(dest);
        assertSame(dest, res1);

        char[] smallDest = new char[2];
        char[] res2 = sb.getChars(smallDest);
        assertNotSame(smallDest, res2);

        char[] target = new char[5];
        sb.getChars(1, 4, target, 1);
        assertEquals('\0', target[0]);
        assertEquals('b', target[1]);
        assertEquals('c', target[2]);
        assertEquals('d', target[3]);
        assertEquals('\0', target[4]);

        try {
            sb.getChars(-1, 2, target, 0);
            fail();
        } catch (StringIndexOutOfBoundsException e) {}

        try {
            sb.getChars(1, 10, target, 0);
            fail();
        } catch (StringIndexOutOfBoundsException e) {}

        try {
            sb.getChars(3, 1, target, 0);
            fail();
        } catch (StringIndexOutOfBoundsException e) {}
    }

    @Test
    public void testAppends() throws Throwable {
        StrBuilder sb = new StrBuilder();
        sb.append(true);
        sb.append(false);
        assertEquals("truefalse", sb.toString());

        sb.clear();
        sb.append('x');
        assertEquals("x", sb.toString());

        sb.append((Object) "abc");
        sb.append((Object) null); // no null text set
        assertEquals("xabc", sb.toString());

        sb.setNullText("null");
        sb.append((Object) null);
        assertEquals("xabcnull", sb.toString());

        sb.clear();
        sb.append("hello", 1, 3);
        assertEquals("ell", sb.toString());
        try {
            sb.append("hello", -1, 2);
            fail();
        } catch (StringIndexOutOfBoundsException e) {}
        try {
            sb.append("hello", 1, -1);
            fail();
        } catch (StringIndexOutOfBoundsException e) {}
        try {
            sb.append("hello", 3, 3);
            fail();
        } catch (StringIndexOutOfBoundsException e) {}
        sb.append((String) null);

        StringBuffer sbuf = new StringBuffer("buf");
        sb.clear().append(sbuf);
        sb.append((StringBuffer) null);
        assertEquals("buf", sb.toString());
        sb.append(sbuf, 1, 2);
        assertEquals("bufuf", sb.toString());
        try {
            sb.append(sbuf, -1, 1);
            fail();
        } catch (StringIndexOutOfBoundsException e) {}
        try {
            sb.append(sbuf, 1, -1);
            fail();
        } catch (StringIndexOutOfBoundsException e) {}
        try {
            sb.append(sbuf, 1, 10);
            fail();
        } catch (StringIndexOutOfBoundsException e) {}
        sb.append((StringBuffer) null, 0, 0);

        StrBuilder other = new StrBuilder("other");
        sb.clear().append(other);
        sb.append((StrBuilder) null);
        assertEquals("other", sb.toString());
        sb.append(other, 1, 3);
        assertEquals("otherThe", sb.toString());
        try {
            sb.append(other, -1, 1);
            fail();
        } catch (StringIndexOutOfBoundsException e) {}
        try {
            sb.append(other, 1, -1);
            fail();
        } catch (StringIndexOutOfBoundsException e) {}
        try {
            sb.append(other, 1, 10);
            fail();
        } catch (StringIndexOutOfBoundsException e) {}
        sb.append((StrBuilder) null, 0, 0);

        char[] chars = new char[]{'a', 'b', 'c'};
        sb.clear().append(chars);
        sb.append((char[]) null);
        assertEquals("abc", sb.toString());
        sb.append(chars, 1, 2);
        assertEquals("abclbc", sb.toString());
        try {
            sb.append(chars, -1, 1);
            fail();
        } catch (StringIndexOutOfBoundsException e) {}
        try {
            sb.append(chars, 1, -1);
            fail();
        } catch (StringIndexOutOfBoundsException e) {}
        try {
            sb.append(chars, 1, 10);
            fail();
        } catch (StringIndexOutOfBoundsException e) {}
        sb.append((char[]) null, 0, 0);

        sb.clear();
        sb.append(123);
        sb.append(123L);
        sb.append(12.3f);
        sb.append(12.3d);
        assertNotNull(sb.toString());

        sb.clear();
        sb.setNewLineText(null);
        sb.appendNewLine();
        assertNotNull(sb.toString());
    }

    @Test
    public void testAppendWithSeparators() throws Throwable {
        StrBuilder sb = new StrBuilder();
        Object[] array = new Object[]{"a", "b", "c"};
        sb.appendWithSeparators(array, ",");
        assertEquals("a,b,c", sb.toString());

        sb.clear();
        sb.appendWithSeparators(array, null);
        assertEquals("abc", sb.toString());

        sb.clear();
        sb.appendWithSeparators((Object[]) null, ",");
        assertTrue(sb.isEmpty());

        List<String> list = new ArrayList<String>();
        list.add("1");
        list.add("2");
        sb.clear();
        sb.appendWithSeparators(list, "-");
        assertEquals("1-2", sb.toString());
        
        sb.clear();
        sb.appendWithSeparators((Collection) null, "-");
        assertTrue(sb.isEmpty());

        sb.clear();
        sb.appendWithSeparators(list.iterator(), "|");
        assertEquals("1|2", sb.toString());

        sb.clear();
        sb.appendWithSeparators((Iterator) null, "|");
        assertTrue(sb.isEmpty());
    }

    @Test
    public void testPaddingAndFixedWidth() throws Throwable {
        StrBuilder sb = new StrBuilder();
        sb.appendPadding(3, '0');
        assertEquals("000", sb.toString());
        sb.appendPadding(-1, '0'); // no effect

        sb.clear();
        sb.appendFixedWidthPadLeft("abc", 5, ' ');
        assertEquals("  abc", sb.toString());

        sb.clear();
        sb.appendFixedWidthPadLeft("abcdef", 4, ' ');
        assertEquals("cdef", sb.toString());

        sb.clear();
        sb.appendFixedWidthPadLeft((Object) null, 4, 'x');
        assertEquals("xxxx", sb.toString());

        sb.clear();
        sb.appendFixedWidthPadLeft(5, 3, '0');
        assertEquals("005", sb.toString());

        sb.clear();
        sb.appendFixedWidthPadRight("abc", 5, ' ');
        assertEquals("abc  ", sb.toString());

        sb.clear();
        sb.appendFixedWidthPadRight("abcdef", 4, ' ');
        assertEquals("abcd", sb.toString());

        sb.clear();
        sb.appendFixedWidthPadRight(5, 3, '0');
        assertEquals("500", sb.toString());
        
        sb.clear();
        sb.appendFixedWidthPadLeft("a", 0, ' ');
        assertTrue(sb.isEmpty());
    }

    @Test
    public void testInserts() throws Throwable {
        StrBuilder sb = new StrBuilder("ac");
        sb.insert(1, "b");
        assertEquals("abc", sb.toString());

        sb.insert(3, (Object) null);
        sb.setNullText("X");
        sb.insert(3, (Object) null);
        assertEquals("abcX", sb.toString());

        sb.clear().append("ac");
        sb.insert(1, new char[]{'b'});
        assertEquals("abc", sb.toString());
        sb.insert(0, (char[]) null);
        
        sb.clear().append("ac");
        sb.insert(1, new char[]{'z', 'b', 'w'}, 1, 1);
        assertEquals("abc", sb.toString());
        
        try {
            sb.insert(1, new char[]{'a'}, -1, 1);
            fail();
        } catch (StringIndexOutOfBoundsException e) {}
        try {
            sb.insert(1, new char[]{'a'}, 0, 5);
            fail();
        } catch (StringIndexOutOfBoundsException e) {}
        sb.insert(1, (char[]) null, 0, 0);

        sb.clear().append("a");
        sb.insert(1, true);
        assertEquals("atrue", sb.toString());

        sb.clear().append("a");
        sb.insert(1, false);
        assertEquals("afalse", sb.toString());

        sb.clear().append("a");
        sb.insert(1, 'z');
        assertEquals("az", sb.toString());

        sb.insert(1, 10);
        sb.insert(1, 10L);
        sb.insert(1, 1.0f);
        sb.insert(1, 1.0d);
        assertNotNull(sb.toString());

        try {
            sb.insert(100, "test");
            fail();
        } catch (StringIndexOutOfBoundsException e) {}
    }

    @Test
    public void testDeletions() throws Throwable {
        StrBuilder sb = new StrBuilder("abcdef");
        sb.delete(1, 4);
        assertEquals("aef", sb.toString());

        sb = new StrBuilder("banana");
        sb.deleteAll('a');
        assertEquals("bnn", sb.toString());

        sb = new StrBuilder("banana");
        sb.deleteFirst('a');
        assertEquals("bnana", sb.toString());

        sb = new StrBuilder("abcabc");
        sb.deleteAll("ab");
        assertEquals("cc", sb.toString());
        sb.deleteAll((String) null);

        sb = new StrBuilder("abcabc");
        sb.deleteFirst("ab");
        assertEquals("cabc", sb.toString());
        sb.deleteFirst((String) null);

        StrMatcher matcher = StrMatcher.charMatcher('a');
        sb = new StrBuilder("banana");
        sb.deleteAll(matcher);
        assertEquals("bnn", sb.toString());

        sb = new StrBuilder("banana");
        sb.deleteFirst(matcher);
        assertEquals("bnana", sb.toString());
    }

    @Test
    public void testReplacements() throws Throwable {
        StrBuilder sb = new StrBuilder("abcdef");
        sb.replace(1, 4, "XYZ");
        assertEquals("aXYZef", sb.toString());

        sb = new StrBuilder("banana");
        sb.replaceAll('a', 'o');
        assertEquals("bonono", sb.toString());

        sb = new StrBuilder("banana");
        sb.replaceFirst('a', 'o');
        assertEquals("bonana", sb.toString());

        sb = new StrBuilder("abcabc");
        sb.replaceAll("ab", "XY");
        assertEquals("XYcXYc", sb.toString());
        sb.replaceAll((String) null, "XY");
        sb.replaceAll("a", (String) null);

        sb = new StrBuilder("abcabc");
        sb.replaceFirst("ab", "XY");
        assertEquals("XYcabc", sb.toString());
        sb.replaceFirst((String) null, "XY");
        sb.replaceFirst("a", (String) null);

        StrMatcher matcher = StrMatcher.charMatcher('a');
        sb = new StrBuilder("banana");
        sb.replaceAll(matcher, "X");
        assertEquals("bXnXnX", sb.toString());

        sb = new StrBuilder("banana");
        sb.replaceFirst(matcher, "X");
        assertEquals("bXnana", sb.toString());

        sb.clear();
        sb.replaceAll(null, "X");
        assertTrue(sb.isEmpty());
    }

    @Test
    public void testReverseAndTrim() throws Throwable {
        StrBuilder sb = new StrBuilder("abcde");
        sb.reverse();
        assertEquals("edcba", sb.toString());

        StrBuilder empty = new StrBuilder();
        empty.reverse();
        assertTrue(empty.isEmpty());

        sb = new StrBuilder("   abc   ");
        sb.trim();
        assertEquals("abc", sb.toString());

        empty.trim();
        assertTrue(empty.isEmpty());
    }

    @Test
    public void testStartsWithAndEndsWith() throws Throwable {
        StrBuilder sb = new StrBuilder("hello world");
        assertTrue(sb.startsWith("hello"));
        assertFalse(sb.startsWith("world"));
        assertFalse(sb.startsWith("hello world!"));
        assertFalse(sb.startsWith(null));
        assertTrue(sb.startsWith(""));

        assertTrue(sb.endsWith("world"));
        assertFalse(sb.endsWith("hello"));
        assertFalse(sb.endsWith("hello world!"));
        assertFalse(sb.endsWith(null));
        assertTrue(sb.endsWith(""));
        
        StrBuilder shortSb = new StrBuilder("hi");
        assertFalse(shortSb.startsWith("hello"));
        assertFalse(shortSb.endsWith("hello"));
    }

    @Test
    public void testSubstringsAndExtracts() throws Throwable {
        StrBuilder sb = new StrBuilder("abcdef");
        assertEquals("cdef", sb.substring(2));
        assertEquals("cd", sb.substring(2, 4));
        assertEquals("abcdef", sb.substring(0, 10)); // endIndex > size

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
        assertEquals("def", sb.midString(3, 10));
    }

    @Test
    public void testContainsAndIndexOf() throws Throwable {
        StrBuilder sb = new StrBuilder("hello world");
        assertTrue(sb.contains('e'));
        assertFalse(sb.contains('z'));

        assertTrue(sb.contains("world"));
        assertFalse(sb.contains("java"));

        assertTrue(sb.contains(StrMatcher.charMatcher('w')));
        assertFalse(sb.contains((StrMatcher) null));

        assertEquals(1, sb.indexOf('e'));
        assertEquals(-1, sb.indexOf('z'));
        assertEquals(4, sb.indexOf('o', 2));
        assertEquals(-1, sb.indexOf('o', 100));
        assertEquals(-1, sb.indexOf('o', -5));

        assertEquals(6, sb.indexOf("world"));
        assertEquals(-1, sb.indexOf((String) null));
        assertEquals(0, sb.indexOf(""));
        assertEquals(6, sb.indexOf("world", 0));
        assertEquals(-1, sb.indexOf("world", 10));
        assertEquals(-1, sb.indexOf("toolongstring", 0));
        assertEquals(1, sb.indexOf("e")); // strLen == 1

        assertEquals(0, sb.indexOf(StrMatcher.charMatcher('h')));
        assertEquals(-1, sb.indexOf((StrMatcher) null, 0));
        assertEquals(-1, sb.indexOf(StrMatcher.charMatcher('h'), 10));

        assertEquals(4, sb.lastIndexOf('o'));
        assertEquals(-1, sb.lastIndexOf('z'));
        assertEquals(4, sb.lastIndexOf('o', 10));
        assertEquals(4, sb.lastIndexOf('o', 4));
        assertEquals(-1, sb.lastIndexOf('o', -1));

        assertEquals(6, sb.lastIndexOf("world"));
        assertEquals(-1, sb.lastIndexOf((String) null));
        assertEquals(6, sb.lastIndexOf("world", 10));
        assertEquals(-1, sb.lastIndexOf("world", 2));
        assertEquals(11, sb.lastIndexOf(""));
        assertEquals(-1, sb.lastIndexOf("toolongstring"));
        assertEquals(4, sb.lastIndexOf("o")); // strLen == 1

        assertEquals(6, sb.lastIndexOf(StrMatcher.charMatcher('w')));
        assertEquals(-1, sb.lastIndexOf((StrMatcher) null));
        assertEquals(-1, sb.lastIndexOf(StrMatcher.charMatcher('w'), -1));
    }

    @Test
    public void testViewsAndEqualsAndHash() throws Throwable {
        StrBuilder sb = new StrBuilder("test");
        assertNotNull(sb.asTokenizer());
        
        Reader reader = sb.asReader();
        assertNotNull(reader);
        assertTrue(reader.markSupported());
        reader.mark(2);
        assertEquals('t', reader.read());
        assertEquals('e', reader.read());
        reader.reset();
        assertEquals('t', reader.read());
        assertEquals(2, reader.skip(2));
        assertFalse(reader.ready() == false);
        char[] buf = new char[2];
        assertEquals(1, reader.read(buf, 0, 2));
        reader.close();

        Writer writer = sb.asWriter();
        assertNotNull(writer);
        writer.write(97);
        writer.write(new char[]{'b'});
        writer.write(new char[]{'c'}, 0, 1);
        writer.write("d");
        writer.write("ef", 0, 2);
        writer.flush();
        writer.close();

        StrBuilder sb2 = new StrBuilder("test");
        StrBuilder sb3 = new StrBuilder("other");
        assertTrue(sb.equals(sb));
        assertTrue(sb.equals(sb2));
        assertTrue(sb.equalsIgnoreCase(new StrBuilder("TEST")));
        assertFalse(sb.equalsIgnoreCase(sb3));
        assertFalse(sb.equalsIgnoreCase(new StrBuilder("longertest")));
        assertFalse(sb.equals(sb3));
        assertFalse(sb.equals("notAStrBuilder"));
        assertFalse(sb.equals((StrBuilder) null));

        assertEquals(sb.hashCode(), sb2.hashCode());
        assertNotNull(sb.toStringBuffer());
    }

    @Test
    public void testTokenizerInnerClass() throws Throwable {
        StrBuilder sb = new StrBuilder("a b c");
        StrTokenizer tokenizer = (StrTokenizer) sb.asTokenizer();
        assertNotNull(tokenizer.getContent());
        assertNotNull(tokenizer.tokenize(null, 0, 0));
        assertNotNull(tokenizer.tokenize(new char[]{'x'}, 0, 1));
    }

    @Test
    public void testReaderInnerClassEdgeCases() throws Throwable {
        StrBuilder sb = new StrBuilder("abc");
        Reader r = sb.asReader();
        char[] b = new char[5];
        
        try {
            r.read(b, -1, 2);
            fail();
        } catch (IndexOutOfBoundsException e) {}
        try {
            r.read(b, 0, -1);
            fail();
        } catch (IndexOutOfBoundsException e) {}
        try {
            r.read(b, 6, 1);
            fail();
        } catch (IndexOutOfBoundsException e) {}
        try {
            r.read(b, 2, 4);
            fail();
        } catch (IndexOutOfBoundsException e) {}

        assertEquals(0, r.read(b, 0, 0));
        assertEquals(3, r.read(b, 0, 5));
        assertEquals(-1, r.read(b, 0, 1));

        r = sb.asReader();
        assertEquals(0, r.skip(-5));
        assertEquals(2, r.skip(10)); // skip beyond size caps to size - pos
    }

    @Test
    public void testWriterInnerClassMethods() throws Throwable {
        StrBuilder sb = new StrBuilder();
        Writer w = sb.asWriter();
        w.write('a');
        w.write(new char[]{'b', 'c'});
        w.write(new char[]{'d', 'e'}, 0, 1);
        w.write("f");
        w.write("gh", 0, 2);
        assertEquals("abcdefgh", sb.toString());
    }
}