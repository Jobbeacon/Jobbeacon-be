package com.jobbeacon.cv;

import java.io.IOException;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.text.PDFTextStripper;
import org.springframework.stereotype.Component;

@Component
public class PdfTextExtractor {

  public PdfText extract(PDDocument document) throws IOException {
    int pageCount = document.getNumberOfPages();
    int[] charsPerPage = new int[pageCount];
    StringBuilder fullText = new StringBuilder();
    PDFTextStripper stripper = new PDFTextStripper();
    stripper.setSortByPosition(true);
    for (int page = 1; page <= pageCount; page++) {
      stripper.setStartPage(page);
      stripper.setEndPage(page);
      String pageText = stripper.getText(document);
      charsPerPage[page - 1] = countNonWhitespace(pageText);
      fullText.append(pageText);
    }
    return new PdfText(fullText.toString(), charsPerPage, pageCount);
  }

  private static int countNonWhitespace(String text) {
    int count = 0;
    for (int i = 0; i < text.length(); i++) {
      if (!Character.isWhitespace(text.charAt(i))) {
        count++;
      }
    }
    return count;
  }
}
