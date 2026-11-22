package lk.swiftdrop.tracking.domain;

/**
 * Where a parcel starts or ends, at city level only. The street address never reaches this service.
 *
 * @param city  city or zone name, for example "Kandy"
 * @param point city-level coordinates (rounded to {@link GeoPoint#CITY_PRECISION} decimals), may be null
 */
public record Place(String city, GeoPoint point) {
}
