package com.jefferson.fintrack.exception;

import org.springframework.http.ResponseEntity;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.multipart.MaxUploadSizeExceededException;

import java.util.HashMap;
import java.util.Map;

@RestControllerAdvice
public class GlobalExceptionHandler {

    @ExceptionHandler(ImportacaoCsvInvalidaException.class)
    public ResponseEntity<Map<String, Object>> tratarImportacaoCsvInvalida(
            ImportacaoCsvInvalidaException exception) {
        return ResponseEntity.badRequest()
                .body(Map.of("linha", exception.getLinha(), "mensagem", exception.getMessage()));
    }

    @ExceptionHandler(ImportacaoCsvConflitoException.class)
    public ResponseEntity<Map<String, String>> tratarImportacaoCsvConflito(
            ImportacaoCsvConflitoException exception) {
        return ResponseEntity.status(HttpStatus.CONFLICT)
                .body(Map.of("mensagem", exception.getMessage()));
    }

    @ExceptionHandler(MaxUploadSizeExceededException.class)
    public ResponseEntity<Map<String, String>> tratarUploadMuitoGrande(MaxUploadSizeExceededException exception) {
        return ResponseEntity.status(HttpStatus.PAYLOAD_TOO_LARGE)
                .body(Map.of("mensagem", "O upload excede o limite permitido"));
    }

    @ExceptionHandler(EmailJaCadastradoException.class)
    public ResponseEntity<Map<String, String>> tratarEmailJaCadastrado(
            EmailJaCadastradoException exception) {
        return ResponseEntity.status(HttpStatus.CONFLICT)
                .body(Map.of("mensagem", exception.getMessage()));
    }

    @ExceptionHandler(FiltroTransacaoInvalidoException.class)
    public ResponseEntity<Map<String, String>> tratarFiltroTransacaoInvalido(
            FiltroTransacaoInvalidoException exception) {
        return ResponseEntity.badRequest()
                .body(Map.of("mensagem", exception.getMessage()));
    }

    @ExceptionHandler(CategoriaEmUsoException.class)
    public ResponseEntity<Map<String, String>> tratarCategoriaEmUso(
            CategoriaEmUsoException exception) {
        return ResponseEntity.status(HttpStatus.CONFLICT)
                .body(Map.of("mensagem", exception.getMessage()));
    }

    @ExceptionHandler(TransacaoNaoEncontradaException.class)
    public ResponseEntity<Map<String, String>> tratarTransacaoNaoEncontrada(
            TransacaoNaoEncontradaException exception) {
        return ResponseEntity.status(HttpStatus.NOT_FOUND)
                .body(Map.of("mensagem", exception.getMessage()));
    }

    @ExceptionHandler(CategoriaNaoEncontradaException.class)
    public ResponseEntity<Map<String, String>> tratarCategoriaNaoEncontrada(
            CategoriaNaoEncontradaException exception) {
        return ResponseEntity.status(HttpStatus.NOT_FOUND)
                .body(Map.of("mensagem", exception.getMessage()));
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<Map<String, String>> tratarErrosDeValidacao(
            MethodArgumentNotValidException exception) {

        Map<String, String> erros = new HashMap<>();

        exception.getBindingResult()
                .getFieldErrors()
                .forEach(erro ->
                        erros.put(
                                erro.getField(),
                                erro.getDefaultMessage()
                        )
                );

        return ResponseEntity.badRequest().body(erros);
    }
}
