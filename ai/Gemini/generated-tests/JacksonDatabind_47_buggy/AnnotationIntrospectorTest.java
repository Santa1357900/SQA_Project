package com.fasterxml.jackson.databind;

import java.lang.annotation.Annotation;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.List;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonFormat;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.core.Version;
import com.fasterxml.jackson.databind.introspect.Annotated;
import com.fasterxml.jackson.databind.introspect.AnnotatedClass;
import com.fasterxml.jackson.databind.introspect.AnnotatedMember;
import com.fasterxml.jackson.databind.introspect.AnnotatedMethod;
import com.fasterxml.jackson.databind.introspect.NopAnnotationIntrospector;
import com.fasterxml.jackson.databind.introspect.ObjectIdInfo;
import com.fasterxml.jackson.databind.introspect.VisibilityChecker;
import com.fasterxml.jackson.databind.jsontype.NamedType;
import com.fasterxml.jackson.databind.jsontype.TypeResolverBuilder;
import com.fasterxml.jackson.databind.ser.BeanPropertyWriter;
import com.fasterxml.jackson.databind.type.TypeFactory;

import org.junit.Test;
import static org.junit.Assert.*;

public class AnnotationIntrospectorTest {

    static class DummyAnnotationIntrospector extends AnnotationIntrospector {
        private static final long serialVersionUID = 1L;

        @Override
        public Version version() {
            return Version.unknownVersion();
        }
    }

    @Test
    public void testReferenceProperty() throws Throwable {
        AnnotationIntrospector.ReferenceProperty managed = AnnotationIntrospector.ReferenceProperty.managed("prop1");
        assertTrue(managed.isManagedReference());
        assertFalse(managed.isBackReference());
        assertEquals(AnnotationIntrospector.ReferenceProperty.Type.MANAGED_REFERENCE, managed.getType());
        assertEquals("prop1", managed.getName());

        AnnotationIntrospector.ReferenceProperty back = AnnotationIntrospector.ReferenceProperty.back("prop2");
        assertFalse(back.isManagedReference());
        assertTrue(back.isBackReference());
        assertEquals(AnnotationIntrospector.ReferenceProperty.Type.BACK_REFERENCE, back.getType());
        assertEquals("prop2", back.getName());
    }

    @Test
    public void testFactoryMethods() throws Throwable {
        AnnotationIntrospector nop = AnnotationIntrospector.nopInstance();
        assertNotNull(nop);
        assertTrue(nop instanceof NopAnnotationIntrospector);

        AnnotationIntrospector d1 = new DummyAnnotationIntrospector();
        AnnotationIntrospector d2 = new DummyAnnotationIntrospector();
        AnnotationIntrospector pair = AnnotationIntrospector.pair(d1, d2);
        assertNotNull(pair);
    }

    @Test
    public void testAllIntrospectors() throws Throwable {
        AnnotationIntrospector introspector = new DummyAnnotationIntrospector();
        Collection<AnnotationIntrospector> list1 = introspector.allIntrospectors();
        assertNotNull(list1);
        assertEquals(1, list1.size());
        assertTrue(list1.contains(introspector));

        List<AnnotationIntrospector> resultCol = new ArrayList<AnnotationIntrospector>();
        Collection<AnnotationIntrospector> list2 = introspector.allIntrospectors(resultCol);
        assertNotNull(list2);
        assertTrue(list2.contains(introspector));
    }

    @Test
    public void testDefaultsAndNulls() throws Throwable {
        AnnotationIntrospector introspector = new DummyAnnotationIntrospector();

        assertFalse(introspector.isAnnotationBundle(null));
        assertNull(introspector.findObjectIdInfo(null));
        
        ObjectIdInfo dummyInfo = new ObjectIdInfo(null, null, null, null);
        assertEquals(dummyInfo, introspector.findObjectReferenceInfo(null, dummyInfo));

        assertNull(introspector.findRootName(null));
        assertNull(introspector.findPropertiesToIgnore(null, true));
        assertNull(introspector.findPropertiesToIgnore(null));
        assertNull(introspector.findIgnoreUnknownProperties(null));
        assertNull(introspector.isIgnorableType(null));
        assertNull(introspector.findFilterId(null));
        assertNull(introspector.findNamingStrategy(null));
        assertNull(introspector.findClassDescription(null));

        VisibilityChecker<?> checker = VisibilityChecker.Std.defaultInstance();
        assertEquals(checker, introspector.findAutoDetectVisibility(null, checker));

        assertNull(introspector.findTypeResolver(null, null, null));
        assertNull(introspector.findPropertyTypeResolver(null, null, null));
        assertNull(introspector.findPropertyContentTypeResolver(null, null, null));
        assertNull(introspector.findSubtypes(null));
        assertNull(introspector.findTypeName(null));
        assertNull(introspector.isTypeId(null));

        assertNull(introspector.findReferenceType(null));
        assertNull(introspector.findUnwrappingNameTransformer(null));
        assertFalse(introspector.hasIgnoreMarker(null));
        assertNull(introspector.findInjectableValueId(null));
        assertNull(introspector.hasRequiredMarker(null));
        assertNull(introspector.findViews(null));
        assertNull(introspector.findFormat(null));
        assertEquals(PropertyName.USE_DEFAULT, introspector.findWrapperName(null));
        assertNull(introspector.findPropertyDefaultValue(null));
        assertNull(introspector.findPropertyDescription(null));
        assertNull(introspector.findPropertyIndex(null));
        assertNull(introspector.findImplicitPropertyName(null));
        assertNull(introspector.findPropertyAccess(null));
        assertNull(introspector.resolveSetterConflict(null, null, null));

        assertNull(introspector.findSerializer(null));
        assertNull(introspector.findKeySerializer(null));
        assertNull(introspector.findContentSerializer(null));
        assertNull(introspector.findNullSerializer(null));
        assertNull(introspector.findSerializationTyping(null));
        assertNull(introspector.findSerializationConverter(null));
        assertNull(introspector.findSerializationContentConverter(null));
        assertEquals(JsonInclude.Include.ALWAYS, introspector.findSerializationInclusion(null, JsonInclude.Include.ALWAYS));
        assertEquals(JsonInclude.Include.ALWAYS, introspector.findSerializationInclusionForContent(null, JsonInclude.Include.ALWAYS));
        assertNotNull(introspector.findPropertyInclusion(null));

        assertNull(introspector.findSerializationType(null));
        assertNull(introspector.findSerializationKeyType(null, null));
        assertNull(introspector.findSerializationContentType(null, null));

        assertNull(introspector.findSerializationPropertyOrder(null));
        assertNull(introspector.findSerializationSortAlphabetically(null));
        
        List<BeanPropertyWriter> bpwList = new ArrayList<BeanPropertyWriter>();
        introspector.findAndAddVirtualProperties(null, null, bpwList);
        assertTrue(bpwList.isEmpty());

        assertNull(introspector.findNameForSerialization(null));
        assertFalse(introspector.hasAsValueAnnotation(null));

        assertEquals("SOME_ENUM", introspector.findEnumValue(Thread.State.RUNNABLE));

        Class<?>[] enumTypes = new Class<?>[] { Thread.State.class };
        Enum<?>[] enumValues = new Enum<?>[] { Thread.State.RUNNABLE };
        String[] names = new String[] { null };
        String[] resNames = introspector.findEnumValues(enumTypes[0], enumValues, names);
        assertEquals("RUNNABLE", resNames[0]);

        assertNull(introspector.findDeserializer(null));
        assertNull(introspector.findKeyDeserializer(null));
        assertNull(introspector.findContentDeserializer(null));
        assertNull(introspector.findDeserializationConverter(null));
        assertNull(introspector.findDeserializationContentConverter(null));

        assertNull(introspector.findDeserializationType(null, null));
        assertNull(introspector.findDeserializationKeyType(null, null));
        assertNull(introspector.findDeserializationContentType(null, null));

        assertNull(introspector.findValueInstantiator(null));
        assertNull(introspector.findPOJOBuilder(null));
        assertNull(introspector.findPOJOBuilderConfig(null));

        assertNull(introspector.findNameForDeserialization(null));
        assertFalse(introspector.hasAnySetterAnnotation(null));
        assertFalse(introspector.hasAnyGetterAnnotation(null));
        assertFalse(introspector.hasCreatorAnnotation(null));
        assertNull(introspector.findCreatorBinding(null));
    }
}