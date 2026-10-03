package com.fasterxml.jackson.databind;

import java.io.ByteArrayInputStream;
import java.io.StringReader;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;

import org.junit.Before;
import org.junit.Test;
import static org.junit.Assert.*;

import com.fasterxml.jackson.core.JsonFactory;
import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.core.JsonPointer;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.node.JsonNodeFactory;

public class ObjectReaderClaudeTest {

    public static class SimpleBean {
        private int value;
        public int getValue() { return value; }
        public void setValue(int value) { this.value = value; }
    }

    private ObjectMapper mapper;

    @Before
    public void setUp() throws Throwable {
        mapper = new ObjectMapper();
    }

    // version(): must return a non-null Version instance
    @Test
    public void testVersion_returnsNonNullVersion() throws Throwable {
        ObjectReader reader = mapper.reader();
        assertNotNull(reader.version());
    }

    // with(DeserializationFeature): enabling a single feature is reflected by isEnabled
    @Test
    public void testWith_singleDeserializationFeature_enablesFeature() throws Throwable {
        ObjectReader base = mapper.reader().without(DeserializationFeature.UNWRAP_ROOT_VALUE);
        ObjectReader r = base.with(DeserializationFeature.UNWRAP_ROOT_VALUE);
        assertTrue(r.isEnabled(DeserializationFeature.UNWRAP_ROOT_VALUE));
    }

    // without(DeserializationFeature): disabling a single feature is reflected by isEnabled
    @Test
    public void testWithout_singleDeserializationFeature_disablesFeature() throws Throwable {
        ObjectReader r = mapper.reader().without(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES);
        assertFalse(r.isEnabled(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES));
    }

    // with(first, other...): varargs overload enables all listed features
    @Test
    public void testWith_multipleDeserializationFeatures_allEnabled() throws Throwable {
        ObjectReader r = mapper.reader().with(DeserializationFeature.UNWRAP_ROOT_VALUE,
                DeserializationFeature.FAIL_ON_NULL_FOR_PRIMITIVES);
        assertTrue(r.isEnabled(DeserializationFeature.UNWRAP_ROOT_VALUE));
        assertTrue(r.isEnabled(DeserializationFeature.FAIL_ON_NULL_FOR_PRIMITIVES));
    }

    // without(first, other...): varargs overload disables all listed features
    @Test
    public void testWithout_multipleDeserializationFeatures_allDisabled() throws Throwable {
        ObjectReader r = mapper.reader().without(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES,
                DeserializationFeature.UNWRAP_ROOT_VALUE);
        assertFalse(r.isEnabled(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES));
        assertFalse(r.isEnabled(DeserializationFeature.UNWRAP_ROOT_VALUE));
    }

    // with(JsonParser.Feature): enabling ALLOW_UNQUOTED_FIELD_NAMES permits unquoted field names
    @Test
    public void testWith_jsonParserFeature_allowsUnquotedFieldNames() throws Throwable {
        ObjectReader r = mapper.readerFor(SimpleBean.class).with(JsonParser.Feature.ALLOW_UNQUOTED_FIELD_NAMES);
        SimpleBean bean = r.readValue("{value:5}");
        assertEquals(5, bean.getValue());
    }

    // without(JsonParser.Feature): unquoted field names remain rejected by default (throws)
    @Test
    public void testWithout_jsonParserFeature_unquotedFieldNames_throws() throws Throwable {
        ObjectReader r = mapper.readerFor(SimpleBean.class);
        try {
            r.readValue("{value:5}");
            fail("expected JsonProcessingException");
        } catch (JsonProcessingException expected) {
        }
    }

    // with(DeserializationConfig): passing the same config instance returns this (identity branch)
    @Test
    public void testWith_deserializationConfig_sameConfig_returnsThis() throws Throwable {
        ObjectReader r = mapper.reader();
        ObjectReader r2 = r.with(r.getConfig());
        assertSame(r, r2);
    }

    // with(InjectableValues): default is null; passing null preserves identity (value == field)
    @Test
    public void testWith_injectableValues_null_returnsThis() throws Throwable {
        ObjectReader r = mapper.reader();
        assertNull(r.getInjectableValues());
        ObjectReader r2 = r.with((InjectableValues) null);
        assertSame(r, r2);
    }

    // with(JsonNodeFactory): reconfigured reader still produces a functional object node
    @Test
    public void testWith_jsonNodeFactory_createsObjectNode() throws Throwable {
        ObjectReader r = mapper.reader().with(JsonNodeFactory.instance);
        JsonNode node = r.createObjectNode();
        assertTrue(node.isObject());
    }

    // with(JsonFactory): same factory returns this; different factory updates getFactory()
    @Test
    public void testWith_jsonFactory_identityAndDifferentFactory() throws Throwable {
        ObjectReader r = mapper.reader();
        ObjectReader same = r.with(r.getFactory());
        assertSame(r, same);
        JsonFactory other = new JsonFactory();
        ObjectReader diff = r.with(other);
        assertNotSame(r, diff);
        assertSame(other, diff.getFactory());
    }

    // withRootName + UNWRAP_ROOT_VALUE: matching root name unwraps and deserializes correctly
    @Test
    public void testWithRootName_unwrapSuccess_returnsDeserializedValue() throws Throwable {
        ObjectReader r = mapper.readerFor(SimpleBean.class)
                .with(DeserializationFeature.UNWRAP_ROOT_VALUE).withRootName("wrapper");
        SimpleBean bean = r.readValue("{\"wrapper\":{\"value\":7}}");
        assertEquals(7, bean.getValue());
    }

    // withRootName: mismatched root field name throws JsonMappingException
    @Test
    public void testWithRootName_nameMismatch_throwsJsonMappingException() throws Throwable {
        ObjectReader r = mapper.readerFor(SimpleBean.class)
                .with(DeserializationFeature.UNWRAP_ROOT_VALUE).withRootName("wrapper");
        try {
            r.readValue("{\"other\":{\"value\":7}}");
            fail("expected JsonMappingException");
        } catch (JsonMappingException expected) {
        }
    }

    // withRootName: content not starting with START_OBJECT throws JsonMappingException
    @Test
    public void testWithRootName_notStartObject_throwsJsonMappingException() throws Throwable {
        ObjectReader r = mapper.readerFor(SimpleBean.class)
                .with(DeserializationFeature.UNWRAP_ROOT_VALUE).withRootName("wrapper");
        try {
            r.readValue("[1,2,3]");
            fail("expected JsonMappingException");
        } catch (JsonMappingException expected) {
        }
    }

    // withRootName: empty object (no FIELD_NAME) throws JsonMappingException
    @Test
    public void testWithRootName_missingFieldName_throwsJsonMappingException() throws Throwable {
        ObjectReader r = mapper.readerFor(SimpleBean.class)
                .with(DeserializationFeature.UNWRAP_ROOT_VALUE).withRootName("wrapper");
        try {
            r.readValue("{}");
            fail("expected JsonMappingException");
        } catch (JsonMappingException expected) {
        }
    }

    // withRootName: extra trailing field after wrapped value throws JsonMappingException
    @Test
    public void testWithRootName_trailingExtraField_throwsJsonMappingException() throws Throwable {
        ObjectReader r = mapper.readerFor(SimpleBean.class)
                .with(DeserializationFeature.UNWRAP_ROOT_VALUE).withRootName("wrapper");
        try {
            r.readValue("{\"wrapper\":{\"value\":1},\"extra\":2}");
            fail("expected JsonMappingException");
        } catch (JsonMappingException expected) {
        }
    }

    // forType(JavaType): same type instance returns this (identity branch)
    @Test
    public void testForType_javaType_sameType_returnsThis() throws Throwable {
        JavaType t = mapper.getTypeFactory().constructType(String.class);
        ObjectReader r1 = mapper.reader().forType(t);
        ObjectReader r2 = r1.forType(t);
        assertSame(r1, r2);
    }

    // forType(Class): configures reader to deserialize the requested root type
    @Test
    public void testForType_classType_readsValue() throws Throwable {
        Integer val = mapper.reader().forType(Integer.class).readValue("42");
        assertEquals(Integer.valueOf(42), val);
    }

    // forType(TypeReference): supports generic target types such as List<Integer>
    @Test
    public void testForType_typeReference_readsListValue() throws Throwable {
        List<Integer> list = mapper.reader().forType(new TypeReference<List<Integer>>() {}).readValue("[1,2,3]");
        assertEquals(3, list.size());
        assertEquals(Integer.valueOf(1), list.get(0));
        assertEquals(Integer.valueOf(3), list.get(2));
    }

    // deprecated withType(Class) still delegates correctly to forType
    @Test
    public void testWithType_classType_deprecatedStillWorks() throws Throwable {
        Integer val = mapper.reader().withType(Integer.class).readValue("99");
        assertEquals(Integer.valueOf(99), val);
    }

    // withValueToUpdate(null) must throw IllegalArgumentException per contract
    @Test
    public void testWithValueToUpdate_null_throwsIllegalArgumentException() throws Throwable {
        try {
            mapper.reader().withValueToUpdate(null);
            fail("expected IllegalArgumentException");
        } catch (IllegalArgumentException expected) {
        }
    }

    // withValueToUpdate: passing same reference twice returns identical reader (identity branch)
    @Test
    public void testWithValueToUpdate_sameValue_returnsThis() throws Throwable {
        SimpleBean bean = new SimpleBean();
        ObjectReader r1 = mapper.readerFor(SimpleBean.class).withValueToUpdate(bean);
        ObjectReader r2 = r1.withValueToUpdate(bean);
        assertSame(r1, r2);
    }

    // withValueToUpdate: existing instance is mutated in place and returned as result
    @Test
    public void testWithValueToUpdate_updatesExistingInstance() throws Throwable {
        SimpleBean bean = new SimpleBean();
        bean.setValue(1);
        SimpleBean result = mapper.readerFor(SimpleBean.class).withValueToUpdate(bean).readValue("{\"value\":9}");
        assertSame(bean, result);
        assertEquals(9, bean.getValue());
    }

    // withValueToUpdate: array-typed value throws IllegalArgumentException per contract
    @Test
    public void testWithValueToUpdate_arrayType_throwsIllegalArgumentException() throws Throwable {
        ObjectReader arrReader = mapper.readerFor(int[].class);
        try {
            arrReader.withValueToUpdate(new int[] {1, 2});
            fail("expected IllegalArgumentException");
        } catch (IllegalArgumentException expected) {
        }
    }

    // withAttribute/withAttributes/withoutAttribute all produce readers with non-null attributes
    @Test
    public void testWithAttribute_withAttributes_withoutAttribute_returnNonNullAttributes() throws Throwable {
        ObjectReader r1 = mapper.reader().withAttribute("key", "value");
        assertNotNull(r1.getAttributes());
        Map<String, Object> attrs = new HashMap<String, Object>();
        attrs.put("a", "b");
        ObjectReader r2 = mapper.reader().withAttributes(attrs);
        assertNotNull(r2.getAttributes());
        ObjectReader r3 = r1.withoutAttribute("key");
        assertNotNull(r3.getAttributes());
    }

    // isEnabled(DeserializationFeature): default configuration enables FAIL_ON_UNKNOWN_PROPERTIES
    @Test
    public void testIsEnabled_deserializationFeature_defaultTrue() throws Throwable {
        ObjectReader r = mapper.reader();
        assertTrue(r.isEnabled(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES));
    }

    // readValue(JsonParser): binds using the pre-configured root type
    @Test
    public void testReadValue_jsonParser_basic() throws Throwable {
        JsonFactory f = new JsonFactory();
        JsonParser p = f.createParser("{\"value\":3}");
        SimpleBean bean = mapper.readerFor(SimpleBean.class).readValue(p);
        assertEquals(3, bean.getValue());
        p.close();
    }

    // readValue(JsonParser, TypeReference): overrides configured type for this single call
    @Test
    public void testReadValue_jsonParserWithTypeReference_basic() throws Throwable {
        JsonFactory f = new JsonFactory();
        JsonParser p = f.createParser("[1,2]");
        List<Integer> list = mapper.reader().readValue(p, new TypeReference<List<Integer>>() {});
        assertEquals(2, list.size());
        p.close();
    }

    // readValues(JsonParser, Class): iterates an unwrapped sequence of root-level values
    @Test
    public void testReadValues_jsonParserWithClass_iteratesAllValues() throws Throwable {
        JsonFactory f = new JsonFactory();
        JsonParser p = f.createParser("1 2 3");
        Iterator<Integer> it = mapper.reader().readValues(p, Integer.class);
        List<Integer> collected = new ArrayList<Integer>();
        while (it.hasNext()) {
            collected.add(it.next());
        }
        assertEquals(3, collected.size());
        assertEquals(Integer.valueOf(2), collected.get(1));
        p.close();
    }

    // readTree(JsonParser): binds content into a JsonNode tree with expected field values
    @Test
    public void testReadTree_jsonParser_returnsObjectNode() throws Throwable {
        JsonFactory f = new JsonFactory();
        JsonParser p = f.createParser("{\"a\":1}");
        JsonNode node = mapper.reader().readTree(p);
        assertTrue(node.isObject());
        assertEquals(1, node.get("a").asInt());
        p.close();
    }

    // createArrayNode()/createObjectNode(): produce tree nodes of the expected kind
    @Test
    public void testCreateArrayNode_and_createObjectNode() throws Throwable {
        ObjectReader r = mapper.reader();
        assertTrue(r.createArrayNode().isArray());
        assertTrue(r.createObjectNode().isObject());
    }

    // writeTree()/writeValue(): both are unimplemented and must throw UnsupportedOperationException
    @Test
    public void testWriteTree_and_writeValue_throwUnsupportedOperationException() throws Throwable {
        ObjectReader r = mapper.reader();
        try {
            r.writeTree(null, null);
            fail("expected UnsupportedOperationException");
        } catch (UnsupportedOperationException expected) {
        }
        try {
            r.writeValue(null, null);
            fail("expected UnsupportedOperationException");
        } catch (UnsupportedOperationException expected) {
        }
    }

    // readValue(InputStream): binds content read from a byte stream
    @Test
    public void testReadValue_inputStream_basic() throws Throwable {
        ByteArrayInputStream in = new ByteArrayInputStream("{\"value\":5}".getBytes("UTF-8"));
        SimpleBean bean = mapper.readerFor(SimpleBean.class).readValue(in);
        assertEquals(5, bean.getValue());
    }

    // readValue(Reader): binds content read from a character stream
    @Test
    public void testReadValue_reader_basic() throws Throwable {
        StringReader reader = new StringReader("{\"value\":6}");
        SimpleBean bean = mapper.readerFor(SimpleBean.class).readValue(reader);
        assertEquals(6, bean.getValue());
    }

    // readValue(String): empty input throws JsonMappingException ("no content to map")
    @Test
    public void testReadValue_emptyString_throwsJsonMappingException() throws Throwable {
        try {
            mapper.readerFor(SimpleBean.class).readValue("");
            fail("expected JsonMappingException");
        } catch (JsonMappingException expected) {
        }
    }

    // readValue(byte[], offset, length): binds only the requested slice of the array
    @Test
    public void testReadValue_byteArrayOffsetLength_basic() throws Throwable {
        String core = "{\"value\":4}";
        String padded = "XX" + core + "YY";
        byte[] src = padded.getBytes("UTF-8");
        SimpleBean bean = mapper.readerFor(SimpleBean.class).readValue(src, 2, core.length());
        assertEquals(4, bean.getValue());
    }

    // readValue(JsonNode): converts a tree into the configured target type
    @Test
    public void testReadValue_jsonNode_basic() throws Throwable {
        JsonNode node = mapper.reader().readTree("{\"value\":11}");
        SimpleBean bean = mapper.readerFor(SimpleBean.class).readValue(node);
        assertEquals(11, bean.getValue());
    }

    // readTree(String): parses JSON text into a tree with expected field values
    @Test
    public void testReadTree_string_basic() throws Throwable {
        JsonNode node = mapper.reader().readTree("{\"a\":3}");
        assertEquals(3, node.get("a").asInt());
    }

    // readValues(String): iterates an unwrapped root-level sequence of values
    @Test
    public void testReadValues_string_iteratesAllValues() throws Throwable {
        MappingIterator<Integer> it = mapper.reader().forType(Integer.class).readValues("1 2 3");
        List<Integer> collected = new ArrayList<Integer>();
        while (it.hasNext()) {
            collected.add(it.next());
        }
        assertEquals(3, collected.size());
        assertEquals(Integer.valueOf(3), collected.get(2));
    }

    // treeToValue(): converts a tree node into the requested value type
    @Test
    public void testTreeToValue_basic() throws Throwable {
        JsonNode node = mapper.reader().readTree("{\"value\":13}");
        SimpleBean bean = mapper.reader().treeToValue(node, SimpleBean.class);
        assertEquals(13, bean.getValue());
    }

    // at(String pointer): extracts the object subtree located at the pointer path, scoped correctly
    @Test
    public void testAt_stringPointer_extractsObjectSubtree() throws Throwable {
        ObjectReader filtered = mapper.reader().at("/b");
        JsonNode result = filtered.readTree("{\"a\":1,\"b\":{\"x\":2,\"y\":3}}");
        JsonNode x = result.get("x");
        assertNotNull(x);
        assertEquals(2, x.asInt());
        assertNull(result.get("a"));
    }

    // at(String pointer) on array: index must resolve to the correct zero-based element
    @Test
    public void testAt_stringPointer_arrayIndex_extractsCorrectElement() throws Throwable {
        ObjectReader filtered = mapper.reader().at("/list/1");
        JsonNode result = filtered.readTree("{\"list\":[10,20,30]}");
        assertEquals(20, result.asInt());
    }

    // at(JsonPointer): overload behaves the same as the String-based variant
    @Test
    public void testAt_jsonPointer_extractsValue() throws Throwable {
        JsonPointer ptr = JsonPointer.compile("/a");
        ObjectReader filtered = mapper.reader().at(ptr);
        JsonNode result = filtered.readTree("{\"a\":5,\"b\":6}");
        assertEquals(5, result.asInt());
    }

    // withFormatDetection + readValue(Reader): char-based source unsupported, must throw
    @Test
    public void testWithFormatDetection_readValueReader_throwsJsonProcessingException() throws Throwable {
        ObjectReader base = mapper.reader();
        ObjectReader detecting = base.withFormatDetection(base);
        try {
            detecting.readValue(new StringReader("{}"));
            fail("expected JsonProcessingException");
        } catch (JsonProcessingException expected) {
        }
    }

    // readValue on a JSON null literal returns the deserializer's null value (null for Integer)
    @Test
    public void testReadValue_nullLiteral_returnsNullForIntegerType() throws Throwable {
        Integer val = mapper.reader().forType(Integer.class).readValue("null");
        assertNull(val);
    }
}
