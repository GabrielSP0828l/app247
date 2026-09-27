package com.jefiro.app247.infra.service;

import com.jefiro.app247.domain.model.dto.PublicAppConfigResponse;
import com.jefiro.app247.domain.model.dto.auth.ClientRegistrationRequest;
import com.jefiro.app247.infra.exception.ApiBusinessException;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;

@Service
public class LegalDocumentsService {
    private final String termsUrl;
    private final String privacyUrl;
    private final String termsVersion;
    private final String privacyVersion;

    public LegalDocumentsService(@Value("${app247.terms-url:}") String termsUrl,
                                 @Value("${app247.privacy-url:}") String privacyUrl,
                                 @Value("${app247.terms-version:}") String termsVersion,
                                 @Value("${app247.privacy-version:}") String privacyVersion) {
        this.termsUrl = clean(termsUrl);
        this.privacyUrl = clean(privacyUrl);
        this.termsVersion = clean(termsVersion);
        this.privacyVersion = clean(privacyVersion);
    }

    public boolean configured() {
        return termsUrl != null && privacyUrl != null && termsVersion != null && privacyVersion != null;
    }

    public void validateAcceptance(ClientRegistrationRequest request) {
        if (!configured()) return;
        if (!Boolean.TRUE.equals(request.termsAccepted()) || !Boolean.TRUE.equals(request.privacyAccepted())
                || !termsVersion.equals(request.termsVersion()) || !privacyVersion.equals(request.privacyVersion())) {
            throw new ApiBusinessException(HttpStatus.BAD_REQUEST, "INVALID_REGISTRATION_DATA",
                    "É necessário aceitar as versões atuais dos Termos de Uso e da Política de Privacidade.");
        }
    }

    public PublicAppConfigResponse publicConfig() {
        return new PublicAppConfigResponse(termsUrl, privacyUrl, termsVersion, privacyVersion, configured(),
                configured() ? null : "LEGAL_DOCUMENTS_NOT_CONFIGURED");
    }

    public String termsVersion() { return termsVersion; }
    public String privacyVersion() { return privacyVersion; }

    private static String clean(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }
}
