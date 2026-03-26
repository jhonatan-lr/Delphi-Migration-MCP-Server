package com.migration.mcp.model;

import com.fasterxml.jackson.annotation.JsonInclude;
import java.util.ArrayList;
import java.util.List;

@JsonInclude(JsonInclude.Include.NON_NULL)
public class DelphiField {
    private String name;
    private String delphiType;
    private String javaType;     // tipo Java sugerido
    private String visibility;   // public, private, protected
    private boolean isComponent; // é um componente visual?

    public String getName() { return name; }
    public void setName(String name) { this.name = name; }
    public String getDelphiType() { return delphiType; }
    public void setDelphiType(String delphiType) { this.delphiType = delphiType; }
    public String getJavaType() { return javaType; }
    public void setJavaType(String javaType) { this.javaType = javaType; }
    public String getVisibility() { return visibility; }
    public void setVisibility(String visibility) { this.visibility = visibility; }
    public boolean isComponent() { return isComponent; }
    public void setComponent(boolean component) { isComponent = component; }
}
