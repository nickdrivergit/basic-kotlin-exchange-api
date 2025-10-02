import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.io.IOException;
import java.io.PrintWriter;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.time.Instant;

/**
 * Utility to exercise the API end-to-end with signed requests.
 *
 * Reads env vars:
 * - API_BASE (default: http://localhost:8080)
 * - API_KEY (default: test-key)
 * - API_SECRET (default: test-secret)
 */
public class SignAndPost {
    private static final HttpClient client = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(5))
            .build();

    private static String env(String k, String d) { String v = System.getenv(k); return (v == null || v.isBlank()) ? d : v; }

    private static PrintWriter W;
    private static void log(String s) {
        System.out.println(s);
        if (W != null) {
            W.println(s);
            W.flush();
        }
    }

    private static String hmacHex(String secret, String message) throws Exception {
        Mac mac = Mac.getInstance("HmacSHA512");
        mac.init(new SecretKeySpec(secret.getBytes(StandardCharsets.UTF_8), "HmacSHA512"));
        byte[] bytes = mac.doFinal(message.getBytes(StandardCharsets.UTF_8));
        StringBuilder sb = new StringBuilder(bytes.length * 2);
        for (byte b : bytes) sb.append(String.format("%02x", b));
        return sb.toString();
    }

    private static HttpResponse<String> get(String base, String path) throws Exception {
        HttpRequest req = HttpRequest.newBuilder(URI.create(base + path)).GET().build();
        return client.send(req, HttpResponse.BodyHandlers.ofString());
    }

    private static HttpResponse<String> postSigned(String base, String key, String secret, String path, String verb, String body) throws Exception {
        String ts = String.valueOf(System.currentTimeMillis());
        String sig = hmacHex(secret, ts + verb + path + body);
        log("\n-- REQUEST " + verb + " " + path);
        log("Headers: content-type=application/json, X-VALR-API-KEY=" + key + ", X-VALR-TIMESTAMP=" + ts + ", X-VALR-SIGNATURE=" + sig);
        log("Body: " + body);
        HttpRequest req = HttpRequest.newBuilder(URI.create(base + path))
                .header("content-type", "application/json")
                .header("X-VALR-API-KEY", key)
                .header("X-VALR-TIMESTAMP", ts)
                .header("X-VALR-SIGNATURE", sig)
                .method(verb, HttpRequest.BodyPublishers.ofString(body))
                .build();
        return client.send(req, HttpResponse.BodyHandlers.ofString());
    }

    private static HttpResponse<String> postRaw(String base, String path, String contentType, String body) throws Exception {
        log("\n-- REQUEST POST " + path);
        log("Headers: content-type=" + contentType);
        log("Body: " + body);
        HttpRequest req = HttpRequest.newBuilder(URI.create(base + path))
                .header("content-type", contentType)
                .POST(HttpRequest.BodyPublishers.ofString(body))
                .build();
        return client.send(req, HttpResponse.BodyHandlers.ofString());
    }

    private static void print(String title, HttpResponse<String> r) {
        log("\n== " + title + " ==");
        log("Status: " + r.statusCode());
        log(r.body());
    }

    public static void main(String[] args) throws Exception {
        // Prepare timestamped log file in current working directory
        Path logDir = Path.of("scripts", "logs");
        Files.createDirectories(logDir);
        String filename = "SignAndPost-" + System.currentTimeMillis() + ".log";
        try {
            Path p = logDir.resolve(filename);
            W = new PrintWriter(Files.newBufferedWriter(p, StandardCharsets.UTF_8));
            log("Log file: " + p.toAbsolutePath());
            log("Started: " + Instant.now());
        } catch (IOException ioe) {
            System.err.println("Failed to open log file: " + filename + ": " + ioe.getMessage());
        }

        final String base = env("API_BASE", "http://localhost:8080");
        final String key = env("API_KEY", "test-key");
        final String secret = env("API_SECRET", "test-secret");

        // Health
        var h = get(base, "/healthz");
        print("Health", h);

        // 1) Basic GTC buy rests on the book
        String symBasic = "E2EBASIC";
        var order1 = postSigned(base, key, secret, "/api/orders/" + symBasic, "POST",
                "{\"side\":\"BUY\",\"price\":\"100.00\",\"quantity\":\"0.01\"}");
        print("Basic BUY GTC", order1);
        print("Snapshot BASIC", get(base, "/api/orderbook/" + symBasic));

        // 2) Matching trade: sell hits the resting buy
        var order2 = postSigned(base, key, secret, "/api/orders/" + symBasic, "POST",
                "{\"side\":\"SELL\",\"price\":\"100.00\",\"quantity\":\"0.01\"}");
        print("SELL to match", order2);
        print("Recent trades (limit=1)", get(base, "/api/trades/" + symBasic + "?limit=1"));

        // 3) Depth query: multiple buys, depth=2
        String symDepth = "E2EDEPTH";
        postSigned(base, key, secret, "/api/orders/" + symDepth, "POST", "{\"side\":\"BUY\",\"price\":\"100\",\"quantity\":\"0.01\"}");
        postSigned(base, key, secret, "/api/orders/" + symDepth, "POST", "{\"side\":\"BUY\",\"price\":\"200\",\"quantity\":\"0.01\"}");
        postSigned(base, key, secret, "/api/orders/" + symDepth, "POST", "{\"side\":\"BUY\",\"price\":\"300\",\"quantity\":\"0.01\"}");
        print("Depth=2 snapshot", get(base, "/api/orderbook/" + symDepth + "?depth=2"));

        // 4) IOC: remainder does not rest
        String symIoc = "E2EIOC";
        postSigned(base, key, secret, "/api/orders/" + symIoc, "POST", "{\"side\":\"SELL\",\"price\":\"100\",\"quantity\":\"0.3\"}");
        var ioc = postSigned(base, key, secret, "/api/orders/" + symIoc, "POST",
                "{\"side\":\"BUY\",\"price\":\"100\",\"quantity\":\"0.5\",\"timeInForce\":\"IOC\"}");
        print("IOC buy against 0.3 ask", ioc);
        print("IOC snapshot (should have no bids)", get(base, "/api/orderbook/" + symIoc));

        // 5) FOK: cannot fully fill -> no trades, no rest
        String symFok = "E2EFOK";
        postSigned(base, key, secret, "/api/orders/" + symFok, "POST", "{\"side\":\"SELL\",\"price\":\"100\",\"quantity\":\"0.4\"}");
        var fok = postSigned(base, key, secret, "/api/orders/" + symFok, "POST",
                "{\"side\":\"BUY\",\"price\":\"100\",\"quantity\":\"0.5\",\"timeInForce\":\"FOK\"}");
        print("FOK buy (should cancel)", fok);
        print("FOK trades (limit=5)", get(base, "/api/trades/" + symFok + "?limit=5"));

        // 6) Negative depth/limit -> expect 400s
        String symNeg = "E2ENEG";
        postSigned(base, key, secret, "/api/orders/" + symNeg, "POST", "{\"side\":\"BUY\",\"price\":\"10\",\"quantity\":\"1\"}");
        print("Depth=-1 snapshot (expect 400)", get(base, "/api/orderbook/" + symNeg + "?depth=-1"));
        print("Trades limit=-5 (expect 400)", get(base, "/api/trades/" + symNeg + "?limit=-5"));

        // 7) Invalid signature -> 403
        String ts = String.valueOf(System.currentTimeMillis());
        String badSig = hmacHex("wrong-secret", ts + "POST" + "/api/orders/" + symNeg + "{\"side\":\"BUY\",\"price\":\"1\",\"quantity\":\"1\"}");
        HttpRequest badReq = HttpRequest.newBuilder(URI.create(base + "/api/orders/" + symNeg))
                .header("content-type", "application/json")
                .header("X-VALR-API-KEY", key)
                .header("X-VALR-TIMESTAMP", ts)
                .header("X-VALR-SIGNATURE", badSig)
                .POST(HttpRequest.BodyPublishers.ofString("{\"side\":\"BUY\",\"price\":\"1\",\"quantity\":\"1\"}"))
                .build();
        var badResp = client.send(badReq, HttpResponse.BodyHandlers.ofString());
        print("Invalid signature (expect 403)", badResp);

        // 8) Wrong content-type -> 403
        print("Wrong content-type (expect 403)", postRaw(base, "/api/orders/" + symNeg, "text/plain", "side=BUY&price=1&quantity=1"));

        log("\nAll requests executed. Review statuses and bodies above.");
        if (W != null) {
            log("Finished: " + Instant.now());
            W.close();
        }
    }
}
