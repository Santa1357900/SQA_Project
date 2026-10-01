package org.apache.commons.lang3.builder;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.junit.Test;
import static org.junit.Assert.*;

public class ToStringStyleClaudeTest {



    // register/isRegistered/unregister round trip for the same reference
    @Test
    public void testRegisterIsRegisteredUnregister_sameInstance_tracksCorrectly() throws Throwable {
        Object obj = new Object();
        ToStringStyle.register(obj);
        assertTrue(ToStringStyle.isRegistered(obj));
        ToStringStyle.unregister(obj);
        assertFalse(ToStringStyle.isRegistered(obj));
    }

    // Branch: value != null guard in register()/unregister()
    @Test
    public void testRegisterAndUnregister_nullValue_noExceptionNoChange() throws Throwable {
        ToStringStyle.register(null);
        ToStringStyle.unregister(null);
        assertFalse(ToStringStyle.isRegistered(null));
    }

    // Branch: toString == null is ignored
    @Test
    public void testAppendSuper_nullToString_ignored() throws Throwable {
        StringBuffer buffer = new StringBuffer();
        ToStringStyle.DEFAULT_STYLE.appendSuper(buffer, null);
        assertEquals(0, buffer.length());
    }

    // Branch: valid toString extracts data between contentStart/contentEnd and appends separator
    @Test
    public void testAppendSuper_validToString_appendsDataAndSeparator() throws Throwable {
        StringBuffer buffer = new StringBuffer();
        ToStringStyle.DEFAULT_STYLE.appendSuper(buffer, "Foo@1[a=1]");
        assertEquals("a=1,", buffer.toString());
    }

    // Branch: pos1 != pos2 fails (no closing marker) -> nothing appended
    @Test
    public void testAppendToString_noMatchingMarkers_doesNothingToBuffer() throws Throwable {
        StringBuffer buffer = new StringBuffer();
        ToStringStyle.DEFAULT_STYLE.appendToString(buffer, "NoMarkersHere");
        assertEquals(0, buffer.length());
    }

    // Branch: object == null -> appendStart does nothing
    @Test
    public void testAppendStart_nullObject_bufferUnchanged() throws Throwable {
        StringBuffer buffer = new StringBuffer();
        ToStringStyle.DEFAULT_STYLE.appendStart(buffer, null);
        assertEquals(0, buffer.length());
    }

    // Branch: valid object appends class name, identity hash code and content start
    @Test
    public void testAppendStart_validObject_appendsClassNameIdAndContentStart() throws Throwable {
        StringBuffer buffer = new StringBuffer();
        Object obj = new Object();
        try {
            ToStringStyle.DEFAULT_STYLE.appendStart(buffer, obj);
            String expected = Object.class.getName() + "@" + Integer.toHexString(System.identityHashCode(obj)) + "[";
            assertEquals(expected, buffer.toString());
        } finally {
            ToStringStyle.unregister(obj);
        }
    }

    // Branch: fieldSeparatorAtEnd == false (default) removes trailing separator before closing
    @Test
    public void testAppendEnd_removesTrailingSeparatorAndAppendsContentEnd() throws Throwable {
        StringBuffer buffer = new StringBuffer("a=1,");
        ToStringStyle.DEFAULT_STYLE.appendEnd(buffer, null);
        assertEquals("a=1]", buffer.toString());
    }

    // appendEnd must unregister the given object from the cyclic-reference registry
    @Test
    public void testAppendEnd_unregistersObject() throws Throwable {
        StringBuffer buffer = new StringBuffer();
        Object obj = new Object();
        ToStringStyle.register(obj);
        ToStringStyle.DEFAULT_STYLE.appendEnd(buffer, obj);
        assertFalse(ToStringStyle.isRegistered(obj));
    }

    // Branch: suffix matches fieldSeparator -> removed
    @Test
    public void testRemoveLastFieldSeparator_matchingSuffix_removed() throws Throwable {
        StringBuffer buffer = new StringBuffer("abc,");
        ToStringStyle.DEFAULT_STYLE.removeLastFieldSeparator(buffer);
        assertEquals("abc", buffer.toString());
    }

    // Branch: suffix does not match fieldSeparator -> buffer unchanged
    @Test
    public void testRemoveLastFieldSeparator_nonMatchingSuffix_unchanged() throws Throwable {
        StringBuffer buffer = new StringBuffer("abcX");
        ToStringStyle.DEFAULT_STYLE.removeLastFieldSeparator(buffer);
        assertEquals("abcX", buffer.toString());
    }

    // Branch: buffer shorter than separator length -> guard skips removal
    @Test
    public void testRemoveLastFieldSeparator_bufferShorterThanSeparator_unchanged() throws Throwable {
        StringBuffer buffer = new StringBuffer("X");
        ToStringStyle.MULTI_LINE_STYLE.removeLastFieldSeparator(buffer);
        assertEquals("X", buffer.toString());
    }

    // Branch: value == null -> appendNullText
    @Test
    public void testAppendObjectFullDetail_nullValue_appendsNullText() throws Throwable {
        StringBuffer buffer = new StringBuffer();
        ToStringStyle.DEFAULT_STYLE.append(buffer, "field", (Object) null, Boolean.TRUE);
        assertEquals("field=<null>,", buffer.toString());
    }

    // Branch: fullDetail TRUE -> full value appended via appendDetail(Object)
    @Test
    public void testAppendObjectFullDetail_detailTrue_appendsFullValue() throws Throwable {
        StringBuffer buffer = new StringBuffer();
        ToStringStyle.DEFAULT_STYLE.append(buffer, "field", (Object) "hello", Boolean.TRUE);
        assertEquals("field=hello,", buffer.toString());
    }

    // Branch: fullDetail FALSE -> summary with short class name
    @Test
    public void testAppendObjectFullDetail_detailFalseSummary_appendsSummaryWithShortClassName() throws Throwable {
        StringBuffer buffer = new StringBuffer();
        ToStringStyle.DEFAULT_STYLE.append(buffer, "field", (Object) "hello", Boolean.FALSE);
        assertEquals("field=<String>,", buffer.toString());
    }

    // Branch: value instanceof Collection, detail=true -> appendDetail(Collection) prints its toString
    @Test
    public void testAppendInternal_collectionDetailTrue_appendsCollectionToString() throws Throwable {
        StringBuffer buffer = new StringBuffer();
        List<String> list = new ArrayList<String>();
        list.add("x");
        ToStringStyle.DEFAULT_STYLE.appendInternal(buffer, "field", list, true);
        assertEquals("[x]", buffer.toString());
    }

    // Branch: value instanceof Collection, detail=false -> appendSummarySize
    @Test
    public void testAppendInternal_collectionDetailFalse_appendsSummarySize() throws Throwable {
        StringBuffer buffer = new StringBuffer();
        List<String> list = new ArrayList<String>();
        list.add("a");
        list.add("b");
        ToStringStyle.DEFAULT_STYLE.appendInternal(buffer, "field", list, false);
        assertEquals("<size=2>", buffer.toString());
    }

    // Branch: value instanceof Map, detail=true -> appendDetail(Map) prints its toString
    @Test
    public void testAppendInternal_mapDetailTrue_appendsMapToString() throws Throwable {
        StringBuffer buffer = new StringBuffer();
        Map<String, String> map = new HashMap<String, String>();
        map.put("k", "v");
        ToStringStyle.DEFAULT_STYLE.appendInternal(buffer, "field", map, true);
        assertEquals("{k=v}", buffer.toString());
    }

    // Branch: value.getClass().isArray() for Object[] -> appendDetail(Object[])
    @Test
    public void testAppendInternal_objectArrayDetailTrue_appendsArrayContents() throws Throwable {
        StringBuffer buffer = new StringBuffer();
        String[] array = new String[] {"a", "b"};
        ToStringStyle.DEFAULT_STYLE.appendInternal(buffer, "field", array, true);
        assertEquals("{a,b}", buffer.toString());
    }

    // Number is excluded from cyclic detection even when already registered
    @Test
    public void testAppendInternal_registeredInteger_notTreatedAsCyclic() throws Throwable {
        StringBuffer buffer = new StringBuffer();
        Integer value = Integer.valueOf(42);
        ToStringStyle.register(value);
        ToStringStyle.DEFAULT_STYLE.appendInternal(buffer, "field", value, true);
        assertEquals("42", buffer.toString());
    }

    // A plain, non-excluded Object already registered is treated as cyclic (identity-style output)
    @Test
    public void testAppendInternal_registeredPlainObject_treatedAsCyclicUsesIdentityFormat() throws Throwable {
        StringBuffer buffer = new StringBuffer();
        Object value = new Object();
        ToStringStyle.register(value);
        try {
            ToStringStyle.DEFAULT_STYLE.appendInternal(buffer, "field", value, true);
            assertTrue(buffer.toString().startsWith(value.getClass().getName()));
        } finally {
            ToStringStyle.unregister(value);
        }
    }



    // appendCyclicObject prints the identity-style representation (class name based)
    @Test
    public void testAppendCyclicObject_appendsIdentityFormat() throws Throwable {
        StringBuffer buffer = new StringBuffer();
        Object obj = new Object();
        ToStringStyle.DEFAULT_STYLE.appendCyclicObject(buffer, "field", obj);
        assertTrue(buffer.toString().startsWith(obj.getClass().getName()));
    }

    // append(long)/append(int) write fieldName=value, separator
    @Test
    public void testAppendPrimitiveIntegersGroup_appendsFieldNameValueAndSeparator() throws Throwable {
        StringBuffer buffer = new StringBuffer();
        ToStringStyle.DEFAULT_STYLE.append(buffer, "f", 10L);
        ToStringStyle.DEFAULT_STYLE.append(buffer, "g", 5);
        assertEquals("f=10,g=5,", buffer.toString());
    }

    // append(short)/append(byte) write fieldName=value, separator
    @Test
    public void testAppendPrimitiveShortByteGroup_appendsFieldNameValueAndSeparator() throws Throwable {
        StringBuffer buffer = new StringBuffer();
        ToStringStyle.DEFAULT_STYLE.append(buffer, "s", (short) 3);
        ToStringStyle.DEFAULT_STYLE.append(buffer, "b", (byte) 7);
        assertEquals("s=3,b=7,", buffer.toString());
    }

    // append(char)/append(double)/append(boolean)/append(float) write fieldName=value, separator
    @Test
    public void testAppendPrimitiveOthersGroup_appendsFieldNameValueAndSeparator() throws Throwable {
        StringBuffer buffer = new StringBuffer();
        ToStringStyle.DEFAULT_STYLE.append(buffer, "c", 'x');
        ToStringStyle.DEFAULT_STYLE.append(buffer, "d", 1.5d);
        ToStringStyle.DEFAULT_STYLE.append(buffer, "bo", true);
        ToStringStyle.DEFAULT_STYLE.append(buffer, "fl", 2.5f);
        assertEquals("c=x,d=1.5,bo=true,fl=2.5,", buffer.toString());
    }

    // Branch: array == null -> appendNullText
    @Test
    public void testAppendIntArray_nullArray_appendsNullText() throws Throwable {
        StringBuffer buffer = new StringBuffer();
        int[] array = null;
        ToStringStyle.DEFAULT_STYLE.append(buffer, "arr", array, Boolean.TRUE);
        assertEquals("arr=<null>,", buffer.toString());
    }

    // Branch: fullDetail TRUE, multiple elements -> appendDetail(int[])
    @Test
    public void testAppendIntArray_fullDetailTrueMultipleElements_appendsDetail() throws Throwable {
        StringBuffer buffer = new StringBuffer();
        int[] array = new int[] {1, 2, 3};
        ToStringStyle.DEFAULT_STYLE.append(buffer, "arr", array, Boolean.TRUE);
        assertEquals("arr={1,2,3},", buffer.toString());
    }

    // Branch: fullDetail FALSE -> appendSummary(int[])
    @Test
    public void testAppendIntArray_fullDetailFalse_appendsSummarySize() throws Throwable {
        StringBuffer buffer = new StringBuffer();
        int[] array = new int[] {1, 2, 3};
        ToStringStyle.DEFAULT_STYLE.append(buffer, "arr", array, Boolean.FALSE);
        assertEquals("arr=<size=3>,", buffer.toString());
    }

    // Branch: fullDetail == null -> isFullDetail falls back to defaultFullDetail (true)
    @Test
    public void testAppendObjectArray_fullDetailNull_usesDefaultFullDetail() throws Throwable {
        StringBuffer buffer = new StringBuffer();
        Object[] array = new Object[] {"a"};
        ToStringStyle.DEFAULT_STYLE.append(buffer, "arr", array, (Boolean) null);
        assertEquals("arr={a},", buffer.toString());
    }

    // boolean[] detail=true appends each element
    @Test
    public void testAppendBooleanArray_detailTrue_appendsElements() throws Throwable {
        StringBuffer buffer = new StringBuffer();
        boolean[] array = new boolean[] {true, false};
        ToStringStyle.DEFAULT_STYLE.append(buffer, "arr", array, Boolean.TRUE);
        assertEquals("arr={true,false},", buffer.toString());
    }

    // Branch: useClassName=true, useShortClassName=false -> full class name appended
    @Test
    public void testAppendClassName_useClassNameTrue_appendsFullClassName() throws Throwable {
        StringBuffer buffer = new StringBuffer();
        Object obj = new Object();
        try {
            ToStringStyle.DEFAULT_STYLE.appendClassName(buffer, obj);
            assertEquals(Object.class.getName(), buffer.toString());
        } finally {
            ToStringStyle.unregister(obj);
        }
    }

    // Branch: useShortClassName=true (SHORT_PREFIX_STYLE) -> short class name appended
    @Test
    public void testAppendClassName_useShortClassNameTrue_appendsShortName() throws Throwable {
        StringBuffer buffer = new StringBuffer();
        Object obj = new Object();
        try {
            ToStringStyle.SHORT_PREFIX_STYLE.appendClassName(buffer, obj);
            assertEquals("Object", buffer.toString());
        } finally {
            ToStringStyle.unregister(obj);
        }
    }

    // Branch: useIdentityHashCode=false (SHORT_PREFIX_STYLE) -> nothing appended
    @Test
    public void testAppendIdentityHashCode_useIdentityHashCodeFalse_doesNotAppend() throws Throwable {
        StringBuffer buffer = new StringBuffer();
        Object obj = new Object();
        ToStringStyle.SHORT_PREFIX_STYLE.appendIdentityHashCode(buffer, obj);
        assertEquals(0, buffer.length());
    }

    // Branch: useIdentityHashCode=true (default) -> '@' + hex identity hash code appended
    @Test
    public void testAppendIdentityHashCode_useIdentityHashCodeTrue_appendsAtAndHex() throws Throwable {
        StringBuffer buffer = new StringBuffer();
        Object obj = new Object();
        try {
            ToStringStyle.DEFAULT_STYLE.appendIdentityHashCode(buffer, obj);
            String expected = "@" + Integer.toHexString(System.identityHashCode(obj));
            assertEquals(expected, buffer.toString());
        } finally {
            ToStringStyle.unregister(obj);
        }
    }

    // appendContentStart/appendContentEnd/appendNullText/appendFieldSeparator append configured text
    @Test
    public void testAppendContentAndNullText_appendsConfiguredText() throws Throwable {
        StringBuffer buffer = new StringBuffer();
        ToStringStyle.DEFAULT_STYLE.appendContentStart(buffer);
        ToStringStyle.DEFAULT_STYLE.appendNullText(buffer, "f");
        ToStringStyle.DEFAULT_STYLE.appendFieldSeparator(buffer);
        ToStringStyle.DEFAULT_STYLE.appendContentEnd(buffer);
        assertEquals("[<null>,]", buffer.toString());
    }

    // Branch: useFieldNames=false skips name; fieldName==null also skips it even if useFieldNames=true
    @Test
    public void testAppendFieldStart_branches() throws Throwable {
        StringBuffer buffer1 = new StringBuffer();
        ToStringStyle.NO_FIELD_NAMES_STYLE.appendFieldStart(buffer1, "field");
        assertEquals(0, buffer1.length());
        StringBuffer buffer2 = new StringBuffer();
        ToStringStyle.DEFAULT_STYLE.appendFieldStart(buffer2, null);
        assertEquals(0, buffer2.length());
    }

    // appendSummarySize formats sizeStartText + size + sizeEndText
    @Test
    public void testAppendSummarySize_appendsSizeStartSizeEndFormat() throws Throwable {
        StringBuffer buffer = new StringBuffer();
        ToStringStyle.DEFAULT_STYLE.appendSummarySize(buffer, "field", 7);
        assertEquals("<size=7>", buffer.toString());
    }

    // isFullDetail: null -> defaultFullDetail; explicit TRUE/FALSE pass through unchanged
    @Test
    public void testIsFullDetail_allBranches_returnsExpectedValue() throws Throwable {
        assertTrue(ToStringStyle.DEFAULT_STYLE.isFullDetail(null));
        assertTrue(ToStringStyle.DEFAULT_STYLE.isFullDetail(Boolean.TRUE));
        assertFalse(ToStringStyle.DEFAULT_STYLE.isFullDetail(Boolean.FALSE));
    }

    // getShortClassName excludes the package name
    @Test
    public void testGetShortClassName_returnsClassNameWithoutPackage() throws Throwable {
        assertEquals("String", ToStringStyle.DEFAULT_STYLE.getShortClassName(String.class));
    }

    // Branch: null text setters convert null to "" (content/field/null text group)
    @Test
    public void testSettersAcceptNull_textFields_convertToEmptyString() throws Throwable {
        ToStringStyle style = new ToStringStyle() { };
        style.setContentStart(null);
        style.setContentEnd(null);
        style.setFieldSeparator(null);
        style.setFieldNameValueSeparator(null);
        style.setNullText(null);
        assertEquals("", style.getContentStart());
        assertEquals("", style.getContentEnd());
        assertEquals("", style.getFieldSeparator());
        assertEquals("", style.getFieldNameValueSeparator());
        assertEquals("", style.getNullText());
    }

    // Branch: null text setters convert null to "" (array/summary text group)
    @Test
    public void testSettersAcceptNull_arrayAndSummaryFields_convertToEmptyString() throws Throwable {
        ToStringStyle style = new ToStringStyle() { };
        style.setArrayStart(null);
        style.setArrayEnd(null);
        style.setArraySeparator(null);
        style.setSizeStartText(null);
        style.setSizeEndText(null);
        style.setSummaryObjectStartText(null);
        style.setSummaryObjectEndText(null);
        assertEquals("", style.getArrayStart());
        assertEquals("", style.getArrayEnd());
        assertEquals("", style.getSummaryObjectStartText());
    }

    // Boolean flag getters/setters round-trip correctly (name/class/identity/field flags)
    @Test
    public void testBooleanFlagSettersAndGetters_roundTrip() throws Throwable {
        ToStringStyle style = new ToStringStyle() { };
        style.setUseClassName(false);
        style.setUseShortClassName(true);
        style.setUseIdentityHashCode(false);
        style.setUseFieldNames(false);
        assertFalse(style.isUseClassName());
        assertTrue(style.isUseShortClassName());
        assertFalse(style.isUseIdentityHashCode());
        assertFalse(style.isUseFieldNames());
    }

    // Boolean flag getters/setters round-trip correctly (detail/separator placement flags)
    @Test
    public void testMoreBooleanFlagSettersAndGetters_roundTrip() throws Throwable {
        ToStringStyle style = new ToStringStyle() { };
        style.setDefaultFullDetail(false);
        style.setArrayContentDetail(false);
        style.setFieldSeparatorAtStart(true);
        style.setFieldSeparatorAtEnd(true);
        assertFalse(style.isDefaultFullDetail());
        assertFalse(style.isArrayContentDetail());
        assertTrue(style.isFieldSeparatorAtStart());
        assertTrue(style.isFieldSeparatorAtEnd());
    }
}
