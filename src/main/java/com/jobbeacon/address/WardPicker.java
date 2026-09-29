package com.jobbeacon.address;

import java.util.List;

public final class WardPicker {

    private WardPicker() {
    }

    public static String code(List<String> places, List<NewLocationConverter.AllowedWard> allowed) {
        String found = null;
        for (String place : places) {
            for (NewLocationConverter.AllowedWard ward : allowed) {
                if (!matches(place, ward)) {
                    continue;
                }
                if (found != null && !found.equals(ward.code())) {
                    return null;
                }
                found = ward.code();
            }
        }
        return found;
    }

    private static boolean matches(String place, NewLocationConverter.AllowedWard ward) {
        String folded = NewLocationConverter.fold(place);
        if (folded.equals(ward.nameFold()) || folded.equals(ward.shortFold())) {
            return true;
        }
        for (String prefix : new String[]{"phuong ", "xa ", "thi tran "}) {
            if (folded.startsWith(prefix) && folded.substring(prefix.length()).equals(ward.shortFold())) {
                return true;
            }
        }
        return false;
    }
}
