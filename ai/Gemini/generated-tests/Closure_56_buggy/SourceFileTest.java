package com.google.javascript.jscomp;

import org.junit.Test;
import static org.junit.Assert.*;

import java.io.ByteArrayInputStream;
import java.io.File;
import java.io.StringReader;
import java.nio.charset.Charset;

public class SourceFileTest {

    @Test
    public void testConstructorValidation() throws Throwable {
        try {
            new SourceFile(null);
            fail("Expected IllegalArgumentException for null file name");
        } catch (IllegalArgumentException e) {
            assertTrue(e.getMessage().contains("a source must have a name"));
        }

        try {
            new SourceFile("");
            fail("Expected IllegalArgumentException for empty file name");
        } catch (IllegalArgumentException e) {
            assertTrue(e.getMessage().contains("a source must have a name"));
        }
    }

    @Test
    public void testPreloadedBasicProperties() throws Throwable {
        SourceFile file = SourceFile.fromCode("test.js", "var x = 1;\nvar y = 2;");
        assertEquals("test.js", file.getName());
        assertEquals("test.js", file.getOriginalPath());
        assertFalse(file.isExtern());
        assertTrue(file.hasSourceInMemory());
        assertEquals("var x = 1;\nvar y = 2;", file.getCode());
        assertEquals("test.js", file.toString());

        file.setIsExtern(true);
        assertTrue(file.isExtern());

        file.setOriginalPath("original.js");
        assertEquals("original.js", file.getOriginalPath());
    }

    @Test
    public void testPreloadedWithOriginalPath() throws Throwable {
        SourceFile file = SourceFile.fromCode("test.js", "orig.js", "code");
        assertEquals("test.js", file.getName());
        assertEquals("orig.js", file.getOriginalPath());
        assertEquals("code", file.getCode());
    }

    @Test
    public void testLineAndOffsetHandling() throws Throwable {
        String code = "line1\nline2\nline3";
        SourceFile file = SourceFile.fromCode("lines.js", code);

        assertEquals(3, file.getNumLines());
        assertEquals(0, file.getLineOffset(1));
        assertEquals(6, file.getLineOffset(2));
        assertEquals(12, file.getLineOffset(3));

        assertEquals("line1", file.getLine(1));
        assertEquals("line2", file.getLine(2));
        assertEquals("line3", file.getLine(3));
        assertNull(file.getLine(4));

        // Test backward/forward line queries and cached offsets
        assertEquals("line3", file.getLine(3));
        assertEquals("line1", file.getLine(1));

        try {
            file.getLineOffset(0);
            fail("Expected IllegalArgumentException for line 0");
        } catch (IllegalArgumentException e) {
            assertTrue(e.getMessage().contains("Expected line number"));
        }

        try {
            file.getLineOffset(5);
            fail("Expected IllegalArgumentException for out-of-bounds line");
        } catch (IllegalArgumentException e) {
            assertTrue(e.getMessage().contains("Expected line number"));
        }
    }

    @Test
    public void testRegions() throws Throwable {
        String code = "1\n2\n3\n4\n5\n6\n7\n8\n9\n10";
        SourceFile file = SourceFile.fromCode("region.js", code);

        Region region = file.getRegion(5);
        assertNotNull(region);
        assertEquals(3, region.getBeginningLineNumber());
        assertEquals(7, region.getEndingLineNumber());
        assertNotNull(region.getSourceExcerpt());

        // Test region near start
        Region startRegion = file.getRegion(1);
        assertNotNull(startRegion);

        // Test region near end
        Region endRegion = file.getRegion(10);
        assertNotNull(endRegion);

        // Test out of bounds region
        assertNull(file.getRegion(20));
    }

    @Test
    public void testGeneratedSourceFile() throws Throwable {
        SourceFile.Generator generator = new SourceFile.Generator() {
            public String getCode() {
                return "generated code";
            }
        };

        SourceFile file = SourceFile.fromGenerator("gen.js", generator);
        assertEquals("gen.js", file.getName());
        assertFalse(file.hasSourceInMemory());
        assertEquals("generated code", file.getCode());
        assertTrue(file.hasSourceInMemory());

        file.clearCachedSource();
        assertFalse(file.hasSourceInMemory());
        assertEquals("generated code", file.getCode());
    }

    @Test
    public void testOnDiskSourceFile() throws Throwable {
        File tempFile = File.createTempFile("closure-test", ".js");
        tempFile.deleteOnExit();
        java.io.FileWriter writer = new java.io.FileWriter(tempFile);
        writer.write("disk code line 1\ndisk code line 2");
        writer.close();

        SourceFile file = SourceFile.fromFile(tempFile, Charset.forName("UTF-8"));
        assertEquals(tempFile.getPath(), file.getName());
        assertFalse(file.hasSourceInMemory());
        assertEquals("disk code line 1\ndisk code line 2", file.getCode());
        assertTrue(file.hasSourceInMemory());

        assertNotNull(file.getCodeReader());
        
        file.clearCachedSource();
        assertFalse(file.hasSourceInMemory());

        // Test constructor without charset
        SourceFile file2 = SourceFile.fromFile(tempFile);
        assertEquals(tempFile.getPath(), file2.getName());
        assertEquals(Charset.forName("UTF-8"), ((SourceFile.OnDisk) file2).getCharset());
        
        ((SourceFile.OnDisk) file2).setCharset(Charset.forName("ISO-8859-1"));
        assertEquals(Charset.forName("ISO-8859-1"), ((SourceFile.OnDisk) file2).getCharset());

        // Test OnDisk getCodeReader when code is not in memory
        assertFalse(file2.hasSourceInMemory());
        assertNotNull(file2.getCodeReader());
    }

    @Test
    public void testInputStreamAndReaderFactories() throws Throwable {
        String content = "stream content";
        ByteArrayInputStream bais = new ByteArrayInputStream(content.getBytes("UTF-8"));
        SourceFile file1 = SourceFile.fromInputStream("stream.js", bais);
        assertEquals(content, file1.getCode());

        ByteArrayInputStream bais2 = new ByteArrayInputStream(content.getBytes("UTF-8"));
        SourceFile file2 = SourceFile.fromInputStream("stream2.js", "orig-stream.js", bais2);
        assertEquals(content, file2.getCode());
        assertEquals("orig-stream.js", file2.getOriginalPath());

        StringReader reader = new StringReader("reader content");
        SourceFile file3 = SourceFile.fromReader("reader.js", reader);
        assertEquals("reader content", file3.getCode());
    }
}