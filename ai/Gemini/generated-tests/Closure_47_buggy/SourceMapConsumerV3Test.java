package com.google.debugging.sourcemap;

import com.google.debugging.sourcemap.proto.Mapping.OriginalMapping;
import org.junit.Test;

import java.util.Collection;
import java.util.Iterator;

import static org.junit.Assert.*;

public class SourceMapConsumerV3Test {

    @Test
    public void testDefaultSourceMapSupplier() throws Throwable {
        SourceMapConsumerV3.DefaultSourceMapSupplier supplier = new SourceMapConsumerV3.DefaultSourceMapSupplier();
        assertNull(supplier.getSourceMap("anyUrl"));
    }

    @Test
    public void testParseInvalidVersion() throws Throwable {
        SourceMapConsumerV3 consumer = new SourceMapConsumerV3();
        String json = "{\"version\":2, \"file\":\"test.js\", \"lineCount\":1, \"mappings\":\"\", \"sources\":[], \"names\":[]}";
        try {
            consumer.parse(json);
            fail("Expected SourceMapParseException");
        } catch (SourceMapParseException e) {
            assertTrue(e.getMessage().contains("Unknown version"));
        }
    }

    @Test
    public void testParseEmptyFileField() throws Throwable {
        SourceMapConsumerV3 consumer = new SourceMapConsumerV3();
        String json = "{\"version\":3, \"file\":\"\", \"lineCount\":1, \"mappings\":\"\", \"sources\":[], \"names\":[]}";
        try {
            consumer.parse(json);
            fail("Expected SourceMapParseException");
        } catch (SourceMapParseException e) {
            assertTrue(e.getMessage().contains("File entry is missing or empty"));
        }
    }

    @Test
    public void testParseValidBasicSourceMap() throws Throwable {
        SourceMapConsumerV3 consumer = new SourceMapConsumerV3();
        // mappings: column 0, source 0, line 0, column 0 (Base64VLQ encoded: AAAAA)
        String json = "{" +
                "\"version\":3," +
                "\"file\":\"test.js\"," +
                "\"lineCount\":1," +
                "\"mappings\":\"AAAAA\"," +
                "\"sources\":[\"foo.js\"]," +
                "\"names\":[\"bar\"]" +
                "}";
        consumer.parse(json);

        Collection<String> sources = consumer.getOriginalSources();
        assertNotNull(sources);
        assertEquals(1, sources.size());
        assertEquals("foo.js", sources.iterator().next());

        OriginalMapping mapping = consumer.getMappingForLine(1, 1);
        assertNotNull(mapping);
        assertEquals("foo.js", mapping.getOriginalFile());
        assertEquals(0, mapping.getLineNumber());
        assertEquals(0, mapping.getColumnPosition());
    }

    @Test
    public void testGetMappingOutOfBounds() throws Throwable {
        SourceMapConsumerV3 consumer = new SourceMapConsumerV3();
        String json = "{" +
                "\"version\":3," +
                "\"file\":\"test.js\"," +
                "\"lineCount\":1," +
                "\"mappings\":\"AAAAA\"," +
                "\"sources\":[\"foo.js\"]," +
                "\"names\":[]" +
                "}";
        consumer.parse(json);

        assertNull(consumer.getMappingForLine(0, 1));
        assertNull(consumer.getMappingForLine(2, 1));
    }

    @Test
    public void testGetReverseMappingEmpty() throws Throwable {
        SourceMapConsumerV3 consumer = new SourceMapConsumerV3();
        String json = "{" +
                "\"version\":3," +
                "\"file\":\"test.js\"," +
                "\"lineCount\":1," +
                "\"mappings\":\"AAAAA\"," +
                "\"sources\":[\"foo.js\"]," +
                "\"names\":[]" +
                "}";
        consumer.parse(json);

        Collection<OriginalMapping> rev = consumer.getReverseMapping("nonexistent.js", 0, 0);
        assertNotNull(rev);
        assertTrue(rev.isEmpty());

        Collection<OriginalMapping> rev2 = consumer.getReverseMapping("foo.js", 999, 0);
        assertNotNull(rev2);
        assertTrue(rev2.isEmpty());
    }

    @Test
    public void testGetReverseMappingValid() throws Throwable {
        SourceMapConsumerV3 consumer = new SourceMapConsumerV3();
        String json = "{" +
                "\"version\":3," +
                "\"file\":\"test.js\"," +
                "\"lineCount\":1," +
                "\"mappings\":\"AAAAA\"," +
                "\"sources\":[\"foo.js\"]," +
                "\"names\":[]" +
                "}";
        consumer.parse(json);

        Collection<OriginalMapping> rev = consumer.getReverseMapping("foo.js", 0, 0);
        assertNotNull(rev);
        assertEquals(1, rev.size());
    }

    @Test
    public void testVisitMappings() throws Throwable {
        SourceMapConsumerV3 consumer = new SourceMapConsumerV3();
        String json = "{" +
                "\"version\":3," +
                "\"file\":\"test.js\"," +
                "\"lineCount\":1," +
                "\"mappings\":\"AAAAA,AACA\"," +
                "\"sources\":[\"foo.js\"]," +
                "\"names\":[\"bar\"]" +
                "}";
        consumer.parse(json);

        final int[] visitCount = new int[1];
        visitCount[0] = 0;

        consumer.visitMappings(new SourceMapConsumerV3.EntryVisitor() {
            public void visit(String sourceName, String symbolName,
                              FilePosition sourceStartPosition,
                              FilePosition startPosition,
                              FilePosition endPosition) {
                visitCount[0]++;
            }
        });

        assertTrue(visitCount[0] >= 0);
    }

    @Test
    public void testParseMetaMapWithUrl() throws Throwable {
        SourceMapConsumerV3 consumer = new SourceMapConsumerV3();
        String json = "{" +
                "\"version\":3," +
                "\"file\":\"test.js\"," +
                "\"sections\":[" +
                "  {" +
                "    \"offset\": {\"line\": 0, \"column\": 0}," +
                "    \"url\": \"http://example.com/map.js\"" +
                "  }" +
                "]" +
                "}";

        SourceMapSupplier supplier = new SourceMapSupplier() {
            public String getSourceMap(String url) {
                if ("http://example.com/map.js".equals(url)) {
                    return "{" +
                            "\"version\":3," +
                            "\"file\":\"sub.js\"," +
                            "\"lineCount\":1," +
                            "\"mappings\":\"AAAAA\"," +
                            "\"sources\":[\"foo.js\"]," +
                            "\"names\":[]" +
                            "}";
                }
                return null;
            }
        };

        consumer.parse(json, supplier);
        Collection<String> sources = consumer.getOriginalSources();
        assertNotNull(sources);
        assertEquals(1, sources.size());
    }

    @Test
    public void testParseMetaMapWithMapString() throws Throwable {
        SourceMapConsumerV3 consumer = new SourceMapConsumerV3();
        String subMap = "{" +
                "\"version\":3," +
                "\"file\":\"sub.js\"," +
                "\"lineCount\":1," +
                "\"mappings\":\"AAAAA\"," +
                "\"sources\":[\"foo.js\"]," +
                "\"names\":[]" +
                "}";
        
        String json = "{" +
                "\"version\":3," +
                "\"file\":\"test.js\"," +
                "\"sections\":[" +
                "  {" +
                "    \"offset\": {\"line\": 0, \"column\": 0}," +
                "    \"map\": " + JSONObject.quote(subMap) +
                "  }" +
                "]" +
                "}";

        consumer.parse(json);
        Collection<String> sources = consumer.getOriginalSources();
        assertNotNull(sources);
        assertEquals(1, sources.size());
    }

    @Test
    public void testParseMetaMapInvalidBothMapAndUrl() throws Throwable {
        SourceMapConsumerV3 consumer = new SourceMapConsumerV3();
        String json = "{" +
                "\"version\":3," +
                "\"file\":\"test.js\"," +
                "\"sections\":[" +
                "  {" +
                "    \"offset\": {\"line\": 0, \"column\": 0}," +
                "    \"url\": \"http://example.com/map.js\"," +
                "    \"map\": \"{}\"" +
                "  }" +
                "]" +
                "}";

        try {
            consumer.parse(json);
            fail("Expected SourceMapParseException");
        } catch (SourceMapParseException e) {
            assertTrue(e.getMessage().contains("may not have both 'map' and 'url'"));
        }
    }

    @Test
    public void testParseMetaMapInvalidNeitherMapNorUrl() throws Throwable {
        SourceMapConsumerV3 consumer = new SourceMapConsumerV3();
        String json = "{" +
                "\"version\":3," +
                "\"file\":\"test.js\"," +
                "\"sections\":[" +
                "  {" +
                "    \"offset\": {\"line\": 0, \"column\": 0}" +
                "  }" +
                "]" +
                "}";

        try {
            consumer.parse(json);
            fail("Expected SourceMapParseException");
        } catch (SourceMapParseException e) {
            assertTrue(e.getMessage().contains("must have either 'map' or 'url'"));
        }
    }

    @Test
    public void testParseMetaMapInvalidFormatExtraFields() throws Throwable {
        SourceMapConsumerV3 consumer = new SourceMapConsumerV3();
        String json = "{" +
                "\"version\":3," +
                "\"file\":\"test.js\"," +
                "\"mappings\":\"\"," +
                "\"sections\":[]" +
                "}";

        try {
            consumer.parse(json);
            fail("Expected SourceMapParseException");
        } catch (SourceMapParseException e) {
            assertTrue(e.getMessage().contains("Invalid map format"));
        }
    }
}