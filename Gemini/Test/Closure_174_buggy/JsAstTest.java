package com.google.javascript.jscomp;

import com.google.javascript.rhino.InputId;
import com.google.javascript.rhino.Node;
import org.junit.Test;

import static org.junit.Assert.*;

public class JsAstTest {

  @Test
  public void testJsAstCreation() throws Throwable {
    SourceFile sourceFile = SourceFile.fromCode("test.js", "var x = 1;");
    JsAst jsAst = new JsAst(sourceFile);

    assertNotNull(jsAst);
    assertEquals("test.js", jsAst.getInputId().getId());
    assertEquals(sourceFile, jsAst.getSourceFile());
  }

  @Test
  public void testClearAst() throws Throwable {
    SourceFile sourceFile = SourceFile.fromCode("test.js", "var x = 1;");
    JsAst jsAst = new JsAst(sourceFile);

    jsAst.clearAst();
    assertNotNull(jsAst.getSourceFile());
  }

  @Test
  public void testSetSourceFileValid() throws Throwable {
    SourceFile sourceFile1 = SourceFile.fromCode("test.js", "var x = 1;");
    SourceFile sourceFile2 = SourceFile.fromCode("test.js", "var y = 2;");
    JsAst jsAst = new JsAst(sourceFile1);

    jsAst.setSourceFile(sourceFile2);
    assertEquals(sourceFile2, jsAst.getSourceFile());
  }

  @Test
  public void testSetSourceFileInvalidName() throws Throwable {
    SourceFile sourceFile1 = SourceFile.fromCode("test1.js", "var x = 1;");
    SourceFile sourceFile2 = SourceFile.fromCode("test2.js", "var y = 2;");
    JsAst jsAst = new JsAst(sourceFile1);

    try {
      jsAst.setSourceFile(sourceFile2);
      fail("Expected IllegalStateException due to mismatched file names");
    } catch (IllegalStateException e) {
      // Expected
    }
  }

  @Test
  public void testGetInputId() throws Throwable {
    SourceFile sourceFile = SourceFile.fromCode("my_input.js", "");
    JsAst jsAst = new JsAst(sourceFile);
    InputId inputId = jsAst.getInputId();

    assertNotNull(inputId);
    assertEquals("my_input.js", inputId.getId());
  }
}