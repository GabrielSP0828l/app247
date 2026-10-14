package com.jefiro.app247.infra.service;

import com.jefiro.app247.domain.model.MercadoPagoConta;
import com.jefiro.app247.domain.model.dto.OrderResponse;
import com.jefiro.app247.infra.exception.ExternalServiceException;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.stereotype.Service;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.web.client.RestTemplate;
import org.springframework.web.client.RestClientException;

@Service
public class MercadoPagoOrderQueryService {

    private static final String DEFAULT_API_BASE_URL = "https://api.mercadopago.com";
    private final RestTemplate restTemplate;
    @Value("${mercado-pago.api-base-url:https://api.mercadopago.com}")
    private String apiBaseUrl;
    @Autowired
    private OauthMercadoPagoService oauthMercadoPagoService;

    public MercadoPagoOrderQueryService(
            RestTemplate restTemplate
    ) {
        this.restTemplate = restTemplate;
    }

    public OrderResponse getOrder(String mercadoPagoUserId, String mercadoPagoOrderId) {
        if (mercadoPagoUserId == null || mercadoPagoUserId.isBlank()) {
            throw new IllegalArgumentException("Webhook resumido sem user_id");
        }
        if (mercadoPagoOrderId == null || mercadoPagoOrderId.isBlank()) {
            throw new IllegalArgumentException("Webhook resumido sem data.id");
        }

        MercadoPagoConta conta = oauthMercadoPagoService.getByMpUserId(mercadoPagoUserId);

        return getOrder(conta, mercadoPagoOrderId);
    }

    public OrderResponse getOrderByEmpresa(String empresaId, String mercadoPagoOrderId) {
        if (empresaId == null || empresaId.isBlank()) {
            throw new IllegalArgumentException("Order local sem empresa");
        }
        MercadoPagoConta conta = oauthMercadoPagoService.getByEmpresa(empresaId);
        return getOrder(conta, mercadoPagoOrderId);
    }

    private OrderResponse getOrder(MercadoPagoConta conta, String mercadoPagoOrderId) {
        if (mercadoPagoOrderId == null || mercadoPagoOrderId.isBlank()) {
            throw new IllegalArgumentException("Order local sem mpOrderId");
        }
        HttpHeaders headers = new HttpHeaders();
        headers.setBearerAuth(conta.getAccessToken());

        try {
            OrderResponse response = restTemplate.exchange(
                    ordersUrl() + "/" + mercadoPagoOrderId,
                    HttpMethod.GET,
                    new HttpEntity<Void>(headers),
                    OrderResponse.class
            ).getBody();
            if (response == null) {
                throw new ExternalServiceException("Mercado Pago", "Consulta de order retornou corpo vazio", null);
            }
            return response;
        } catch (RestClientException e) {
            throw new ExternalServiceException("Mercado Pago",
                    "Falha ao consultar order", e);
        }
    }

    private String ordersUrl() {
        String baseUrl = apiBaseUrl == null || apiBaseUrl.isBlank()
                ? DEFAULT_API_BASE_URL : apiBaseUrl;
        return baseUrl.replaceAll("/+$", "") + "/v1/orders";
    }
}
