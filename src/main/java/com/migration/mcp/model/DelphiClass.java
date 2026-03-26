package com.migration.mcp.model;

import com.fasterxml.jackson.annotation.JsonInclude;
import java.util.ArrayList;
import java.util.List;

@JsonInclude(JsonInclude.Include.NON_NULL)
public class DelphiClass {
    private String name;
    private String parentClass;
    private String classType; // TForm, TDataModule, TThread, etc.
    private List<DelphiField> fields = new ArrayList<>();
    private List<DelphiProcedure> methods = new ArrayList<>();
    private List<String> properties = new ArrayList<>();
    private String migrationSuggestion; // ex: "Spring @Service", "Angular Component"

    public String getName() { return name; }
    public void setName(String name) { this.name = name; }
    public String getParentClass() { return parentClass; }
    public void setParentClass(String parentClass) { this.parentClass = parentClass; }
    public String getClassType() { return classType; }
    public void setClassType(String classType) { this.classType = classType; }
    public List<DelphiField> getFields() { return fields; }
    public void setFields(List<DelphiField> fields) { this.fields = fields; }
    public List<DelphiProcedure> getMethods() { return methods; }
    public void setMethods(List<DelphiProcedure> methods) { this.methods = methods; }
    public List<String> getProperties() { return properties; }
    public void setProperties(List<String> properties) { this.properties = properties; }
    public String getMigrationSuggestion() { return migrationSuggestion; }
    public void setMigrationSuggestion(String s) { this.migrationSuggestion = s; }
}
