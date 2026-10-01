package org.mockito.internal.configuration.injection;

import org.junit.Test;
import static org.junit.Assert.*;
import org.mockito.exceptions.base.MockitoException;

import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.LinkedList;

public class FinalMockCandidateFilterClaudeTest {

    static class StringFieldHolder {
        public String name;
    }

    static class IntFieldHolder {
        int number;
    }

    static class PrivateFieldHolder {
        private String secret;
    }

    static class SetterHolder {
        private String value;
        private int setterCalls = 0;

        public void setValue(String v) {
            setterCalls++;
            value = v;
        }

        public String getValue() {
            return value;
        }

        public int getSetterCalls() {
            return setterCalls;
        }
    }

    // Branch: mocks.size() != 1 (zero candidates) -> OngoingInjecter.thenInject() returns false
    @Test
    public void testFilterCandidate_zeroMocks_thenInjectReturnsFalse() throws Throwable {
        FinalMockCandidateFilter filter = new FinalMockCandidateFilter();
        StringFieldHolder holder = new StringFieldHolder();
        Field field = StringFieldHolder.class.getDeclaredField("name");
        Collection<Object> mocks = new ArrayList<Object>();
        OngoingInjecter injecter = filter.filterCandidate(mocks, field, holder);
        assertFalse(injecter.thenInject());
    }

    // Branch: zero candidates -> field must stay untouched (no-op path never calls FieldSetter)
    @Test
    public void testFilterCandidate_zeroMocks_fieldValueRemainsUnchanged() throws Throwable {
        FinalMockCandidateFilter filter = new FinalMockCandidateFilter();
        StringFieldHolder holder = new StringFieldHolder();
        holder.name = "original";
        Field field = StringFieldHolder.class.getDeclaredField("name");
        Collection<Object> mocks = new ArrayList<Object>();
        filter.filterCandidate(mocks, field, holder).thenInject();
        assertEquals("original", holder.name);
    }

    // Branch: zero candidates -> fieldInstance is never dereferenced, so null fieldInstance must not throw
    @Test
    public void testFilterCandidate_zeroMocks_nullFieldInstanceDoesNotThrow() throws Throwable {
        FinalMockCandidateFilter filter = new FinalMockCandidateFilter();
        Field field = StringFieldHolder.class.getDeclaredField("name");
        Collection<Object> mocks = new ArrayList<Object>();
        OngoingInjecter injecter = filter.filterCandidate(mocks, field, null);
        assertFalse(injecter.thenInject());
    }

    // Branch: zero candidates -> repeated calls to thenInject() stay consistent (false, false)
    @Test
    public void testFilterCandidate_zeroMocks_thenInjectCalledTwice_bothReturnFalse() throws Throwable {
        FinalMockCandidateFilter filter = new FinalMockCandidateFilter();
        StringFieldHolder holder = new StringFieldHolder();
        Field field = StringFieldHolder.class.getDeclaredField("name");
        Collection<Object> mocks = new ArrayList<Object>();
        OngoingInjecter injecter = filter.filterCandidate(mocks, field, holder);
        assertFalse(injecter.thenInject());
        assertFalse(injecter.thenInject());
    }

    // Branch: mocks.size() == 1 -> thenInject() succeeds and returns true
    @Test
    public void testFilterCandidate_oneMock_thenInjectReturnsTrue() throws Throwable {
        FinalMockCandidateFilter filter = new FinalMockCandidateFilter();
        StringFieldHolder holder = new StringFieldHolder();
        Field field = StringFieldHolder.class.getDeclaredField("name");
        Collection<Object> mocks = new ArrayList<Object>();
        mocks.add("theMock");
        assertTrue(filter.filterCandidate(mocks, field, holder).thenInject());
    }

    // Branch: single candidate injected into a public reference-type field
    @Test
    public void testFilterCandidate_oneMock_setsPublicStringField() throws Throwable {
        FinalMockCandidateFilter filter = new FinalMockCandidateFilter();
        StringFieldHolder holder = new StringFieldHolder();
        Field field = StringFieldHolder.class.getDeclaredField("name");
        Collection<Object> mocks = new ArrayList<Object>();
        mocks.add("injectedValue");
        filter.filterCandidate(mocks, field, holder).thenInject();
        assertEquals("injectedValue", holder.name);
    }

    // Branch: single candidate injected into a package-private primitive field with autoboxed value
    @Test
    public void testFilterCandidate_oneMock_setsPackagePrivateIntField() throws Throwable {
        FinalMockCandidateFilter filter = new FinalMockCandidateFilter();
        IntFieldHolder holder = new IntFieldHolder();
        Field field = IntFieldHolder.class.getDeclaredField("number");
        Collection<Object> mocks = new ArrayList<Object>();
        mocks.add(Integer.valueOf(42));
        filter.filterCandidate(mocks, field, holder).thenInject();
        assertEquals(42, holder.number);
    }

    // Branch: single candidate injected into a private field by bypassing access modifiers
    @Test
    public void testFilterCandidate_oneMock_setsPrivateFieldBypassingAccessModifier() throws Throwable {
        FinalMockCandidateFilter filter = new FinalMockCandidateFilter();
        PrivateFieldHolder holder = new PrivateFieldHolder();
        Field field = PrivateFieldHolder.class.getDeclaredField("secret");
        Collection<Object> mocks = new ArrayList<Object>();
        mocks.add("hiddenValue");
        filter.filterCandidate(mocks, field, holder).thenInject();
        assertEquals("hiddenValue", holder.secret);
    }

    // Branch: single candidate overwrites a pre-existing field value
    @Test
    public void testFilterCandidate_oneMock_overwritesExistingFieldValue() throws Throwable {
        FinalMockCandidateFilter filter = new FinalMockCandidateFilter();
        StringFieldHolder holder = new StringFieldHolder();
        holder.name = "before";
        Field field = StringFieldHolder.class.getDeclaredField("name");
        Collection<Object> mocks = new ArrayList<Object>();
        mocks.add("after");
        filter.filterCandidate(mocks, field, holder).thenInject();
        assertEquals("after", holder.name);
    }

    // Branch: single candidate that is null is still a valid match for a reference-type field
    @Test
    public void testFilterCandidate_oneMock_nullMockValue_setsReferenceFieldToNull() throws Throwable {
        FinalMockCandidateFilter filter = new FinalMockCandidateFilter();
        StringFieldHolder holder = new StringFieldHolder();
        holder.name = "before";
        Field field = StringFieldHolder.class.getDeclaredField("name");
        Collection<Object> mocks = new ArrayList<Object>();
        mocks.add(null);
        assertTrue(filter.filterCandidate(mocks, field, holder).thenInject());
        assertNull(holder.name);
    }

    // Branch: single candidate -> calling thenInject() repeatedly stays idempotent and keeps returning true
    @Test
    public void testFilterCandidate_oneMock_thenInjectCalledTwice_bothReturnTrue() throws Throwable {
        FinalMockCandidateFilter filter = new FinalMockCandidateFilter();
        StringFieldHolder holder = new StringFieldHolder();
        Field field = StringFieldHolder.class.getDeclaredField("name");
        Collection<Object> mocks = new ArrayList<Object>();
        mocks.add("value");
        OngoingInjecter injecter = filter.filterCandidate(mocks, field, holder);
        assertTrue(injecter.thenInject());
        assertTrue(injecter.thenInject());
        assertEquals("value", holder.name);
    }

    // Branch: mocks.size() == 2 (ambiguous) -> thenInject() returns false
    @Test
    public void testFilterCandidate_twoMocks_thenInjectReturnsFalse() throws Throwable {
        FinalMockCandidateFilter filter = new FinalMockCandidateFilter();
        StringFieldHolder holder = new StringFieldHolder();
        Field field = StringFieldHolder.class.getDeclaredField("name");
        Collection<Object> mocks = new ArrayList<Object>();
        mocks.add("one");
        mocks.add("two");
        assertFalse(filter.filterCandidate(mocks, field, holder).thenInject());
    }

    // Branch: ambiguous candidates (size 2) -> field must not be modified
    @Test
    public void testFilterCandidate_twoMocks_fieldNotModified() throws Throwable {
        FinalMockCandidateFilter filter = new FinalMockCandidateFilter();
        StringFieldHolder holder = new StringFieldHolder();
        Field field = StringFieldHolder.class.getDeclaredField("name");
        Collection<Object> mocks = new ArrayList<Object>();
        mocks.add("one");
        mocks.add("two");
        filter.filterCandidate(mocks, field, holder).thenInject();
        assertNull(holder.name);
    }

    // Branch: mocks.size() == 3, boundary beyond the ambiguous-pair case -> still returns false
    @Test
    public void testFilterCandidate_threeMocks_thenInjectReturnsFalse() throws Throwable {
        FinalMockCandidateFilter filter = new FinalMockCandidateFilter();
        StringFieldHolder holder = new StringFieldHolder();
        Field field = StringFieldHolder.class.getDeclaredField("name");
        Collection<Object> mocks = new ArrayList<Object>();
        mocks.add("one");
        mocks.add("two");
        mocks.add("three");
        assertFalse(filter.filterCandidate(mocks, field, holder).thenInject());
    }

    // Branch: FieldSetter throws when candidate type is incompatible with a primitive field -> caught and rethrown as MockitoException
    @Test
    public void testFilterCandidate_typeMismatchPrimitiveField_throwsMockitoException() throws Throwable {
        FinalMockCandidateFilter filter = new FinalMockCandidateFilter();
        IntFieldHolder holder = new IntFieldHolder();
        Field field = IntFieldHolder.class.getDeclaredField("number");
        Collection<Object> mocks = new ArrayList<Object>();
        mocks.add("notANumber");
        try {
            filter.filterCandidate(mocks, field, holder).thenInject();
            fail("expected MockitoException");
        } catch (MockitoException expected) {
        }
    }

    // Branch: exception message built by FinalMockCandidateFilter must mention the offending field name
    @Test
    public void testFilterCandidate_typeMismatchPrimitiveField_exceptionMessageContainsFieldName() throws Throwable {
        FinalMockCandidateFilter filter = new FinalMockCandidateFilter();
        IntFieldHolder holder = new IntFieldHolder();
        Field field = IntFieldHolder.class.getDeclaredField("number");
        Collection<Object> mocks = new ArrayList<Object>();
        mocks.add("notANumber");
        try {
            filter.filterCandidate(mocks, field, holder).thenInject();
            fail("expected MockitoException");
        } catch (MockitoException e) {
            assertTrue(e.getMessage().contains("number"));
        }
    }

    // Branch: FieldSetter throws when candidate type is incompatible with a reference-type field
    @Test
    public void testFilterCandidate_typeMismatchReferenceField_throwsMockitoException() throws Throwable {
        FinalMockCandidateFilter filter = new FinalMockCandidateFilter();
        StringFieldHolder holder = new StringFieldHolder();
        Field field = StringFieldHolder.class.getDeclaredField("name");
        Collection<Object> mocks = new ArrayList<Object>();
        mocks.add(Integer.valueOf(7));
        try {
            filter.filterCandidate(mocks, field, holder).thenInject();
            fail("expected MockitoException");
        } catch (MockitoException expected) {
        }
    }

    // Branch: caught exception must be preserved as the cause of the wrapping MockitoException
    @Test
    public void testFilterCandidate_exceptionCause_isOriginalException() throws Throwable {
        FinalMockCandidateFilter filter = new FinalMockCandidateFilter();
        IntFieldHolder holder = new IntFieldHolder();
        Field field = IntFieldHolder.class.getDeclaredField("number");
        Collection<Object> mocks = new ArrayList<Object>();
        mocks.add("notANumber");
        try {
            filter.filterCandidate(mocks, field, holder).thenInject();
            fail("expected MockitoException");
        } catch (MockitoException e) {
            assertNotNull(e.getCause());
        }
    }

    // Branch: single candidate with a null fieldInstance for a non-static field -> exception caught and wrapped
    @Test
    public void testFilterCandidate_nullFieldInstanceWithInstanceField_throwsMockitoException() throws Throwable {
        FinalMockCandidateFilter filter = new FinalMockCandidateFilter();
        Field field = StringFieldHolder.class.getDeclaredField("name");
        Collection<Object> mocks = new ArrayList<Object>();
        mocks.add("value");
        try {
            filter.filterCandidate(mocks, field, null).thenInject();
            fail("expected MockitoException");
        } catch (MockitoException expected) {
        }
    }

    // Javadoc contract: single candidate must be injected "trying first the property setter" before falling back to field access
    @Test
    public void testFilterCandidate_javadocContract_propertySetterTriedFirst() throws Throwable {
        FinalMockCandidateFilter filter = new FinalMockCandidateFilter();
        SetterHolder holder = new SetterHolder();
        Field field = SetterHolder.class.getDeclaredField("value");
        Collection<Object> mocks = new ArrayList<Object>();
        mocks.add("injected");
        assertTrue(filter.filterCandidate(mocks, field, holder).thenInject());
        assertEquals("injected", holder.getValue());
        assertEquals(1, holder.getSetterCalls());
    }

    // Branch: single candidate still works when mocks collection is a LinkedList instead of an ArrayList
    @Test
    public void testFilterCandidate_oneMock_linkedListCollection_stillInjects() throws Throwable {
        FinalMockCandidateFilter filter = new FinalMockCandidateFilter();
        StringFieldHolder holder = new StringFieldHolder();
        Field field = StringFieldHolder.class.getDeclaredField("name");
        Collection<Object> mocks = new LinkedList<Object>();
        mocks.add("fromLinkedList");
        assertTrue(filter.filterCandidate(mocks, field, holder).thenInject());
        assertEquals("fromLinkedList", holder.name);
    }

    // Branch: single candidate still works when mocks collection is an immutable singleton Set
    @Test
    public void testFilterCandidate_oneMock_singletonSetCollection_stillInjects() throws Throwable {
        FinalMockCandidateFilter filter = new FinalMockCandidateFilter();
        StringFieldHolder holder = new StringFieldHolder();
        Field field = StringFieldHolder.class.getDeclaredField("name");
        Collection<Object> mocks = Collections.<Object>singleton("fromSingleton");
        assertTrue(filter.filterCandidate(mocks, field, holder).thenInject());
        assertEquals("fromSingleton", holder.name);
    }
}
