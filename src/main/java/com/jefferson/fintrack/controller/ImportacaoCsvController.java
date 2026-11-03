package com.jefferson.fintrack.controller;

import com.jefferson.fintrack.dto.ImportacaoCsvResponse;
import com.jefferson.fintrack.service.ImportacaoCsvService;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

@RestController
@RequestMapping("/transacoes")
public class ImportacaoCsvController {

    private final ImportacaoCsvService importacaoCsvService;

    public ImportacaoCsvController(ImportacaoCsvService importacaoCsvService) {
        this.importacaoCsvService = importacaoCsvService;
    }

    @PostMapping(value = "/importar-csv", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ResponseEntity<ImportacaoCsvResponse> importar(@RequestParam("arquivo") MultipartFile arquivo) {
        return ResponseEntity.status(201).body(importacaoCsvService.importar(arquivo));
    }
}
