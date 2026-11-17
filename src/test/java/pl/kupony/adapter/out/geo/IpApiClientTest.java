package pl.kupony.adapter.out.geo;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.test.web.client.ExpectedCount.once;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withException;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withServerError;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withStatus;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

import java.io.IOException;
import java.time.Duration;
import java.time.Instant;
import pl.kupony.MutableClock;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;
import pl.kupony.domain.exception.CouponException;
import pl.kupony.domain.exception.ErrorCode;
import pl.kupony.domain.model.ClientIp;
import pl.kupony.domain.model.IpInfo;

class IpApiClientTest {

    private static final String BASE = "http://ip-api.test";
    private static final String FIELDS = "?fields=status,message,countryCode,proxy,hosting";
    private static final ClientIp PL_IP = ClientIp.of("212.77.98.9");
    private static final ClientIp OTHER_IP = ClientIp.of("83.12.34.56");

    private final MutableClock clock = new MutableClock(Instant.parse("2026-11-17T10:00:00Z"));
    private MockRestServiceServer server;
    private RestClient.Builder builder;

    @BeforeEach
    void setUp() {
        builder = RestClient.builder();
        server = MockRestServiceServer.bindTo(builder).build();
    }

    private IpApiClient client(boolean rejectHosting) {
        var properties = new GeoProperties(BASE, Duration.ofMillis(300), Duration.ofSeconds(1), rejectHosting,
                Duration.ofHours(1), 1000);
        return new IpApiClient(builder, properties, new IpGeoCache(1000, Duration.ofHours(1), clock), clock);
    }

    private void respond(ClientIp ip, String json) {
        server.expect(once(), requestTo(BASE + "/json/" + ip.value() + FIELDS))
                .andRespond(withSuccess(json, MediaType.APPLICATION_JSON));
    }

    private void respond(ClientIp ip, String json, HttpHeaders headers) {
        server.expect(once(), requestTo(BASE + "/json/" + ip.value() + FIELDS))
                .andRespond(withSuccess(json, MediaType.APPLICATION_JSON).headers(headers));
    }

    private static void assertUnavailable(Runnable call) {
        assertThatThrownBy(call::run).isInstanceOfSatisfying(CouponException.class,
                e -> assertThat(e.code()).isEqualTo(ErrorCode.GEOLOCATION_UNAVAILABLE));
    }

    @Test
    void returnsCountryForCleanIp() {
        respond(PL_IP, """
                {"status":"success","countryCode":"PL","proxy":false,"hosting":false}""");

        assertThat(client(false).lookup(PL_IP)).isEqualTo(new IpInfo("PL", false));
    }

    @Test
    void proxyIsAnonymized() {
        respond(PL_IP, """
                {"status":"success","countryCode":"DE","proxy":true,"hosting":true}""");

        assertThat(client(false).lookup(PL_IP).anonymized()).isTrue();
    }

    @Test
    void hostingIsAnonymizedOnlyWhenConfigured() {
        respond(PL_IP, """
                {"status":"success","countryCode":"CA","proxy":false,"hosting":true}""");
        respond(OTHER_IP, """
                {"status":"success","countryCode":"CA","proxy":false,"hosting":true}""");

        assertThat(client(false).lookup(PL_IP).anonymized()).isFalse();
        assertThat(client(true).lookup(OTHER_IP).anonymized()).isTrue();
    }

    @Test
    void lowercaseCountryIsNormalized() {
        respond(PL_IP, """
                {"status":"success","countryCode":"pl","proxy":false,"hosting":false}""");

        assertThat(client(false).lookup(PL_IP).country()).isEqualTo("PL");
    }

    @Test
    void successWithoutCountryIsUnavailable() {
        respond(PL_IP, """
                {"status":"success","proxy":false,"hosting":false}""");

        assertUnavailable(() -> client(false).lookup(PL_IP));
    }

    @Test
    void addressTheProviderCannotGeolocateIsInvalidAndNotRetryable() {
        respond(PL_IP, """
                {"status":"fail","message":"reserved range"}""");

        assertThatThrownBy(() -> client(false).lookup(PL_IP))
                .isInstanceOfSatisfying(CouponException.class, e -> {
                    assertThat(e.code()).isEqualTo(ErrorCode.INVALID_IP_ADDRESS);
                    assertThat(e.retryAfter()).isEmpty();
                });
    }

    @Test
    void serverErrorIsUnavailable() {
        server.expect(requestTo(BASE + "/json/" + PL_IP.value() + FIELDS)).andRespond(withServerError());

        assertUnavailable(() -> client(false).lookup(PL_IP));
    }

    @Test
    void networkErrorIsUnavailable() {
        server.expect(requestTo(BASE + "/json/" + PL_IP.value() + FIELDS))
                .andRespond(withException(new IOException("connection reset")));

        assertUnavailable(() -> client(false).lookup(PL_IP));
    }

    @Test
    void tooManyRequestsBlocksFurtherCallsUntilTtlPasses() {
        server.expect(once(), requestTo(BASE + "/json/" + PL_IP.value() + FIELDS))
                .andRespond(withStatus(HttpStatus.TOO_MANY_REQUESTS).header("X-Ttl", "30"));
        respond(OTHER_IP, """
                {"status":"success","countryCode":"PL","proxy":false,"hosting":false}""");
        var client = client(false);

        assertThatThrownBy(() -> client.lookup(PL_IP)).isInstanceOfSatisfying(CouponException.class, e -> {
            assertThat(e.code()).isEqualTo(ErrorCode.GEOLOCATION_UNAVAILABLE);
            assertThat(e.retryAfter()).contains(Duration.ofSeconds(30));
        });

        clock.advance(Duration.ofSeconds(10));
        assertThatThrownBy(() -> client.lookup(OTHER_IP)).isInstanceOfSatisfying(CouponException.class,
                e -> assertThat(e.retryAfter()).contains(Duration.ofSeconds(20)));

        clock.advance(Duration.ofSeconds(20));
        assertThat(client.lookup(OTHER_IP).country()).isEqualTo("PL");
        server.verify();
    }

    @Test
    void exhaustedRateLimitHeaderBlocksNextCall() {
        var headers = new HttpHeaders();
        headers.add("X-Rl", "0");
        headers.add("X-Ttl", "20");
        respond(PL_IP, """
                {"status":"success","countryCode":"PL","proxy":false,"hosting":false}""", headers);
        var client = client(false);

        assertThat(client.lookup(PL_IP).country()).isEqualTo("PL");
        assertThatThrownBy(() -> client.lookup(OTHER_IP)).isInstanceOfSatisfying(CouponException.class,
                e -> assertThat(e.retryAfter()).contains(Duration.ofSeconds(20)));
        server.verify();
    }

    @Test
    void secondLookupOfSameIpComesFromCache() {
        respond(PL_IP, """
                {"status":"success","countryCode":"PL","proxy":false,"hosting":false}""");
        var client = client(false);

        client.lookup(PL_IP);
        assertThat(client.lookup(PL_IP)).isEqualTo(new IpInfo("PL", false));
        server.verify();
    }

    @Test
    void failuresAreNotCached() {
        respond(PL_IP, """
                {"status":"fail","message":"timeout"}""");
        respond(PL_IP, """
                {"status":"success","countryCode":"PL","proxy":false,"hosting":false}""");
        var client = client(false);

        assertUnavailable(() -> client.lookup(PL_IP));
        assertThat(client.lookup(PL_IP).country()).isEqualTo("PL");
        server.verify();
    }
}
