package com.fasterxml.jackson.databind.jsontype.impl;

import org.junit.Test;
import static org.junit.Assert.*;

import java.util.Collection;
import java.util.ArrayList;
import java.util.List;

import com.fasterxml.jackson.databind.AnnotationIntrospector;
import com.fasterxml.jackson.databind.JavaType;
import com.fasterxml.jackson.databind.cfg.MapperConfig;
import com.fasterxml.jackson.databind.introspect.AnnotatedClass;
import com.fasterxml.jackson.databind.introspect.AnnotatedMember;
import com.fasterxml.jackson.databind.introspect.AnnotatedClassResolver;
import com.fasterxml.jackson.databind.jsontype.NamedType;
import com.fasterxml.jackson.databind.type.TypeFactory;

public class StdSubtypeResolverTest {

    @Test
    public void testRegisterSubtypesWithClasses() throws Throwable {
        StdSubtypeResolver resolver = new StdSubtypeResolver();
        resolver.registerSubtypes(String.class, Integer.class);
        assertNotNull(resolver._registeredSubtypes);
        assertEquals(2, resolver._registeredSubtypes.size());
    }

    @Test
    public void testRegisterSubtypesWithNamedTypes() throws Throwable {
        StdSubtypeResolver resolver = new StdSubtypeResolver();
        NamedType nt1 = new NamedType(String.class, "string");
        NamedType nt2 = new NamedType(Integer.class, "int");
        resolver.registerSubtypes(nt1, nt2);
        assertNotNull(resolver._registeredSubtypes);
        assertEquals(2, resolver._registeredSubtypes.size());
    }

    @Test
    public void testCollectAndResolveSubtypesByClassWithNullBaseType() throws Throwable {
        StdSubtypeResolver resolver = new StdSubtypeResolver();
        MapperConfig<?> config = new DummyMapperConfig();
        DummyAnnotatedMember property = new DummyAnnotatedMember(String.class);

        Collection<NamedType> result = resolver.collectAndResolveSubtypesByClass(config, property, null);
        assertNotNull(result);
        assertFalse(result.isEmpty());
    }

    @Test
    public void testCollectAndResolveSubtypesByClassWithAnnotatedClass() throws Throwable {
        StdSubtypeResolver resolver = new StdSubtypeResolver();
        MapperConfig<?> config = new DummyMapperConfig();
        AnnotatedClass ac = AnnotatedClassResolver.resolveWithoutSuperTypes(config, String.class);

        resolver.registerSubtypes(new NamedType(String.class, "str"));
        Collection<NamedType> result = resolver.collectAndResolveSubtypesByClass(config, ac);
        assertNotNull(result);
        assertFalse(result.isEmpty());
    }

    @Test
    public void testCollectAndResolveSubtypesByTypeIdWithProperty() throws Throwable {
        StdSubtypeResolver resolver = new StdSubtypeResolver();
        MapperConfig<?> config = new DummyMapperConfig();
        DummyAnnotatedMember property = new DummyAnnotatedMember(Number.class);
        JavaType baseType = TypeFactory.defaultInstance().constructType(Number.class);

        resolver.registerSubtypes(new NamedType(Integer.class, "integer"));
        Collection<NamedType> result = resolver.collectAndResolveSubtypesByTypeId(config, property, baseType);
        assertNotNull(result);
        assertFalse(result.isEmpty());
    }

    @Test
    public void testCollectAndResolveSubtypesByTypeIdWithAnnotatedClass() throws Throwable {
        StdSubtypeResolver resolver = new StdSubtypeResolver();
        MapperConfig<?> config = new DummyMapperConfig();
        AnnotatedClass ac = AnnotatedClassResolver.resolveWithoutSuperTypes(config, Number.class);

        resolver.registerSubtypes(new NamedType(Integer.class, "integer"));
        Collection<NamedType> result = resolver.collectAndResolveSubtypesByTypeId(config, ac);
        assertNotNull(result);
        assertFalse(result.isEmpty());
    }

    private static class DummyMapperConfig extends MapperConfig<DummyMapperConfig> {
        private static final long serialVersionUID = 1L;

        public DummyMapperConfig() {
            super(null, null);
        }

        @Override
        public AnnotationIntrospector getAnnotationIntrospector() {
            return new AnnotationIntrospector() {
                private static final long serialVersionUID = 1L;
                @Override
                public String version() {
                    return "1.0";
                }
            };
        }

        @Override
        public boolean isAnnotationProcessingEnabled() {
            return false;
        }

        @Override
        public com.fasterxml.jackson.databind.cfg.MapperConfigBase<Class<?>, com.fasterxml.jackson.databind.introspect.ClassIntrospector.MixInResolver, DummyMapperConfig> setMixInResolver(com.fasterxml.jackson.databind.introspect.ClassIntrospector.MixInResolver mixInResolver) {
            return null;
        }

        @Override
        public Class<?> findMixInClassFor(Class<?> cls) {
            return null;
        }

        @Override
        public int mixInCount() {
            return 0;
        }
    }

    private static class DummyAnnotatedMember extends AnnotatedMember {
        private static final long serialVersionUID = 1L;
        protected final Class<?> _type;

        public DummyAnnotatedMember(Class<?> type) {
            super(null, null);
            _type = type;
        }

        @Override
        public AnnotatedMember withAnnotations(com.fasterxml.jackson.databind.util.Annotations anns) {
            return this;
        }

        @Override
        public void setValue(Object pojo, Object value) throws UnsupportedOperationException, IllegalArgumentException {
        }

        @Override
        public Object getValue(Object pojo) throws UnsupportedOperationException, IllegalArgumentException {
            return null;
        }

        @Override
        public Class<?> getRawType() {
            return _type;
        }

        @Override
        public JavaType getType() {
            return TypeFactory.defaultInstance().constructType(_type);
        }

        @Override
        public java.lang.reflect.Member getMember() {
            return null;
        }

        @Override
        public com.fasterxml.jackson.databind.introspect.Annotated element() {
            return null;
        }
    }
}