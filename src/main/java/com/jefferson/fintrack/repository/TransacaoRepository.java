package com.jefferson.fintrack.repository;

import com.jefferson.fintrack.model.Transacao;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;

public interface TransacaoRepository
        extends JpaRepository<Transacao, Long>, JpaSpecificationExecutor<Transacao> {

    boolean existsByCategoriaId(Long categoriaId);
}
