package xyz.rawmanoj.mrbank.exception.response;

import java.time.Instant;
import java.util.List;

public record ErrorResponse(
        String code,
        String message,
        int status,
        String path,
        Instant timestamp,
        List<FieldErrorResponse> fieldErrors
) {
}
