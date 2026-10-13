package com.jefferson.fintrack.service;

import com.jefferson.fintrack.model.Transacao;
import com.jefferson.fintrack.repository.TransacaoRepository;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
public class TransacaoService {

    private final TransacaoRepository transacaoRepository;

    public TransacaoService(TransacaoRepository transacaoRepository) {
        this.transacaoRepository = transacaoRepository;
    }

    public Transacao salvar (Transacao transacao){
        return transacaoRepository.save(transacao);
    }

    public List<Transacao> listarTodas (){
        return transacaoRepository.findAll();
    }

    public Transacao buscarPorId(Long id){
        return transacaoRepository.findById(id).orElse(null);
    }

    public Transacao atualizar(Long id, Transacao novaTransacao) {

        Transacao transacaoExistente = transacaoRepository.findById(id)
                .orElse(null);

        if (transacaoExistente == null) {
            return null;
        }

        transacaoExistente.setDescricao(novaTransacao.getDescricao());
        transacaoExistente.setValor(novaTransacao.getValor());
        transacaoExistente.setData(novaTransacao.getData());
        transacaoExistente.setTipo(novaTransacao.getTipo());
        transacaoExistente.setCategoria(novaTransacao.getCategoria());

        return transacaoRepository.save(transacaoExistente);
    }

    public void excluir(Long id) {
        transacaoRepository.deleteById(id);
    }
}