package xyz.rawmanoj.mrbank.dto.request;

import xyz.rawmanoj.mrbank.enumeration.UserStatus;

import java.util.List;

public record AccountRequest(
        Integer accountNumber,
        Double balance,
        Long userId,
        List<Long> kycDocumentIds,
        UserStatus userStatus
) {
}
