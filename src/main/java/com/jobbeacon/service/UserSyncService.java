package com.jobbeacon.service;

import com.google.firebase.auth.FirebaseToken;
import com.jobbeacon.entity.User;

public interface UserSyncService {

  User syncFromFirebaseToken(FirebaseToken firebaseToken);

  User getCurrentUser();
}
