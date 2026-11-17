package pl.kupony.config;

import java.net.http.HttpClient;
import java.time.Clock;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Profile;
import org.springframework.http.client.JdkClientHttpRequestFactory;
import org.springframework.web.client.RestClient;
import pl.kupony.adapter.out.geo.FixedCountryIpIntelligence;
import pl.kupony.adapter.out.geo.GeoProperties;
import pl.kupony.adapter.out.geo.IpApiClient;
import pl.kupony.adapter.out.geo.IpGeoCache;

@Configuration(proxyBeanMethods = false)
@EnableConfigurationProperties(GeoProperties.class)
class GeoConfig {

    static final String STRESS_PROFILE = "stress";

    @Bean
    @Profile(STRESS_PROFILE)
    FixedCountryIpIntelligence fixedCountryIpIntelligence(@Value("${coupons.stress.country:PL}") String country) {
        return new FixedCountryIpIntelligence(country);
    }

    @Bean
    @Profile("!" + STRESS_PROFILE)
    IpGeoCache ipGeoCache(GeoProperties properties, Clock clock) {
        return new IpGeoCache(properties.cacheMaxSize(), properties.cacheTtl(), clock);
    }

    @Bean
    @Profile("!" + STRESS_PROFILE)
    IpApiClient ipApiClient(GeoProperties properties, IpGeoCache cache, Clock clock) {
        var httpClient = HttpClient.newBuilder().connectTimeout(properties.connectTimeout()).build();
        var requestFactory = new JdkClientHttpRequestFactory(httpClient);
        requestFactory.setReadTimeout(properties.readTimeout());
        return new IpApiClient(RestClient.builder().requestFactory(requestFactory), properties, cache, clock);
    }
}
