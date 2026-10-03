package com.google.javascript.jscomp;

import com.google.common.base.Supplier;
import com.google.javascript.jscomp.MakeDeclaredNamesUnique.ContextualRenamer;
import com.google.javascript.jscomp.MakeDeclaredNamesUnique.InlineRenamer;
import com.google.javascript.jscomp.MakeDeclaredNamesUnique.Renamer;
import com.google.javascript.rhino.Node;
import com.google.javascript.rhino.Token;
import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

public class MakeDeclaredNamesUniqueTest {

  @Test
  public void testDefaultConstructor() throws Throwable {
    MakeDeclaredNamesUnique makeNamesUnique = new MakeDeclaredNamesUnique();
    assertNotNull(makeNamesUnique);
  }

  @Test
  public void testCustomRenamerConstructor() throws Throwable {
    Renamer dummyRenamer = new ContextualRenamer();
    MakeDeclaredNamesUnique makeNamesUnique = new MakeDeclaredNamesUnique(dummyRenamer);
    assertNotNull(makeNamesUnique);
  }

  @Test
  public void testGetContextualRenameInverter() throws Throwable {
    CompilerPass pass = MakeDeclaredNamesUnique.getContextualRenameInverter(null);
    assertNotNull(pass);
  }

  @Test
  public void testContextualRenamerGlobalAndChild() throws Throwable {
    Renamer renamer = new ContextualRenamer();
    renamer.addDeclaredName("a");
    
    Renamer child = renamer.forChildScope();
    assertNotNull(child);
    
    child.addDeclaredName("a");
    assertEquals("a$$1", child.getReplacementName("a"));
    
    // Test second occurrence in child
    child.addDeclaredName("a");
    assertEquals("a$$1", child.getReplacementName("a"));
  }

  @Test
  public void testContextualRenamerStripConst() throws Throwable {
    Renamer renamer = new ContextualRenamer();
    assertEquals(false, renamer.stripConstIfReplaced());
  }

  @Test
  public void testInlineRenamerBasic() throws Throwable {
    Supplier<String> supplier = new Supplier<String>() {
      private int id = 0;
      public String get() {
        return String.valueOf(++id);
      }
    };

    InlineRenamer renamer = new InlineRenamer(supplier, "prefix", true);
    renamer.addDeclaredName("x");

    assertEquals("x$$prefix1", renamer.getReplacementName("x"));
    assertEquals(true, renamer.stripConstIfReplaced());

    Renamer child = renamer.forChildScope();
    assertNotNull(child);
  }

  @Test
  public void testInlineRenamerEmptyPrefixException() throws Throwable {
    Supplier<String> supplier = new Supplier<String>() {
      public String get() {
        return "1";
      }
    };

    try {
      new InlineRenamer(supplier, "", false);
      fail("Expected IllegalArgumentException");
    } catch (IllegalArgumentException e) {
      // Expected
    }
  }

  @Test
  public void testInlineRenamerEmptyName() throws Throwable {
    Supplier<String> supplier = new Supplier<String>() {
      public String get() {
        return "1";
      }
    };

    InlineRenamer renamer = new InlineRenamer(supplier, "p", false);
    renamer.addDeclaredName("");
    assertNull(renamer.getReplacementName(""));
  }

  @Test
  public void testInlineRenamerWithExistingSeparator() throws Throwable {
    Supplier<String> supplier = new Supplier<String>() {
      public String get() {
        return "2";
      }
    };

    InlineRenamer renamer = new InlineRenamer(supplier, "p", false);
    renamer.addDeclaredName("x$$1");
    assertEquals("x$$p2", renamer.getReplacementName("x$$1"));
  }

  @Test
  public void testContextualRenameInverterOriginalName() throws Throwable {
    String original = MakeDeclaredNamesUnique.ContextualRenameInverter.getOrginalName("foo$$1");
    assertEquals("foo", original);

    String originalNoSep = MakeDeclaredNamesUnique.ContextualRenameInverter.getOrginalName("foo");
    assertEquals("foo", originalNoSep);
  }

  @Test
  public void testShouldTraverseAndVisitCoverage() throws Throwable {
    MakeDeclaredNamesUnique instance = new MakeDeclaredNamesUnique();
    
    Node fnNode = new Node(Token.FUNCTION, Node.newString(Token.NAME, "myFunc"));
    Node paramNode = new Node(Token.PARAM_LIST);
    Node bodyNode = new Node(Token.BLOCK);
    fnNode.addChildToBack(paramNode);
    fnNode.addChildToBack(bodyNode);

    NodeTraversal traversal = new NodeTraversal(null, instance);
    
    boolean traverse = instance.shouldTraverse(traversal, fnNode, null);
    assertTrue(traverse);
  }

  @Test
  public void testCatchTokenHandling() throws Throwable {
    MakeDeclaredNamesUnique instance = new MakeDeclaredNamesUnique();
    Node catchNode = new Node(Token.CATCH, Node.newString(Token.NAME, "err"));
    NodeTraversal traversal = new NodeTraversal(null, instance);

    boolean traverse = instance.shouldTraverse(traversal, catchNode, null);
    assertTrue(traverse);
    
    instance.visit(traversal, catchNode, null);
  }

  @Test
  public void testNameTokenHandling() throws Throwable {
    MakeDeclaredNamesUnique instance = new MakeDeclaredNamesUnique();
    Node nameNode = Node.newString(Token.NAME, "foo");
    NodeTraversal traversal = new NodeTraversal(null, instance);

    instance.visit(traversal, nameNode, null);
  }
}