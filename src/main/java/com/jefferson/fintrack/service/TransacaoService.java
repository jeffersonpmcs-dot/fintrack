package com.jefferson.fintrack.service;

import com.jefferson.fintrack.dto.ResumoFinanceiroResponse;
import com.jefferson.fintrack.dto.GastoPorCategoriaResponse;
import com.jefferson.fintrack.dto.TotalPorTipo;
import com.jefferson.fintrack.dto.TransacaoRequest;
import com.jefferson.fintrack.exception.CategoriaNaoEncontradaException;
import com.jefferson.fintrack.exception.FiltroTransacaoInvalidoException;
import com.jefferson.fintrack.exception.TransacaoNaoEncontradaException;
import com.jefferson.fintrack.model.Categoria;
import com.jefferson.fintrack.model.TipoTransacao;
import com.jefferson.fintrack.model.Transacao;
import com.jefferson.fintrack.model.Usuario;
import com.jefferson.fintrack.repository.CategoriaRepository;
import com.jefferson.fintrack.repository.TransacaoRepository;
import jakarta.persistence.criteria.Predicate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

@Service
@Transactional(readOnly = true)
public class TransacaoService {

    private final TransacaoRepository transacaoRepository;
    private final CategoriaRepository categoriaRepository;
    private final UsuarioAtualService usuarioAtualService;

    public TransacaoService(TransacaoRepository transacaoRepository,
                           CategoriaRepository categoriaRepository,
                           UsuarioAtualService usuarioAtualService) {
        this.transacaoRepository = transacaoRepository;
        this.categoriaRepository = categoriaRepository;
        this.usuarioAtualService = usuarioAtualService;
    }

    @Transactional
    public Transacao salvar(TransacaoRequest request) {
        Usuario usuario = usuarioAtualService.obter();
        Categoria categoria = buscarCategoria(request.getCategoriaId(), usuario.getId());
        Transacao transacao = new Transacao();
        transacao.setUsuario(usuario);
        preencher(transacao, request, categoria);
        return transacaoRepository.save(transacao);
    }

    public List<Transacao> listarTodas() {
        return transacaoRepository.findAllByUsuarioId(usuarioAtualService.obter().getId());
    }

    public List<Transacao> listar(LocalDate dataInicio, LocalDate dataFim,
                                 Long categoriaId, TipoTransacao tipo) {
        Long usuarioId = usuarioAtualService.obter().getId();
        validarPeriodo(dataInicio, dataFim);
        if (categoriaId != null && categoriaId <= 0) {
            throw new FiltroTransacaoInvalidoException("O ID da categoria deve ser maior que zero");
        }
        if (dataInicio == null && dataFim == null && categoriaId == null && tipo == null) {
            return transacaoRepository.findAllByUsuarioId(usuarioId);
        }

        return transacaoRepository.findAll((root, query, builder) -> {
            List<Predicate> filtros = new ArrayList<>();
            filtros.add(builder.equal(root.get("usuario").get("id"), usuarioId));
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

    public Transacao buscarPorId(Long id) {
        return buscarTransacao(id, usuarioAtualService.obter().getId());
    }

    public ResumoFinanceiroResponse resumir(LocalDate dataInicio, LocalDate dataFim) {
        Long usuarioId = usuarioAtualService.obter().getId();
        validarPeriodo(dataInicio, dataFim);
        BigDecimal totalReceitas = new BigDecimal("0.00");
        BigDecimal totalDespesas = new BigDecimal("0.00");

        for (TotalPorTipo total : transacaoRepository.somarPorTipoNoPeriodo(usuarioId, dataInicio, dataFim)) {
            if (total.tipo() == TipoTransacao.RECEITA) {
                totalReceitas = total.total();
            } else if (total.tipo() == TipoTransacao.DESPESA) {
                totalDespesas = total.total();
            }
        }
        return new ResumoFinanceiroResponse(
                totalReceitas, totalDespesas, totalReceitas.subtract(totalDespesas));
    }

    public List<GastoPorCategoriaResponse> gastosPorCategoria(LocalDate dataInicio, LocalDate dataFim) {
        Long usuarioId = usuarioAtualService.obter().getId();
        validarPeriodo(dataInicio, dataFim);
        return transacaoRepository.somarDespesasPorCategoriaNoPeriodo(usuarioId, dataInicio, dataFim);
    }

    @Transactional
    public Transacao atualizar(Long id, TransacaoRequest request) {
        Long usuarioId = usuarioAtualService.obter().getId();
        Transacao transacao = buscarTransacao(id, usuarioId);
        Categoria categoria = buscarCategoria(request.getCategoriaId(), usuarioId);
        preencher(transacao, request, categoria);
        return transacaoRepository.save(transacao);
    }

    @Transactional
    public void excluir(Long id) {
        Transacao transacao = buscarTransacao(id, usuarioAtualService.obter().getId());
        transacaoRepository.delete(transacao);
    }

    private Transacao buscarTransacao(Long id, Long usuarioId) {
        return transacaoRepository.findByIdAndUsuarioId(id, usuarioId)
                .orElseThrow(TransacaoNaoEncontradaException::new);
    }

    private Categoria buscarCategoria(Long id, Long usuarioId) {
        return categoriaRepository.findByIdAndUsuarioId(id, usuarioId)
                .orElseThrow(CategoriaNaoEncontradaException::new);
    }

    private void preencher(Transacao transacao, TransacaoRequest request, Categoria categoria) {
        transacao.setDescricao(request.getDescricao());
        transacao.setValor(request.getValor());
        transacao.setData(request.getData());
        transacao.setTipo(request.getTipo());
        transacao.setCategoria(categoria);
    }

    private void validarPeriodo(LocalDate dataInicio, LocalDate dataFim) {
        if (dataInicio != null && dataFim != null && dataInicio.isAfter(dataFim)) {
            throw new FiltroTransacaoInvalidoException("A data inicial não pode ser posterior à data final");
        }
    }
}
