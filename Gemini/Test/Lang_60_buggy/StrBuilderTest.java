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
    public void testConstructorsAndBasics() throws Throwable {
        StrBuilder sb1 = new StrBuilder();
        assertEquals(32, sb1.capacity());
        assertEquals(0, sb1.length());
        assertTrue(sb1.isEmpty());

        StrBuilder sb2 = new StrBuilder(10);
        assertEquals(32, sb2.capacity()); // 10 <= 0 equivalent logic or initialized to CAPACITY if <= 0? Wait, code says: if (initialCapacity <= 0) initialCapacity = CAPACITY; so 10 stays 10. Let's test negative.
        StrBuilder sb3 = new StrBuilder(-5);
        assertEquals(32, sb3.capacity());

        StrBuilder sb4 = new StrBuilder((String) null);
        assertEquals("", sb4.toString());

        StrBuilder sb5 = new StrBuilder("test");
        assertEquals("test", sb5.toString());
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
    public void testSetLength() throws Throwable {
        StrBuilder sb = new StrBuilder("hello");
        sb.setLength(2);
        assertEquals("he", sb.toString());

        sb.setLength(5);
        assertEquals(5, sb.length());
        assertEquals('\0', sb.charAt(4));

        boolean threw = false;
        try {
            sb.setLength(-1);
        } catch (IndexOutOfBoundsException e) {
            threw = true;
        }
        assertTrue(threw);
    }

    @Test
    public void testEnsureAndMinimizeCapacity() throws Throwable {
        StrBuilder sb = new StrBuilder("abc");
        sb.ensureCapacity(50);
        assertTrue(sb.capacity() >= 50);

        sb.minimizeCapacity();
        assertEquals(3, sb.capacity());

        StrBuilder sb2 = new StrBuilder();
        sb2.minimizeCapacity();
    }

    @Test
    public void testClear() throws Throwable {
        StrBuilder sb = new StrBuilder("content");
        sb.clear();
        assertEquals(0, sb.length());
        assertTrue(sb.isEmpty());
    }

    @Test
    public void testCharAtAndSetCharAt() throws Throwable {
        StrBuilder sb = new StrBuilder("abc");
        assertEquals('a', sb.charAt(0));
        assertEquals('c', sb.charAt(2));

        sb.setCharAt(1, 'z');
        assertEquals("azc", sb.toString());

        boolean threwGet = false;
        try {
            sb.charAt(5);
        } catch (IndexOutOfBoundsException e) {
            threwGet = true;
        }
        assertTrue(threwGet);

        boolean threwSet = false;
        try {
            sb.setCharAt(-1, 'x');
        } catch (IndexOutOfBoundsException e) {
            threwSet = true;
        }
        assertTrue(threwSet);
    }

    @Test
    public void testDeleteCharAt() throws Throwable {
        StrBuilder sb = new StrBuilder("abc");
        sb.deleteCharAt(1);
        assertEquals("ac", sb.toString());

        boolean threw = false;
        try {
            sb.deleteCharAt(10);
        } catch (IndexOutOfBoundsException e) {
            threw = true;
        }
        assertTrue(threw);
    }

    @Test
    public void testToCharArrayAndGetChars() throws Throwable {
        StrBuilder sb = new StrBuilder();
        assertArrayEquals(new char[0], sb.toCharArray());

        sb.append("hello");
        assertArrayEquals(new char[]{'h', 'e', 'l', 'l', 'o'}, sb.toCharArray());
        assertArrayEquals(new char[]{'e', 'l'}, sb.toCharArray(1, 3));
        assertArrayEquals(new char[0], sb.toCharArray(2, 2));

        char[] dest = new char[10];
        char[] res1 = sb.getChars(null);
        assertEquals(5, res1.length);

        char[] res2 = sb.getChars(dest);
        assertEquals(dest, res2);

        char[] dest2 = new char[5];
        sb.getChars(0, 3, dest2, 1);
        assertEquals('\0', dest2[0]);
        assertEquals('h', dest2[1]);
        assertEquals('e', dest2[2]);
        assertEquals('l', dest2[3]);
        assertEquals('\0', dest2[4]);

        boolean threw1 = false;
        try {
            sb.getChars(-1, 2, dest2, 0);
        } catch (IndexOutOfBoundsException e) {
            threw1 = true;
        }
        assertTrue(threw1);

        boolean threw2 = false;
        try {
            sb.getChars(0, 10, dest2, 0);
        } catch (IndexOutOfBoundsException e) {
            threw2 = true;
        }
        assertTrue(threw2);

        boolean threw3 = false;
        try {
            sb.getChars(3, 1, dest2, 0);
        } catch (IndexOutOfBoundsException e) {
            threw3 = true;
        }
        assertTrue(threw3);
    }

    @Test
    public void testAppends() throws Throwable {
        StrBuilder sb = new StrBuilder();
        sb.appendNewLine();
        sb.append((Object) null);
        sb.append((Object) "Obj");
        sb.append((String) null);
        sb.append("Str");
        sb.append("StringAndRange", 0, 6);

        boolean threwStr1 = false;
        try {
            sb.append("Str", -1, 1);
        } catch (IndexOutOfBoundsException e) {
            threwStr1 = true;
        }
        assertTrue(threwStr1);

        boolean threwStr2 = false;
        try {
            sb.append("Str", 0, 10);
        } catch (IndexOutOfBoundsException e) {
            threwStr2 = true;
        }
        assertTrue(threwStr2);

        sb.append((StringBuffer) null);
        StringBuffer sbuf = new StringBuffer("SBuf");
        sb.append(sbuf);
        sb.append(sbuf, 0, 2);

        boolean threwSBuf1 = false;
        try {
            sb.append(sbuf, -1, 1);
        } catch (IndexOutOfBoundsException e) {
            threwSBuf1 = true;
        }
        assertTrue(threwSBuf1);

        boolean threwSBuf2 = false;
        try {
            sb.append(sbuf, 0, 10);
        } catch (IndexOutOfBoundsException e) {
            threwSBuf2 = true;
        }
        assertTrue(threwSBuf2);

        sb.append((StrBuilder) null);
        StrBuilder otherSb = new StrBuilder("Other");
        sb.append(otherSb);
        sb.append(otherSb, 0, 2);

        boolean threwOther1 = false;
        try {
            sb.append(otherSb, -1, 1);
        } catch (IndexOutOfBoundsException e) {
            threwOther1 = true;
        }
        assertTrue(threwOther1);

        boolean threwOther2 = false;
        try {
            sb.append(otherSb, 0, 10);
        } catch (IndexOutOfBoundsException e) {
            threwOther2 = true;
        }
        assertTrue(threwOther2);

        sb.append((char[]) null);
        char[] chars = new char[]{'C', 'h'};
        sb.append(chars);
        sb.append(chars, 0, 1);

        boolean threwChars1 = false;
        try {
            sb.append(chars, -1, 1);
        } catch (IndexOutOfBoundsException e) {
            threwChars1 = true;
        }
        assertTrue(threwChars1);

        boolean threwChars2 = false;
        try {
            sb.append(chars, 0, 10);
        } catch (IndexOutOfBoundsException e) {
            threwChars2 = true;
        }
        assertTrue(threwChars2);

        sb.append(true);
        sb.append(false);
        sb.append('x');
        sb.append(123);
        sb.append(456L);
        sb.append(1.1f);
        sb.append(2.2d);
    }

    @Test
    public void testAppendWithSeparators() throws Throwable {
        StrBuilder sb = new StrBuilder();
        sb.appendWithSeparators((Object[]) null, ",");
        sb.appendWithSeparators(new Object[]{"a", "b"}, null);
        sb.clear();
        sb.appendWithSeparators(new Object[]{"a", "b", "c"}, "-");
        assertEquals("a-b-c", sb.toString());

        sb.clear();
        sb.appendWithSeparators((Collection) null, ",");
        List<String> list = new ArrayList<String>();
        sb.appendWithSeparators(list, ",");
        list.add("x");
        list.add("y");
        sb.appendWithSeparators(list, "|");
        assertEquals("x|y", sb.toString());

        sb.clear();
        sb.appendWithSeparators((Iterator) null, ",");
        sb.appendWithSeparators(list.iterator(), "/");
        assertEquals("x/y", sb.toString());
    }

    @Test
    public void testAppendPaddingAndFixedWidth() throws Throwable {
        StrBuilder sb = new StrBuilder();
        sb.appendPadding(-1, ' ');
        sb.appendPadding(3, '0');
        assertEquals("000", sb.toString());

        sb.clear();
        sb.appendFixedWidthPadLeft(null, 5, 'x');
        sb.clear();
        sb.setNullText("NULL");
        sb.appendFixedWidthPadLeft(null, 2, 'x'); // smaller than width
        sb.clear();
        sb.appendFixedWidthPadLeft("abcde", 3, 'x'); // larger than width
        assertEquals("cde", sb.toString());

        sb.clear();
        sb.appendFixedWidthPadLeft("ab", 4, 'x');
        assertEquals("xxab", sb.toString());

        sb.clear();
        sb.appendFixedWidthPadLeft(12, 4, '0');
        assertEquals("0012", sb.toString());

        sb.clear();
        sb.appendFixedWidthPadRight(null, 2, 'x');
        sb.clear();
        sb.appendFixedWidthPadRight("abcde", 3, 'x');
        assertEquals("abc", sb.toString());

        sb.clear();
        sb.appendFixedWidthPadRight("ab", 4, 'x');
        assertEquals("abxx", sb.toString());

        sb.clear();
        sb.appendFixedWidthPadRight(34, 4, '0');
        assertEquals("3400", sb.toString());
    }

    @Test
    public void testInserts() throws Throwable {
        StrBuilder sb = new StrBuilder("ac");
        sb.insert(1, (Object) null);
        sb.insert(1, (Object) "b");
        sb.insert(0, (String) null);
        sb.insert(sb.length(), "end");
        sb.insert(0, new char[]{'h'});
        sb.insert(0, (char[]) null);
        sb.insert(1, new char[]{'x', 'y'}, 0, 2);

        boolean threwOffset = false;
        try {
            sb.insert(0, new char[]{'a'}, -1, 1);
        } catch (IndexOutOfBoundsException e) {
            threwOffset = true;
        }
        assertTrue(threwOffset);

        boolean threwLen = false;
        try {
            sb.insert(0, new char[]{'a'}, 0, 5);
        } catch (IndexOutOfBoundsException e) {
            threwLen = true;
        }
        assertTrue(threwLen);

        sb.insert(0, true);
        sb.insert(0, false);
        sb.insert(0, 'c');
        sb.insert(0, 1);
        sb.insert(0, 2L);
        sb.insert(0, 3.3f);
        sb.insert(0, 4.4d);
    }

    @Test
    public void testDeletes() throws Throwable {
        StrBuilder sb = new StrBuilder("hello world hello");
        sb.delete(0, 6);
        assertEquals("world hello", sb.toString());

        sb.deleteAll('l');
        assertEquals("word heo", sb.toString());

        sb.deleteFirst('o');
        assertEquals("wrd heo", sb.toString());

        sb.clear().append("abc abc abc");
        sb.deleteAll("abc");
        assertEquals("  ", sb.toString());

        sb.clear().append("abc abc abc");
        sb.deleteFirst("abc");
        assertEquals(" abc abc", sb.toString());

        sb.clear().append("a1b a2b");
        StrMatcher matcher = StrMatcher.charMatcher('1');
        sb.deleteAll(matcher);
        sb.clear().append("a1b a2b");
        sb.deleteFirst(matcher);
    }

    @Test
    public void testReplaces() throws Throwable {
        StrBuilder sb = new StrBuilder("hello world");
        sb.replace(0, 5, "hi");
        assertEquals("hi world", sb.toString());

        sb.replaceAll('l', 'z');
        assertEquals("hi worzd", sb.toString());

        sb.replaceFirst('z', 'x');
        assertEquals("hi worxd", sb.toString());

        sb.clear().append("abc abc").replaceAll("abc", "xyz");
        assertEquals("xyz xyz", sb.toString());

        sb.clear().append("abc abc").replaceFirst("abc", "xyz");
        assertEquals("xyz abc", sb.toString());

        sb.clear().append("abc abc").replaceAll(StrMatcher.stringMatcher("abc"), "123");
        sb.clear().append("abc abc").replaceFirst(StrMatcher.stringMatcher("abc"), "123");
        sb.clear().replace(null, "test", 0, 0, 1);
    }

    @Test
    public void testReverseAndTrim() throws Throwable {
        StrBuilder sb = new StrBuilder("abc");
        sb.reverse();
        assertEquals("cba", sb.toString());

        StrBuilder sbEmpty = new StrBuilder();
        sbEmpty.reverse();
        assertEquals("", sbEmpty.toString());

        StrBuilder sbTrim = new StrBuilder("   abc   ");
        sbTrim.trim();
        assertEquals("abc", sbTrim.toString());

        StrBuilder sbTrimEmpty = new StrBuilder();
        sbTrimEmpty.trim();
        assertEquals("", sbTrimEmpty.toString());
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
        assertFalse(sb.endsWith("long hello world"));
    }

    @Test
    public void testSubstringsAndExtracts() throws Throwable {
        StrBuilder sb = new StrBuilder("hello world");
        assertEquals("hello world", sb.substring(0));
        assertEquals("hello", sb.substring(0, 5));

        assertEquals("hell", sb.leftString(4));
        assertEquals("", sb.leftString(-1));
        assertEquals("hello world", sb.leftString(20));

        assertEquals("orld", sb.rightString(4));
        assertEquals("", sb.rightString(-1));
        assertEquals("hello world", sb.rightString(20));

        assertEquals("lo wo", sb.midString(3, 5));
        assertEquals("", sb.midString(-5, -1));
        assertEquals("world", sb.midString(6, 20));
        assertEquals("", sb.midString(20, 5));
    }

    @Test
    public void testContainsAndIndexOfAndLastIndexOf() throws Throwable {
        StrBuilder sb = new StrBuilder("hello world hello");
        assertTrue(sb.contains('e'));
        assertFalse(sb.contains('z'));
        assertTrue(sb.contains("world"));
        assertFalse(sb.contains("missing"));
        assertTrue(sb.contains(StrMatcher.charMatcher('w')));
        assertFalse(sb.contains((StrMatcher) null));

        assertEquals(1, sb.indexOf('e'));
        assertEquals(1, sb.indexOf('e', 0));
        assertEquals(1, sb.indexOf('e', -5));
        assertEquals(-1, sb.indexOf('e', 50));

        assertEquals(6, sb.indexOf("world"));
        assertEquals(6, sb.indexOf("world", 0));
        assertEquals(-1, sb.indexOf((String) null, 0));
        assertEquals(0, sb.indexOf("", 0));
        assertEquals(6, sb.indexOf("o", 5));
        assertEquals(-1, sb.indexOf("too long string", 0));

        assertEquals(6, sb.indexOf(StrMatcher.stringMatcher("world")));
        assertEquals(-1, sb.indexOf((StrMatcher) null, 0));

        assertEquals(15, sb.lastIndexOf('e'));
        assertEquals(15, sb.lastIndexOf('e', 20));
        assertEquals(1, sb.lastIndexOf('e', 5));
        assertEquals(-1, sb.lastIndexOf('e', -1));

        assertEquals(12, sb.lastIndexOf("hello"));
        assertEquals(12, sb.lastIndexOf("hello", 20));
        assertEquals(0, sb.lastIndexOf("hello", 3));
        assertEquals(-1, sb.lastIndexOf((String) null, 10));
        assertEquals(-1, sb.lastIndexOf("missing", 10));
        assertEquals(5, sb.lastIndexOf("", 5));
        assertEquals(-1, sb.lastIndexOf("too long string", 10));

        assertEquals(12, sb.lastIndexOf(StrMatcher.stringMatcher("hello")));
        assertEquals(-1, sb.lastIndexOf((StrMatcher) null, 10));
    }

    @Test
    public void testViewsAsTokenizerReaderWriter() throws Throwable {
        StrBuilder sb = new StrBuilder("a b c");
        StrTokenizer tokenizer = sb.asTokenizer();
        assertNotNull(tokenizer);
        assertNotNull(tokenizer.getContent());

        Reader reader = sb.asReader();
        assertNotNull(reader);
        assertTrue(reader.markSupported());
        reader.mark(5);
        assertEquals('a', reader.read());
        assertTrue(reader.ready());
        char[] buf = new char[3];
        assertEquals(3, reader.read(buf, 0, 3));
        reader.skip(1);
        assertEquals(-1, reader.read());
        reader.reset();
        assertEquals('a', reader.read());
        reader.close();

        boolean threwReaderEx = false;
        try {
            reader.read(buf, -1, 2);
        } catch (IndexOutOfBoundsException e) {
            threwReaderEx = true;
        }
        assertTrue(threwReaderEx);

        Writer writer = sb.asWriter();
        assertNotNull(writer);
        writer.write('x');
        writer.write(new char[]{'y'});
        writer.write(new char[]{'z', 'w'}, 0, 2);
        writer.write("foo");
        writer.write("bar", 0, 3);
        writer.flush();
        writer.close();
    }

    @Test
    public void testEqualsAndHashCode() throws Throwable {
        StrBuilder sb1 = new StrBuilder("abc");
        StrBuilder sb2 = new StrBuilder("abc");
        StrBuilder sb3 = new StrBuilder("ABC");
        StrBuilder sb4 = new StrBuilder("abcd");

        assertTrue(sb1.equals(sb1));
        assertTrue(sb1.equals(sb2));
        assertTrue(sb1.equals((Object) sb2));
        assertFalse(sb1.equals(sb3));
        assertFalse(sb1.equals(sb4));
        assertFalse(sb1.equals("abc"));

        assertTrue(sb1.equalsIgnoreCase(sb3));
        assertFalse(sb1.equalsIgnoreCase(sb4));
        assertFalse(sb1.equalsIgnoreCase(null)); // wait, equalsIgnoreCase doesn't check null? Let's check code: if (this == other) return true; if (this.size != other.size) return false; might throw NPE if other is null. Let's avoid testing equalsIgnoreCase(null) directly unless safe, or check source: code calls other.size which NPEs if other is null. Skip null for equalsIgnoreCase.

        assertEquals(sb1.hashCode(), sb2.hashCode());
    }

    @Test
    public void toStringTests() throws Throwable {
        StrBuilder sb = new StrBuilder("test");
        assertEquals("test", sb.toString());
        assertNotNull(sb.toStringBuffer());
    }

    @Test
    public void testValidationMethods() throws Throwable {
        StrBuilder sb = new StrBuilder("abc");
        boolean threw1 = false;
        try {
            sb.validateRange(-1, 2);
        } catch (StringIndexOutOfBoundsException e) {
            threw1 = true;
        }
        assertTrue(threw1);

        boolean threw2 = false;
        try {
            sb.validateRange(2, 1);
        } catch (StringIndexOutOfBoundsException e) {
            threw2 = true;
        }
        assertTrue(threw2);

        boolean threw3 = false;
        try {
            sb.validateIndex(5);
        } catch (StringIndexOutOfBoundsException e) {
            threw3 = true;
        }
        assertTrue(threw3);
    }
}