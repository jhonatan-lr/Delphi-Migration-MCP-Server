package com.migration.mcp.model;

import com.fasterxml.jackson.annotation.JsonInclude;
import java.util.ArrayList;
import java.util.List;

@JsonInclude(JsonInclude.Include.NON_NULL)
public class MigrationPlan {

    private String projectName;
    private String analysisDate;
    private Summary summary;
    private List<Phase> phases = new ArrayList<>();
    private List<String> risks = new ArrayList<>();
    private List<String> recommendations = new ArrayList<>();
    private ArchitectureSuggestion architectureSuggestion;

    @JsonInclude(JsonInclude.Include.NON_NULL)
    public static class Summary {
        private int totalUnits;
        private int totalForms;
        private int totalClasses;
        private int totalMethods;
        private int totalSqlQueries;
        private int totalBusinessRules;
        private String estimatedComplexity; // "low", "medium", "high", "very_high"
        private String estimatedEffortWeeks;

        public int getTotalUnits() { return totalUnits; }
        public void setTotalUnits(int totalUnits) { this.totalUnits = totalUnits; }
        public int getTotalForms() { return totalForms; }
        public void setTotalForms(int totalForms) { this.totalForms = totalForms; }
        public int getTotalClasses() { return totalClasses; }
        public void setTotalClasses(int totalClasses) { this.totalClasses = totalClasses; }
        public int getTotalMethods() { return totalMethods; }
        public void setTotalMethods(int totalMethods) { this.totalMethods = totalMethods; }
        public int getTotalSqlQueries() { return totalSqlQueries; }
        public void setTotalSqlQueries(int totalSqlQueries) { this.totalSqlQueries = totalSqlQueries; }
        public int getTotalBusinessRules() { return totalBusinessRules; }
        public void setTotalBusinessRules(int totalBusinessRules) { this.totalBusinessRules = totalBusinessRules; }
        public String getEstimatedComplexity() { return estimatedComplexity; }
        public void setEstimatedComplexity(String estimatedComplexity) { this.estimatedComplexity = estimatedComplexity; }
        public String getEstimatedEffortWeeks() { return estimatedEffortWeeks; }
        public void setEstimatedEffortWeeks(String estimatedEffortWeeks) { this.estimatedEffortWeeks = estimatedEffortWeeks; }
    }

    @JsonInclude(JsonInclude.Include.NON_NULL)
    public static class Phase {
        private int phaseNumber;
        private String name;
        private String description;
        private List<String> tasks = new ArrayList<>();
        private String estimatedWeeks;
        private String priority; // "critical", "high", "medium", "low"

        public int getPhaseNumber() { return phaseNumber; }
        public void setPhaseNumber(int phaseNumber) { this.phaseNumber = phaseNumber; }
        public String getName() { return name; }
        public void setName(String name) { this.name = name; }
        public String getDescription() { return description; }
        public void setDescription(String description) { this.description = description; }
        public List<String> getTasks() { return tasks; }
        public void setTasks(List<String> tasks) { this.tasks = tasks; }
        public String getEstimatedWeeks() { return estimatedWeeks; }
        public void setEstimatedWeeks(String estimatedWeeks) { this.estimatedWeeks = estimatedWeeks; }
        public String getPriority() { return priority; }
        public void setPriority(String priority) { this.priority = priority; }
    }

    @JsonInclude(JsonInclude.Include.NON_NULL)
    public static class ArchitectureSuggestion {
        private String backendFramework;   // "Spring Boot 3.x"
        private String frontendFramework;  // "Angular 17+"
        private String databaseStrategy;   // "JPA/Hibernate com flyway"
        private String authStrategy;       // "Spring Security + JWT"
        private List<String> suggestedDependencies = new ArrayList<>();
        private String projectStructure;

        public String getBackendFramework() { return backendFramework; }
        public void setBackendFramework(String backendFramework) { this.backendFramework = backendFramework; }
        public String getFrontendFramework() { return frontendFramework; }
        public void setFrontendFramework(String frontendFramework) { this.frontendFramework = frontendFramework; }
        public String getDatabaseStrategy() { return databaseStrategy; }
        public void setDatabaseStrategy(String databaseStrategy) { this.databaseStrategy = databaseStrategy; }
        public String getAuthStrategy() { return authStrategy; }
        public void setAuthStrategy(String authStrategy) { this.authStrategy = authStrategy; }
        public List<String> getSuggestedDependencies() { return suggestedDependencies; }
        public void setSuggestedDependencies(List<String> suggestedDependencies) { this.suggestedDependencies = suggestedDependencies; }
        public String getProjectStructure() { return projectStructure; }
        public void setProjectStructure(String projectStructure) { this.projectStructure = projectStructure; }
    }

    public String getProjectName() { return projectName; }
    public void setProjectName(String projectName) { this.projectName = projectName; }
    public String getAnalysisDate() { return analysisDate; }
    public void setAnalysisDate(String analysisDate) { this.analysisDate = analysisDate; }
    public Summary getSummary() { return summary; }
    public void setSummary(Summary summary) { this.summary = summary; }
    public List<Phase> getPhases() { return phases; }
    public void setPhases(List<Phase> phases) { this.phases = phases; }
    public List<String> getRisks() { return risks; }
    public void setRisks(List<String> risks) { this.risks = risks; }
    public List<String> getRecommendations() { return recommendations; }
    public void setRecommendations(List<String> recommendations) { this.recommendations = recommendations; }
    public ArchitectureSuggestion getArchitectureSuggestion() { return architectureSuggestion; }
    public void setArchitectureSuggestion(ArchitectureSuggestion architectureSuggestion) { this.architectureSuggestion = architectureSuggestion; }
}
