package tr.com.envapp.http;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.Collections;
import java.util.List;
import java.util.Map;

/**
 * Merkezi HTTP istek servisi.
 * <p>GET, POST ve header yönetimini tek noktadan sağlar.</p>
 *
 * <pre>
 * // GET örneği
 * HttpResponse<String> res = HttpService.getInstance().get("<a href="https://example.com/api">...</a>", Map.of());
 *
 * // POST örneği
 * HttpResponse<String> res = HttpService.getInstance()
 *     .post("<a href="https://example.com/api/login">...</a>", "{\"user\":\"x\"}", Map.of("Cookie", "abc=123"));
 * </pre>
 */
public class HttpService {

    private static final Duration DEFAULT_REQUEST_TIMEOUT = Duration.ofSeconds(30);

    private final HttpClient client;

    public HttpService() {
        this.client = HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(10))
                .followRedirects(HttpClient.Redirect.NORMAL)
                .build();
    }
    // -------------------------------------------------------------------------
    // GET
    // -------------------------------------------------------------------------

    /**
     * Ekstra header olmadan GET isteği atar.
     */
    public HttpResponse<String> get(String url) throws Exception {
        return get(url, Collections.emptyMap());
    }

    /**
     * Verilen header'larla GET isteği atar.
     */
    public HttpResponse<String> get(String url, Map<String, String> headers) throws Exception {
        HttpRequest.Builder builder = HttpRequest.newBuilder()
                .uri(URI.create(url))
                .GET()
                .timeout(DEFAULT_REQUEST_TIMEOUT);

        applyHeaders(builder, headers);

        return client.send(builder.build(), HttpResponse.BodyHandlers.ofString());
    }

    // -------------------------------------------------------------------------
    // POST
    // -------------------------------------------------------------------------

    /**
     * JSON body ile POST isteği atar. Content-Type otomatik olarak
     * {@code application/json} setlenir.
     */
    public HttpResponse<String> postJson(String url, String jsonBody) throws Exception {
        return postJson(url, jsonBody, Collections.emptyMap());
    }

    /**
     * JSON body ve ekstra header'larla POST isteği atar.
     */
    public HttpResponse<String> postJson(String url, String jsonBody, Map<String, String> headers) throws Exception {
        HttpRequest.Builder builder = HttpRequest.newBuilder()
                .uri(URI.create(url))
                .header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(jsonBody))
                .timeout(DEFAULT_REQUEST_TIMEOUT);

        applyHeaders(builder, headers);

        return client.send(builder.build(), HttpResponse.BodyHandlers.ofString());
    }

    // -------------------------------------------------------------------------
    // Yardımcı
    // -------------------------------------------------------------------------

    /**
     * Response'un {@code set-cookie} header'larını döner.
     */
    public List<String> getCookies(HttpResponse<String> response) {
        return response.headers().allValues("set-cookie");
    }

    /**
     * İlk {@code set-cookie} header'ından belirtilen cookie değerini parse eder.
     *
     * <pre>
     * String sessionId = HttpService.getInstance()
     *     .parseCookieValue(response, "hazelcast.sessionId");
     * </pre>
     */
    public String parseCookieValue(HttpResponse<String> response, String cookieName) {
        List<String> setCookieHeaders = getCookies(response);
        if (setCookieHeaders.isEmpty()) {
            return "";
        }
        String prefix = cookieName + "=";
        for (String header : setCookieHeaders) {
            int startIdx = header.indexOf(prefix);
            if (startIdx != -1) {
                startIdx += prefix.length();
                int endIdx = header.indexOf(";", startIdx);
                return endIdx != -1
                        ? header.substring(startIdx, endIdx)
                        : header.substring(startIdx);
            }
        }
        return "";
    }

    /**
     * Tüm {@code set-cookie} header değerlerini {@code "; "} ile birleştirip döner.
     */
    public String joinCookies(HttpResponse<String> response) {
        return String.join("; ", getCookies(response));
    }

    // -------------------------------------------------------------------------
    // Private
    // -------------------------------------------------------------------------

    private void applyHeaders(HttpRequest.Builder builder, Map<String, String> headers) {
        if (headers != null) {
            headers.forEach(builder::header);
        }
    }
}

