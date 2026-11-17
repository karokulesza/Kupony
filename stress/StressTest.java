import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Random;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicIntegerArray;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.atomic.LongAdder;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class StressTest {

    private static final Pattern ERROR_CODE = Pattern.compile("\"errorCode\"\\s*:\\s*\"([A-Z_]+)\"");
    private static final Pattern GAUGE_VALUE = Pattern.compile("\"value\"\\s*:\\s*([0-9.Ee+-]+)");

    record Config(String url, int coupons, int minUses, int maxUses, int threads, int rps, String country,
                  String ip, long seed, Duration archiveTimeout, Duration reportEvery) {

        static Config parse(String[] args) {
            Map<String, String> a = new HashMap<>();
            for (String arg : args) {
                if (arg.equals("--help") || arg.equals("-h")) {
                    usage();
                    System.exit(0);
                }
                if (!arg.startsWith("--") || !arg.contains("=")) {
                    System.err.println("Unknown argument: " + arg);
                    usage();
                    System.exit(2);
                }
                int eq = arg.indexOf('=');
                a.put(arg.substring(2, eq), arg.substring(eq + 1));
            }
            var config = new Config(
                    a.getOrDefault("url", "http://localhost:8080"),
                    Integer.parseInt(a.getOrDefault("coupons", "5000")),
                    Integer.parseInt(a.getOrDefault("min-uses", "2000")),
                    Integer.parseInt(a.getOrDefault("max-uses", "10000")),
                    Integer.parseInt(a.getOrDefault("threads", "16")),
                    Integer.parseInt(a.getOrDefault("rps", "0")),
                    a.getOrDefault("country", "PL"),
                    a.getOrDefault("ip", "212.77.98.9"),
                    Long.parseLong(a.getOrDefault("seed", String.valueOf(System.nanoTime()))),
                    Duration.ofMinutes(Long.parseLong(a.getOrDefault("archive-timeout-min", "120"))),
                    Duration.ofSeconds(Long.parseLong(a.getOrDefault("report-sec", "5"))));
            if (config.coupons < 1 || config.minUses < 1 || config.maxUses < config.minUses || config.threads < 1
                    || config.rps < 0) {
                System.err.println("Invalid parameters: " + config);
                System.exit(2);
            }
            return config;
        }

        static void usage() {
            System.out.println("""
                    Usage: java stress/StressTest.java [--name=value ...]
                      --url=http://localhost:8080   service base URL
                      --coupons=5000                number of coupons to create
                      --min-uses=2000               minimum maxUses per coupon (random, inclusive)
                      --max-uses=10000              maximum maxUses per coupon (random, inclusive)
                      --threads=16                  concurrent client workers
                      --rps=0                       request rate limit per second, 0 = unlimited
                      --country=PL                  coupon country (must match coupons.stress.country)
                      --ip=212.77.98.9              client IP sent in every redemption
                      --seed=<nanoTime>             random seed for maxUses
                      --archive-timeout-min=120     how long to wait for archival
                      --report-sec=5                progress report interval
                    The service must run with the "stress" profile:
                      SPRING_PROFILES_ACTIVE=stress docker compose up --build -d""");
        }
    }

    record Coupon(String code, int maxUses) {
    }

    private final Config config;
    private final HttpClient http;
    private final Map<String, LongAdder> unexpected = new ConcurrentHashMap<>();
    private final LongAdder requests = new LongAdder();
    private final LongAdder busyRetries = new LongAdder();
    private final LongAdder latencyNanos = new LongAdder();
    private final AtomicLong maxLatencyNanos = new AtomicLong();
    private final AtomicLong nextSlotNanos = new AtomicLong(System.nanoTime());

    StressTest(Config config) {
        this.config = config;
        this.http = HttpClient.newBuilder()
                .version(HttpClient.Version.HTTP_1_1)
                .connectTimeout(Duration.ofSeconds(5))
                .build();
    }

    public static void main(String[] args) throws Exception {
        Locale.setDefault(Locale.ROOT);
        var config = Config.parse(args);
        boolean ok = new StressTest(config).run();
        System.exit(ok ? 0 : 1);
    }

    boolean run() throws Exception {
        System.out.printf("Config: %s%n", config);
        checkHealth();
        long failedBefore = gauge("coupons.archival.failed");

        List<Coupon> coupons = createCoupons();
        long total = coupons.stream().mapToLong(Coupon::maxUses).sum();
        System.out.printf("Created %d coupons, %,d redemptions to make%n", coupons.size(), total);

        AtomicIntegerArray succeeded = redeemAll(coupons, total);
        boolean ok = verifyCounts(coupons, succeeded) & unexpected.isEmpty();
        ok &= verifyExhausted(coupons);
        ok &= waitForArchival(failedBefore);

        System.out.println();
        System.out.println("=== Summary ===");
        System.out.printf("Requests sent:      %,d%n", requests.sum());
        System.out.printf("COUPON_BUSY retries: %,d%n", busyRetries.sum());
        System.out.printf("Avg latency:        %.1f ms, max %.1f ms%n",
                latencyNanos.sum() / 1e6 / Math.max(1, requests.sum()), maxLatencyNanos.get() / 1e6);
        if (!unexpected.isEmpty()) {
            System.out.println("Unexpected responses:");
            unexpected.forEach((k, v) -> System.out.printf("  %-40s %,d%n", k, v.sum()));
        }
        System.out.println(ok ? "RESULT: OK" : "RESULT: FAILED");
        return ok;
    }

    private void checkHealth() throws Exception {
        var response = send(HttpRequest.newBuilder(URI.create(config.url + "/actuator/health")).GET().build());
        if (response.statusCode() != 200 || !response.body().contains("\"UP\"")) {
            throw new IllegalStateException("Service is not healthy: " + response.statusCode() + " " + response.body());
        }
    }

    private List<Coupon> createCoupons() throws Exception {
        var random = new Random(config.seed);
        String run = Long.toString(Math.abs(random.nextLong()) % 1_000_000_000L, 36).toUpperCase();
        List<Coupon> coupons = new ArrayList<>(config.coupons);
        for (int i = 0; i < config.coupons; i++) {
            int uses = config.minUses + random.nextInt(config.maxUses - config.minUses + 1);
            coupons.add(new Coupon("ST-" + run + "-" + i, uses));
        }
        var created = new AtomicInteger();
        var started = System.nanoTime();
        try (ExecutorService pool = Executors.newFixedThreadPool(config.threads)) {
            for (Coupon coupon : coupons) {
                pool.submit(() -> {
                    String body = "{\"code\":\"%s\",\"country\":\"%s\",\"maxUses\":%d}"
                            .formatted(coupon.code, config.country, coupon.maxUses);
                    var response = post(config.url + "/api/v1/coupons", body);
                    if (response.statusCode() != 201) {
                        throw new IllegalStateException("Create " + coupon.code + " failed: "
                                + response.statusCode() + " " + response.body());
                    }
                    int n = created.incrementAndGet();
                    if (n % 1000 == 0) {
                        System.out.printf("  created %,d / %,d%n", n, config.coupons);
                    }
                    return null;
                });
            }
        }
        if (created.get() != config.coupons) {
            throw new IllegalStateException("Created only " + created.get() + " of " + config.coupons + " coupons");
        }
        System.out.printf("Coupons created in %.1f s%n", (System.nanoTime() - started) / 1e9);
        return coupons;
    }

    private AtomicIntegerArray redeemAll(List<Coupon> coupons, long total) throws Exception {
        var issued = new AtomicIntegerArray(coupons.size());
        var succeeded = new AtomicIntegerArray(coupons.size());
        var queue = new ConcurrentLinkedQueue<Integer>();
        for (int i = 0; i < coupons.size(); i++) {
            queue.add(i);
        }
        var done = new LongAdder();
        var started = System.nanoTime();
        var reporter = Executors.newSingleThreadScheduledExecutor();
        var last = new long[]{0, started};
        reporter.scheduleAtFixedRate(() -> report(done.sum(), total, started, last),
                config.reportEvery.toMillis(), config.reportEvery.toMillis(), TimeUnit.MILLISECONDS);

        try (ExecutorService pool = Executors.newFixedThreadPool(config.threads)) {
            for (int t = 0; t < config.threads; t++) {
                pool.submit(() -> {
                    Integer index;
                    while ((index = queue.poll()) != null) {
                        Coupon coupon = coupons.get(index);
                        int ticket = issued.getAndIncrement(index);
                        if (ticket >= coupon.maxUses) {
                            continue;
                        }
                        if (ticket + 1 < coupon.maxUses) {
                            queue.add(index);
                        }
                        if (redeemUntilAnswered(coupon) == 201) {
                            succeeded.incrementAndGet(index);
                        }
                        done.increment();
                    }
                    return null;
                });
            }
        }
        reporter.shutdownNow();
        report(done.sum(), total, started, last);
        System.out.printf("Redemptions finished in %s%n", format(Duration.ofNanos(System.nanoTime() - started)));
        return succeeded;
    }

    private int redeemUntilAnswered(Coupon coupon) throws Exception {
        String body = "{\"userId\":\"%s\",\"ip\":\"%s\"}".formatted(UUID.randomUUID(), config.ip);
        String url = config.url + "/api/v1/coupons/" + coupon.code + "/redemptions";
        while (true) {
            HttpResponse<String> response;
            try {
                response = post(url, body);
            } catch (java.io.IOException e) {
                recordUnexpected("redeem " + e.getClass().getSimpleName());
                return -1;
            }
            int status = response.statusCode();
            if (status == 201) {
                return status;
            }
            String code = errorCode(response.body());
            if (status == 503 && "COUPON_BUSY".equals(code)) {
                busyRetries.increment();
                Thread.sleep(retryAfterMillis(response));
                continue;
            }
            recordUnexpected("redeem " + status + " " + code);
            return status;
        }
    }

    private boolean verifyCounts(List<Coupon> coupons, AtomicIntegerArray succeeded) {
        int wrong = 0;
        for (int i = 0; i < coupons.size(); i++) {
            if (succeeded.get(i) != coupons.get(i).maxUses) {
                if (wrong++ < 10) {
                    System.out.printf("  %s: %d successful redemptions, expected %d%n",
                            coupons.get(i).code, succeeded.get(i), coupons.get(i).maxUses);
                }
            }
        }
        System.out.println(wrong == 0
                ? "Check: every coupon redeemed exactly maxUses times - OK"
                : "Check: " + wrong + " coupons with wrong redemption count - FAILED");
        return wrong == 0;
    }

    private boolean verifyExhausted(List<Coupon> coupons) throws Exception {
        var wrong = new AtomicInteger();
        try (ExecutorService pool = Executors.newFixedThreadPool(config.threads)) {
            for (Coupon coupon : coupons) {
                pool.submit(() -> {
                    String body = "{\"userId\":\"%s\",\"ip\":\"%s\"}".formatted(UUID.randomUUID(), config.ip);
                    var response = post(config.url + "/api/v1/coupons/" + coupon.code + "/redemptions", body);
                    String code = errorCode(response.body());
                    if (response.statusCode() != 409 || !"COUPON_EXHAUSTED".equals(code)) {
                        wrong.incrementAndGet();
                        recordUnexpected("over-limit " + response.statusCode() + " " + code);
                    }
                    return null;
                });
            }
        }
        System.out.println(wrong.get() == 0
                ? "Check: one more redemption per coupon -> 409 COUPON_EXHAUSTED - OK"
                : "Check: " + wrong.get() + " coupons accepted or misreported an over-limit redemption - FAILED");
        return wrong.get() == 0;
    }

    private boolean waitForArchival(long failedBefore) throws Exception {
        System.out.println("Waiting for archival ...");
        long started = System.nanoTime();
        long deadline = started + config.archiveTimeout.toNanos();
        long firstPending = -1;
        while (System.nanoTime() < deadline) {
            long pending = gauge("coupons.archival.pending");
            long failed = gauge("coupons.archival.failed") - failedBefore;
            if (failed > 0) {
                System.out.printf("Check: %d archival tasks FAILED%n", failed);
                return false;
            }
            if (firstPending < 0) {
                firstPending = pending;
            }
            if (pending == 0) {
                System.out.printf("Check: all coupons archived in %s - OK%n",
                        format(Duration.ofNanos(System.nanoTime() - started)));
                return true;
            }
            double elapsed = (System.nanoTime() - started) / 1e9;
            long archived = firstPending - pending;
            String eta = archived > 0 ? format(Duration.ofSeconds((long) (pending * elapsed / archived))) : "?";
            System.out.printf("  archival pending %,d (archived %,d, %.1f coupons/s, ETA %s)%n",
                    pending, archived, archived / Math.max(elapsed, 0.001), eta);
            Thread.sleep(config.reportEvery.toMillis());
        }
        System.out.println("Check: archival did not finish in " + format(config.archiveTimeout) + " - FAILED");
        return false;
    }

    private void report(long done, long total, long started, long[] last) {
        long now = System.nanoTime();
        double elapsed = (now - started) / 1e9;
        double window = (now - last[1]) / 1e9;
        double currentRps = window > 0 ? (done - last[0]) / window : 0;
        double avgRps = done / Math.max(elapsed, 0.001);
        last[0] = done;
        last[1] = now;
        long remaining = total - done;
        String eta = avgRps > 0 ? format(Duration.ofSeconds((long) (remaining / avgRps))) : "?";
        long req = requests.sum();
        System.out.printf("  %,d / %,d (%.1f%%) | %,.0f req/s now, %,.0f avg | latency avg %.1f ms | busy %,d"
                        + " | errors %,d | elapsed %s | ETA %s%n",
                done, total, 100.0 * done / total, currentRps, avgRps,
                latencyNanos.sum() / 1e6 / Math.max(1, req), busyRetries.sum(),
                unexpected.values().stream().mapToLong(LongAdder::sum).sum(),
                format(Duration.ofSeconds((long) elapsed)), eta);
    }

    private HttpResponse<String> post(String url, String json) throws Exception {
        return send(HttpRequest.newBuilder(URI.create(url))
                .header("Content-Type", "application/json")
                .timeout(Duration.ofSeconds(30))
                .POST(HttpRequest.BodyPublishers.ofString(json))
                .build());
    }

    private HttpResponse<String> send(HttpRequest request) throws Exception {
        throttle();
        long start = System.nanoTime();
        var response = http.send(request, HttpResponse.BodyHandlers.ofString());
        long took = System.nanoTime() - start;
        requests.increment();
        latencyNanos.add(took);
        maxLatencyNanos.accumulateAndGet(took, Math::max);
        return response;
    }

    private void throttle() throws InterruptedException {
        if (config.rps == 0) {
            return;
        }
        long interval = 1_000_000_000L / config.rps;
        long slot = nextSlotNanos.getAndUpdate(next -> Math.max(next, System.nanoTime()) + interval);
        long wait = slot - System.nanoTime();
        if (wait > 0) {
            TimeUnit.NANOSECONDS.sleep(wait);
        }
    }

    private long gauge(String name) throws Exception {
        var response = send(HttpRequest.newBuilder(URI.create(config.url + "/actuator/metrics/" + name)).GET().build());
        Matcher m = GAUGE_VALUE.matcher(response.body());
        if (response.statusCode() != 200 || !m.find()) {
            throw new IllegalStateException("Cannot read metric " + name + ": " + response.statusCode());
        }
        return (long) Double.parseDouble(m.group(1));
    }

    private void recordUnexpected(String key) {
        unexpected.computeIfAbsent(key, k -> new LongAdder()).increment();
    }

    private static String errorCode(String body) {
        Matcher m = ERROR_CODE.matcher(body);
        return m.find() ? m.group(1) : "-";
    }

    private static long retryAfterMillis(HttpResponse<String> response) {
        return response.headers().firstValue("Retry-After")
                .map(v -> Math.min(Long.parseLong(v.trim()) * 1000, 1000))
                .orElse(100L);
    }

    private static String format(Duration d) {
        return "%d:%02d:%02d".formatted(d.toHours(), d.toMinutesPart(), d.toSecondsPart());
    }
}
