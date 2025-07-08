package cn.yuencode.flowlongplus.workflow.config;

import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * 读取超级管理员白名单配置
 */

@Component
@ConfigurationProperties(prefix = "workflow")
//@RefreshScope
@Getter
@Setter
public class WorkflowProperties {
    /**
     * 超级管理员白名单
     */
    private List<String> superAdminWhitelist;

}
