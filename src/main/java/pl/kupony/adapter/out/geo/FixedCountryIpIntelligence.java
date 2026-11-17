package pl.kupony.adapter.out.geo;

import java.util.Locale;
import lombok.extern.slf4j.Slf4j;
import pl.kupony.domain.model.ClientIp;
import pl.kupony.domain.model.IpInfo;
import pl.kupony.domain.port.out.IpIntelligencePort;

@Slf4j
public class FixedCountryIpIntelligence implements IpIntelligencePort {

    private final IpInfo answer;

    public FixedCountryIpIntelligence(String country) {
        this.answer = new IpInfo(country.toUpperCase(Locale.ROOT), false);
        log.warn("Stress profile active: geolocation disabled, every IP resolves to {}", answer.country());
    }

    @Override
    public IpInfo lookup(ClientIp ip) {
        return answer;
    }
}
