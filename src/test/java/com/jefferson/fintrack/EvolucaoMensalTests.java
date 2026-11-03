package com.jefferson.fintrack;

import com.jefferson.fintrack.dto.EvolucaoMensalResponse;
import com.jefferson.fintrack.dto.ResumoFinanceiroResponse;
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
import static org.junit.jupiter.api.Assertions.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest(properties = "spring.datasource.url=jdbc:h2:mem:fintrack-evolucao-mensal;DB_CLOSE_DELAY=-1")
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Transactional
class EvolucaoMensalTests {

    private static final String SENHA = "SenhaDeTeste123!";
    private static final String ROTA = "/transacoes/evolucao-mensal";

    @Autowired private MockMvc mvc;
    @Autowired private ObjectMapper mapper;
    @Autowired private PasswordEncoder encoder;
    @Autowired private UsuarioRepository usuarios;
    @Autowired private CategoriaRepository categorias;
    @Autowired private TransacaoRepository transacoes;

    private Usuario a;
    private Usuario b;
    private Categoria categoriaA;
    private Categoria categoriaB;
    private Sessao sessaoA;
    private Sessao sessaoB;

    @BeforeEach
    void preparar() throws Exception {
        a = usuario("a@example.com");
        b = usuario("b@example.com");
        categoriaA = categoria(a);
        categoriaB = categoria(b);
        lancar(a, categoriaA, "2025-12-31", TipoTransacao.RECEITA, "90000.00");
        lancar(a, categoriaA, "2026-01-01", TipoTransacao.RECEITA, "1000.10");
        lancar(a, categoriaA, "2026-01-31", TipoTransacao.RECEITA, "0.20");
        lancar(a, categoriaA, "2026-01-15", TipoTransacao.DESPESA, "100.05");
        lancar(a, categoriaA, "2026-02-01", TipoTransacao.DESPESA, "50.10");
        lancar(a, categoriaA, "2026-11-03", TipoTransacao.RECEITA, "200.20");
        lancar(a, categoriaA, "2026-12-31", TipoTransacao.DESPESA, "75.00");
        lancar(a, categoriaA, "2027-01-01", TipoTransacao.RECEITA, "100000.00");
        lancar(b, categoriaB, "2026-01-01", TipoTransacao.RECEITA, "999999.00");
        lancar(b, categoriaB, "2026-01-15", TipoTransacao.DESPESA, "888888.00");
        sessaoA = entrar(a.getEmail());
        sessaoB = entrar(b.getEmail());
    }

    @Test
    void retornaDozeMesesEmOrdemComTotaisESaldosDoMes() throws Exception {
        EvolucaoMensalResponse[] meses = ler(consultar(2026, sessaoA).andReturn());
        assertEquals(12, meses.length);
        for (int indice = 0; indice < 12; indice++) {
            assertEquals(2026, meses[indice].ano());
            assertEquals(indice + 1, meses[indice].mes());
        }
        conferir(meses[0], "1000.30", "100.05", "900.25");
        conferir(meses[1], "0.00", "50.10", "-50.10");
        conferir(meses[2], "0.00", "0.00", "0.00");
        conferir(meses[10], "200.20", "0.00", "200.20");
        conferir(meses[11], "0.00", "75.00", "-75.00");
    }

    @Test
    void respostaContemApenasOsCamposDoRelatorio() throws Exception {
        consultar(2026, sessaoA)
                .andExpect(jsonPath("$", hasSize(12)))
                .andExpect(jsonPath("$[0]", aMapWithSize(5)))
                .andExpect(jsonPath("$[0].ano").value(2026))
                .andExpect(jsonPath("$[0].mes").value(1))
                .andExpect(jsonPath("$[0].usuario").doesNotExist());
    }

    @Test
    void cadaAnoIncluiSomenteSuasPropriasDatasLimite() throws Exception {
        EvolucaoMensalResponse[] anterior = ler(consultar(2025, sessaoA).andReturn());
        conferir(anterior[11], "90000.00", "0.00", "90000.00");
        conferir(anterior[0], "0.00", "0.00", "0.00");
        EvolucaoMensalResponse[] seguinte = ler(consultar(2027, sessaoA).andReturn());
        conferir(seguinte[0], "100000.00", "0.00", "100000.00");
        conferir(seguinte[11], "0.00", "0.00", "0.00");
    }

    @Test
    void somaDosMesesConfereComOResumoDoMesmoAno() throws Exception {
        EvolucaoMensalResponse[] meses = ler(consultar(2026, sessaoA).andReturn());
        MvcResult resultado = mvc.perform(get("/transacoes/resumo?dataInicio=2026-01-01&dataFim=2026-12-31")
                        .session(sessaoA.http())).andExpect(status().isOk()).andReturn();
        ResumoFinanceiroResponse resumo = mapper.readValue(resultado.getResponse().getContentAsString(),
                ResumoFinanceiroResponse.class);
        BigDecimal receitas = new BigDecimal("0.00");
        BigDecimal despesas = new BigDecimal("0.00");
        BigDecimal saldo = new BigDecimal("0.00");
        for (EvolucaoMensalResponse mes : meses) {
            receitas = receitas.add(mes.totalReceitas());
            despesas = despesas.add(mes.totalDespesas());
            saldo = saldo.add(mes.saldo());
        }
        assertEquals(resumo.totalReceitas(), receitas);
        assertEquals(resumo.totalDespesas(), despesas);
        assertEquals(resumo.saldo(), saldo);
    }

    @Test
    void outraContaVeApenasSeusPropriosTotais() throws Exception {
        EvolucaoMensalResponse[] meses = ler(consultar(2026, sessaoB).andReturn());
        conferir(meses[0], "999999.00", "888888.00", "111111.00");
        for (int indice = 1; indice < 12; indice++) {
            conferir(meses[indice], "0.00", "0.00", "0.00");
        }
        conferir(ler(consultar(2026, sessaoA).andReturn())[0], "1000.30", "100.05", "900.25");
    }

    @Test
    void parametroDeUsuarioNaoMudaAContaDaSessao() throws Exception {
        MvcResult resultado = mvc.perform(get(ROTA + "?ano=2026&usuarioId=" + b.getId())
                        .session(sessaoA.http())).andExpect(status().isOk()).andReturn();
        conferir(ler(resultado)[0], "1000.30", "100.05", "900.25");
    }

    @Test
    void ignoraTransacoesLegadasSemDono() throws Exception {
        lancar(null, categoriaA, "2026-01-20", TipoTransacao.RECEITA, "70000.00");
        lancar(null, categoriaB, "2026-02-20", TipoTransacao.DESPESA, "60000.00");
        EvolucaoMensalResponse[] meses = ler(consultar(2026, sessaoA).andReturn());
        conferir(meses[0], "1000.30", "100.05", "900.25");
        conferir(meses[1], "0.00", "50.10", "-50.10");
    }

    @Test
    void anoSemMovimentoRetornaDozeMesesZerados() throws Exception {
        EvolucaoMensalResponse[] meses = ler(consultar(2030, sessaoA).andReturn());
        assertEquals(12, meses.length);
        for (EvolucaoMensalResponse mes : meses) {
            assertEquals(2030, mes.ano());
            conferir(mes, "0.00", "0.00", "0.00");
        }
    }

    @Test
    void contaNovaRecebeDozeMesesZerados() throws Exception {
        Sessao nova = entrar(usuario("nova@example.com").getEmail());
        for (EvolucaoMensalResponse mes : ler(consultar(2026, nova).andReturn())) {
            conferir(mes, "0.00", "0.00", "0.00");
        }
    }

    @Test
    void receitasEDespesasIguaisZeramOSaldoDoMes() throws Exception {
        lancar(a, categoriaA, "2026-01-31", TipoTransacao.DESPESA, "900.25");
        conferir(ler(consultar(2026, sessaoA).andReturn())[0], "1000.30", "1000.30", "0.00");
    }

    @Test
    void incluiDiaVinteENoveDeFevereiroEmAnoBissexto() throws Exception {
        lancar(a, categoriaA, "2024-02-29", TipoTransacao.DESPESA, "1.10");
        conferir(ler(consultar(2024, sessaoA).andReturn())[1], "0.00", "1.10", "-1.10");
    }

    @ParameterizedTest
    @ValueSource(ints = {1, 9999})
    void aceitaOsLimitesDeAno(int ano) throws Exception {
        EvolucaoMensalResponse[] meses = ler(consultar(ano, sessaoA).andReturn());
        assertEquals(12, meses.length);
        for (EvolucaoMensalResponse mes : meses) {
            assertEquals(ano, mes.ano());
            conferir(mes, "0.00", "0.00", "0.00");
        }
    }

    @ParameterizedTest
    @ValueSource(ints = {0, -1, 10000, Integer.MAX_VALUE, Integer.MIN_VALUE})
    void anoForaDoIntervaloRetorna400ComMensagem(int ano) throws Exception {
        mvc.perform(get(ROTA).param("ano", Integer.toString(ano)).session(sessaoA.http()))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.mensagem").value("O ano deve estar entre 1 e 9999"));
    }

    @ParameterizedTest
    @ValueSource(strings = {"", "?ano=abc", "?ano=2026.5", "?ano=", "?ano=999999999999999999"})
    void anoAusenteOuMalformadoRetorna400(String parametro) throws Exception {
        mvc.perform(get(ROTA + parametro).session(sessaoA.http())).andExpect(status().isBadRequest());
    }

    @Test
    void consultaSemLoginRetorna401() throws Exception {
        mvc.perform(get(ROTA + "?ano=2026")).andExpect(status().isUnauthorized());
    }

    private ResultActions consultar(int ano, Sessao sessao) throws Exception {
        return mvc.perform(get(ROTA).param("ano", Integer.toString(ano)).session(sessao.http()))
                .andExpect(status().isOk());
    }

    private EvolucaoMensalResponse[] ler(MvcResult resultado) throws Exception {
        return mapper.readValue(resultado.getResponse().getContentAsString(), EvolucaoMensalResponse[].class);
    }

    private void conferir(EvolucaoMensalResponse mes, String receitas, String despesas, String saldo) {
        assertEquals(new BigDecimal(receitas), mes.totalReceitas());
        assertEquals(new BigDecimal(despesas), mes.totalDespesas());
        assertEquals(new BigDecimal(saldo), mes.saldo());
    }

    private Usuario usuario(String email) {
        Usuario usuario = new Usuario();
        usuario.setNome(email);
        usuario.setEmail(email);
        usuario.setSenhaHash(encoder.encode(SENHA));
        return usuarios.saveAndFlush(usuario);
    }

    private Categoria categoria(Usuario usuario) {
        Categoria categoria = new Categoria();
        categoria.setNome("Categoria de teste");
        categoria.setUsuario(usuario);
        return categorias.saveAndFlush(categoria);
    }

    private void lancar(Usuario usuario, Categoria categoria, String data, TipoTransacao tipo, String valor) {
        Transacao transacao = new Transacao();
        transacao.setDescricao("Teste de evolução mensal");
        transacao.setUsuario(usuario);
        transacao.setCategoria(categoria);
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
