package com.fasterxml.jackson.databind.introspect;

import org.junit.Test;
import static org.junit.Assert.*;

import java.lang.annotation.Annotation;
import java.lang.reflect.Modifier;
import java.util.Collections;
import java.util.List;

import com.fasterxml.jackson.databind.AnnotationIntrospector;
import com.fasterxml.jackson.databind.util.Annotations;

public class AnnotatedClassTest {

    private static class DummySampleClass {
        public int publicField;
        private int privateField;
        protected int protectedField;
        int packageField;
        
        public DummySampleClass() { }
        public DummySampleClass(int x) { }
        
        public void publicMethod() { }
        public void publicMethodWithArg(int x) { }
        
        public static void staticMethod() { }
    }

    @Test
    public void testConstructAndBasicGetters() throws Throwable {
        AnnotatedClass ac = AnnotatedClass.construct(DummySampleClass.class, null, null);
        assertNotNull(ac);
        assertEquals(DummySampleClass.class, ac.getAnnotated());
        assertEquals(DummySampleClass.class.getName(), ac.getName());
        assertEquals(DummySampleClass.class, ac.getRawType());
        assertEquals(DummySampleClass.class, ac.getGenericType());
        assertEquals(DummySampleClass.class.getModifiers(), ac.getModifiers());
        assertTrue(ac.toString().contains("DummySampleClass"));
    }

    @Test
    public void testConstructWithoutSuperTypes() throws Throwable {
        AnnotatedClass ac = AnnotatedClass.constructWithoutSuperTypes(DummySampleClass.class, null, null);
        assertNotNull(ac);
        assertEquals(DummySampleClass.class, ac.getAnnotated());
    }

    @Test
    public void testWithAnnotations() throws Throwable {
        AnnotatedClass ac = AnnotatedClass.construct(DummySampleClass.class, null, null);
        AnnotationMap annMap = new AnnotationMap();
        AnnotatedClass ac2 = ac.withAnnotations(annMap);
        assertNotNull(ac2);
        assertNotSame(ac, ac2);
        assertEquals(annMap, ac2.getAllAnnotations());
    }

    @Test
    public void testAnnotationsAccessors() throws Throwable {
        AnnotatedClass ac = AnnotatedClass.construct(DummySampleClass.class, null, null);
        assertNotNull(ac.annotations());
        assertNotNull(ac.getAnnotations());
        assertFalse(ac.hasAnnotations());
        assertNull(ac.getAnnotation(Deprecated.class));
    }

    @Test
    public void testCreatorsResolution() throws Throwable {
        AnnotatedClass ac = AnnotatedClass.construct(DummySampleClass.class, null, null);
        AnnotatedConstructor defaultCtor = ac.getDefaultConstructor();
        assertNotNull(defaultCtor);

        List<AnnotatedConstructor> ctors = ac.getConstructors();
        assertNotNull(ctors);
        assertFalse(ctors.isEmpty());

        List<AnnotatedMethod> staticMethods = ac.getStaticMethods();
        assertNotNull(staticMethods);
    }

    @Test
    public void testMemberMethodsResolution() throws Throwable {
        AnnotatedClass ac = AnnotatedClass.construct(DummySampleClass.class, null, null);
        Iterable<AnnotatedMethod> methods = ac.memberMethods();
        assertNotNull(methods);
        int count = ac.getMemberMethodCount();
        assertTrue(count >= 0);

        AnnotatedMethod found = ac.findMethod("publicMethod", new Class<?>[0]);
        assertNotNull(found);

        AnnotatedMethod notFound = ac.findMethod("nonExistentMethod", new Class<?>[0]);
        assertNull(notFound);
    }

    @Test
    public void testFieldsResolution() throws Throwable {
        AnnotatedClass ac = AnnotatedClass.construct(DummySampleClass.class, null, null);
        int fieldCount = ac.getFieldCount();
        assertTrue(fieldCount >= 0);

        Iterable<AnnotatedField> fields = ac.fields();
        assertNotNull(fields);
    }

    @Test
    public void testConstructWithAnnotationIntrospector() throws Throwable {
        AnnotationIntrospector ai = new AnnotationIntrospector() {
            private static final long serialVersionUID = 1L;
        };
        AnnotatedClass ac = AnnotatedClass.construct(DummySampleClass.class, ai, null);
        assertNotNull(ac);
        assertNotNull(ac.getDefaultConstructor());
        assertNotNull(ac.getConstructors());
        assertNotNull(ac.getStaticMethods());
    }

    @Test
    public void testMixInResolverHandling() throws Throwable {
        ClassIntrospector.MixInResolver mir = new ClassIntrospector.MixInResolver() {
            public Class<?> findMixInClassFor(Class<?> cls) {
                if (cls == DummySampleClass.class) {
                    return DummyMixIn.class;
                }
                return null;
            }
        };
        AnnotatedClass ac = AnnotatedClass.construct(DummySampleClass.class, null, mir);
        assertNotNull(ac);
        assertNotNull(ac.fields());
    }

    private static class DummyMixIn {
        public int publicField;
    }
}