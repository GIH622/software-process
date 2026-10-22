package edu.course.library.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

/** 所有可配置业务值集中维护，不在 Service 内散落“魔法数字”。 */
@Component
public class LibrarySettings {
    @Value("${library.default-borrow-days:30}") public int borrowDays;
    @Value("${library.overdue-freeze-days:35}") public int freezeDays;
    @Value("${library.admin-account:admin}") public String adminAccount;
    @Value("${library.admin-password:}") public String adminPassword;
    @Value("${library.demo-reader-password:}") public String demoReaderPassword;
    @Value("${library.renewal-enabled:false}") public boolean renewalEnabled;
    @Value("${library.renewal-days:15}") public int renewalDays;
    @Value("${library.max-renewals:1}") public int maxRenewals;
}
