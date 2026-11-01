package lk.swiftdrop.merchant.order.dto;

import java.util.List;

/** Result of a CSV upload: every data row was tried, a bad row never stops the others. */
public record BulkResult(int totalRows, int created, int failed, List<BulkRowResult> results) {

    public static BulkResult of(List<BulkRowResult> results) {
        int created = (int) results.stream().filter(row -> row.status() == BulkRowStatus.CREATED).count();
        return new BulkResult(results.size(), created, results.size() - created, List.copyOf(results));
    }
}
