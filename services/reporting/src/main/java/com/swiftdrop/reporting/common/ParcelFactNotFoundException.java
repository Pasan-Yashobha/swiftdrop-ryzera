package com.swiftdrop.reporting.common;

import java.util.UUID;

/**
 * Thrown when a status event arrives before parcel.created has inserted parcel_fact.
 * This triggers a bounded retry in the consumer (brief §3).
 */
public class ParcelFactNotFoundException extends RuntimeException {

    private final UUID parcelId;

    public ParcelFactNotFoundException(UUID parcelId) {
        super("ParcelFact not found for parcelId: " + parcelId);
        this.parcelId = parcelId;
    }

    public UUID getParcelId() {
        return parcelId;
    }
}
