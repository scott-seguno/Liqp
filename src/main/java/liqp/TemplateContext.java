package liqp;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import liqp.RenderTransformer.ObjectAppender;
import liqp.parser.Flavor;

public class TemplateContext {

    public static final String REGISTRY_CYCLE = "cycle";
    public static final String REGISTRY_IFCHANGED = "ifchanged";
    public static final String REGISTRY_FOR = "for";
    public static final String REGISTRY_FOR_STACK = "for_stack";

    protected TemplateContext parent;

    private final TemplateParser parser;

    @Deprecated
    public final ProtectionSettings protectionSettings;
    @Deprecated
    public final RenderSettings renderSettings;
    @Deprecated
    public final ParseSettings parseSettings;

    private Map<String, Object> variables;
    private Map<String, Object> environmentMap;
    private Map<String, Object> registry;

    private List<RuntimeException> errors;

    public TemplateContext() {
        this(TemplateParser.DEFAULT, new LinkedHashMap<>());
    }

    @Deprecated // Use `TemplateContext(parser, variables)` instead
    public TemplateContext(ProtectionSettings protectionSettings, RenderSettings renderSettings,
            Flavor flavor, Map<String, Object> variables) {
        this(new TemplateParser.Builder().withProtectionSettings(protectionSettings)
                .withRenderSettings(renderSettings).withParseSettings(new ParseSettings.Builder()
                        .withFlavor(flavor).build()).build(), variables);
    }

    @Deprecated // Use `TemplateContext(parser, variables)` instead
    public TemplateContext(ProtectionSettings protectionSettings, RenderSettings renderSettings,
            ParseSettings parseSettings, Map<String, Object> variables) {
        this(new TemplateParser.Builder().withProtectionSettings(protectionSettings)
                .withRenderSettings(renderSettings).withParseSettings(parseSettings).build(), variables);
    }

    public TemplateContext(TemplateParser parser, Map<String, Object> variables) {
        this.parent = null;
        this.parser = parser;
        this.protectionSettings = parser.getProtectionSettings();
        this.renderSettings = parser.getRenderSettings();
        this.parseSettings = parser.getParseSettings();
        this.variables = new LinkedHashMap<>(variables);
        this.errors = new ArrayList<>();
    }

    public TemplateContext(TemplateContext parent) {
        this(parent.getParser(), new LinkedHashMap<String, Object>());
        this.parent = parent;
    }

    /**
     * Deprecated. Use {@link TemplateContext#newChildContext(Map)} instead.
     * 
     * @param parent The parent context.
     * @param variables The variables to use in this child context.
     */
    @Deprecated
    public TemplateContext(TemplateContext parent, Map<String, Object> variables) {
        this(variables, parent);
    }

    protected TemplateContext(Map<String, Object> variables, TemplateContext parent) {
        this(parent);
        this.variables = variables;
    }

    public TemplateParser getParser() {
        return parser;
    }

    public void addError(RuntimeException exception) {
        this.errors.add(exception);
    }

    public List<RuntimeException> errors() {
        return new ArrayList<>(this.errors);
    }

    public void incrementIterations() {
        this.protectionSettings.incrementIterations();
    }

    public boolean containsKey(String key) {

        if (this.variables.containsKey(key)) {
            return true;
        }

        if (parent != null) {
            return parent.containsKey(key);
        }

        return false;
    }

    public Object get(String key) {

        // First try to retrieve the key from the local context
        Object value = this.variables.get(key);

        if (value != null) {
            return value;
        }

        if (parent != null) {
            // Not available locally, try the parent context
            return parent.get(key);
        }

        // Not available
        return null;
    }

    public Object put(String key, Object value) {
        return this.put(key, value, false);
    }

    public Object put(String key, Object value, boolean putInRootContext) {

        if (!putInRootContext || parent == null) {
            // Either store it in the local context, or this context is the root context
            return this.variables.put(key, value);
        }

        // Else put in the parent context
        return parent.put(key, value, putInRootContext);
    }

    public Object remove(String key) {

        if (this.variables.containsKey(key)) {
            // Remove the key from the local context
            return this.variables.remove(key);
        }

        if (parent != null) {
            // Not available in the local context, try the parent
            return parent.remove(key);
        }

        // Key was not present
        return null;
    }

    public Map<String, Object> getVariables() {
        return new LinkedHashMap<String, Object>(this.variables);
    }

    public Map<String, Object> getEnvironmentMap() {
        if (parent != null) {
            return parent.getEnvironmentMap();
        }
        if (environmentMap == null) {
            environmentMap = new HashMap<>();
        }
        return environmentMap;
    }

    /**
     * The registry is
     */
    public <T extends Map<String, ?>> T getRegistry(String registryName) {
        if (parent != null) {
            return parent.getRegistry(registryName);
        }

        if (!Arrays.asList(REGISTRY_CYCLE, REGISTRY_IFCHANGED, REGISTRY_FOR, REGISTRY_FOR_STACK)
                .contains(registryName)) {
            // this checking exists for safety of library, any listed type is expected, not more
            throw new RuntimeException("unknown registry type: " + registryName);
        }
        if (registry == null) {
            registry = new HashMap<>();
        }

        if (!registry.containsKey(registryName)) {
            registry.put(registryName, new HashMap<String, T>());
        }
        return (T) registry.get(registryName);
    }

    public ParseSettings getParseSettings() {
        return parser.getParseSettings();
    }

    public RenderSettings getRenderSettings() {
        return parser.getRenderSettings();
    }

    public ProtectionSettings getProtectionSettings() {
        return parser.getProtectionSettings();
    }

    public ObjectAppender.Controller newObjectAppender(int estimatedNumberOfAppends) {
        return renderSettings.getRenderTransformer().newObjectAppender(this,
                estimatedNumberOfAppends);
    }

    public TemplateContext newChildContext(Map<String, Object> variablesForChild) {
        return new TemplateContext(variablesForChild, this);
    }
}
