package org.apache.commons.lang3.builder;

import org.junit.Test;
import static org.junit.Assert.*;

import java.util.ArrayList;
import java.util.HashMap;

public class ToStringStyleTest {

    private static class TestToStringStyle extends ToStringStyle {
        private static final long serialVersionUID = 1L;
    }

    @Test
    public void testConstants() throws Throwable {
        assertNotNull(ToStringStyle.DEFAULT_STYLE);
        assertNotNull(ToStringStyle.MULTI_LINE_STYLE);
        assertNotNull(ToStringStyle.NO_FIELD_NAMES_STYLE);
        assertNotNull(ToStringStyle.SHORT_PREFIX_STYLE);
        assertNotNull(ToStringStyle.SIMPLE_STYLE);
    }

    @Test
    public void testRegistry() throws Throwable {
        Object obj = new Object();
        assertFalse(ToStringStyle.isRegistered(obj));
        ToStringStyle.register(obj);
        assertTrue(ToStringStyle.isRegistered(obj));
        ToStringStyle.unregister(obj);
        assertFalse(ToStringStyle.isRegistered(obj));

        ToStringStyle.register(null);
        ToStringStyle.unregister(null);
    }

    @Test
    public void testAppendSuperAndToString() throws Throwable {
        ToStringStyle style = ToStringStyle.DEFAULT_STYLE;
        StringBuffer sb = new StringBuffer();
        style.appendToString(sb, null);
        style.appendSuper(sb, null);

        StringBuffer sb2 = new StringBuffer();
        style.appendStart(sb2, new Object());
        style.appendEnd(sb2, new Object());
        String baseStr = sb2.toString();

        StringBuffer sb3 = new StringBuffer();
        style.appendToString(sb3, baseStr);
        assertTrue(sb3.length() >= 0);
    }

    @Test
    public void testAppendPrimitives() throws Throwable {
        ToStringStyle style = ToStringStyle.DEFAULT_STYLE;
        StringBuffer sb = new StringBuffer();

        style.append(sb, "fieldLong", 10L);
        style.append(sb, "fieldInt", 20);
        style.append(sb, "fieldShort", (short) 30);
        style.append(sb, "fieldByte", (byte) 40);
        style.append(sb, "fieldChar", 'a');
        style.append(sb, "fieldDouble", 50.0);
        style.append(sb, "fieldFloat", 60.0f);
        style.append(sb, "fieldBoolean", true);

        assertTrue(sb.length() > 0);
    }

    @Test
    public void testAppendObjectsAndCollectionsAndMaps() throws Throwable {
        ToStringStyle style = ToStringStyle.DEFAULT_STYLE;
        StringBuffer sb = new StringBuffer();

        style.append(sb, "objNull", (Object) null, true);
        style.append(sb, "objStr", "testValue", true);
        style.append(sb, "objStrSummary", "testValue", false);

        ArrayList<String> list = new ArrayList<String>();
        list.add("item1");
        style.append(sb, "listDetail", list, true);
        style.append(sb, "listSummary", list, false);

        HashMap<String, String> map = new HashMap<String, String>();
        map.put("key1", "val1");
        style.append(sb, "mapDetail", map, true);
        style.append(sb, "mapSummary", map, false);

        assertTrue(sb.length() > 0);
    }

    @Test
    public void testAppendArrays() throws Throwable {
        ToStringStyle style = ToStringStyle.DEFAULT_STYLE;
        StringBuffer sb = new StringBuffer();

        Object[] objArray = new Object[] { "a", null, "b" };
        style.append(sb, "objArrayNull", (Object[]) null, true);
        style.append(sb, "objArrayDetail", objArray, true);
        style.append(sb, "objArraySummary", objArray, false);

        long[] longArray = new long[] { 1L, 2L };
        style.append(sb, "longArrayNull", (long[]) null, true);
        style.append(sb, "longArrayDetail", longArray, true);
        style.append(sb, "longArraySummary", longArray, false);

        int[] intArray = new int[] { 1, 2 };
        style.append(sb, "intArrayDetail", intArray, true);
        style.append(sb, "intArraySummary", intArray, false);

        short[] shortArray = new short[] { 1, 2 };
        style.append(sb, "shortArrayDetail", shortArray, true);
        style.append(sb, "shortArraySummary", shortArray, false);

        byte[] byteArray = new byte[] { 1, 2 };
        style.append(sb, "byteArrayDetail", byteArray, true);
        style.append(sb, "byteArraySummary", byteArray, false);

        char[] charArray = new char[] { 'a', 'b' };
        style.append(sb, "charArrayDetail", charArray, true);
        style.append(sb, "charArraySummary", charArray, false);

        double[] doubleArray = new double[] { 1.0, 2.0 };
        style.append(sb, "doubleArrayDetail", doubleArray, true);
        style.append(sb, "doubleArraySummary", doubleArray, false);

        float[] floatArray = new float[] { 1.0f, 2.0f };
        style.append(sb, "floatArrayDetail", floatArray, true);
        style.append(sb, "floatArraySummary", floatArray, false);

        boolean[] booleanArray = new boolean[] { true, false };
        style.append(sb, "booleanArrayDetail", booleanArray, true);
        style.append(sb, "booleanArraySummary", booleanArray, false);

        assertTrue(sb.length() > 0);
    }

    @Test
    public void testSettersAndGetters() throws Throwable {
        TestToStringStyle style = new TestToStringStyle();

        style.setUseClassName(true);
        assertTrue(style.isUseClassName());

        style.setUseShortClassName(true);
        assertTrue(style.isUseShortClassName());

        style.setUseIdentityHashCode(true);
        assertTrue(style.isUseIdentityHashCode());

        style.setUseFieldNames(true);
        assertTrue(style.isUseFieldNames());

        style.setDefaultFullDetail(true);
        assertTrue(style.isDefaultFullDetail());

        style.setArrayContentDetail(true);
        assertTrue(style.isArrayContentDetail());

        style.setArrayStart(null);
        style.setArrayStart("{");
        assertEquals("{", style.getArrayStart());

        style.setArrayEnd(null);
        style.setArrayEnd("}");
        assertEquals("}", style.getArrayEnd());

        style.setArraySeparator(null);
        style.setArraySeparator(",");
        assertEquals(",", style.getArraySeparator());

        style.setContentStart(null);
        style.setContentStart("[");
        assertEquals("[", style.getContentStart());

        style.setContentEnd(null);
        style.setContentEnd("]");
        assertEquals("]", style.getContentEnd());

        style.setFieldNameValueSeparator(null);
        style.setFieldNameValueSeparator("=");
        assertEquals("=", style.getFieldNameValueSeparator());

        style.setFieldSeparator(null);
        style.setFieldSeparator(",");
        assertEquals(",", style.getFieldSeparator());

        style.setFieldSeparatorAtStart(true);
        assertTrue(style.isFieldSeparatorAtStart());

        style.setFieldSeparatorAtEnd(true);
        assertTrue(style.isFieldSeparatorAtEnd());

        style.setNullText(null);
        style.setNullText("<null>");
        assertEquals("<null>", style.getNullText());

        style.setSizeStartText(null);
        style.setSizeStartText("<size=");
        assertEquals("<size=", style.getSizeStartText());

        style.setSizeEndText(null);
        style.setSizeEndText(">");
        assertEquals(">", style.getSizeEndText());

        style.setSummaryObjectStartText(null);
        style.setSummaryObjectStartText("<");
        assertEquals("<", style.getSummaryObjectStartText());

        style.setSummaryObjectEndText(null);
        style.setSummaryObjectEndText(">");
        assertEquals(">", style.getSummaryObjectEndText());
    }

    @Test
    public void testCyclicObject() throws Throwable {
        TestToStringStyle style = new TestToStringStyle();
        StringBuffer sb = new StringBuffer();
        Object obj = new Object();
        ToStringStyle.register(obj);
        try {
            style.appendInternal(sb, "cyclic", obj, true);
        } finally {
            ToStringStyle.unregister(obj);
        }
        assertTrue(sb.length() > 0);
    }
}