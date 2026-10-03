package org.apache.commons.compress.archivers.zip;

import org.junit.Test;
import java.util.zip.ZipException;

import static org.junit.Assert.*;

public class Zip64ExtendedInformationExtraFieldTest {

    @Test
    public void testHeaderId() throws Throwable {
        Zip64ExtendedInformationExtraField field = new Zip64ExtendedInformationExtraField();
        assertEquals(new ZipShort(0x0001), field.getHeaderId());
    }

    @Test
    public void testConstructorsAndGettersSetters() throws Throwable {
        ZipEightByteInteger size = new ZipEightByteInteger(100);
        ZipEightByteInteger compressedSize = new ZipEightByteInteger(50);
        ZipEightByteInteger rho = new ZipEightByteInteger(200);
        ZipLong diskStart = new ZipLong(1);

        Zip64ExtendedInformationExtraField field = 
            new Zip64ExtendedInformationExtraField(size, compressedSize, rho, diskStart);

        assertEquals(size, field.getSize());
        assertEquals(compressedSize, field.getCompressedSize());
        assertEquals(rho, field.getRelativeHeaderOffset());
        assertEquals(diskStart, field.getDiskStartNumber());

        ZipEightByteInteger newSize = new ZipEightByteInteger(300);
        ZipEightByteInteger newCompSize = new ZipEightByteInteger(150);
        ZipEightByteInteger newRho = new ZipEightByteInteger(400);
        ZipLong newDisk = new ZipLong(2);

        field.setSize(newSize);
        field.setCompressedSize(newCompSize);
        field.setRelativeHeaderOffset(newRho);
        field.setDiskStartNumber(newDisk);

        assertEquals(newSize, field.getSize());
        assertEquals(newCompSize, field.getCompressedSize());
        assertEquals(newRho, field.getRelativeHeaderOffset());
        assertEquals(newDisk, field.getDiskStartNumber());
    }

    @Test
    public void testTwoParamConstructor() throws Throwable {
        ZipEightByteInteger size = new ZipEightByteInteger(10);
        ZipEightByteInteger compressedSize = new ZipEightByteInteger(5);
        Zip64ExtendedInformationExtraField field = 
            new Zip64ExtendedInformationExtraField(size, compressedSize);

        assertEquals(size, field.getSize());
        assertEquals(compressedSize, field.getCompressedSize());
        assertNull(field.getRelativeHeaderOffset());
        assertNull(field.getDiskStartNumber());
    }

    @Test
    public void testGetLocalFileDataLength() throws Throwable {
        Zip64ExtendedInformationExtraField field = new Zip64ExtendedInformationExtraField();
        assertEquals(0, field.getLocalFileDataLength().getValue());

        field.setSize(new ZipEightByteInteger(10));
        // size is not null, so it returns 2 * DWORD (16)
        assertEquals(16, field.getLocalFileDataLength().getValue());
    }

    @Test
    public void testGetCentralDirectoryLength() throws Throwable {
        Zip64ExtendedInformationExtraField field = new Zip64ExtendedInformationExtraField();
        assertEquals(0, field.getCentralDirectoryLength().getValue());

        field.setSize(new ZipEightByteInteger(10));
        assertEquals(8, field.getCentralDirectoryLength().getValue());

        field.setCompressedSize(new ZipEightByteInteger(5));
        assertEquals(16, field.getCentralDirectoryLength().getValue());

        field.setRelativeHeaderOffset(new ZipEightByteInteger(20));
        assertEquals(24, field.getCentralDirectoryLength().getValue());

        field.setDiskStartNumber(new ZipLong(1));
        // 24 + WORD (4) = 28
        assertEquals(28, field.getCentralDirectoryLength().getValue());
    }

    @Test
    public void testGetLocalFileDataDataEmpty() throws Throwable {
        Zip64ExtendedInformationExtraField field = new Zip64ExtendedInformationExtraField();
        byte[] data = field.getLocalFileDataData();
        assertNotNull(data);
        assertEquals(0, data.length);
    }

    @Test
    public void testGetLocalFileDataDataThrowsException() throws Throwable {
        Zip64ExtendedInformationExtraField field = new Zip64ExtendedInformationExtraField();
        field.setSize(new ZipEightByteInteger(10));
        // compressedSize is null, should throw IllegalArgumentException
        boolean thrown = false;
        try {
            field.getLocalFileDataData();
        } catch (IllegalArgumentException e) {
            thrown = true;
            assertTrue(e.getMessage().contains("Zip64 extended information must contain"));
        }
        assertTrue(thrown);
    }

    @Test
    public void testGetLocalFileDataDataSuccess() throws Throwable {
        ZipEightByteInteger size = new ZipEightByteInteger(10);
        ZipEightByteInteger compressedSize = new ZipEightByteInteger(5);
        Zip64ExtendedInformationExtraField field = 
            new Zip64ExtendedInformationExtraField(size, compressedSize);

        byte[] data = field.getLocalFileDataData();
        assertNotNull(data);
        assertEquals(16, data.length);
    }

    @Test
    public void testGetCentralDirectoryData() throws Throwable {
        ZipEightByteInteger size = new ZipEightByteInteger(10);
        ZipEightByteInteger compressedSize = new ZipEightByteInteger(5);
        ZipEightByteInteger rho = new ZipEightByteInteger(20);
        ZipLong diskStart = new ZipLong(1);
        Zip64ExtendedInformationExtraField field = 
            new Zip64ExtendedInformationExtraField(size, compressedSize, rho, diskStart);

        byte[] data = field.getCentralDirectoryData();
        assertNotNull(data);
        assertEquals(28, data.length);
    }

    @Test
    public void testParseFromLocalFileDataZeroLength() throws Throwable {
        Zip64ExtendedInformationExtraField field = new Zip64ExtendedInformationExtraField();
        field.parseFromLocalFileData(new byte[0], 0, 0);
        assertNull(field.getSize());
    }

    @Test
    public void testParseFromLocalFileDataInvalidLength() throws Throwable {
        Zip64ExtendedInformationExtraField field = new Zip64ExtendedInformationExtraField();
        boolean thrown = false;
        try {
            field.parseFromLocalFileData(new byte[10], 0, 10);
        } catch (ZipException e) {
            thrown = true;
        }
        assertTrue(thrown);
    }

    @Test
    public void testParseFromLocalFileDataValid() throws Throwable {
        byte[] buffer = new byte[32];
        Zip64ExtendedInformationExtraField field = new Zip64ExtendedInformationExtraField();
        field.parseFromLocalFileData(buffer, 0, 24);
        assertNotNull(field.getSize());
        assertNotNull(field.getCompressedSize());
        assertNotNull(field.getRelativeHeaderOffset());
        assertNull(field.getDiskStartNumber());

        field.parseFromLocalFileData(buffer, 0, 28);
        assertNotNull(field.getDiskStartNumber());
    }

    @Test
    public void testParseFromCentralDirectoryDataVariousLengths() throws Throwable {
        Zip64ExtendedInformationExtraField field1 = new Zip64ExtendedInformationExtraField();
        byte[] buffer = new byte[32];
        
        // length >= 3 * DWORD + WORD (28)
        field1.parseFromCentralDirectoryData(buffer, 0, 28);
        assertNotNull(field1.getDiskStartNumber());

        // length == 3 * DWORD (24)
        Zip64ExtendedInformationExtraField field2 = new Zip64ExtendedInformationExtraField();
        field2.parseFromCentralDirectoryData(buffer, 0, 24);
        assertNotNull(field2.getRelativeHeaderOffset());

        // length % DWORD == WORD (e.g., length = 4)
        Zip64ExtendedInformationExtraField field3 = new Zip64ExtendedInformationExtraField();
        field3.parseFromCentralDirectoryData(buffer, 0, 4);
        assertNotNull(field3.getDiskStartNumber());
    }

    @Test
    public void testReparseCentralDirectoryData() throws Throwable {
        Zip64ExtendedInformationExtraField field = new Zip64ExtendedInformationExtraField();
        // rawCentralDirectoryData is null initially
        field.reparseCentralDirectoryData(true, true, true, true);
        assertNull(field.getSize());

        byte[] buffer = new byte[28];
        field.parseFromCentralDirectoryData(buffer, 0, 28);

        // Mismatch length test
        boolean thrown = false;
        try {
            field.reparseCentralDirectoryData(false, false, false, false);
        } catch (ZipException e) {
            thrown = true;
        }
        assertTrue(thrown);

        // Correct expected length (24 + 4 = 28)
        field.reparseCentralDirectoryData(true, true, true, true);
        assertNotNull(field.getSize());
        assertNotNull(field.getCompressedSize());
        assertNotNull(field.getRelativeHeaderOffset());
        assertNotNull(field.getDiskStartNumber());
    }
}