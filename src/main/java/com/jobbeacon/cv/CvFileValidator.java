package com.jobbeacon.cv;

import com.jobbeacon.exception.CvExtractionException;
import java.nio.charset.StandardCharsets;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.util.unit.DataSize;
import org.springframework.web.multipart.MultipartFile;

@Component
public class CvFileValidator {

  private static final byte[] PDF_MAGIC = "%PDF-".getBytes(StandardCharsets.US_ASCII);
  private static final int HEADER_SEARCH_LIMIT = 1024;

  private final long maxFileSizeBytes;
  private final int maxPages;

  public CvFileValidator(
      @Value("${app.cv.max-file-size:10MB}") DataSize maxFileSize,
      @Value("${app.cv.max-pages:20}") int maxPages
  ) {
    this.maxFileSizeBytes = maxFileSize.toBytes();
    this.maxPages = maxPages;
  }

  public void validateUpload(MultipartFile file) {
    if (file == null || file.isEmpty()) {
      throw CvExtractionException.invalidFileType();
    }
    validateSize(file.getSize());
  }

  public void validateSize(long size) {
    if (size > maxFileSizeBytes) {
      throw CvExtractionException.fileTooLarge();
    }
  }

  /**
   * Accepts the {@code %PDF-} header anywhere in the first 1024 bytes, like Acrobat and PDFBox:
   * valid files may start with a BOM, whitespace or junk bytes added by exporters.
   */
  public void validateMagicBytes(byte[] content) {
    if (content == null) {
      throw CvExtractionException.invalidFileType();
    }
    int lastStart = Math.min(content.length, HEADER_SEARCH_LIMIT) - PDF_MAGIC.length;
    for (int start = 0; start <= lastStart; start++) {
      if (startsWithMagic(content, start)) {
        return;
      }
    }
    throw CvExtractionException.invalidFileType();
  }

  private static boolean startsWithMagic(byte[] content, int start) {
    for (int i = 0; i < PDF_MAGIC.length; i++) {
      if (content[start + i] != PDF_MAGIC[i]) {
        return false;
      }
    }
    return true;
  }

  public void validateDocument(PDDocument document) {
    if (document.isEncrypted()) {
      throw CvExtractionException.passwordProtected();
    }
    if (document.getNumberOfPages() > maxPages) {
      throw CvExtractionException.tooManyPages();
    }
  }
}
