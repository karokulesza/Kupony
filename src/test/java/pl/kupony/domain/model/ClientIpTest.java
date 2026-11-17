package pl.kupony.domain.model;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.NullAndEmptySource;
import org.junit.jupiter.params.provider.ValueSource;
import pl.kupony.domain.exception.CouponException;
import pl.kupony.domain.exception.ErrorCode;

class ClientIpTest {

    @ParameterizedTest
    @ValueSource(strings = {"83.12.34.56", "212.77.98.9", "2001:4860:4860::8888"})
    void acceptsPublicAddresses(String raw) {
        assertThat(ClientIp.of(raw).value()).isEqualTo(raw);
    }

    @ParameterizedTest
    @ValueSource(strings = {"2001:4860:4860:0:0:0:0:8888", "2001:4860:4860:0000::8888", "2001:4860:4860::8888",
            "2001:4860:4860::0:8888"})
    void ipv6SpellingsShareOneCanonicalForm(String raw) {
        assertThat(ClientIp.of(raw)).isEqualTo(ClientIp.of("2001:4860:4860::8888"));
        assertThat(ClientIp.of(raw).value()).isEqualTo("2001:4860:4860::8888");
    }

    @Test
    void ipv6IsLowercased() {
        assertThat(ClientIp.of("2001:4860:4860::ABCD").value()).isEqualTo("2001:4860:4860::abcd");
    }

    @Test
    void ipv4MappedIpv6BecomesIpv4() {
        assertThat(ClientIp.of("::ffff:8.8.8.8").value()).isEqualTo("8.8.8.8");
    }

    @ParameterizedTest
    @NullAndEmptySource
    @ValueSource(strings = {
            "10.0.0.1", "172.16.5.4", "192.168.1.1", "127.0.0.1", "::1", "169.254.1.1", "fe80::1",
            "224.0.0.1", "0.0.0.0", "::", "::ffff:192.168.1.1", "fd12:3456:789a::1", "100.64.0.1",
            "localhost", "example.com", "abc", "999.1.1.1", "1.2.3",
            "192.0.2.1", "198.51.100.7", "203.0.113.5", "198.18.0.1", "192.0.0.1", "240.0.0.1",
            "255.255.255.255", "0.1.2.3", "2001:db8::1", "3fff::1", "::10.0.0.1", "::8.8.8.8",
            "64:ff9b::808:808", "100::1", "2001::1", "ff02::1"
    })
    void rejectsNonPublicOrNonLiteralAddresses(String raw) {
        assertThatThrownBy(() -> ClientIp.of(raw))
                .isInstanceOfSatisfying(CouponException.class,
                        e -> assertThat(e.code()).isEqualTo(ErrorCode.INVALID_IP_ADDRESS));
    }
}
