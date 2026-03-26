package com.migration.mcp.parser;

import com.migration.mcp.model.DfmForm;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.DisplayName;

import static org.junit.jupiter.api.Assertions.*;

@DisplayName("DfmFormParser — Testes unitários")
class DfmFormParserTest {

    private DfmFormParser parser;

    private static final String SAMPLE_DFM = """
            object frmCliente: TfrmCliente
              Left = 0
              Top = 0
              Caption = 'Cadastro de Clientes'
              ClientHeight = 480
              ClientWidth = 640
              Color = clBtnFace
              object edNome: TEdit
                Left = 120
                Top = 24
                Width = 300
                Height = 23
                Caption = 'Nome'
                TabOrder = 0
                OnChange = edNomeChange
                OnExit = edNomeExit
              end
              object edCPF: TMaskEdit
                Left = 120
                Top = 56
                Width = 150
                Height = 23
                Caption = 'CPF'
                TabOrder = 1
              end
              object cmbStatus: TComboBox
                Left = 120
                Top = 88
                Width = 200
                Height = 23
                Caption = 'Status'
                TabOrder = 2
              end
              object grdClientes: TDBGrid
                Left = 8
                Top = 200
                Width = 600
                Height = 200
                TabOrder = 3
              end
              object btnSalvar: TButton
                Left = 450
                Top = 430
                Width = 80
                Height = 35
                Caption = 'Salvar'
                TabOrder = 4
                OnClick = btnSalvarClick
              end
              object btnCancelar: TButton
                Left = 540
                Top = 430
                Width = 80
                Height = 35
                Caption = 'Cancelar'
                TabOrder = 5
                OnClick = btnCancelarClick
              end
            end
            """;

    @BeforeEach
    void setUp() {
        parser = new DfmFormParser();
    }

    @Test
    @DisplayName("Deve extrair nome do form")
    void testFormName() {
        DfmForm form = parser.parse(SAMPLE_DFM);
        assertEquals("frmCliente", form.getFormName());
    }

    @Test
    @DisplayName("Deve extrair caption do form")
    void testCaption() {
        DfmForm form = parser.parse(SAMPLE_DFM);
        assertEquals("Cadastro de Clientes", form.getCaption());
    }

    @Test
    @DisplayName("Deve extrair componentes do form")
    void testComponentExtraction() {
        DfmForm form = parser.parse(SAMPLE_DFM);
        assertFalse(form.getComponents().isEmpty(), "Deve encontrar componentes");
        assertTrue(form.getComponents().size() >= 5, "Deve encontrar pelo menos 5 componentes");
    }

    @Test
    @DisplayName("Deve mapear TEdit para Angular Material")
    void testEditMapping() {
        DfmForm form = parser.parse(SAMPLE_DFM);
        form.getComponents().stream()
                .filter(c -> "TEdit".equals(c.getDelphiType()))
                .findFirst()
                .ifPresent(c -> {
                    assertNotNull(c.getAngularEquivalent());
                    assertTrue(c.getAngularEquivalent().contains("matInput"),
                            "TEdit deve mapear para matInput");
                });
    }

    @Test
    @DisplayName("Deve mapear TDBGrid para mat-table")
    void testGridMapping() {
        DfmForm form = parser.parse(SAMPLE_DFM);
        form.getComponents().stream()
                .filter(c -> "TDBGrid".equals(c.getDelphiType()))
                .findFirst()
                .ifPresent(c -> {
                    assertNotNull(c.getAngularEquivalent());
                    assertTrue(c.getAngularEquivalent().contains("mat-table"),
                            "TDBGrid deve mapear para mat-table");
                });
    }

    @Test
    @DisplayName("Deve sugerir rota Angular baseada no nome do form")
    void testAngularRoute() {
        DfmForm form = parser.parse(SAMPLE_DFM);
        assertNotNull(form.getSuggestedRoute(), "Deve sugerir uma rota Angular");
        assertTrue(form.getSuggestedRoute().startsWith("/"), "Rota deve começar com /");
    }

    @Test
    @DisplayName("Deve gerar template Angular HTML")
    void testAngularTemplateGeneration() {
        DfmForm form = parser.parse(SAMPLE_DFM);
        assertNotNull(form.getAngularTemplate(), "Deve gerar template HTML");
        assertTrue(form.getAngularTemplate().contains("mat-card"), "Template deve usar mat-card");
        assertTrue(form.getAngularTemplate().contains("formGroup"), "Template deve ter formGroup");
        assertTrue(form.getAngularTemplate().contains("mat-table"), "Template deve ter mat-table para grid");
    }

    @Test
    @DisplayName("Deve gerar TypeScript do componente Angular")
    void testAngularComponentTsGeneration() {
        DfmForm form = parser.parse(SAMPLE_DFM);
        assertNotNull(form.getAngularComponentTs(), "Deve gerar TypeScript");
        assertTrue(form.getAngularComponentTs().contains("FormBuilder"), "TS deve usar FormBuilder");
        assertTrue(form.getAngularComponentTs().contains("implements OnInit"), "TS deve implementar OnInit");
        assertTrue(form.getAngularComponentTs().contains("onSubmit"), "TS deve ter método onSubmit");
    }

    @Test
    @DisplayName("Deve detectar eventos dos componentes")
    void testEventDetection() {
        DfmForm form = parser.parse(SAMPLE_DFM);
        boolean hasEvents = form.getComponents().stream()
                .anyMatch(c -> !c.getEvents().isEmpty());
        assertTrue(hasEvents, "Deve detectar eventos nos componentes");
    }
}
