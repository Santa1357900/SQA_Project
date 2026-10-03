package com.fasterxml.jackson.databind.deser.impl;

import org.junit.Before;
import org.junit.Test;
import static org.junit.Assert.*;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.annotation.JsonSubTypes;
import com.fasterxml.jackson.annotation.JsonTypeInfo;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.JsonMappingException;
import com.fasterxml.jackson.databind.ObjectMapper;

public class ExternalTypeHandlerClaudeTest
{
    private ObjectMapper mapper;

    @Before
    public void setUp() throws Throwable {
        mapper = new ObjectMapper();
    }

    public static class Animal {
        private String name;
        public String getName() { return name; }
        public void setName(String n) { this.name = n; }
    }

    public static class Dog extends Animal {
        private String breed;
        public String getBreed() { return breed; }
        public void setBreed(String b) { this.breed = b; }
    }

    public static class Cat extends Animal {
        private int lives;
        public int getLives() { return lives; }
        public void setLives(int l) { this.lives = l; }
    }

    public static class Container {
        @JsonTypeInfo(use = JsonTypeInfo.Id.NAME, include = JsonTypeInfo.As.EXTERNAL_PROPERTY, property = "type")
        @JsonSubTypes({
            @JsonSubTypes.Type(value = Dog.class, name = "dog"),
            @JsonSubTypes.Type(value = Cat.class, name = "cat")
        })
        private Animal pet;
        public Animal getPet() { return pet; }
        public void setPet(Animal p) { this.pet = p; }
    }

    public static class DefaultContainer {
        @JsonTypeInfo(use = JsonTypeInfo.Id.NAME, include = JsonTypeInfo.As.EXTERNAL_PROPERTY,
                property = "type", defaultImpl = Dog.class)
        @JsonSubTypes({
            @JsonSubTypes.Type(value = Dog.class, name = "dog"),
            @JsonSubTypes.Type(value = Cat.class, name = "cat")
        })
        private Animal pet;
        public Animal getPet() { return pet; }
        public void setPet(Animal p) { this.pet = p; }
    }

    public static class RequiredContainer {
        @JsonTypeInfo(use = JsonTypeInfo.Id.NAME, include = JsonTypeInfo.As.EXTERNAL_PROPERTY, property = "type")
        @JsonSubTypes({
            @JsonSubTypes.Type(value = Dog.class, name = "dog")
        })
        @JsonProperty(required = true)
        private Animal pet;
        public Animal getPet() { return pet; }
        public void setPet(Animal p) { this.pet = p; }
    }

    public static class MultiContainer {
        @JsonTypeInfo(use = JsonTypeInfo.Id.NAME, include = JsonTypeInfo.As.EXTERNAL_PROPERTY, property = "type")
        @JsonSubTypes({
            @JsonSubTypes.Type(value = Dog.class, name = "dog"),
            @JsonSubTypes.Type(value = Cat.class, name = "cat")
        })
        private Animal petA;
        @JsonTypeInfo(use = JsonTypeInfo.Id.NAME, include = JsonTypeInfo.As.EXTERNAL_PROPERTY, property = "type")
        @JsonSubTypes({
            @JsonSubTypes.Type(value = Dog.class, name = "dog"),
            @JsonSubTypes.Type(value = Cat.class, name = "cat")
        })
        private Animal petB;
        public Animal getPetA() { return petA; }
        public void setPetA(Animal p) { this.petA = p; }
        public Animal getPetB() { return petB; }
        public void setPetB(Animal p) { this.petB = p; }
    }

    // handlePropertyValue: type id token arrives before the polymorphic value
    @Test
    public void testHandlePropertyValue_typeIdBeforeValue_deserializesDog() throws Throwable {
        String json = "{\"type\":\"dog\",\"pet\":{\"name\":\"Rex\",\"breed\":\"Labrador\"}}";
        Container c = mapper.readValue(json, Container.class);
        assertTrue(c.getPet() instanceof Dog);
        assertEquals("Rex", c.getPet().getName());
        assertEquals("Labrador", ((Dog) c.getPet()).getBreed());
    }

    // handlePropertyValue: polymorphic value arrives before the type id token
    @Test
    public void testHandlePropertyValue_valueBeforeTypeId_deserializesDog() throws Throwable {
        String json = "{\"pet\":{\"name\":\"Rex\",\"breed\":\"Labrador\"},\"type\":\"dog\"}";
        Container c = mapper.readValue(json, Container.class);
        assertTrue(c.getPet() instanceof Dog);
        assertEquals("Rex", c.getPet().getName());
    }

    // type discrimination branch: distinct subtype "cat" with own field lives
    @Test
    public void testComplete_catTypeId_deserializesCatWithLives() throws Throwable {
        String json = "{\"type\":\"cat\",\"pet\":{\"name\":\"Whiskers\",\"lives\":7}}";
        Container c = mapper.readValue(json, Container.class);
        assertTrue(c.getPet() instanceof Cat);
        assertEquals(7, ((Cat) c.getPet()).getLives());
    }

    // complete(): tokens==null -> continue; both type id and value missing, no error
    @Test
    public void testComplete_missingBothTypeAndValue_petRemainsNull() throws Throwable {
        Container c = mapper.readValue("{}", Container.class);
        assertNull(c.getPet());
    }

    // complete(): type id present, value missing, feature disabled and prop not required -> no exception
    @Test
    public void testComplete_missingValueOnly_featureDisabled_noException() throws Throwable {
        ObjectMapper m = new ObjectMapper();
        m.configure(DeserializationFeature.FAIL_ON_MISSING_EXTERNAL_TYPE_ID_PROPERTY, false);
        Container c = m.readValue("{\"type\":\"dog\"}", Container.class);
        assertNull(c.getPet());
    }

    // complete(): type id present, value missing, feature enabled -> reportInputMismatch throws
    @Test
    public void testComplete_missingValueOnly_featureEnabled_throws() throws Throwable {
        ObjectMapper m = new ObjectMapper();
        m.configure(DeserializationFeature.FAIL_ON_MISSING_EXTERNAL_TYPE_ID_PROPERTY, true);
        try {
            m.readValue("{\"type\":\"dog\"}", RequiredContainer.class);
            fail("expected JsonMappingException");
        } catch (JsonMappingException expected) { }
    }

    // complete(): prop.isRequired() forces exception even when feature disabled
    @Test
    public void testComplete_requiredPropertyMissingValue_throwsRegardlessOfFeature() throws Throwable {
        ObjectMapper m = new ObjectMapper();
        m.configure(DeserializationFeature.FAIL_ON_MISSING_EXTERNAL_TYPE_ID_PROPERTY, false);
        try {
            m.readValue("{\"type\":\"dog\"}", RequiredContainer.class);
            fail("expected JsonMappingException");
        } catch (JsonMappingException expected) { }
    }

    // _deserializeAndSet: VALUE_NULL special-cased, property set to null without error
    @Test
    public void testComplete_nullPetValueWithTypeId_setsNullWithoutException() throws Throwable {
        String json = "{\"type\":\"dog\",\"pet\":null}";
        Container c = mapper.readValue(json, Container.class);
        assertNull(c.getPet());
    }

    // unknown type id not registered as a subtype -> resolution failure
    @Test
    public void testComplete_unknownTypeId_throwsJsonMappingException() throws Throwable {
        String json = "{\"type\":\"elephant\",\"pet\":{\"name\":\"Dumbo\"}}";
        try {
            mapper.readValue(json, Container.class);
            fail("expected JsonMappingException");
        } catch (JsonMappingException expected) { }
    }

    // boundary value: empty string type id, not a registered subtype name
    @Test
    public void testComplete_emptyStringTypeId_throwsJsonMappingException() throws Throwable {
        String json = "{\"type\":\"\",\"pet\":{\"name\":\"X\"}}";
        try {
            mapper.readValue(json, Container.class);
            fail("expected JsonMappingException");
        } catch (JsonMappingException expected) { }
    }

    // BUG: missing type id with non-scalar (object) value and defaultImpl set must still
    // use defaultImpl per Javadoc "[databind#94], must allow use of 'defaultImpl'"
    @Test
    public void testComplete_missingTypeIdWithDefaultImplForObjectValue_usesDefaultImpl() throws Throwable {
        String json = "{\"pet\":{\"name\":\"Fido\",\"breed\":\"Poodle\"}}";
        DefaultContainer c = mapper.readValue(json, DefaultContainer.class);
        assertNotNull(c.getPet());
        assertTrue(c.getPet() instanceof Dog);
        assertEquals("Fido", c.getPet().getName());
        assertEquals("Poodle", ((Dog) c.getPet()).getBreed());
    }

    // contract: missing type id, object value present, no defaultImpl -> must be reported as error
    @Test
    public void testComplete_missingTypeIdNoDefaultImplForObjectValue_throwsJsonMappingException() throws Throwable {
        String json = "{\"pet\":{\"name\":\"Fido\",\"breed\":\"Poodle\"}}";
        try {
            mapper.readValue(json, Container.class);
            fail("expected JsonMappingException");
        } catch (JsonMappingException expected) { }
    }

    // handlePropertyValue list-branch: shared type property id, type token arrives first
    @Test
    public void testHandlePropertyValue_sharedTypeIdAcrossProperties_typeFirst() throws Throwable {
        String json = "{\"type\":\"dog\",\"petA\":{\"name\":\"A\",\"breed\":\"b1\"},\"petB\":{\"name\":\"B\",\"breed\":\"b2\"}}";
        MultiContainer c = mapper.readValue(json, MultiContainer.class);
        assertTrue(c.getPetA() instanceof Dog);
        assertTrue(c.getPetB() instanceof Dog);
        assertEquals("A", c.getPetA().getName());
        assertEquals("B", c.getPetB().getName());
    }

    // handlePropertyValue list-branch: shared type property id, values buffered before type
    @Test
    public void testHandlePropertyValue_sharedTypeIdAcrossProperties_valuesFirst() throws Throwable {
        String json = "{\"petA\":{\"name\":\"A\",\"breed\":\"b1\"},\"petB\":{\"name\":\"B\",\"breed\":\"b2\"},\"type\":\"dog\"}";
        MultiContainer c = mapper.readValue(json, MultiContainer.class);
        assertTrue(c.getPetA() instanceof Dog);
        assertTrue(c.getPetB() instanceof Dog);
    }

    // defaultImpl configured but explicit type id given -> explicit wins, default is ignored
    @Test
    public void testComplete_defaultImplWithExplicitTypeIdStillUsesExplicitType() throws Throwable {
        String json = "{\"type\":\"cat\",\"pet\":{\"name\":\"Tom\",\"lives\":9}}";
        DefaultContainer c = mapper.readValue(json, DefaultContainer.class);
        assertTrue(c.getPet() instanceof Cat);
        assertEquals(9, ((Cat) c.getPet()).getLives());
    }

    // defaultImpl configured, both type id and value entirely absent -> still null, no deserialize attempt
    @Test
    public void testComplete_defaultImplMissingBothTypeAndValue_petRemainsNull() throws Throwable {
        DefaultContainer c = mapper.readValue("{}", DefaultContainer.class);
        assertNull(c.getPet());
    }

    // value-before-type ordering using the Cat subtype, confirms order independence
    @Test
    public void testHandlePropertyValue_valueBeforeTypeId_deserializesCat() throws Throwable {
        String json = "{\"pet\":{\"name\":\"Milo\",\"lives\":3},\"type\":\"cat\"}";
        Container c = mapper.readValue(json, Container.class);
        assertTrue(c.getPet() instanceof Cat);
        assertEquals(3, ((Cat) c.getPet()).getLives());
    }

    // boundary numeric value 0 for int field
    @Test
    public void testHandlePropertyValue_catWithZeroLives_setsZero() throws Throwable {
        String json = "{\"type\":\"cat\",\"pet\":{\"name\":\"Ghost\",\"lives\":0}}";
        Container c = mapper.readValue(json, Container.class);
        assertEquals(0, ((Cat) c.getPet()).getLives());
    }

    // type id resolution is case-sensitive: "Dog" does not match registered "dog"
    @Test
    public void testComplete_caseSensitiveTypeIdMismatch_throwsJsonMappingException() throws Throwable {
        String json = "{\"type\":\"Dog\",\"pet\":{\"name\":\"X\",\"breed\":\"b\"}}";
        try {
            mapper.readValue(json, Container.class);
            fail("expected JsonMappingException");
        } catch (JsonMappingException expected) { }
    }

    // start(): repeated reads on the same mapper/class must not leak state between calls
    @Test
    public void testStart_multipleSequentialReads_areIndependent() throws Throwable {
        Container first = mapper.readValue(
                "{\"type\":\"dog\",\"pet\":{\"name\":\"A\",\"breed\":\"b1\"}}", Container.class);
        Container second = mapper.readValue(
                "{\"type\":\"cat\",\"pet\":{\"name\":\"B\",\"lives\":5}}", Container.class);
        assertTrue(first.getPet() instanceof Dog);
        assertTrue(second.getPet() instanceof Cat);
        assertEquals(5, ((Cat) second.getPet()).getLives());
    }
}
