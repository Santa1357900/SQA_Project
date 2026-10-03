package org.apache.commons.cli;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import java.io.File;
import java.net.URL;
import java.util.Date;

import org.junit.Test;

public class TypeHandlerTest {

    @Test
    public void testCreateValueWithString() throws Throwable {
        Object result = TypeHandler.createValue("testString", PatternOptionBuilder.STRING_VALUE);
        assertEquals("testString", result);

        Object resultObj = TypeHandler.createValue("testString", (Object) PatternOptionBuilder.STRING_VALUE);
        assertEquals("testString", resultObj);
    }

    @Test
    public void testCreateValueWithObject() throws Throwable {
        Object result = TypeHandler.createValue("java.util.Date", PatternOptionBuilder.OBJECT_VALUE);
        assertNotNull(result);
        assertTrue(result instanceof Date);
    }

    @Test
    public void testCreateValueWithNumber() throws Throwable {
        Object result = TypeHandler.createValue("123", PatternOptionBuilder.NUMBER_VALUE);
        assertNotNull(result);
        assertTrue(result instanceof Number);
        assertEquals(Long.valueOf(123L), result);

        Object doubleResult = TypeHandler.createValue("123.45", PatternOptionBuilder.NUMBER_VALUE);
        assertNotNull(doubleResult);
        assertTrue(doubleResult instanceof Double);
        assertEquals(Double.valueOf(123.45), doubleResult);
    }

    @Test
    public void testCreateValueWithDate() throws Throwable {
        Object result = TypeHandler.createValue("anyDateStr", PatternOptionBuilder.DATE_VALUE);
        assertNull(result);
    }

    @Test
    public void testCreateValueWithClass() throws Throwable {
        Object result = TypeHandler.createValue("java.util.Date", PatternOptionBuilder.CLASS_VALUE);
        assertNotNull(result);
        assertEquals(Date.class, result);
    }

    @Test
    public void testCreateValueWithFile() throws Throwable {
        Object result = TypeHandler.createValue("testFile.txt", PatternOptionBuilder.FILE_VALUE);
        assertNotNull(result);
        assertTrue(result instanceof File);
        assertEquals("testFile.txt", ((File) result).getName());
    }

    @Test
    public void testCreateValueWithExistingFile() throws Throwable {
        Object result = TypeHandler.createValue("testFile.txt", PatternOptionBuilder.EXISTING_FILE_VALUE);
        assertNotNull(result);
        assertTrue(result instanceof File);
        assertEquals("testFile.txt", ((File) result).getName());
    }

    @Test
    public void testCreateValueWithFiles() throws Throwable {
        Object result = TypeHandler.createValue("testFiles", PatternOptionBuilder.FILES_VALUE);
        assertNull(result);
    }

    @Test
    public void testCreateValueWithURL() throws Throwable {
        Object result = TypeHandler.createValue("http://localhost/", PatternOptionBuilder.URL_VALUE);
        assertNotNull(result);
        assertTrue(result instanceof URL);
    }

    @Test
    public void testCreateValueWithUnknownType() throws Throwable {
        Class unknownClass = String.class;
        Object result = TypeHandler.createValue("test", unknownClass);
        assertNull(result);
    }

    @Test
    public void testCreateObjectValid() throws Throwable {
        Object result = TypeHandler.createObject("java.util.Date");
        assertNotNull(result);
        assertTrue(result instanceof Date);
    }

    @Test
    public void testCreateObjectClassNotFound() throws Throwable {
        Object result = TypeHandler.createObject("non.existent.ClassName");
        assertNull(result);
    }

    @Test
    public void testCreateObjectInstantiationException() throws Throwable {
        // Abstract class or interface will cause InstantiationException
        Object result = TypeHandler.createObject("java.util.List");
        assertNull(result);
    }

    @Test
    public void testCreateObjectIllegalAccessException() throws Throwable {
        // Class with private constructor
        Object result = TypeHandler.createObject("org.apache.commons.cli.TypeHandlerTest$PrivateConstructorClass");
        assertNull(result);
    }

    @Test
    public void testCreateNumberValidLong() throws Throwable {
        Number result = TypeHandler.createNumber("456");
        assertNotNull(result);
        assertEquals(Long.valueOf(456L), result);
    }

    @Test
    public void testCreateNumberValidDouble() throws Throwable {
        Number result = TypeHandler.createNumber("456.78");
        assertNotNull(result);
        assertEquals(Double.valueOf(456.78), result);
    }

    @Test
    public void testCreateNumberInvalid() throws Throwable {
        Number result = TypeHandler.createNumber("notANumber");
        assertNull(result);
    }

    @Test
    public void testCreateClassValid() throws Throwable {
        Class result = TypeHandler.createClass("java.util.Date");
        assertNotNull(result);
        assertEquals(Date.class, result);
    }

    @Test
    public void testCreateClassInvalid() throws Throwable {
        Class result = TypeHandler.createClass("non.existent.ClassName");
        assertNull(result);
    }

    @Test
    public void testCreateDate() throws Throwable {
        Date result = TypeHandler.createDate("2023-01-01");
        assertNull(result);
    }

    @Test
    public void testCreateURLValid() throws Throwable {
        URL result = TypeHandler.createURL("http://www.apache.org");
        assertNotNull(result);
        assertEquals("http", result.getProtocol());
    }

    @Test
    public void testCreateURLInvalid() throws Throwable {
        URL result = TypeHandler.createURL("invalid-url");
        assertNull(result);
    }

    @Test
    public void testCreateFile() throws Throwable {
        File result = TypeHandler.createFile("dummyPath");
        assertNotNull(result);
        assertEquals("dummyPath", result.getPath());
    }

    @Test
    public void testCreateFiles() throws Throwable {
        File[] result = TypeHandler.createFiles("dummyPath");
        assertNull(result);
    }

    private static class PrivateConstructorClass {
        private PrivateConstructorClass() {
        }
    }
}