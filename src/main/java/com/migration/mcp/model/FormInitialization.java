package com.migration.mcp.model;

import com.fasterxml.jackson.annotation.JsonInclude;

import java.util.ArrayList;
import java.util.List;

/**
 * Representa a lógica de inicialização de uma tela Delphi (FormShow/FormCreate/FormActivate).
 * Detecta valores default, pré-seleções de combos, auto-loads e estados iniciais de componentes.
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public class FormInitialization {

    private String context;  // "FormShow", "FormCreate", "FormActivate"
    private List<DefaultValue> defaultValues = new ArrayList<>();
    private List<ConditionalDefault> conditionalDefaults = new ArrayList<>();
    private List<ComboPreselection> comboPreselections = new ArrayList<>();
    private List<AutoLoad> autoLoads = new ArrayList<>();
    private List<InitialState> initialStates = new ArrayList<>();

    public int totalDetected() {
        return defaultValues.size() + conditionalDefaults.size() +
               comboPreselections.size() + autoLoads.size() + initialStates.size();
    }

    // ── Getters / Setters ──────────────────────────────────────────────────

    public String getContext() { return context; }
    public void setContext(String context) { this.context = context; }
    public List<DefaultValue> getDefaultValues() { return defaultValues; }
    public void setDefaultValues(List<DefaultValue> defaultValues) { this.defaultValues = defaultValues; }
    public List<ConditionalDefault> getConditionalDefaults() { return conditionalDefaults; }
    public void setConditionalDefaults(List<ConditionalDefault> conditionalDefaults) { this.conditionalDefaults = conditionalDefaults; }
    public List<ComboPreselection> getComboPreselections() { return comboPreselections; }
    public void setComboPreselections(List<ComboPreselection> comboPreselections) { this.comboPreselections = comboPreselections; }
    public List<AutoLoad> getAutoLoads() { return autoLoads; }
    public void setAutoLoads(List<AutoLoad> autoLoads) { this.autoLoads = autoLoads; }
    public List<InitialState> getInitialStates() { return initialStates; }
    public void setInitialStates(List<InitialState> initialStates) { this.initialStates = initialStates; }

    // ── Nested: DEFAULT_VALUE ──────────────────────────────────────────────

    @JsonInclude(JsonInclude.Include.NON_NULL)
    public static class DefaultValue {
        private String type = "DEFAULT_VALUE";
        private String component;
        private String componentType;
        private String property;
        private String value;
        private String description;
        private String context;
        private String migration;

        public String getType() { return type; }
        public void setType(String type) { this.type = type; }
        public String getComponent() { return component; }
        public void setComponent(String component) { this.component = component; }
        public String getComponentType() { return componentType; }
        public void setComponentType(String componentType) { this.componentType = componentType; }
        public String getProperty() { return property; }
        public void setProperty(String property) { this.property = property; }
        public String getValue() { return value; }
        public void setValue(String value) { this.value = value; }
        public String getDescription() { return description; }
        public void setDescription(String description) { this.description = description; }
        public String getContext() { return context; }
        public void setContext(String context) { this.context = context; }
        public String getMigration() { return migration; }
        public void setMigration(String migration) { this.migration = migration; }
    }

    // ── Nested: CONDITIONAL_DEFAULT ────────────────────────────────────────

    @JsonInclude(JsonInclude.Include.NON_NULL)
    public static class ConditionalDefault {
        private String type = "CONDITIONAL_DEFAULT";
        private String component;
        private String condition;
        private String value;
        private boolean disabled;
        private String description;
        private String migration;

        public String getType() { return type; }
        public void setType(String type) { this.type = type; }
        public String getComponent() { return component; }
        public void setComponent(String component) { this.component = component; }
        public String getCondition() { return condition; }
        public void setCondition(String condition) { this.condition = condition; }
        public String getValue() { return value; }
        public void setValue(String value) { this.value = value; }
        public boolean isDisabled() { return disabled; }
        public void setDisabled(boolean disabled) { this.disabled = disabled; }
        public String getDescription() { return description; }
        public void setDescription(String description) { this.description = description; }
        public String getMigration() { return migration; }
        public void setMigration(String migration) { this.migration = migration; }
    }

    // ── Nested: COMBO_PRESELECTION ─────────────────────────────────────────

    @JsonInclude(JsonInclude.Include.NON_NULL)
    public static class ComboPreselection {
        private String type = "COMBO_PRESELECTION";
        private String component;
        private List<String> selectedKeys;
        private String description;
        private String migration;

        public String getType() { return type; }
        public void setType(String type) { this.type = type; }
        public String getComponent() { return component; }
        public void setComponent(String component) { this.component = component; }
        public List<String> getSelectedKeys() { return selectedKeys; }
        public void setSelectedKeys(List<String> selectedKeys) { this.selectedKeys = selectedKeys; }
        public String getDescription() { return description; }
        public void setDescription(String description) { this.description = description; }
        public String getMigration() { return migration; }
        public void setMigration(String migration) { this.migration = migration; }
    }

    // ── Nested: AUTO_LOAD ──────────────────────────────────────────────────

    @JsonInclude(JsonInclude.Include.NON_NULL)
    public static class AutoLoad {
        private String type = "AUTO_LOAD";
        private String method;
        private String context;
        private String description;
        private String migration;

        public String getType() { return type; }
        public void setType(String type) { this.type = type; }
        public String getMethod() { return method; }
        public void setMethod(String method) { this.method = method; }
        public String getContext() { return context; }
        public void setContext(String context) { this.context = context; }
        public String getDescription() { return description; }
        public void setDescription(String description) { this.description = description; }
        public String getMigration() { return migration; }
        public void setMigration(String migration) { this.migration = migration; }
    }

    // ── Nested: INITIAL_STATE ──────────────────────────────────────────────

    @JsonInclude(JsonInclude.Include.NON_NULL)
    public static class InitialState {
        private String type = "INITIAL_STATE";
        private String component;
        private String state;       // "disabled", "enabled", "hidden", "visible"
        private String condition;
        private String description;
        private String migration;

        public String getType() { return type; }
        public void setType(String type) { this.type = type; }
        public String getComponent() { return component; }
        public void setComponent(String component) { this.component = component; }
        public String getState() { return state; }
        public void setState(String state) { this.state = state; }
        public String getCondition() { return condition; }
        public void setCondition(String condition) { this.condition = condition; }
        public String getDescription() { return description; }
        public void setDescription(String description) { this.description = description; }
        public String getMigration() { return migration; }
        public void setMigration(String migration) { this.migration = migration; }
    }
}
