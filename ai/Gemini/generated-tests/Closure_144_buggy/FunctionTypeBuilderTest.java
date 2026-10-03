package com.google.javascript.jscomp;

import com.google.javascript.rhino.JSDocInfo;
import com.google.javascript.rhino.Node;
import com.google.javascript.rhino.Token;
import com.google.javascript.rhino.jstype.JSType;
import com.google.javascript.rhino.jstype.JSTypeRegistry;
import org.junit.Before;
import org.junit.Test;

import static org.junit.Assert.*;

public class FunctionTypeBuilderTest {

  private Compiler compiler;
  private Scope scope;
  private Node errorRoot;
  private String sourceName;

  @Before
  public void setUp() throws Throwable {
    compiler = new Compiler();
    compiler.initOptions(new CompilerOptions());
    sourceName = "testcode";
    errorRoot = new Node(Token.SCRIPT);
    scope = new Scope(null, errorRoot);
  }

  @Test
  public void testConstructorWithNullName() throws Throwable {
    FunctionTypeBuilder builder = new FunctionTypeBuilder(null, compiler, errorRoot, sourceName, scope);
    assertNotNull(builder);
    JSType fnType = builder.buildAndRegister();
    assertNotNull(fnType);
  }

  @Test
  public void testIsFunctionTypeDeclarationEmpty() throws Throwable {
    JSDocInfo info = new JSDocInfo();
    boolean result = FunctionTypeBuilder.isFunctionTypeDeclaration(info);
    assertFalse(result);
  }

  @Test
  public void testIsFunctionTypeDeclarationWithReturn() throws Throwable {
    JSDocInfo info = new JSDocInfo();
    // Since we cannot easily construct full JSDocInfo expressions without complex Rhino internals,
    // we test basic methods that don't throw NPE.
    assertFalse(FunctionTypeBuilder.isFunctionTypeDeclaration(info));
  }

  @Test
  public void testSetSourceNode() throws Throwable {
    FunctionTypeBuilder builder = new FunctionTypeBuilder("myFunc", compiler, errorRoot, sourceName, scope);
    Node sourceNode = new Node(Token.FUNCTION);
    FunctionTypeBuilder returnedBuilder = builder.setSourceNode(sourceNode);
    assertSame(builder, returnedBuilder);
  }

  @Test
  public void testInferReturnTypeNullInfo() throws Throwable {
    FunctionTypeBuilder builder = new FunctionTypeBuilder("myFunc", compiler, errorRoot, sourceName, scope);
    FunctionTypeBuilder returnedBuilder = builder.inferReturnType(null);
    assertSame(builder, returnedBuilder);
  }

  @Test
  public void testInferInheritanceNullInfo() throws Throwable {
    FunctionTypeBuilder builder = new FunctionTypeBuilder("myFunc", compiler, errorRoot, sourceName, scope);
    FunctionTypeBuilder returnedBuilder = builder.inferInheritance(null);
    assertSame(builder, returnedBuilder);
  }

  @Test
  public void testInferThisTypeNullInfoAndOwner() throws Throwable {
    FunctionTypeBuilder builder = new FunctionTypeBuilder("myFunc", compiler, errorRoot, sourceName, scope);
    JSTypeRegistry registry = compiler.getTypeRegistry();
    JSType nativeType = registry.getNativeType(com.google.javascript.rhino.jstype.JSTypeNative.UNKNOWN_TYPE);
    
    FunctionTypeBuilder returnedBuilder = builder.inferThisType(null, nativeType);
    assertSame(builder, returnedBuilder);
  }

  @Test
  public void testInferThisTypeWithNodeOwnerNull() throws Throwable {
    FunctionTypeBuilder builder = new FunctionTypeBuilder("myFunc", compiler, errorRoot, sourceName, scope);
    FunctionTypeBuilder returnedBuilder = builder.inferThisType(null, (Node) null);
    assertSame(builder, returnedBuilder);
  }

  @Test
  public void testInferParameterTypesNulls() throws Throwable {
    FunctionTypeBuilder builder = new FunctionTypeBuilder("myFunc", compiler, errorRoot, sourceName, scope);
    FunctionTypeBuilder returnedBuilder = builder.inferParameterTypes((Node) null, null);
    assertSame(builder, returnedBuilder);
  }

  @Test
  public void testInferParameterTypesWithNullArgsParent() throws Throwable {
    FunctionTypeBuilder builder = new FunctionTypeBuilder("myFunc", compiler, errorRoot, sourceName, scope);
    JSDocInfo info = new JSDocInfo();
    FunctionTypeBuilder returnedBuilder = builder.inferParameterTypes(null, info);
    assertSame(builder, returnedBuilder);
  }

  @Test
  public void testInferTemplateTypeNameNull() throws Throwable {
    FunctionTypeBuilder builder = new FunctionTypeBuilder("myFunc", compiler, errorRoot, sourceName, scope);
    FunctionTypeBuilder returnedBuilder = builder.inferTemplateTypeName(null);
    assertSame(builder, returnedBuilder);
  }

  @Test
  public void testBuildAndRegisterWithoutParamsThrowsException() throws Throwable {
    FunctionTypeBuilder builder = new FunctionTypeBuilder("myFunc", compiler, errorRoot, sourceName, scope);
    try {
      builder.buildAndRegister();
      fail("Expected IllegalStateException due to missing parametersNode");
    } catch (IllegalStateException e) {
      assertTrue(e.getMessage().contains("All Function types must have params and a return type"));
    }
  }
}