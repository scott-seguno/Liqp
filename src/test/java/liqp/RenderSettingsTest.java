package liqp;


import static org.hamcrest.CoreMatchers.is;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertThat;
import static org.junit.Assert.assertTrue;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;

import org.junit.Test;

import liqp.RenderTransformer.ObjectAppender;
import liqp.exceptions.VariableNotExistException;
import liqp.filters.Filter;

public class RenderSettingsTest {
    
    protected TemplateParser parserWithStrictVariables() {
        return new TemplateParser.Builder().withRenderSettings(new RenderSettings.Builder()
                .withStrictVariables(true).build()).build();
    }

    protected TemplateParser parserWithStrictVariablesAndRaiseExceptionsInStrictModeFalse() {
        RenderSettings renderSettings = new RenderSettings.Builder()
                .withStrictVariables(true)
                .withRaiseExceptionsInStrictMode(false)
                .build();
        return new TemplateParser.Builder().withRenderSettings(renderSettings).build();
    }
    
    @Test
    public void renderWithStrictVariables1() {
        try {
            parserWithStrictVariables().parse("{{mu}}").render();
        } catch (RuntimeException ex) {
            VariableNotExistException e = (VariableNotExistException) TestUtils.getExceptionRootCause(ex);
            assertThat(e.getVariableName(), is("mu"));
        }
    }

    @Test
    public void renderWithStrictVariables2() {
        try {
            parserWithStrictVariables().parse("{{mu}} {{qwe.asd.zxc}}").render("mu", "muValue");
        } catch (RuntimeException ex) {
            VariableNotExistException e = (VariableNotExistException) TestUtils.getExceptionRootCause(ex);
            assertThat(e.getVariableName(), is("qwe.asd.zxc"));
        }
    }

    @Test
    public void renderWithStrictVariablesInCondition1() {
        parserWithStrictVariables().parse("{% if mu == \"somethingElse\" %}{{ badVariableName }}{% endif %}")
                .render("mu", "muValue");
    }

    @Test
    public void renderWithStrictVariablesInCondition2() {
        try {
            parserWithStrictVariables().parse("{% if mu == \"muValue\" %}{{ badVariableName }}{% endif %}")
                    .render("mu", "muValue");
        } catch (RuntimeException ex) {
            VariableNotExistException e = (VariableNotExistException) TestUtils.getExceptionRootCause(ex);
            assertThat(e.getVariableName(), is("badVariableName"));
        }
    }

    @Test
    public void renderWithStrictVariablesInAnd1() {
        try {
            parserWithStrictVariables().parse("{% if mu == \"muValue\" and checkThis %}{{ badVariableName }}{% endif %}")
                    .render("mu", "muValue");
        } catch (RuntimeException ex) {
            VariableNotExistException e = (VariableNotExistException) TestUtils.getExceptionRootCause(ex);
            assertThat(e.getVariableName(), is("checkThis"));
        }
    }

    @Test
    public void renderWithStrictVariablesInAnd2() {
        parserWithStrictVariables().parse("{% if mu == \"somethingElse\" and doNotCheckThis %}{{ badVariableName }}{% endif %}")
                .render("mu", "muValue");
    }

    @Test
    public void renderWithStrictVariablesInOr1() {
        try {
            parserWithStrictVariables().parse("{% if mu == \"muValue\" or doNotCheckThis %}{{ badVariableName }}{% endif %}")
                    .render("mu", "muValue");
        } catch (RuntimeException ex) {
            VariableNotExistException e = (VariableNotExistException) TestUtils.getExceptionRootCause(ex);
            assertThat(e.getVariableName(), is("badVariableName"));
        }
    }

    @Test
    public void renderWithStrictVariablesInOr2() {
        try {
            parserWithStrictVariables().parse("{% if mu == \"somethingElse\" or checkThis %}{{ badVariableName }}{% endif %}")
                    .render("mu", "muValue");
        } catch (RuntimeException ex) {
            VariableNotExistException e = (VariableNotExistException) TestUtils.getExceptionRootCause(ex);
            assertThat(e.getVariableName(), is("checkThis"));
        }
    }

    @Test
    public void raiseExceptionsInStrictModeFalseTest() {
        TemplateParser parser = parserWithStrictVariablesAndRaiseExceptionsInStrictModeFalse();

        Template template = parser.parse("{{a}}{{b}}{{c}}");

        assertThat(template.errors().size(), is(0));

        String rendered = template.render("b", "FOO");

        // There should be 2 exceptions recorded for non-existing variables `a` and `c`
        assertThat(template.errors().size(), is(2));

        // Rendering should not terminate
        assertThat(rendered, is("FOO"));
    }

    @Test
    public void raiseExceptionsInStrictModeFalseTest2() {
        TemplateParser parser = parserWithStrictVariablesAndRaiseExceptionsInStrictModeFalse();

        Template template = parser.parse("{% for v in a %}{{v.b}}{% endfor %}" +
                                         "{% for v in badVariableName %}{{v.b}}{% endfor %}" +
                                         "{% for v in a %}{{v.badVariableName}}{% endfor %}");

        assertThat(template.errors().size(), is(0));

        String rendered = template.render("{\"a\" : [ { \"b\" : \"FOO\" } ] }");

        // There should be 2 exceptions recorded for non-existing variables `badVariableName` and `v.badVariableName`
        assertThat(template.errors().size(), is(2));
        assertThat(((VariableNotExistException) template.errors().get(0)).getVariableName(), is("badVariableName"));
        assertThat(((VariableNotExistException) template.errors().get(1)).getVariableName(), is("v.badVariableName"));

        // Rendering should not terminate
        assertThat(rendered, is("FOO"));
    }

    @Test
    public void testEnvironmentMapConfigurator() throws Exception {
        final String secretKey = getClass() + ".secretKey";

        ParseSettings parseSettings = new ParseSettings.Builder().with(new Filter("secret") {
            @Override
            public Object apply(Object value, TemplateContext context, Object... params) {
                ObjectAppender.Controller sb = context.newObjectAppender(3);
                sb.append(value);
                sb.append(" ");
                sb.append(context.getEnvironmentMap().get(secretKey));
                return sb.getResult();
            }
        }).build();

        AtomicBoolean gotEnvironmentMap = new AtomicBoolean(false);
        RenderSettings renderSettings = new RenderSettings.Builder().withEnvironmentMapConfigurator((
            env) -> {
            env.put(secretKey, "world");

            gotEnvironmentMap.set(true);
        }).build();

        TemplateParser parser = new TemplateParser.Builder().withParseSettings(parseSettings)
            .withRenderSettings(renderSettings).build();
        Template template = parser.parse("{{ 'Hello' | secret }}");

        assertFalse(gotEnvironmentMap.get());
        assertEquals("Hello world", template.render());
        assertTrue(gotEnvironmentMap.get());
    }

    @Test
    public void testCustomRenderTransformer() throws Exception {
        RenderSettings renderSettings = new RenderSettings.Builder().withRenderTransformer(
            new CustomRenderTransformer()).build();

        TemplateParser parser = new TemplateParser.Builder().withRenderSettings(renderSettings).build();
        Template template = parser.parse("{{ 'Hello' }} {{ 'world' }}");

        Object obj = template.renderToObject();
        assertEquals(MyAppender.class, obj.getClass());

        // our custom RenderTransformer allows access to appended fragments
        List<Object> list = ((MyAppender) obj).getList();
        assertEquals(Arrays.asList("Hello", " ", "world"), list);

        // the final output for Template.render and Template.renderToObject.toString should be identical
        assertEquals("Hello world", obj.toString());
        assertEquals("Hello world", template.render());
    }

    private static final class MyAppender implements ObjectAppender.Controller {
        final List<Object> list = new ArrayList<>();

        @Override
        public Object getResult() {
            return this;
        }

        @Override
        public void append(Object obj) {
            list.add(obj);
        }

        public List<Object> getList() {
            return list;
        }

        @Override
        public String toString() {
            StringBuilder sb = new StringBuilder();
            for (Object o : list) {
                sb.append(o);
            }
            return sb.toString();
        }
    }

    private static final class CustomRenderTransformer implements RenderTransformer {
        @Override
        public Object transformObject(TemplateContext context, Object obj) {
            return obj;
        }

        @Override
        public ObjectAppender.Controller newObjectAppender(TemplateContext context,
            int estimatedNumberOfAppends) {
            return new MyAppender();
        }
    }
}
