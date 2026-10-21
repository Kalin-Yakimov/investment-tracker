package com.kalin.investment_tracker.pricing;

import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;
import com.kalin.investment_tracker.config.BoundedInputStream;
import com.kalin.investment_tracker.exception.ExternalServiceException;
import lombok.RequiredArgsConstructor;
import org.springframework.http.*;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestTemplate;

import java.io.ByteArrayInputStream;
import java.io.InputStream;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.*;
import java.util.zip.GZIPInputStream;

/** Provider transport/format only: no cache publication, currency lookup or database writes. */
@Component
@RequiredArgsConstructor
public class CsgotraderFeedClient {
    private static final String FEED_URL = "https://prices.csgotrader.app/latest/steam.json";
    private final RestTemplate restTemplate;
    private final ObjectMapper objectMapper;
    private final PriceFeedProperties properties;

    public ResponseEntity<byte[]> download(PriceFeedSnapshot previous, boolean force) {
        HttpHeaders headers = requestHeaders();
        // A new publication can reuse the ETag: prefer its Last-Modified date.
        if (!force && previous != null && previous.modifiedMillis() > 0) {
            headers.setIfModifiedSince(previous.modifiedMillis());
        } else if (!force && previous != null && previous.etag() != null) {
            headers.setIfNoneMatch(previous.etag());
        }
        return restTemplate.exchange(FEED_URL, HttpMethod.GET, new HttpEntity<>(headers), byte[].class);
    }

    public ResponseEntity<byte[]> revalidate(long receivedDate) {
        HttpHeaders headers = requestHeaders();
        headers.setIfModifiedSince(receivedDate);
        return restTemplate.exchange(FEED_URL, HttpMethod.GET, new HttpEntity<>(headers), byte[].class);
    }

    private HttpHeaders requestHeaders() {
        HttpHeaders headers = new HttpHeaders();
        headers.set("User-Agent", "InvestmentTracker/1.0");
        headers.setAccept(List.of(MediaType.APPLICATION_JSON));
        return headers;
    }

    public Publication readPublication(ResponseEntity<byte[]> response) throws Exception {
        byte[] body = response.getBody();
        if (!response.getStatusCode().is2xxSuccessful() || body == null || body.length == 0) {
            throw new ExternalServiceException("The price source returned no usable data");
        }
        byte[] decoded = decodeBody(body);
        Map<String, BigDecimal> prices = parsePrices(decoded);
        long modified = response.getHeaders().getLastModified();
        String etag = response.getHeaders().getETag();
        return new Publication(Map.copyOf(prices), buildSourceVersion(etag, modified, decoded), etag, modified);
    }

    public record Publication(Map<String, BigDecimal> prices, String version, String etag, long modified) {}

    private byte[] decodeBody(byte[] body) throws Exception {
        boolean gzip = body.length >= 2 && (body[0] & 0xff) == 0x1f && (body[1] & 0xff) == 0x8b;
        InputStream input = new ByteArrayInputStream(body);
        if (gzip) input = new GZIPInputStream(input);
        try (InputStream bounded = new BoundedInputStream(input, properties.getMaximumDecodedBytes())) {
            return bounded.readAllBytes();
        }
    }

    private Map<String, BigDecimal> parsePrices(byte[] json) throws Exception {
        JsonNode root = objectMapper.readTree(json);
        if (root == null || !root.isObject()) throw new ExternalServiceException("Invalid price feed format");
        Map<String, BigDecimal> prices = new HashMap<>();
        for (var entry : root.properties()) {
            JsonNode value = entry.getValue().path("last_24h");
            if (entry.getKey().isBlank() || entry.getKey().length() > 500 || !value.isNumber()) continue;
            BigDecimal usd = value.decimalValue().setScale(4, RoundingMode.HALF_UP);
            if (usd.signum() > 0 && usd.precision() <= 18) prices.put(entry.getKey(), usd);
        }
        if (prices.isEmpty()) throw new ExternalServiceException("No usable 24-hour prices were returned");
        return prices;
    }

    private String buildSourceVersion(String etag, long modified, byte[] decoded) throws Exception {
        String identity = etag == null || etag.isBlank() ? hash(decoded) : etag;
        // Hash untrusted ETags so the stored identifier always fits the existing column.
        String identityHash = hash(identity.getBytes(StandardCharsets.UTF_8));
        if (modified > 0) return "published-" + modified + "-" + identityHash;
        if (etag != null && !etag.isBlank()) {
            String legacyVersion = etag.replace("W/", "").replace("\"", "");
            if (!legacyVersion.isBlank() && legacyVersion.length() <= 128) return legacyVersion;
            return "content-" + identityHash;
        }
        return hash(decoded);
    }

    private String hash(byte[] body) throws Exception {
        return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(body));
    }
}
