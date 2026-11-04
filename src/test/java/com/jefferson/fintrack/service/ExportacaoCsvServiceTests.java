package com.jefferson.fintrack.service;

import com.jefferson.fintrack.model.Categoria;
import com.jefferson.fintrack.model.TipoTransacao;
import com.jefferson.fintrack.model.Transacao;
import jakarta.validation.Validation;
import jakarta.validation.ValidatorFactory;
import org.apache.commons.csv.CSVFormat;
import org.apache.commons.csv.CSVParser;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.mock.web.MockMultipartFile;

import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class ExportacaoCsvServiceTests {

    private static ValidatorFactory factory;

    @BeforeAll
    static void configurar() {
        factory = Validation.buildDefaultValidatorFactory();
    }

    @AfterAll
    static void fechar() {
        factory.close();
    }

    @Test
    void aspasSeparadoresEQuebrasDeLinhaContinuamEmUmUnicoCampo() throws Exception {
        String descricao = "Mercado; compra \"mensal\"\nsegunda linha;=1+2";
        byte[] bytes = exportar(transacao(descricao, "187.50"));
        String texto = new String(bytes, StandardCharsets.UTF_8);
        assertTrue(texto.startsWith("\uFEFF"));
        assertTrue(texto.endsWith("\r\n"));
        try (CSVParser parser = CSVParser.parse(texto.substring(1), CSVFormat.RFC4180.builder().setDelimiter(';').get())) {
            var registros = parser.getRecords();
            assertEquals(2, registros.size());
            assertEquals(List.of("descricao", "valor", "data", "tipo", "categoriaId"), registros.getFirst().toList());
            assertEquals(5, registros.get(1).size());
            assertEquals(descricao, registros.get(1).get(0));
            assertEquals("187.50", registros.get(1).get(1));
            assertEquals("2026-11-03", registros.get(1).get(2));
            assertEquals("DESPESA", registros.get(1).get(3));
            assertEquals("1", registros.get(1).get(4));
        }
    }

    @ParameterizedTest
    @ValueSource(strings = {"=1+2", "+1+2", "-1+2", "@SOMA(1)", "\t=1+2", "\r=1+2", "\n=1+2",
            "   =1+2", "\u00A0=1+2", "\uFEFF=1+2", "\u200B=1+2", "＝1+2", "＋1+2", "－1+2", "＠SOMA(1)"})
    void prefixosDeFormulaRecebemTabSemAlterarODadoOriginal(String descricao) throws Exception {
        Transacao transacao = transacao(descricao, "1.00");
        String texto = new String(exportar(transacao), StandardCharsets.UTF_8);
        try (CSVParser parser = CSVParser.parse(texto.substring(1), CSVFormat.RFC4180.builder().setDelimiter(';').get())) {
            assertEquals("\t" + descricao, parser.getRecords().get(1).get(0));
        }
        assertEquals(descricao, transacao.getDescricao());
    }

    @ParameterizedTest
    @ValueSource(strings = {"Supermercado", "Descrição com acentuação", "=1+2"})
    void arquivoPodeSerLidoPeloImportador(String descricao) {
        Transacao original = transacao(descricao, "0.10");
        byte[] csv = exportar(original);
        var linhas = new LeitorTransacoesCsv(factory.getValidator())
                .ler(new MockMultipartFile("arquivo", "transacoes.csv", "text/csv", csv));
        assertEquals(1, linhas.size());
        var importada = linhas.getFirst().transacao();
        assertEquals(descricao, importada.getDescricao());
        assertEquals(new BigDecimal("0.10"), importada.getValor());
        assertEquals(original.getData(), importada.getData());
        assertEquals(original.getTipo(), importada.getTipo());
        assertEquals(1L, importada.getCategoriaId());
    }

    @ParameterizedTest
    @ValueSource(strings = {"0.10", "999999999999999.99", "1E+20"})
    void valorSaiSemNotacaoCientificaESemArredondamento(String valor) throws Exception {
        String texto = new String(exportar(transacao("Teste", valor)), StandardCharsets.UTF_8);
        try (CSVParser parser = CSVParser.parse(texto.substring(1), CSVFormat.RFC4180.builder().setDelimiter(';').get())) {
            assertEquals(new BigDecimal(valor).toPlainString(), parser.getRecords().get(1).get(1));
        }
    }

    private byte[] exportar(Transacao transacao) {
        TransacaoService service = mock(TransacaoService.class);
        when(service.listarParaExportacao(null, null, null, null)).thenReturn(List.of(transacao));
        return new ExportacaoCsvService(service).exportar(null, null, null, null);
    }

    private Transacao transacao(String descricao, String valor) {
        Categoria categoria = new Categoria();
        categoria.setId(1L);
        Transacao transacao = new Transacao();
        transacao.setDescricao(descricao);
        transacao.setValor(new BigDecimal(valor));
        transacao.setData(LocalDate.of(2026, 11, 3));
        transacao.setTipo(TipoTransacao.DESPESA);
        transacao.setCategoria(categoria);
        return transacao;
    }
}
