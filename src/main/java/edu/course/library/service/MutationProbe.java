package edu.course.library.service;

/**
 * 可选故障注入点。生产 Bean 不会制造故障；测试注入抛异常的 Bean，
 * 在多个 UPDATE/INSERT 之间故意失败，观察数据库是否完整回滚。
 */
public interface MutationProbe {
    default void afterBorrowWrites() { }
    default void afterReturnWrites() { }
}
