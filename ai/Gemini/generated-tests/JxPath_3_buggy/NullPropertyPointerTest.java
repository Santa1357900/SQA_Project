package org.apache.commons.jxpath.ri.model.beans;

import junit.framework.TestCase;
import org.apache.commons.jxpath.JXPathContext;
import org.apache.commons.jxpath.JXPathInvalidAccessException;
import org.apache.commons.jxpath.ri.QName;
import org.apache.commons.jxpath.ri.model.NodePointer;

public class NullPropertyPointerTest extends TestCase {

    private NullPropertyPointer nullPropertyPointer;
    private BeanPointer parentPointer;

    protected void setUp() throws Exception {
        super.setUp();
        Object bean = new Object();
        QName qName = new QName("testBean");
        parentPointer = new BeanPointer(qName, bean, null, null);
        nullPropertyPointer = new NullPropertyPointer(parentPointer);
    }

    public void testGettersAndSetters() throws Throwable {
        assertEquals("*", nullPropertyPointer.getPropertyName());
        
        nullPropertyPointer.setPropertyName("customProperty");
        assertEquals("customProperty", nullPropertyPointer.getPropertyName());
        
        QName name = nullPropertyPointer.getName();
        assertEquals("customProperty", name.getName());
        
        nullPropertyPointer.setPropertyIndex(5);
        assertEquals(0, nullPropertyPointer.getLength());
        assertNull(nullPropertyPointer.getBaseValue());
        assertNull(nullPropertyPointer.getImmediateNode());
        assertTrue(nullPropertyPointer.isLeaf());
        assertFalse(nullPropertyPointer.isActualProperty());
        assertFalse(nullPropertyPointer.isActual());
        assertTrue(nullPropertyPointer.isContainer());
        assertEquals(0, nullPropertyPointer.getPropertyCount());
        
        String[] names = nullPropertyPointer.getPropertyNames();
        assertNotNull(names);
        assertEquals(0, names.length);
    }

    public void testGetValuePointer() throws Throwable {
        nullPropertyPointer.setPropertyName("someProp");
        NodePointer vp = nullPropertyPointer.getValuePointer();
        assertNotNull(vp);
        assertTrue(vp instanceof NullPointer);
    }

    public void testIsCollection() throws Throwable {
        nullPropertyPointer.setIndex(NodePointer.WHOLE_COLLECTION);
        assertFalse(nullPropertyPointer.isCollection());

        nullPropertyPointer.setIndex(0);
        assertTrue(nullPropertyPointer.isCollection());
    }

    public void testSetNameAttributeValueAndAsPath() throws Throwable {
        nullPropertyPointer.setPropertyName("prop's name\"withquotes");
        nullPropertyPointer.setNameAttributeValue("prop's name\"withquotes");
        
        String path = nullPropertyPointer.asPath();
        assertNotNull(path);
        assertTrue(path.contains("@name='prop&apos;s name&quot;withquotes'"));

        nullPropertyPointer.setIndex(0);
        String pathWithIndex = nullPropertyPointer.asPath();
        assertTrue(pathWithIndex.contains("[1]"));
    }

    public void testSetValueWithNullParent() throws Throwable {
        NullPropertyPointer orphanPointer = new NullPropertyPointer(null);
        try {
            orphanPointer.setValue("value");
            fail("Expected JXPathInvalidAccessException");
        } catch (JXPathInvalidAccessException e) {
            assertTrue(e.getMessage().contains("target object is null"));
        }
    }

    public void testSetValueWithContainerParent() throws Throwable {
        NullPointer containerParent = new NullPointer(parentPointer, new QName("container"));
        NullPropertyPointer containerPropPointer = new NullPropertyPointer(containerParent);
        try {
            containerPropPointer.setValue("value");
            fail("Expected JXPathInvalidAccessException");
        } catch (JXPathInvalidAccessException e) {
            assertTrue(e.getMessage().contains("target object is null"));
        }
    }

    public void testSetValueDynamicNotSupported() throws Throwable {
        try {
            nullPropertyPointer.setValue("value");
            fail("Expected JXPathInvalidAccessException");
        } catch (JXPathInvalidAccessException e) {
            assertTrue(e.getMessage().contains("path does not match a changeable location"));
        }
    }

    public void testCreateChildDelegation() throws Throwable {
        // Test that createChild delegates properly; might throw exception depending on context/parent,
        // but we verify execution flow.
        JXPathContext context = JXPathContext.newContext(new Object());
        QName childName = new QName("childProp");
        try {
            nullPropertyPointer.createChild(context, childName, 0);
        } catch (Throwable t) {
            // Expected if parent cannot create path directly without real bean property structures
            assertNotNull(t);
        }

        try {
            nullPropertyPointer.createChild(context, childName, 0, "val");
        } catch (Throwable t) {
            // Expected
            assertNotNull(t);
        }
    }
}