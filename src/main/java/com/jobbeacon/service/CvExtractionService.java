package com.jobbeacon.service;

import com.jobbeacon.service.CvProfileService.CvProfileView;
import java.util.UUID;
import org.springframework.web.multipart.MultipartFile;

public interface CvExtractionService {
  CvProfileView extract(UUID userId, MultipartFile file);
}
