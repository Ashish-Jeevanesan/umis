package com.umis.mailbox.connector.model;

import java.time.Instant;

/**
 * Result returned upon completing a mailbox authorization flow.
 */
public record ConnectionResult(
        boolean success,
        String emailAddress,
        String displayName,
        String accessToken,
        String refreshToken,
        Instant tokenExpiresAt,
        String scope,
        String errorMessage
) {
    public static ConnectionResult ok(
            String emailAddress,
            String displayName,
            String accessToken,
            String refreshToken,
            Instant tokenExpiresAt,
            String scope
    ) {
        return new ConnectionResult(
                true, emailAddress, displayName, accessToken, refreshToken, tokenExpiresAt, scope, null);
    }

    public static ConnectionResult failure(String errorMessage) {
        return new ConnectionResult(false, null, null, null, null, null, null, errorMessage);
    }
}
