package pl.kupony;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.ApplicationContext;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.ActiveProfiles;
import pl.kupony.adapter.out.geo.FixedCountryIpIntelligence;
import pl.kupony.adapter.out.geo.IpApiClient;
import pl.kupony.domain.port.out.IpIntelligencePort;

@SpringBootTest
@ActiveProfiles("stress")
@Import(TestcontainersConfiguration.class)
class StressProfileIT {

    @Autowired
    ApplicationContext context;

    @Test
    void stressProfileReplacesIpApiWithFixedCountry() {
        assertThat(context.getBean(IpIntelligencePort.class)).isInstanceOf(FixedCountryIpIntelligence.class);
        assertThat(context.getBeansOfType(IpApiClient.class)).isEmpty();
    }
}
