package org.jsoup;

import org.junit.Test;
import static org.junit.Assert.*;

import org.jsoup.nodes.Document;
import org.jsoup.parser.Parser;
import org.jsoup.safety.Whitelist;

import java.io.File;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.URL;

public class JsoupTest {

    @Test
    public void testParseStringAndBaseUri() throws Throwable {
        String html = "<html><head><title>Test</title></head><body><p>Hello</p></body></html>";
        String baseUri = "http://example.com";
        Document doc = Jsoup.parse(html, baseUri);
        assertNotNull(doc);
        assertEquals("Test", doc.title());
        assertEquals("Hello", doc.select("p").text());
        assertEquals("http://example.com/", doc.baseUri());
    }

    @Test
    public void testParseStringBaseUriAndParser() throws Throwable {
        String html = "<root><child>Text</child></root>";
        String baseUri = "";
        Parser parser = Parser.xmlParser();
        Document doc = Jsoup.parse(html, baseUri, parser);
        assertNotNull(doc);
        assertEquals("Text", doc.select("child").text());
    }

    @Test
    public void testParseStringOnly() throws Throwable {
        String html = "<div>Simple HTML</div>";
        Document doc = Jsoup.parse(html);
        assertNotNull(doc);
        assertEquals("Simple HTML", doc.select("div").text());
    }

    @Test
    public void testConnect() throws Throwable {
        Connection conn = Jsoup.connect("http://example.com");
        assertNotNull(conn);
    }

    @Test
    public void testParseFileWithCharsetAndBaseUri() throws Throwable {
        File tempFile = File.createTempFile("jsoup-test", ".html");
        tempFile.deleteOnExit();
        
        java.io.FileWriter writer = new java.io.FileWriter(tempFile);
        writer.write("<html><body>File Content</body></html>");
        writer.close();

        Document doc = Jsoup.parse(tempFile, "UTF-8", "http://example.com");
        assertNotNull(doc);
        assertEquals("File Content", doc.select("body").text());
    }

    @Test
    public void testParseFileWithCharsetOnly() throws Throwable {
        File tempFile = File.createTempFile("jsoup-test", ".html");
        tempFile.deleteOnExit();
        
        java.io.FileWriter writer = new java.io.FileWriter(tempFile);
        writer.write("<html><body>File Content 2</body></html>");
        writer.close();

        Document doc = Jsoup.parse(tempFile, "UTF-8");
        assertNotNull(doc);
        assertEquals("File Content 2", doc.select("body").text());
    }

    @Test
    public void testParseInputStreamCharsetAndBaseUri() throws Throwable {
        String content = "<html><body>Stream Content</body></html>";
        InputStream in = new ByteArrayInputStream(content.getBytes("UTF-8"));
        
        Document doc = Jsoup.parse(in, "UTF-8", "http://example.com");
        assertNotNull(doc);
        assertEquals("Stream Content", doc.select("body").text());
    }

    @Test
    public void testParseInputStreamCharsetBaseUriAndParser() throws Throwable {
        String content = "<root>Stream XML</root>";
        InputStream in = new ByteArrayInputStream(content.getBytes("UTF-8"));
        Parser parser = Parser.xmlParser();
        
        Document doc = Jsoup.parse(in, "UTF-8", "", parser);
        assertNotNull(doc);
        assertEquals("Stream XML", doc.select("root").text());
    }

    @Test
    public void testParseBodyFragmentAndBaseUri() throws Throwable {
        String bodyHtml = "<p>Fragment body</p>";
        String baseUri = "http://example.com";
        Document doc = Jsoup.parseBodyFragment(bodyHtml, baseUri);
        assertNotNull(doc);
        assertEquals("Fragment body", doc.body().text());
    }

    @Test
    public void testParseBodyFragmentOnly() throws Throwable {
        String bodyHtml = "<p>Fragment body only</p>";
        Document doc = Jsoup.parseBodyFragment(bodyHtml);
        assertNotNull(doc);
        assertEquals("Fragment body only", doc.body().text());
    }

    @Test
    public void testCleanWithWhitelist() throws Throwable {
        String bodyHtml = "<p>Hello <script>alert(1);</script>World</p>";
        Whitelist whitelist = Whitelist.basic();
        String cleaned = Jsoup.clean(bodyHtml, whitelist);
        assertNotNull(cleaned);
        assertFalse(cleaned.contains("script"));
        assertTrue(cleaned.contains("Hello"));
        assertTrue(cleaned.contains("World"));
    }

    @Test
    public void testCleanWithBaseUriAndWhitelist() throws Throwable {
        String bodyHtml = "<a href='http://example.com'>Link</a><script>bad()</script>";
        String baseUri = "http://example.com";
        Whitelist whitelist = Whitelist.basic();
        String cleaned = Jsoup.clean(bodyHtml, baseUri, whitelist);
        assertNotNull(cleaned);
        assertFalse(cleaned.contains("script"));
        assertTrue(cleaned.contains("Link"));
    }

    @Test
    public void testCleanWithOutputSettings() throws Throwable {
        String bodyHtml = "<p>Output settings test</p>";
        String baseUri = "http://example.com";
        Whitelist whitelist = Whitelist.basic();
        Document.OutputSettings outputSettings = new Document.OutputSettings();
        
        String cleaned = Jsoup.clean(bodyHtml, baseUri, whitelist, outputSettings);
        assertNotNull(cleaned);
        assertTrue(cleaned.contains("Output settings test"));
    }

    @Test
    public void testIsValid() throws Throwable {
        String validHtml = "<p>This is <b>safe</b> text.</p>";
        String invalidHtml = "<p>This is <script>alert(1);</script> unsafe.</p>";
        Whitelist whitelist = Whitelist.basic();

        assertTrue(Jsoup.isValid(validHtml, whitelist));
        assertFalse(Jsoup.isValid(invalidHtml, whitelist));
    }

    @Test
    public void testParseUrlTimeout() throws Throwable {
        boolean exceptionThrown = false;
        try {
            URL url = new URL("http://invalid-url-that-does-not-exist-jsoup.com");
            Jsoup.parse(url, 1000);
        } catch (IOException e) {
            exceptionThrown = true;
        }
        assertTrue(exceptionThrown);
    }
}