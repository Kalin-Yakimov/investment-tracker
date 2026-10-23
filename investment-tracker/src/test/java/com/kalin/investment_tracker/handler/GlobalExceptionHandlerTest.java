package com.kalin.investment_tracker.handler;

import tools.jackson.databind.ObjectMapper;
import com.kalin.investment_tracker.exception.ExpiredTokenException;
import com.kalin.investment_tracker.exception.ExternalServiceException;
import com.kalin.investment_tracker.exception.ItemNotFoundException;
import com.kalin.investment_tracker.auth.AccountActionException;
import jakarta.mail.MessagingException;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpMethod;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.mock.http.MockHttpInputMessage;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.authentication.DisabledException;
import org.springframework.security.authentication.LockedException;
import org.springframework.web.HttpMediaTypeNotSupportedException;
import org.springframework.web.bind.ServletRequestBindingException;
import org.springframework.web.method.annotation.HandlerMethodValidationException;
import org.springframework.web.servlet.resource.NoResourceFoundException;

import java.util.Map;
import java.util.function.Function;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class GlobalExceptionHandlerTest {
    private final GlobalExceptionHandler handler = new GlobalExceptionHandler();
    private final ObjectMapper json = new ObjectMapper();

    @ParameterizedTest
    @org.junit.jupiter.params.provider.EnumSource(value = BusinessErrorCodes.class, names = {
            "INCORRECT_CURRENT_PASSWORD", "NEW_PASSWORD_DOES_NOT_MATCH", "USERNAME_ALREADY_TAKEN"})
    void expectedAccountErrorsHaveTheirDeclared4xxStatusAndFixedPublicMessage(BusinessErrorCodes code) {
        var response = handler.handleAccountAction(new AccountActionException(code));
        assertEquals(code.getHttpStatus(), response.getStatusCode());
        assertEquals(json.valueToTree(Map.of("businessErrorCode", code.getCode(),
                        "businessErrorDescription", code.getDescription(), "error", code.getDescription())),
                json.valueToTree(response.getBody()));
    }

    @ParameterizedTest
    @MethodSource("plainErrors")
    void sharedErrorBuilderRetainsStatusAndExactJson(
            Function<GlobalExceptionHandler, ResponseEntity<ExceptionResponse>> call,
            int status, String message) {
        var response = call.apply(handler);
        assertEquals(status, response.getStatusCode().value());
        assertNotNull(response.getBody());
        assertEquals(json.valueToTree(Map.of("error", message)), json.valueToTree(response.getBody()));
    }

    private static Stream<Arguments> plainErrors() {
        return Stream.of(
                plain(h -> h.handleInvalidParameters(new ServletRequestBindingException("private value")),
                        400, "Invalid or missing request parameter."),
                plain(h -> h.handleUnsupportedContentType(new HttpMediaTypeNotSupportedException("private value")),
                        415, "Unsupported request content type."),
                plain(h -> h.handleMissingResource(new NoResourceFoundException(HttpMethod.GET, "private-path", "private-path")),
                        404, "The requested resource was not found."),
                plain(h -> h.handleUnreadableBody(new HttpMessageNotReadableException("private input",
                                new MockHttpInputMessage(new byte[0]))), 400, "Invalid request body."),
                plain(h -> h.handleConflict(new DataIntegrityViolationException("private SQL")),
                        409, "The change conflicts with an existing record."),
                plain(h -> h.handleDuplicateTrackedItem(new com.kalin.investment_tracker.tracking.DuplicateTrackedItemException()),
                        409, "You are already tracking this item"),
                plain(h -> h.handleException(new ItemNotFoundException(42L)),
                        404, "The requested item was not found"),
                plain(h -> h.handleException(new ExternalServiceException("Provider temporarily unavailable")),
                        503, "Provider temporarily unavailable"),
                plain(h -> h.handleException(new MessagingException("private SMTP detail")),
                        500, "The email could not be sent. Please try again later."),
                plain(h -> h.handleException(new IllegalArgumentException("Invalid filter")), 400, "Invalid filter"),
                plain(h -> h.handleException(new AccessDeniedException("private access detail")), 403, "Access denied"),
                plain(h -> {
                    var failure = mock(HandlerMethodValidationException.class);
                    when(failure.isForReturnValue()).thenReturn(true);
                    return h.handleMethodValidation(failure);
                }, 500, "An unexpected server error occurred")
        );
    }

    private static Arguments plain(Function<GlobalExceptionHandler, ResponseEntity<ExceptionResponse>> call,
                                   int status, String message) {
        return Arguments.of(call, status, message);
    }

    @ParameterizedTest
    @MethodSource("authenticationErrors")
    void sharedAuthenticationBuilderRetains401AndExistingBusinessFields(
            Function<GlobalExceptionHandler, ResponseEntity<ExceptionResponse>> call,
            BusinessErrorCodes code, String message) {
        var response = call.apply(handler);
        assertEquals(401, response.getStatusCode().value());
        assertNotNull(response.getBody());
        assertEquals(json.valueToTree(Map.of(
                        "businessErrorCode", code.getCode(),
                        "businessErrorDescription", code.getDescription(),
                        "error", message)),
                json.valueToTree(response.getBody()));
    }

    private static Stream<Arguments> authenticationErrors() {
        return Stream.of(
                authentication(h -> h.handleException(new LockedException("Locked account")),
                        BusinessErrorCodes.ACCOUNT_LOCKED, "Locked account"),
                authentication(h -> h.handleException(new DisabledException("Disabled account")),
                        BusinessErrorCodes.ACCOUNT_DISABLED, "Disabled account"),
                authentication(h -> h.handleException(new BadCredentialsException("private credential detail")),
                        BusinessErrorCodes.BAD_CREDENTIALS, BusinessErrorCodes.BAD_CREDENTIALS.getDescription()),
                authentication(h -> h.handleException(new ExpiredTokenException("private token detail")),
                        BusinessErrorCodes.EXPIRED_TOKEN, BusinessErrorCodes.EXPIRED_TOKEN.getDescription())
        );
    }

    private static Arguments authentication(Function<GlobalExceptionHandler, ResponseEntity<ExceptionResponse>> call,
                                            BusinessErrorCodes code, String message) {
        return Arguments.of(call, code, message);
    }
}
