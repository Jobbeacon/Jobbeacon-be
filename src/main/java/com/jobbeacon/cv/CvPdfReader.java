package com.jobbeacon.cv;

import com.jobbeacon.exception.CvExtractionException;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.util.UUID;
import lombok.extern.slf4j.Slf4j;
import org.apache.pdfbox.Loader;
import org.apache.pdfbox.io.MemoryUsageSetting;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.encryption.InvalidPasswordException;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import org.springframework.web.multipart.MultipartFile;

/**
 * Reads an uploaded CV: validates it, writes it to a UUID-named temp file (never the original
 * filename), loads it with capped PDFBox memory and extracts the native text. The temp file is
 * always deleted. Never logs CV content.
 */
@Slf4j
@Component
public class CvPdfReader {

  private static final long MAX_MAIN_MEMORY_BYTES = 16L * 1024 * 1024;
  private static final long MAX_STORAGE_BYTES = 256L * 1024 * 1024;

  private final CvFileValidator validator;
  private final PdfTextExtractor textExtractor;
  private final Path tempDir;

  @Autowired
  public CvPdfReader(CvFileValidator validator, PdfTextExtractor textExtractor) {
    this(validator, textExtractor, Path.of(System.getProperty("java.io.tmpdir")));
  }

  CvPdfReader(CvFileValidator validator, PdfTextExtractor textExtractor, Path tempDir) {
    this.validator = validator;
    this.textExtractor = textExtractor;
    this.tempDir = tempDir;
  }

  public CvPdfContent read(MultipartFile file) {
    validator.validateUpload(file);
    byte[] bytes;
    try {
      bytes = file.getBytes();
    } catch (IOException e) {
      throw CvExtractionException.unreadable(e);
    }
    return read(bytes);
  }

  public CvPdfContent read(byte[] bytes) {
    long start = System.currentTimeMillis();
    validator.validateSize(bytes.length);
    validator.validateMagicBytes(bytes);
    Path tempFile = tempDir.resolve(UUID.randomUUID() + ".pdf");
    try {
      Files.write(tempFile, bytes, StandardOpenOption.CREATE_NEW, StandardOpenOption.WRITE);
      try (PDDocument document = Loader.loadPDF(tempFile.toFile(),
          MemoryUsageSetting.setupMixed(MAX_MAIN_MEMORY_BYTES, MAX_STORAGE_BYTES).streamCache)) {
        validator.validateDocument(document);
        PdfText text = textExtractor.extract(document);
        log.info("(read) stage: pdf_text, status: ok, pages: {}, chars: {}, tookMs: {}",
            text.pageCount(), text.totalChars(), System.currentTimeMillis() - start);
        return new CvPdfContent(bytes, text);
      }
    } catch (InvalidPasswordException e) {
      log.info("(read) stage: pdf_load, status: password_protected");
      throw CvExtractionException.passwordProtected();
    } catch (CvExtractionException e) {
      log.info("(read) stage: pdf_validate, status: rejected, errorCode: {}", e.getCode());
      throw e;
    } catch (IOException | RuntimeException e) {
      log.warn("(read) stage: pdf_load, status: unreadable, error: {}", e.getClass().getSimpleName());
      throw CvExtractionException.unreadable(e);
    } finally {
      deleteQuietly(tempFile);
    }
  }

  private static void deleteQuietly(Path path) {
    try {
      Files.deleteIfExists(path);
    } catch (IOException e) {
      log.warn("(deleteQuietly) stage: temp_cleanup, status: failed, error: {}",
          e.getClass().getSimpleName());
    }
  }
}
