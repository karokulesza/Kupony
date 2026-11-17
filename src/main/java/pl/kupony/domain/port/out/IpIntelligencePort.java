package pl.kupony.domain.port.out;

import pl.kupony.domain.model.ClientIp;
import pl.kupony.domain.model.IpInfo;

public interface IpIntelligencePort {

    IpInfo lookup(ClientIp ip);
}
