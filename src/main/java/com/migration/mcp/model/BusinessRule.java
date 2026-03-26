package com.migration.mcp.model;

import com.fasterxml.jackson.annotation.JsonInclude;

@JsonInclude(JsonInclude.Include.NON_NULL)
public class BusinessRule {
    private String id;
    private String description;
    private String ruleType;        // "validation", "calculation", "workflow", "constraint"
    private String sourceMethod;
    private String sourceCode;
    private String complexity;      // "low", "medium", "high"
    private String migrationStrategy; // como migrar essa regra
    private String suggestedJavaCode;

    public String getId() { return id; }
    public void setId(String id) { this.id = id; }
    public String getDescription() { return description; }
    public void setDescription(String description) { this.description = description; }
    public String getRuleType() { return ruleType; }
    public void setRuleType(String ruleType) { this.ruleType = ruleType; }
    public String getSourceMethod() { return sourceMethod; }
    public void setSourceMethod(String sourceMethod) { this.sourceMethod = sourceMethod; }
    public String getSourceCode() { return sourceCode; }
    public void setSourceCode(String sourceCode) { this.sourceCode = sourceCode; }
    public String getComplexity() { return complexity; }
    public void setComplexity(String complexity) { this.complexity = complexity; }
    public String getMigrationStrategy() { return migrationStrategy; }
    public void setMigrationStrategy(String migrationStrategy) { this.migrationStrategy = migrationStrategy; }
    public String getSuggestedJavaCode() { return suggestedJavaCode; }
    public void setSuggestedJavaCode(String suggestedJavaCode) { this.suggestedJavaCode = suggestedJavaCode; }
}
