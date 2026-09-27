package org.jsoup.select;

import org.jsoup.nodes.Comment;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.DocumentType;
import org.jsoup.nodes.Element;
import org.jsoup.nodes.XmlDeclaration;
import org.junit.Test;

import java.util.regex.Pattern;

import static org.junit.Assert.*;

public class EvaluatorTest {

    @Test
    public void testTagEvaluator() throws Throwable {
        Evaluator.Tag tag = new Evaluator.Tag("div");
        Element root = new Element("div");
        Element child = new Element("div");
        Element other = new Element("span");
        root.appendChild(child);
        root.appendChild(other);

        assertTrue(tag.matches(root, child));
        assertFalse(tag.matches(root, other));
        assertEquals("div", tag.toString());
    }

    @Test
    public void testTagEndsWithEvaluator() throws Throwable {
        Evaluator.TagEndsWith tagEndsWith = new Evaluator.TagEndsWith("content");
        Element root = new Element("div");
        Element child = new Element("div-content");
        Element other = new Element("div");
        root.appendChild(child);
        root.appendChild(other);

        assertTrue(tagEndsWith.matches(root, child));
        assertFalse(tagEndsWith.matches(root, other));
        assertEquals("content", tagEndsWith.toString());
    }

    @Test
    public void testIdEvaluator() throws Throwable {
        Evaluator.Id idEval = new Evaluator.Id("main");
        Element root = new Element("div");
        Element child = new Element("div").id("main");
        Element other = new Element("div").id("other");
        root.appendChild(child);
        root.appendChild(other);

        assertTrue(idEval.matches(root, child));
        assertFalse(idEval.matches(root, other));
        assertEquals("#main", idEval.toString());
    }

    @Test
    public void testClassEvaluator() throws Throwable {
        Evaluator.Class classEval = new Evaluator.Class("active");
        Element root = new Element("div");
        Element child = new Element("div").addClass("active");
        Element other = new Element("div").addClass("inactive");
        root.appendChild(child);
        root.appendChild(other);

        assertTrue(classEval.matches(root, child));
        assertFalse(classEval.matches(root, other));
        assertEquals(".active", classEval.toString());
    }

    @Test
    public void testAttributeEvaluator() throws Throwable {
        Evaluator.Attribute attrEval = new Evaluator.Attribute("href");
        Element root = new Element("div");
        Element child = new Element("a").attr("href", "http://example.com");
        Element other = new Element("a");
        root.appendChild(child);
        root.appendChild(other);

        assertTrue(attrEval.matches(root, child));
        assertFalse(attrEval.matches(root, other));
        assertEquals("[href]", attrEval.toString());
    }

    @Test
    public void testAttributeStartingEvaluator() throws Throwable {
        Evaluator.AttributeStarting attrStart = new Evaluator.AttributeStarting("data-");
        Element root = new Element("div");
        Element child = new Element("div").attr("data-id", "123");
        Element other = new Element("div").attr("id", "123");
        root.appendChild(child);
        root.appendChild(other);

        assertTrue(attrStart.matches(root, child));
        assertFalse(attrStart.matches(root, other));
        assertEquals("[^data-]", attrStart.toString());
    }

    @Test
    public void testAttributeWithValueEvaluator() throws Throwable {
        Evaluator.AttributeWithValue attrVal = new Evaluator.AttributeWithValue("class", "btn");
        Element root = new Element("div");
        Element child = new Element("button").attr("class", "btn");
        Element other = new Element("button").attr("class", "panel");
        root.appendChild(child);
        root.appendChild(other);

        assertTrue(attrVal.matches(root, child));
        assertFalse(attrVal.matches(root, other));
        assertEquals("[class=btn]", attrVal.toString());
    }

    @Test
    public void testAttributeWithValueNotEvaluator() throws Throwable {
        Evaluator.AttributeWithValueNot attrValNot = new Evaluator.AttributeWithValueNot("class", "btn");
        Element root = new Element("div");
        Element child = new Element("button").attr("class", "panel");
        Element other = new Element("button").attr("class", "btn");
        root.appendChild(child);
        root.appendChild(other);

        assertTrue(attrValNot.matches(root, child));
        assertFalse(attrValNot.matches(root, other));
        assertEquals("[class!=btn]", attrValNot.toString());
    }

    @Test
    public void testAttributeWithValueStartingEvaluator() throws Throwable {
        Evaluator.AttributeWithValueStarting eval = new Evaluator.AttributeWithValueStarting("href", "http");
        Element root = new Element("div");
        Element child = new Element("a").attr("href", "HTTP://example.com");
        Element other = new Element("a").attr("href", "ftp://example.com");
        root.appendChild(child);
        root.appendChild(other);

        assertTrue(eval.matches(root, child));
        assertFalse(eval.matches(root, other));
        assertEquals("[href^=http]", eval.toString());
    }

    @Test
    public void testAttributeWithValueEndingEvaluator() throws Throwable {
        Evaluator.AttributeWithValueEnding eval = new Evaluator.AttributeWithValueEnding("src", ".png");
        Element root = new Element("div");
        Element child = new Element("img").attr("src", "image.PNG");
        Element other = new Element("img").attr("src", "image.jpg");
        root.appendChild(child);
        root.appendChild(other);

        assertTrue(eval.matches(root, child));
        assertFalse(eval.matches(root, other));
        assertEquals("[src$=.png]", eval.toString());
    }

    @Test
    public void testAttributeWithValueContainingEvaluator() throws Throwable {
        Evaluator.AttributeWithValueContaining eval = new Evaluator.AttributeWithValueContaining("title", "test");
        Element root = new Element("div");
        Element child = new Element("div").attr("title", "A TEST string");
        Element other = new Element("div").attr("title", "No match");
        root.appendChild(child);
        root.appendChild(other);

        assertTrue(eval.matches(root, child));
        assertFalse(eval.matches(root, other));
        assertEquals("[title*=test]", eval.toString());
    }

    @Test
    public void testAttributeWithValueMatchingEvaluator() throws Throwable {
        Pattern pattern = Pattern.compile("^\\d+$");
        Evaluator.AttributeWithValueMatching eval = new Evaluator.AttributeWithValueMatching("data-id", pattern);
        Element root = new Element("div");
        Element child = new Element("div").attr("data-id", "12345");
        Element other = new Element("div").attr("data-id", "abc");
        root.appendChild(child);
        root.appendChild(other);

        assertTrue(eval.matches(root, child));
        assertFalse(eval.matches(root, other));
        assertEquals("[data-id~=" + pattern.toString() + "]", eval.toString());
    }

    @Test
    public void testAttributeKeyPairQuotesHandling() throws Throwable {
        Evaluator.AttributeWithValue eval = new Evaluator.AttributeWithValue("class", "\"quoted\"");
        Element root = new Element("div");
        Element child = new Element("div").attr("class", "quoted");
        root.appendChild(child);
        assertTrue(eval.matches(root, child));

        Evaluator.AttributeWithValue evalSingle = new Evaluator.AttributeWithValue("class", "'single'");
        Element childSingle = new Element("div").attr("class", "single");
        root.appendChild(childSingle);
        assertTrue(evalSingle.matches(root, childSingle));
    }

    @Test
    public void testAllElementsEvaluator() throws Throwable {
        Evaluator.AllElements all = new Evaluator.AllElements();
        Element root = new Element("div");
        Element child = new Element("p");
        root.appendChild(child);

        assertTrue(all.matches(root, child));
        assertTrue(all.matches(root, root));
        assertEquals("*", all.toString());
    }

    @Test
    public void testIndexEvaluators() throws Throwable {
        Element root = new Element("div");
        Element child0 = new Element("p");
        Element child1 = new Element("p");
        Element child2 = new Element("p");
        root.appendChild(child0);
        root.appendChild(child1);
        root.appendChild(child2);

        Evaluator.IndexLessThan lt = new Evaluator.IndexLessThan(2);
        Evaluator.IndexGreaterThan gt = new Evaluator.IndexGreaterThan(0);
        Evaluator.IndexEquals eq = new Evaluator.IndexEquals(1);

        assertTrue(lt.matches(root, child0));
        assertTrue(lt.matches(root, child1));
        assertFalse(lt.matches(root, child2));
        assertFalse(lt.matches(root, root)); // root != element check
        assertEquals(":lt(2)", lt.toString());

        assertFalse(gt.matches(root, child0));
        assertTrue(gt.matches(root, child1));
        assertTrue(gt.matches(root, child2));
        assertEquals(":gt(0)", gt.toString());

        assertFalse(eq.matches(root, child0));
        assertTrue(eq.matches(root, child1));
        assertFalse(eq.matches(root, child2));
        assertEquals(":eq(1)", eq.toString());
    }

    @Test
    public void testChildAndSiblingEvaluators() throws Throwable {
        Document doc = new Document("");
        Element root = new Element("div");
        doc.appendChild(root);
        Element child0 = new Element("p");
        Element child1 = new Element("span");
        root.appendChild(child0);
        root.appendChild(child1);

        Evaluator.IsFirstChild firstChild = new Evaluator.IsFirstChild();
        Evaluator.IsLastChild lastChild = new Evaluator.IsLastChild();
        Evaluator.IsOnlyChild onlyChild = new Evaluator.IsOnlyChild();
        Evaluator.IsRoot rootEval = new Evaluator.IsRoot();

        assertTrue(firstChild.matches(root, child0));
        assertFalse(firstChild.matches(root, child1));
        assertEquals(":first-child", firstChild.toString());

        assertFalse(lastChild.matches(root, child0));
        assertTrue(lastChild.matches(root, child1));
        assertEquals(":last-child", lastChild.toString());

        assertFalse(onlyChild.matches(root, child0));
        assertFalse(onlyChild.matches(root, child1));
        assertEquals(":only-child", onlyChild.toString());

        assertTrue(rootEval.matches(root, root));
        assertFalse(rootEval.matches(root, child0));
        assertEquals(":root", rootEval.toString());
        
        // Test Document as root
        Evaluator.IsRoot docRootEval = new Evaluator.IsRoot();
        assertTrue(docRootEval.matches(doc, root));
    }

    @Test
    public void testOnlyChildAndTypeEvaluators() throws Throwable {
        Element root = new Element("div");
        Element child0 = new Element("p");
        root.appendChild(child0);

        Evaluator.IsOnlyChild onlyChild = new Evaluator.IsOnlyChild();
        assertTrue(onlyChild.matches(root, child0));

        Evaluator.IsOnlyOfType onlyOfType = new Evaluator.IsOnlyOfType();
        assertTrue(onlyOfType.matches(root, child0));
        assertEquals(":only-of-type", onlyOfType.toString());
        
        Element child1 = new Element("p");
        root.appendChild(child1);
        assertFalse(onlyOfType.matches(root, child0));

        // Test parent null or Document for IsOnlyOfType
        Document doc = new Document("");
        assertFalse(onlyOfType.matches(doc, doc));
    }

    @Test
    public void testIsEmptyEvaluator() throws Throwable {
        Element root = new Element("div");
        Element emptyEl = new Element("div");
        Element notEmptyEl = new Element("div");
        notEmptyEl.appendChild(new Element("span"));
        
        Element commentEl = new Element("div");
        commentEl.appendChild(new Comment("comment"));
        commentEl.appendChild(new XmlDeclaration("xml", true));
        commentEl.appendChild(new DocumentType("html", "", ""));

        Evaluator.IsEmpty isEmpty = new Evaluator.IsEmpty();
        assertTrue(isEmpty.matches(root, emptyEl));
        assertTrue(isEmpty.matches(root, commentEl));
        assertFalse(isEmpty.matches(root, notEmptyEl));
        assertEquals(":empty", isEmpty.toString());
    }

    @Test
    public void testCssNthEvaluators() throws Throwable {
        Element root = new Element("div");
        Element p1 = new Element("p");
        Element p2 = new Element("p");
        Element p3 = new Element("p");
        Element p4 = new Element("p");
        root.appendChild(p1);
        root.appendChild(p2);
        root.appendChild(p3);
        root.appendChild(p4);

        Evaluator.IsNthChild nthChild = new Evaluator.IsNthChild(2, 1); // 2n+1 (1, 3, 5)
        assertTrue(nthChild.matches(root, p1)); // pos 1
        assertFalse(nthChild.matches(root, p2)); // pos 2
        assertTrue(nthChild.matches(root, p3)); // pos 3
        assertEquals(":nth-child(2n+1)", nthChild.toString());

        Evaluator.IsNthChild nthChildA0 = new Evaluator.IsNthChild(0, 2); // pos == 2
        assertTrue(nthChildA0.matches(root, p2));
        assertFalse(nthChildA0.matches(root, p1));
        assertEquals(":nth-child(2)", nthChildA0.toString());

        Evaluator.IsNthLastChild nthLastChild = new Evaluator.IsNthLastChild(0, 1); // last child
        assertTrue(nthLastChild.matches(root, p4));
        assertFalse(nthLastChild.matches(root, p3));
        assertEquals(":nth-last-child(1)", nthLastChild.toString());

        Evaluator.IsFirstOfType firstOfType = new Evaluator.IsFirstOfType();
        assertTrue(firstOfType.matches(root, p1));
        assertEquals(":first-of-type", firstOfType.toString());

        Evaluator.IsLastOfType lastOfType = new Evaluator.IsLastOfType();
        assertTrue(lastOfType.matches(root, p4));
        assertEquals(":last-of-type", lastOfType.toString());

        Evaluator.IsNthOfType nthOfType = new Evaluator.IsNthOfType(0, 1);
        assertTrue(nthOfType.matches(root, p1));

        Evaluator.IsNthLastOfType nthLastOfType = new Evaluator.IsNthLastOfType(0, 1);
        assertTrue(nthLastOfType.matches(root, p4));
        
        // Test parent null or Document for CssNthEvaluator
        Document doc = new Document("");
        assertFalse(nthChild.matches(doc, doc));
    }

    @Test
    public void testToStringBranchInCssNthEvaluator() throws Throwable {
        Evaluator.IsNthChild bZero = new Evaluator.IsNthChild(2, 0); // b = 0 -> 2n
        assertEquals(":nth-child(2n)", bZero.toString());
    }

    @Test
    public void testTextAndDataEvaluators() throws Throwable {
        Element root = new Element("div");
        Element child = new Element("div").text("Hello World Test");
        Element ownChild = new Element("div").text("Parent").append("<span>Child Text</span>");
        Element dataChild = new Element("script").data("var x = 10; test data;");
        root.appendChild(child);
        root.appendChild(ownChild);
        root.appendChild(dataChild);

        Evaluator.ContainsText containsText = new Evaluator.ContainsText("world");
        assertTrue(containsText.matches(root, child));
        assertFalse(containsText.matches(root, new Element("div")));
        assertEquals(":contains(world)", containsText.toString());

        Evaluator.ContainsOwnText containsOwnText = new Evaluator.ContainsOwnText("parent");
        assertTrue(containsOwnText.matches(root, ownChild));
        assertFalse(containsOwnText.matches(root, child)); // "Child Text" is not own text
        assertEquals(":containsOwn(parent)", containsOwnText.toString());

        Evaluator.ContainsData containsData = new Evaluator.ContainsData("test data");
        assertTrue(containsData.matches(root, dataChild));
        assertFalse(containsData.matches(root, child));
        assertEquals(":containsData(test data)", containsData.toString());

        Evaluator.Matches matches = new Evaluator.Matches(Pattern.compile("World"));
        assertTrue(matches.matches(root, child));
        assertFalse(matches.matches(root, new Element("div")));
        assertEquals(":matches(" + matches.toString().substring(10), matches.toString()); // check format roughly

        Evaluator.MatchesOwn matchesOwn = new Evaluator.MatchesOwn(Pattern.compile("Parent"));
        assertTrue(matchesOwn.matches(root, ownChild));
        assertFalse(matchesOwn.matches(root, child));
        assertTrue(matchesOwn.toString().startsWith(":matchesOwn("));
    }
}