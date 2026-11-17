package pl.kupony.domain.model;

import java.net.Inet4Address;
import java.net.InetAddress;
import java.net.UnknownHostException;
import java.util.Arrays;
import java.util.List;
import java.util.regex.Pattern;
import pl.kupony.domain.exception.CouponException;
import pl.kupony.domain.exception.ErrorCode;

public record ClientIp(String value) {

    private static final Pattern IPV4 = Pattern.compile("^(25[0-5]|2[0-4]\\d|1\\d\\d|[1-9]?\\d)(\\.(25[0-5]|2[0-4]\\d|1\\d\\d|[1-9]?\\d)){3}$");
    private static final Pattern IPV6 = Pattern.compile("^[0-9A-Fa-f:.]+$");

    private static final List<Range> SPECIAL_PURPOSE = List.of(
            Range.of("0.0.0.0/8"), Range.of("10.0.0.0/8"), Range.of("100.64.0.0/10"), Range.of("127.0.0.0/8"),
            Range.of("169.254.0.0/16"), Range.of("172.16.0.0/12"), Range.of("192.0.0.0/24"),
            Range.of("192.0.2.0/24"), Range.of("192.88.99.0/24"), Range.of("192.168.0.0/16"),
            Range.of("198.18.0.0/15"), Range.of("198.51.100.0/24"), Range.of("203.0.113.0/24"),
            Range.of("224.0.0.0/4"), Range.of("240.0.0.0/4"),
            Range.of("::/96"), Range.of("64:ff9b::/96"), Range.of("64:ff9b:1::/48"), Range.of("100::/64"),
            Range.of("2001::/23"), Range.of("2001:db8::/32"), Range.of("3fff::/20"), Range.of("fc00::/7"),
            Range.of("fe80::/10"), Range.of("ff00::/8"));

    public ClientIp {
        InetAddress address = parseLiteral(value);
        if (address == null || SPECIAL_PURPOSE.stream().anyMatch(r -> r.contains(address))) {
            throw new CouponException(ErrorCode.INVALID_IP_ADDRESS, "IP address must be a public IPv4 or IPv6 literal.");
        }
        value = canonical(address);
    }

    public static ClientIp of(String raw) {
        return new ClientIp(raw);
    }

    private static InetAddress parseLiteral(String raw) {
        if (raw == null || !(IPV4.matcher(raw).matches() || (raw.contains(":") && IPV6.matcher(raw).matches()))) {
            return null;
        }
        try {
            return InetAddress.getByName(raw);
        } catch (UnknownHostException e) {
            return null;
        }
    }

    private static String canonical(InetAddress address) {
        if (address instanceof Inet4Address) {
            return address.getHostAddress();
        }
        String[] groups = address.getHostAddress().split(":");
        int bestStart = -1;
        int bestLength = 1;
        for (int i = 0; i < groups.length; i++) {
            int j = i;
            while (j < groups.length && groups[j].equals("0")) {
                j++;
            }
            if (j - i > bestLength) {
                bestStart = i;
                bestLength = j - i;
            }
        }
        if (bestStart < 0) {
            return String.join(":", groups);
        }
        String head = String.join(":", Arrays.copyOfRange(groups, 0, bestStart));
        String tail = String.join(":", Arrays.copyOfRange(groups, bestStart + bestLength, groups.length));
        return head + "::" + tail;
    }

    private record Range(byte[] network, int prefix) {

        static Range of(String cidr) {
            int slash = cidr.indexOf('/');
            try {
                return new Range(InetAddress.getByName(cidr.substring(0, slash)).getAddress(),
                        Integer.parseInt(cidr.substring(slash + 1)));
            } catch (UnknownHostException e) {
                throw new IllegalArgumentException(cidr, e);
            }
        }

        boolean contains(InetAddress address) {
            byte[] bytes = address.getAddress();
            if (bytes.length != network.length) {
                return false;
            }
            int full = prefix / 8;
            for (int i = 0; i < full; i++) {
                if (bytes[i] != network[i]) {
                    return false;
                }
            }
            int rest = prefix % 8;
            if (rest == 0) {
                return true;
            }
            int mask = 0xFF << (8 - rest);
            return (bytes[full] & mask) == (network[full] & mask);
        }
    }
}
