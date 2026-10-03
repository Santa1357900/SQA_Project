package com.google.javascript.jscomp;

import com.google.javascript.rhino.IR;
import com.google.javascript.rhino.Node;
import org.junit.Test;

import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

public class ScopedAliasesTest {

  @Test
  public void testScopedAliasesInstantiationAndProcess() throws Throwable {
    Compiler compiler = new Compiler();
    CompilerOptions options = new CompilerOptions();
    compiler.initOptions(options);

    Node externs = IR.root(IR.script());
    Node root = IR.root(IR.script());

    AliasTransformationHandler transformationHandler = new AliasTransformationHandler() {
      public AliasTransformation logAliasTransformation(String sourceName, SourcePosition<AliasTransformation> position) {
        return new AliasTransformation() {
          public void addAlias(String alias, String definition) {
          }
        };
      }
    };

    ScopedAliases scopedAliases = new ScopedAliases(compiler, null, transformationHandler);
    assertNotNull(scopedAliases);

    scopedAliases.process(externs, root);
    scopedAliases.hotSwapScript(root, null);
  }

  @Test
  public void testGoogScopeUsedImproperly() throws Throwable {
    Compiler compiler = new Compiler();
    CompilerOptions options = new CompilerOptions();
    compiler.initOptions(options);

    Node script = IR.script();
    Node call = IR.call(IR.getprop(IR.name("goog"), IR.string("scope")));
    Node varNode = IR.var(IR.name("x"), call);
    script.addChildToBack(varNode);

    Node externs = IR.script();
    compiler.parseSyntheticCode("test.js", "");

    AliasTransformationHandler transformationHandler = new AliasTransformationHandler() {
      public AliasTransformation logAliasTransformation(String sourceName, SourcePosition<AliasTransformation> position) {
        return new AliasTransformation() {
          public void addAlias(String alias, String definition) {
          }
        };
      }
    };

    ScopedAliases scopedAliases = new ScopedAliases(compiler, null, transformationHandler);
    scopedAliases.process(externs, script);
    
    assertTrue(compiler.hasErrors());
  }
}