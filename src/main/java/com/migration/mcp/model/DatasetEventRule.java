package com.migration.mcp.model;

import com.fasterxml.jackson.annotation.JsonInclude;
import java.util.ArrayList;
import java.util.List;

/**
 * Representa lógica de eventos de dataset (AfterInsert, CalcFields, BeforeDelete, etc).
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public class DatasetEventRule {

    private String dataset;            // "cdsPedido"
    private String event;              // "AfterInsert", "CalcFields", "BeforeDelete"
    private String eventType;          // "NEW_RECORD_DEFAULTS", "COMPUTED_FIELD", "GUARD", "CASCADE"
    private List<FieldAssignment> fields = new ArrayList<>();
    private String expression;         // para CalcFields: "dcr_produto + dcr_variedade"
    private String sourceMethod;       // "cdsPedidoAfterInsert"
    private String migration;

    public String getDataset() { return dataset; }
    public void setDataset(String dataset) { this.dataset = dataset; }
    public String getEvent() { return event; }
    public void setEvent(String event) { this.event = event; }
    public String getEventType() { return eventType; }
    public void setEventType(String eventType) { this.eventType = eventType; }
    public List<FieldAssignment> getFields() { return fields; }
    public void setFields(List<FieldAssignment> fields) { this.fields = fields; }
    public String getExpression() { return expression; }
    public void setExpression(String expression) { this.expression = expression; }
    public String getSourceMethod() { return sourceMethod; }
    public void setSourceMethod(String sourceMethod) { this.sourceMethod = sourceMethod; }
    public String getMigration() { return migration; }
    public void setMigration(String migration) { this.migration = migration; }

    @JsonInclude(JsonInclude.Include.NON_NULL)
    public static class FieldAssignment {
        private String field;
        private String value;

        public FieldAssignment() {}
        public FieldAssignment(String field, String value) { this.field = field; this.value = value; }

        public String getField() { return field; }
        public void setField(String field) { this.field = field; }
        public String getValue() { return value; }
        public void setValue(String value) { this.value = value; }
    }
}
