package pl.kupony.application;

import java.time.Duration;
import java.util.Optional;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.dao.DataAccessResourceFailureException;
import org.springframework.dao.TransientDataAccessException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.CannotCreateTransactionException;
import pl.kupony.domain.exception.CouponException;
import pl.kupony.domain.exception.ErrorCode;
import pl.kupony.domain.model.ClientIp;
import pl.kupony.domain.model.Coupon;
import pl.kupony.domain.model.CouponCode;
import pl.kupony.domain.model.IpInfo;
import pl.kupony.domain.port.in.RedeemCouponUseCase;
import pl.kupony.domain.port.out.CouponRepositoryPort;
import pl.kupony.domain.port.out.IpIntelligencePort;

@Service
@RequiredArgsConstructor
public class RedeemCouponService implements RedeemCouponUseCase {

    private static final Duration BUSY_RETRY_AFTER = Duration.ofSeconds(1);

    private final CouponRepositoryPort coupons;
    private final IpIntelligencePort ipIntelligence;
    private final RedemptionWriter writer;
    @Value("${coupons.reveal-exhausted-reason:true}")
    private final boolean revealExhaustedReason;

    @Override
    public RedemptionResult redeem(RedeemCouponCommand command) {
        try {
            return doRedeem(command);
        } catch (TransientDataAccessException | DataAccessResourceFailureException
                 | CannotCreateTransactionException e) {
            throw new CouponException(ErrorCode.COUPON_BUSY,
                    "Coupon service is under heavy load, retry shortly.", BUSY_RETRY_AFTER);
        }
    }

    private RedemptionResult doRedeem(RedeemCouponCommand command) {
        ClientIp ip = ClientIp.of(command.ip());
        if (!CouponCode.isValid(command.code())) {
            throw notFound(command.code());
        }
        CouponCode code = new CouponCode(command.code());
        Coupon coupon = findRedeemable(code);

        IpInfo ipInfo = ipIntelligence.lookup(ip);
        if (ipInfo.anonymized()) {
            throw new CouponException(ErrorCode.VPN_DETECTED, "Redemptions through VPN, proxy or Tor are not allowed.");
        }
        if (!ipInfo.country().equals(coupon.country())) {
            throw new CouponException(ErrorCode.COUPON_COUNTRY_MISMATCH,
                    "Coupon '" + code.value() + "' is not available in your country.");
        }
        return consume(coupon, command, ip, ipInfo.country());
    }

    private Coupon findRedeemable(CouponCode code) {
        Optional<Coupon> found = coupons.findByCode(code);
        if (found.isEmpty()) {
            throw revealExhaustedReason && coupons.existsInArchive(code) ? exhausted(code.value()) : notFound(code.value());
        }
        if (found.get().exhausted()) {
            throw exhaustedOrNotFound(code.value());
        }
        return found.get();
    }

    private RedemptionResult consume(Coupon coupon, RedeemCouponCommand command, ClientIp ip, String country) {
        return writer.consume(coupon, command.userId(), ip, country)
                .orElseThrow(() -> exhaustedOrNotFound(coupon.code().value()));
    }

    private CouponException exhaustedOrNotFound(String code) {
        return revealExhaustedReason ? exhausted(code) : notFound(code);
    }

    private static CouponException exhausted(String code) {
        return new CouponException(ErrorCode.COUPON_EXHAUSTED,
                "Coupon '" + code + "' has reached its maximum number of uses.");
    }

    private static CouponException notFound(String code) {
        return new CouponException(ErrorCode.COUPON_NOT_FOUND, "Coupon '" + code + "' does not exist.");
    }
}
