package com.migration.mcp.model;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonInclude;
import java.util.*;

/**
 * Padrões do projeto alvo extraídos de entity-patterns.json.
 * Carregado via load_target_patterns ou automaticamente de ~/.delphi-mcp/entity-patterns.json.
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
@JsonIgnoreProperties(ignoreUnknown = true)
public class TargetPatterns {

    /** Expansão de abreviações: "cancel" → "cancelamento", "prev" → "previsao" */
    private Map<String, String> columnNameExpansions = new LinkedHashMap<>();

    /** FKs conhecidas: "cdg_filial" → "FilialEntity" */
    private Map<String, String> knownForeignKeys = new LinkedHashMap<>();

    /** FKs que são String (CNPJ, códigos texto): "cdg_fornecedor" */
    private List<String> stringForeignKeys = new ArrayList<>();

    /** Enums conhecidos: "flg_status_pedido" → {enumClass, converterClass, values} */
    private Map<String, EnumPattern> knownEnums = new LinkedHashMap<>();

    /** Tabelas conhecidas: "estmpedautomatico" → {entity, pk} */
    private Map<String, TablePattern> knownTables = new LinkedHashMap<>();

    /** Relacionamentos master-detail: "estdpedautomatico" → {master, fk} */
    private Map<String, MasterDetailPattern> masterDetailRelationships = new LinkedHashMap<>();

    // ── Inner classes ──

    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class EnumPattern {
        private String enumClass;
        private String converterClass;
        private Map<String, String> values;

        public String getEnumClass() { return enumClass; }
        public void setEnumClass(String e) { this.enumClass = e; }
        public String getConverterClass() { return converterClass; }
        public void setConverterClass(String c) { this.converterClass = c; }
        public Map<String, String> getValues() { return values; }
        public void setValues(Map<String, String> v) { this.values = v; }
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class ColumnPattern {
        private String name;       // nome da coluna (lowercase), ex: "cdg_filial"
        private String typeName;   // tipo Informix: "INTEGER", "VARCHAR", "DECIMAL"
        private String javaType;   // tipo Java mapeado: "Integer", "String", "BigDecimal"
        private boolean nullable;
        private int length;        // collength do Informix

        public String getName() { return name; }
        public void setName(String n) { this.name = n; }
        public String getTypeName() { return typeName; }
        public void setTypeName(String t) { this.typeName = t; }
        public String getJavaType() { return javaType; }
        public void setJavaType(String j) { this.javaType = j; }
        public boolean isNullable() { return nullable; }
        public void setNullable(boolean n) { this.nullable = n; }
        public int getLength() { return length; }
        public void setLength(int l) { this.length = l; }
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class TablePattern {
        private String entity;
        private String pk;
        private boolean naturalKey;
        private List<ColumnPattern> columns;

        public String getEntity() { return entity; }
        public void setEntity(String e) { this.entity = e; }
        public String getPk() { return pk; }
        public void setPk(String p) { this.pk = p; }
        public boolean isNaturalKey() { return naturalKey; }
        public void setNaturalKey(boolean n) { this.naturalKey = n; }
        public List<ColumnPattern> getColumns() { return columns; }
        public void setColumns(List<ColumnPattern> c) { this.columns = c; }
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class MasterDetailPattern {
        private String master;
        private String fk;

        public String getMaster() { return master; }
        public void setMaster(String m) { this.master = m; }
        public String getFk() { return fk; }
        public void setFk(String f) { this.fk = f; }
    }

    // ── Getters/Setters ──

    public Map<String, String> getColumnNameExpansions() { return columnNameExpansions; }
    public void setColumnNameExpansions(Map<String, String> c) { this.columnNameExpansions = c; }
    public Map<String, String> getKnownForeignKeys() { return knownForeignKeys; }
    public void setKnownForeignKeys(Map<String, String> k) { this.knownForeignKeys = k; }
    public List<String> getStringForeignKeys() { return stringForeignKeys; }
    public void setStringForeignKeys(List<String> s) { this.stringForeignKeys = s; }
    public Map<String, EnumPattern> getKnownEnums() { return knownEnums; }
    public void setKnownEnums(Map<String, EnumPattern> e) { this.knownEnums = e; }
    public Map<String, TablePattern> getKnownTables() { return knownTables; }
    public void setKnownTables(Map<String, TablePattern> t) { this.knownTables = t; }
    public Map<String, MasterDetailPattern> getMasterDetailRelationships() { return masterDetailRelationships; }
    public void setMasterDetailRelationships(Map<String, MasterDetailPattern> m) { this.masterDetailRelationships = m; }
}
