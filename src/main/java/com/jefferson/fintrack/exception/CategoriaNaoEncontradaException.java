package com.jefferson.fintrack.exception;

public class CategoriaNaoEncontradaException extends RuntimeException {

    public CategoriaNaoEncontradaException() {
        super("Categoria não encontrada");
    }
}
