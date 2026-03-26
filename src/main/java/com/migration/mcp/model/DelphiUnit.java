package com.migration.mcp.model;

import com.fasterxml.jackson.annotation.JsonInclude;
import java.util.ArrayList;
import java.util.List;

/**
 * Representa uma Unit Delphi analisada
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public class DelphiUnit {

    private String unitName;
    private String filePath;
    private String unitType; // "form", "datamodule", "class", "utility"
    private List<DelphiClass> classes = new ArrayList<>();
    private List<String> uses = new ArrayList<>();         // units importadas
    private List<String> globalVariables = new ArrayList<>();
    private List<DelphiProcedure> globalProcedures = new ArrayList<>();
    private List<SqlQuery> sqlQueries = new ArrayList<>();
    private List<BusinessRule> businessRules = new ArrayList<>();
    private String rawContent;

    // getters e setters
    public String getUnitName() { return unitName; }
    public void setUnitName(String unitName) { this.unitName = unitName; }

    public String getFilePath() { return filePath; }
    public void setFilePath(String filePath) { this.filePath = filePath; }

    public String getUnitType() { return unitType; }
    public void setUnitType(String unitType) { this.unitType = unitType; }

    public List<DelphiClass> getClasses() { return classes; }
    public void setClasses(List<DelphiClass> classes) { this.classes = classes; }

    public List<String> getUses() { return uses; }
    public void setUses(List<String> uses) { this.uses = uses; }

    public List<String> getGlobalVariables() { return globalVariables; }
    public void setGlobalVariables(List<String> globalVariables) { this.globalVariables = globalVariables; }

    public List<DelphiProcedure> getGlobalProcedures() { return globalProcedures; }
    public void setGlobalProcedures(List<DelphiProcedure> procs) { this.globalProcedures = procs; }

    public List<SqlQuery> getSqlQueries() { return sqlQueries; }
    public void setSqlQueries(List<SqlQuery> sqlQueries) { this.sqlQueries = sqlQueries; }

    public List<BusinessRule> getBusinessRules() { return businessRules; }
    public void setBusinessRules(List<BusinessRule> businessRules) { this.businessRules = businessRules; }

    public String getRawContent() { return rawContent; }
    public void setRawContent(String rawContent) { this.rawContent = rawContent; }
}
