package com.fasterxml.jackson.dataformat.xml.ser;

import java.io.StringWriter;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import org.junit.Test;
import static org.junit.Assert.*;

import com.fasterxml.jackson.core.JsonFactory;
import com.fasterxml.jackson.core.JsonGenerator;
import com.fasterxml.jackson.databind.JsonMappingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.dataformat.xml.XmlMapper;

public class XmlSerializerProviderClaudeTest
{
    public static class SimpleBean {
        public String value;
        public SimpleBean() { }
        public SimpleBean(String v) { this.value = v; }
    }

    public static class MultiFieldBean {
        public String name;
        public int age;
        public MultiFieldBean() { }
        public MultiFieldBean(String n, int a) { name = n; age = a; }
    }

    public static class ThrowingBean {
        public String getValue() {
            throw new IllegalStateException("boom");
        }
    }

    // serializeValue: xgen != null, no configured root name -> uses default (simple class name) root tag and field value
    @Test
    public void testSerializeValue_defaultRootName_usesClassSimpleNameAndFieldValue() throws Throwable {
        XmlMapper mapper = new XmlMapper();
        String xml = mapper.writeValueAsString(new SimpleBean("hello"));
        assertTrue(xml.contains("<SimpleBean>"));
        assertTrue(xml.contains("<value>hello</value>"));
    }

    // _rootNameFromConfig: configured full root name overrides default lookup based on class
    @Test
    public void testSerializeValue_configuredRootName_overridesDefault() throws Throwable {
        XmlMapper mapper = new XmlMapper();
        String xml = mapper.writer().withRootName("custom").writeValueAsString(new SimpleBean("x"));
        assertTrue(xml.contains("<custom>"));
        assertFalse(xml.contains("<SimpleBean>"));
    }

    // _serializeXmlNull: null value with no configured root name falls back to ROOT_NAME_FOR_NULL ("null")
    @Test
    public void testSerializeValue_nullValue_usesNullRootName() throws Throwable {
        XmlMapper mapper = new XmlMapper();
        String xml = mapper.writeValueAsString(null);
        assertTrue(xml.contains("<null"));
    }

    // _serializeXmlNull: null value with explicitly configured root name uses the configured name instead of default
    @Test
    public void testSerializeValue_nullValueWithConfiguredRootName_usesConfiguredRootName() throws Throwable {
        XmlMapper mapper = new XmlMapper();
        String xml = mapper.writer().withRootName("myRoot").writeValueAsString(null);
        assertTrue(xml.contains("<myRoot"));
        assertFalse(xml.contains("<null"));
    }

    // serializeValue: root value is an array of primitives -> asArray branch wraps each entry with "item"
    @Test
    public void testSerializeValue_arrayRootValueWithExplicitRootName_wrapsEachElementAsItem() throws Throwable {
        XmlMapper mapper = new XmlMapper();
        int[] arr = new int[] {1, 2, 3};
        String xml = mapper.writer().withRootName("numbers").writeValueAsString(arr);
        assertTrue(xml.contains("<item>1</item>"));
        assertTrue(xml.contains("<item>2</item>"));
        assertTrue(xml.contains("<item>3</item>"));
    }

    // serializeValue: root value is a List -> asArray branch wraps each entry with "item"
    @Test
    public void testSerializeValue_listRootValueWithExplicitRootName_wrapsEachElementAsItem() throws Throwable {
        XmlMapper mapper = new XmlMapper();
        List<String> list = new ArrayList<String>();
        list.add("a");
        list.add("b");
        String xml = mapper.writer().withRootName("letters").writeValueAsString(list);
        assertTrue(xml.contains("<item>a</item>"));
        assertTrue(xml.contains("<item>b</item>"));
    }

    // serializeValue: empty List root still hits asArray branch, but no item children get written
    @Test
    public void testSerializeValue_emptyListRootValue_noItemElementsProduced() throws Throwable {
        XmlMapper mapper = new XmlMapper();
        List<String> empty = new ArrayList<String>();
        String xml = mapper.writer().withRootName("emptyRoot").writeValueAsString(empty);
        assertFalse(xml.contains("<item"));
        assertTrue(xml.length() > 0);
    }

    // serializeValue overload with rootType: single element list still wraps correctly
    @Test
    public void testSerializeValue_singleElementListWithExplicitRootName_wrapsSingleItem() throws Throwable {
        XmlMapper mapper = new XmlMapper();
        List<String> list = new ArrayList<String>();
        list.add("solo");
        String xml = mapper.writer().withRootName("wrap").writeValueAsString(list);
        assertTrue(xml.contains("<item>solo</item>"));
    }

    // serializeValue: String array root wraps each entry with "item"
    @Test
    public void testSerializeValue_stringArrayWithExplicitRootName_wrapsEachElementAsItem() throws Throwable {
        XmlMapper mapper = new XmlMapper();
        String[] arr = new String[] {"x", "y"};
        String xml = mapper.writer().withRootName("letters2").writeValueAsString(arr);
        assertTrue(xml.contains("<item>x</item>"));
        assertTrue(xml.contains("<item>y</item>"));
    }

    // serializeValue(gen,value,rootType,ser) overload: declared type matches runtime list type -> still wraps as array
    @Test
    public void testSerializeValueOverload_declaredListTypeMatchesRuntimeType_wrapsAsArray() throws Throwable {
        XmlMapper mapper = new XmlMapper();
        List<String> list = new ArrayList<String>();
        list.add("x1");
        String xml = mapper.writerFor(list.getClass()).writeValueAsString(list);
        assertTrue(xml.contains("<item>x1</item>"));
    }

    // serializeValue(gen,value,rootType,ser) overload: declared array type matches runtime array type -> wraps as array
    @Test
    public void testSerializeValueOverload_declaredArrayTypeMatchesRuntimeType_wrapsAsArray() throws Throwable {
        XmlMapper mapper = new XmlMapper();
        int[] arr = new int[] {7, 8};
        String xml = mapper.writerFor(int[].class).withRootName("nums").writeValueAsString(arr);
        assertTrue(xml.contains("<item>7</item>"));
        assertTrue(xml.contains("<item>8</item>"));
    }

    // Bug-hunt: declared type is plain Object but runtime value is a List; contract requires item-wrapping
    // regardless of declared static type, since XML output must remain a single well-formed root element.
    @Test
    public void testSerializeValueOverload_declaredObjectTypeWithListRuntimeValue_stillWrapsAsArray() throws Throwable {
        XmlMapper mapper = new XmlMapper();
        List<String> list = new ArrayList<String>();
        list.add("a1");
        list.add("b1");
        String xml = mapper.writerFor(Object.class).writeValueAsString(list);
        assertTrue(xml.contains("<item>a1</item>"));
        assertTrue(xml.contains("<item>b1</item>"));
    }

    // Bug-hunt variant: declared type is plain Object but runtime value is an int array
    @Test
    public void testSerializeValueOverload_declaredObjectTypeWithArrayRuntimeValue_stillWrapsAsArray() throws Throwable {
        XmlMapper mapper = new XmlMapper();
        int[] arr = new int[] {11, 22};
        String xml = mapper.writerFor(Object.class).writeValueAsString(arr);
        assertTrue(xml.contains("<item>11</item>"));
        assertTrue(xml.contains("<item>22</item>"));
    }

    // serializeValue(gen,value,rootType,ser) overload: null value short-circuits to _serializeXmlNull uniformly
    @Test
    public void testSerializeValue_declaredTypeNullValue_usesNullRootName() throws Throwable {
        XmlMapper mapper = new XmlMapper();
        String xml = mapper.writerFor(SimpleBean.class).writeValueAsString(null);
        assertTrue(xml.contains("<null"));
    }

    // convertValue: internal generator is a TokenBuffer (not ToXmlGenerator) -> xgen==null branch, asArray forced false
    @Test
    public void testConvertValue_beanToMap_returnsExpectedEntries() throws Throwable {
        XmlMapper mapper = new XmlMapper();
        Map map = mapper.convertValue(new SimpleBean("hi"), Map.class);
        assertEquals("hi", map.get("value"));
    }

    // convertValue: null value still resolved via _serializeXmlNull, but without XML-specific init (non ToXmlGenerator)
    @Test
    public void testConvertValue_nullValue_returnsNull() throws Throwable {
        XmlMapper mapper = new XmlMapper();
        Object result = mapper.convertValue(null, SimpleBean.class);
        assertNull(result);
    }

    // convertValue: array value converted via non-Xml generator path, xgen==null forces asArray=false but conversion
    // still preserves all elements correctly (no XML wrapping logic should interfere with plain conversion)
    @Test
    public void testConvertValue_arrayToList_preservesElementsWithoutXmlWrapping() throws Throwable {
        XmlMapper mapper = new XmlMapper();
        int[] arr = new int[] {1, 2, 3};
        List list = mapper.convertValue(arr, List.class);
        assertEquals(3, list.size());
        assertEquals(Integer.valueOf(1), list.get(0));
        assertEquals(Integer.valueOf(3), list.get(2));
    }

    // _asXmlGenerator: generator that is neither ToXmlGenerator nor TokenBuffer must trigger JsonMappingException
    @Test
    public void testWriteValue_withNonXmlGenerator_throwsJsonMappingException() throws Throwable {
        XmlMapper mapper = new XmlMapper();
        JsonGenerator gen = new JsonFactory().createGenerator(new StringWriter());
        try {
            mapper.writeValue(gen, new SimpleBean("z"));
            fail("expected JsonMappingException");
        } catch (JsonMappingException expected) {
            assertTrue(expected.getMessage().contains("ToXmlGenerator"));
        }
    }

    // serializeValue: RuntimeException thrown while serializing a property is caught and wrapped preserving message
    @Test
    public void testSerializeValue_getterThrowsRuntimeException_wrappedPreservingMessage() throws Throwable {
        XmlMapper mapper = new XmlMapper();
        try {
            mapper.writeValueAsString(new ThrowingBean());
            fail("expected exception due to getter failure");
        } catch (JsonMappingException expected) {
            assertTrue(expected.getMessage() != null && expected.getMessage().contains("boom"));
        }
    }

    // copy(): XmlSerializerProvider.copy() must produce a working provider so serialization still succeeds after copy
    @Test
    public void testMapperCopy_stillProducesValidXmlAfterCopy() throws Throwable {
        XmlMapper mapper = new XmlMapper();
        ObjectMapper copied = mapper.copy();
        String xml = copied.writeValueAsString(new SimpleBean("copied"));
        assertTrue(xml.contains("<value>copied</value>"));
    }

    // serializeValue: multiple fields on a bean are all serialized under default root name
    @Test
    public void testSerializeValue_beanWithMultipleFields_allFieldsSerialized() throws Throwable {
        XmlMapper mapper = new XmlMapper();
        MultiFieldBean bean = new MultiFieldBean("Alice", 30);
        String xml = mapper.writeValueAsString(bean);
        assertTrue(xml.contains("<name>Alice</name>"));
        assertTrue(xml.contains("<age>30</age>"));
    }

    // serializeValue: root name lookup is per-runtime-class; different bean classes get different default root tags
    @Test
    public void testSerializeValue_differentBeanClasses_useDifferentDefaultRootNames() throws Throwable {
        XmlMapper mapper = new XmlMapper();
        String xmlSimple = mapper.writeValueAsString(new SimpleBean("v"));
        String xmlMulti = mapper.writeValueAsString(new MultiFieldBean("n", 1));
        assertTrue(xmlSimple.contains("<SimpleBean>"));
        assertTrue(xmlMulti.contains("<MultiFieldBean>"));
    }

    // serializeValue: configured root name via writer() applies consistently across repeated calls (stateless config)
    @Test
    public void testSerializeValue_configuredRootNameAppliesRepeatedly_isConsistent() throws Throwable {
        XmlMapper mapper = new XmlMapper();
        String xml1 = mapper.writer().withRootName("rep").writeValueAsString(new SimpleBean("one"));
        String xml2 = mapper.writer().withRootName("rep").writeValueAsString(new SimpleBean("two"));
        assertTrue(xml1.contains("<rep>"));
        assertTrue(xml2.contains("<rep>"));
        assertTrue(xml1.contains("<value>one</value>"));
        assertTrue(xml2.contains("<value>two</value>"));
    }
}
