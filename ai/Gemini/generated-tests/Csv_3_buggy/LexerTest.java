package org.apache.commons.csv;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

import java.io.IOException;
import java.io.StringReader;

import org.junit.Test;

public class LexerTest {

    private static class ConcreteLexer extends Lexer {
        ConcreteLexer(final CSVFormat format, final ExtendedBufferedReader in) {
            super(format, in);
        }

        @Override
        Token nextToken(final Token reusableToken) throws IOException {
            return reusableToken;
        }
    }

    @Test
    public void testReadEscapeStandardChars() throws Throwable {
        final CSVFormat format = CSVFormat.DEFAULT;
        final ExtendedBufferedReader reader = new ExtendedBufferedReader(new StringReader("rnbtf"));
        final ConcreteLexer lexer = new ConcreteLexer(format, reader);

        assertEquals(Constants.CR, lexer.readEscape());
        assertEquals(Constants.LF, lexer.readEscape());
        assertEquals(Constants.BACKSPACE, lexer.readEscape());
        assertEquals(Constants.TAB, lexer.readEscape());
        assertEquals(Constants.FF, lexer.readEscape());
    }

    @Test
    public void testReadEscapeOtherChars() throws Throwable {
        final CSVFormat format = CSVFormat.DEFAULT;
        final ExtendedBufferedReader reader = new ExtendedBufferedReader(new StringReader("ax"));
        final ConcreteLexer lexer = new ConcreteLexer(format, reader);

        assertEquals('a', lexer.readEscape());
        assertEquals('x', lexer.readEscape());
    }

    @Test
    public void testReadEscapeEof() throws Throwable {
        final CSVFormat format = CSVFormat.DEFAULT;
        final ExtendedBufferedReader reader = new ExtendedBufferedReader(new StringReader(""));
        final ConcreteLexer lexer = new ConcreteLexer(format, reader);

        try {
            lexer.readEscape();
            fail("Expected IOException for EOF in escape sequence");
        } catch (IOException e) {
            assertTrue(e.getMessage().contains("EOF"));
        }
    }

    @Test
    public void testTrimTrailingSpaces() throws Throwable {
        final CSVFormat format = CSVFormat.DEFAULT;
        final ExtendedBufferedReader reader = new ExtendedBufferedReader(new StringReader(""));
        final ConcreteLexer lexer = new ConcreteLexer(format, reader);

        StringBuilder sb = new StringBuilder("Hello   \t ");
        lexer.trimTrailingSpaces(sb);
        assertEquals("Hello", sb.toString());

        StringBuilder sb2 = new StringBuilder("NoSpaces");
        lexer.trimTrailingSpaces(sb2);
        assertEquals("NoSpaces", sb2.toString());

        StringBuilder sb3 = new StringBuilder("   ");
        lexer.trimTrailingSpaces(sb3);
        assertEquals("", sb3.toString());
    }

    @Test
    public void testReadEndOfLine() throws Throwable {
        final CSVFormat format = CSVFormat.DEFAULT;
        final ExtendedBufferedReader reader = new ExtendedBufferedReader(new StringReader("\n"));
        final ConcreteLexer lexer = new ConcreteLexer(format, reader);

        assertTrue(lexer.readEndOfLine(Constants.CR));
        assertTrue(lexer.readEndOfLine(Constants.LF));
        assertFalse(lexer.readEndOfLine('a'));
    }

    @Test
    public void testIsWhitespace() throws Throwable {
        final CSVFormat format = CSVFormat.DEFAULT.withDelimiter(',');
        final ExtendedBufferedReader reader = new ExtendedBufferedReader(new StringReader(""));
        final ConcreteLexer lexer = new ConcreteLexer(format, reader);

        assertTrue(lexer.isWhitespace(' '));
        assertTrue(lexer.isWhitespace('\t'));
        assertFalse(lexer.isWhitespace(','));
        assertFalse(lexer.isWhitespace('a'));
    }

    @Test
    public void testIsStartOfLine() throws Throwable {
        final CSVFormat format = CSVFormat.DEFAULT;
        final ExtendedBufferedReader reader = new ExtendedBufferedReader(new StringReader(""));
        final ConcreteLexer lexer = new ConcreteLexer(format, reader);

        assertTrue(lexer.isStartOfLine(Constants.LF));
        assertTrue(lexer.isStartOfLine(Constants.CR));
        assertTrue(lexer.isStartOfLine(Constants.UNDEFINED));
        assertFalse(lexer.isStartOfLine('a'));
    }

    @Test
    public void testIsEndOfFile() throws Throwable {
        final CSVFormat format = CSVFormat.DEFAULT;
        final ExtendedBufferedReader reader = new ExtendedBufferedReader(new StringReader(""));
        final ConcreteLexer lexer = new ConcreteLexer(format, reader);

        assertTrue(lexer.isEndOfFile(Constants.END_OF_STREAM));
        assertFalse(lexer.isEndOfFile('a'));
    }

    @Test
    public void testTokenCheckers() throws Throwable {
        final CSVFormat format = CSVFormat.DEFAULT.withDelimiter(',').withEscape('\\').withQuote('"').withCommentStart('#');
        final ExtendedBufferedReader reader = new ExtendedBufferedReader(new StringReader(""));
        final ConcreteLexer lexer = new ConcreteLexer(format, reader);

        assertTrue(lexer.isDelimiter(','));
        assertFalse(lexer.isDelimiter('a'));

        assertTrue(lexer.isEscape('\\'));
        assertFalse(lexer.isEscape('a'));

        assertTrue(lexer.isQuoteChar('"'));
        assertFalse(lexer.isQuoteChar('a'));

        assertTrue(lexer.isCommentStart('#'));
        assertFalse(lexer.isCommentStart('a'));
        
        assertEquals(0, lexer.getLineNumber());
    }
}