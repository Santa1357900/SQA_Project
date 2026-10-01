package org.mockito.internal.stubbing.defaultanswers;

import java.io.Serializable;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.junit.Before;
import org.junit.Test;
import static org.junit.Assert.*;

import org.mockito.Mockito;
import org.mockito.internal.util.reflection.GenericMetadataSupport;
import org.mockito.stubbing.Answer;

public class ReturnsDeepStubsClaudeTest {

    private ReturnsDeepStubs answer;

    public interface Inner {
        String getValue();
    }

    public interface Middle {
        Inner getInner();
    }

    public static final class FinalThing {
    }

    public static abstract class AbstractThing {
        public abstract int compute();
    }

    public interface HasAbstract {
        AbstractThing getAbstractThing();
    }

    public interface Sample {
        int getCount();
        boolean isActive();
        String getLabel();
        FinalThing getFinalThing();
        List getItems();
        Inner getInner();
        Inner getInnerByArg(int id);
        Middle getMiddle();
        Sample getSelf();
    }

    public interface GenericsNest<K extends Comparable<K> & Cloneable> extends Map<K, Set<Number>> {
    }

    @Before
    public void setUp() throws Throwable {
        answer = new ReturnsDeepStubs();
    }

    // covers answer(): rawType is primitive int -> not mockable -> delegate returns 0
    @Test
    public void testAnswer_primitiveIntReturnType_returnsZero() throws Throwable {
        Sample mock = Mockito.mock(Sample.class, answer);
        assertEquals(0, mock.getCount());
    }

    // covers answer(): rawType is primitive boolean -> not mockable -> delegate returns false
    @Test
    public void testAnswer_primitiveBooleanReturnType_returnsFalse() throws Throwable {
        Sample mock = Mockito.mock(Sample.class, answer);
        assertFalse(mock.isActive());
    }



    // covers answer(): rawType is a user-defined final class -> not mockable -> delegate returns null
    @Test
    public void testAnswer_finalCustomClassReturnType_returnsNull() throws Throwable {
        Sample mock = Mockito.mock(Sample.class, answer);
        assertNull(mock.getFinalThing());
    }

    // covers answer(): rawType is an abstract class -> mockable -> deep stub mock created
    @Test
    public void testAnswer_abstractClassReturnType_createsDeepStubMock() throws Throwable {
        HasAbstract mock = Mockito.mock(HasAbstract.class, answer);
        AbstractThing thing = mock.getAbstractThing();
        assertNotNull(thing);
        assertEquals(0, thing.compute());
    }

    // covers answer(): rawType is an interface -> mockable -> deep stub mock is not null
    @Test
    public void testAnswer_mockableInterfaceReturnType_createsNonNullDeepStub() throws Throwable {
        Sample mock = Mockito.mock(Sample.class, answer);
        assertNotNull(mock.getInner());
    }

    // covers answer(): rawType is a JDK collection interface (List) -> mockable, size defaults to 0
    @Test
    public void testAnswer_mockableListReturnType_sizeDefaultsToZero() throws Throwable {
        Sample mock = Mockito.mock(Sample.class, answer);
        List items = mock.getItems();
        assertNotNull(items);
        assertEquals(0, items.size());
    }

    // covers deepStub(): Javadoc contract - same invocation matches previously stubbed invocation
    @Test
    public void testAnswer_sameInvocationTwice_returnsSameMockInstance() throws Throwable {
        Sample mock = Mockito.mock(Sample.class, answer);
        Inner first = mock.getInner();
        Inner second = mock.getInner();
        assertSame(first, second);
    }

    // covers deepStub(): different arguments -> invocation does not match -> distinct mocks
    @Test
    public void testAnswer_differentArguments_returnsDistinctMockInstances() throws Throwable {
        Sample mock = Mockito.mock(Sample.class, answer);
        Inner first = mock.getInnerByArg(1);
        Inner second = mock.getInnerByArg(2);
        assertNotSame(first, second);
    }

    // covers deepStub(): loop over multiple stubbed invocations finds the matching one
    @Test
    public void testAnswer_sameArgumentsAfterMultipleCalls_returnsSameMockInstance() throws Throwable {
        Sample mock = Mockito.mock(Sample.class, answer);
        Inner first = mock.getInnerByArg(5);
        mock.getInnerByArg(6);
        Inner third = mock.getInnerByArg(5);
        assertSame(first, third);
    }

    // covers deepStub(): different methods never match each other's stubbed invocation
    @Test
    public void testAnswer_differentMethodsSameReturnType_returnDistinctMockInstances() throws Throwable {
        Sample mock = Mockito.mock(Sample.class, answer);
        Inner a = mock.getInner();
        Inner b = mock.getInnerByArg(1);
        assertNotSame(a, b);
    }

    // covers answer(): self-referencing return type produces deep stub mock, matching works
    @Test
    public void testAnswer_selfReferencingReturnType_sameInvocationReturnsSameMock() throws Throwable {
        Sample mock = Mockito.mock(Sample.class, answer);
        Sample self1 = mock.getSelf();
        Sample self2 = mock.getSelf();
        assertSame(self1, self2);
    }

    // covers nested chain: first level of a two-level deep stub chain is consistently matched
    @Test
    public void testAnswer_nestedChain_sameOuterInvocation_returnsSameMiddleMock() throws Throwable {
        Sample mock = Mockito.mock(Sample.class, answer);
        Middle first = mock.getMiddle();
        Middle second = mock.getMiddle();
        assertSame(first, second);
    }

    // covers nested chain: second level of a two-level deep stub chain is consistently matched
    @Test
    public void testAnswer_nestedChain_sameInnerInvocation_returnsSameInnerMock() throws Throwable {
        Sample mock = Mockito.mock(Sample.class, answer);
        Inner first = mock.getMiddle().getInner();
        Inner second = mock.getMiddle().getInner();
        assertSame(first, second);
    }

    // covers Javadoc generics example: nested Map/Set/Iterator resolve down to a Number deep stub
    @Test
    public void testAnswer_nestedGenericsJavadocExample_numberValueDefaultsToZero() throws Throwable {
        GenericsNest<?> mock = Mockito.mock(GenericsNest.class, answer);
        Number number = mock.entrySet().iterator().next().getValue().iterator().next();
        assertNotNull(number);
        assertEquals(0, number.intValue());
    }

    // covers withSettingsUsing(): generic bound with extra interfaces (K extends Comparable & Cloneable)
    @Test
    public void testAnswer_genericBoundWithExtraInterfaces_mockImplementsBothBounds() throws Throwable {
        GenericsNest<?> mock = Mockito.mock(GenericsNest.class, answer);
        Map.Entry<?, Set<Number>> entry = mock.entrySet().iterator().next();
        Object key = entry.getKey();
        assertNotNull(key);
        assertTrue(key instanceof Cloneable);
    }

    // covers deepStub(): different mock instances have independent invocation containers
    @Test
    public void testAnswer_differentMockInstances_returnIndependentDeepStubs() throws Throwable {
        Sample mock1 = Mockito.mock(Sample.class, answer);
        Sample mock2 = Mockito.mock(Sample.class, answer);
        Inner a = mock1.getInner();
        Inner b = mock2.getInner();
        assertNotSame(a, b);
    }

    // covers deepStub(): repeated collection-type invocation also matches and reuses the mock
    @Test
    public void testAnswer_repeatedListCall_returnsSameListMockInstance() throws Throwable {
        Sample mock = Mockito.mock(Sample.class, answer);
        List first = mock.getItems();
        List second = mock.getItems();
        assertSame(first, second);
    }

    // covers actualParameterizedType(): metadata rawType matches the interface passed to mock()
    @Test
    public void testActualParameterizedType_plainInterfaceMock_rawTypeMatchesMockedClass() throws Throwable {
        Inner mock = Mockito.mock(Inner.class);
        GenericMetadataSupport metadata = answer.actualParameterizedType(mock);
        assertEquals(Inner.class, metadata.rawType());
    }

    // covers actualParameterizedType(): metadata rawType matches a JDK interface (List)
    @Test
    public void testActualParameterizedType_listMock_rawTypeMatchesListClass() throws Throwable {
        List mock = Mockito.mock(List.class);
        GenericMetadataSupport metadata = answer.actualParameterizedType(mock);
        assertEquals(List.class, metadata.rawType());
    }

    // covers actualParameterizedType(): passing a non-mock object cannot resolve a mock handler
    @Test
    public void testActualParameterizedType_nonMockObject_throwsException() throws Throwable {
        try {
            answer.actualParameterizedType(new Object());
            fail("expected an exception when the argument is not a mock");
        } catch (Exception expected) {
            // no mock handler can be retrieved for a plain, non-mocked object
        }
    }

    // covers class contract: ReturnsDeepStubs is a Serializable Answer<Object> as declared
    @Test
    public void testConstructor_implementsAnswerAndSerializable() throws Throwable {
        assertTrue(answer instanceof Answer);
        assertTrue(answer instanceof Serializable);
    }
}
