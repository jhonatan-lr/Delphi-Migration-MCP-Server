package com.migration.mcp.model;

import com.fasterxml.jackson.annotation.JsonInclude;

/**
 * Representa uma regra de validação estruturada por campo, extraída de métodos ValidacaoOk.
 * Cada regra mapeia para um Validator Angular específico.
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public class FieldValidationRule {

    private String field;              // "edtDataEmissaoDe"
    private String validationType;     // "required", "cross_field", "pattern", "custom"
    private String relatedField;       // campo relacionado (cross-field): "edtDataEmissaoAte"
    private String operator;           // ">=", "<=", "max_range", "min_range", "equals"
    private String value;              // "90" (para max_range), "0" (para comparação)
    private String message;            // "Obrigatório informar data de emissão inicial."
    private String sourceMethod;       // "ValidacaoOk", "ValidacaoOkNovoPedido"
    private String angularValidator;   // "Validators.required", "Validators.pattern('^[0-9]*$')"
    private String angularCode;        // código completo do custom validator se necessário

    // ── Getters / Setters ──────────────────────────────────────────────────

    public String getField() { return field; }
    public void setField(String field) { this.field = field; }
    public String getValidationType() { return validationType; }
    public void setValidationType(String validationType) { this.validationType = validationType; }
    public String getRelatedField() { return relatedField; }
    public void setRelatedField(String relatedField) { this.relatedField = relatedField; }
    public String getOperator() { return operator; }
    public void setOperator(String operator) { this.operator = operator; }
    public String getValue() { return value; }
    public void setValue(String value) { this.value = value; }
    public String getMessage() { return message; }
    public void setMessage(String message) { this.message = message; }
    public String getSourceMethod() { return sourceMethod; }
    public void setSourceMethod(String sourceMethod) { this.sourceMethod = sourceMethod; }
    public String getAngularValidator() { return angularValidator; }
    public void setAngularValidator(String angularValidator) { this.angularValidator = angularValidator; }
    public String getAngularCode() { return angularCode; }
    public void setAngularCode(String angularCode) { this.angularCode = angularCode; }
}
