package com.fasterxml.jackson.databind.deser;

import java.lang.reflect.InvocationHandler;
import java.lang.reflect.Method;
import java.lang.reflect.Proxy;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.junit.Test;
import static org.junit.Assert.*;

import com.fasterxml.jackson.annotation.JsonAnySetter;
import com.fasterxml.jackson.annotation.JsonBackReference;
import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonIdentityInfo;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonIgnoreType;
import com.fasterxml.jackson.annotation.JsonManagedReference;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.annotation.JsonView;
import com.fasterxml.jackson.annotation.ObjectIdGenerators;
import com.fasterxml.jackson.databind.BeanDescription;
import com.fasterxml.jackson.databind.DeserializationConfig;
import com.fasterxml.jackson.databind.JavaType;
import com.fasterxml.jackson.databind.JsonMappingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.annotation.JsonDeserialize;
import com.fasterxml.jackson.databind.annotation.JsonPOJOBuilder;
import com.fasterxml.jackson.databind.cfg.DeserializerFactoryConfig;

public class BeanDeserializerFactoryClaudeTest {

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

    public static class AnySetterBean {
        private Map<String, Object> extra = new HashMap<String, Object>();
        private String name;
        public String getName() { return name; }
        public void setName(String name) { this.name = name; }
        @JsonAnySetter
        public void setExtra(String key, Object value) { extra.put(key, value); }
        public Map<String, Object> getExtra() { return extra; }
    }

    public static class MyException extends Exception {
        private String extra;
        public MyException() { super(); }
        public String getExtra() { return extra; }
        public void setExtra(String extra) { this.extra = extra; }
    }

    @JsonIdentityInfo(generator = ObjectIdGenerators.IntSequenceGenerator.class, property = "@id")
    public static class Node {
        public int value;
        public Node next;
        public Node() { }
    }

    @JsonIdentityInfo(generator = ObjectIdGenerators.PropertyGenerator.class, property = "id")
    public static class PropIdBean {
        public int id;
        public String name;
        public PropIdBean() { }
    }

    @JsonIdentityInfo(generator = ObjectIdGenerators.PropertyGenerator.class, property = "missingProp")
    public static class BadPropIdBean {
        public int id;
        public BadPropIdBean() { }
    }

    public static class Parent {
        public String name;
        @JsonManagedReference
        public List<Child> children;
        public Parent() { }
    }

    public static class Child {
        public String name;
        @JsonBackReference
        public Parent parent;
        public Child() { }
    }

    public static class PublicView { }

    public static class ViewBean {
        @JsonView(PublicView.class)
        public String name;
        public ViewBean() { }
    }

    @JsonIgnoreType
    public static class SecretType {
        public String data;
    }

    public static class HolderBean {
        public String name;
        public SecretType secret;
        public HolderBean() { }
    }

    public static class CreatorBean {
        private final int id;
        private final String name;
        @JsonCreator
        public CreatorBean(@JsonProperty("id") int id, @JsonProperty("name") String name) {
            this.id = id;
            this.name = name;
        }
        public int getId() { return id; }
        public String getName() { return name; }
    }

    public static class FieldBean {
        public int id;
        public String name;
    }

    @JsonDeserialize(builder = BuilderBean.Builder.class)
    public static class BuilderBean {
        private final String name;
        private BuilderBean(String name) { this.name = name; }
        public String getName() { return name; }

        @JsonPOJOBuilder(withPrefix = "with")
        public static class Builder {
            private String name;
            public Builder withName(String name) { this.name = name; return this; }
            public BuilderBean build() { return new BuilderBean(name); }
        }
    }

    public static interface Greeter {
        String greet();
    }

    // ทดสอบว่า instance เป็น BeanDeserializerFactory ตัวเดียวที่ถูกแชร์
    @Test
    public void testInstance_isBeanDeserializerFactoryClass() throws Throwable {
        assertNotNull(BeanDeserializerFactory.instance);
        assertSame(BeanDeserializerFactory.class, BeanDeserializerFactory.instance.getClass());
    }

    // withConfig: ส่ง config เดิม (== เดิม) ต้องคืน instance เดิม
    @Test
    public void testWithConfig_sameConfig_returnsSameInstance() throws Throwable {
        DeserializerFactoryConfig cfg = new DeserializerFactoryConfig();
        BeanDeserializerFactory factory = new BeanDeserializerFactory(cfg);
        DeserializerFactory result = factory.withConfig(cfg);
        assertSame(factory, result);
    }

    // withConfig: ส่ง config ต่างกัน ต้องสร้าง instance ใหม่แต่เป็นคลาสเดิม
    @Test
    public void testWithConfig_differentConfig_returnsNewInstanceSameClass() throws Throwable {
        DeserializerFactoryConfig cfg1 = new DeserializerFactoryConfig();
        DeserializerFactoryConfig cfg2 = new DeserializerFactoryConfig();
        BeanDeserializerFactory factory = new BeanDeserializerFactory(cfg1);
        DeserializerFactory result = factory.withConfig(cfg2);
        assertNotSame(factory, result);
        assertEquals(BeanDeserializerFactory.class, result.getClass());
    }

    // ตรวจ static set ของคลาสอันตรายเริ่มต้นตามที่ประกาศในซอร์ส
    @Test
    public void testDefaultNoDeserClassNames_containsKnownDangerousClassNames() throws Throwable {
        assertTrue(BeanDeserializerFactory.DEFAULT_NO_DESER_CLASS_NAMES.contains("com.sun.rowset.JdbcRowSetImpl"));
        assertTrue(BeanDeserializerFactory.DEFAULT_NO_DESER_CLASS_NAMES.contains(
                "org.apache.commons.collections.functors.InvokerTransformer"));
    }

    // set ต้องเป็น unmodifiable (Collections.unmodifiableSet)
    @Test
    public void testDefaultNoDeserClassNames_isUnmodifiable() throws Throwable {
        try {
            BeanDeserializerFactory.DEFAULT_NO_DESER_CLASS_NAMES.add("x.y.Z");
            fail("expected UnsupportedOperationException");
        } catch (UnsupportedOperationException expected) {
        }
    }

    // _cfgIllegalClassNames ต้องถูกตั้งค่าเริ่มต้นเป็น DEFAULT_NO_DESER_CLASS_NAMES
    @Test
    public void testCfgIllegalClassNames_defaultsToDefaultSet() throws Throwable {
        assertSame(BeanDeserializerFactory.DEFAULT_NO_DESER_CLASS_NAMES,
                BeanDeserializerFactory.instance._cfgIllegalClassNames);
    }

    // isPotentialBeanType: bean ปกติต้องคืน true ไม่ throw
    @Test
    public void testIsPotentialBeanType_normalBean_returnsTrue() throws Throwable {
        boolean result = BeanDeserializerFactory.instance.isPotentialBeanType(SimpleBean.class);
        assertTrue(result);
    }





    // isIgnorableType: ประเภทปกติที่ไม่มี annotation ต้องคืน false
    @Test
    public void testIsIgnorableType_normalType_returnsFalse() throws Throwable {
        ObjectMapper mapper = new ObjectMapper();
        DeserializationConfig config = mapper.getDeserializationConfig();
        JavaType type = mapper.getTypeFactory().constructType(SimpleBean.class);
        BeanDescription beanDesc = config.introspect(type);
        Map<Class<?>, Boolean> cache = new HashMap<Class<?>, Boolean>();
        boolean result = BeanDeserializerFactory.instance.isIgnorableType(config, beanDesc, SimpleBean.class, cache);
        assertFalse(result);
    }

    // buildBeanDeserializer happy path ผ่าน ObjectMapper: setter/getter ปกติ
    @Test
    public void testDeserialize_simpleBean_roundTrip() throws Throwable {
        ObjectMapper mapper = new ObjectMapper();
        SimpleBean bean = mapper.readValue("{\"id\":3,\"name\":\"abc\"}", SimpleBean.class);
        assertEquals(3, bean.getId());
        assertEquals("abc", bean.getName());
    }

    // ไม่มี @JsonIgnoreProperties: property ที่ไม่รู้จักต้องทำให้เกิด JsonMappingException ตามค่าเริ่มต้น
    @Test
    public void testDeserialize_unknownProperty_default_throwsJsonMappingException() throws Throwable {
        ObjectMapper mapper = new ObjectMapper();
        try {
            mapper.readValue("{\"id\":1,\"name\":\"a\",\"extra\":\"x\"}", SimpleBean.class);
            fail("expected JsonMappingException");
        } catch (JsonMappingException expected) {
        }
    }

    // @JsonIgnoreProperties(ignoreUnknown=true): property แปลกปลอมต้องถูกข้ามได้โดยไม่ throw
    @Test
    public void testDeserialize_ignoreUnknownTrue_extraFieldIgnored() throws Throwable {
        ObjectMapper mapper = new ObjectMapper();
        IgnoreUnknownBean bean = mapper.readValue("{\"name\":\"abc\",\"extra\":\"x\"}", IgnoreUnknownBean.class);
        assertEquals("abc", bean.getName());
    }

    // @JsonIgnoreProperties({"secret"}): field ที่ถูกระบุให้ ignore ต้องไม่ถูก set แม้ปรากฏใน JSON
    @Test
    public void testDeserialize_explicitIgnoreProperties_fieldIgnored() throws Throwable {
        ObjectMapper mapper = new ObjectMapper();
        ExplicitIgnoreBean bean = mapper.readValue("{\"name\":\"a\",\"secret\":\"s\"}", ExplicitIgnoreBean.class);
        assertEquals("a", bean.getName());
        assertNull(bean.getSecret());
    }

    // @JsonAnySetter: property ที่ไม่รู้จักต้องถูกจับใส่ any-setter แทนการ throw
    @Test
    public void testDeserialize_anySetter_capturesUnknownProperties() throws Throwable {
        ObjectMapper mapper = new ObjectMapper();
        AnySetterBean bean = mapper.readValue("{\"name\":\"a\",\"foo\":\"bar\"}", AnySetterBean.class);
        assertEquals("a", bean.getName());
        assertEquals("bar", bean.getExtra().get("foo"));
    }

    // buildThrowableDeserializer: "message" และ "localizedMessage" ต้องถูก ignore โดยไม่ throw
    @Test
    public void testDeserialize_throwableSubtype_ignoresMessageAndLocalizedMessage() throws Throwable {
        ObjectMapper mapper = new ObjectMapper();
        String json = "{\"message\":\"boom\",\"localizedMessage\":\"boom loc\",\"extra\":\"val\"}";
        MyException ex = mapper.readValue(json, MyException.class);
        assertEquals("val", ex.getExtra());
    }

    // addObjectIdReader: generator แบบ IntSequenceGenerator (ไม่ใช่ PropertyGenerator) ต้อง deserialize ได้ปกติ
    @Test
    public void testDeserialize_objectIdentity_intSequenceGenerator_simple() throws Throwable {
        ObjectMapper mapper = new ObjectMapper();
        Node node = mapper.readValue("{\"@id\":1,\"value\":5,\"next\":null}", Node.class);
        assertEquals(5, node.value);
        assertNull(node.next);
    }

    // addObjectIdReader: PropertyGenerator กับชื่อ property ที่มีอยู่จริง ต้อง bind ได้ปกติ
    @Test
    public void testDeserialize_objectIdentity_propertyGenerator_validProperty() throws Throwable {
        ObjectMapper mapper = new ObjectMapper();
        PropIdBean bean = mapper.readValue("{\"id\":7,\"name\":\"x\"}", PropIdBean.class);
        assertEquals(7, bean.id);
        assertEquals("x", bean.name);
    }



    // addReferenceProperties: @JsonManagedReference/@JsonBackReference ต้อง link parent อัตโนมัติ
    @Test
    public void testDeserialize_backReference_linksParentAutomatically() throws Throwable {
        ObjectMapper mapper = new ObjectMapper();
        String json = "{\"name\":\"p\",\"children\":[{\"name\":\"c1\"}]}";
        Parent parent = mapper.readValue(json, Parent.class);
        assertEquals(1, parent.children.size());
        assertSame(parent, parent.children.get(0).parent);
    }

    // @JsonView บน property: การ deserialize ปกติ (ไม่ระบุ view) ต้องยังทำงานได้เมื่อ default view inclusion เปิดอยู่
    @Test
    public void testDeserialize_viewAnnotatedProperty_defaultInclusion_works() throws Throwable {
        ObjectMapper mapper = new ObjectMapper();
        ViewBean bean = mapper.readValue("{\"name\":\"a\"}", ViewBean.class);
        assertEquals("a", bean.name);
    }

    // filterBeanProps + isIgnorableType: type ที่ติด @JsonIgnoreType ต้องถูกข้ามทั้ง property
    @Test
    public void testDeserialize_ignoreType_propertyExcluded() throws Throwable {
        ObjectMapper mapper = new ObjectMapper();
        String json = "{\"name\":\"a\",\"secret\":{\"data\":\"x\"}}";
        HolderBean bean = mapper.readValue(json, HolderBean.class);
        assertEquals("a", bean.name);
        assertNull(bean.secret);
    }

    // addBeanProps: constructor-based property (@JsonCreator + @JsonProperty) ต้องถูก bind ผ่าน creator
    @Test
    public void testDeserialize_creatorBasedBean_constructorProperties() throws Throwable {
        ObjectMapper mapper = new ObjectMapper();
        CreatorBean bean = mapper.readValue("{\"id\":5,\"name\":\"n\"}", CreatorBean.class);
        assertEquals(5, bean.getId());
        assertEquals("n", bean.getName());
    }

    // addBeanProps: property จาก public field (ไม่มี getter/setter) ต้องถูก bind ได้
    @Test
    public void testDeserialize_fieldBasedBean_publicFields() throws Throwable {
        ObjectMapper mapper = new ObjectMapper();
        FieldBean bean = mapper.readValue("{\"id\":3,\"name\":\"f\"}", FieldBean.class);
        assertEquals(3, bean.id);
        assertEquals("f", bean.name);
    }

    // buildBuilderBasedDeserializer/createBuilderBasedDeserializer: @JsonDeserialize(builder=...) ต้องทำงานได้
    @Test
    public void testDeserialize_withBuilder_usesBuilderBasedDeserializer() throws Throwable {
        ObjectMapper mapper = new ObjectMapper();
        BuilderBean bean = mapper.readValue("{\"name\":\"bld\"}", BuilderBean.class);
        assertEquals("bld", bean.getName());
    }
}
