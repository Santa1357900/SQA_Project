package com.google.javascript.jscomp;

import com.google.common.base.Supplier;
import com.google.javascript.jscomp.FunctionInjector.InliningMode;
import com.google.javascript.jscomp.FunctionInjector.Reference;
import com.google.javascript.rhino.IR;
import com.google.javascript.rhino.Node;
import org.junit.Test;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import static org.junit.Assert.*;

public class FunctionInjectorTest {

  private static class DummyCompiler extends Compiler {
    private boolean inlinable = true;
    private CodingConvention codingConvention = new DefaultCodingConvention() {
      @Override
      public boolean isInlinableFunction(Node fnNode) {
        return inlinable;
      }
    };

    @Override
    public CodingConvention getCodingConvention() {
      return codingConvention;
    }

    @Override
    public LifeCycleStage getLifeCycleStage() {
      return LifeCycleStage.NORMALIZED;
    }

    @Override
    public JSModuleGraph getModuleGraph() {
      return null;
    }
  }

  private static class DummySupplier implements Supplier<String> {
    private int id = 0;
    @Override
    public String get() {
      return "jscomp_inline_" + (id++);
    }
  }

  @Test
  public void testConstructorAndBasicProperties() throws Throwable {
    Compiler compiler = new DummyCompiler();
    Supplier<String> supplier = new DummySupplier();
    FunctionInjector injector = new FunctionInjector(compiler, supplier, true, true, true);
    assertNotNull(injector);

    Set<String> constants = new HashSet<String>();
    constants.add("CONST_VAR");
    injector.setKnownConstants(constants);
  }

  @Test
  public void testReferenceClass() throws Throwable {
    Node callNode = IR.call(IR.name("foo"));
    JSModule module = new JSModule("module1");
    Reference ref = new Reference(callNode, module, InliningMode.DIRECT);
    assertSame(callNode, ref.callNode);
    assertSame(module, ref.module);
    assertEquals(InliningMode.DIRECT, ref.mode);
  }

  @Test
  public void testDoesFunctionMeetMinimumRequirements() throws Throwable {
    Compiler compiler = new DummyCompiler();
    Supplier<String> supplier = new DummySupplier();
    FunctionInjector injector = new FunctionInjector(compiler, supplier, true, true, true);

    Node fnNode = IR.function(IR.name("myFunc"), IR.paramList(), IR.block(IR.returnNode(IR.number(1))));
    boolean meets = injector.doesFunctionMeetMinimumRequirements("myFunc", fnNode);
    assertTrue(meets);
  }

  @Test
  public void testDoesFunctionMeetMinimumRequirementsWithArguments() throws Throwable {
    Compiler compiler = new DummyCompiler();
    Supplier<String> supplier = new DummySupplier();
    FunctionInjector injector = new FunctionInjector(compiler, supplier, true, true, true);

    Node block = IR.block(IR.returnNode(IR.name("arguments")));
    Node fnNode = IR.function(IR.name("myFunc"), IR.paramList(), block);
    boolean meets = injector.doesFunctionMeetMinimumRequirements("myFunc", fnNode);
    assertFalse(meets);
  }

  @Test
  public void testDoesFunctionMeetMinimumRequirementsWithEval() throws Throwable {
    Compiler compiler = new DummyCompiler();
    Supplier<String> supplier = new DummySupplier();
    FunctionInjector injector = new FunctionInjector(compiler, supplier, true, true, true);

    Node block = IR.block(IR.returnNode(IR.name("eval")));
    Node fnNode = IR.function(IR.name("myFunc"), IR.paramList(), block);
    boolean meets = injector.doesFunctionMeetMinimumRequirements("myFunc", fnNode);
    assertFalse(meets);
  }

  @Test
  public void testIsDirectCallNodeReplacementPossibleEmptyBlock() throws Throwable {
    Compiler compiler = new DummyCompiler();
    Supplier<String> supplier = new DummySupplier();
    FunctionInjector injector = new FunctionInjector(compiler, supplier, true, true, true);

    Node fnNode = IR.function(IR.name("myFunc"), IR.paramList(), IR.block());
    assertTrue(injector.isDirectCallNodeReplacementPossible(fnNode));
  }

  @Test
  public void testIsDirectCallNodeReplacementPossibleValidReturn() throws Throwable {
    Compiler compiler = new DummyCompiler();
    Supplier<String> supplier = new DummySupplier();
    FunctionInjector injector = new FunctionInjector(compiler, supplier, true, true, true);

    Node fnNode = IR.function(IR.name("myFunc"), IR.paramList(), IR.block(IR.returnNode(IR.number(42))));
    assertTrue(injector.isDirectCallNodeReplacementPossible(fnNode));
  }

  @Test
  public void testIsDirectCallNodeReplacementPossibleInvalidMultipleStatements() throws Throwable {
    Compiler compiler = new DummyCompiler();
    Supplier<String> supplier = new DummySupplier();
    FunctionInjector injector = new FunctionInjector(compiler, supplier, true, true, true);

    Node fnNode = IR.function(IR.name("myFunc"), IR.paramList(), IR.block(
        IR.exprResult(IR.number(1)),
        IR.returnNode(IR.number(42))
    ));
    assertFalse(injector.isDirectCallNodeReplacementPossible(fnNode));
  }

  @Test
  public void testCanInlineReferenceToFunctionUnsupportedCall() throws Throwable {
    Compiler compiler = new DummyCompiler();
    Supplier<String> supplier = new DummySupplier();
    FunctionInjector injector = new FunctionInjector(compiler, supplier, true, true, true);

    Node callNode = IR.call(IR.number(1));
    Node fnNode = IR.function(IR.name("myFunc"), IR.paramList(), IR.block(IR.returnNode(IR.number(1))));
    Set<String> aliases = new HashSet<String>();

    FunctionInjector.CanInlineResult result = injector.canInlineReferenceToFunction(
        null, callNode, fnNode, aliases, InliningMode.DIRECT, false, false);
    assertEquals(FunctionInjector.CanInlineResult.NO, result);
  }

  @Test
  public void testInlineDirectReturnValue() throws Throwable {
    Compiler compiler = new DummyCompiler();
    Supplier<String> supplier = new DummySupplier();
    FunctionInjector injector = new FunctionInjector(compiler, supplier, true, true, true);

    Node callNode = IR.call(IR.name("myFunc"));
    Node exprResult = IR.exprResult(callNode);

    Node fnNode = IR.function(IR.name("myFunc"), IR.paramList(), IR.block(IR.returnNode(IR.number(99))));
    
    Node inlined = injector.inline(callNode, "myFunc", fnNode, InliningMode.DIRECT);
    assertNotNull(inlined);
  }

  @Test
  public void testInliningLowersCostEmptyRefs() throws Throwable {
    Compiler compiler = new DummyCompiler();
    Supplier<String> supplier = new DummySupplier();
    FunctionInjector injector = new FunctionInjector(compiler, supplier, true, true, true);

    Node fnNode = IR.function(IR.name("myFunc"), IR.paramList(), IR.block(IR.returnNode(IR.number(1))));
    List<Reference> refs = new ArrayList<Reference>();
    Set<String> aliases = new HashSet<String>();

    boolean lowers = injector.inliningLowersCost(null, fnNode, refs, aliases, true, false);
    assertTrue(lowers);
  }
}