package com.fasterxml.jackson.databind;

import org.junit.Before;
import org.junit.Test;
import static org.junit.Assert.*;

import java.io.ByteArrayInputStream;
import java.io.InputStream;
import java.io.StringWriter;
import java.util.Iterator;
import java.util.Locale;
import java.util.TimeZone;

import com.fasterxml.jackson.core.JsonGenerator;
import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.core.type.TypeReference;

public class ObjectReaderClaudeTest
{
    public static class SimpleBean
    {
        private int value;
        private String name;
        public SimpleBean() { }
        public int getValue() { return value; }
        public void setValue(int v) { this.value = v; }
        public String getName() { return name; }
        public void setName(String n) { this.name = n; }
    }

    private ObjectMapper mapper;

    @Before
    public void setUp() throws Throwable {
        mapper = new ObjectMapper();
    }

    // covers version() returning non-null Version object
    @Test
    public void testVersion_returnsNonNullVersion() throws Throwable {
        ObjectReader r = mapper.readerFor(Integer.class);
        assertNotNull(r.version());
    }

    // covers with(single feature), with(first,other...), withFeatures(varargs) branches
    @Test
    public void testWithDeserializationFeature_singleAndMultipleAndVarargs_enablesAll() throws Throwable {
        ObjectReader r = mapper.readerFor(Integer.class).with(DeserializationFeature.UNWRAP_ROOT_VALUE);
        assertTrue(r.isEnabled(DeserializationFeature.UNWRAP_ROOT_VALUE));
        ObjectReader r2 = r.with(DeserializationFeature.EAGER_DESERIALIZER_FETCH, DeserializationFeature.FAIL_ON_NULL_FOR_PRIMITIVES);
        assertTrue(r2.isEnabled(DeserializationFeature.EAGER_DESERIALIZER_FETCH));
        assertTrue(r2.isEnabled(DeserializationFeature.FAIL_ON_NULL_FOR_PRIMITIVES));
        ObjectReader r3 = r2.withFeatures(DeserializationFeature.WRAP_EXCEPTIONS);
        assertTrue(r3.isEnabled(DeserializationFeature.WRAP_EXCEPTIONS));
    }

    // covers without(single), without(first,other...), withoutFeatures(varargs) branches
    @Test
    public void testWithoutDeserializationFeature_singleAndMultipleAndVarargs_disablesAll() throws Throwable {
        ObjectReader r = mapper.readerFor(Integer.class).without(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES);
        assertFalse(r.isEnabled(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES));
        ObjectReader r2 = r.without(DeserializationFeature.WRAP_EXCEPTIONS, DeserializationFeature.FAIL_ON_NULL_FOR_PRIMITIVES);
        assertFalse(r2.isEnabled(DeserializationFeature.WRAP_EXCEPTIONS));
        assertFalse(r2.isEnabled(DeserializationFeature.FAIL_ON_NULL_FOR_PRIMITIVES));
        ObjectReader r3 = r2.withoutFeatures(DeserializationFeature.EAGER_DESERIALIZER_FETCH);
        assertFalse(r3.isEnabled(DeserializationFeature.EAGER_DESERIALIZER_FETCH));
    }

    // covers with(JsonParser.Feature), withFeatures(...), without(...), withoutFeatures(...)
    @Test
    public void testWithAndWithoutJsonParserFeature_variousForms_toggleCorrectly() throws Throwable {
        ObjectReader r = mapper.readerFor(Integer.class).with(JsonParser.Feature.ALLOW_COMMENTS);
        assertTrue(r.isEnabled(JsonParser.Feature.ALLOW_COMMENTS));
        ObjectReader r2 = r.withFeatures(JsonParser.Feature.ALLOW_SINGLE_QUOTES);
        assertTrue(r2.isEnabled(JsonParser.Feature.ALLOW_SINGLE_QUOTES));
        ObjectReader r3 = r2.without(JsonParser.Feature.ALLOW_COMMENTS);
        assertFalse(r3.isEnabled(JsonParser.Feature.ALLOW_COMMENTS));
        ObjectReader r4 = r3.withoutFeatures(JsonParser.Feature.ALLOW_SINGLE_QUOTES);
        assertFalse(r4.isEnabled(JsonParser.Feature.ALLOW_SINGLE_QUOTES));
    }

    // covers isEnabled(MapperFeature)
    @Test
    public void testIsEnabledMapperFeature_defaultTrueForUseAnnotations() throws Throwable {
        ObjectReader r = mapper.readerFor(Integer.class);
        assertTrue(r.isEnabled(MapperFeature.USE_ANNOTATIONS));
    }

    // covers with(DeserializationConfig) identity branch (newConfig == _config)
    @Test
    public void testWithDeserializationConfig_sameReference_returnsThis() throws Throwable {
        ObjectReader r = mapper.readerFor(Integer.class);
        ObjectReader r2 = r.with(r.getConfig());
        assertSame(r, r2);
    }

    // covers with(InjectableValues) identity branch (_injectableValues == injectableValues)
    @Test
    public void testWithInjectableValues_sameNullReference_returnsThis() throws Throwable {
        ObjectReader r = mapper.readerFor(Integer.class);
        ObjectReader r2 = r.with((InjectableValues) null);
        assertSame(r, r2);
    }

    // covers with(JsonFactory) identity branch (f == _parserFactory)
    @Test
    public void testWithJsonFactory_sameReference_returnsThis() throws Throwable {
        ObjectReader r = mapper.readerFor(Integer.class);
        ObjectReader r2 = r.with(r.getFactory());
        assertSame(r, r2);
    }

    // covers with(FormatSchema) identity branch (_schema == schema)
    @Test
    public void testWithFormatSchema_sameNullReference_returnsThis() throws Throwable {
        ObjectReader r = mapper.readerFor(Integer.class);
        ObjectReader r2 = r.with((FormatSchema) null);
        assertSame(r, r2);
    }

    // covers withRootName + unwrap branch: matching name, successful deserialize
    @Test
    public void testUnwrapRoot_matchingName_deserializesInnerValue() throws Throwable {
        ObjectReader r = mapper.readerFor(SimpleBean.class)
                .with(DeserializationFeature.UNWRAP_ROOT_VALUE).withRootName("root");
        SimpleBean bean = r.readValue("{\"root\":{\"value\":5,\"name\":\"foo\"}}");
        assertEquals(5, bean.getValue());
        assertEquals("foo", bean.getName());
    }

    // covers _unwrapAndDeserialize name-mismatch throw branch
    @Test
    public void testUnwrapRoot_nameMismatch_throwsJsonMappingException() throws Throwable {
        ObjectReader r = mapper.readerFor(SimpleBean.class)
                .with(DeserializationFeature.UNWRAP_ROOT_VALUE).withRootName("root");
        try {
            r.readValue("{\"other\":{\"value\":1}}");
            fail("expected JsonMappingException");
        } catch (JsonMappingException expected) { }
    }

    // covers _unwrapAndDeserialize not-START_OBJECT throw branch
    @Test
    public void testUnwrapRoot_notStartObject_throwsJsonMappingException() throws Throwable {
        ObjectReader r = mapper.readerFor(SimpleBean.class)
                .with(DeserializationFeature.UNWRAP_ROOT_VALUE).withRootName("root");
        try {
            r.readValue("[1,2,3]");
            fail("expected JsonMappingException");
        } catch (JsonMappingException expected) { }
    }

    // covers forType(Class) identity branch (valueType.equals(_valueType))
    @Test
    public void testForTypeClass_sameType_returnsSameInstance() throws Throwable {
        ObjectReader r = mapper.readerFor(Integer.class);
        ObjectReader r2 = r.forType(Integer.class);
        assertSame(r, r2);
    }

    // covers forType(Class) different-type branch
    @Test
    public void testForTypeClass_differentType_changesDeserializedType() throws Throwable {
        ObjectReader r = mapper.readerFor(Integer.class).forType(String.class);
        String result = r.readValue("\"changed\"");
        assertEquals("changed", result);
    }

    // covers forType(TypeReference)
    @Test
    public void testForTypeTypeReference_deserializesCorrectType() throws Throwable {
        ObjectReader r = mapper.readerFor(Object.class).forType(new TypeReference<Integer>() { });
        Integer result = r.readValue("77");
        assertEquals(Integer.valueOf(77), result);
    }

    // covers deprecated withType(Class) delegating to forType
    @Test
    public void testDeprecatedWithTypeClass_deserializesCorrectType() throws Throwable {
        ObjectReader r = mapper.readerFor(Object.class).withType(String.class);
        String result = r.readValue("\"dep\"");
        assertEquals("dep", result);
    }

    // covers withValueToUpdate(null) throw branch
    @Test
    public void testWithValueToUpdate_nullValue_throwsIllegalArgumentException() throws Throwable {
        ObjectReader r = mapper.readerFor(SimpleBean.class);
        try {
            r.withValueToUpdate(null);
            fail("expected IllegalArgumentException");
        } catch (IllegalArgumentException expected) { }
    }

    // covers withValueToUpdate identity branch (value == _valueToUpdate)
    @Test
    public void testWithValueToUpdate_sameReferenceTwice_returnsSameInstance() throws Throwable {
        SimpleBean bean = new SimpleBean();
        ObjectReader r1 = mapper.readerFor(SimpleBean.class).withValueToUpdate(bean);
        ObjectReader r2 = r1.withValueToUpdate(bean);
        assertSame(r1, r2);
    }

    // covers withValueToUpdate updating existing instance and returning same reference
    @Test
    public void testWithValueToUpdate_updatesExistingInstance() throws Throwable {
        SimpleBean bean = new SimpleBean();
        ObjectReader r = mapper.readerFor(SimpleBean.class).withValueToUpdate(bean);
        SimpleBean result = r.readValue("{\"value\":42,\"name\":\"up\"}");
        assertSame(bean, result);
        assertEquals(42, result.getValue());
    }

    // covers withValueToUpdate array-type throw branch
    @Test
    public void testWithValueToUpdate_arrayType_throwsIllegalArgumentException() throws Throwable {
        ObjectReader r = mapper.readerFor(int[].class);
        try {
            r.withValueToUpdate(new int[3]);
            fail("expected IllegalArgumentException");
        } catch (IllegalArgumentException expected) { }
    }

    // covers withView, with(Locale), with(TimeZone) delegation methods functionally
    @Test
    public void testWithViewLocaleTimeZone_returnFunctionalReaders() throws Throwable {
        ObjectReader r = mapper.readerFor(Integer.class)
                .withView(Object.class).with(Locale.US).with(TimeZone.getTimeZone("UTC"));
        Integer result = r.readValue("6");
        assertEquals(Integer.valueOf(6), result);
    }

    // covers accessor methods getConfig/getFactory/getJsonFactory/getTypeFactory/getAttributes
    @Test
    public void testAccessors_returnNonNullConfigurationObjects() throws Throwable {
        ObjectReader r = mapper.readerFor(Integer.class);
        assertNotNull(r.getConfig());
        assertNotNull(r.getFactory());
        assertSame(r.getFactory(), r.getJsonFactory());
        assertNotNull(r.getTypeFactory());
        assertNotNull(r.getAttributes());
    }

    // covers readValue(JsonParser) basic path
    @Test
    public void testReadValueJsonParser_simpleInt_returnsValue() throws Throwable {
        ObjectReader r = mapper.readerFor(Integer.class);
        JsonParser p = r.getFactory().createParser("123");
        Integer result = r.readValue(p);
        assertEquals(Integer.valueOf(123), result);
    }

    // covers readValue(JsonParser, Class) overriding configured type
    @Test
    public void testReadValueJsonParserClass_overridesConfiguredType_returnsCorrectType() throws Throwable {
        ObjectReader r = mapper.readerFor(Integer.class);
        JsonParser p = r.getFactory().createParser("\"hello\"");
        String result = r.readValue(p, String.class);
        assertEquals("hello", result);
    }

    // covers readValue(JsonParser, TypeReference)
    @Test
    public void testReadValueJsonParserTypeReference_returnsCorrectType() throws Throwable {
        ObjectReader r = mapper.readerFor(Object.class);
        JsonParser p = r.getFactory().createParser("42");
        Integer result = r.readValue(p, new TypeReference<Integer>() { });
        assertEquals(Integer.valueOf(42), result);
    }

    // covers readValue(JsonParser, JavaType)
    @Test
    public void testReadValueJsonParserJavaType_returnsCorrectType() throws Throwable {
        ObjectReader r = mapper.readerFor(Object.class);
        JavaType jt = r.getConfig().constructType(String.class);
        JsonParser p = r.getFactory().createParser("\"abc\"");
        String result = r.readValue(p, jt);
        assertEquals("abc", result);
    }

    // covers readValues(JsonParser, Class) wrapped-array iteration
    @Test
    public void testReadValuesJsonParserClass_wrappedArray_iteratesAllElements() throws Throwable {
        ObjectReader r = mapper.readerFor(Object.class);
        JsonParser p = r.getFactory().createParser("[1,2,3]");
        p.nextToken();
        p.nextToken();
        Iterator<Integer> it = r.readValues(p, Integer.class);
        assertEquals(Integer.valueOf(1), it.next());
        assertEquals(Integer.valueOf(2), it.next());
        assertEquals(Integer.valueOf(3), it.next());
        assertFalse(it.hasNext());
    }

    // covers readValues(JsonParser, TypeReference)
    @Test
    public void testReadValuesJsonParserTypeReference_iteratesElements() throws Throwable {
        ObjectReader r = mapper.readerFor(Object.class);
        JsonParser p = r.getFactory().createParser("[\"a\",\"b\"]");
        p.nextToken();
        p.nextToken();
        Iterator<String> it = r.readValues(p, new TypeReference<String>() { });
        assertEquals("a", it.next());
        assertEquals("b", it.next());
        assertFalse(it.hasNext());
    }

    // covers createArrayNode/createObjectNode TreeCodec methods
    @Test
    public void testCreateArrayNodeAndObjectNode_returnCorrectNodeTypes() throws Throwable {
        ObjectReader r = mapper.readerFor(Object.class);
        JsonNode arr = r.createArrayNode();
        JsonNode obj = r.createObjectNode();
        assertTrue(arr.isArray());
        assertTrue(obj.isObject());
    }

    // covers treeAsTokens + readTree(JsonParser) round trip
    @Test
    public void testTreeAsTokensAndReadTreeJsonParser_roundTrips() throws Throwable {
        ObjectReader r = mapper.readerFor(Object.class);
        JsonNode obj = r.createObjectNode();
        JsonParser p = r.treeAsTokens(obj);
        JsonNode result = r.readTree(p);
        assertTrue(result.isObject());
    }

    // covers readValue(InputStream)
    @Test
    public void testReadValueInputStream_returnsValue() throws Throwable {
        ObjectReader r = mapper.readerFor(Integer.class);
        InputStream in = new ByteArrayInputStream("99".getBytes("UTF-8"));
        Integer result = r.readValue(in);
        assertEquals(Integer.valueOf(99), result);
    }

    // covers readValue(String) end-of-input throw branch in _initForReading
    @Test
    public void testReadValueString_emptyContent_throwsJsonMappingException() throws Throwable {
        ObjectReader r = mapper.readerFor(Integer.class);
        try {
            r.readValue("");
            fail("expected JsonMappingException");
        } catch (JsonMappingException expected) {
            assertTrue(expected.getMessage().contains("No content to map"));
        }
    }

    // covers readValue(byte[])
    @Test
    public void testReadValueByteArray_returnsValue() throws Throwable {
        ObjectReader r = mapper.readerFor(Integer.class);
        Integer result = r.readValue("42".getBytes("UTF-8"));
        assertEquals(Integer.valueOf(42), result);
    }

    // covers readValue(byte[], offset, length) correctly honoring offset/length
    @Test
    public void testReadValueByteArrayOffsetLength_respectsOffsetAndLength() throws Throwable {
        ObjectReader r = mapper.readerFor(Integer.class);
        byte[] src = "XX123YY".getBytes("UTF-8");
        Integer result = r.readValue(src, 2, 3);
        assertEquals(Integer.valueOf(123), result);
    }

    // covers readValue(JsonNode)
    @Test
    public void testReadValueJsonNode_convertsTreeToValue() throws Throwable {
        ObjectReader treeReader = mapper.readerFor(Object.class);
        JsonNode node = treeReader.readTree("55");
        ObjectReader r = mapper.readerFor(Integer.class);
        Integer result = r.readValue(node);
        assertEquals(Integer.valueOf(55), result);
    }

    // covers readTree(InputStream)
    @Test
    public void testReadTreeInputStream_returnsTree() throws Throwable {
        ObjectReader r = mapper.readerFor(Object.class);
        InputStream in = new ByteArrayInputStream("{\"a\":1}".getBytes("UTF-8"));
        JsonNode node = r.readTree(in);
        assertTrue(node.isObject());
        assertEquals(1, node.get("a").asInt());
    }

    // covers readValues(JsonParser) unwrapped-sequence path
    @Test
    public void testReadValuesJsonParser_unwrappedSequence_iteratesAllValues() throws Throwable {
        ObjectReader r = mapper.readerFor(Integer.class);
        JsonParser p = r.getFactory().createParser("1 2 3");
        p.nextToken();
        MappingIterator<Integer> it = r.readValues(p);
        assertEquals(Integer.valueOf(1), it.nextValue());
        assertEquals(Integer.valueOf(2), it.nextValue());
        assertEquals(Integer.valueOf(3), it.nextValue());
        assertFalse(it.hasNextValue());
    }

    // covers readValues(InputStream) wrapped-array path
    @Test
    public void testReadValuesInputStream_wrappedArray_iteratesAllElements() throws Throwable {
        ObjectReader r = mapper.readerFor(Integer.class);
        InputStream in = new ByteArrayInputStream("[7,8]".getBytes("UTF-8"));
        MappingIterator<Integer> it = r.readValues(in);
        assertEquals(Integer.valueOf(7), it.nextValue());
        assertEquals(Integer.valueOf(8), it.nextValue());
        assertFalse(it.hasNextValue());
    }

    // covers readValues(String) wrapped-array path
    @Test
    public void testReadValuesString_wrappedArray_iteratesAllElements() throws Throwable {
        ObjectReader r = mapper.readerFor(Integer.class);
        MappingIterator<Integer> it = r.readValues("[9,10,11]");
        assertEquals(Integer.valueOf(9), it.nextValue());
        assertEquals(Integer.valueOf(10), it.nextValue());
        assertEquals(Integer.valueOf(11), it.nextValue());
        assertFalse(it.hasNextValue());
    }

    // covers readValues(byte[]) delegating correctly to readValues(byte[],0,length)
    @Test
    public void testReadValuesByteArray_wrappedArray_iteratesAllElements() throws Throwable {
        ObjectReader r = mapper.readerFor(Integer.class);
        MappingIterator<Integer> it = r.readValues("[1,2]".getBytes("UTF-8"));
        assertEquals(Integer.valueOf(1), it.nextValue());
        assertEquals(Integer.valueOf(2), it.nextValue());
        assertFalse(it.hasNextValue());
    }

    // BUG TEST: readValues(byte[], offset, length) must honor offset/length like readValue(byte[],offset,length) does
    @Test
    public void testReadValuesByteArrayOffsetLength_respectsOffsetAndLength_bugCheck() throws Throwable {
        ObjectReader r = mapper.readerFor(Integer.class);
        byte[] src = "XX[1,2,3]YY".getBytes("UTF-8");
        MappingIterator<Integer> it = r.readValues(src, 2, 7);
        assertEquals(Integer.valueOf(1), it.nextValue());
        assertEquals(Integer.valueOf(2), it.nextValue());
        assertEquals(Integer.valueOf(3), it.nextValue());
        assertFalse(it.hasNextValue());
    }

    // covers treeToValue(TreeNode, Class)
    @Test
    public void testTreeToValue_convertsNodeToTargetType() throws Throwable {
        ObjectReader r = mapper.readerFor(Object.class);
        JsonNode node = r.readTree("\"hello\"");
        String result = r.treeToValue(node, String.class);
        assertEquals("hello", result);
    }

    // covers writeValue always throwing UnsupportedOperationException
    @Test
    public void testWriteValue_alwaysThrowsUnsupportedOperationException() throws Throwable {
        ObjectReader r = mapper.readerFor(Integer.class);
        JsonGenerator gen = r.getFactory().createGenerator(new StringWriter());
        try {
            r.writeValue(gen, Integer.valueOf(5));
            fail("expected UnsupportedOperationException");
        } catch (UnsupportedOperationException expected) { }
    }

    // covers writeTree always throwing UnsupportedOperationException
    @Test
    public void testWriteTree_alwaysThrowsUnsupportedOperationException() throws Throwable {
        ObjectReader r = mapper.readerFor(Object.class);
        JsonGenerator gen = r.getFactory().createGenerator(new StringWriter());
        try {
            r.writeTree(gen, r.createObjectNode());
            fail("expected UnsupportedOperationException");
        } catch (UnsupportedOperationException expected) { }
    }
}
