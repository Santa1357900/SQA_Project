package org.apache.commons.compress.archivers.sevenz;

import org.junit.Test;
import static org.junit.Assert.*;

import java.io.File;
import java.io.IOException;
import java.util.Date;

public class SevenZOutputFileTest {

    @Test
    public void testOpenAndClose() throws Throwable {
        File tempFile = File.createTempFile("sevenz_test_", ".7z");
        tempFile.deleteOnExit();

        SevenZOutputFile out = null;
        try {
            out = new SevenZOutputFile(tempFile);
            out.setContentCompression(SevenZMethod.COPY);
            out.close();
        } finally {
            if (out != null) {
                try {
                    out.close();
                } catch (IOException e) {
                    // ignored
                }
            }
            if (tempFile.exists()) {
                tempFile.delete();
            }
        }
    }

    @Test
    public void testFinishTwice() throws Throwable {
        File tempFile = File.createTempFile("sevenz_test_", ".7z");
        tempFile.deleteOnExit();

        SevenZOutputFile out = null;
        try {
            out = new SevenZOutputFile(tempFile);
            out.finish();
            
            boolean exceptionThrown = false;
            try {
                out.finish();
            } catch (IOException e) {
                exceptionThrown = true;
                assertTrue(e.getMessage().contains("already been finished"));
            }
            assertTrue(exceptionThrown);
        } finally {
            if (out != null) {
                try {
                    out.close();
                } catch (IOException e) {
                    // ignored
                }
            }
            if (tempFile.exists()) {
                tempFile.delete();
            }
        }
    }

    @Test
    public void testCreateArchiveEntryAndWrite() throws Throwable {
        File tempFile = File.createTempFile("sevenz_test_", ".7z");
        tempFile.deleteOnExit();

        File dummyFile = File.createTempFile("dummy_", ".txt");
        dummyFile.deleteOnExit();

        SevenZOutputFile out = null;
        try {
            out = new SevenZOutputFile(tempFile);
            out.setContentCompression(SevenZMethod.COPY);

            SevenZArchiveEntry entry = out.createArchiveEntry(dummyFile, "testEntry.txt");
            entry.setCreationDate(new Date());
            entry.setAccessDate(new Date());
            entry.setLastModifiedDate(new Date());
            entry.setWindowsAttributes(0x20);

            out.putArchiveEntry(entry);
            out.write(new byte[] { 1, 2, 3, 4, 5 }, 0, 5);
            out.write(6);
            out.closeArchiveEntry();

            // Add an empty entry
            SevenZArchiveEntry emptyEntry = out.createArchiveEntry(dummyFile, "emptyEntry.txt");
            emptyEntry.setDirectory(true);
            out.putArchiveEntry(emptyEntry);
            out.closeArchiveEntry();

            out.finish();
        } finally {
            if (out != null) {
                try {
                    out.close();
                } catch (IOException e) {
                    // ignored
                }
            }
            if (tempFile.exists()) {
                tempFile.delete();
            }
            if (dummyFile.exists()) {
                dummyFile.delete();
            }
        }
    }

    @Test
    public void testCompressionMethodsAndAttributes() throws Throwable {
        File tempFile = File.createTempFile("sevenz_test_", ".7z");
        tempFile.deleteOnExit();

        SevenZOutputFile out = null;
        try {
            out = new SevenZOutputFile(tempFile);
            out.setContentCompression(SevenZMethod.LZMA2);

            SevenZArchiveEntry entry1 = new SevenZArchiveEntry();
            entry1.setName("lzma2.txt");
            entry1.setHasCreationDate(true);
            entry1.setCreationDate(new Date());
            entry1.setHasAccessDate(true);
            entry1.setAccessDate(new Date());
            entry1.setHasLastModifiedDate(true);
            entry1.setLastModifiedDate(new Date());
            entry1.setHasWindowsAttributes(true);
            entry1.setWindowsAttributes(1);

            out.putArchiveEntry(entry1);
            out.write(new byte[] { 10, 20, 30 });
            out.closeArchiveEntry();

            out.setContentCompression(SevenZMethod.BZIP2);
            SevenZArchiveEntry entry2 = new SevenZArchiveEntry();
            entry2.setName("bzip2.txt");
            out.putArchiveEntry(entry2);
            out.write(new byte[] { 40, 50 });
            out.closeArchiveEntry();

            out.setContentCompression(SevenZMethod.DEFLATE);
            SevenZArchiveEntry entry3 = new SevenZArchiveEntry();
            entry3.setName("deflate.txt");
            out.putArchiveEntry(entry3);
            out.write(60);
            out.closeArchiveEntry();

            out.finish();
        } finally {
            if (out != null) {
                try {
                    out.close();
                } catch (IOException e) {
                    // ignored
                }
            }
            if (tempFile.exists()) {
                tempFile.delete();
            }
        }
    }
}