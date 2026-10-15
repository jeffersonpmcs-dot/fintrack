package com.jefferson.fintrack.controller;

import com.jefferson.fintrack.dto.TransacaoRequest;
import com.jefferson.fintrack.model.Transacao;
import com.jefferson.fintrack.service.TransacaoService;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/transacoes")
public class TransacaoController {

    private final TransacaoService transacaoService;

    public TransacaoController(TransacaoService transacaoService) {
        this.transacaoService = transacaoService;
    }

    @GetMapping
    public List<Transacao> listarTodos() {
        return transacaoService.listarTodas();
    }

    @PostMapping
    public ResponseEntity<Transacao> salvar(
            @Valid @RequestBody TransacaoRequest request) {

        Transacao transacaoSalva = transacaoService.salvar(request);

        if (transacaoSalva == null) {
            return ResponseEntity.notFound().build();
        }

        return ResponseEntity.status(201).body(transacaoSalva);
    }

    @GetMapping("/{id}")
    public ResponseEntity<Transacao> buscarPorId(@PathVariable Long id) {

        Transacao transacao = transacaoService.buscarPorId(id);

        if (transacao == null) {
            return ResponseEntity.notFound().build();
        }

        return ResponseEntity.ok(transacao);
    }

    @PutMapping("/{id}")
    public ResponseEntity<Transacao> atualizar(
            @PathVariable Long id,
            @Valid @RequestBody TransacaoRequest request) {

        Transacao transacaoAtualizada =
                transacaoService.atualizar(id, request);

        if (transacaoAtualizada == null) {
            return ResponseEntity.notFound().build();
        }

        return ResponseEntity.ok(transacaoAtualizada);
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> excluir(@PathVariable Long id) {

        Transacao transacao = transacaoService.buscarPorId(id);

        if (transacao == null) {
            return ResponseEntity.notFound().build();
        }

        transacaoService.excluir(id);

        return ResponseEntity.noContent().build();
    }
}