package cn.yuencode.flowlongplus.workflow.config;

import cn.yuencode.flowlongplus.workflow.service.impl.CustomTaskActorProvider;
import com.aizuda.bpm.engine.TaskActorProvider;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Primary;

/**
 * FlowLong引擎配置类
 * 用于覆盖默认的TaskActorProvider实现
 * 并读取超级管理员白名单配置
 *
 * @author 贾小宇
 * @since 1.0
 */
@Configuration
public class FlowLongConfig {

    /**
     * 配置自定义的TaskActorProvider
     * 用于处理发起人节点的角色权限验证
     */
    @Bean
    @Primary
    public TaskActorProvider taskActorProvider(CustomTaskActorProvider customTaskActorProvider) {
        return customTaskActorProvider;
    }

} 