package com.jobbeacon.controller.internal;

import com.jobbeacon.dto.internal.InternalUserResponse;
import com.jobbeacon.exception.base.NotFoundException;
import com.jobbeacon.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** Lets other services, which verify Firebase tokens themselves, find the backend user behind a token. */
@RestController
@RequestMapping("/internal/v1/users")
@RequiredArgsConstructor
public class InternalUserController {

  static final String USER_NOT_FOUND = "user_not_found";

  private final UserRepository userRepository;

  /** 404 until the user's first authenticated backend call, which is what creates the user. */
  @GetMapping("/by-firebase-uid/{firebaseUid}")
  public InternalUserResponse findByFirebaseUid(@PathVariable String firebaseUid) {
    return userRepository.findByFirebaseUid(firebaseUid)
        .map(user -> new InternalUserResponse(user.getId()))
        .orElseThrow(() -> new NotFoundException(USER_NOT_FOUND));
  }
}
