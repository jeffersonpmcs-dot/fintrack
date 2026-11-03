package com.jefferson.fintrack.service;

import com.jefferson.fintrack.model.Categoria;
import com.jefferson.fintrack.exception.CategoriaNaoEncontradaException;
import com.jefferson.fintrack.exception.CategoriaEmUsoException;
import com.jefferson.fintrack.repository.CategoriaRepository;
import com.jefferson.fintrack.repository.TransacaoRepository;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
@Transactional(readOnly = true)
public class CategoriaService {

    private final CategoriaRepository categoriaRepository;
    private final TransacaoRepository transacaoRepository;
    private final UsuarioAtualService usuarioAtualService;

    public CategoriaService(CategoriaRepository categoriaRepository,
                            TransacaoRepository transacaoRepository,
                            UsuarioAtualService usuarioAtualService) {
        this.categoriaRepository = categoriaRepository;
        this.transacaoRepository = transacaoRepository;
        this.usuarioAtualService = usuarioAtualService;
    }

    @Transactional
    public Categoria salvar(Categoria categoria) {
        Categoria nova = new Categoria();
        nova.setNome(categoria.getNome());
        nova.setUsuario(usuarioAtualService.obter());
        return categoriaRepository.save(nova);
    }

    public List<Categoria> listarTodas() {
        return categoriaRepository.findAllByUsuarioId(usuarioAtualService.obter().getId());
    }

    public Categoria buscarPorId(Long id) {
        return categoriaRepository.findByIdAndUsuarioId(id, usuarioAtualService.obter().getId())
                .orElseThrow(CategoriaNaoEncontradaException::new);
    }

    @Transactional
    public Categoria atualizar(Long id, Categoria categoria) {
        Categoria categoriaExistente = buscarPorId(id);
        categoriaExistente.setNome(categoria.getNome());
        return categoriaRepository.save(categoriaExistente);
    }

    @Transactional
    public void excluir(Long id) {
        Categoria categoria = buscarPorId(id);

        if (transacaoRepository.existsByCategoriaId(id)) {
            throw new CategoriaEmUsoException();
        }

        try {
            categoriaRepository.delete(categoria);
            categoriaRepository.flush();
        } catch (DataIntegrityViolationException exception) {
            throw new CategoriaEmUsoException(exception);
        }
    }
}
