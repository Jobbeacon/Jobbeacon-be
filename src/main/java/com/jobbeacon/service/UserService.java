package com.jobbeacon.service;

import com.jobbeacon.dto.response.UserResponse;
import com.jobbeacon.entity.User;
import java.util.UUID;

public interface UserService {

  UserResponse toResponse(User user);

  UUID getUserId(String firebaseUid);
}
