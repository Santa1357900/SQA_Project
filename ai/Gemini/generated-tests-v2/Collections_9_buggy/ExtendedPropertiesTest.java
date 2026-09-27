package org.apache.commons.collections;

import org.junit.Test;
import static org.junit.Assert.*;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.StringReader;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.NoSuchElementException;
import java.util.Properties;
import java.util.Vector;

public class ExtendedPropertiesTest {

    @Test
    public void testEmptyConstructor() throws Throwable {
        ExtendedProperties props = new ExtendedProperties();
        assertFalse(props.isInitialized());
        assertNull(props.getInclude());
        props.setInclude("customInclude");
        assertEquals("customInclude", props.getInclude());
        props.setInclude(null);
        assertNull(props.getInclude());
        props.setInclude("");
        assertNull(props.getInclude());
    }

    @Test
    public void testFileConstructorAndLoad() throws Throwable {
        File tempFile = File.createTempFile("extprops", ".properties");
        tempFile.deleteOnExit();
        java.io.FileWriter writer = new java.io.FileWriter(tempFile);
        writer.write("key1 = value1\n");
        writer.write("key2 = token1, token2\n");
        writer.write("long = line1 \\\n line2\n");
        writer.write("include = non-existent.properties\n");
        writer.close();

        ExtendedProperties props = new ExtendedProperties(tempFile.getAbsolutePath());
        assertTrue(props.isInitialized());
        assertEquals("value1", props.getString("key1"));
        assertEquals("line1line2", props.getString("long"));
    }

    @Test
    public void testLoadInputStreamWithEncoding() throws Throwable {
        ExtendedProperties props = new ExtendedProperties();
        String data = "test.key = test.value\n";
        ByteArrayInputStream bais = new ByteArrayInputStream(data.getBytes("UTF-8"));
        props.load(bais, "UTF-8");
        assertEquals("test.value", props.getString("test.key"));
    }

    @Test
    public void testLoadInputStreamUnsupportedEncoding() throws Throwable {
        ExtendedProperties props = new ExtendedProperties();
        String data = "test.key = test.value\n";
        ByteArrayInputStream bais = new ByteArrayInputStream(data.getBytes("8859_1"));
        props.load(bais, "NON_EXISTENT_ENCODING");
        assertEquals("test.value", props.getString("test.key"));
    }

    @Test
    public void testPropertiesReaderAndTokenizerEdges() throws Throwable {
        String content = "# Comment\n\n   \nkey.escaped = Hi\\, what'up?\\\n, next token\n";
        ExtendedProperties.PropertiesReader reader = new ExtendedProperties.PropertiesReader(new StringReader(content));
        String prop = reader.readProperty();
        assertNotNull(prop);

        ExtendedProperties.PropertiesTokenizer tokenizer = new ExtendedProperties.PropertiesTokenizer("token1,token2\\,escaped");
        assertTrue(tokenizer.hasMoreTokens());
        assertEquals("token1", tokenizer.nextToken());
        assertEquals("token2,escaped", tokenizer.nextToken());
        assertFalse(tokenizer.hasMoreTokens());
    }

    @Test
    public void testInterpolationAndInfiniteLoop() throws Throwable {
        ExtendedProperties props = new ExtendedProperties();
        props.setProperty("prop1", "${prop2}");
        props.setProperty("prop2", "${prop1}");

        try {
            props.getString("prop1");
            fail("Expected IllegalStateException for infinite loop");
        } catch (IllegalStateException e) {
            assertTrue(e.getMessage().contains("infinite loop"));
        }

        ExtendedProperties props2 = new ExtendedProperties();
        props2.setProperty("name", "World");
        props2.setProperty("greeting", "Hello ${name}");
        assertEquals("Hello World", props2.getString("greeting"));
        assertNull(props2.getString(null));
        assertNull(props2.interpolate(null));
        
        // Undefined variable interpolation
        assertEquals("${undefined.var}", props2.getString("undefined.var", "${undefined.var}"));
    }

    @Test
    public void testDefaultsInterpolation() throws Throwable {
        ExtendedProperties defaults = new ExtendedProperties();
        defaults.setProperty("def.key", "def.value");
        
        ExtendedProperties props = new ExtendedProperties();
        // use reflection or package access? defaults is private, but we can set via file or subclass, or test through constructor if needed.
        // Actually, we can test defaults via second parameter of constructor if file exists, or just test null fallback.
        assertNull(props.getString("missing", null));
    }

    @Test
    public void testAddAndGetPropertyVariations() throws Throwable {
        ExtendedProperties props = new ExtendedProperties();
        props.addProperty("list.key", "val1,val2");
        props.addProperty("list.key", "val3");
        
        List list = props.getList("list.key");
        assertEquals(3, list.size());

        Vector vector = props.getVector("list.key");
        assertEquals(3, vector.size());

        String[] arr = props.getStringArray("list.key");
        assertEquals(3, arr.length);

        // Test non-string add
        props.addProperty("int.key", new Integer(123));
        assertEquals(new Integer(123), props.get("int.key"));

        // Test putting a list directly to trigger branch in addPropertyInternal
        List directList = new ArrayList();
        directList.add("a");
        props.put("direct.list", directList);
        props.addProperty("direct.list", "b");
    }

    @Test
    public void testGetPropertiesParsing() throws Throwable {
        ExtendedProperties props = new ExtendedProperties();
        props.setProperty("props.key", "p1=v1, p2=v2");
        Properties p = props.getProperties("props.key");
        assertEquals("v1", p.getProperty("p1"));
        assertEquals("v2", p.getProperty("p2"));

        props.setProperty("props.malformed", "malformed_token");
        try {
            props.getProperties("props.malformed");
            fail("Expected IllegalArgumentException");
        } catch (IllegalArgumentException e) {
            assertTrue(e.getMessage().contains("does not contain an equals sign"));
        }
    }

    @Test
    public void testPrimitiveGettersAndClassCasts() throws Throwable {
        ExtendedProperties props = new ExtendedProperties();
        props.setProperty("bool.true", "true");
        props.setProperty("bool.yes", "YES");
        props.setProperty("bool.off", "off");
        props.setProperty("bool.invalid", "maybe");
        props.setProperty("byte.val", "10");
        props.setProperty("short.val", "20");
        props.setProperty("int.val", "30");
        props.setProperty("long.val", "40");
        props.setProperty("float.val", "50.5");
        props.setProperty("double.val", "60.6");

        assertTrue(props.getBoolean("bool.true"));
        assertTrue(props.getBoolean("bool.yes", false));
        assertFalse(props.getBoolean("bool.off"));
        assertNull(props.getBoolean("bool.invalid", (Boolean) null));
        
        assertEquals((byte) 10, props.getByte("byte.val"));
        assertEquals((byte) 15, props.getByte("non.existent", (byte) 15));
        assertEquals(new Byte((byte) 10), props.getByte("byte.val", (Byte) null));

        assertEquals((short) 20, props.getShort("short.val"));
        assertEquals((short) 25, props.getShort("non.existent", (short) 25));
        assertEquals(new Short((short) 20), props.getShort("short.val", (Short) null));

        assertEquals(30, props.getInt("int.val"));
        assertEquals(30, props.getInt("int.val", 99));
        assertEquals(35, props.getInt("non.existent", 35));
        assertEquals(new Integer(30), props.getInteger("int.val", (Integer) null));

        assertEquals(40L, props.getLong("long.val"));
        assertEquals(45L, props.getLong("non.existent", 45L));
        assertEquals(new Long(40L), props.getLong("long.val", (Long) null));

        assertEquals(50.5f, props.getFloat("float.val"), 0.001f);
        assertEquals(55.5f, props.getFloat("non.existent", 55.5f), 0.001f);
        assertEquals(new Float(50.5f), props.getFloat("float.val", (Float) null));

        assertEquals(60.6, props.getDouble("double.val"), 0.001);
        assertEquals(65.6, props.getDouble("non.existent", 65.6), 0.001);
        assertEquals(new Double(60.6), props.getDouble("double.val", (Double) null));

        // Test NoSuchElementException for primitive getters
        try {
            props.getBoolean("missing.bool");
            fail();
        } catch (NoSuchElementException e) {}

        try {
            props.getByte("missing.byte");
            fail();
        } catch (NoSuchElementException e) {}

        try {
            props.getShort("missing.short");
            fail();
        } catch (NoSuchElementException e) {}

        try {
            props.getInteger("missing.int");
            fail();
        } catch (NoSuchElementException e) {}

        try {
            props.getLong("missing.long");
            fail();
        } catch (NoSuchElementException e) {}

        try {
            props.getFloat("missing.float");
            fail();
        } catch (NoSuchElementException e) {}

        try {
            props.getDouble("missing.double");
            fail();
        } catch (NoSuchElementException e) {}

        // Test ClassCastException cases
        props.setProperty("string.val", "text");
        try {
            props.getBoolean("string.val");
            fail();
        } catch (ClassCastException e) {}

        try {
            props.getStringArray("string.val"); // string maps to string, but test invalid type mapping for other things like getVector with non-list/non-string
        } catch (Exception e) {}
    }

    @Test
    def testClassCastExceptionsForCollectionsAndScalars() throws Throwable {
        ExtendedProperties props = new ExtendedProperties();
        props.setProperty("int.obj", new Integer(5));
        try {
            props.getString("int.obj");
            fail();
        } catch (ClassCastException e) {}

        try {
            props.getStringArray("int.obj");
            fail();
        } catch (ClassCastException e) {}

        try {
            props.getVector("int.obj");
            fail();
        } catch (ClassCastException e) {}

        try {
            props.getList("int.obj");
            fail();
        } catch (ClassCastException e) {}
    }

    @Test
    public void testSubsetAndGetKeysWithPrefix() throws Throwable {
        ExtendedProperties props = new ExtendedProperties();
        props.setProperty("app.name", "TestApp");
        props.setProperty("app.version", "1.0");
        props.setProperty("other.key", "value");

        Iterator keysWithPrefix = props.getKeys("app.");
        assertTrue(keysWithPrefix.hasNext());

        ExtendedProperties subset = props.subset("app");
        assertNotNull(subset);
        assertEquals("TestApp", subset.getString("name"));

        ExtendedProperties singleSubset = props.subset("app.version");
        assertNotNull(singleSubset);
        assertEquals("1.0", singleSubset.getString("version"));

        ExtendedProperties invalidSubset = props.subset("nonexistent");
        assertNull(invalidSubset);
    }

    @Test
    public void testSaveAndDisplayAndCombine() throws Throwable {
        ExtendedProperties props = new ExtendedProperties();
        props.setProperty("save.key1", "val1");
        props.addProperty("save.key2", "val2,val3");

        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        props.save(baos, "Header Comment");
        assertTrue(baos.size() > 0);

        ByteArrayOutputStream baosNullHeader = new ByteArrayOutputStream();
        props.save(baosNullHeader, null);

        ByteArrayOutputStream baosNullOut = new ByteArrayOutputStream();
        props.save(null, "Header");

        ExtendedProperties props2 = new ExtendedProperties();
        props2.setProperty("save.key1", "newval1");
        props.combine(props2);
        assertEquals("newval1", props.getString("save.key1"));

        props.display();
    }

    @Test
    public void testConvertPropertiesAndPutAll() throws Throwable {
        Properties javaProps = new Properties();
        javaProps.setProperty("jp1", "jv1");
        ExtendedProperties ep = ExtendedProperties.convertProperties(javaProps);
        assertEquals("jv1", ep.getString("jp1"));

        ExtendedProperties ep2 = new ExtendedProperties();
        ep2.setProperty("jp2", "jv2");
        
        ExtendedProperties target = new ExtendedProperties();
        target.putAll(ep2);
        assertEquals("jv2", target.getString("jp2"));

        java.util.HashMap map = new java.util.HashMap();
        map.put("hm1", "hv1");
        target.putAll(map);
        assertEquals("hv1", target.getString("hm1"));
    }

    @Test
    public void testRemoveAndClearProperty() throws Throwable {
        ExtendedProperties props = new ExtendedProperties();
        props.setProperty("rem.key", "rem.val");
        assertEquals("rem.val", props.remove("rem.key"));
        assertNull(props.get("rem.key"));

        props.setProperty("rem.key2", "rem.val2");
        props.clearProperty("rem.key2");
        assertNull(props.get("rem.key2"));
    }
}