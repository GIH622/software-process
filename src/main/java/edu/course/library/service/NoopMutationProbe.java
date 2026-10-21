package edu.course.library.service;

import org.springframework.stereotype.Component;
/** 正式环境默认空实现；禁止在真实服务器上添加故障注入开关。 */
@Component
public class NoopMutationProbe implements MutationProbe { }
