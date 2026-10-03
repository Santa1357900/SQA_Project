package com.fasterxml.jackson.databind.cfg;

import org.junit.Test;
import static org.junit.Assert.*;

import java.text.DateFormat;
import java.text.SimpleDateFormat;
import java.util.Locale;
import java.util.TimeZone;

import com.fasterxml.jackson.annotation.JsonAutoDetect;
import com.fasterxml.jackson.annotation.PropertyAccessor;
import com.fasterxml.jackson.core.Base64Variant;
import com.fasterxml.jackson.core.Base64Variants;
import com.fasterxml.jackson.databind.AnnotationIntrospector;
import com.fasterxml.jackson.databind.PropertyNamingStrategy;
import com.fasterxml.jackson.databind.introspect.BasicClassIntrospector;
import com.fasterxml.jackson.databind.introspect.ClassIntrospector;
import com.fasterxml.jackson.databind.introspect.VisibilityChecker;
import com.fasterxml.jackson.databind.jsontype.TypeResolverBuilder;
import com.fasterxml.jackson.databind.type.TypeFactory;
import com.fasterxml.jackson.databind.util.StdDateFormat;

public class BaseSettingsTest {

    @Test
    public void testConstructorAndGetters() throws Throwable {
        ClassIntrospector ci = new BasicClassIntrospector();
        AnnotationIntrospector ai = AnnotationIntrospector.nopInstance();
        VisibilityChecker<?> vc = VisibilityChecker.Std.defaultInstance();
        PropertyNamingStrategy pns = PropertyNamingStrategy.CAMEL_CASE_TO_LOWER_CASE_WITH_UNDERSCORES;
        TypeFactory tf = TypeFactory.defaultInstance();
        TypeResolverBuilder<?> typer = null;
        DateFormat df = new StdDateFormat();
        HandlerInstantiator hi = null;
        Locale locale = Locale.US;
        TimeZone tz = TimeZone.getTimeZone("GMT");
        Base64Variant b64 = Base64Variants.MIME;

        BaseSettings settings = new BaseSettings(ci, ai, vc, pns, tf, typer, df, hi, locale, tz, b64);

        assertSame(ci, settings.getClassIntrospector());
        assertSame(ai, settings.getAnnotationIntrospector());
        assertSame(vc, settings.getVisibilityChecker());
        assertSame(pns, settings.getPropertyNamingStrategy());
        assertSame(tf, settings.getTypeFactory());
        assertSame(typer, settings.getTypeResolverBuilder());
        assertSame(df, settings.getDateFormat());
        assertSame(hi, settings.getHandlerInstantiator());
        assertSame(locale, settings.getLocale());
        assertSame(tz, settings.getTimeZone());
        assertSame(b64, settings.getBase64Variant());
    }

    @Test
    public void testWithClassIntrospector() throws Throwable {
        ClassIntrospector ci1 = new BasicClassIntrospector();
        ClassIntrospector ci2 = new BasicClassIntrospector();
        BaseSettings settings = createDefaultSettings();

        BaseSettings updated1 = settings.withClassIntrospector(ci1);
        assertSame(settings, updated1);

        BaseSettings updated2 = settings.withClassIntrospector(ci2);
        assertNotSame(settings, updated2);
        assertSame(ci2, updated2.getClassIntrospector());
    }

    @Test
    public void testWithAnnotationIntrospector() throws Throwable {
        AnnotationIntrospector ai1 = AnnotationIntrospector.nopInstance();
        AnnotationIntrospector ai2 = AnnotationIntrospector.nopInstance();
        BaseSettings settings = new BaseSettings(null, ai1, null, null, null, null, null, null, null, null, null);

        BaseSettings updated1 = settings.withAnnotationIntrospector(ai1);
        assertSame(settings, updated1);

        BaseSettings updated2 = settings.withAnnotationIntrospector(ai2);
        assertNotSame(settings, updated2);
        assertSame(ai2, updated2.getAnnotationIntrospector());
    }

    @Test
    public void testInsertedAndAppendedAnnotationIntrospector() throws Throwable {
        AnnotationIntrospector ai1 = AnnotationIntrospector.nopInstance();
        AnnotationIntrospector ai2 = AnnotationIntrospector.nopInstance();
        BaseSettings settings = new BaseSettings(null, ai1, null, null, null, null, null, null, null, null, null);

        BaseSettings inserted = settings.withInsertedAnnotationIntrospector(ai2);
        assertNotNull(inserted.getAnnotationIntrospector());

        BaseSettings appended = settings.withAppendedAnnotationIntrospector(ai2);
        assertNotNull(appended.getAnnotationIntrospector());
    }

    @Test
    public void testWithVisibilityChecker() throws Throwable {
        VisibilityChecker<?> vc1 = VisibilityChecker.Std.defaultInstance();
        VisibilityChecker<?> vc2 = VisibilityChecker.Std.defaultInstance();
        BaseSettings settings = new BaseSettings(null, null, vc1, null, null, null, null, null, null, null, null);

        BaseSettings updated1 = settings.withVisibilityChecker(vc1);
        assertSame(settings, updated1);

        BaseSettings updated2 = settings.withVisibilityChecker(vc2);
        assertNotSame(settings, updated2);
        assertSame(vc2, updated2.getVisibilityChecker());
    }

    @Test
    public void testWithVisibility() throws Throwable {
        BaseSettings settings = createDefaultSettings();
        BaseSettings updated = settings.withVisibility(PropertyAccessor.ALL, JsonAutoDetect.Visibility.PUBLIC_ONLY);
        assertNotNull(updated);
        assertNotSame(settings, updated);
    }

    @Test
    public void testWithPropertyNamingStrategy() throws Throwable {
        PropertyNamingStrategy pns1 = PropertyNamingStrategy.CAMEL_CASE_TO_LOWER_CASE_WITH_UNDERSCORES;
        PropertyNamingStrategy pns2 = PropertyNamingStrategy.SNAKE_CASE;
        BaseSettings settings = new BaseSettings(null, null, null, pns1, null, null, null, null, null, null, null);

        BaseSettings updated1 = settings.withPropertyNamingStrategy(pns1);
        assertSame(settings, updated1);

        BaseSettings updated2 = settings.withPropertyNamingStrategy(pns2);
        assertNotSame(settings, updated2);
        assertSame(pns2, updated2.getPropertyNamingStrategy());
    }

    @Test
    public void testWithTypeFactory() throws Throwable {
        TypeFactory tf1 = TypeFactory.defaultInstance();
        TypeFactory tf2 = TypeFactory.defaultInstance();
        BaseSettings settings = new BaseSettings(null, null, null, null, tf1, null, null, null, null, null, null);

        BaseSettings updated1 = settings.withTypeFactory(tf1);
        assertSame(settings, updated1);

        BaseSettings updated2 = settings.withTypeFactory(tf2);
        assertNotSame(settings, updated2);
        assertSame(tf2, updated2.getTypeFactory());
    }

    @Test
    public void testWithTypeResolverBuilder() throws Throwable {
        TypeResolverBuilder<?> typer1 = null;
        TypeResolverBuilder<?> typer2 = null;
        BaseSettings settings = new BaseSettings(null, null, null, null, null, typer1, null, null, null, null, null);

        BaseSettings updated1 = settings.withTypeResolverBuilder(typer1);
        assertSame(settings, updated1);

        // create a dummy TypeResolverBuilder or just test with null/non-null logic
        // Since TypeResolverBuilder is an interface, let's avoid direct implementation if large, 
        // but we can pass null if both are null, or test different instances if available.
    }

    @Test
    public void testWithDateFormat() throws Throwable {
        DateFormat df1 = new StdDateFormat();
        DateFormat df2 = new SimpleDateFormat("yyyy-MM-dd");
        BaseSettings settings = new BaseSettings(null, null, null, null, null, null, df1, null, null, TimeZone.getDefault(), null);

        BaseSettings updated1 = settings.withDateFormat(df1);
        assertSame(settings, updated1);

        BaseSettings updated2 = settings.withDateFormat(df2);
        assertNotSame(settings, updated2);
        assertSame(df2, updated2.getDateFormat());

        BaseSettings updatedNullDf = settings.withDateFormat(null);
        assertNull(updatedNullDf.getDateFormat());
    }

    @Test
    public void testWithHandlerInstantiator() throws Throwable {
        HandlerInstantiator hi1 = null;
        HandlerInstantiator hi2 = null;
        BaseSettings settings = new BaseSettings(null, null, null, null, null, null, null, hi1, null, null, null);

        BaseSettings updated1 = settings.withHandlerInstantiator(hi1);
        assertSame(settings, updated1);
    }

    @Test
    public void testWithLocale() throws Throwable {
        Locale l1 = Locale.US;
        Locale l2 = Locale.CANADA;
        BaseSettings settings = new BaseSettings(null, null, null, null, null, null, null, null, l1, null, null);

        BaseSettings updated1 = settings.with(l1);
        assertSame(settings, updated1);

        BaseSettings updated2 = settings.with(l2);
        assertNotSame(settings, updated2);
        assertSame(l2, updated2.getLocale());
    }

    @Test
    public void testWithTimeZone() throws Throwable {
        TimeZone tz1 = TimeZone.getTimeZone("GMT");
        TimeZone tz2 = TimeZone.getTimeZone("PST");
        DateFormat dfStd = new StdDateFormat();
        DateFormat dfCustom = new SimpleDateFormat("yyyy-MM-dd");

        BaseSettings settings1 = new BaseSettings(null, null, null, null, null, null, dfStd, null, null, tz1, null);
        BaseSettings updated1 = settings1.with(tz1);
        assertSame(settings1, updated1);

        BaseSettings updatedTz = settings1.with(tz2);
        assertNotSame(settings1, updatedTz);
        assertEquals(tz2, updatedTz.getTimeZone());

        BaseSettings settingsWithCustomDf = new BaseSettings(null, null, null, null, null, null, dfCustom, null, null, tz1, null);
        BaseSettings updatedCustomTz = settingsWithCustomDf.with(tz2);
        assertNotNull(updatedCustomTz);
        assertEquals(tz2, updatedCustomTz.getTimeZone());
    }

    @Test(expected = IllegalArgumentException.class)
    public void testWithNullTimeZone() throws Throwable {
        BaseSettings settings = createDefaultSettings();
        settings.with((TimeZone) null);
    }

    @Test
    public void testWithBase64Variant() throws Throwable {
        Base64Variant b64_1 = Base64Variants.MIME;
        Base64Variant b64_2 = Base64Variants.PEM;
        BaseSettings settings = new BaseSettings(null, null, null, null, null, null, null, null, null, null, b64_1);

        BaseSettings updated1 = settings.with(b64_1);
        assertSame(settings, updated1);

        BaseSettings updated2 = settings.with(b64_2);
        assertNotSame(settings, updated2);
        assertSame(b64_2, updated2.getBase64Variant());
    }

    private BaseSettings createDefaultSettings() {
        return new BaseSettings(
            new BasicClassIntrospector(),
            AnnotationIntrospector.nopInstance(),
            VisibilityChecker.Std.defaultInstance(),
            PropertyNamingStrategy.CAMEL_CASE_TO_LOWER_CASE_WITH_UNDERSCORES,
            TypeFactory.defaultInstance(),
            null,
            new StdDateFormat(),
            null,
            Locale.US,
            TimeZone.getDefault(),
            Base64Variants.MIME
        );
    }
}