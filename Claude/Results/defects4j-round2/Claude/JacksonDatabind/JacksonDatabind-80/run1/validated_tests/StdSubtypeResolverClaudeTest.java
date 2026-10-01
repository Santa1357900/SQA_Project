package com.fasterxml.jackson.databind.jsontype.impl;

import java.util.LinkedHashSet;

import org.junit.Before;
import org.junit.Test;
import static org.junit.Assert.*;

import com.fasterxml.jackson.annotation.JsonTypeInfo;
import com.fasterxml.jackson.annotation.JsonTypeName;
import com.fasterxml.jackson.annotation.JsonSubTypes;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.JsonMappingException;
import com.fasterxml.jackson.databind.jsontype.NamedType;

public class StdSubtypeResolverClaudeTest {

    @JsonTypeInfo(use = JsonTypeInfo.Id.NAME, include = JsonTypeInfo.As.PROPERTY, property = "type")
    @JsonSubTypes({ @JsonSubTypes.Type(value = Bird.class) })
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
        private boolean indoor;
        public boolean isIndoor() { return indoor; }
        public void setIndoor(boolean indoor) { this.indoor = indoor; }
    }

    @JsonTypeName("bird")
    public static class Bird extends Animal {
        private boolean canFly;
        public boolean isCanFly() { return canFly; }
        public void setCanFly(boolean canFly) { this.canFly = canFly; }
    }

    @JsonTypeInfo(use = JsonTypeInfo.Id.NAME, include = JsonTypeInfo.As.PROPERTY, property = "type")
    public static class Shape {
        private String label;
        public String getLabel() { return label; }
        public void setLabel(String label) { this.label = label; }
    }

    public static class Square extends Shape {
        private int side;
        public int getSide() { return side; }
        public void setSide(int side) { this.side = side; }
    }

    @JsonTypeInfo(use = JsonTypeInfo.Id.NAME, include = JsonTypeInfo.As.PROPERTY, property = "type")
    public static abstract class Vehicle {
        private int wheels;
        public int getWheels() { return wheels; }
        public void setWheels(int wheels) { this.wheels = wheels; }
    }

    public static class Car extends Vehicle {
        private String model;
        public String getModel() { return model; }
        public void setModel(String model) { this.model = model; }
    }

    private ObjectMapper mapper;

    @Before
    public void setUp() throws Throwable {
        mapper = new ObjectMapper();
    }

    // registerSubtypes: internal set stays null before any registration call
    @Test
    public void testRegisterSubtypesNamedTypeArray_beforeCall_fieldIsNull() throws Throwable {
        StdSubtypeResolver resolver = new StdSubtypeResolver();
        assertNull(resolver._registeredSubtypes);
    }

    // registerSubtypes(NamedType...): single entry is added to the internal set
    @Test
    public void testRegisterSubtypesNamedTypeArray_singleEntry_addsToSet() throws Throwable {
        StdSubtypeResolver resolver = new StdSubtypeResolver();
        resolver.registerSubtypes(new NamedType(Dog.class, "dog"));
        assertNotNull(resolver._registeredSubtypes);
        assertEquals(1, resolver._registeredSubtypes.size());
        assertTrue(resolver._registeredSubtypes.contains(new NamedType(Dog.class, "dog")));
    }

    // registerSubtypes(NamedType...): zero-length varargs still initializes an empty set (0-iteration loop)
    @Test
    public void testRegisterSubtypesNamedTypeArray_emptyArray_initializesEmptySet() throws Throwable {
        StdSubtypeResolver resolver = new StdSubtypeResolver();
        resolver.registerSubtypes(new NamedType[0]);
        assertNotNull(resolver._registeredSubtypes);
        assertEquals(0, resolver._registeredSubtypes.size());
    }

    // registerSubtypes(Class...): each class is wrapped as an unnamed NamedType
    @Test
    public void testRegisterSubtypesClassArray_wrapsAsUnnamedNamedType() throws Throwable {
        StdSubtypeResolver resolver = new StdSubtypeResolver();
        resolver.registerSubtypes(Dog.class, Cat.class);
        assertEquals(2, resolver._registeredSubtypes.size());
        assertTrue(resolver._registeredSubtypes.contains(new NamedType(Dog.class)));
        assertTrue(resolver._registeredSubtypes.contains(new NamedType(Cat.class)));
    }

    // registerSubtypes(Class...): zero-length array produces no entries but does not throw
    @Test
    public void testRegisterSubtypesClassArray_emptyArray_noEntries() throws Throwable {
        StdSubtypeResolver resolver = new StdSubtypeResolver();
        resolver.registerSubtypes(new Class<?>[0]);
        assertNotNull(resolver._registeredSubtypes);
        assertEquals(0, resolver._registeredSubtypes.size());
    }

    // registerSubtypes called twice: second call reuses the already-initialized set (null-check false branch)
    @Test
    public void testRegisterSubtypesCalledTwice_reusesSameSetInstance() throws Throwable {
        StdSubtypeResolver resolver = new StdSubtypeResolver();
        resolver.registerSubtypes(new NamedType(Dog.class, "dog"));
        LinkedHashSet<NamedType> first = resolver._registeredSubtypes;
        resolver.registerSubtypes(new NamedType(Cat.class, "cat"));
        assertSame(first, resolver._registeredSubtypes);
        assertEquals(2, resolver._registeredSubtypes.size());
    }

    // registerSubtypes: adding the exact same NamedType instance twice must not duplicate the Set entry
    @Test
    public void testRegisterSubtypesSameNamedTypeTwice_noDuplicate() throws Throwable {
        StdSubtypeResolver resolver = new StdSubtypeResolver();
        NamedType nt = new NamedType(Dog.class, "dog");
        resolver.registerSubtypes(nt);
        resolver.registerSubtypes(nt);
        assertEquals(1, resolver._registeredSubtypes.size());
    }

    // registerSubtypes(NamedType...): multiple entries passed in one call are all added (loop with >1 iteration)
    @Test
    public void testRegisterSubtypesMultipleNamedTypesOneCall_allAdded() throws Throwable {
        StdSubtypeResolver resolver = new StdSubtypeResolver();
        resolver.registerSubtypes(new NamedType(Dog.class, "dog"), new NamedType(Cat.class, "cat"));
        assertEquals(2, resolver._registeredSubtypes.size());
        assertTrue(resolver._registeredSubtypes.contains(new NamedType(Dog.class, "dog")));
        assertTrue(resolver._registeredSubtypes.contains(new NamedType(Cat.class, "cat")));
    }





    // registerSubtypes(NamedType): explicit name is used verbatim for serialization output
    @Test
    public void testSerialize_registeredNamedType_usesExplicitName() throws Throwable {
        mapper.registerSubtypes(new NamedType(Cat.class, "cat"));
        Cat c = new Cat();
        c.setName("Tom");
        c.setIndoor(true);
        String json = mapper.writeValueAsString(c);
        assertTrue(json.contains("\"type\":\"cat\""));
    }

    // registerSubtypes(NamedType): explicit name resolves back to the registered class on deserialization
    @Test
    public void testDeserialize_registeredNamedType_explicitNameResolvesToCat() throws Throwable {
        mapper.registerSubtypes(new NamedType(Cat.class, "cat"));
        String json = "{\"type\":\"cat\",\"name\":\"Tom\",\"indoor\":true}";
        Animal a = mapper.readValue(json, Animal.class);
        assertTrue(a instanceof Cat);
        assertTrue(((Cat) a).isIndoor());
    }

    // registerSubtypes: multiple types registered in a single call are both resolvable during deserialization
    @Test
    public void testRegisterSubtypesMultipleInOneCall_bothResolvableOnDeserialize() throws Throwable {
        mapper.registerSubtypes(new NamedType(Dog.class, "dog"), new NamedType(Cat.class, "cat"));
        Animal dog = mapper.readValue("{\"type\":\"dog\",\"name\":\"Rex\"}", Animal.class);
        Animal cat = mapper.readValue("{\"type\":\"cat\",\"name\":\"Tom\"}", Animal.class);
        assertTrue(dog instanceof Dog);
        assertTrue(cat instanceof Cat);
    }

    // registerSubtypes: incremental calls accumulate into the same underlying set; both remain resolvable
    @Test
    public void testRegisterSubtypesIncrementalCalls_bothResolvableOnDeserialize() throws Throwable {
        mapper.registerSubtypes(new NamedType(Dog.class, "dog"));
        mapper.registerSubtypes(new NamedType(Cat.class, "cat"));
        Animal dog = mapper.readValue("{\"type\":\"dog\",\"name\":\"Rex\"}", Animal.class);
        Animal cat = mapper.readValue("{\"type\":\"cat\",\"name\":\"Tom\"}", Animal.class);
        assertTrue(dog instanceof Dog);
        assertTrue(cat instanceof Cat);
    }

    // collectAndResolveSubtypesByTypeId: an unregistered, unknown type id must fail to resolve
    @Test
    public void testDeserialize_unregisteredUnknownTypeId_throwsJsonMappingException() throws Throwable {
        mapper.registerSubtypes(new NamedType(Dog.class, "dog"));
        String json = "{\"type\":\"ghost\",\"name\":\"X\"}";
        try {
            mapper.readValue(json, Animal.class);
            fail("expected JsonMappingException for unknown type id");
        } catch (JsonMappingException expected) {
        }
    }

    // collectAndResolveSubtypesByClass: explicitly registered name has precedence over the subtype's own @JsonTypeName for serialization
    @Test
    public void testSerialize_explicitRegisteredNameOverridesAnnotationDerivedName() throws Throwable {
        mapper.registerSubtypes(new NamedType(Bird.class, "custombird"));
        Bird b = new Bird();
        b.setName("Tweety");
        b.setCanFly(true);
        String json = mapper.writeValueAsString(b);
        assertTrue(json.contains("\"type\":\"custombird\""));
        assertFalse(json.contains("\"type\":\"bird\""));
    }

    // collectAndResolveSubtypesByTypeId: both the annotation-derived name and the registered name resolve to the same class
    @Test
    public void testDeserialize_bothAnnotationNameAndRegisteredNameResolveSameClass() throws Throwable {
        mapper.registerSubtypes(new NamedType(Bird.class, "custombird"));
        Animal viaAnnotationName = mapper.readValue("{\"type\":\"bird\",\"name\":\"Tweety\"}", Animal.class);
        Animal viaRegisteredName = mapper.readValue("{\"type\":\"custombird\",\"name\":\"Polly\"}", Animal.class);
        assertTrue(viaAnnotationName instanceof Bird);
        assertTrue(viaRegisteredName instanceof Bird);
    }

    // registering a subtype of an unrelated hierarchy (Vehicle/Car) must not interfere with the Animal hierarchy
    @Test
    public void testCrossHierarchyRegistration_doesNotAffectUnrelatedHierarchy() throws Throwable {
        mapper.registerSubtypes(new NamedType(Car.class, "car"));
        mapper.registerSubtypes(new NamedType(Dog.class, "dog"));
        Dog d = new Dog();
        d.setName("Rex");
        d.setBreed("Lab");
        String json = mapper.writeValueAsString(d);
        assertTrue(json.contains("\"type\":\"dog\""));
        assertTrue(json.contains("\"breed\":\"Lab\""));
    }

    // an abstract base type that was never explicitly named must not be resolvable via its own default simple class name
    @Test
    public void testDeserialize_typeIdEqualsAbstractBaseSimpleName_notRecognized() throws Throwable {
        mapper.registerSubtypes(new NamedType(Dog.class, "dog"));
        String json = "{\"type\":\"Animal\",\"name\":\"X\"}";
        try {
            mapper.readValue(json, Animal.class);
            fail("expected exception: abstract base type itself must not be a valid subtype id");
        } catch (JsonMappingException expected) {
        }
    }



    // using an explicitly constructed StdSubtypeResolver instance set on the mapper works end-to-end
    @Test
    public void testExplicitResolverInstanceSetOnMapper_roundTripWorks() throws Throwable {
        StdSubtypeResolver resolver = new StdSubtypeResolver();
        resolver.registerSubtypes(new NamedType(Cat.class, "cat"));
        mapper.setSubtypeResolver(resolver);
        Cat c = new Cat();
        c.setName("Tom");
        c.setIndoor(false);
        String json = mapper.writeValueAsString(c);
        Animal back = mapper.readValue(json, Animal.class);
        assertTrue(back instanceof Cat);
        assertEquals("Tom", back.getName());
    }

    // base-class property ("name") is present alongside the resolved type id in serialized output
    @Test
    public void testSerialize_basePropertyPresentAlongsideTypeId() throws Throwable {
        mapper.registerSubtypes(new NamedType(Dog.class, "dog"));
        Dog d = new Dog();
        d.setName("Buddy");
        d.setBreed("Beagle");
        String json = mapper.writeValueAsString(d);
        assertTrue(json.contains("\"name\":\"Buddy\""));
        assertTrue(json.contains("\"breed\":\"Beagle\""));
    }

    // registerSubtypes(NamedType) with a unicode-escaped name round-trips correctly
    @Test
    public void testRegisterSubtypesNamedType_unicodeName_roundTrip() throws Throwable {
        mapper.registerSubtypes(new NamedType(Cat.class, "caf\u00e9"));
        Cat c = new Cat();
        c.setName("Neko");
        String json = mapper.writeValueAsString(c);
        Animal back = mapper.readValue(json, Animal.class);
        assertTrue(back instanceof Cat);
        assertEquals("Neko", back.getName());
    }

    // registered subtype unrelated to the requested base class must be filtered out by the isAssignableFrom check
    @Test
    public void testRegisterSubtypes_unrelatedTypeFilteredByAssignability() throws Throwable {
        mapper.registerSubtypes(new NamedType(Car.class, "car"), new NamedType(Dog.class, "dog"));
        String json = "{\"type\":\"car\",\"name\":\"Rex\"}";
        try {
            mapper.readValue(json, Animal.class);
            fail("expected exception: Car is not assignable to the Animal hierarchy");
        } catch (JsonMappingException expected) {
        }
    }
}
