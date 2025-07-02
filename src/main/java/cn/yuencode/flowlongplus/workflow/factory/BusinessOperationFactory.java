package cn.yuencode.flowlongplus.workflow.factory;

import cn.yuencode.flowlongplus.workflow.enums.FlwBusinessKeyEnum;
import cn.yuencode.flowlongplus.workflow.handler.BusinessOperationHandler;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import javax.annotation.PostConstruct;
import javax.annotation.Resource;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * 业务操作工厂类
 * 工厂方法模式，根据业务类型获取对应的处理器
 *
 * @author Java-Zhangyi
 * @since 1.0
 */
@Slf4j
@Component
public class BusinessOperationFactory {

    private static final Map<String, BusinessOperationHandler> HANDLER_MAP = new HashMap<>();

    @Resource
    private List<BusinessOperationHandler> businessOperationHandlers;

    /**
     * 初始化处理器映射
     */
    @PostConstruct
    public void init() {
        if (businessOperationHandlers != null) {
            for (BusinessOperationHandler handler : businessOperationHandlers) {
                String businessType = handler.getSupportedBusinessType();
                if (businessType != null && !businessType.trim().isEmpty()) {
                    HANDLER_MAP.put(businessType, handler);
                }
            }
        }
        log.info("业务操作工厂初始化完成，共注册 {} 个处理器", HANDLER_MAP.size());
    }

    /**
     * 根据业务key枚举获取处理器
     *
     * @param businessKeyEnum 业务key枚举
     * @return 业务操作处理器
     */
    public static BusinessOperationHandler getHandler(FlwBusinessKeyEnum businessKeyEnum) {
        if (businessKeyEnum == null) {
            return null;
        }
        return getHandler(businessKeyEnum.getProcessKey());
    }

    /**
     * 根据业务类型获取处理器
     *
     * @param businessType 业务类型
     * @return 业务操作处理器
     */
    public static BusinessOperationHandler getHandler(String businessType) {
        if (businessType == null || businessType.trim().isEmpty()) {
            return null;
        }
        return HANDLER_MAP.get(businessType.trim());
    }

    /**
     * 注册处理器
     *
     * @param businessType 业务类型
     * @param handler 处理器
     */
    public static void registerHandler(String businessType, BusinessOperationHandler handler) {
        if (businessType != null && handler != null) {
            HANDLER_MAP.put(businessType, handler);
            log.info("手动注册业务操作处理器: {}", businessType);
        }
    }

    /**
     * 获取所有已注册的业务类型
     *
     * @return 业务类型集合
     */
    public static java.util.Set<String> getRegisteredBusinessTypes() {
        return HANDLER_MAP.keySet();
    }
}
