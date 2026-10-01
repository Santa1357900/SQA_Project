package com.fasterxml.jackson.databind.deser;

import java.util.List;
import java.util.ArrayList;
import java.util.Map;
import java.util.HashMap;

import org.junit.Before;
import org.junit.Test;
import static org.junit.Assert.*;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonAnySetter;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonManagedReference;
import com.fasterxml.jackson.annotation.JsonBackReference;
import com.fasterxml.jackson.annotation.JsonIdentityInfo;
import com.fasterxml.jackson.annotation.ObjectIdGenerators;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.JsonMappingException;
import com.fasterxml.jackson.databind.annotation.JsonDeserialize;
import com.fasterxml.jackson.databind.annotation.JsonPOJOBuilder;
import com.fasterxml.jackson.databind.cfg.DeserializerFactoryConfig;

public class BeanDeserializerFactoryClaudeTest {

    private ObjectMapper mapper;

    @Before
    public void setUp() throws Throwable {
        mapper = new ObjectMapper();
    }

    // ---- fixtures ----

    public static class SimpleBean {
        private int id;
        private String name;
        public int getId() { return id; }
        public void setId(int id) { this.id = id; }
        public String getName() { return name; }
        public void setName(String name) { this.name = name; }
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class IgnoreUnknownBean {
        private String name;
        public String getName() { return name; }
        public void setName(String name) { this.name = name; }
    }

    @JsonIgnoreProperties({"secret"})
    public static class ExplicitIgnoreBean {
        private String name;
        private String secret;
        public String getName() { return name; }
        public void setName(String name) { this.name = name; }
        public String getSecret() { return secret; }
        public void setSecret(String secret) { this.secret = secret; }
    }

    public static class JsonIgnoreBean {
        private String name;
        private String hidden;
        public String getName() { return name; }
        public void setName(String name) { this.name = name; }
        @JsonIgnore
        public String getHidden() { return hidden; }
        @JsonIgnore
        public void setHidden(String hidden) { this.hidden = hidden; }
    }

    public static class AnySetterBean {
        private String name;
        private Map<String, Object> extra = new HashMap<String, Object>();
        public String getName() { return name; }
        public void setName(String name) { this.name = name; }
        @JsonAnySetter
        public void setExtra(String key, Object value) { extra.put(key, value); }
        public Map<String, Object> getExtra() { return extra; }
    }

    public static class RenamedBean {
        private String value;
        @JsonProperty("val")
        public String getValue() { return value; }
        @JsonProperty("val")
        public void setValue(String value) { this.value = value; }
    }

    public static class CreatorBean {
        private final String name;
        private final int age;
        @JsonCreator
        public CreatorBean(@JsonProperty("name") String name, @JsonProperty("age") int age) {
            this.name = name;
            this.age = age;
        }
        public String getName() { return name; }
        public int getAge() { return age; }
    }

    public static class GetterAsSetterBean {
        private List<String> items = new ArrayList<String>();
        public List<String> getItems() { return items; }
    }

    public static class MapGetterBean {
        private Map<String, String> data = new HashMap<String, String>();
        public Map<String, String> getData() { return data; }
    }

    public static class EmptyBean {
    }

    public abstract static class AbstractBean {
        public abstract String getName();
    }

    public interface SimpleInterface {
        String getName();
    }

    public static class PersonBuilder {
        private String name;
        private int age;
        public PersonBuilder withName(String name) { this.name = name; return this; }
        public PersonBuilder withAge(int age) { this.age = age; return this; }
        public Person build() { return new Person(name, age); }
    }

    @JsonDeserialize(builder = PersonBuilder.class)
    public static class Person {
        private final String name;
        private final int age;
        Person(String name, int age) { this.name = name; this.age = age; }
        public String getName() { return name; }
        public int getAge() { return age; }
    }

    @JsonPOJOBuilder(buildMethodName = "create", withPrefix = "set")
    public static class PersonBuilder2 {
        private String name;
        public PersonBuilder2 setName(String name) { this.name = name; return this; }
        public Person2 create() { return new Person2(name); }
    }

    @JsonDeserialize(builder = PersonBuilder2.class)
    public static class Person2 {
        private final String name;
        Person2(String name) { this.name = name; }
        public String getName() { return name; }
    }

    public static class ParentRef {
        private String name;
        @JsonManagedReference
        private List<ChildRef> children;
        public String getName() { return name; }
        public void setName(String name) { this.name = name; }
        public List<ChildRef> getChildren() { return children; }
        public void setChildren(List<ChildRef> children) { this.children = children; }
    }

    public static class ChildRef {
        private String name;
        @JsonBackReference
        private ParentRef parent;
        public String getName() { return name; }
        public void setName(String name) { this.name = name; }
        public ParentRef getParent() { return parent; }
        public void setParent(ParentRef parent) { this.parent = parent; }
    }

    @JsonIdentityInfo(generator = ObjectIdGenerators.PropertyGenerator.class, property = "id")
    public static class NodeBean {
        private int id;
        private String name;
        private NodeBean self;
        public int getId() { return id; }
        public void setId(int id) { this.id = id; }
        public String getName() { return name; }
        public void setName(String name) { this.name = name; }
        public NodeBean getSelf() { return self; }
        public void setSelf(NodeBean self) { this.self = self; }
    }

    @JsonIdentityInfo(generator = ObjectIdGenerators.IntSequenceGenerator.class, property = "@id")
    public static class RingBean {
        private String name;
        private RingBean next;
        public String getName() { return name; }
        public void setName(String name) { this.name = name; }
        public RingBean getNext() { return next; }
        public void setNext(RingBean next) { this.next = next; }
    }

    static class SubFactory extends BeanDeserializerFactory {
        public SubFactory(DeserializerFactoryConfig config) { super(config); }
    }

    enum SampleEnum { A, B }

    // ---- tests ----

    // covers: public static final field 'instance'
    @Test
    public void testInstance_isNotNullAndSameClass() throws Throwable {
        assertNotNull(BeanDeserializerFactory.instance);
        assertEquals(BeanDeserializerFactory.class, BeanDeserializerFactory.instance.getClass());
    }

    // covers: withConfig branch where getClass()==BeanDeserializerFactory.class -> new instance
    @Test
    public void testWithConfig_differentConfig_returnsNewInstance() throws Throwable {
        DeserializerFactoryConfig newConfig = new DeserializerFactoryConfig();
        DeserializerFactory result = BeanDeserializerFactory.instance.withConfig(newConfig);
        assertNotSame(BeanDeserializerFactory.instance, result);
        assertTrue(result instanceof BeanDeserializerFactory);
    }

    // covers: withConfig subtype branch -> throws IllegalStateException
    @Test
    public void testWithConfig_subclassWithoutOverride_throwsIllegalStateException() throws Throwable {
        SubFactory sub = new SubFactory(new DeserializerFactoryConfig());
        try {
            sub.withConfig(new DeserializerFactoryConfig());
            fail("expected IllegalStateException");
        } catch (IllegalStateException expected) {
            assertTrue(expected.getMessage().contains("has not properly overridden"));
        }
    }

    // covers: default FAIL_ON_UNKNOWN_PROPERTIES -> unrecognized property throws
    @Test
    public void testReadValue_unknownProperty_throwsJsonMappingException() throws Throwable {
        try {
            mapper.readValue("{\"id\":1,\"extra\":true}", SimpleBean.class);
            fail("expected JsonMappingException");
        } catch (JsonMappingException expected) {
        }
    }

    // covers: buildBeanDeserializer basic path, setters set correctly
    @Test
    public void testReadValue_simplePojo_setsFieldsCorrectly() throws Throwable {
        SimpleBean bean = mapper.readValue("{\"id\":5,\"name\":\"abc\"}", SimpleBean.class);
        assertEquals(5, bean.getId());
        assertEquals("abc", bean.getName());
    }

    // covers: addBeanProps ignoreUnknown class-level annotation branch
    @Test
    public void testReadValue_ignoreUnknownClassAnnotation_succeeds() throws Throwable {
        IgnoreUnknownBean bean = mapper.readValue("{\"name\":\"a\",\"extra\":true}", IgnoreUnknownBean.class);
        assertEquals("a", bean.getName());
    }

    // covers: addBeanProps explicit ignored property list branch
    @Test
    public void testReadValue_explicitIgnoredProperty_propertyNotSet() throws Throwable {
        ExplicitIgnoreBean bean = mapper.readValue("{\"name\":\"x\",\"secret\":\"y\"}", ExplicitIgnoreBean.class);
        assertEquals("x", bean.getName());
        assertNull(bean.getSecret());
    }

    // covers: addBeanProps @JsonIgnore property branch (no exception, field not set)
    @Test
    public void testReadValue_jsonIgnoreOnProperty_noExceptionAndFieldNotSet() throws Throwable {
        JsonIgnoreBean bean = mapper.readValue("{\"name\":\"a\",\"hidden\":\"b\"}", JsonIgnoreBean.class);
        assertEquals("a", bean.getName());
        assertNull(bean.getHidden());
    }

    // covers: addBeanProps anySetter branch
    @Test
    public void testReadValue_anySetter_capturesUnknownProperties() throws Throwable {
        AnySetterBean bean = mapper.readValue("{\"name\":\"a\",\"foo\":\"bar\"}", AnySetterBean.class);
        assertEquals("a", bean.getName());
        assertEquals("bar", bean.getExtra().get("foo"));
    }

    // covers: @JsonProperty rename affecting property definitions
    @Test
    public void testReadValue_renamedProperty_setsCorrectField() throws Throwable {
        RenamedBean bean = mapper.readValue("{\"val\":\"x\"}", RenamedBean.class);
        assertEquals("x", bean.getValue());
    }

    // covers: addBeanProps creator-property (constructor parameter) branch
    @Test
    public void testReadValue_creatorBasedConstructor_setsFieldsCorrectly() throws Throwable {
        CreatorBean bean = mapper.readValue("{\"name\":\"n\",\"age\":5}", CreatorBean.class);
        assertEquals("n", bean.getName());
        assertEquals(5, bean.getAge());
    }

    // covers: useGettersAsSetters branch for Collection typed getter
    @Test
    public void testReadValue_getterAsSetterCollection_mutatesExistingList() throws Throwable {
        GetterAsSetterBean bean = mapper.readValue("{\"items\":[\"a\",\"b\"]}", GetterAsSetterBean.class);
        assertEquals(2, bean.getItems().size());
        assertEquals("a", bean.getItems().get(0));
    }

    // covers: useGettersAsSetters branch for Map typed getter
    @Test
    public void testReadValue_getterAsSetterMap_mutatesExistingMap() throws Throwable {
        MapGetterBean bean = mapper.readValue("{\"data\":{\"k\":\"v\"}}", MapGetterBean.class);
        assertEquals("v", bean.getData().get("k"));
    }

    // covers: buildBeanDeserializer with zero bean properties
    @Test
    public void testReadValue_emptyBean_noException() throws Throwable {
        EmptyBean bean = mapper.readValue("{}", EmptyBean.class);
        assertNotNull(bean);
    }

    // covers: createBeanDeserializer abstract branch -> buildAbstract() fails on use
    @Test
    public void testReadValue_abstractClassWithoutConcrete_throwsJsonMappingException() throws Throwable {
        try {
            mapper.readValue("{}", AbstractBean.class);
            fail("expected JsonMappingException");
        } catch (JsonMappingException expected) {
        }
    }

    // covers: createBeanDeserializer interface (abstract) type branch
    @Test
    public void testReadValue_interfaceType_throwsJsonMappingException() throws Throwable {
        try {
            mapper.readValue("{}", SimpleInterface.class);
            fail("expected JsonMappingException");
        } catch (JsonMappingException expected) {
        }
    }

    // covers: buildThrowableDeserializer basic message handling
    @Test
    public void testReadValue_exceptionWithMessage_setsMessage() throws Throwable {
        Exception ex = mapper.readValue("{\"message\":\"boom\"}", Exception.class);
        assertEquals("boom", ex.getMessage());
    }

    // covers: buildThrowableDeserializer addIgnorable localizedMessage/suppressed branches
    @Test
    public void testReadValue_exceptionIgnorableProperties_noException() throws Throwable {
        String json = "{\"message\":\"m\",\"localizedMessage\":\"m\",\"suppressed\":[]}";
        Exception ex = mapper.readValue(json, Exception.class);
        assertEquals("m", ex.getMessage());
    }

    // covers: buildThrowableDeserializer initCause property construction branch
    @Test
    public void testReadValue_exceptionWithCause_setsCause() throws Throwable {
        String json = "{\"message\":\"outer\",\"cause\":{\"message\":\"inner\"}}";
        Exception ex = mapper.readValue(json, Exception.class);
        assertNotNull(ex.getCause());
        assertEquals("inner", ex.getCause().getMessage());
    }

    // covers: createBuilderBasedDeserializer / buildBuilderBasedDeserializer default build method
    @Test
    public void testReadValue_builderBased_buildsCorrectObject() throws Throwable {
        Person p = mapper.readValue("{\"name\":\"Bob\",\"age\":30}", Person.class);
        assertEquals("Bob", p.getName());
        assertEquals(30, p.getAge());
    }

    // covers: buildBuilderBasedDeserializer custom buildMethodName branch (builderConfig != null)
    @Test
    public void testReadValue_builderBasedCustomBuildMethod_buildsCorrectObject() throws Throwable {
        Person2 p = mapper.readValue("{\"name\":\"Ann\"}", Person2.class);
        assertEquals("Ann", p.getName());
    }

    // covers: addReferenceProperties managed/back reference linking
    @Test
    public void testReadValue_managedAndBackReference_linksParentChild() throws Throwable {
        String json = "{\"name\":\"p\",\"children\":[{\"name\":\"c1\"}]}";
        ParentRef parent = mapper.readValue(json, ParentRef.class);
        assertEquals(1, parent.getChildren().size());
        assertSame(parent, parent.getChildren().get(0).getParent());
    }

    // covers: addObjectIdReader PropertyGenerator branch (idProp bound to real property)
    @Test
    public void testReadValue_objectIdPropertyGenerator_resolvesSelfReference() throws Throwable {
        String json = "{\"id\":1,\"name\":\"root\",\"self\":1}";
        NodeBean node = mapper.readValue(json, NodeBean.class);
        assertSame(node, node.getSelf());
    }

    // covers: addObjectIdReader default (non-property) generator branch
    @Test
    public void testReadValue_objectIdDefaultGenerator_resolvesCycleReference() throws Throwable {
        String json = "{\"@id\":1,\"name\":\"a\",\"next\":{\"@id\":2,\"name\":\"b\",\"next\":1}}";
        RingBean a = mapper.readValue(json, RingBean.class);
        assertSame(a, a.getNext().getNext());
    }

    // covers: isPotentialBeanType normal class -> returns true
    @Test
    public void testIsPotentialBeanType_normalClass_returnsTrue() throws Throwable {
        assertTrue(BeanDeserializerFactory.instance.isPotentialBeanType(SimpleBean.class));
    }

    // covers: isPotentialBeanType primitive type -> throws IllegalArgumentException
    @Test
    public void testIsPotentialBeanType_primitiveType_throwsIllegalArgumentException() throws Throwable {
        try {
            BeanDeserializerFactory.instance.isPotentialBeanType(int.class);
            fail("expected IllegalArgumentException");
        } catch (IllegalArgumentException expected) {
            assertTrue(expected.getMessage().contains("Bean"));
        }
    }

    // covers: isPotentialBeanType array type -> throws IllegalArgumentException
    @Test
    public void testIsPotentialBeanType_arrayType_throwsIllegalArgumentException() throws Throwable {
        try {
            BeanDeserializerFactory.instance.isPotentialBeanType(int[].class);
            fail("expected IllegalArgumentException");
        } catch (IllegalArgumentException expected) {
            assertTrue(expected.getMessage().contains("Bean"));
        }
    }

    // covers: isPotentialBeanType enum type -> throws IllegalArgumentException
    @Test
    public void testIsPotentialBeanType_enumType_throwsIllegalArgumentException() throws Throwable {
        try {
            BeanDeserializerFactory.instance.isPotentialBeanType(SampleEnum.class);
            fail("expected IllegalArgumentException");
        } catch (IllegalArgumentException expected) {
            assertTrue(expected.getMessage().contains("Bean"));
        }
    }

    // covers: isPotentialBeanType local (non-static) class -> throws IllegalArgumentException
    @Test
    public void testIsPotentialBeanType_localClass_throwsIllegalArgumentException() throws Throwable {
        class LocalType {
            public String value;
        }
        try {
            BeanDeserializerFactory.instance.isPotentialBeanType(LocalType.class);
            fail("expected IllegalArgumentException");
        } catch (IllegalArgumentException expected) {
            assertTrue(expected.getMessage().contains("Bean"));
        }
    }
}
