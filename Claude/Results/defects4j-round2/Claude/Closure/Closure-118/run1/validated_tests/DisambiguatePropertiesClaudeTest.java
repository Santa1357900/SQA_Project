package com.google.javascript.jscomp;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNotSame;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertSame;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

import org.junit.Before;
import org.junit.Test;

import com.google.common.collect.Multimap;
import com.google.javascript.rhino.IR;
import com.google.javascript.rhino.Node;
import com.google.javascript.rhino.jstype.JSType;
import com.google.javascript.rhino.jstype.JSTypeNative;
import com.google.javascript.rhino.jstype.JSTypeRegistry;

import java.util.Collection;
import java.util.HashMap;
import java.util.Map;

public class DisambiguatePropertiesClaudeTest {

  private Compiler compiler;
  private Map<String, CheckLevel> emptyMap;

  @Before
  public void setUp() throws Throwable {
    compiler = new Compiler();
    emptyMap = new HashMap<String, CheckLevel>();
  }





  // forJSTypeSystem กับ compiler เป็น null ต้องโยน NullPointerException
  @Test
  public void testForJSTypeSystem_nullCompiler_throwsNullPointerException() throws Throwable {
    try {
      DisambiguateProperties.forJSTypeSystem(null, emptyMap);
      fail("expected NullPointerException");
    } catch (NullPointerException expected) {
    }
  }

  // forJSTypeSystem กับ propertiesToErrorFor เป็น null ต้องโยน NullPointerException
  @Test
  public void testForJSTypeSystem_nullPropertiesToErrorFor_throwsNullPointerException() throws Throwable {
    try {
      DisambiguateProperties.forJSTypeSystem(compiler, null);
      fail("expected NullPointerException");
    } catch (NullPointerException expected) {
    }
  }




















































}
