package com.jefferson.fintrack.controller;

import com.jefferson.fintrack.model.TipoTransacao;
import com.jefferson.fintrack.service.ExportacaoCsvService;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.CacheControl;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.nio.charset.StandardCharsets;
import java.time.LocalDate;

@RestController
@RequestMapping("/transacoes")
public class ExportacaoCsvController {

    private final ExportacaoCsvService exportacaoCsvService;

    public ExportacaoCsvController(ExportacaoCsvService exportacaoCsvService) {
        this.exportacaoCsvService = exportacaoCsvService;
    }

    @GetMapping("/exportar-csv")
    public ResponseEntity<byte[]> exportar(
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate dataInicio,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate dataFim,
            @RequestParam(required = false) Long categoriaId,
            @RequestParam(required = false) TipoTransacao tipo) {
        byte[] conteudo = exportacaoCsvService.exportar(dataInicio, dataFim, categoriaId, tipo);
        return ResponseEntity.ok()
                .contentType(new MediaType("text", "csv", StandardCharsets.UTF_8))
                .header(HttpHeaders.CONTENT_DISPOSITION,
                        ContentDisposition.attachment().filename("transacoes.csv").build().toString())
                .cacheControl(CacheControl.noStore())
                .contentLength(conteudo.length)
                .body(conteudo);
    }
}
