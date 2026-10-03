package com.fasterxml.jackson.databind.introspect;

import org.junit.Test;
import static org.junit.Assert.*;

import java.lang.annotation.Annotation;
import java.util.List;
import java.util.ArrayList;

import com.fasterxml.jackson.annotation.*;
import com.fasterxml.jackson.core.Version;
import com.fasterxml.jackson.databind.JavaType;
import com.fasterxml.jackson.databind.PropertyName;
import com.fasterxml.jackson.databind.PropertyMetadata;
import com.fasterxml.jackson.databind.annotation.JsonSerialize;
import com.fasterxml.jackson.databind.annotation.JsonDeserialize;
import com.fasterxml.jackson.databind.annotation.JsonPOJOBuilder;
import com.fasterxml.jackson.databind.annotation.JsonValueInstantiator;
import com.fasterxml.jackson.databind.cfg.MapperConfig;
import com.fasterxml.jackson.databind.type.TypeFactory;
import com.fasterxml.jackson.databind.jsontype.NamedType;
import com.fasterxml.jackson.databind.ser.BeanPropertyWriter;

public class JacksonAnnotationIntrospectorTest {

    @JsonAnnotationsInside
    @Retention(RetentionPolicy.RUNTIME)
    private @interface DummyBundleAnn {}

    private @interface NonBundleAnn {}

    private enum DummyEnumWithProp {
        @JsonProperty("explicitName")
        WITH_PROP,
        WITHOUT_PROP
    }

    @JsonRootName(value = "root", namespace = "ns")
    private static class DummyRootClass {}

    @JsonRootName(value = "rootNoNs", namespace = "")
    private static class DummyRootClassEmptyNs {}

    @JsonIgnoreProperties(value = {"prop1", "prop2"}, ignoreUnknown = true, allowGetters = true, allowSetters = true)
    private static class DummyIgnorePropsClass {}

    @JsonIgnoreType
    private static class DummyIgnoreTypeClass {}

    @JsonFilter("filterId")
    private static class DummyFilterClass {}

    @JsonFilter("")
    private static class DummyEmptyFilterClass {}

    @JsonNaming(value = Object.class)
    private static class DummyNamingClass {}

    @JsonAutoDetect(fieldVisibility = JsonAutoDetect.Visibility.ANY)
    private static class DummyAutoDetectClass {}

    private static class DummyAnnotatedMemberClass {
        @JsonProperty(value = "prop", required = true, access = JsonProperty.Access.READ_ONLY, index = 5, defaultValue = "defaultVal")
        @JsonPropertyDescription("desc")
        @JsonFormat(shape = JsonFormat.Shape.STRING)
        @JsonManagedReference("ref")
        @JsonUnwrapped(enabled = true, prefix = "pre_", suffix = "_suf")
        @JacksonInject("injectId")
        @JsonView(Object.class)
        @JsonIdentityInfo(generator = ObjectIdGenerators.PropertyGenerator.class, property = "id")
        @JsonIdentityReference(alwaysAsId = true)
        public String field;
    }

    private static class DummyBackRefClass {
        @JsonBackReference("backRef")
        public String field;
    }

    private static class DummyInjectNoIdClass {
        @JacksonInject
        public String field;
    }

    @JsonSubTypes({
        @JsonSubTypes.Type(value = String.class, name = "str")
    })
    private static class DummySubTypesClass {}

    @JsonTypeName("typeName")
    private static class DummyTypeNameClass {}

    private static class DummyTypeIdClass {
        @JsonTypeId
        public String field;
    }

    private static class DummySerializerClass {
        @JsonSerialize(
            using = JsonSerializer.None.class,
            keyUsing = JsonSerializer.None.class,
            contentUsing = JsonSerializer.None.class,
            nullsUsing = JsonSerializer.None.class,
            include = JsonSerialize.Inclusion.NON_NULL,
            typing = JsonSerialize.Typing.STATIC,
            converter = Converter.None.class,
            contentConverter = Converter.None.class
        )
        @JsonRawValue(true)
        public String field;
    }

    @JsonPropertyOrder(value = {"a", "b"}, alphabetic = true)
    private static class DummyOrderClass {}

    @JsonInclude(value = JsonInclude.Include.NON_EMPTY, content = JsonInclude.Include.NON_ABSENT)
    private static class DummyIncludeClass {}

    private static class DummyGetterClass {
        @JsonGetter("getterProp")
        public String getProp() { return ""; }

        @JsonValue(true)
        public String getValue() { return ""; }
    }

    private static class DummyDeserializerClass {
        @JsonDeserialize(
            using = JsonDeserializer.None.class,
            keyUsing = KeyDeserializer.None.class,
            contentUsing = JsonDeserializer.None.class,
            converter = Converter.None.class,
            contentConverter = Converter.None.class,
            builder = Object.class
        )
        @JsonSetter("setterProp")
        @JsonAnySetter
        @JsonAnyGetter
        @JsonCreator(mode = JsonCreator.Mode.PROPERTIES)
        public String field;
    }

    @JsonValueInstantiator(Object.class)
    @JsonPOJOBuilder(buildMethodName = "build")
    private static class DummyValueInstantiatorClass {}

    @Test
    public void testVersion() throws Throwable {
        JacksonAnnotationIntrospector intr = new JacksonAnnotationIntrospector();
        Version v = intr.version();
        assertNotNull(v);
    }

    @Test
    public void testIsAnnotationBundle() throws Throwable {
        JacksonAnnotationIntrospector intr = new JacksonAnnotationIntrospector();
        Annotation annBundle = DummyBundleAnn.class.getAnnotation(DummyBundleAnn.class);
        Annotation annNormal = NonBundleAnn.class.getAnnotation(NonBundleAnn.class);
        
        assertTrue(intr.isAnnotationBundle(annBundle));
        assertFalse(intr.isAnnotationBundle(annNormal));
    }

    @Test
    public void testFindEnumValue() throws Throwable {
        JacksonAnnotationIntrospector intr = new JacksonAnnotationIntrospector();
        assertEquals("explicitName", intr.findEnumValue(DummyEnumWithProp.WITH_PROP));
        assertEquals("WITHOUT_PROP", intr.findEnumValue(DummyEnumWithProp.WITHOUT_PROP));
    }

    @Test
    public void testFindRootName() throws Throwable {
        JacksonAnnotationIntrospector intr = new JacksonAnnotationIntrospector();
        AnnotatedClass ac = AnnotatedClass.construct(DummyRootClass.class, intr, null);
        PropertyName name = intr.findRootName(ac);
        assertNotNull(name);
        assertEquals("root", name.getSimpleName());
        assertEquals("ns", name.getNamespace());

        AnnotatedClass acEmptyNs = AnnotatedClass.construct(DummyRootClassEmptyNs.class, intr, null);
        PropertyName nameEmptyNs = intr.findRootName(acEmptyNs);
        assertNotNull(nameEmptyNs);
        assertEquals("rootNoNs", nameEmptyNs.getSimpleName());
        assertFalse(nameEmptyNs.hasNamespace());

        AnnotatedClass acNull = AnnotatedClass.construct(String.class, intr, null);
        assertNull(intr.findRootName(acNull));
    }

    @Test
    public void testFindPropertiesToIgnore() throws Throwable {
        JacksonAnnotationIntrospector intr = new JacksonAnnotationIntrospector();
        AnnotatedClass ac = AnnotatedClass.construct(DummyIgnorePropsClass.class, intr, null);
        
        String[] ignoreProps = intr.findPropertiesToIgnore(ac);
        assertNotNull(ignoreProps);
        assertEquals(2, ignoreProps.length);

        String[] ignoreSerialization = intr.findPropertiesToIgnore(ac, true);
        assertNull(ignoreSerialization); // allowGetters is true

        String[] ignoreDeserialization = intr.findPropertiesToIgnore(ac, false);
        assertNull(ignoreDeserialization); // allowSetters is true

        AnnotatedClass acNull = AnnotatedClass.construct(String.class, intr, null);
        assertNull(intr.findPropertiesToIgnore(acNull));
        assertNull(intr.findPropertiesToIgnore(acNull, true));
    }

    @Test
    public void testFindIgnoreUnknownProperties() throws Throwable {
        JacksonAnnotationIntrospector intr = new JacksonAnnotationIntrospector();
        AnnotatedClass ac = AnnotatedClass.construct(DummyIgnorePropsClass.class, intr, null);
        assertEquals(Boolean.TRUE, intr.findIgnoreUnknownProperties(ac));

        AnnotatedClass acNull = AnnotatedClass.construct(String.class, intr, null);
        assertNull(intr.findIgnoreUnknownProperties(acNull));
    }

    @Test
    public void testIsIgnorableType() throws Throwable {
        JacksonAnnotationIntrospector intr = new JacksonAnnotationIntrospector();
        AnnotatedClass ac = AnnotatedClass.construct(DummyIgnoreTypeClass.class, intr, null);
        assertEquals(Boolean.TRUE, intr.isIgnorableType(ac));

        AnnotatedClass acNull = AnnotatedClass.construct(String.class, intr, null);
        assertNull(intr.isIgnorableType(acNull));
    }

    @Test
    public void testFindFilterId() throws Throwable {
        JacksonAnnotationIntrospector intr = new JacksonAnnotationIntrospector();
        AnnotatedClass ac = AnnotatedClass.construct(DummyFilterClass.class, intr, null);
        assertEquals("filterId", intr.findFilterId(ac));
        assertEquals("filterId", intr.findFilterId((Annotated) ac));

        AnnotatedClass acEmpty = AnnotatedClass.construct(DummyEmptyFilterClass.class, intr, null);
        assertNull(intr.findFilterId(acEmpty));

        AnnotatedClass acNull = AnnotatedClass.construct(String.class, intr, null);
        assertNull(intr.findFilterId(acNull));
    }

    @Test
    public void testFindNamingStrategy() throws Throwable {
        JacksonAnnotationIntrospector intr = new JacksonAnnotationIntrospector();
        AnnotatedClass ac = AnnotatedClass.construct(DummyNamingClass.class, intr, null);
        assertEquals(Object.class, intr.findNamingStrategy(ac));

        AnnotatedClass acNull = AnnotatedClass.construct(String.class, intr, null);
        assertNull(intr.findNamingStrategy(acNull));
    }

    @Test
    public void testFindAutoDetectVisibility() throws Throwable {
        JacksonAnnotationIntrospector intr = new JacksonAnnotationIntrospector();
        AnnotatedClass ac = AnnotatedClass.construct(DummyAutoDetectClass.class, intr, null);
        VisibilityChecker<?> checker = VisibilityChecker.Std.defaultInstance();
        assertNotNull(intr.findAutoDetectVisibility(ac, checker));

        AnnotatedClass acNull = AnnotatedClass.construct(String.class, intr, null);
        assertEquals(checker, intr.findAutoDetectVisibility(acNull, checker));
    }

    @Test
    public void testMemberAnnotations() throws Throwable {
        JacksonAnnotationIntrospector intr = new JacksonAnnotationIntrospector();
        AnnotatedClass ac = AnnotatedClass.construct(DummyAnnotatedMemberClass.class, intr, null);
        AnnotatedField field = ac.fields().iterator().next();

        assertNull(intr.findImplicitPropertyName(field));
        assertFalse(intr.hasIgnoreMarker(field));
        assertEquals(Boolean.TRUE, intr.hasRequiredMarker(field));
        assertEquals(JsonProperty.Access.READ_ONLY, intr.findPropertyAccess(field));
        assertEquals("desc", intr.findPropertyDescription(field));
        assertEquals(Integer.valueOf(5), intr.findPropertyIndex(field));
        assertEquals("defaultVal", intr.findPropertyDefaultValue(field));
        assertNotNull(intr.findFormat(field));
        
        AnnotationIntrospector.ReferenceProperty ref = intr.findReferenceType(field);
        assertNotNull(ref);
        assertTrue(ref.isManagedReference());
        assertEquals("ref", ref.getName());

        assertNotNull(intr.findUnwrappingNameTransformer(field));
        assertEquals("injectId", intr.findInjectableValueId(field));
        assertNotNull(intr.findViews(field));

        AnnotatedClass acBack = AnnotatedClass.construct(DummyBackRefClass.class, intr, null);
        AnnotatedField fieldBack = acBack.fields().iterator().next();
        AnnotationIntrospector.ReferenceProperty refBack = intr.findReferenceType(fieldBack);
        assertNotNull(refBack);
        assertTrue(refBack.isBackReference());
        assertEquals("backRef", refBack.getName());

        AnnotatedClass acInjectNoId = AnnotatedClass.construct(DummyInjectNoIdClass.class, intr, null);
        AnnotatedField fieldInjectNoId = acInjectNoId.fields().iterator().next();
        assertNotNull(intr.findInjectableValueId(fieldInjectNoId));

        AnnotatedClass acTypeId = AnnotatedClass.construct(DummyTypeIdClass.class, intr, null);
        AnnotatedField fieldTypeId = acTypeId.fields().iterator().next();
        assertEquals(Boolean.TRUE, intr.isTypeId(fieldTypeId));
    }

    @Test
    public void testFindSubtypesAndTypeName() throws Throwable {
        JacksonAnnotationIntrospector intr = new JacksonAnnotationIntrospector();
        AnnotatedClass ac = AnnotatedClass.construct(DummySubTypesClass.class, intr, null);
        List<NamedType> subtypes = intr.findSubtypes(ac);
        assertNotNull(subtypes);
        assertEquals(1, subtypes.size());

        AnnotatedClass acType = AnnotatedClass.construct(DummyTypeNameClass.class, intr, null);
        assertEquals("typeName", intr.findTypeName(acType));

        AnnotatedClass acNull = AnnotatedClass.construct(String.class, intr, null);
        assertNull(intr.findSubtypes(acNull));
        assertNull(intr.findTypeName(acNull));
    }

    @Test
    public void testObjectIdInfo() throws Throwable {
        JacksonAnnotationIntrospector intr = new JacksonAnnotationIntrospector();
        AnnotatedClass ac = AnnotatedClass.construct(DummyAnnotatedMemberClass.class, intr, null);
        ObjectIdInfo info = intr.findObjectIdInfo(ac);
        assertNotNull(info);
        assertEquals("id", info.getPropertyName().getSimpleName());

        ObjectIdInfo refInfo = intr.findObjectReferenceInfo(ac, info);
        assertNotNull(refInfo);
        assertTrue(refInfo.getAlwaysAsId());

        AnnotatedClass acNull = AnnotatedClass.construct(String.class, intr, null);
        assertNull(intr.findObjectIdInfo(acNull));
    }

    @Test
    public void testSerializationAnnotations() throws Throwable {
        JacksonAnnotationIntrospector intr = new JacksonAnnotationIntrospector();
        AnnotatedClass ac = AnnotatedClass.construct(DummySerializerClass.class, intr, null);
        AnnotatedField field = ac.fields().iterator().next();

        assertNull(intr.findSerializer(field));
        assertNull(intr.findKeySerializer(field));
        assertNull(intr.findContentSerializer(field));
        assertNull(intr.findNullSerializer(field));
        
        AnnotatedClass acRaw = AnnotatedClass.construct(DummyAnnotatedMemberClass.class, intr, null);
        AnnotatedField fieldRaw = acRaw.fields().iterator().next();
        assertNotNull(intr.findSerializer(fieldRaw)); // has @JsonRawValue

        assertEquals(JsonInclude.Include.NON_NULL, intr.findSerializationInclusion(field, JsonInclude.Include.ALWAYS));
        assertEquals(JsonInclude.Include.USE_DEFAULTS, intr.findSerializationInclusionForContent(field, JsonInclude.Include.USE_DEFAULTS));
        assertNotNull(intr.findPropertyInclusion(field));

        assertNull(intr.findSerializationType(field));
        assertNull(intr.findSerializationKeyType(field, null));
        assertNull(intr.findSerializationContentType(field, null));
        assertEquals(JsonSerialize.Typing.STATIC, intr.findSerializationTyping(field));
        assertNull(intr.findSerializationConverter(field));
        assertNull(intr.findSerializationContentConverter(field));

        AnnotatedClass acOrder = AnnotatedClass.construct(DummyOrderClass.class, intr, null);
        assertNotNull(intr.findSerializationPropertyOrder(acOrder));
        assertEquals(Boolean.TRUE, intr.findSerializationSortAlphabetically(acOrder));

        AnnotatedClass acInc = AnnotatedClass.construct(DummyIncludeClass.class, intr, null);
        assertEquals(JsonInclude.Include.NON_EMPTY, intr.findSerializationInclusion(acInc, JsonInclude.Include.ALWAYS));
        assertEquals(JsonInclude.Include.NON_ABSENT, intr.findSerializationInclusionForContent(acInc, JsonInclude.Include.USE_DEFAULTS));
    }

    @Test
    public void testFindNameForSerializationAndDeserialization() throws Throwable {
        JacksonAnnotationIntrospector intr = new JacksonAnnotationIntrospector();
        AnnotatedClass ac = AnnotatedClass.construct(DummyGetterClass.class, intr, null);
        AnnotatedMethod getter = ac.annotatedMethods().iterator().next();

        assertEquals("getterProp", intr.findNameForSerialization(getter).getSimpleName());
        assertTrue(intr.hasAsValueAnnotation(getter));

        AnnotatedClass acDeser = AnnotatedClass.construct(DummyDeserializerClass.class, intr, null);
        AnnotatedField fieldDeser = acDeser.fields().iterator().next();

        assertEquals("setterProp", intr.findNameForDeserialization(fieldDeser).getSimpleName());
        assertTrue(intr.hasAnySetterAnnotation(acDeser.annotatedMethods().iterator().next())); // Just checking method
        assertTrue(intr.hasAnyGetterAnnotation(acDeser.annotatedMethods().iterator().next()));
        assertTrue(intr.hasCreatorAnnotation(fieldDeser));
        assertEquals(JsonCreator.Mode.PROPERTIES, intr.findCreatorBinding(fieldDeser));
    }

    @Test
    public void testDeserializationAnnotations() throws Throwable {
        JacksonAnnotationIntrospector intr = new JacksonAnnotationIntrospector();
        AnnotatedClass ac = AnnotatedClass.construct(DummyDeserializerClass.class, intr, null);
        AnnotatedField field = ac.fields().iterator().next();

        assertNull(intr.findDeserializer(field));
        assertNull(intr.findKeyDeserializer(field));
        assertNull(intr.findContentDeserializer(field));
        assertNull(intr.findDeserializationType(field, null));
        assertNull(intr.findDeserializationKeyType(field, null));
        assertNull(intr.findDeserializationContentType(field, null));
        assertNull(intr.findDeserializationConverter(field));
        assertNull(intr.findDeserializationContentConverter(field));

        AnnotatedClass acInst = AnnotatedClass.construct(DummyValueInstantiatorClass.class, intr, null);
        assertEquals(Object.class, intr.findValueInstantiator(acInst));
        assertEquals(Object.class, intr.findPOJOBuilder(acInst));
        assertNotNull(intr.findPOJOBuilderConfig(acInst));
    }

    @Test
    public void testHelperMethods() throws Throwable {
        JacksonAnnotationIntrospector intr = new JacksonAnnotationIntrospector();
        JavaType type = TypeFactory.defaultInstance().constructType(String.class);
        AnnotatedClass ac = AnnotatedClass.construct(String.class, intr, null);
        
        assertNull(intr.findPropertyTypeResolver(null, ac.fields().iterator().next(), type));
        
        try {
            JavaType containerType = TypeFactory.defaultInstance().constructType(List.class);
            intr.findPropertyContentTypeResolver(null, ac.fields().iterator().next(), containerType);
            fail("Should throw IllegalArgumentException");
        } catch (IllegalArgumentException e) {
            assertTrue(e.getMessage().contains("Must call method with a container type"));
        }
    }
}