package com.migration.mcp.model;

import com.fasterxml.jackson.annotation.JsonInclude;
import java.util.ArrayList;
import java.util.List;

/**
 * Representa lógica do BeforeUpdateRecord (sequences, propagação de chaves master→detail).
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public class ProviderUpdateRule {

    private String provider;           // "dspPedido"
    private String sourceMethod;       // "dspPedidoBeforeUpdateRecord"
    private List<SequenceRule> sequences = new ArrayList<>();
    private List<KeyPropagation> keyPropagations = new ArrayList<>();
    private String migration;

    public String getProvider() { return provider; }
    public void setProvider(String provider) { this.provider = provider; }
    public String getSourceMethod() { return sourceMethod; }
    public void setSourceMethod(String sourceMethod) { this.sourceMethod = sourceMethod; }
    public List<SequenceRule> getSequences() { return sequences; }
    public void setSequences(List<SequenceRule> sequences) { this.sequences = sequences; }
    public List<KeyPropagation> getKeyPropagations() { return keyPropagations; }
    public void setKeyPropagations(List<KeyPropagation> keyPropagations) { this.keyPropagations = keyPropagations; }
    public String getMigration() { return migration; }
    public void setMigration(String migration) { this.migration = migration; }

    @JsonInclude(JsonInclude.Include.NON_NULL)
    public static class SequenceRule {
        private String field;          // "cdg_ped_auto"
        private String table;          // "estmpedautomatico"
        private String strategy;       // "Conexao.Next global", "Conexao.Next filtered by cdg_filial"
        private String filter;         // "cdg_filial = X" (se filtrado)

        public SequenceRule() {}
        public String getField() { return field; }
        public void setField(String field) { this.field = field; }
        public String getTable() { return table; }
        public void setTable(String table) { this.table = table; }
        public String getStrategy() { return strategy; }
        public void setStrategy(String strategy) { this.strategy = strategy; }
        public String getFilter() { return filter; }
        public void setFilter(String filter) { this.filter = filter; }
    }

    @JsonInclude(JsonInclude.Include.NON_NULL)
    public static class KeyPropagation {
        private String targetDataset;  // "cdsProdutos"
        private String targetField;    // "cdg_ped_auto"
        private String sourceField;    // "cdg_ped_auto"

        public KeyPropagation() {}
        public String getTargetDataset() { return targetDataset; }
        public void setTargetDataset(String targetDataset) { this.targetDataset = targetDataset; }
        public String getTargetField() { return targetField; }
        public void setTargetField(String targetField) { this.targetField = targetField; }
        public String getSourceField() { return sourceField; }
        public void setSourceField(String sourceField) { this.sourceField = sourceField; }
    }
}
