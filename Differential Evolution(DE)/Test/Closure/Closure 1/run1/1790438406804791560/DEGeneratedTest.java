public class DEGeneratedTest {
    private static String compileClosure(String source) throws Exception {
        com.google.javascript.jscomp.Compiler compiler =
            new com.google.javascript.jscomp.Compiler();
        com.google.javascript.jscomp.CompilerOptions options =
            new com.google.javascript.jscomp.CompilerOptions();
        com.google.javascript.jscomp.CompilationLevel.ADVANCED_OPTIMIZATIONS
            .setOptionsForCompilationLevel(options);
        Class current = options.getClass();
        while (current != null) {
            java.lang.reflect.Field[] fields = current.getDeclaredFields();
            for (int i = 0; i < fields.length; i++) {
                java.lang.reflect.Field field = fields[i];
                String name = field.getName().toLowerCase(java.util.Locale.ROOT);
                if (field.getType() == Boolean.TYPE &&
                    (name.equals("removeglobals") || name.contains("removeunusedvar") ||
                     name.contains("removeunusedlocal"))) {
                    field.setAccessible(true);
                    field.setBoolean(options, !name.equals("removeglobals"));
                }
            }
            current = current.getSuperclass();
        }
        java.lang.reflect.Method[] methods = options.getClass().getMethods();
        for (int i = 0; i < methods.length; i++) {
            java.lang.reflect.Method method = methods[i];
            String name = method.getName().toLowerCase(java.util.Locale.ROOT);
            Class[] parameters = method.getParameterTypes();
            if (!name.startsWith("set") || parameters.length != 1 ||
                parameters[0] != Boolean.TYPE) continue;
            if (name.contains("removeglobals")) method.invoke(options, Boolean.FALSE);
            else if (name.contains("removeunusedvar") || name.contains("removeunusedlocal"))
                method.invoke(options, Boolean.TRUE);
        }
        com.google.javascript.jscomp.SourceFile input =
            com.google.javascript.jscomp.SourceFile.fromCode("de-input.js", source);
        compiler.compile(java.util.Collections.<com.google.javascript.jscomp.SourceFile>emptyList(),
            java.util.Collections.singletonList(input), options);
        String output = compiler.toSource();
        return "CLOSURE_SOURCE:java.lang.String:" +
            java.util.Base64.getEncoder().encodeToString(output.getBytes("UTF-8"));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00000() throws Exception {
        org.junit.Assert.assertEquals("CLOSURE_SOURCE:java.lang.String:d2luZG93LmE9ZnVuY3Rpb24oYyxiKXtyZXR1cm4gYisxfTs=",
            compileClosure("window.f = function(unused, value) { value = value + 1; return value; };"));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00001() throws Exception {
        org.junit.Assert.assertEquals("CLOSURE_SOURCE:java.lang.String:d2luZG93LmE9ZnVuY3Rpb24oKXtyZXR1cm4gMX07",
            compileClosure("window.f = function(unused) { function inner() { return 1; } return inner(); };"));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00002() throws Exception {
        org.junit.Assert.assertEquals("CLOSURE_SOURCE:java.lang.String:d2luZG93LmE9ZnVuY3Rpb24oYyxiKXtyZXR1cm4gZnVuY3Rpb24oKXtyZXR1cm4gYn19Ow==",
            compileClosure("window.f = function(unused, value) { return function() { return value; }; };"));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00003() throws Exception {
        org.junit.Assert.assertEquals("CLOSURE_SOURCE:java.lang.String:d2luZG93LmE9ZnVuY3Rpb24oYixjKXtyZXR1cm4gYitjfTs=",
            compileClosure("window.f = function(a, b, unused) { return a + b; };"));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00004() throws Exception {
        org.junit.Assert.assertEquals("CLOSURE_SOURCE:java.lang.String:d2luZG93LmE9ZnVuY3Rpb24oYyxiKXtyZXR1cm4gYn07",
            compileClosure("window.f = function(a, b) { var used = b; return used; };"));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00005() throws Exception {
        org.junit.Assert.assertEquals("CLOSURE_SOURCE:java.lang.String:d2luZG93LmE9ZnVuY3Rpb24oYyxiKXtyZXR1cm4gYn07",
            compileClosure("window.f = function(unused, value) { var alias = value; return alias; };"));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00006() throws Exception {
        org.junit.Assert.assertEquals("CLOSURE_SOURCE:java.lang.String:d2luZG93LmE9ZnVuY3Rpb24oYyxiKXtyZXR1cm4gYn07",
            compileClosure("window.f = function(unused, value) { return value; };"));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00007() throws Exception {
        org.junit.Assert.assertEquals("CLOSURE_SOURCE:java.lang.String:d2luZG93LmE9ZnVuY3Rpb24oYyxkLGIpe3JldHVybiBifTs=",
            compileClosure("window.f = function(first, unused, last) { return last; };"));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00008() throws Exception {
        org.junit.Assert.assertEquals("CLOSURE_SOURCE:java.lang.String:d2luZG93LmE9ZnVuY3Rpb24oYixkLGUsYyl7cmV0dXJuIGIrY307",
            compileClosure("window.f = function(a, unused, b, c) { return a + c; };"));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00009() throws Exception {
        org.junit.Assert.assertEquals("CLOSURE_SOURCE:java.lang.String:d2luZG93LmE9ZnVuY3Rpb24oYyxiKXtyZXR1cm4gYj8xOjJ9Ow==",
            compileClosure("window.f = function(unused, value) { if (value) { return 1; } return 2; };"));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00010() throws Exception {
        org.junit.Assert.assertEquals("CLOSURE_SOURCE:java.lang.String:d2luZG93LmE9ZnVuY3Rpb24oYyxiKXtyZXR1cm4gYn07",
            compileClosure("window.f = function(a, b) { return b; };"));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00011() throws Exception {
        org.junit.Assert.assertEquals("CLOSURE_SOURCE:java.lang.String:d2luZG93LmY9ZnVuY3Rpb24oYixhKXtyZXR1cm4gYX07",
            compileClosure("window['f'] = function(unused, value) { return value; };"));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00012() throws Exception {
        org.junit.Assert.assertEquals("CLOSURE_SOURCE:java.lang.String:d2luZG93LmE9ZnVuY3Rpb24oKXt9Ow==",
            compileClosure("window.f = function(a) {};"));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00013() throws Exception {
        org.junit.Assert.assertEquals("CLOSURE_SOURCE:java.lang.String:d2luZG93LmE9ZnVuY3Rpb24oKXtyZXR1cm4gMX07",
            compileClosure("window.f = function(unused) { var local = 1; return local; };"));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00014() throws Exception {
        org.junit.Assert.assertEquals("CLOSURE_SOURCE:java.lang.String:d2luZG93LmY9ZnVuY3Rpb24oKXt9Ow==",
            compileClosure("window['f'] = function(unused) {};"));
    }
}
