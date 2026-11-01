package lk.swiftdrop.merchant.credential;

import java.util.Collection;
import java.util.List;

/** Scopes of the merchant API (CONTRACT section 3). */
public final class Scopes {

    public static final String ORDERS_WRITE = "orders:write";
    public static final String ORDERS_READ = "orders:read";

    /** Bean Validation pattern accepting exactly the known scopes. */
    public static final String REGEX = "orders:(write|read)";

    /** All scopes in canonical order, which is also the order in which they are listed and stored. */
    public static final List<String> ALL = List.of(ORDERS_WRITE, ORDERS_READ);

    private static final String SEPARATOR = " ";

    private Scopes() {
    }

    /** The known scopes contained in {@code scopes}, in canonical order and without duplicates. */
    public static List<String> canonical(Collection<String> scopes) {
        return ALL.stream().filter(scopes::contains).toList();
    }

    /** Space separated form used in the {@code scope} token claim and the {@code oauth_clients.scopes} column. */
    public static String join(Collection<String> scopes) {
        return String.join(SEPARATOR, scopes);
    }

    public static List<String> split(String scopes) {
        return scopes.isBlank() ? List.of() : List.of(scopes.trim().split("\\s+"));
    }
}
