package com.google.javascript.jscomp;

import com.google.javascript.jscomp.CompilerOptions.AliasTransformation;
import com.google.javascript.jscomp.CompilerOptions.AliasTransformationHandler;
import com.google.javascript.rhino.Node;
import com.google.javascript.rhino.SourcePosition;
import com.google.javascript.rhino.Token;
import junit.framework.TestCase;

public class ScopedAliasesTest extends TestCase {

  private Compiler compiler;

  @Override
  protected void setUp() throws Exception {
    super.setUp();
    compiler = new Compiler();
  }

  public void testScopedAliasesConstruction() throws Throwable {
    AliasTransformationHandler handler = new AliasTransformationHandler() {
      public AliasTransformation logAliasTransformation(String sourceName, SourcePosition<AliasTransformation> position) {
        return new AliasTransformation() {
          public void addAlias(String alias, String namespace) {}
        };
      }
    };
    ScopedAliases scopedAliases = new ScopedAliases(compiler, null, handler);
    assertNotNull(scopedAliases);
  }

  public void testProcessBasic() throws Throwable {
    AliasTransformationHandler handler = new AliasTransformationHandler() {
      public AliasTransformation logAliasTransformation(String sourceName, SourcePosition<AliasTransformation> position) {
        return new AliasTransformation() {
          public void addAlias(String alias, String namespace) {}
        };
      }
    };
    ScopedAliases scopedAliases = new ScopedAliases(compiler, null, handler);
    Node root = new Node(Token.SCRIPT);
    scopedAliases.process(null, root);
    assertTrue(true);
  }

  public void testHotSwapScript() throws Throwable {
    AliasTransformationHandler handler = new AliasTransformationHandler() {
      public AliasTransformation logAliasTransformation(String sourceName, SourcePosition<AliasTransformation> position) {
        return new AliasTransformation() {
          public void addAlias(String alias, String namespace) {}
        };
      }
    };
    ScopedAliases scopedAliases = new ScopedAliases(compiler, null, handler);
    Node root = new Node(Token.SCRIPT);
    scopedAliases.hotSwapScript(root, null);
    assertTrue(true);
  }
}