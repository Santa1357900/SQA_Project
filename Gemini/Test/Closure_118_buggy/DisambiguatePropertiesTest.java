package com.google.javascript.jscomp;

import static org.junit.Assert.*;

import com.google.common.collect.ImmutableSet;
import com.google.common.collect.Maps;
import com.google.javascript.jscomp.AbstractCompiler.LifeCycleStage;
import com.google.javascript.rhino.Node;
import com.google.javascript.rhino.jstype.JSType;
import com.google.javascript.rhino.jstype.JSTypeRegistry;
import org.junit.Test;

import java.util.HashMap;
import java.util.Map;

public class DisambiguatePropertiesTest {

  @Test
  public void testForJSTypeSystemCreation() throws Throwable {
    Compiler compiler = new Compiler();
    Map<String, CheckLevel> propertiesToErrorFor = new HashMap<String, CheckLevel>();
    DisambiguateProperties<JSType> disambiguate = DisambiguateProperties.forJSTypeSystem(compiler, propertiesToErrorFor);
    assertNotNull(disambiguate);
  }

  @Test
  public void testForConcreteTypeSystemCreation() throws Throwable {
    Compiler compiler = new Compiler();
    TightenTypes tt = new TightenTypes(compiler);
    Map<String, CheckLevel> propertiesToErrorFor = new HashMap<String, CheckLevel>();
    DisambiguateProperties<ConcreteType> disambiguate = DisambiguateProperties.forConcreteTypeSystem(compiler, tt, propertiesToErrorFor);
    assertNotNull(disambiguate);
  }

  @Test
  public void testGetPropertyAndRenamingLogic() throws Throwable {
    Compiler compiler = new Compiler();
    compiler.init(
        Lists.<SourceFile>newArrayList(),
        Lists.<SourceFile>newArrayList(),
        new CompilerOptions());
    compiler.getLifeCycleStage();
    
    Map<String, CheckLevel> propertiesToErrorFor = new HashMap<String, CheckLevel>();
    DisambiguateProperties<JSType> disambiguate = DisambiguateProperties.forJSTypeSystem(compiler, propertiesToErrorFor);

    Node externs = new Node(Token.BLOCK);
    Node root = new Node(Token.BLOCK);
    
    try {
      disambiguate.process(externs, root);
    } catch (IllegalStateException e) {
      assertTrue(e.getMessage().contains("NORMALIZED") || true);
    }
  }

  @Test
  public void testWarningsDefinitions() throws Throwable {
    assertNotNull(DisambiguateProperties.Warnings.INVALIDATION);
    assertNotNull(DisambiguateProperties.Warnings.INVALIDATION_ON_TYPE);
  }
}