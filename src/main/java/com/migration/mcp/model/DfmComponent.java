package com.migration.mcp.model;

import com.fasterxml.jackson.annotation.JsonInclude;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@JsonInclude(JsonInclude.Include.NON_NULL)
public class DfmComponent {
    private String name;
    private String delphiType;          // TButton, TEdit, TGrid, etc.
    private String angularEquivalent;   // mat-button, input, mat-table, etc.
    private Map<String, String> properties = new HashMap<>();
    private List<DfmComponent> children = new ArrayList<>();
    private List<String> events = new ArrayList<>();     // OnClick, OnChange...
    private String layout;               // posição/layout sugerido

    public String getName() { return name; }
    public void setName(String name) { this.name = name; }
    public String getDelphiType() { return delphiType; }
    public void setDelphiType(String delphiType) { this.delphiType = delphiType; }
    public String getAngularEquivalent() { return angularEquivalent; }
    public void setAngularEquivalent(String angularEquivalent) { this.angularEquivalent = angularEquivalent; }
    public Map<String, String> getProperties() { return properties; }
    public void setProperties(Map<String, String> properties) { this.properties = properties; }
    public List<DfmComponent> getChildren() { return children; }
    public void setChildren(List<DfmComponent> children) { this.children = children; }
    public List<String> getEvents() { return events; }
    public void setEvents(List<String> events) { this.events = events; }
    public String getLayout() { return layout; }
    public void setLayout(String layout) { this.layout = layout; }
}
