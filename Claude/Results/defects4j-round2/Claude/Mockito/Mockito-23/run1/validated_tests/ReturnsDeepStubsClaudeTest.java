package org.mockito.internal.stubbing.defaultanswers;

import static org.junit.Assert.*;
import static org.mockito.Mockito.*;

import java.io.Serializable;

import org.junit.Before;
import org.junit.Test;
import org.mockito.stubbing.Answer;

public class ReturnsDeepStubsClaudeTest {

    public static interface Foo {
        Bar getBar();
        Bar getOtherBar();
        int getCount();
        long getBigCount();
        boolean isActive();
        double getRatio();
        float getFloatVal();
        Integer getBoxedCount();
        Long getBoxedBigCount();
        Boolean getBoxedActive();
        String getName();
        String[] getNames();
    }

    public static interface Bar {
        String getName();
        Baz getBaz();
    }

    public static interface Baz {
        int getValue();
    }

    private ReturnsDeepStubs returnsDeepStubs;

    @Before
    public void setUp() throws Throwable {
        returnsDeepStubs = new ReturnsDeepStubs();
    }

    // constructor produces a usable Answer that is Serializable (class contract)
    @Test
    public void testConstructor_createsSerializableAnswerInstance() throws Throwable {
        assertTrue(returnsDeepStubs instanceof Serializable);
        assertTrue(returnsDeepStubs instanceof Answer);
    }

    // branch: rawType mockable (interface) -> getMock creates a non-null deep stub
    @Test
    public void testAnswer_methodReturningInterfaceType_returnsNonNullDeepStub() throws Throwable {
        Foo foo = mock(Foo.class, returnsDeepStubs);
        Bar bar = foo.getBar();
        assertNotNull(bar);
    }

    // branch: loop in getMock finds previously recorded invocation -> same mock returned
    @Test
    public void testAnswer_repeatedCallSameMethod_returnsSameDeepStubMockInstance() throws Throwable {
        Foo foo = mock(Foo.class, returnsDeepStubs);
        Bar first = foo.getBar();
        Bar second = foo.getBar();
        assertSame(first, second);
    }

    // branch: distinct invocations do not match -> distinct deep stub mocks created
    @Test
    public void testAnswer_differentMethodsReturningSameType_returnDistinctMocks() throws Throwable {
        Foo foo = mock(Foo.class, returnsDeepStubs);
        Bar bar = foo.getBar();
        Bar otherBar = foo.getOtherBar();
        assertNotSame(bar, otherBar);
    }

    // branch: loop iterates over multiple recorded stubbed invocations to find the match
    @Test
    public void testAnswer_multipleDistinctStubsRecorded_matchingInvocationFoundAmongMultiple() throws Throwable {
        Foo foo = mock(Foo.class, returnsDeepStubs);
        Bar otherBar1 = foo.getOtherBar();
        Bar bar1 = foo.getBar();
        Bar otherBar2 = foo.getOtherBar();
        assertSame(otherBar1, otherBar2);
        assertNotSame(otherBar1, bar1);
    }

    // documented behavior: nested chained deep stub calls stay consistent across invocations
    @Test
    public void testAnswer_chainedNestedInvocation_returnsConsistentDeepStub() throws Throwable {
        Foo foo = mock(Foo.class, returnsDeepStubs);
        Baz first = foo.getBar().getBaz();
        Baz second = foo.getBar().getBaz();
        assertSame(first, second);
    }

    // branch: primitive leaf at the end of a deep stub chain returns default value 0
    @Test
    public void testAnswer_leafPrimitiveInDeepChain_returnsDefaultZero() throws Throwable {
        Foo foo = mock(Foo.class, returnsDeepStubs);
        assertEquals(0, foo.getBar().getBaz().getValue());
    }

    // primary documented use-case: deep stub combined with explicit when/thenReturn stubbing
    @Test
    public void testAnswer_deepStubCombinedWithExplicitStubbing_returnsStubbedValue() throws Throwable {
        Foo foo = mock(Foo.class, returnsDeepStubs);
        when(foo.getBar().getName()).thenReturn("stubbed");
        assertEquals("stubbed", foo.getBar().getName());
    }

    // documented use-case extended to a three-level deep chain with explicit stubbing on leaf
    @Test
    public void testAnswer_deepChainWithExplicitStubbingAtLeafLevel_returnsStubbedValue() throws Throwable {
        Foo foo = mock(Foo.class, returnsDeepStubs);
        when(foo.getBar().getBaz().getValue()).thenReturn(42);
        assertEquals(42, foo.getBar().getBaz().getValue());
    }



    // branch: rawType not mockable (primitive int) -> delegate.returnValueFor default 0
    @Test
    public void testAnswer_methodReturningPrimitiveInt_returnsDefaultZero() throws Throwable {
        Foo foo = mock(Foo.class, returnsDeepStubs);
        assertEquals(0, foo.getCount());
    }

    // branch: rawType not mockable (primitive long) -> delegate default 0L
    @Test
    public void testAnswer_methodReturningPrimitiveLong_returnsDefaultZero() throws Throwable {
        Foo foo = mock(Foo.class, returnsDeepStubs);
        assertEquals(0L, foo.getBigCount());
    }

    // branch: rawType not mockable (primitive boolean) -> delegate default false
    @Test
    public void testAnswer_methodReturningPrimitiveBoolean_returnsDefaultFalse() throws Throwable {
        Foo foo = mock(Foo.class, returnsDeepStubs);
        assertFalse(foo.isActive());
    }

    // branch: rawType not mockable (primitive double) -> delegate default 0.0
    @Test
    public void testAnswer_methodReturningPrimitiveDouble_returnsDefaultZero() throws Throwable {
        Foo foo = mock(Foo.class, returnsDeepStubs);
        assertEquals(0.0, foo.getRatio(), 1e-9);
    }

    // branch: rawType not mockable (primitive float) -> delegate default 0.0f
    @Test
    public void testAnswer_methodReturningPrimitiveFloat_returnsDefaultZero() throws Throwable {
        Foo foo = mock(Foo.class, returnsDeepStubs);
        assertEquals(0.0f, foo.getFloatVal(), 1e-6f);
    }

    // branch: rawType not mockable (final boxed Integer) -> delegate default numeric value
    @Test
    public void testAnswer_methodReturningBoxedInteger_returnsDefaultZero() throws Throwable {
        Foo foo = mock(Foo.class, returnsDeepStubs);
        assertEquals(Integer.valueOf(0), foo.getBoxedCount());
    }

    // branch: rawType not mockable (final boxed Long) -> delegate default numeric value
    @Test
    public void testAnswer_methodReturningBoxedLong_returnsDefaultZero() throws Throwable {
        Foo foo = mock(Foo.class, returnsDeepStubs);
        assertEquals(Long.valueOf(0L), foo.getBoxedBigCount());
    }

    // branch: rawType not mockable (final boxed Boolean) -> delegate default false
    @Test
    public void testAnswer_methodReturningBoxedBoolean_returnsDefaultFalse() throws Throwable {
        Foo foo = mock(Foo.class, returnsDeepStubs);
        assertFalse(foo.getBoxedActive().booleanValue());
    }







    // branch: separate mock instances maintain independent invocation containers / deep stubs
    @Test
    public void testAnswer_independentMockInstances_produceIndependentDeepStubs() throws Throwable {
        Foo foo1 = mock(Foo.class, returnsDeepStubs);
        Foo foo2 = mock(Foo.class, returnsDeepStubs);
        assertNotSame(foo1.getBar(), foo2.getBar());
    }

    // branch: repeated calls directly on an already-returned deep stub sub-chain stay consistent
    @Test
    public void testAnswer_repeatedCallOnSameDeepStubInstance_subChainRemainsConsistent() throws Throwable {
        Foo foo = mock(Foo.class, returnsDeepStubs);
        Bar bar = foo.getBar();
        Baz baz1 = bar.getBaz();
        Baz baz2 = bar.getBaz();
        assertSame(baz1, baz2);
    }

    // critical matching-loop branch: first-level deep stub identity must not change after later explicit stubbing
    @Test
    public void testAnswer_afterExplicitStubbing_firstLevelDeepStubIdentityUnchanged() throws Throwable {
        Foo foo = mock(Foo.class, returnsDeepStubs);
        Bar barBefore = foo.getBar();
        when(foo.getBar().getName()).thenReturn("x");
        Bar barAfter = foo.getBar();
        assertSame(barBefore, barAfter);
    }
}
