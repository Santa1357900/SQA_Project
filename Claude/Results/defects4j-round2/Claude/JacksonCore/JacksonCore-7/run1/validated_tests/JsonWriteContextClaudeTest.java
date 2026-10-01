package com.fasterxml.jackson.core.json;

import org.junit.Test;
import static org.junit.Assert.*;

public class JsonWriteContextClaudeTest
{
    // createRootContext() deprecated no-arg factory: returns usable root context
    @Test
    public void testCreateRootContext_deprecated_returnsNonNullRootContext() throws Throwable {
        JsonWriteContext root = JsonWriteContext.createRootContext();
        assertNotNull(root);
        assertNull(root.getParent());
    }

    // createRootContext(DupDetector) with null dd: dup detector stays null
    @Test
    public void testCreateRootContextWithDupDetector_null_returnsRootContextWithNullDupDetector() throws Throwable {
        JsonWriteContext root = JsonWriteContext.createRootContext(null);
        assertNotNull(root);
        assertNull(root.getDupDetector());
    }

    // createChildArrayContext: creates a non-null new child the first time
    @Test
    public void testCreateChildArrayContext_fromRoot_returnsNonNullChild() throws Throwable {
        JsonWriteContext root = JsonWriteContext.createRootContext(null);
        JsonWriteContext arr = root.createChildArrayContext();
        assertNotNull(arr);
        assertSame(root, arr.getParent());
    }

    // createChildArrayContext called twice on same parent: reuses & resets same instance
    @Test
    public void testCreateChildArrayContext_calledTwiceOnSameParent_reusesAndResetsSameInstance() throws Throwable {
        JsonWriteContext root = JsonWriteContext.createRootContext(null);
        JsonWriteContext child1 = root.createChildArrayContext();
        child1.writeValue();
        JsonWriteContext child2 = root.createChildArrayContext();
        assertSame(child1, child2);
        assertEquals(JsonWriteContext.STATUS_OK_AS_IS, child2.writeValue());
    }

    // createChildObjectContext: creates a non-null new child the first time
    @Test
    public void testCreateChildObjectContext_fromRoot_returnsNonNullChild() throws Throwable {
        JsonWriteContext root = JsonWriteContext.createRootContext(null);
        JsonWriteContext obj = root.createChildObjectContext();
        assertNotNull(obj);
        assertSame(root, obj.getParent());
    }

    // createChildObjectContext called twice on same parent: reuses & resets currentName/gotName
    @Test
    public void testCreateChildObjectContext_calledTwiceOnSameParent_reusesAndResetsSameInstance() throws Throwable {
        JsonWriteContext root = JsonWriteContext.createRootContext(null);
        JsonWriteContext obj1 = root.createChildObjectContext();
        obj1.writeFieldName("a");
        JsonWriteContext obj2 = root.createChildObjectContext();
        assertSame(obj1, obj2);
        assertNull(obj2.getCurrentName());
        assertEquals(JsonWriteContext.STATUS_OK_AS_IS, obj2.writeFieldName("b"));
    }

    // getParent on root context must be null
    @Test
    public void testGetParent_rootContext_isNull() throws Throwable {
        JsonWriteContext root = JsonWriteContext.createRootContext(null);
        assertNull(root.getParent());
    }

    // getParent on child context must return the exact parent instance
    @Test
    public void testGetParent_childContext_returnsParentInstance() throws Throwable {
        JsonWriteContext root = JsonWriteContext.createRootContext(null);
        JsonWriteContext arr = root.createChildArrayContext();
        assertSame(root, arr.getParent());
    }

    // getCurrentName on fresh context is null
    @Test
    public void testGetCurrentName_freshContext_isNull() throws Throwable {
        JsonWriteContext root = JsonWriteContext.createRootContext(null);
        assertNull(root.getCurrentName());
    }

    // getDupDetector when no dup detector was provided returns null
    @Test
    public void testGetDupDetector_noDupDetectorProvided_isNull() throws Throwable {
        JsonWriteContext root = JsonWriteContext.createRootContext(null);
        assertNull(root.getDupDetector());
    }

    // getCurrentValue initially null on a fresh context
    @Test
    public void testGetCurrentValue_initiallyNull() throws Throwable {
        JsonWriteContext root = JsonWriteContext.createRootContext(null);
        assertNull(root.getCurrentValue());
    }

    // setCurrentValue then getCurrentValue returns the same object set
    @Test
    public void testSetCurrentValue_thenGetCurrentValue_returnsSetValue() throws Throwable {
        JsonWriteContext root = JsonWriteContext.createRootContext(null);
        Object value = "hello";
        root.setCurrentValue(value);
        assertSame(value, root.getCurrentValue());
    }

    // writeFieldName: first call in a fresh object context (_index<0) returns STATUS_OK_AS_IS
    @Test
    public void testWriteFieldName_firstCallInFreshObjectContext_returnsStatusOkAsIs() throws Throwable {
        JsonWriteContext root = JsonWriteContext.createRootContext(null);
        JsonWriteContext obj = root.createChildObjectContext();
        int status = obj.writeFieldName("first");
        assertEquals(JsonWriteContext.STATUS_OK_AS_IS, status);
    }

    // writeFieldName: after one value already written (_index>=0) returns STATUS_OK_AFTER_COMMA
    @Test
    public void testWriteFieldName_afterOneValueWritten_returnsStatusOkAfterComma() throws Throwable {
        JsonWriteContext root = JsonWriteContext.createRootContext(null);
        JsonWriteContext obj = root.createChildObjectContext();
        obj.writeFieldName("first");
        obj.writeValue();
        int status = obj.writeFieldName("second");
        assertEquals(JsonWriteContext.STATUS_OK_AFTER_COMMA, status);
    }

    // writeFieldName called twice without an intervening writeValue returns STATUS_EXPECT_VALUE
    @Test
    public void testWriteFieldName_calledTwiceWithoutValue_returnsStatusExpectValue() throws Throwable {
        JsonWriteContext root = JsonWriteContext.createRootContext(null);
        JsonWriteContext obj = root.createChildObjectContext();
        obj.writeFieldName("first");
        int status = obj.writeFieldName("second");
        assertEquals(JsonWriteContext.STATUS_EXPECT_VALUE, status);
    }

    // writeFieldName sets the current name field visible via getCurrentName
    @Test
    public void testWriteFieldName_setsCurrentNameField() throws Throwable {
        JsonWriteContext root = JsonWriteContext.createRootContext(null);
        JsonWriteContext obj = root.createChildObjectContext();
        obj.writeFieldName("myName");
        assertEquals("myName", obj.getCurrentName());
    }

    // writeFieldName with no dup detector configured: duplicate names are allowed, no exception
    @Test
    public void testWriteFieldName_noDupDetectorConfigured_allowsDuplicateNamesWithoutException() throws Throwable {
        JsonWriteContext root = JsonWriteContext.createRootContext(null);
        JsonWriteContext obj = root.createChildObjectContext();
        obj.writeFieldName("dup");
        obj.writeValue();
        int status = obj.writeFieldName("dup");
        assertEquals(JsonWriteContext.STATUS_OK_AFTER_COMMA, status);
    }

    // writeValue in root context: first call (_index becomes 0) returns STATUS_OK_AS_IS
    @Test
    public void testWriteValue_rootContext_firstCall_returnsStatusOkAsIs() throws Throwable {
        JsonWriteContext root = JsonWriteContext.createRootContext(null);
        int status = root.writeValue();
        assertEquals(JsonWriteContext.STATUS_OK_AS_IS, status);
    }

    // writeValue in root context: second call (_index becomes 1) returns STATUS_OK_AFTER_SPACE
    @Test
    public void testWriteValue_rootContext_secondCall_returnsStatusOkAfterSpace() throws Throwable {
        JsonWriteContext root = JsonWriteContext.createRootContext(null);
        root.writeValue();
        int status = root.writeValue();
        assertEquals(JsonWriteContext.STATUS_OK_AFTER_SPACE, status);
    }

    // writeValue in array context: first call (ix<0) returns STATUS_OK_AS_IS
    @Test
    public void testWriteValue_arrayContext_firstCall_returnsStatusOkAsIs() throws Throwable {
        JsonWriteContext root = JsonWriteContext.createRootContext(null);
        JsonWriteContext arr = root.createChildArrayContext();
        int status = arr.writeValue();
        assertEquals(JsonWriteContext.STATUS_OK_AS_IS, status);
    }

    // writeValue in array context: second call (ix>=0) returns STATUS_OK_AFTER_COMMA
    @Test
    public void testWriteValue_arrayContext_secondCall_returnsStatusOkAfterComma() throws Throwable {
        JsonWriteContext root = JsonWriteContext.createRootContext(null);
        JsonWriteContext arr = root.createChildArrayContext();
        arr.writeValue();
        int status = arr.writeValue();
        assertEquals(JsonWriteContext.STATUS_OK_AFTER_COMMA, status);
    }

    // BUG TEST: writeValue in object context without a preceding writeFieldName must
    // signal that a field name is expected (contract: STATUS_EXPECT_NAME), not silently proceed.
    @Test
    public void testWriteValue_objectContext_withoutPriorFieldName_returnsStatusExpectName() throws Throwable {
        JsonWriteContext root = JsonWriteContext.createRootContext(null);
        JsonWriteContext obj = root.createChildObjectContext();
        int status = obj.writeValue();
        assertEquals(JsonWriteContext.STATUS_EXPECT_NAME, status);
    }

    // writeValue in object context after a field name has been written returns STATUS_OK_AFTER_COLON
    @Test
    public void testWriteValue_objectContext_afterFieldName_returnsStatusOkAfterColon() throws Throwable {
        JsonWriteContext root = JsonWriteContext.createRootContext(null);
        JsonWriteContext obj = root.createChildObjectContext();
        obj.writeFieldName("f");
        int status = obj.writeValue();
        assertEquals(JsonWriteContext.STATUS_OK_AFTER_COLON, status);
    }

    // appendDesc/toString for root context: should be a single slash
    @Test
    public void testAppendDesc_rootContext_toStringIsSlash() throws Throwable {
        JsonWriteContext root = JsonWriteContext.createRootContext(null);
        assertEquals("/", root.toString());
    }

    // appendDesc/toString for array context includes the current index
    @Test
    public void testAppendDesc_arrayContext_toStringContainsIndex() throws Throwable {
        JsonWriteContext root = JsonWriteContext.createRootContext(null);
        JsonWriteContext arr = root.createChildArrayContext();
        arr.writeValue();
        arr.writeValue();
        assertEquals("[1]", arr.toString());
    }

    // appendDesc/toString for object context with no name yet uses '?' placeholder
    @Test
    public void testAppendDesc_objectContextNoName_toStringIsQuestionMark() throws Throwable {
        JsonWriteContext root = JsonWriteContext.createRootContext(null);
        JsonWriteContext obj = root.createChildObjectContext();
        assertEquals("{?}", obj.toString());
    }

    // appendDesc/toString for object context includes the current field name
    @Test
    public void testAppendDesc_objectContextWithName_toStringContainsFieldName() throws Throwable {
        JsonWriteContext root = JsonWriteContext.createRootContext(null);
        JsonWriteContext obj = root.createChildObjectContext();
        obj.writeFieldName("foo");
        assertEquals("{\"foo\"}", obj.toString());
    }

    // withDupDetector(null) returns the same instance ('this') and leaves dup detector null
    @Test
    public void testWithDupDetector_setNull_returnsSameInstanceAndDupDetectorNull() throws Throwable {
        JsonWriteContext root = JsonWriteContext.createRootContext(null);
        JsonWriteContext same = root.withDupDetector(null);
        assertSame(root, same);
        assertNull(root.getDupDetector());
    }
}
