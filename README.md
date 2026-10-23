# FinTrack

Aplicação de gestão financeira pessoal em desenvolvimento, criada como projeto de portfólio para demonstrar desenvolvimento web, modelagem de dados e análise de informações financeiras.

O objetivo final é permitir que cada pessoa tenha sua conta, registre receitas e despesas, importe extratos CSV e acompanhe sua situação financeira por dashboards e relatórios. Atualmente, o projeto oferece o núcleo da API REST; usuários, interface e publicação serão desenvolvidos nas próximas etapas.

## Tecnologias

- Java 21 e Maven Wrapper.
- Spring Boot 4.1.1, Spring Web MVC e Jakarta Validation.
- Spring Data JPA / Hibernate e PostgreSQL.
- JUnit e Mockito nos testes; H2 em memória nos testes de integração.

## Funcionalidades disponíveis

- Cadastro, listagem, consulta, edição e exclusão de receitas e despesas.
- Cadastro, listagem, consulta, edição e exclusão de categorias.
- Associação de cada transação a uma categoria existente.
- Bloqueio da exclusão de categorias com transações vinculadas.
- Filtros combináveis por período, categoria e tipo (`RECEITA` ou `DESPESA`).
- Resumo financeiro com total de receitas, total de despesas e saldo por período.
- Validação de campos obrigatórios e valores positivos.
- Respostas 404 para categorias ou transações inexistentes e 409 para categoria em uso.

## Executar localmente

### Requisitos

- JDK 21 com `JAVA_HOME` configurado.
- PostgreSQL em execução e um banco chamado `fintrack`.
- Git e acesso à internet para o primeiro download do Maven e das dependências.

Não é necessário instalar Maven separadamente: o projeto inclui o Maven Wrapper.

### Preparar o projeto e o banco

```powershell
git clone https://github.com/jeffersonpmcs-dot/fintrack.git
cd fintrack
```

Crie o banco pelo pgAdmin ou execute no PostgreSQL, conectado a um banco administrativo como `postgres`:

```sql
CREATE DATABASE fintrack;
```

Se o banco já existir, utilize-o sem recriá-lo. Na configuração atual de desenvolvimento, o Hibernate cria ou atualiza as tabelas ao iniciar a aplicação (`spring.jpa.hibernate.ddl-auto=update`).

### Configurar a conexão e iniciar

A aplicação lê estas variáveis de ambiente:

| Variável | Exemplo / finalidade |
| --- | --- |
| `DB_URL` | `jdbc:postgresql://localhost:5432/fintrack` |
| `DB_USERNAME` | Usuário do PostgreSQL |
| `DB_PASSWORD` | Senha desse usuário |

No PowerShell, o exemplo abaixo solicita as credenciais em uma janela e configura as variáveis apenas para a sessão atual:

```powershell
$dbCredential = Get-Credential -UserName 'postgres' -Message 'Conexao local do FinTrack'
$env:DB_URL = 'jdbc:postgresql://localhost:5432/fintrack'
$env:DB_USERNAME = $dbCredential.UserName
$env:DB_PASSWORD = $dbCredential.GetNetworkCredential().Password

.\mvnw.cmd spring-boot:run
```

Use seu próprio usuário do banco. Também é possível configurar as três variáveis na execução da IDE. Um arquivo `.env.local` não é carregado automaticamente pelo Spring Boot; é necessário configurar seu carregamento na ferramenta utilizada.

Em Linux/macOS, configure as mesmas variáveis no ambiente e execute `./mvnw spring-boot:run`.

A API fica disponível em `http://localhost:8080`. Para verificar a inicialização, acesse `http://localhost:8080/categorias`; em um banco vazio, a resposta esperada é `[]`.

Credenciais e configurações locais devem permanecer fora do Git. O [`.gitignore`](.gitignore) já ignora arquivos `.env`, `.env.*` e configurações locais das IDEs.

## Usar a API

Os exemplos abaixo podem ser enviados com um cliente HTTP. Primeiro crie uma categoria:

```http
POST http://localhost:8080/categorias
Content-Type: application/json

{
  "nome": "Alimentação"
}
```

Depois use o `id` retornado para cadastrar uma transação. O valor `1` abaixo é ilustrativo:

```http
POST http://localhost:8080/transacoes
Content-Type: application/json

{
  "descricao": "Supermercado",
  "valor": 187.50,
  "data": "2026-10-22",
  "tipo": "DESPESA",
  "categoriaId": 1
}
```

O cadastro e a edição de transações recebem `categoriaId`, em vez do nome ou de um objeto `categoria` no corpo da requisição.

### Endpoints

| Método | Caminho | Finalidade |
| --- | --- | --- |
| `POST` | `/categorias` | Cadastrar categoria |
| `GET` | `/categorias` | Listar categorias |
| `GET` | `/categorias/{id}` | Consultar categoria |
| `PUT` | `/categorias/{id}` | Atualizar o nome da categoria |
| `DELETE` | `/categorias/{id}` | Excluir categoria sem transações vinculadas |
| `POST` | `/transacoes` | Cadastrar transação |
| `GET` | `/transacoes` | Listar ou filtrar transações |
| `GET` | `/transacoes/{id}` | Consultar transação |
| `PUT` | `/transacoes/{id}` | Atualizar transação |
| `DELETE` | `/transacoes/{id}` | Excluir transação |
| `GET` | `/transacoes/resumo` | Consultar receitas, despesas e saldo |

Cadastros retornam 201, consultas e atualizações retornam 200, e exclusões bem-sucedidas retornam 204 sem corpo.

### Filtros e resumo financeiro

```http
GET http://localhost:8080/transacoes?dataInicio=2026-10-01&dataFim=2026-10-31&categoriaId=1&tipo=DESPESA
```

Os quatro filtros são opcionais e podem ser combinados. As datas usam `AAAA-MM-DD`, os limites são inclusivos e `tipo` aceita `RECEITA` ou `DESPESA`. Sem filtros, a consulta lista todo o histórico; sem correspondências, retorna `[]`.

```http
GET http://localhost:8080/transacoes/resumo?dataInicio=2026-10-01&dataFim=2026-10-31
```

O resumo aceita apenas os filtros opcionais `dataInicio` e `dataFim`. Exemplo de resposta:

```json
{
  "totalReceitas": 3500.00,
  "totalDespesas": 187.50,
  "saldo": 3312.50
}
```

O saldo é calculado como receitas menos despesas. Sem datas, o resumo considera todo o histórico; sem transações no período, os três valores são zero. Uma data inicial posterior à final retorna 400.

O arquivo [`request.http`](request.http) registra exemplos usados durante o desenvolvimento. Ele ainda contém requisições antigas com `categoria` no corpo; para criar ou editar transações, siga o formato atual com `categoriaId` apresentado acima.

## Executar os testes

No PowerShell, a partir da raiz do projeto:

```powershell
.\mvnw.cmd clean test
```

Em Linux/macOS: `./mvnw clean test`.

Os testes de integração ativam o perfil `test`, configurado em [`application-test.properties`](src/test/resources/application-test.properties), e usam um banco H2 temporário em memória. Não precisam do PostgreSQL nem das variáveis `DB_URL`, `DB_USERNAME` e `DB_PASSWORD`. Evite sobrescrever a configuração desse perfil com variáveis `SPRING_DATASOURCE_*` ou `SPRING_JPA_HIBERNATE_DDL_AUTO` no ambiente de testes.

A última execução validada, após a criação do perfil de testes, teve **53 testes aprovados**, sem falhas. Os testes cobrem cenários de categorias, transações inexistentes, associação de categorias, filtros, resumo financeiro e inicialização da aplicação. A validação específica com PostgreSQL continua sendo necessária antes da publicação.

## Organização do código

```text
src/main/java/com/jefferson/fintrack/
├── controller/   # Recebe as requisições HTTP
├── dto/          # Define dados de entrada e resultados de consultas
├── exception/    # Exceções de domínio e tratamento global de erros
├── model/        # Entidades e tipo de transação
├── repository/   # Acesso aos dados com Spring Data JPA
└── service/      # Regras e operações da aplicação
```

O fluxo principal é `Controller → Service → Repository → PostgreSQL`, com injeção de dependências pelo construtor. Valores monetários usam `BigDecimal`, e datas de transações usam `LocalDate`.

## Roteiro do projeto

O objetivo permanece construir uma aplicação web completa. O estado atual não inclui autenticação nem separação de dados por usuário.

- [x] Núcleo da API: transações, categorias, validações e tratamento de registros inexistentes.
- [x] Filtros por período, categoria e tipo.
- [x] Totais de receitas, despesas e saldo por período.
- [x] Suíte de testes independente do banco pessoal.
- [ ] Cadastro e login de usuários, com acesso restrito aos próprios dados.
- [ ] Gastos por categoria e evolução mensal para o dashboard.
- [ ] Importação de extratos CSV, com definição do formato e tratamento de linhas inválidas.
- [ ] Relatórios e definição do formato de exportação.
- [ ] Frontend com HTML, CSS e JavaScript, incluindo dashboard e gráficos.
- [ ] Documentação formal da API e revisão dos exemplos HTTP antigos.
- [ ] Preparação e validação do ambiente de produção, publicação e material de apresentação do portfólio.

O desenvolvimento segue em etapas pequenas: cada etapa é explicada, implementada e validada antes da próxima, com um commit correspondente. A escolha da hospedagem será retomada na preparação do deploy.
