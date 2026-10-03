package com.google.javascript.jscomp;

import com.google.javascript.rhino.Node;
import com.google.javascript.rhino.Token;
import org.junit.Test;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Map;

import static org.junit.Assert.*;

public class GlobalNamespaceTest {

  @Test
  public void testGlobalNamespaceCreationAndBasicMethods() throws Throwable {
    Compiler compiler = new Compiler();
    Node root = new Node(Token.BLOCK);
    GlobalNamespace namespace = new GlobalNamespace(compiler, root);

    assertFalse(namespace.hasExternsRoot());
    assertNotNull(namespace.getRootNode());
    assertNull(namespace.getParentScope());
    assertNotNull(namespace.getTypeOfThis());
    assertNull(namespace.getScope(null));
  }

  @Test
  public void testGlobalNamespaceWithExterns() throws Throwable {
    Compiler compiler = new Compiler();
    Node externsRoot = new Node(Token.BLOCK);
    Node root = new Node(Token.BLOCK);
    GlobalNamespace namespace = new GlobalNamespace(compiler, externsRoot, root);

    assertTrue(namespace.hasExternsRoot());
  }

  @Test
  public void testNameMethods() throws Throwable {
    GlobalNamespace.Name nameObj = new GlobalNamespace.Name("a", null, false);
    assertEquals("a", nameObj.getBaseName());
    assertEquals("a", nameObj.getName());
    assertEquals("a", nameObj.getFullName());
    assertNull(nameObj.getDeclaration());
    assertFalse(nameObj.isTypeInferred());
    assertNull(nameObj.getType());
    assertTrue(nameObj.getRefs().isEmpty());
    assertTrue(nameObj.isSimpleName());
    assertFalse(nameObj.isDeclaredType());
    assertFalse(nameObj.isNamespace());
    assertNotNull(nameObj.toString());
    assertNull(nameObj.getJSDocInfo());

    GlobalNamespace.Name propObj = nameObj.addProperty("b", false);
    assertEquals("b", propObj.getBaseName());
    assertEquals("a.b", propObj.getFullName());
    assertFalse(propObj.isSimpleName());
  }

  @Test
  public void testRefMethods() throws Throwable {
    GlobalNamespace.Ref ref = GlobalNamespace.Ref.createRefForTesting(GlobalNamespace.Ref.Type.DIRECT_GET);
    assertNull(ref.getNode());
    assertNull(ref.getSourceFile());
    assertNull(ref.getSymbol());
    assertNull(ref.getModule());
    assertEquals("", ref.getSourceName());
    assertNull(ref.getTwin());
    assertFalse(ref.isSet());

    GlobalNamespace.Ref cloned = ref.cloneAndReclassify(GlobalNamespace.Ref.Type.ALIASING_GET);
    assertNotNull(cloned);
  }

  @Test
  public void testScanNewNodesWithEmptyList() throws Throwable {
    Compiler compiler = new Compiler();
    Node root = new Node(Token.BLOCK);
    GlobalNamespace namespace = new GlobalNamespace(compiler, root);

    List<GlobalNamespace.AstChange> newNodes = new ArrayList<GlobalNamespace.AstChange>();
    namespace.scanNewNodes(newNodes);
    
    Map<String, GlobalNamespace.Name> index = namespace.getNameIndex();
    assertNotNull(index);
  }
}