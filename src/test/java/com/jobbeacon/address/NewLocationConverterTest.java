package com.jobbeacon.address;

import com.jobbeacon.service.LocationMappingService;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class NewLocationConverterTest {

    private static NewLocationConverter converter;

    @BeforeAll
    static void setUp() {
        converter = new NewLocationConverter();
    }

    @Test
    void mapsKnownWardsToSeedCodes() {
        NewLocationConverter.Match tuLiem = converter.match("SSI Tower, 1 Châu Văn Liêm, Tu Liem, Ha Noi");
        assertEquals("01", tuLiem.provinceCode());
        assertEquals("00592", tuLiem.wardCode());
        assertEquals("SSI Tower, 1 Châu Văn Liêm", tuLiem.street());

        NewLocationConverter.Match cuaNam = converter.match("Số 6 Quang Trung, phường Trần Hưng Đạo, Ha Noi");
        assertEquals("01", cuaNam.provinceCode());
        assertEquals("00082", cuaNam.wardCode());

        NewLocationConverter.Match saiGon = converter.match(
                "Tầng 20A, Vincom Center Đồng Khởi, 72 Lê Thánh Tôn, phường Bến Nghé, Ho Chi Minh");
        assertEquals("79", saiGon.provinceCode());
        assertEquals("26740", saiGon.wardCode());
    }

    @Test
    void currentWardWinsOverAnOldWardWithTheSameName() {
        assertEquals("00082", converter.match("Phường Cửa Nam, Hà Nội").wardCode());
        assertEquals("00175", converter.match("Phường Yên Hòa, Hà Nội").wardCode());
        assertEquals("00008", converter.match("Phường Ngọc Hà, Hà Nội").wardCode());
        assertEquals("00367", converter.match("12 Nguyễn Trãi, Phường Thanh Xuân, Hà Nội").wardCode());
    }

    @Test
    void currentWardNamedLikeADividedOldWardIsGeocodedAmongItsSuccessors() {
        NewLocationConverter.Outcome cuaNam = converter.resolve("Số 1 Tràng Tiền, Phường Cửa Nam, Thành phố Hà Nội");
        assertNull(cuaNam.match());
        assertEquals("Số 1 Tràng Tiền", cuaNam.street());
        assertEquals("00082", cuaNam.hint().defaultCode());
        assertEquals(4, cuaNam.hint().sites().size());

        // Old Yên Hòa went to Cầu Giấy and Yên Hòa; the 2025 Yên Hòa stays the answer
        // unless the street falls squarely inside Cầu Giấy.
        NewLocationConverter.Outcome yenHoa = converter.resolve("Tòa nhà MB, số 18 Lê Văn Lương, Phường Yên Hòa, Ha Noi");
        assertNull(yenHoa.match());
        assertEquals("00175", yenHoa.hint().defaultCode());
        assertEquals("00166", SplitWardPicker.code(21.0309, 105.788, yenHoa.hint().sites(), false));
        assertNull(SplitWardPicker.code(21.0500, 105.700, yenHoa.hint().sites(), false));
    }

    @Test
    void explicitPhuongCauGiayIsTheCurrentWard() {
        NewLocationConverter.Match cauGiay = converter.match("Số 5 Duy Tân, Phường Cầu Giấy, Hà Nội");
        assertEquals("01", cauGiay.provinceCode());
        assertEquals("00166", cauGiay.wardCode());
        assertEquals("Số 5 Duy Tân", cauGiay.street());
    }

    @Test
    void oldCommuneInsideItsDistrictMapsToTheSingleNewWard() {
        NewLocationConverter.Match quatDong = converter.match(
                "xã Quất Động, huyện Thường Tín, thành phố Hà Nội");
        assertEquals("01", quatDong.provinceCode());
        assertEquals("10231", quatDong.wardCode());
        assertEquals("", quatDong.street());
        NewLocationConverter.Match house = converter.match("nhà 13, quất động, thường tín, hà nội");
        assertEquals("01", house.provinceCode());
        assertEquals("10231", house.wardCode());
        assertEquals("nhà 13", house.street());
        assertNull(converter.match("huyện Thường Tín, thành phố Hà Nội"));
        assertEquals("01", converter.resolve("huyện Thường Tín, thành phố Hà Nội").provinceCode());
        assertNull(converter.match("nhà 13, thường tín, hà nội"));

        assertEquals("00376", converter.match("Xã Đông Xuân, Huyện Sóc Sơn, Thành phố Hà Nội").wardCode());
    }

    @Test
    void repeatedOldWardNamesAreResolvedInsideTheirDistrict() {
        assertEquals("27154", converter.match("Phường 1, Quận 3, Hồ Chí Minh").wardCode());
        assertEquals("27154", converter.match("12 Võ Văn Tần, P.1, Q.3, TP.HCM").wardCode());
        assertEquals("27154", converter.match("Phường 01, Quận 3, TP. Hồ Chí Minh, Việt Nam").wardCode());
        assertEquals("27154", converter.match("Ward 1, District 3, Ho Chi Minh City").wardCode());
        assertNull(converter.match("Phường 1, Hồ Chí Minh"));
        assertEquals("79", converter.resolve("Phường 1, Hồ Chí Minh").provinceCode());
    }

    @Test
    void dividedOldWardUsesTheLibraryDefaultAndSquare() {
        NewLocationConverter.Match yenHoa = converter.match("Phường Yên Hòa, Quận Cầu Giấy, Hà Nội");
        assertEquals("01", yenHoa.provinceCode());
        assertEquals("00175", yenHoa.wardCode());
        assertEquals("", yenHoa.street());

        NewLocationConverter.Outcome street = converter.resolve(
                "Số 18 Lê Văn Lương, Phường Yên Hòa, Quận Cầu Giấy, Hà Nội");
        assertNull(street.match());
        assertEquals("01", street.provinceCode());
        assertEquals("Số 18 Lê Văn Lương", street.street());
        assertEquals("00175", street.hint().defaultCode());
        assertEquals("00175", SplitWardPicker.code(21.0139, 105.796, street.hint().sites()));
        assertEquals("00166", SplitWardPicker.code(21.0309, 105.788, street.hint().sites()));
        assertNull(SplitWardPicker.code(10.7769, 106.7009, street.hint().sites()));

        NewLocationConverter.Outcome district = converter.resolve("Quận Cầu Giấy, Hà Nội");
        assertNull(district.match());
        assertEquals("01", district.provinceCode());

        NewLocationConverter.Match thoiLai = converter.match("xã Tân Thành, huyện Thới Lai, Cần Thơ");
        assertEquals("92", thoiLai.provinceCode());
        assertEquals("31288", thoiLai.wardCode());
        // The only "Xã Tân Thành" left in Cần Thơ came from Ngã Bảy (Hậu Giang) and joined Đại Thành.
        assertEquals("31411", converter.match("xã Tân Thành, Cần Thơ").wardCode());
        assertNull(converter.match("xã Tân Thanh, Cần Thơ"));
        assertEquals("92", converter.resolve("xã Tân Thanh, Cần Thơ").provinceCode());
    }

    @Test
    void oldProvincesMapOntoTheirSuccessor() {
        NewLocationConverter.Outcome province = converter.resolve("Bắc Giang");
        assertNull(province.match());
        assertEquals("24", province.provinceCode());

        NewLocationConverter.Match ward = converter.match("phường Dĩnh Trì, Tỉnh Bắc Giang");
        assertEquals("24", ward.provinceCode());
        assertEquals("07210", ward.wardCode());
        assertEquals("", ward.street());

        assertNull(converter.match("Bà Rịa - Vũng Tàu"));
        assertEquals("79", converter.resolve("Bà Rịa - Vũng Tàu").provinceCode());
        assertEquals("79", converter.resolve("Vũng Tàu").provinceCode());

        assertEquals("25942", converter.match("Số 1, Phường Dĩ An, Thành phố Dĩ An, Bình Dương").wardCode());
        assertEquals("25942", converter.match("Số 1, Phường Dĩ An, Thành phố Dĩ An, Hồ Chí Minh").wardCode());
        assertEquals("10525", converter.match("Phường Trần Hưng Đạo, TP Hải Dương, Hải Dương").wardCode());
    }

    @Test
    void missingProvinceIsInferredFromAUniqueTypedName() {
        NewLocationConverter.Match haDong = converter.match("Phường Hà Đông");
        assertEquals("01", haDong.provinceCode());
        assertEquals("09556", haDong.wardCode());
        // Xã Hà Đông is a 2025 commune of Hải Phòng and an old one in Thanh Hóa and Gia Lai.
        assertNull(converter.resolve("Xã Hà Đông").provinceCode());
        assertEquals("09556", converter.match("Số 10 Quang Trung, Phường Hà Đông").wardCode());
        assertEquals("27154", converter.match("Phường 1, Quận 3").wardCode());

        assertNull(converter.resolve("Hà Đông").provinceCode());
        assertNull(converter.resolve("Phường 1").provinceCode());
        assertNull(converter.resolve("Remote").provinceCode());
    }

    @Test
    void leavesIncompleteAddressesUnmapped() {
        assertNull(converter.match("106 Nguyen Van Troi, Phu Nhuan, Ho Chi Minh"));
        assertNull(converter.match("City, Ha Noi"));
    }

    @Test
    void oldDistrictWithAStreetCanBeGeocodedInsideThatDistrict() {
        NewLocationConverter.Outcome outcome = converter.resolve(
                "106 Nguyen Van Troi, Phu Nhuan, Ho Chi Minh");

        assertNull(outcome.match());
        assertEquals("79", outcome.hint().provinceCode());
        assertNull(outcome.hint().defaultCode());
        assertTrue(outcome.hint().sites().stream().anyMatch(site -> site.code().equals("27073")));
        assertEquals("27073", SplitWardPicker.code(10.7943, 106.673, outcome.hint().sites()));
        assertNull(SplitWardPicker.code(21.0285, 105.8542, outcome.hint().sites()));
    }

    @Test
    void displayUsesSeedNamesWithoutAdministrativePrefixes() {
        assertEquals(
                "Số 6 Quang Trung, Cửa Nam, Hà Nội",
                LocationMappingService.display("Số 6 Quang Trung", "Cửa Nam", "Hà Nội")
        );
        assertEquals("Hà Nội", LocationMappingService.display("", null, "Hà Nội"));
        assertEquals(
                "Số 18 Lê Văn Lương, Hà Nội",
                LocationMappingService.display("Số 18 Lê Văn Lương", null, "Hà Nội")
        );
    }
}
