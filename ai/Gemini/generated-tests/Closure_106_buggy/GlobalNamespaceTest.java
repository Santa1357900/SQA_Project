package com.google.javascript.jscomp;

import com.google.javascript.rhino.Node;
import com.google.javascript.rhino.Token;
import org.junit.Test;

import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.junit.Assert.*;

public class GlobalNamespaceTest {

    @Test
    public void testGlobalNamespaceCreation() throws Throwable {
        Compiler compiler = new Compiler();
        Node root = new Node(Token.BLOCK);
        GlobalNamespace namespace = new GlobalNamespace(compiler, root);
        assertNotNull(namespace);
        
        List<Name> forest = namespace.getNameForest();
        assertNotNull(forest);
        
        Map<String, Name> index = namespace.getNameIndex();
        assertNotNull(index);
    }

    @Test
    public void testGlobalNamespaceWithExterns() throws Throwable {
        Compiler compiler = new Compiler();
        Node externsRoot = new Node(Token.BLOCK);
        Node root = new Node(Token.BLOCK);
        GlobalNamespace namespace = new GlobalNamespace(compiler, externsRoot, root);
        assertNotNull(namespace);
        
        Map<String, Name> index = namespace.getNameIndex();
        assertNotNull(index);
    }

    @Test
    public void testScanNewNodes() throws Throwable {
        Compiler compiler = new Compiler();
        Node root = new Node(Token.BLOCK);
        GlobalNamespace namespace = new GlobalNamespace(compiler, root);
        
        Scope scope = new Scope(null, root);
        Set<Node> newNodes = new HashSet<Node>();
        Node node = IR.name("a");
        newNodes.add(node);
        
        namespace.scanNewNodes(scope, newNodes);
        assertTrue(true);
    }

    @Test
    public void testNameOperations() throws Throwable {
        GlobalNamespace.Name name = new GlobalNamespace.Name("testName", null, false);
        assertEquals("testName", name.name);
        assertNull(name.parent);
        assertFalse(name.inExterns);
        assertTrue(name.isSimpleName());
        
        GlobalNamespace.Name prop = name.addProperty("propName", false);
        assertNotNull(prop);
        assertEquals("testName.propName", prop.fullName());
        assertFalse(prop.isSimpleName());
        
        name.setIsClassOrEnum();
        assertTrue(name.canCollapse());
        
        GlobalNamespace.Ref ref = GlobalNamespace.Ref.createRefForTesting(GlobalNamespace.Ref.Type.SET_FROM_GLOBAL);
        assertNotNull(ref);
        assertTrue(ref.isSet());
        
        GlobalNamespace.Ref cloned = ref.cloneAndReclassify(GlobalNamespace.Ref.Type.DIRECT_GET);
        assertNotNull(cloned);
        assertFalse(cloned.isSet());
    }

    @Test
    public void testRefMarkTwins() throws Throwable {
        GlobalNamespace.Ref ref1 = GlobalNamespace.Ref.createRefForTesting(GlobalNamespace.Ref.Type.SET_FROM_GLOBAL);
        GlobalNamespace.Ref ref2 = GlobalNamespace.Ref.createRefForTesting(GlobalNamespace.Ref.Type.ALIASING_GET);
        
        GlobalNamespace.Ref.markTwins(ref1, ref2);
        assertEquals(ref2, ref1.getTwin());
        assertEquals(ref1, ref2.getTwin());
    }

    @Test
    public void testNameAddAndRemoveRef() throws Throwable {
        GlobalNamespace.Name name = new GlobalNamespace.Name("a", null, false);
        GlobalNamespace.Ref ref = GlobalNamespace.Ref.createRefForTesting(GlobalNamespace.Ref.Type.SET_FROM_GLOBAL);
        
        name.addRef(ref);
        assertEquals(1, name.globalSets);
        
        name.removeRef(ref);
        assertEquals(0, name.globalSets);
    }
}