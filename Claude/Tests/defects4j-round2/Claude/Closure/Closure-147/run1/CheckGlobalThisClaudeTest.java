package com.google.javascript.jscomp;

import org.junit.Test;
import static org.junit.Assert.*;

public class CheckGlobalThisClaudeTest {

    // Helper: parses code, runs CheckGlobalThis via a real NodeTraversal, returns
    // the number of warnings newly added to the compiler by this pass.
    private int countGlobalThisWarnings(String code, CheckLevel level) throws Throwable {
        Compiler compiler = new Compiler();
        CompilerOptions options = new CompilerOptions();
        SourceFile externs = SourceFile.fromCode("externs.js", "");
        SourceFile input = SourceFile.fromCode("test.js", code);
        compiler.compile(externs, input, options);
        int before = compiler.getWarnings().length;
        CheckGlobalThis check = new CheckGlobalThis(compiler, level);
        NodeTraversal t = new NodeTraversal(compiler, check);
        t.traverse(compiler.getRoot());
        return compiler.getWarnings().length - before;
    }

    // Helper: same as above but counts newly added errors instead of warnings.
    private int countGlobalThisErrors(String code, CheckLevel level) throws Throwable {
        Compiler compiler = new Compiler();
        CompilerOptions options = new CompilerOptions();
        SourceFile externs = SourceFile.fromCode("externs.js", "");
        SourceFile input = SourceFile.fromCode("test.js", code);
        compiler.compile(externs, input, options);
        int before = compiler.getErrors().length;
        CheckGlobalThis check = new CheckGlobalThis(compiler, level);
        NodeTraversal t = new NodeTraversal(compiler, check);
        t.traverse(compiler.getRoot());
        return compiler.getErrors().length - before;
    }

    // field: GLOBAL_THIS diagnostic type must exist and be usable.
    @Test
    public void testGlobalThisField_isAccessibleAndNotNull() throws Throwable {
        assertNotNull(CheckGlobalThis.GLOBAL_THIS);
    }

    // shouldTraverse: FUNCTION with @constructor jsDoc directly attached -> not traversed, no report.
    @Test
    public void testConstructorAnnotatedFunction_thisPropertyAssign_notFlagged() throws Throwable {
        String code = "/** @constructor */ function Foo() { this.x = 1; }";
        assertEquals(0, countGlobalThisWarnings(code, CheckLevel.WARNING));
    }

    // shouldTraverse: FUNCTION with @this jsDoc directly attached -> not traversed, no report.
    @Test
    public void testThisAnnotatedFunction_thisPropertyAssign_notFlagged() throws Throwable {
        String code = "/** @this {Object} */ function foo() { this.x = 1; }";
        assertEquals(0, countGlobalThisWarnings(code, CheckLevel.WARNING));
    }

    // shouldTraverse: FUNCTION with @override jsDoc directly attached -> not traversed, no report.
    @Test
    public void testOverrideAnnotatedFunction_thisPropertyAssign_notFlagged() throws Throwable {
        String code = "/** @override */ function foo() { this.x = 1; }";
        assertEquals(0, countGlobalThisWarnings(code, CheckLevel.WARNING));
    }

    // shouldTraverse: top-level function declaration, parent==SCRIPT is allowed -> traversed and flagged.
    @Test
    public void testPlainFunctionDeclaration_thisPropertyAssign_flagged() throws Throwable {
        String code = "function foo() { this.x = 1; }";
        assertEquals(1, countGlobalThisWarnings(code, CheckLevel.WARNING));
    }

    // shouldTraverse: function declaration whose parent is BLOCK is allowed -> traversed and flagged.
    @Test
    public void testFunctionDeclarationInsideBlock_thisPropertyAssign_flagged() throws Throwable {
        String code = "{ function foo() { this.x = 1; } }";
        assertEquals(1, countGlobalThisWarnings(code, CheckLevel.WARNING));
    }

    // shouldTraverse: function expression whose parent is NAME (var init) is allowed -> flagged.
    @Test
    public void testFunctionExpressionAssignedToVar_thisPropertyAssign_flagged() throws Throwable {
        String code = "var a = function() { this.x = 1; };";
        assertEquals(1, countGlobalThisWarnings(code, CheckLevel.WARNING));
    }

    // shouldTraverse: lhs is a plain NAME (not a get) so prototype skip logic is bypassed -> flagged.
    @Test
    public void testPlainNameAssignment_functionTraversedAndFlagged() throws Throwable {
        String code = "a = function() { this.x = 1; };";
        assertEquals(1, countGlobalThisWarnings(code, CheckLevel.WARNING));
    }

    // shouldTraverse: rhs of ASSIGN, lhs is a non-prototype GETPROP -> rhs traversed -> flagged.
    @Test
    public void testFunctionAssignedToProperty_thisPropertyAssign_flagged() throws Throwable {
        String code = "a.x = function() { this.y = 1; };";
        assertEquals(1, countGlobalThisWarnings(code, CheckLevel.WARNING));
    }

    // shouldTraverse: lhs is a GETELEM (isGet true but not GETPROP), prototype checks don't match -> flagged.
    @Test
    public void testGetElemAssignment_functionTraversedAndFlagged() throws Throwable {
        String code = "a['x'] = function() { this.y = 1; };";
        assertEquals(1, countGlobalThisWarnings(code, CheckLevel.WARNING));
    }

    // Bug hunt: per class javadoc, "var a = {x: function() {}};" is a documented traversable
    // pattern, so this global-this misuse must be flagged (fails on the buggy STRING-parent check).
    @Test
    public void testFunctionAsObjectLiteralValue_shouldBeFlaggedPerJavadoc() throws Throwable {
        String code = "var a = {x: function() { this.y = 1; }};";
        assertEquals(1, countGlobalThisWarnings(code, CheckLevel.WARNING));
    }

    // shouldTraverse: FUNCTION parent is ARRAYLIT, not an allowed pType -> not traversed, no report.
    @Test
    public void testFunctionAsArrayElement_thisPropertyAssign_notFlagged() throws Throwable {
        String code = "var a = [function() { this.x = 1; }];";
        assertEquals(0, countGlobalThisWarnings(code, CheckLevel.WARNING));
    }

    // shouldTraverse: FUNCTION parent is CALL (callback argument), not allowed -> not traversed.
    @Test
    public void testFunctionAsCallArgument_thisPropertyAssign_notFlagged() throws Throwable {
        String code = "foo(function() { this.x = 1; });";
        assertEquals(0, countGlobalThisWarnings(code, CheckLevel.WARNING));
    }

    // shouldTraverse: FUNCTION parent is HOOK (ternary), not allowed -> not traversed.
    @Test
    public void testFunctionInsideTernary_thisPropertyAssign_notFlagged() throws Throwable {
        String code = "var a = (cond ? function() { this.x = 1; } : null);";
        assertEquals(0, countGlobalThisWarnings(code, CheckLevel.WARNING));
    }

    // shouldTraverse: rhs of ASSIGN whose lhs is "X.prototype.method" -> rhs skipped, no report.
    @Test
    public void testPrototypeMethodAssignment_thisPropertyAssign_notFlagged() throws Throwable {
        String code = "Foo.prototype.bar = function() { this.x = 1; };";
        assertEquals(0, countGlobalThisWarnings(code, CheckLevel.WARNING));
    }

    // shouldTraverse: rhs of ASSIGN whose lhs is exactly "X.prototype" -> rhs skipped, no report.
    @Test
    public void testPrototypeDirectAssignment_thisPropertyAssign_notFlagged() throws Throwable {
        String code = "Foo.prototype = function() { this.x = 1; };";
        assertEquals(0, countGlobalThisWarnings(code, CheckLevel.WARNING));
    }

    // getFunctionJsDocInfo: jsDoc missing on FUNCTION/NAME, found via gramps VAR node -> not flagged.
    @Test
    public void testVarWithConstructorJsDocViaGramps_notFlagged() throws Throwable {
        String code = "/** @constructor */ var Foo = function() { this.x = 1; };";
        assertEquals(0, countGlobalThisWarnings(code, CheckLevel.WARNING));
    }

    // getFunctionJsDocInfo: jsDoc missing on FUNCTION, found via ASSIGN parent -> not flagged.
    @Test
    public void testPropertyAssignWithConstructorJsDocOnAssign_notFlagged() throws Throwable {
        String code = "/** @constructor */ a.Foo = function() { this.x = 1; };";
        assertEquals(0, countGlobalThisWarnings(code, CheckLevel.WARNING));
    }

    // visit: two separate THIS-lhs property assignments inside the same traversable function -> two reports.
    @Test
    public void testMultipleThisUsagesInSameFunction_flaggedTwice() throws Throwable {
        String code = "function foo() { this.x = 1; this.y = 2; }";
        assertEquals(2, countGlobalThisWarnings(code, CheckLevel.WARNING));
    }

    // shouldReportThis: THIS with a property-get parent but no enclosing ASSIGN -> still flagged.
    @Test
    public void testGlobalThisPropertyAccessWithoutAssign_flagged() throws Throwable {
        String code = "var y = this.foo;";
        assertEquals(1, countGlobalThisWarnings(code, CheckLevel.WARNING));
    }

    // shouldReportThis: bare THIS used as a plain value (no get, no assign-lhs) -> not flagged.
    @Test
    public void testBareThisAssignedToVar_notFlagged() throws Throwable {
        String code = "var y = this;";
        assertEquals(0, countGlobalThisWarnings(code, CheckLevel.WARNING));
    }

    // shouldReportThis: THIS as a bare expression statement -> not flagged.
    @Test
    public void testBareThisExpressionStatement_notFlagged() throws Throwable {
        String code = "this;";
        assertEquals(0, countGlobalThisWarnings(code, CheckLevel.WARNING));
    }

    // shouldReportThis: THIS passed as a call argument (not a get) -> not flagged.
    @Test
    public void testThisAsCallArgument_notFlagged() throws Throwable {
        String code = "function foo() { bar(this); }";
        assertEquals(0, countGlobalThisWarnings(code, CheckLevel.WARNING));
    }

    // Javadoc example "(a = this).property = c;": assignLhsChild set by outer lhs must not be
    // overridden by the nested inner assignment, so the inner THIS still gets reported.
    @Test
    public void testNestedParenthesizedAssignThis_propertyAssign_flagged() throws Throwable {
        String code = "(a = this).property = c;";
        assertEquals(1, countGlobalThisWarnings(code, CheckLevel.WARNING));
    }

    // CheckLevel.OFF: even though the usage would be detected, OFF level means nothing is recorded.
    @Test
    public void testCheckLevelOff_thisPropertyAssign_notRecordedAsWarning() throws Throwable {
        String code = "function foo() { this.x = 1; }";
        assertEquals(0, countGlobalThisWarnings(code, CheckLevel.OFF));
    }

    // CheckLevel.ERROR: detected usage is recorded as an error, not a warning.
    @Test
    public void testCheckLevelError_thisPropertyAssign_recordedAsError() throws Throwable {
        String code = "function foo() { this.x = 1; }";
        assertEquals(1, countGlobalThisErrors(code, CheckLevel.ERROR));
    }

    // CheckLevel.WARNING: detected usage is recorded as a warning, not an error.
    @Test
    public void testCheckLevelWarning_thisPropertyAssign_notRecordedAsError() throws Throwable {
        String code = "function foo() { this.x = 1; }";
        assertEquals(0, countGlobalThisErrors(code, CheckLevel.WARNING));
    }
}
