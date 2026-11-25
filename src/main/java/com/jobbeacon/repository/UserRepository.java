package com.jobbeacon.repository;

import com.jobbeacon.constant.UserRole;
import com.jobbeacon.constant.UserStatus;
import com.jobbeacon.entity.User;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.stereotype.Repository;

import java.util.Optional;
import java.util.UUID;

@Repository
public interface UserRepository extends JpaRepository<User, UUID>, JpaSpecificationExecutor<User> {

  Optional<User> findByEmail(String email);

  Optional<User> findByFirebaseUid(String firebaseUid);

  long countByRole(UserRole role);

  long countByStatus(UserStatus status);

  long countByRoleAndStatusAndIdNot(UserRole role, UserStatus status, UUID id);
}
