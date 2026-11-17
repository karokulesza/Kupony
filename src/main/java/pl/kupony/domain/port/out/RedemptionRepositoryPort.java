package pl.kupony.domain.port.out;

import java.util.UUID;
import pl.kupony.domain.model.Redemption;

public interface RedemptionRepositoryPort {

    UUID save(Redemption redemption);
}
