package com.google.javascript.jscomp;

import com.google.common.base.Supplier;
import com.google.common.collect.Sets;
import com.google.javascript.jscomp.FunctionInjector.InliningMode;
import com.google.javascript.jscomp.FunctionInjector.Reference;
import com.google.javascript.rhino.Node;
import com.google.javascript.rhino.Token;

import org.junit.Test;
import static org.junit.Assert.*;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

public class FunctionInjectorTest {

  private static class DummyCompiler extends Compiler {
    private CodingConvention codingConvention = new DefaultCodingConvention();

    @Override
    public CodingConvention getCodingConvention() {
      return codingConvention;
    }

    public void setCodingConvention(CodingConvention convention) {
      this.codingConvention = convention;
    }

    @Override
    public JSModuleGraph getModuleGraph() {
      return null;
    }

    @Override
    public CompilerOptions getOptions() {
      return new CompilerOptions();
    }
  }

  @Test
  public void testReferenceConstructor() throws Throwable {
    Node callNode = new Node(Token.CALL);
    JSModule module = new JSModule("module1");
    InliningMode mode = InliningMode.DIRECT;

    Reference ref = new Reference(callNode, module, mode);
    assertNotNull(ref);
    assertEquals(callNode, ref.callNode);
    assertEquals(module, ref.module);
    assertEquals(mode, ref.mode);
  }

  @Test
  public void testFunctionInjectorConstruction() throws Throwable {
    DummyCompiler compiler = new DummyCompiler();
    Supplier<String> supplier = new Supplier<String>() {
      @Override
      public String get() {
        return "temp";
      }
    };

    FunctionInjector injector = new FunctionInjector(
        compiler, supplier, true, true, true);
    assertNotNull(injector);
  }

  @Test(expected = NullPointerException.class)
  public void testFunctionInjectorNullCompiler() throws Throwable {
    Supplier<String> supplier = new Supplier<String>() {
      @Override
      public String get() {
        return "temp";
      }
    };
    new FunctionInjector(null, supplier, true, true, true);
  }

  @Test(expected = NullPointerException.class)
  public void testFunctionInjectorNullSupplier() throws Throwable {
    DummyCompiler compiler = new DummyCompiler();
    new FunctionInjector(compiler, null, true, true, true);
  }

  @Test
  public void testSetKnownConstants() throws Throwable {
    DummyCompiler compiler = new DummyCompiler();
    Supplier<String> supplier = new Supplier<String>() {
      @Override
      public String get() {
        return "temp";
      }
    };

    FunctionInjector injector = new FunctionInjector(
        compiler, supplier, true, true, true);
    Set<String> constants = new HashSet<String>();
    constants.add("CONST_VAL");
    injector.setKnownConstants(constants);
  }

  @Test(expected = IllegalStateException.class)
  public void testSetKnownConstantsTwice() throws Throwable {
    DummyCompiler compiler = new DummyCompiler();
    Supplier<String> supplier = new Supplier<String>() {
      @Override
      public String get() {
        return "temp";
      }
    };

    FunctionInjector injector = new FunctionInjector(
        compiler, supplier, true, true, true);
    Set<String> constants = new HashSet<String>();
    constants.add("CONST_VAL");
    injector.setKnownConstants(constants);
    injector.setKnownConstants(constants);
  }

  @Test
  public void testDoesFunctionMeetMinimumRequirementsEmpty() throws Throwable {
    DummyCompiler compiler = new DummyCompiler();
    Supplier<String> supplier = new Supplier<String>() {
      @Override
      public String get() {
        return "temp";
      }
    };

    FunctionInjector injector = new FunctionInjector(
        compiler, supplier, true, true, true);

    Node fnNode = new Node(Token.FUNCTION, Node.newString(Token.NAME, "fn"), new Node(Token.LP), new Node(Token.BLOCK));
    boolean result = injector.doesFunctionMeetMinimumRequirements("fn", fnNode);
    assertTrue(result);
  }

  @Test
  public void testIsDirectCallNodeReplacementPossible() throws Throwable {
    DummyCompiler compiler = new DummyCompiler();
    Supplier<String> supplier = new Supplier<String>() {
      @Override
      public String get() {
        return "temp";
      }
    };

    FunctionInjector injector = new FunctionInjector(
        compiler, supplier, true, true, true);

    Node block = new Node(Token.BLOCK);
    Node fnNode = new Node(Token.FUNCTION, Node.newString(Token.NAME, "fn"), new Node(Token.LP), block);
    
    // Empty block should be possible
    assertTrue(injector.isDirectCallNodeReplacementPossible(fnNode));

    // Block with single return and expression
    Node returnNode = new Node(Token.RETURN, Node.newNumber(1.0));
    block.addChildToBack(returnNode);
    assertTrue(injector.isDirectCallNodeReplacementPossible(fnNode));
  }

  @Test
  public void testInliningLowersCostZeroRefs() throws Throwable {
    DummyCompiler compiler = new DummyCompiler();
    Supplier<String> supplier = new Supplier<String>() {
      @Override
      public String get() {
        return "temp";
      }
    };

    FunctionInjector injector = new FunctionInjector(
        compiler, supplier, true, true, true);

    Node fnNode = new Node(Token.FUNCTION, Node.newString(Token.NAME, "fn"), new Node(Token.LP), new Node(Token.BLOCK));
    List<Reference> refs = new ArrayList<Reference>();
    Set<String> namesToAlias = new HashSet<String>();

    boolean lowers = injector.inliningLowersCost(null, fnNode, refs, namesToAlias, true, false);
    assertTrue(lowers);
  }
}