package org.apache.commons.csv;

import org.junit.Test;
import static org.junit.Assert.*;

import java.io.StringReader;
import java.io.IOException;

public class ExtendedBufferedReaderTest {

    @Test
    public void testInitialState() throws Throwable {
        StringReader reader = new StringReader("a");
        ExtendedBufferedReader extReader = new ExtendedBufferedReader(reader);
        
        assertEquals(ExtendedBufferedReader.UNDEFINED, extReader.readAgain());
        assertEquals(0, extReader.getLineNumber());
    }

    @Test
    public void testReadSingleChar() throws Throwable {
        StringReader reader = new StringReader("a");
        ExtendedBufferedReader extReader = new ExtendedBufferedReader(reader);
        
        int c = extReader.read();
        assertEquals('a', c);
        assertEquals('a', extReader.readAgain());
        assertEquals(0, extReader.getLineNumber());
    }

    @Test
    public void testReadLineCounterWithLF() throws Throwable {
        StringReader reader = new StringReader("a\nb\n");
        ExtendedBufferedReader extReader = new ExtendedBufferedReader(reader);
        
        assertEquals('a', extReader.read());
        assertEquals(0, extReader.getLineNumber());
        
        assertEquals('\n', extReader.read());
        assertEquals(1, extReader.getLineNumber());
        
        assertEquals('b', extReader.read());
        assertEquals(1, extReader.getLineNumber());
        
        assertEquals('\n', extReader.read());
        assertEquals(2, extReader.getLineNumber());
        
        assertEquals(ExtendedBufferedReader.END_OF_STREAM, extReader.read());
        assertEquals(ExtendedBufferedReader.END_OF_STREAM, extReader.readAgain());
    }

    @Test
    public void testLookAhead() throws Throwable {
        StringReader reader = new StringReader("abc");
        ExtendedBufferedReader extReader = new ExtendedBufferedReader(reader);
        
        assertEquals('a', extReader.lookAhead());
        assertEquals(ExtendedBufferedReader.UNDEFINED, extReader.readAgain());
        
        assertEquals('a', extReader.read());
        assertEquals('b', extReader.lookAhead());
        assertEquals('a', extReader.readAgain());
        
        assertEquals('b', extReader.read());
        assertEquals('c', extReader.read());
        assertEquals(ExtendedBufferedReader.END_OF_STREAM, extReader.lookAhead());
    }

    @Test
    public void testReadIntoBufferZeroLength() throws Throwable {
        StringReader reader = new StringReader("abc");
        ExtendedBufferedReader extReader = new ExtendedBufferedReader(reader);
        
        char[] buf = new char[5];
        int len = extReader.read(buf, 0, 0);
        assertEquals(0, len);
    }

    @Test
    public void testReadIntoBufferEOF() throws Throwable {
        StringReader reader = new StringReader("");
        ExtendedBufferedReader extReader = new ExtendedBufferedReader(reader);
        
        char[] buf = new char[5];
        int len = extReader.read(buf, 0, 5);
        assertEquals(-1, len);
        assertEquals(ExtendedBufferedReader.END_OF_STREAM, extReader.readAgain());
    }

    @Test
    public void testReadIntoBufferWithLineEndings() throws Throwable {
        StringReader reader = new StringReader("a\r\nb\nc\r");
        ExtendedBufferedReader extReader = new ExtendedBufferedReader(reader);
        
        char[] buf = new char[10];
        int len = extReader.read(buf, 0, 10);
        assertEquals(7, len);
        // \r\n should count line once, \n should count line once, \r should count line once
        assertEquals(3, extReader.getLineNumber());
        assertEquals('\r', extReader.readAgain());
    }

    @Test
    public void testReadLine() throws Throwable {
        StringReader reader = new StringReader("line1\nline2");
        ExtendedBufferedReader extReader = new ExtendedBufferedReader(reader);
        
        String line1 = extReader.readLine();
        assertEquals("line1", line1);
        assertEquals(1, extReader.getLineNumber());
        assertEquals('1', extReader.readAgain());
        
        String line2 = extReader.readLine();
        assertEquals("line2", line2);
        assertEquals(2, extReader.getLineNumber());
        assertEquals('2', extReader.readAgain());
        
        String line3 = extReader.readLine();
        assertNull(line3);
        assertEquals(ExtendedBufferedReader.END_OF_STREAM, extReader.readAgain());
    }

    @Test
    public void testReadLineEmpty() throws Throwable {
        StringReader reader = new StringReader("\n");
        ExtendedBufferedReader extReader = new ExtendedBufferedReader(reader);
        
        String line = extReader.readLine();
        assertEquals("", line);
        assertEquals(1, extReader.getLineNumber());
    }
}