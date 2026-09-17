package xyz.rawmanoj.mrbank.exception;

import jakarta.servlet.http.HttpServletRequest;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.MissingServletRequestParameterException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import xyz.rawmanoj.mrbank.exception.response.ErrorResponse;
import xyz.rawmanoj.mrbank.exception.response.FieldErrorResponse;

import java.time.Instant;
import java.util.List;

@RestControllerAdvice
public class GlobalExceptionHandler {

    @ExceptionHandler(MrBankException.class)
    public ResponseEntity<ErrorResponse> handleMrBankException(
            MrBankException exception,
            HttpServletRequest request
    ) {
        ErrorCode errorCode = exception.getErrorCode();
        HttpStatus status = errorCode.getHttpStatus();

        return ResponseEntity
                .status(status)
                .body(buildErrorResponse(errorCode.getCode(), exception.getMessage(), status, request, List.of()));
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<ErrorResponse> handleMethodArgumentNotValidException(
            MethodArgumentNotValidException exception,
            HttpServletRequest request
    ) {
        List<FieldErrorResponse> fieldErrors = exception.getBindingResult()
                .getFieldErrors()
                .stream()
                .map(fieldError -> new FieldErrorResponse(fieldError.getField(), fieldError.getDefaultMessage()))
                .toList();

        ErrorCode errorCode = ErrorCode.BAD_REQUEST;
        HttpStatus status = errorCode.getHttpStatus();

        return ResponseEntity
                .status(status)
                .body(buildErrorResponse(errorCode.getCode(), "Validation failed", status, request, fieldErrors));
    }

    @ExceptionHandler({
            MissingServletRequestParameterException.class,
            HttpMessageNotReadableException.class
    })
    public ResponseEntity<ErrorResponse> handleBadRequestException(
            Exception exception,
            HttpServletRequest request
    ) {
        ErrorCode errorCode = ErrorCode.BAD_REQUEST;
        HttpStatus status = errorCode.getHttpStatus();

        return ResponseEntity
                .status(status)
                .body(buildErrorResponse(errorCode.getCode(), exception.getMessage(), status, request, List.of()));
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<ErrorResponse> handleException(
            Exception exception,
            HttpServletRequest request
    ) {
        ErrorCode errorCode = ErrorCode.INTERNAL_SERVER_ERROR;
        HttpStatus status = errorCode.getHttpStatus();

        return ResponseEntity
                .status(status)
                .body(buildErrorResponse(errorCode.getCode(), errorCode.getMessage(), status, request, List.of()));
    }

    private ErrorResponse buildErrorResponse(
            String code,
            String message,
            HttpStatus status,
            HttpServletRequest request,
            List<FieldErrorResponse> fieldErrors
    ) {
        return new ErrorResponse(
                code,
                message,
                status.value(),
                request.getRequestURI(),
                Instant.now(),
                fieldErrors
        );
    }
}
