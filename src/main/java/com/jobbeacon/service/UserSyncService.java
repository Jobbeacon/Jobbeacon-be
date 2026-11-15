package com.jobbeacon.service;

import com.jobbeacon.entity.User;
import com.jobbeacon.security.GatewayPrincipal;

public interface UserSyncService {

  User syncFromPrincipal(GatewayPrincipal principal);
}
