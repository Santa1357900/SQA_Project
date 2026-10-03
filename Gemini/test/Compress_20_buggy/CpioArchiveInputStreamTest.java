package org.apache.commons.compress.archivers.cpio;

import org.junit.Test;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;

import static org.junit.Assert.*;

public class CpioArchiveInputStreamTest {

    @Test
    public void testMatches() throws Throwable {
        byte[] validNew = new byte[] { 0x30, 0x37, 0x30, 0x37, 0x30, 0x31 };
        byte[] validNewCrc = new byte[] { 0x30, 0x37, 0x30, 0x37, 0x30, 0x32 };
        byte[] validOldAscii = new byte[] { 0x30, 0x37, 0x30, 0x37, 0x30, 0x37 };
        byte[] validOldBinary1 = new byte[] { 0x71, (byte) 0xc7, 0, 0, 0, 0 };
        byte[] validOldBinary2 = new byte[] { (byte) 0xc7, 0x71, 0, 0, 0, 0 };
        
        byte[] tooShort = new byte[] { 0x30, 0x37, 0x30 };
        byte[] invalidFirst = new byte[] { 0x31, 0x37, 0x30, 0x37, 0x30, 0x31 };
        byte[] invalidLast = new byte[] { 0x30, 0x37, 0x30, 0x37, 0x30, 0x33 };

        assertTrue(CpioArchiveInputStream.matches(validNew, validNew.length));
        assertTrue(CpioArchiveInputStream.matches(validNewCrc, validNewCrc.length));
        assertTrue(CpioArchiveInputStream.matches(validOldAscii, validOldAscii.length));
        assertTrue(CpioArchiveInputStream.matches(validOldBinary1, validOldBinary1.length));
        assertTrue(CpioArchiveInputStream.matches(validOldBinary2, validOldBinary2.length));

        assertFalse(CpioArchiveInputStream.matches(tooShort, tooShort.length));
        assertFalse(CpioArchiveInputStream.matches(invalidFirst, invalidFirst.length));
        assertFalse(CpioArchiveInputStream.matches(invalidLast, invalidLast.length));
    }

    @Test
    public void testConstructorsAndAvailable() throws Throwable {
        InputStream in = new ByteArrayInputStream(new byte[10]);
        CpioArchiveInputStream cpioIn = new CpioArchiveInputStream(in);
        assertEquals(1, cpioIn.available());
        cpioIn.close();
        
        boolean ioExceptionThrown = false;
        try {
            cpioIn.available();
        } catch (IOException e) {
            ioExceptionThrown = true;
        }
        assertTrue(ioExceptionThrown);
    }

    @Test
    public void testBlockSizeConstructor() throws Throwable {
        InputStream in = new ByteArrayInputStream(new byte[10]);
        CpioArchiveInputStream cpioIn = new CpioArchiveInputStream(in, 512);
        assertEquals(1, cpioIn.available());
        cpioIn.close();
    }

    @Test
    public void testCloseIdempotent() throws Throwable {
        InputStream in = new ByteArrayInputStream(new byte[10]);
        CpioArchiveInputStream cpioIn = new CpioArchiveInputStream(in);
        cpioIn.close();
        cpioIn.close(); // Should not throw exception
    }

    @Test
    public void testSkipNegative() throws Throwable {
        InputStream in = new ByteArrayInputStream(new byte[10]);
        CpioArchiveInputStream cpioIn = new CpioArchiveInputStream(in);
        boolean exceptionThrown = false;
        try {
            cpioIn.skip(-1L);
        } catch (IllegalArgumentException e) {
            exceptionThrown = true;
        }
        assertTrue(exceptionThrown);
        cpioIn.close();
    }

    @Test
    public void testReadInvalidArguments() throws Throwable {
        InputStream in = new ByteArrayInputStream(new byte[10]);
        CpioArchiveInputStream cpioIn = new CpioArchiveInputStream(in);
        byte[] buf = new byte[10];

        boolean ex1 = false;
        try {
            cpioIn.read(buf, -1, 5);
        } catch (IndexOutOfBoundsException e) {
            ex1 = true;
        }
        assertTrue(ex1);

        boolean ex2 = false;
        try {
            cpioIn.read(buf, 0, -1);
        } catch (IndexOutOfBoundsException e) {
            ex2 = true;
        }
        assertTrue(ex2);

        boolean ex3 = false;
        try {
            cpioIn.read(buf, 5, 10);
        } catch (IndexOutOfBoundsException e) {
            ex3 = true;
        }
        assertTrue(ex3);

        assertEquals(0, cpioIn.read(buf, 0, 0));

        cpioIn.close();
    }

    @Test
    public void testReadWithoutEntry() throws Throwable {
        InputStream in = new ByteArrayInputStream(new byte[10]);
        CpioArchiveInputStream cpioIn = new CpioArchiveInputStream(in);
        byte[] buf = new byte[10];
        assertEquals(-1, cpioIn.read(buf, 0, 5));
        cpioIn.close();
    }

    @Test
    public void testSkipWithoutEntry() throws Throwable {
        InputStream in = new ByteArrayInputStream(new byte[10]);
        CpioArchiveInputStream cpioIn = new CpioArchiveInputStream(in);
        assertEquals(0, cpioIn.skip(5));
        cpioIn.close();
    }

    @Test
    public void testGetNextEntryUnknownMagic() throws Throwable {
        byte[] badData = new byte[] { 0x12, 0x34, 0x56, 0x78, 0x12, 0x34 };
        InputStream in = new ByteArrayInputStream(badData);
        CpioArchiveInputStream cpioIn = new CpioArchiveInputStream(in);
        boolean exceptionThrown = false;
        try {
            cpioIn.getNextEntry();
        } catch (IOException e) {
            assertTrue(e.getMessage().contains("Unknown magic"));
            exceptionThrown = true;
        }
        assertTrue(exceptionThrown);
        cpioIn.close();
    }
}