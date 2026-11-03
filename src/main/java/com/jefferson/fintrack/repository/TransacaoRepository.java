package com.jefferson.fintrack.repository;

import com.jefferson.fintrack.model.Transacao;
import com.jefferson.fintrack.dto.TotalPorTipo;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

public interface TransacaoRepository
        extends JpaRepository<Transacao, Long>, JpaSpecificationExecutor<Transacao> {

    boolean existsByCategoriaId(Long categoriaId);

    List<Transacao> findAllByUsuarioId(Long usuarioId);

    Optional<Transacao> findByIdAndUsuarioId(Long id, Long usuarioId);

    @Query("""
            select new com.jefferson.fintrack.dto.TotalPorTipo(t.tipo, sum(t.valor))
            from Transacao t
            where t.usuario.id = :usuarioId
              and t.data >= coalesce(:dataInicio, t.data)
              and t.data <= coalesce(:dataFim, t.data)
            group by t.tipo
            """)
    List<TotalPorTipo> somarPorTipoNoPeriodo(
            @Param("usuarioId") Long usuarioId,
            @Param("dataInicio") LocalDate dataInicio,
            @Param("dataFim") LocalDate dataFim);
}
