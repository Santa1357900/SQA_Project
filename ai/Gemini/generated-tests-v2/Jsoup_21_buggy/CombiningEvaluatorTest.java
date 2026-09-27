package org.jsoup.select;

import org.junit.Test;
import org.jsoup.nodes.Element;
import org.jsoup.parser.Tag;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

public class CombiningEvaluatorTest {

    private static final class DummyEvaluator extends Evaluator {
        private final boolean result;

        DummyEvaluator(boolean result) {
            this.result = result;
        }

        @Override
        public boolean matches(Element root, Element node) {
            return result;
        }

        @Override
        public String toString() {
            return "dummy(" + result + ")";
        }
    }

    @Test
    public void testAndEvaluatorMatchesAllTrue() throws Throwable {
        Evaluator eval1 = new DummyEvaluator(true);
        Evaluator eval2 = new DummyEvaluator(true);
        CombiningEvaluator.And andEval = new CombiningEvaluator.And(eval1, eval2);

        Element root = new Element(Tag.valueOf("div"), "");
        Element node = new Element(Tag.valueOf("p"), "");

        assertTrue(andEval.matches(root, node));
    }

    @Test
    public void testAndEvaluatorMatchesWithFalse() throws Throwable {
        Evaluator eval1 = new DummyEvaluator(true);
        Evaluator eval2 = new DummyEvaluator(false);
        CombiningEvaluator.And andEval = new CombiningEvaluator.And(eval1, eval2);

        Element root = new Element(Tag.valueOf("div"), "");
        Element node = new Element(Tag.valueOf("p"), "");

        assertFalse(andEval.matches(root, node));
    }

    @Test
    public void testAndEvaluatorCollectionConstructor() throws Throwable {
        List<Evaluator> list = new ArrayList<Evaluator>();
        list.add(new DummyEvaluator(true));
        list.add(new DummyEvaluator(true));
        CombiningEvaluator.And andEval = new CombiningEvaluator.And(list);

        Element root = new Element(Tag.valueOf("div"), "");
        Element node = new Element(Tag.valueOf("p"), "");

        assertTrue(andEval.matches(root, node));
        assertTrue(andEval.toString().contains("dummy(true)"));
    }

    @Test
    public void testOrEvaluatorSingleElement() throws Throwable {
        List<Evaluator> list = new ArrayList<Evaluator>();
        list.add(new DummyEvaluator(false));
        CombiningEvaluator.Or orEval = new CombiningEvaluator.Or(list);

        Element root = new Element(Tag.valueOf("div"), "");
        Element node = new Element(Tag.valueOf("p"), "");

        assertFalse(orEval.matches(root, node));
    }

    @Test
    public void testOrEvaluatorMultipleElements() throws Throwable {
        List<Evaluator> list = new ArrayList<Evaluator>();
        list.add(new DummyEvaluator(true));
        list.add(new DummyEvaluator(false));
        CombiningEvaluator.Or orEval = new CombiningEvaluator.Or(list);

        Element root = new Element(Tag.valueOf("div"), "");
        Element node = new Element(Tag.valueOf("p"), "");

        assertTrue(orEval.matches(root, node));
    }

    @Test
    public void testOrEvaluatorAddAndToString() throws Throwable {
        List<Evaluator> list = new ArrayList<Evaluator>();
        CombiningEvaluator.Or orEval = new CombiningEvaluator.Or(list);
        orEval.add(new DummyEvaluator(true));

        Element root = new Element(Tag.valueOf("div"), "");
        Element node = new Element(Tag.valueOf("p"), "");

        assertTrue(orEval.matches(root, node));
        assertTrue(orEval.toString().startsWith(":or"));
    }
}