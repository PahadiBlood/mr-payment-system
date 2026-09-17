package xyz.rawmanoj.mrbank.dto.request;

import xyz.rawmanoj.mrbank.enumeration.DocumentType;
import xyz.rawmanoj.mrbank.enumeration.KycStatus;
import xyz.rawmanoj.mrbank.enumeration.UserStatus;

public record KycDocumentRequest(
        DocumentType documentType,
        String documentNumber,
        KycStatus kycStatus,
        Long accountId,
        UserStatus userStatus
) {
}
