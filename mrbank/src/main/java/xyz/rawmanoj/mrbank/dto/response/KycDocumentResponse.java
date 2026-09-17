package xyz.rawmanoj.mrbank.dto.response;

import xyz.rawmanoj.mrbank.enumeration.DocumentType;
import xyz.rawmanoj.mrbank.enumeration.KycStatus;
import xyz.rawmanoj.mrbank.enumeration.UserStatus;

import java.time.Instant;

public record KycDocumentResponse(
        Long id,
        DocumentType documentType,
        String documentNumber,
        KycStatus kycStatus,
        Long accountId,
        UserStatus userStatus,
        Instant createdAt,
        Instant updatedAt
) {
}
