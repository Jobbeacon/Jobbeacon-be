package com.jobbeacon.address;

import java.util.ArrayList;
import java.util.List;

public final class SplitWardPicker {

    private static final double EARTH_KM = 6371.0088;

    private SplitWardPicker() {
    }

    public static String code(double latitude, double longitude, List<NewLocationConverter.SplitSite> sites) {
        return code(latitude, longitude, sites, true);
    }

    public static String code(double latitude, double longitude, List<NewLocationConverter.SplitSite> sites, boolean nearest) {
        if (sites == null || sites.isEmpty()) {
            return null;
        }
        List<NewLocationConverter.SplitSite> inside = new ArrayList<>();
        for (NewLocationConverter.SplitSite site : sites) {
            if (site.km2() > 0 && contains(latitude, longitude, site)) {
                inside.add(site);
            }
        }
        if (inside.size() == 1) {
            return inside.get(0).code();
        }
        if (!nearest) {
            return null;
        }
        NewLocationConverter.SplitSite chosen = nearest(latitude, longitude, sites);
        if (chosen == null || meters(latitude, longitude, chosen.lat(), chosen.lon()) > reachMeters(chosen)) {
            return null;
        }
        return chosen.code();
    }

    private static double reachMeters(NewLocationConverter.SplitSite site) {
        double sideKm = site.km2() > 0 ? Math.sqrt(site.km2()) : 1.0;
        return Math.max(1.5, 1.5 * sideKm) * 1000;
    }

    private static boolean contains(double latitude, double longitude, NewLocationConverter.SplitSite site) {
        double halfSideKm = Math.sqrt(site.km2()) / 2.0;
        double north = destination(site.lat(), site.lon(), 0, halfSideKm)[0];
        double south = destination(site.lat(), site.lon(), 180, halfSideKm)[0];
        double east = destination(site.lat(), site.lon(), 90, halfSideKm)[1];
        double west = destination(site.lat(), site.lon(), 270, halfSideKm)[1];
        return longitude > west && longitude < east && latitude > south && latitude < north;
    }

    private static NewLocationConverter.SplitSite nearest(
            double latitude,
            double longitude,
            List<NewLocationConverter.SplitSite> sites
    ) {
        NewLocationConverter.SplitSite chosen = null;
        double best = Double.MAX_VALUE;
        for (NewLocationConverter.SplitSite site : sites) {
            double meters = meters(latitude, longitude, site.lat(), site.lon());
            if (meters < best) {
                best = meters;
                chosen = site;
            }
        }
        return chosen;
    }

    private static double[] destination(double latitude, double longitude, double bearingDegrees, double distanceKm) {
        double angular = distanceKm / EARTH_KM;
        double bearing = Math.toRadians(bearingDegrees);
        double lat = Math.toRadians(latitude);
        double lon = Math.toRadians(longitude);
        double nextLat = Math.asin(Math.sin(lat) * Math.cos(angular) + Math.cos(lat) * Math.sin(angular) * Math.cos(bearing));
        double nextLon = lon + Math.atan2(
                Math.sin(bearing) * Math.sin(angular) * Math.cos(lat),
                Math.cos(angular) - Math.sin(lat) * Math.sin(nextLat)
        );
        return new double[] {Math.toDegrees(nextLat), Math.toDegrees(nextLon)};
    }

    private static double meters(double lat1, double lon1, double lat2, double lon2) {
        double phi1 = Math.toRadians(lat1);
        double phi2 = Math.toRadians(lat2);
        double dPhi = Math.toRadians(lat2 - lat1);
        double dLambda = Math.toRadians(lon2 - lon1);
        double a = Math.sin(dPhi / 2) * Math.sin(dPhi / 2)
                + Math.cos(phi1) * Math.cos(phi2) * Math.sin(dLambda / 2) * Math.sin(dLambda / 2);
        return EARTH_KM * 1000 * 2 * Math.atan2(Math.sqrt(a), Math.sqrt(1 - a));
    }
}
