package com.jefferson.fintrack.service;

import com.jefferson.fintrack.model.Transacao;
import com.jefferson.fintrack.exception.CategoriaNaoEncontradaException;
import com.jefferson.fintrack.exception.TransacaoNaoEncontradaException;
import com.jefferson.fintrack.exception.FiltroTransacaoInvalidoException;
import com.jefferson.fintrack.model.TipoTransacao;
import jakarta.persistence.criteria.Predicate;
import com.jefferson.fintrack.repository.TransacaoRepository;
import org.springframework.stereotype.Service;
import com.jefferson.fintrack.dto.TransacaoRequest;
import com.jefferson.fintrack.model.Categoria;
import com.jefferson.fintrack.repository.CategoriaRepository;
import java.util.List;
import java.util.ArrayList;
import java.time.LocalDate;

@Service
public class TransacaoService {

    private final TransacaoRepository transacaoRepository;

    private final CategoriaRepository categoriaRepository;



    public TransacaoService(
            TransacaoRepository transacaoRepository,
            CategoriaRepository categoriaRepository) {

        this.transacaoRepository = transacaoRepository;
        this.categoriaRepository = categoriaRepository;
    }

    public Transacao salvar(TransacaoRequest request) {

        Categoria categoria = categoriaRepository
                .findById(request.getCategoriaId())
                .orElseThrow(CategoriaNaoEncontradaException::new);

        Transacao transacao = new Transacao();

        transacao.setDescricao(request.getDescricao());
        transacao.setValor(request.getValor());
        transacao.setData(request.getData());
        transacao.setTipo(request.getTipo());
        transacao.setCategoria(categoria);

        return transacaoRepository.save(transacao);
    }

    public List<Transacao> listarTodas (){
        return transacaoRepository.findAll();
    }

    public List<Transacao> listar(LocalDate dataInicio, LocalDate dataFim,
                                 Long categoriaId, TipoTransacao tipo) {
        if (dataInicio != null && dataFim != null && dataInicio.isAfter(dataFim)) {
            throw new FiltroTransacaoInvalidoException(
                    "A data inicial não pode ser posterior à data final");
        }
        if (categoriaId != null && categoriaId <= 0) {
            throw new FiltroTransacaoInvalidoException(
                    "O ID da categoria deve ser maior que zero");
        }
        if (dataInicio == null && dataFim == null && categoriaId == null && tipo == null) {
            return listarTodas();
        }

        return transacaoRepository.findAll((root, query, builder) -> {
            List<Predicate> filtros = new ArrayList<>();
            if (dataInicio != null) {
                filtros.add(builder.greaterThanOrEqualTo(root.<LocalDate>get("data"), dataInicio));
            }
            if (dataFim != null) {
                filtros.add(builder.lessThanOrEqualTo(root.<LocalDate>get("data"), dataFim));
            }
            if (categoriaId != null) {
                filtros.add(builder.equal(root.get("categoria").get("id"), categoriaId));
            }
            if (tipo != null) {
                filtros.add(builder.equal(root.get("tipo"), tipo));
            }
            return builder.and(filtros.toArray(Predicate[]::new));
        });
    }

    public Transacao buscarPorId(Long id){
        return transacaoRepository.findById(id)
                .orElseThrow(TransacaoNaoEncontradaException::new);
    }

    public Transacao atualizar(Long id, TransacaoRequest request) {

        Transacao transacaoExistente = buscarPorId(id);

        Categoria categoria = categoriaRepository
                .findById(request.getCategoriaId())
                .orElseThrow(CategoriaNaoEncontradaException::new);

        transacaoExistente.setDescricao(request.getDescricao());
        transacaoExistente.setValor(request.getValor());
        transacaoExistente.setData(request.getData());
        transacaoExistente.setTipo(request.getTipo());
        transacaoExistente.setCategoria(categoria);

        return transacaoRepository.save(transacaoExistente);
    }

    public void excluir(Long id) {
        Transacao transacao = buscarPorId(id);
        transacaoRepository.delete(transacao);
    }
}
