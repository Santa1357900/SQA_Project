package com.fasterxml.jackson.databind.introspect;

import java.lang.annotation.Annotation;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;
import java.lang.reflect.Modifier;
import java.util.List;

import org.junit.Before;
import org.junit.Test;
import static org.junit.Assert.*;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.databind.AnnotationIntrospector;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.util.Annotations;

public class AnnotatedClassClaudeTest
{
    @Retention(RetentionPolicy.RUNTIME)
    @Target(ElementType.TYPE)
    public @interface MyClassAnno {
    }

    @Retention(RetentionPolicy.RUNTIME)
    @Target(ElementType.TYPE)
    public @interface OtherClassAnno {
    }

    public static class SimplePojo {
        public int value;
        public SimplePojo() { }
        public int getValue() { return value; }
        public void setValue(int v) { this.value = v; }
    }

    @MyClassAnno
    public static class AnnotatedBase {
    }

    public static class AnnotatedSub extends AnnotatedBase {
    }

    public static class OnlyDefaultCtorPojo {
        public OnlyDefaultCtorPojo() { }
    }

    public static class SingleArgCtorPojo {
        public final int x;
        public SingleArgCtorPojo(int x) { this.x = x; }
    }

    public static class FactoryPojo {
        public static FactoryPojo createOne() { return new FactoryPojo(); }
        @JsonIgnore
        public static FactoryPojo createIgnored() { return new FactoryPojo(); }
    }

    public static class MethodPojo {
        private String name;
        public String getName() { return name; }
        public void setName(String n) { this.name = n; }
    }

    public static class ManyArgMethodPojo {
        public String getLabel() { return "l"; }
        public void threeArgs(int a, int b, int c) { }
    }

    public static class TwoArgMethodPojo {
        public String getLabel() { return "l"; }
        public void setPair(String a, String b) { }
    }

    public static class FieldMixedPojo {
        public int pubField;
        private int privField;
    }

    public static class StaticFieldPojo {
        public static int staticField = 1;
        public int instanceField;
    }

    public static class TransientFieldPojo {
        public transient int transField;
        public int normalField;
    }

    public static class FieldBase {
        public int baseField;
    }

    public static class FieldSub extends FieldBase {
        public int subField;
    }

    private AnnotationIntrospector aintr;

    @Before
    public void setUp() throws Throwable {
        aintr = new ObjectMapper().getSerializationConfig().getAnnotationIntrospector();
    }

    // construct(): getAnnotated/getRawType/getGenericType all refer to the same Class
    @Test
    public void testConstruct_basicClass_returnsClassInfo() throws Throwable {
        AnnotatedClass ac = AnnotatedClass.construct(SimplePojo.class, aintr, null);
        assertEquals(SimplePojo.class, ac.getAnnotated());
        assertEquals(SimplePojo.class, ac.getRawType());
    }

    // constructWithoutSuperTypes(): same basic class info accessors work
    @Test
    public void testConstructWithoutSuperTypes_basicClass_returnsClassInfo() throws Throwable {
        AnnotatedClass ac = AnnotatedClass.constructWithoutSuperTypes(SimplePojo.class, aintr, null);
        assertEquals(SimplePojo.class, ac.getAnnotated());
        assertEquals(SimplePojo.class, ac.getRawType());
    }

    // getModifiers(): public class modifier bit is set
    @Test
    public void testGetModifiers_publicClass_hasPublicModifier() throws Throwable {
        AnnotatedClass ac = AnnotatedClass.construct(SimplePojo.class, aintr, null);
        assertTrue(Modifier.isPublic(ac.getModifiers()));
    }

    // getName(): returns fully qualified class name
    @Test
    public void testGetName_returnsFullyQualifiedName() throws Throwable {
        AnnotatedClass ac = AnnotatedClass.construct(SimplePojo.class, aintr, null);
        assertEquals(SimplePojo.class.getName(), ac.getName());
    }

    // getGenericType()/getRawType(): both point to same class token
    @Test
    public void testGetGenericTypeAndRawType_returnSameClass() throws Throwable {
        AnnotatedClass ac = AnnotatedClass.construct(SimplePojo.class, aintr, null);
        assertEquals(SimplePojo.class, ac.getGenericType());
        assertEquals(ac.getRawType(), ac.getGenericType());
    }

    // getAnnotation(): when introspector is null, annotation resolution is skipped
    @Test
    public void testGetAnnotation_nullIntrospector_returnsNull() throws Throwable {
        AnnotatedClass ac = AnnotatedClass.construct(AnnotatedBase.class, null, null);
        assertNull(ac.getAnnotation(MyClassAnno.class));
    }

    // getAnnotation(): annotation declared directly on class itself is found
    @Test
    public void testGetAnnotation_presentOnClassItself_returnsInstance() throws Throwable {
        AnnotatedClass ac = AnnotatedClass.construct(AnnotatedBase.class, aintr, null);
        assertNotNull(ac.getAnnotation(MyClassAnno.class));
    }

    // getAnnotation(): annotation type not present anywhere returns null
    @Test
    public void testGetAnnotation_absentAnnotation_returnsNull() throws Throwable {
        AnnotatedClass ac = AnnotatedClass.construct(AnnotatedBase.class, aintr, null);
        assertNull(ac.getAnnotation(OtherClassAnno.class));
    }

    // getAnnotation(): construct() traverses super types and finds inherited annotation
    @Test
    public void testGetAnnotation_constructIncludesSuperType_returnsInstance() throws Throwable {
        AnnotatedClass ac = AnnotatedClass.construct(AnnotatedSub.class, aintr, null);
        assertNotNull(ac.getAnnotation(MyClassAnno.class));
    }

    // getAnnotation(): constructWithoutSuperTypes() must not see super type annotations
    @Test
    public void testGetAnnotation_constructWithoutSuperTypes_excludesSuperType_returnsNull() throws Throwable {
        AnnotatedClass ac = AnnotatedClass.constructWithoutSuperTypes(AnnotatedSub.class, aintr, null);
        assertNull(ac.getAnnotation(MyClassAnno.class));
    }

    // annotations(): iterable contains the class-level annotation instance
    @Test
    public void testAnnotations_iterableContainsAddedAnnotation() throws Throwable {
        AnnotatedClass ac = AnnotatedClass.construct(AnnotatedBase.class, aintr, null);
        boolean found = false;
        for (Annotation a : ac.annotations()) {
            if (a instanceof MyClassAnno) {
                found = true;
            }
        }
        assertTrue(found);
    }

    // getAnnotations(): returns a non-null Annotations holder object
    @Test
    public void testGetAnnotations_returnsNonNullAnnotationsInstance() throws Throwable {
        AnnotatedClass ac = AnnotatedClass.construct(AnnotatedBase.class, aintr, null);
        Annotations annos = ac.getAnnotations();
        assertNotNull(annos);
    }

    // hasAnnotations(): with null introspector, no annotations are ever gathered
    @Test
    public void testHasAnnotations_noIntrospector_returnsFalse() throws Throwable {
        AnnotatedClass ac = AnnotatedClass.construct(AnnotatedBase.class, null, null);
        assertFalse(ac.hasAnnotations());
    }

    // hasAnnotations(): class with a real class-level annotation returns true
    @Test
    public void testHasAnnotations_withAnnotatedClass_returnsTrue() throws Throwable {
        AnnotatedClass ac = AnnotatedClass.construct(AnnotatedBase.class, aintr, null);
        assertTrue(ac.hasAnnotations());
    }

    // withAnnotations(): new instance uses supplied map directly, skipping resolution
    @Test
    public void testWithAnnotations_usesProvidedMapDirectly() throws Throwable {
        AnnotatedClass ac = AnnotatedClass.construct(AnnotatedBase.class, aintr, null);
        Annotation ann = AnnotatedBase.class.getAnnotation(MyClassAnno.class);
        AnnotationMap map = new AnnotationMap();
        map.addIfNotPresent(ann);
        AnnotatedClass ac2 = ac.withAnnotations(map);
        assertNotNull(ac2.getAnnotation(MyClassAnno.class));
    }

    // getDefaultConstructor(): class declaring only a no-arg constructor resolves it
    @Test
    public void testGetDefaultConstructor_present_returnsMatchingConstructor() throws Throwable {
        AnnotatedClass ac = AnnotatedClass.construct(OnlyDefaultCtorPojo.class, aintr, null);
        AnnotatedConstructor ctor = ac.getDefaultConstructor();
        assertNotNull(ctor);
        assertEquals(OnlyDefaultCtorPojo.class, ctor.getAnnotated().getDeclaringClass());
    }

    // getDefaultConstructor(): class with only a single-arg constructor has no default
    @Test
    public void testGetDefaultConstructor_onlyParamConstructor_returnsNull() throws Throwable {
        AnnotatedClass ac = AnnotatedClass.construct(SingleArgCtorPojo.class, aintr, null);
        assertNull(ac.getDefaultConstructor());
    }

    // getConstructors(): single-arg constructor is reported in the list
    @Test
    public void testGetConstructors_singleArgConstructor_returnsOneEntry() throws Throwable {
        AnnotatedClass ac = AnnotatedClass.construct(SingleArgCtorPojo.class, aintr, null);
        List<AnnotatedConstructor> ctors = ac.getConstructors();
        assertEquals(1, ctors.size());
        assertEquals(1, ctors.get(0).getAnnotated().getParameterTypes().length);
    }

    // getConstructors(): class with only default constructor yields empty list
    @Test
    public void testGetConstructors_onlyDefaultConstructor_returnsEmptyList() throws Throwable {
        AnnotatedClass ac = AnnotatedClass.construct(OnlyDefaultCtorPojo.class, aintr, null);
        assertEquals(0, ac.getConstructors().size());
    }

    // getStaticMethods(): a factory method marked @JsonIgnore must be excluded
    @Test
    public void testGetStaticMethods_excludesIgnoredFactoryMethod() throws Throwable {
        AnnotatedClass ac = AnnotatedClass.construct(FactoryPojo.class, aintr, null);
        List<AnnotatedMethod> statics = ac.getStaticMethods();
        boolean hasOne = false;
        boolean hasIgnored = false;
        for (int i = 0; i < statics.size(); i++) {
            String n = statics.get(i).getName();
            if ("createOne".equals(n)) { hasOne = true; }
            if ("createIgnored".equals(n)) { hasIgnored = true; }
        }
        assertTrue(hasOne);
        assertFalse(hasIgnored);
    }

    // getStaticMethods(): class with no static methods yields empty list
    @Test
    public void testGetStaticMethods_noStaticMethods_returnsEmptyList() throws Throwable {
        AnnotatedClass ac = AnnotatedClass.construct(SimplePojo.class, aintr, null);
        assertEquals(0, ac.getStaticMethods().size());
    }

    // memberMethods(): getter and setter are both discovered
    @Test
    public void testMemberMethods_iterableIncludesGetterAndSetter() throws Throwable {
        AnnotatedClass ac = AnnotatedClass.construct(MethodPojo.class, aintr, null);
        boolean hasGetter = false;
        boolean hasSetter = false;
        for (AnnotatedMethod m : ac.memberMethods()) {
            if ("getName".equals(m.getName())) { hasGetter = true; }
            if ("setName".equals(m.getName())) { hasSetter = true; }
        }
        assertTrue(hasGetter);
        assertTrue(hasSetter);
    }

    // getMemberMethodCount(): count matches number of methods returned by memberMethods()
    @Test
    public void testGetMemberMethodCount_matchesIterableSize() throws Throwable {
        AnnotatedClass ac = AnnotatedClass.construct(MethodPojo.class, aintr, null);
        int iterated = 0;
        for (AnnotatedMethod m : ac.memberMethods()) {
            iterated++;
        }
        assertEquals(iterated, ac.getMemberMethodCount());
        assertEquals(2, ac.getMemberMethodCount());
    }

    // _isIncludableMemberMethod: a method with more than two parameters is always excluded
    @Test
    public void testGetMemberMethodCount_methodWithMoreThanTwoArgsExcluded() throws Throwable {
        AnnotatedClass ac = AnnotatedClass.construct(ManyArgMethodPojo.class, aintr, null);
        assertEquals(1, ac.getMemberMethodCount());
        assertNull(ac.findMethod("threeArgs", new Class<?>[] { int.class, int.class, int.class }));
    }

    // per class Javadoc contract ("0 or 1 arguments"), a 2-argument member method must not be kept
    @Test
    public void testGetMemberMethodCount_methodWithExactlyTwoArgsExcludedPerContract() throws Throwable {
        AnnotatedClass ac = AnnotatedClass.construct(TwoArgMethodPojo.class, aintr, null);
        assertEquals(1, ac.getMemberMethodCount());
    }

    // findMethod(): existing zero-arg method is located by name and parameter types
    @Test
    public void testFindMethod_existingZeroArgMethod_returnsMethod() throws Throwable {
        AnnotatedClass ac = AnnotatedClass.construct(MethodPojo.class, aintr, null);
        AnnotatedMethod m = ac.findMethod("getName", new Class<?>[0]);
        assertNotNull(m);
        assertEquals("getName", m.getName());
    }

    // findMethod(): a method name that does not exist returns null
    @Test
    public void testFindMethod_nonExistentMethod_returnsNull() throws Throwable {
        AnnotatedClass ac = AnnotatedClass.construct(MethodPojo.class, aintr, null);
        assertNull(ac.findMethod("doesNotExist", new Class<?>[0]));
    }

    // findMethod(): matching name but wrong parameter types returns null
    @Test
    public void testFindMethod_wrongParamTypes_returnsNull() throws Throwable {
        AnnotatedClass ac = AnnotatedClass.construct(MethodPojo.class, aintr, null);
        assertNull(ac.findMethod("getName", new Class<?>[] { String.class }));
    }

    // getFieldCount(): both public and private non-static fields are counted (no visibility filter)
    @Test
    public void testGetFieldCount_publicAndPrivateFieldsIncluded() throws Throwable {
        AnnotatedClass ac = AnnotatedClass.construct(FieldMixedPojo.class, aintr, null);
        assertEquals(2, ac.getFieldCount());
    }

    // getFieldCount(): static field must be excluded
    @Test
    public void testGetFieldCount_staticFieldExcluded() throws Throwable {
        AnnotatedClass ac = AnnotatedClass.construct(StaticFieldPojo.class, aintr, null);
        assertEquals(1, ac.getFieldCount());
    }

    // getFieldCount(): transient field must be excluded
    @Test
    public void testGetFieldCount_transientFieldExcluded() throws Throwable {
        AnnotatedClass ac = AnnotatedClass.construct(TransientFieldPojo.class, aintr, null);
        assertEquals(1, ac.getFieldCount());
    }

    // fields(): iterable exposes the expected field name
    @Test
    public void testFields_iterableContainsFieldName() throws Throwable {
        AnnotatedClass ac = AnnotatedClass.construct(TransientFieldPojo.class, aintr, null);
        boolean found = false;
        for (AnnotatedField f : ac.fields()) {
            if ("normalField".equals(f.getName())) {
                found = true;
            }
        }
        assertTrue(found);
    }

    // fields/constructors resolution uses real reflection hierarchy regardless of _superTypes mode
    @Test
    public void testConstructWithoutSuperTypes_fieldsStillResolvedFromRealSuperclass() throws Throwable {
        AnnotatedClass ac = AnnotatedClass.constructWithoutSuperTypes(FieldSub.class, aintr, null);
        assertEquals(2, ac.getFieldCount());
    }

    // toString(): matches the documented literal format including full class name
    @Test
    public void testToString_matchesExpectedFormat() throws Throwable {
        AnnotatedClass ac = AnnotatedClass.construct(SimplePojo.class, aintr, null);
        String expected = "[AnnotedClass " + SimplePojo.class.getName() + "]";
        assertEquals(expected, ac.toString());
    }
}
