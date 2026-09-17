package xyz.rawmanoj.mrbank.exception.response;

public record FieldErrorResponse(
        String field,
        String message
) {
}
