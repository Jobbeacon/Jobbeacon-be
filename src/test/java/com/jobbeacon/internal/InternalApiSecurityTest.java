package com.jobbeacon.internal;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.google.firebase.auth.FirebaseAuth;
import com.jobbeacon.dto.internal.InternalGraphJobResponse;
import com.jobbeacon.repository.UserRepository;
import com.jobbeacon.security.SecurityConfiguration;
import com.jobbeacon.service.UserSyncService;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Import;
import org.springframework.data.jpa.mapping.JpaMetamodelMappingContext;
import org.springframework.http.MediaType;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.MockMvc;

@WebMvcTest({InternalGraphController.class, InternalUserController.class})
@Import(SecurityConfiguration.class)
@TestPropertySource(properties = "app.internal.token=secret-token")
class InternalApiSecurityTest {

  private static final UUID JOB = UUID.randomUUID();

  @Autowired
  private MockMvc mvc;
  @MockBean
  private InternalGraphService internalGraphService;
  @MockBean
  private UserRepository userRepository;
  @MockBean
  private FirebaseAuth firebaseAuth;
  @MockBean
  private UserSyncService userSyncService;
  // The application class enables JPA auditing, which a web slice cannot satisfy without this.
  @MockBean
  private JpaMetamodelMappingContext jpaMappingContext;

  @Test
  void callWithTheSharedTokenGetsSnakeCaseBody() throws Exception {
    when(internalGraphService.findJobs(any())).thenReturn(List.of(new InternalGraphJobResponse(
        JOB, "Backend", null, null, Instant.parse("2026-11-01T00:00:00Z"), null,
        List.of(new InternalGraphJobResponse.Skill(UUID.randomUUID(), "Java", "java")), List.of())));

    mvc.perform(jobsRequest().header(InternalTokenFilter.HEADER, "secret-token"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$[0].id").value(JOB.toString()))
        .andExpect(jsonPath("$[0].posted_date").value("2026-11-01T00:00:00Z"))
        .andExpect(jsonPath("$[0].skills[0].normalized_name").value("java"));
  }

  @Test
  void callWithoutOrWithAWrongTokenIsRejected() throws Exception {
    mvc.perform(jobsRequest()).andExpect(status().is4xxClientError());
    mvc.perform(jobsRequest().header(InternalTokenFilter.HEADER, "wrong")).andExpect(status().is4xxClientError());
    mvc.perform(get("/internal/v1/graph/job-ids").param("limit", "10")).andExpect(status().is4xxClientError());

    verifyNoInteractions(internalGraphService);
  }

  @Test
  void tooLargePagesAreRejected() throws Exception {
    when(internalGraphService.findJobIds(any(), anyInt())).thenReturn(List.of());

    mvc.perform(get("/internal/v1/graph/job-ids").param("limit", "100000")
            .header(InternalTokenFilter.HEADER, "secret-token"))
        .andExpect(status().is4xxClientError());
  }

  @Test
  void unknownFirebaseUserIsNotFound() throws Exception {
    when(userRepository.findByFirebaseUid("nobody")).thenReturn(java.util.Optional.empty());

    mvc.perform(get("/internal/v1/users/by-firebase-uid/nobody").header(InternalTokenFilter.HEADER, "secret-token"))
        .andExpect(status().isNotFound());
  }

  private static org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder jobsRequest() {
    return post("/internal/v1/graph/jobs")
        .contentType(MediaType.APPLICATION_JSON)
        .content("{\"ids\": [\"" + JOB + "\"]}");
  }
}
