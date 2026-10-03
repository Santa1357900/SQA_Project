package com.google.javascript.jscomp;

import com.google.javascript.rhino.Node;
import com.google.javascript.rhino.Token;
import com.google.javascript.rhino.jstype.JSType;
import com.google.javascript.rhino.jstype.JSTypeNative;
import com.google.javascript.rhino.jstype.JSTypeRegistry;
import org.junit.Test;

import java.util.Map;

import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

public class AmbiguatePropertiesTest {

  @Test
  public void testInstantiationAndProcessBasic() throws Throwable {
    Compiler compiler = new Compiler();
    char[] reserved = new char[0];
    AmbiguateProperties ambiguateProperties = new AmbiguateProperties(compiler, reserved);

    Node externs = new Node(Token.BLOCK);
    Node root = new Node(Token.BLOCK);

    ambiguateProperties.process(externs, root);

    Map<String, String> renamingMap = ambiguateProperties.getRenamingMap();
    assertNotNull(renamingMap);
  }

  @Test
  public void testProcessWithSkipPrefixProperty() throws Throwable {
    Compiler compiler = new Compiler();
    char[] reserved = new char[0];
    AmbiguateProperties ambiguateProperties = new AmbiguateProperties(compiler, reserved);

    Node externs = new Node(Token.BLOCK);
    Node root = new Node(Token.BLOCK);
    
    // Create a property node starting with SKIP_PREFIX ("JSAbstractCompiler")
    Node getProp = new Node(Token.GETPROP);
    Node left = Node.newString(Token.NAME, "obj");
    Node right = Node.newString(Token.STRING, AmbiguateProperties.SKIP_PREFIX + "Test");
    getProp.addChildToBack(left);
    getProp.addChildToBack(right);
    
    root.addChildToBack(getProp);

    ambiguateProperties.process(externs, root);

    Map<String, String> renamingMap = ambiguateProperties.getRenamingMap();
    // Properties starting with SKIP_PREFIX should be skipped and not renamed
    assertTrue(!renamingMap.containsKey(AmbiguateProperties.SKIP_PREFIX + "Test"));
  }

  @Test
  public void testProcessWithExterns() throws Throwable {
    Compiler compiler = new Compiler();
    char[] reserved = new char[0];
    AmbiguateProperties ambiguateProperties = new AmbiguateProperties(compiler, reserved);

    Node externs = new Node(Token.BLOCK);
    Node getProp = new Node(Token.GETPROP);
    Node left = Node.newString(Token.NAME, "window");
    Node right = Node.newString(Token.STRING, "externProp");
    getProp.addChildToBack(left);
    getProp.addChildToBack(right);
    externs.addChildToBack(getProp);

    Node root = new Node(Token.BLOCK);

    ambiguateProperties.process(externs, root);

    Map<String, String> renamingMap = ambiguateProperties.getRenamingMap();
    assertTrue(!renamingMap.containsKey("externProp"));
  }
}