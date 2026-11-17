package pl.kupony.integration;

import java.util.Map;
import java.util.UUID;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.http.MediaType;
import org.springframework.web.client.RestClient;

final class ApiClient {

    private static final ParameterizedTypeReference<Map<String, Object>> JSON = new ParameterizedTypeReference<>() {
    };

    private final RestClient http;

    ApiClient(int port) {
        this.http = RestClient.builder()
                .baseUrl("http://localhost:" + port)
                .defaultStatusHandler(status -> true, (request, response) -> { })
                .build();
    }

    Response createCoupon(String code, String country, int maxUses) {
        return post("/api/v1/coupons", Map.of("code", code, "country", country, "maxUses", maxUses));
    }

    Response redeem(String code, UUID userId, String ip) {
        return post("/api/v1/coupons/" + code + "/redemptions", Map.of("userId", userId.toString(), "ip", ip));
    }

    private Response post(String path, Map<String, Object> body) {
        var entity = http.post().uri(path)
                .contentType(MediaType.APPLICATION_JSON)
                .body(body)
                .retrieve()
                .toEntity(JSON);
        return new Response(entity.getStatusCode().value(), entity.getBody() == null ? Map.of() : entity.getBody());
    }

    record Response(int status, Map<String, Object> json) {

        String errorCode() {
            return (String) json.get("errorCode");
        }
    }
}
