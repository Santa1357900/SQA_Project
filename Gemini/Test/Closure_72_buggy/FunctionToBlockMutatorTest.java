package com.google.javascript.jscomp;

import com.google.common.base.Supplier;
import com.google.javascript.rhino.Node;
import com.google.javascript.rhino.Token;
import org.junit.Test;

import static org.junit.Assert.*;

public class FunctionToBlockMutatorTest {

  private static class DummyCompiler extends AbstractCompiler {
    @Override
    public void report(JSError error) {}

    @Override
    public CheckLevel getErrorLevel(JSError error) {
      return CheckLevel.ERROR;
    }

    @Override
    public void warning(JSError error) {}

    @Override
    public void throwInternalError(String message, Exception e) {
      throw new RuntimeException(message, e);
    }

    @Override
    public Node parse(CompilerInput input) {
      return null;
    }

    @Override
    public SourceFile getSourceFileByName(String fileName) {
      return null;
    }

    @Override
    public CodingConvention getCodingConvention() {
      return new DefaultCodingConvention();
    }

    @Override
    public void reportCodeChange() {}

    @Override
    public boolean acceptEcmascript5() {
      return false;
    }

    @Override
    public boolean hasHaltingErrors() {
      return false;
    }

    @Override
    public Supplier<String> getUniqueNameIdSupplier() {
      return new Supplier<String>() {
        private int id = 0;
        @Override
        public String get() {
          return String.valueOf(id++);
        }
      };
    }
  }

  @Test
  public void testLabelNameSupplier() throws Throwable {
    Supplier<String> idSupplier = new Supplier<String>() {
      @Override
      public String get() {
        return "123";
      }
    };
    FunctionToBlockMutator.LabelNameSupplier supplier =
        new FunctionToBlockMutator.LabelNameSupplier(idSupplier);
    assertEquals("JSCompiler_inline_label_123", supplier.get());
  }

  @Test
  public void testMutateBasicFunction() throws Throwable {
    AbstractCompiler compiler = new DummyCompiler();
    Supplier<String> safeNameIdSupplier = new Supplier<String>() {
      private int id = 0;
      @Override
      public String get() {
        return String.valueOf(id++);
      }
    };

    FunctionToBlockMutator mutator = new FunctionToBlockMutator(compiler, safeNameIdSupplier);

    Node fnNode = new Node(Token.FUNCTION,
        Node.newString(Token.NAME, "myFunc"),
        new Node(Token.LP),
        new Node(Token.BLOCK, new Node(Token.RETURN, Node.newNumber(42)))
    );
    Node callNode = new Node(Token.CALL, Node.newString(Token.NAME, "myFunc"));

    Node result = mutator.mutate("myFunc", fnNode, callNode, "resultVar", true, false);
    assertNotNull(result);
  }

  @Test
  public void testMutateInLoop() throws Throwable {
    AbstractCompiler compiler = new DummyCompiler();
    Supplier<String> safeNameIdSupplier = new Supplier<String>() {
      private int id = 0;
      @Override
      public String get() {
        return String.valueOf(id++);
      }
    };

    FunctionToBlockMutator mutator = new FunctionToBlockMutator(compiler, safeNameIdSupplier);

    Node varNode = new Node(Token.VAR, Node.newString(Token.NAME, "x"));
    Node block = new Node(Token.BLOCK, varNode);
    Node fnNode = new Node(Token.FUNCTION,
        Node.newString(Token.NAME, "loopFunc"),
        new Node(Token.LP),
        block
    );
    Node callNode = new Node(Token.CALL, Node.newString(Token.NAME, "loopFunc"));

    Node result = mutator.mutate("loopFunc", fnNode, callNode, null, false, true);
    assertNotNull(result);
  }

  @Test
  public void testMutateAnonymousFunction() throws Throwable {
    AbstractCompiler compiler = new DummyCompiler();
    Supplier<String> safeNameIdSupplier = new Supplier<String>() {
      private int id = 0;
      @Override
      public String get() {
        return String.valueOf(id++);
      }
    };

    FunctionToBlockMutator mutator = new FunctionToBlockMutator(compiler, safeNameIdSupplier);

    Node fnNode = new Node(Token.FUNCTION,
        Node.newString(Token.NAME, ""),
        new Node(Token.LP),
        new Node(Token.BLOCK, new Node(Token.RETURN))
    );
    Node callNode = new Node(Token.CALL, Node.newString(Token.NAME, "anon"));

    Node result = mutator.mutate("", fnNode, callNode, null, false, false);
    assertNotNull(result);
  }
}