package com.kalin.investment_tracker.inventory;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.kalin.investment_tracker.exception.ExternalServiceException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestTemplate;
import java.time.Clock;
import static org.junit.jupiter.api.Assertions.*;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.*;

class SteamProfileXmlTest {
    private static final String ID = SteamCommunityClientTest.ID;
    private static final String PROFILE = "https://steamcommunity.com/profiles/" + ID + "/?xml=1";
    private static final String VANITY = "https://steamcommunity.com/id/example/?xml=1";
    private MockRestServiceServer server;
    private SteamCommunityClient client;

    @BeforeEach void setup() {
        RestTemplate http = new RestTemplate();
        server = MockRestServiceServer.bindTo(http).build();
        client = new SteamCommunityClient(http, new ObjectMapper(), new SteamInventoryProperties(), Clock.systemUTC());
    }
    private void expect(String url, String xml) {
        server.expect(requestTo(url)).andRespond(withSuccess(xml, MediaType.APPLICATION_XML));
    }
    @Test void resolvesVanityViaXml() {
        expect(VANITY, "<profile><steamID64>" + ID + "</steamID64></profile>");
        assertEquals(ID, client.resolveVanityId("example"));
    }
    @Test void missingVanityProfileIsAUserInputError() {
        expect(VANITY, "<response><error>Profile not found</error></response>");
        assertThrows(IllegalArgumentException.class, () -> client.resolveVanityId("example"));
    }
    @Test void extractsCdataAndEscapedTextWithoutStringSplitting() {
        expect(PROFILE, "<profile><steamID64>" + ID + "</steamID64><steamID><![CDATA[Name <one> & two]]></steamID>"
                + "<avatarFull>https://avatars.steamstatic.com/image?x=1&amp;y=2</avatarFull></profile>");
        var profile = client.fetchProfile(ID);
        assertEquals("Name <one> & two", profile.name());
        assertEquals("https://avatars.steamstatic.com/image?x=1&y=2", profile.avatar());
    }
    @Test void doesNotResolveExternalXmlEntities() {
        expect(VANITY, "<!DOCTYPE profile [<!ENTITY secret SYSTEM \"file:///must-not-be-read\">]>"
                + "<profile><steamID64>&secret;</steamID64></profile>");
        assertThrows(ExternalServiceException.class, () -> client.resolveVanityId("example"));
        server.verify();
    }
    @Test void metadataFailureUsesAFallback() {
        server.expect(requestTo(PROFILE)).andRespond(withServerError());
        assertEquals(new SteamCommunityClient.Profile("Steam User (" + ID + ")", null), client.fetchProfile(ID));
    }
    @Test void refusesAnotherProfilesMetadata() {
        expect(PROFILE, "<profile><steamID64>76561198000000002</steamID64><steamID>Other</steamID></profile>");
        assertEquals("Steam User (" + ID + ")", client.fetchProfile(ID).name());
    }
    @Test void refusesUntrustedAvatarUrl() {
        expect(PROFILE, "<profile><steamID64>" + ID + "</steamID64><steamID>Example</steamID>"
                + "<avatarFull>https://untrusted.example/image</avatarFull></profile>");
        assertNull(client.fetchProfile(ID).avatar());
    }
}
