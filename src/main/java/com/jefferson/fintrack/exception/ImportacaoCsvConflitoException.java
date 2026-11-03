package com.jefferson.fintrack.exception;

public class ImportacaoCsvConflitoException extends RuntimeException {

    public ImportacaoCsvConflitoException(Throwable causa) {
        super("Importação não concluída por conflito nos dados. Confira as categorias e tente novamente", causa);
    }
}
