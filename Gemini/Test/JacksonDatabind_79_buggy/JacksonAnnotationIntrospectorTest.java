package com.fasterxml.jackson.databind.introspect;

import org.junit.Test;
import static org.junit.Assert.*;

import java.lang.annotation.Annotation;
import java.util.ArrayList;
import java.util.List;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonFormat;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.annotation.JsonSerialize;
import com.fasterxml.jackson.annotation.JsonTypeInfo;
import com.fasterxml.jackson.databind.JavaType;
import com.fasterxml.jackson.databind.PropertyName;
import com.fasterxml.jackson.databind.cfg.MapperConfig;
import com.fasterxml.jackson.databind.jsontype.NamedType;
import com.fasterxml.jackson.databind.jsontype.TypeResolverBuilder;
import com.fasterxml.jackson.databind.jsontype.impl.StdTypeResolverBuilder;
import com.fasterxml.jackson.databind.util.LRUMap;

public class JacksonAnnotationIntrospectorTest {

    @Test
    public void testVersion() throws Throwable {
        JacksonAnnotationIntrospector introspector = new JacksonAnnotationIntrospector();
        assertNotNull(introspector.version());
    }

    @Test
    public void testReadResolve() throws Throwable {
        JacksonAnnotationIntrospector introspector = new JacksonAnnotationIntrospector();
        introspector._annotationsInside = null;
        Object resolved = introspector.readResolve();
        assertNotNull(resolved);
    }

    @Test
    public void testSetConstructorPropertiesImpliesCreator() throws Throwable {
        JacksonAnnotationIntrospector introspector = new JacksonAnnotationIntrospector();
        JacksonAnnotationIntrospector result = introspector.setConstructorPropertiesImpliesCreator(false);
        assertNotNull(result);
    }

    @Test
    public void testFindEnumValue() throws Throwable {
        JacksonAnnotationIntrospector introspector = new JacksonAnnotationIntrospector();
        String val = introspector.findEnumValue(MockEnum.VALUE1);
        assertEquals("VALUE1", val);
    }

    @Test
    public void testFindEnumValues() throws Throwable {
        JacksonAnnotationIntrospector introspector = new JacksonAnnotationIntrospector();
        Class<?> enumType = MockEnum.class;
        Enum<?>[] enumValues = MockEnum.values();
        String[] names = new String[] { "v1", "v2" };
        String[] result = introspector.findEnumValues(enumType, enumValues, names);
        assertNotNull(result);
    }

    @Test
    public void testFindPropertiesToIgnore() throws Throwable {
        JacksonAnnotationIntrospector introspector = new JacksonAnnotationIntrospector();
        String[] props = introspector.findPropertiesToIgnore(null, true);
        assertNull(props);
    }

    @Test
    public void testFindIgnoreUnknownProperties() throws Throwable {
        JacksonAnnotationIntrospector introspector = new JacksonAnnotationIntrospector();
        Boolean ign = introspector.findIgnoreUnknownProperties(null);
        assertNull(ign);
    }

    @Test
    public void testIsIgnorableType() throws Throwable {
        JacksonAnnotationIntrospector introspector = new JacksonAnnotationIntrospector();
        Boolean ign = introspector.isIgnorableType(null);
        assertNull(ign);
    }

    @Test
    public void testFindFilterId() throws Throwable {
        JacksonAnnotationIntrospector introspector = new JacksonAnnotationIntrospector();
        Object filter = introspector.findFilterId(null);
        assertNull(filter);
    }

    @Test
    public void testFindNamingStrategy() throws Throwable {
        JacksonAnnotationIntrospector introspector = new JacksonAnnotationIntrospector();
        Object naming = introspector.findNamingStrategy(null);
        assertNull(naming);
    }

    @Test
    public void testFindClassDescription() throws Throwable {
        JacksonAnnotationIntrospector introspector = new JacksonAnnotationIntrospector();
        String desc = introspector.findClassDescription(null);
        assertNull(desc);
    }

    @Test
    public void testFindAutoDetectVisibility() throws Throwable {
        JacksonAnnotationIntrospector introspector = new JacksonAnnotationIntrospector();
        VisibilityChecker<?> checker = VisibilityChecker.Std.defaultInstance();
        VisibilityChecker<?> result = introspector.findAutoDetectVisibility(null, checker);
        assertNotNull(result);
    }

    @Test
    public void testHasRequiredMarker() throws Throwable {
        JacksonAnnotationIntrospector introspector = new JacksonAnnotationIntrospector();
        Boolean req = introspector.hasRequiredMarker(null);
        assertNull(req);
    }

    @Test
    public void testFindPropertyAccess() throws Throwable {
        JacksonAnnotationIntrospector introspector = new JacksonAnnotationIntrospector();
        JsonProperty.Access access = introspector.findPropertyAccess(null);
        assertNull(access);
    }

    @Test
    public void testFindPropertyDescription() throws Throwable {
        JacksonAnnotationIntrospector introspector = new JacksonAnnotationIntrospector();
        String desc = introspector.findPropertyDescription(null);
        assertNull(desc);
    }

    @Test
    public void testFindPropertyIndex() throws Throwable {
        JacksonAnnotationIntrospector introspector = new JacksonAnnotationIntrospector();
        Integer index = introspector.findPropertyIndex(null);
        assertNull(index);
    }

    @Test
    public void testFindPropertyDefaultValue() throws Throwable {
        JacksonAnnotationIntrospector introspector = new JacksonAnnotationIntrospector();
        String defVal = introspector.findPropertyDefaultValue(null);
        assertNull(defVal);
    }

    @Test
    public void testFindFormat() throws Throwable {
        JacksonAnnotationIntrospector introspector = new JacksonAnnotationIntrospector();
        JsonFormat.Value format = introspector.findFormat(null);
        assertNull(format);
    }

    @Test
    public void testFindReferenceType() throws Throwable {
        JacksonAnnotationIntrospector introspector = new JacksonAnnotationIntrospector();
        AnnotationIntrospector.ReferenceProperty ref = introspector.findReferenceType(null);
        assertNull(ref);
    }

    @Test
    public void testFindUnwrappingNameTransformer() throws Throwable {
        JacksonAnnotationIntrospector introspector = new JacksonAnnotationIntrospector();
        NameTransformer trans = introspector.findUnwrappingNameTransformer(null);
        assertNull(trans);
    }

    @Test
    public void testFindInjectableValueId() throws Throwable {
        JacksonAnnotationIntrospector introspector = new JacksonAnnotationIntrospector();
        Object id = introspector.findInjectableValueId(null);
        assertNull(id);
    }

    @Test
    public void testFindViews() throws Throwable {
        JacksonAnnotationIntrospector introspector = new JacksonAnnotationIntrospector();
        Class<?>[] views = introspector.findViews(null);
        assertNull(views);
    }

    @Test
    public void testFindSubtypes() throws Throwable {
        JacksonAnnotationIntrospector introspector = new JacksonAnnotationIntrospector();
        List<NamedType> subtypes = introspector.findSubtypes(null);
        assertNull(subtypes);
    }

    @Test
    public void testFindTypeName() throws Throwable {
        JacksonAnnotationIntrospector introspector = new JacksonAnnotationIntrospector();
        String name = introspector.findTypeName(null);
        assertNull(name);
    }

    @Test
    public void testIsTypeId() throws Throwable {
        JacksonAnnotationIntrospector introspector = new JacksonAnnotationIntrospector();
        Boolean isId = introspector.isTypeId(null);
        assertNotNull(isId);
    }

    @Test
    public void testFindObjectIdInfo() throws Throwable {
        JacksonAnnotationIntrospector introspector = new JacksonAnnotationIntrospector();
        ObjectIdInfo info = introspector.findObjectIdInfo(null);
        assertNull(info);
    }

    @Test
    public void testFindObjectReferenceInfo() throws Throwable {
        JacksonAnnotationIntrospector introspector = new JacksonAnnotationIntrospector();
        ObjectIdInfo info = new ObjectIdInfo(PropertyName.construct("test"), null, null, null);
        ObjectIdInfo result = introspector.findObjectReferenceInfo(null, info);
        assertNotNull(result);
    }

    @Test
    public void testFindSerializer() throws Throwable {
        JacksonAnnotationIntrospector introspector = new JacksonAnnotationIntrospector();
        Object ser = introspector.findSerializer(null);
        assertNull(ser);
    }

    @Test
    public void testFindKeySerializer() throws Throwable {
        JacksonAnnotationIntrospector introspector = new JacksonAnnotationIntrospector();
        Object ser = introspector.findKeySerializer(null);
        assertNull(ser);
    }

    @Test
    public void testFindContentSerializer() throws Throwable {
        JacksonAnnotationIntrospector introspector = new JacksonAnnotationIntrospector();
        Object ser = introspector.findContentSerializer(null);
        assertNull(ser);
    }

    @Test
    public void testFindNullSerializer() throws Throwable {
        JacksonAnnotationIntrospector introspector = new JacksonAnnotationIntrospector();
        Object ser = introspector.findNullSerializer(null);
        assertNull(ser);
    }

    @Test
    public void testFindSerializationInclusion() throws Throwable {
        JacksonAnnotationIntrospector introspector = new JacksonAnnotationIntrospector();
        JsonInclude.Include inc = introspector.findSerializationInclusion(null, JsonInclude.Include.ALWAYS);
        assertEquals(JsonInclude.Include.ALWAYS, inc);
    }

    @Test
    public void testFindSerializationInclusionForContent() throws Throwable {
        JacksonAnnotationIntrospector introspector = new JacksonAnnotationIntrospector();
        JsonInclude.Include inc = introspector.findSerializationInclusionForContent(null, JsonInclude.Include.NON_NULL);
        assertEquals(JsonInclude.Include.NON_NULL, inc);
    }

    @Test
    public void testFindPropertyInclusion() throws Throwable {
        JacksonAnnotationIntrospector introspector = new JacksonAnnotationIntrospector();
        JsonInclude.Value val = introspector.findPropertyInclusion(null);
        assertNotNull(val);
    }

    @Test
    public void testFindSerializationTyping() throws Throwable {
        JacksonAnnotationIntrospector introspector = new JacksonAnnotationIntrospector();
        JsonSerialize.Typing typing = introspector.findSerializationTyping(null);
        assertNull(typing);
    }

    @Test
    public void testFindSerializationConverter() throws Throwable {
        JacksonAnnotationIntrospector introspector = new JacksonAnnotationIntrospector();
        Object conv = introspector.findSerializationConverter(null);
        assertNull(conv);
    }

    @Test
    public void testFindSerializationPropertyOrder() throws Throwable {
        JacksonAnnotationIntrospector introspector = new JacksonAnnotationIntrospector();
        String[] order = introspector.findSerializationPropertyOrder(null);
        assertNull(order);
    }

    @Test
    public void testFindSerializationSortAlphabetically() throws Throwable {
        JacksonAnnotationIntrospector introspector = new JacksonAnnotationIntrospector();
        Boolean sort = introspector.findSerializationSortAlphabetically(null);
        assertNull(sort);
    }

    @Test
    public void testFindNameForSerialization() throws Throwable {
        JacksonAnnotationIntrospector introspector = new JacksonAnnotationIntrospector();
        PropertyName name = introspector.findNameForSerialization(null);
        assertNull(name);
    }

    @Test
    public void testHasAsValueAnnotation() throws Throwable {
        JacksonAnnotationIntrospector introspector = new JacksonAnnotationIntrospector();
        boolean has = introspector.hasAsValueAnnotation(null);
        assertFalse(has);
    }

    @Test
    public void testFindDeserializer() throws Throwable {
        JacksonAnnotationIntrospector introspector = new JacksonAnnotationIntrospector();
        Object deser = introspector.findDeserializer(null);
        assertNull(deser);
    }

    @Test
    public void testFindKeyDeserializer() throws Throwable {
        JacksonAnnotationIntrospector introspector = new JacksonAnnotationIntrospector();
        Object deser = introspector.findKeyDeserializer(null);
        assertNull(deser);
    }

    @Test
    public void testFindContentDeserializer() throws Throwable {
        JacksonAnnotationIntrospector introspector = new JacksonAnnotationIntrospector();
        Object deser = introspector.findContentDeserializer(null);
        assertNull(deser);
    }

    @Test
    public void testFindDeserializationConverter() throws Throwable {
        JacksonAnnotationIntrospector introspector = new JacksonAnnotationIntrospector();
        Object conv = introspector.findDeserializationConverter(null);
        assertNull(conv);
    }

    @Test
    public void testFindValueInstantiator() throws Throwable {
        JacksonAnnotationIntrospector introspector = new JacksonAnnotationIntrospector();
        Object inst = introspector.findValueInstantiator(null);
        assertNull(inst);
    }

    @Test
    public void testFindPOJOBuilder() throws Throwable {
        JacksonAnnotationIntrospector introspector = new JacksonAnnotationIntrospector();
        Class<?> builder = introspector.findPOJOBuilder(null);
        assertNull(builder);
    }

    @Test
    public void testFindPOJOBuilderConfig() throws Throwable {
        JacksonAnnotationIntrospector introspector = new JacksonAnnotationIntrospector();
        JsonPOJOBuilder.Value config = introspector.findPOJOBuilderConfig(null);
        assertNull(config);
    }

    @Test
    public void testFindNameForDeserialization() throws Throwable {
        JacksonAnnotationIntrospector introspector = new JacksonAnnotationIntrospector();
        PropertyName name = introspector.findNameForDeserialization(null);
        assertNull(name);
    }

    @Test
    public void testHasAnySetterAnnotation() throws Throwable {
        JacksonAnnotationIntrospector introspector = new JacksonAnnotationIntrospector();
        boolean has = introspector.hasAnySetterAnnotation(null);
        assertFalse(has);
    }

    @Test
    public void testHasAnyGetterAnnotation() throws Throwable {
        JacksonAnnotationIntrospector introspector = new JacksonAnnotationIntrospector();
        boolean has = introspector.hasAnyGetterAnnotation(null);
        assertFalse(has);
    }

    @Test
    public void testHasCreatorAnnotation() throws Throwable {
        JacksonAnnotationIntrospector introspector = new JacksonAnnotationIntrospector();
        boolean has = introspector.hasCreatorAnnotation(null);
        assertFalse(has);
    }

    @Test
    public void testFindCreatorBinding() throws Throwable {
        JacksonAnnotationIntrospector introspector = new JacksonAnnotationIntrospector();
        JsonCreator.Mode mode = introspector.findCreatorBinding(null);
        assertNull(mode);
    }

    @Test
    public void testIsIgnorable() throws Throwable {
        JacksonAnnotationIntrospector introspector = new JacksonAnnotationIntrospector();
        boolean ign = introspector._isIgnorable(null);
        assertFalse(ign);
    }

    @Test
    public void testClassIfExplicit() throws Throwable {
        JacksonAnnotationIntrospector introspector = new JacksonAnnotationIntrospector();
        Class<?> cls = introspector._classIfExplicit(null);
        assertNull(cls);
        
        Class<?> cls2 = introspector._classIfExplicit(String.class, String.class);
        assertNull(cls2);
    }

    @Test
    public void testPropertyNameHelper() throws Throwable {
        JacksonAnnotationIntrospector introspector = new JacksonAnnotationIntrospector();
        PropertyName pn = introspector._propertyName("", "");
        assertEquals(PropertyName.USE_DEFAULT, pn);

        PropertyName pn2 = introspector._propertyName("local", "");
        assertEquals("local", pn2.getSimpleName());

        PropertyName pn3 = introspector._propertyName("local", "ns");
        assertEquals("local", pn3.getSimpleName());
        assertEquals("ns", pn3.getNamespace());
    }

    @Test
    public void testFindConstructorName() throws Throwable {
        JacksonAnnotationIntrospector introspector = new JacksonAnnotationIntrospector();
        PropertyName name = introspector._findConstructorName(null);
        assertNull(name);
    }

    @Test
    public void testConstructStdTypeResolverBuilder() throws Throwable {
        JacksonAnnotationIntrospector introspector = new JacksonAnnotationIntrospector();
        StdTypeResolverBuilder builder = introspector._constructStdTypeResolverBuilder();
        assertNotNull(builder);
    }

    @Test
    public void testConstructNoTypeResolverBuilder() throws Throwable {
        JacksonAnnotationIntrospector introspector = new JacksonAnnotationIntrospector();
        StdTypeResolverBuilder builder = introspector._constructNoTypeResolverBuilder();
        assertNotNull(builder);
    }

    private enum MockEnum {
        @JsonProperty("v1")
        VALUE1,
        VALUE2
    }
}