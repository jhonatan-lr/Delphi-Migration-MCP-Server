# Delphi Migration MCP Server

MCP Server especialista em **analisar codigo-fonte Delphi**, **aprender os padroes do seu projeto** e **gerar codigo Java (Spring Boot 2.x) + Angular (PrimeNG)** fiel as convencoes existentes.

Gera codigo no padrao exato dos projetos **logus-corporativo-api** (Java 8 / Spring Boot 2.1.2) e **logus-corporativo-web** (Angular 10 / PrimeNG 11).

---

## Ferramentas disponiveis (13 tools)

### Aprendizado de repositorio
| Tool | Descricao |
|------|-----------|
| `learn_repository` | **Varre o projeto inteiro** e aprende todos os padroes -- execute uma vez |
| `get_learned_profile` | Mostra o que foi aprendido (nomenclatura, BD, modulos, SQL...) |
| `clear_learned_profile` | Esquece o perfil aprendido (para trocar de projeto) |

### Analise
| Tool | Descricao |
|------|-----------|
| `analyze_delphi_unit` | Analisa arquivo `.pas` -- extrai classes, metodos, SQL, regras |
| `analyze_dfm_form` | Analisa arquivo `.dfm` -- mapeia componentes para PrimeNG |
| `extract_sql_queries` | Extrai todas as queries SQL com sugestao JPA |
| `extract_business_rules` | Extrai validacoes e regras de negocio com codigo Java sugerido |
| `analyze_delphi_project` | Varre um diretorio inteiro e cria inventario completo |
| `detect_inconsistencies` | **Compara um arquivo com o perfil** -- detecta desvios de padrao (HIGH/MEDIUM/LOW) |

### Geracao de codigo
| Tool | Descricao |
|------|-----------|
| `generate_migration_plan` | Plano completo personalizado para o projeto (JSON + Markdown) |
| `generate_java_class` | **7 arquivos**: Entity, Repository, Service, Resource, Dto, PesquisaDto, GridVo |
| `generate_angular_component` | **17 arquivos**: Module, Routing, Container, Grid, Filtros, Cadastro, Service, HttpService, Models |
| `generate_full_module` | **24 arquivos** de uma vez (7 Java + 17 Angular) com opcao de salvar em disco (`output_dir`) |

---

## Build e instalacao

### Pre-requisitos
- Java 17+ (para compilar o servidor MCP -- o codigo gerado e Java 8)
- Maven 3.8+

### Build

```bash
cd delphi-migration-mcp
JAVA_HOME="C:/Program Files/Java/jdk-17" mvn clean package -DskipTests
```

Gera `target/delphi-migration-mcp-1.0.0.jar` (fat JAR com todas as dependencias).

**Nota:** Se o `JAVA_HOME` aponta para JDK 8 (padrao em maquinas com Logus), sempre setar explicitamente para JDK 17 no build.

---

## Configuracao

### Claude Code CLI (`.mcp.json` na raiz do projeto)

```json
{
  "mcpServers": {
    "delphi-migration": {
      "type": "stdio",
      "command": "C:\\Program Files\\Java\\jdk-17\\bin\\java.exe",
      "args": [
        "-Xmx1024m",
        "-Dfile.encoding=UTF-8",
        "-jar",
        "C:\\caminho\\para\\delphi-migration-mcp-1.0.0.jar"
      ]
    }
  }
}
```

### VS Code (`%APPDATA%\Code\User\mcp.json`)

```json
{
  "servers": {
    "delphi-migration": {
      "type": "stdio",
      "command": "C:\\Program Files\\Java\\jdk-17\\bin\\java.exe",
      "args": [
        "-Xmx1024m",
        "-Dfile.encoding=UTF-8",
        "-jar",
        "C:\\caminho\\para\\delphi-migration-mcp-1.0.0.jar"
      ]
    }
  }
}
```

**IMPORTANTE:** Usar o caminho absoluto do JDK 17 (`java.exe`), nao apenas `java`, pois o `JAVA_HOME` pode apontar para JDK 8. Heap minimo recomendado: `-Xmx1024m` para repositorios grandes (5000+ arquivos).
```

---

## Manual de instrucoes

### Passo 1 -- Ensinar o repositorio (uma vez)

Diga ao Claude:

> "Execute learn_repository no diretorio C:\meu-projeto-delphi"

O servidor varre todos os `.pas` e `.dfm` e aprende automaticamente:

- Prefixos de arquivo (`f_`, `d_`, `u_`, `r_`, `s_`, `o_`)
- Tecnologia de BD (FireDAC / ADO / BDE / dbExpress / IBX)
- Vendor do banco (SQL Server / Informix / Firebird / Oracle...)
- Versao do Delphi
- Bibliotecas de terceiros (DevExpress, QuickReport, InfoPower, ACBr, JEDI...)
- Modulos do projeto
- Convencoes de SQL
- Padroes de validacao

O perfil e **persistido** em `~/.delphi-mcp/project-profile.json` e sobrevive a reinicios.
Para verificar o que foi aprendido, use `get_learned_profile`.

### Passo 2 -- Analisar um arquivo especifico

> "Analise o arquivo C:\projeto\f_Pedido.pas"

Retorna: classes, metodos, campos, queries SQL extraidas, regras de negocio.

> "Analise o arquivo C:\projeto\f_Pedido.dfm"

Retorna: componentes visuais, mapeamento para PrimeNG, eventos detectados.

### Passo 3 -- Detectar inconsistencias

> "Use detect_inconsistencies no arquivo f_Pedido.pas"

Compara o arquivo com o perfil aprendido e retorna:

```
HIGH   | sql       | SQL dinamico detectado -- projeto usa parametrizado
MEDIUM | component | TRVReport nao encontrado em outros forms
LOW    | naming    | Query 'Query1' nao usa prefixo 'qry'
```

### Passo 4 -- Gerar o plano de migracao

> "Gere o plano de migracao para o projeto SistemaERP"

Retorna um documento estruturado com:
- Arquitetura sugerida com driver JDBC correto para o banco detectado
- Estrutura de pastas Angular com os modulos reais do projeto
- Fases de migracao com tasks especificas por modulo
- Riscos identificados
- Recomendacoes

### Passo 5 -- Gerar codigo Java (API)

> "Gere o codigo Java para a unit u_Cliente.pas. Package: logus.corporativo.api"

Gera **7 arquivos** no padrao da API:

| Arquivo | Descricao |
|---------|-----------|
| `ClienteEntity.java` | `@Entity` com `javax.persistence`, getters/setters manuais, `Serializable` |
| `ClienteRepository.java` | `JpaRepository` + `JpaSpecificationExecutor`, `@Transactional(readOnly=true)` |
| `ClienteService.java` | `@Autowired` field injection, `pesquisar()` com `ResultDto<GridVo>`, `save()`, `delete()` |
| `ClienteResource.java` | `@RestController` com try/catch padrao: `ValidationException`->409, `Exception`->500 |
| `ClienteDto.java` | DTO com constructor de Entity, campo `isEdicao` |
| `PesquisaClienteDto.java` | Filtros de pesquisa com `LazyLoadDto` |
| `ClienteGridVo.java` | View Object para grid com constructor de Entity |

**Padrao da API gerada:**
- Java 8 (`javax.persistence`, nao `jakarta`)
- Spring Boot 2.1.2
- `@Autowired` field injection (nao constructor injection)
- Endpoints: `POST /pesquisar`, `POST /save`, `GET /getById/{id}`, `DELETE /delete/{id}`
- Swagger 2 (`@ApiOperation`, `@ApiParam`)
- Entity com `Integer` como tipo de ID
- Error handling: `ValidationException` -> 409, `Exception` -> 500

### Passo 6 -- Gerar codigo Angular (Web)

> "Gere o componente Angular para o form f_Pedido.dfm com o pas f_Pedido.pas"

Gera **17 arquivos** no padrao do web app:

| Arquivo | Descricao |
|---------|-----------|
| `pedido.module.ts` | NgModule com lazy loading |
| `pedido.routing.ts` | RouterModule.forChild com ContainerComponent |
| `pedido.model.ts` | Interface TypeScript com campos do .pas |
| `pesquisa-pedido.model.ts` | Interface de filtros com LazyLoadDto |
| `pedido.service.ts` | BehaviorSubject state store (grid$, selecionado$, alterarEditar$) |
| `http-pedido.service.ts` | HttpClient: POST /pesquisar, POST /save, GET /getById, DELETE /delete |
| `pedido-container.component.ts` | ChangeDetection.OnPush, error handling via ErroDispacher |
| `pedido-container.component.html` | `app-componente-basico` + toggle lista/cadastro |
| `pedido-grid.component.ts` | DataGridColunasModel + DataGridItem com callbacks |
| `pedido-grid.component.html` | `app-data-grid` + `app-button` Novo + `app-botoes-exportar` |
| `pedido-grid.module.ts` | DataGridModule, ButtonModule, BotoesExportarModule |
| `pedido-filtros.component.ts` | FormBuilder com handlePesquisar() |
| `pedido-filtros.component.html` | `app-filtro` wrapper + pInputText (PrimeNG) |
| `pedido-filtros.module.ts` | FiltroModule, InputTextModule, DropdownModule |
| `pedido-cadastro.component.ts` | Reactive form, isEdicao, patchValue, getRawValue |
| `pedido-cadastro.component.html` | pInputText, p-checkbox, app-button Salvar/Voltar |
| `pedido-cadastro.module.ts` | ButtonModule, ValidationMessageModule, CurrencyMaskModule |

**Padrao do Angular gerado:**
- Angular 10 com PrimeNG 11 (nao Angular Material)
- Padrao Container / Grid / Filtros / Cadastro
- Service como state store com BehaviorSubject
- HTTP service separado em shared/services/http/
- Models como interfaces TypeScript
- ChangeDetectionStrategy.OnPush
- Shared components: DataGrid, Filtro, Button, ComponenteBasico
- Lazy loading via dynamic import no routing

### Passo 7 -- Analisar projeto inteiro

> "Analise todo o projeto Delphi em C:\meu-projeto-delphi"

Retorna inventario completo: total de arquivos, classes, queries, regras de negocio, complexidade estimada.

---

## Tecnologias suportadas

### Camadas de acesso a dados
| Tecnologia | Componentes detectados |
|------------|----------------------|
| **FireDAC** | TFDConnection, TFDQuery, TFDTable, TFDStoredProc |
| **dbExpress (DBX)** | TSQLConnection, TSQLQuery, TSQLDataSet |
| **BDE** | TDatabase, TQuery, TTable, TBatchMove |
| **ADO** | TADOConnection, TADOQuery, TADOTable |
| **IBX** | TIBDatabase, TIBQuery, TIBTransaction |
| **ZeosLib** | TZConnection, TZQuery, TZTable |
| **UniDAC** | TUniConnection, TUniQuery |
| **ClientDataSet** | TClientDataSet, TDataSetProvider |

### Vendors de banco
| Vendor | Driver JDBC sugerido |
|--------|---------------------|
| **SQL Server** | com.microsoft.sqlserver:mssql-jdbc |
| **Informix** | com.ibm.informix:jdbc + InformixDialect |
| **Firebird** | org.firebirdsql:jaybird |
| **Oracle** | com.oracle.database.jdbc:ojdbc11 |
| **MySQL** | com.mysql:mysql-connector-j |
| **PostgreSQL** | org.postgresql:postgresql |

### Bibliotecas de terceiros detectadas
| Biblioteca | Detectado por |
|-----------|---------------|
| DevExpress | TcxGrid, TdxGrid, TcxDBGrid |
| FastReport | TfrxReport, TfrxDBDataset |
| QuickReport | TQRBand, TQuickRep |
| Rave Reports | TRvReport, TRvSystem |
| TMS Components | TMS, AdvGrid, TAdvEdit |
| ACBr | TACBrNFe, TACBrBoleto |
| JEDI/JVCL | JvValidateEdit, TJvEdit |
| InfoPower | TwwDBGrid, Wwdbigrd |
| Logus Components | LgBitBtn, LgCorporativo |
| NFe/Sefaz | nfdNFe, ComunicadorSefaz |
| Indy | TIdHTTP, TIdTCPClient |

### Prefixos de arquivo detectados
| Prefixo | Significado | Exemplo |
|---------|-------------|---------|
| `f_` / `frm` | Form (tela) | f_Pedido.pas, frmCliente.pas |
| `d_` / `dm` | DataModule | d_Logus.pas, dmConexao.pas |
| `u_` / `u` | Unit (logica) | u_Conexao.pas, uCliente.pas |
| `r_` / `rpt` | Report | r_histpadrao.pas |
| `s_` | Selection screen | s_AnaliseComportamento.pas |
| `o_` | Business object | o_pedido.pas |

---

## Parsing de SQL

O parser reconhece multiplos padroes de SQL em codigo Delphi:

**SQL.Text := (atribuicao direta)**
```delphi
qryClientes.SQL.Text := 'SELECT * FROM CLIENTES WHERE ATIVO = 1';
```

**SQL.Add() (blocos multi-linha -- linhas consecutivas sao unidas)**
```delphi
with vQry do begin
  SQL.Add('SELECT ecf.cdg_filial,');
  SQL.Add('       ecf.nmr_ecf,');
  SQL.Add('       frec.dcr_frec');
  SQL.Add('  FROM cadecf ecf');
  SQL.Add('  JOIN cadfrec frec ON (...)');
  ParamByName('dcr_usuario').AsString := vUsuario;
  Open;
end;
```

Para cada query extraida, o servidor sugere:
- Tipo (SELECT/INSERT/UPDATE/DELETE/STORED_PROC)
- Tabelas utilizadas
- Equivalente JPQL/@Query
- Metodo Spring Data Repository

---

## Mapeamento de tipos Delphi -> Java

| Delphi | Java (API) | TypeScript (Angular) |
|--------|------------|---------------------|
| Integer, SmallInt, Byte | `Integer` | `number` |
| Int64 | `Integer` | `number` |
| String, AnsiString | `String` | `string` |
| Boolean | `Boolean` | `boolean` |
| Double, Extended, Real | `BigDecimal` | `number` |
| Currency | `BigDecimal` | `number` |
| TDateTime, TDate | `Date` | `string` |
| Variant | `String` | `string` |

---

## Estrutura do projeto

```
delphi-migration-mcp/
├── start.sh / start.bat
├── claude_desktop_config.example.json
├── pom.xml
└── src/
    ├── main/java/com/migration/mcp/
    │   ├── server/   -> Entry point (MCP SDK 0.9.0 + STDIO)
    │   ├── tools/    -> 12 MCP Tools
    │   ├── parser/
    │   │   ├── DelphiSourceParser.java       <- Parser .pas (SQL.Add block)
    │   │   ├── DfmFormParser.java            <- Parser .dfm (70+ component mappings)
    │   │   └── RepositoryLearner.java        <- Varredura do repositorio
    │   ├── generator/
    │   │   ├── JavaCodeGenerator.java        <- 7 arquivos Java (padrao Logus API)
    │   │   ├── AngularCodeGenerator.java     <- 17 arquivos Angular (padrao Logus Web)
    │   │   └── MigrationPlanGenerator.java   <- Plano de migracao (7 DB vendors)
    │   └── model/
    │       ├── ProjectProfile.java           <- Perfil completo do projeto
    │       ├── ProjectProfileStore.java      <- Singleton + persistencia
    │       └── ... (9 outros models)
    └── test/
        └── ... (6 suites, 51 testes)
```

---

## Testes

```bash
mvn test
```

| Suite | O que testa |
|-------|-------------|
| `FullIntegrationTest` | Fluxo completo: learn -> analyze -> generate -> plan |
| `RepositoryLearnerTest` | Deteccao de BD, nomenclatura, SQL, modulos |
| `DelphiSourceParserTest` | Parsing de units, SQL, tipos, regras |
| `DfmFormParserTest` | Parsing de forms, mapeamento componentes |
| `MigrationPlanGeneratorTest` | Plano, Markdown, complexidade |
| `JavaCodeGeneratorTest` | Entity, Repository, Service, Resource |

---

## Notas tecnicas

- **STDIO only** -- logs vao para `stderr` e `logs/delphi-mcp.log`
- **Perfil persistido** -- `~/.delphi-mcp/project-profile.json` (caminho fixo, mesmo para CLI e VS Code)
- **DFM binario** -- converta antes: Delphi IDE -> "View as Text"
- **Codigo gerado e ponto de partida** -- revisao manual sempre necessaria
- **MCP SDK 0.9.0** -- `io.modelcontextprotocol.server.*` / `io.modelcontextprotocol.spec.McpSchema.*`
- **Java 17 para compilar** o servidor MCP, mas o **codigo gerado e Java 8** (javax.persistence)
- **Angular 10** com PrimeNG 11 -- nao usa Angular Material
- **Charset fallback** -- tenta UTF-8 primeiro, cai para ISO-8859-1 (arquivos Delphi legados)
- **Tools aceitam file_path** -- todas as tools leem direto do disco se `content` estiver vazio
- **Heap recomendado** -- `-Xmx1024m` para repos com 5000+ arquivos (prevencao de OOM)
- **Testado com** -- repositorio Logus ERP (5411 arquivos, telas de 21k linhas, 240 metodos)
