package com.fasterxml.jackson.dataformat.xml.ser;

import org.junit.Test;
import static org.junit.Assert.*;

import java.io.StringWriter;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import com.fasterxml.jackson.annotation.JsonRootName;
import com.fasterxml.jackson.core.JsonFactory;
import com.fasterxml.jackson.core.JsonGenerator;
import com.fasterxml.jackson.databind.JavaType;
import com.fasterxml.jackson.databind.JsonMappingException;
import com.fasterxml.jackson.dataformat.xml.XmlMapper;
import com.fasterxml.jackson.dataformat.xml.util.XmlRootNameLookup;

public class XmlSerializerProviderClaudeTest
{
    public static class SimpleBean {
        private int value;
        public SimpleBean() { }
        public SimpleBean(int value) { this.value = value; }
        public int getValue() { return value; }
        public void setValue(int value) { this.value = value; }
    }

    @JsonRootName("named")
    public static class NamedBean {
        private int x = 10;
        public int getX() { return x; }
        public void setX(int x) { this.x = x; }
    }

    public static class MultiPropBean {
        private String name;
        private int age;
        public MultiPropBean() { }
        public MultiPropBean(String name, int age) { this.name = name; this.age = age; }
        public String getName() { return name; }
        public void setName(String name) { this.name = name; }
        public int getAge() { return age; }
        public void setAge(int age) { this.age = age; }
    }

    // Tests constructor initializes provider without error
    @Test
    public void testConstructor_withRootNameLookup_notNull() throws Throwable {
        XmlSerializerProvider provider = new XmlSerializerProvider(new XmlRootNameLookup());
        assertNotNull(provider);
    }

    // Tests _asXmlGenerator: non-ToXmlGenerator, non-TokenBuffer generator must throw JsonMappingException
    @Test
    public void test_asXmlGenerator_withNonXmlGenerator_throwsJsonMappingException() throws Throwable {
        XmlSerializerProvider provider = new XmlSerializerProvider(new XmlRootNameLookup());
        JsonFactory factory = new JsonFactory();
        StringWriter sw = new StringWriter();
        JsonGenerator gen = factory.createGenerator(sw);
        try {
            provider._asXmlGenerator(gen);
            fail("expected JsonMappingException");
        } catch (JsonMappingException expected) {
            assertTrue(expected.getMessage().contains("ToXmlGenerator"));
        }
        gen.close();
    }



    // Tests serializeValue null branch: default root name is "null" when no explicit root name configured
    @Test
    public void testWriteValueAsString_nullValue_defaultRootIsNullTag() throws Throwable {
        XmlMapper mapper = new XmlMapper();
        String xml = mapper.writeValueAsString(null);
        assertTrue(xml.contains("<null"));
    }

    // Tests _serializeXmlNull: explicit root name configured must override default "null" root (dataformat-xml#213)
    @Test
    public void testWriteValueAsString_nullValueWithExplicitRootName_usesConfiguredRootName() throws Throwable {
        XmlMapper mapper = new XmlMapper();
        String xml = mapper.writer().withRootName("customRoot").writeValueAsString(null);
        assertTrue(xml.contains("<customRoot"));
        assertFalse(xml.contains("<null"));
    }

    // Tests root name lookup: default root uses simple class name when no annotation present
    @Test
    public void testWriteValueAsString_simplePojo_usesClassSimpleNameAsRoot() throws Throwable {
        XmlMapper mapper = new XmlMapper();
        String xml = mapper.writeValueAsString(new SimpleBean(42));
        assertTrue(xml.contains("<SimpleBean>"));
    }

    // Tests property serialization within root element
    @Test
    public void testWriteValueAsString_simplePojo_containsPropertyElement() throws Throwable {
        XmlMapper mapper = new XmlMapper();
        String xml = mapper.writeValueAsString(new SimpleBean(42));
        assertTrue(xml.contains("<value>42</value>"));
    }

    // Tests root name lookup honors @JsonRootName annotation
    @Test
    public void testWriteValueAsString_withJsonRootNameAnnotation_usesAnnotatedRootName() throws Throwable {
        XmlMapper mapper = new XmlMapper();
        String xml = mapper.writeValueAsString(new NamedBean());
        assertTrue(xml.contains("<named>"));
    }

    // Tests _rootNameFromConfig: explicit root name via ObjectWriter overrides default lookup
    @Test
    public void testWriteValueAsString_withExplicitRootNameOverridesDefault() throws Throwable {
        XmlMapper mapper = new XmlMapper();
        String xml = mapper.writer().withRootName("explicitRoot").writeValueAsString(new SimpleBean(1));
        assertTrue(xml.contains("<explicitRoot>"));
        assertFalse(xml.contains("<SimpleBean>"));
    }

    // Tests asArray branch: int array wraps each element as <item>
    @Test
    public void testWriteValueAsString_intArray_wrapsElementsWithItemTags() throws Throwable {
        XmlMapper mapper = new XmlMapper();
        int[] arr = {1, 2, 3};
        String xml = mapper.writer().withRootName("root").writeValueAsString(arr);
        assertTrue(xml.contains("<item>1</item>"));
        assertTrue(xml.contains("<item>2</item>"));
        assertTrue(xml.contains("<item>3</item>"));
    }

    // Tests asArray branch with zero elements: no item tags written
    @Test
    public void testWriteValueAsString_emptyIntArray_noItemTags() throws Throwable {
        XmlMapper mapper = new XmlMapper();
        int[] arr = new int[0];
        String xml = mapper.writer().withRootName("root").writeValueAsString(arr);
        assertFalse(xml.contains("<item>"));
    }

    // Tests asArray branch: List wraps each element as <item>
    @Test
    public void testWriteValueAsString_listOfStrings_wrapsElementsWithItemTags() throws Throwable {
        XmlMapper mapper = new XmlMapper();
        List<String> list = new ArrayList<String>();
        list.add("a");
        list.add("b");
        String xml = mapper.writer().withRootName("root").writeValueAsString(list);
        assertTrue(xml.contains("<item>a</item>"));
        assertTrue(xml.contains("<item>b</item>"));
    }

    // Tests asArray branch with empty list: no item tags
    @Test
    public void testWriteValueAsString_emptyList_noItemTags() throws Throwable {
        XmlMapper mapper = new XmlMapper();
        List<String> list = new ArrayList<String>();
        String xml = mapper.writer().withRootName("root").writeValueAsString(list);
        assertFalse(xml.contains("<item>"));
    }

    // Tests asArray branch with a single-element list (loop executes once)
    @Test
    public void testWriteValueAsString_singleElementList_singleItemTag() throws Throwable {
        XmlMapper mapper = new XmlMapper();
        List<String> list = new ArrayList<String>();
        list.add("only");
        String xml = mapper.writer().withRootName("root").writeValueAsString(list);
        assertTrue(xml.contains("<item>only</item>"));
    }

    // Tests asArray branch: Set (Collection) also wraps elements as <item>
    @Test
    public void testWriteValueAsString_setOfIntegers_wrapsElementsWithItemTags() throws Throwable {
        XmlMapper mapper = new XmlMapper();
        Set<Integer> set = new LinkedHashSet<Integer>();
        set.add(1);
        set.add(2);
        String xml = mapper.writer().withRootName("root").writeValueAsString(set);
        assertTrue(xml.contains("<item>1</item>"));
        assertTrue(xml.contains("<item>2</item>"));
    }

    // Tests non-indexed type (Map) does not use item-wrapping, uses keys as element names
    @Test
    public void testWriteValueAsString_mapRoot_usesKeysAsElementNames() throws Throwable {
        XmlMapper mapper = new XmlMapper();
        Map<String, Integer> map = new LinkedHashMap<String, Integer>();
        map.put("a", 1);
        String xml = mapper.writer().withRootName("root").writeValueAsString(map);
        assertTrue(xml.contains("<a>1</a>"));
        assertFalse(xml.contains("<item>"));
    }

    // Tests serializeValue(gen, value, rootType) overload: root name resolved via rootType lookup
    @Test
    public void testWriterFor_javaType_simplePojo_producesCorrectRoot() throws Throwable {
        XmlMapper mapper = new XmlMapper();
        JavaType type = mapper.constructType(SimpleBean.class);
        String xml = mapper.writerFor(type).writeValueAsString(new SimpleBean(9));
        assertTrue(xml.contains("<SimpleBean>"));
        assertTrue(xml.contains("<value>9</value>"));
    }

    // Tests serializeValue(gen, value, rootType) overload combined with explicit root name override
    @Test
    public void testWriterFor_javaType_withExplicitRootName_overridesLookup() throws Throwable {
        XmlMapper mapper = new XmlMapper();
        JavaType type = mapper.constructType(SimpleBean.class);
        String xml = mapper.writerFor(type).withRootName("override").writeValueAsString(new SimpleBean(3));
        assertTrue(xml.contains("<override>"));
        assertFalse(xml.contains("<SimpleBean>"));
    }

    // Tests xgen==null branch (TokenBuffer path) of serializeValue via convertValue
    @Test
    public void testConvertValue_toMap_succeedsWithoutXmlGenerator() throws Throwable {
        XmlMapper mapper = new XmlMapper();
        SimpleBean bean = new SimpleBean(7);
        Map<?, ?> result = mapper.convertValue(bean, Map.class);
        assertEquals(7, ((Number) result.get("value")).intValue());
    }

    // Tests asArray branch: String array also wraps elements as <item>
    @Test
    public void testWriteValueAsString_stringArray_wrapsElementsWithItemTags() throws Throwable {
        XmlMapper mapper = new XmlMapper();
        String[] arr = {"x", "y"};
        String xml = mapper.writer().withRootName("root").writeValueAsString(arr);
        assertTrue(xml.contains("<item>x</item>"));
        assertTrue(xml.contains("<item>y</item>"));
    }

    // Tests serialization of bean with multiple properties, all present in output
    @Test
    public void testWriteValueAsString_beanWithMultipleProperties_allPropertiesPresent() throws Throwable {
        XmlMapper mapper = new XmlMapper();
        MultiPropBean bean = new MultiPropBean("Alice", 30);
        String xml = mapper.writeValueAsString(bean);
        assertTrue(xml.contains("<name>Alice</name>"));
        assertTrue(xml.contains("<age>30</age>"));
    }

    // Tests createInstance cloning: repeated serialization calls produce consistent root naming
    @Test
    public void testWriteValueAsString_repeatedSerialization_consistentRootName() throws Throwable {
        XmlMapper mapper = new XmlMapper();
        String xml1 = mapper.writeValueAsString(new SimpleBean(1));
        String xml2 = mapper.writeValueAsString(new SimpleBean(2));
        assertTrue(xml1.contains("<SimpleBean>"));
        assertTrue(xml2.contains("<SimpleBean>"));
    }
}
