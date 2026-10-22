package edu.course.library.model;

/** reader_account 行模型：passwordHash 一律为 BCrypt 哈希，不向客户端返回。 */
public record Reader(long id, String cardNo, String name, String passwordHash,
                     String status, int borrowedCount, int borrowLimit) { }
