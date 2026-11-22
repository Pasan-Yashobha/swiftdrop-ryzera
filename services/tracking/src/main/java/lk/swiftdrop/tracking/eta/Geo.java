package lk.swiftdrop.tracking.eta;

import lk.swiftdrop.tracking.domain.GeoPoint;

/** Great-circle distance. */
public final class Geo {

    private static final double EARTH_RADIUS_KM = 6371.0088;

    private Geo() {
    }

    /** Haversine distance in kilometres. */
    public static double distanceKm(GeoPoint from, GeoPoint to) {
        double lat1 = Math.toRadians(from.lat());
        double lat2 = Math.toRadians(to.lat());
        double dLat = lat2 - lat1;
        double dLng = Math.toRadians(to.lng() - from.lng());
        double a = Math.sin(dLat / 2) * Math.sin(dLat / 2)
                + Math.cos(lat1) * Math.cos(lat2) * Math.sin(dLng / 2) * Math.sin(dLng / 2);
        return 2 * EARTH_RADIUS_KM * Math.asin(Math.min(1, Math.sqrt(a)));
    }
}
