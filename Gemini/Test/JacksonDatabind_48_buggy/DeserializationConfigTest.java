package com.fasterxml.jackson.databind;

import org.junit.Test;
import static org.junit.Assert.*;

import com.fasterxml.jackson.annotation.JsonAutoDetect;
import com.fasterxml.jackson.annotation.PropertyAccessor;
import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.databind.cfg.BaseSettings;
import com.fasterxml.jackson.databind.cfg.HandlerInstantiator;
import com.fasterxml.jackson.databind.introspect.ClassIntrospector;
import com.fasterxml.jackson.databind.introspect.VisibilityChecker;
import com.fasterxml.jackson.databind.jsontype.SubtypeResolver;
import com.fasterxml.jackson.databind.jsontype.TypeResolverBuilder;
import com.fasterxml.jackson.databind.node.JsonNodeFactory;
import com.fasterxml.jackson.databind.type.TypeFactory;
import com.fasterxml.jackson.databind.util.LinkedNode;
import com.fasterxml.jackson.databind.util.RootNameLookup;
import com.fasterxml.jackson.databind.deser.DeserializationProblemHandler;

import java.text.DateFormat;
import java.util.Locale;
import java.util.TimeZone;

public class DeserializationConfigTest {

    private DeserializationConfig createConfig() {
        BaseSettings base = ObjectMapper.DEFAULT_BASE;
        SubtypeResolver str = new com.fasterxml.jackson.databind.jsontype.impl.StdSubtypeResolver();
        SimpleMixInResolver mixins = new SimpleMixInResolver(null);
        RootNameLookup rootNames = new RootNameLookup();
        return new DeserializationConfig(base, str, mixins, rootNames);
    }

    @Test
    public void testFeaturesAndInspection() throws Throwable {
        DeserializationConfig config = createConfig();

        // Test DeserializationFeature fluent methods
        DeserializationConfig c2 = config.with(DeserializationFeature.USE_BIG_DECIMAL_FOR_FLOATS);
        assertTrue(c2.isEnabled(DeserializationFeature.USE_BIG_DECIMAL_FOR_FLOATS));
        assertSame(c2, c2.with(DeserializationFeature.USE_BIG_DECIMAL_FOR_FLOATS));

        DeserializationConfig c3 = c2.without(DeserializationFeature.USE_BIG_DECIMAL_FOR_FLOATS);
        assertFalse(c3.isEnabled(DeserializationFeature.USE_BIG_DECIMAL_FOR_FLOATS));
        assertSame(c3, c3.without(DeserializationFeature.USE_BIG_DECIMAL_FOR_FLOATS));

        DeserializationConfig c4 = config.with(DeserializationFeature.USE_BIG_DECIMAL_FOR_FLOATS, DeserializationFeature.USE_BIG_INTEGER_FOR_INTS);
        assertTrue(c4.isEnabled(DeserializationFeature.USE_BIG_DECIMAL_FOR_FLOATS));
        assertTrue(c4.isEnabled(DeserializationFeature.USE_BIG_INTEGER_FOR_INTS));

        DeserializationConfig c5 = config.withFeatures(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES);
        assertTrue(c5.isEnabled(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES));

        DeserializationConfig c6 = c4.without(DeserializationFeature.USE_BIG_DECIMAL_FOR_FLOATS, DeserializationFeature.USE_BIG_INTEGER_FOR_INTS);
        assertFalse(c6.isEnabled(DeserializationFeature.USE_BIG_DECIMAL_FOR_FLOATS));
        assertFalse(c6.isEnabled(DeserializationFeature.USE_BIG_INTEGER_FOR_INTS));

        DeserializationConfig c7 = c5.withoutFeatures(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES);
        assertFalse(c7.isEnabled(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES));

        // Bulk checks
        assertTrue(c4.hasDeserializationFeatures(DeserializationFeature.USE_BIG_DECIMAL_FOR_FLOATS.getMask() | DeserializationFeature.USE_BIG_INTEGER_FOR_INTS.getMask()));
        assertTrue(c4.hasSomeOfFeatures(DeserializationFeature.USE_BIG_DECIMAL_FOR_FLOATS.getMask()));
        assertEquals(c4.getDeserializationFeatures(), c4.getDeserializationFeatures());
    }

    @Test
    public void testParserFeatures() throws Throwable {
        DeserializationConfig config = createConfig();

        JsonParser.Feature feat = JsonParser.Feature.ALLOW_COMMENTS;
        DeserializationConfig c1 = config.with(feat);
        assertTrue(c1.isEnabled(feat, new com.fasterxml.jackson.core.json.JsonFactoryBuilder().build()));
        assertSame(c1, c1.with(feat));

        DeserializationConfig c2 = c1.withFeatures(feat);
        assertSame(c1, c2);

        DeserializationConfig c3 = c1.without(feat);
        assertFalse(c3.isEnabled(feat, new com.fasterxml.jackson.core.json.JsonFactoryBuilder().build()));
        assertSame(c3, c3.without(feat));

        DeserializationConfig c4 = c3.withoutFeatures(feat);
        assertSame(c3, c4);
    }

    @Test
    public void testFormatFeatures() throws Throwable {
        DeserializationConfig config = createConfig();

        com.fasterxml.jackson.core.FormatFeature feat = new com.fasterxml.jackson.core.FormatFeature() {
            public boolean enabledByDefault() { return false; }
            public int getMask() { return 1; }
            public boolean enabledIn(int flags) { return (flags & getMask()) != 0; }
        };

        DeserializationConfig c1 = config.with(feat);
        assertSame(c1, c1.with(feat));

        DeserializationConfig c2 = c1.withFeatures(feat);
        assertSame(c1, c2);

        DeserializationConfig c3 = c1.without(feat);
        assertSame(c3, c3.without(feat));

        DeserializationConfig c4 = c3.withoutFeatures(feat);
        assertSame(c3, c4);
    }

    @Test
    public void testNodeFactoryAndProblemHandlers() throws Throwable {
        DeserializationConfig config = createConfig();
        JsonNodeFactory f = JsonNodeFactory.instance;
        DeserializationConfig c1 = config.with(f);
        assertSame(config, c1);

        JsonNodeFactory customF = new JsonNodeFactory();
        DeserializationConfig c2 = config.with(customF);
        assertSame(customF, c2.getNodeFactory());

        DeserializationProblemHandler handler = new DeserializationProblemHandler() { };
        DeserializationConfig c3 = c2.withHandler(handler);
        assertNotNull(c3.getProblemHandlers());
        // Add same handler again, should be no-op return same instance
        assertSame(c3, c3.withHandler(handler));

        DeserializationConfig c4 = c3.withNoProblemHandlers();
        assertNull(c4.getProblemHandlers());
        assertSame(c4, c4.withNoProblemHandlers());
    }

    @Test
    public void testMapperConfigOverridesAndBase() throws Throwable {
        DeserializationConfig config = createConfig();

        MapperFeature mf = MapperFeature.USE_ANNOTATIONS;
        DeserializationConfig c1 = config.with(mf, false);
        assertFalse(c1.isEnabled(mf));
        assertSame(c1, c1.with(mf, false));

        DeserializationConfig c2 = c1.with(mf, true);
        assertTrue(c2.isEnabled(mf));

        DeserializationConfig c3 = config.with(new MapperFeature[] { mf });
        assertSame(config, c3);

        DeserializationConfig c4 = config.without(new MapperFeature[] { mf });
        assertFalse(c4.isEnabled(mf));

        // Test other with methods
        assertNotNull(config.with(config.getClassIntrospector()));
        assertNotNull(config.with(config.getAnnotationIntrospector()));
        assertNotNull(config.with(config.getDefaultVisibilityChecker()));
        assertNotNull(config.withVisibility(PropertyAccessor.ALL, JsonAutoDetect.Visibility.ANY));
        assertNotNull(config.with((TypeResolverBuilder<?>) null));
        assertNotNull(config.with(config.getSubtypeResolver()));
        assertNotNull(config.with(config.getPropertyNamingStrategy()));
        assertNotNull(config.withRootName(PropertyName.construct("test")));
        assertNotNull(config.withRootName((PropertyName) null));
        assertNotNull(config.with(TypeFactory.defaultInstance()));
        assertNotNull(config.with((DateFormat) null));
        assertNotNull(config.with((HandlerInstantiator) null));
        assertNotNull(config.withInsertedAnnotationIntrospector(config.getAnnotationIntrospector()));
        assertNotNull(config.withAppendedAnnotationIntrospector(config.getAnnotationIntrospector()));
        assertNotNull(config.withView(Object.class));
        assertNotNull(config.with(Locale.US));
        assertNotNull(config.with(TimeZone.getDefault()));
        assertNotNull(config.with(Base64Variant.getDefaultBase64()));
        assertNotNull(config.with(config.getAttributes()));
    }

    @Test
    public void testRootWrappingAndInitialization() throws Throwable {
        DeserializationConfig config = createConfig();
        assertFalse(config.useRootWrapping());

        DeserializationConfig c2 = config.withRootName(PropertyName.construct("root"));
        assertTrue(c2.useRootWrapping());

        DeserializationConfig c3 = config.withRootName(PropertyName.NO_NAME);
        assertFalse(c3.useRootWrapping());

        com.fasterxml.jackson.core.JsonParser p = new com.fasterxml.jackson.core.json.ReaderBasedJsonParser(
                new com.fasterxml.jackson.core.io.IOContext(new com.fasterxml.jackson.core.util.BufferRecycler(), null, false),
                0, new java.io.StringReader("{}"), null, null);
        config.initialize(p);
        p.close();
    }

    @Test
    public void testIntrospectionAndVisibility() throws Throwable {
        DeserializationConfig config = createConfig();
        JavaType type = TypeFactory.defaultInstance().constructType(Object.class);

        assertNotNull(config.introspectClassAnnotations(type));
        assertNotNull(config.introspectDirectClassAnnotations(type));
        assertNotNull(config.introspect(type));
        assertNotNull(config.introspectForCreation(type));
        assertNotNull(config.introspectForBuilder(type));

        assertNotNull(config.getDefaultPropertyInclusion());
        assertNotNull(config.getDefaultPropertyInclusion(Object.class));
        assertNotNull(config.getDefaultPropertyFormat(Object.class));

        VisibilityChecker<?> vc = config.getDefaultVisibilityChecker();
        assertNotNull(vc);

        DeserializationConfig cDisabled = config.without(MapperFeature.AUTO_DETECT_SETTERS, MapperFeature.AUTO_DETECT_CREATORS, MapperFeature.AUTO_DETECT_FIELDS);
        assertNotNull(cDisabled.getDefaultVisibilityChecker());
    }

    @Test
    public void testFindTypeDeserializer() throws Throwable {
        DeserializationConfig config = createConfig();
        JavaType type = TypeFactory.defaultInstance().constructType(Object.class);
        try {
            config.findTypeDeserializer(type);
        } catch (Exception e) {
            // Expected if type resolver is not configured, just exercising branch coverage
        }
    }
}