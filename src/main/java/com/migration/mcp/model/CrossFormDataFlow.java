package com.migration.mcp.model;

import com.fasterxml.jackson.annotation.JsonInclude;
import java.util.ArrayList;
import java.util.List;

/**
 * Representa fluxo de dados entre telas (parâmetros de entrada, retorno esperado, ação pós-retorno).
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public class CrossFormDataFlow {

    private String targetForm;         // "TfrmManutencaoPedidoAutomatico"
    private String method;             // "MakeShowModal"
    private List<FormParam> paramsIn = new ArrayList<>();
    private String expectedReturn;     // "mrOk", "mrCancel", null (void)
    private String onSuccessAction;    // "CarregarListaPedidoAutomatico"
    private String sourceMethod;       // "grdPedidoAutomaticoDblClick"
    private String migration;

    public String getTargetForm() { return targetForm; }
    public void setTargetForm(String targetForm) { this.targetForm = targetForm; }
    public String getMethod() { return method; }
    public void setMethod(String method) { this.method = method; }
    public List<FormParam> getParamsIn() { return paramsIn; }
    public void setParamsIn(List<FormParam> paramsIn) { this.paramsIn = paramsIn; }
    public String getExpectedReturn() { return expectedReturn; }
    public void setExpectedReturn(String expectedReturn) { this.expectedReturn = expectedReturn; }
    public String getOnSuccessAction() { return onSuccessAction; }
    public void setOnSuccessAction(String onSuccessAction) { this.onSuccessAction = onSuccessAction; }
    public String getSourceMethod() { return sourceMethod; }
    public void setSourceMethod(String sourceMethod) { this.sourceMethod = sourceMethod; }
    public String getMigration() { return migration; }
    public void setMigration(String migration) { this.migration = migration; }

    @JsonInclude(JsonInclude.Include.NON_NULL)
    public static class FormParam {
        private String value;          // "cdsPedidosAutomaticos.FieldByName('cdg_filial').AsInteger"
        private String field;          // "cdg_filial" (extraído)
        private String type;           // "Integer"

        public FormParam() {}
        public FormParam(String value, String field, String type) {
            this.value = value; this.field = field; this.type = type;
        }

        public String getValue() { return value; }
        public void setValue(String value) { this.value = value; }
        public String getField() { return field; }
        public void setField(String field) { this.field = field; }
        public String getType() { return type; }
        public void setType(String type) { this.type = type; }
    }
}
