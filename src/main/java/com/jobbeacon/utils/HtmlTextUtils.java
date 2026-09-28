package com.jobbeacon.utils;

import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;

public class HtmlTextUtils {

    private static final String NOISE_TAGS_SELECTOR = "script, style, noscript, svg";
    private static final String BLOCK_TAGS_SELECTOR = "p, div, li, ul, ol, h1, h2, h3, h4, h5, h6, tr";

    public static String toPlainText(String html) {
        if (html == null || html.isBlank()) {
            return "";
        }

        Document document = Jsoup.parse(html);
        document.select(NOISE_TAGS_SELECTOR).remove();

        document.select("br").after("\n");
        for (Element block : document.select(BLOCK_TAGS_SELECTOR)) {
            block.appendText("\n");
        }

        String text = document.wholeText();

        text = text.replaceAll("[ \\t]+", " ");
        text = text.replaceAll(" *\\n+ *", "\n");
        text = text.replaceAll("\\n{3,}", "\n\n");

        return text.strip();
    }
}
