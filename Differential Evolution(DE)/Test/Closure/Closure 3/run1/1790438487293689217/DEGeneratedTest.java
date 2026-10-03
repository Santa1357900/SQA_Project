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
        org.junit.Assert.assertEquals("CLOSURE_SOURCE:java.lang.String:d2luZG93LmE9ZnVuY3Rpb24oKXt2YXIgYjt0cnl7dGhyb3cgRXJyb3IoIngiKTt9Y2F0Y2goYyl7Yj1jfXJldHVybiBTdHJpbmcoYil9Ow==",
            compileClosure("window.f = function() { var saved; try { throw Error('x'); } catch (caught) { saved = caught; } return String(saved); };"));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00001() throws Exception {
        org.junit.Assert.assertEquals("CLOSURE_SOURCE:java.lang.String:d2luZG93LmE9ZnVuY3Rpb24oKXt2YXIgYjt0cnl7dGhyb3cgRXJyb3IoIngiKTt9Y2F0Y2goYyl7Yj1jfXJldHVybiBiLmJ9Ow==",
            compileClosure("window.f = function() { var saved; try { throw Error('x'); } catch (caught) { saved = caught; } return saved.stack; };"));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00002() throws Exception {
        org.junit.Assert.assertEquals("CLOSURE_SOURCE:java.lang.String:d2luZG93LmE9ZnVuY3Rpb24oKXt2YXIgYjt0cnl7dGhyb3cgRXJyb3IoIngiKTt9Y2F0Y2goYyl7Yj1jfXJldHVybiBiLmJ9Ow==",
            compileClosure("window.f = function() { var saved; try { throw Error('x'); } catch (caught) { saved = caught; } return saved.name; };"));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00003() throws Exception {
        org.junit.Assert.assertEquals("CLOSURE_SOURCE:java.lang.String:d2luZG93LmE9ZnVuY3Rpb24oKXt2YXIgYjt0cnl7dGhyb3cgRXJyb3IoIngiKTt9Y2F0Y2goYyl7Yj1jfXJldHVybiBiLmJ9Ow==",
            compileClosure("window.f = function() { var saved; try { throw Error('x'); } catch (problem) { saved = problem; } return saved.message; };"));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00004() throws Exception {
        org.junit.Assert.assertEquals("CLOSURE_SOURCE:java.lang.String:d2luZG93LmE9ZnVuY3Rpb24oYyl7dmFyIGI7dHJ5e2lmKGMpdGhyb3cgRXJyb3IoIngiKTt9Y2F0Y2goZCl7Yj1kfXJldHVybiBiLmJ9Ow==",
            compileClosure("window.f = function(flag) { var saved; try { if (flag) throw Error('x'); } catch (caught) { saved = caught; } return saved.message; };"));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00005() throws Exception {
        org.junit.Assert.assertEquals("CLOSURE_SOURCE:java.lang.String:d2luZG93LmE9ZnVuY3Rpb24oYyl7dmFyIGI7dHJ5e2lmKGMpdGhyb3cgRXJyb3IoIngiKTt9Y2F0Y2goZCl7Yj1kfXJldHVybiBiLmJ9Ow==",
            compileClosure("window.f = function(flag) { var saved; try { if (flag) throw Error('x'); } catch (problem) { saved = problem; } return saved.stack; };"));
    }
}
