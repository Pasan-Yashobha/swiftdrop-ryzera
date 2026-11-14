package com.swiftdrop.reporting.client;

import org.springframework.stereotype.Component;

import java.time.LocalDate;

@Component
public class StubParcelServiceClient implements ParcelServiceClient {

    // Default stub returns -1 or null if not mocked/configured in tests
    private ParcelDailyCounts stubbedCounts;

    public void setStubbedCounts(ParcelDailyCounts stubbedCounts) {
        this.stubbedCounts = stubbedCounts;
    }

    @Override
    public ParcelDailyCounts getDailyCounts(LocalDate date) {
        if (stubbedCounts != null) {
            return stubbedCounts;
        }
        return new ParcelDailyCounts(0, 0);
    }
}
