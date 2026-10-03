package com.google.javascript.jscomp;

import com.google.javascript.rhino.Node;
import com.google.javascript.rhino.Token;
import com.google.javascript.rhino.JSDocInfo;
import com.google.javascript.rhino.jstype.JSType;
import com.google.javascript.rhino.jstype.FunctionType;
import org.junit.Test;

import static org.junit.Assert.*;

public class FunctionTypeBuilderTest {

  @Test
  public void testConstructorPreconditions() throws Throwable {
    Compiler compiler = new Compiler();
    Node errorRoot = new Node(Token.BLOCK);
    Scope scope = new Scope(null, compiler.getTypeRegistry());
    
    boolean thrown = false;
    try {
      new FunctionTypeBuilder("testFn", compiler, null, "testSource", scope);
    } catch (NullPointerException e) {
      thrown = true;
    }
    assertTrue(thrown);
  }

  @Test
  public void testIsFunctionTypeDeclarationEmpty() throws Throwable {
    JSDocInfo info = new JSDocInfo();
    assertFalse(FunctionTypeBuilder.isFunctionTypeDeclaration(info));
  }

  @Test
  public void testIsFunctionTypeDeclarationWithParam() throws Throwable {
    JSDocInfo info = new JSDocInfo();
    info.addParameterName("p1");
    assertTrue(FunctionTypeBuilder.isFunctionTypeDeclaration(info));
  }

  @Test
  public void testBuildAndRegisterDefault() throws Throwable {
    Compiler compiler = new Compiler();
    compiler.initOptions(new CompilerOptions());
    Node errorRoot = new Node(Token.BLOCK);
    Scope scope = new Scope(null, compiler.getTypeRegistry());
    
    FunctionTypeBuilder builder = new FunctionTypeBuilder("myFunc", compiler, errorRoot, "testSource", scope);
    
    boolean thrown = false;
    try {
      builder.buildAndRegister();
    } catch (IllegalStateException e) {
      thrown = true;
    }
    assertTrue(thrown);
  }
}