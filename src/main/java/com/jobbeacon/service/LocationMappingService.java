package com.jobbeacon.service;

import com.jobbeacon.address.AddressGeocoder;
import com.jobbeacon.address.GeocodeCache;
import com.jobbeacon.address.NewLocationConverter;
import com.jobbeacon.address.SplitWardPicker;
import com.jobbeacon.configuration.GeocodeProperties;
import com.jobbeacon.entity.Province;
import com.jobbeacon.entity.Ward;
import com.jobbeacon.repository.ProvinceRepository;
import com.jobbeacon.repository.WardRepository;
import org.springframework.stereotype.Service;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.HexFormat;

@Service
public class LocationMappingService {

    private static final String MISS = "";

    private final NewLocationConverter converter;
    private final ProvinceRepository provinceRepository;
    private final WardRepository wardRepository;
    private final AddressGeocoder geocoder;
    private final GeocodeCache geocodeCache;
    private final GeocodeProperties geocodeProperties;

    public LocationMappingService(
            NewLocationConverter converter,
            ProvinceRepository provinceRepository,
            WardRepository wardRepository,
            AddressGeocoder geocoder,
            GeocodeCache geocodeCache,
            GeocodeProperties geocodeProperties
    ) {
        this.converter = converter;
        this.provinceRepository = provinceRepository;
        this.wardRepository = wardRepository;
        this.geocoder = geocoder;
        this.geocodeCache = geocodeCache;
        this.geocodeProperties = geocodeProperties;
    }

    public MappedLocation map(String rawLocation) {
        NewLocationConverter.Outcome outcome = converter.resolve(rawLocation);
        if (outcome.match() != null) {
            return fromCodes(rawLocation, outcome.match().provinceCode(), outcome.match().wardCode(), outcome.match().street());
        }
        NewLocationConverter.GeocodeHint hint = outcome.hint();
        if (hint != null) {
            String wardCode = geocodeProperties.enabled() ? geocodedWard(hint) : null;
            if (wardCode == null) {
                wardCode = hint.defaultCode();
            }
            if (wardCode != null) {
                return fromCodes(rawLocation, hint.provinceCode(), wardCode, hint.street());
            }
        }
        if (outcome.provinceCode() != null) {
            return provinceOnly(rawLocation, outcome.provinceCode(), outcome.street());
        }
        return MappedLocation.unmapped(rawLocation);
    }

    private String geocodedWard(NewLocationConverter.GeocodeHint hint) {
        if (hint.sites() == null || hint.sites().isEmpty()) {
            return null;
        }
        String key = sha256(NewLocationConverter.fold(hint.query()));
        String cached = geocodeCache.get(key);
        if (cached != null) {
            return cached.isEmpty() ? null : cached;
        }
        geocodeCache.awaitTurn();
        AddressGeocoder.GeoHit hit = geocoder.lookup(hint.query());
        if (!hit.definitive()) {
            return null;
        }
        if (hit.latitude() == null || hit.longitude() == null
                || !provinceAcceptable(hit.provinceText(), hint.provinceName())) {
            geocodeCache.put(key, MISS, geocodeProperties.negativeTtl());
            return null;
        }
        String wardCode = SplitWardPicker.code(hit.latitude(), hit.longitude(), hint.sites(), hint.defaultCode() == null);
        if (wardCode == null) {
            geocodeCache.put(key, MISS, geocodeProperties.negativeTtl());
            return null;
        }
        geocodeCache.put(key, wardCode, geocodeProperties.positiveTtl());
        return wardCode;
    }

    private MappedLocation fromCodes(String rawLocation, String provinceCode, String wardCode, String street) {
        Province province = provinceRepository.findById(provinceCode).orElse(null);
        Ward ward = wardRepository.findById(wardCode).orElse(null);
        if (province == null || ward == null || !province.getId().equals(ward.getProvinceId())) {
            return MappedLocation.unmapped(rawLocation);
        }
        return new MappedLocation(display(street, ward.getNameLocal(), province.getNameLocal()), province, ward);
    }

    private MappedLocation provinceOnly(String rawLocation, String provinceCode, String street) {
        Province province = provinceRepository.findById(provinceCode).orElse(null);
        if (province == null) {
            return MappedLocation.unmapped(rawLocation);
        }
        return new MappedLocation(display(street, null, province.getNameLocal()), province, null);
    }

    private static boolean provinceAcceptable(String provinceText, String provinceName) {
        if (provinceText == null || provinceText.isBlank()) {
            return true;
        }
        String folded = NewLocationConverter.fold(provinceText);
        String expected = NewLocationConverter.fold(provinceName);
        return folded.equals(expected) || expected.endsWith(folded) || folded.endsWith(expected);
    }

    private static String sha256(String value) {
        try {
            byte[] digest = MessageDigest.getInstance("SHA-256").digest(value.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(digest);
        } catch (Exception exception) {
            throw new IllegalStateException(exception);
        }
    }

    public static String display(String street, String wardName, String provinceName) {
        boolean hasStreet = street != null && !street.isBlank();
        boolean hasWard = wardName != null && !wardName.isBlank();
        if (hasStreet && hasWard) {
            return street + ", " + wardName + ", " + provinceName;
        }
        if (hasWard) {
            return wardName + ", " + provinceName;
        }
        if (hasStreet) {
            return street + ", " + provinceName;
        }
        return provinceName;
    }

    public record MappedLocation(String display, Province province, Ward ward) {
        static MappedLocation unmapped(String rawLocation) {
            return new MappedLocation(rawLocation, null, null);
        }
    }
}
