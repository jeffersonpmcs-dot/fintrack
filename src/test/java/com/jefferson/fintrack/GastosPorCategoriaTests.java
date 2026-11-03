package com.jefferson.fintrack;

import com.jefferson.fintrack.dto.GastoPorCategoriaResponse;
import com.jefferson.fintrack.model.Categoria;
import com.jefferson.fintrack.model.TipoTransacao;
import com.jefferson.fintrack.model.Transacao;
import com.jefferson.fintrack.model.Usuario;
import com.jefferson.fintrack.repository.CategoriaRepository;
import com.jefferson.fintrack.repository.TransacaoRepository;
import com.jefferson.fintrack.repository.UsuarioRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
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
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import org.springframework.transaction.annotation.Transactional;
import tools.jackson.databind.ObjectMapper;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.Map;

import static org.hamcrest.Matchers.*;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest(properties = "spring.datasource.url=jdbc:h2:mem:fintrack-gastos-categoria;DB_CLOSE_DELAY=-1")
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Transactional
class GastosPorCategoriaTests {

    private static final String SENHA = "SenhaDeTeste123!";
    private static final String ROTA = "/transacoes/gastos-por-categoria";

    @Autowired private MockMvc mvc;
    @Autowired private ObjectMapper mapper;
    @Autowired private PasswordEncoder encoder;
    @Autowired private UsuarioRepository usuarios;
    @Autowired private CategoriaRepository categorias;
    @Autowired private TransacaoRepository transacoes;

    private Usuario a;
    private Usuario b;
    private Categoria alimentacao;
    private Categoria transporte;
    private Categoria alimentacaoB;
    private Sessao sessaoA;
    private Sessao sessaoB;

    @BeforeEach
    void preparar() throws Exception {
        a = usuario("a@example.com");
        b = usuario("b@example.com");
        alimentacao = categoria("Alimentação", a);
        transporte = categoria("Transporte", a);
        categoria("Sem movimento", a);
        Categoria soReceita = categoria("Somente receita", a);
        alimentacaoB = categoria("Alimentação", b);

        transacao(alimentacao, a, "2026-10-31", TipoTransacao.DESPESA, "5.00");
        transacao(alimentacao, a, "2026-11-01", TipoTransacao.DESPESA, "100.10");
        transacao(alimentacao, a, "2026-11-15", TipoTransacao.DESPESA, "0.20");
        transacao(alimentacao, a, "2026-11-30", TipoTransacao.DESPESA, "20.00");
        transacao(alimentacao, a, "2026-12-01", TipoTransacao.DESPESA, "7.00");
        transacao(transporte, a, "2026-11-15", TipoTransacao.DESPESA, "50.00");
        transacao(alimentacao, a, "2026-11-16", TipoTransacao.RECEITA, "9999.00");
        transacao(soReceita, a, "2026-11-16", TipoTransacao.RECEITA, "888.00");
        transacao(alimentacaoB, b, "2026-11-15", TipoTransacao.DESPESA, "99999.00");

        sessaoA = entrar(a.getEmail());
        sessaoB = entrar(b.getEmail());
    }

    @Test
    void somaDespesasPorCategoriaNoPeriodoInclusivo() throws Exception {
        consultar("?dataInicio=2026-11-01&dataFim=2026-11-30", sessaoA)
                .andExpect(jsonPath("$", hasSize(2)))
                .andExpect(jsonPath("$[0]", aMapWithSize(3)))
                .andExpect(jsonPath("$[0].categoriaId").value(alimentacao.getId()))
                .andExpect(jsonPath("$[0].categoriaNome").value("Alimentação"))
                .andExpect(jsonPath("$[0].totalDespesas").value(120.30))
                .andExpect(jsonPath("$[1].categoriaId").value(transporte.getId()))
                .andExpect(jsonPath("$[1].totalDespesas").value(50.00));
    }

    @Test
    void semDatasConsideraTodoOHistoricoEExcluiReceitasECategoriasSemDespesas() throws Exception {
        consultar("", sessaoA)
                .andExpect(jsonPath("$", hasSize(2)))
                .andExpect(jsonPath("$[0].totalDespesas").value(132.30))
                .andExpect(jsonPath("$[1].totalDespesas").value(50.00));
    }

    @Test
    void aceitaSomenteDataInicial() throws Exception {
        consultar("?dataInicio=2026-11-30", sessaoA)
                .andExpect(jsonPath("$", hasSize(1)))
                .andExpect(jsonPath("$[0].categoriaId").value(alimentacao.getId()))
                .andExpect(jsonPath("$[0].totalDespesas").value(27.00));
    }

    @Test
    void aceitaSomenteDataFinal() throws Exception {
        consultar("?dataFim=2026-11-01", sessaoA)
                .andExpect(jsonPath("$", hasSize(1)))
                .andExpect(jsonPath("$[0].totalDespesas").value(105.10));
    }

    @Test
    void aceitaPeriodoDeUmDiaEOrdenaPeloMaiorGasto() throws Exception {
        consultar("?dataInicio=2026-11-15&dataFim=2026-11-15", sessaoA)
                .andExpect(jsonPath("$", hasSize(2)))
                .andExpect(jsonPath("$[0].categoriaId").value(transporte.getId()))
                .andExpect(jsonPath("$[0].totalDespesas").value(50.00))
                .andExpect(jsonPath("$[1].totalDespesas").value(0.20));
    }

    @ParameterizedTest
    @ValueSource(strings = {"?dataInicio=2027-01-01&dataFim=2027-01-31",
            "?dataInicio=2026-11-16&dataFim=2026-11-16"})
    void periodoSemDespesasRetornaListaVazia(String periodo) throws Exception {
        consultar(periodo, sessaoA).andExpect(jsonPath("$", hasSize(0)));
    }

    @Test
    void contasComCategoriaDeMesmoNomeNaoCompartilhamTotais() throws Exception {
        consultar("", sessaoA).andExpect(jsonPath("$[0].totalDespesas").value(132.30));
        consultar("", sessaoB)
                .andExpect(jsonPath("$", hasSize(1)))
                .andExpect(jsonPath("$[0].categoriaId").value(alimentacaoB.getId()))
                .andExpect(jsonPath("$[0].totalDespesas").value(99999.00));
    }

    @Test
    void categoriasDistintasComMesmoNomeSaoAgrupadasPorId() throws Exception {
        Categoria outra = categoria("Alimentação", a);
        transacao(outra, a, "2026-11-15", TipoTransacao.DESPESA, "10.00");
        consultar("?dataInicio=2026-11-01&dataFim=2026-11-30", sessaoA)
                .andExpect(jsonPath("$", hasSize(3)))
                .andExpect(jsonPath("$[0].categoriaId").value(alimentacao.getId()))
                .andExpect(jsonPath("$[0].totalDespesas").value(120.30))
                .andExpect(jsonPath("$[2].categoriaId").value(outra.getId()))
                .andExpect(jsonPath("$[2].totalDespesas").value(10.00));
    }

    @Test
    void empateDeTotaisUsaNomeEIdComoDesempate() throws Exception {
        Categoria primeira = categoria("AAA", a);
        Categoria segunda = categoria("AAA", a);
        transacao(segunda, a, "2026-11-15", TipoTransacao.DESPESA, "50.00");
        transacao(primeira, a, "2026-11-15", TipoTransacao.DESPESA, "50.00");
        consultar("?dataInicio=2026-11-01&dataFim=2026-11-30", sessaoA)
                .andExpect(jsonPath("$", hasSize(4)))
                .andExpect(jsonPath("$[1].categoriaId").value(primeira.getId()))
                .andExpect(jsonPath("$[2].categoriaId").value(segunda.getId()))
                .andExpect(jsonPath("$[3].categoriaId").value(transporte.getId()));
    }

    @Test
    void naoIncluiRegistrosSemDonoNemExibeCategoriaDeOutraContaEmDadoInconsistente() throws Exception {
        Categoria legada = categoria("Legada", null);
        transacao(legada, null, "2026-11-15", TipoTransacao.DESPESA, "70000.00");
        transacao(alimentacao, null, "2026-11-15", TipoTransacao.DESPESA, "60000.00");
        transacao(alimentacaoB, a, "2026-11-15", TipoTransacao.DESPESA, "50000.00");
        consultar("", sessaoA)
                .andExpect(jsonPath("$", hasSize(2)))
                .andExpect(jsonPath("$[0].categoriaId").value(alimentacao.getId()))
                .andExpect(jsonPath("$[0].totalDespesas").value(132.30));
    }

    @Test
    void contaSemMovimentoRetornaListaVazia() throws Exception {
        Usuario nova = usuario("nova@example.com");
        consultar("", entrar(nova.getEmail())).andExpect(jsonPath("$", hasSize(0)));
    }

    @Test
    void preservaPrecisaoDecimalNoResultado() throws Exception {
        MvcResult resultado = consultar("?dataInicio=2026-11-01&dataFim=2026-11-30", sessaoA).andReturn();
        GastoPorCategoriaResponse[] dados = mapper.readValue(resultado.getResponse().getContentAsString(),
                GastoPorCategoriaResponse[].class);
        assertEquals(new BigDecimal("120.30"), dados[0].totalDespesas());
    }

    @Test
    void periodoInvertidoRetorna400ComMensagem() throws Exception {
        mvc.perform(get(ROTA + "?dataInicio=2026-11-30&dataFim=2026-11-01").session(sessaoA.http()))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.mensagem").value("A data inicial não pode ser posterior à data final"));
    }

    @ParameterizedTest
    @ValueSource(strings = {"dataInicio=03-11-2026", "dataFim=2026-02-30", "dataInicio=invalida"})
    void datasMalformadasRetornam400(String parametro) throws Exception {
        mvc.perform(get(ROTA + "?" + parametro).session(sessaoA.http())).andExpect(status().isBadRequest());
    }

    @Test
    void consultaSemLoginRetorna401() throws Exception {
        mvc.perform(get(ROTA)).andExpect(status().isUnauthorized());
    }

    private ResultActions consultar(String filtro, Sessao sessao) throws Exception {
        return mvc.perform(get(ROTA + filtro).session(sessao.http())).andExpect(status().isOk());
    }

    private Usuario usuario(String email) {
        Usuario usuario = new Usuario();
        usuario.setNome(email);
        usuario.setEmail(email);
        usuario.setSenhaHash(encoder.encode(SENHA));
        return usuarios.saveAndFlush(usuario);
    }

    private Categoria categoria(String nome, Usuario usuario) {
        Categoria categoria = new Categoria();
        categoria.setNome(nome);
        categoria.setUsuario(usuario);
        return categorias.saveAndFlush(categoria);
    }

    private void transacao(Categoria categoria, Usuario usuario, String data, TipoTransacao tipo, String valor) {
        Transacao transacao = new Transacao();
        transacao.setDescricao("Teste de gastos");
        transacao.setCategoria(categoria);
        transacao.setUsuario(usuario);
        transacao.setData(LocalDate.parse(data));
        transacao.setTipo(tipo);
        transacao.setValor(new BigDecimal(valor));
        transacoes.saveAndFlush(transacao);
    }

    private Sessao entrar(String email) throws Exception {
        Sessao antes = csrf(null);
        mvc.perform(post("/auth/login").session(antes.http()).header(antes.cabecalho(), antes.token())
                        .contentType(MediaType.APPLICATION_FORM_URLENCODED)
                        .param("email", email).param("senha", SENHA))
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
