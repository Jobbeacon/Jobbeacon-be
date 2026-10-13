package com.jobbeacon.address;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.text.Normalizer;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;
import java.util.function.Predicate;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
 
@Component
public class NewLocationConverter {

    private static final String[] PREFIXES = {
            "thanh pho", "thi tran", "thi xa", "dac khu", "phuong", "huyen", "quan", "tinh", "xa", "tp"
    };
    private static final Pattern NUMBERED = Pattern.compile("(?iu)^(p|f|q)\\.?\\s*(\\d+[a-z]?)$");
    private static final Pattern ABBREVIATED = Pattern.compile("(?iu)^(p|f|q|tp|tx|tt|h|x)\\.\\s*(\\S.*)$");
    private static final Pattern LEADING_ZERO = Pattern.compile("\\b0+(\\d)");
    private static final Map<String, String> ABBREVIATIONS = Map.of(
            "p", "Phường", "f", "Phường", "q", "Quận", "tp", "Thành phố",
            "tx", "Thị xã", "tt", "Thị trấn", "h", "Huyện", "x", "Xã"
    );
    private static final String[][] TONE_PLACEMENT = {
            {"oà", "òa"}, {"oá", "óa"}, {"oả", "ỏa"}, {"oã", "õa"}, {"oạ", "ọa"},
            {"oè", "òe"}, {"oé", "óe"}, {"oẻ", "ỏe"}, {"oẽ", "õe"}, {"oẹ", "ọe"},
            {"uỳ", "ùy"}, {"uý", "úy"}, {"uỷ", "ủy"}, {"uỹ", "ũy"}, {"uỵ", "ụy"}
    };

    private final Map<String, Province> provinces = new LinkedHashMap<>();
    private final Map<String, List<ProvinceRef>> provinceKeys = new HashMap<>();
    private final Map<String, Area> areas = new HashMap<>();
    private final List<Placed> allWardNames = new ArrayList<>();
    private final List<Placed> allDistrictNames = new ArrayList<>();

    public NewLocationConverter() {
        this(loadDefault());
    }

    NewLocationConverter(InputStream source) {
        try {
            JsonNode root = new ObjectMapper().readTree(source);
            for (JsonNode node : root.path("provinces")) {
                String code = node.path("code").asText();
                provinces.put(code, new Province(code, node.path("name").asText(), node.path("short").asText()));
                addProvinceKeys(new ProvinceRef(code, null), node);
                for (JsonNode legacy : node.path("legacy")) {
                    addProvinceKeys(new ProvinceRef(code, legacy.path("code").asText()), legacy);
                }
            }
            for (String code : provinces.keySet()) {
                Area area = Area.read(root.path("wards").path(code), root.path("districts").path(code));
                areas.put(code, area);
                area.wards().forEach(ward -> allWardNames.add(new Placed(code, ward.names())));
                area.oldWards().forEach(ward -> allWardNames.add(new Placed(code, ward.names())));
                area.districts().forEach(district -> allDistrictNames.add(new Placed(code, district.names())));
            }
        } catch (IOException exception) {
            throw new UncheckedIOException(exception);
        }
    }

    public Match match(String raw) {
        return resolve(raw).match();
    }

    public Outcome resolve(String raw) {
        List<String> parts = split(unescape(raw).trim());
        while (!parts.isEmpty() && isCountry(parts.get(parts.size() - 1))) {
            parts.remove(parts.size() - 1);
        }
        if (parts.isEmpty()) {
            return Outcome.empty();
        }
        ProvinceMatch found = matchProvince(parts.get(parts.size() - 1));
        if (found == null) {
            found = inferProvince(parts.get(parts.size() - 1));
            if (found == null) {
                return Outcome.empty();
            }
            parts.add(provinces.get(found.code()).name());
        }
        Province province = provinces.get(found.code());
        Area area = areas.get(found.code());
        List<Integer> locality = new ArrayList<>();
        for (int i = parts.size() - 2; i >= 0 && locality.size() < 2; i--) {
            if (!isSameProvince(parts.get(i), province.code())) {
                locality.add(i);
            }
        }
        Resolver resolver = new Resolver(parts, province, area, found.legacyCode(), found.legacyOnly());
        if (locality.isEmpty()) {
            return resolver.provinceOnly(parts.size() - 1);
        }
        int first = locality.get(0);
        Integer second = locality.size() > 1 ? locality.get(1) : null;
        String firstText = parts.get(first);
        List<District> districts = isExplicitWard(firstText) ? List.of() : resolver.districts(firstText);
        if (districts.isEmpty()) {
            return resolver.wardOnly(first);
        }
        if (second != null) {
            Outcome outcome = resolver.wardInDistricts(second, first, districts);
            if (outcome != null) {
                return outcome;
            }
        }
        return resolver.districtOnly(first, districts);
    }

    private final class Resolver {
        private final List<String> parts;
        private final Province province;
        private final Area area;
        private final String legacyCode;
        private final boolean legacyOnly;

        private Resolver(List<String> parts, Province province, Area area, String legacyCode, boolean legacyOnly) {
            this.parts = parts;
            this.province = province;
            this.area = area;
            this.legacyCode = legacyCode;
            this.legacyOnly = legacyOnly;
        }

        List<District> districts(String segment) {
            List<District> hits = find(adminForms(segment), area.districts(), District::names, false);
            return preferLegacy(hits, District::legacyProvince);
        }

        Outcome wardInDistricts(int wardIndex, int districtIndex, List<District> districts) {
            List<String> forms = adminForms(parts.get(wardIndex));
            List<OldWard> pool = new ArrayList<>();
            for (District district : districts) {
                pool.addAll(district.wards());
            }
            List<OldWard> hits = find(forms, pool, OldWard::names, false);
            if (!hits.isEmpty()) {
                return fromLegacy(hits, wardIndex, districtIndex);
            }
            Set<String> targets = new LinkedHashSet<>();
            for (District district : districts) {
                targets.addAll(district.targets());
            }
            List<NewWard> current = find(forms, area.wardsIn(targets), NewWard::names, false);
            if (current.size() == 1) {
                return certain(current.get(0).code(), wardIndex);
            }
            return null;
        }

        Outcome districtOnly(int districtIndex, List<District> districts) {
            Set<String> targets = new LinkedHashSet<>();
            for (District district : districts) {
                targets.addAll(district.targets());
            }
            if (targets.size() == 1) {
                return certain(targets.iterator().next(), districtIndex);
            }
            String street = street(districtIndex);
            if (street.isBlank()) {
                return provinceOnly(districtIndex);
            }
            GeocodeHint hint = new GeocodeHint(
                    province.code(), province.name(), query(street, districtIndex, null), street, null, sites(targets)
            );
            return new Outcome(null, hint, province.code(), street);
        }

        Outcome wardOnly(int wardIndex) {
            List<String> forms = adminForms(parts.get(wardIndex));
            Outcome outcome = legacyOnly ? legacyWard(forms, wardIndex) : currentWard(forms, wardIndex);
            if (outcome == null) {
                outcome = legacyOnly ? currentWard(forms, wardIndex) : legacyWard(forms, wardIndex);
            }
            return outcome == null ? provinceOnly(wardIndex) : outcome;
        }

        private Outcome currentWard(List<String> forms, int wardIndex) {
            List<NewWard> current = find(forms, area.wards(), NewWard::names, true);
            if (current.size() != 1) {
                return current.isEmpty() ? null : provinceOnly(wardIndex);
            }
            String code = current.get(0).code();
            String street = street(wardIndex);
            if (street.isBlank()) {
                return certain(code, wardIndex);
            }
            Set<String> candidates = new LinkedHashSet<>();
            candidates.add(code);
            for (OldWard ward : find(forms, area.oldWards(), OldWard::names, true)) {
                if (ward.to().size() > 1) {
                    candidates.addAll(ward.to());
                }
            }
            if (candidates.size() == 1) {
                return certain(code, wardIndex);
            }
            GeocodeHint hint = new GeocodeHint(
                    province.code(), province.name(), query(street, wardIndex, null), street, code, sites(candidates)
            );
            return new Outcome(null, hint, province.code(), street);
        }

        private Outcome legacyWard(List<String> forms, int wardIndex) {
            List<OldWard> hits = preferLegacy(find(forms, area.oldWards(), OldWard::names, true), OldWard::legacyProvince);
            if (hits.isEmpty()) {
                return null;
            }
            Outcome outcome = fromLegacy(hits, wardIndex, null);
            return outcome == null ? provinceOnly(wardIndex) : outcome;
        }

        Outcome provinceOnly(int firstUnused) {
            String street = street(firstUnused + 1);
            return new Outcome(null, null, province.code(), street);
        }

        private Outcome fromLegacy(List<OldWard> hits, int wardIndex, Integer districtIndex) {
            Set<String> targets = new LinkedHashSet<>();
            boolean allWhole = true;
            for (OldWard ward : hits) {
                targets.addAll(ward.to());
                allWhole &= ward.to().size() == 1;
            }
            if (allWhole && targets.size() == 1) {
                return certain(targets.iterator().next(), wardIndex);
            }
            if (hits.size() != 1) {
                return null;
            }
            OldWard ward = hits.get(0);
            String street = street(wardIndex);
            if (street.isBlank()) {
                return certain(ward.defaultCode(), wardIndex);
            }
            GeocodeHint hint = new GeocodeHint(
                    province.code(),
                    province.name(),
                    query(street, wardIndex, districtIndex),
                    street,
                    ward.defaultCode(),
                    sites(ward.to())
            );
            return new Outcome(null, hint, province.code(), street);
        }

        private Outcome certain(String wardCode, int wardIndex) {
            String street = street(wardIndex);
            return new Outcome(new Match(province.code(), wardCode, street), null, province.code(), street);
        }

        private String street(int end) {
            List<String> street = new ArrayList<>(parts.subList(0, Math.min(end, parts.size())));
            while (!street.isEmpty()) {
                String last = street.get(street.size() - 1);
                if (!isCountry(last) && !isSameProvince(last, province.code())) {
                    break;
                }
                street.remove(street.size() - 1);
            }
            return String.join(", ", street);
        }

        private String query(String street, int wardIndex, Integer districtIndex) {
            String road = street == null ? "" : street.trim();
            int comma = road.lastIndexOf(',');
            if (comma >= 0 && comma < road.length() - 1) {
                road = road.substring(comma + 1).trim();
            }
            List<String> pieces = new ArrayList<>();
            if (!road.isBlank()) {
                pieces.add(road);
            }
            addPlace(pieces, stripPrefix(parts.get(wardIndex)));
            if (districtIndex != null) {
                addPlace(pieces, stripPrefix(parts.get(districtIndex)));
            }
            String provinceLabel = province.shortName() == null || province.shortName().isBlank()
                    ? province.name()
                    : province.shortName();
            addPlace(pieces, provinceLabel);
            return String.join(", ", pieces);
        }

        private void addPlace(List<String> pieces, String place) {
            if (place == null || place.isBlank()) {
                return;
            }
            String folded = fold(place);
            for (String piece : pieces) {
                if (fold(piece).equals(folded)) {
                    return;
                }
            }
            pieces.add(place);
        }

        private List<SplitSite> sites(Iterable<String> codes) {
            List<SplitSite> sites = new ArrayList<>();
            for (String code : codes) {
                NewWard ward = area.byCode().get(code);
                if (ward != null) {
                    sites.add(new SplitSite(ward.code(), ward.lat(), ward.lon(), ward.km2()));
                }
            }
            return sites;
        }

        private <T> List<T> preferLegacy(List<T> hits, Function<T, String> legacyOf) {
            if (legacyCode == null || hits.size() < 2) {
                return hits;
            }
            List<T> scoped = new ArrayList<>();
            for (T hit : hits) {
                if (legacyCode.equals(legacyOf.apply(hit))) {
                    scoped.add(hit);
                }
            }
            return scoped.isEmpty() ? hits : scoped;
        }
    }

    private static <T> List<T> find(List<String> forms, List<T> pool, Function<T, Names> namesOf, boolean strict) {
        List<Form> keys = new ArrayList<>();
        List<String> spellings = new ArrayList<>();
        for (String form : forms) {
            String spelling = nfc(form);
            keys.add(new Form(compact(form), spelling, strict && !spelling.equals(fold(form))));
            spellings.add(spelling);
        }
        int best = 0;
        List<T> hits = new ArrayList<>();
        for (T unit : pool) {
            int score = namesOf.apply(unit).score(keys);
            if (score == 0 || score < best) {
                continue;
            }
            if (score > best) {
                best = score;
                hits.clear();
            }
            hits.add(unit);
        }
        if (hits.size() < 2) {
            return hits;
        }
        List<T> exact = new ArrayList<>();
        for (T unit : hits) {
            if (namesOf.apply(unit).spelledAs(spellings)) {
                exact.add(unit);
            }
        }
        return exact.isEmpty() ? hits : exact;
    }

    private ProvinceMatch matchProvince(String segment) {
        Set<String> codes = new LinkedHashSet<>();
        Set<String> legacyCodes = new LinkedHashSet<>();
        boolean current = false;
        List<String> keys = new ArrayList<>();
        keys.add(compact(segment));
        for (String form : adminForms(segment)) {
            keys.add(compact(form));
        }
        for (String key : keys) {
            for (ProvinceRef ref : provinceKeys.getOrDefault(key, List.of())) {
                codes.add(ref.code());
                if (ref.legacyCode() != null) {
                    legacyCodes.add(ref.legacyCode());
                } else {
                    current = true;
                }
            }
        }
        if (codes.size() != 1) {
            return null;
        }
        return new ProvinceMatch(
                codes.iterator().next(),
                legacyCodes.size() == 1 ? legacyCodes.iterator().next() : null,
                !current
        );
    }

    private ProvinceMatch inferProvince(String segment) {
        boolean ward = isExplicitWard(segment);
        if (!ward && !isExplicitDistrict(segment)) {
            return null;
        }
        Set<String> codes = new LinkedHashSet<>();
        for (Placed placed : find(adminForms(segment), ward ? allWardNames : allDistrictNames, Placed::names, true)) {
            codes.add(placed.province());
        }
        return codes.size() == 1 ? new ProvinceMatch(codes.iterator().next(), null, false) : null;
    }

    private boolean isSameProvince(String segment, String code) {
        if (isCountry(segment)) {
            return true;
        }
        ProvinceMatch match = matchProvince(segment);
        return match != null && match.code().equals(code) && !isExplicitWard(segment) && !isExplicitDistrict(segment);
    }

    private void addProvinceKeys(ProvinceRef ref, JsonNode node) {
        Set<String> keys = new LinkedHashSet<>();
        keys.add(compact(node.path("name").asText()));
        keys.add(compact(node.path("short").asText()));
        for (JsonNode alias : node.path("aliases")) {
            keys.add(alias.asText());
        }
        for (String key : keys) {
            if (!key.isEmpty()) {
                provinceKeys.computeIfAbsent(key, ignored -> new ArrayList<>()).add(ref);
            }
        }
    }

    private static InputStream loadDefault() {
        InputStream source = NewLocationConverter.class.getResourceAsStream("/address/vn-admin-2025.json");
        if (source == null) {
            throw new IllegalStateException("Missing /address/vn-admin-2025.json");
        }
        return source;
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

    static String compact(String value) {
        return fold(value).replaceAll("[^a-z0-9]", "");
    }

    private static String nfc(String value) {
        String text = Normalizer.normalize(value == null ? "" : value, Normalizer.Form.NFC)
                .toLowerCase(Locale.ROOT)
                .trim()
                .replaceAll("\\s+", " ");
        for (String[] pair : TONE_PLACEMENT) {
            text = text.replaceAll("(?<![qg])" + pair[0], pair[1]);
        }
        return text;
    }

    private static List<String> adminForms(String raw) {
        String segment = canonical(raw);
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
            if (form.matches("\\d+")) {
                expanded.add("Phường " + form);
                expanded.add("Quận " + form);
            }
        }
        List<String> unique = new ArrayList<>();
        Set<String> seen = new LinkedHashSet<>();
        for (String form : expanded) {
            String key = compact(form);
            if (!key.isEmpty() && seen.add(key)) {
                unique.add(form);
            }
        }
        return unique;
    }

    private static String canonical(String segment) {
        String text = LEADING_ZERO.matcher(segment.trim()).replaceAll("$1");
        Matcher numbered = NUMBERED.matcher(text);
        if (numbered.matches()) {
            return ABBREVIATIONS.get(numbered.group(1).toLowerCase(Locale.ROOT)) + " " + numbered.group(2);
        }
        Matcher abbreviated = ABBREVIATED.matcher(text);
        if (abbreviated.matches()) {
            return ABBREVIATIONS.get(abbreviated.group(1).toLowerCase(Locale.ROOT)) + " " + abbreviated.group(2);
        }
        return text;
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
        String folded = fold(canonical(segment));
        return folded.startsWith("phuong ")
                || folded.startsWith("xa ")
                || folded.startsWith("thi tran ")
                || folded.startsWith("dac khu ")
                || folded.startsWith("ward ")
                || folded.endsWith(" ward");
    }

    private static boolean isExplicitDistrict(String segment) {
        String folded = fold(canonical(segment));
        return folded.startsWith("huyen ")
                || folded.startsWith("quan ")
                || folded.startsWith("thi xa ")
                || folded.startsWith("thanh pho ")
                || folded.startsWith("tp ")
                || folded.startsWith("tx ")
                || folded.startsWith("district ")
                || folded.endsWith(" district");
    }

    private static boolean isCountry(String segment) {
        String key = compact(segment);
        return key.equals("vietnam") || key.equals("vn");
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

    private static List<String> texts(JsonNode node) {
        List<String> values = new ArrayList<>();
        node.forEach(item -> values.add(item.asText()));
        return values;
    }

    private record Province(String code, String name, String shortName) {
    }

    private record ProvinceRef(String code, String legacyCode) {
    }

    private record ProvinceMatch(String code, String legacyCode, boolean legacyOnly) {
    }

    private record Names(String fullKey, String shortKey, Set<String> aliases, String fullSpelling, String shortSpelling) {
        static Names of(String full, String shortName, List<String> aliases) {
            return new Names(compact(full), compact(shortName), new LinkedHashSet<>(aliases), nfc(full), nfc(shortName));
        }

        int score(List<Form> forms) {
            int score = 0;
            for (Form form : forms) {
                if (form.key().equals(fullKey) && (!form.accented() || form.spelling().equals(fullSpelling))) {
                    return 2;
                }
                if (form.key().equals(shortKey) && (!form.accented() || form.spelling().equals(shortSpelling))) {
                    score = 1;
                } else if (aliases.contains(form.key())) {
                    score = 1;
                }
            }
            return score;
        }

        boolean spelledAs(List<String> spellings) {
            return spellings.contains(fullSpelling) || spellings.contains(shortSpelling);
        }
    }

    private record Form(String key, String spelling, boolean accented) {
    }

    private record Placed(String province, Names names) {
    }

    private record NewWard(String code, Names names, double lat, double lon, double km2) {
    }

    private record OldWard(String legacyProvince, Names names, List<String> to, String defaultCode) {
    }

    private record District(String legacyProvince, Names names, List<String> targets, List<OldWard> wards) {
    }

    private record Area(List<NewWard> wards, Map<String, NewWard> byCode, List<District> districts, List<OldWard> oldWards) {
        static Area read(JsonNode wardNodes, JsonNode districtNodes) {
            List<NewWard> wards = new ArrayList<>();
            Map<String, NewWard> byCode = new HashMap<>();
            for (JsonNode node : wardNodes) {
                NewWard ward = new NewWard(
                        node.get(0).asText(),
                        Names.of(node.get(1).asText(), node.get(2).asText(), List.of()),
                        node.get(3).asDouble(),
                        node.get(4).asDouble(),
                        node.get(5).asDouble()
                );
                wards.add(ward);
                byCode.put(ward.code(), ward);
            }
            List<District> districts = new ArrayList<>();
            List<OldWard> oldWards = new ArrayList<>();
            for (JsonNode node : districtNodes) {
                String legacy = node.path("p").asText();
                List<OldWard> members = new ArrayList<>();
                for (JsonNode item : node.path("w")) {
                    members.add(new OldWard(
                            legacy,
                            Names.of(item.get(0).asText(), item.get(1).asText(), texts(item.get(4))),
                            texts(item.get(2)),
                            item.get(3).asText()
                    ));
                }
                districts.add(new District(
                        legacy,
                        Names.of(node.path("n").asText(), node.path("s").asText(), texts(node.path("a"))),
                        texts(node.path("t")),
                        List.copyOf(members)
                ));
                oldWards.addAll(members);
            }
            return new Area(List.copyOf(wards), Map.copyOf(byCode), List.copyOf(districts), List.copyOf(oldWards));
        }

        List<NewWard> wardsIn(Set<String> codes) {
            Predicate<NewWard> inside = ward -> codes.contains(ward.code());
            return wards.stream().filter(inside).toList();
        }
    }

    public record Match(String provinceCode, String wardCode, String street) {
    }

    public record SplitSite(String code, double lat, double lon, double km2) {
    }

    public record GeocodeHint(
            String provinceCode,
            String provinceName,
            String query,
            String street,
            String defaultCode,
            List<SplitSite> sites
    ) {
    }

    public record Outcome(Match match, GeocodeHint hint, String provinceCode, String street) {
        static Outcome empty() {
            return new Outcome(null, null, null, "");
        }
    }
}
