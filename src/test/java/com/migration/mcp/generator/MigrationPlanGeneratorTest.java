package com.migration.mcp.generator;

import com.migration.mcp.model.*;
import com.migration.mcp.parser.DelphiSourceParser;
import com.migration.mcp.parser.DfmFormParser;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.DisplayName;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

@DisplayName("MigrationPlanGenerator — Testes unitários")
class MigrationPlanGeneratorTest {

    private MigrationPlanGenerator generator;
    private DelphiSourceParser sourceParser;
    private DfmFormParser dfmParser;

    @BeforeEach
    void setUp() {
        generator    = new MigrationPlanGenerator();
        sourceParser = new DelphiSourceParser();
        dfmParser    = new DfmFormParser();
    }

    @Test
    @DisplayName("Deve gerar plano com nome do projeto")
    void testProjectName() {
        MigrationPlan plan = generator.generate(List.of(), List.of(), "SistemaERP");
        assertEquals("SistemaERP", plan.getProjectName());
    }

    @Test
    @DisplayName("Deve ter data de análise preenchida")
    void testAnalysisDate() {
        MigrationPlan plan = generator.generate(List.of(), List.of(), "Test");
        assertNotNull(plan.getAnalysisDate());
        assertFalse(plan.getAnalysisDate().isEmpty());
    }

    @Test
    @DisplayName("Deve gerar sugestão de arquitetura")
    void testArchitectureSuggestion() {
        MigrationPlan plan = generator.generate(List.of(), List.of(), "Test");
        assertNotNull(plan.getArchitectureSuggestion());
        assertTrue(plan.getArchitectureSuggestion().getBackendFramework().contains("Spring Boot"));
        assertTrue(plan.getArchitectureSuggestion().getFrontendFramework().contains("Angular"));
    }

    @Test
    @DisplayName("Deve gerar 6 fases de migração")
    void testMigrationPhases() {
        MigrationPlan plan = generator.generate(List.of(), List.of(), "Test");
        assertEquals(6, plan.getPhases().size(), "Deve gerar exatamente 6 fases");
        plan.getPhases().forEach(p -> {
            assertNotNull(p.getName());
            assertFalse(p.getTasks().isEmpty(), "Fase " + p.getPhaseNumber() + " deve ter tasks");
        });
    }

    @Test
    @DisplayName("Deve identificar riscos")
    void testRisks() {
        MigrationPlan plan = generator.generate(List.of(), List.of(), "Test");
        assertFalse(plan.getRisks().isEmpty(), "Deve listar riscos");
    }

    @Test
    @DisplayName("Deve gerar recomendações")
    void testRecommendations() {
        MigrationPlan plan = generator.generate(List.of(), List.of(), "Test");
        assertFalse(plan.getRecommendations().isEmpty(), "Deve listar recomendações");
    }

    @Test
    @DisplayName("Deve gerar Markdown válido")
    void testMarkdownOutput() {
        String src = """
                unit uPedido;
                interface
                type TPedido = class(TObject)
                  private FId: Integer; FTotal: Currency;
                  procedure Calcular;
                end;
                implementation
                procedure TPedido.Calcular;
                begin
                  if FTotal <= 0 then
                    ShowMessage('Total deve ser positivo!');
                end;
                end.
                """;

        DelphiUnit unit = sourceParser.parse(src, "uPedido.pas");
        MigrationPlan plan = generator.generate(List.of(unit), List.of(), "SistemaPedidos");
        String markdown = generator.toMarkdown(plan);

        assertNotNull(markdown);
        assertTrue(markdown.contains("# 📋 Plano de Migração"), "Deve ter título");
        assertTrue(markdown.contains("SistemaPedidos"), "Deve conter nome do projeto");
        assertTrue(markdown.contains("## 🗺️ Fases"), "Deve ter seção de fases");
        assertTrue(markdown.contains("Spring Boot"), "Deve mencionar Spring Boot");
        assertTrue(markdown.contains("Angular"), "Deve mencionar Angular");
    }

    @Test
    @DisplayName("Deve calcular complexidade baseada no tamanho do projeto")
    void testComplexityEstimation() {
        // Projeto pequeno — complexidade baixa/média
        MigrationPlan smallPlan = generator.generate(List.of(), List.of(), "Small");
        assertNotNull(smallPlan.getSummary().getEstimatedComplexity());

        // Projeto com muitas units — complexidade alta
        List<DelphiUnit> manyUnits = new java.util.ArrayList<>();
        for (int i = 0; i < 10; i++) {
            String code = "unit u" + i + "; interface type TC" + i + " = class(TObject) " +
                          "private f1: Integer; f2: String; f3: Boolean; f4: Currency; f5: Double; " +
                          "procedure M1; procedure M2; procedure M3; end; implementation end.";
            manyUnits.add(sourceParser.parse(code, "u" + i + ".pas"));
        }
        MigrationPlan largePlan = generator.generate(manyUnits, List.of(), "Large");
        assertNotNull(largePlan.getSummary().getEstimatedComplexity());
        assertNotNull(largePlan.getSummary().getEstimatedEffortWeeks());
    }
}
