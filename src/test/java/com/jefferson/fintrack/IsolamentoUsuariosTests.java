package com.jefferson.fintrack;

import com.jefferson.fintrack.model.Categoria;
import com.jefferson.fintrack.model.TipoTransacao;
import com.jefferson.fintrack.model.Transacao;
import com.jefferson.fintrack.model.Usuario;
import com.jefferson.fintrack.repository.CategoriaRepository;
import com.jefferson.fintrack.repository.TransacaoRepository;
import com.jefferson.fintrack.repository.UsuarioRepository;
import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import org.springframework.transaction.annotation.Transactional;
import tools.jackson.databind.ObjectMapper;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.Map;

import static org.hamcrest.Matchers.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest(properties = "spring.datasource.url=jdbc:h2:mem:fintrack-isolamento;DB_CLOSE_DELAY=-1")
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Transactional
class IsolamentoUsuariosTests {

    private static final String SENHA = "SenhaDeTeste123!";

    @Autowired private MockMvc mvc;
    @Autowired private ObjectMapper mapper;
    @Autowired private PasswordEncoder encoder;
    @Autowired private UsuarioRepository usuarios;
    @Autowired private CategoriaRepository categorias;
    @Autowired private TransacaoRepository transacoes;
    @Autowired private EntityManager entityManager;

    private Usuario a;
    private Usuario b;
    private Categoria categoriaA;
    private Categoria categoriaB;
    private Transacao receitaA;
    private Transacao despesaA;
    private Transacao receitaB;
    private Transacao despesaB;
    private Sessao sessaoA;
    private Sessao sessaoB;

    @BeforeEach
    void prepararDuasContasComDadosNoMesmoPeriodo() throws Exception {
        a = criarUsuario("a@example.com");
        b = criarUsuario("b@example.com");
        categoriaA = criarCategoria("Categoria A", a);
        categoriaB = criarCategoria("Categoria B", b);
        receitaA = criarTransacao("Receita A", "1000.00", TipoTransacao.RECEITA, categoriaA, a);
        despesaA = criarTransacao("Despesa A", "100.00", TipoTransacao.DESPESA, categoriaA, a);
        receitaB = criarTransacao("Receita B", "9999.00", TipoTransacao.RECEITA, categoriaB, b);
        despesaB = criarTransacao("Despesa B", "5000.00", TipoTransacao.DESPESA, categoriaB, b);
        sessaoA = entrar(a.getEmail());
        sessaoB = entrar(b.getEmail());
    }

    @Test
    void cadaContaListaSomenteCategoriasETransacoesProprias() throws Exception {
        mvc.perform(get("/categorias").session(sessaoA.http()))
                .andExpect(status().isOk()).andExpect(jsonPath("$", hasSize(1)))
                .andExpect(jsonPath("$[0].id").value(categoriaA.getId()))
                .andExpect(jsonPath("$[0].usuario").doesNotExist());
        mvc.perform(get("/categorias").session(sessaoB.http()))
                .andExpect(status().isOk()).andExpect(jsonPath("$", hasSize(1)))
                .andExpect(jsonPath("$[0].id").value(categoriaB.getId()));
        mvc.perform(get("/transacoes").session(sessaoA.http()))
                .andExpect(status().isOk()).andExpect(jsonPath("$", hasSize(2)))
                .andExpect(jsonPath("$[*].descricao", containsInAnyOrder("Receita A", "Despesa A")))
                .andExpect(jsonPath("$[0].usuario").doesNotExist())
                .andExpect(jsonPath("$[0].categoria.usuario").doesNotExist());
        mvc.perform(get("/transacoes").session(sessaoB.http()))
                .andExpect(status().isOk()).andExpect(jsonPath("$", hasSize(2)))
                .andExpect(jsonPath("$[*].descricao", containsInAnyOrder("Receita B", "Despesa B")));
    }

    @Test
    void filtrosPorTipoPeriodoECategoriaContinuamRestritosAConta() throws Exception {
        mvc.perform(get("/transacoes?tipo=DESPESA").session(sessaoA.http()))
                .andExpect(status().isOk()).andExpect(jsonPath("$", hasSize(1)))
                .andExpect(jsonPath("$[0].descricao").value("Despesa A"));
        mvc.perform(get("/transacoes?dataInicio=2026-11-01&dataFim=2026-11-30&tipo=RECEITA")
                        .session(sessaoB.http()))
                .andExpect(status().isOk()).andExpect(jsonPath("$", hasSize(1)))
                .andExpect(jsonPath("$[0].descricao").value("Receita B"));
        mvc.perform(get("/transacoes?categoriaId=" + categoriaB.getId()).session(sessaoA.http()))
                .andExpect(status().isOk()).andExpect(jsonPath("$", hasSize(0)));
        mvc.perform(get("/transacoes?categoriaId=" + categoriaA.getId()
                        + "&tipo=DESPESA&dataInicio=2026-11-01&dataFim=2026-11-30").session(sessaoB.http()))
                .andExpect(status().isOk()).andExpect(jsonPath("$", hasSize(0)));
    }

    @ParameterizedTest
    @ValueSource(strings = {"", "?dataInicio=2026-11-01&dataFim=2026-11-30"})
    void resumoNaoSomaTransacoesDaOutraConta(String filtro) throws Exception {
        mvc.perform(get("/transacoes/resumo" + filtro).session(sessaoA.http()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalReceitas").value(1000.00))
                .andExpect(jsonPath("$.totalDespesas").value(100.00))
                .andExpect(jsonPath("$.saldo").value(900.00));
        mvc.perform(get("/transacoes/resumo" + filtro).session(sessaoB.http()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalReceitas").value(9999.00))
                .andExpect(jsonPath("$.totalDespesas").value(5000.00))
                .andExpect(jsonPath("$.saldo").value(4999.00));
    }

    @ParameterizedTest
    @ValueSource(strings = {"categorias", "transacoes"})
    void idDeOutraContaRetornaOMesmo404QueIdInexistente(String recurso) throws Exception {
        Long idB = recurso.equals("categorias") ? categoriaB.getId() : despesaB.getId();
        Long idA = recurso.equals("categorias") ? categoriaA.getId() : despesaA.getId();
        String estrangeiro = mvc.perform(get("/" + recurso + "/" + idB).session(sessaoA.http()))
                .andExpect(status().isNotFound()).andReturn().getResponse().getContentAsString();
        String inexistente = mvc.perform(get("/" + recurso + "/99999999").session(sessaoA.http()))
                .andExpect(status().isNotFound()).andReturn().getResponse().getContentAsString();
        assertEquals(inexistente, estrangeiro);
        mvc.perform(get("/" + recurso + "/" + idA).session(sessaoB.http()))
                .andExpect(status().isNotFound());
        mvc.perform(get("/" + recurso + "/" + idA).session(sessaoA.http()))
                .andExpect(status().isOk());
    }

    @ParameterizedTest
    @CsvSource({"categorias,PUT,true", "categorias,DELETE,true", "transacoes,PUT,true",
            "transacoes,DELETE,true", "categorias,PUT,false", "categorias,DELETE,false",
            "transacoes,PUT,false", "transacoes,DELETE,false"})
    void naoAlteraNemExcluiRegistrosDaOutraConta(String recurso, String metodo, boolean atacanteA)
            throws Exception {
        Sessao sessao = atacanteA ? sessaoA : sessaoB;
        Categoria categoriaAlvo = atacanteA ? categoriaB : categoriaA;
        Transacao transacaoAlvo = atacanteA ? despesaB : despesaA;
        Long id = recurso.equals("categorias") ? categoriaAlvo.getId() : transacaoAlvo.getId();
        String nome = categoriaAlvo.getNome();
        String descricao = transacaoAlvo.getDescricao();
        String corpo = recurso.equals("categorias") ? "{\"nome\":\"Invasao\"}"
                : corpoTransacao((atacanteA ? categoriaA : categoriaB).getId());
        MockHttpServletRequestBuilder request = metodo.equals("PUT")
                ? put("/" + recurso + "/" + id).contentType(MediaType.APPLICATION_JSON).content(corpo)
                : delete("/" + recurso + "/" + id);
        mvc.perform(comCsrf(request, sessao)).andExpect(status().isNotFound());

        entityManager.clear();
        assertEquals(nome, categorias.findById(categoriaAlvo.getId()).orElseThrow().getNome());
        assertEquals(descricao, transacoes.findById(transacaoAlvo.getId()).orElseThrow().getDescricao());
    }

    @Test
    void naoCadastraTransacaoUsandoCategoriaDeOutraConta() throws Exception {
        mvc.perform(comCsrf(post("/transacoes").contentType(MediaType.APPLICATION_JSON)
                        .content(corpoTransacao(categoriaB.getId())), sessaoA))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.mensagem").value("Categoria não encontrada"));
        assertEquals(4, transacoes.count());
    }

    @Test
    void naoTrocaCategoriaDaPropriaTransacaoPorCategoriaDeOutraConta() throws Exception {
        mvc.perform(comCsrf(put("/transacoes/" + despesaA.getId()).contentType(MediaType.APPLICATION_JSON)
                        .content(corpoTransacao(categoriaB.getId())), sessaoA))
                .andExpect(status().isNotFound());
        entityManager.clear();
        Transacao original = transacoes.findById(despesaA.getId()).orElseThrow();
        assertEquals("Despesa A", original.getDescricao());
        assertEquals(categoriaA.getId(), original.getCategoria().getId());
        assertEquals(new BigDecimal("100.00"), original.getValor());
    }

    @Test
    void cadastroDeCategoriaIgnoraIdEDonoForjadosNoCorpo() throws Exception {
        String corpo = mapper.writeValueAsString(Map.of("id", categoriaB.getId(), "nome", "Nova A",
                "usuario", Map.of("id", b.getId(), "email", b.getEmail())));
        MvcResult resultado = mvc.perform(comCsrf(post("/categorias")
                        .contentType(MediaType.APPLICATION_JSON).content(corpo), sessaoA))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.usuario").doesNotExist()).andReturn();
        Long novoId = idDaResposta(resultado);
        assertNotEquals(categoriaB.getId(), novoId);
        entityManager.clear();
        assertEquals(a.getId(), categorias.findById(novoId).orElseThrow().getUsuario().getId());
        assertEquals("Categoria B", categorias.findById(categoriaB.getId()).orElseThrow().getNome());
    }

    @Test
    void cadastroDeTransacaoAssociaSempreOUsuarioDaSessao() throws Exception {
        String corpo = corpoTransacao(categoriaA.getId()).replace("}",
                ",\"id\":" + despesaB.getId() + ",\"usuario\":{\"id\":" + b.getId() + "}}");
        MvcResult resultado = mvc.perform(comCsrf(post("/transacoes").contentType(MediaType.APPLICATION_JSON)
                        .content(corpo), sessaoA))
                .andExpect(status().isCreated()).andExpect(jsonPath("$.usuario").doesNotExist()).andReturn();
        Long id = idDaResposta(resultado);
        assertNotEquals(despesaB.getId(), id);
        entityManager.clear();
        assertEquals(a.getId(), transacoes.findById(id).orElseThrow().getUsuario().getId());
        assertEquals("Despesa B", transacoes.findById(despesaB.getId()).orElseThrow().getDescricao());
    }

    @Test
    void atualizacaoIgnoraTentativaDeTransferirDono() throws Exception {
        String categoria = mapper.writeValueAsString(Map.of("id", categoriaB.getId(), "nome", "Editada A",
                "usuario", Map.of("id", b.getId())));
        mvc.perform(comCsrf(put("/categorias/" + categoriaA.getId()).contentType(MediaType.APPLICATION_JSON)
                        .content(categoria), sessaoA)).andExpect(status().isOk());
        String transacao = corpoTransacao(categoriaA.getId()).replace("}",
                ",\"usuario\":{\"id\":" + b.getId() + "}}");
        mvc.perform(comCsrf(put("/transacoes/" + despesaA.getId()).contentType(MediaType.APPLICATION_JSON)
                        .content(transacao), sessaoA)).andExpect(status().isOk());
        entityManager.clear();
        assertEquals(a.getId(), categorias.findById(categoriaA.getId()).orElseThrow().getUsuario().getId());
        assertEquals(a.getId(), transacoes.findById(despesaA.getId()).orElseThrow().getUsuario().getId());
    }

    @Test
    void donoPodeExcluirTransacoesECategoriaSemAfetarOutraConta() throws Exception {
        mvc.perform(comCsrf(delete("/categorias/" + categoriaA.getId()), sessaoA))
                .andExpect(status().isConflict());
        mvc.perform(comCsrf(delete("/transacoes/" + receitaA.getId()), sessaoA))
                .andExpect(status().isNoContent());
        mvc.perform(comCsrf(delete("/transacoes/" + despesaA.getId()), sessaoA))
                .andExpect(status().isNoContent());
        mvc.perform(comCsrf(delete("/categorias/" + categoriaA.getId()), sessaoA))
                .andExpect(status().isNoContent());
        assertFalse(categorias.existsById(categoriaA.getId()));
        assertEquals(2, transacoes.count());
        assertTrue(categorias.existsById(categoriaB.getId()));
    }

    @Test
    void registrosAntigosSemDonoSaoPreservadosEOcultos() throws Exception {
        Categoria antiga = criarCategoria("Legada", null);
        Transacao antigaTransacao = criarTransacao("Legada", "100000.00", TipoTransacao.RECEITA, antiga, null);
        for (Sessao sessao : new Sessao[]{sessaoA, sessaoB}) {
            mvc.perform(get("/categorias").session(sessao.http()))
                    .andExpect(status().isOk()).andExpect(jsonPath("$", hasSize(1)));
            mvc.perform(get("/transacoes").session(sessao.http()))
                    .andExpect(status().isOk()).andExpect(jsonPath("$", hasSize(2)));
            mvc.perform(get("/categorias/" + antiga.getId()).session(sessao.http()))
                    .andExpect(status().isNotFound());
            mvc.perform(get("/transacoes/" + antigaTransacao.getId()).session(sessao.http()))
                    .andExpect(status().isNotFound());
            mvc.perform(comCsrf(post("/transacoes").contentType(MediaType.APPLICATION_JSON)
                            .content(corpoTransacao(antiga.getId())), sessao))
                    .andExpect(status().isNotFound());
        }
        mvc.perform(get("/transacoes/resumo").session(sessaoA.http()))
                .andExpect(status().isOk()).andExpect(jsonPath("$.totalReceitas").value(1000.00));
        assertTrue(categorias.existsById(antiga.getId()));
        assertTrue(transacoes.existsById(antigaTransacao.getId()));
    }

    @Test
    void contaNovaVeListasVaziasEResumoZero() throws Exception {
        Usuario nova = criarUsuario("nova@example.com");
        Sessao sessao = entrar(nova.getEmail());
        mvc.perform(get("/categorias").session(sessao.http()))
                .andExpect(status().isOk()).andExpect(jsonPath("$", hasSize(0)));
        mvc.perform(get("/transacoes").session(sessao.http()))
                .andExpect(status().isOk()).andExpect(jsonPath("$", hasSize(0)));
        mvc.perform(get("/transacoes/resumo").session(sessao.http()))
                .andExpect(status().isOk()).andExpect(jsonPath("$.totalReceitas").value(0))
                .andExpect(jsonPath("$.totalDespesas").value(0)).andExpect(jsonPath("$.saldo").value(0));
    }

    @Test
    void novoLoginNaMesmaSessaoUsaOsDadosDaNovaConta() throws Exception {
        mvc.perform(comCsrf(post("/auth/login").contentType(MediaType.APPLICATION_FORM_URLENCODED)
                        .param("email", b.getEmail()).param("senha", SENHA), sessaoA))
                .andExpect(status().isOk());
        mvc.perform(get("/auth/me").session(sessaoA.http()))
                .andExpect(status().isOk()).andExpect(jsonPath("$.email").value(b.getEmail()));
        mvc.perform(get("/categorias").session(sessaoA.http()))
                .andExpect(status().isOk()).andExpect(jsonPath("$", hasSize(1)))
                .andExpect(jsonPath("$[0].id").value(categoriaB.getId()));
    }

    private Usuario criarUsuario(String email) {
        Usuario usuario = new Usuario();
        usuario.setNome(email);
        usuario.setEmail(email);
        usuario.setSenhaHash(encoder.encode(SENHA));
        return usuarios.saveAndFlush(usuario);
    }

    private Categoria criarCategoria(String nome, Usuario usuario) {
        Categoria categoria = new Categoria();
        categoria.setNome(nome);
        categoria.setUsuario(usuario);
        return categorias.saveAndFlush(categoria);
    }

    private Transacao criarTransacao(String descricao, String valor, TipoTransacao tipo,
                                    Categoria categoria, Usuario usuario) {
        Transacao transacao = new Transacao();
        transacao.setDescricao(descricao);
        transacao.setValor(new BigDecimal(valor));
        transacao.setData(LocalDate.of(2026, 11, 3));
        transacao.setTipo(tipo);
        transacao.setCategoria(categoria);
        transacao.setUsuario(usuario);
        return transacoes.saveAndFlush(transacao);
    }

    private String corpoTransacao(Long categoriaId) {
        return "{\"descricao\":\"Alterada\",\"valor\":37.25,\"data\":\"2026-11-03\","
                + "\"tipo\":\"DESPESA\",\"categoriaId\":" + categoriaId + "}";
    }

    private Long idDaResposta(MvcResult resultado) throws Exception {
        Map<?, ?> dados = mapper.readValue(resultado.getResponse().getContentAsString(), Map.class);
        return ((Number) dados.get("id")).longValue();
    }

    private MockHttpServletRequestBuilder comCsrf(MockHttpServletRequestBuilder request, Sessao sessao) {
        return request.session(sessao.http()).header(sessao.cabecalho(), sessao.token());
    }

    private Sessao entrar(String email) throws Exception {
        Sessao antes = csrf(null);
        mvc.perform(comCsrf(post("/auth/login").contentType(MediaType.APPLICATION_FORM_URLENCODED)
                        .param("email", email).param("senha", SENHA), antes))
                .andExpect(status().isOk());
        return csrf(antes.http());
    }

    private Sessao csrf(MockHttpSession http) throws Exception {
        MockHttpServletRequestBuilder request = get("/auth/csrf");
        if (http != null) request.session(http);
        MvcResult resultado = mvc.perform(request).andExpect(status().isOk()).andReturn();
        Map<?, ?> dados = mapper.readValue(resultado.getResponse().getContentAsString(), Map.class);
        return new Sessao((MockHttpSession) resultado.getRequest().getSession(false),
                dados.get("headerName").toString(), dados.get("token").toString());
    }

    private record Sessao(MockHttpSession http, String cabecalho, String token) {
    }
}
