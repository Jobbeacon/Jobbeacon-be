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
    }
}
