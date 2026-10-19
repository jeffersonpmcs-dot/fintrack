package com.jefferson.fintrack.service;

import com.jefferson.fintrack.model.Transacao;
import com.jefferson.fintrack.exception.CategoriaNaoEncontradaException;
import com.jefferson.fintrack.exception.TransacaoNaoEncontradaException;
import com.jefferson.fintrack.repository.TransacaoRepository;
import org.springframework.stereotype.Service;
import com.jefferson.fintrack.dto.TransacaoRequest;
import com.jefferson.fintrack.model.Categoria;
import com.jefferson.fintrack.repository.CategoriaRepository;
import java.util.List;

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
