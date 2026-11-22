package lk.swiftdrop.tracking.domain;

import java.math.BigDecimal;
import java.math.RoundingMode;

/** A latitude/longitude pair in decimal degrees. */
public record GeoPoint(double lat, double lng) {

    /** About 1.1 km: enough to place a city on a map, not enough to find a house. */
    public static final int CITY_PRECISION = 2;
    /** About 110 m: used for the courier's live position. */
    public static final int COURIER_PRECISION = 3;

    public GeoPoint {
        if (Double.isNaN(lat) || Double.isNaN(lng) || lat < -90 || lat > 90 || lng < -180 || lng > 180) {
            throw new IllegalArgumentException("coordinates out of range");
        }
    }

    /** Returns a point rounded to the given number of decimals, or null when the input is not a valid coordinate. */
    public static GeoPoint roundedOrNull(Double lat, Double lng, int decimals) {
        if (lat == null || lng == null || Double.isNaN(lat) || Double.isNaN(lng)
                || lat < -90 || lat > 90 || lng < -180 || lng > 180) {
            return null;
        }
        return new GeoPoint(round(lat, decimals), round(lng, decimals));
    }

    public GeoPoint rounded(int decimals) {
        return new GeoPoint(round(lat, decimals), round(lng, decimals));
    }

    private static double round(double value, int decimals) {
        return BigDecimal.valueOf(value).setScale(decimals, RoundingMode.HALF_UP).doubleValue();
    }
}
