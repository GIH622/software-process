package edu.course.library.model;

import java.time.LocalDate;
import java.time.LocalDateTime;

/** 一次借阅完整生命周期。BORROWED 转 RETURNED 后历史记录仍要保留。 */
public record Loan(long id, long readerId, long bookId, LocalDateTime borrowTime,
                   LocalDate dueDate, LocalDateTime returnTime, int overdueDays, String status) { }
