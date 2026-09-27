package com.google.javascript.jscomp;

import org.junit.Test;
import static org.junit.Assert.*;

import com.google.javascript.rhino.Node;
import com.google.javascript.rhino.Token;

public class TypedScopeCreatorTest {

  @Test
  public void testConstantsAndTypes() throws Throwable {
    assertNotNull(TypedScopeCreator.DELEGATE_PROXY_SUFFIX);
    assertNotNull(TypedScopeCreator.MALFORMED_TYPEDEF);
    assertNotNull(TypedScopeCreator.ENUM_INITIALIZER);
    assertNotNull(TypedScopeCreator.CTOR_INITIALIZER);
    assertNotNull(TypedScopeCreator.IFACE_INITIALIZER);
    assertNotNull(TypedScopeCreator.CONSTRUCTOR_EXPECTED);
    assertNotNull(TypedScopeCreator.UNKNOWN_LENDS);
    assertNotNull(TypedScopeCreator.LENDS_ON_NON_OBJECT);
  }

  @Test
  public void testGetBestJSDocInfoNull() throws Throwable {
    Node node = new Node(Token.NUMBER, 1.0);
    assertNull(TypedScopeCreator.getBestJSDocInfo(node));
  }
}