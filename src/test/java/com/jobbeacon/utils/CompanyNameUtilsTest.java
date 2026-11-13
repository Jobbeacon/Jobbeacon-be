package com.jobbeacon.utils;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class CompanyNameUtilsTest {

  @Test
  void keyDropsLegalFormsAccentsAndCountry() {
    assertEquals("fpt software", CompanyNameUtils.key("CÔNG TY TNHH FPT Software"));
    assertEquals("fpt software", CompanyNameUtils.key("FPT Software Việt Nam"));
    assertEquals("fpt software", CompanyNameUtils.key("FPT Software Co., Ltd"));
    assertEquals("dau tu ha noi", CompanyNameUtils.key("Công ty Cổ phần Đầu tư Hà Nội"));
  }

  @Test
  void keyFallsBackToFoldedNameWhenOnlyNoiseRemains() {
    assertEquals("cong ty tnhh", CompanyNameUtils.key("Công ty TNHH"));
  }

  @Test
  void likelySameMatchesEqualKeysAndDistinctiveContainment() {
    assertTrue(CompanyNameUtils.likelySame("fpt software", "fpt software"));
    assertTrue(CompanyNameUtils.likelySame("vng", "vng"));
    assertTrue(CompanyNameUtils.likelySame("momo", "momo online mobile services"));
    assertFalse(CompanyNameUtils.likelySame("abc", "abc def"));
    assertFalse(CompanyNameUtils.likelySame("fpt", "fpt telecom"));
    assertFalse(CompanyNameUtils.likelySame("viettel", "vietcombank"));
    assertFalse(CompanyNameUtils.likelySame("", "abc"));
  }
}
