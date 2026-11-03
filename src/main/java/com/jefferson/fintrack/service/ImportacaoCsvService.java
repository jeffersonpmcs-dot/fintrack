package com.jefferson.fintrack.service;

import com.jefferson.fintrack.dto.ImportacaoCsvResponse;
import com.jefferson.fintrack.exception.ImportacaoCsvConflitoException;
import com.jefferson.fintrack.exception.ImportacaoCsvInvalidaException;
import com.jefferson.fintrack.model.Categoria;
import com.jefferson.fintrack.model.Transacao;
import com.jefferson.fintrack.model.Usuario;
import com.jefferson.fintrack.repository.CategoriaRepository;
import com.jefferson.fintrack.repository.TransacaoRepository;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Service
public class ImportacaoCsvService {

    private final LeitorTransacoesCsv leitor;
    private final UsuarioAtualService usuarioAtualService;
    private final CategoriaRepository categoriaRepository;
    private final TransacaoRepository transacaoRepository;

    public ImportacaoCsvService(LeitorTransacoesCsv leitor, UsuarioAtualService usuarioAtualService,
                               CategoriaRepository categoriaRepository, TransacaoRepository transacaoRepository) {
        this.leitor = leitor;
        this.usuarioAtualService = usuarioAtualService;
        this.categoriaRepository = categoriaRepository;
        this.transacaoRepository = transacaoRepository;
    }

    @Transactional
    public ImportacaoCsvResponse importar(MultipartFile arquivo) {
        Usuario usuario = usuarioAtualService.obter();
        List<LeitorTransacoesCsv.Linha> linhas = leitor.ler(arquivo);
        Map<Long, Categoria> categorias = new HashMap<>();
        List<Transacao> novas = new ArrayList<>(linhas.size());
        for (LeitorTransacoesCsv.Linha linha : linhas) {
            var request = linha.transacao();
            Categoria categoria = categorias.computeIfAbsent(request.getCategoriaId(), id ->
                    categoriaRepository.findByIdAndUsuarioId(id, usuario.getId())
                            .orElseThrow(() -> new ImportacaoCsvInvalidaException(linha.numero(),
                                    "Categoria não encontrada para esta conta")));
            Transacao transacao = new Transacao();
            transacao.setDescricao(request.getDescricao());
            transacao.setValor(request.getValor());
            transacao.setData(request.getData());
            transacao.setTipo(request.getTipo());
            transacao.setCategoria(categoria);
            transacao.setUsuario(usuario);
            novas.add(transacao);
        }

        try {
            transacaoRepository.saveAll(novas);
            transacaoRepository.flush();
        } catch (DataIntegrityViolationException exception) {
            throw new ImportacaoCsvConflitoException(exception);
        }
        return new ImportacaoCsvResponse(novas.size());
    }
}
