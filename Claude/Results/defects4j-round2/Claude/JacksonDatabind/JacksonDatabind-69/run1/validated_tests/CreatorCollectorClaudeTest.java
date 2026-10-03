package com.fasterxml.jackson.databind.deser.impl;

import java.util.List;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.HashMap;
import java.util.Collection;
import java.util.Map;

import org.junit.Before;
import org.junit.Test;
import static org.junit.Assert.*;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.JavaType;
import com.fasterxml.jackson.databind.BeanDescription;
import com.fasterxml.jackson.databind.DeserializationConfig;
import com.fasterxml.jackson.databind.DeserializationContext;
import com.fasterxml.jackson.databind.deser.ValueInstantiator;
import com.fasterxml.jackson.databind.deser.CreatorProperty;
import com.fasterxml.jackson.databind.deser.SettableBeanProperty;
import com.fasterxml.jackson.databind.introspect.AnnotatedConstructor;
import com.fasterxml.jackson.databind.introspect.AnnotatedWithParams;

public class CreatorCollectorClaudeTest
{
    private ObjectMapper mapper;
    private DeserializationConfig config;
    private CreatorCollector collector;

    @Before
    public void setUp() throws Throwable {
        mapper = new ObjectMapper();
        config = mapper.getDeserializationConfig();
        collector = collectorFor(DefaultCtorBean.class);
    }

    private CreatorCollector collectorFor(Class<?> cls) throws Throwable {
        BeanDescription bd = config.introspect(mapper.constructType(cls));
        return new CreatorCollector(bd, config);
    }

    private AnnotatedWithParams ctorOf(Class<?> cls) throws Throwable {
        BeanDescription bd = config.introspect(mapper.constructType(cls));
        List<AnnotatedConstructor> ctors = bd.getConstructors();
        return ctors.get(0);
    }

    // Constructor: no creator set -> hasDefaultCreator() must be false
    @Test
    public void testConstructor_initialState_hasDefaultCreatorFalse() throws Throwable {
        assertFalse(collector.hasDefaultCreator());
    }



    // addStringCreator(creator,false): sets slot, marks _hasNonDefaultCreator
    @Test
    public void testAddStringCreator_nonExplicit_setsStringSlotAndNonDefaultFlag() throws Throwable {
        AnnotatedWithParams c = ctorOf(StringCtorBean.class);
        collector.addStringCreator(c, false);
        assertSame(c, collector._creators[CreatorCollector.C_STRING]);
        assertTrue(collector._hasNonDefaultCreator);
    }

    // addStringCreator(creator,true): explicit bit gets set
    @Test
    public void testAddStringCreator_explicit_setsExplicitBitmask() throws Throwable {
        AnnotatedWithParams c = ctorOf(StringCtorBean.class);
        collector.addStringCreator(c, true);
        int mask = 1 << CreatorCollector.C_STRING;
        assertTrue((collector._explicitCreators & mask) != 0);
    }

    // addIntCreator(creator,explicit) two-arg: sets C_INT slot
    @Test
    public void testAddIntCreator_explicitTwoArg_setsIntSlot() throws Throwable {
        AnnotatedWithParams c = ctorOf(IntCtorBean.class);
        collector.addIntCreator(c, true);
        assertSame(c, collector._creators[CreatorCollector.C_INT]);
    }

    // addLongCreator(creator,explicit) two-arg: sets C_LONG slot
    @Test
    public void testAddLongCreator_explicitTwoArg_setsLongSlot() throws Throwable {
        AnnotatedWithParams c = ctorOf(LongCtorBean.class);
        collector.addLongCreator(c, true);
        assertSame(c, collector._creators[CreatorCollector.C_LONG]);
    }

    // addDoubleCreator(creator,explicit) two-arg: sets C_DOUBLE slot
    @Test
    public void testAddDoubleCreator_explicitTwoArg_setsDoubleSlot() throws Throwable {
        AnnotatedWithParams c = ctorOf(DoubleCtorBean.class);
        collector.addDoubleCreator(c, true);
        assertSame(c, collector._creators[CreatorCollector.C_DOUBLE]);
    }

    // addBooleanCreator(creator,explicit) two-arg: sets C_BOOLEAN slot
    @Test
    public void testAddBooleanCreator_explicitTwoArg_setsBooleanSlot() throws Throwable {
        AnnotatedWithParams c = ctorOf(BooleanCtorBean.class);
        collector.addBooleanCreator(c, true);
        assertSame(c, collector._creators[CreatorCollector.C_BOOLEAN]);
    }

    // addDelegatingCreator: non-collection-like param -> C_DELEGATE slot, hasDelegatingCreator true
    @Test
    public void testAddDelegatingCreator_nonCollectionParam_setsDelegateSlot() throws Throwable {
        AnnotatedWithParams c = ctorOf(DelegateBean.class);
        collector.addDelegatingCreator(c, false, null);
        assertTrue(collector.hasDelegatingCreator());
        assertSame(c, collector._creators[CreatorCollector.C_DELEGATE]);
    }

    // addDelegatingCreator: collection-like param -> C_ARRAY_DELEGATE slot
    @Test
    public void testAddDelegatingCreator_collectionParam_setsArrayDelegateSlot() throws Throwable {
        AnnotatedWithParams c = ctorOf(ArrayDelegateBean.class);
        collector.addDelegatingCreator(c, false, null);
        assertFalse(collector.hasDelegatingCreator());
        assertSame(c, collector._creators[CreatorCollector.C_ARRAY_DELEGATE]);
    }

    // addPropertyCreator with zero-length array: skip dup-name loop, sets props slot
    @Test
    public void testAddPropertyCreator_emptyArray_setsPropsSlotNoException() throws Throwable {
        AnnotatedWithParams c = ctorOf(StringCtorBean.class);
        SettableBeanProperty[] props = new SettableBeanProperty[0];
        collector.addPropertyCreator(c, false, props);
        assertTrue(collector.hasPropertyBasedCreator());
    }

    // addPropertyCreator with single null element: length<=1 so no getName() call, no NPE
    @Test
    public void testAddPropertyCreator_singleNullElementArray_setsPropsSlotNoException() throws Throwable {
        AnnotatedWithParams c = ctorOf(StringCtorBean.class);
        SettableBeanProperty[] props = new SettableBeanProperty[1];
        collector.addPropertyCreator(c, true, props);
        assertTrue(collector.hasPropertyBasedCreator());
    }

    // addIncompeteParameter(null): first assignment branch, does not throw
    @Test
    public void testAddIncompeteParameter_nullParameter_doesNotThrow() throws Throwable {
        collector.addIncompeteParameter(null);
        assertNull(collector._incompleteParameter);
    }

    // deprecated addStringCreator(creator) delegates to non-explicit string slot
    @Test
    public void testAddStringCreatorDeprecated_oneArg_delegatesNonExplicitToStringSlot() throws Throwable {
        AnnotatedWithParams c = ctorOf(StringCtorBean.class);
        collector.addStringCreator(c);
        assertSame(c, collector._creators[CreatorCollector.C_STRING]);
    }







    // deprecated addBooleanCreator(creator) correctly delegates to boolean slot
    @Test
    public void testAddBooleanCreatorDeprecated_oneArg_delegatesNonExplicitToBooleanSlot() throws Throwable {
        AnnotatedWithParams c = ctorOf(BooleanCtorBean.class);
        collector.addBooleanCreator(c);
        assertSame(c, collector._creators[CreatorCollector.C_BOOLEAN]);
    }

    // deprecated addDelegatingCreator(creator, CreatorProperty[]) sets delegate slot
    @Test
    public void testAddDelegatingCreatorDeprecated_twoArg_setsDelegateSlot() throws Throwable {
        AnnotatedWithParams c = ctorOf(DelegateBean.class);
        CreatorProperty[] empty = new CreatorProperty[0];
        collector.addDelegatingCreator(c, empty);
        assertTrue(collector.hasDelegatingCreator());
    }

    // deprecated addPropertyCreator(creator, CreatorProperty[]) sets props slot
    @Test
    public void testAddPropertyCreatorDeprecated_twoArg_setsPropsSlot() throws Throwable {
        AnnotatedWithParams c = ctorOf(StringCtorBean.class);
        CreatorProperty[] empty = new CreatorProperty[0];
        collector.addPropertyCreator(c, empty);
        assertTrue(collector.hasPropertyBasedCreator());
    }

    // hasDefaultCreator() false when nothing configured
    @Test
    public void testHasDefaultCreator_noCreatorSet_false() throws Throwable {
        assertFalse(collector.hasDefaultCreator());
    }

    // hasDelegatingCreator() false when nothing configured
    @Test
    public void testHasDelegatingCreator_noCreatorSet_false() throws Throwable {
        assertFalse(collector.hasDelegatingCreator());
    }

    // hasPropertyBasedCreator() false when nothing configured
    @Test
    public void testHasPropertyBasedCreator_noCreatorSet_false() throws Throwable {
        assertFalse(collector.hasPropertyBasedCreator());
    }

    // verifyNonDup: both non-explicit, same raw param type, same runtime class -> throws
    @Test
    public void testVerifyNonDup_bothNonExplicitSameTypeSameClass_throwsIllegalArgumentException() throws Throwable {
        AnnotatedWithParams c1 = ctorOf(StringCtorBean.class);
        AnnotatedWithParams c2 = ctorOf(AnotherStringCtorBean.class);
        collector.addStringCreator(c1, false);
        try {
            collector.addStringCreator(c2, false);
            fail("expected IllegalArgumentException");
        } catch (IllegalArgumentException expected) {
            assertTrue(expected.getMessage().contains("Conflicting"));
        }
    }

    // verifyNonDup: old explicit, new non-explicit -> skip silently, old kept
    @Test
    public void testVerifyNonDup_oldExplicitNewNonExplicit_keepsOldSilently() throws Throwable {
        AnnotatedWithParams c1 = ctorOf(StringCtorBean.class);
        AnnotatedWithParams c2 = ctorOf(AnotherStringCtorBean.class);
        collector.addStringCreator(c1, true);
        collector.addStringCreator(c2, false);
        assertSame(c1, collector._creators[CreatorCollector.C_STRING]);
    }

    // verifyNonDup: both explicit, same raw param type, same runtime class -> throws
    @Test
    public void testVerifyNonDup_bothExplicitSameTypeSameClass_throwsIllegalArgumentException() throws Throwable {
        AnnotatedWithParams c1 = ctorOf(StringCtorBean.class);
        AnnotatedWithParams c2 = ctorOf(AnotherStringCtorBean.class);
        collector.addStringCreator(c1, true);
        try {
            collector.addStringCreator(c2, true);
            fail("expected IllegalArgumentException");
        } catch (IllegalArgumentException expected) {
            assertTrue(expected.getMessage().contains("Conflicting"));
        }
    }

    // verifyNonDup: new type more generic than old (List assignable from ArrayList) -> keeps old
    @Test
    public void testVerifyNonDup_newTypeMoreGeneric_keepsOldCreator() throws Throwable {
        AnnotatedWithParams oldC = ctorOf(ArrayListSubtypeBean.class);
        AnnotatedWithParams newC = ctorOf(ListSupertypeBean.class);
        collector.addStringCreator(oldC, false);
        collector.addStringCreator(newC, false);
        assertSame(oldC, collector._creators[CreatorCollector.C_STRING]);
    }

    // verifyNonDup: new type more specific than old -> overwrites with new
    @Test
    public void testVerifyNonDup_newTypeMoreSpecific_overwritesWithNewCreator() throws Throwable {
        AnnotatedWithParams oldC = ctorOf(ListSupertypeBean.class);
        AnnotatedWithParams newC = ctorOf(ArrayListSubtypeBean.class);
        collector.addStringCreator(oldC, false);
        collector.addStringCreator(newC, false);
        assertSame(newC, collector._creators[CreatorCollector.C_STRING]);
    }

    // constructValueInstantiator: Collection.class, no creators -> Vanilla creates ArrayList
    @Test
    public void testConstructValueInstantiator_collectionType_returnsVanillaCreatingArrayList() throws Throwable {
        CreatorCollector cc = collectorFor(Collection.class);
        ValueInstantiator inst = cc.constructValueInstantiator(config);
        Object created = inst.createUsingDefault((DeserializationContext) null);
        assertTrue(created instanceof ArrayList);
    }

    // constructValueInstantiator: List.class, no creators -> Vanilla creates ArrayList
    @Test
    public void testConstructValueInstantiator_listType_returnsVanillaCreatingArrayList() throws Throwable {
        CreatorCollector cc = collectorFor(List.class);
        ValueInstantiator inst = cc.constructValueInstantiator(config);
        assertTrue(inst.canCreateUsingDefault());
        Object created = inst.createUsingDefault((DeserializationContext) null);
        assertTrue(created instanceof ArrayList);
    }

    // constructValueInstantiator: Map.class, no creators -> Vanilla creates LinkedHashMap
    @Test
    public void testConstructValueInstantiator_mapType_returnsVanillaCreatingLinkedHashMap() throws Throwable {
        CreatorCollector cc = collectorFor(Map.class);
        ValueInstantiator inst = cc.constructValueInstantiator(config);
        Object created = inst.createUsingDefault((DeserializationContext) null);
        assertTrue(created instanceof LinkedHashMap);
    }

    // constructValueInstantiator: LinkedHashMap.class, no creators -> Vanilla creates LinkedHashMap
    @Test
    public void testConstructValueInstantiator_linkedHashMapType_returnsVanillaCreatingLinkedHashMap() throws Throwable {
        CreatorCollector cc = collectorFor(LinkedHashMap.class);
        ValueInstantiator inst = cc.constructValueInstantiator(config);
        Object created = inst.createUsingDefault((DeserializationContext) null);
        assertTrue(created instanceof LinkedHashMap);
    }

    // constructValueInstantiator: HashMap.class, no creators -> Vanilla creates HashMap
    @Test
    public void testConstructValueInstantiator_hashMapType_returnsVanillaCreatingHashMap() throws Throwable {
        CreatorCollector cc = collectorFor(HashMap.class);
        ValueInstantiator inst = cc.constructValueInstantiator(config);
        Object created = inst.createUsingDefault((DeserializationContext) null);
        assertTrue(created instanceof HashMap);
    }

    // constructValueInstantiator: even eligible raw type (ArrayList) with a creator set must skip Vanilla
    @Test
    public void testConstructValueInstantiator_hasNonDefaultCreator_skipsVanillaEvenForArrayList() throws Throwable {
        CreatorCollector cc = collectorFor(ArrayList.class);
        cc.addStringCreator(ctorOf(StringCtorBean.class), false);
        ValueInstantiator inst = cc.constructValueInstantiator(config);
        assertFalse(inst instanceof CreatorCollector.Vanilla);
    }

    // constructValueInstantiator: plain POJO type -> not a Vanilla instantiator
    @Test
    public void testConstructValueInstantiator_plainPojoType_returnsNonVanillaInstantiator() throws Throwable {
        CreatorCollector cc = collectorFor(DefaultCtorBean.class);
        ValueInstantiator inst = cc.constructValueInstantiator(config);
        assertFalse(inst instanceof CreatorCollector.Vanilla);
    }

    public static class DefaultCtorBean {
        public DefaultCtorBean() { }
    }

    public static class StringCtorBean {
        public StringCtorBean(String s) { }
    }

    public static class AnotherStringCtorBean {
        public AnotherStringCtorBean(String s) { }
    }

    public static class IntCtorBean {
        public IntCtorBean(int i) { }
    }

    public static class LongCtorBean {
        public LongCtorBean(long l) { }
    }

    public static class DoubleCtorBean {
        public DoubleCtorBean(double d) { }
    }

    public static class BooleanCtorBean {
        public BooleanCtorBean(boolean b) { }
    }

    public static class DelegateBean {
        public DelegateBean(Object o) { }
    }

    public static class ArrayDelegateBean {
        public ArrayDelegateBean(List<String> list) { }
    }

    public static class ArrayListSubtypeBean {
        public ArrayListSubtypeBean(ArrayList<String> list) { }
    }

    public static class ListSupertypeBean {
        public ListSupertypeBean(List<String> list) { }
    }
}
