package org.apache.commons.jxpath.ri;

import junit.framework.TestCase;
import org.apache.commons.jxpath.Pointer;
import org.apache.commons.jxpath.ri.model.NodePointer;

public class NamespaceResolverTest extends TestCase {

    public void testDefaultConstructor() throws Throwable {
        NamespaceResolver resolver = new NamespaceResolver();
        assertNull(resolver.getParent());
        assertFalse(resolver.isSealed());
        assertNull(resolver.getNamespaceContextPointer());
    }

    public void testParentConstructor() throws Throwable {
        NamespaceResolver parent = new NamespaceResolver();
        NamespaceResolver resolver = new NamespaceResolver(parent);
        assertNotNull(resolver);
        assertFalse(resolver.isSealed());
    }

    public void testRegisterNamespaceAndGetURI() throws Throwable {
        NamespaceResolver resolver = new NamespaceResolver();
        resolver.registerNamespace("prefix1", "http://example.com/ns1");
        assertEquals("http://example.com/ns1", resolver.getNamespaceURI("prefix1"));
        assertNull(resolver.getNamespaceURI("unknown"));
    }

    public void testRegisterNamespaceWhenSealed() throws Throwable {
        NamespaceResolver resolver = new NamespaceResolver();
        resolver.seal();
        assertTrue(resolver.isSealed());
        try {
            resolver.registerNamespace("prefix1", "http://example.com/ns1");
            fail("Expected IllegalStateException");
        } catch (IllegalStateException e) {
            assertTrue(e.getMessage().contains("sealed"));
        }
    }

    public void testSealParent() throws Throwable {
        NamespaceResolver parent = new NamespaceResolver();
        NamespaceResolver resolver = new NamespaceResolver(parent);
        resolver.seal();
        assertTrue(resolver.isSealed());
        assertTrue(parent.isSealed());
    }

    public void testGetNamespaceContextPointerWithParent() throws Throwable {
        NamespaceResolver parent = new NamespaceResolver();
        NamespaceResolver resolver = new NamespaceResolver(parent);
        assertNull(resolver.getNamespaceContextPointer());
    }

    public void testGetNamespaceURIFromParent() throws Throwable {
        NamespaceResolver parent = new NamespaceResolver();
        parent.registerNamespace("p", "http://parent.ns");
        NamespaceResolver resolver = new NamespaceResolver(parent);
        assertEquals("http://parent.ns", resolver.getNamespaceURI("p"));
    }

    public void testGetPrefixWithParent() throws Throwable {
        NamespaceResolver parent = new NamespaceResolver();
        parent.registerNamespace("pParent", "http://uri.parent");
        
        NamespaceResolver resolver = new NamespaceResolver(parent);
        resolver.registerNamespace("pChild", "http://uri.child");
        
        assertEquals("pChild", resolver.getPrefix("http://uri.child"));
        assertEquals("pParent", resolver.getPrefix("http://uri.parent"));
        assertNull(resolver.getPrefix("http://unknown.uri"));
    }

    public void testCloneResolver() throws Throwable {
        NamespaceResolver resolver = new NamespaceResolver();
        resolver.registerNamespace("prefix", "http://uri");
        resolver.seal();
        
        NamespaceResolver clone = (NamespaceResolver) resolver.clone();
        assertNotNull(clone);
        assertFalse(clone.isSealed());
        assertEquals("http://uri", clone.getNamespaceURI("prefix"));
    }

    public void testGetPrefixWithNullPointerReverseMapCreation() throws Throwable {
        NamespaceResolver resolver = new NamespaceResolver();
        resolver.registerNamespace("ns", "http://ns.uri");
        // pointer is null, reverseMap should be built solely from namespaceMap
        assertEquals("ns", resolver.getPrefix("http://ns.uri"));
        // Call again to hit reverseMap != null branch
        assertEquals("ns", resolver.getPrefix("http://ns.uri"));
    }
}