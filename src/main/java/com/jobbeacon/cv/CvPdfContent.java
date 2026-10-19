package com.jobbeacon.cv;

/**
 * Result of reading an uploaded CV: raw PDF bytes (kept in memory only, for the model/OCR
 * fallback) and the native text layer.
 */
public record CvPdfContent(byte[] bytes, PdfText text) {
}
