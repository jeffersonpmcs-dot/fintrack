# FinTrack

Aplicação de gestão financeira pessoal em desenvolvimento, criada como projeto de portfólio para demonstrar desenvolvimento web, modelagem de dados e análise de informações financeiras.

O objetivo final é permitir que cada pessoa tenha sua conta, registre receitas e despesas, importe extratos CSV e acompanhe sua situação financeira por dashboards e relatórios. Atualmente, o projeto oferece o núcleo da API REST, cadastro, login com sessão, separação dos dados por usuário, dados para o dashboard e importação CSV no formato do FinTrack. Interface, dashboard visual e publicação serão desenvolvidos nas próximas etapas.

## Tecnologias

- Java 21 e Maven Wrapper.
- Spring Boot 4.1.1, Spring Web MVC e Jakarta Validation.
- Spring Data JPA / Hibernate e PostgreSQL.
- JUnit e Mockito nos testes; H2 em memória nos testes de integração.
- [Apache Commons CSV](https://commons.apache.org/proper/commons-csv/) para leitura de arquivos CSV.

## Funcionalidades disponíveis

- Cadastro de usuários com e-mail único e hash da senha usando PBKDF2-HMAC-SHA256, salt aleatório de 16 bytes e 600.000 iterações.
- Login com sessão em cookie, consulta do usuário autenticado e logout.
- Exigência de autenticação nas rotas financeiras e proteção CSRF nas operações de escrita.
- Categorias e transações privadas por conta, incluindo filtros e resumo financeiro.
- Cadastro, listagem, consulta, edição e exclusão de receitas e despesas.
- Cadastro, listagem, consulta, edição e exclusão de categorias.
- Associação de cada transação a uma categoria existente.
- Bloqueio da exclusão de categorias com transações vinculadas.
- Filtros combináveis por período, categoria e tipo (`RECEITA` ou `DESPESA`).
- Resumo financeiro com total de receitas, total de despesas e saldo por período.
- Gastos por categoria no período, ordenados do maior para o menor e restritos à conta autenticada.
- Evolução mensal de receitas, despesas e saldo, com os 12 meses do ano escolhido.
- Importação CSV de receitas e despesas, com validação completa e gravação de todo o arquivo em uma transação.
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

A API fica disponível em `http://localhost:8080`. Para verificar a inicialização, acesse `http://localhost:8080/auth/csrf`; a resposta contém o token CSRF e o nome do cabeçalho a utilizar. Consultas a `/categorias` e `/transacoes` exigem login.

Credenciais e configurações locais devem permanecer fora do Git. O [`.gitignore`](.gitignore) já ignora arquivos `.env`, `.env.*` e configurações locais das IDEs.

## Usar a API

### Sessão e token CSRF

O cliente deve manter o cookie `JSESSIONID` recebido do servidor. Configure seu cliente HTTP para preservar cookies entre as requisições. No navegador, chamadas para a mesma origem mantêm esse cookie automaticamente; a senha não precisa ser reenviada a cada consulta.

Antes do cadastro ou login, obtenha um token:

```http
GET http://localhost:8080/auth/csrf
```

O JSON informa `headerName`, `parameterName` e `token`. Envie o valor de `token` no cabeçalho indicado por `headerName` (por padrão, `X-CSRF-TOKEN`), junto com o cookie da mesma sessão, nas requisições `POST`, `PUT`, `PATCH` e `DELETE`. Nos exemplos abaixo, `{{csrfToken}}` representa esse valor, configurado na variável do cliente HTTP.

O cadastro e o login são públicos, mas também exigem CSRF. Após login ou logout, obtenha um novo token em `/auth/csrf`; o token anterior deixa de ser válido.

### Cadastrar um usuário

```http
POST http://localhost:8080/usuarios
Content-Type: application/json
X-CSRF-TOKEN: {{csrfToken}}

{
  "nome": "Pessoa de exemplo",
  "email": "pessoa@example.com",
  "senha": "SenhaFicticia123!"
}
```

A senha acima é apenas um exemplo. O cadastro exige nome de até 100 caracteres, e-mail válido de até 254 caracteres e senha de 12 a 128 caracteres. Nome e e-mail têm espaços nas pontas removidos; o e-mail é salvo em minúsculas. A senha é preservada exatamente como recebida, inclusive espaços e caracteres Unicode.

O sucesso retorna 201 com apenas `id`, `nome` e `email`. Dados inválidos retornam 400 com mensagens por campo; um e-mail já cadastrado retorna 409 com `{"mensagem":"E-mail já cadastrado"}`. A senha e seu hash não são devolvidos. O cadastro não autentica automaticamente a conta.

### Login, usuário atual e logout

O login utiliza formulário HTTP (`application/x-www-form-urlencoded`), com os campos `email` e `senha`. As respostas são JSON, sem redirecionamento para páginas HTML.

```http
POST http://localhost:8080/auth/login
Content-Type: application/x-www-form-urlencoded
X-CSRF-TOKEN: {{csrfToken}}

email=pessoa%40example.com&senha=SenhaFicticia123%21
```

Sucesso retorna 200 com `{"mensagem":"Login realizado com sucesso"}` e mantém a autenticação na sessão. E-mail inexistente ou senha incorreta retornam a mesma resposta 401: `{"mensagem":"E-mail ou senha inválidos"}`. O ID da sessão é renovado no login.

Depois de entrar, mantenha o cookie, obtenha um novo token CSRF e consulte:

```http
GET http://localhost:8080/auth/me
```

A resposta tem apenas `id`, `nome` e `email` da conta autenticada.

Para experimentar pelo PowerShell com a conta fictícia cadastrada acima:

```powershell
$baseUrl = 'http://localhost:8080'
$csrf = Invoke-RestMethod "$baseUrl/auth/csrf" -SessionVariable sessao
$cabecalhos = @{}
$cabecalhos[$csrf.headerName] = $csrf.token

Invoke-RestMethod "$baseUrl/auth/login" -Method Post -WebSession $sessao `
    -Headers $cabecalhos -ContentType 'application/x-www-form-urlencoded' `
    -Body @{ email = 'pessoa@example.com'; senha = 'SenhaFicticia123!' }

$csrf = Invoke-RestMethod "$baseUrl/auth/csrf" -WebSession $sessao
$cabecalhos[$csrf.headerName] = $csrf.token
Invoke-RestMethod "$baseUrl/auth/me" -WebSession $sessao
```

Para sair, use a mesma sessão e o token obtido após o login:

```powershell
Invoke-RestMethod "$baseUrl/auth/logout" -Method Post -WebSession $sessao -Headers $cabecalhos
```

O logout retorna 204, invalida a sessão e remove o cookie. O cookie está configurado como `HttpOnly`, `SameSite=Lax`, com expiração da sessão após 30 minutos de inatividade. A configuração de produção deverá usar HTTPS e `server.servlet.session.cookie.secure=true`.

Consultas protegidas sem login retornam 401. Requisições de escrita sem token CSRF válido retornam 403, inclusive antes do login. Sem autenticação, uma escrita com CSRF válido retorna 401.

Cada conta acessa apenas suas próprias categorias e transações. O servidor define o dono a partir da sessão autenticada; os corpos das requisições continuam iguais e não devem enviar `usuario` ou `usuarioId`. O cadastro de categorias também ignora um `id` fornecido no corpo e sempre cria um novo registro. Edições preservam o dono original.

Buscar, editar ou excluir um ID de outra conta retorna o mesmo 404 de um ID inexistente. Uma transação só pode usar categorias da própria conta; categoria de outra conta retorna 404. A filtragem por categoria de outra conta retorna uma lista vazia. O resumo financeiro considera somente transações da conta autenticada.

### Registros anteriores à separação por conta

Na primeira inicialização desta versão com a configuração de desenvolvimento `ddl-auto=update`, o Hibernate acrescenta a coluna `usuario_id` às tabelas `categoria` e `transacao`. Os registros anteriores são preservados com essa coluna nula e ficam fora das consultas, alterações e resumos da API. Uma conta nova começa com listas vazias e totais zero.

As colunas permitem nulo para compatibilidade com esse histórico; todos os cadastros pela API recebem o usuário autenticado. A associação dos dados antigos a uma conta exige uma migração específica, com a conta de destino definida e a consistência entre transações e categorias verificada. Esta versão não atribui automaticamente esses dados a nenhuma conta.

### Cadastrar categorias e transações

Os exemplos abaixo podem ser enviados com um cliente HTTP. Primeiro crie uma categoria:

```http
POST http://localhost:8080/categorias
Content-Type: application/json
X-CSRF-TOKEN: {{csrfToken}}

{
  "nome": "Alimentação"
}
```

Depois use o `id` retornado para cadastrar uma transação. O valor `1` abaixo é ilustrativo:

```http
POST http://localhost:8080/transacoes
Content-Type: application/json
X-CSRF-TOKEN: {{csrfToken}}

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
| `GET` | `/auth/csrf` | Obter token CSRF (público) |
| `POST` | `/auth/login` | Autenticar e iniciar sessão (formulário HTTP) |
| `GET` | `/auth/me` | Consultar a conta autenticada |
| `POST` | `/auth/logout` | Invalidar a sessão |
| `POST` | `/usuarios` | Cadastrar usuário |
| `POST` | `/categorias` | Cadastrar categoria |
| `GET` | `/categorias` | Listar categorias |
| `GET` | `/categorias/{id}` | Consultar categoria |
| `PUT` | `/categorias/{id}` | Atualizar o nome da categoria |
| `DELETE` | `/categorias/{id}` | Excluir categoria sem transações vinculadas |
| `POST` | `/transacoes` | Cadastrar transação |
| `POST` | `/transacoes/importar-csv` | Importar um arquivo CSV da conta autenticada |
| `GET` | `/transacoes` | Listar ou filtrar transações |
| `GET` | `/transacoes/{id}` | Consultar transação |
| `PUT` | `/transacoes/{id}` | Atualizar transação |
| `DELETE` | `/transacoes/{id}` | Excluir transação |
| `GET` | `/transacoes/resumo` | Consultar receitas, despesas e saldo |
| `GET` | `/transacoes/gastos-por-categoria` | Consultar despesas agrupadas por categoria |
| `GET` | `/transacoes/evolucao-mensal` | Consultar receitas, despesas e saldo de cada mês do ano |

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

### Gastos por categoria

Com a sessão autenticada, consulte:

```http
GET http://localhost:8080/transacoes/gastos-por-categoria?dataInicio=2026-11-01&dataFim=2026-11-30
```

Exemplo de resposta:

```json
[
  { "categoriaId": 1, "categoriaNome": "Alimentação", "totalDespesas": 120.30 },
  { "categoriaId": 2, "categoriaNome": "Transporte", "totalDespesas": 50.00 }
]
```

Os dois filtros de data são opcionais e inclusivos; sem datas, a consulta considera todo o histórico da conta. Apenas despesas são somadas. Categorias sem despesas no período não aparecem, e uma consulta sem despesas retorna `[]`. Categorias com nomes iguais continuam separadas pelo ID.

O resultado é ordenado por total decrescente, depois por nome e ID crescentes para desempate. Os valores usam `BigDecimal`. Datas invertidas ou malformadas retornam 400; consultas sem login retornam 401. A consulta verifica tanto o dono da transação quanto o da categoria e exclui registros antigos sem dono. Esta rota fornece dados para o futuro dashboard; a interface visual ainda está pendente.

### Evolução mensal

Com a sessão autenticada, informe o ano desejado:

```http
GET http://localhost:8080/transacoes/evolucao-mensal?ano=2026
```

A resposta contém exatamente 12 itens, em ordem de janeiro (`mes: 1`) a dezembro (`mes: 12`). Cada item informa `ano`, `mes`, `totalReceitas`, `totalDespesas` e `saldo`. Exemplo de um item:

```json
{
  "ano": 2026,
  "mes": 1,
  "totalReceitas": 1000.30,
  "totalDespesas": 100.05,
  "saldo": 900.25
}
```

O parâmetro `ano` é obrigatório e aceita inteiros de 1 a 9999. Ano ausente, inválido ou fora desse intervalo retorna 400; consultas sem login retornam 401. O período inclui o primeiro e o último dia do ano e considera apenas as transações da conta autenticada. Registros antigos sem dono ficam fora dos totais.

Meses sem movimento têm os três valores financeiros iguais a zero. Um ano inteiro sem transações também retorna os 12 meses zerados. O saldo é o resultado daquele mês (`receitas - despesas`), sem carregar o saldo dos meses ou anos anteriores. As somas usam `BigDecimal` e são agrupadas no banco. A soma dos meses corresponde ao resumo financeiro do mesmo ano. O gráfico visual será construído junto com o frontend.

### Importar transações por CSV

A primeira versão usa um formato padronizado do FinTrack. Extratos de bancos com outro layout precisam ser convertidos para este formato antes do envio.

```csv
descricao;valor;data;tipo;categoriaId
Salário;3500.00;2026-11-03;RECEITA;1
"Supermercado; compra mensal";187.50;2026-11-03;DESPESA;2
```

Use o [arquivo de exemplo](exemplos/importacao.csv) e substitua os IDs por categorias existentes na sua conta. A importação utiliza as categorias informadas; ela não cria categorias automaticamente.

| Campo | Regra |
| --- | --- |
| `descricao` | Obrigatória, até 255 caracteres; espaços nas pontas são removidos |
| `valor` | Positivo, ponto decimal, até 15 dígitos inteiros e 2 decimais; sem separador de milhar ou notação científica |
| `data` | Data válida em `AAAA-MM-DD`, com ano entre 1 e 9999 |
| `tipo` | Exatamente `RECEITA` ou `DESPESA` |
| `categoriaId` | Inteiro positivo; categoria existente e pertencente à conta autenticada |

O cabeçalho deve conter exatamente essas cinco colunas, nessa ordem e com esses nomes. Use UTF-8, com ou sem BOM, separador `;` e quebras de linha LF ou CRLF. Descrições com `;`, aspas ou quebras de linha devem estar entre aspas duplas; aspas dentro do texto são escapadas como `""`. Linhas vazias intermediárias são rejeitadas.

Envie `POST /transacoes/importar-csv` como `multipart/form-data`, com o campo de arquivo chamado `arquivo`, o cookie da sessão e o cabeçalho CSRF atual. Há uma requisição multipart pronta no [`request.http`](request.http). O limite é de **1 MB (1.048.576 bytes)** e **1.000 transações por arquivo**, além do cabeçalho. O upload multipart completo tem limite de 2 MB.

Sucesso retorna 201 com:

```json
{ "totalImportadas": 2 }
```

Todos os registros e suas categorias são validados antes da gravação. Um arquivo inválido retorna 400 com o primeiro erro identificado e não grava nenhuma transação. Exemplo:

```json
{ "linha": 3, "mensagem": "Categoria não encontrada para esta conta" }
```

`linha` é o número do registro lógico no CSV: cabeçalho = 1, primeira transação = 2. Um campo entre aspas com quebra de linha continua sendo um único registro. Categorias inexistentes, sem dono ou pertencentes a outra conta recebem a mesma mensagem.

Um upload acima do limite retorna 413. Conflitos de integridade durante a gravação retornam 409 e desfazem a importação inteira, inclusive transações já inseridas naquela tentativa. Login e CSRF são obrigatórios. O nome do arquivo e campos externos não definem o usuário: o dono sempre vem da sessão.

**Reenvio:** cada envio válido cria novas transações. Esta versão não faz deduplicação; reenviar o mesmo arquivo duplica seus lançamentos. Confira o arquivo antes de confirmar o envio.

O arquivo [`request.http`](request.http) registra exemplos usados durante o desenvolvimento. Ele ainda contém requisições antigas com `categoria` no corpo; para criar ou editar transações, siga o formato atual com `categoriaId` apresentado acima.

## Executar os testes

No PowerShell, a partir da raiz do projeto:

```powershell
.\mvnw.cmd clean test
```

Em Linux/macOS: `./mvnw clean test`.

Os testes de integração ativam o perfil `test`, configurado em [`application-test.properties`](src/test/resources/application-test.properties), e usam um banco H2 temporário em memória. Não precisam do PostgreSQL nem das variáveis `DB_URL`, `DB_USERNAME` e `DB_PASSWORD`. Evite sobrescrever a configuração desse perfil com variáveis `SPRING_DATASOURCE_*` ou `SPRING_JPA_HIBERNATE_DDL_AUTO` no ambiente de testes.

Os testes cobrem categorias, transações inexistentes, associação de categorias, filtros, resumo financeiro, persistência e cadastro de usuários, hash de senhas e inicialização da aplicação. Os testes de autenticação e isolamento usam a cadeia real de filtros do Spring Security com MockMvc, verificando login, renovação da sessão, CSRF, logout e acesso entre duas contas. Também verificam tentativas de editar ou excluir dados alheios, forjar o dono, usar categoria de outra conta e acessar registros antigos sem dono. Os testes de CSV cobrem formato, codificação, limites, validação e atomicidade. O teste de rollback provoca uma falha real de integridade no H2 e verifica que a primeira linha também foi desfeita, sem uma transação externa de teste. A validação específica com PostgreSQL continua sendo necessária antes da publicação.

## Organização do código

```text
src/main/java/com/jefferson/fintrack/
├── config/       # Configuração do hash de senhas e segurança HTTP
├── controller/   # Recebe as requisições HTTP
├── dto/          # Define dados de entrada e resultados de consultas
├── exception/    # Exceções de domínio e tratamento global de erros
├── model/        # Entidades e tipo de transação
├── repository/   # Acesso aos dados com Spring Data JPA
└── service/      # Regras e operações da aplicação
```

O fluxo principal é `Controller → Service → Repository → PostgreSQL`, com injeção de dependências pelo construtor. Valores monetários usam `BigDecimal`, e datas de transações usam `LocalDate`.

## Roteiro do projeto

O objetivo permanece construir uma aplicação web completa. O estado atual inclui autenticação por sessão e separação dos dados financeiros por usuário.

- [x] Núcleo da API: transações, categorias, validações e tratamento de registros inexistentes.
- [x] Filtros por período, categoria e tipo.
- [x] Totais de receitas, despesas e saldo por período.
- [x] Suíte de testes independente do banco pessoal.
- [x] Estrutura e cadastro de usuários, com validação, e-mail único e hash de senhas.
- [x] Login por sessão, consulta da conta autenticada, CSRF e logout.
- [x] Vínculo dos dados e autorização de acesso por usuário.
- [x] Consulta de gastos por categoria para o dashboard.
- [x] Consulta de evolução mensal para o dashboard.
- [x] Importação CSV no formato do FinTrack, com validação e gravação integral.
- [ ] Relatórios e definição do formato de exportação.
- [ ] Frontend com HTML, CSS e JavaScript, incluindo dashboard e gráficos.
- [ ] Documentação formal da API e revisão dos exemplos HTTP antigos.
- [ ] Preparação e validação do ambiente de produção, publicação e material de apresentação do portfólio.

O desenvolvimento segue em etapas pequenas: cada etapa é explicada, implementada e validada antes da próxima, com um commit correspondente. A escolha da hospedagem será retomada na preparação do deploy.
