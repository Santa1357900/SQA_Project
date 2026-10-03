package org.apache.commons.cli;

import org.junit.Test;
import static org.junit.Assert.*;

import java.io.File;
import java.io.FileInputStream;
import java.net.URL;
import java.util.Date;

public class TypeHandlerTest {

    @Test
    public void testCreateValueString() throws Throwable {
        String result = TypeHandler.createValue("testString", PatternOptionBuilder.STRING_VALUE);
        assertEquals("testString", result);

        Object resultObj = TypeHandler.createValue("testObjStr", (Object) PatternOptionBuilder.STRING_VALUE);
        assertEquals("testObjStr", resultObj);
    }

    @Test
    public void testCreateValueObject() throws Throwable {
        Object result = TypeHandler.createValue("java.util.Date", PatternOptionBuilder.OBJECT_VALUE);
        assertNotNull(result);
        assertTrue(result instanceof Date);
    }

    @Test
    public void testCreateValueNumber() throws Throwable {
        Number longResult = TypeHandler.createValue("123", PatternOptionBuilder.NUMBER_VALUE);
        assertNotNull(longResult);
        assertEquals(Long.valueOf(123L), longResult);

        Number doubleResult = TypeHandler.createValue("123.45", PatternOptionBuilder.NUMBER_VALUE);
        assertNotNull(doubleResult);
        assertEquals(Double.valueOf(123.45), doubleResult);
    }

    @Test
    public void testCreateValueClass() throws Throwable {
        Class<?> clazz = TypeHandler.createValue("java.lang.String", PatternOptionBuilder.CLASS_VALUE);
        assertEquals(String.class, clazz);
    }

    @Test
    public void testCreateValueFile() throws Throwable {
        File file = TypeHandler.createValue("test.txt", PatternOptionBuilder.FILE_VALUE);
        assertNotNull(file);
        assertEquals("test.txt", file.getPath());
    }

    @Test
    public void testCreateValueExistingFile() throws Throwable {
        File tempFile = File.createTempFile("typeHandlerTest", ".tmp");
        tempFile.deleteOnExit();
        FileInputStream fis = TypeHandler.createValue(tempFile.getAbsolutePath(), PatternOptionBuilder.EXISTING_FILE_VALUE);
        assertNotNull(fis);
        fis.close();
    }

    @Test
    public void testCreateValueURL() throws Throwable {
        URL url = TypeHandler.createValue("http://localhost/", PatternOptionBuilder.URL_VALUE);
        assertNotNull(url);
        assertEquals("http", url.getProtocol());
    }

    @Test
    public void testCreateValueUnknownClass() throws Throwable {
        Object result = TypeHandler.createValue("someValue", Integer.class);
        assertNull(result);
    }

    @Test(expected = ParseException.class)
    public void testCreateObjectClassNotFound() throws Throwable {
        TypeHandler.createObject("non.existent.ClassName");
    }

    @Test(expected = ParseException.class)
    public void testCreateObjectInstantiationFailure() throws Throwable {
        // java.util.Date has no default zero-arg constructor that doesn't conflict, wait, Date has default constructor.
        // Let's use an abstract class or interface which cannot be instantiated via newInstance().
        TypeHandler.createObject("java.util.List");
    }

    @Test
    public void testCreateNumberLong() throws Throwable {
        Number num = TypeHandler.createNumber("987654");
        assertTrue(num instanceof Long);
        assertEquals(987654L, num.longValue());
    }

    @Test
    public void testCreateNumberDouble() throws Throwable {
        Number num = TypeHandler.createNumber("987.654");
        assertTrue(num instanceof Double);
        assertEquals(987.654, num.doubleValue(), 0.0001);
    }

    @Test(expected = ParseException.class)
    public void testCreateNumberInvalid() throws Throwable {
        TypeHandler.createNumber("not-a-number");
    }

    @Test
    public void testCreateClassSuccess() throws Throwable {
        Class<?> clazz = TypeHandler.createClass("java.lang.Object");
        assertEquals(Object.class, clazz);
    }

    @Test(expected = ParseException.class)
    public void testCreateClassFailure() throws Throwable {
        TypeHandler.createClass("org.apache.commons.cli.NonExistentClass");
    }

    @Test(expected = UnsupportedOperationException.class)
    public void testCreateDate() throws Throwable {
        TypeHandler.createDate("2020-01-01");
    }

    @Test
    public void testCreateURLSuccess() throws Throwable {
        URL url = TypeHandler.createURL("http://apache.org");
        assertNotNull(url);
        assertEquals("apache.org", url.getHost());
    }

    @Test(expected = ParseException.class)
    public void testCreateURLFailure() throws Throwable {
        TypeHandler.createURL("malformed-url");
    }

    @Test
    public void testCreateFile() throws Throwable {
        File file = TypeHandler.createFile("dummy/path.txt");
        assertNotNull(file);
        assertEquals("dummy/path.txt", file.getPath());
    }

    @Test
    public void testOpenFileSuccess() throws Throwable {
        File tempFile = File.createTempFile("openFileTest", ".tmp");
        tempFile.deleteOnExit();
        FileInputStream fis = TypeHandler.openFile(tempFile.getAbsolutePath());
        assertNotNull(fis);
        fis.close();
    }

    @Test(expected = ParseException.class)
    public void testOpenFileFailure() throws Throwable {
        TypeHandler.openFile("non_existent_file_123456789.tmp");
    }

    @Test(expected = UnsupportedOperationException.class)
    public void testCreateFiles() throws Throwable {
        TypeHandler.createFiles("some/path");
    }

    @Test
    public void testCreateValueDateUnsupported() throws Throwable {
        // PatternOptionBuilder.DATE_VALUE calls createDate which throws UnsupportedOperationException
        boolean caught = false;
        try {
            TypeHandler.createValue("2020-01-01", PatternOptionBuilder.DATE_VALUE);
        } catch (UnsupportedOperationException e) {
            caught = true;
        }
        assertTrue(caught);
    }

    @Test
    public void testCreateValueFilesUnsupported() throws Throwable {
        // PatternOptionBuilder.FILES_VALUE calls createFiles which throws UnsupportedOperationException
        boolean caught = false;
        try {
            TypeHandler.createValue("file1,file2", PatternOptionBuilder.FILES_VALUE);
        } catch (UnsupportedOperationException e) {
            caught = true;
        }
        assertTrue(caught);
    }
}