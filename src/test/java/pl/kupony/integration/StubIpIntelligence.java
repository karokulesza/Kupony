package pl.kupony.integration;

import java.util.Map;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Primary;
import pl.kupony.domain.model.IpInfo;
import pl.kupony.domain.port.out.IpIntelligencePort;

@TestConfiguration(proxyBeanMethods = false)
class StubIpIntelligence {

    static final String PL_IP = "212.77.98.9";
    static final String DE_IP = "85.214.132.117";
    static final String TOR_IP = "185.220.101.1";

    private static final Map<String, IpInfo> ANSWERS = Map.of(
            PL_IP, new IpInfo("PL", false),
            DE_IP, new IpInfo("DE", false),
            TOR_IP, new IpInfo("DE", true));

    @Bean
    @Primary
    IpIntelligencePort stubIpIntelligence() {
        return ip -> ANSWERS.getOrDefault(ip.value(), new IpInfo("PL", false));
    }
}
