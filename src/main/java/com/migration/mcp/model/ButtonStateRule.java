package com.migration.mcp.model;

import com.fasterxml.jackson.annotation.JsonInclude;

import java.util.ArrayList;
import java.util.List;

/**
 * Representa a lógica de estado de um botão em tela Delphi.
 * Combina informações do AfterScroll (quando habilitado) com o Click handler (o que faz).
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public class ButtonStateRule {

    private String buttonName;          // "bbtCancelar"
    private String action;              // "vPedidoAutomatico.Cancelar"
    private String actionType;          // "business_method", "navigation", "report", "search", "crud"
    private String confirmMessage;      // "Deseja realmente cancelar este pedido {cdg_ped_auto}?"
    private List<String> enableConditions = new ArrayList<>();  // condições do EnableComponent
    private String enableConditionRaw;  // condição original completa (multi-linha)
    private String requiresPermission;  // "Parametros.WMS.Estoque.PedidoAutomatico.PermitirConfirmacaoPelaLoja"
    private List<String> fieldReferences = new ArrayList<>();   // ["flg_tp_pedido", "flg_status_pedido"]
    private String sourceMethod;        // "bbtCancelarClick"
    private String dataset;             // "cdsPedidosAutomaticos" (de qual AfterScroll veio)
    private List<String> migrationHints = new ArrayList<>();

    // ── Getters / Setters ──────────────────────────────────────────────────

    public String getButtonName() { return buttonName; }
    public void setButtonName(String buttonName) { this.buttonName = buttonName; }
    public String getAction() { return action; }
    public void setAction(String action) { this.action = action; }
    public String getActionType() { return actionType; }
    public void setActionType(String actionType) { this.actionType = actionType; }
    public String getConfirmMessage() { return confirmMessage; }
    public void setConfirmMessage(String confirmMessage) { this.confirmMessage = confirmMessage; }
    public List<String> getEnableConditions() { return enableConditions; }
    public void setEnableConditions(List<String> enableConditions) { this.enableConditions = enableConditions; }
    public String getEnableConditionRaw() { return enableConditionRaw; }
    public void setEnableConditionRaw(String enableConditionRaw) { this.enableConditionRaw = enableConditionRaw; }
    public String getRequiresPermission() { return requiresPermission; }
    public void setRequiresPermission(String requiresPermission) { this.requiresPermission = requiresPermission; }
    public List<String> getFieldReferences() { return fieldReferences; }
    public void setFieldReferences(List<String> fieldReferences) { this.fieldReferences = fieldReferences; }
    public String getSourceMethod() { return sourceMethod; }
    public void setSourceMethod(String sourceMethod) { this.sourceMethod = sourceMethod; }
    public String getDataset() { return dataset; }
    public void setDataset(String dataset) { this.dataset = dataset; }
    public List<String> getMigrationHints() { return migrationHints; }
    public void setMigrationHints(List<String> migrationHints) { this.migrationHints = migrationHints; }
}
