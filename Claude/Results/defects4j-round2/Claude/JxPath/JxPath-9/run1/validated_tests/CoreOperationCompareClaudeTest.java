package org.apache.commons.jxpath.ri.compiler;

import org.apache.commons.jxpath.JXPathContext;
import org.junit.Test;
import static org.junit.Assert.*;

public class CoreOperationCompareClaudeTest {

    public static class Item {
        private int id;

        public Item(int id) {
            this.id = id;
        }

        public int getId() {
            return id;
        }

        public boolean equals(Object o) {
            if (!(o instanceof Item)) {
                return false;
            }
            return ((Item) o).id == this.id;
        }

        public int hashCode() {
            return id;
        }
    }

    public static class Bean {
        private String name;
        private String[] items;
        private String[] others;
        private Item item1;
        private Item item2;

        public String getName() {
            return name;
        }

        public void setName(String name) {
            this.name = name;
        }

        public String[] getItems() {
            return items;
        }

        public void setItems(String[] items) {
            this.items = items;
        }

        public String[] getOthers() {
            return others;
        }

        public void setOthers(String[] others) {
            this.others = others;
        }

        public Item getItem1() {
            return item1;
        }

        public void setItem1(Item item1) {
            this.item1 = item1;
        }

        public Item getItem2() {
            return item2;
        }

        public void setItem2(Item item2) {
            this.item2 = item2;
        }
    }

    // equal(Object,Object): Number branch, numeric equal values -> true
    @Test
    public void testEqual_NumberEqualNumber_ReturnsTrue() throws Throwable {
        JXPathContext ctx = JXPathContext.newContext(new Bean());
        Object result = ctx.getValue("1 = 1");
        assertEquals(Boolean.TRUE, result);
    }

    // equal(Object,Object): Number branch, different values -> false
    @Test
    public void testEqual_NumberNotEqualNumber_ReturnsFalse() throws Throwable {
        JXPathContext ctx = JXPathContext.newContext(new Bean());
        Object result = ctx.getValue("1 = 2");
        assertEquals(Boolean.FALSE, result);
    }

    // CoreOperationNotEqual negation of equal(): different numbers -> true
    @Test
    public void testNotEqual_NumberDifferent_ReturnsTrue() throws Throwable {
        JXPathContext ctx = JXPathContext.newContext(new Bean());
        Object result = ctx.getValue("1 != 2");
        assertEquals(Boolean.TRUE, result);
    }

    // CoreOperationNotEqual negation of equal(): same numbers -> false
    @Test
    public void testNotEqual_NumberSame_ReturnsFalse() throws Throwable {
        JXPathContext ctx = JXPathContext.newContext(new Bean());
        Object result = ctx.getValue("1 != 1");
        assertEquals(Boolean.FALSE, result);
    }

    // equal(Object,Object): Number branch takes precedence over String, numeric coercion of string
    @Test
    public void testEqual_NumberStringCoercion_NumericCompareTrue() throws Throwable {
        JXPathContext ctx = JXPathContext.newContext(new Bean());
        Object result = ctx.getValue("1 = '1'");
        assertEquals(Boolean.TRUE, result);
    }

    // equal(Object,Object): Number branch, negative numbers equal
    @Test
    public void testEqual_NegativeNumbers_ReturnsTrue() throws Throwable {
        JXPathContext ctx = JXPathContext.newContext(new Bean());
        Object result = ctx.getValue("-1 = -1");
        assertEquals(Boolean.TRUE, result);
    }

    // equal(Object,Object): Number branch, int literal equals double literal
    @Test
    public void testEqual_IntVsDoubleLiteral_ReturnsTrue() throws Throwable {
        JXPathContext ctx = JXPathContext.newContext(new Bean());
        Object result = ctx.getValue("1 = 1.0");
        assertEquals(Boolean.TRUE, result);
    }

    // equal(Object,Object): String branch, equal strings -> true
    @Test
    public void testEqual_StringEqualString_ReturnsTrue() throws Throwable {
        JXPathContext ctx = JXPathContext.newContext(new Bean());
        Object result = ctx.getValue("'abc' = 'abc'");
        assertEquals(Boolean.TRUE, result);
    }

    // equal(Object,Object): String branch, different strings -> false
    @Test
    public void testEqual_StringDifferent_ReturnsFalse() throws Throwable {
        JXPathContext ctx = JXPathContext.newContext(new Bean());
        Object result = ctx.getValue("'abc' = 'abd'");
        assertEquals(Boolean.FALSE, result);
    }

    // CoreOperationNotEqual negation: different strings -> true
    @Test
    public void testNotEqual_StringDifferent_ReturnsTrue() throws Throwable {
        JXPathContext ctx = JXPathContext.newContext(new Bean());
        Object result = ctx.getValue("'abc' != 'abd'");
        assertEquals(Boolean.TRUE, result);
    }

    // equal(Object,Object): Boolean branch, true=true -> true
    @Test
    public void testEqual_BooleanTrueTrue_ReturnsTrue() throws Throwable {
        JXPathContext ctx = JXPathContext.newContext(new Bean());
        Object result = ctx.getValue("true() = true()");
        assertEquals(Boolean.TRUE, result);
    }

    // equal(Object,Object): Boolean branch, true=false -> false
    @Test
    public void testEqual_BooleanTrueFalse_ReturnsFalse() throws Throwable {
        JXPathContext ctx = JXPathContext.newContext(new Bean());
        Object result = ctx.getValue("true() = false()");
        assertEquals(Boolean.FALSE, result);
    }

    // CoreOperationNotEqual negation: boolean different -> true
    @Test
    public void testNotEqual_BooleanDifferent_ReturnsTrue() throws Throwable {
        JXPathContext ctx = JXPathContext.newContext(new Bean());
        Object result = ctx.getValue("true() != false()");
        assertEquals(Boolean.TRUE, result);
    }

    // equal(Object,Object): Boolean branch has precedence over Number; 0 coerces to false
    @Test
    public void testEqual_BooleanPrecedenceOverNumber_ZeroFalse() throws Throwable {
        JXPathContext ctx = JXPathContext.newContext(new Bean());
        Object result = ctx.getValue("0 = false()");
        assertEquals(Boolean.TRUE, result);
    }

    // equal(Object,Object): Boolean branch has precedence over Number; nonzero coerces to true
    @Test
    public void testEqual_BooleanPrecedenceOverNumber_OneTrue() throws Throwable {
        JXPathContext ctx = JXPathContext.newContext(new Bean());
        Object result = ctx.getValue("1 = true()");
        assertEquals(Boolean.TRUE, result);
    }

    // equal(Object,Object): Boolean branch has precedence over String; empty string coerces to false
    @Test
    public void testEqual_BooleanPrecedenceOverString_EmptyFalse() throws Throwable {
        JXPathContext ctx = JXPathContext.newContext(new Bean());
        Object result = ctx.getValue("'' = false()");
        assertEquals(Boolean.TRUE, result);
    }

    // equal(Object,Object): Boolean branch has precedence over String; non-empty string coerces to true
    @Test
    public void testEqual_BooleanPrecedenceOverString_NonEmptyTrue() throws Throwable {
        JXPathContext ctx = JXPathContext.newContext(new Bean());
        Object result = ctx.getValue("'abc' = true()");
        assertEquals(Boolean.TRUE, result);
    }

    // equal(Object,Object): Number branch, NaN never equals NaN (per comment)
    @Test
    public void testEqual_NaNEqualsNaN_ReturnsFalse() throws Throwable {
        JXPathContext ctx = JXPathContext.newContext(new Bean());
        Object result = ctx.getValue("number('abc') = number('abc')");
        assertEquals(Boolean.FALSE, result);
    }

    // Documented contract: "if either side is NaN, no comparison returns true" -> != with NaN must be false
    @Test
    public void testNotEqual_NaNNotEqualsNaN_ReturnsFalsePerContract() throws Throwable {
        JXPathContext ctx = JXPathContext.newContext(new Bean());
        Object result = ctx.getValue("number('abc') != number('abc')");
        assertEquals(Boolean.FALSE, result);
    }

    // equal(Object,Object): Number branch, NaN vs ordinary number -> false
    @Test
    public void testEqual_NaNVsNumber_ReturnsFalse() throws Throwable {
        JXPathContext ctx = JXPathContext.newContext(new Bean());
        Object result = ctx.getValue("number('abc') = 1");
        assertEquals(Boolean.FALSE, result);
    }

    // Documented contract: NaN vs ordinary number via != must also be false (no comparison returns true)
    @Test
    public void testNotEqual_NaNVsNumber_ReturnsFalsePerContract() throws Throwable {
        JXPathContext ctx = JXPathContext.newContext(new Bean());
        Object result = ctx.getValue("number('abc') != 1");
        assertEquals(Boolean.FALSE, result);
    }

    // equal(EvalContext,...)+contains(Iterator,Object): collection vs scalar, element found -> true
    @Test
    public void testEqual_NodeSetContainsMatch_ReturnsTrue() throws Throwable {
        Bean bean = new Bean();
        bean.setItems(new String[] { "a", "b", "c" });
        JXPathContext ctx = JXPathContext.newContext(bean);
        Object result = ctx.getValue("items = 'b'");
        assertEquals(Boolean.TRUE, result);
    }

    // equal(EvalContext,...)+contains(Iterator,Object): collection vs scalar, element not found -> false
    @Test
    public void testEqual_NodeSetContainsNoMatch_ReturnsFalse() throws Throwable {
        Bean bean = new Bean();
        bean.setItems(new String[] { "a", "b", "c" });
        JXPathContext ctx = JXPathContext.newContext(bean);
        Object result = ctx.getValue("items = 'z'");
        assertEquals(Boolean.FALSE, result);
    }

    // contains(Iterator,Object): zero-iteration loop over empty collection -> false
    @Test
    public void testEqual_NodeSetEmptyCollection_ReturnsFalse() throws Throwable {
        Bean bean = new Bean();
        bean.setItems(new String[0]);
        JXPathContext ctx = JXPathContext.newContext(bean);
        Object result = ctx.getValue("items = 'a'");
        assertEquals(Boolean.FALSE, result);
    }

    // findMatch(Iterator,Iterator): two node-sets with overlapping element -> true
    @Test
    public void testEqual_NodeSetVsNodeSetOverlap_ReturnsTrue() throws Throwable {
        Bean bean = new Bean();
        bean.setItems(new String[] { "a", "b" });
        bean.setOthers(new String[] { "b", "c" });
        JXPathContext ctx = JXPathContext.newContext(bean);
        Object result = ctx.getValue("items = others");
        assertEquals(Boolean.TRUE, result);
    }

    // findMatch(Iterator,Iterator): two node-sets with no overlap -> false
    @Test
    public void testEqual_NodeSetVsNodeSetNoOverlap_ReturnsFalse() throws Throwable {
        Bean bean = new Bean();
        bean.setItems(new String[] { "a", "b" });
        bean.setOthers(new String[] { "c", "d" });
        JXPathContext ctx = JXPathContext.newContext(bean);
        Object result = ctx.getValue("items = others");
        assertEquals(Boolean.FALSE, result);
    }

    // findMatch(Iterator,Iterator): single-element node-sets matching -> true (1-iteration loops)
    @Test
    public void testEqual_NodeSetVsNodeSetSingleMatch_ReturnsTrue() throws Throwable {
        Bean bean = new Bean();
        bean.setItems(new String[] { "x" });
        bean.setOthers(new String[] { "x" });
        JXPathContext ctx = JXPathContext.newContext(bean);
        Object result = ctx.getValue("items = others");
        assertEquals(Boolean.TRUE, result);
    }

    // equal(Object,Object): fallback branch, same object reference read twice -> true
    @Test
    public void testEqual_FallbackObjectEquals_SameReference_ReturnsTrue() throws Throwable {
        Bean bean = new Bean();
        bean.setItem1(new Item(5));
        JXPathContext ctx = JXPathContext.newContext(bean);
        Object result = ctx.getValue("item1 = item1");
        assertEquals(Boolean.TRUE, result);
    }

    // equal(Object,Object): fallback branch, distinct objects with equal() true -> true
    @Test
    public void testEqual_FallbackObjectEquals_EqualValues_ReturnsTrue() throws Throwable {
        Bean bean = new Bean();
        bean.setItem1(new Item(7));
        bean.setItem2(new Item(7));
        JXPathContext ctx = JXPathContext.newContext(bean);
        Object result = ctx.getValue("item1 = item2");
        assertEquals(Boolean.TRUE, result);
    }

    // equal(Object,Object): fallback branch, distinct objects with equal() false -> false
    @Test
    public void testEqual_FallbackObjectEquals_DifferentValues_ReturnsFalse() throws Throwable {
        Bean bean = new Bean();
        bean.setItem1(new Item(1));
        bean.setItem2(new Item(2));
        JXPathContext ctx = JXPathContext.newContext(bean);
        Object result = ctx.getValue("item1 = item2");
        assertEquals(Boolean.FALSE, result);
    }

    // CoreOperationNotEqual negation on fallback branch: equal objects -> false
    @Test
    public void testNotEqual_FallbackObjectEquals_EqualValues_ReturnsFalse() throws Throwable {
        Bean bean = new Bean();
        bean.setItem1(new Item(9));
        bean.setItem2(new Item(9));
        JXPathContext ctx = JXPathContext.newContext(bean);
        Object result = ctx.getValue("item1 != item2");
        assertEquals(Boolean.FALSE, result);
    }

    // CoreOperationNotEqual negation on fallback branch: different objects -> true
    @Test
    public void testNotEqual_FallbackObjectEquals_DifferentValues_ReturnsTrue() throws Throwable {
        Bean bean = new Bean();
        bean.setItem1(new Item(3));
        bean.setItem2(new Item(4));
        JXPathContext ctx = JXPathContext.newContext(bean);
        Object result = ctx.getValue("item1 != item2");
        assertEquals(Boolean.TRUE, result);
    }

    // equal(Object,Object): String branch, same string value from bean property -> true
    @Test
    public void testEqual_StringProperty_SameValue_ReturnsTrue() throws Throwable {
        Bean bean = new Bean();
        bean.setName("hello");
        JXPathContext ctx = JXPathContext.newContext(bean);
        Object result = ctx.getValue("name = 'hello'");
        assertEquals(Boolean.TRUE, result);
    }

    // equal(Object,Object): String branch, differing string value from bean property -> false
    @Test
    public void testEqual_StringProperty_DifferentValue_ReturnsFalse() throws Throwable {
        Bean bean = new Bean();
        bean.setName("hello");
        JXPathContext ctx = JXPathContext.newContext(bean);
        Object result = ctx.getValue("name = 'world'");
        assertEquals(Boolean.FALSE, result);
    }
}
