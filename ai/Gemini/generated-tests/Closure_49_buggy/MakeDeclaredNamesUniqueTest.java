package com.google.javascript.jscomp;

import com.google.common.base.Supplier;
import com.google.javascript.jscomp.MakeDeclaredNamesUnique.ContextualRenamer;
import com.google.javascript.jscomp.MakeDeclaredNamesUnique.ContextualRenameInverter;
import com.google.javascript.jscomp.MakeDeclaredNamesUnique.InlineRenamer;
import com.google.javascript.jscomp.MakeDeclaredNamesUnique.BoilerplateRenamer;
import com.google.javascript.rhino.Node;
import com.google.javascript.rhino.Token;
import com.google.javascript.rhino.IR;

import junit.framework.TestCase;

public class MakeDeclaredNamesUniqueTest extends TestCase {

  private Compiler compiler;

  @Override
  protected void setUp() throws Exception {
    super.setUp();
    compiler = new Compiler();
  }

  public void testContextualRenamerBasic() throws Throwable {
    MakeDeclaredNamesUnique.Renamer renamer = new MakeDeclaredNamesUnique.ContextualRenamer();
    renamer.addDeclaredName("a");
    renamer.addDeclaredName("arguments");
    assertNull(renamer.getReplacementName("arguments"));
    assertFalse(renamer.stripConstIfReplaced());

    MakeDeclaredNamesUnique.Renamer child = renamer.forChildScope();
    assertNotNull(child);
  }

  public void testContextualRenamerLocalDeclarations() throws Throwable {
    MakeDeclaredNamesUnique.ContextualRenamer root = new MakeDeclaredNamesUnique.ContextualRenamer();
    MakeDeclaredNamesUnique.Renamer child = root.forChildScope();

    child.addDeclaredName("x");
    child.addDeclaredName("x"); // Duplicate should increment or handle safely
    assertEquals("x$$1", child.getReplacementName("x"));

    MakeDeclaredNamesUnique.Renamer grandchild = child.forChildScope();
    grandchild.addDeclaredName("x");
    assertEquals("x$$2", grandchild.getReplacementName("x"));
  }

  public void testInlineRenamer() throws Throwable {
    Supplier<String> supplier = new Supplier<String>() {
      private int id = 0;
      public String get() {
        return String.valueOf(id++);
      }
    };

    InlineRenamer renamer = new InlineRenamer(supplier, "PREFIX_", true);
    assertTrue(renamer.stripConstIfReplaced());

    renamer.addDeclaredName("foo");
    assertEquals("foo$$PREFIX_0", renamer.getReplacementName("foo"));

    renamer.addDeclaredName("bar$$oldId");
    assertEquals("bar$$PREFIX_1", renamer.getReplacementName("bar$$oldId"));

    renamer.addDeclaredName("");
    assertEquals("", renamer.getReplacementName(""));

    MakeDeclaredNamesUnique.Renamer child = renamer.forChildScope();
    assertNotNull(child);
  }

  public void testBoilerplateRenamer() throws Throwable {
    Supplier<String> supplier = new Supplier<String>() {
      public String get() {
        return "1";
      }
    };

    BoilerplateRenamer renamer = new BoilerplateRenamer(supplier, "B_");
    MakeDeclaredNamesUnique.Renamer child = renamer.forChildScope();
    assertNotNull(child);
    assertFalse(child.stripConstIfReplaced());
  }

  public void testMakeDeclaredNamesUniqueTraversal() throws Throwable {
    MakeDeclaredNamesUnique pass = new MakeDeclaredNamesUnique();
    CompilerPass contextualInverter = MakeDeclaredNamesUnique.getContextualRenameInverter(compiler);
    assertNotNull(contextualInverter);

    Node script = IR.script(
        IR.var(IR.name("a"), IR.number(1)),
        IR.function(IR.name("f"), IR.paramList(IR.name("a")), IR.block(
            IR.var(IR.name("a"), IR.number(2)),
            IR.tryCatch(IR.block(IR.empty()), IR.catchNode(IR.name("e"), IR.block()))
        ))
    );

    NodeTraversal.traverse(compiler, script, pass);
  }

  public void testContextualRenameInverterProcess() throws Throwable {
    ContextualRenameInverter inverter = (ContextualRenameInverter) MakeDeclaredNamesUnique.getContextualRenameInverter(compiler);
    assertNotNull(inverter);

    Node externs = IR.script();
    Node script = IR.script(
        IR.function(IR.name("f"), IR.paramList(), IR.block(
            IR.var(IR.name("a$$1"), IR.number(1)),
            IR.exprResult(IR.name("a$$1"))
        ))
    );

    inverter.process(externs, script);
    assertEquals("a", ContextualRenameInverter.getOrginalName("a$$1"));
    assertEquals("plain", ContextualRenameInverter.getOrginalName("plain"));
  }

  public void testShouldTraverseAndVisitCases() throws Throwable {
    MakeDeclaredNamesUnique pass = new MakeDeclaredNamesUnique();
    Node func = IR.function(IR.name(""), IR.paramList(), IR.block());
    Node catchNode = IR.catchNode(IR.name("ex"), IR.block());
    Node nameNode = IR.name("a");

    NodeTraversal t = new NodeTraversal(compiler, pass);
    
    boolean shouldTravFunc = pass.shouldTraverse(t, func, null);
    assertTrue(shouldTravFunc);

    boolean shouldTravCatch = pass.shouldTraverse(t, catchNode, null);
    assertTrue(shouldTravCatch);

    pass.visit(t, nameNode, null);
    pass.visit(t, func, null);
    pass.visit(t, catchNode, null);
  }
}