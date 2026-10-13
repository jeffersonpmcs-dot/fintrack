package com.jefferson.fintrack.controller;

import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import com.jefferson.fintrack.service.TransacaoService;
import org.springframework.web.bind.annotation.*;
import com.jefferson.fintrack.model.Transacao;
import java.util.List;

@RestController
@RequestMapping("/transacoes")
public class TransacaoController {
    private final TransacaoService transacaoService;

    public TransacaoController(TransacaoService transacaoService) {
        this.transacaoService = transacaoService;
    }

    @GetMapping
    public List<Transacao> listarTodos(){
        return transacaoService.listarTodas();
    }

    @PostMapping
    public ResponseEntity<Transacao> salvar(@Valid @RequestBody Transacao transacao) {

        Transacao transacaoSalva = transacaoService.salvar(transacao);

        return ResponseEntity.status(201).body(transacaoSalva);
    }

    @GetMapping("/{id}")
    public ResponseEntity<Transacao> buscarPorId(@PathVariable Long id){

        Transacao transacao = transacaoService.buscarPorId(id);

        if(transacao == null){
            return ResponseEntity.notFound().build();
        }
        return ResponseEntity.ok(transacao);
    }

    @PutMapping("/{id}")
    public ResponseEntity<Transacao> atualizar(
            @PathVariable Long id,
            @Valid @RequestBody Transacao transacao) {

        Transacao transacaoAtualizada =
                transacaoService.atualizar(id, transacao);

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
