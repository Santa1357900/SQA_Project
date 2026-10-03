package com.fasterxml.jackson.core.json;

import com.fasterxml.jackson.core.DupDetector;
import com.fasterxml.jackson.core.JsonGenerationException;
import com.fasterxml.jackson.core.JsonProcessingException;
import org.junit.Test;

import static org.junit.Assert.*;

public class JsonWriteContextTest {

    @Test
    public void testCreateRootContextWithoutDupDetector() throws Throwable {
        JsonWriteContext context = JsonWriteContext.createRootContext();
        assertNotNull(context);
        assertEquals(JsonWriteContext.TYPE_ROOT, context.getType());
        assertNull(context.getParent());
        assertNull(context.getCurrentName());
        assertNull(context.getCurrentValue());
        assertNull(context.getDupDetector());
        assertEquals("/", context.toString());
    }

    @Test
    public void testCreateRootContextWithDupDetector() throws Throwable {
        DupDetector dd = DupDetector.rootDetector((Object) null);
        JsonWriteContext context = JsonWriteContext.createRootContext(dd);
        assertNotNull(context);
        assertEquals(JsonWriteContext.TYPE_ROOT, context.getType());
        assertEquals(dd, context.getDupDetector());
    }

    @Test
    public void testCurrentValueOperations() throws Throwable {
        JsonWriteContext context = JsonWriteContext.createRootContext();
        assertNull(context.getCurrentValue());
        
        String testValue = "myValue";
        context.setCurrentValue(testValue);
        assertEquals(testValue, context.getCurrentValue());
        
        context.setCurrentValue(null);
        assertNull(context.getCurrentValue());
    }

    @Test
    public void testChildArrayContextReuse() throws Throwable {
        JsonWriteContext root = JsonWriteContext.createRootContext();
        
        JsonWriteContext child1 = root.createChildArrayContext();
        assertNotNull(child1);
        assertEquals(JsonWriteContext.TYPE_ARRAY, child1.getType());
        assertEquals(root, child1.getParent());
        assertEquals("[0]", child1.toString());

        // Test reuse of child context
        JsonWriteContext child2 = root.createChildArrayContext();
        assertEquals(child1, child2);
    }

    @Test
    public void testChildObjectContextReuse() throws Throwable {
        JsonWriteContext root = JsonWriteContext.createRootContext();
        
        JsonWriteContext child1 = root.createChildObjectContext();
        assertNotNull(child1);
        assertEquals(JsonWriteContext.TYPE_OBJECT, child1.getType());
        assertEquals(root, child1.getParent());
        assertEquals("{?}", child1.toString());

        // Test reuse of child context
        JsonWriteContext child2 = root.createChildObjectContext();
        assertEquals(child1, child2);
    }

    @Test
    public void testWriteFieldNameInObject() throws Throwable {
        JsonWriteContext root = JsonWriteContext.createRootContext();
        JsonWriteContext objContext = root.createChildObjectContext();

        int status1 = objContext.writeFieldName("field1");
        assertEquals(JsonWriteContext.STATUS_OK_AS_IS, status1);
        assertEquals("field1", objContext.getCurrentName());
        assertEquals("{\"field1\"}", objContext.toString());

        // Writing another field name without value should trigger STATUS_EXPECT_VALUE
        int status2 = objContext.writeFieldName("field2");
        assertEquals(JsonWriteContext.STATUS_EXPECT_VALUE, status2);
    }

    @Test
    public void testWriteValueInObject() throws Throwable {
        JsonWriteContext root = JsonWriteContext.createRootContext();
        JsonWriteContext objContext = root.createChildObjectContext();

        objContext.writeFieldName("field1");
        int status = objContext.writeValue();
        assertEquals(JsonWriteContext.STATUS_OK_AFTER_COLON, status);
        assertEquals(0, objContext.getCurrentIndex());
    }

    @Test
    public void testWriteValueInArray() throws Throwable {
        JsonWriteContext root = JsonWriteContext.createRootContext();
        JsonWriteContext arrContext = root.createChildArrayContext();

        int status1 = arrContext.writeValue();
        assertEquals(JsonWriteContext.STATUS_OK_AS_IS, status1);
        assertEquals(0, arrContext.getCurrentIndex());
        assertEquals("[0]", arrContext.toString());

        int status2 = arrContext.writeValue();
        assertEquals(JsonWriteContext.STATUS_OK_AFTER_COMMA, status2);
        assertEquals(1, arrContext.getCurrentIndex());
        assertEquals("[1]", arrContext.toString());
    }

    @Test
    public void testWriteValueInRoot() throws Throwable {
        JsonWriteContext root = JsonWriteContext.createRootContext();

        int status1 = root.writeValue();
        assertEquals(JsonWriteContext.STATUS_OK_AS_IS, status1);
        assertEquals(0, root.getCurrentIndex());

        int status2 = root.writeValue();
        assertEquals(JsonWriteContext.STATUS_OK_AFTER_SPACE, status2);
        assertEquals(1, root.getCurrentIndex());
    }

    @Test
    public void testDuplicateFieldDetection() throws Throwable {
        DupDetector dd = DupDetector.rootDetector((Object) null);
        JsonWriteContext root = JsonWriteContext.createRootContext(dd);
        JsonWriteContext objContext = root.createChildObjectContext();

        objContext.writeFieldName("dupField");
        objContext.writeValue();

        boolean exceptionThrown = false;
        try {
            objContext.writeFieldName("dupField");
        } catch (JsonGenerationException e) {
            exceptionThrown = true;
            assertTrue(e.getMessage().contains("Duplicate field 'dupField'"));
        }
        assertTrue(exceptionThrown);
    }

    @Test
    public void testWithDupDetector() throws Throwable {
        JsonWriteContext root = JsonWriteContext.createRootContext();
        assertNull(root.getDupDetector());

        DupDetector dd = DupDetector.rootDetector((Object) null);
        JsonWriteContext updated = root.withDupDetector(dd);
        assertEquals(dd, updated.getDupDetector());
    }

    @Test
    public void testResetContext() throws Throwable {
        DupDetector dd = DupDetector.rootDetector((Object) null);
        JsonWriteContext root = JsonWriteContext.createRootContext(dd);
        JsonWriteContext objContext = root.createChildObjectContext();

        objContext.writeFieldName("name");
        objContext.writeValue();
        objContext.setCurrentValue("value");

        objContext.reset(JsonWriteContext.TYPE_ARRAY);
        assertEquals(JsonWriteContext.TYPE_ARRAY, objContext.getType());
        assertEquals(-1, objContext.getCurrentIndex());
        assertNull(objContext.getCurrentName());
        assertNull(objContext.getCurrentValue());
    }
}