package com.jobbeacon.cv;

/**
 * Native text extracted from a PDF. {@code charsPerPage[i]} is the number of non-whitespace
 * characters on page {@code i + 1}.
 */
public record PdfText(String fullText, int[] charsPerPage, int pageCount) {

  public int totalChars() {
    int total = 0;
    for (int chars : charsPerPage) {
      total += chars;
    }
    return total;
  }
}
