package com.jobbeacon.service;

import com.jobbeacon.dto.response.UserResponse;
import com.jobbeacon.entity.User;

public interface UserService {

  UserResponse toResponse(User user);
}
