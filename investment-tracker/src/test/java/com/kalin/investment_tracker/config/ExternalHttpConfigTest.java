package com.kalin.investment_tracker.config;

import jakarta.validation.Validation;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClientException;
import org.springframework.test.util.ReflectionTestUtils;
import java.io.*;
import java.time.Duration;
import static org.junit.jupiter.api.Assertions.*;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

class ExternalHttpConfigTest {
    @Test void configuredConnectionAndReadTimeoutsReachTheHttpClient() {
        var http = new ExternalHttpConfig(new ExternalHttpProperties()).restTemplate();
        Object factory = ReflectionTestUtils.getField(http.getRequestFactory(), "requestFactory");
        assertNotNull(factory);
        assertEquals(5000, ReflectionTestUtils.getField(factory, "connectTimeout"));
        assertEquals(20000, ReflectionTestUtils.getField(factory, "readTimeout"));
    }
    @Test void exactLimitAndEmptyStreamsRemainReadable() throws Exception {
        try (var exact = new BoundedInputStream(new ByteArrayInputStream(new byte[10]), 10)) {
            assertEquals(10, exact.readAllBytes().length); assertEquals(-1, exact.read());
        }
        try (var empty = new BoundedInputStream(new ByteArrayInputStream(new byte[0]), 1)) { assertEquals(-1, empty.read()); }
    }
    @Test void bulkAndSingleByteReadsCannotBypassLimit() throws Exception {
        try (var bulk = new BoundedInputStream(new ByteArrayInputStream(new byte[11]), 10)) {
            assertThrows(IOException.class, bulk::readAllBytes); assertThrows(IOException.class, bulk::read);
        }
        try (var single = new BoundedInputStream(new ByteArrayInputStream(new byte[2]), 1)) {
            assertEquals(0, single.read()); assertThrows(IOException.class, single::read);
        }
    }
    @Test void skipAlsoCountsBytesAndMarkResetCannotReuseAllowance() throws Exception {
        try (var input = new BoundedInputStream(new ByteArrayInputStream(new byte[11]), 10)) {
            assertEquals(10, input.skip(10)); assertThrows(IOException.class, input::read);
            assertFalse(input.markSupported()); assertThrows(IOException.class, input::reset);
        }
    }
    @Test void restTemplateRejectsOversizedBodiesWithOrWithoutContentLength() {
        for (boolean declared : new boolean[]{true, false}) {
            ExternalHttpProperties settings = new ExternalHttpProperties(); settings.setMaximumResponseBytes(10);
            var http = new ExternalHttpConfig(settings).restTemplate();
            var server = MockRestServiceServer.bindTo(http).build();
            var reply = withSuccess(new byte[11], MediaType.APPLICATION_OCTET_STREAM);
            if (declared) reply.header(HttpHeaders.CONTENT_LENGTH, "11");
            server.expect(requestTo("https://example.test/feed")).andRespond(reply);
            assertThrows(RestClientException.class, () -> http.getForObject("https://example.test/feed", byte[].class));
            server.verify();
        }
    }
    @Test void configurationRejectsInfiniteTimeoutsAndUnboundedResponseSizes() {
        try (var factory = Validation.buildDefaultValidatorFactory()) {
            var validator = factory.getValidator(); ExternalHttpProperties settings = new ExternalHttpProperties();
            assertTrue(validator.validate(settings).isEmpty());
            settings.setReadTimeout(Duration.ZERO); assertFalse(validator.validate(settings).isEmpty());
            settings.setReadTimeout(Duration.ofSeconds(20)); settings.setMaximumResponseBytes(Integer.MAX_VALUE);
            assertFalse(validator.validate(settings).isEmpty());
        }
    }
}
