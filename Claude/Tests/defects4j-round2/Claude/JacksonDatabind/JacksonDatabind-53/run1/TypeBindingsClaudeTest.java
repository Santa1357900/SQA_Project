package com.fasterxml.jackson.databind.type;

import java.util.List;
import java.util.ArrayList;
import java.util.Map;

import org.junit.Before;
import org.junit.Test;
import static org.junit.Assert.*;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.JavaType;

public class TypeBindingsClaudeTest
{
    public static class NoParams {}
    public static class OneParam<A> {}
    public static class TwoParams<A, B> {}
    public static class ThreeParams<A, B, C> {}

    private ObjectMapper mapper;
    private JavaType stringType;
    private JavaType intType;
    private JavaType longType;

    @Before
    public void setUp() throws Throwable
    {
        mapper = new ObjectMapper();
        stringType = mapper.constructType(String.class);
        intType = mapper.constructType(Integer.class);
        longType = mapper.constructType(Long.class);
    }

    // emptyBindings(): returns canonical, zero-size instance on every call
    @Test
    public void testEmptyBindings_returnsCanonicalEmptyInstance() throws Throwable {
        TypeBindings b = TypeBindings.emptyBindings();
        assertTrue(b.isEmpty());
        assertEquals(0, b.size());
        assertSame(b, TypeBindings.emptyBindings());
    }

    // create(Class, List<JavaType>): null list branch produces empty bindings
    @Test
    public void testCreateWithList_nullList_producesEmptyBindings() throws Throwable {
        TypeBindings b = TypeBindings.create(NoParams.class, (List<JavaType>) null);
        assertTrue(b.isEmpty());
        assertEquals(0, b.size());
    }

    // create(Class, List<JavaType>): empty list branch produces empty bindings
    @Test
    public void testCreateWithList_emptyList_producesEmptyBindings() throws Throwable {
        List<JavaType> list = new ArrayList<JavaType>();
        TypeBindings b = TypeBindings.create(NoParams.class, list);
        assertTrue(b.isEmpty());
    }

    // create(Class, List<JavaType>): 1-element list binds to declared variable "A"
    @Test
    public void testCreateWithList_oneElement_bindsCorrectly() throws Throwable {
        List<JavaType> list = new ArrayList<JavaType>();
        list.add(stringType);
        TypeBindings b = TypeBindings.create(OneParam.class, list);
        assertEquals(1, b.size());
        assertEquals(stringType, b.findBoundType("A"));
    }

    // create(Class, List<JavaType>): mismatched count throws IllegalArgumentException
    @Test
    public void testCreateWithList_mismatchedCount_throws() throws Throwable {
        List<JavaType> list = new ArrayList<JavaType>();
        list.add(stringType);
        try {
            TypeBindings.create(TwoParams.class, list);
            fail("expected IllegalArgumentException");
        } catch (IllegalArgumentException expected) {
            assertTrue(expected.getMessage().contains("class expects"));
        }
    }

    // create(Class, JavaType[]): null array on no-param class produces empty bindings
    @Test
    public void testCreateArray_nullArray_noParamClass_producesEmpty() throws Throwable {
        TypeBindings b = TypeBindings.create(NoParams.class, (JavaType[]) null);
        assertTrue(b.isEmpty());
        assertEquals(0, b.size());
    }

    // create(Class, JavaType[]): length 1 delegates to single-arg create
    @Test
    public void testCreateArray_lengthOne_delegatesCorrectly() throws Throwable {
        TypeBindings b = TypeBindings.create(OneParam.class, new JavaType[] { stringType });
        assertEquals(1, b.size());
        assertEquals("A", b.getBoundName(0));
        assertEquals(stringType, b.getBoundType(0));
    }

    // create(Class, JavaType[]): length 2 delegates to two-arg create
    @Test
    public void testCreateArray_lengthTwo_delegatesCorrectly() throws Throwable {
        TypeBindings b = TypeBindings.create(TwoParams.class, new JavaType[] { stringType, intType });
        assertEquals(2, b.size());
        assertEquals(stringType, b.getBoundType(0));
        assertEquals(intType, b.getBoundType(1));
    }

    // create(Class, JavaType[]): length 3 falls through to general path, matches declared vars
    @Test
    public void testCreateArray_lengthThree_matchesDeclaredVars() throws Throwable {
        TypeBindings b = TypeBindings.create(ThreeParams.class,
                new JavaType[] { stringType, intType, longType });
        assertEquals(3, b.size());
        assertEquals("A", b.getBoundName(0));
        assertEquals("B", b.getBoundName(1));
        assertEquals("C", b.getBoundName(2));
    }

    // create(Class, JavaType[]): mismatched count in general path throws
    @Test
    public void testCreateArray_mismatchedCountGeneralPath_throws() throws Throwable {
        try {
            TypeBindings.create(ThreeParams.class, new JavaType[0]);
            fail("expected IllegalArgumentException");
        } catch (IllegalArgumentException expected) {
            assertTrue(expected.getMessage().contains("class expects"));
        }
    }

    // create(Class, JavaType): normal single type-parameter class binds correctly
    @Test
    public void testCreateSingleArg_normalClass_success() throws Throwable {
        TypeBindings b = TypeBindings.create(OneParam.class, stringType);
        assertEquals(stringType, b.findBoundType("A"));
        assertEquals(1, b.size());
        assertFalse(b.hasUnbound("A"));
    }

    // create(Class, JavaType): wrong param count (0 expected) throws
    @Test
    public void testCreateSingleArg_wrongParamCount_throws() throws Throwable {
        try {
            TypeBindings.create(NoParams.class, stringType);
            fail("expected IllegalArgumentException");
        } catch (IllegalArgumentException expected) {
            assertTrue(expected.getMessage().contains("1 type parameter"));
        }
    }

    // create(Class, JavaType): TypeParamStash special-cased List.class resolves correctly
    @Test
    public void testCreateSingleArg_stashSpecialListClass_success() throws Throwable {
        String expectedName = List.class.getTypeParameters()[0].getName();
        TypeBindings b = TypeBindings.create(List.class, stringType);
        assertEquals(stringType, b.findBoundType(expectedName));
    }

    // create(Class, JavaType, JavaType): normal two type-parameter class binds correctly
    @Test
    public void testCreateTwoArg_normalClass_success() throws Throwable {
        TypeBindings b = TypeBindings.create(TwoParams.class, stringType, intType);
        assertEquals(stringType, b.findBoundType("A"));
        assertEquals(intType, b.findBoundType("B"));
    }

    // create(Class, JavaType, JavaType): wrong param count (1 expected) throws
    @Test
    public void testCreateTwoArg_wrongParamCount_throws() throws Throwable {
        try {
            TypeBindings.create(OneParam.class, stringType, intType);
            fail("expected IllegalArgumentException");
        } catch (IllegalArgumentException expected) {
            assertTrue(expected.getMessage().contains("2 type parameters"));
        }
    }

    // create(Class, JavaType, JavaType): TypeParamStash special-cased Map.class resolves correctly
    @Test
    public void testCreateTwoArg_stashSpecialMapClass_success() throws Throwable {
        String k = Map.class.getTypeParameters()[0].getName();
        String v = Map.class.getTypeParameters()[1].getName();
        TypeBindings b = TypeBindings.create(Map.class, stringType, intType);
        assertEquals(stringType, b.findBoundType(k));
        assertEquals(intType, b.findBoundType(v));
    }

    // createIfNeeded(Class, JavaType): class with 0 type params returns EMPTY bindings
    @Test
    public void testCreateIfNeededSingle_noParamClass_returnsEmpty() throws Throwable {
        TypeBindings b = TypeBindings.createIfNeeded(NoParams.class, stringType);
        assertTrue(b.isEmpty());
    }

    // createIfNeeded(Class, JavaType): normal 1-param class binds correctly
    @Test
    public void testCreateIfNeededSingle_oneParamClass_bindsCorrectly() throws Throwable {
        TypeBindings b = TypeBindings.createIfNeeded(OneParam.class, stringType);
        assertEquals(stringType, b.findBoundType("A"));
    }

    // createIfNeeded(Class, JavaType): wrong param count (2 expected) throws
    @Test
    public void testCreateIfNeededSingle_wrongParamCount_throws() throws Throwable {
        try {
            TypeBindings.createIfNeeded(TwoParams.class, stringType);
            fail("expected IllegalArgumentException");
        } catch (IllegalArgumentException expected) {
            assertTrue(expected.getMessage().contains("1 type parameter"));
        }
    }

    // createIfNeeded(Class, JavaType[]): 0-param class returns EMPTY regardless of array
    @Test
    public void testCreateIfNeededArray_noParamClass_returnsEmpty() throws Throwable {
        TypeBindings b = TypeBindings.createIfNeeded(NoParams.class, new JavaType[] { stringType });
        assertTrue(b.isEmpty());
    }

    // createIfNeeded(Class, JavaType[]): null types array but class needs params throws
    @Test
    public void testCreateIfNeededArray_nullTypesWithParams_throws() throws Throwable {
        try {
            TypeBindings.createIfNeeded(OneParam.class, (JavaType[]) null);
            fail("expected IllegalArgumentException");
        } catch (IllegalArgumentException expected) {
            assertTrue(expected.getMessage().contains("class expects"));
        }
    }

    // createIfNeeded(Class, JavaType[]): matching count succeeds
    @Test
    public void testCreateIfNeededArray_matchingCount_success() throws Throwable {
        TypeBindings b = TypeBindings.createIfNeeded(TwoParams.class,
                new JavaType[] { stringType, intType });
        assertEquals(2, b.size());
    }

    // createIfNeeded(Class, JavaType[]): mismatched count throws
    @Test
    public void testCreateIfNeededArray_mismatchedCount_throws() throws Throwable {
        try {
            TypeBindings.createIfNeeded(TwoParams.class, new JavaType[] { stringType });
            fail("expected IllegalArgumentException");
        } catch (IllegalArgumentException expected) {
            assertTrue(expected.getMessage().contains("class expects"));
        }
    }

    // withUnboundVariable: adds the given name as unbound on top of empty bindings
    @Test
    public void testWithUnboundVariable_addsNameToEmptyBindings() throws Throwable {
        TypeBindings b = TypeBindings.emptyBindings().withUnboundVariable("X");
        assertTrue(b.hasUnbound("X"));
        assertFalse(b.hasUnbound("Y"));
    }

    // withUnboundVariable: chained calls retain both previously registered names
    @Test
    public void testWithUnboundVariable_chainedCallsRetainBothNames() throws Throwable {
        TypeBindings b = TypeBindings.emptyBindings().withUnboundVariable("X").withUnboundVariable("Y");
        assertTrue(b.hasUnbound("X"));
        assertTrue(b.hasUnbound("Y"));
    }

    // withUnboundVariable: original bound types/size are preserved unchanged
    @Test
    public void testWithUnboundVariable_preservesOriginalBoundTypes() throws Throwable {
        TypeBindings original = TypeBindings.create(OneParam.class, stringType);
        TypeBindings b = original.withUnboundVariable("Z");
        assertEquals(1, b.size());
        assertEquals(stringType, b.findBoundType("A"));
    }

    // findBoundType: unknown name returns null
    @Test
    public void testFindBoundType_nameNotBound_returnsNull() throws Throwable {
        TypeBindings b = TypeBindings.create(OneParam.class, stringType);
        assertNull(b.findBoundType("NOT_BOUND"));
    }

    // isEmpty: reflects actual presence of bound types
    @Test
    public void testIsEmpty_reflectsBoundTypeCount() throws Throwable {
        assertTrue(TypeBindings.emptyBindings().isEmpty());
        TypeBindings b = TypeBindings.create(OneParam.class, stringType);
        assertFalse(b.isEmpty());
    }

    // size(): returns exact count of bound types
    @Test
    public void testSize_returnsExactCount() throws Throwable {
        TypeBindings b = TypeBindings.create(TwoParams.class, stringType, intType);
        assertEquals(2, b.size());
    }

    // getBoundName: negative index returns null
    @Test
    public void testGetBoundName_negativeIndex_returnsNull() throws Throwable {
        TypeBindings b = TypeBindings.create(OneParam.class, stringType);
        assertNull(b.getBoundName(-1));
    }

    // getBoundName: index equal to size (boundary, out of bounds) returns null
    @Test
    public void testGetBoundName_indexEqualsSize_returnsNull() throws Throwable {
        TypeBindings b = TypeBindings.create(OneParam.class, stringType);
        assertNull(b.getBoundName(1));
    }

    // getBoundName: valid indices return declared variable names in order
    @Test
    public void testGetBoundName_validIndex_returnsDeclaredName() throws Throwable {
        TypeBindings b = TypeBindings.create(TwoParams.class, stringType, intType);
        assertEquals("A", b.getBoundName(0));
        assertEquals("B", b.getBoundName(1));
    }

    // getBoundType: out-of-bounds index returns null
    @Test
    public void testGetBoundType_outOfBoundsIndex_returnsNull() throws Throwable {
        TypeBindings b = TypeBindings.create(OneParam.class, stringType);
        assertNull(b.getBoundType(5));
    }

    // getBoundType: valid index returns the bound JavaType
    @Test
    public void testGetBoundType_validIndex_returnsBoundType() throws Throwable {
        TypeBindings b = TypeBindings.create(TwoParams.class, stringType, intType);
        assertEquals(stringType, b.getBoundType(0));
        assertEquals(intType, b.getBoundType(1));
    }

    // getTypeParameters: empty bindings returns empty list
    @Test
    public void testGetTypeParameters_empty_returnsEmptyList() throws Throwable {
        List<JavaType> params = TypeBindings.emptyBindings().getTypeParameters();
        assertTrue(params.isEmpty());
    }

    // getTypeParameters: non-empty bindings returns types in declaration order
    @Test
    public void testGetTypeParameters_nonEmpty_returnsInOrder() throws Throwable {
        TypeBindings b = TypeBindings.create(TwoParams.class, stringType, intType);
        List<JavaType> params = b.getTypeParameters();
        assertEquals(2, params.size());
        assertEquals(stringType, params.get(0));
        assertEquals(intType, params.get(1));
    }

    // toString: empty bindings returns "<>"
    @Test
    public void testToString_empty_returnsEmptyAngleBrackets() throws Throwable {
        assertEquals("<>", TypeBindings.emptyBindings().toString());
    }

    // toString: non-empty bindings wraps the generic signature in angle brackets
    @Test
    public void testToString_nonEmpty_wrapsGenericSignature() throws Throwable {
        TypeBindings b = TypeBindings.create(OneParam.class, stringType);
        String expected = "<" + stringType.getGenericSignature() + ">";
        assertEquals(expected, b.toString());
    }

    // hashCode: empty bindings hash is the fixed base value of 1
    @Test
    public void testHashCode_emptyBindings_isOne() throws Throwable {
        assertEquals(1, TypeBindings.emptyBindings().hashCode());
    }

    // hashCode: consistent across separately created but equal bindings
    @Test
    public void testHashCode_consistentForEqualBindings() throws Throwable {
        TypeBindings b1 = TypeBindings.create(OneParam.class, stringType);
        TypeBindings b2 = TypeBindings.create(OneParam.class, stringType);
        assertEquals(b1.hashCode(), b2.hashCode());
    }

    // equals: same instance is equal to itself
    @Test
    public void testEquals_sameInstance_true() throws Throwable {
        TypeBindings b = TypeBindings.create(OneParam.class, stringType);
        assertTrue(b.equals(b));
    }

    // equals: null or different-class object is not equal
    @Test
    public void testEquals_nullOrDifferentClass_false() throws Throwable {
        TypeBindings b = TypeBindings.create(OneParam.class, stringType);
        assertFalse(b.equals(null));
        assertFalse(b.equals("not a TypeBindings"));
    }

    // equals: different sizes are not equal
    @Test
    public void testEquals_differentSize_false() throws Throwable {
        TypeBindings b1 = TypeBindings.create(OneParam.class, stringType);
        TypeBindings b2 = TypeBindings.create(TwoParams.class, stringType, intType);
        assertFalse(b1.equals(b2));
    }

    // equals: same bound types in the same order are equal
    @Test
    public void testEquals_sameBoundTypes_true() throws Throwable {
        TypeBindings b1 = TypeBindings.create(TwoParams.class, stringType, intType);
        TypeBindings b2 = TypeBindings.create(TwoParams.class, stringType, intType);
        assertTrue(b1.equals(b2));
    }

    // equals: differing bound types are not equal
    @Test
    public void testEquals_differentBoundTypes_false() throws Throwable {
        TypeBindings b1 = TypeBindings.create(OneParam.class, stringType);
        TypeBindings b2 = TypeBindings.create(OneParam.class, intType);
        assertFalse(b1.equals(b2));
    }
}
