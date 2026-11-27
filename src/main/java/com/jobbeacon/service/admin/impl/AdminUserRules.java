package com.jobbeacon.service.admin.impl;

import com.jobbeacon.constant.UserRole;
import com.jobbeacon.constant.UserStatus;

/** Invariants for admin edits of user accounts. */
final class AdminUserRules {

  private AdminUserRules() {
  }

  static boolean isActiveAdmin(UserRole role, UserStatus status) {
    return role == UserRole.ADMIN && status == UserStatus.ACTIVE;
  }

  /**
   * True when changing an account from (currentRole, currentStatus) to (nextRole, nextStatus) would
   * leave no active administrator, given how many other active admins exist.
   */
  static boolean removesLastActiveAdmin(
      UserRole currentRole, UserStatus currentStatus,
      UserRole nextRole, UserStatus nextStatus,
      long otherActiveAdmins
  ) {
    return isActiveAdmin(currentRole, currentStatus)
        && !isActiveAdmin(nextRole, nextStatus)
        && otherActiveAdmins == 0;
  }
}
