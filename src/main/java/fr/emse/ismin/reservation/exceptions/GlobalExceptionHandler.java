package fr.emse.ismin.reservation.exceptions;

import fr.emse.ismin.reservation.dtos.ApiErrorResponse;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.context.MessageSourceResolvable;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.validation.FieldError;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.MissingServletRequestParameterException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.method.annotation.HandlerMethodValidationException;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;
import tools.jackson.core.JacksonException;

import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * Turns every exception raised while handling a request into an
 * {@link ApiErrorResponse}, the error format defined by the OpenAPI contract.
 * <p>
 * Business errors are {@link ApiException}s and already carry their code.
 * Errors raised by Spring before reaching the controller (invalid body,
 * missing or malformed parameter) are all reported as {@code VALIDATION_ERROR}.
 */
@RestControllerAdvice
public class GlobalExceptionHandler {

    /**
     * Handles the business exceptions thrown by the services.
     *
     * @param ex      the business exception
     * @param request the current request
     * @return the error response with the status bound to the exception code
     */
    @ExceptionHandler(ApiException.class)
    public ResponseEntity<ApiErrorResponse> handleApiException(ApiException ex, HttpServletRequest request) {
        return buildResponse(ex.getCode(), ex.getMessage(), ex.getDetails(), ex.getFieldErrors(), request);
    }

    /**
     * Handles a request body refused by Bean Validation ({@code @Valid}).
     *
     * @param ex      the validation exception
     * @param request the current request
     * @return a {@code VALIDATION_ERROR} listing the invalid fields
     */
    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<ApiErrorResponse> handleInvalidBody(MethodArgumentNotValidException ex,
                                                              HttpServletRequest request) {
        Map<String, String> fieldErrors = new LinkedHashMap<>();
        for (FieldError fieldError : ex.getBindingResult().getFieldErrors()) {
            // Keep the first message when a field breaks several constraints
            fieldErrors.putIfAbsent(fieldError.getField(), fieldError.getDefaultMessage());
        }
        return validationError(fieldErrors, request);
    }

    /**
     * Handles query or path parameters refused by a constraint, for example
     * {@code capacity=0} on the availability search.
     *
     * @param ex      the validation exception
     * @param request the current request
     * @return a {@code VALIDATION_ERROR} listing the invalid parameters
     */
    @ExceptionHandler(HandlerMethodValidationException.class)
    public ResponseEntity<ApiErrorResponse> handleInvalidParameter(HandlerMethodValidationException ex,
                                                                   HttpServletRequest request) {
        Map<String, String> fieldErrors = new LinkedHashMap<>();
        ex.getParameterValidationResults().forEach(result -> fieldErrors.putIfAbsent(
                result.getMethodParameter().getParameterName(),
                result.getResolvableErrors().stream()
                        .map(MessageSourceResolvable::getDefaultMessage)
                        .collect(Collectors.joining(", "))));
        return validationError(fieldErrors, request);
    }

    /**
     * Handles a required query parameter that is missing.
     *
     * @param ex      the exception naming the missing parameter
     * @param request the current request
     * @return a {@code VALIDATION_ERROR} on the missing parameter
     */
    @ExceptionHandler(MissingServletRequestParameterException.class)
    public ResponseEntity<ApiErrorResponse> handleMissingParameter(MissingServletRequestParameterException ex,
                                                                   HttpServletRequest request) {
        return validationError(Map.of(ex.getParameterName(), "est obligatoire"), request);
    }

    /**
     * Handles a parameter that cannot be converted to the expected type, for
     * example a malformed date or a non numeric id.
     *
     * @param ex      the conversion exception
     * @param request the current request
     * @return a {@code VALIDATION_ERROR} on the malformed parameter
     */
    @ExceptionHandler(MethodArgumentTypeMismatchException.class)
    public ResponseEntity<ApiErrorResponse> handleTypeMismatch(MethodArgumentTypeMismatchException ex,
                                                               HttpServletRequest request) {
        return validationError(Map.of(ex.getName(), "a un format invalide"), request);
    }

    /**
     * Handles a request body that cannot be read: malformed JSON, wrong type,
     * unknown enum value or badly formatted date.
     *
     * @param ex      the parsing exception
     * @param request the current request
     * @return a {@code VALIDATION_ERROR}, naming the faulty field when it is known
     */
    @ExceptionHandler(HttpMessageNotReadableException.class)
    public ResponseEntity<ApiErrorResponse> handleUnreadableBody(HttpMessageNotReadableException ex,
                                                                 HttpServletRequest request) {
        Map<String, String> fieldErrors = new LinkedHashMap<>();
        if (ex.getCause() instanceof JacksonException jacksonException && !jacksonException.getPath().isEmpty()) {
            String field = jacksonException.getPath().stream()
                    .map(reference -> reference.getPropertyName() != null
                            ? reference.getPropertyName()
                            : String.valueOf(reference.getIndex()))
                    .collect(Collectors.joining("."));
            fieldErrors.put(field, "a un format invalide");
        }
        return validationError(fieldErrors, request);
    }

    private ResponseEntity<ApiErrorResponse> validationError(Map<String, String> fieldErrors,
                                                             HttpServletRequest request) {
        return buildResponse(ErrorCode.VALIDATION_ERROR, InvalidFieldException.DEFAULT_MESSAGE,
                Map.of(), fieldErrors, request);
    }

    private ResponseEntity<ApiErrorResponse> buildResponse(ErrorCode code, String message,
                                                           Map<String, Object> details,
                                                           Map<String, String> fieldErrors,
                                                           HttpServletRequest request) {
        ApiErrorResponse body = new ApiErrorResponse(code.name(), message, Instant.now(),
                request.getRequestURI(), details, fieldErrors);
        return ResponseEntity.status(code.getStatus()).body(body);
    }
}
