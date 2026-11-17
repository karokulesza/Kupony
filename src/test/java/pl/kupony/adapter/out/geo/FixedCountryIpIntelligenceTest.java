package pl.kupony.adapter.out.geo;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;
import pl.kupony.domain.model.ClientIp;
import pl.kupony.domain.model.IpInfo;

class FixedCountryIpIntelligenceTest {

    @Test
    void everyAddressResolvesToConfiguredCountryWithoutAnonymizer() {
        var intelligence = new FixedCountryIpIntelligence("pl");

        assertThat(intelligence.lookup(ClientIp.of("212.77.98.9"))).isEqualTo(new IpInfo("PL", false));
        assertThat(intelligence.lookup(ClientIp.of("185.220.101.1"))).isEqualTo(new IpInfo("PL", false));
    }
}
