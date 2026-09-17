package xyz.rawmanoj.mrbank.dto.request;

import xyz.rawmanoj.mrbank.enumeration.UserStatus;

public record UserRequest(
        String email,
        String password,
        Boolean kycComplete,
        UserStatus userStatus
) {
}
