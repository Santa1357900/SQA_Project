package org.apache.commons.csv;

import java.io.StringReader;

import org.junit.Test;

import static org.junit.Assert.*;

public class ExtendedBufferedReaderClaudeTest {

    // Constructor: initial lastChar is UNDEFINED, lineCounter is 0
    @Test
    public void testConstructor_initialState_lastCharUndefinedAndLineNumberZero() throws Throwable {
        ExtendedBufferedReader reader = new ExtendedBufferedReader(new StringReader("abc"));
        assertEquals(ExtendedBufferedReader.UNDEFINED, reader.readAgain());
        assertEquals(0, reader.getLineNumber());
    }

    // read(): normal char, no newline, lastChar set
    @Test
    public void testRead_singleChar_returnsCharSetsLastChar() throws Throwable {
        ExtendedBufferedReader reader = new ExtendedBufferedReader(new StringReader("a"));
        int c = reader.read();
        assertEquals('a', c);
        assertEquals('a', reader.readAgain());
        assertEquals(0, reader.getLineNumber());
    }

    // read(): '\n' branch increments lineCounter
    @Test
    public void testRead_newline_incrementsLineCounter() throws Throwable {
        ExtendedBufferedReader reader = new ExtendedBufferedReader(new StringReader("\na"));
        int c = reader.read();
        assertEquals('\n', c);
        assertEquals(1, reader.getLineNumber());
        assertEquals('\n', reader.readAgain());
    }

    // read(): multiple newlines each increment counter (loop over calls)
    @Test
    public void testRead_multipleNewlines_incrementsEachTime() throws Throwable {
        ExtendedBufferedReader reader = new ExtendedBufferedReader(new StringReader("\n\nx"));
        reader.read();
        reader.read();
        assertEquals(2, reader.getLineNumber());
    }

    // read(): end of stream returns -1 and sets lastChar to END_OF_STREAM
    @Test
    public void testRead_atEndOfStream_returnsMinusOneSetsEndOfStream() throws Throwable {
        ExtendedBufferedReader reader = new ExtendedBufferedReader(new StringReader(""));
        int c = reader.read();
        assertEquals(-1, c);
        assertEquals(ExtendedBufferedReader.END_OF_STREAM, reader.readAgain());
    }

    // readAgain(): before any read returns UNDEFINED
    @Test
    public void testReadAgain_beforeAnyRead_returnsUndefined() throws Throwable {
        ExtendedBufferedReader reader = new ExtendedBufferedReader(new StringReader("xyz"));
        assertEquals(ExtendedBufferedReader.UNDEFINED, reader.readAgain());
    }

    // readAgain(): after read returns last char read
    @Test
    public void testReadAgain_afterRead_returnsLastCharRead() throws Throwable {
        ExtendedBufferedReader reader = new ExtendedBufferedReader(new StringReader("xy"));
        reader.read();
        reader.read();
        assertEquals('y', reader.readAgain());
    }

    // readAgain(): after EOF returns END_OF_STREAM
    @Test
    public void testReadAgain_afterEOF_returnsEndOfStream() throws Throwable {
        ExtendedBufferedReader reader = new ExtendedBufferedReader(new StringReader(""));
        reader.read();
        assertEquals(ExtendedBufferedReader.END_OF_STREAM, reader.readAgain());
    }

    // read(buf,off,len): length==0 branch returns 0 immediately, no state change
    @Test
    public void testReadCharArray_zeroLength_returnsZeroWithoutChangingState() throws Throwable {
        ExtendedBufferedReader reader = new ExtendedBufferedReader(new StringReader("abc"));
        char[] buf = new char[5];
        int len = reader.read(buf, 0, 0);
        assertEquals(0, len);
        assertEquals(ExtendedBufferedReader.UNDEFINED, reader.readAgain());
    }

    // read(buf,off,len): normal chars, no newline, no counter increment
    @Test
    public void testReadCharArray_normalChars_noNewlineNoIncrement() throws Throwable {
        ExtendedBufferedReader reader = new ExtendedBufferedReader(new StringReader("abc"));
        char[] buf = new char[3];
        int len = reader.read(buf, 0, 3);
        assertEquals(3, len);
        assertEquals(0, reader.getLineNumber());
        assertEquals('c', reader.readAgain());
    }

    // read(buf,off,len): len==-1 branch at EOF sets lastChar to END_OF_STREAM
    @Test
    public void testReadCharArray_atEOF_returnsMinusOneSetsEndOfStream() throws Throwable {
        ExtendedBufferedReader reader = new ExtendedBufferedReader(new StringReader(""));
        char[] buf = new char[3];
        int len = reader.read(buf, 0, 3);
        assertEquals(-1, len);
        assertEquals(ExtendedBufferedReader.END_OF_STREAM, reader.readAgain());
    }

    // read(buf,off,len): partial read shorter than requested length returns actual count
    @Test
    public void testReadCharArray_partialReadShorterThanBuffer_returnsActualLength() throws Throwable {
        ExtendedBufferedReader reader = new ExtendedBufferedReader(new StringReader("ab"));
        char[] buf = new char[5];
        int len = reader.read(buf, 0, 5);
        assertEquals(2, len);
        assertEquals('b', reader.readAgain());
    }

    // read(buf,off,len): newline at i==offset(0) uses lastChar (not '\r') -> increments
    @Test
    public void testReadCharArray_newlineAtOffsetZero_incrementsUsingLastChar() throws Throwable {
        ExtendedBufferedReader reader = new ExtendedBufferedReader(new StringReader("\nxy"));
        char[] buf = new char[3];
        int len = reader.read(buf, 0, 3);
        assertEquals(3, len);
        assertEquals(1, reader.getLineNumber());
        assertEquals('y', reader.readAgain());
    }

    // read(buf,off,len): CR followed by LF within same buffer counts as a single line
    @Test
    public void testReadCharArray_crlfTogetherInBuffer_countsAsSingleLine() throws Throwable {
        ExtendedBufferedReader reader = new ExtendedBufferedReader(new StringReader("\r\nxy"));
        char[] buf = new char[4];
        int len = reader.read(buf, 0, 4);
        assertEquals(4, len);
        assertEquals(1, reader.getLineNumber());
    }

    // read(buf,off,len): lone carriage return increments counter
    @Test
    public void testReadCharArray_loneCarriageReturn_incrementsLineCounter() throws Throwable {
        ExtendedBufferedReader reader = new ExtendedBufferedReader(new StringReader("\rxy"));
        char[] buf = new char[3];
        int len = reader.read(buf, 0, 3);
        assertEquals(3, len);
        assertEquals(1, reader.getLineNumber());
    }

    // read(buf,off,len): multiple separate newlines across the buffer each increment counter
    @Test
    public void testReadCharArray_multipleNewlinesAcrossBuffer_countsEachOccurrence() throws Throwable {
        ExtendedBufferedReader reader = new ExtendedBufferedReader(new StringReader("a\nb\nc\nd"));
        char[] buf = new char[7];
        int len = reader.read(buf, 0, 7);
        assertEquals(7, len);
        assertEquals(3, reader.getLineNumber());
        assertEquals('d', reader.readAgain());
    }



    // readLine(): normal line returns content without terminator, increments counter, sets lastChar
    @Test
    public void testReadLine_normalLine_returnsContentAndIncrementsCounter() throws Throwable {
        ExtendedBufferedReader reader = new ExtendedBufferedReader(new StringReader("hello\nworld"));
        String line = reader.readLine();
        assertEquals("hello", line);
        assertEquals(1, reader.getLineNumber());
        assertEquals('o', reader.readAgain());
    }

    // readLine(): empty line keeps lastChar unchanged but still increments counter
    @Test
    public void testReadLine_emptyLine_lastCharUnchangedCounterIncrements() throws Throwable {
        ExtendedBufferedReader reader = new ExtendedBufferedReader(new StringReader("\nabc"));
        String line = reader.readLine();
        assertEquals("", line);
        assertEquals(1, reader.getLineNumber());
        assertEquals(ExtendedBufferedReader.UNDEFINED, reader.readAgain());
    }

    // readLine(): at EOF returns null and sets lastChar to END_OF_STREAM
    @Test
    public void testReadLine_atEOF_returnsNullSetsEndOfStream() throws Throwable {
        ExtendedBufferedReader reader = new ExtendedBufferedReader(new StringReader(""));
        String line = reader.readLine();
        assertNull(line);
        assertEquals(ExtendedBufferedReader.END_OF_STREAM, reader.readAgain());
    }

    // readLine(): multiple calls accumulate the line counter; EOF call does not increment further
    @Test
    public void testReadLine_multipleLines_counterAccumulates() throws Throwable {
        ExtendedBufferedReader reader = new ExtendedBufferedReader(new StringReader("a\nb\nc\n"));
        assertEquals("a", reader.readLine());
        assertEquals(1, reader.getLineNumber());
        assertEquals("b", reader.readLine());
        assertEquals(2, reader.getLineNumber());
        assertEquals("c", reader.readLine());
        assertEquals(3, reader.getLineNumber());
        assertNull(reader.readLine());
        assertEquals(3, reader.getLineNumber());
        assertEquals(ExtendedBufferedReader.END_OF_STREAM, reader.readAgain());
    }

    // lookAhead(): peeks next char without consuming it; subsequent read() returns same char
    @Test
    public void testLookAhead_doesNotConsumeNextChar() throws Throwable {
        ExtendedBufferedReader reader = new ExtendedBufferedReader(new StringReader("xyz"));
        int peek = reader.lookAhead();
        assertEquals('x', peek);
        int c = reader.read();
        assertEquals('x', c);
    }

    // lookAhead(): at EOF returns -1
    @Test
    public void testLookAhead_atEOF_returnsMinusOne() throws Throwable {
        ExtendedBufferedReader reader = new ExtendedBufferedReader(new StringReader(""));
        int peek = reader.lookAhead();
        assertEquals(-1, peek);
    }

    // lookAhead(): does not affect lineCounter or lastChar (not an actual read)
    @Test
    public void testLookAhead_doesNotAffectLineCounterOrLastChar() throws Throwable {
        ExtendedBufferedReader reader = new ExtendedBufferedReader(new StringReader("\n"));
        int peek = reader.lookAhead();
        assertEquals('\n', peek);
        assertEquals(0, reader.getLineNumber());
        assertEquals(ExtendedBufferedReader.UNDEFINED, reader.readAgain());
    }

    // lookAhead(): can be called repeatedly returning the same next char each time
    @Test
    public void testLookAhead_calledTwice_returnsSameCharBothTimes() throws Throwable {
        ExtendedBufferedReader reader = new ExtendedBufferedReader(new StringReader("qr"));
        int peek1 = reader.lookAhead();
        int peek2 = reader.lookAhead();
        assertEquals(peek1, peek2);
        assertEquals('q', peek1);
    }

    // getLineNumber(): accumulates correctly across sequential single-char reads (loop, multiple rounds)
    @Test
    public void testGetLineNumber_afterSequentialCharReads_matchesNewlineCount() throws Throwable {
        ExtendedBufferedReader reader = new ExtendedBufferedReader(new StringReader("a\nb\nc\n"));
        int c;
        do {
            c = reader.read();
        } while (c != -1);
        assertEquals(3, reader.getLineNumber());
    }

    // getLineNumber(): remains 0 when no newline characters are present in the stream
    @Test
    public void testGetLineNumber_noNewlines_remainsZero() throws Throwable {
        ExtendedBufferedReader reader = new ExtendedBufferedReader(new StringReader("abcdef"));
        char[] buf = new char[6];
        reader.read(buf, 0, 6);
        assertEquals(0, reader.getLineNumber());
    }
}
