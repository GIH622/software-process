package edu.course.library.model;

/** 每个 barcode 指向一册可借出的实体书；同一 ISBN 可有多个 barcode。 */
public record BookCopy(long id, String barcode, String isbn, String title,
                       String author, String category, String location, String status) { }
