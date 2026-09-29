package com.jobbeacon.address;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.text.Normalizer;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;

@Component
public class NewLocationConverter {

    private static final String[] PREFIXES = {
            "thanh pho", "thi tran", "thi xa", "phuong", "huyen", "quan", "tinh", "xa", "tp"
    };

    /**
     * Old province names from vietnamadminunits {@code convert_address}:
     * {@code DICT_PROVINCE} maps each abolished province onto one new province.
     * Short aliases {@code baria} and {@code vungtau} come from
     * {@code data/alias_keywords/legacy/alias_province.csv}.
     */
    private static final java.util.Map<String, String> LEGACY_PROVINCE = legacyProvinces();

    private final List<Province> provinces;
    private final java.util.Map<String, ProvinceData> index;
    private final SplitIndex splits;

    public NewLocationConverter() {
        this(loadDefault());
    }

    NewLocationConverter(InputStream source) {
        try {
            JsonNode root = new ObjectMapper().readTree(source);
            provinces = new ArrayList<>();
            index = new java.util.HashMap<>();
            for (JsonNode node : root.path("provinces")) {
                provinces.add(new Province(
                        node.path("code").asText(),
                        node.path("name").asText(),
                        textSet(node.path("folds")),
                        textSet(node.path("compacts"))
                ));
            }
            JsonNode all = root.path("index");
            all.fieldNames().forEachRemaining(code -> index.put(code, ProvinceData.read(all.path(code))));
            splits = SplitIndex.load();
        } catch (IOException exception) {
            throw new UncheckedIOException(exception);
        }
    }

    public Match match(String raw) {
        return resolve(raw).match();
    }

    public Outcome resolve(String raw) {
        String text = unescape(raw).trim();
        if (text.isEmpty()) {
            return Outcome.empty();
        }
        List<String> parts = split(text);
        if (parts.isEmpty()) {
            return Outcome.empty();
        }
        Province province = matchProvince(parts.get(parts.size() - 1));
        if (province == null) {
            return Outcome.empty();
        }
        ProvinceData data = index.get(province.code);
        if (data == null) {
            return Outcome.empty();
        }
        List<Located> locality = new ArrayList<>();
        int indexCursor = parts.size() - 2;
        while (indexCursor >= 0) {
            String segment = parts.get(indexCursor);
            Province same = matchProvince(segment);
            if (isCountry(segment) || (same != null && same.code.equals(province.code))) {
                indexCursor--;
                continue;
            }
            Token token = classify(segment, data);
            if (token.newWards.isEmpty() && token.old.isEmpty() && token.districts.isEmpty()) {
                break;
            }
            locality.add(new Located(segment, token));
            indexCursor--;
        }
        java.util.Collections.reverse(locality);
        String street = String.join(", ", parts.subList(0, indexCursor + 1));
        if (locality.isEmpty()) {
            return new Outcome(null, null, province.code, street);
        }
        List<Token> tokens = locality.stream().map(Located::token).toList();
        Decision chosen = choose(tokens, data.names);
        if (chosen != null && chosen.ward != null) {
            return new Outcome(new Match(province.code, chosen.ward.code, street), null, province.code, street);
        }
        SplitGroup split = splits.select(province.code, locality);
        if (split != null && street.isBlank()) {
            return new Outcome(new Match(province.code, split.defaultCode, street), null, province.code, street);
        }
        if (split != null) {
            String query = street + ", " + split.segment + ", " + province.name + ", Vietnam";
            return new Outcome(
                    null,
                    new GeocodeHint(province.code, province.name, query, street, allowedWards(data, split.codes), split.defaultCode, split.sites),
                    province.code,
                    street
            );
        }
        if (tokens.size() == 1 && !street.isBlank()) {
            List<AllowedWard> allowed = allowedWards(data, tokens.get(0).candidateCodes());
            if (allowed.size() >= 2) {
                String query = street + ", " + locality.get(0).segment() + ", " + province.name + ", Vietnam";
                return new Outcome(
                        null,
                        new GeocodeHint(province.code, province.name, query, street, allowed, null, List.of()),
                        province.code,
                        street
                );
            }
        }
        return new Outcome(null, null, province.code, street);
    }

    private static InputStream loadDefault() {
        InputStream source = NewLocationConverter.class.getResourceAsStream("/address/vn-admin-2025.json");
        if (source == null) {
            throw new IllegalStateException("Missing /address/vn-admin-2025.json");
        }
        return source;
    }

    private Decision choose(List<Token> locality, java.util.Map<String, String> names) {
        List<Decision> unique = new ArrayList<>();
        for (Token token : locality) {
            Decision decision = token.decision(names);
            if (decision.ward != null) {
                unique.add(decision);
            }
        }
        Set<String> codes = new LinkedHashSet<>();
        for (Decision decision : unique) {
            codes.add(decision.ward.code);
        }
        if (codes.size() != 1) {
            return null;
        }
        String wardCode = codes.iterator().next();
        for (Token token : locality) {
            Decision decision = token.decision(names);
            if (decision.ward != null) {
                continue;
            }
            Set<String> targets = new LinkedHashSet<>();
            for (District district : token.districts) {
                targets.addAll(district.targets);
            }
            Set<String> other = new LinkedHashSet<>();
            for (OldWard old : token.old) {
                other.addAll(old.mapped);
            }
            for (Ward ward : token.newWards) {
                other.add(ward.code);
            }
            boolean sameWardOnly = other.isEmpty() || (other.size() == 1 && other.contains(wardCode));
            if (!targets.contains(wardCode) && !(targets.isEmpty() && sameWardOnly)) {
                return null;
            }
        }
        return unique.get(0);
    }

    private Token classify(String segment, ProvinceData data) {
        List<String> forms = adminForms(segment);
        boolean districtOnly = isExplicitDistrict(segment);
        List<Ward> news = new ArrayList<>();
        Set<String> seen = new LinkedHashSet<>();
        List<OldWard> olds = new ArrayList<>();
        if (!districtOnly) {
            for (Ward ward : data.wards) {
                if ((matches(forms, ward.shortFold, ward.shortNfc, data.collisions)
                        || matches(forms, ward.nameFold, ward.nameNfc, data.collisions))
                        && seen.add(ward.code)) {
                    news.add(ward);
                }
            }
            for (OldWard old : data.legacy) {
                if (matches(forms, old.shortFold, old.shortNfc, data.collisions)
                        || matches(forms, old.nameFold, old.nameNfc, data.collisions)) {
                    olds.add(old);
                }
            }
        }
        List<District> districts = new ArrayList<>();
        if (!isExplicitWard(segment)) {
            for (District district : data.districts) {
                if (matches(forms, district.shortFold, district.shortNfc, data.collisions)
                        || matches(forms, district.nameFold, district.nameNfc, data.collisions)) {
                    districts.add(district);
                }
            }
        }
        return new Token(news, olds, districts);
    }

    private Province matchProvince(String segment) {
        Set<String> hits = new LinkedHashSet<>();
        Province found = null;
        for (String form : adminForms(segment)) {
            found = considerOfficial(hits, found, fold(form));
        }
        String legacy = legacyKey(fold(segment));
        found = considerLegacy(hits, found, legacy);
        if (legacy.startsWith("tinh ")) {
            found = considerLegacy(hits, found, legacy.substring("tinh ".length()));
        }
        return hits.size() == 1 ? found : null;
    }

    private Province considerOfficial(Set<String> hits, Province found, String folded) {
        String compact = folded.replace(" ", "");
        for (Province province : provinces) {
            if (province.folds.contains(folded) || province.compacts.contains(compact) || province.compacts.contains(folded)) {
                if (hits.add(province.code)) {
                    found = province;
                }
            }
        }
        return found;
    }

    private Province considerLegacy(Set<String> hits, Province found, String key) {
        if (key == null || key.isBlank()) {
            return found;
        }
        String code = LEGACY_PROVINCE.get(key);
        if (code == null) {
            code = LEGACY_PROVINCE.get(key.replace(" ", ""));
        }
        if (code == null || !hits.add(code)) {
            return found;
        }
        Province province = provinceByCode(code);
        if (province == null) {
            hits.remove(code);
            return found;
        }
        return province;
    }

    private Province provinceByCode(String code) {
        for (Province province : provinces) {
            if (province.code.equals(code)) {
                return province;
            }
        }
        return null;
    }

    private static java.util.Map<String, String> legacyProvinces() {
        java.util.Map<String, String> map = new java.util.HashMap<>();
        String[][] rows = {
                {"24", "bac giang"},
                {"08", "ha giang"},
                {"15", "yen bai"},
                {"19", "bac kan"},
                {"25", "vinh phuc"},
                {"25", "hoa binh"},
                {"31", "hai duong"},
                {"33", "thai binh"},
                {"37", "ha nam"},
                {"37", "nam dinh"},
                {"44", "quang binh"},
                {"48", "quang nam"},
                {"51", "kon tum"},
                {"56", "ninh thuan"},
                {"52", "binh dinh"},
                {"66", "phu yen"},
                {"68", "dak nong"},
                {"68", "binh thuan"},
                {"80", "long an"},
                {"75", "binh phuoc"},
                {"79", "binh duong"},
                {"79", "ba ria vung tau"},
                {"79", "ba ria"},
                {"79", "vung tau"},
                {"86", "ben tre"},
                {"86", "tra vinh"},
                {"82", "tien giang"},
                {"91", "kien giang"},
                {"92", "soc trang"},
                {"92", "hau giang"},
                {"96", "bac lieu"}
        };
        for (String[] row : rows) {
            map.put(row[1], row[0]);
            map.put(row[1].replace(" ", ""), row[0]);
            map.put("tinh " + row[1], row[0]);
            map.put(("tinh " + row[1]).replace(" ", ""), row[0]);
        }
        return java.util.Map.copyOf(map);
    }

    private static String legacyKey(String folded) {
        return folded.replace('–', '-').replace('—', '-').replaceAll("\\s*-\\s*", " ").replaceAll("\\s+", " ").trim();
    }

    private static boolean matches(List<String> forms, String officialFold, String officialNfc, Set<String> collisions) {
        if (officialFold == null || officialFold.isEmpty()) {
            return false;
        }
        for (String form : forms) {
            if (!fold(form).equals(officialFold)) {
                continue;
            }
            if (collisions.contains(officialFold) && !nfc(form).equals(officialNfc)) {
                continue;
            }
            return true;
        }
        return false;
    }

    public static String fold(String value) {
        String lower = Normalizer.normalize(value == null ? "" : value, Normalizer.Form.NFC)
                .toLowerCase(Locale.ROOT)
                .replace('đ', 'd')
                .replace('Đ', 'd');
        String decomposed = Normalizer.normalize(lower, Normalizer.Form.NFD);
        StringBuilder out = new StringBuilder(decomposed.length());
        for (int i = 0; i < decomposed.length(); ) {
            int cp = decomposed.codePointAt(i);
            if (Character.getType(cp) != Character.NON_SPACING_MARK) {
                out.appendCodePoint(cp);
            }
            i += Character.charCount(cp);
        }
        return out.toString().trim().replaceAll("\\s+", " ");
    }

    private static String nfc(String value) {
        return Normalizer.normalize(value == null ? "" : value, Normalizer.Form.NFC)
                .toLowerCase(Locale.ROOT)
                .trim()
                .replaceAll("\\s+", " ");
    }

    private static List<String> adminForms(String segment) {
        List<String> forms = new ArrayList<>();
        forms.add(segment);
        forms.add(stripPrefix(segment));
        String[] words = segment.split("\\s+");
        if (words.length > 1 && (fold(words[0]).equals("ward") || fold(words[0]).equals("district"))) {
            forms.add(join(words, 1, words.length));
        }
        if (words.length > 1) {
            String last = fold(words[words.length - 1]);
            if (last.equals("ward") || last.equals("district") || last.equals("city")) {
                forms.add(join(words, 0, words.length - 1));
            }
        }
        List<String> expanded = new ArrayList<>(forms);
        for (String form : forms) {
            String[] bits = form.split("\\s+");
            if (bits.length == 1 && bits[0].matches("\\d+")) {
                expanded.add("Quận " + bits[0]);
            }
        }
        List<String> unique = new ArrayList<>();
        Set<String> seen = new LinkedHashSet<>();
        for (String form : expanded) {
            String key = fold(form);
            if (!key.isEmpty() && seen.add(key)) {
                unique.add(form);
            }
        }
        return unique;
    }

    private static String stripPrefix(String segment) {
        String folded = fold(segment);
        for (String prefix : PREFIXES) {
            String head = prefix + " ";
            if (folded.startsWith(head) && !folded.substring(head.length()).isBlank()) {
                String[] words = segment.split("\\s+");
                int count = prefix.split(" ").length;
                if (words.length > count) {
                    return join(words, count, words.length);
                }
            }
        }
        return segment;
    }

    private static String join(String[] words, int from, int to) {
        return String.join(" ", java.util.Arrays.copyOfRange(words, from, to));
    }

    private static boolean isExplicitWard(String segment) {
        String folded = fold(segment);
        return folded.startsWith("phuong ")
                || folded.startsWith("xa ")
                || folded.startsWith("thi tran ")
                || folded.startsWith("ward ")
                || folded.endsWith(" ward");
    }

    private static boolean isExplicitDistrict(String segment) {
        String folded = fold(segment);
        return folded.startsWith("huyen ")
                || folded.startsWith("quan ")
                || folded.startsWith("thi xa ")
                || folded.startsWith("thanh pho ")
                || folded.startsWith("district ");
    }

    private static boolean isCountry(String segment) {
        String folded = fold(segment);
        return folded.equals("vietnam") || folded.equals("viet nam") || folded.equals("vn");
    }

    private static String unescape(String value) {
        if (value == null) {
            return "";
        }
        return value.replace("&amp;", "&").replace("&apos;", "'");
    }

    private static List<String> split(String text) {
        List<String> parts = new ArrayList<>();
        for (String part : text.split(",")) {
            String trimmed = part.trim();
            if (!trimmed.isEmpty()) {
                parts.add(trimmed);
            }
        }
        return parts;
    }

    private static Set<String> textSet(JsonNode node) {
        Set<String> values = new LinkedHashSet<>();
        if (node.isArray()) {
            node.forEach(item -> values.add(item.asText()));
        }
        return values;
    }

    private record Province(String code, String name, Set<String> folds, Set<String> compacts) {
    }

    private record Ward(String shortFold, String shortNfc, String nameFold, String nameNfc, String code, String name) {
    }

    private record OldWard(String shortFold, String shortNfc, String nameFold, String nameNfc, List<String> mapped) {
    }

    private record District(String shortFold, String shortNfc, String nameFold, String nameNfc, Set<String> targets) {
    }

    private record ProvinceData(
            List<Ward> wards,
            List<OldWard> legacy,
            List<District> districts,
            Set<String> collisions,
            java.util.Map<String, String> names
    ) {
        static ProvinceData read(JsonNode node) {
            List<Ward> wards = new ArrayList<>();
            java.util.Map<String, String> names = new java.util.HashMap<>();
            for (JsonNode item : node.path("wards")) {
                Ward ward = new Ward(
                        item.path("sf").asText(), item.path("sn").asText(),
                        item.path("nf").asText(), item.path("nn").asText(),
                        item.path("code").asText(), item.path("name").asText()
                );
                wards.add(ward);
                names.putIfAbsent(ward.code, ward.name);
            }
            List<OldWard> legacy = new ArrayList<>();
            for (JsonNode item : node.path("legacy")) {
                legacy.add(new OldWard(
                        item.path("sf").asText(), item.path("sn").asText(),
                        item.path("nf").asText(), item.path("nn").asText(),
                        codes(item.path("m"))
                ));
            }
            List<District> districts = new ArrayList<>();
            for (JsonNode item : node.path("districts")) {
                districts.add(new District(
                        item.path("sf").asText(), item.path("sn").asText(),
                        item.path("nf").asText(), item.path("nn").asText(),
                        new LinkedHashSet<>(codes(item.path("t")))
                ));
            }
            return new ProvinceData(wards, legacy, districts, textSet(node.path("collisions")), names);
        }
    }

    private static List<String> codes(JsonNode node) {
        List<String> codes = new ArrayList<>();
        if (node.isArray()) {
            node.forEach(item -> codes.add(item.asText()));
        }
        return codes;
    }

    private List<AllowedWard> allowedWards(ProvinceData data, Set<String> codes) {
        List<AllowedWard> allowed = new ArrayList<>();
        for (String code : codes) {
            for (Ward ward : data.wards) {
                if (ward.code.equals(code)) {
                    allowed.add(new AllowedWard(ward.code, ward.shortFold, ward.nameFold));
                    break;
                }
            }
        }
        return allowed;
    }

    public record Match(String provinceCode, String wardCode, String street) {
    }

    public record AllowedWard(String code, String shortFold, String nameFold) {
    }

    public record SplitSite(String code, double lat, double lon, double km2) {
    }

    public record GeocodeHint(
            String provinceCode,
            String provinceName,
            String query,
            String street,
            List<AllowedWard> allowed,
            String defaultCode,
            List<SplitSite> sites
    ) {
    }

    private record SplitGroup(String segment, String defaultCode, Set<String> codes, Set<String> districts, List<SplitSite> sites) {
    }

    private static final class SplitIndex {
        private final java.util.Map<String, java.util.Map<String, List<SplitGroup>>> byProvince;

        private SplitIndex(java.util.Map<String, java.util.Map<String, List<SplitGroup>>> byProvince) {
            this.byProvince = byProvince;
        }

        static SplitIndex load() {
            try (InputStream source = NewLocationConverter.class.getResourceAsStream("/address/vn-admin-split.json")) {
                if (source == null) {
                    return new SplitIndex(java.util.Map.of());
                }
                JsonNode root = new ObjectMapper().readTree(source);
                java.util.Map<String, java.util.Map<String, List<SplitGroup>>> provinces = new java.util.HashMap<>();
                root.fieldNames().forEachRemaining(provinceCode -> {
                    java.util.Map<String, List<SplitGroup>> names = new java.util.HashMap<>();
                    JsonNode byName = root.path(provinceCode);
                    byName.fieldNames().forEachRemaining(name -> {
                        List<SplitGroup> groups = new ArrayList<>();
                        for (JsonNode group : byName.path(name)) {
                            List<SplitSite> sites = new ArrayList<>();
                            Set<String> codes = new LinkedHashSet<>();
                            for (JsonNode site : group.path("sites")) {
                                String code = site.path("code").asText();
                                codes.add(code);
                                sites.add(new SplitSite(
                                        code,
                                        site.path("lat").asDouble(),
                                        site.path("lon").asDouble(),
                                        site.path("km2").asDouble()
                                ));
                            }
                            groups.add(new SplitGroup(
                                    "",
                                    group.path("defaultCode").asText(),
                                    codes,
                                    textSet(group.path("districts")),
                                    List.copyOf(sites)
                            ));
                        }
                        names.put(name, List.copyOf(groups));
                    });
                    provinces.put(provinceCode, names);
                });
                return new SplitIndex(provinces);
            } catch (IOException exception) {
                throw new UncheckedIOException(exception);
            }
        }

        SplitGroup select(String provinceCode, List<Located> locality) {
            Set<String> districtNames = new LinkedHashSet<>();
            for (Located located : locality) {
                for (District district : located.token().districts()) {
                    districtNames.add(compact(district.shortFold()));
                }
            }
            List<SplitGroup> matches = new ArrayList<>();
            for (Located located : locality) {
                for (OldWard oldWard : located.token().old()) {
                    if (oldWard.mapped().size() < 2) {
                        continue;
                    }
                    List<SplitGroup> named = byProvince
                            .getOrDefault(provinceCode, java.util.Map.of())
                            .getOrDefault(compact(oldWard.shortFold()), List.of());
                    Set<String> mapped = new LinkedHashSet<>(oldWard.mapped());
                    List<SplitGroup> sameCodes = new ArrayList<>();
                    for (SplitGroup group : named) {
                        if (group.codes.equals(mapped)) {
                            sameCodes.add(group);
                        }
                    }
                    List<SplitGroup> chosen = sameCodes;
                    if (!districtNames.isEmpty()) {
                        List<SplitGroup> byDistrict = new ArrayList<>();
                        for (SplitGroup group : sameCodes) {
                            for (String district : group.districts) {
                                if (districtNames.contains(district)) {
                                    byDistrict.add(group);
                                    break;
                                }
                            }
                        }
                        if (!byDistrict.isEmpty()) {
                            chosen = byDistrict;
                        }
                    }
                    if (chosen.size() == 1) {
                        SplitGroup group = chosen.get(0);
                        matches.add(new SplitGroup(located.segment(), group.defaultCode, group.codes, group.districts, group.sites));
                    }
                }
            }
            if (matches.size() != 1) {
                return null;
            }
            return matches.get(0);
        }

        private static String compact(String folded) {
            return folded.replace(" ", "");
        }
    }

    public record Outcome(Match match, GeocodeHint hint, String provinceCode, String street) {
        static Outcome empty() {
            return new Outcome(null, null, null, "");
        }
    }

    private record Located(String segment, Token token) {
    }

    private record Decision(Ward ward) {
    }

    private record Token(List<Ward> newWards, List<OldWard> old, List<District> districts) {
        Decision decision(java.util.Map<String, String> names) {
            int n = newWards.size();
            Set<String> targets = new LinkedHashSet<>();
            for (District district : districts) {
                targets.addAll(district.targets);
            }
            int d = targets.isEmpty() && !districts.isEmpty() ? districts.size() : targets.size();
            if (n == 1 && d == 0) {
                Ward ward = newWards.get(0);
                boolean same = true;
                for (OldWard oldWard : old) {
                    Set<String> mapped = new LinkedHashSet<>(oldWard.mapped);
                    if (!(mapped.size() == 1 && mapped.contains(ward.code))) {
                        same = false;
                        break;
                    }
                }
                return same ? new Decision(ward) : new Decision(null);
            }
            if (n == 0 && d == 0 && old.size() == 1) {
                Set<String> mapped = new LinkedHashSet<>(old.get(0).mapped);
                if (mapped.size() == 1) {
                    String code = mapped.iterator().next();
                    for (Ward candidate : newWards) {
                        if (candidate.code.equals(code)) {
                            return new Decision(candidate);
                        }
                    }
                    String name = names.getOrDefault(code, code);
                    return new Decision(new Ward("", "", "", "", code, name));
                }
            }
            return new Decision(null);
        }

        Set<String> candidateCodes() {
            Set<String> codes = new LinkedHashSet<>();
            for (District district : districts) {
                codes.addAll(district.targets);
            }
            for (OldWard oldWard : old) {
                if (oldWard.mapped.size() > 1) {
                    codes.addAll(oldWard.mapped);
                }
            }
            if (codes.isEmpty()) {
                for (OldWard oldWard : old) {
                    codes.addAll(oldWard.mapped);
                }
            }
            return codes;
        }
    }
}
