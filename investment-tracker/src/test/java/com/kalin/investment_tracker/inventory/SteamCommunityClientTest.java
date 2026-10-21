package com.kalin.investment_tracker.inventory;

import tools.jackson.databind.ObjectMapper;
import com.kalin.investment_tracker.exception.ExternalServiceException;
import com.kalin.investment_tracker.exception.PrivateSteamInventoryException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.http.*;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestTemplate;
import java.time.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.*;

class SteamCommunityClientTest {
    static final String ID = "76561198000000001";
    static final String URL = "https://steamcommunity.com/inventory/" + ID + "/730/2?l=english&count=2000";
    static final String DESCRIPTION = """
            {"classid":"10","instanceid":"0","market_hash_name":"Revolution Case","name":"Revolution Case","tradable":1,"icon_url":"image_123"}
            """.trim();
    static String asset(String id, String amount) {
        return "{\"assetid\":\"" + id + "\",\"classid\":\"10\",\"instanceid\":\"0\",\"amount\":" + amount + "}";
    }
    static String page(String assets, String descriptions, String extra) {
        return "{\"success\":1,\"assets\":[" + assets + "],\"descriptions\":[" + descriptions + "]" + extra + "}";
    }
    private MockRestServiceServer server;
    private SteamInventoryProperties settings;
    private SteamCommunityClient client;
    private final MutableClock clock = new MutableClock();

    @BeforeEach void setup() {
        RestTemplate http = new RestTemplate();
        server = MockRestServiceServer.bindTo(http).build();
        settings = new SteamInventoryProperties();
        settings.setPageDelayMs(0);
        settings.setMinimumRequestIntervalMs(1);
        client = new SteamCommunityClient(http, new ObjectMapper(), settings, clock, new SteamWorkLimiter(settings));
    }
    private void expect(String url, String json) {
        server.expect(requestTo(url)).andRespond(withSuccess(json, MediaType.APPLICATION_JSON));
    }

    @Test void loadsNextCursorPageAndDeduplicatesAnIdenticalAsset() {
        expect(URL, page(asset("1", "\"3\""), DESCRIPTION, ",\"more_items\":1,\"last_assetid\":\"1\",\"total_inventory_count\":2"));
        expect(URL + "&start_assetid=1", page(asset("1", "\"3\"") + "," + asset("2", "2"), "", ",\"more_items\":0,\"total_inventory_count\":2"));
        var data = client.loadInventory(ID);
        assertEquals(2, data.assets().size());
        assertEquals(5, data.assets().stream().mapToInt(SteamCommunityClient.Asset::amount).sum());
        assertEquals(1, data.descriptions().size());
        server.verify();
    }

    @ParameterizedTest
    @ValueSource(strings = {"{}", "[]", "{\"success\":0}", "{\"success\":1}", "{\"success\":1,\"assets\":null}",
            "{\"success\":1,\"assets\":{}}", "{\"success\":1,\"assets\":[],\"more_items\":2}",
            "{\"success\":1,\"assets\":[],\"total_inventory_count\":1}", "{\"success\":null,\"assets\":[]}"})
    void doesNotTreatAnInvalidResponseAsAnEmptyInventory(String json) {
        expect(URL, json);
        assertThrows(ExternalServiceException.class, () -> client.loadInventory(ID));
        server.verify();
    }
    @ParameterizedTest
    @ValueSource(strings = {"{\"success\":1,\"assets\":[],\"total_inventory_count\":0}",
            "{\"success\":1,\"total_inventory_count\":0}", "{\"success\":true,\"assets\":[],\"more_items\":false}"})
    void acceptsExplicitSuccessfulEmptyInventories(String json) {
        expect(URL, json);
        assertTrue(client.loadInventory(ID).assets().isEmpty());
    }
    @ParameterizedTest
    @ValueSource(strings = {"0", "-1", "1.5", "\"1.5\"", "null", "100001", "\"99999999999999999999\""})
    void rejectsInvalidStackAmounts(String amount) {
        expect(URL, page(asset("1", amount), DESCRIPTION, ""));
        assertThrows(ExternalServiceException.class, () -> client.loadInventory(ID));
    }
    @Test void missingDescriptionDoesNotSilentlyDropAnItem() {
        expect(URL, page(asset("1", "1"), "", ""));
        assertThrows(ExternalServiceException.class, () -> client.loadInventory(ID));
    }
    @Test void conflictingDuplicateAssetDoesNotProduceAValue() {
        expect(URL, page(asset("1", "1") + "," + asset("1", "2"), DESCRIPTION, ""));
        assertThrows(ExternalServiceException.class, () -> client.loadInventory(ID));
    }
    @Test void repeatedCursorStopsWithoutAnotherRequest() {
        expect(URL, page(asset("1", "1"), DESCRIPTION, ",\"more_items\":1,\"last_assetid\":\"1\""));
        expect(URL + "&start_assetid=1", page(asset("2", "1"), DESCRIPTION, ",\"more_items\":1,\"last_assetid\":\"1\""));
        assertThrows(ExternalServiceException.class, () -> client.loadInventory(ID));
        server.verify();
    }
    @Test void missingCursorIsRejected() {
        expect(URL, page(asset("1", "1"), DESCRIPTION, ",\"more_items\":1"));
        assertThrows(ExternalServiceException.class, () -> client.loadInventory(ID));
    }
    @Test void reachingPageLimitDoesNotReturnPartialInventory() {
        settings.setMaximumPages(1);
        expect(URL, page(asset("1", "1"), DESCRIPTION, ",\"more_items\":1,\"last_assetid\":\"1\""));
        assertThrows(ExternalServiceException.class, () -> client.loadInventory(ID));
        server.verify();
    }
    @Test void totalQuantityIsBoundedAcrossDifferentAssets() {
        settings.setMaximumQuantity(3);
        expect(URL, page(asset("1", "2") + "," + asset("2", "2"), DESCRIPTION, ""));
        assertThrows(ExternalServiceException.class, () -> client.loadInventory(ID));
    }
    @Test void failureOnSecondPageDoesNotReturnPartialInventory() {
        expect(URL, page(asset("1", "1"), DESCRIPTION, ",\"more_items\":1,\"last_assetid\":\"1\""));
        server.expect(requestTo(URL + "&start_assetid=1")).andRespond(withServerError());
        assertThrows(ExternalServiceException.class, () -> client.loadInventory(ID));
    }
    @Test void changedInventoryCountIsRejected() {
        expect(URL, page(asset("1", "1"), DESCRIPTION, ",\"more_items\":1,\"last_assetid\":\"1\",\"total_inventory_count\":2"));
        expect(URL + "&start_assetid=1", page(asset("2", "1"), DESCRIPTION, ",\"total_inventory_count\":3"));
        assertThrows(ExternalServiceException.class, () -> client.loadInventory(ID));
    }
    @Test void privateInventoryRemainsADistinctError() {
        server.expect(requestTo(URL)).andRespond(withStatus(HttpStatus.FORBIDDEN));
        assertThrows(PrivateSteamInventoryException.class, () -> client.loadInventory(ID));
    }
    @Test void jsonPrivateErrorRemainsADistinctError() {
        expect(URL, "{\"success\":0,\"Error\":\"This profile is private\"}");
        assertThrows(PrivateSteamInventoryException.class, () -> client.loadInventory(ID));
    }
    @Test void honoursRetryAfterAndDoesNotLeakProviderBody() {
        server.expect(requestTo(URL)).andRespond(withStatus(HttpStatus.TOO_MANY_REQUESTS)
                .header(HttpHeaders.RETRY_AFTER, "120").body("confidential provider response"));
        expect(URL, page("", "", ",\"total_inventory_count\":0"));
        var failure = assertThrows(ExternalServiceException.class, () -> client.loadInventory(ID));
        assertFalse(failure.getMessage().contains("confidential"));
        clock.advance(61);
        assertThrows(ExternalServiceException.class, () -> client.loadInventory(ID));
        clock.advance(60);
        assertTrue(client.loadInventory(ID).assets().isEmpty());
        server.verify();
    }
    private static class MutableClock extends Clock {
        private Instant now = Instant.parse("2026-10-10T10:00:00Z");
        void advance(long seconds) { now = now.plusSeconds(seconds); }
        @Override public ZoneId getZone() { return ZoneOffset.UTC; }
        @Override public Clock withZone(ZoneId zone) { return this; }
        @Override public Instant instant() { return now; }
    }
}
