package com.google.javascript.jscomp;

import com.google.common.base.Supplier;
import com.google.javascript.rhino.IR;
import com.google.javascript.rhino.Node;
import com.google.javascript.rhino.Token;
import org.junit.Test;

import static org.junit.Assert.*;

public class InlineObjectLiteralsTest {

  @Test
  public void testConstructorAndProcess() throws Throwable {
    Compiler compiler = new Compiler();
    Supplier<String> safeNameIdSupplier = new Supplier<String>() {
      public String get() {
        return "1";
      }
    };

    InlineObjectLiterals pass = new InlineObjectLiterals(compiler, safeNameIdSupplier);
    assertNotNull(pass);

    Node externs = new Node(Token.BLOCK);
    Node root = new Node(Token.BLOCK);
    pass.process(externs, root);
  }

  @Test
  public void testVarPrefixConstant() throws Throwable {
    assertEquals("JSCompiler_object_inline_", InlineObjectLiterals.VAR_PREFIX);
  }
}