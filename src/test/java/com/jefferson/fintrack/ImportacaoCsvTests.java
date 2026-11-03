package com.jefferson.fintrack;

import com.jefferson.fintrack.model.Categoria;
import com.jefferson.fintrack.model.Transacao;
import com.jefferson.fintrack.model.Usuario;
import com.jefferson.fintrack.repository.CategoriaRepository;
import com.jefferson.fintrack.repository.TransacaoRepository;
import com.jefferson.fintrack.repository.UsuarioRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.request.MockMultipartHttpServletRequestBuilder;
import tools.jackson.databind.ObjectMapper;

import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

// Sem transação externa de teste: commits e rollbacks pertencem ao serviço real.
@SpringBootTest(properties = "spring.datasource.url=jdbc:h2:mem:fintrack-importacao-csv;DB_CLOSE_DELAY=-1")
@AutoConfigureMockMvc
@ActiveProfiles("test")
class ImportacaoCsvTests {

    private static final String ROTA = "/transacoes/importar-csv";
    private static final String HEADER = "descricao;valor;data;tipo;categoriaId\n";
    private static final String SENHA = "SenhaDeTeste123!";

    @Autowired private MockMvc mvc;
    @Autowired private PasswordEncoder encoder;
    @Autowired private ObjectMapper mapper;
    @Autowired private UsuarioRepository usuarios;
    @Autowired private CategoriaRepository categorias;
    @Autowired private TransacaoRepository transacoes;
    @Autowired private JdbcTemplate jdbc;

    private Usuario a;
    private Usuario b;
    private Categoria categoriaA;
    private Categoria categoriaB;
    private Sessao sessaoA;

    @BeforeEach
    void prepararBancoTemporario() throws Exception {
        transacoes.deleteAll();
        categorias.deleteAll();
        usuarios.deleteAll();
        a = usuario("a@example.com");
        b = usuario("b@example.com");
        categoriaA = categoria("Categoria A", a);
        categoriaB = categoria("Categoria B", b);
        sessaoA = entrar(a.getEmail());
    }

    @Test
    void importaReceitaEDespesaComPrecisaoEUsuarioDaSessao() throws Exception {
        String csv = HEADER + linha("Salário", "3500.00", "RECEITA", categoriaA.getId())
                + linha("Mercado", "187.50", "DESPESA", categoriaA.getId());
        mvc.perform(upload(csv, sessaoA)).andExpect(status().isCreated())
                .andExpect(jsonPath("$.totalImportadas").value(2));
        var gravadas = transacoes.findAllByUsuarioId(a.getId());
        assertEquals(2, gravadas.size());
        assertTrue(gravadas.stream().allMatch(t -> t.getCategoria().getId().equals(categoriaA.getId())));
        Transacao despesa = gravadas.stream().filter(t -> t.getDescricao().equals("Mercado")).findFirst().orElseThrow();
        assertEquals(new BigDecimal("187.50"), despesa.getValor());
        assertTrue(transacoes.findAllByUsuarioId(b.getId()).isEmpty());
        Sessao sessaoB = entrar(b.getEmail());
        mvc.perform(get("/transacoes").session(sessaoB.http()))
                .andExpect(status().isOk()).andExpect(content().json("[]"));
        mvc.perform(get("/transacoes/resumo").session(sessaoA.http()))
                .andExpect(status().isOk()).andExpect(jsonPath("$.saldo").value(3312.50));
    }

    @Test
    void registroInvalidoDepoisDeUmValidoNaoGravaNada() throws Exception {
        String csv = HEADER + linha("Válida", "1.00", "DESPESA", categoriaA.getId())
                + linha("Inválida", "0", "DESPESA", categoriaA.getId());
        mvc.perform(upload(csv, sessaoA)).andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.linha").value(3));
        assertEquals(0, transacoes.count());
    }

    @Test
    void categoriaDeOutraContaDepoisDeLinhaValidaRejeitaArquivoInteiro() throws Exception {
        String csv = HEADER + linha("Válida", "1.00", "DESPESA", categoriaA.getId())
                + linha("Outra conta", "2.00", "DESPESA", categoriaB.getId());
        mvc.perform(upload(csv, sessaoA)).andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.linha").value(3))
                .andExpect(jsonPath("$.mensagem").value("Categoria não encontrada para esta conta"));
        assertEquals(0, transacoes.count());
    }

    @Test
    void categoriaInexistenteOuSemDonoTambemERejeitada() throws Exception {
        Categoria legada = categoria("Legada", null);
        for (long id : new long[]{99999999, legada.getId()}) {
            mvc.perform(upload(HEADER + linha("Teste", "1.00", "DESPESA", id), sessaoA))
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.linha").value(2))
                    .andExpect(jsonPath("$.mensagem").value("Categoria não encontrada para esta conta"));
        }
        assertEquals(0, transacoes.count());
    }

    @Test
    void falhaDeIntegridadeNaSegundaGravacaoDesfazAPrimeira() throws Exception {
        jdbc.execute("alter table transacao add constraint ck_csv_teste check (descricao <> 'Falha controlada')");
        try {
            String csv = HEADER + linha("Primeira válida", "1.00", "DESPESA", categoriaA.getId())
                    + linha("Falha controlada", "2.00", "DESPESA", categoriaA.getId());
            mvc.perform(upload(csv, sessaoA)).andExpect(status().isConflict());
            assertEquals(0, transacoes.count());
        } finally {
            jdbc.execute("alter table transacao drop constraint ck_csv_teste");
        }
    }

    @Test
    void parametrosExternosNaoMudamODonoDoArquivo() throws Exception {
        String csv = HEADER + linha("Conta A", "1.00", "DESPESA", categoriaA.getId());
        mvc.perform(upload(csv, sessaoA).param("usuarioId", b.getId().toString()))
                .andExpect(status().isCreated());
        assertEquals(1, transacoes.findAllByUsuarioId(a.getId()).size());
        assertTrue(transacoes.findAllByUsuarioId(b.getId()).isEmpty());
    }

    @Test
    void colunaDeDonoNoCsvRejeitaArquivoSemPersistir() throws Exception {
        String csv = HEADER.stripTrailing() + ";usuarioId\n"
                + linha("Teste", "1.00", "DESPESA", categoriaA.getId()).stripTrailing() + ";" + b.getId();
        mvc.perform(upload(csv, sessaoA)).andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.linha").value(1));
        assertEquals(0, transacoes.count());
    }

    @Test
    void reenvioDoMesmoArquivoCriaNovasTransacoes() throws Exception {
        String csv = HEADER + linha("Compra", "1.00", "DESPESA", categoriaA.getId());
        mvc.perform(upload(csv, sessaoA)).andExpect(status().isCreated());
        mvc.perform(upload(csv, sessaoA)).andExpect(status().isCreated());
        assertEquals(2, transacoes.count());
    }

    @Test
    void importacaoExigeLoginETokenCsrf() throws Exception {
        String csv = HEADER + linha("Teste", "1.00", "DESPESA", categoriaA.getId());
        Sessao anonima = csrf(null);
        mvc.perform(upload(csv, anonima)).andExpect(status().isUnauthorized());
        mvc.perform(multipart(ROTA).file(arquivo(csv)).session(sessaoA.http()))
                .andExpect(status().isForbidden());
        assertEquals(0, transacoes.count());
    }

    @Test
    void arquivoVazioOuAusenteRetorna400() throws Exception {
        mvc.perform(upload("", sessaoA)).andExpect(status().isBadRequest());
        mvc.perform(multipart(ROTA).session(sessaoA.http()).header(sessaoA.cabecalho(), sessaoA.token()))
                .andExpect(status().isBadRequest());
        assertEquals(0, transacoes.count());
    }

    @Test
    void arquivoMaiorQueUmMegabyteRetorna413() throws Exception {
        MockMultipartFile grande = new MockMultipartFile("arquivo", "grande.csv", "text/csv", new byte[1_048_577]);
        mvc.perform(multipart(ROTA).file(grande).session(sessaoA.http())
                        .header(sessaoA.cabecalho(), sessaoA.token()))
                .andExpect(status().isPayloadTooLarge());
        assertEquals(0, transacoes.count());
    }

    private MockMultipartHttpServletRequestBuilder upload(String csv, Sessao sessao) {
        MockMultipartHttpServletRequestBuilder request = multipart(ROTA).file(arquivo(csv));
        request.session(sessao.http()).header(sessao.cabecalho(), sessao.token());
        return request;
    }

    private MockMultipartFile arquivo(String csv) {
        return new MockMultipartFile("arquivo", "teste.csv", "text/csv", csv.getBytes(StandardCharsets.UTF_8));
    }

    private String linha(String descricao, String valor, String tipo, long categoriaId) {
        return descricao + ";" + valor + ";2026-11-03;" + tipo + ";" + categoriaId + "\n";
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

    private Sessao entrar(String email) throws Exception {
        Sessao antes = csrf(null);
        mvc.perform(post("/auth/login").session(antes.http()).header(antes.cabecalho(), antes.token())
                        .contentType(MediaType.APPLICATION_FORM_URLENCODED)
                        .param("email", email).param("senha", SENHA))
                .andExpect(status().isOk());
        return csrf(antes.http());
    }

    private Sessao csrf(MockHttpSession http) throws Exception {
        var request = get("/auth/csrf");
        if (http != null) request.session(http);
        MvcResult resultado = mvc.perform(request).andExpect(status().isOk()).andReturn();
        Map<?, ?> dados = mapper.readValue(resultado.getResponse().getContentAsString(), Map.class);
        return new Sessao((MockHttpSession) resultado.getRequest().getSession(false),
                dados.get("headerName").toString(), dados.get("token").toString());
    }

    private record Sessao(MockHttpSession http, String cabecalho, String token) {
    }
}
