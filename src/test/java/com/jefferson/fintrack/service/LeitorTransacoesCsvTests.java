package com.jefferson.fintrack.service;

import com.jefferson.fintrack.exception.ImportacaoCsvInvalidaException;
import com.jefferson.fintrack.model.TipoTransacao;
import jakarta.validation.Validation;
import jakarta.validation.ValidatorFactory;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.web.multipart.MaxUploadSizeExceededException;

import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.*;

class LeitorTransacoesCsvTests {

    private static final String HEADER = "descricao;valor;data;tipo;categoriaId\n";
    private static final String LINHA = "Mercado;187.50;2026-11-03;DESPESA;1\n";
    private static ValidatorFactory factory;
    private static LeitorTransacoesCsv leitor;

    @BeforeAll
    static void configurar() {
        factory = Validation.buildDefaultValidatorFactory();
        leitor = new LeitorTransacoesCsv(factory.getValidator());
    }

    @AfterAll
    static void fechar() {
        factory.close();
    }

    @Test
    void leUtf8ComBomCrLfEAspasEscapadas() {
        String csv = "\uFEFF" + HEADER + "\"Supermercado; compra \"\"mensal\"\"\";187.50;2026-11-03;DESPESA;1\n";
        var linhas = leitor.ler(arquivo(csv.replace("\n", "\r\n")));
        assertEquals(1, linhas.size());
        assertEquals(2, linhas.getFirst().numero());
        var transacao = linhas.getFirst().transacao();
        assertEquals("Supermercado; compra \"mensal\"", transacao.getDescricao());
        assertEquals(new BigDecimal("187.50"), transacao.getValor());
        assertEquals(TipoTransacao.DESPESA, transacao.getTipo());
        assertEquals(1L, transacao.getCategoriaId());
    }

    @Test
    void descricaoComQuebraDeLinhaContaComoUmRegistroLogico() {
        String primeira = "\"Compra\nmensal\";1.00;2026-11-03;DESPESA;1\n";
        var linhas = leitor.ler(arquivo(HEADER + primeira + LINHA));
        assertEquals("Compra\nmensal", linhas.getFirst().transacao().getDescricao());
        assertEquals(3, linhas.get(1).numero());
        var erro = assertThrows(ImportacaoCsvInvalidaException.class,
                () -> leitor.ler(arquivo(HEADER + primeira + "Invalida;0;2026-11-03;DESPESA;1\n")));
        assertEquals(3, erro.getLinha());
    }

    @ParameterizedTest
    @ValueSource(strings = {"1", "1.2", "999999999999999.99"})
    void aceitaValoresExatosSemArredondar(String valor) {
        var linhas = leitor.ler(arquivo(HEADER + LINHA.replace("187.50", valor)));
        assertEquals(new BigDecimal(valor).setScale(2), linhas.getFirst().transacao().getValor());
    }

    @ParameterizedTest
    @MethodSource("registrosInvalidos")
    void rejeitaRegistroInvalidoComNumeroDaLinha(String linha) {
        var erro = assertThrows(ImportacaoCsvInvalidaException.class,
                () -> leitor.ler(arquivo(HEADER + linha)));
        assertEquals(2, erro.getLinha());
    }

    static Stream<String> registrosInvalidos() {
        return Stream.of(
                " ;1.00;2026-11-03;DESPESA;1\n",
                "a".repeat(256) + ";1.00;2026-11-03;DESPESA;1\n",
                LINHA.replace("187.50", "0"), LINHA.replace("187.50", "-1.00"),
                LINHA.replace("187.50", "187,50"), LINHA.replace("187.50", "1.234"),
                LINHA.replace("187.50", "1e2"), LINHA.replace("187.50", "NaN"),
                LINHA.replace("187.50", "1000000000000000.00"), LINHA.replace("187.50", ""),
                LINHA.replace("2026-11-03", "2026-02-30"), LINHA.replace("2026-11-03", "03/11/2026"),
                LINHA.replace("2026-11-03", "0000-01-01"), LINHA.replace("2026-11-03", "10000-01-01"),
                LINHA.replace("DESPESA", "despesa"), LINHA.replace("DESPESA", "INVALIDO"),
                LINHA.replace(";1\n", ";0\n"), LINHA.replace(";1\n", ";-1\n"),
                LINHA.replace(";1\n", ";9223372036854775808\n"), LINHA.replace(";1\n", ";abc\n"),
                "Mercado;187.50;2026-11-03;DESPESA\n",
                LINHA.stripTrailing() + ";coluna-extra\n",
                "\"Aspas abertas;1.00;2026-11-03;DESPESA;1\n");
    }

    @ParameterizedTest
    @ValueSource(strings = {"descricao,valor,data,tipo,categoriaId\n", "valor;descricao;data;tipo;categoriaId\n",
            "descricao;valor;data;tipo;usuarioId\n", "descricao;valor;data;tipo;categoriaId;usuarioId\n",
            "descricao;valor;data;tipo;tipo\n"})
    void rejeitaCabecalhoDiferenteOuComDonoForjado(String header) {
        var erro = assertThrows(ImportacaoCsvInvalidaException.class, () -> leitor.ler(arquivo(header + LINHA)));
        assertEquals(1, erro.getLinha());
    }

    @Test
    void rejeitaArquivoVazioECabecalhoSemTransacoes() {
        assertThrows(ImportacaoCsvInvalidaException.class, () -> leitor.ler(arquivo("")));
        var erro = assertThrows(ImportacaoCsvInvalidaException.class, () -> leitor.ler(arquivo(HEADER)));
        assertEquals(2, erro.getLinha());
    }

    @Test
    void rejeitaLinhaVaziaIntermediaria() {
        var erro = assertThrows(ImportacaoCsvInvalidaException.class,
                () -> leitor.ler(arquivo(HEADER + LINHA + "\n" + LINHA)));
        assertEquals(3, erro.getLinha());
    }

    @Test
    void aceitaMilTransacoesERejeitaOMilesimoPrimeiroRegistro() {
        assertEquals(1000, leitor.ler(arquivo(HEADER + LINHA.repeat(1000))).size());
        var erro = assertThrows(ImportacaoCsvInvalidaException.class,
                () -> leitor.ler(arquivo(HEADER + LINHA.repeat(1001))));
        assertEquals(1002, erro.getLinha());
    }

    @Test
    void rejeitaArquivoMaiorQueUmMegabyte() {
        byte[] grande = new byte[LeitorTransacoesCsv.LIMITE_BYTES + 1];
        assertThrows(MaxUploadSizeExceededException.class,
                () -> leitor.ler(new MockMultipartFile("arquivo", grande)));
    }

    @Test
    void rejeitaUtf8Malformado() {
        var erro = assertThrows(ImportacaoCsvInvalidaException.class,
                () -> leitor.ler(new MockMultipartFile("arquivo", new byte[]{(byte) 0xC3, 0x28})));
        assertEquals(1, erro.getLinha());
    }

    private static MockMultipartFile arquivo(String csv) {
        return new MockMultipartFile("arquivo", "teste.csv", "text/csv", csv.getBytes(StandardCharsets.UTF_8));
    }
}
