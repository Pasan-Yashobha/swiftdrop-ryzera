package lk.swiftdrop.tracking.dev;

import java.util.List;
import java.util.Locale;
import java.util.Optional;

/** Approximate city centres used by the dev simulator. */
final class SriLankaCities {

    record City(String name, double lat, double lng) {
    }

    static final City COLOMBO = new City("Colombo", 6.9271, 79.8612);
    static final City KANDY = new City("Kandy", 7.2906, 80.6337);

    private static final List<City> ALL = List.of(
            COLOMBO,
            KANDY,
            new City("Galle", 6.0535, 80.2210),
            new City("Jaffna", 9.6615, 80.0255),
            new City("Peradeniya", 7.2690, 80.5970),
            new City("Nuwara Eliya", 6.9497, 80.7891),
            new City("Negombo", 7.2083, 79.8358),
            new City("Kurunegala", 7.4863, 80.3647),
            new City("Matara", 5.9549, 80.5550),
            new City("Anuradhapura", 8.3114, 80.4037),
            new City("Trincomalee", 8.5874, 81.2152),
            new City("Batticaloa", 7.7310, 81.6747),
            new City("Ratnapura", 6.6828, 80.3992),
            new City("Badulla", 6.9934, 81.0550));

    private SriLankaCities() {
    }

    static Optional<City> find(String name) {
        if (name == null || name.isBlank()) {
            return Optional.empty();
        }
        String wanted = name.trim().toLowerCase(Locale.ROOT);
        return ALL.stream().filter(city -> city.name().toLowerCase(Locale.ROOT).equals(wanted)).findFirst();
    }

    static List<String> names() {
        return ALL.stream().map(City::name).toList();
    }
}
