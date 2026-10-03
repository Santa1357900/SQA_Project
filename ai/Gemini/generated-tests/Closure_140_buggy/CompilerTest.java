package com.google.javascript.jscomp;

import com.google.javascript.rhino.Node;
import com.google.javascript.rhino.Token;
import org.junit.Test;

import java.io.PrintStream;
import java.util.logging.Level;

import static org.junit.Assert.*;

public class CompilerTest {

    @Test
    public void testDefaultConstructor() throws Throwable {
        Compiler compiler = new Compiler();
        assertNotNull(compiler);
        assertNull(compiler.getErrorManager());
    }

    @Test
    public void testPrintStreamConstructor() throws Throwable {
        Compiler compiler = new Compiler((PrintStream) null);
        assertNotNull(compiler);
    }

    @Test
    public void testErrorManagerConstructor() throws Throwable {
        LoggerErrorManager errorManager = new LoggerErrorManager(
            new CheckLevelErrorsFormatter(new Compiler(), CheckLevel.ERROR),
            java.util.logging.Logger.getLogger("test")
        );
        Compiler compiler = new Compiler(errorManager);
        assertNotNull(compiler);
        assertEquals(errorManager, compiler.getErrorManager());
    }

    @Test
    public void testSetErrorManagerNull() throws Throwable {
        Compiler compiler = new Compiler();
        try {
            compiler.setErrorManager(null);
            fail("Expected NullPointerException");
        } catch (NullPointerException e) {
            assertTrue(e.getMessage().contains("the error manager cannot be null"));
        }
    }

    @Test
    public void testDisableThreads() throws Throwable {
        Compiler compiler = new Compiler();
        compiler.disableThreads();
        // Trigger compilation with disabled threads to cover that branch
        CompilerOptions options = new CompilerOptions();
        JSSourceFile extern = JSSourceFile.fromCode("extern.js", "");
        JSSourceFile input = JSSourceFile.fromCode("input.js", "var x = 1;");
        Result result = compiler.compile(extern, input, options);
        assertNotNull(result);
    }

    @Test
    public void testGetSourceLineInvalidLine() throws Throwable {
        Compiler compiler = new Compiler();
        String line = compiler.getSourceLine("nonexistent", -1);
        assertNull(line);
        String lineZero = compiler.getSourceLine("nonexistent", 0);
        assertNull(lineZero);
    }

    @Test
    public void testGetSourceRegionInvalidLine() throws Throwable {
        Compiler compiler = new Compiler();
        Region region = compiler.getSourceRegion("nonexistent", 0);
        assertNull(region);
        Region regionNeg = compiler.getSourceRegion("nonexistent", -5);
        assertNull(regionNeg);
    }

    @Test
    public void testResetUniqueNameId() throws Throwable {
        Compiler compiler = new Compiler();
        compiler.resetUniqueNameId();
        // Verify via supplier behavior indirectly or just execute method
        assertNotNull(compiler.getUniqueNameIdSupplier());
    }

    @Test
    public void testSetLoggingLevel() throws Throwable {
        Compiler.setLoggingLevel(Level.WARNING);
        // No exception should be thrown
    }

    @Test
    public void testGetAstDotGraphNullRoot() throws Throwable {
        Compiler compiler = new Compiler();
        String dot = compiler.getAstDotGraph();
        assertEquals("", dot);
    }

    @Test
    public void testCodeBuilderOperations() throws Throwable {
        Compiler.CodeBuilder cb = new Compiler.CodeBuilder();
        assertEquals(0, cb.getLength());
        assertEquals("", cb.toString());

        cb.append("line1\nline2");
        assertTrue(cb.getLength() > 0);
        assertTrue(cb.endsWith("line2"));
        assertEquals(1, cb.getLineIndex());
        assertEquals(5, cb.getColumnIndex());

        cb.reset();
        assertEquals(0, cb.getLength());
    }

    @Test
    public void testCompileMultipleInputsAndErrors() throws Throwable {
        Compiler compiler = new Compiler();
        CompilerOptions options = new CompilerOptions();
        JSSourceFile extern = JSSourceFile.fromCode("extern.js", "");
        JSSourceFile[] inputs = new JSSourceFile[] {
            JSSourceFile.fromCode("in1.js", "var a = 1;"),
            JSSourceFile.fromCode("in1.js", "var a = 2;") // Duplicate input name trigger
        };
        Result result = compiler.compile(extern, inputs, options);
        assertNotNull(result);
        assertTrue(compiler.hasErrors());
    }

    @Test
    public void testModuleCompilationErrors() throws Throwable {
        Compiler compiler = new Compiler();
        CompilerOptions options = new CompilerOptions();
        JSSourceFile extern = JSSourceFile.fromCode("extern.js", "");
        JSModule[] modules = new JSModule[0]; // Empty modules list
        Result result = compiler.compile(extern, modules, options);
        assertNotNull(result);
    }

    @Test
    public void testParseSyntheticCode() throws Throwable {
        Compiler compiler = new Compiler();
        Node node = compiler.parseSyntheticCode("var x = 10;");
        assertNotNull(node);
        Node namedNode = compiler.parseSyntheticCode("filename.js", "var y = 20;");
        assertNotNull(namedNode);
    }

    @Test
    public void testToSourceWithoutRoot() throws Throwable {
        Compiler compiler = new Compiler();
        String source = compiler.toSource();
        assertEquals("", source);
    }

    @Test
    public void testGettersAndSetters() throws Throwable {
        Compiler compiler = new Compiler();
        assertNotNull(compiler.getTypeRegistry());
        assertNotNull(compiler.getTypeValidator());
        assertNotNull(compiler.getCodingConvention());
        assertNotNull(compiler.getParserConfig());
        assertFalse(compiler.isTypeCheckingEnabled());
        assertFalse(compiler.isIdeMode());
        assertNull(compiler.getSourceMap());
    }
}