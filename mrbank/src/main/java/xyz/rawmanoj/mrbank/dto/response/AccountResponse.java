package xyz.rawmanoj.mrbank.dto.response;

import xyz.rawmanoj.mrbank.enumeration.UserStatus;

import java.time.Instant;
import java.util.List;

public record AccountResponse(
        Long id,
        Integer accountNumber,
        Double balance,
        Long userId,
        List<Long> kycDocumentIds,
        UserStatus userStatus,
        Instant createdAt,
        Instant updatedAt
) {
}
