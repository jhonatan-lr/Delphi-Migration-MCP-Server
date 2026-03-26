package com.migration.mcp.model;

import com.fasterxml.jackson.annotation.JsonInclude;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.LinkedHashMap;

@JsonInclude(JsonInclude.Include.NON_NULL)
public class DfmForm {
    private String formName;
    private String formType;           // "TForm", "TDataModule", "TFrame"
    private String caption;
    private int width;
    private int height;
    private List<DfmComponent> components = new ArrayList<>();
    private String angularComponentName;
    private String suggestedRoute;     // rota Angular sugerida
    private String angularTemplate;    // HTML gerado para o template Angular
    private String angularComponentTs; // TypeScript do componente Angular

    /** Colunas extraídas do TwwDBGrid.Selected.Strings: field, width, header, subheader */
    private List<GridColumn> gridColumns = new ArrayList<>();

    /** Campos extraídos dos TClientDataSet/TSQLQuery: nome -> tipo Delphi (TIntegerField, etc.) */
    private List<DatasetField> datasetFields = new ArrayList<>();

    public static class GridColumn {
        private String field;
        private String header;
        private String subHeader;
        private int widthChars;

        public GridColumn() {}
        public GridColumn(String field, String header, String subHeader, int widthChars) {
            this.field = field; this.header = header; this.subHeader = subHeader; this.widthChars = widthChars;
        }

        public String getField() { return field; }
        public void setField(String f) { this.field = f; }
        public String getHeader() { return header; }
        public void setHeader(String h) { this.header = h; }
        public String getSubHeader() { return subHeader; }
        public void setSubHeader(String s) { this.subHeader = s; }
        public int getWidthChars() { return widthChars; }
        public void setWidthChars(int w) { this.widthChars = w; }
    }

    public static class DatasetField {
        private String name;        // ex: cdg_ped_auto
        private String delphiType;  // ex: TIntegerField
        private String javaType;    // ex: Integer
        private String tsType;      // ex: number
        private boolean visible = true;

        public DatasetField() {}
        public DatasetField(String name, String delphiType) {
            this.name = name;
            this.delphiType = delphiType;
            this.javaType = mapToJava(delphiType);
            this.tsType = mapToTs(delphiType);
        }

        private String mapToJava(String dt) {
            if (dt == null) return "String";
            if (dt.contains("Integer") || dt.contains("Smallint")) return "Integer";
            if (dt.contains("Float") || dt.contains("Currency") || dt.contains("BCD")) return "BigDecimal";
            if (dt.contains("Date") || dt.contains("Time") || dt.contains("Timestamp")) return "Date";
            if (dt.contains("Boolean")) return "Boolean";
            return "String";
        }

        private String mapToTs(String dt) {
            if (dt == null) return "string";
            if (dt.contains("Integer") || dt.contains("Smallint") || dt.contains("Float") ||
                dt.contains("Currency") || dt.contains("BCD")) return "number";
            if (dt.contains("Date") || dt.contains("Time") || dt.contains("Timestamp")) return "string";
            if (dt.contains("Boolean")) return "boolean";
            return "string";
        }

        public String getName() { return name; }
        public void setName(String n) { this.name = n; }
        public String getDelphiType() { return delphiType; }
        public void setDelphiType(String dt) { this.delphiType = dt; }
        public String getJavaType() { return javaType; }
        public void setJavaType(String jt) { this.javaType = jt; }
        public String getTsType() { return tsType; }
        public void setTsType(String tt) { this.tsType = tt; }
        public boolean isVisible() { return visible; }
        public void setVisible(boolean v) { this.visible = v; }
    }

    public String getFormName() { return formName; }
    public void setFormName(String formName) { this.formName = formName; }
    public String getFormType() { return formType; }
    public void setFormType(String formType) { this.formType = formType; }
    public String getCaption() { return caption; }
    public void setCaption(String caption) { this.caption = caption; }
    public int getWidth() { return width; }
    public void setWidth(int width) { this.width = width; }
    public int getHeight() { return height; }
    public void setHeight(int height) { this.height = height; }
    public List<DfmComponent> getComponents() { return components; }
    public void setComponents(List<DfmComponent> components) { this.components = components; }
    public String getAngularComponentName() { return angularComponentName; }
    public void setAngularComponentName(String angularComponentName) { this.angularComponentName = angularComponentName; }
    public String getSuggestedRoute() { return suggestedRoute; }
    public void setSuggestedRoute(String suggestedRoute) { this.suggestedRoute = suggestedRoute; }
    public String getAngularTemplate() { return angularTemplate; }
    public void setAngularTemplate(String angularTemplate) { this.angularTemplate = angularTemplate; }
    public String getAngularComponentTs() { return angularComponentTs; }
    public void setAngularComponentTs(String angularComponentTs) { this.angularComponentTs = angularComponentTs; }
    public List<GridColumn> getGridColumns() { return gridColumns; }
    public void setGridColumns(List<GridColumn> gridColumns) { this.gridColumns = gridColumns; }
    public List<DatasetField> getDatasetFields() { return datasetFields; }
    public void setDatasetFields(List<DatasetField> datasetFields) { this.datasetFields = datasetFields; }
}
