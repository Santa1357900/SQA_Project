package org.apache.commons.cli;

import org.junit.Test;
import static org.junit.Assert.*;

import java.io.File;
import java.net.URL;
import java.util.ArrayList;

public class TypeHandlerClaudeTest
{
    // createValue(String, Object) with STRING_VALUE -> should delegate and return the string unchanged
    @Test
    public void testCreateValueObjectOverload_stringType_returnsStringUnchanged() throws Throwable {
        Object typeObj = PatternOptionBuilder.STRING_VALUE;
        Object result = TypeHandler.createValue("hello", typeObj);
        assertEquals("hello", result);
    }

    // createValue(String, Object) with NUMBER_VALUE -> should delegate to createNumber
    @Test
    public void testCreateValueObjectOverload_numberType_returnsNumber() throws Throwable {
        Object typeObj = PatternOptionBuilder.NUMBER_VALUE;
        Object result = TypeHandler.createValue("42", typeObj);
        assertTrue(result instanceof Long);
        assertEquals(42L, ((Long) result).longValue());
    }

    // createValue(String, Class): STRING_VALUE branch returns the same string
    @Test
    public void testCreateValueClass_stringValue_returnsSameString() throws Throwable {
        Object result = TypeHandler.createValue("sample text", PatternOptionBuilder.STRING_VALUE);
        assertEquals("sample text", result);
    }

    // createValue(String, Class): OBJECT_VALUE branch creates an instance via no-arg constructor
    @Test
    public void testCreateValueClass_objectValue_createsObjectInstance() throws Throwable {
        Object result = TypeHandler.createValue("java.util.ArrayList", PatternOptionBuilder.OBJECT_VALUE);
        assertTrue(result instanceof ArrayList);
    }

    // createValue(String, Class): NUMBER_VALUE branch with a decimal returns Double
    @Test
    public void testCreateValueClass_numberValueWithDecimal_returnsDouble() throws Throwable {
        Object result = TypeHandler.createValue("3.14", PatternOptionBuilder.NUMBER_VALUE);
        assertTrue(result instanceof Double);
        assertEquals(3.14, ((Double) result).doubleValue(), 1e-9);
    }

    // createValue(String, Class): NUMBER_VALUE branch without a decimal returns Long
    @Test
    public void testCreateValueClass_numberValueWithoutDecimal_returnsLong() throws Throwable {
        Object result = TypeHandler.createValue("123", PatternOptionBuilder.NUMBER_VALUE);
        assertTrue(result instanceof Long);
        assertEquals(123L, ((Long) result).longValue());
    }

    // createValue(String, Class): DATE_VALUE branch delegates to createDate which always throws
    @Test
    public void testCreateValueClass_dateValue_throwsUnsupportedOperationException() throws Throwable {
        try
        {
            TypeHandler.createValue("2020-01-01", PatternOptionBuilder.DATE_VALUE);
            fail("expected UnsupportedOperationException");
        }
        catch (UnsupportedOperationException expected)
        {
        }
    }

    // createValue(String, Class): CLASS_VALUE branch resolves the named class
    @Test
    public void testCreateValueClass_classValue_returnsClassObject() throws Throwable {
        Object result = TypeHandler.createValue("java.lang.String", PatternOptionBuilder.CLASS_VALUE);
        assertEquals(String.class, result);
    }

    // createValue(String, Class): FILE_VALUE branch wraps the path in a File
    @Test
    public void testCreateValueClass_fileValue_returnsFile() throws Throwable {
        Object result = TypeHandler.createValue("test.txt", PatternOptionBuilder.FILE_VALUE);
        assertTrue(result instanceof File);
        assertEquals("test.txt", ((File) result).getPath());
    }

    // createValue(String, Class): FILES_VALUE branch delegates to createFiles which always throws
    @Test
    public void testCreateValueClass_filesValue_throwsUnsupportedOperationException() throws Throwable {
        try
        {
            TypeHandler.createValue("a.txt,b.txt", PatternOptionBuilder.FILES_VALUE);
            fail("expected UnsupportedOperationException");
        }
        catch (UnsupportedOperationException expected)
        {
        }
    }

    // createValue(String, Class): URL_VALUE branch parses a well-formed URL
    @Test
    public void testCreateValueClass_urlValue_returnsURLObject() throws Throwable {
        Object result = TypeHandler.createValue("http://apache.org", PatternOptionBuilder.URL_VALUE);
        assertTrue(result instanceof URL);
        assertEquals("http", ((URL) result).getProtocol());
    }



    // createObject: valid classname with a no-arg constructor is instantiated
    @Test
    public void testCreateObject_validClassname_returnsInstance() throws Throwable {
        Object result = TypeHandler.createObject("java.util.ArrayList");
        assertTrue(result instanceof ArrayList);
        assertEquals(0, ((ArrayList) result).size());
    }

    // createObject: classname that cannot be found throws ParseException
    @Test
    public void testCreateObject_invalidClassname_throwsParseException() throws Throwable {
        try
        {
            TypeHandler.createObject("org.apache.commons.cli.NoSuchClassXYZ123");
            fail("expected ParseException");
        }
        catch (ParseException expected)
        {
        }
    }

    // createObject: a class without a usable no-arg constructor (interface) throws ParseException
    @Test
    public void testCreateObject_nonInstantiableClass_throwsParseException() throws Throwable {
        try
        {
            TypeHandler.createObject("java.util.Map");
            fail("expected ParseException");
        }
        catch (ParseException expected)
        {
        }
    }

    // createNumber: plain integer string returns a Long per the javadoc contract
    @Test
    public void testCreateNumber_integerString_returnsLong() throws Throwable {
        Number result = TypeHandler.createNumber("123");
        assertTrue(result instanceof Long);
        assertEquals(123L, result.longValue());
    }

    // createNumber: string containing a dot returns a Double per the javadoc contract
    @Test
    public void testCreateNumber_decimalString_returnsDouble() throws Throwable {
        Number result = TypeHandler.createNumber("123.45");
        assertTrue(result instanceof Double);
        assertEquals(123.45, result.doubleValue(), 1e-9);
    }

    // createNumber: negative integer string returns Long
    @Test
    public void testCreateNumber_negativeInteger_returnsLong() throws Throwable {
        Number result = TypeHandler.createNumber("-42");
        assertTrue(result instanceof Long);
        assertEquals(-42L, result.longValue());
    }

    // createNumber: string starting with a dot is a valid Double per Double.valueOf contract
    @Test
    public void testCreateNumber_leadingDotDecimal_returnsDouble() throws Throwable {
        Number result = TypeHandler.createNumber(".5");
        assertTrue(result instanceof Double);
        assertEquals(0.5, result.doubleValue(), 1e-9);
    }

    // createNumber: non-numeric string throws ParseException
    @Test
    public void testCreateNumber_invalidString_throwsParseException() throws Throwable {
        try
        {
            TypeHandler.createNumber("abc");
            fail("expected ParseException");
        }
        catch (ParseException expected)
        {
        }
    }

    // createNumber: empty string is not a number, throws ParseException
    @Test
    public void testCreateNumber_emptyString_throwsParseException() throws Throwable {
        try
        {
            TypeHandler.createNumber("");
            fail("expected ParseException");
        }
        catch (ParseException expected)
        {
        }
    }

    // createNumber: integer literal exceeding Long range without a dot throws ParseException
    @Test
    public void testCreateNumber_overflowLongWithoutDot_throwsParseException() throws Throwable {
        try
        {
            TypeHandler.createNumber("99999999999999999999");
            fail("expected ParseException");
        }
        catch (ParseException expected)
        {
        }
    }

    // createClass: valid fully qualified classname resolves successfully
    @Test
    public void testCreateClass_validClassname_returnsClassObject() throws Throwable {
        Class<?> result = TypeHandler.createClass("java.lang.String");
        assertEquals(String.class, result);
    }

    // createClass: unknown classname throws ParseException
    @Test
    public void testCreateClass_invalidClassname_throwsParseException() throws Throwable {
        try
        {
            TypeHandler.createClass("org.apache.commons.cli.NoSuchClassXYZ123");
            fail("expected ParseException");
        }
        catch (ParseException expected)
        {
        }
    }

    // createDate: per its javadoc, always throws UnsupportedOperationException regardless of input
    @Test
    public void testCreateDate_anyString_throwsUnsupportedOperationException() throws Throwable {
        try
        {
            TypeHandler.createDate("2020-01-01");
            fail("expected UnsupportedOperationException");
        }
        catch (UnsupportedOperationException expected)
        {
        }
    }

    // createURL: well-formed URL string is parsed successfully
    @Test
    public void testCreateURL_validURL_returnsURLObject() throws Throwable {
        URL result = TypeHandler.createURL("http://apache.org");
        assertEquals("http", result.getProtocol());
        assertEquals("apache.org", result.getHost());
    }

    // createURL: malformed URL string (no protocol) throws ParseException
    @Test
    public void testCreateURL_malformedURL_throwsParseException() throws Throwable {
        try
        {
            TypeHandler.createURL("not a url");
            fail("expected ParseException");
        }
        catch (ParseException expected)
        {
        }
    }

    // createFile: wraps the given path into a File object without touching the filesystem
    @Test
    public void testCreateFile_returnsFileWithGivenPath() throws Throwable {
        File result = TypeHandler.createFile("somefile.txt");
        assertEquals("somefile.txt", result.getPath());
    }

    // openFile: nonexistent file path causes ParseException per the javadoc contract
    @Test
    public void testOpenFile_nonexistentFile_throwsParseException() throws Throwable {
        try
        {
            TypeHandler.openFile("/nonexistent_dir_for_cli_test_xyz/afile.txt");
            fail("expected ParseException");
        }
        catch (ParseException expected)
        {
        }
    }

    // createFiles: per its javadoc, always throws UnsupportedOperationException regardless of input
    @Test
    public void testCreateFiles_anyString_throwsUnsupportedOperationException() throws Throwable {
        try
        {
            TypeHandler.createFiles("a.txt,b.txt");
            fail("expected UnsupportedOperationException");
        }
        catch (UnsupportedOperationException expected)
        {
        }
    }
}
