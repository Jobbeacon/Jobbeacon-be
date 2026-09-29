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
    }

    @Test
    void dividedWardUsesTheLibraryDefaultAndSquare() {
        NewLocationConverter.Match yenHoa = converter.match("Phường Yên Hòa, Hà Nội");
        assertEquals("01", yenHoa.provinceCode());
        assertEquals("00175", yenHoa.wardCode());
        assertEquals("", yenHoa.street());

        NewLocationConverter.Outcome street = converter.resolve("Số 18 Lê Văn Lương, Phường Yên Hòa, Hà Nội");
        assertNull(street.match());
        assertEquals("01", street.provinceCode());
        assertEquals("Số 18 Lê Văn Lương", street.street());
        assertEquals("00175", street.hint().defaultCode());
        assertEquals("00175", SplitWardPicker.code(21.0139, 105.796, street.hint().sites()));
        assertEquals("00166", SplitWardPicker.code(21.0309, 105.788, street.hint().sites()));

        NewLocationConverter.Outcome district = converter.resolve("Quận Cầu Giấy, Hà Nội");
        assertNull(district.match());
        assertEquals("01", district.provinceCode());

        NewLocationConverter.Match thoiLai = converter.match("xã Tân Thành, huyện Thới Lai, Cần Thơ");
        assertEquals("92", thoiLai.provinceCode());
        assertEquals("31288", thoiLai.wardCode());
        assertNull(converter.match("xã Tân Thành, Cần Thơ"));
        assertEquals("92", converter.resolve("xã Tân Thành, Cần Thơ").provinceCode());
    }

    @Test
    void explicitPhuongCauGiayIsTheCurrentWard() {
        NewLocationConverter.Match cauGiay = converter.match("Số 5 Duy Tân, Phường Cầu Giấy, Hà Nội");
        assertEquals("01", cauGiay.provinceCode());
        assertEquals("00166", cauGiay.wardCode());
        assertEquals("Số 5 Duy Tân", cauGiay.street());
    }

    @Test
    void oldProvinceBacGiangMapsToBacNinh() {
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
    }

    @Test
    void leavesSplitOrIncompleteAddressesUnmapped() {
        assertNull(converter.match("Tòa nhà MB, số 18 Lê Văn Lương, Phường Yên Hòa, Ha Noi"));
        assertNull(converter.match("106 Nguyen Van Troi, Phu Nhuan, Ho Chi Minh"));
        assertNull(converter.match("City, Ha Noi"));
    }

    @Test
    void splitDistrictWithAStreetCanBeGeocodedInsideThatDistrict() {
        NewLocationConverter.Outcome outcome = converter.resolve(
                "106 Nguyen Van Troi, Phu Nhuan, Ho Chi Minh");

        assertNull(outcome.match());
        assertEquals("79", outcome.hint().provinceCode());
        assertTrue(outcome.hint().allowed().stream().anyMatch(ward -> ward.code().equals("27073")));
        assertEquals(
                "27073",
                WardPicker.code(java.util.List.of("Phường Phú Nhuận"), outcome.hint().allowed())
        );
        assertNull(WardPicker.code(java.util.List.of("Phường Bình Thạnh"), outcome.hint().allowed()));
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
