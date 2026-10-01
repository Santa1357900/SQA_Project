package com.fasterxml.jackson.databind.jsontype.impl;

import java.util.List;
import java.util.ArrayList;
import java.util.Map;
import java.util.HashMap;

import org.junit.Before;
import org.junit.Test;
import static org.junit.Assert.*;

import com.fasterxml.jackson.annotation.JsonTypeInfo;
import com.fasterxml.jackson.annotation.JsonSubTypes;
import com.fasterxml.jackson.databind.JsonMappingException;
import com.fasterxml.jackson.databind.ObjectMapper;

public class AsWrapperTypeDeserializerClaudeTest {

    @JsonTypeInfo(use = JsonTypeInfo.Id.NAME, include = JsonTypeInfo.As.WRAPPER_OBJECT, property = "type")
    @JsonSubTypes({
        @JsonSubTypes.Type(value = Dog.class, name = "dog"),
        @JsonSubTypes.Type(value = Cat.class, name = "cat")
    })
    public static abstract class Animal {
        private String name;
        public String getName() { return name; }
        public void setName(String name) { this.name = name; }
    }

    public static class Dog extends Animal {
        private String breed;
        public String getBreed() { return breed; }
        public void setBreed(String breed) { this.breed = breed; }
    }

    public static class Cat extends Animal {
        private int lives;
        public int getLives() { return lives; }
        public void setLives(int lives) { this.lives = lives; }
    }

    public static class AnimalHolder {
        private List<Animal> animals;
        public List<Animal> getAnimals() { return animals; }
        public void setAnimals(List<Animal> animals) { this.animals = animals; }
    }

    @JsonTypeInfo(use = JsonTypeInfo.Id.NAME, include = JsonTypeInfo.As.WRAPPER_OBJECT,
            property = "type", visible = true)
    @JsonSubTypes({
        @JsonSubTypes.Type(value = VisibleDog.class, name = "vdog")
    })
    public static abstract class VisibleAnimal {
        private String type;
        public String getType() { return type; }
        public void setType(String type) { this.type = type; }
    }

    public static class VisibleDog extends VisibleAnimal {
        private String breed;
        public String getBreed() { return breed; }
        public void setBreed(String breed) { this.breed = breed; }
    }

    public static class ObjectContainer {
        @JsonTypeInfo(use = JsonTypeInfo.Id.CLASS, include = JsonTypeInfo.As.WRAPPER_OBJECT)
        private Object value;
        public Object getValue() { return value; }
        public void setValue(Object value) { this.value = value; }
    }

    private ObjectMapper mapper;

    @Before
    public void setUp() throws Throwable {
        mapper = new ObjectMapper();
    }

    // covers normal path: START_OBJECT check pass, FIELD_NAME found, deser found, END_OBJECT matched
    @Test
    public void testDeserializeTypedFromObject_basicDogWrapping_returnsCorrectSubtype() throws Throwable {
        String json = "{\"dog\":{\"name\":\"Rex\",\"breed\":\"Lab\"}}";
        Animal result = mapper.readValue(json, Animal.class);
        assertTrue(result instanceof Dog);
        assertEquals("Rex", result.getName());
        assertEquals("Lab", ((Dog) result).getBreed());
    }

    // covers same normal path with a second registered subtype and an int field
    @Test
    public void testDeserializeTypedFromObject_catSubtypeFields_setCorrectly() throws Throwable {
        String json = "{\"cat\":{\"name\":\"Tom\",\"lives\":9}}";
        Animal result = mapper.readValue(json, Animal.class);
        assertTrue(result instanceof Cat);
        assertEquals("Tom", result.getName());
        assertEquals(9, ((Cat) result).getLives());
    }

    // full round trip serialize+deserialize confirms wrapper produced by writer is readable back
    @Test
    public void testDeserializeTypedFromObject_roundTripSerializeDeserialize_dog() throws Throwable {
        Dog d = new Dog();
        d.setName("Buddy");
        d.setBreed("Beagle");
        String json = mapper.writeValueAsString((Animal) d);
        Animal result = mapper.readValue(json, Animal.class);
        assertTrue(result instanceof Dog);
        assertEquals("Buddy", result.getName());
    }

    // verifies getTypeInclusion()==WRAPPER_OBJECT: single-field object {"typeId": {...}} structure
    @Test
    public void testGetTypeInclusion_viaWrapperObjectStructure_singleFieldObject() throws Throwable {
        Dog d = new Dog();
        d.setName("Spot");
        String json = mapper.writeValueAsString((Animal) d);
        assertTrue(json.startsWith("{\"dog\":"));
        assertTrue(json.endsWith("}}"));
    }

    // covers branch: p.getCurrentToken() != START_OBJECT -> throws wrongTokenException
    @Test
    public void testDeserializeTypedFromScalar_bareScalarValue_throwsException() throws Throwable {
        try {
            mapper.readValue("5", Animal.class);
            fail("expected JsonMappingException for non-object wrapper");
        } catch (JsonMappingException expected) {
            // expected: WRAPPER_OBJECT requires JSON Object
        }
    }

    // covers branch: p.nextToken() != FIELD_NAME -> throws wrongTokenException (empty object)
    @Test
    public void testDeserializeTypedFromObject_emptyObjectMissingFieldName_throwsException() throws Throwable {
        try {
            mapper.readValue("{}", Animal.class);
            fail("expected JsonMappingException for missing field name");
        } catch (JsonMappingException expected) {
            // expected: need type id field name
        }
    }

    // covers _findDeserializer failure path for an unregistered subtype id
    @Test
    public void testDeserializeTypedFromObject_unknownTypeId_throwsException() throws Throwable {
        try {
            mapper.readValue("{\"fish\":{\"name\":\"Nemo\"}}", Animal.class);
            fail("expected JsonMappingException for unknown subtype id");
        } catch (JsonMappingException expected) {
            // expected: unresolvable type id
        }
    }

    // covers final branch: p.nextToken() != END_OBJECT after value -> throws wrongTokenException
    @Test
    public void testDeserializeTypedFromObject_extraFieldAfterValue_missingEndObject_throwsException() throws Throwable {
        String json = "{\"dog\":{\"name\":\"Rex\",\"breed\":\"Lab\"},\"extra\":1}";
        try {
            mapper.readValue(json, Animal.class);
            fail("expected JsonMappingException for missing closing END_OBJECT");
        } catch (JsonMappingException expected) {
            // expected: extra trailing field breaks wrapper contract
        }
    }

    // covers _typeIdVisible merge branch: type id injected as visible property on nested object
    @Test
    public void testDeserializeTypedFromObject_typeIdVisibleTrue_injectsTypePropertyIntoBean() throws Throwable {
        String json = "{\"vdog\":{\"breed\":\"Poodle\"}}";
        VisibleAnimal result = mapper.readValue(json, VisibleAnimal.class);
        assertTrue(result instanceof VisibleDog);
        assertEquals("Poodle", ((VisibleDog) result).getBreed());
        assertEquals("vdog", result.getType());
    }



    // covers deserializeTypedFromScalar dispatch through a generic Object-typed field (Integer)
    @Test
    public void testDeserializeTypedFromScalar_integerValueInContainer_roundTrip() throws Throwable {
        ObjectContainer c = new ObjectContainer();
        c.setValue(Integer.valueOf(42));
        String json = mapper.writeValueAsString(c);
        ObjectContainer result = mapper.readValue(json, ObjectContainer.class);
        assertEquals(Integer.valueOf(42), result.getValue());
    }

    // covers deserializeTypedFromScalar dispatch for a String scalar value
    @Test
    public void testDeserializeTypedFromScalar_stringValueInContainer_roundTrip() throws Throwable {
        ObjectContainer c = new ObjectContainer();
        c.setValue("hello");
        String json = mapper.writeValueAsString(c);
        ObjectContainer result = mapper.readValue(json, ObjectContainer.class);
        assertEquals("hello", result.getValue());
    }

    // covers deserializeTypedFromArray dispatch through a generic Object-typed field (List)
    @Test
    public void testDeserializeTypedFromArray_listValueInContainer_roundTrip() throws Throwable {
        ObjectContainer c = new ObjectContainer();
        List<String> list = new ArrayList<String>();
        list.add("a");
        list.add("b");
        c.setValue(list);
        String json = mapper.writeValueAsString(c);
        ObjectContainer result = mapper.readValue(json, ObjectContainer.class);
        assertTrue(result.getValue() instanceof List);
        assertEquals(2, ((List<?>) result.getValue()).size());
    }

    // covers deserializeTypedFromObject dispatch through a generic Object-typed field (Map)
    @Test
    public void testDeserializeTypedFromObject_mapValueInContainer_roundTrip() throws Throwable {
        ObjectContainer c = new ObjectContainer();
        Map<String, Integer> map = new HashMap<String, Integer>();
        map.put("k", Integer.valueOf(7));
        c.setValue(map);
        String json = mapper.writeValueAsString(c);
        ObjectContainer result = mapper.readValue(json, ObjectContainer.class);
        assertTrue(result.getValue() instanceof Map);
        assertEquals(Integer.valueOf(7), ((Map<?, ?>) result.getValue()).get("k"));
    }

    // covers normal path with an array field of polymorphic elements, multiple wrapper invocations
    @Test
    public void testDeserializeTypedFromObject_multipleAnimalsInList_eachCorrectSubtype() throws Throwable {
        String json = "{\"animals\":[{\"dog\":{\"name\":\"Rex\",\"breed\":\"Lab\"}},"
                + "{\"cat\":{\"name\":\"Tom\",\"lives\":9}}]}";
        AnimalHolder holder = mapper.readValue(json, AnimalHolder.class);
        assertEquals(2, holder.getAnimals().size());
        assertTrue(holder.getAnimals().get(0) instanceof Dog);
        assertTrue(holder.getAnimals().get(1) instanceof Cat);
    }

    // covers empty-list branch (0 iterations) for a list of wrapped polymorphic values
    @Test
    public void testDeserializeTypedFromObject_emptyAnimalsList_zeroElements() throws Throwable {
        String json = "{\"animals\":[]}";
        AnimalHolder holder = mapper.readValue(json, AnimalHolder.class);
        assertNotNull(holder.getAnimals());
        assertEquals(0, holder.getAnimals().size());
    }

    // covers normal path where subtype has only base field set, other left at default
    @Test
    public void testDeserializeTypedFromObject_dogWithoutBreed_breedIsNull() throws Throwable {
        String json = "{\"dog\":{\"name\":\"Rex\"}}";
        Animal result = mapper.readValue(json, Animal.class);
        assertTrue(result instanceof Dog);
        assertEquals("Rex", result.getName());
        assertNull(((Dog) result).getBreed());
    }

    // covers normal path robustness against extra whitespace around wrapper tokens
    @Test
    public void testDeserializeTypedFromObject_whitespaceAroundTokens_parsesCorrectly() throws Throwable {
        String json = "  {  \"dog\"  :  { \"name\" : \"Rex\" , \"breed\" : \"Lab\" }  }  ";
        Animal result = mapper.readValue(json, Animal.class);
        assertTrue(result instanceof Dog);
        assertEquals("Rex", result.getName());
    }

    // covers branch where value part itself starts with START_ARRAY causing START_OBJECT mismatch on close
    @Test
    public void testDeserializeTypedFromObject_arrayInsteadOfObjectWrapperValue_stillDelegatesToDeser() throws Throwable {
        ObjectContainer c = new ObjectContainer();
        List<Integer> nums = new ArrayList<Integer>();
        nums.add(Integer.valueOf(1));
        c.setValue(nums);
        String json = mapper.writeValueAsString(c);
        ObjectContainer result = mapper.readValue(json, ObjectContainer.class);
        List<?> resultList = (List<?>) result.getValue();
        assertEquals(1, resultList.size());
    }

    // covers repeated invocation stability: two independent reads produce independent correct instances
    @Test
    public void testDeserializeTypedFromObject_repeatedIndependentCalls_noSharedState() throws Throwable {
        Animal first = mapper.readValue("{\"dog\":{\"name\":\"A\",\"breed\":\"X\"}}", Animal.class);
        Animal second = mapper.readValue("{\"cat\":{\"name\":\"B\",\"lives\":3}}", Animal.class);
        assertEquals("A", first.getName());
        assertEquals("B", second.getName());
        assertTrue(first instanceof Dog);
        assertTrue(second instanceof Cat);
    }

    // covers unknown subtype id thrown even when wrapper value itself is a scalar, not object
    @Test
    public void testDeserializeTypedFromScalar_unknownTypeIdWithScalarValue_throwsException() throws Throwable {
        try {
            mapper.readValue("{\"fish\":5}", Animal.class);
            fail("expected JsonMappingException for unknown subtype id with scalar value");
        } catch (JsonMappingException expected) {
            // expected: unresolvable type id regardless of value shape
        }
    }

    // covers case where the wrapped value is itself an empty JSON object for a known subtype
    @Test
    public void testDeserializeTypedFromObject_dogWithEmptyValueObject_allFieldsNull() throws Throwable {
        String json = "{\"dog\":{}}";
        Animal result = mapper.readValue(json, Animal.class);
        assertTrue(result instanceof Dog);
        assertNull(result.getName());
        assertNull(((Dog) result).getBreed());
    }
}
