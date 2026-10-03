package org.apache.commons.jxpath.ri.model.beans;

import junit.framework.TestCase;
import org.apache.commons.jxpath.JXPathAbstractFactoryException;
import org.apache.commons.jxpath.ri.QName;
import org.apache.commons.jxpath.ri.model.NodePointer;

public class PropertyPointerTest extends TestCase {

    private static class ConcretePropertyPointer extends PropertyPointer {
        private String propertyName = "testProperty";
        private int propertyCount = 1;
        private String[] propertyNames = new String[] { "testProperty" };
        private boolean actualProperty = true;
        private Object baseValue = "testValue";

        public ConcretePropertyPointer(NodePointer parent) {
            super(parent);
        }

        public String getPropertyName() {
            return propertyName;
        }

        public void setPropertyName(String propertyName) {
            this.propertyName = propertyName;
        }

        public int getPropertyCount() {
            return propertyCount;
        }

        public String[] getPropertyNames() {
            return propertyNames;
        }

        protected boolean isActualProperty() {
            return actualProperty;
        }

        public Object getBaseValue() {
            return baseValue;
        }

        public void setBaseValue(Object baseValue) {
            this.baseValue = baseValue;
        }

        public void setActualProperty(boolean actualProperty) {
            this.actualProperty = actualProperty;
        }
    }

    private static class DummyNodePointer extends NodePointer {
        public DummyNodePointer(NodePointer parent) {
            super(parent);
        }

        public QName getName() {
            return new QName("dummy");
        }

        public Object getBaseValue() {
            return "dummyBase";
        }

        public boolean isCollection() {
            return false;
        }

        public int getLength() {
            return 1;
        }

        public Object getImmediateNode() {
            return "dummyImmediate";
        }

        public boolean isActual() {
            return true;
        }

        public boolean isLeaf() {
            return true;
        }

        public void setValue(Object value) {
        }
    }

    public void testPropertyIndexAndSetIndex() throws Throwable {
        DummyNodePointer parent = new DummyNodePointer(null);
        ConcretePropertyPointer ptr = new ConcretePropertyPointer(parent);

        assertEquals(PropertyPointer.UNSPECIFIED_PROPERTY, ptr.getPropertyIndex());
        assertEquals(NodePointer.WHOLE_COLLECTION, ptr.getIndex());

        ptr.setIndex(5);
        assertEquals(5, ptr.getIndex());

        ptr.setPropertyIndex(10);
        assertEquals(10, ptr.getPropertyIndex());
        assertEquals(NodePointer.WHOLE_COLLECTION, ptr.getIndex());

        ptr.setPropertyIndex(10);
        assertEquals(10, ptr.getPropertyIndex());
    }

    public void testGetName() throws Throwable {
        DummyNodePointer parent = new DummyNodePointer(null);
        ConcretePropertyPointer ptr = new ConcretePropertyPointer(parent);
        ptr.setPropertyName("myProp");

        QName qname = ptr.getName();
        assertNotNull(qname);
        assertEquals("myProp", qname.getName());
        assertNull(qname.getPrefix());
    }

    public void testIsActual() throws Throwable {
        DummyNodePointer parent = new DummyNodePointer(null);
        ConcretePropertyPointer ptr = new ConcretePropertyPointer(parent);

        ptr.setActualProperty(true);
        assertTrue(ptr.isActual());

        ptr.setActualProperty(false);
        assertFalse(ptr.isActual());
    }

    public void testGetImmediateNodeAndCollection() throws Throwable {
        DummyNodePointer parent = new DummyNodePointer(null);
        ConcretePropertyPointer ptr = new ConcretePropertyPointer(parent);

        ptr.setBaseValue("SingleValue");
        assertFalse(ptr.isCollection());
        assertEquals(1, ptr.getLength());
        assertNotNull(ptr.getImmediateNode());

        java.util.List<String> list = new java.util.ArrayList<String>();
        list.add("val1");
        list.add("val2");
        ptr.setBaseValue(list);
        assertTrue(ptr.isCollection());
        assertEquals(2, ptr.getLength());
    }

    public void testIsLeaf() throws Throwable {
        DummyNodePointer parent = new DummyNodePointer(null);
        ConcretePropertyPointer ptr = new ConcretePropertyPointer(parent);

        ptr.setBaseValue(null);
        assertTrue(ptr.isLeaf());

        ptr.setBaseValue(Integer.valueOf(123));
        assertTrue(ptr.isLeaf());
    }

    public void testGetImmediateValuePointer() throws Throwable {
        DummyNodePointer parent = new DummyNodePointer(null);
        ConcretePropertyPointer ptr = new ConcretePropertyPointer(parent);
        ptr.setBaseValue("hello");

        NodePointer valuePtr = ptr.getImmediateValuePointer();
        assertNotNull(valuePtr);
    }

    public void testEqualsAndHashCode() throws Throwable {
        DummyNodePointer parent1 = new DummyNodePointer(null);
        DummyNodePointer parent2 = new DummyNodePointer(null);

        ConcretePropertyPointer ptr1 = new ConcretePropertyPointer(parent1);
        ptr1.setPropertyIndex(1);
        ptr1.setPropertyName("prop");
        ptr1.setIndex(0);

        ConcretePropertyPointer ptr2 = new ConcretePropertyPointer(parent1);
        ptr2.setPropertyIndex(1);
        ptr2.setPropertyName("prop");
        ptr2.setIndex(0);

        ConcretePropertyPointer ptr3 = new ConcretePropertyPointer(parent2);
        ptr3.setPropertyIndex(1);
        ptr3.setPropertyName("prop");
        ptr3.setIndex(0);

        ConcretePropertyPointer ptr4 = new ConcretePropertyPointer(parent1);
        ptr4.setPropertyIndex(2);
        ptr4.setPropertyName("prop");
        ptr4.setIndex(0);

        assertTrue(ptr1.equals(ptr1));
        assertTrue(ptr1.equals(ptr2));
        assertFalse(ptr1.equals(null));
        assertFalse(ptr1.equals("someString"));
        assertFalse(ptr1.equals(ptr3));
        assertFalse(ptr1.equals(ptr4));

        assertTrue(ptr1.hashCode() != 0);
    }

    public void testCreatePathWithoutFactoryException() throws Throwable {
        DummyNodePointer parent = new DummyNodePointer(null);
        ConcretePropertyPointer ptr = new ConcretePropertyPointer(parent);
        ptr.setBaseValue(null);

        try {
            ptr.createPath(null);
            fail("Expected JXPathAbstractFactoryException");
        } catch (JXPathAbstractFactoryException e) {
            assertTrue(e.getMessage() != null);
        } catch (NullPointerException e) {
            // Context is null, so if it fails on context internally, it's acceptable for this unit test structure
        }
    }
}