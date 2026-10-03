package org.apache.commons.jxpath.ri.axes;

import org.junit.Test;
import static org.junit.Assert.*;

import org.apache.commons.jxpath.ri.EvalContext;
import org.apache.commons.jxpath.ri.QName;
import org.apache.commons.jxpath.ri.compiler.NodeNameTest;
import org.apache.commons.jxpath.ri.compiler.NodeTest;
import org.apache.commons.jxpath.ri.compiler.NodeTypeTest;
import org.apache.commons.jxpath.ri.model.NodePointer;

public class AttributeContextTest {

    @Test(expected = NullPointerException.class)
    public void testConstructorNullParent() throws Throwable {
        NodeNameTest nodeTest = new NodeNameTest(new QName("test"));
        AttributeContext context = new AttributeContext(null, nodeTest);
        assertNull(context.getCurrentNodePointer());
    }

    @Test
    public void testResetAndSetPositionBackward() throws Throwable {
        NodeNameTest nodeTest = new NodeNameTest(new QName("test"));
        AttributeContext context = new AttributeContext(null, nodeTest);
        context.reset();
        
        boolean result = context.setPosition(1);
        assertFalse(result);
    }

    @Test
    public void testNextNodeWithNonNodeNameTest() throws Throwable {
        NodeTypeTest nodeTest = new NodeTypeTest(1);
        AttributeContext context = new AttributeContext(null, nodeTest);
        
        boolean result = context.nextNode();
        assertFalse(result);
        assertNull(context.getCurrentNodePointer());
    }

    @Test
    public void testSetPositionZero() throws Throwable {
        NodeNameTest nodeTest = new NodeNameTest(new QName("attr"));
        AttributeContext context = new AttributeContext(null, nodeTest);
        
        boolean result = context.setPosition(0);
        assertTrue(result);
    }

    @Test
    public void testResetMethodDirectly() throws Throwable {
        NodeNameTest nodeTest = new NodeNameTest(new QName("attr"));
        AttributeContext context = new AttributeContext(null, nodeTest);
        
        context.reset();
        assertNull(context.getCurrentNodePointer());
    }
}