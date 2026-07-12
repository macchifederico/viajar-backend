package ar.com.viajar.service.pricing;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class GeoUtilsTest {

    @Test
    void sameCoordinates_returnsZero() {
        assertEquals(0.0, GeoUtils.haversineKm(-34.6037, -58.3816, -34.6037, -58.3816), 0.0001);
    }

    @Test
    void oneDegreeOfLatitudeAtEquator_isApproximately111Km() {
        double distance = GeoUtils.haversineKm(0, 0, 1, 0);
        assertEquals(111.19, distance, 0.5);
    }

    @Test
    void oneDegreeOfLongitudeAtEquator_isApproximately111Km() {
        double distance = GeoUtils.haversineKm(0, 0, 0, 1);
        assertEquals(111.19, distance, 0.5);
    }

    @Test
    void isSymmetric() {
        double ab = GeoUtils.haversineKm(-34.6037, -58.3816, -34.9214, -57.9544);
        double ba = GeoUtils.haversineKm(-34.9214, -57.9544, -34.6037, -58.3816);
        assertEquals(ab, ba, 0.0001);
    }
}
