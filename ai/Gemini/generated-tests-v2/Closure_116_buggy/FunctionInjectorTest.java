package com.google.javascript.jscomp;

import com.google.common.base.Supplier;
import com.google.javascript.jscomp.FunctionInjector.InliningMode;
import com.google.javascript.jscomp.FunctionInjector.Reference;
import com.google.javascript.rhino.Node;
import com.google.javascript.rhino.Token;
import org.junit.Test;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import static org.junit.Assert.*;

public class FunctionInjectorTest {

  private static class DummyCompiler extends Compiler {
    @Override
    public CodingConvention getCodingConvention() {
      return new DefaultCodingConvention();
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

  @Test
  public void testReferenceCreation() throws Throwable {
    Node callNode = new Node(Token.CALL);
    JSModule module = new JSModule("testModule");
    InliningMode mode = InliningMode.DIRECT;

    Reference ref = new Reference(callNode, module, mode);
    assertEquals(callNode, ref.callNode);
    assertEquals(module, ref.module);
    assertEquals(mode, ref.mode);
  }

  @Test
  public void testConstructorAndSetKnownConstants() throws Throwable {
    Compiler compiler = new DummyCompiler();
    Supplier<String> supplier = new Supplier<String>() {
      @Override
      public String get() {
        return "temp1";
      }
    };

    FunctionInjector injector = new FunctionInjector(
        compiler, supplier, true, true, true);

    Set<String> constants = new HashSet<String>();
    constants.add("CONST_VAR");
    injector.setKnownConstants(constants);
  }

  @Test(expected = IllegalStateException.class)
  public void testSetKnownConstantsTwiceThrows() throws Throwable {
    Compiler compiler = new DummyCompiler();
    Supplier<String> supplier = new Supplier<String>() {
      @Override
      public String get() {
        return "temp1";
      }
    };

    FunctionInjector injector = new FunctionInjector(
        compiler, supplier, true, true, true);

    Set<String> constants1 = new HashSet<String>();
    constants1.add("CONST_1");
    injector.setKnownConstants(constants1);

    Set<String> constants2 = new HashSet<String>();
    constants2.add("CONST_2");
    injector.setKnownConstants(constants2); // Should throw IllegalStateException
  }

  @Test
  public void testDoesFunctionMeetMinimumRequirementsEmptyBody() throws Throwable {
    Compiler compiler = new DummyCompiler();
    Supplier<String> supplier = new Supplier<String>() {
      @Override
      public String get() {
        return "temp1";
      }
    };

    FunctionInjector injector = new FunctionInjector(
        compiler, supplier, true, true, true);

    Node fnNameNode = Node.newString(Token.NAME, "myFunc");
    Node lpNode = new Node(Token.LP);
    Node blockNode = new Node(Token.BLOCK);
    Node fnNode = new Node(Token.FUNCTION, fnNameNode, lpNode, blockNode);

    boolean meets = injector.doesFunctionMeetMinimumRequirements("myFunc", fnNode);
    assertTrue(meets);
  }

  @Test
  public void testIsDirectCallNodeReplacementPossibleEmptyBlock() throws Throwable {
    Compiler compiler = new DummyCompiler();
    Supplier<String> supplier = new Supplier<String>() {
      @Override
      public String get() {
        return "temp1";
      }
    };

    FunctionInjector injector = new FunctionInjector(
        compiler, supplier, true, true, true);

    Node blockNode = new Node(Token.BLOCK);
    Node fnNode = new Node(Token.FUNCTION, Node.newString(Token.NAME, "f"), new Node(Token.LP), blockNode);

    boolean res = injector.isDirectCallNodeReplacementPossible(fnNode);
    assertTrue(res);
  }

  @Test
  public void testIsDirectCallNodeReplacementPossibleSingleReturn() throws Throwable {
    Compiler compiler = new DummyCompiler();
    Supplier<String> supplier = new Supplier<String>() {
      @Override
      public String get() {
        return "temp1";
      }
    };

    FunctionInjector injector = new FunctionInjector(
        compiler, supplier, true, true, true);

    Node returnNode = new Node(Token.RETURN, Node.newNumber(1.0));
    Node blockNode = new Node(Token.BLOCK, returnNode);
    Node fnNode = new Node(Token.FUNCTION, Node.newString(Token.NAME, "f"), new Node(Token.LP), blockNode);

    boolean res = injector.isDirectCallNodeReplacementPossible(fnNode);
    assertTrue(res);
  }

  @Test
  public void testIsDirectCallNodeReplacementPossibleInvalidBlock() throws Throwable {
    Compiler compiler = new DummyCompiler();
    Supplier<String> supplier = new Supplier<String>() {
      @Override
      public String get() {
        return "temp1";
      }
    };

    FunctionInjector injector = new FunctionInjector(
        compiler, supplier, true, true, true);

    Node blockNode = new Node(Token.BLOCK, new Node(Token.EXPR_RESULT, Node.newNumber(1.0)), new Node(Token.RETURN, Node.newNumber(2.0)));
    Node fnNode = new Node(Token.FUNCTION, Node.newString(Token.NAME, "f"), new Node(Token.LP), blockNode);

    boolean res = injector.isDirectCallNodeReplacementPossible(fnNode);
    assertFalse(res);
  }

  @Test
  public void testInliningLowersCostEmptyRefs() throws Throwable {
    Compiler compiler = new DummyCompiler();
    Supplier<String> supplier = new Supplier<String>() {
      @Override
      public String get() {
        return "temp1";
      }
    };

    FunctionInjector injector = new FunctionInjector(
        compiler, supplier, true, true, true);

    Node fnNode = new Node(Token.FUNCTION, Node.newString(Token.NAME, "f"), new Node(Token.LP), new Node(Token.BLOCK));
    List<Reference> refs = new ArrayList<Reference>();

    boolean lowers = injector.inliningLowersCost(null, fnNode, refs, new HashSet<String>(), true, false);
    assertTrue(lowers);
  }
}