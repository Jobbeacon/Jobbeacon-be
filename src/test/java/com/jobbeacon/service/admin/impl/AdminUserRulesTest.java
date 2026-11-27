package com.jobbeacon.service.admin.impl;

import com.jobbeacon.constant.UserRole;
import com.jobbeacon.constant.UserStatus;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class AdminUserRulesTest {

  @Test
  void blocksDemotingOrLockingTheLastActiveAdmin() {
    assertTrue(AdminUserRules.removesLastActiveAdmin(
        UserRole.ADMIN, UserStatus.ACTIVE, UserRole.USER, UserStatus.ACTIVE, 0));
    assertTrue(AdminUserRules.removesLastActiveAdmin(
        UserRole.ADMIN, UserStatus.ACTIVE, UserRole.ADMIN, UserStatus.SUSPENDED, 0));
    assertTrue(AdminUserRules.removesLastActiveAdmin(
        UserRole.ADMIN, UserStatus.ACTIVE, UserRole.ADMIN, UserStatus.DELETED, 0));
  }

  @Test
  void allowsChangesWhileAnotherActiveAdminRemains() {
    assertFalse(AdminUserRules.removesLastActiveAdmin(
        UserRole.ADMIN, UserStatus.ACTIVE, UserRole.USER, UserStatus.ACTIVE, 1));
  }

  @Test
  void ignoresAccountsThatAreNotActiveAdmins() {
    assertFalse(AdminUserRules.removesLastActiveAdmin(
        UserRole.USER, UserStatus.ACTIVE, UserRole.USER, UserStatus.SUSPENDED, 0));
    assertFalse(AdminUserRules.removesLastActiveAdmin(
        UserRole.ADMIN, UserStatus.SUSPENDED, UserRole.USER, UserStatus.SUSPENDED, 0));
    assertFalse(AdminUserRules.removesLastActiveAdmin(
        UserRole.ADMIN, UserStatus.ACTIVE, UserRole.ADMIN, UserStatus.ACTIVE, 0));
  }
}
