package com.jefferson.fintrack;

import com.jefferson.fintrack.controller.TransacaoController;
import com.jefferson.fintrack.exception.GlobalExceptionHandler;
import com.jefferson.fintrack.model.Categoria;
import com.jefferson.fintrack.model.TipoTransacao;
import com.jefferson.fintrack.model.Transacao;
import com.jefferson.fintrack.repository.CategoriaRepository;
import com.jefferson.fintrack.repository.TransacaoRepository;
import com.jefferson.fintrack.service.TransacaoService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDate;

import static org.hamcrest.Matchers.containsInAnyOrder;
import static org.hamcrest.Matchers.hasSize;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest(properties = {
        "spring.datasource.url=jdbc:h2:mem:fintrack-filtros;DB_CLOSE_DELAY=-1",
        "spring.datasource.username=sa",
        "spring.datasource.password=",
        "spring.datasource.driver-class-name=org.h2.Driver",
        "spring.jpa.hibernate.ddl-auto=create-drop"
})
@Transactional
class TransacaoFiltrosTests {

    @Autowired
    private TransacaoRepository transacoes;
    @Autowired
    private CategoriaRepository categorias;
    @Autowired
    private TransacaoService service;

    private MockMvc mvc;
    private Long categoriaId;

    @BeforeEach
    void configurar() {
        mvc = MockMvcBuilders.standaloneSetup(new TransacaoController(service))
                .setControllerAdvice(new GlobalExceptionHandler()).build();
        Categoria alimentacao = criarCategoria("Alimentação");
        Categoria transporte = criarCategoria("Transporte");
        categoriaId = alimentacao.getId();

        criarTransacao("Antes", "2026-09-30", TipoTransacao.DESPESA, alimentacao);
        criarTransacao("Inicio", "2026-10-01", TipoTransacao.DESPESA, alimentacao);
        criarTransacao("Receita", "2026-10-15", TipoTransacao.RECEITA, alimentacao);
        criarTransacao("Meio", "2026-10-15", TipoTransacao.DESPESA, alimentacao);
        criarTransacao("Fim", "2026-10-31", TipoTransacao.DESPESA, transporte);
        criarTransacao("Depois", "2026-11-01", TipoTransacao.DESPESA, alimentacao);
    }

    @Test
    void semFiltrosContinuaListandoTodas() throws Exception {
        consultar("").andExpect(jsonPath("$", hasSize(6)));
    }

    @Test
    void periodoIncluiAsDuasDatasLimite() throws Exception {
        consultar("?dataInicio=2026-10-01&dataFim=2026-10-31")
                .andExpect(jsonPath("$[*].descricao", containsInAnyOrder("Inicio", "Receita", "Meio", "Fim")));
    }

    @Test
    void aceitaSomenteDataInicial() throws Exception {
        consultar("?dataInicio=2026-10-31")
                .andExpect(jsonPath("$[*].descricao", containsInAnyOrder("Fim", "Depois")));
    }

    @Test
    void aceitaSomenteDataFinal() throws Exception {
        consultar("?dataFim=2026-10-01")
                .andExpect(jsonPath("$[*].descricao", containsInAnyOrder("Antes", "Inicio")));
    }

    @Test
    void aceitaPeriodoDeUmUnicoDia() throws Exception {
        consultar("?dataInicio=2026-10-15&dataFim=2026-10-15")
                .andExpect(jsonPath("$[*].descricao", containsInAnyOrder("Receita", "Meio")));
    }

    @Test
    void filtraSomenteCategoria() throws Exception {
        consultar("?categoriaId=" + categoriaId)
                .andExpect(jsonPath("$[*].descricao", containsInAnyOrder("Antes", "Inicio", "Receita", "Meio", "Depois")));
    }

    @Test
    void filtraSomenteTipo() throws Exception {
        consultar("?tipo=RECEITA")
                .andExpect(jsonPath("$[*].descricao", containsInAnyOrder("Receita")));
    }

    @Test
    void combinaPeriodoCategoriaETipo() throws Exception {
        consultar("?dataInicio=2026-10-01&dataFim=2026-10-31&categoriaId=" + categoriaId + "&tipo=DESPESA")
                .andExpect(jsonPath("$[*].descricao", containsInAnyOrder("Inicio", "Meio")));
    }

    @Test
    void filtroSemCorrespondenciasRetornaListaVazia() throws Exception {
        consultar("?categoriaId=999999")
                .andExpect(jsonPath("$", hasSize(0)));
    }

    @Test
    void periodoInvertidoRetorna400ComMensagem() throws Exception {
        mvc.perform(get("/transacoes?dataInicio=2026-10-31&dataFim=2026-10-01"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.mensagem").value("A data inicial não pode ser posterior à data final"));
    }

    @ParameterizedTest
    @ValueSource(longs = {0, -1})
    void categoriaNaoPositivaRetorna400(long id) throws Exception {
        mvc.perform(get("/transacoes?categoriaId=" + id))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.mensagem").value("O ID da categoria deve ser maior que zero"));
    }

    @ParameterizedTest
    @ValueSource(strings = {"tipo=INVALIDO", "dataInicio=22-10-2026", "dataFim=2026-02-30", "categoriaId=abc"})
    void parametrosComFormatoInvalidoRetornam400(String parametro) throws Exception {
        mvc.perform(get("/transacoes?" + parametro))
                .andExpect(status().isBadRequest());
    }

    private ResultActions consultar(String parametros) throws Exception {
        return mvc.perform(get("/transacoes" + parametros)).andExpect(status().isOk());
    }

    private Categoria criarCategoria(String nome) {
        Categoria categoria = new Categoria();
        categoria.setNome(nome);
        return categorias.save(categoria);
    }

    private void criarTransacao(String descricao, String data, TipoTransacao tipo, Categoria categoria) {
        Transacao transacao = new Transacao();
        transacao.setDescricao(descricao);
        transacao.setValor(new BigDecimal("100.00"));
        transacao.setData(LocalDate.parse(data));
        transacao.setTipo(tipo);
        transacao.setCategoria(categoria);
        transacoes.saveAndFlush(transacao);
    }
}
