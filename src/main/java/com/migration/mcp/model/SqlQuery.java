package com.migration.mcp.model;

import com.fasterxml.jackson.annotation.JsonInclude;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

@JsonInclude(JsonInclude.Include.NON_NULL)
public class SqlQuery {
    private String id;
    private String sql;
    private String queryType;       // SELECT, INSERT, UPDATE, DELETE, STORED_PROC
    private String componentName;   // nome do TQuery, TTable, etc.
    private String context;         // método onde foi encontrada
    private List<String> tablesUsed = new ArrayList<>();
    private String jpaEquivalent;   // sugestão de JPA/JPQL equivalente
    private String repositoryMethod; // sugestão de método de repository
    private String conditionalBranch; // condição do if/else se SQL dinâmico

    /** Parâmetros tipados: [{name, javaType, delphiAsType, bindExpression}] */
    private List<Map<String, String>> params = new ArrayList<>();

    /** Injeções dinâmicas via concatenação de string: ex "lucSituacaoPedido.KeyValue" */
    private List<String> dynamicInjections = new ArrayList<>();

    public String getId() { return id; }
    public void setId(String id) { this.id = id; }
    public String getSql() { return sql; }
    public void setSql(String sql) { this.sql = sql; }
    public String getQueryType() { return queryType; }
    public void setQueryType(String queryType) { this.queryType = queryType; }
    public String getComponentName() { return componentName; }
    public void setComponentName(String componentName) { this.componentName = componentName; }
    public String getContext() { return context; }
    public void setContext(String context) { this.context = context; }
    public List<String> getTablesUsed() { return tablesUsed; }
    public void setTablesUsed(List<String> tablesUsed) { this.tablesUsed = tablesUsed; }
    public String getJpaEquivalent() { return jpaEquivalent; }
    public void setJpaEquivalent(String jpaEquivalent) { this.jpaEquivalent = jpaEquivalent; }
    public String getRepositoryMethod() { return repositoryMethod; }
    public void setRepositoryMethod(String repositoryMethod) { this.repositoryMethod = repositoryMethod; }
    public String getConditionalBranch() { return conditionalBranch; }
    public void setConditionalBranch(String conditionalBranch) { this.conditionalBranch = conditionalBranch; }
    public List<Map<String, String>> getParams() { return params; }
    public void setParams(List<Map<String, String>> params) { this.params = params; }
    public List<String> getDynamicInjections() { return dynamicInjections; }
    public void setDynamicInjections(List<String> dynamicInjections) { this.dynamicInjections = dynamicInjections; }
}
