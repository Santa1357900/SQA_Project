package org.jsoup.select;

import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;

import java.util.regex.Pattern;

import org.junit.Before;
import org.junit.Test;
import static org.junit.Assert.*;

public class PseudoTextElementClaudeTest {

    private Document doc;
    private Element htmlEl;
    private Element divEl;
    private Element p1, span1, p2, span2, p3;

    @Before
    public void setUp() throws Throwable {
        String html = "<html><head></head><body><div id='parent'>" +
                "<p class='a' title='Foo' data-info='abc'>Hello <b>World</b> Sam</p>" +
                "<span class='b'></span>" +
                "<p class='a'>Third</p>" +
                "<span class='b'>Fourth</span>" +
                "<p class='a'>Fifth</p>" +
                "</div></body></html>";
        doc = Jsoup.parse(html);
        htmlEl = doc.child(0);
        Element body = htmlEl.children().get(htmlEl.children().size() - 1);
        divEl = body.children().get(0);
        p1 = divEl.children().get(0);
        span1 = divEl.children().get(1);
        p2 = divEl.children().get(2);
        span2 = divEl.children().get(3);
        p3 = divEl.children().get(4);
    }

    // Tag: equalsIgnoreCase true/false branches + toString
    @Test
    public void testTagMatches_caseInsensitiveAndMismatch() throws Throwable {
        Evaluator.Tag eval = new Evaluator.Tag("P");
        assertTrue(eval.matches(doc, p1));
        Evaluator.Tag eval2 = new Evaluator.Tag("span");
        assertFalse(eval2.matches(doc, p1));
    }

    @Test
    public void testTagToString_returnsTagName() throws Throwable {
        Evaluator.Tag eval = new Evaluator.Tag("p");
        assertEquals("p", eval.toString());
    }

    // TagEndsWith: endsWith true/false branches
    @Test
    public void testTagEndsWithMatches_suffixAndNonSuffix() throws Throwable {
        Evaluator.TagEndsWith eval = new Evaluator.TagEndsWith("iv");
        assertTrue(eval.matches(doc, divEl));
        assertFalse(eval.matches(doc, p1));
    }

    // Id: equals true/false
    @Test
    public void testIdMatches_equalAndNotEqual() throws Throwable {
        Evaluator.Id eval = new Evaluator.Id("parent");
        assertTrue(eval.matches(doc, divEl));
        Evaluator.Id eval2 = new Evaluator.Id("other");
        assertFalse(eval2.matches(doc, divEl));
    }

    // Class: hasClass true/false
    @Test
    public void testClassMatches_hasClassAndNot() throws Throwable {
        Evaluator.Class eval = new Evaluator.Class("a");
        assertTrue(eval.matches(doc, p1));
        Evaluator.Class eval2 = new Evaluator.Class("zz");
        assertFalse(eval2.matches(doc, p1));
    }

    // Attribute: hasAttr present/absent
    @Test
    public void testAttributeMatches_presentAndAbsent() throws Throwable {
        Evaluator.Attribute eval = new Evaluator.Attribute("title");
        assertTrue(eval.matches(doc, p1));
        Evaluator.Attribute eval2 = new Evaluator.Attribute("nope");
        assertFalse(eval2.matches(doc, p1));
    }

    // AttributeStarting constructor: Validate.notEmpty throws for empty prefix
    @Test
    public void testAttributeStartingConstructor_emptyPrefix_throws() throws Throwable {
        try {
            new Evaluator.AttributeStarting("");
            fail("expected IllegalArgumentException");
        } catch (IllegalArgumentException expected) { }
    }

    // AttributeStarting: prefix found/not found among attribute keys
    @Test
    public void testAttributeStartingMatches_prefixFoundAndNotFound() throws Throwable {
        Evaluator.AttributeStarting eval = new Evaluator.AttributeStarting("data-");
        assertTrue(eval.matches(doc, p1));
        Evaluator.AttributeStarting eval2 = new Evaluator.AttributeStarting("zzz-");
        assertFalse(eval2.matches(doc, p1));
    }

    // AttributeWithValue constructor: empty key throws
    @Test
    public void testAttributeWithValueConstructor_emptyKey_throws() throws Throwable {
        try {
            new Evaluator.AttributeWithValue("", "x");
            fail("expected IllegalArgumentException");
        } catch (IllegalArgumentException expected) { }
    }

    // AttributeWithValue constructor: empty value throws
    @Test
    public void testAttributeWithValueConstructor_emptyValue_throws() throws Throwable {
        try {
            new Evaluator.AttributeWithValue("title", "");
            fail("expected IllegalArgumentException");
        } catch (IllegalArgumentException expected) { }
    }

    // AttributeWithValue: equalsIgnoreCase trimmed true/false + quoted value stripping
    @Test
    public void testAttributeWithValueMatches_equalIgnoreCaseAndQuoted() throws Throwable {
        Evaluator.AttributeWithValue eval = new Evaluator.AttributeWithValue("title", "foo");
        assertTrue(eval.matches(doc, p1));
        Evaluator.AttributeWithValue eval2 = new Evaluator.AttributeWithValue("title", "bar");
        assertFalse(eval2.matches(doc, p1));
        Evaluator.AttributeWithValue eval3 = new Evaluator.AttributeWithValue("title", "\"Foo\"");
        assertTrue(eval3.matches(doc, p1));
    }

    // AttributeWithValueNot: attribute absent -> true, attribute equal -> false
    @Test
    public void testAttributeWithValueNotMatches_absentAndEqual() throws Throwable {
        Evaluator.AttributeWithValueNot eval = new Evaluator.AttributeWithValueNot("nope", "bar");
        assertTrue(eval.matches(doc, p1));
        Evaluator.AttributeWithValueNot eval2 = new Evaluator.AttributeWithValueNot("title", "Foo");
        assertFalse(eval2.matches(doc, p1));
    }

    // AttributeWithValueStarting: startsWith true/false
    @Test
    public void testAttributeWithValueStartingMatches_trueAndFalse() throws Throwable {
        Evaluator.AttributeWithValueStarting eval = new Evaluator.AttributeWithValueStarting("title", "fo");
        assertTrue(eval.matches(doc, p1));
        Evaluator.AttributeWithValueStarting eval2 = new Evaluator.AttributeWithValueStarting("title", "oo");
        assertFalse(eval2.matches(doc, p1));
    }

    // AttributeWithValueEnding: endsWith true/false
    @Test
    public void testAttributeWithValueEndingMatches_trueAndFalse() throws Throwable {
        Evaluator.AttributeWithValueEnding eval = new Evaluator.AttributeWithValueEnding("title", "oo");
        assertTrue(eval.matches(doc, p1));
        Evaluator.AttributeWithValueEnding eval2 = new Evaluator.AttributeWithValueEnding("title", "zz");
        assertFalse(eval2.matches(doc, p1));
    }

    // AttributeWithValueContaining: contains true/false
    @Test
    public void testAttributeWithValueContainingMatches_trueAndFalse() throws Throwable {
        Evaluator.AttributeWithValueContaining eval = new Evaluator.AttributeWithValueContaining("data-info", "bc");
        assertTrue(eval.matches(doc, p1));
        Evaluator.AttributeWithValueContaining eval2 = new Evaluator.AttributeWithValueContaining("data-info", "zz");
        assertFalse(eval2.matches(doc, p1));
    }

    // AttributeWithValueMatching: regex found/not found
    @Test
    public void testAttributeWithValueMatchingMatches_trueAndFalse() throws Throwable {
        Pattern pattern1 = Pattern.compile("^[A-Za-z]+$");
        Evaluator.AttributeWithValueMatching eval = new Evaluator.AttributeWithValueMatching("title", pattern1);
        assertTrue(eval.matches(doc, p1));
        Pattern pattern2 = Pattern.compile("^[0-9]+$");
        Evaluator.AttributeWithValueMatching eval2 = new Evaluator.AttributeWithValueMatching("title", pattern2);
        assertFalse(eval2.matches(doc, p1));
    }

    // AllElements: always true + toString
    @Test
    public void testAllElementsMatches_alwaysTrue() throws Throwable {
        Evaluator.AllElements eval = new Evaluator.AllElements();
        assertTrue(eval.matches(doc, p1));
        assertEquals("*", eval.toString());
    }

    // IndexLessThan: below threshold true, root==element excluded false
    @Test
    public void testIndexLessThanMatches_belowThresholdAndRootExcluded() throws Throwable {
        Evaluator.IndexLessThan eval = new Evaluator.IndexLessThan(2);
        assertTrue(eval.matches(divEl, p1));
        Evaluator.IndexLessThan eval2 = new Evaluator.IndexLessThan(5);
        assertFalse(eval2.matches(divEl, divEl));
    }

    // IndexGreaterThan: above/below threshold
    @Test
    public void testIndexGreaterThanMatches_aboveAndBelowThreshold() throws Throwable {
        Evaluator.IndexGreaterThan eval = new Evaluator.IndexGreaterThan(1);
        assertTrue(eval.matches(divEl, span2));
        Evaluator.IndexGreaterThan eval2 = new Evaluator.IndexGreaterThan(10);
        assertFalse(eval2.matches(divEl, p1));
    }

    // IndexEquals: equal/not equal
    @Test
    public void testIndexEqualsMatches_equalAndNotEqual() throws Throwable {
        Evaluator.IndexEquals eval = new Evaluator.IndexEquals(2);
        assertTrue(eval.matches(divEl, p2));
        assertFalse(eval.matches(divEl, p1));
    }

    // IsLastChild: last true, non-last false, parent is Document false
    @Test
    public void testIsLastChildMatches_lastNonLastAndDocumentParent() throws Throwable {
        Evaluator.IsLastChild eval = new Evaluator.IsLastChild();
        assertTrue(eval.matches(doc, p3));
        assertFalse(eval.matches(doc, p1));
        assertFalse(eval.matches(doc, htmlEl));
    }

    // IsFirstOfType: first true, second false + toString
    @Test
    public void testIsFirstOfTypeMatches_firstAndSecond() throws Throwable {
        Evaluator.IsFirstOfType eval = new Evaluator.IsFirstOfType();
        assertTrue(eval.matches(doc, p1));
        assertFalse(eval.matches(doc, p2));
        assertEquals(":first-of-type", eval.toString());
    }

    // IsLastOfType: last true, first false + toString
    @Test
    public void testIsLastOfTypeMatches_lastAndFirst() throws Throwable {
        Evaluator.IsLastOfType eval = new Evaluator.IsLastOfType();
        assertTrue(eval.matches(doc, p3));
        assertFalse(eval.matches(doc, p1));
        assertEquals(":last-of-type", eval.toString());
    }

    // CssNthEvaluator.toString: a==0, b==0, general branches
    @Test
    public void testIsNthChildToString_allBranches() throws Throwable {
        Evaluator.IsNthChild eval1 = new Evaluator.IsNthChild(0, 3);
        assertEquals(":nth-child(3)", eval1.toString());
        Evaluator.IsNthChild eval2 = new Evaluator.IsNthChild(2, 0);
        assertEquals(":nth-child(2n)", eval2.toString());
        Evaluator.IsNthChild eval3 = new Evaluator.IsNthChild(2, 1);
        assertEquals(":nth-child(2n+1)", eval3.toString());
    }

    // IsNthChild: an+b formula matching odd positions among all siblings
    @Test
    public void testIsNthChildMatches_oddPositions() throws Throwable {
        Evaluator.IsNthChild eval = new Evaluator.IsNthChild(2, 1);
        assertTrue(eval.matches(doc, p1));
        assertFalse(eval.matches(doc, span1));
        assertTrue(eval.matches(doc, p2));
    }

    // IsNthLastChild: position from end
    @Test
    public void testIsNthLastChildMatches_lastAndNotLast() throws Throwable {
        Evaluator.IsNthLastChild eval = new Evaluator.IsNthLastChild(0, 1);
        assertTrue(eval.matches(doc, p3));
        assertFalse(eval.matches(doc, p1));
    }

    // IsNthOfType: position among same-tag siblings
    @Test
    public void testIsNthOfTypeMatches_secondPAndFirstP() throws Throwable {
        Evaluator.IsNthOfType eval = new Evaluator.IsNthOfType(0, 2);
        assertTrue(eval.matches(doc, p2));
        assertFalse(eval.matches(doc, p1));
    }

    // IsNthLastOfType: position from end among same-tag siblings
    @Test
    public void testIsNthLastOfTypeMatches_lastPAndFirstP() throws Throwable {
        Evaluator.IsNthLastOfType eval = new Evaluator.IsNthLastOfType(0, 1);
        assertTrue(eval.matches(doc, p3));
        assertFalse(eval.matches(doc, p1));
    }

    // IsFirstChild: first true, non-first false, document parent false
    @Test
    public void testIsFirstChildMatches_firstNonFirstAndDocumentParent() throws Throwable {
        Evaluator.IsFirstChild eval = new Evaluator.IsFirstChild();
        assertTrue(eval.matches(doc, p1));
        assertFalse(eval.matches(doc, span1));
        assertFalse(eval.matches(doc, htmlEl));
    }

    // IsRoot: Document root branch and non-Document root branch
    @Test
    public void testIsRootMatches_documentAndNonDocumentRoot() throws Throwable {
        Evaluator.IsRoot eval = new Evaluator.IsRoot();
        assertTrue(eval.matches(doc, htmlEl));
        assertFalse(eval.matches(doc, p1));
        assertTrue(eval.matches(divEl, divEl));
    }

    // IsOnlyChild: has siblings false, no siblings true
    @Test
    public void testIsOnlyChildMatches_hasSiblingsAndNoSiblings() throws Throwable {
        Evaluator.IsOnlyChild eval = new Evaluator.IsOnlyChild();
        assertFalse(eval.matches(doc, p1));
        assertTrue(eval.matches(doc, divEl));
    }

    // IsOnlyOfType: multiple of type false, single of type true
    @Test
    public void testIsOnlyOfTypeMatches_multipleAndSingle() throws Throwable {
        Evaluator.IsOnlyOfType eval = new Evaluator.IsOnlyOfType();
        assertFalse(eval.matches(doc, p1));
        assertTrue(eval.matches(doc, divEl));
    }

    // IsEmpty: no children true, has text child false
    @Test
    public void testIsEmptyMatches_noChildrenAndHasTextChild() throws Throwable {
        Evaluator.IsEmpty eval = new Evaluator.IsEmpty();
        assertTrue(eval.matches(doc, span1));
        assertFalse(eval.matches(doc, p1));
    }

    // ContainsText: found (descendant text) / not found
    @Test
    public void testContainsTextMatches_foundAndNotFound() throws Throwable {
        Evaluator.ContainsText eval = new Evaluator.ContainsText("world");
        assertTrue(eval.matches(doc, p1));
        Evaluator.ContainsText eval2 = new Evaluator.ContainsText("xyz");
        assertFalse(eval2.matches(doc, p1));
    }

    // ContainsOwnText: own text matches, child element text excluded
    @Test
    public void testContainsOwnTextMatches_ownTextAndExcludedChildText() throws Throwable {
        Evaluator.ContainsOwnText eval = new Evaluator.ContainsOwnText("sam");
        assertTrue(eval.matches(doc, p1));
        Evaluator.ContainsOwnText eval2 = new Evaluator.ContainsOwnText("world");
        assertFalse(eval2.matches(doc, p1));
    }



    // Matches: regex on full descendant text found/not found + toString
    @Test
    public void testMatchesMatches_foundAndNotFound() throws Throwable {
        Pattern pattern1 = Pattern.compile("World");
        Evaluator.Matches eval = new Evaluator.Matches(pattern1);
        assertTrue(eval.matches(doc, p1));
        Pattern pattern2 = Pattern.compile("ZZZ");
        Evaluator.Matches eval2 = new Evaluator.Matches(pattern2);
        assertFalse(eval2.matches(doc, p1));
        assertTrue(eval.toString().contains("matches"));
    }

    // MatchesOwn: regex on own text found (Sam) and excluded child text (World)
    @Test
    public void testMatchesOwnMatches_ownTextFoundAndChildExcluded() throws Throwable {
        Pattern pattern1 = Pattern.compile("Sam");
        Evaluator.MatchesOwn eval = new Evaluator.MatchesOwn(pattern1);
        assertTrue(eval.matches(doc, p1));
        Pattern pattern2 = Pattern.compile("World");
        Evaluator.MatchesOwn eval2 = new Evaluator.MatchesOwn(pattern2);
        assertFalse(eval2.matches(doc, p1));
    }
}
