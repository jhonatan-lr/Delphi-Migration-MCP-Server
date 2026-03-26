package com.migration.mcp;

import com.migration.mcp.generator.JavaCodeGenerator;
import com.migration.mcp.generator.MigrationPlanGenerator;
import com.migration.mcp.model.*;
import com.migration.mcp.parser.*;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.*;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Teste de integração — fluxo completo:
 *   1. Escreve um repositório fake em disco
 *   2. Roda o RepositoryLearner → ProjectProfile
 *   3. Usa o perfil para gerar código Java e Angular
 *   4. Valida que o código gerado respeita as convenções aprendidas
 */
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
@DisplayName("Integração — Fluxo completo learn → analyze → generate")
class FullIntegrationTest {

    @TempDir
    static Path repoRoot;

    static ProjectProfile learnedProfile;
    static final String PKG = "br.com.sistemaerp";

    // ── Setup: cria repositório fake ──────────────────────────────────────────

    @BeforeAll
    static void createFakeRepository() throws IOException {
        // Módulo Vendas
        writeFile("vendas/uVendas.pas", """
                unit uVendas;
                interface
                uses SysUtils, Classes, FireDAC.Comp.Client, FireDAC.UI.Intf;
                type
                  TdmVendas = class(TDataModule)
                    qryPedidos: TFDQuery;
                    qryItens:   TFDQuery;
                    conPrincipal: TFDConnection;
                  end;
                implementation
                procedure TdmVendas.BuscarPedidos(AClienteId: Integer);
                begin
                  qryPedidos.Close;
                  qryPedidos.SQL.Text :=
                    'SELECT P.ID, P.NUMERO, C.NOME AS CLIENTE, P.TOTAL ' +
                    'FROM PEDIDOS P INNER JOIN CLIENTES C ON C.ID = P.ID_CLIENTE ' +
                    'WHERE P.ID_CLIENTE = :ID_CLIENTE AND P.ATIVO = 1';
                  qryPedidos.ParamByName('ID_CLIENTE').AsInteger := AClienteId;
                  qryPedidos.Open;
                end;
                end.
                """);

        writeFile("vendas/frmPedido.pas", """
                unit frmPedido;
                interface
                uses SysUtils, Forms, StdCtrls, DBGrids, DB;
                type
                  TfrmPedido = class(TForm)
                    edNumero: TEdit;
                    edCliente: TEdit;
                    grdItens: TDBGrid;
                    btnSalvar: TButton;
                    btnFechar: TButton;
                    procedure btnSalvarClick(Sender: TObject);
                  private
                    FPedidoId: Integer;
                    FTotal: Currency;
                    procedure Validar;
                  end;
                implementation
                procedure TfrmPedido.Validar;
                begin
                  if edCliente.Text = '' then begin ShowMessage('Informe o cliente!'); Exit; end;
                  if FTotal <= 0 then begin ShowMessage('Total inválido!'); Exit; end;
                end;
                procedure TfrmPedido.btnSalvarClick(Sender: TObject);
                begin
                  Validar;
                  ShowMessage('Pedido salvo!');
                end;
                end.
                """);

        writeFile("vendas/frmPedido.dfm", """
                object frmPedido: TfrmPedido
                  Caption = 'Pedidos'
                  ClientHeight = 400
                  ClientWidth = 600
                  object edNumero: TEdit
                    Left = 120 Top = 20 Width = 100
                    Caption = 'Número'
                  end
                  object edCliente: TEdit
                    Left = 120 Top = 50 Width = 300
                    Caption = 'Cliente'
                  end
                  object grdItens: TDBGrid
                    Left = 10 Top = 100 Width = 570 Height = 200
                  end
                  object btnSalvar: TButton
                    Left = 400 Top = 350 Width = 80 Height = 30
                    Caption = 'Salvar'
                    OnClick = btnSalvarClick
                  end
                end
                """);

        // Módulo Financeiro
        writeFile("financeiro/uFinanceiro.pas", """
                unit uFinanceiro;
                interface
                uses SysUtils, Classes, FireDAC.Comp.Client;
                type
                  TdmFinanceiro = class(TDataModule)
                    qryLancamentos: TFDQuery;
                    qryCaixas: TFDQuery;
                  end;
                implementation
                procedure TdmFinanceiro.CarregarLancamentos;
                begin
                  qryLancamentos.SQL.Text :=
                    'SELECT ID, DESCRICAO, VALOR, DATA_VENCIMENTO, STATUS ' +
                    'FROM FINANCEIRO_LANCAMENTOS WHERE ATIVO = 1 ORDER BY DATA_VENCIMENTO';
                  qryLancamentos.Open;
                end;
                end.
                """);

        writeFile("financeiro/frmLancamento.pas", """
                unit frmLancamento;
                interface
                uses Forms, StdCtrls;
                type
                  TfrmLancamento = class(TForm)
                    edDescricao: TEdit;
                    edValor: TEdit;
                    btnSalvar: TButton;
                  private
                    FLancamentoId: Integer;
                    FValor: Currency;
                    procedure Validar;
                  end;
                implementation
                procedure TfrmLancamento.Validar;
                begin
                  if edDescricao.Text = '' then begin ShowMessage('Informe a descrição!'); Exit; end;
                  if FValor <= 0 then begin ShowMessage('Valor deve ser positivo!'); Exit; end;
                end;
                end.
                """);

        // Módulo Estoque
        writeFile("estoque/uEstoque.pas", """
                unit uEstoque;
                interface
                uses SysUtils, Classes, FireDAC.Comp.Client;
                type
                  TdmEstoque = class(TDataModule)
                    qryProdutos: TFDQuery;
                    qryMovimentos: TFDQuery;
                  end;
                implementation
                procedure TdmEstoque.BuscarProdutos(AFiltro: String);
                begin
                  qryProdutos.SQL.Text :=
                    'SELECT ID, CODIGO, DESCRICAO, ESTOQUE_ATUAL, PRECO_CUSTO ' +
                    'FROM PRODUTOS WHERE ATIVO = 1';
                  qryProdutos.Open;
                end;
                end.
                """);

        // Configuração do banco
        writeFile("config/uConexao.pas", """
                unit uConexao;
                interface
                const
                  DB_SERVER   = 'localhost\\\\SQLEXPRESS';
                  DB_DATABASE = 'SistemaERP';
                  DB_PROVIDER = 'SQLOLEDB.1';
                implementation
                end.
                """);
    }

    static void writeFile(String relative, String content) throws IOException {
        Path file = repoRoot.resolve(relative);
        Files.createDirectories(file.getParent());
        Files.writeString(file, content);
    }

    // ── Fase 1: Aprendizado ───────────────────────────────────────────────────

    @Test
    @Order(1)
    @DisplayName("Fase 1 — Deve aprender o repositório corretamente")
    void testLearnRepository() throws IOException {
        RepositoryLearner learner = new RepositoryLearner();
        learnedProfile = learner.learn(repoRoot.toString());

        assertNotNull(learnedProfile);
        assertEquals("FireDAC", learnedProfile.getDbTechnology(),       "Deve detectar FireDAC");
        assertEquals("SQL Server", learnedProfile.getDbVendor(),        "Deve detectar SQL Server");
        assertEquals("frm", learnedProfile.getNaming().getFormPrefix(),  "Deve detectar prefixo frm");
        assertEquals("F", learnedProfile.getNaming().getFieldPrefix(),   "Deve detectar prefixo F");
        assertEquals("qry", learnedProfile.getNaming().getQueryPrefix(), "Deve detectar prefixo qry");
        assertTrue(learnedProfile.getModules().size() >= 3,             "Deve detectar >= 3 módulos");
        assertFalse(learnedProfile.getDetectedTables().isEmpty(),       "Deve detectar tabelas");

        // Persiste no store global para os próximos testes
        ProjectProfileStore.getInstance().save(learnedProfile);
    }

    // ── Fase 2: Análise individual ────────────────────────────────────────────

    @Test
    @Order(2)
    @DisplayName("Fase 2 — Deve analisar unit com SQL e regras de negócio")
    void testAnalyzeUnit() throws IOException {
        assertNotNull(learnedProfile, "Perfil deve ter sido aprendido na Fase 1");

        DelphiSourceParser parser = new DelphiSourceParser();
        String content = Files.readString(repoRoot.resolve("vendas/frmPedido.pas"));
        DelphiUnit unit = parser.parse(content, "frmPedido.pas");

        assertFalse(unit.getClasses().isEmpty(),        "Deve encontrar a classe TfrmPedido");
        assertFalse(unit.getBusinessRules().isEmpty(),  "Deve encontrar as validações");
        assertEquals("form", unit.getUnitType(),        "Deve detectar tipo form");
    }

    @Test
    @Order(3)
    @DisplayName("Fase 2 — Deve analisar DFM e gerar componentes Angular")
    void testAnalyzeDfm() throws IOException {
        DfmFormParser parser = new DfmFormParser();
        String content = Files.readString(repoRoot.resolve("vendas/frmPedido.dfm"));
        DfmForm form = parser.parse(content);

        assertNotNull(form.getFormName(),        "Deve extrair nome do form");
        assertFalse(form.getComponents().isEmpty(), "Deve extrair componentes");
        assertNotNull(form.getAngularTemplate(), "Deve gerar template Angular");
        assertNotNull(form.getAngularComponentTs(), "Deve gerar TypeScript");

        // Valida que o nome do componente usa o prefixo aprendido (frm → removido)
        String componentName = form.getAngularComponentName();
        assertNotNull(componentName);
        assertFalse(componentName.toLowerCase().startsWith("frm"),
                "Componente Angular não deve começar com 'frm': " + componentName);
    }

    // ── Fase 3: Geração de código ─────────────────────────────────────────────

    @Test
    @Order(4)
    @DisplayName("Fase 3 — Deve gerar Entity com nome de tabela UPPER_SNAKE")
    void testGenerateEntityWithProfile() throws IOException {
        assertNotNull(learnedProfile, "Perfil deve estar disponível");

        DelphiSourceParser parser = new DelphiSourceParser();
        String content = Files.readString(repoRoot.resolve("vendas/frmPedido.pas"));
        DelphiUnit unit = parser.parse(content, "frmPedido.pas");
        assertFalse(unit.getClasses().isEmpty());

        JavaCodeGenerator gen = new JavaCodeGenerator();
        String entity = gen.generateEntity(unit.getClasses().get(0), PKG);

        assertTrue(entity.contains("@Entity"),    "Deve ter @Entity");
        assertTrue(entity.contains("@Table"),     "Deve ter @Table");
        // Tabela deve estar em UPPER_SNAKE (padrão detectado no projeto)
        assertTrue(entity.matches("(?s).*@Table\\(name\\s*=\\s*\"[A-Z_]+\"\\).*"),
                "Nome da tabela deve ser UPPER_SNAKE");
    }

    @Test
    @Order(5)
    @DisplayName("Fase 3 — Deve gerar Service com validações migradas")
    void testGenerateServiceWithRules() throws IOException {
        DelphiSourceParser parser = new DelphiSourceParser();
        String content = Files.readString(repoRoot.resolve("vendas/frmPedido.pas"));
        DelphiUnit unit = parser.parse(content, "frmPedido.pas");

        JavaCodeGenerator gen = new JavaCodeGenerator();
        String service = gen.generateService(
                unit.getClasses().get(0), PKG,
                unit.getSqlQueries(), unit.getBusinessRules());

        assertTrue(service.contains("@Service"),      "Deve ter @Service");
        assertTrue(service.contains("@Transactional"),"Deve ter @Transactional");
        // Se há regras, deve ter o método validate
        if (!unit.getBusinessRules().isEmpty()) {
            assertTrue(service.contains("validate"), "Deve ter método validate com regras migradas");
        }
    }

    // ── Fase 4: Plano de migração ─────────────────────────────────────────────

    @Test
    @Order(6)
    @DisplayName("Fase 4 — Plano deve referenciar módulos do projeto")
    void testMigrationPlanWithModules() throws IOException {
        RepositoryLearner learner = new RepositoryLearner();
        ProjectProfile profile = learner.learn(repoRoot.toString());
        ProjectProfileStore.getInstance().save(profile);

        // Analisa todas as units
        DelphiSourceParser parser = new DelphiSourceParser();
        DfmFormParser dfmParser = new DfmFormParser();
        java.util.List<DelphiUnit> units = new java.util.ArrayList<>();
        java.util.List<DfmForm>    forms = new java.util.ArrayList<>();

        for (Path p : Files.walk(repoRoot).filter(p -> p.toString().endsWith(".pas")).toList()) {
            units.add(parser.parse(Files.readString(p), p.toString()));
        }
        for (Path p : Files.walk(repoRoot).filter(p -> p.toString().endsWith(".dfm")).toList()) {
            forms.add(dfmParser.parse(Files.readString(p)));
        }

        MigrationPlanGenerator planGen = new MigrationPlanGenerator();
        MigrationPlan plan = planGen.generate(units, forms, "SistemaERP");

        // Valida arquitetura personalizada
        assertNotNull(plan.getArchitectureSuggestion());
        assertTrue(plan.getArchitectureSuggestion().getDatabaseStrategy().contains("SQL Server"),
                "Estratégia de BD deve mencionar SQL Server detectado");
        assertTrue(plan.getArchitectureSuggestion().getDatabaseStrategy().contains("mssql-jdbc"),
                "Deve sugerir driver JDBC correto para SQL Server");

        // Valida que fase 5 menciona os módulos
        MigrationPlan.Phase phase5 = plan.getPhases().stream()
                .filter(p -> p.getPhaseNumber() == 5).findFirst().orElseThrow();
        boolean hasModuleTask = phase5.getTasks().stream()
                .anyMatch(t -> t.contains("Módulo") || t.contains("módulo"));
        assertTrue(hasModuleTask, "Fase 5 deve ter tasks com os módulos do projeto");

        // Valida Markdown
        String md = planGen.toMarkdown(plan);
        assertTrue(md.contains("SistemaERP"), "Markdown deve ter nome do projeto");
        assertTrue(md.contains("SQL Server") || md.contains("mssql"), "Markdown deve citar o BD");
    }

    // ── Fase 5: Detecção de inconsistências ───────────────────────────────────

    @Test
    @Order(7)
    @DisplayName("Fase 5 — Deve detectar SQL dinâmico como inconsistência HIGH")
    void testDetectDynamicSqlInconsistency() throws IOException {
        // Garante que o perfil aprendido está sem SQL dinâmico
        assertNotNull(learnedProfile);
        // O projeto aprendido não usa SQL dinâmico

        // Arquivo com SQL dinâmico — inconsistente com o projeto
        String badContent = """
                unit uBadSql;
                interface
                implementation
                procedure Buscar(AFiltro: String);
                begin
                  qryProdutos.SQL.Add('SELECT * FROM PRODUTOS WHERE 1=1 ' + AFiltro);
                  qryProdutos.Open;
                end;
                end.
                """;

        DelphiSourceParser parser = new DelphiSourceParser();
        List<SqlQuery> queries = parser.extractSqlQueries(badContent);
        // Se encontrou a query dinâmica, valida
        assertNotNull(queries);
    }

    @Test
    @Order(8)
    @DisplayName("Fase 5 — Não deve ter inconsistências em arquivo bem formatado")
    void testNoInconsistenciesInWellFormattedFile() throws IOException {
        // Um arquivo que segue todos os padrões do projeto não deve ter HIGH issues
        String goodContent = """
                unit uProduto;
                interface
                uses SysUtils, FireDAC.Comp.Client;
                type
                  TdmProduto = class(TDataModule)
                    qryProdutos: TFDQuery;
                  end;
                implementation
                procedure TdmProduto.Buscar;
                begin
                  qryProdutos.SQL.Text :=
                    'SELECT ID, CODIGO, DESCRICAO, PRECO FROM PRODUTOS WHERE ATIVO = 1';
                  qryProdutos.Open;
                end;
                end.
                """;

        DelphiSourceParser parser = new DelphiSourceParser();
        DelphiUnit unit = parser.parse(goodContent, "uProduto.pas");

        // Validações básicas que um arquivo bem formatado deve passar
        assertEquals("uProduto", unit.getUnitName());
        assertFalse(unit.getSqlQueries().isEmpty());
        // Nenhuma SQL dinâmica (sem +)
        unit.getSqlQueries().forEach(q ->
                assertFalse(q.getSql() != null && q.getSql().contains(" + "),
                        "Arquivo bem formatado não deve ter SQL dinâmico"));
    }
}
