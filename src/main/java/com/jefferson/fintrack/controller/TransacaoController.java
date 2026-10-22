package com.jefferson.fintrack.controller;

import com.jefferson.fintrack.dto.TransacaoRequest;
import com.jefferson.fintrack.dto.ResumoFinanceiroResponse;
import com.jefferson.fintrack.model.Transacao;
import com.jefferson.fintrack.model.TipoTransacao;
import com.jefferson.fintrack.service.TransacaoService;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.time.LocalDate;

@RestController
@RequestMapping("/transacoes")
public class TransacaoController {

    private final TransacaoService transacaoService;

    public TransacaoController(TransacaoService transacaoService) {
        this.transacaoService = transacaoService;
    }

    @GetMapping
    public List<Transacao> listarTodos(
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE)
            LocalDate dataInicio,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE)
            LocalDate dataFim,
            @RequestParam(required = false) Long categoriaId,
            @RequestParam(required = false) TipoTransacao tipo) {
        return transacaoService.listar(dataInicio, dataFim, categoriaId, tipo);
    }

    @PostMapping
    public ResponseEntity<Transacao> salvar(
            @Valid @RequestBody TransacaoRequest request) {

        Transacao transacaoSalva = transacaoService.salvar(request);

        return ResponseEntity.status(201).body(transacaoSalva);
    }

    @GetMapping("/resumo")
    public ResumoFinanceiroResponse resumir(
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE)
            LocalDate dataInicio,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE)
            LocalDate dataFim) {
        return transacaoService.resumir(dataInicio, dataFim);
    }

    @GetMapping("/{id}")
    public ResponseEntity<Transacao> buscarPorId(@PathVariable Long id) {

        Transacao transacao = transacaoService.buscarPorId(id);

        return ResponseEntity.ok(transacao);
    }

    @PutMapping("/{id}")
    public ResponseEntity<Transacao> atualizar(
            @PathVariable Long id,
            @Valid @RequestBody TransacaoRequest request) {

        Transacao transacaoAtualizada =
                transacaoService.atualizar(id, request);

        return ResponseEntity.ok(transacaoAtualizada);
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> excluir(@PathVariable Long id) {

        transacaoService.excluir(id);

        return ResponseEntity.noContent().build();
    }
}
