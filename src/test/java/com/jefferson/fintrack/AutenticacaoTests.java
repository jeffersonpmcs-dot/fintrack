package com.jefferson.fintrack;

import com.jefferson.fintrack.dto.UsuarioCadastroRequest;
import com.jefferson.fintrack.repository.CategoriaRepository;
import com.jefferson.fintrack.repository.TransacaoRepository;
import com.jefferson.fintrack.repository.UsuarioRepository;
import com.jefferson.fintrack.service.UsuarioService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.security.core.context.SecurityContext;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import org.springframework.transaction.annotation.Transactional;
import tools.jackson.databind.ObjectMapper;

import static org.hamcrest.Matchers.aMapWithSize;
import static org.junit.jupiter.api.Assertions.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest(properties = "spring.datasource.url=jdbc:h2:mem:fintrack-auth;DB_CLOSE_DELAY=-1")
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Transactional
class AutenticacaoTests {

    private static final String EMAIL = "pessoa@example.com";
    private static final String SENHA = "SenhaDeTeste123!";

    @Autowired
    private MockMvc mvc;
    @Autowired
    private ObjectMapper mapper;
    @Autowired
    private UsuarioService usuarioService;
    @Autowired
    private UsuarioRepository usuarios;
    @Autowired
    private CategoriaRepository categorias;
    @Autowired
    private TransacaoRepository transacoes;

    @BeforeEach
    void criarConta() {
        UsuarioCadastroRequest request = new UsuarioCadastroRequest();
        request.setNome("Pessoa de teste");
        request.setEmail(EMAIL);
        request.setSenha(SENHA);
        usuarioService.cadastrar(request);
    }

    @ParameterizedTest
    @ValueSource(strings = {"/categorias", "/transacoes", "/transacoes/resumo", "/auth/me"})
    void consultaSemLoginRetorna401EmJson(String caminho) throws Exception {
        mvc.perform(get(caminho))
                .andExpect(status().isUnauthorized())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_JSON))
                .andExpect(jsonPath("$.mensagem").value("Autenticação necessária"));
    }

    @Test
    void forneceTokenCsrfAntesDoLoginSemAutenticar() throws Exception {
        SessaoCsrf csrf = csrf(null);
        assertNotNull(csrf.sessao());
        assertFalse(csrf.token().isBlank());
        mvc.perform(get("/auth/me").session(csrf.sessao()))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void loginNormalizaEmailRenovaIdDaSessaoEPermiteConsultarUsuario() throws Exception {
        SessaoCsrf csrf = csrf(null);
        String idAnterior = csrf.sessao().getId();
        MvcResult login = mvc.perform(login(csrf, "  PESSOA@EXAMPLE.COM  ", SENHA))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.mensagem").value("Login realizado com sucesso"))
                .andExpect(jsonPath("$.senha").doesNotExist())
                .andExpect(jsonPath("$.senhaHash").doesNotExist())
                .andReturn();

        MockHttpSession sessao = (MockHttpSession) login.getRequest().getSession(false);
        assertNotNull(sessao);
        assertNotEquals(idAnterior, sessao.getId());
        SecurityContext contexto = (SecurityContext) sessao.getAttribute("SPRING_SECURITY_CONTEXT");
        assertNotNull(contexto);
        assertNull(contexto.getAuthentication().getCredentials());

        mvc.perform(get("/auth/me").session(sessao))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", aMapWithSize(3)))
                .andExpect(jsonPath("$.id").isNumber())
                .andExpect(jsonPath("$.nome").value("Pessoa de teste"))
                .andExpect(jsonPath("$.email").value(EMAIL));
    }

    @Test
    void senhaErradaEContaInexistenteTemMesmaRespostaSemAutenticar() throws Exception {
        SessaoCsrf csrf = csrf(null);
        String senhaErrada = mvc.perform(login(csrf, EMAIL, "SenhaIncorreta123!"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.mensagem").value("E-mail ou senha inválidos"))
                .andReturn().getResponse().getContentAsString();
        String emailInexistente = mvc.perform(login(csrf, "ausente@example.com", SENHA))
                .andExpect(status().isUnauthorized())
                .andReturn().getResponse().getContentAsString();

        assertEquals(senhaErrada, emailInexistente);
        assertNull(csrf.sessao().getAttribute("SPRING_SECURITY_CONTEXT"));
        mvc.perform(get("/transacoes").session(csrf.sessao()))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void loginSemTokenCsrfERejeitado() throws Exception {
        mvc.perform(post("/auth/login").contentType(MediaType.APPLICATION_FORM_URLENCODED)
                        .param("email", EMAIL).param("senha", SENHA))
                .andExpect(status().isForbidden());
    }

    @Test
    void cookieDeOutraSessaoNaoAutenticadaNaoHerdaLogin() throws Exception {
        SessaoCsrf primeira = csrf(null);
        SessaoCsrf segunda = csrf(null);
        mvc.perform(login(primeira, EMAIL, SENHA)).andExpect(status().isOk());
        mvc.perform(get("/auth/me").session(segunda.sessao()))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void cadastroPublicoExigeCsrfEMantemRespostaSemCredenciais() throws Exception {
        SessaoCsrf csrf = csrf(null);
        String corpo = """
                {"nome":"Nova pessoa","email":"nova@example.com","senha":"SenhaDeTeste123!"}
                """;
        mvc.perform(post("/usuarios").contentType(MediaType.APPLICATION_JSON).content(corpo))
                .andExpect(status().isForbidden());
        assertEquals(1, usuarios.count());

        mvc.perform(post("/usuarios").session(csrf.sessao()).header(csrf.cabecalho(), csrf.token())
                        .contentType(MediaType.APPLICATION_JSON).content(corpo))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$", aMapWithSize(3)))
                .andExpect(jsonPath("$.email").value("nova@example.com"));
        assertEquals(2, usuarios.count());
        mvc.perform(get("/auth/me").session(csrf.sessao()))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void acessoFinanceiroExigeLoginMesmoComTokenCsrfValido() throws Exception {
        SessaoCsrf csrf = csrf(null);
        mvc.perform(post("/categorias").session(csrf.sessao()).header(csrf.cabecalho(), csrf.token())
                        .contentType(MediaType.APPLICATION_JSON).content("{\"nome\":\"Teste\"}"))
                .andExpect(status().isUnauthorized());
        assertEquals(0, categorias.count());
    }

    @Test
    void autenticadoPodeLerECriarCategoriaMasEscritaExigeCsrfAtual() throws Exception {
        SessaoCsrf antes = csrf(null);
        mvc.perform(login(antes, EMAIL, SENHA)).andExpect(status().isOk());
        mvc.perform(get("/categorias").session(antes.sessao())).andExpect(status().isOk());
        mvc.perform(get("/transacoes").session(antes.sessao())).andExpect(status().isOk());
        mvc.perform(get("/transacoes/resumo").session(antes.sessao())).andExpect(status().isOk());

        mvc.perform(post("/categorias").session(antes.sessao())
                        .contentType(MediaType.APPLICATION_JSON).content("{\"nome\":\"Teste\"}"))
                .andExpect(status().isForbidden());
        mvc.perform(post("/categorias").session(antes.sessao()).header(antes.cabecalho(), antes.token())
                        .contentType(MediaType.APPLICATION_JSON).content("{\"nome\":\"Teste\"}"))
                .andExpect(status().isForbidden());
        assertEquals(0, categorias.count());

        SessaoCsrf atual = csrf(antes.sessao());
        mvc.perform(post("/categorias").session(atual.sessao()).header(atual.cabecalho(), atual.token())
                        .contentType(MediaType.APPLICATION_JSON).content("{\"nome\":\"Teste\"}"))
                .andExpect(status().isCreated());
        assertEquals(1, categorias.count());
        assertEquals(0, transacoes.count());
    }

    @Test
    void logoutExigeCsrfInvalidaSessaoEPermiteNovoLogin() throws Exception {
        SessaoCsrf antes = csrf(null);
        mvc.perform(login(antes, EMAIL, SENHA)).andExpect(status().isOk());
        mvc.perform(post("/auth/logout").session(antes.sessao()))
                .andExpect(status().isForbidden());
        mvc.perform(get("/auth/me").session(antes.sessao())).andExpect(status().isOk());

        SessaoCsrf atual = csrf(antes.sessao());
        mvc.perform(post("/auth/logout").session(atual.sessao()).header(atual.cabecalho(), atual.token()))
                .andExpect(status().isNoContent())
                .andExpect(cookie().maxAge("JSESSIONID", 0));
        assertTrue(atual.sessao().isInvalid());
        mvc.perform(get("/auth/me")).andExpect(status().isUnauthorized());

        SessaoCsrf nova = csrf(null);
        mvc.perform(login(nova, EMAIL, SENHA)).andExpect(status().isOk());
        mvc.perform(get("/auth/me").session(nova.sessao())).andExpect(status().isOk());
    }

    @Test
    void getLogoutNaoEncerraSessao() throws Exception {
        SessaoCsrf csrf = csrf(null);
        mvc.perform(login(csrf, EMAIL, SENHA)).andExpect(status().isOk());
        mvc.perform(get("/auth/logout").session(csrf.sessao()));
        assertFalse(csrf.sessao().isInvalid());
        mvc.perform(get("/auth/me").session(csrf.sessao())).andExpect(status().isOk());
    }

    private MockHttpServletRequestBuilder login(SessaoCsrf csrf, String email, String senha) {
        return post("/auth/login").session(csrf.sessao()).header(csrf.cabecalho(), csrf.token())
                .contentType(MediaType.APPLICATION_FORM_URLENCODED)
                .param("email", email).param("senha", senha);
    }

    private SessaoCsrf csrf(MockHttpSession sessao) throws Exception {
        MockHttpServletRequestBuilder request = get("/auth/csrf");
        if (sessao != null) {
            request.session(sessao);
        }
        MvcResult resultado = mvc.perform(request).andExpect(status().isOk()).andReturn();
        CsrfData dados = mapper.readValue(resultado.getResponse().getContentAsString(), CsrfData.class);
        return new SessaoCsrf((MockHttpSession) resultado.getRequest().getSession(false),
                dados.headerName(), dados.token());
    }

    private record CsrfData(String headerName, String parameterName, String token) {
    }

    private record SessaoCsrf(MockHttpSession sessao, String cabecalho, String token) {
    }
}
