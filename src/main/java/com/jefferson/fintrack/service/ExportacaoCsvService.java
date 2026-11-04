package com.jefferson.fintrack.service;

import com.jefferson.fintrack.model.TipoTransacao;
import org.apache.commons.csv.CSVFormat;
import org.apache.commons.csv.CSVPrinter;
import org.apache.commons.csv.QuoteMode;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.io.StringWriter;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.time.LocalDate;

@Service
public class ExportacaoCsvService {

    private static final CSVFormat FORMATO = CSVFormat.RFC4180.builder()
            .setDelimiter(';').setQuoteMode(QuoteMode.ALL).setRecordSeparator("\r\n").get();

    private final TransacaoService transacaoService;

    public ExportacaoCsvService(TransacaoService transacaoService) {
        this.transacaoService = transacaoService;
    }

    public byte[] exportar(LocalDate dataInicio, LocalDate dataFim, Long categoriaId, TipoTransacao tipo) {
        var transacoes = transacaoService.listarParaExportacao(dataInicio, dataFim, categoriaId, tipo);
        StringWriter texto = new StringWriter();
        texto.write("\uFEFF");
        try (CSVPrinter csv = new CSVPrinter(texto, FORMATO)) {
            csv.printRecord("descricao", "valor", "data", "tipo", "categoriaId");
            for (var transacao : transacoes) {
                csv.printRecord(protegerTexto(transacao.getDescricao()), transacao.getValor().toPlainString(),
                        protegerTexto(transacao.getData().toString()), transacao.getTipo().name(),
                        transacao.getCategoria().getId());
            }
        } catch (IOException exception) {
            throw new UncheckedIOException("Não foi possível gerar o CSV", exception);
        }
        return texto.toString().getBytes(StandardCharsets.UTF_8);
    }

    private String protegerTexto(String texto) {
        boolean controleInicial = false;
        for (int indice = 0; indice < texto.length();) {
            int caractere = texto.codePointAt(indice);
            boolean controle = Character.isISOControl(caractere) || Character.getType(caractere) == Character.FORMAT;
            controleInicial |= controle;
            if (Character.isWhitespace(caractere) || Character.isSpaceChar(caractere) || controle) {
                indice += Character.charCount(caractere);
                continue;
            }
            // O tab fica dentro do campo entre aspas; não altera a descrição persistida.
            return controleInicial || "=+-@＝＋－＠".indexOf(caractere) >= 0 ? "\t" + texto : texto;
        }
        return controleInicial ? "\t" + texto : texto;
    }
}
