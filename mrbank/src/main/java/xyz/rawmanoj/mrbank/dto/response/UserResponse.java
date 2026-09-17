package xyz.rawmanoj.mrbank.dto.response;

import xyz.rawmanoj.mrbank.enumeration.UserStatus;

import java.time.Instant;

public record UserResponse(
        Long id,
        String email,
        Boolean kycComplete,
        UserStatus userStatus,
        Instant createdAt,
        Instant updatedAt
) {
}
