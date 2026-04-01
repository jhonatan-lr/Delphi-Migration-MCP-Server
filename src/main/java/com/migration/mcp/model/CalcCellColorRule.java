package com.migration.mcp.model;

import com.fasterxml.jackson.annotation.JsonInclude;

import java.util.ArrayList;
import java.util.List;

/**
 * Representa lógica de colorização condicional de células no grid Delphi (CalcCellColors).
 * Mapeia para [ngClass] ou [ngStyle] no template PrimeNG.
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public class CalcCellColorRule {

    private String gridName;           // "grdPedidoAutomatico"
    private String conditionField;     // "flg_tp_pedido" — campo que determina a cor
    private List<ColorMapping> colorMappings = new ArrayList<>();
    private String angularCode;        // código [ngClass] ou [ngStyle] sugerido
    /** "COLOR_ONLY" (CalcCellColors) ou "FULL_RENDERER" (DrawColumnCell — requer ng-template manual) */
    private String renderType = "COLOR_ONLY";
    private String migrationNote;      // nota adicional de migração

    // ── Getters / Setters ──────────────────────────────────────────────────

    public String getGridName() { return gridName; }
    public void setGridName(String gridName) { this.gridName = gridName; }
    public String getConditionField() { return conditionField; }
    public void setConditionField(String conditionField) { this.conditionField = conditionField; }
    public List<ColorMapping> getColorMappings() { return colorMappings; }
    public void setColorMappings(List<ColorMapping> colorMappings) { this.colorMappings = colorMappings; }
    public String getAngularCode() { return angularCode; }
    public void setAngularCode(String angularCode) { this.angularCode = angularCode; }
    public String getRenderType() { return renderType; }
    public void setRenderType(String renderType) { this.renderType = renderType; }
    public String getMigrationNote() { return migrationNote; }
    public void setMigrationNote(String migrationNote) { this.migrationNote = migrationNote; }

    // ── Nested: ColorMapping ────────────────────────────────────────────────

    @JsonInclude(JsonInclude.Include.NON_NULL)
    public static class ColorMapping {
        private String value;          // "1", "2", "3", "4"
        private String color;          // "green", "blue", "yellow", "orange"
        private String cssClass;       // classe CSS sugerida: "text-green", "text-blue"
        private String label;          // label da legenda se encontrado: "Automático", "Manual"

        public ColorMapping() {}
        public ColorMapping(String value, String color, String cssClass, String label) {
            this.value = value;
            this.color = color;
            this.cssClass = cssClass;
            this.label = label;
        }

        public String getValue() { return value; }
        public void setValue(String value) { this.value = value; }
        public String getColor() { return color; }
        public void setColor(String color) { this.color = color; }
        public String getCssClass() { return cssClass; }
        public void setCssClass(String cssClass) { this.cssClass = cssClass; }
        public String getLabel() { return label; }
        public void setLabel(String label) { this.label = label; }
    }
}
