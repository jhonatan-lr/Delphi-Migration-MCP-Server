package com.migration.mcp.generator;

import com.migration.mcp.model.*;
import com.migration.mcp.parser.DelphiSourceParser;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.DisplayName;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

@DisplayName("JavaCodeGenerator — Testes unitarios")
class JavaCodeGeneratorTest {

    private JavaCodeGenerator generator;
    private DelphiSourceParser parser;

    private static final String PRODUCT_UNIT = """
            unit uProduto;
            interface
            uses SysUtils, Classes;
            type
              TProduto = class(TObject)
              private
                FId: Integer;
                FNome: String;
                FPreco: Currency;
                FEstoque: Integer;
                FAtivo: Boolean;
              public
                procedure Salvar;
                function Buscar(AId: Integer): TProduto;
              end;
            implementation
            procedure TProduto.Salvar;
            begin
              if FNome = '' then
                raise Exception.Create('Nome do produto obrigatorio');
              if FPreco <= 0 then
                ShowMessage('Preco deve ser maior que zero');
            end;
            end.
            """;

    @BeforeEach
    void setUp() {
        generator = new JavaCodeGenerator();
        parser    = new DelphiSourceParser();
    }

    @Test
    @DisplayName("Deve gerar @Entity JPA com anotacoes corretas (Java 8 / javax)")
    void testEntityGeneration() {
        DelphiUnit unit = parser.parse(PRODUCT_UNIT, "uProduto.pas");
        assertFalse(unit.getClasses().isEmpty());

        DelphiClass dc = unit.getClasses().get(0);
        String entity = generator.generateEntity(dc, "com.empresa.erp");

        assertTrue(entity.contains("@Entity"),              "Deve ter @Entity");
        assertTrue(entity.contains("@Table"),               "Deve ter @Table");
        assertTrue(entity.contains("@Id"),                  "Deve ter @Id");
        assertTrue(entity.contains("@GeneratedValue"),      "Deve ter @GeneratedValue");
        assertTrue(entity.contains("@Column"),              "Deve ter @Column");
        assertTrue(entity.contains("com.empresa.erp.entity"), "Deve ter package correto");
        assertTrue(entity.contains("javax.persistence"),    "Deve usar javax (Java 8)");
        assertTrue(entity.contains("ProdutoEntity"),        "Deve ter sufixo Entity");
        assertTrue(entity.contains("getId"),                "Deve ter getter manual");
        assertTrue(entity.contains("setId"),                "Deve ter setter manual");
    }

    @Test
    @DisplayName("Deve mapear Currency para BigDecimal na Entity")
    void testCurrencyMapping() {
        DelphiUnit unit = parser.parse(PRODUCT_UNIT, "uProduto.pas");
        DelphiClass dc = unit.getClasses().get(0);
        String entity = generator.generateEntity(dc, "com.empresa.erp");
        assertTrue(entity.contains("BigDecimal"), "Currency deve virar BigDecimal");
    }

    @Test
    @DisplayName("Deve gerar JpaRepository com JpaSpecificationExecutor")
    void testRepositoryGeneration() {
        DelphiUnit unit = parser.parse(PRODUCT_UNIT, "uProduto.pas");
        DelphiClass dc = unit.getClasses().get(0);
        String repo = generator.generateRepository(dc, "com.empresa.erp");

        assertTrue(repo.contains("@Repository"),                "Deve ter @Repository");
        assertTrue(repo.contains("JpaRepository"),              "Deve estender JpaRepository");
        assertTrue(repo.contains("JpaSpecificationExecutor"),   "Deve ter Specification support");
        assertTrue(repo.contains("Integer"),                    "ID deve ser Integer");
        assertTrue(repo.contains("com.empresa.erp.repository"), "Deve ter package correto");
    }

    @Test
    @DisplayName("Deve gerar @Service com padrao Logus API")
    void testServiceGeneration() {
        DelphiUnit unit = parser.parse(PRODUCT_UNIT, "uProduto.pas");
        DelphiClass dc = unit.getClasses().get(0);
        String service = generator.generateService(dc, "com.empresa.erp",
                unit.getSqlQueries(), unit.getBusinessRules());

        assertTrue(service.contains("@Service"),            "Deve ter @Service");
        assertTrue(service.contains("@Transactional"),      "Deve ter @Transactional");
        assertTrue(service.contains("@Autowired"),          "Deve usar @Autowired field injection");
        assertTrue(service.contains("findAll"),             "Deve ter findAll");
        assertTrue(service.contains("findById"),            "Deve ter findById");
        assertTrue(service.contains("save"),                "Deve ter save");
        assertTrue(service.contains("deleteById"),          "Deve ter deleteById");
        assertTrue(service.contains("pesquisar"),           "Deve ter pesquisar (padrao Logus)");
        assertTrue(service.contains("ResultDto"),           "Deve usar ResultDto wrapper");
    }

    @Test
    @DisplayName("Deve gerar Resource com try/catch padrao Logus")
    void testControllerGeneration() {
        DelphiUnit unit = parser.parse(PRODUCT_UNIT, "uProduto.pas");
        DelphiClass dc = unit.getClasses().get(0);
        String controller = generator.generateController(dc, "com.empresa.erp");

        assertTrue(controller.contains("@RestController"),  "Deve ter @RestController");
        assertTrue(controller.contains("@RequestMapping"),  "Deve ter @RequestMapping");
        assertTrue(controller.contains("@GetMapping"),      "Deve ter @GetMapping");
        assertTrue(controller.contains("@PostMapping"),     "Deve ter @PostMapping");
        assertTrue(controller.contains("@DeleteMapping"),   "Deve ter @DeleteMapping");
        assertTrue(controller.contains("@CrossOrigin"),     "Deve ter CORS configurado");
        assertTrue(controller.contains("ResponseEntity"),   "Deve retornar ResponseEntity");
        assertTrue(controller.contains("Resource"),         "Deve ser Resource (nao Controller)");
        assertTrue(controller.contains("@Autowired"),       "Deve usar @Autowired");
        assertTrue(controller.contains("ValidationException"), "Deve tratar ValidationException");
        assertTrue(controller.contains("HttpStatus.CONFLICT"), "Deve retornar 409 em validacao");
        assertTrue(controller.contains("@ApiOperation"),    "Deve ter Swagger 2");
    }

    @Test
    @DisplayName("Deve usar package correto em todos os artefatos")
    void testPackageConsistency() {
        String pkg = "br.com.minhaempresa.sistema";
        DelphiUnit unit = parser.parse(PRODUCT_UNIT, "uProduto.pas");
        DelphiClass dc = unit.getClasses().get(0);

        assertTrue(generator.generateEntity(dc, pkg).contains(pkg + ".entity"));
        assertTrue(generator.generateRepository(dc, pkg).contains(pkg + ".repository"));
        assertTrue(generator.generateService(dc, pkg, List.of(), List.of()).contains(pkg + ".service"));
        assertTrue(generator.generateController(dc, pkg).contains(pkg + ".resource"));
    }
}
