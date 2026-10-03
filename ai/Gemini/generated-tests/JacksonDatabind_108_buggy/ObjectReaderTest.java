package com.fasterxml.jackson.databind;

import java.io.*;
import java.net.URL;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

import org.junit.Test;
import static org.junit.Assert.*;

import com.fasterxml.jackson.core.*;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.node.JsonNodeFactory;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.type.TypeFactory;

public class ObjectReaderTest {

    @Test
    public void testObjectReaderConfigurationAndAccessors() throws Throwable {
        ObjectMapper mapper = new ObjectMapper();
        ObjectReader reader = mapper.reader();

        assertNotNull(reader.version());
        assertNotNull(reader.getConfig());
        assertNotNull(reader.getFactory());
        assertNotNull(reader.getTypeFactory());
        
        // Test feature flags and fluent factory methods
        ObjectReader readerFeatureOn = reader.with(DeserializationFeature.USE_BIG_DECIMAL_FOR_FLOATS);
        assertTrue(readerFeatureOn.isEnabled(DeserializationFeature.USE_BIG_DECIMAL_FOR_FLOATS));

        ObjectReader readerFeatureOff = readerFeatureOn.without(DeserializationFeature.USE_BIG_DECIMAL_FOR_FLOATS);
        assertFalse(readerFeatureOff.isEnabled(DeserializationFeature.USE_BIG_DECIMAL_FOR_FLOATS));

        ObjectReader readerWithFeatures = reader.withFeatures(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES);
        assertNotNull(readerWithFeatures);

        ObjectReader readerWithoutFeatures = reader.withoutFeatures(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES);
        assertNotNull(readerWithoutFeatures);

        ObjectReader readerParserFeature = reader.with(JsonParser.Feature.ALLOW_COMMENTS);
        assertTrue(readerParserFeature.isEnabled(JsonParser.Feature.ALLOW_COMMENTS));

        ObjectReader readerParserFeatureOff = reader.without(JsonParser.Feature.ALLOW_COMMENTS);
        assertFalse(readerParserFeatureOff.isEnabled(JsonParser.Feature.ALLOW_COMMENTS));

        ObjectReader readerParserFeatures = reader.withFeatures(JsonParser.Feature.ALLOW_COMMENTS);
        assertNotNull(readerParserFeatures);

        ObjectReader readerParserFeaturesOff = reader.withoutFeatures(JsonParser.Feature.ALLOW_COMMENTS);
        assertNotNull(readerParserFeaturesOff);

        ObjectReader readerFormatFeature = reader.with(com.fasterxml.jackson.core.json.JsonReadFeature.ALLOW_JAVA_COMMENTS);
        assertNotNull(readerFormatFeature);

        ObjectReader readerFormatFeatures = reader.withFeatures(com.fasterxml.jackson.core.json.JsonReadFeature.ALLOW_JAVA_COMMENTS);
        assertNotNull(readerFormatFeatures);

        ObjectReader readerFormatFeatureOff = reader.without(com.fasterxml.jackson.core.json.JsonReadFeature.ALLOW_JAVA_COMMENTS);
        assertNotNull(readerFormatFeatureOff);

        ObjectReader readerFormatFeaturesOff = reader.withoutFeatures(com.fasterxml.jackson.core.json.JsonReadFeature.ALLOW_JAVA_COMMENTS);
        assertNotNull(readerFormatFeaturesOff);
    }

    @Test
    public void testObjectReaderAtAndRootName() throws Throwable {
        ObjectMapper mapper = new ObjectMapper();
        ObjectReader reader = mapper.reader();

        ObjectReader atStr = reader.at("/test");
        assertNotNull(atStr);

        JsonPointer ptr = JsonPointer.compile("/test");
        ObjectReader atPtr = reader.at(ptr);
        assertNotNull(atPtr);

        ObjectReader rootNameStr = reader.withRootName("root");
        assertNotNull(rootNameStr);

        PropertyName propName = new PropertyName("rootProp");
        ObjectReader rootNameProp = reader.withRootName(propName);
        assertNotNull(rootNameProp);

        ObjectReader withoutRoot = reader.withoutRootName();
        assertNotNull(withoutRoot);
    }

    @Test
    public void testObjectReaderInjectableValuesAndView() throws Throwable {
        ObjectMapper mapper = new ObjectMapper();
        ObjectReader reader = mapper.reader();

        InjectableValues iv = new InjectableValues.Std();
        ObjectReader withIv = reader.with(iv);
        assertSame(iv, withIv.getInjectableValues());
        assertSame(withIv, withIv.with(iv)); // Test identity branch

        ObjectReader withView = reader.withView(Object.class);
        assertNotNull(withView);

        ObjectReader withLocale = reader.with(Locale.US);
        assertNotNull(withLocale);

        ObjectReader withTz = reader.with(TimeZone.getDefault());
        assertNotNull(withTz);

        ObjectReader withHandler = reader.withHandler(new DeserializationProblemHandler() {});
        assertNotNull(withHandler);

        ObjectReader withBase64 = reader.with(Base64Variants.MIME);
        assertNotNull(withBase64);
    }

    @Test
    public void testObjectReaderAttributes() throws Throwable {
        ObjectMapper mapper = new ObjectMapper();
        ObjectReader reader = mapper.reader();

        ObjectReader withAttrsObj = reader.with(com.fasterxml.jackson.databind.cfg.ContextAttributes.getEmpty());
        assertNotNull(withAttrsObj);

        Map<Object, Object> map = new HashMap<Object, Object>();
        map.put("key", "val");
        ObjectReader withAttrsMap = reader.withAttributes(map);
        assertNotNull(withAttrsMap);

        ObjectReader withAttr = reader.withAttribute("key", "val");
        assertNotNull(withAttr);

        ObjectReader withoutAttr = reader.withoutAttribute("key");
        assertNotNull(withoutAttr);
    }

    @Test
    public void testObjectReaderForTypeAndValueToUpdate() throws Throwable {
        ObjectMapper mapper = new ObjectMapper();
        ObjectReader reader = mapper.reader();

        ObjectReader forClass = reader.forType(String.class);
        assertNotNull(forClass);
        assertSame(forClass, reader.forType(String.class)); // idempotent check

        ObjectReader forTypeRef = reader.forType(new TypeReference<String>() {});
        assertNotNull(forTypeRef);

        ObjectReader forJavaType = reader.forType(reader.getTypeFactory().constructType(String.class));
        assertNotNull(forJavaType);

        @SuppressWarnings("deprecation")
        ObjectReader withTypeClass = reader.withType(String.class);
        assertNotNull(withTypeClass);

        @SuppressWarnings("deprecation")
        ObjectReader withTypeJavaType = reader.withType(reader.getTypeFactory().constructType(String.class));
        assertNotNull(withTypeJavaType);

        @SuppressWarnings("deprecation")
        ObjectReader withTypeRef = reader.withType(new TypeReference<String>() {});
        assertNotNull(withTypeRef);

        @SuppressWarnings("deprecation")
        ObjectReader withTypeRefPlain = reader.withType((java.lang.reflect.Type) String.class);
        assertNotNull(withTypeRefPlain);

        Object updateObj = new Object();
        ObjectReader withValUpdate = reader.withValueToUpdate(updateObj);
        assertNotNull(withValUpdate);
        assertSame(withValUpdate, reader.withValueToUpdate(updateObj)); // idempotent check

        ObjectReader removeValUpdate = reader.withValueToUpdate(null);
        assertNotNull(removeValUpdate);

        ObjectReader withValUpdateNoType = reader.forType((JavaType) null).withValueToUpdate(updateObj);
        assertNotNull(withValUpdateNoType);
    }

    @Test
    public void testObjectReaderNodeFactoryAndTreeCodec() throws Throwable {
        ObjectMapper mapper = new ObjectMapper();
        ObjectReader reader = mapper.reader();

        JsonNodeFactory nf = JsonNodeFactory.instance;
        ObjectReader withNf = reader.with(nf);
        assertNotNull(withNf);

        JsonNode arr = reader.createArrayNode();
        assertNotNull(arr);
        assertTrue(arr.isArray());

        JsonNode obj = reader.createObjectNode();
        assertNotNull(obj);
        assertTrue(obj.isObject());

        JsonParser parser = reader.treeAsTokens(obj);
        assertNotNull(parser);
        parser.close();

        boolean exceptionThrown = false;
        try {
            reader.writeTree(null, obj);
        } catch (UnsupportedOperationException e) {
            exceptionThrown = true;
        }
        assertTrue(exceptionThrown);

        exceptionThrown = false;
        try {
            reader.writeValue(null, obj);
        } catch (UnsupportedOperationException e) {
            exceptionThrown = true;
        }
        assertTrue(exceptionThrown);
    }

    @Test
    public void testObjectReaderFormatDetection() throws Throwable {
        ObjectMapper mapper = new ObjectMapper();
        ObjectReader reader = mapper.reader();

        ObjectReader formatDet = reader.withFormatDetection(reader);
        assertNotNull(formatDet);

        ObjectReader formatDetArr = reader.withFormatDetection(new ObjectReader[] { reader });
        assertNotNull(formatDetArr);
    }

    @Test
    public void testObjectReaderReadValuesAndReadTreeSources() throws Throwable {
        ObjectMapper mapper = new ObjectMapper();
        ObjectReader reader = mapper.reader().forType(String.class);

        String json = "\"hello\"";
        
        String valStr = reader.readValue(json);
        assertEquals("hello", valStr);

        String valReader = reader.readValue(new StringReader(json));
        assertEquals("hello", valReader);

        String valBytes = reader.readValue(json.getBytes("UTF-8"));
        assertEquals("hello", valBytes);

        String valBytesOffset = reader.readValue(json.getBytes("UTF-8"), 0, json.getBytes("UTF-8").length);
        assertEquals("hello", valBytesOffset);

        JsonNode treeStr = reader.readTree(json);
        assertNotNull(treeStr);
        assertEquals("hello", treeStr.asText());

        JsonNode treeReader = reader.readTree(new StringReader(json));
        assertNotNull(treeReader);

        JsonNode treeBytes = reader.readTree(json.getBytes("UTF-8"));
        assertNotNull(treeBytes);

        JsonNode treeBytesOffset = reader.readTree(json.getBytes("UTF-8"), 0, json.getBytes("UTF-8").length);
        assertNotNull(treeBytesOffset);

        JsonNode treeNode = reader.readValue(treeStr);
        assertEquals("hello", treeNode);

        Iterator<String> itStr = reader.readValues(json);
        assertNotNull(itStr);
        assertTrue(itStr.hasNext());
        assertEquals("hello", itStr.next());

        Iterator<String> itReader = reader.readValues(new StringReader(json));
        assertNotNull(itReader);

        Iterator<String> itBytes = reader.readValues(json.getBytes("UTF-8"), 0, json.getBytes("UTF-8").length);
        assertNotNull(itBytes);

        Iterator<String> itBytesPlain = reader.readValues(json.getBytes("UTF-8"));
        assertNotNull(itBytesPlain);

        Iterator<String> itTypeRef = reader.readValues(mapper.createParser(json), new TypeReference<String>() {});
        assertNotNull(itTypeRef);

        Iterator<String> itResolvedType = reader.readValues(mapper.createParser(json), reader.getTypeFactory().constructType(String.class));
        assertNotNull(itResolvedType);

        String valResolved = reader.readValue(mapper.createParser(json), (ResolvedType) reader.getTypeFactory().constructType(String.class));
        assertEquals("hello", valResolved);

        String valTypeRef = reader.readValue(mapper.createParser(json), new TypeReference<String>() {});
        assertEquals("hello", valTypeRef);

        String valClass = reader.readValue(mapper.createParser(json), String.class);
        assertEquals("hello", valClass);
    }

    @Test
    public void testObjectReaderFileAndURLSources() throws Throwable {
        ObjectMapper mapper = new ObjectMapper();
        ObjectReader reader = mapper.reader().forType(String.class);

        File tempFile = File.createTempFile("jackson-test", ".json");
        tempFile.deleteOnExit();
        FileWriter fw = new FileWriter(tempFile);
        fw.write("\"test-file\"");
        fw.close();

        String valFile = reader.readValue(tempFile);
        assertEquals("test-file", valFile);

        JsonNode treeFile = reader.readTree(tempFile);
        assertNotNull(treeFile);

        Iterator<String> itFile = reader.readValues(tempFile);
        assertNotNull(itFile);

        URL url = tempFile.toURI().toURL();
        String valUrl = reader.readValue(url);
        assertEquals("test-file", valUrl);

        Iterator<String> itUrl = reader.readValues(url);
        assertNotNull(itUrl);
    }

    @Test
    public void testObjectReaderDataInputAndInputStreamSources() throws Throwable {
        ObjectMapper mapper = new ObjectMapper();
        ObjectReader reader = mapper.reader().forType(String.class);

        byte[] data = "\"datainput\"".getBytes("UTF-8");
        DataInput dataInput = new DataInputStream(new ByteArrayInputStream(data));
        String valDataInput = reader.readValue(dataInput);
        assertEquals("datainput", valDataInput);

        DataInput dataInputTree = new DataInputStream(new ByteArrayInputStream(data));
        JsonNode treeDataInput = reader.readTree(dataInputTree);
        assertNotNull(treeDataInput);

        DataInput dataInputIter = new DataInputStream(new ByteArrayInputStream(data));
        Iterator<String> itDataInput = reader.readValues(dataInputIter);
        assertNotNull(itDataInput);

        InputStream in = new ByteArrayInputStream(data);
        String valIn = reader.readValue(in);
        assertEquals("datainput", valIn);

        InputStream inTree = new ByteArrayInputStream(data);
        JsonNode treeIn = reader.readTree(inTree);
        assertNotNull(treeIn);

        InputStream inIter = new ByteArrayInputStream(data);
        Iterator<String> itIn = reader.readValues(inIter);
        assertNotNull(itIn);
    }

    @Test
    public void testObjectReaderUndetectableSourcesException() throws Throwable {
        ObjectMapper mapper = new ObjectMapper();
        ObjectReader reader = mapper.reader().withFormatDetection(mapper.reader());

        boolean exCaught = false;
        try {
            reader.readValue(new StringReader("abc"));
        } catch (JsonParseException e) {
            exCaught = true;
            assertTrue(e.getMessage().contains("Cannot use source of type"));
        }
        assertTrue(exCaught);

        exCaught = false;
        try {
            reader.readTree(new StringReader("abc"));
        } catch (JsonParseException e) {
            exCaught = true;
        }
        assertTrue(exCaught);

        exCaught = false;
        try {
            reader.readValues(new StringReader("abc"));
        } catch (JsonParseException e) {
            exCaught = true;
        }
        assertTrue(exCaught);

        exCaught = false;
        try {
            reader.readValue("abc");
        } catch (JsonParseException e) {
            exCaught = true;
        }
        assertTrue(exCaught);

        exCaught = false;
        try {
            reader.readTree("abc");
        } catch (JsonParseException e) {
            exCaught = true;
        }
        assertTrue(exCaught);

        exCaught = false;
        try {
            reader.readValues("abc");
        } catch (JsonParseException e) {
            exCaught = true;
        }
        assertTrue(exCaught);

        exCaught = false;
        try {
            reader.readValue(JsonNodeFactory.instance.textNode("abc"));
        } catch (JsonParseException e) {
            exCaught = true;
        }
        assertTrue(exCaught);

        exCaught = false;
        try {
            reader.readValue(new DataInputStream(new ByteArrayInputStream(new byte[0])));
        } catch (JsonParseException e) {
            exCaught = true;
        }
        assertTrue(exCaught);

        exCaught = false;
        try {
            reader.readTree(new DataInputStream(new ByteArrayInputStream(new byte[0])));
        } catch (JsonParseException e) {
            exCaught = true;
        }
        assertTrue(exCaught);

        exCaught = false;
        try {
            reader.readValues(new DataInputStream(new ByteArrayInputStream(new byte[0])));
        } catch (JsonParseException e) {
            exCaught = true;
        }
        assertTrue(exCaught);
    }

    @Test
    public void testObjectReaderNullValueAndEmptyObjectHandling() throws Throwable {
        ObjectMapper mapper = new ObjectMapper();
        ObjectReader reader = mapper.reader().forType(String.class);

        String nullVal = reader.readValue("null");
        assertNull(nullVal);

        String emptyObjVal = reader.readValue("{}");
        assertNull(emptyObjVal);

        String emptyArrVal = reader.readValue("[]");
        assertNull(emptyArrVal);

        ObjectReader readerWithUpdate = reader.withValueToUpdate("default");
        String updateNull = readerWithUpdate.readValue("null");
        assertEquals("default", updateNull);

        String updateEmptyObj = readerWithUpdate.readValue("{}");
        assertEquals("default", updateEmptyObj);

        JsonNode nullTree = reader.readTree("null");
        assertNotNull(nullTree);
        assertTrue(nullTree.isNull());

        JsonNode emptyTree = reader.readTree(mapper.createParser(""));
        assertNotNull(emptyTree);
        assertTrue(emptyTree.isMissingNode());
    }

    @Test
    public void testObjectReaderCustomFactoryRelinking() throws Throwable {
        ObjectMapper mapper = new ObjectMapper();
        ObjectReader reader = mapper.reader();

        JsonFactory customFactory = new JsonFactory();
        customFactory.setCodec(null);
        ObjectReader readerWithCustom = reader.with(customFactory);
        assertNotNull(readerWithCustom);
        assertSame(readerWithCustom, reader.with(mapper.getFactory())); // Same factory branch
    }

    @Test
    public void testObjectReaderSchemaValidationException() throws Throwable {
        ObjectMapper mapper = new ObjectMapper();
        ObjectReader reader = mapper.reader();

        FormatSchema badSchema = new FormatSchema() {
            public String getSchemaType() { return "unknown"; }
        };

        boolean exCaught = false;
        try {
            reader.with(badSchema);
        } catch (IllegalArgumentException e) {
            exCaught = true;
            assertTrue(e.getMessage().contains("Cannot use FormatSchema"));
        }
        assertTrue(exCaught);
    }

}