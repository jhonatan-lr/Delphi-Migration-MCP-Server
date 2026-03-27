package com.migration.mcp.parser;

import com.migration.mcp.model.*;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.DisplayName;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

@DisplayName("DelphiSourceParser — Testes unitários")
class DelphiSourceParserTest {

    private DelphiSourceParser parser;

    private static final String SAMPLE_UNIT = """
            unit uClienteForm;
            
            interface
            
            uses
              Windows, Messages, SysUtils, Classes, Graphics, Controls, Forms,
              Dialogs, StdCtrls, DBGrids, DB, ADODB, ComCtrls;
            
            type
              TfrmCliente = class(TForm)
                edNome: TEdit;
                edCPF: TMaskEdit;
                cmbStatus: TComboBox;
                grdClientes: TDBGrid;
                btnSalvar: TButton;
                btnCancelar: TButton;
                qryClientes: TADOQuery;
                dsClientes: TDataSource;
                procedure btnSalvarClick(Sender: TObject);
                procedure btnCancelarClick(Sender: TObject);
                procedure FormCreate(Sender: TObject);
              private
                FClienteId: Integer;
                FNomeCliente: String;
                procedure ValidarCampos;
                procedure CarregarClientes;
              public
                procedure AbrirParaEdicao(AId: Integer);
              end;
            
            implementation
            
            procedure TfrmCliente.FormCreate(Sender: TObject);
            begin
              CarregarClientes;
            end;
            
            procedure TfrmCliente.btnSalvarClick(Sender: TObject);
            begin
              if edNome.Text = '' then
              begin
                ShowMessage('Nome do cliente é obrigatório!');
                Exit;
              end;
              if Length(edCPF.Text) < 11 then
              begin
                ShowMessage('CPF inválido. Informe 11 dígitos.');
                Exit;
              end;
              qryClientes.SQL.Text := 'INSERT INTO CLIENTES (NOME, CPF, STATUS) VALUES (:NOME, :CPF, :STATUS)';
              qryClientes.Parameters.ParamByName('NOME').Value := edNome.Text;
              qryClientes.Parameters.ParamByName('CPF').Value := edCPF.Text;
              qryClientes.Parameters.ParamByName('STATUS').Value := cmbStatus.Text;
              qryClientes.ExecSQL;
              ShowMessage('Cliente salvo com sucesso!');
            end;
            
            procedure TfrmCliente.CarregarClientes;
            begin
              qryClientes.Close;
              qryClientes.SQL.Text := 'SELECT ID, NOME, CPF, STATUS, DATA_CADASTRO FROM CLIENTES WHERE ATIVO = 1 ORDER BY NOME';
              qryClientes.Open;
            end;
            
            procedure TfrmCliente.ValidarCampos;
            begin
              if FClienteId <= 0 then
                raise Exception.Create('ID de cliente inválido');
            end;
            
            end.
            """;

    @BeforeEach
    void setUp() {
        parser = new DelphiSourceParser();
    }

    @Test
    @DisplayName("Deve extrair nome da unit corretamente")
    void testUnitName() {
        DelphiUnit unit = parser.parse(SAMPLE_UNIT, "uClienteForm.pas");
        assertEquals("uClienteForm", unit.getUnitName());
    }

    @Test
    @DisplayName("Deve detectar tipo da unit como 'form'")
    void testUnitType() {
        DelphiUnit unit = parser.parse(SAMPLE_UNIT, "uClienteForm.pas");
        assertEquals("form", unit.getUnitType());
    }

    @Test
    @DisplayName("Deve extrair uses clause")
    void testUsesList() {
        DelphiUnit unit = parser.parse(SAMPLE_UNIT, "uClienteForm.pas");
        assertTrue(unit.getUses().size() > 0);
        assertTrue(unit.getUses().contains("SysUtils"));
        assertTrue(unit.getUses().contains("Classes"));
    }

    @Test
    @DisplayName("Deve extrair a classe TfrmCliente")
    void testClassExtraction() {
        DelphiUnit unit = parser.parse(SAMPLE_UNIT, "uClienteForm.pas");
        assertFalse(unit.getClasses().isEmpty(), "Deve encontrar pelo menos uma classe");

        boolean foundCliente = unit.getClasses().stream()
                .anyMatch(c -> "TfrmCliente".equals(c.getName()));
        assertTrue(foundCliente, "Deve encontrar a classe TfrmCliente");
    }

    @Test
    @DisplayName("Deve detectar herança TForm e sugerir migração Angular")
    void testClassMigrationSuggestion() {
        DelphiUnit unit = parser.parse(SAMPLE_UNIT, "uClienteForm.pas");
        unit.getClasses().stream()
                .filter(c -> "TfrmCliente".equals(c.getName()))
                .findFirst()
                .ifPresent(c -> {
                    assertEquals("TForm", c.getClassType());
                    assertNotNull(c.getMigrationSuggestion());
                    assertTrue(c.getMigrationSuggestion().toLowerCase().contains("angular"),
                            "Deve sugerir migração para Angular");
                });
    }

    @Test
    @DisplayName("Deve extrair queries SQL")
    void testSqlExtraction() {
        List<SqlQuery> queries = parser.extractSqlQueries(SAMPLE_UNIT);
        assertTrue(queries.size() >= 2, "Deve encontrar pelo menos 2 queries SQL");

        boolean hasSelect = queries.stream().anyMatch(q -> "SELECT".equals(q.getQueryType()));
        boolean hasInsert = queries.stream().anyMatch(q -> "INSERT".equals(q.getQueryType()));
        assertTrue(hasSelect, "Deve encontrar SELECT");
        assertTrue(hasInsert, "Deve encontrar INSERT");
    }

    @Test
    @DisplayName("Deve extrair tabela CLIENTES das queries")
    void testSqlTableExtraction() {
        List<SqlQuery> queries = parser.extractSqlQueries(SAMPLE_UNIT);
        boolean hasClientesTable = queries.stream()
                .anyMatch(q -> q.getTablesUsed() != null &&
                               q.getTablesUsed().stream().anyMatch(t -> t.equalsIgnoreCase("CLIENTES")));
        assertTrue(hasClientesTable, "Deve identificar a tabela CLIENTES");
    }

    @Test
    @DisplayName("Deve extrair regras de negócio (validações)")
    void testBusinessRulesExtraction() {
        List<BusinessRule> rules = parser.extractBusinessRules(SAMPLE_UNIT);
        assertFalse(rules.isEmpty(), "Deve encontrar pelo menos uma regra de negócio");

        boolean hasValidation = rules.stream()
                .anyMatch(r -> "validation".equals(r.getRuleType()));
        assertTrue(hasValidation, "Deve encontrar validações");
    }

    @Test
    @DisplayName("Deve gerar sugestão JPA para SELECT")
    void testJpaSuggestion() {
        List<SqlQuery> queries = parser.extractSqlQueries(SAMPLE_UNIT);
        queries.stream()
                .filter(q -> "SELECT".equals(q.getQueryType()))
                .findFirst()
                .ifPresent(q -> {
                    assertNotNull(q.getJpaEquivalent(), "Deve sugerir equivalente JPA");
                    assertTrue(q.getJpaEquivalent().contains("@Query") ||
                               q.getJpaEquivalent().contains("findBy"),
                            "Sugestão JPA deve conter @Query ou findBy");
                });
    }

    // ── Form Initialization Tests ─────────────────────────────────────────

    @Test
    @DisplayName("Deve detectar AUTO_LOAD no FormCreate do SAMPLE_UNIT")
    void testFormInitAutoLoad() {
        List<FormInitialization> inits = parser.extractFormInitialization(SAMPLE_UNIT);
        assertFalse(inits.isEmpty(), "Deve encontrar inicialização no FormCreate");

        boolean hasAutoLoad = inits.stream()
                .flatMap(i -> i.getAutoLoads().stream())
                .anyMatch(al -> al.getMethod().contains("CarregarClientes"));
        assertTrue(hasAutoLoad, "Deve detectar CarregarClientes como AUTO_LOAD");
    }

    private static final String FORMSHOW_SAMPLE = """
            unit f_MonitorPedido;

            interface
            uses Windows, SysUtils, Classes;

            type
              TfrmMonitorPedido = class(TForm)
                edtDataEmissaoDe: TJvDateEdit;
                edtDataEmissaoAte: TJvDateEdit;
                lucFilial: TLgCorporativoLookupComboEdit;
                edtNomeFornecedorDisplay: TEdit;
                lucSituacaoPedido: TLgCheckListCombo;
                procedure FormShow(Sender: TObject);
              end;

            implementation

            procedure TfrmMonitorPedido.FormShow(Sender: TObject);
            var
              i: Integer;
            begin
              TLogusWinControl.DisableComponent(edtNomeFornecedorDisplay);
              edtDataEmissaoDe.Date := Conexao.Date;
              edtDataEmissaoAte.Date := Conexao.Date;
              if (Buf_Filial.Cdg_Filial <> 0) then
              begin
                lucFilial.KeyValue := Buf_Filial.Cdg_Filial;
                TLogusWinControl.DisableComponent(lucFilial);
              end;
              with lucSituacaoPedido do
              begin
                for i := 1 to (Items.Count - 1) do
                begin
                  if ((Items.Items[i].Key = '1') or (Items.Items[i].Key = '2')) then
                  begin
                    Items.Items[i].Selected := True;
                  end;
                end;
              end;
              CarregarListaPedidoAutomatico;
            end;

            end.
            """;

    @Test
    @DisplayName("Deve detectar DEFAULT_VALUE de datas no FormShow")
    void testFormShowDefaultValues() {
        List<FormInitialization> inits = parser.extractFormInitialization(FORMSHOW_SAMPLE);
        assertFalse(inits.isEmpty(), "Deve encontrar FormShow");

        FormInitialization formShow = inits.stream()
                .filter(i -> "FormShow".equals(i.getContext()))
                .findFirst().orElseThrow();

        assertTrue(formShow.getDefaultValues().size() >= 2,
                "Deve detectar pelo menos 2 default values (datas)");

        boolean hasDataDe = formShow.getDefaultValues().stream()
                .anyMatch(dv -> "edtDataEmissaoDe".equals(dv.getComponent()) && "Date".equals(dv.getProperty()));
        assertTrue(hasDataDe, "Deve detectar edtDataEmissaoDe.Date := Conexao.Date");
    }

    @Test
    @DisplayName("Deve detectar CONDITIONAL_DEFAULT com filial")
    void testFormShowConditionalDefault() {
        List<FormInitialization> inits = parser.extractFormInitialization(FORMSHOW_SAMPLE);
        FormInitialization formShow = inits.stream()
                .filter(i -> "FormShow".equals(i.getContext()))
                .findFirst().orElseThrow();

        assertFalse(formShow.getConditionalDefaults().isEmpty(),
                "Deve detectar conditional defaults");

        boolean hasFilial = formShow.getConditionalDefaults().stream()
                .anyMatch(cd -> "lucFilial".equals(cd.getComponent()) && cd.isDisabled());
        assertTrue(hasFilial, "Deve detectar lucFilial com disabled=true dentro de if Buf_Filial");
    }

    @Test
    @DisplayName("Deve detectar COMBO_PRESELECTION com keys 1 e 2")
    void testFormShowComboPreselection() {
        List<FormInitialization> inits = parser.extractFormInitialization(FORMSHOW_SAMPLE);
        FormInitialization formShow = inits.stream()
                .filter(i -> "FormShow".equals(i.getContext()))
                .findFirst().orElseThrow();

        assertFalse(formShow.getComboPreselections().isEmpty(),
                "Deve detectar combo preselections");

        boolean hasKeys = formShow.getComboPreselections().stream()
                .anyMatch(cp -> cp.getSelectedKeys() != null &&
                               cp.getSelectedKeys().contains("1") &&
                               cp.getSelectedKeys().contains("2"));
        assertTrue(hasKeys, "Deve detectar keys '1' e '2' pré-selecionadas");
    }

    @Test
    @DisplayName("Deve detectar AUTO_LOAD CarregarListaPedidoAutomatico")
    void testFormShowAutoLoad() {
        List<FormInitialization> inits = parser.extractFormInitialization(FORMSHOW_SAMPLE);
        FormInitialization formShow = inits.stream()
                .filter(i -> "FormShow".equals(i.getContext()))
                .findFirst().orElseThrow();

        boolean hasCarregar = formShow.getAutoLoads().stream()
                .anyMatch(al -> "CarregarListaPedidoAutomatico".equals(al.getMethod()));
        assertTrue(hasCarregar, "Deve detectar CarregarListaPedidoAutomatico como AUTO_LOAD");
    }

    @Test
    @DisplayName("Deve detectar INITIAL_STATE DisableComponent(edtNomeFornecedorDisplay)")
    void testFormShowInitialState() {
        List<FormInitialization> inits = parser.extractFormInitialization(FORMSHOW_SAMPLE);
        FormInitialization formShow = inits.stream()
                .filter(i -> "FormShow".equals(i.getContext()))
                .findFirst().orElseThrow();

        boolean hasDisabled = formShow.getInitialStates().stream()
                .anyMatch(is -> "edtNomeFornecedorDisplay".equals(is.getComponent()) &&
                               "disabled".equals(is.getState()));
        assertTrue(hasDisabled, "Deve detectar edtNomeFornecedorDisplay disabled");
    }

    @Test
    @DisplayName("totalDetected deve somar todas as categorias")
    void testTotalDetected() {
        List<FormInitialization> inits = parser.extractFormInitialization(FORMSHOW_SAMPLE);
        FormInitialization formShow = inits.stream()
                .filter(i -> "FormShow".equals(i.getContext()))
                .findFirst().orElseThrow();

        assertTrue(formShow.totalDetected() >= 5,
                "totalDetected deve ser >= 5 (2 defaults + 1 conditional + 1 combo + 1 autoload + 1 state). Got: " + formShow.totalDetected());
    }

    // ── Type Mapping Tests ──────────────────────────────────────────────────

    @Test
    @DisplayName("Deve mapear tipos Delphi para Java")
    void testTypeMapping() {
        String unitWithTypes = """
                unit uTipos;
                interface
                type
                  TEntidade = class(TObject)
                  private
                    FId: Integer;
                    FNome: String;
                    FAtivo: Boolean;
                    FValor: Currency;
                    FDataCriacao: TDateTime;
                  end;
                implementation
                end.
                """;
        DelphiUnit unit = parser.parse(unitWithTypes, "uTipos.pas");
        unit.getClasses().stream()
                .filter(c -> "TEntidade".equals(c.getName()))
                .flatMap(c -> c.getFields().stream())
                .forEach(f -> {
                    assertNotNull(f.getJavaType(), "Todo campo deve ter tipo Java mapeado");
                    switch (f.getName()) {
                        case "FId"          -> assertEquals("Integer", f.getJavaType());
                        case "FNome"        -> assertEquals("String", f.getJavaType());
                        case "FAtivo"       -> assertEquals("Boolean", f.getJavaType());
                        case "FValor"       -> assertEquals("BigDecimal", f.getJavaType());
                        case "FDataCriacao" -> assertEquals("LocalDateTime", f.getJavaType());
                    }
                });
    }
}
