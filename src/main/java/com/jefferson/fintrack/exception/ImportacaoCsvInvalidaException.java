package com.jefferson.fintrack.exception;

public class ImportacaoCsvInvalidaException extends RuntimeException {

    private final int linha;

    public ImportacaoCsvInvalidaException(int linha, String mensagem) {
        super(mensagem);
        this.linha = linha;
    }

    public int getLinha() {
        return linha;
    }
}
