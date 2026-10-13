package com.jefferson.fintrack.repository;

import com.jefferson.fintrack.model.Transacao;
import org.springframework.data.jpa.repository.JpaRepository;

public interface TransacaoRepository
        extends JpaRepository<Transacao, Long> {


}