package com.jobbeacon.address;

import java.util.List;

public interface AddressGeocoder {

    GeoHit lookup(String query);

    record GeoHit(List<String> places, String provinceText, Double latitude, Double longitude, boolean definitive) {
        static GeoHit failed() {
            return new GeoHit(List.of(), "", null, null, false);
        }

        static GeoHit none() {
            return new GeoHit(List.of(), "", null, null, true);
        }
    }
}
