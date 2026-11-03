package com.jefferson.fintrack.service;

import com.jefferson.fintrack.dto.TransacaoRequest;
import com.jefferson.fintrack.exception.ImportacaoCsvInvalidaException;
import com.jefferson.fintrack.model.TipoTransacao;
import jakarta.validation.Validator;
import org.apache.commons.csv.CSVFormat;
import org.apache.commons.csv.CSVParser;
import org.apache.commons.csv.CSVRecord;
import org.springframework.stereotype.Component;
import org.springframework.web.multipart.MaxUploadSizeExceededException;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.math.BigDecimal;
import java.nio.ByteBuffer;
import java.nio.charset.CharacterCodingException;
import java.nio.charset.CodingErrorAction;
import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.Iterator;
import java.util.List;
import java.util.regex.Pattern;

@Component
public class LeitorTransacoesCsv {

    static final int LIMITE_BYTES = 1_048_576;
    static final int LIMITE_TRANSACOES = 1000;
    private static final List<String> CABECALHO = List.of("descricao", "valor", "data", "tipo", "categoriaId");
    private static final CSVFormat FORMATO = CSVFormat.RFC4180.builder().setDelimiter(';').get();
    private static final Pattern VALOR = Pattern.compile("[0-9]{1,15}(\\.[0-9]{1,2})?");
    private static final Pattern DATA = Pattern.compile("[0-9]{4}-[0-9]{2}-[0-9]{2}");
    private static final Pattern ID = Pattern.compile("[0-9]{1,19}");

    private final Validator validator;

    public LeitorTransacoesCsv(Validator validator) {
        this.validator = validator;
    }

    List<Linha> ler(MultipartFile arquivo) {
        String conteudo = lerUtf8(arquivo);
        int numero = 1;
        try (CSVParser parser = CSVParser.parse(conteudo, FORMATO)) {
            Iterator<CSVRecord> registros = parser.iterator();
            if (!registros.hasNext() || !cabecalhoValido(registros.next())) {
                throw erro(1, "Cabeçalho esperado: descricao;valor;data;tipo;categoriaId");
            }
            numero = 2;
            List<Linha> linhas = new ArrayList<>();
            while (registros.hasNext()) {
                if (linhas.size() == LIMITE_TRANSACOES) {
                    throw erro(numero, "O CSV deve conter no máximo 1000 transações");
                }
                CSVRecord registro = registros.next();
                if (registro.size() != CABECALHO.size()) {
                    throw erro(numero, "Cada registro deve conter exatamente 5 colunas");
                }
                linhas.add(new Linha(numero, converter(registro, numero)));
                numero++;
            }
            if (linhas.isEmpty()) {
                throw erro(2, "O CSV deve conter ao menos uma transação");
            }
            return linhas;
        } catch (IOException | UncheckedIOException exception) {
            throw erro(numero, "CSV malformado ou ilegível; confira as aspas e os separadores");
        }
    }

    private String lerUtf8(MultipartFile arquivo) {
        if (arquivo == null || arquivo.isEmpty()) {
            throw erro(1, "O arquivo CSV está vazio");
        }
        if (arquivo.getSize() > LIMITE_BYTES) {
            throw new MaxUploadSizeExceededException(LIMITE_BYTES);
        }
        try (var entrada = arquivo.getInputStream()) {
            byte[] bytes = entrada.readNBytes(LIMITE_BYTES + 1);
            if (bytes.length > LIMITE_BYTES) {
                throw new MaxUploadSizeExceededException(LIMITE_BYTES);
            }
            String texto = StandardCharsets.UTF_8.newDecoder()
                    .onMalformedInput(CodingErrorAction.REPORT)
                    .onUnmappableCharacter(CodingErrorAction.REPORT)
                    .decode(ByteBuffer.wrap(bytes)).toString();
            return texto.startsWith("\uFEFF") ? texto.substring(1) : texto;
        } catch (CharacterCodingException exception) {
            throw erro(1, "O arquivo deve usar codificação UTF-8");
        } catch (IOException exception) {
            throw erro(1, "Não foi possível ler o arquivo CSV");
        }
    }

    private boolean cabecalhoValido(CSVRecord registro) {
        if (registro.size() != CABECALHO.size()) return false;
        for (int coluna = 0; coluna < CABECALHO.size(); coluna++) {
            if (!CABECALHO.get(coluna).equals(registro.get(coluna))) return false;
        }
        return true;
    }

    private TransacaoRequest converter(CSVRecord registro, int numero) {
        TransacaoRequest request = new TransacaoRequest();
        String descricao = registro.get(0).strip();
        if (descricao.length() > 255) {
            throw erro(numero, "A descrição deve ter no máximo 255 caracteres");
        }
        request.setDescricao(descricao);

        String valor = registro.get(1).strip();
        if (!VALOR.matcher(valor).matches()) {
            throw erro(numero, "O valor deve ser positivo, usar ponto decimal e ter até 15 dígitos inteiros e 2 decimais");
        }
        request.setValor(new BigDecimal(valor).setScale(2));

        String data = registro.get(2).strip();
        try {
            if (!DATA.matcher(data).matches()) throw new DateTimeParseException("Formato", data, 0);
            LocalDate dia = LocalDate.parse(data);
            if (dia.getYear() < 1) throw new DateTimeParseException("Ano", data, 0);
            request.setData(dia);
        } catch (DateTimeParseException exception) {
            throw erro(numero, "A data deve ser válida e usar AAAA-MM-DD, com ano entre 1 e 9999");
        }

        try {
            request.setTipo(TipoTransacao.valueOf(registro.get(3).strip()));
        } catch (IllegalArgumentException exception) {
            throw erro(numero, "O tipo deve ser RECEITA ou DESPESA");
        }

        String categoria = registro.get(4).strip();
        try {
            if (!ID.matcher(categoria).matches()) throw new NumberFormatException();
            long categoriaId = Long.parseLong(categoria);
            if (categoriaId <= 0) throw new NumberFormatException();
            request.setCategoriaId(categoriaId);
        } catch (NumberFormatException exception) {
            throw erro(numero, "O ID da categoria deve ser um inteiro positivo válido");
        }

        validator.validate(request).stream()
                .sorted(Comparator.comparing(violacao -> violacao.getPropertyPath().toString()))
                .findFirst().ifPresent(violacao -> { throw erro(numero, violacao.getMessage()); });
        return request;
    }

    private ImportacaoCsvInvalidaException erro(int numero, String mensagem) {
        return new ImportacaoCsvInvalidaException(numero, mensagem);
    }

    record Linha(int numero, TransacaoRequest transacao) {
    }
}
