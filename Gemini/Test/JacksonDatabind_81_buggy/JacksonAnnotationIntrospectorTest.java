package com.fasterxml.jackson.databind.introspect;

import org.junit.Test;
import static org.junit.Assert.*;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;
import java.util.List;

import com.fasterxml.jackson.annotation.*;
import com.fasterxml.jackson.databind.AnnotationIntrospector;
import com.fasterxml.jackson.databind.JavaType;
import com.fasterxml.jackson.databind.JsonMappingException;
import com.fasterxml.jackson.databind.PropertyName;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.databind.cfg.MapperConfig;
import com.fasterxml.jackson.databind.jsontype.NamedType;
import com.fasterxml.jackson.databind.jsontype.TypeResolverBuilder;
import com.fasterxml.jackson.databind.type.TypeFactory;

public class JacksonAnnotationIntrospectorTest {

    @Retention(RetentionPolicy.RUNTIME)
    @Target({ElementType.ANNOTATION_TYPE, ElementType.TYPE})
    @JacksonAnnotationsInside
    public static @interface BundleAnnotation {
    }

    @BundleAnnotation
    public static class BundledClass {
    }

    public static enum SampleEnum {
        @JsonProperty("CUSTOM_VAL")
        FIRST,
        SECOND;
    }

    @JsonRootName(value = "root", namespace = "ns")
    public static class RootNamedClass {
    }

    @JsonRootName(value = "rootNoNs", namespace = "")
    public static class RootNamedNoNsClass {
    }

    @JsonIgnoreProperties(value = {"prop1"})
    public static class IgnorablePropsClass {
    }

    @JsonIgnoreType
    public static class IgnorableTypeClass {
    }

    @JsonFilter("myFilter")
    public static class FilteredClass {
    }

    @JsonFilter("")
    public static class EmptyFilterClass {
    }

    @JsonNaming(value = Object.class)
    public static class NamingStrategyClass {
    }

    @JsonClassDescription("class description")
    public static class ClassDescriptionClass {
    }

    @JsonAutoDetect(fieldVisibility = JsonAutoDetect.Visibility.ANY)
    public static class AutoDetectClass {
    }

    public static class DummyAnnotatedMember extends AnnotatedMember {
        private static final long serialVersionUID = 1L;

        public DummyAnnotatedMember() {
            super(null, null);
        }

        @Override public Class<?> getRawType() { return String.class; }
        @Override public void setValue(Object pojo, Object value) throws UnsupportedOperationException, IllegalArgumentException {}
        @Override public Object getValue(Object pojo) throws UnsupportedOperationException, IllegalArgumentException { return null; }
        @Override public Annotated withAnnotations(AnnotationMap ann) { return null; }
        @Override public int getModifiers() { return 0; }
        @Override public String getName() { return "dummy"; }
        @Override public <A extends java.lang.annotation.Annotation> A getAnnotation(Class<A> acls) { return null; }
        @Override public boolean hasAnnotation(Class<?> acls) { return false; }
        @Override public boolean hasOneOf(Class<? extends java.lang.annotation.Annotation>[] acls) { return false; }
        @Override public java.lang.reflect.Member getMember() { return null; }
    }

    @Test
    public void testVersion() throws Throwable {
        JacksonAnnotationIntrospector intr = new JacksonAnnotationIntrospector();
        assertNotNull(intr.version());
    }

    @Test
    public void testReadResolve() throws Throwable {
        JacksonAnnotationIntrospector intr = new JacksonAnnotationIntrospector();
        assertNotNull(intr.readResolve());
    }

    @Test
    public void testSetConstructorPropertiesImpliesCreator() throws Throwable {
        JacksonAnnotationIntrospector intr = new JacksonAnnotationIntrospector();
        assertSame(intr, intr.setConstructorPropertiesImpliesCreator(false));
        assertSame(intr, intr.setConstructorPropertiesImpliesCreator(true));
    }

    @Test
    public void testIsAnnotationBundle() throws Throwable {
        JacksonAnnotationIntrospector intr = new JacksonAnnotationIntrospector();
        BundleAnnotation bundleAnn = BundledClass.class.getAnnotation(BundleAnnotation.class);
        assertTrue(intr.isAnnotationBundle(bundleAnn));

        Retention retentionAnn = BundledClass.class.getAnnotation(Retention.class);
        // If retention is null on BundledClass, test with another standard annotation
        Retention retAnn = JacksonAnnotationIntrospectorTest.class.getAnnotation(Retention.class);
        if (retAnn != null) {
            assertFalse(intr.isAnnotationBundle(retAnn));
        }
    }

    @Test
    public void testFindEnumValue() throws Throwable {
        JacksonAnnotationIntrospector intr = new JacksonAnnotationIntrospector();
        assertEquals("CUSTOM_VAL", intr.findEnumValue(SampleEnum.FIRST));
        assertEquals("SECOND", intr.findEnumValue(SampleEnum.SECOND));
    }

    @Test
    public void testFindEnumValues() throws Throwable {
        JacksonAnnotationIntrospector intr = new JacksonAnnotationIntrospector();
        Enum<?>[] enumValues = SampleEnum.values();
        String[] names = new String[] { "FIRST", "SECOND" };
        String[] result = intr.findEnumValues(SampleEnum.class, enumValues, names);
        assertNotNull(result);
        assertEquals("CUSTOM_VAL", result[0]);
        assertEquals("SECOND", result[1]);
    }

    @Test
    public void testFindRootName() throws Throwable {
        JacksonAnnotationIntrospector intr = new JacksonAnnotationIntrospector();
        AnnotatedClass ac = AnnotatedClassResolver.resolveWithoutSuperTypes(null, RootNamedClass.class, null);
        PropertyName name = intr.findRootName(ac);
        assertNotNull(name);
        assertEquals("root", name.getSimpleName());
        assertEquals("ns", name.getNamespace());

        AnnotatedClass ac2 = AnnotatedClassResolver.resolveWithoutSuperTypes(null, RootNamedNoNsClass.class, null);
        PropertyName name2 = intr.findRootName(ac2);
        assertNotNull(name2);
        assertEquals("rootNoNs", name2.getSimpleName());
        assertFalse(name2.hasNamespace());

        AnnotatedClass ac3 = AnnotatedClassResolver.resolveWithoutSuperTypes(null, Object.class, null);
        assertNull(intr.findRootName(ac3));
    }

    @Test
    public void testFindPropertyIgnorals() throws Throwable {
        JacksonAnnotationIntrospector intr = new JacksonAnnotationIntrospector();
        AnnotatedClass ac = AnnotatedClassResolver.resolveWithoutSuperTypes(null, IgnorablePropsClass.class, null);
        assertNotNull(intr.findPropertyIgnorals(ac));
    }

    @Test
    public void testIsIgnorableType() throws Throwable {
        JacksonAnnotationIntrospector intr = new JacksonAnnotationIntrospector();
        AnnotatedClass ac = AnnotatedClassResolver.resolveWithoutSuperTypes(null, IgnorableTypeClass.class, null);
        assertEquals(Boolean.TRUE, intr.isIgnorableType(ac));

        AnnotatedClass ac2 = AnnotatedClassResolver.resolveWithoutSuperTypes(null, Object.class, null);
        assertNull(intr.isIgnorableType(ac2));
    }

    @Test
    public void testFindFilterId() throws Throwable {
        JacksonAnnotationIntrospector intr = new JacksonAnnotationIntrospector();
        AnnotatedClass ac = AnnotatedClassResolver.resolveWithoutSuperTypes(null, FilteredClass.class, null);
        assertEquals("myFilter", intr.findFilterId(ac));

        AnnotatedClass acEmpty = AnnotatedClassResolver.resolveWithoutSuperTypes(null, EmptyFilterClass.class, null);
        assertNull(intr.findFilterId(acEmpty));

        AnnotatedClass acObj = AnnotatedClassResolver.resolveWithoutSuperTypes(null, Object.class, null);
        assertNull(intr.findFilterId(acObj));
    }

    @Test
    public void testFindNamingStrategy() throws Throwable {
        JacksonAnnotationIntrospector intr = new JacksonAnnotationIntrospector();
        AnnotatedClass ac = AnnotatedClassResolver.resolveWithoutSuperTypes(null, NamingStrategyClass.class, null);
        assertEquals(Object.class, intr.findNamingStrategy(ac));

        AnnotatedClass acObj = AnnotatedClassResolver.resolveWithoutSuperTypes(null, Object.class, null);
        assertNull(intr.findNamingStrategy(acObj));
    }

    @Test
    public void testFindClassDescription() throws Throwable {
        JacksonAnnotationIntrospector intr = new JacksonAnnotationIntrospector();
        AnnotatedClass ac = AnnotatedClassResolver.resolveWithoutSuperTypes(null, ClassDescriptionClass.class, null);
        assertEquals("class description", intr.findClassDescription(ac));

        AnnotatedClass acObj = AnnotatedClassResolver.resolveWithoutSuperTypes(null, Object.class, null);
        assertNull(intr.findClassDescription(acObj));
    }

    @Test
    public void testFindAutoDetectVisibility() throws Throwable {
        JacksonAnnotationIntrospector intr = new JacksonAnnotationIntrospector();
        AnnotatedClass ac = AnnotatedClassResolver.resolveWithoutSuperTypes(null, AutoDetectClass.class, null);
        VisibilityChecker<?> checker = VisibilityChecker.Std.defaultInstance();
        assertNotNull(intr.findAutoDetectVisibility(ac, checker));

        AnnotatedClass acObj = AnnotatedClassResolver.resolveWithoutSuperTypes(null, Object.class, null);
        assertSame(checker, intr.findAutoDetectVisibility(acObj, checker));
    }

    @Test
    public void testFindImplicitPropertyName() throws Throwable {
        JacksonAnnotationIntrospector intr = new JacksonAnnotationIntrospector();
        DummyAnnotatedMember member = new DummyAnnotatedMember();
        assertNull(intr.findImplicitPropertyName(member));
    }

    @Test
    public void testFindPropertyAliases() throws Throwable {
        JacksonAnnotationIntrospector intr = new JacksonAnnotationIntrospector();
        DummyAnnotatedMember member = new DummyAnnotatedMember();
        assertNull(intr.findPropertyAliases(member));
    }

    @Test
    public void testHasIgnoreMarker() throws Throwable {
        JacksonAnnotationIntrospector intr = new JacksonAnnotationIntrospector();
        DummyAnnotatedMember member = new DummyAnnotatedMember();
        assertFalse(intr.hasIgnoreMarker(member));
    }

    @Test
    public void testHasRequiredMarker() throws Throwable {
        JacksonAnnotationIntrospector intr = new JacksonAnnotationIntrospector();
        DummyAnnotatedMember member = new DummyAnnotatedMember();
        assertNull(intr.hasRequiredMarker(member));
    }

    @Test
    public void testFindPropertyAccess() throws Throwable {
        JacksonAnnotationIntrospector intr = new JacksonAnnotationIntrospector();
        DummyAnnotatedMember member = new DummyAnnotatedMember();
        assertNull(intr.findPropertyAccess(member));
    }

    @Test
    public void testFindPropertyDescription() throws Throwable {
        JacksonAnnotationIntrospector intr = new JacksonAnnotationIntrospector();
        DummyAnnotatedMember member = new DummyAnnotatedMember();
        assertNull(intr.findPropertyDescription(member));
    }

    @Test
    public void testFindPropertyIndex() throws Throwable {
        JacksonAnnotationIntrospector intr = new JacksonAnnotationIntrospector();
        DummyAnnotatedMember member = new DummyAnnotatedMember();
        assertNull(intr.findPropertyIndex(member));
    }

    @Test
    public void testFindPropertyDefaultValue() throws Throwable {
        JacksonAnnotationIntrospector intr = new JacksonAnnotationIntrospector();
        DummyAnnotatedMember member = new DummyAnnotatedMember();
        assertNull(intr.findPropertyDefaultValue(member));
    }

    @Test
    public void testFindFormat() throws Throwable {
        JacksonAnnotationIntrospector intr = new JacksonAnnotationIntrospector();
        DummyAnnotatedMember member = new DummyAnnotatedMember();
        assertNull(intr.findFormat(member));
    }

    @Test
    public void testFindReferenceType() throws Throwable {
        JacksonAnnotationIntrospector intr = new JacksonAnnotationIntrospector();
        DummyAnnotatedMember member = new DummyAnnotatedMember();
        assertNull(intr.findReferenceType(member));
    }

    @Test
    public void testFindUnwrappingNameTransformer() throws Throwable {
        JacksonAnnotationIntrospector intr = new JacksonAnnotationIntrospector();
        DummyAnnotatedMember member = new DummyAnnotatedMember();
        assertNull(intr.findUnwrappingNameTransformer(member));
    }

    @Test
    public void testFindInjectableValue() throws Throwable {
        JacksonAnnotationIntrospector intr = new JacksonAnnotationIntrospector();
        DummyAnnotatedMember member = new DummyAnnotatedMember();
        assertNull(intr.findInjectableValue(member));
        assertNull(intr.findInjectableValueId(member));
    }

    @Test
    public void testFindViews() throws Throwable {
        JacksonAnnotationIntrospector intr = new JacksonAnnotationIntrospector();
        DummyAnnotatedMember member = new DummyAnnotatedMember();
        assertNull(intr.findViews(member));
    }

    @Test
    public void testFindSubtypes() throws Throwable {
        JacksonAnnotationIntrospector intr = new JacksonAnnotationIntrospector();
        AnnotatedClass ac = AnnotatedClassResolver.resolveWithoutSuperTypes(null, Object.class, null);
        assertNull(intr.findSubtypes(ac));
    }

    @Test
    public void testFindTypeName() throws Throwable {
        JacksonAnnotationIntrospector intr = new JacksonAnnotationIntrospector();
        AnnotatedClass ac = AnnotatedClassResolver.resolveWithoutSuperTypes(null, Object.class, null);
        assertNull(intr.findTypeName(ac));
    }

    @Test
    public void testIsTypeId() throws Throwable {
        JacksonAnnotationIntrospector intr = new JacksonAnnotationIntrospector();
        DummyAnnotatedMember member = new DummyAnnotatedMember();
        assertFalse(intr.isTypeId(member));
    }

    @Test
    public void testFindObjectIdInfo() throws Throwable {
        JacksonAnnotationIntrospector intr = new JacksonAnnotationIntrospector();
        AnnotatedClass ac = AnnotatedClassResolver.resolveWithoutSuperTypes(null, Object.class, null);
        assertNull(intr.findObjectIdInfo(ac));
    }

    @Test
    public void testFindObjectReferenceInfo() throws Throwable {
        JacksonAnnotationIntrospector intr = new JacksonAnnotationIntrospector();
        AnnotatedClass ac = AnnotatedClassResolver.resolveWithoutSuperTypes(null, Object.class, null);
        ObjectIdInfo info = new ObjectIdInfo(PropertyName.construct("test"), Object.class, null, null);
        assertSame(info, intr.findObjectReferenceInfo(ac, info));
    }

    @Test
    public void testFindSerializer() throws Throwable {
        JacksonAnnotationIntrospector intr = new JacksonAnnotationIntrospector();
        AnnotatedClass ac = AnnotatedClassResolver.resolveWithoutSuperTypes(null, Object.class, null);
        assertNull(intr.findSerializer(ac));
        assertNull(intr.findKeySerializer(ac));
        assertNull(intr.findContentSerializer(ac));
        assertNull(intr.findNullSerializer(ac));
    }

    @Test
    public void testFindPropertyInclusion() throws Throwable {
        JacksonAnnotationIntrospector intr = new JacksonAnnotationIntrospector();
        AnnotatedClass ac = AnnotatedClassResolver.resolveWithoutSuperTypes(null, Object.class, null);
        assertNotNull(intr.findPropertyInclusion(ac));
    }

    @Test
    public void testFindSerializationTyping() throws Throwable {
        JacksonAnnotationIntrospector intr = new JacksonAnnotationIntrospector();
        AnnotatedClass ac = AnnotatedClassResolver.resolveWithoutSuperTypes(null, Object.class, null);
        assertNull(intr.findSerializationTyping(ac));
    }

    @Test
    public void testFindSerializationConverter() throws Throwable {
        JacksonAnnotationIntrospector intr = new JacksonAnnotationIntrospector();
        AnnotatedClass ac = AnnotatedClassResolver.resolveWithoutSuperTypes(null, Object.class, null);
        assertNull(intr.findSerializationConverter(ac));
        DummyAnnotatedMember member = new DummyAnnotatedMember();
        assertNull(intr.findSerializationContentConverter(member));
    }

    @Test
    public void testFindSerializationPropertyOrder() throws Throwable {
        JacksonAnnotationIntrospector intr = new JacksonAnnotationIntrospector();
        AnnotatedClass ac = AnnotatedClassResolver.resolveWithoutSuperTypes(null, Object.class, null);
        assertNull(intr.findSerializationPropertyOrder(ac));
        assertNull(intr.findSerializationSortAlphabetically(ac));
    }

    @Test
    public void testFindNameForSerialization() throws Throwable {
        JacksonAnnotationIntrospector intr = new JacksonAnnotationIntrospector();
        AnnotatedClass ac = AnnotatedClassResolver.resolveWithoutSuperTypes(null, Object.class, null);
        assertNull(intr.findNameForSerialization(ac));
        assertNull(intr.hasAsValue(ac));
        assertNull(intr.hasAnyGetter(ac));
        assertFalse(intr.hasAnyGetterAnnotation(null));
        assertFalse(intr.hasAsValueAnnotation(null));
    }

    @Test
    public void testFindDeserializer() throws Throwable {
        JacksonAnnotationIntrospector intr = new JacksonAnnotationIntrospector();
        AnnotatedClass ac = AnnotatedClassResolver.resolveWithoutSuperTypes(null, Object.class, null);
        assertNull(intr.findDeserializer(ac));
        assertNull(intr.findKeyDeserializer(ac));
        assertNull(intr.findContentDeserializer(ac));
        assertNull(intr.findDeserializationConverter(ac));
        DummyAnnotatedMember member = new DummyAnnotatedMember();
        assertNull(intr.findDeserializationContentConverter(member));
    }

    @Test
    public void testFindValueInstantiator() throws Throwable {
        JacksonAnnotationIntrospector intr = new JacksonAnnotationIntrospector();
        AnnotatedClass ac = AnnotatedClassResolver.resolveWithoutSuperTypes(null, Object.class, null);
        assertNull(intr.findValueInstantiator(ac));
        assertNull(intr.findPOJOBuilder(ac));
        assertNull(intr.findPOJOBuilderConfig(ac));
    }

    @Test
    public void testFindNameForDeserialization() throws Throwable {
        JacksonAnnotationIntrospector intr = new JacksonAnnotationIntrospector();
        AnnotatedClass ac = AnnotatedClassResolver.resolveWithoutSuperTypes(null, Object.class, null);
        assertNull(intr.findNameForDeserialization(ac));
        assertNull(intr.hasAnySetter(ac));
        assertNull(intr.findSetterInfo(ac));
        assertNull(intr.findMergeInfo(ac));
        assertFalse(intr.hasAnySetterAnnotation(null));
        assertFalse(intr.hasCreatorAnnotation(ac));
        assertNull(intr.findCreatorBinding(ac));
    }

    @Test
    public void testHelperMethods() throws Throwable {
        JacksonAnnotationIntrospector intr = new JacksonAnnotationIntrospector();
        DummyAnnotatedMember member = new DummyAnnotatedMember();
        assertFalse(intr._isIgnorable(member));
        assertNull(intr._classIfExplicit(null));
        assertNull(intr._classIfExplicit(Object.class, Object.class));
        assertNotNull(intr._propertyName("local", "ns"));
        assertNotNull(intr._propertyName("local", ""));
        assertNotNull(intr._propertyName("", "ns"));
        assertEquals(PropertyName.USE_DEFAULT, intr._propertyName("", ""));
        assertNull(intr._findConstructorName(member));
    }
}