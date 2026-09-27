package com.jefiro.app247.infra.controller;

import com.jefiro.app247.domain.model.dto.PublicAppConfigResponse;
import com.jefiro.app247.infra.service.LegalDocumentsService;
import io.swagger.v3.oas.annotations.Operation;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/public/app-config")
public class PublicAppConfigController {
    private final LegalDocumentsService legalDocuments;

    public PublicAppConfigController(LegalDocumentsService legalDocuments) {
        this.legalDocuments = legalDocuments;
    }

    @GetMapping
    @Operation(summary = "Expõe apenas a configuração pública de documentos legais do App Cliente")
    public PublicAppConfigResponse get() {
        return legalDocuments.publicConfig();
    }
}
