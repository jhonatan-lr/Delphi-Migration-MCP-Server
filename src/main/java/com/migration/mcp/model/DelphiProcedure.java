package com.migration.mcp.model;

import com.fasterxml.jackson.annotation.JsonInclude;
import java.util.ArrayList;
import java.util.List;

@JsonInclude(JsonInclude.Include.NON_NULL)
public class DelphiProcedure {
    private String name;
    private String type;          // "procedure", "function", "constructor", "destructor"
    private String returnType;    // Delphi return type (for functions)
    private String javaReturnType;
    private List<String> parameters = new ArrayList<>();
    private String body;
    private String visibility;
    private boolean isEventHandler;
    private String eventType;     // ex: "OnClick", "OnChange"
    private List<String> sqlQueriesFound = new ArrayList<>();
    private List<String> businessRulesFound = new ArrayList<>();
    private String migrationNotes;

    public String getName() { return name; }
    public void setName(String name) { this.name = name; }
    public String getType() { return type; }
    public void setType(String type) { this.type = type; }
    public String getReturnType() { return returnType; }
    public void setReturnType(String returnType) { this.returnType = returnType; }
    public String getJavaReturnType() { return javaReturnType; }
    public void setJavaReturnType(String javaReturnType) { this.javaReturnType = javaReturnType; }
    public List<String> getParameters() { return parameters; }
    public void setParameters(List<String> parameters) { this.parameters = parameters; }
    public String getBody() { return body; }
    public void setBody(String body) { this.body = body; }
    public String getVisibility() { return visibility; }
    public void setVisibility(String visibility) { this.visibility = visibility; }
    public boolean isEventHandler() { return isEventHandler; }
    public void setEventHandler(boolean eventHandler) { isEventHandler = eventHandler; }
    public String getEventType() { return eventType; }
    public void setEventType(String eventType) { this.eventType = eventType; }
    public List<String> getSqlQueriesFound() { return sqlQueriesFound; }
    public void setSqlQueriesFound(List<String> s) { this.sqlQueriesFound = s; }
    public List<String> getBusinessRulesFound() { return businessRulesFound; }
    public void setBusinessRulesFound(List<String> b) { this.businessRulesFound = b; }
    public String getMigrationNotes() { return migrationNotes; }
    public void setMigrationNotes(String migrationNotes) { this.migrationNotes = migrationNotes; }
}
