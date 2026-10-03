package org.jsoup.helper;

import org.junit.Test;
import static org.junit.Assert.*;

import org.jsoup.Connection;
import org.jsoup.parser.Parser;

import java.io.ByteArrayInputStream;
import java.io.InputStream;
import java.net.URL;
import java.nio.charset.IllegalCharsetNameException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public class HttpConnectionClaudeTest {

    // covers: connect(String) sets url; connect(URL) sets url
    @Test
    public void testConnectString_and_ConnectURL_setsRequestUrl() throws Throwable {
        Connection con1 = HttpConnection.connect("http://example.com/");
        assertEquals("http://example.com/", con1.request().url().toString());
        URL u = new URL("http://example.org/path");
        Connection con2 = HttpConnection.connect(u);
        assertEquals(u, con2.request().url());
    }

    // covers: url(String) encodeUrl replaces spaces with %20
    @Test
    public void testUrlString_withSpaces_encodesToPercent20() throws Throwable {
        Connection con = HttpConnection.connect("http://example.com/a b");
        assertEquals("http://example.com/a%20b", con.request().url().toString());
    }

    // covers: url(String) null -> Validate.notEmpty throws IllegalArgumentException
    @Test
    public void testUrlString_null_throwsIllegalArgumentException() throws Throwable {
        try {
            HttpConnection.connect((String) null);
            fail("expected IllegalArgumentException");
        } catch (IllegalArgumentException expected) { }
    }

    // covers: url(String) malformed -> MalformedURLException wrapped as IllegalArgumentException
    @Test
    public void testUrlString_malformed_throwsIllegalArgumentException() throws Throwable {
        try {
            HttpConnection.connect("timbuktu");
            fail("expected IllegalArgumentException");
        } catch (IllegalArgumentException expected) { }
    }

    // covers: userAgent(String) sets User-Agent header
    @Test
    public void testUserAgent_valid_setsHeader() throws Throwable {
        Connection con = HttpConnection.connect("http://example.com");
        con.userAgent("MyAgent/1.0");
        assertEquals("MyAgent/1.0", con.request().header("User-Agent"));
    }

    // covers: userAgent(null) -> Validate.notNull throws IllegalArgumentException
    @Test
    public void testUserAgent_null_throwsIllegalArgumentException() throws Throwable {
        Connection con = HttpConnection.connect("http://example.com");
        try {
            con.userAgent(null);
            fail("expected IllegalArgumentException");
        } catch (IllegalArgumentException expected) { }
    }

    // covers: timeout(millis) negative throws; zero is valid boundary
    @Test
    public void testTimeout_negativeThrows_zeroSetsBoundary() throws Throwable {
        Connection con = HttpConnection.connect("http://example.com");
        try {
            con.timeout(-1);
            fail("expected IllegalArgumentException");
        } catch (IllegalArgumentException expected) { }
        con.timeout(0);
        assertEquals(0, con.request().timeout());
    }

    // covers: maxBodySize(bytes) negative throws; zero is valid boundary
    @Test
    public void testMaxBodySize_negativeThrows_zeroSetsBoundary() throws Throwable {
        Connection con = HttpConnection.connect("http://example.com");
        try {
            con.maxBodySize(-1);
            fail("expected IllegalArgumentException");
        } catch (IllegalArgumentException expected) { }
        con.maxBodySize(0);
        assertEquals(0, con.request().maxBodySize());
    }

    // covers: followRedirects(false) sets field
    @Test
    public void testFollowRedirects_false_setsFalse() throws Throwable {
        Connection con = HttpConnection.connect("http://example.com");
        con.followRedirects(false);
        assertFalse(con.request().followRedirects());
    }

    // covers: referrer(null) throws; referrer(valid) sets Referer header
    @Test
    public void testReferrer_nullThrows_validSetsHeader() throws Throwable {
        Connection con = HttpConnection.connect("http://example.com");
        try {
            con.referrer(null);
            fail("expected IllegalArgumentException");
        } catch (IllegalArgumentException expected) { }
        con.referrer("http://ref.example.com");
        assertEquals("http://ref.example.com", con.request().header("Referer"));
    }

    // covers: method(Method) sets method; method(null) throws
    @Test
    public void testMethod_validSets_nullThrowsIllegalArgumentException() throws Throwable {
        Connection con = HttpConnection.connect("http://example.com");
        con.method(Connection.Method.POST);
        assertEquals(Connection.Method.POST, con.request().method());
        try {
            con.method(null);
            fail("expected IllegalArgumentException");
        } catch (IllegalArgumentException expected) { }
    }

    // covers: ignoreHttpErrors(true) sets flag
    @Test
    public void testIgnoreHttpErrors_true_setsTrue() throws Throwable {
        Connection con = HttpConnection.connect("http://example.com");
        con.ignoreHttpErrors(true);
        assertTrue(con.request().ignoreHttpErrors());
    }

    // covers: ignoreContentType(true) sets flag
    @Test
    public void testIgnoreContentType_true_setsTrue() throws Throwable {
        Connection con = HttpConnection.connect("http://example.com");
        con.ignoreContentType(true);
        assertTrue(con.request().ignoreContentType());
    }

    // covers: validateTLSCertificates(false) sets flag
    @Test
    public void testValidateTLSCertificates_false_setsFalse() throws Throwable {
        Connection con = HttpConnection.connect("http://example.com");
        con.validateTLSCertificates(false);
        assertFalse(con.request().validateTLSCertificates());
    }

    // covers: data(key,value) adds a KeyVal
    @Test
    public void testDataKeyValue_valid_addsKeyVal() throws Throwable {
        Connection con = HttpConnection.connect("http://example.com");
        con.data("name", "value");
        Connection.KeyVal kv = con.request().data().iterator().next();
        assertEquals("name", kv.key());
        assertEquals("value", kv.value());
    }

    // covers: data(key,filename,inputStream) adds KeyVal with stream
    @Test
    public void testDataKeyFilenameStream_valid_addsKeyValWithStream() throws Throwable {
        Connection con = HttpConnection.connect("http://example.com");
        InputStream is = new ByteArrayInputStream(new byte[] {1, 2, 3});
        con.data("file", "test.txt", is);
        Connection.KeyVal kv = con.request().data().iterator().next();
        assertEquals("test.txt", kv.value());
        assertTrue(kv.hasInputStream());
        assertSame(is, kv.inputStream());
    }

    // covers: data(Map) null -> Validate.notNull throws IllegalArgumentException
    @Test
    public void testDataMap_null_throwsIllegalArgumentException() throws Throwable {
        Connection con = HttpConnection.connect("http://example.com");
        try {
            con.data((Map<String, String>) null);
            fail("expected IllegalArgumentException");
        } catch (IllegalArgumentException expected) { }
    }

    // covers: data(Map) valid adds all entries
    @Test
    public void testDataMap_valid_addsAllEntries() throws Throwable {
        Connection con = HttpConnection.connect("http://example.com");
        Map<String, String> m = new LinkedHashMap<String, String>();
        m.put("a", "1");
        m.put("b", "2");
        con.data(m);
        assertEquals(2, con.request().data().size());
    }

    // covers: data(String...) odd length -> Validate.isTrue throws IllegalArgumentException
    @Test
    public void testDataVarargs_oddLength_throwsIllegalArgumentException() throws Throwable {
        Connection con = HttpConnection.connect("http://example.com");
        try {
            con.data("k1", "v1", "k2");
            fail("expected IllegalArgumentException");
        } catch (IllegalArgumentException expected) { }
    }

    // covers: data(String...) null -> Validate.notNull throws IllegalArgumentException
    @Test
    public void testDataVarargs_null_throwsIllegalArgumentException() throws Throwable {
        Connection con = HttpConnection.connect("http://example.com");
        try {
            con.data((String[]) null);
            fail("expected IllegalArgumentException");
        } catch (IllegalArgumentException expected) { }
    }

    // covers: data(String...) valid adds pairs in order
    @Test
    public void testDataVarargs_valid_addsPairsInOrder() throws Throwable {
        Connection con = HttpConnection.connect("http://example.com");
        con.data("k1", "v1", "k2", "v2");
        Iterator<Connection.KeyVal> it = con.request().data().iterator();
        assertEquals("k1", it.next().key());
        assertEquals("k2", it.next().key());
    }

    // covers: data(Collection) null -> Validate.notNull throws IllegalArgumentException
    @Test
    public void testDataCollection_null_throwsIllegalArgumentException() throws Throwable {
        Connection con = HttpConnection.connect("http://example.com");
        try {
            con.data((Collection<Connection.KeyVal>) null);
            fail("expected IllegalArgumentException");
        } catch (IllegalArgumentException expected) { }
    }

    // covers: header(name) lookup is case-insensitive
    @Test
    public void testHeader_caseInsensitiveLookup_returnsValue() throws Throwable {
        Connection con = HttpConnection.connect("http://example.com");
        con.header("X-Test", "abc");
        assertEquals("abc", con.request().header("x-test"));
        assertTrue(con.request().hasHeader("X-TEST"));
    }

    // covers: cookie(name,value) then cookie(name) returns value
    @Test
    public void testCookie_setAndGet_returnsValue() throws Throwable {
        Connection con = HttpConnection.connect("http://example.com");
        con.cookie("sid", "12345");
        assertEquals("12345", con.request().cookie("sid"));
    }

    // covers: cookies(Map) null -> Validate.notNull throws IllegalArgumentException
    @Test
    public void testCookiesMap_null_throwsIllegalArgumentException() throws Throwable {
        Connection con = HttpConnection.connect("http://example.com");
        try {
            con.cookies(null);
            fail("expected IllegalArgumentException");
        } catch (IllegalArgumentException expected) { }
    }

    // covers: cookies(Map) valid adds all cookies
    @Test
    public void testCookiesMap_valid_addsAllCookies() throws Throwable {
        Connection con = HttpConnection.connect("http://example.com");
        Map<String, String> m = new LinkedHashMap<String, String>();
        m.put("a", "1");
        m.put("b", "2");
        con.cookies(m);
        assertEquals("1", con.request().cookie("a"));
        assertEquals("2", con.request().cookie("b"));
    }

    // covers: parser(Parser) sets and returns the same instance
    @Test
    public void testParser_setAndGet_returnsSameInstance() throws Throwable {
        Connection con = HttpConnection.connect("http://example.com");
        Parser p = Parser.xmlParser();
        con.parser(p);
        assertSame(p, con.request().parser());
    }

    // covers: request()/response() getters non-null; request(Request)/response(Response) setters replace
    @Test
    public void testRequestAndResponseGetterSetter_replacesObjects() throws Throwable {
        Connection con = HttpConnection.connect("http://example.com");
        assertNotNull(con.request());
        assertNotNull(con.response());
        Connection.Request newReq = HttpConnection.connect("http://other.com").request();
        con.request(newReq);
        assertSame(newReq, con.request());
        Connection.Response newRes = new HttpConnection.Response();
        con.response(newRes);
        assertSame(newRes, con.response());
    }

    // covers: postDataCharset(valid) sets charset
    @Test
    public void testPostDataCharset_valid_setsCharset() throws Throwable {
        Connection con = HttpConnection.connect("http://example.com");
        con.postDataCharset("ISO-8859-1");
        assertEquals("ISO-8859-1", con.request().postDataCharset());
    }

    // covers: postDataCharset(illegal name) -> IllegalCharsetNameException
    @Test
    public void testPostDataCharset_invalidName_throwsIllegalCharsetNameException() throws Throwable {
        Connection con = HttpConnection.connect("http://example.com");
        try {
            con.postDataCharset("not a charset!!");
            fail("expected IllegalCharsetNameException");
        } catch (IllegalCharsetNameException expected) { }
    }

    // covers: postDataCharset(null) -> Validate.notNull throws IllegalArgumentException
    @Test
    public void testPostDataCharset_null_throwsIllegalArgumentException() throws Throwable {
        Connection con = HttpConnection.connect("http://example.com");
        try {
            con.postDataCharset(null);
            fail("expected IllegalArgumentException");
        } catch (IllegalArgumentException expected) { }
    }

    // covers: Base.hasHeaderWithValue is case-insensitive for default Accept-Encoding:gzip
    @Test
    public void testBaseDefaults_hasHeaderWithValueAcceptEncodingGzip() throws Throwable {
        Connection.Request req = HttpConnection.connect("http://example.com").request();
        assertTrue(req.hasHeaderWithValue("Accept-Encoding", "gzip"));
        assertTrue(req.hasHeaderWithValue("ACCEPT-ENCODING", "GZIP"));
    }

    // covers: Base.removeHeader is case-insensitive
    @Test
    public void testBaseRemoveHeader_caseInsensitive_removesHeader() throws Throwable {
        Connection.Request req = HttpConnection.connect("http://example.com").request();
        req.removeHeader("accept-encoding");
        assertFalse(req.hasHeader("Accept-Encoding"));
    }

    // covers: Base.hasCookie/removeCookie
    @Test
    public void testBaseCookie_hasAndRemoveCookie() throws Throwable {
        Connection.Request req = HttpConnection.connect("http://example.com").request();
        req.cookie("a", "1");
        assertTrue(req.hasCookie("a"));
        req.removeCookie("a");
        assertFalse(req.hasCookie("a"));
    }

    // covers: Request default field values from constructor
    @Test
    public void testRequestDefaults_timeoutMaxBodyFollowRedirectsMethod() throws Throwable {
        Connection.Request req = HttpConnection.connect("http://example.com").request();
        assertEquals(3000, req.timeout());
        assertEquals(1024 * 1024, req.maxBodySize());
        assertTrue(req.followRedirects());
        assertEquals(Connection.Method.GET, req.method());
        assertFalse(req.ignoreHttpErrors());
        assertNotNull(req.parser());
    }

    // covers: processResponseHeaders combines multiple values of the same header with a comma (RFC 2616 sec 4.2)
    @Test
    public void testProcessResponseHeaders_multipleValues_combinedWithComma() throws Throwable {
        HttpConnection.Response res = new HttpConnection.Response();
        Map<String, List<String>> headers = new LinkedHashMap<String, List<String>>();
        List<String> vals = new ArrayList<String>();
        vals.add("no-cache");
        vals.add("no-store");
        headers.put("Cache-Control", vals);
        res.processResponseHeaders(headers);
        assertEquals("no-cache, no-store", res.header("Cache-Control"));
    }

    // covers: processResponseHeaders parses Set-Cookie name/value pair
    @Test
    public void testProcessResponseHeaders_setCookie_parsesNameAndValue() throws Throwable {
        HttpConnection.Response res = new HttpConnection.Response();
        Map<String, List<String>> headers = new LinkedHashMap<String, List<String>>();
        List<String> vals = new ArrayList<String>();
        vals.add("sid=abc123; Path=/; HttpOnly");
        headers.put("Set-Cookie", vals);
        res.processResponseHeaders(headers);
        assertEquals("abc123", res.cookie("sid"));
    }

    // covers: processResponseHeaders skips null header name (http status line) without error
    @Test
    public void testProcessResponseHeaders_nullKey_skippedWithoutError() throws Throwable {
        HttpConnection.Response res = new HttpConnection.Response();
        Map<String, List<String>> headers = new LinkedHashMap<String, List<String>>();
        List<String> vals = new ArrayList<String>();
        vals.add("HTTP/1.1 200 OK");
        headers.put(null, vals);
        res.processResponseHeaders(headers);
        assertEquals(0, res.headers().size());
    }

    // covers: processResponseHeaders skips header with empty values list
    @Test
    public void testProcessResponseHeaders_emptyValues_notAdded() throws Throwable {
        HttpConnection.Response res = new HttpConnection.Response();
        Map<String, List<String>> headers = new LinkedHashMap<String, List<String>>();
        headers.put("X-Empty", new ArrayList<String>());
        res.processResponseHeaders(headers);
        assertFalse(res.hasHeader("X-Empty"));
    }

    // covers: KeyVal.create(key,value), key(), value(), toString()
    @Test
    public void testKeyValCreate_keyValue_toStringFormat() throws Throwable {
        Connection.KeyVal kv = HttpConnection.KeyVal.create("name", "value");
        assertEquals("name", kv.key());
        assertEquals("value", kv.value());
        assertEquals("name=value", kv.toString());
    }

    // covers: KeyVal.create(key,filename,stream) hasInputStream true and same stream reference
    @Test
    public void testKeyValCreateWithStream_hasInputStreamTrueAndSameStream() throws Throwable {
        InputStream is = new ByteArrayInputStream(new byte[] {9});
        Connection.KeyVal kv = HttpConnection.KeyVal.create("f", "name.bin", is);
        assertTrue(kv.hasInputStream());
        assertSame(is, kv.inputStream());
    }
}
