package org.apache.commons.cli;

import org.junit.Test;
import static org.junit.Assert.*;

import java.io.File;
import java.net.URL;

public class TypeHandlerClaudeTest
{
    // covers createValue(String,Object) -> STRING_VALUE branch, object overload delegation
    @Test
    public void testCreateValueObject_stringType_returnsStringUnchanged() throws Throwable {
        Object type = PatternOptionBuilder.STRING_VALUE;
        Object result = TypeHandler.createValue("hello world", type);
        assertEquals("hello world", result);
    }

    // covers createValue(String,Object) -> OBJECT_VALUE branch, successful instantiation
    @Test
    public void testCreateValueObject_objectType_returnsInstance() throws Throwable {
        Object type = PatternOptionBuilder.OBJECT_VALUE;
        Object result = TypeHandler.createValue("java.lang.Object", type);
        assertNotNull(result);
        assertEquals("java.lang.Object", result.getClass().getName());
    }

    // covers createValue(String,Class) -> OBJECT_VALUE branch, invalid classname propagates ParseException
    @Test
    public void testCreateValueClass_objectType_invalidClassname_throwsParseException() throws Throwable {
        Class<?> type = PatternOptionBuilder.OBJECT_VALUE;
        try
        {
            TypeHandler.createValue("no.such.ClassXyz", type);
            fail("expected ParseException");
        }
        catch (ParseException expected)
        {
            assertTrue(expected.getMessage().contains("Unable to find the class"));
        }
    }

    // covers createValue(String,Class) -> NUMBER_VALUE branch with a dot -> Double
    @Test
    public void testCreateValueClass_numberType_withDot_returnsDouble() throws Throwable {
        Class<?> type = PatternOptionBuilder.NUMBER_VALUE;
        Object result = TypeHandler.createValue("2.5", type);
        assertTrue(result instanceof Double);
        assertEquals(2.5, ((Number) result).doubleValue(), 1e-9);
    }

    // covers createValue(String,Class) -> NUMBER_VALUE branch without a dot -> Long
    @Test
    public void testCreateValueClass_numberType_withoutDot_returnsLong() throws Throwable {
        Class<?> type = PatternOptionBuilder.NUMBER_VALUE;
        Object result = TypeHandler.createValue("42", type);
        assertTrue(result instanceof Long);
        assertEquals(Long.valueOf(42L), result);
    }

    // covers createValue(String,Object) delegation path for NUMBER_VALUE
    @Test
    public void testCreateValueObject_numberType_delegatesToCreateNumber() throws Throwable {
        Object type = PatternOptionBuilder.NUMBER_VALUE;
        Object result = TypeHandler.createValue("7", type);
        assertEquals(Long.valueOf(7L), result);
    }

    // covers createValue -> DATE_VALUE branch, always throws per contract
    @Test
    public void testCreateValueClass_dateType_throwsUnsupportedOperationException() throws Throwable {
        Class<?> type = PatternOptionBuilder.DATE_VALUE;
        try
        {
            TypeHandler.createValue("2020-01-01", type);
            fail("expected UnsupportedOperationException");
        }
        catch (UnsupportedOperationException expected)
        {
            // ok
        }
    }

    // covers createValue -> CLASS_VALUE branch
    @Test
    public void testCreateValueClass_classType_returnsClassObject() throws Throwable {
        Class<?> type = PatternOptionBuilder.CLASS_VALUE;
        Object result = TypeHandler.createValue("java.lang.String", type);
        assertEquals(String.class, result);
    }

    // covers createValue -> FILE_VALUE branch
    @Test
    public void testCreateValueClass_fileType_returnsFile() throws Throwable {
        Class<?> type = PatternOptionBuilder.FILE_VALUE;
        Object result = TypeHandler.createValue("data.csv", type);
        assertTrue(result instanceof File);
        assertEquals("data.csv", ((File) result).getName());
    }

    // covers createValue -> EXISTING_FILE_VALUE branch
    @Test
    public void testCreateValueClass_existingFileType_returnsFile() throws Throwable {
        Class<?> type = PatternOptionBuilder.EXISTING_FILE_VALUE;
        Object result = TypeHandler.createValue("existing.txt", type);
        assertTrue(result instanceof File);
        assertEquals("existing.txt", ((File) result).getName());
    }

    // covers createValue -> FILES_VALUE branch, always throws per contract
    @Test
    public void testCreateValueClass_filesType_throwsUnsupportedOperationException() throws Throwable {
        Class<?> type = PatternOptionBuilder.FILES_VALUE;
        try
        {
            TypeHandler.createValue("a.txt,b.txt", type);
            fail("expected UnsupportedOperationException");
        }
        catch (UnsupportedOperationException expected)
        {
            // ok
        }
    }

    // covers createValue -> URL_VALUE branch, valid url
    @Test
    public void testCreateValueClass_urlType_returnsURL() throws Throwable {
        Class<?> type = PatternOptionBuilder.URL_VALUE;
        Object result = TypeHandler.createValue("http://apache.org/path", type);
        assertTrue(result instanceof URL);
        URL url = (URL) result;
        assertEquals("http", url.getProtocol());
        assertEquals("apache.org", url.getHost());
    }

    // covers createValue -> URL_VALUE branch, malformed url propagates ParseException
    @Test
    public void testCreateValueClass_urlType_malformed_throwsParseException() throws Throwable {
        Class<?> type = PatternOptionBuilder.URL_VALUE;
        try
        {
            TypeHandler.createValue("not a url", type);
            fail("expected ParseException");
        }
        catch (ParseException expected)
        {
            assertTrue(expected.getMessage().contains("Unable to parse the URL"));
        }
    }

    // covers createValue -> default else branch for an unrecognized type marker
    @Test
    public void testCreateValueClass_unknownType_returnsNull() throws Throwable {
        Class<?> type = Boolean.class;
        Object result = TypeHandler.createValue("true", type);
        assertNull(result);
    }

    // covers createObject with a valid classname having a no-arg constructor
    @Test
    public void testCreateObject_validClassname_returnsInstance() throws Throwable {
        Object result = TypeHandler.createObject("java.lang.Object");
        assertNotNull(result);
        assertEquals("java.lang.Object", result.getClass().getName());
    }

    // covers createObject ClassNotFoundException path
    @Test
    public void testCreateObject_invalidClassname_throwsParseException() throws Throwable {
        try
        {
            TypeHandler.createObject("no.such.Class.Here");
            fail("expected ParseException");
        }
        catch (ParseException expected)
        {
            assertTrue(expected.getMessage().contains("Unable to find the class"));
        }
    }

    // covers createObject instantiation failure path (abstract class has no accessible ctor)
    @Test
    public void testCreateObject_abstractClass_throwsParseException() throws Throwable {
        try
        {
            TypeHandler.createObject("java.lang.Number");
            fail("expected ParseException");
        }
        catch (ParseException expected)
        {
            assertTrue(expected.getMessage().contains("Unable to create an instance of"));
        }
    }

    // covers createNumber branch with a dot -> Double
    @Test
    public void testCreateNumber_withDot_returnsDouble() throws Throwable {
        Number result = TypeHandler.createNumber("3.14");
        assertTrue(result instanceof Double);
        assertEquals(3.14, result.doubleValue(), 1e-9);
    }

    // covers createNumber branch without a dot -> Long
    @Test
    public void testCreateNumber_withoutDot_returnsLong() throws Throwable {
        Number result = TypeHandler.createNumber("100");
        assertTrue(result instanceof Long);
        assertEquals(Long.valueOf(100L), result);
    }

    // covers createNumber with a negative integer, no dot
    @Test
    public void testCreateNumber_negativeInteger_returnsLong() throws Throwable {
        Number result = TypeHandler.createNumber("-42");
        assertEquals(Long.valueOf(-42L), result);
    }

    // covers createNumber NumberFormatException wrapping into ParseException
    @Test
    public void testCreateNumber_invalidString_throwsParseException() throws Throwable {
        try
        {
            TypeHandler.createNumber("not-a-number");
            fail("expected ParseException");
        }
        catch (ParseException expected)
        {
            // ok, exception type is what matters
        }
    }

    // covers createNumber boundary value at Long.MAX_VALUE (no dot)
    @Test
    public void testCreateNumber_longMaxValue_returnsLong() throws Throwable {
        Number result = TypeHandler.createNumber(String.valueOf(Long.MAX_VALUE));
        assertEquals(Long.valueOf(Long.MAX_VALUE), result);
    }

    // covers createNumber overflow beyond Long.MAX_VALUE without a dot -> ParseException per "otherwise a Long" contract
    @Test
    public void testCreateNumber_overflowWithoutDot_throwsParseException() throws Throwable {
        try
        {
            TypeHandler.createNumber("9223372036854775808");
            fail("expected ParseException");
        }
        catch (ParseException expected)
        {
            // ok
        }
    }

    // covers createNumber with only a dot character -> invalid Double -> ParseException
    @Test
    public void testCreateNumber_dotOnly_throwsParseException() throws Throwable {
        try
        {
            TypeHandler.createNumber(".");
            fail("expected ParseException");
        }
        catch (ParseException expected)
        {
            // ok
        }
    }

    // covers createNumber with trailing dot, valid Double per Java parsing rules
    @Test
    public void testCreateNumber_trailingDot_returnsDouble() throws Throwable {
        Number result = TypeHandler.createNumber("5.");
        assertTrue(result instanceof Double);
        assertEquals(5.0, result.doubleValue(), 1e-9);
    }

    // covers createClass valid classname
    @Test
    public void testCreateClass_validClassname_returnsClass() throws Throwable {
        Class<?> result = TypeHandler.createClass("java.util.ArrayList");
        assertEquals(java.util.ArrayList.class, result);
    }

    // covers createClass ClassNotFoundException path
    @Test
    public void testCreateClass_invalidClassname_throwsParseException() throws Throwable {
        try
        {
            TypeHandler.createClass("no.such.ClassAtAll");
            fail("expected ParseException");
        }
        catch (ParseException expected)
        {
            assertTrue(expected.getMessage().contains("Unable to find the class"));
        }
    }

    // covers createDate contract: always throws UnsupportedOperationException
    @Test
    public void testCreateDate_anyString_throwsUnsupportedOperationException() throws Throwable {
        try
        {
            TypeHandler.createDate("2020-01-01");
            fail("expected UnsupportedOperationException");
        }
        catch (UnsupportedOperationException expected)
        {
            // ok
        }
    }

    // covers createURL success path with protocol and host verification
    @Test
    public void testCreateURL_validURL_returnsURLWithCorrectFields() throws Throwable {
        URL url = TypeHandler.createURL("http://example.com/index.html");
        assertEquals("http", url.getProtocol());
        assertEquals("example.com", url.getHost());
        assertEquals("/index.html", url.getFile());
    }

    // covers createURL MalformedURLException path (unknown protocol)
    @Test
    public void testCreateURL_unknownProtocol_throwsParseException() throws Throwable {
        try
        {
            TypeHandler.createURL("htp://bad-protocol");
            fail("expected ParseException");
        }
        catch (ParseException expected)
        {
            assertTrue(expected.getMessage().contains("Unable to parse the URL"));
        }
    }

    // covers createURL MalformedURLException path (no protocol at all)
    @Test
    public void testCreateURL_noProtocol_throwsParseException() throws Throwable {
        try
        {
            TypeHandler.createURL("just a plain string");
            fail("expected ParseException");
        }
        catch (ParseException expected)
        {
            assertTrue(expected.getMessage().contains("Unable to parse the URL"));
        }
    }

    // covers createFile returns a File wrapping the given path
    @Test
    public void testCreateFile_returnsFileWithGivenPath() throws Throwable {
        File f = TypeHandler.createFile("some/path/test.txt");
        assertEquals("test.txt", f.getName());
    }

    // covers createFile with empty string path boundary case
    @Test
    public void testCreateFile_emptyString_returnsFileWithEmptyPath() throws Throwable {
        File f = TypeHandler.createFile("");
        assertEquals("", f.getPath());
    }

    // covers createFiles contract: always throws UnsupportedOperationException
    @Test
    public void testCreateFiles_anyString_throwsUnsupportedOperationException() throws Throwable {
        try
        {
            TypeHandler.createFiles("a.txt,b.txt");
            fail("expected UnsupportedOperationException");
        }
        catch (UnsupportedOperationException expected)
        {
            // ok
        }
    }
}
