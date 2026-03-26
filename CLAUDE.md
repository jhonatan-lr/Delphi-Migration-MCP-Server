# Delphi Migration MCP Server

## Build

Este projeto requer **JDK 17**. O `JAVA_HOME` da maquina aponta para JDK 8 (usado pelo Logus), entao sempre setar explicitamente:

```bash
JAVA_HOME="C:/Program Files/Java/jdk-17" mvn compile
JAVA_HOME="C:/Program Files/Java/jdk-17" mvn package -DskipTests
JAVA_HOME="C:/Program Files/Java/jdk-17" mvn test
```

Apos build, reiniciar o MCP com `/mcp` no Claude Code para carregar o jar novo.

## Stack

- Java 17, Maven (compilacao do MCP server)
- MCP SDK `io.modelcontextprotocol.sdk:mcp:0.9.0`
- Jackson para JSON, SLF4J + Logback para logs
- Sem Spring Boot — servidor MCP standalone via stdio

## Estrutura do codigo

```
src/main/java/com/migration/mcp/
  server/       -> Entry point (MCP SDK + STDIO)
  tools/        -> 12 MCP Tools (McpTools.java, DetectInconsistenciesTool.java, LearnRepositoryTool.java)
  parser/
    DelphiSourceParser.java   <- Parser .pas (metodos, SQL, regras de negocio)
    DfmFormParser.java        <- Parser .dfm (componentes visuais, mapeamento PrimeNG)
    RepositoryLearner.java    <- Varredura do repositorio (5400+ arquivos)
  generator/
    JavaCodeGenerator.java    <- 7 arquivos Java (padrao Logus API)
    AngularCodeGenerator.java <- 17 arquivos Angular (padrao Logus Web)
    MigrationPlanGenerator.java <- Plano de migracao
  model/
    ProjectProfile.java       <- Perfil completo do projeto aprendido
    ProjectProfileStore.java  <- Singleton + persistencia em disco
```

## Convencoes CRITICAS (nao violar)

### OOM Prevention
- `RepositoryLearner` NUNCA concatenar todos os conteudos em uma string (String.join) — usar helpers `anyMatch`, `countMatchesAll`, `collectMatches` que iteram arquivo por arquivo
- Perfil persistido em caminho fixo: `C:\Users\Usuario\.delphi-mcp\project-profile.json`
- `.mcp.json` usa `-Xmx1024m` para o heap

### Charset
- Arquivos Delphi legados usam ISO-8859-1, nao UTF-8
- SEMPRE usar `AnalyzeDelphiUnitTool.readFileWithFallback(path)` em vez de `Files.readString(path, UTF_8)` direto
- O fallback tenta UTF-8 primeiro, cai para ISO-8859-1 em `MalformedInputException`

### Static initialization order
- Em `ProjectProfileStore`: INSTANCE deve vir DEPOIS do static block que inicializa PROFILE_DIR/PROFILE_FILE
- Campos static sao inicializados na ordem de declaracao em Java

### Tools - content vs file_path
- Todas as tools aceitam `content` e `file_path`
- Se `content` estiver vazio/em branco (isBlank), ler do `file_path` automaticamente
- Padrao: `args.containsKey("content") && !requireString(args, "content").isBlank()`

## Projeto alvo: Logus ERP

O MCP foi construido para migrar o **Logus ERP** — sistema Delphi 7 com 5400+ arquivos.

### Padrao da API (logus-corporativo-api)
- Java 8 / Spring Boot 2.1.2
- Camadas: Resource (controller), Service, Repository, Entity
- DTOs: DTO (entrada), VO (saida grid), PesquisaDto (filtros com LazyLoadDto), ResultDto (paginacao)
- `@Autowired` field injection (nao constructor injection)
- Swagger 2 (`@ApiOperation`, `@ApiParam`, springfox)
- Endpoints: `POST /pesquisar`, `POST /save`, `GET /getById/{id}`, `DELETE /delete/{id}`
- Error handling: `ValidationException` -> 409, `Exception` -> 500

### Padrao do Web (logus-corporativo-web)
- Angular 10 / PrimeNG 11 (NAO Angular Material)
- Padrao Container / Grid / Filtros / Cadastro (4 sub-components por feature)
- Service como state store com BehaviorSubject (grid$, selecionado$, alterarEditar$)
- HTTP service separado em `shared/services/http/`
- Shared components: DataGrid, Filtro, Button, ComponenteBasico, BotoesExportar
- ChangeDetectionStrategy.OnPush no Container
- Lazy loading via RouterModule.forChild

### Mapeamento de componentes (DFM -> PrimeNG)
- `TLgCorporativoLookupComboEdit` -> `<p-dropdown [filter]='true' [showClear]='true'>`
- `TLgBitBtn` / `PngBitBtn` -> `<button pButton>`
- `TJvDateEdit` -> `<p-calendar dateFormat='dd/mm/yy'>`
- `TJvValidateEdit` -> `<input pInputText> + Validators`
- `TwwDBGrid` -> `<p-table>` com paginator (shared DataGrid)
- `TGroupBox` -> `<p-fieldset legend='...'>`
- `TEdit` -> `<input pInputText>`
- `TCheckBox` -> `<p-checkbox>`
- `TClientDataSet` -> `BehaviorSubject<T[]>` no Service
- `TPageControl` -> `<p-tabView>`

## Melhorias pendentes (backlog)

### Alto impacto
1. SQL extraction truncada — queries vem cortadas, precisa concatenar todos os SQL.Add corretamente
2. Campos da Entity vazios — extrair campos do TClientDataSet/TSQLQuery do .dfm para gerar @Column
3. Validacoes no Java com sintaxe Delphi — converter para Java real
4. TJvDBLookupCombo sem mapeamento PrimeNG

### Medio impacto
5. Filtros component vazio — preencher com campos do .dfm
6. Grid sem colunas — extrair do TwwDBGrid.Selected.Strings
7. Model/Cadastro sem campos — preencher com dados da analise
8. Decodificar captions Delphi (#231#227 -> cedilha)

### Baixo impacto
9. Eventos falsos (ont -> False, ons -> False) sao propriedades parseadas como eventos
10. Prefixo 'frm' nos nomes de arquivo Java — remover

## README.md

O README.md deve ser mantido atualizado quando houver mudancas significativas nas tools, mapeamentos ou padroes de geracao. Ele serve como documentacao publica do projeto.
