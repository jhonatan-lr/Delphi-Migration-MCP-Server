package com.migration.mcp.model;

import com.fasterxml.jackson.annotation.JsonInclude;
import java.util.ArrayList;
import java.util.List;

/**
 * Representa lógica de dependência entre campos (Exit/Change/OnSelect events).
 * Campo A muda → Campo B é preenchido/limpo/validado.
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public class DependentFieldRule {

    private String triggerField;       // "edtCodigoFornecedorDisplay"
    private String triggerEvent;       // "Exit", "Change", "Click", "OnSelect"
    private String sourceMethod;       // "edtCodigoFornecedorDisplayExit"
    private List<FieldEffect> effects = new ArrayList<>();
    private String validationMessage;  // "Fornecedor não encontrado!"
    private String angularHint;        // "valueChanges → switchMap para endpoint"

    public String getTriggerField() { return triggerField; }
    public void setTriggerField(String triggerField) { this.triggerField = triggerField; }
    public String getTriggerEvent() { return triggerEvent; }
    public void setTriggerEvent(String triggerEvent) { this.triggerEvent = triggerEvent; }
    public String getSourceMethod() { return sourceMethod; }
    public void setSourceMethod(String sourceMethod) { this.sourceMethod = sourceMethod; }
    public List<FieldEffect> getEffects() { return effects; }
    public void setEffects(List<FieldEffect> effects) { this.effects = effects; }
    public String getValidationMessage() { return validationMessage; }
    public void setValidationMessage(String validationMessage) { this.validationMessage = validationMessage; }
    public String getAngularHint() { return angularHint; }
    public void setAngularHint(String angularHint) { this.angularHint = angularHint; }

    @JsonInclude(JsonInclude.Include.NON_NULL)
    public static class FieldEffect {
        private String targetField;    // "edtNomeFornecedorDisplay"
        private String action;         // "fill", "clear", "disable", "enable", "calculate"
        private String source;         // "TFornecedor.GetNomeFornecedor(codigo)"
        private String fallback;       // "TODOS FORNECEDORES"

        public FieldEffect() {}
        public FieldEffect(String targetField, String action, String source, String fallback) {
            this.targetField = targetField; this.action = action; this.source = source; this.fallback = fallback;
        }

        public String getTargetField() { return targetField; }
        public void setTargetField(String targetField) { this.targetField = targetField; }
        public String getAction() { return action; }
        public void setAction(String action) { this.action = action; }
        public String getSource() { return source; }
        public void setSource(String source) { this.source = source; }
        public String getFallback() { return fallback; }
        public void setFallback(String fallback) { this.fallback = fallback; }
    }
}
