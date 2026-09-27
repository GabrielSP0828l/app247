package com.jefiro.app247.domain.model.dto;

public record PublicAppConfigResponse(
        String termsUrl,
        String privacyUrl,
        String termsVersion,
        String privacyVersion,
        boolean legalDocumentsConfigured,
        String operationalGap
) {}
