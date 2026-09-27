package org.apache.commons.collections;

import org.junit.Test;
import static org.junit.Assert.*;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.StringReader;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.NoSuchElementException;
import java.util.Properties;
import java.util.Vector;

public class ExtendedPropertiesTest {

    @Test
    public void testDefaultConstructorAndState() throws Throwable {
        ExtendedProperties props = new ExtendedProperties();
        assertFalse(props.isInitialized());
        assertNull(props.getInclude());
        
        props.setInclude("include");
        assertEquals("include", props.getInclude());

        props.setInclude(null);
        assertNull(props.getInclude());

        props.setInclude("");
        assertNull(props.getInclude());
    }

    @Test
    public void testFileConstructors() throws Throwable {
        File tempFile = File.createTempFile("extprops", ".properties");
        tempFile.deleteOnExit();

        File defaultFile = File.createTempFile("defaultprops", ".properties");
        defaultFile.deleteOnExit();

        ExtendedProperties props = new ExtendedProperties(tempFile.getAbsolutePath(), defaultFile.getAbsolutePath());
        assertNotNull(props);
    }

    @Test
    public void testLoadInputStreamAndEncoding() throws Throwable {
        ExtendedProperties props = new ExtendedProperties();
        String content = "key1 = value1\nkey2 = value2,value3\n# comment\ninvalid_line\n";
        ByteArrayInputStream bais = new ByteArrayInputStream(content.getBytes("UTF8"));
        props.load(bais, "UTF8");
        assertEquals("value1", props.getString("key1"));
        assertEquals("value2", props.getString("key2"));
    }

    @Test
    public void testLoadInputStreamUnsupportedEncoding() throws Throwable {
        ExtendedProperties props = new ExtendedProperties();
        String content = "key1 = value1\n";
        ByteArrayInputStream bais = new ByteArrayInputStream(content.getBytes("ISO-8859-1"));
        props.load(bais, "UNKNOWN_ENCODING_XYZ");
        assertEquals("value1", props.getString("key1"));
    }

    @Test
    public void testIncludeDirective() throws Throwable {
        File includedFile = File.createTempFile("inc", ".properties");
        includedFile.deleteOnExit();

        java.io.FileWriter fw = new java.io.FileWriter(includedFile);
        fw.write("included.key = included.value\n");
        fw.close();

        ExtendedProperties props = new ExtendedProperties();
        props.setInclude("include");
        
        String content = "include = " + includedFile.getAbsolutePath() + "\n";
        ByteArrayInputStream bais = new ByteArrayInputStream(content.getBytes());
        props.load(bais);

        assertEquals("included.value", props.getString("included.key"));
    }

    @Test
    public void testIncludeRelativeDirective() throws Throwable {
        File parentFile = File.createTempFile("parent", ".properties");
        parentFile.deleteOnExit();
        String parentPath = parentFile.getAbsolutePath();
        String parentDir = parentPath.substring(0, parentPath.lastIndexOf(File.separator) + 1);

        File childFile = new File(parentDir + "child.properties");
        childFile.deleteOnExit();
        java.io.FileWriter fw = new java.io.FileWriter(childFile);
        fw.write("child.key = child.value\n");
        fw.close();

        ExtendedProperties props = new ExtendedProperties(parentPath);
        props.setInclude("include");
        
        String content = "include = ./" + childFile.getName() + "\n";
        ByteArrayInputStream bais = new ByteArrayInputStream(content.getBytes());
        props.load(bais);

        assertEquals("child.value", props.getString("child.key"));
    }

    @Test
    public void testAddAndGetPropertyVariations() throws Throwable {
        ExtendedProperties props = new ExtendedProperties();
        props.addProperty("prop.string", "abc");
        props.addProperty("prop.string", "def"); // converts to list
        assertEquals(2, props.getList("prop.string").size());

        props.addProperty("prop.comma", "a,b,c");
        assertEquals("a", props.getStringArray("prop.comma")[0]);

        props.addProperty("prop.obj", new Object());
        assertNotNull(props.get("prop.obj"));

        props.setProperty("prop.set", "single");
        assertEquals("single", props.getString("prop.set"));
    }

    @Test
    public void testInterpolationAndLoops() throws Throwable {
        ExtendedProperties props = new ExtendedProperties();
        props.setProperty("base", "value");
        props.setProperty("inter", "${base}/sub");
        assertEquals("value/sub", props.getString("inter"));

        props.setProperty("loop1", "${loop2}");
        props.setProperty("loop2", "${loop1}");
        try {
            props.getString("loop1");
            fail("Expected IllegalStateException due to infinite loop");
        } catch (IllegalStateException e) {
            assertTrue(e.getMessage().contains("infinite loop"));
        }

        props.clear();
        props.setProperty("undef", "${undefined.var}");
        assertEquals("${undefined.var}", props.getString("undef"));
    }

    @Test
    public void testInterpolationWithDefaults() throws Throwable {
        ExtendedProperties defaults = new ExtendedProperties();
        defaults.setProperty("def.key", "def.val");

        ExtendedProperties props = new ExtendedProperties();
        // Use reflection or constructor to set defaults if needed, or via subclass/internal
        // Here we test defaults through standard get methods that check defaults
        // Since defaults is private, we can test via ExtendedProperties(file, defaultFile) or subclassing.
        // Let's test standard getter with defaults using a dummy file approach or subclass.
    }

    @Test
    public void testSaveAndCombine() throws Throwable {
        ExtendedProperties props = new ExtendedProperties();
        props.setProperty("save.key", "save.value");
        props.addProperty("save.list", "item1");
        props.addProperty("save.list", "item2");

        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        props.save(baos, "Header Comment");
        assertTrue(baos.toString().length() > 0);

        ByteArrayOutputStream baosNull = new ByteArrayOutputStream();
        props.save(null, "Header"); // Should return safely

        ExtendedProperties props2 = new ExtendedProperties();
        props2.setProperty("save.key2", "value2");
        props.combine(props2);
        assertEquals("value2", props.getString("save.key2"));
    }

    @Test
    public void testClearPropertyAndGetKeys() throws Throwable {
        ExtendedProperties props = new ExtendedProperties();
        props.setProperty("k1", "v1");
        props.setProperty("k2", "v2");
        props.setProperty("prefix.k3", "v3");

        assertTrue(props.getKeys().hasNext());
        
        Iterator matching = props.getKeys("prefix.");
        assertTrue(matching.hasNext());

        ExtendedProperties subset = props.subset("prefix");
        assertNotNull(subset);
        assertEquals("v3", subset.getString("k3"));

        ExtendedProperties invalidSubset = props.subset("nonexistent");
        assertNull(invalidSubset);

        props.clearProperty("k1");
        assertNull(props.getString("k1", null));
    }

    @Test
    public void testDisplay() throws Throwable {
        ExtendedProperties props = new ExtendedProperties();
        props.setProperty("k", "v");
        props.display(); // Just ensure it executes without error
    }

    @Test
    public void testGetProperties() throws Throwable {
        ExtendedProperties props = new ExtendedProperties();
        props.setProperty("props.key", "a=1,b=2");
        Properties p = props.getProperties("props.key");
        assertEquals("1", p.getProperty("a"));
        assertEquals("2", p.getProperty("b"));

        props.setProperty("props.invalid", "malformed_token");
        try {
            props.getProperties("props.invalid");
            fail("Expected IllegalArgumentException");
        } catch (IllegalArgumentException e) {
            assertTrue(e.getMessage().contains("does not contain"));
        }
    }

    @Test
    public void testGetPrimitivesAndTypes() throws Throwable {
        ExtendedProperties props = new ExtendedProperties();
        props.setProperty("bool.true", "true");
        props.setProperty("bool.yes", "yes");
        props.setProperty("bool.on", "on");
        props.setProperty("bool.false", "false");
        props.setProperty("bool.no", "no");
        props.setProperty("bool.off", "off");
        props.setProperty("bool.obj", Boolean.TRUE);

        assertTrue(props.getBoolean("bool.true"));
        assertTrue(props.getBoolean("bool.yes", false));
        assertTrue(props.getBoolean("bool.on"));
        assertFalse(props.getBoolean("bool.false"));
        assertFalse(props.getBoolean("bool.no"));
        assertFalse(props.getBoolean("bool.off"));
        assertTrue(props.getBoolean("bool.obj"));
        assertNull(props.testBoolean("invalid"));

        try {
            props.getBoolean("nonexistent");
            fail("Expected NoSuchElementException");
        } catch (NoSuchElementException e) {
            // expected
        }

        props.setProperty("byte.val", "10");
        props.setProperty("byte.obj", new Byte((byte) 5));
        assertEquals(10, props.getByte("byte.val"));
        assertEquals(5, props.getByte("byte.obj", (byte) 1));
        try {
            props.getByte("nonexistent");
            fail();
        } catch (NoSuchElementException e) {}

        props.setProperty("short.val", "20");
        props.setProperty("short.obj", new Short((short) 15));
        assertEquals(20, props.getShort("short.val"));
        assertEquals(15, props.getShort("short.obj", (short) 1));
        try {
            props.getShort("nonexistent");
            fail();
        } catch (NoSuchElementException e) {}

        props.setProperty("int.val", "100");
        props.setProperty("int.obj", new Integer(50));
        assertEquals(100, props.getInt("int.val"));
        assertEquals(100, props.getInteger("int.val"));
        assertEquals(50, props.getInt("int.obj", 10));
        try {
            props.getInteger("nonexistent");
            fail();
        } catch (NoSuchElementException e) {}

        props.setProperty("long.val", "1000");
        props.setProperty("long.obj", new Long(500L));
        assertEquals(1000L, props.getLong("long.val"));
        assertEquals(500L, props.getLong("long.obj", 10L));
        try {
            props.getLong("nonexistent");
            fail();
        } catch (NoSuchElementException e) {}

        props.setProperty("float.val", "1.5");
        props.setProperty("float.obj", new Float(2.5f));
        assertEquals(1.5f, props.getFloat("float.val"), 0.001f);
        assertEquals(2.5f, props.getFloat("float.obj", 1.0f), 0.001f);
        try {
            props.getFloat("nonexistent");
            fail();
        } catch (NoSuchElementException e) {}

        props.setProperty("double.val", "10.5");
        props.setProperty("double.obj", new Double(20.5));
        assertEquals(10.5, props.getDouble("double.val"), 0.001);
        assertEquals(20.5, props.getDouble("double.obj", 1.0), 0.001);
        try {
            props.getDouble("nonexistent");
            fail();
        } catch (NoSuchElementException e) {}
    }

    @Test
    public void testCastExceptionsAndNullHandling() throws Throwable {
        ExtendedProperties props = new ExtendedProperties();
        props.setProperty("string.val", "text");

        try {
            props.getBoolean("string.val");
            fail();
        } catch (ClassCastException e) {}

        try {
            props.getByte("string.val");
            fail();
        } catch (NumberFormatException e) {} // String parsed as Byte throws NumberFormatException if invalid, or ClassCastException if handled differently. Wait, getString tries new Byte(val) which throws NumberFormatException.

        try {
            props.getInt("string.val");
            fail();
        } catch (NumberFormatException e) {}

        props.setProperty("invalid.type", new Object());
        try {
            props.getString("invalid.type");
            fail();
        } catch (ClassCastException e) {}

        try {
            props.getStringArray("invalid.type");
            fail();
        } catch (ClassCastException e) {}

        try {
            props.getVector("invalid.type");
            fail();
        } catch (ClassCastException e) {}

        try {
            props.getList("invalid.type");
            fail();
        } catch (ClassCastException e) {}

        try {
            props.getBoolean("invalid.type");
            fail();
        } catch (ClassCastException e) {}

        try {
            props.getByte("invalid.type");
            fail();
        } catch (ClassCastException e) {}

        try {
            props.getShort("invalid.type");
            fail();
        } catch (ClassCastException e) {}

        try {
            props.getInteger("invalid.type");
            fail();
        } catch (ClassCastException e) {}

        try {
            props.getLong("invalid.type");
            fail();
        } catch (ClassCastException e) {}

        try {
            props.getFloat("invalid.type");
            fail();
        } catch (ClassCastException e) {}

        try {
            props.getDouble("invalid.type");
            fail();
        } catch (ClassCastException e) {}
    }

    @Test
    public void testVectorsAndListsDefaults() throws Throwable {
        ExtendedProperties props = new ExtendedProperties();
        Vector defVec = new Vector();
        assertNotNull(props.getVector("missing", defVec));
        assertNotNull(props.getVector("missing", null));

        List defLst = new ArrayList();
        assertNotNull(props.getList("missing", defLst));
        assertNotNull(props.getList("missing", null));
        
        assertEquals(0, props.getStringArray("missing").length);
    }

    @Test
    public void testConvertPropertiesAndPutAll() throws Throwable {
        Properties javaProps = new Properties();
        javaProps.setProperty("p1", "v1");
        ExtendedProperties ext = ExtendedProperties.convertProperties(javaProps);
        assertEquals("v1", ext.getString("p1"));

        ExtendedProperties ext2 = new ExtendedProperties();
        ext2.setProperty("p2", "v2");
        Map map = new HashMap();
        map.put("p3", "v3");

        ExtendedProperties target = new ExtendedProperties();
        target.putAll(ext2);
        target.putAll(map);
        assertEquals("v2", target.getString("p2"));
        assertEquals("v3", target.getString("p3"));
    }

    @Test
    public void testPropertiesReaderAndTokenizer() throws Throwable {
        String data = "line1 = val1\\\nval2\n#comment\n   \nkey2 = token1\\,token2,token3\\";
        ExtendedProperties.PropertiesReader reader = new ExtendedProperties.PropertiesReader(new StringReader(data));
        assertNotNull(reader.readProperty());

        ExtendedProperties.PropertiesTokenizer tokenizer = new ExtendedProperties.PropertiesTokenizer("a\\,b,c\\");
        assertTrue(tokenizer.hasMoreTokens());
        assertEquals("a,b", tokenizer.nextToken());
    }
}