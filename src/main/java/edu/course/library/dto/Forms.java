package edu.course.library.dto;

import jakarta.validation.constraints.*;

/** 统一存放 HTTP 请求/响应 DTO，避免把数据库实体（含密码）直接暴露给前端。 */
public final class Forms {
    private Forms() { }
    public record Login(@NotBlank String account, @NotBlank String password) { }
    public record LoginInfo(String role, String account, String name, String csrfToken) { }
    public record Borrow(@NotBlank String barcode, @NotBlank String readerCardNo) { }
    public record Returned(@NotBlank String barcode) { }
    public record RegisterReader(
        // 需求存在“6~12位”和既有数据1001的冲突：暂兼容4~12位，参见差异清单 D-04。
        @NotBlank @Pattern(regexp = "[0-9]{4,12}") String cardNo,
        @NotBlank String name,
        @NotBlank @Size(min=8, max=128) String password,
        @Min(1) @Max(50) int borrowLimit) { }
    public record AddBook(@NotBlank String barcode,
        @NotBlank @Pattern(regexp="[0-9Xx-]{10,20}") String isbn,
        @NotBlank String title, String author, String category, String location) { }
    public record Receipt(long recordId, String readerCardNo, String barcode, String bookTitle,
        String status, java.time.LocalDate borrowDate, java.time.LocalDate dueDate,
        java.time.LocalDate returnDate, int overdueDays, String readerStatus) { }
}
