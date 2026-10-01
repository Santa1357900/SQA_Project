package org.jsoup.select;

import org.junit.Before;
import org.junit.Test;
import static org.junit.Assert.*;

import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

public class CombiningEvaluatorClaudeTest {

    private Document doc;
    private Element div;
    private Element p;
    private Evaluator tagDiv;
    private Evaluator tagP;
    private Evaluator idA;
    private Evaluator idB;
    private Evaluator classC1;
    private Evaluator classC2;

    @Before
    public void setUp() throws Throwable {
        doc = Jsoup.parse("<div id=\"a\" class=\"c1\"><p id=\"b\" class=\"c2\">text</p></div>");
        div = doc.select("div").first();
        p = doc.select("p").first();
        tagDiv = new Evaluator.Tag("div");
        tagP = new Evaluator.Tag("p");
        idA = new Evaluator.Id("a");
        idB = new Evaluator.Id("b");
        classC1 = new Evaluator.Class("c1");
        classC2 = new Evaluator.Class("c2");
    }

    // And.matches: all evaluators match -> true
    @Test
    public void testAndMatches_allEvaluatorsMatch_returnsTrue() throws Throwable {
        CombiningEvaluator.And and = new CombiningEvaluator.And(tagDiv, idA);
        assertTrue(and.matches(doc, div));
    }

    // And.matches: one evaluator fails -> false
    @Test
    public void testAndMatches_oneEvaluatorFails_returnsFalse() throws Throwable {
        CombiningEvaluator.And and = new CombiningEvaluator.And(tagDiv, idB);
        assertFalse(and.matches(doc, div));
    }

    // And.matches: empty collection -> vacuously true
    @Test
    public void testAndMatches_emptyEvaluators_vacuousTrue() throws Throwable {
        List<Evaluator> empty = new ArrayList<Evaluator>();
        CombiningEvaluator.And and = new CombiningEvaluator.And(empty);
        assertTrue(and.matches(doc, div));
    }

    // And(Collection) constructor: evaluators field populated with given collection
    @Test
    public void testAndConstructorCollection_copiesEvaluators_sizeMatches() throws Throwable {
        List<Evaluator> list = new ArrayList<Evaluator>();
        list.add(tagDiv);
        list.add(idA);
        CombiningEvaluator.And and = new CombiningEvaluator.And(list);
        assertEquals(2, and.evaluators.size());
    }

    // And.toString joins evaluators with a single space
    @Test
    public void testAndToString_joinsEvaluatorsWithSpace() throws Throwable {
        CombiningEvaluator.And and = new CombiningEvaluator.And(tagDiv, idA);
        String expected = tagDiv.toString() + " " + idA.toString();
        assertEquals(expected, and.toString());
    }

    // And(Evaluator...) varargs constructor with a single evaluator still matches
    @Test
    public void testAndVarargsConstructor_singleEvaluator_matches() throws Throwable {
        CombiningEvaluator.And and = new CombiningEvaluator.And(tagDiv);
        assertTrue(and.matches(doc, div));
    }

    // And.matches on p element with multiple conditions all true
    @Test
    public void testAndMatches_pElement_allConditionsMatch_returnsTrue() throws Throwable {
        CombiningEvaluator.And and = new CombiningEvaluator.And(tagP, idB, classC2);
        assertTrue(and.matches(doc, p));
    }

    // And.matches on p element where one condition fails
    @Test
    public void testAndMatches_pElement_oneConditionFails_returnsFalse() throws Throwable {
        CombiningEvaluator.And and = new CombiningEvaluator.And(tagP, idA);
        assertFalse(and.matches(doc, p));
    }

    // Or(Collection) with empty collection: no evaluators added, matches is false
    @Test
    public void testOrMatches_emptyCollection_matchesReturnsFalse() throws Throwable {
        List<Evaluator> empty = new ArrayList<Evaluator>();
        CombiningEvaluator.Or or = new CombiningEvaluator.Or(empty);
        assertEquals(0, or.evaluators.size());
        assertFalse(or.matches(doc, div));
    }

    // Or(Collection) with a single evaluator: added directly (not wrapped in And)
    @Test
    public void testOrConstructor_singleEvaluator_addedDirectlyNotWrapped() throws Throwable {
        List<Evaluator> list = new ArrayList<Evaluator>();
        list.add(tagDiv);
        CombiningEvaluator.Or or = new CombiningEvaluator.Or(list);
        assertEquals(1, or.evaluators.size());
        assertSame(tagDiv, or.evaluators.get(0));
    }

    // Or.matches: single evaluator matches -> true
    @Test
    public void testOrMatches_singleEvaluatorMatches_returnsTrue() throws Throwable {
        List<Evaluator> list = new ArrayList<Evaluator>();
        list.add(tagDiv);
        CombiningEvaluator.Or or = new CombiningEvaluator.Or(list);
        assertTrue(or.matches(doc, div));
    }

    // Or.matches: single evaluator does not match -> false
    @Test
    public void testOrMatches_singleEvaluatorDoesNotMatch_returnsFalse() throws Throwable {
        List<Evaluator> list = new ArrayList<Evaluator>();
        list.add(tagP);
        CombiningEvaluator.Or or = new CombiningEvaluator.Or(list);
        assertFalse(or.matches(doc, div));
    }

    // Or(Collection) with more than one evaluator: they are ANDed together as the first clause
    @Test
    public void testOrConstructor_multipleEvaluators_wrappedInAndClause() throws Throwable {
        List<Evaluator> list = new ArrayList<Evaluator>();
        list.add(tagDiv);
        list.add(idA);
        CombiningEvaluator.Or or = new CombiningEvaluator.Or(list);
        assertEquals(1, or.evaluators.size());
        assertTrue(or.evaluators.get(0) instanceof CombiningEvaluator.And);
    }

    // Or.matches: multiple initial evaluators all match -> AND clause satisfied -> true
    @Test
    public void testOrMatches_multipleEvaluatorsAllMatch_returnsTrue() throws Throwable {
        List<Evaluator> list = new ArrayList<Evaluator>();
        list.add(tagDiv);
        list.add(idA);
        CombiningEvaluator.Or or = new CombiningEvaluator.Or(list);
        assertTrue(or.matches(doc, div));
    }

    // Or.matches: multiple initial evaluators, only one matches -> AND clause fails -> false
    @Test
    public void testOrMatches_multipleEvaluatorsOnlyOneMatches_returnsFalse() throws Throwable {
        List<Evaluator> list = new ArrayList<Evaluator>();
        list.add(tagDiv);
        list.add(idB);
        CombiningEvaluator.Or or = new CombiningEvaluator.Or(list);
        assertFalse(or.matches(doc, div));
    }

    // Or.add: adds a new OR clause, increasing size
    @Test
    public void testOrAdd_addsAdditionalClause_increasesSize() throws Throwable {
        CombiningEvaluator.Or or = new CombiningEvaluator.Or(new ArrayList<Evaluator>());
        or.add(tagDiv);
        assertEquals(1, or.evaluators.size());
    }

    // Or.add: OR semantics - matches true if any added clause matches
    @Test
    public void testOrAdd_orSemantics_returnsTrueIfAnyClauseMatches() throws Throwable {
        CombiningEvaluator.Or or = new CombiningEvaluator.Or(new ArrayList<Evaluator>());
        or.add(tagP);
        or.add(idA);
        assertTrue(or.matches(doc, div));
    }

    // Or.add: OR semantics - matches false if no added clause matches
    @Test
    public void testOrAdd_noClauseMatches_returnsFalse() throws Throwable {
        CombiningEvaluator.Or or = new CombiningEvaluator.Or(new ArrayList<Evaluator>());
        or.add(tagP);
        or.add(idB);
        assertFalse(or.matches(doc, div));
    }

    // Or.toString: format includes ":or" prefix and evaluator representation
    @Test
    public void testOrToString_containsOrPrefixAndEvaluators() throws Throwable {
        List<Evaluator> list = new ArrayList<Evaluator>();
        list.add(tagDiv);
        CombiningEvaluator.Or or = new CombiningEvaluator.Or(list);
        String s = or.toString();
        assertTrue(s.startsWith(":or"));
        assertTrue(s.contains(tagDiv.toString()));
    }

    // Or.matches on p element using add(): any matching clause yields true
    @Test
    public void testOrAdd_pElement_anyMatchReturnsTrue() throws Throwable {
        CombiningEvaluator.Or or = new CombiningEvaluator.Or(Arrays.asList(idA));
        or.add(idB);
        assertTrue(or.matches(doc, p));
    }

    // Or(Collection) with single evaluator that fails on a different element
    @Test
    public void testOrMatches_singleEvaluator_pElement_doesNotMatch_returnsFalse() throws Throwable {
        List<Evaluator> list = new ArrayList<Evaluator>();
        list.add(classC1);
        CombiningEvaluator.Or or = new CombiningEvaluator.Or(list);
        assertFalse(or.matches(doc, p));
    }
}
