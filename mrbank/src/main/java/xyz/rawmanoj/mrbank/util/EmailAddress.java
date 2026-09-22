package xyz.rawmanoj.mrbank.util;

import java.util.Locale;

public final class EmailAddress {

    private EmailAddress() {
    }

    public static String normalize(String email) {
        if (email == null) {
            return null;
        }
        return email.trim().toLowerCase(Locale.ROOT);
    }
}
