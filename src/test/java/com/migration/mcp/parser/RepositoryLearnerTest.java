package com.migration.mcp.parser;

import com.migration.mcp.model.ProjectProfile;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.*;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

@DisplayName("RepositoryLearner — Testes unitários")
class RepositoryLearnerTest {

    private RepositoryLearner learner;

    @TempDir
    Path tempRepo;

    @BeforeEach
    void setUp() {
        learner = new RepositoryLearner();
    }

    private void writeFile(String relativePath, String content) throws IOException {
        Path file = tempRepo.resolve(relativePath);
        Files.createDirectories(file.getParent());
        Files.writeString(file, content);
    }

    @Test
    @DisplayName("Deve detectar tecnologia FireDAC")
    void testFireDacDetection() throws IOException {
        writeFile("vendas/uVendas.pas", """
                unit uVendas;
                interface
                uses FireDAC.Comp.Client, FireDAC.Stan.Intf;
                type TdmVendas = class(TDataModule)
                  qryPedidos: TFDQuery;
                  conPrincipal: TFDConnection;
                end;
                implementation
                end.
                """);

        ProjectProfile profile = learner.learn(tempRepo.toString());
        assertEquals("FireDAC", profile.getDbTechnology());
    }

    @Test
    @DisplayName("Deve detectar tecnologia BDE")
    void testBdeDetection() throws IOException {
        writeFile("src/uCliente.pas", """
                unit uCliente;
                interface
                uses DB, DBTables;
                type TfrmCliente = class(TForm)
                  tblClientes: TTable;
                  qryBusca: TQuery;
                end;
                implementation
                end.
                """);

        ProjectProfile profile = learner.learn(tempRepo.toString());
        assertEquals("BDE", profile.getDbTechnology());
    }

    @Test
    @DisplayName("Deve detectar vendor SQL Server")
    void testSqlServerDetection() throws IOException {
        writeFile("src/uConexao.pas", """
                unit uConexao;
                interface
                const
                  CONN_STRING = 'Provider=SQLOLEDB.1;Server=localhost;Database=ERP;';
                implementation
                end.
                """);

        ProjectProfile profile = learner.learn(tempRepo.toString());
        assertEquals("SQL Server", profile.getDbVendor());
    }

    @Test
    @DisplayName("Deve detectar prefixo de form 'frm'")
    void testFormPrefixDetection() throws IOException {
        // Cria vários arquivos com prefixo frm
        for (String name : List.of("frmCliente", "frmPedido", "frmProduto", "frmEstoque")) {
            writeFile("forms/" + name + ".pas", "unit " + name + "; interface type T" + name + " = class(TForm) end; implementation end.");
        }

        ProjectProfile profile = learner.learn(tempRepo.toString());
        assertEquals("frm", profile.getNaming().getFormPrefix());
    }

    @Test
    @DisplayName("Deve detectar prefixo de query 'qry'")
    void testQueryPrefixDetection() throws IOException {
        writeFile("src/uPedido.pas", """
                unit uPedido;
                interface
                type TfrmPedido = class(TForm)
                  qryPedidos: TFDQuery;
                  qryItens: TFDQuery;
                  qryClientes: TFDQuery;
                  qryProdutos: TFDQuery;
                end;
                implementation
                end.
                """);

        ProjectProfile profile = learner.learn(tempRepo.toString());
        assertEquals("qry", profile.getNaming().getQueryPrefix());
    }

    @Test
    @DisplayName("Deve detectar SQL dinâmico como risco")
    void testDynamicSqlDetection() throws IOException {
        writeFile("src/uRelatorio.pas", """
                unit uRelatorio;
                interface
                implementation
                procedure Buscar(AFiltro: String);
                begin
                  qry.SQL.Add('SELECT * FROM PEDIDOS WHERE 1=1 ' + AFiltro);
                  qry.Open;
                end;
                end.
                """);

        ProjectProfile profile = learner.learn(tempRepo.toString());
        assertTrue(profile.getSqlConventions().isUsesDynamicSql(),
                "Deve detectar SQL dinâmico");
    }

    @Test
    @DisplayName("Deve detectar estilo de validação ShowMessage")
    void testValidationStyleDetection() throws IOException {
        writeFile("src/uForm.pas", """
                unit uForm;
                interface
                implementation
                procedure Validar;
                begin
                  if edNome.Text = '' then begin ShowMessage('Nome obrigatório!'); Exit; end;
                  if edCPF.Text = '' then begin ShowMessage('CPF obrigatório!'); Exit; end;
                  if edEmail.Text = '' then begin ShowMessage('Email obrigatório!'); Exit; end;
                end;
                end.
                """);

        ProjectProfile profile = learner.learn(tempRepo.toString());
        assertEquals("showmessage", profile.getCodePatterns().getValidationStyle());
    }

    @Test
    @DisplayName("Deve detectar módulos por pasta")
    void testModuleDetection() throws IOException {
        for (String mod : List.of("vendas", "financeiro", "estoque")) {
            for (int i = 1; i <= 3; i++) {
                writeFile(mod + "/uUnit" + i + ".pas",
                        "unit uUnit" + i + "; interface type TC" + i + " = class(TObject) end; implementation end.");
            }
        }

        ProjectProfile profile = learner.learn(tempRepo.toString());
        assertTrue(profile.getModules().size() >= 3, "Deve detectar 3 módulos");
    }

    @Test
    @DisplayName("Deve extrair tabelas mais usadas")
    void testTableExtraction() throws IOException {
        writeFile("src/uPedido.pas", """
                unit uPedido;
                interface
                implementation
                procedure Carregar;
                begin
                  qry.SQL.Text := 'SELECT * FROM PEDIDOS P INNER JOIN CLIENTES C ON C.ID = P.ID_CLIENTE';
                  qry2.SQL.Text := 'SELECT * FROM PEDIDO_ITENS I WHERE I.ID_PEDIDO = :ID';
                end;
                end.
                """);

        ProjectProfile profile = learner.learn(tempRepo.toString());
        assertFalse(profile.getDetectedTables().isEmpty(), "Deve extrair tabelas");
    }

    @Test
    @DisplayName("Deve persistir e retornar o perfil via Store")
    void testProfilePersistence() throws IOException {
        writeFile("src/uApp.pas",
                "unit uApp; interface type TfrmPrincipal = class(TForm) end; implementation end.");

        ProjectProfile profile = learner.learn(tempRepo.toString());

        com.migration.mcp.model.ProjectProfileStore store =
                com.migration.mcp.model.ProjectProfileStore.getInstance();

        // Salva o perfil existente para restaurar depois (evita sobrescrever perfil real)
        ProjectProfile previousProfile = store.get();
        try {
            store.save(profile);

            assertTrue(store.hasProfile());
            assertNotNull(store.get());
            assertEquals(profile.getProjectName(), store.get().getProjectName());
        } finally {
            // Restaura o perfil anterior (ou limpa se não havia)
            if (previousProfile != null) {
                store.save(previousProfile);
            } else {
                store.clear();
            }
        }
    }
}
