package com.umis.mailbox.connector.model;

import java.util.List;

/**
 * Result returned by a mailbox connector message fetch operation.
 */
public record FetchResult(
        List<NormalizedEmail> messages,
        String newCursor,
        boolean hasMore,
        String lastHistoryId,
        Long lastUid
) {
    public static FetchResult of(List<NormalizedEmail> messages, String newCursor, boolean hasMore) {
        return new FetchResult(messages, newCursor, hasMore, null, null);
    }

    public static FetchResult empty() {
        return new FetchResult(List.of(), null, false, null, null);
    }
}
