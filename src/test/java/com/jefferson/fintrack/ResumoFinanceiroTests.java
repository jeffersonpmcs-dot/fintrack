package com.jefferson.fintrack;

import com.jefferson.fintrack.controller.TransacaoController;
import com.jefferson.fintrack.dto.ResumoFinanceiroResponse;
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
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDate;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest
@ActiveProfiles("test")
@Transactional
class ResumoFinanceiroTests {

    @Autowired
    private TransacaoRepository transacoes;
    @Autowired
    private CategoriaRepository categorias;
    @Autowired
    private TransacaoService service;

    private MockMvc mvc;
    private Categoria categoria;

    @BeforeEach
    void configurar() {
        mvc = MockMvcBuilders.standaloneSetup(new TransacaoController(service))
                .setControllerAdvice(new GlobalExceptionHandler()).build();
        categoria = new Categoria();
        categoria.setNome("Teste de resumo");
        categoria = categorias.save(categoria);
        criarTransacao("2026-09-30", TipoTransacao.DESPESA, "100.00");
        criarTransacao("2026-10-01", TipoTransacao.RECEITA, "1000.10");
        criarTransacao("2026-10-15", TipoTransacao.RECEITA, "0.20");
        criarTransacao("2026-10-15", TipoTransacao.DESPESA, "50.05");
        criarTransacao("2026-10-31", TipoTransacao.DESPESA, "150.25");
        criarTransacao("2026-11-01", TipoTransacao.RECEITA, "9000.00");
    }

    @Test
    void periodoIncluiAsDatasLimiteECalculaSaldo() throws Exception {
        conferir("?dataInicio=2026-10-01&dataFim=2026-10-31", 1000.30, 200.30, 800.00);
    }

    @Test
    void semDatasResumeTodoOHistorico() throws Exception {
        conferir("", 10000.30, 300.30, 9700.00);
    }

    @Test
    void aceitaSomenteDataInicial() throws Exception {
        conferir("?dataInicio=2026-10-31", 9000.00, 150.25, 8849.75);
    }

    @Test
    void aceitaSomenteDataFinal() throws Exception {
        conferir("?dataFim=2026-10-01", 1000.10, 100.00, 900.10);
    }

    @Test
    void periodoDeUmDiaPodeTerSaldoNegativo() throws Exception {
        conferir("?dataInicio=2026-10-15&dataFim=2026-10-15", 0.20, 50.05, -49.85);
    }

    @Test
    void periodoSomenteComReceitasZeraDespesas() throws Exception {
        conferir("?dataInicio=2026-10-01&dataFim=2026-10-01", 1000.10, 0.00, 1000.10);
    }

    @Test
    void periodoSomenteComDespesasZeraReceitas() throws Exception {
        conferir("?dataInicio=2026-10-31&dataFim=2026-10-31", 0.00, 150.25, -150.25);
    }

    @Test
    void periodoSemTransacoesRetornaZeros() throws Exception {
        conferir("?dataInicio=2027-01-01&dataFim=2027-01-31", 0.00, 0.00, 0.00);
    }

    @Test
    void receitasEDespesasIguaisRetornamSaldoZero() throws Exception {
        criarTransacao("2026-10-01", TipoTransacao.DESPESA, "1000.10");
        conferir("?dataInicio=2026-10-01&dataFim=2026-10-01", 1000.10, 1000.10, 0.00);
    }

    @Test
    void somaMonetariaPreservaPrecisaoDecimal() {
        ResumoFinanceiroResponse resumo = service.resumir(
                LocalDate.of(2026, 10, 1), LocalDate.of(2026, 10, 31));
        assertEquals(new BigDecimal("1000.30"), resumo.totalReceitas());
        assertEquals(new BigDecimal("200.30"), resumo.totalDespesas());
        assertEquals(new BigDecimal("800.00"), resumo.saldo());
    }

    @Test
    void periodoInvertidoRetorna400ComMensagem() throws Exception {
        mvc.perform(get("/transacoes/resumo?dataInicio=2026-10-31&dataFim=2026-10-01"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.mensagem").value("A data inicial não pode ser posterior à data final"));
    }

    @ParameterizedTest
    @ValueSource(strings = {"dataInicio=22-10-2026", "dataFim=2026-02-30", "dataFim=texto"})
    void datasComFormatoInvalidoRetornam400(String parametro) throws Exception {
        mvc.perform(get("/transacoes/resumo?" + parametro))
                .andExpect(status().isBadRequest());
    }

    private void conferir(String parametros, double receitas, double despesas, double saldo) throws Exception {
        mvc.perform(get("/transacoes/resumo" + parametros))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalReceitas").value(receitas))
                .andExpect(jsonPath("$.totalDespesas").value(despesas))
                .andExpect(jsonPath("$.saldo").value(saldo));
    }

    private void criarTransacao(String data, TipoTransacao tipo, String valor) {
        Transacao transacao = new Transacao();
        transacao.setDescricao("Resumo " + data + " " + tipo);
        transacao.setData(LocalDate.parse(data));
        transacao.setTipo(tipo);
        transacao.setValor(new BigDecimal(valor));
        transacao.setCategoria(categoria);
        transacoes.saveAndFlush(transacao);
    }
}
