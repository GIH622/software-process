package edu.course.library.model;

/** 真实库兼容模型：原字段名为 password_md5，现存储 MD5 或升级后的 BCrypt，不向客户端返回。 */
public record Reader(long id, String cardNo, String name, String passwordHash,
                     String status, int borrowedCount, int borrowLimit) { }
