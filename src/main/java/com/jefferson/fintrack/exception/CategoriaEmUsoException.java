package com.jefferson.fintrack.exception;

public class CategoriaEmUsoException extends RuntimeException {

    private static final String MENSAGEM =
            "Não é possível excluir uma categoria com transações vinculadas";

    public CategoriaEmUsoException() {
        super(MENSAGEM);
    }

    public CategoriaEmUsoException(Throwable causa) {
        super(MENSAGEM, causa);
    }
}
