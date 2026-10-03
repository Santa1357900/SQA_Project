package com.fasterxml.jackson.databind;

import java.io.ByteArrayInputStream;
import java.io.StringReader;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.TimeZone;

import org.junit.Test;
import static org.junit.Assert.*;

import com.fasterxml.jackson.core.Base64Variants;
import com.fasterxml.jackson.core.JsonFactory;
import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.core.JsonPointer;
import com.fasterxml.jackson.core.type.TypeReference;

import com.fasterxml.jackson.databind.cfg.ContextAttributes;
import com.fasterxml.jackson.databind.deser.DeserializationProblemHandler;
import com.fasterxml.jackson.databind.node.JsonNodeFactory;

public class ObjectReaderClaudeTest {

    public static class SimpleBean {
        private String name;
        private int age;

        public SimpleBean() {
        }

        public String getName() {
            return name;
        }

        public void setName(String name) {
            this.name = name;
        }

        public int getAge() {
            return age;
        }

        public void setAge(int age) {
            this.age = age;
        }
    }

    // Covers version() returning non-null Version instance
    @Test
    public void testVersion_returnsNonNullVersion() throws Throwable {
        ObjectMapper mapper = new ObjectMapper();
        ObjectReader reader = mapper.reader();
        assertNotNull(reader.version());
    }

    // Covers with(feature)/without(feature)/with(first, other...) branches
    @Test
    public void testWith_and_without_DeserializationFeature_variants() throws Throwable {
        ObjectMapper mapper = new ObjectMapper();
        ObjectReader r = mapper.reader().without(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES);
        assertFalse(r.isEnabled(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES));
        r = r.with(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES);
        assertTrue(r.isEnabled(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES));
        r = r.with(DeserializationFeature.FAIL_ON_NULL_FOR_PRIMITIVES, DeserializationFeature.UNWRAP_ROOT_VALUE);
        assertTrue(r.isEnabled(DeserializationFeature.FAIL_ON_NULL_FOR_PRIMITIVES));
        assertTrue(r.isEnabled(DeserializationFeature.UNWRAP_ROOT_VALUE));
    }

    // Covers withFeatures(array)/withoutFeatures(array) for DeserializationFeature
    @Test
    public void testWithFeatures_and_withoutFeatures_DeserializationFeature_arrays() throws Throwable {
        ObjectMapper mapper = new ObjectMapper();
        ObjectReader r = mapper.reader().withFeatures(DeserializationFeature.UNWRAP_ROOT_VALUE);
        assertTrue(r.isEnabled(DeserializationFeature.UNWRAP_ROOT_VALUE));
        r = r.withoutFeatures(DeserializationFeature.UNWRAP_ROOT_VALUE);
        assertFalse(r.isEnabled(DeserializationFeature.UNWRAP_ROOT_VALUE));
    }

    // Covers with(JsonParser.Feature)/without(JsonParser.Feature)
    @Test
    public void testWith_and_without_JsonParserFeature() throws Throwable {
        ObjectMapper mapper = new ObjectMapper();
        ObjectReader r = mapper.reader().with(JsonParser.Feature.ALLOW_COMMENTS);
        assertTrue(r.isEnabled(JsonParser.Feature.ALLOW_COMMENTS));
        r = r.without(JsonParser.Feature.ALLOW_COMMENTS);
        assertFalse(r.isEnabled(JsonParser.Feature.ALLOW_COMMENTS));
    }

    // Covers withFeatures(array)/withoutFeatures(array) for JsonParser.Feature
    @Test
    public void testWithFeatures_and_withoutFeatures_JsonParserFeature_arrays() throws Throwable {
        ObjectMapper mapper = new ObjectMapper();
        ObjectReader r = mapper.reader().withFeatures(JsonParser.Feature.ALLOW_SINGLE_QUOTES);
        assertTrue(r.isEnabled(JsonParser.Feature.ALLOW_SINGLE_QUOTES));
        r = r.withoutFeatures(JsonParser.Feature.ALLOW_SINGLE_QUOTES);
        assertFalse(r.isEnabled(JsonParser.Feature.ALLOW_SINGLE_QUOTES));
    }

    // Covers at(String) and at(JsonPointer) both creating new filtered reader instances
    @Test
    public void testAt_stringAndJsonPointer_createNewReaderInstances() throws Throwable {
        ObjectMapper mapper = new ObjectMapper();
        ObjectReader base = mapper.reader();
        ObjectReader r1 = base.at("/foo");
        assertNotSame(base, r1);
        JsonPointer ptr = JsonPointer.compile("/bar");
        ObjectReader r2 = base.at(ptr);
        assertNotSame(base, r2);
    }

    // Covers actual filtering behavior applied through _considerFilter
    @Test
    public void testAt_filtersToSubDocument() throws Throwable {
        ObjectMapper mapper = new ObjectMapper();
        ObjectReader reader = mapper.readerFor(String.class).at("/name");
        String json = "{\"name\":\"Bob\",\"age\":30}";
        String result = reader.readValue(json);
        assertEquals("Bob", result);
    }

    // Covers with(DeserializationConfig) short-circuit branch when config unchanged
    @Test
    public void testWith_DeserializationConfig_sameConfigReturnsThis() throws Throwable {
        ObjectMapper mapper = new ObjectMapper();
        ObjectReader reader = mapper.reader();
        DeserializationConfig cfg = reader.getConfig();
        ObjectReader r = reader.with(cfg);
        assertSame(reader, r);
    }

    // Covers with(InjectableValues) setting value and short-circuit for same instance
    @Test
    public void testWith_InjectableValues_setsAndShortCircuitsSameInstance() throws Throwable {
        ObjectMapper mapper = new ObjectMapper();
        ObjectReader base = mapper.reader();
        InjectableValues.Std iv = new InjectableValues.Std();
        ObjectReader withIv = base.with(iv);
        assertSame(iv, withIv.getInjectableValues());
        assertSame(withIv, withIv.with(iv));
    }

    // Covers with(JsonNodeFactory) applying custom node factory to config
    @Test
    public void testWith_JsonNodeFactory_appliesFactory() throws Throwable {
        ObjectMapper mapper = new ObjectMapper();
        ObjectReader base = mapper.reader();
        JsonNodeFactory factory = JsonNodeFactory.instance;
        ObjectReader r = base.with(factory);
        assertSame(factory, r.getConfig().getNodeFactory());
    }

    // Covers with(JsonFactory) same-instance short circuit and different-instance new reader
    @Test
    public void testWith_JsonFactory_sameAndDifferentInstanceBehavior() throws Throwable {
        ObjectMapper mapper = new ObjectMapper();
        ObjectReader base = mapper.reader();
        JsonFactory same = base.getFactory();
        assertSame(base, base.with(same));
        JsonFactory f2 = new JsonFactory();
        ObjectReader r2 = base.with(f2);
        assertSame(f2, r2.getFactory());
    }

    // Covers withRootName(String) and withRootName(PropertyName) unwrapping named root
    @Test
    public void testWithRootName_StringAndPropertyName_unwrapNamedRoot() throws Throwable {
        ObjectMapper mapper = new ObjectMapper();
        mapper.enable(DeserializationFeature.UNWRAP_ROOT_VALUE);
        ObjectReader r1 = mapper.readerFor(SimpleBean.class).withRootName("wrapper");
        SimpleBean b1 = r1.readValue("{\"wrapper\":{\"name\":\"Kay\",\"age\":2}}");
        assertEquals("Kay", b1.getName());
        PropertyName pn = new PropertyName("wrap2");
        ObjectReader r2 = mapper.readerFor(SimpleBean.class).withRootName(pn);
        SimpleBean b2 = r2.readValue("{\"wrap2\":{\"name\":\"Lou\",\"age\":9}}");
        assertEquals("Lou", b2.getName());
    }

    // Covers withoutRootName() forcing no unwrapping even when feature is enabled
    @Test
    public void testWithoutRootName_disablesRootUnwrappingEvenIfFeatureEnabled() throws Throwable {
        ObjectMapper mapper = new ObjectMapper();
        mapper.enable(DeserializationFeature.UNWRAP_ROOT_VALUE);
        ObjectReader reader = mapper.readerFor(SimpleBean.class).withoutRootName();
        SimpleBean bean = reader.readValue("{\"name\":\"Ed\",\"age\":1}");
        assertEquals("Ed", bean.getName());
    }

    // Covers with(FormatSchema) short-circuit when schema equals current (null==null)
    @Test
    public void testWith_FormatSchema_nullSameAsCurrentReturnsThis() throws Throwable {
        ObjectMapper mapper = new ObjectMapper();
        ObjectReader base = mapper.reader();
        ObjectReader r = base.with((FormatSchema) null);
        assertSame(base, r);
    }

    // Covers forType(JavaType) short-circuit when type equals current value type
    @Test
    public void testForType_JavaType_sameTypeReturnsThis() throws Throwable {
        ObjectMapper mapper = new ObjectMapper();
        ObjectReader base = mapper.readerFor(SimpleBean.class);
        JavaType type = base.getConfig().constructType(SimpleBean.class);
        ObjectReader r = base.forType(type);
        assertSame(base, r);
    }

    // Covers forType(Class) creating a new reader when type differs
    @Test
    public void testForType_Class_differentTypeCreatesNewReader() throws Throwable {
        ObjectMapper mapper = new ObjectMapper();
        ObjectReader base = mapper.reader();
        ObjectReader r = base.forType(SimpleBean.class);
        assertNotSame(base, r);
    }

    // Covers forType(TypeReference) resolving generic List type
    @Test
    public void testForType_TypeReference_readsListType() throws Throwable {
        ObjectMapper mapper = new ObjectMapper();
        ObjectReader base = mapper.reader();
        ObjectReader r = base.forType(new TypeReference<List<String>>() { });
        List<String> result = r.readValue("[\"a\",\"b\"]");
        assertEquals(2, result.size());
        assertEquals("a", result.get(0));
    }

    // Covers withValueToUpdate(null) short-circuit when already null
    @Test
    public void testWithValueToUpdate_null_whenAlreadyNullReturnsThis() throws Throwable {
        ObjectMapper mapper = new ObjectMapper();
        ObjectReader base = mapper.readerFor(SimpleBean.class);
        ObjectReader r = base.withValueToUpdate(null);
        assertSame(base, r);
    }

    // Covers withValueToUpdate branch where _valueType already set (kept as-is)
    @Test
    public void testWithValueToUpdate_existingValueType_keepsValueType() throws Throwable {
        ObjectMapper mapper = new ObjectMapper();
        SimpleBean bean = new SimpleBean();
        ObjectReader base = mapper.readerFor(SimpleBean.class);
        ObjectReader r = base.withValueToUpdate(bean);
        SimpleBean result = r.readValue("{\"name\":\"Alice\",\"age\":5}");
        assertSame(bean, result);
        assertEquals("Alice", result.getName());
    }

    // Covers withValueToUpdate branch where _valueType is null (constructed from value's class)
    @Test
    public void testWithValueToUpdate_noValueType_constructsTypeFromValue() throws Throwable {
        ObjectMapper mapper = new ObjectMapper();
        SimpleBean bean = new SimpleBean();
        ObjectReader base = mapper.reader();
        ObjectReader r = base.withValueToUpdate(bean);
        SimpleBean result = r.readValue("{\"name\":\"Zoe\",\"age\":9}");
        assertSame(bean, result);
        assertEquals("Zoe", result.getName());
    }

    // Covers withView creating a new reader instance
    @Test
    public void testWithView_setsActiveView() throws Throwable {
        ObjectMapper mapper = new ObjectMapper();
        ObjectReader base = mapper.reader();
        ObjectReader r = base.withView(Object.class);
        assertNotSame(base, r);
    }

    // Covers with(Locale) and with(TimeZone) both producing distinct reader instances
    @Test
    public void testWith_Locale_and_TimeZone_produceDistinctReaders() throws Throwable {
        ObjectMapper mapper = new ObjectMapper();
        ObjectReader base = mapper.reader();
        ObjectReader r1 = base.with(Locale.US);
        ObjectReader r2 = r1.with(Locale.CANADA);
        assertNotSame(r1, r2);
        ObjectReader t1 = base.with(TimeZone.getTimeZone("UTC"));
        ObjectReader t2 = t1.with(TimeZone.getTimeZone("America/Los_Angeles"));
        assertNotSame(t1, t2);
    }

    // Covers withHandler and with(Base64Variant) both producing new reader instances
    @Test
    public void testWithHandler_and_withBase64Variant_produceNewReaders() throws Throwable {
        ObjectMapper mapper = new ObjectMapper();
        ObjectReader base = mapper.reader();
        DeserializationProblemHandler handler = new DeserializationProblemHandler() { };
        ObjectReader r1 = base.withHandler(handler);
        assertNotSame(base, r1);
        ObjectReader b1 = base.with(Base64Variants.MIME);
        ObjectReader b2 = b1.with(Base64Variants.MODIFIED_FOR_URL);
        assertNotSame(b1, b2);
    }

    // Covers withFormatDetection(readers...) taking the _detectBindAndClose(byte[]) path
    @Test
    public void testWithFormatDetection_detectsAndReadsContent() throws Throwable {
        ObjectMapper mapper = new ObjectMapper();
        ObjectReader jsonReader = mapper.readerFor(SimpleBean.class);
        ObjectReader detect = jsonReader.withFormatDetection(jsonReader);
        byte[] data = "{\"name\":\"Ann\",\"age\":11}".getBytes("UTF-8");
        SimpleBean result = detect.readValue(data);
        assertEquals("Ann", result.getName());
        assertEquals(11, result.getAge());
    }

    // Covers withAttribute setting a value and withoutAttribute removing it
    @Test
    public void testWithAttribute_and_withoutAttribute_manageAttributes() throws Throwable {
        ObjectMapper mapper = new ObjectMapper();
        ObjectReader base = mapper.reader();
        ObjectReader withAttr = base.withAttribute("k", "v");
        assertEquals("v", withAttr.getAttributes().getAttribute("k"));
        ObjectReader withoutAttr = withAttr.withoutAttribute("k");
        assertNull(withoutAttr.getAttributes().getAttribute("k"));
    }

    // Covers withAttributes(Map) and with(ContextAttributes) setting values
    @Test
    public void testWithAttributesMap_and_ContextAttributes_setValues() throws Throwable {
        ObjectMapper mapper = new ObjectMapper();
        ObjectReader base = mapper.reader();
        Map<Object, Object> map = new HashMap<Object, Object>();
        map.put("foo", "bar");
        ObjectReader r = base.withAttributes(map);
        assertEquals("bar", r.getAttributes().getAttribute("foo"));
        ContextAttributes attrs = ContextAttributes.getEmpty().withSharedAttribute("q", "z");
        ObjectReader r2 = base.with(attrs);
        assertEquals("z", r2.getAttributes().getAttribute("q"));
    }

    // Covers isEnabled(DeserializationFeature)/isEnabled(MapperFeature)/isEnabled(JsonParser.Feature)
    @Test
    public void testIsEnabled_reflectsConfigAndFactoryState() throws Throwable {
        ObjectMapper mapper = new ObjectMapper();
        ObjectReader base = mapper.reader();
        ObjectReader withFail = base.with(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES);
        assertTrue(withFail.isEnabled(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES));
        assertEquals(base.getConfig().isEnabled(MapperFeature.AUTO_DETECT_GETTERS),
                base.isEnabled(MapperFeature.AUTO_DETECT_GETTERS));
        assertFalse(base.isEnabled(JsonParser.Feature.ALLOW_COMMENTS));
    }

    // Covers getConfig/getFactory/getTypeFactory/getAttributes/getInjectableValues accessors
    @Test
    public void testSimpleAccessors_returnExpectedValues() throws Throwable {
        ObjectMapper mapper = new ObjectMapper();
        ObjectReader reader = mapper.reader();
        assertNotNull(reader.getConfig());
        assertSame(mapper.getFactory(), reader.getFactory());
        assertNotNull(reader.getTypeFactory());
        assertNotNull(reader.getAttributes());
        assertNull(reader.getInjectableValues());
    }

    // Covers readValue(JsonParser) binding a configured root type
    @Test
    public void testReadValue_JsonParser_bindsBasicObject() throws Throwable {
        ObjectMapper mapper = new ObjectMapper();
        ObjectReader reader = mapper.readerFor(SimpleBean.class);
        JsonParser p = mapper.getFactory().createParser("{\"name\":\"Tom\",\"age\":7}");
        SimpleBean bean = reader.readValue(p);
        assertEquals("Tom", bean.getName());
        assertEquals(7, bean.getAge());
        p.close();
    }

    // Covers readValue(JsonParser, Class) overriding configured type via forType
    @Test
    public void testReadValue_JsonParser_withClassType_overridesConfiguredType() throws Throwable {
        ObjectMapper mapper = new ObjectMapper();
        ObjectReader reader = mapper.reader();
        JsonParser p = mapper.getFactory().createParser("\"hello\"");
        String s = reader.readValue(p, String.class);
        assertEquals("hello", s);
        p.close();
    }

    // Covers readValue(JsonParser, TypeReference) for a generic List type
    @Test
    public void testReadValue_JsonParser_withTypeReference() throws Throwable {
        ObjectMapper mapper = new ObjectMapper();
        ObjectReader reader = mapper.reader();
        JsonParser p = mapper.getFactory().createParser("[1,2,3]");
        List<Integer> list = reader.readValue(p, new TypeReference<List<Integer>>() { });
        assertEquals(3, list.size());
        p.close();
    }

    // Covers createArrayNode() and createObjectNode() returning empty nodes
    @Test
    public void testCreateArrayNode_and_createObjectNode_returnEmptyNodes() throws Throwable {
        ObjectMapper mapper = new ObjectMapper();
        ObjectReader reader = mapper.reader();
        JsonNode array = reader.createArrayNode();
        assertTrue(array.isArray());
        assertEquals(0, array.size());
        JsonNode obj = reader.createObjectNode();
        assertTrue(obj.isObject());
        assertEquals(0, obj.size());
    }

    // Covers readTree(JsonParser) with actual content present
    @Test
    public void testReadTree_JsonParser_withContent_returnsNode() throws Throwable {
        ObjectMapper mapper = new ObjectMapper();
        ObjectReader reader = mapper.reader();
        JsonParser p = mapper.getFactory().createParser("{\"a\":1}");
        JsonNode result = reader.readTree(p);
        assertTrue(result.isObject());
        p.close();
    }

    // Bug-catching test: Javadoc of readTree(JsonParser) states null is returned on EOF,
    // unlike readTree(InputStream) which returns "missing node"
    @Test
    public void testReadTree_JsonParser_noContent_returnsNullPerJavadoc() throws Throwable {
        ObjectMapper mapper = new ObjectMapper();
        ObjectReader reader = mapper.reader();
        JsonParser p = mapper.getFactory().createParser("");
        JsonNode result = reader.readTree(p);
        assertNull(result);
        p.close();
    }

    // Covers readValue(Reader) binding a configured bean type
    @Test
    public void testReadValue_Reader_bindsBean() throws Throwable {
        ObjectMapper mapper = new ObjectMapper();
        ObjectReader reader = mapper.readerFor(SimpleBean.class);
        StringReader sr = new StringReader("{\"name\":\"Lee\",\"age\":15}");
        SimpleBean bean = reader.readValue(sr);
        assertEquals("Lee", bean.getName());
        assertEquals(15, bean.getAge());
    }

    // Covers readValue(String) binding a configured bean type
    @Test
    public void testReadValue_String_bindsBean() throws Throwable {
        ObjectMapper mapper = new ObjectMapper();
        ObjectReader reader = mapper.readerFor(SimpleBean.class);
        SimpleBean bean = reader.readValue("{\"name\":\"Sue\",\"age\":22}");
        assertEquals("Sue", bean.getName());
        assertEquals(22, bean.getAge());
    }

    // Covers _bind's VALUE_NULL branch returning getNullValue when valueToUpdate is null
    @Test
    public void testReadValue_String_nullJsonReturnsNull() throws Throwable {
        ObjectMapper mapper = new ObjectMapper();
        ObjectReader reader = mapper.readerFor(SimpleBean.class);
        SimpleBean bean = reader.readValue("null");
        assertNull(bean);
    }

    // Covers readValue(byte[]) binding a configured bean type
    @Test
    public void testReadValue_byteArray_bindsBean() throws Throwable {
        ObjectMapper mapper = new ObjectMapper();
        ObjectReader reader = mapper.readerFor(SimpleBean.class);
        byte[] data = "{\"name\":\"Max\",\"age\":3}".getBytes("UTF-8");
        SimpleBean bean = reader.readValue(data);
        assertEquals("Max", bean.getName());
    }

    // Covers readTree(InputStream) returning "missing node" (not null) on EOF, per its own Javadoc
    @Test
    public void testReadTree_InputStream_noContent_returnsMissingNode() throws Throwable {
        ObjectMapper mapper = new ObjectMapper();
        ObjectReader reader = mapper.reader();
        ByteArrayInputStream in = new ByteArrayInputStream(new byte[0]);
        JsonNode result = reader.readTree(in);
        assertNotNull(result);
        assertTrue(result.isMissingNode());
    }

    // Covers readTree(String) parsing an array document
    @Test
    public void testReadTree_String_parsesArray() throws Throwable {
        ObjectMapper mapper = new ObjectMapper();
        ObjectReader reader = mapper.reader();
        JsonNode result = reader.readTree("[1,2,3]");
        assertTrue(result.isArray());
        assertEquals(3, result.size());
    }

    // Covers readValues(JsonParser) iterating an unwrapped root-level sequence
    @Test
    public void testReadValues_JsonParser_iteratesSequence() throws Throwable {
        ObjectMapper mapper = new ObjectMapper();
        ObjectReader reader = mapper.readerFor(Integer.class);
        JsonParser p = mapper.getFactory().createParser("1 2 3");
        MappingIterator<Integer> it = reader.readValues(p);
        int sum = 0;
        while (it.hasNext()) {
            sum += it.next().intValue();
        }
        assertEquals(6, sum);
        it.close();
    }

    // Covers readValues(String) iterating a wrapped JSON array sequence
    @Test
    public void testReadValues_String_iteratesWrappedArray() throws Throwable {
        ObjectMapper mapper = new ObjectMapper();
        ObjectReader reader = mapper.readerFor(String.class);
        MappingIterator<String> it = reader.readValues("[\"a\",\"b\",\"c\"]");
        List<String> result = new ArrayList<String>();
        while (it.hasNext()) {
            result.add(it.next());
        }
        assertEquals(3, result.size());
        assertEquals("a", result.get(0));
        it.close();
    }

    // Covers treeToValue(TreeNode, Class) delegating through treeAsTokens + readValue
    @Test
    public void testTreeToValue_convertsTreeNodeToTarget() throws Throwable {
        ObjectMapper mapper = new ObjectMapper();
        ObjectReader reader = mapper.reader();
        JsonNode node = mapper.readTree("{\"name\":\"Kim\",\"age\":30}");
        SimpleBean bean = reader.treeToValue(node, SimpleBean.class);
        assertEquals("Kim", bean.getName());
        assertEquals(30, bean.getAge());
    }

    // Covers writeValue and writeTree both throwing UnsupportedOperationException
    @Test
    public void testWriteValue_and_writeTree_throwUnsupportedOperationException() throws Throwable {
        ObjectMapper mapper = new ObjectMapper();
        ObjectReader reader = mapper.reader();
        try {
            reader.writeValue(null, "x");
            fail("expected UnsupportedOperationException");
        } catch (UnsupportedOperationException expected) {
        }
        try {
            reader.writeTree(null, null);
            fail("expected UnsupportedOperationException");
        } catch (UnsupportedOperationException expected) {
        }
    }

    // Covers FAIL_ON_TRAILING_TOKENS branch in _verifyNoTrailingTokens throwing on extra content
    @Test
    public void testReadValue_String_trailingTokensThrowsWhenFeatureEnabled() throws Throwable {
        ObjectMapper mapper = new ObjectMapper();
        ObjectReader reader = mapper.readerFor(Integer.class)
                .with(DeserializationFeature.FAIL_ON_TRAILING_TOKENS);
        try {
            reader.readValue("1 2");
            fail("expected JsonMappingException due to trailing tokens");
        } catch (JsonMappingException expected) {
        }
    }
}
