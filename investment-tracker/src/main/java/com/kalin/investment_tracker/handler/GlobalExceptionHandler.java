package com.kalin.investment_tracker.handler;

import com.kalin.investment_tracker.exeption.ExpiredTokenException;
import com.kalin.investment_tracker.exeption.ExternalServiceException;
import com.kalin.investment_tracker.exeption.ItemNotFoundException;
import com.kalin.investment_tracker.exeption.PrivateSteamInventoryException;
import com.kalin.investment_tracker.security.InvalidRefreshTokenException;
import com.kalin.investment_tracker.auth.AuthRateLimitException;
import org.springframework.http.HttpHeaders;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.web.HttpRequestMethodNotSupportedException;
import org.springframework.http.converter.HttpMessageNotReadableException;
import jakarta.mail.MessagingException;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.authentication.DisabledException;
import org.springframework.security.authentication.LockedException;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import java.util.HashSet;
import java.util.Set;

import static com.kalin.investment_tracker.handler.BusinessErrorCodes.*;
import static org.springframework.http.HttpStatus.*;

@RestControllerAdvice
@Slf4j
public class GlobalExceptionHandler {

    @ExceptionHandler(HttpRequestMethodNotSupportedException.class)
    public ResponseEntity<ExceptionResponse> handleUnsupportedMethod(HttpRequestMethodNotSupportedException exception) {
        var response = ResponseEntity.status(METHOD_NOT_ALLOWED);
        if (exception.getSupportedMethods() != null) {
            response.header(HttpHeaders.ALLOW, String.join(", ", exception.getSupportedMethods()));
        }
        return response.body(ExceptionResponse.builder().error("This HTTP method is not supported.").build());
    }

    @ExceptionHandler(HttpMessageNotReadableException.class)
    public ResponseEntity<ExceptionResponse> handleUnreadableBody(HttpMessageNotReadableException exception) {
        // Parsing exceptions may contain fragments of passwords or codes.
        return ResponseEntity.badRequest()
                .body(ExceptionResponse.builder().error("Invalid request body.").build());
    }

    @ExceptionHandler(AuthRateLimitException.class)
    public ResponseEntity<ExceptionResponse> handleAuthLimit(AuthRateLimitException exception) {
        return ResponseEntity.status(TOO_MANY_REQUESTS)
                .header(HttpHeaders.RETRY_AFTER, Long.toString(exception.getRetryAfterSeconds()))
                .body(ExceptionResponse.builder().error(exception.getMessage()).build());
    }

    @ExceptionHandler(DataIntegrityViolationException.class)
    public ResponseEntity<ExceptionResponse> handleConflict(DataIntegrityViolationException exception) {
        return ResponseEntity.status(CONFLICT)
                .body(ExceptionResponse.builder().error("The change conflicts with an existing record.").build());
    }

    @ExceptionHandler(LockedException.class)
    public ResponseEntity<ExceptionResponse> handleException(LockedException exp) {
        return ResponseEntity
                .status(UNAUTHORIZED)
                .body(
                        ExceptionResponse.builder()
                                .businessErrorCode(ACCOUNT_LOCKED.getCode())
                                .businessErrorDescription(ACCOUNT_LOCKED.getDescription())
                                .error(exp.getMessage())
                                .build()
                );
    }

    @ExceptionHandler(DisabledException.class)
    public ResponseEntity<ExceptionResponse> handleException(DisabledException exp) {
        return ResponseEntity
                .status(UNAUTHORIZED)
                .body(
                        ExceptionResponse.builder()
                                .businessErrorCode(ACCOUNT_DISABLED.getCode())
                                .businessErrorDescription(ACCOUNT_DISABLED.getDescription())
                                .error(exp.getMessage())
                                .build()
                );
    }

    @ExceptionHandler(BadCredentialsException.class)
    public ResponseEntity<ExceptionResponse> handleException(BadCredentialsException exp) {
        return ResponseEntity
                .status(UNAUTHORIZED)
                .body(
                        ExceptionResponse.builder()
                                .businessErrorCode(BAD_CREDENTIALS.getCode())
                                .businessErrorDescription(BAD_CREDENTIALS.getDescription())
                                .error(BAD_CREDENTIALS.getDescription())
                                .build()
                );
    }

    @ExceptionHandler(ExpiredTokenException.class)
    public ResponseEntity<ExceptionResponse> handleException(ExpiredTokenException exp) {
        return ResponseEntity
                .status(UNAUTHORIZED)
                .body(
                        ExceptionResponse.builder()
                                .businessErrorCode(EXPIRED_TOKEN.getCode())
                                .businessErrorDescription(EXPIRED_TOKEN.getDescription())
                                .error(EXPIRED_TOKEN.getDescription())
                                .build()
                );
    }

    @ExceptionHandler(ItemNotFoundException.class)
    public ResponseEntity<ExceptionResponse> handleException(ItemNotFoundException exp) {
        return ResponseEntity
                .status(NOT_FOUND)
                .body(ExceptionResponse.builder()
                        .error("The requested item was not found")
                        .build());
    }

    @ExceptionHandler(PrivateSteamInventoryException.class)
    public ResponseEntity<ExceptionResponse> handleException(PrivateSteamInventoryException exp) {
        return ResponseEntity
                .status(BAD_REQUEST)
                .body(ExceptionResponse.builder()
                        .code("PRIVACY_PRIVATE")
                        .error(exp.getMessage())
                        .build());
    }

    @ExceptionHandler(ExternalServiceException.class)
    public ResponseEntity<ExceptionResponse> handleException(ExternalServiceException exp) {
        return ResponseEntity
                .status(HttpStatus.SERVICE_UNAVAILABLE)
                .body(ExceptionResponse.builder().error(exp.getMessage()).build());
    }

    @ExceptionHandler(InvalidRefreshTokenException.class)
    public ResponseEntity<ExceptionResponse> handleException(InvalidRefreshTokenException exp) {
        return ResponseEntity
                .status(UNAUTHORIZED)
                .body(
                        ExceptionResponse.builder()
                                .businessErrorDescription("Invalid or expired refresh token")
                                .error("The session is no longer valid. Please sign in again.")
                                .build()
                );
    }

    @ExceptionHandler(MessagingException.class)
    public ResponseEntity<ExceptionResponse> handleException(MessagingException exp) {
        log.error("Email delivery failed", exp);
        return ResponseEntity
                .status(INTERNAL_SERVER_ERROR)
                .body(
                        ExceptionResponse.builder()
                                .error("The email could not be sent. Please try again later.")
                                .build()
                );
    }

    @ExceptionHandler(IllegalArgumentException.class)
    public ResponseEntity<ExceptionResponse> handleException(IllegalArgumentException exp) {
        return ResponseEntity
                .status(BAD_REQUEST)
                .body(ExceptionResponse.builder().error(exp.getMessage()).build());
    }

    @ExceptionHandler(AccessDeniedException.class)
    public ResponseEntity<ExceptionResponse> handleException(AccessDeniedException exp) {
        return ResponseEntity
                .status(FORBIDDEN)
                .body(ExceptionResponse.builder().error("Access denied").build());
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<ExceptionResponse> handleException(MethodArgumentNotValidException exp) {
        Set<String> errors = new HashSet<>();
        exp.getBindingResult().getAllErrors()
                .forEach(error -> {
                    var errorMessage = error.getDefaultMessage();
                    errors.add(errorMessage);
                });
        return ResponseEntity
                .status(BAD_REQUEST)
                .body(
                        ExceptionResponse.builder()
                                .validationErrors(errors)
                                .build()
                );
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<ExceptionResponse> handleException(Exception exp) {
        log.error("Unhandled server error", exp);
        return ResponseEntity
                .status(INTERNAL_SERVER_ERROR)
                .body(
                        ExceptionResponse.builder()
                                .businessErrorDescription("Internal error")
                                .error("An unexpected server error occurred")
                                .build()
                );
    }
}
