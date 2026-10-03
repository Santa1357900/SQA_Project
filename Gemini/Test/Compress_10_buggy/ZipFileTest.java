package org.apache.commons.compress.archivers.zip;

import org.junit.Test;
import static org.junit.Assert.*;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.util.Enumeration;
import java.util.zip.ZipException;

public class ZipFileTest {

    @Test
    public void testConstructorWithNonExistentFile() throws Throwable {
        File nonExistent = new File("non_existent_zip_file_12345.zip");
        try {
            ZipFile zf = new ZipFile(nonExistent);
            fail("Should have thrown IOException for non-existent file");
        } catch (IOException e) {
            // Expected
        }
    }

    @Test
    public void testCloseQuietlyWithNull() throws Throwable {
        ZipFile.closeQuietly(null);
    }

    @Test
    public void testCanReadEntryDataWithNull() throws Throwable {
        File tempFile = File.createTempFile("empty", ".zip");
        tempFile.deleteOnExit();
        ZipFile zf = null;
        try {
            zf = new ZipFile(tempFile);
            fail("Should have thrown exception for empty/invalid zip");
        } catch (IOException e) {
            // Expected because it's not a valid zip
        } finally {
            ZipFile.closeQuietly(zf);
        }
    }
}