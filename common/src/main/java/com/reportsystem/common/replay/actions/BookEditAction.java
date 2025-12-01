package com.reportsystem.common.replay.actions;

import java.util.List;

/**
 * Kitap yazma/düzenleme
 */
public class BookEditAction extends ReplayAction {

    private final String title;      // Kitap başlığı
    private final String author;     // Yazar
    private final List<String> pages; // Sayfa içerikleri
    private final boolean signed;    // İmzalanmış mı

    public BookEditAction(String title, String author, List<String> pages, boolean signed) {
        super();
        this.title = title;
        this.author = author;
        this.pages = pages;
        this.signed = signed;
    }

    public String getTitle() { return title; }
    public String getAuthor() { return author; }
    public List<String> getPages() { return pages; }
    public boolean isSigned() { return signed; }
}
