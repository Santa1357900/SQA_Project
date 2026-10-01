package org.apache.commons.csv;

import static org.apache.commons.csv.Constants.BACKSPACE;
import static org.apache.commons.csv.Constants.CR;
import static org.apache.commons.csv.Constants.END_OF_STREAM;
import static org.apache.commons.csv.Constants.FF;
import static org.apache.commons.csv.Constants.LF;
import static org.apache.commons.csv.Constants.TAB;
import static org.apache.commons.csv.Constants.UNDEFINED;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

import java.io.IOException;
import java.io.StringReader;

import org.junit.Test;

public class LexerClaudeTest {

    private Lexer createLexer(final String input, final CSVFormat format) {
        final ExtendedBufferedReader reader = new ExtendedBufferedReader(new StringReader(input));
        return new Lexer(format, reader) {
            Token nextToken(final Token reusableToken) throws IOException {
                return reusableToken;
            }
        };
    }

    private Lexer createLexer(final String input) {
        return createLexer(input, CSVFormat.DEFAULT);
    }

    // getLineNumber(): before any read the counter must start at zero
    @Test
    public void testGetLineNumber_beforeAnyRead_returnsZero() throws Throwable {
        final Lexer lexer = createLexer("a\nb");
        assertEquals(0, lexer.getLineNumber());
    }

    // getLineNumber(): reading past a line terminator must increase the counter
    @Test
    public void testGetLineNumber_afterReadingNewline_increases() throws Throwable {
        final Lexer lexer = createLexer("a\nb");
        lexer.in.read();
        lexer.in.read();
        lexer.in.read();
        assertTrue(lexer.getLineNumber() > 0);
    }

    // readEscape(): case 'r' branch
    @Test
    public void testReadEscape_lowerCaseR_returnsCR() throws Throwable {
        final Lexer lexer = createLexer("r");
        assertEquals(CR, lexer.readEscape());
    }

    // readEscape(): case 'n' branch
    @Test
    public void testReadEscape_lowerCaseN_returnsLF() throws Throwable {
        final Lexer lexer = createLexer("n");
        assertEquals(LF, lexer.readEscape());
    }

    // readEscape(): case 't' branch
    @Test
    public void testReadEscape_lowerCaseT_returnsTab() throws Throwable {
        final Lexer lexer = createLexer("t");
        assertEquals(TAB, lexer.readEscape());
    }

    // readEscape(): case 'b' branch
    @Test
    public void testReadEscape_lowerCaseB_returnsBackspace() throws Throwable {
        final Lexer lexer = createLexer("b");
        assertEquals(BACKSPACE, lexer.readEscape());
    }

    // readEscape(): case 'f' branch
    @Test
    public void testReadEscape_lowerCaseF_returnsFF() throws Throwable {
        final Lexer lexer = createLexer("f");
        assertEquals(FF, lexer.readEscape());
    }

    // readEscape(): literal CR fall-through branch returns the char itself
    @Test
    public void testReadEscape_literalCR_returnsCR() throws Throwable {
        final Lexer lexer = createLexer("\r");
        assertEquals(CR, lexer.readEscape());
    }

    // readEscape(): literal LF fall-through branch returns the char itself
    @Test
    public void testReadEscape_literalLF_returnsLF() throws Throwable {
        final Lexer lexer = createLexer("\n");
        assertEquals(LF, lexer.readEscape());
    }

    // readEscape(): default branch, an arbitrary meta character is returned unchanged
    @Test
    public void testReadEscape_metaChar_returnsSameChar() throws Throwable {
        final Lexer lexer = createLexer("x");
        assertEquals((int) 'x', lexer.readEscape());
    }

    // readEscape(): end-of-stream after the escape char must throw IOException per contract
    @Test
    public void testReadEscape_endOfStream_throwsIOException() throws Throwable {
        final Lexer lexer = createLexer("");
        try {
            lexer.readEscape();
            fail("expected IOException");
        } catch (IOException expected) {
            assertTrue(expected.getMessage().contains("escape"));
        }
    }

    // trimTrailingSpaces(): no trailing whitespace leaves buffer unchanged
    @Test
    public void testTrimTrailingSpaces_noTrailingWhitespace_unchanged() throws Throwable {
        final Lexer lexer = createLexer("");
        final StringBuilder sb = new StringBuilder("abc");
        lexer.trimTrailingSpaces(sb);
        assertEquals("abc", sb.toString());
    }

    // trimTrailingSpaces(): trailing spaces are removed, content preserved
    @Test
    public void testTrimTrailingSpaces_trailingSpaces_trimmed() throws Throwable {
        final Lexer lexer = createLexer("");
        final StringBuilder sb = new StringBuilder("abc   ");
        lexer.trimTrailingSpaces(sb);
        assertEquals("abc", sb.toString());
    }

    // trimTrailingSpaces(): buffer made entirely of whitespace becomes empty
    @Test
    public void testTrimTrailingSpaces_allWhitespace_empty() throws Throwable {
        final Lexer lexer = createLexer("");
        final StringBuilder sb = new StringBuilder("   ");
        lexer.trimTrailingSpaces(sb);
        assertEquals(0, sb.length());
    }

    // trimTrailingSpaces(): already-empty buffer stays empty (length==0 loop guard)
    @Test
    public void testTrimTrailingSpaces_emptyBuffer_staysEmpty() throws Throwable {
        final Lexer lexer = createLexer("");
        final StringBuilder sb = new StringBuilder("");
        lexer.trimTrailingSpaces(sb);
        assertEquals(0, sb.length());
    }

    // readEndOfLine(): CR followed by LF is greedily consumed, returns true
    @Test
    public void testReadEndOfLine_crlf_consumesLFAndReturnsTrue() throws Throwable {
        final Lexer lexer = createLexer("\n");
        final boolean result = lexer.readEndOfLine(CR);
        assertTrue(result);
        assertEquals(END_OF_STREAM, lexer.in.read());
    }

    // readEndOfLine(): CR not followed by LF returns true without consuming next char
    @Test
    public void testReadEndOfLine_crAlone_returnsTrueWithoutConsumingNext() throws Throwable {
        final Lexer lexer = createLexer("X");
        final boolean result = lexer.readEndOfLine(CR);
        assertTrue(result);
        assertEquals((int) 'X', lexer.in.read());
    }

    // readEndOfLine(): LF alone returns true
    @Test
    public void testReadEndOfLine_lf_returnsTrue() throws Throwable {
        final Lexer lexer = createLexer("");
        assertTrue(lexer.readEndOfLine(LF));
    }

    // readEndOfLine(): an ordinary character is not an end of line
    @Test
    public void testReadEndOfLine_otherChar_returnsFalse() throws Throwable {
        final Lexer lexer = createLexer("");
        assertFalse(lexer.readEndOfLine('x'));
    }

    // isWhitespace(): plain space with default delimiter is whitespace
    @Test
    public void testIsWhitespace_spaceChar_returnsTrue() throws Throwable {
        final Lexer lexer = createLexer("");
        assertTrue(lexer.isWhitespace(' '));
    }

    // isWhitespace(): ordinary letter is not whitespace
    @Test
    public void testIsWhitespace_nonWhitespaceChar_returnsFalse() throws Throwable {
        final Lexer lexer = createLexer("");
        assertFalse(lexer.isWhitespace('a'));
    }

    // isWhitespace(): when the delimiter itself is a whitespace char, it must NOT count as whitespace
    @Test
    public void testIsWhitespace_delimiterThatIsWhitespace_returnsFalse() throws Throwable {
        final Lexer lexer = createLexer("", CSVFormat.DEFAULT.withDelimiter('\t'));
        assertFalse(lexer.isWhitespace('\t'));
    }

    // isStartOfLine(): LF is a start-of-line marker
    @Test
    public void testIsStartOfLine_lf_returnsTrue() throws Throwable {
        final Lexer lexer = createLexer("");
        assertTrue(lexer.isStartOfLine(LF));
    }

    // isStartOfLine(): CR is a start-of-line marker
    @Test
    public void testIsStartOfLine_cr_returnsTrue() throws Throwable {
        final Lexer lexer = createLexer("");
        assertTrue(lexer.isStartOfLine(CR));
    }

    // isStartOfLine(): UNDEFINED (start of file) is a start-of-line marker
    @Test
    public void testIsStartOfLine_undefined_returnsTrue() throws Throwable {
        final Lexer lexer = createLexer("");
        assertTrue(lexer.isStartOfLine(UNDEFINED));
    }

    // isStartOfLine(): a regular character is not a start-of-line marker
    @Test
    public void testIsStartOfLine_regularChar_returnsFalse() throws Throwable {
        final Lexer lexer = createLexer("");
        assertFalse(lexer.isStartOfLine('a'));
    }

    // isEndOfFile(): the END_OF_STREAM sentinel indicates end of file
    @Test
    public void testIsEndOfFile_endOfStreamValue_returnsTrue() throws Throwable {
        final Lexer lexer = createLexer("");
        assertTrue(lexer.isEndOfFile(END_OF_STREAM));
    }

    // isEndOfFile(): a regular character does not indicate end of file
    @Test
    public void testIsEndOfFile_regularChar_returnsFalse() throws Throwable {
        final Lexer lexer = createLexer("");
        assertFalse(lexer.isEndOfFile('a'));
    }

    // isDelimiter(): matches the configured delimiter (default ',')
    @Test
    public void testIsDelimiter_matchingChar_returnsTrue() throws Throwable {
        final Lexer lexer = createLexer("");
        assertTrue(lexer.isDelimiter(','));
    }

    // isDelimiter(): does not match a different character
    @Test
    public void testIsDelimiter_nonMatchingChar_returnsFalse() throws Throwable {
        final Lexer lexer = createLexer("");
        assertFalse(lexer.isDelimiter('x'));
    }

    // isEscape(): default format has no escape configured, so no char should match
    @Test
    public void testIsEscape_defaultDisabled_returnsFalseForBackslash() throws Throwable {
        final Lexer lexer = createLexer("");
        assertFalse(lexer.isEscape('\\'));
    }

    // isEscape(): configured escape char matches
    @Test
    public void testIsEscape_configuredEscape_returnsTrueForMatch() throws Throwable {
        final Lexer lexer = createLexer("", CSVFormat.DEFAULT.withEscape('\\'));
        assertTrue(lexer.isEscape('\\'));
    }

    // isEscape(): configured escape char does not match a different char
    @Test
    public void testIsEscape_configuredEscape_returnsFalseForNonMatch() throws Throwable {
        final Lexer lexer = createLexer("", CSVFormat.DEFAULT.withEscape('\\'));
        assertFalse(lexer.isEscape('x'));
    }

    // isQuoteChar(): default quote character '"' matches
    @Test
    public void testIsQuoteChar_defaultQuote_returnsTrueForDoubleQuote() throws Throwable {
        final Lexer lexer = createLexer("");
        assertTrue(lexer.isQuoteChar('"'));
    }

    // isQuoteChar(): a different char does not match the configured quote char
    @Test
    public void testIsQuoteChar_nonMatching_returnsFalse() throws Throwable {
        final Lexer lexer = createLexer("");
        assertFalse(lexer.isQuoteChar('\''));
    }

    // isCommentStart(): default format has no comment start configured
    @Test
    public void testIsCommentStart_defaultDisabled_returnsFalse() throws Throwable {
        final Lexer lexer = createLexer("");
        assertFalse(lexer.isCommentStart('#'));
    }

    // isCommentStart(): configured comment start char matches
    @Test
    public void testIsCommentStart_configured_returnsTrueForMatch() throws Throwable {
        final Lexer lexer = createLexer("", CSVFormat.DEFAULT.withCommentStart('#'));
        assertTrue(lexer.isCommentStart('#'));
    }

    // isCommentStart(): configured comment start char does not match a different char
    @Test
    public void testIsCommentStart_configured_returnsFalseForNonMatch() throws Throwable {
        final Lexer lexer = createLexer("", CSVFormat.DEFAULT.withCommentStart('#'));
        assertFalse(lexer.isCommentStart('x'));
    }
}
