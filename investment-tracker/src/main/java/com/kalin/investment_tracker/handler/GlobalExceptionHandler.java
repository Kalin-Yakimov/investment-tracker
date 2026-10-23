package com.kalin.investment_tracker.handler;

import com.kalin.investment_tracker.exception.ExpiredTokenException;
import com.kalin.investment_tracker.exception.ExternalServiceException;
import com.kalin.investment_tracker.exception.ItemNotFoundException;
import com.kalin.investment_tracker.exception.PrivateSteamInventoryException;
import com.kalin.investment_tracker.security.InvalidRefreshTokenException;
import com.kalin.investment_tracker.auth.AuthRateLimitException;
import com.kalin.investment_tracker.auth.AccountActionException;
import com.kalin.investment_tracker.tracking.DuplicateTrackedItemException;
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
import org.springframework.web.bind.ServletRequestBindingException;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;
import org.springframework.web.method.annotation.HandlerMethodValidationException;
import org.springframework.web.HttpMediaTypeNotSupportedException;
import org.springframework.web.servlet.resource.NoResourceFoundException;

import java.util.HashSet;
import java.util.Set;

import static com.kalin.investment_tracker.handler.BusinessErrorCodes.*;
import static org.springframework.http.HttpStatus.*;

@RestControllerAdvice
@Slf4j
public class GlobalExceptionHandler {

    @ExceptionHandler(com.kalin.investment_tracker.validation.ResourceLimitException.class)
    public ResponseEntity<ExceptionResponse> handleResourceLimit(com.kalin.investment_tracker.validation.ResourceLimitException exception) {
        var response = ResponseEntity.status(exception.getStatus());
        if (exception.getRetryAfterSeconds() > 0) response.header(HttpHeaders.RETRY_AFTER, Long.toString(exception.getRetryAfterSeconds()));
        return response.body(ExceptionResponse.builder().error(exception.getMessage()).build());
    }

    @ExceptionHandler(DuplicateTrackedItemException.class)
    public ResponseEntity<ExceptionResponse> handleDuplicateTrackedItem(DuplicateTrackedItemException exception) {
        return errorResponse(CONFLICT, exception.getMessage());
    }

    @ExceptionHandler({ServletRequestBindingException.class, MethodArgumentTypeMismatchException.class})
    public ResponseEntity<ExceptionResponse> handleInvalidParameters(Exception exception) {
        // Do not echo rejected query, path or header values.
        return errorResponse(BAD_REQUEST, "Invalid or missing request parameter.");
    }

    @ExceptionHandler(HandlerMethodValidationException.class)
    public ResponseEntity<ExceptionResponse> handleMethodValidation(HandlerMethodValidationException exception) {
        if (exception.isForReturnValue()) {
            return errorResponse(INTERNAL_SERVER_ERROR, "An unexpected server error occurred");
        }
        Set<String> errors = new HashSet<>();
        exception.getParameterValidationResults().forEach(result -> result.getResolvableErrors().forEach(error ->
                errors.add(error.getDefaultMessage() == null ? "Invalid request value" : error.getDefaultMessage())));
        return ResponseEntity.badRequest().body(ExceptionResponse.builder().validationErrors(errors).build());
    }

    @ExceptionHandler(HttpMediaTypeNotSupportedException.class)
    public ResponseEntity<ExceptionResponse> handleUnsupportedContentType(HttpMediaTypeNotSupportedException exception) {
        return errorResponse(UNSUPPORTED_MEDIA_TYPE, "Unsupported request content type.");
    }

    @ExceptionHandler(NoResourceFoundException.class)
    public ResponseEntity<ExceptionResponse> handleMissingResource(NoResourceFoundException exception) {
        return errorResponse(NOT_FOUND, "The requested resource was not found.");
    }

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
        return errorResponse(BAD_REQUEST, "Invalid request body.");
    }

    @ExceptionHandler(AuthRateLimitException.class)
    public ResponseEntity<ExceptionResponse> handleAuthLimit(AuthRateLimitException exception) {
        return ResponseEntity.status(TOO_MANY_REQUESTS)
                .header(HttpHeaders.RETRY_AFTER, Long.toString(exception.getRetryAfterSeconds()))
                .body(ExceptionResponse.builder().error(exception.getMessage()).build());
    }

    @ExceptionHandler(DataIntegrityViolationException.class)
    public ResponseEntity<ExceptionResponse> handleConflict(DataIntegrityViolationException exception) {
        return errorResponse(CONFLICT, "The change conflicts with an existing record.");
    }

    @ExceptionHandler(AccountActionException.class)
    public ResponseEntity<ExceptionResponse> handleAccountAction(AccountActionException exception) {
        BusinessErrorCodes code = exception.getErrorCode();
        return ResponseEntity.status(code.getHttpStatus())
                .body(ExceptionResponse.builder()
                        .businessErrorCode(code.getCode())
                        .businessErrorDescription(code.getDescription())
                        .error(code.getDescription()).build());
    }

    @ExceptionHandler(LockedException.class)
    public ResponseEntity<ExceptionResponse> handleException(LockedException exp) {
        return authenticationError(ACCOUNT_LOCKED, exp.getMessage());
    }

    @ExceptionHandler(DisabledException.class)
    public ResponseEntity<ExceptionResponse> handleException(DisabledException exp) {
        return authenticationError(ACCOUNT_DISABLED, exp.getMessage());
    }

    @ExceptionHandler(BadCredentialsException.class)
    public ResponseEntity<ExceptionResponse> handleException(BadCredentialsException exp) {
        return authenticationError(BAD_CREDENTIALS, BAD_CREDENTIALS.getDescription());
    }

    @ExceptionHandler(ExpiredTokenException.class)
    public ResponseEntity<ExceptionResponse> handleException(ExpiredTokenException exp) {
        return authenticationError(EXPIRED_TOKEN, EXPIRED_TOKEN.getDescription());
    }

    @ExceptionHandler(ItemNotFoundException.class)
    public ResponseEntity<ExceptionResponse> handleException(ItemNotFoundException exp) {
        return errorResponse(NOT_FOUND, "The requested item was not found");
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
        return errorResponse(SERVICE_UNAVAILABLE, exp.getMessage());
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
        log.error("Email delivery failed ({})", exp.getClass().getSimpleName());
        return errorResponse(INTERNAL_SERVER_ERROR, "The email could not be sent. Please try again later.");
    }

    @ExceptionHandler(IllegalArgumentException.class)
    public ResponseEntity<ExceptionResponse> handleException(IllegalArgumentException exp) {
        return errorResponse(BAD_REQUEST, exp.getMessage());
    }

    @ExceptionHandler(AccessDeniedException.class)
    public ResponseEntity<ExceptionResponse> handleException(AccessDeniedException exp) {
        return errorResponse(FORBIDDEN, "Access denied");
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
        log.error("Unhandled server error ({})", exp.getClass().getSimpleName());
        return ResponseEntity
                .status(INTERNAL_SERVER_ERROR)
                .body(
                        ExceptionResponse.builder()
                                .businessErrorDescription("Internal error")
                                .error("An unexpected server error occurred")
                                .build()
                );
    }

    private ResponseEntity<ExceptionResponse> errorResponse(HttpStatus status, String message) {
        return ResponseEntity.status(status)
                .body(ExceptionResponse.builder().error(message).build());
    }

    // Authentication handlers retain HTTP 401 even when the business-code enum
    // specifies another status; this is the existing public API contract.
    private ResponseEntity<ExceptionResponse> authenticationError(BusinessErrorCodes code, String message) {
        return ResponseEntity.status(UNAUTHORIZED)
                .body(ExceptionResponse.builder()
                        .businessErrorCode(code.getCode())
                        .businessErrorDescription(code.getDescription())
                        .error(message)
                        .build());
    }
}
