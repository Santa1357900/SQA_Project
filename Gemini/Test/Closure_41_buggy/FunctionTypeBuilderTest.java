package com.google.javascript.jscomp;

import org.junit.Test;
import static org.junit.Assert.*;

import com.google.javascript.rhino.IR;
import com.google.javascript.rhino.Node;
import com.google.javascript.rhino.JSDocInfo;
import com.google.javascript.rhino.jstype.JSType;
import com.google.javascript.rhino.jstype.JSTypeRegistry;
import com.google.javascript.rhino.jstype.FunctionType;

public class FunctionTypeBuilderTest {

  @Test
  public void testIsFunctionTypeDeclarationNull() throws Throwable {
    boolean result = FunctionTypeBuilder.isFunctionTypeDeclaration(new JSDocInfo());
    assertFalse(result);
  }

  @Test
  public void testUnknownFunctionContents() throws Throwable {
    FunctionTypeBuilder.FunctionContents contents = FunctionTypeBuilder.UnknownFunctionContents.get();
    assertNull(contents.getSourceNode());
    assertTrue(contents.mayBeFromExterns());
    assertTrue(contents.mayHaveNonEmptyReturns());
    assertNotNull(contents.getEscapedVarNames());
  }

  @Test
  public void testAstFunctionContents() throws Throwable {
    Node node = IR.block();
    FunctionTypeBuilder.AstFunctionContents contents = new FunctionTypeBuilder.AstFunctionContents(node);
    assertEquals(node, contents.getSourceNode());
    assertFalse(contents.mayBeFromExterns());
    assertFalse(contents.mayHaveNonEmptyReturns());
    
    contents.recordNonEmptyReturn();
    assertTrue(contents.mayHaveNonEmptyReturns());

    assertNotNull(contents.getEscapedVarNames());
    contents.recordEscapedVarName("testVar");
    assertNotNull(contents.getEscapedVarNames());
  }

  @Test(expected = NullPointerException.class)
  public void testBuilderConstructorNullErrorRoot() throws Throwable {
    Compiler compiler = new Compiler();
    compiler.initOptions(new CompilerOptions());
    Scope scope = new Scope(null, compiler);
    new FunctionTypeBuilder("myFunc", compiler, null, "source.js", scope);
  }

  @Test
  public void testSetContents() throws Throwable {
    Compiler compiler = new Compiler();
    compiler.initOptions(new CompilerOptions());
    Node errorRoot = IR.block();
    Scope scope = new Scope(null, compiler);
    
    FunctionTypeBuilder builder = new FunctionTypeBuilder("myFunc", compiler, errorRoot, "source.js", scope);
    FunctionTypeBuilder resultBuilder = builder.setContents(null);
    assertNotNull(resultBuilder);

    FunctionTypeBuilder.FunctionContents contents = FunctionTypeBuilder.UnknownFunctionContents.get();
    FunctionTypeBuilder resultBuilder2 = builder.setContents(contents);
    assertNotNull(resultBuilder2);
  }

  @Test
  public void testInferFromOverriddenFunctionNull() throws Throwable {
    Compiler compiler = new Compiler();
    compiler.initOptions(new CompilerOptions());
    Node errorRoot = IR.block();
    Scope scope = new Scope(null, compiler);

    FunctionTypeBuilder builder = new FunctionTypeBuilder("myFunc", compiler, errorRoot, "source.js", scope);
    FunctionTypeBuilder result = builder.inferFromOverriddenFunction(null, null);
    assertNotNull(result);
  }

  @Test
  public void testInferReturnTypeNull() throws Throwable {
    Compiler compiler = new Compiler();
    compiler.initOptions(new CompilerOptions());
    Node errorRoot = IR.block();
    Scope scope = new Scope(null, compiler);

    FunctionTypeBuilder builder = new FunctionTypeBuilder("myFunc", compiler, errorRoot, "source.js", scope);
    FunctionTypeBuilder result = builder.inferReturnType(null);
    assertNotNull(result);
  }

  @Test
  public void testInferInheritanceNull() throws Throwable {
    Compiler compiler = new Compiler();
    compiler.initOptions(new CompilerOptions());
    Node errorRoot = IR.block();
    Scope scope = new Scope(null, compiler);

    FunctionTypeBuilder builder = new FunctionTypeBuilder("myFunc", compiler, errorRoot, "source.js", scope);
    FunctionTypeBuilder result = builder.inferInheritance(null);
    assertNotNull(result);
  }

  @Test
  public void testInferThisTypeNullInfo() throws Throwable {
    Compiler compiler = new Compiler();
    compiler.initOptions(new CompilerOptions());
    Node errorRoot = IR.block();
    Scope scope = new Scope(null, compiler);

    FunctionTypeBuilder builder = new FunctionTypeBuilder("myFunc", compiler, errorRoot, "source.js", scope);
    FunctionTypeBuilder result = builder.inferThisType(null);
    assertNotNull(result);
  }

  @Test
  public void testInferParameterTypesNull() throws Throwable {
    Compiler compiler = new Compiler();
    compiler.initOptions(new CompilerOptions());
    Node errorRoot = IR.block();
    Scope scope = new Scope(null, compiler);

    FunctionTypeBuilder builder = new FunctionTypeBuilder("myFunc", compiler, errorRoot, "source.js", scope);
    FunctionTypeBuilder result = builder.inferParameterTypes((Node) null, null);
    assertNotNull(result);
  }

  @Test
  public void testInferTemplateTypeNameNull() throws Throwable {
    Compiler compiler = new Compiler();
    compiler.initOptions(new CompilerOptions());
    Node errorRoot = IR.block();
    Scope scope = new Scope(null, compiler);

    FunctionTypeBuilder builder = new FunctionTypeBuilder("myFunc", compiler, errorRoot, "source.js", scope);
    FunctionTypeBuilder result = builder.inferTemplateTypeName(null);
    assertNotNull(result);
  }

  @Test
  public void testBuildAndRegisterNoParams() throws Throwable {
    Compiler compiler = new Compiler();
    compiler.initOptions(new CompilerOptions());
    Node errorRoot = IR.block();
    Scope scope = new Scope(null, compiler);

    FunctionTypeBuilder builder = new FunctionTypeBuilder("myFunc", compiler, errorRoot, "source.js", scope);
    try {
      builder.buildAndRegister();
      fail("Expected IllegalStateException due to missing parametersNode");
    } catch (IllegalStateException e) {
      assertTrue(e.getMessage().contains("params"));
    }
  }
}