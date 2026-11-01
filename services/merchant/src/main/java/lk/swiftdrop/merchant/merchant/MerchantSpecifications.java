package lk.swiftdrop.merchant.merchant;

import java.util.Locale;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.util.StringUtils;

/** Optional filters of the admin merchant list; a filter that is not given adds no condition. */
final class MerchantSpecifications {

    private static final char LIKE_ESCAPE = '\\';

    private MerchantSpecifications() {
    }

    static Specification<Merchant> filter(MerchantStatus status, String search) {
        return Specification.allOf(hasStatus(status), matchesSearch(search));
    }

    private static Specification<Merchant> hasStatus(MerchantStatus status) {
        return (root, query, builder) -> status == null ? null : builder.equal(root.get("status"), status);
    }

    /** Case-insensitive "contains" on business name or contact email. */
    private static Specification<Merchant> matchesSearch(String search) {
        return (root, query, builder) -> {
            if (!StringUtils.hasText(search)) {
                return null;
            }
            String pattern = "%" + escapeLike(search.trim().toLowerCase(Locale.ROOT)) + "%";
            return builder.or(
                    builder.like(builder.lower(root.get("businessName")), pattern, LIKE_ESCAPE),
                    builder.like(builder.lower(root.get("contactEmail")), pattern, LIKE_ESCAPE));
        };
    }

    private static String escapeLike(String value) {
        return value.replace("\\", "\\\\").replace("%", "\\%").replace("_", "\\_");
    }
}
