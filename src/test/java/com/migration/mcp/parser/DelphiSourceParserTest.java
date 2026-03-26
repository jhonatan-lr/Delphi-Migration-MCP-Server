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
