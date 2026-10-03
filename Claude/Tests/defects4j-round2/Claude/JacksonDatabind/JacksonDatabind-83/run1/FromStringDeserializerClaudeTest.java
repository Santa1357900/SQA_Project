package com.fasterxml.jackson.databind.deser.std;

import java.io.File;
import java.io.IOException;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.URI;
import java.net.URL;
import java.nio.charset.Charset;
import java.util.Arrays;
import java.util.Currency;
import java.util.List;
import java.util.Locale;
import java.util.TimeZone;
import java.util.regex.Pattern;

import org.junit.Test;
import static org.junit.Assert.*;

import com.fasterxml.jackson.databind.DeserializationContext;
import com.fasterxml.jackson.databind.JavaType;
import com.fasterxml.jackson.databind.JsonMappingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.annotation.JsonDeserialize;

public class FromStringDeserializerClaudeTest
{
    public static class Holder {
        private File file;
        private URL url;
        private URI uri;
        private Class<?> clazz;
        private Currency currency;
        private Pattern pattern;
        private Locale locale;
        private Charset charset;
        private TimeZone timeZone;
        private InetSocketAddress inetSocketAddress;
        private StringBuilder stringBuilder;

        public File getFile() { return file; }
        public void setFile(File file) { this.file = file; }
        public URL getUrl() { return url; }
        public void setUrl(URL url) { this.url = url; }
        public URI getUri() { return uri; }
        public void setUri(URI uri) { this.uri = uri; }
        public Class<?> getClazz() { return clazz; }
        public void setClazz(Class<?> clazz) { this.clazz = clazz; }
        public Currency getCurrency() { return currency; }
        public void setCurrency(Currency currency) { this.currency = currency; }
        public Pattern getPattern() { return pattern; }
        public void setPattern(Pattern pattern) { this.pattern = pattern; }
        public Locale getLocale() { return locale; }
        public void setLocale(Locale locale) { this.locale = locale; }
        public Charset getCharset() { return charset; }
        public void setCharset(Charset charset) { this.charset = charset; }
        public TimeZone getTimeZone() { return timeZone; }
        public void setTimeZone(TimeZone timeZone) { this.timeZone = timeZone; }
        public InetSocketAddress getInetSocketAddress() { return inetSocketAddress; }
        public void setInetSocketAddress(InetSocketAddress a) { this.inetSocketAddress = a; }
        public StringBuilder getStringBuilder() { return stringBuilder; }
        public void setStringBuilder(StringBuilder sb) { this.stringBuilder = sb; }
    }

    public static class NullReturningDeserializer extends FromStringDeserializer<String> {
        public NullReturningDeserializer() {
            super(String.class);
        }
        @Override
        protected String _deserialize(String value, DeserializationContext ctxt) throws IOException {
            return null;
        }
    }

    public static class NullDeserHolder {
        private String value;
        public String getValue() { return value; }
        @JsonDeserialize(using = NullReturningDeserializer.class)
        public void setValue(String value) { this.value = value; }
    }

    // covers types(): must return exactly the 13 documented supported classes
    @Test
    public void testTypes_returnsAllSupportedClasses() throws Throwable {
        Class<?>[] types = FromStringDeserializer.types();
        assertEquals(13, types.length);
        List<Class<?>> list = Arrays.asList(types);
        assertTrue(list.contains(File.class));
        assertTrue(list.contains(URL.class));
        assertTrue(list.contains(URI.class));
        assertTrue(list.contains(Currency.class));
        assertTrue(list.contains(Pattern.class));
        assertTrue(list.contains(Locale.class));
        assertTrue(list.contains(Charset.class));
        assertTrue(list.contains(TimeZone.class));
        assertTrue(list.contains(InetSocketAddress.class));
        assertTrue(list.contains(StringBuilder.class));
    }

    // covers findDeserializer(): File branch -> STD_FILE kind
    @Test
    public void testFindDeserializer_file_returnsStdWithFileKind() throws Throwable {
        FromStringDeserializer.Std d = FromStringDeserializer.findDeserializer(File.class);
        assertNotNull(d);
        assertEquals(FromStringDeserializer.Std.STD_FILE, d._kind);
    }

    // covers findDeserializer(): URL branch -> STD_URL kind
    @Test
    public void testFindDeserializer_url_returnsStdWithUrlKind() throws Throwable {
        FromStringDeserializer.Std d = FromStringDeserializer.findDeserializer(URL.class);
        assertEquals(FromStringDeserializer.Std.STD_URL, d._kind);
    }

    // covers findDeserializer(): URI branch -> STD_URI kind
    @Test
    public void testFindDeserializer_uri_returnsStdWithUriKind() throws Throwable {
        FromStringDeserializer.Std d = FromStringDeserializer.findDeserializer(URI.class);
        assertEquals(FromStringDeserializer.Std.STD_URI, d._kind);
    }

    // covers findDeserializer(): Class branch -> STD_CLASS kind
    @Test
    public void testFindDeserializer_classType_returnsStdWithClassKind() throws Throwable {
        FromStringDeserializer.Std d = FromStringDeserializer.findDeserializer(Class.class);
        assertEquals(FromStringDeserializer.Std.STD_CLASS, d._kind);
    }

    // covers findDeserializer(): JavaType branch -> STD_JAVA_TYPE kind
    @Test
    public void testFindDeserializer_javaType_returnsStdWithJavaTypeKind() throws Throwable {
        FromStringDeserializer.Std d = FromStringDeserializer.findDeserializer(JavaType.class);
        assertEquals(FromStringDeserializer.Std.STD_JAVA_TYPE, d._kind);
    }

    // covers findDeserializer(): Currency branch -> STD_CURRENCY kind
    @Test
    public void testFindDeserializer_currency_returnsStdWithCurrencyKind() throws Throwable {
        FromStringDeserializer.Std d = FromStringDeserializer.findDeserializer(Currency.class);
        assertEquals(FromStringDeserializer.Std.STD_CURRENCY, d._kind);
    }

    // covers findDeserializer(): Pattern branch -> STD_PATTERN kind
    @Test
    public void testFindDeserializer_pattern_returnsStdWithPatternKind() throws Throwable {
        FromStringDeserializer.Std d = FromStringDeserializer.findDeserializer(Pattern.class);
        assertEquals(FromStringDeserializer.Std.STD_PATTERN, d._kind);
    }

    // covers findDeserializer(): Locale branch -> STD_LOCALE kind
    @Test
    public void testFindDeserializer_locale_returnsStdWithLocaleKind() throws Throwable {
        FromStringDeserializer.Std d = FromStringDeserializer.findDeserializer(Locale.class);
        assertEquals(FromStringDeserializer.Std.STD_LOCALE, d._kind);
    }

    // covers findDeserializer(): Charset branch -> STD_CHARSET kind
    @Test
    public void testFindDeserializer_charset_returnsStdWithCharsetKind() throws Throwable {
        FromStringDeserializer.Std d = FromStringDeserializer.findDeserializer(Charset.class);
        assertEquals(FromStringDeserializer.Std.STD_CHARSET, d._kind);
    }

    // covers findDeserializer(): TimeZone branch -> STD_TIME_ZONE kind
    @Test
    public void testFindDeserializer_timeZone_returnsStdWithTimeZoneKind() throws Throwable {
        FromStringDeserializer.Std d = FromStringDeserializer.findDeserializer(TimeZone.class);
        assertEquals(FromStringDeserializer.Std.STD_TIME_ZONE, d._kind);
    }

    // covers findDeserializer(): InetAddress branch -> STD_INET_ADDRESS kind
    @Test
    public void testFindDeserializer_inetAddress_returnsStdWithInetAddressKind() throws Throwable {
        FromStringDeserializer.Std d = FromStringDeserializer.findDeserializer(InetAddress.class);
        assertEquals(FromStringDeserializer.Std.STD_INET_ADDRESS, d._kind);
    }

    // covers findDeserializer(): InetSocketAddress branch -> STD_INET_SOCKET_ADDRESS kind
    @Test
    public void testFindDeserializer_inetSocketAddress_returnsStdWithKind() throws Throwable {
        FromStringDeserializer.Std d = FromStringDeserializer.findDeserializer(InetSocketAddress.class);
        assertEquals(FromStringDeserializer.Std.STD_INET_SOCKET_ADDRESS, d._kind);
    }

    // covers findDeserializer(): StringBuilder branch -> STD_STRING_BUILDER kind
    @Test
    public void testFindDeserializer_stringBuilder_returnsStdWithKind() throws Throwable {
        FromStringDeserializer.Std d = FromStringDeserializer.findDeserializer(StringBuilder.class);
        assertEquals(FromStringDeserializer.Std.STD_STRING_BUILDER, d._kind);
    }

    // covers findDeserializer(): else branch -> unsupported type returns null
    @Test
    public void testFindDeserializer_unsupportedType_returnsNull() throws Throwable {
        FromStringDeserializer.Std d = FromStringDeserializer.findDeserializer(Integer.class);
        assertNull(d);
    }

    // BUG TEST: covers deserialize() null-handling branch; per javadoc comment a
    // legitimate null result from _deserialize must be returned as null, not error
    @Test
    public void testDeserialize_customDeserializerReturnsNull_shouldReturnNullNotThrow() throws Throwable {
        ObjectMapper mapper = new ObjectMapper();
        NullDeserHolder holder = mapper.readValue("{\"value\":\"abc\"}", NullDeserHolder.class);
        assertNull(holder.getValue());
    }

    // covers Std._deserialize STD_FILE branch: new File(value)
    @Test
    public void testDeserialize_file_validPath() throws Throwable {
        ObjectMapper mapper = new ObjectMapper();
        Holder h = mapper.readValue("{\"file\":\"/tmp/test.txt\"}", Holder.class);
        assertEquals(new File("/tmp/test.txt"), h.getFile());
    }

    // covers Std._deserialize STD_URL branch: valid URL string
    @Test
    public void testDeserialize_url_valid() throws Throwable {
        ObjectMapper mapper = new ObjectMapper();
        Holder h = mapper.readValue("{\"url\":\"http://example.com\"}", Holder.class);
        assertEquals("http://example.com", h.getUrl().toString());
    }

    // covers Std._deserialize STD_URL branch: MalformedURLException -> wrapped exception
    @Test
    public void testDeserialize_url_invalid_throws() throws Throwable {
        ObjectMapper mapper = new ObjectMapper();
        try {
            mapper.readValue("{\"url\":\"abc\"}", Holder.class);
            fail("expected JsonMappingException");
        } catch (JsonMappingException expected) { }
    }

    // covers Std._deserialize STD_URI branch: valid URI string
    @Test
    public void testDeserialize_uri_valid() throws Throwable {
        ObjectMapper mapper = new ObjectMapper();
        Holder h = mapper.readValue("{\"uri\":\"http://example.com/path\"}", Holder.class);
        assertEquals("http://example.com/path", h.getUri().toString());
    }

    // covers Std._deserialize STD_URI branch: IllegalArgumentException -> wrapped exception
    @Test
    public void testDeserialize_uri_invalid_throws() throws Throwable {
        ObjectMapper mapper = new ObjectMapper();
        try {
            mapper.readValue("{\"uri\":\"%\"}", Holder.class);
            fail("expected JsonMappingException");
        } catch (JsonMappingException expected) { }
    }

    // covers Std._deserialize STD_CLASS branch: ctxt.findClass success
    @Test
    public void testDeserialize_class_valid() throws Throwable {
        ObjectMapper mapper = new ObjectMapper();
        Holder h = mapper.readValue("{\"clazz\":\"java.lang.String\"}", Holder.class);
        assertEquals(String.class, h.getClazz());
    }

    // covers Std._deserialize STD_CLASS branch: unknown class -> handleInstantiationProblem throws
    @Test
    public void testDeserialize_class_invalid_throws() throws Throwable {
        ObjectMapper mapper = new ObjectMapper();
        try {
            mapper.readValue("{\"clazz\":\"com.example.NoSuchClassXYZ123\"}", Holder.class);
            fail("expected JsonMappingException");
        } catch (JsonMappingException expected) { }
    }

    // covers Std._deserialize STD_CURRENCY branch: valid ISO 4217 code
    @Test
    public void testDeserialize_currency_valid() throws Throwable {
        ObjectMapper mapper = new ObjectMapper();
        Holder h = mapper.readValue("{\"currency\":\"USD\"}", Holder.class);
        assertEquals("USD", h.getCurrency().getCurrencyCode());
    }

    // covers Std._deserialize STD_CURRENCY branch: invalid code -> IllegalArgumentException wrapped
    @Test
    public void testDeserialize_currency_invalid_throws() throws Throwable {
        ObjectMapper mapper = new ObjectMapper();
        try {
            mapper.readValue("{\"currency\":\"AAA\"}", Holder.class);
            fail("expected JsonMappingException");
        } catch (JsonMappingException expected) { }
    }

    // covers Std._deserialize STD_PATTERN branch: valid regex compiles
    @Test
    public void testDeserialize_pattern_valid() throws Throwable {
        ObjectMapper mapper = new ObjectMapper();
        Holder h = mapper.readValue("{\"pattern\":\"^[a-z]+$\"}", Holder.class);
        assertEquals("^[a-z]+$", h.getPattern().pattern());
    }

    // covers Std._deserialize STD_PATTERN branch: PatternSyntaxException wrapped
    @Test
    public void testDeserialize_pattern_invalid_throws() throws Throwable {
        ObjectMapper mapper = new ObjectMapper();
        try {
            mapper.readValue("{\"pattern\":\"(\"}", Holder.class);
            fail("expected JsonMappingException");
        } catch (JsonMappingException expected) { }
    }

    // covers Std._deserialize STD_LOCALE branch: single-part value (no separator)
    @Test
    public void testDeserialize_locale_singlePart() throws Throwable {
        ObjectMapper mapper = new ObjectMapper();
        Holder h = mapper.readValue("{\"locale\":\"fr\"}", Holder.class);
        assertEquals("fr", h.getLocale().getLanguage());
        assertEquals("", h.getLocale().getCountry());
    }

    // covers Std._deserialize STD_LOCALE branch: two-part value (language-country)
    @Test
    public void testDeserialize_locale_twoParts() throws Throwable {
        ObjectMapper mapper = new ObjectMapper();
        Holder h = mapper.readValue("{\"locale\":\"en-US\"}", Holder.class);
        assertEquals("en", h.getLocale().getLanguage());
        assertEquals("US", h.getLocale().getCountry());
    }

    // covers Std._deserialize STD_LOCALE branch: three-part value (language-country-variant)
    @Test
    public void testDeserialize_locale_threeParts() throws Throwable {
        ObjectMapper mapper = new ObjectMapper();
        Holder h = mapper.readValue("{\"locale\":\"no-NO-NY\"}", Holder.class);
        assertEquals("no", h.getLocale().getLanguage());
        assertEquals("NO", h.getLocale().getCountry());
        assertEquals("NY", h.getLocale().getVariant());
    }

    // covers Std._deserialize STD_CHARSET branch: valid charset name
    @Test
    public void testDeserialize_charset_valid() throws Throwable {
        ObjectMapper mapper = new ObjectMapper();
        Holder h = mapper.readValue("{\"charset\":\"UTF-8\"}", Holder.class);
        assertEquals("UTF-8", h.getCharset().name());
    }

    // covers Std._deserialize STD_CHARSET branch: invalid charset name -> IllegalArgumentException wrapped
    @Test
    public void testDeserialize_charset_invalid_throws() throws Throwable {
        ObjectMapper mapper = new ObjectMapper();
        try {
            mapper.readValue("{\"charset\":\"bad charset name\"}", Holder.class);
            fail("expected JsonMappingException");
        } catch (JsonMappingException expected) { }
    }

    // covers Std._deserialize STD_TIME_ZONE branch: unrecognized id falls back to GMT per contract
    @Test
    public void testDeserialize_timeZone_unknownId_returnsGMT() throws Throwable {
        ObjectMapper mapper = new ObjectMapper();
        Holder h = mapper.readValue("{\"timeZone\":\"Not/AZone\"}", Holder.class);
        assertEquals("GMT", h.getTimeZone().getID());
    }

    // covers Std._deserialize STD_INET_SOCKET_ADDRESS branch: host:port single colon
    @Test
    public void testDeserialize_inetSocketAddress_hostPort() throws Throwable {
        ObjectMapper mapper = new ObjectMapper();
        Holder h = mapper.readValue("{\"inetSocketAddress\":\"127.0.0.1:8080\"}", Holder.class);
        assertEquals(8080, h.getInetSocketAddress().getPort());
        assertEquals("127.0.0.1", h.getInetSocketAddress().getHostString());
    }

    // covers Std._deserialize STD_INET_SOCKET_ADDRESS branch: bracketed IPv6 with port
    @Test
    public void testDeserialize_inetSocketAddress_bracketedIPv6() throws Throwable {
        ObjectMapper mapper = new ObjectMapper();
        Holder h = mapper.readValue("{\"inetSocketAddress\":\"[::1]:9090\"}", Holder.class);
        assertEquals(9090, h.getInetSocketAddress().getPort());
    }

    // covers Std._deserialize STD_INET_SOCKET_ADDRESS branch: host only, no port -> port 0
    @Test
    public void testDeserialize_inetSocketAddress_hostOnly() throws Throwable {
        ObjectMapper mapper = new ObjectMapper();
        Holder h = mapper.readValue("{\"inetSocketAddress\":\"127.0.0.1\"}", Holder.class);
        assertEquals(0, h.getInetSocketAddress().getPort());
        assertEquals("127.0.0.1", h.getInetSocketAddress().getHostString());
    }

    // covers Std._deserialize STD_STRING_BUILDER branch: plain string wrapped
    @Test
    public void testDeserialize_stringBuilder_valid() throws Throwable {
        ObjectMapper mapper = new ObjectMapper();
        Holder h = mapper.readValue("{\"stringBuilder\":\"hello\"}", Holder.class);
        assertEquals("hello", h.getStringBuilder().toString());
    }

    // covers Std._deserializeFromEmptyString(): URI special-case returns empty URI, not null
    @Test
    public void testDeserializeFromEmptyString_uri_returnsEmptyUri() throws Throwable {
        FromStringDeserializer.Std d = FromStringDeserializer.findDeserializer(URI.class);
        Object result = d._deserializeFromEmptyString();
        assertEquals(URI.create(""), result);
    }

    // covers Std._deserializeFromEmptyString(): Locale special-case returns Locale.ROOT
    @Test
    public void testDeserializeFromEmptyString_locale_returnsRoot() throws Throwable {
        FromStringDeserializer.Std d = FromStringDeserializer.findDeserializer(Locale.class);
        Object result = d._deserializeFromEmptyString();
        assertEquals(Locale.ROOT, result);
    }

    // covers Std._deserializeFromEmptyString(): StringBuilder special-case returns empty instance
    @Test
    public void testDeserializeFromEmptyString_stringBuilder_returnsEmpty() throws Throwable {
        FromStringDeserializer.Std d = FromStringDeserializer.findDeserializer(StringBuilder.class);
        Object result = d._deserializeFromEmptyString();
        assertEquals(0, ((StringBuilder) result).length());
    }

    // covers Std._deserializeFromEmptyString(): default branch delegates to super -> null
    @Test
    public void testDeserializeFromEmptyString_default_returnsNull() throws Throwable {
        FromStringDeserializer.Std d = FromStringDeserializer.findDeserializer(File.class);
        assertNull(d._deserializeFromEmptyString());
    }

    // covers Std._firstHyphenOrUnderscore(): hyphen present -> returns its index
    @Test
    public void testFirstHyphenOrUnderscore_hyphen_returnsIndex() throws Throwable {
        FromStringDeserializer.Std d = FromStringDeserializer.findDeserializer(Locale.class);
        assertEquals(2, d._firstHyphenOrUnderscore("en-US"));
    }

    // covers Std._firstHyphenOrUnderscore(): underscore present -> returns its index
    @Test
    public void testFirstHyphenOrUnderscore_underscore_returnsIndex() throws Throwable {
        FromStringDeserializer.Std d = FromStringDeserializer.findDeserializer(Locale.class);
        assertEquals(2, d._firstHyphenOrUnderscore("en_US"));
    }

    // covers Std._firstHyphenOrUnderscore(): neither present -> returns -1
    @Test
    public void testFirstHyphenOrUnderscore_none_returnsMinusOne() throws Throwable {
        FromStringDeserializer.Std d = FromStringDeserializer.findDeserializer(Locale.class);
        assertEquals(-1, d._firstHyphenOrUnderscore("english"));
    }
}
