package cn.yuencode.flowlongplus.workflow.enums;

import lombok.Getter;

/**
 * FLW业务钥匙枚举
 *
 * @author sxw
 * @date 2025/05/28
 */
@Getter
public enum FlwBusinessKeyEnum {
    /**
     * 订单退款
     */
    ORDER_REFUND("ONC3I3"),
    ;

    /**
     * 流程key
     * 对应 流程定义表【flw_process.process_key】
     */
    private final String processKey;

    FlwBusinessKeyEnum(String processKey) {
        this.processKey = processKey;
    }
}
