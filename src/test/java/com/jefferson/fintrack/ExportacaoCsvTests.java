package com.jefferson.fintrack;

import com.jefferson.fintrack.model.Categoria;
import com.jefferson.fintrack.model.TipoTransacao;
import com.jefferson.fintrack.model.Transacao;
import com.jefferson.fintrack.model.Usuario;
import com.jefferson.fintrack.repository.CategoriaRepository;
import com.jefferson.fintrack.repository.TransacaoRepository;
import com.jefferson.fintrack.repository.UsuarioRepository;
import org.apache.commons.csv.CSVFormat;
import org.apache.commons.csv.CSVParser;
import org.apache.commons.csv.CSVRecord;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import org.springframework.transaction.annotation.Transactional;
import tools.jackson.databind.ObjectMapper;

import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;

import static org.hamcrest.Matchers.containsString;
import static org.junit.jupiter.api.Assertions.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest(properties = "spring.datasource.url=jdbc:h2:mem:fintrack-exportacao-csv;DB_CLOSE_DELAY=-1")
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Transactional
class ExportacaoCsvTests {

    private static final String ROTA = "/transacoes/exportar-csv";
    private static final String SENHA = "SenhaDeTeste123!";
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

    @BeforeEach
    void preparar() throws Exception {
        a = usuario("a@example.com");
        b = usuario("b@example.com");
        categoriaA = categoria(a);
        categoriaB = categoria(b);
        lancar("Fim", "2026-11-30", TipoTransacao.DESPESA, categoriaA, a);
        lancar("Inicio", "2026-11-01", TipoTransacao.DESPESA, categoriaA, a);
        lancar("Receita", "2026-11-15", TipoTransacao.RECEITA, categoriaA, a);
        lancar("Mesmo dia", "2026-11-01", TipoTransacao.DESPESA, categoriaA, a);
        lancar("Antes", "2026-10-31", TipoTransacao.DESPESA, categoriaA, a);
        lancar("Depois", "2026-12-01", TipoTransacao.DESPESA, categoriaA, a);
        lancar("Conta B", "2026-11-15", TipoTransacao.DESPESA, categoriaB, b);
        sessaoA = entrar(a.getEmail());
    }

    @Test
    void downloadTemCabecalhosCorretosEOrdenacaoPorDataEId() throws Exception {
        MvcResult resultado = mvc.perform(get(ROTA).session(sessaoA.http()))
                .andExpect(status().isOk()).andExpect(content().contentTypeCompatibleWith("text/csv"))
                .andExpect(content().encoding("UTF-8"))
                .andExpect(header().string("Content-Disposition", containsString("attachment")))
                .andExpect(header().string("Content-Disposition", containsString("transacoes.csv")))
                .andExpect(header().string("Cache-Control", containsString("no-store"))).andReturn();
        byte[] bytes = resultado.getResponse().getContentAsByteArray();
        assertEquals(bytes.length, resultado.getResponse().getContentLength());
        assertEquals(List.of("Antes", "Inicio", "Mesmo dia", "Receita", "Fim", "Depois"), descricoes(resultado));
        assertEquals(7, transacoes.count());
    }

    @Test
    void filtrosSaoCombinadosEPeriodoIncluiAsDatasLimite() throws Exception {
        assertEquals(List.of("Inicio", "Mesmo dia", "Receita", "Fim"),
                descricoes(consultar("?dataInicio=2026-11-01&dataFim=2026-11-30", sessaoA)));
        assertEquals(List.of("Inicio", "Mesmo dia", "Fim"), descricoes(consultar(
                "?dataInicio=2026-11-01&dataFim=2026-11-30&tipo=DESPESA&categoriaId=" + categoriaA.getId(), sessaoA)));
        assertEquals(List.of("Receita"), descricoes(consultar("?tipo=RECEITA", sessaoA)));
    }

    @Test
    void limitesIndividuaisDeDataFuncionam() throws Exception {
        assertEquals(List.of("Fim", "Depois"), descricoes(consultar("?dataInicio=2026-11-30", sessaoA)));
        assertEquals(List.of("Antes", "Inicio", "Mesmo dia"), descricoes(consultar("?dataFim=2026-11-01", sessaoA)));
    }

    @Test
    void outrasContasEParametrosForjadosNaoAmpliamAExportacao() throws Exception {
        assertEquals(6, descricoes(consultar("?usuarioId=" + b.getId(), sessaoA)).size());
        assertTrue(descricoes(consultar("?categoriaId=" + categoriaB.getId(), sessaoA)).isEmpty());
        assertEquals(List.of("Conta B"), descricoes(consultar("", entrar(b.getEmail()))));
    }

    @Test
    void semResultadosRetornaCsvComApenasCabecalho() throws Exception {
        var registros = registros(consultar("?dataInicio=2030-01-01", sessaoA));
        assertEquals(1, registros.size());
        assertEquals(List.of("descricao", "valor", "data", "tipo", "categoriaId"), registros.getFirst().toList());
        assertEquals(1, registros(consultar("", entrar(usuario("nova@example.com").getEmail()))).size());
    }

    @Test
    void registrosSemDonoOuComCategoriaDeOutraContaFicamFora() throws Exception {
        lancar("Legada", "2026-11-03", TipoTransacao.DESPESA, categoriaA, null);
        lancar("Inconsistente", "2026-11-03", TipoTransacao.DESPESA, categoriaB, a);
        assertEquals(6, descricoes(consultar("", sessaoA)).size());
    }

    @Test
    void exportacaoPodeSerReimportadaNaMesmaConta() throws Exception {
        byte[] bytes = consultar("", sessaoA).getResponse().getContentAsByteArray();
        mvc.perform(multipart("/transacoes/importar-csv")
                        .file(new MockMultipartFile("arquivo", "transacoes.csv", "text/csv", bytes))
                        .session(sessaoA.http()).header(sessaoA.cabecalho(), sessaoA.token()))
                .andExpect(status().isCreated()).andExpect(jsonPath("$.totalImportadas").value(6));
        assertEquals(12, transacoes.findAllByUsuarioId(a.getId()).size());
        assertEquals(1, transacoes.findAllByUsuarioId(b.getId()).size());
    }

    @Test
    void aceitaMilRegistrosERejeitaMaisSemTruncarOSDados() throws Exception {
        for (int indice = 0; indice < 994; indice++) {
            lancar("Extra " + indice, "2026-11-15", TipoTransacao.RECEITA, categoriaA, a);
        }
        assertEquals(1001, registros(consultar("", sessaoA)).size());
        lancar("Excedente", "2026-11-15", TipoTransacao.RECEITA, categoriaA, a);
        mvc.perform(get(ROTA).session(sessaoA.http())).andExpect(status().isBadRequest())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_JSON))
                .andExpect(header().doesNotExist("Content-Disposition"))
                .andExpect(jsonPath("$.mensagem").value("A exportação permite até 1000 transações. Ajuste os filtros"));
        assertEquals(5, descricoes(consultar("?tipo=DESPESA", sessaoA)).size());
    }

    @Test
    void consultaSemLoginRetorna401() throws Exception {
        mvc.perform(get(ROTA)).andExpect(status().isUnauthorized());
    }

    @ParameterizedTest
    @ValueSource(strings = {"dataInicio=2026-11-30&dataFim=2026-11-01", "categoriaId=0", "categoriaId=-1",
            "tipo=INVALIDO", "dataInicio=03-11-2026", "dataFim=2026-02-30", "categoriaId=abc"})
    void filtrosInvalidosRetornam400(String filtro) throws Exception {
        mvc.perform(get(ROTA + "?" + filtro).session(sessaoA.http())).andExpect(status().isBadRequest());
    }

    private MvcResult consultar(String filtros, Sessao sessao) throws Exception {
        return mvc.perform(get(ROTA + filtros).session(sessao.http())).andExpect(status().isOk()).andReturn();
    }

    private List<String> descricoes(MvcResult resultado) throws Exception {
        return registros(resultado).stream().skip(1).map(registro -> registro.get(0)).toList();
    }

    private List<CSVRecord> registros(MvcResult resultado) throws Exception {
        String texto = new String(resultado.getResponse().getContentAsByteArray(), StandardCharsets.UTF_8);
        assertTrue(texto.startsWith("\uFEFF"));
        try (CSVParser parser = CSVParser.parse(texto.substring(1), CSVFormat.RFC4180.builder().setDelimiter(';').get())) {
            return parser.getRecords();
        }
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

    private void lancar(String descricao, String data, TipoTransacao tipo, Categoria categoria, Usuario usuario) {
        Transacao transacao = new Transacao();
        transacao.setDescricao(descricao);
        transacao.setData(LocalDate.parse(data));
        transacao.setValor(new BigDecimal("187.50"));
        transacao.setTipo(tipo);
        transacao.setCategoria(categoria);
        transacao.setUsuario(usuario);
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
