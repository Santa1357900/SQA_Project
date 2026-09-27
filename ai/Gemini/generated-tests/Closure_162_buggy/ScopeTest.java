package com.google.javascript.jscomp;

import static com.google.javascript.rhino.jstype.JSTypeNative.GLOBAL_THIS;

import com.google.common.base.Predicate;
import com.google.common.collect.ImmutableList;
import com.google.javascript.rhino.ErrorReporter;
import com.google.javascript.rhino.JSDocInfo;
import com.google.javascript.rhino.Node;
import com.google.javascript.rhino.Token;
import com.google.javascript.rhino.jstype.JSType;
import com.google.javascript.rhino.jstype.ObjectType;
import com.google.javascript.rhino.jstype.StaticSourceFile;

import org.junit.Test;
import static org.junit.Assert.*;

import java.util.Iterator;

public class ScopeTest {

  @Test
  public void testGlobalScopeCreation() throws Throwable {
    Node rootNode = new Node(Token.BLOCK);
    Compiler compiler = new Compiler();
    // Initialize standard types so TypeRegistry isn't null
    compiler.initOptions(new CompilerOptions());
    compiler.init(ImmutableList.<SourceFile>of(), ImmutableList.<SourceFile>of(), new CompilerOptions());

    Scope globalScope = new Scope(rootNode, compiler);
    assertTrue(globalScope.isGlobal());
    assertFalse(globalScope.isLocal());
    assertNull(globalScope.getParent());
    assertEquals(0, globalScope.getDepth());
    assertEquals(rootNode, globalScope.getRootNode());
    assertNotNull(globalScope.getTypeOfThis());
    assertFalse(globalScope.isBottom());
    assertEquals(globalScope, globalScope.getGlobalScope());
  }

  @Test
  public void testBottomScopeCreation() throws Throwable {
    Node rootNode = new Node(Token.BLOCK);
    Scope bottomScope = new Scope(rootNode, (ObjectType) null);
    assertTrue(bottomScope.isBottom());
    assertEquals(0, bottomScope.getDepth());
    assertNull(bottomScope.getParent());
    assertEquals(rootNode, bottomScope.getRootNode());
  }

  @Test
  public void testNestedScopeCreation() throws Throwable {
    Node globalRoot = new Node(Token.BLOCK);
    Compiler compiler = new Compiler();
    compiler.initOptions(new CompilerOptions());
    compiler.init(ImmutableList.<SourceFile>of(), ImmutableList.<SourceFile>of(), new CompilerOptions());

    Scope globalScope = new Scope(globalRoot, compiler);
    
    Node funcRoot = new Node(Token.FUNCTION);
    Scope localScope = new Scope(globalScope, funcRoot);

    assertFalse(localScope.isGlobal());
    assertTrue(localScope.isLocal());
    assertEquals(globalScope, localScope.getParent());
    assertEquals(globalScope, localScope.getGlobalScope());
    assertEquals(1, localScope.getDepth());
    assertEquals(funcRoot, localScope.getRootNode());
  }

  @Test
  public void testVariableDeclarationAndRetrieval() throws Throwable {
    Node globalRoot = new Node(Token.BLOCK);
    Compiler compiler = new Compiler();
    compiler.initOptions(new CompilerOptions());
    compiler.init(ImmutableList.<SourceFile>of(), ImmutableList.<SourceFile>of(), new CompilerOptions());

    Scope globalScope = new Scope(globalRoot, compiler);

    Node nameNode = Node.newString(Token.NAME, "x");
    CompilerInput input = new CompilerInput(SourceFile.fromCode("test.js", "var x;"));

    Scope.Var var = globalScope.declare("x", nameNode, null, input, true);
    assertNotNull(var);
    assertEquals("x", var.getName());
    assertEquals(nameNode, var.getNode());
    assertEquals(input, var.getInput());
    assertEquals("test.js", var.getInputName());
    assertTrue(var.isTypeInferred());
    assertEquals(globalScope, var.getScope());
    assertTrue(var.isGlobal());
    assertFalse(var.isLocal());
    assertEquals(0, var.index);
    assertNull(var.getJSDocInfo());

    // Test retrieval
    assertEquals(var, globalScope.getVar("x"));
    assertEquals(var, globalScope.getSlot("x"));
    assertEquals(var, globalScope.getOwnSlot("x"));
    assertTrue(globalScope.isDeclared("x", true));
    assertTrue(globalScope.isDeclared("x", false));
    assertEquals(1, globalScope.getVarCount());

    // Test nested scope variable lookup (lexical scoping)
    Node funcRoot = new Node(Token.FUNCTION);
    Scope localScope = new Scope(globalScope, funcRoot);
    assertEquals(var, localScope.getVar("x"));
    assertFalse(localScope.isDeclared("x", false));
    assertTrue(localScope.isDeclared("x", true));
    assertNull(localScope.getOwnSlot("x"));

    // Test undeclare
    globalScope.undeclare(var);
    assertEquals(0, globalScope.getVarCount());
    assertNull(globalScope.getVar("x"));
    assertFalse(globalScope.isDeclared("x", false));
  }

  @Test
  public void testArgumentsVar() throws Throwable {
    Node globalRoot = new Node(Token.BLOCK);
    Compiler compiler = new Compiler();
    compiler.initOptions(new CompilerOptions());
    compiler.init(ImmutableList.<SourceFile>of(), ImmutableList.<SourceFile>of(), new CompilerOptions());

    Scope globalScope = new Scope(globalRoot, compiler);
    Scope.Var args1 = globalScope.getArgumentsVar();
    Scope.Var args2 = globalScope.getArgumentsVar();

    assertNotNull(args1);
    assertEquals("arguments", args1.getName());
    assertEquals(args1, args2); // Same instance cached
  }

  @Test
  public void testVarMethodsAndEdgeCases() throws Throwable {
    Node globalRoot = new Node(Token.BLOCK);
    Compiler compiler = new Compiler();
    compiler.initOptions(new CompilerOptions());
    compiler.init(ImmutableList.<SourceFile>of(), ImmutableList.<SourceFile>of(), new CompilerOptions());

    Scope globalScope = new Scope(globalRoot, compiler);
    Node nameNode = Node.newString(Token.NAME, "y");
    CompilerInput input = new CompilerInput(SourceFile.fromCode("test.js", "y;"));

    Scope.Var var = globalScope.declare("y", nameNode, null, input, true);

    assertNull(var.getDeclaration());
    assertNull(var.getParentNode());
    assertFalse(var.isBleedingFunction());
    assertFalse(var.isConst());
    assertFalse(var.isDefine());
    assertNull(var.getInitialValue());
    assertFalse(var.isNoShadow());
    assertNotNull(var.toString());
    assertNotNull(var.getSymbol());

    // Test equals and hashCode with Var and non-Var
    Scope.Var var2 = globalScope.declare("z", Node.newString(Token.NAME, "z"), null, input, true);
    assertFalse(var.equals(null));
    assertFalse(var.equals("not a var"));
    assertFalse(var.equals(var2));
    assertTrue(var.equals(var));
    assertEquals(nameNode.hashCode(), var.hashCode());

    // Test Arguments equals and hashCode
    Scope.Arguments arg1 = new Scope.Arguments(globalScope);
    Scope.Arguments arg2 = new Scope.Arguments(globalScope);
    assertFalse(arg1.equals(null));
    assertFalse(arg1.equals("string"));
    assertTrue(arg1.equals(arg1));
  }

  @Test
  public void testGetVarsAndSymbols() throws Throwable {
    Node globalRoot = new Node(Token.BLOCK);
    Compiler compiler = new Compiler();
    compiler.initOptions(new CompilerOptions());
    compiler.init(ImmutableList.<SourceFile>of(), ImmutableList.<SourceFile>of(), new CompilerOptions());

    Scope globalScope = new Scope(globalRoot, compiler);
    globalScope.declare("a", Node.newString(Token.NAME, "a"), null, null, true);
    globalScope.declare("b", Node.newString(Token.NAME, "b"), null, null, true);

    Iterator<Scope.Var> varsIter = globalScope.getVars();
    assertNotNull(varsIter);
    int count = 0;
    while (varsIter.hasNext()) {
      varsIter.next();
      count++;
    }
    assertEquals(2, count);

    assertNotNull(globalScope.getAllSymbols());
    assertNotNull(globalScope.getDeclarativelyUnboundVarsWithoutTypes());
    
    Scope.Var aVar = globalScope.getVar("a");
    assertNotNull(globalScope.getReferences(aVar));
    assertEquals(globalScope, globalScope.getScope(aVar));
  }
}