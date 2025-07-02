package cn.yuencode.flowlongplus.workflow.config;

import cn.yuencode.flowlongplus.workflow.service.impl.ClaimAutoTaskAccessStrategy;
import com.aizuda.bpm.engine.impl.GeneralAccessStrategy;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Primary;

/**
 * FlowLong 扩展配置类
 * 用于配置自定义的访问策略和引擎行为
 */
@Configuration
public class FlowLongExtConfig {


    /**
     * 配置 FlowLongEngine 使用自定义访问策略  用于处理角色和部门类型的任务自动认领
     * 通过 @Primary 注解确保 Spring 优先使用我们的自定义策略
     */
    @Bean
    @Primary
    public GeneralAccessStrategy generalAccessStrategy() {
        return new ClaimAutoTaskAccessStrategy();
    }
}
