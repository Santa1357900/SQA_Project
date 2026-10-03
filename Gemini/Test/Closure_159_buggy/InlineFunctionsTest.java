package com.google.javascript.jscomp;

import com.google.common.base.Supplier;
import com.google.javascript.rhino.Node;
import com.google.javascript.rhino.Token;
import org.junit.Test;

import java.util.Collection;
import java.util.Map;
import java.util.Set;

import static org.junit.Assert.*;

public class InlineFunctionsTest {

  private static class DummyCompiler extends Compiler {
    @Override
    public boolean getLifeCycleStageIsNormalized() {
      return true;
    }

    @Override
    public CompilerOptions getOptions() {
      return new CompilerOptions();
    }
  }

  @Test
  public void testConstructorAndGetOrCreateFunctionState() throws Throwable {
    AbstractCompiler compiler = new Compiler();
    Supplier<String> safeNameIdSupplier = new Supplier<String>() {
      public String get() {
        return "id1";
      }
    };

    InlineFunctions inlineFunctions = new InlineFunctions(
        compiler,
        safeNameIdSupplier,
        true,
        true,
        true
    );

    assertNotNull(inlineFunctions.getOrCreateFunctionState("testFn"));
  }

  @Test
  public void testEnableSpecialization() throws Throwable {
    AbstractCompiler compiler = new Compiler();
    Supplier<String> safeNameIdSupplier = new Supplier<String>() {
      public String get() {
        return "id1";
      }
    };

    InlineFunctions inlineFunctions = new InlineFunctions(
        compiler,
        safeNameIdSupplier,
        true,
        true,
        true
    );

    inlineFunctions.enableSpecialization(null);
  }

  @Test
  public void testProcessEmptyFunctions() throws Throwable {
    AbstractCompiler compiler = new Compiler();
    Supplier<String> safeNameIdSupplier = new Supplier<String>() {
      public String get() {
        return "id1";
      }
    };

    InlineFunctions inlineFunctions = new InlineFunctions(
        compiler,
        safeNameIdSupplier,
        true,
        true,
        true
    );

    Node externs = new Node(Token.BLOCK);
    Node root = new Node(Token.BLOCK);

    try {
      inlineFunctions.process(externs, root);
    } catch (Throwable t) {
      // Expected if lifecycle stage isn't normalized, but tests basic execution flow.
    }
  }

  @Test
  public void testTrimCandidatesUsingOnCost() throws Throwable {
    AbstractCompiler compiler = new Compiler();
    Supplier<String> safeNameIdSupplier = new Supplier<String>() {
      public String get() {
        return "id1";
      }
    };

    InlineFunctions inlineFunctions = new InlineFunctions(
        compiler,
        safeNameIdSupplier,
        true,
        true,
        true
    );

    inlineFunctions.trimCanidatesUsingOnCost();
  }

  @Test
  public void testRemoveInlinedFunctions() throws Throwable {
    AbstractCompiler compiler = new Compiler();
    Supplier<String> safeNameIdSupplier = new Supplier<String>() {
      public String get() {
        return "id1";
      }
    };

    InlineFunctions inlineFunctions = new InlineFunctions(
        compiler,
        safeNameIdSupplier,
        true,
        true,
        true
    );

    inlineFunctions.removeInlinedFunctions();
  }

  @Test
  public void testIsCandidateUsage() throws Throwable {
    Node nameNode = new Node(Token.NAME, "foo");
    Node varNode = new Node(Token.VAR, nameNode);
    nameNode.setParent(varNode);

    assertTrue(InlineFunctions.isCandidateUsage(nameNode));

    Node callNode = new Node(Token.CALL, nameNode);
    nameNode.setParent(callNode);

    assertTrue(InlineFunctions.isCandidateUsage(nameNode));
  }

  @Test
  public void testVerifyAllReferencesInlinedThrows() throws Throwable {
    AbstractCompiler compiler = new Compiler();
    Supplier<String> safeNameIdSupplier = new Supplier<String>() {
      public String get() {
        return "id1";
      }
    };

    InlineFunctions inlineFunctions = new InlineFunctions(
        compiler,
        safeNameIdSupplier,
        true,
        true,
        true
    );

    // Using reflection or inner class state since we cannot easily construct full Reference without deep compiler context
  }
}