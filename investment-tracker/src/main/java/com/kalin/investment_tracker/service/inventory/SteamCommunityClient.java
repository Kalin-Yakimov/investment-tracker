package com.kalin.investment_tracker.service.inventory;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.kalin.investment_tracker.config.CacheConfig;
import com.kalin.investment_tracker.exeption.ExternalServiceException;
import com.kalin.investment_tracker.exeption.PrivateSteamInventoryException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.http.*;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClientResponseException;
import org.springframework.web.client.RestTemplate;
import org.w3c.dom.Document;
import org.w3c.dom.NodeList;
import org.xml.sax.InputSource;
import org.xml.sax.SAXException;
import org.xml.sax.SAXParseException;
import org.xml.sax.helpers.DefaultHandler;

import javax.xml.XMLConstants;
import javax.xml.parsers.DocumentBuilderFactory;
import java.io.StringReader;
import java.net.URI;
import java.time.Clock;
import java.time.Instant;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.concurrent.atomic.AtomicReference;

@Service
@RequiredArgsConstructor
@Slf4j
public class SteamCommunityClient {
    private static final String STEAM = "https://steamcommunity.com";
    private final RestTemplate http;
    private final ObjectMapper mapper;
    private final SteamInventoryProperties settings;
    private final Clock clock;
    private final AtomicReference<Instant> retryAfter = new AtomicReference<>();

    public record DescriptionKey(String classId, String instanceId) {}
    public record Asset(String id, DescriptionKey description, int amount) {}
    public record Description(String marketName, String name, String icon, boolean tradable) {}
    public record Inventory(List<Asset> assets, Map<DescriptionKey, Description> descriptions) {}
    public record Profile(String name, String avatar) {}

    @Cacheable(value = CacheConfig.STEAM_PROFILE_RESOLUTION_CACHE, key = "#vanity", sync = true)
    public String resolveVanityId(String vanity) {
        Document profile = readXml(request(STEAM + "/id/" + vanity + "/?xml=1", false));
        String id = xmlText(profile, "steamID64");
        if (!SteamProfileReference.isValidId(id)) {
            throw new IllegalArgumentException("Steam could not find this custom profile name");
        }
        return id;
    }

    public Profile fetchProfile(String steamId) {
        String fallback = "Steam User (" + steamId + ")";
        try {
            Document profile = readXml(request(STEAM + "/profiles/" + steamId + "/?xml=1", false));
            if (!steamId.equals(xmlText(profile, "steamID64"))) return new Profile(fallback, null);
            String name = xmlText(profile, "steamID");
            String avatar = xmlText(profile, "avatarFull");
            return new Profile(name == null ? fallback : name, safeAvatar(avatar));
        } catch (ExternalServiceException failure) {
            // Optional metadata failure must not discard an already complete inventory.
            return new Profile(fallback, null);
        }
    }

    public Inventory loadInventory(String steamId) {
        Map<String, Asset> assets = new LinkedHashMap<>();
        Map<DescriptionKey, Description> descriptions = new HashMap<>();
        Set<String> cursors = new HashSet<>();
        String cursor = null;
        Integer expectedCount = null;
        long quantity = 0;
        for (int page = 0; page < settings.getMaximumPages(); page++) {
            if (page > 0) pauseBetweenPages();
            String url = STEAM + "/inventory/" + steamId + "/730/2?l=english&count=2000"
                    + (cursor == null ? "" : "&start_assetid=" + cursor);
            JsonNode root = readJson(request(url, true));
            if (!flag(root.path("success"), false)) {
                String error = root.path("Error").asText(root.path("error").asText("")).toLowerCase(Locale.ROOT);
                if (error.contains("private") || error.contains("friends only")) throw privateInventory();
                throw malformed();
            }
            if (root.has("total_inventory_count")) {
                int count = boundedInt(root.get("total_inventory_count"), 0, settings.getMaximumAssets());
                if (expectedCount != null && expectedCount != count) throw incomplete();
                expectedCount = count;
            }
            JsonNode pageAssets = root.path("assets");
            if (pageAssets.isMissingNode() && Integer.valueOf(0).equals(expectedCount)) pageAssets = mapper.createArrayNode();
            if (!pageAssets.isArray() || pageAssets.size() > settings.getMaximumAssets()) throw malformed();
            JsonNode pageDescriptions = root.path("descriptions");
            if (!pageDescriptions.isMissingNode() && !pageDescriptions.isArray()) throw malformed();
            if (pageDescriptions.isArray()) {
                if (pageDescriptions.size() > settings.getMaximumAssets()) throw incomplete();
                for (JsonNode node : pageDescriptions) {
                    checkApp(node);
                    DescriptionKey key = descriptionKey(node);
                    Description description = new Description(text(node, "market_hash_name", 500), text(node, "name", 500),
                            text(node, "icon_url", 2048), flag(node.path("tradable"), false));
                    if (description.marketName() == null && description.name() == null) throw malformed();
                    Description previous = descriptions.putIfAbsent(key, description);
                    if (previous != null && !previous.equals(description)) throw incomplete();
                    if (descriptions.size() > settings.getMaximumAssets()) throw incomplete();
                }
            }
            for (JsonNode node : pageAssets) {
                checkApp(node);
                if (node.has("contextid") && !"2".equals(node.get("contextid").asText())) throw malformed();
                Asset asset = new Asset(numericId(node.path("assetid")), descriptionKey(node),
                        boundedInt(node.path("amount"), 1, settings.getMaximumQuantity()));
                Asset previous = assets.putIfAbsent(asset.id(), asset);
                if (previous != null && !previous.equals(asset)) throw incomplete();
                if (previous == null) quantity += asset.amount();
                if (assets.size() > settings.getMaximumAssets() || quantity > settings.getMaximumQuantity()) throw incomplete();
            }
            boolean more = flag(root.path("more_items"), false);
            if (!more) {
                // Steam counts asset entries here; the user-facing quantity also includes stacked amounts.
                if (expectedCount != null && expectedCount != assets.size()) throw incomplete();
                if (assets.values().stream().anyMatch(asset -> !descriptions.containsKey(asset.description()))) throw incomplete();
                return new Inventory(List.copyOf(assets.values()), Map.copyOf(descriptions));
            }
            if (pageAssets.isEmpty()) throw incomplete();
            cursor = numericId(root.path("last_assetid"));
            if (!cursors.add(cursor)) throw incomplete();
        }
        throw incomplete();
    }

    private String request(String url, boolean inventory) {
        Instant blockedUntil = retryAfter.get();
        if (blockedUntil != null && clock.instant().isBefore(blockedUntil)) {
            throw new ExternalServiceException("Steam is temporarily rate limited; please try again later");
        }
        HttpHeaders headers = new HttpHeaders();
        headers.set("User-Agent", "InvestmentTracker/1.0");
        headers.setAccept(List.of(inventory ? MediaType.APPLICATION_JSON : MediaType.APPLICATION_XML));
        try {
            ResponseEntity<String> response = http.exchange(url, HttpMethod.GET, new HttpEntity<>(headers), String.class);
            if (!response.getStatusCode().is2xxSuccessful() || response.getBody() == null || response.getBody().isBlank()) throw malformed();
            return response.getBody();
        } catch (RestClientResponseException failure) {
            int status = failure.getStatusCode().value();
            if (inventory && (status == 401 || status == 403)) throw privateInventory();
            if (status == 429 || status == 503) {
                Instant minimum = clock.instant().plusMillis(settings.getFailureRetryIntervalMs());
                Instant requested = parseRetryAfter(failure.getResponseHeaders(), minimum);
                retryAfter.accumulateAndGet(requested, (old, next) -> old == null || next.isAfter(old) ? next : old);
            }
            log.warn("Steam Community request failed (HTTP {})", status);
            throw new ExternalServiceException("Steam is temporarily unavailable; please try again later");
        } catch (ExternalServiceException failure) { throw failure; }
        catch (Exception failure) {
            log.warn("Steam Community request failed ({})", failure.getClass().getSimpleName());
            throw new ExternalServiceException("Could not reach Steam; please try again later");
        }
    }

    private Instant parseRetryAfter(HttpHeaders headers, Instant minimum) {
        String value = headers == null ? null : headers.getFirst(HttpHeaders.RETRY_AFTER);
        if (value != null) {
            try {
                Instant requested = value.matches("[0-9]+") ? clock.instant().plusSeconds(Long.parseLong(value))
                        : ZonedDateTime.parse(value, DateTimeFormatter.RFC_1123_DATE_TIME).toInstant();
                if (requested.isAfter(minimum)) return requested;
            } catch (RuntimeException ignored) { /* Keep the configured cooldown for an invalid header. */ }
        }
        return minimum;
    }

    private void pauseBetweenPages() {
        try { Thread.sleep(settings.getPageDelayMs()); }
        catch (InterruptedException failure) {
            Thread.currentThread().interrupt();
            throw new ExternalServiceException("Inventory loading was interrupted; please try again");
        }
    }

    private JsonNode readJson(String body) {
        try {
            JsonNode root = mapper.readTree(body);
            if (root == null || !root.isObject()) throw malformed();
            return root;
        } catch (ExternalServiceException failure) { throw failure; }
        catch (Exception failure) { throw malformed(); }
    }

    private Document readXml(String xml) {
        try {
            if (xml.length() > 131072) throw malformed();
            DocumentBuilderFactory factory = DocumentBuilderFactory.newDefaultInstance();
            factory.setFeature(XMLConstants.FEATURE_SECURE_PROCESSING, true);
            factory.setFeature("http://apache.org/xml/features/disallow-doctype-decl", true);
            factory.setAttribute(XMLConstants.ACCESS_EXTERNAL_DTD, "");
            factory.setAttribute(XMLConstants.ACCESS_EXTERNAL_SCHEMA, "");
            factory.setXIncludeAware(false);
            factory.setExpandEntityReferences(false);
            var builder = factory.newDocumentBuilder();
            builder.setErrorHandler(new DefaultHandler() {
                @Override public void error(SAXParseException error) throws SAXException { throw error; }
                @Override public void fatalError(SAXParseException error) throws SAXException { throw error; }
            });
            return builder.parse(new InputSource(new StringReader(xml)));
        } catch (Exception failure) { throw malformed(); }
    }

    private String xmlText(Document document, String tag) {
        NodeList nodes = document.getElementsByTagName(tag);
        if (nodes.getLength() != 1) return null;
        String text = nodes.item(0).getTextContent().trim();
        return text.isEmpty() || text.length() > 2048 ? null : text;
    }

    private String safeAvatar(String value) {
        if (value == null) return null;
        try {
            URI uri = URI.create(value);
            String host = uri.getHost() == null ? "" : uri.getHost().toLowerCase(Locale.ROOT);
            return "https".equalsIgnoreCase(uri.getScheme()) && uri.getUserInfo() == null && uri.getPort() == -1
                    && (host.endsWith(".steamstatic.com") || host.endsWith(".akamaihd.net")) ? value : null;
        } catch (IllegalArgumentException ignored) { return null; }
    }

    private DescriptionKey descriptionKey(JsonNode node) {
        String instance = node.has("instanceid") ? numericId(node.get("instanceid")) : "0";
        return new DescriptionKey(numericId(node.path("classid")), instance);
    }

    private void checkApp(JsonNode node) {
        if (!node.isObject() || (node.has("appid") && !"730".equals(node.get("appid").asText()))) throw malformed();
    }

    private String numericId(JsonNode node) {
        if (!node.isTextual() && !node.isIntegralNumber()) throw malformed();
        String value = node.asText();
        if (!value.matches("[0-9]{1,20}")) throw malformed();
        return value;
    }

    private int boundedInt(JsonNode node, int minimum, int maximum) {
        String value = numericId(node);
        try {
            long number = Long.parseLong(value);
            if (number < minimum || number > maximum) throw incomplete();
            return (int) number;
        } catch (NumberFormatException failure) { throw malformed(); }
    }

    private boolean flag(JsonNode node, boolean missingDefault) {
        if (node.isMissingNode()) return missingDefault;
        if (node.isBoolean()) return node.booleanValue();
        if ((node.isTextual() || node.isIntegralNumber()) && "1".equals(node.asText())) return true;
        if ((node.isTextual() || node.isIntegralNumber()) && "0".equals(node.asText())) return false;
        throw malformed();
    }

    private String text(JsonNode node, String field, int limit) {
        JsonNode value = node.path(field);
        if (value.isMissingNode() || value.isNull()) return null;
        if (!value.isTextual() || value.textValue().length() > limit) throw malformed();
        return value.textValue().isBlank() ? null : value.textValue();
    }

    private ExternalServiceException malformed() {
        return new ExternalServiceException("Steam returned an invalid inventory or profile response; please try again later");
    }

    private ExternalServiceException incomplete() {
        return new ExternalServiceException("The complete Steam inventory could not be loaded; no partial valuation was saved");
    }

    private PrivateSteamInventoryException privateInventory() {
        return new PrivateSteamInventoryException("Steam did not allow access to this inventory. Please set Inventory Privacy to Public in Steam Profile -> Edit Profile -> Privacy Settings.");
    }
}
