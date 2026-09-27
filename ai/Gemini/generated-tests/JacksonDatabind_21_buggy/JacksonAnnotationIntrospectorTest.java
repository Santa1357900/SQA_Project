package com.fasterxml.jackson.databind.introspect;

import org.junit.Test;
import static org.junit.Assert.*;

import java.lang.annotation.Annotation;
import java.util.ArrayList;
import java.util.List;

import com.fasterxml.jackson.annotation.*;
import com.fasterxml.jackson.core.Version;
import com.fasterxml.jackson.databind.*;
import com.fasterxml.jackson.databind.annotation.*;
import com.fasterxml.jackson.databind.jsontype.NamedType;
import com.fasterxml.jackson.databind.jsontype.TypeResolverBuilder;
import com.fasterxml.jackson.databind.util.NameTransformer;

public class JacksonAnnotationIntrospectorTest {

    @JacksonAnnotationsInside
    @Retention(RetentionPolicy.RUNTIME)
    private static @interface BundleAnnotation {
    }

    private static @interface NonBundleAnnotation {
    }

    @JsonRootName(value = "rootName", namespace = "rootNs")
    private static class SampleRootClass {
    }

    @JsonRootName(value = "rootNameEmptyNs", namespace = "")
    private static class SampleRootEmptyNsClass {
    }

    private static class SampleNoRootClass {
    }

    @JsonIgnoreProperties(value = {"prop1", "prop2"}, ignoreUnknown = true, allowGetters = true, allowSetters = false)
    private static class SampleIgnorePropertiesClass {
    }

    @JsonIgnoreType
    private static class SampleIgnoreTypeClass {
    }

    @JsonFilter("filterId")
    private static class SampleFilterClass {
    }

    @JsonFilter("")
    private static class SampleEmptyFilterClass {
    }

    @JsonNaming(PropertyNamingStrategy.LowerCaseStrategy.class)
    private static class SampleNamingClass {
    }

    @JsonAutoDetect(fieldVisibility = JsonAutoDetect.Visibility.ANY)
    private static class SampleAutoDetectClass {
    }

    @Test
    public void testVersion() throws Throwable {
        JacksonAnnotationIntrospector introspector = new JacksonAnnotationIntrospector();
        Version version = introspector.version();
        assertNotNull(version);
    }

    @Test
    public void testIsAnnotationBundle() throws Throwable {
        JacksonAnnotationIntrospector introspector = new JacksonAnnotationIntrospector();
        Annotation bundleAnn = SampleRootClass.class.getAnnotation(JsonRootName.class); // Just any annotation or use custom
        Annotation insideAnn = BundleAnnotation.class.getAnnotation(JacksonAnnotationsInside.class);
        Annotation nonBundleAnn = NonBundleAnnotation.class.getAnnotation(Retention.class);

        assertTrue(introspector.isAnnotationBundle(BundleAnnotation.class.getAnnotation(JacksonAnnotationsInside.class)));
        assertFalse(introspector.isAnnotationBundle(NonBundleAnnotation.class.getAnnotation(Retention.class)));
    }

    @Test
    public void testFindRootName() throws Throwable {
        JacksonAnnotationIntrospector introspector = new JacksonAnnotationIntrospector();
        AnnotatedClass ac = AnnotatedClass.constructWithoutSuperTypes(SampleRootClass.class, null, null);
        PropertyName name = introspector.findRootName(ac);
        assertNotNull(name);
        assertEquals("rootName", name.getSimpleName());
        assertEquals("rootNs", name.getNamespace());

        AnnotatedClass acEmpty = AnnotatedClass.constructWithoutSuperTypes(SampleRootEmptyNsClass.class, null, null);
        PropertyName nameEmpty = introspector.findRootName(acEmpty);
        assertNotNull(nameEmpty);
        assertEquals("rootNameEmptyNs", nameEmpty.getSimpleName());
        assertNull(nameEmpty.getNamespace());

        AnnotatedClass acNone = AnnotatedClass.constructWithoutSuperTypes(SampleNoRootClass.class, null, null);
        assertNull(introspector.findRootName(acNone));
    }

    @Test
    public void testFindPropertiesToIgnore() throws Throwable {
        JacksonAnnotationIntrospector introspector = new JacksonAnnotationIntrospector();
        AnnotatedClass ac = AnnotatedClass.constructWithoutSuperTypes(SampleIgnorePropertiesClass.class, null, null);
        
        String[] ignoreSer = introspector.findPropertiesToIgnore(ac, true);
        assertNull(ignoreSer); // because allowGetters = true

        String[] ignoreDeser = introspector.findPropertiesToIgnore(ac, false);
        assertNotNull(ignoreDeser);
        assertEquals(2, ignoreDeser.length);

        AnnotatedClass acNone = AnnotatedClass.constructWithoutSuperTypes(SampleNoRootClass.class, null, null);
        assertNull(introspector.findPropertiesToIgnore(acNone, true));
        assertNull(introspector.findPropertiesToIgnore(acNone));
    }

    @Test
    public void testFindIgnoreUnknownProperties() throws Throwable {
        JacksonAnnotationIntrospector introspector = new JacksonAnnotationIntrospector();
        AnnotatedClass ac = AnnotatedClass.constructWithoutSuperTypes(SampleIgnorePropertiesClass.class, null, null);
        assertEquals(Boolean.TRUE, introspector.findIgnoreUnknownProperties(ac));

        AnnotatedClass acNone = AnnotatedClass.constructWithoutSuperTypes(SampleNoRootClass.class, null, null);
        assertNull(introspector.findIgnoreUnknownProperties(acNone));
    }

    @Test
    public void testIsIgnorableType() throws Throwable {
        JacksonAnnotationIntrospector introspector = new JacksonAnnotationIntrospector();
        AnnotatedClass ac = AnnotatedClass.constructWithoutSuperTypes(SampleIgnoreTypeClass.class, null, null);
        assertEquals(Boolean.TRUE, introspector.isIgnorableType(ac));

        AnnotatedClass acNone = AnnotatedClass.constructWithoutSuperTypes(SampleNoRootClass.class, null, null);
        assertNull(introspector.isIgnorableType(acNone));
    }

    @Test
    public void testFindFilterId() throws Throwable {
        JacksonAnnotationIntrospector introspector = new JacksonAnnotationIntrospector();
        AnnotatedClass ac = AnnotatedClass.constructWithoutSuperTypes(SampleFilterClass.class, null, null);
        assertEquals("filterId", introspector.findFilterId(ac));
        assertEquals("filterId", introspector.findFilterId((Annotated) ac));

        AnnotatedClass acEmpty = AnnotatedClass.constructWithoutSuperTypes(SampleEmptyFilterClass.class, null, null);
        assertNull(introspector.findFilterId(acEmpty));

        AnnotatedClass acNone = AnnotatedClass.constructWithoutSuperTypes(SampleNoRootClass.class, null, null);
        assertNull(introspector.findFilterId(acNone));
    }

    @Test
    public void testFindNamingStrategy() throws Throwable {
        JacksonAnnotationIntrospector introspector = new JacksonAnnotationIntrospector();
        AnnotatedClass ac = AnnotatedClass.constructWithoutSuperTypes(SampleNamingClass.class, null, null);
        assertEquals(PropertyNamingStrategy.LowerCaseStrategy.class, introspector.findNamingStrategy(ac));

        AnnotatedClass acNone = AnnotatedClass.constructWithoutSuperTypes(SampleNoRootClass.class, null, null);
        assertNull(introspector.findNamingStrategy(acNone));
    }

    @Test
    public void testFindAutoDetectVisibility() throws Throwable {
        JacksonAnnotationIntrospector introspector = new JacksonAnnotationIntrospector();
        AnnotatedClass ac = AnnotatedClass.constructWithoutSuperTypes(SampleAutoDetectClass.class, null, null);
        VisibilityChecker<?> checker = VisibilityChecker.Std.defaultInstance();
        VisibilityChecker<?> result = introspector.findAutoDetectVisibility(ac, checker);
        assertNotNull(result);

        AnnotatedClass acNone = AnnotatedClass.constructWithoutSuperTypes(SampleNoRootClass.class, null, null);
        assertEquals(checker, introspector.findAutoDetectVisibility(acNone, checker));
    }

    @Test
    public void testGeneralMemberMethods() throws Throwable {
        JacksonAnnotationIntrospector introspector = new JacksonAnnotationIntrospector();
        AnnotatedClass ac = AnnotatedClass.constructWithoutSuperTypes(SampleNoRootClass.class, null, null);
        assertNull(introspector.findImplicitPropertyName(null));
        assertFalse(introspector.hasIgnoreMarker(null));
        assertNull(introspector.hasRequiredMarker(null));
        assertNull(introspector.findPropertyAccess(null));
        assertNull(introspector.findPropertyDescription(null));
        assertNull(introspector.findPropertyIndex(null));
        assertNull(introspector.findPropertyDefaultValue(null));
        assertNull(introspector.findFormat(null));
        assertNull(introspector.findReferenceType(null));
        assertNull(introspector.findUnwrappingNameTransformer(null));
        assertNull(introspector.findInjectableValueId(null));
        assertNull(introspector.findViews(null));
    }

    @Test
    public void testSubtypesAndNames() throws Throwable {
        JacksonAnnotationIntrospector introspector = new JacksonAnnotationIntrospector();
        AnnotatedClass ac = AnnotatedClass.constructWithoutSuperTypes(SampleNoRootClass.class, null, null);
        assertNull(introspector.findSubtypes(ac));
        assertNull(introspector.findTypeName(ac));
        assertNull(introspector.isTypeId(null));
    }

    @Test
    public void testObjectIdInfo() throws Throwable {
        JacksonAnnotationIntrospector introspector = new JacksonAnnotationIntrospector();
        AnnotatedClass ac = AnnotatedClass.constructWithoutSuperTypes(SampleNoRootClass.class, null, null);
        assertNull(introspector.findObjectIdInfo(ac));
        assertNotNull(introspector.findObjectReferenceInfo(ac, null));
    }

    @Test
    public void testSerializationAnnotations() throws Throwable {
        JacksonAnnotationIntrospector introspector = new JacksonAnnotationIntrospector();
        AnnotatedClass ac = AnnotatedClass.constructWithoutSuperTypes(SampleNoRootClass.class, null, null);
        assertNull(introspector.findSerializer(ac));
        assertNull(introspector.findKeySerializer(ac));
        assertNull(introspector.findContentSerializer(ac));
        assertNull(introspector.findNullSerializer(ac));
        assertEquals(JsonInclude.Include.ALWAYS, introspector.findSerializationInclusion(ac, JsonInclude.Include.ALWAYS));
        assertEquals(JsonInclude.Include.ALWAYS, introspector.findSerializationInclusionForContent(ac, JsonInclude.Include.ALWAYS));
        assertNull(introspector.findSerializationType(ac));
        assertNull(introspector.findSerializationKeyType(ac, null));
        assertNull(introspector.findSerializationContentType(ac, null));
        assertNull(introspector.findSerializationTyping(ac));
        assertNull(introspector.findSerializationConverter(ac));
        assertNull(introspector.findSerializationContentConverter(null));
        assertNull(introspector.findSerializationPropertyOrder(ac));
        assertNull(introspector.findSerializationSortAlphabetically(ac));
        assertNull(introspector.findNameForSerialization(ac));
        assertFalse(introspector.hasAsValueAnnotation(null));
    }

    @Test
    public void testDeserializationAnnotations() throws Throwable {
        JacksonAnnotationIntrospector introspector = new JacksonAnnotationIntrospector();
        AnnotatedClass ac = AnnotatedClass.constructWithoutSuperTypes(SampleNoRootClass.class, null, null);
        assertNull(introspector.findDeserializer(ac));
        assertNull(introspector.findKeyDeserializer(ac));
        assertNull(introspector.findContentDeserializer(ac));
        assertNull(introspector.findDeserializationType(null, null));
        assertNull(introspector.findDeserializationKeyType(null, null));
        assertNull(introspector.findDeserializationContentType(null, null));
        assertNull(introspector.findDeserializationConverter(ac));
        assertNull(introspector.findDeserializationContentConverter(null));
        assertNull(introspector.findValueInstantiator(ac));
        assertNull(introspector.findPOJOBuilder(ac));
        assertNull(introspector.findPOJOBuilderConfig(ac));
        assertNull(introspector.findNameForDeserialization(ac));
        assertFalse(introspector.hasAnySetterAnnotation(null));
        assertFalse(introspector.hasAnyGetterAnnotation(null));
        assertFalse(introspector.hasCreatorAnnotation(null));
        assertNull(introspector.findCreatorBinding(ac));
    }

    @Test
    public void testContainerTypeResolverException() throws Throwable {
        JacksonAnnotationIntrospector introspector = new JacksonAnnotationIntrospector();
        ObjectMapper mapper = new ObjectMapper();
        JavaType notContainerType = mapper.constructType(String.class);
        
        try {
            introspector.findPropertyContentTypeResolver(null, null, notContainerType);
            fail("Should have thrown IllegalArgumentException");
        } catch (IllegalArgumentException e) {
            assertTrue(e.getMessage().contains("Must call method with a container type"));
        }
    }
}