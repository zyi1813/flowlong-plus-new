package cn.yuencode.flowlongplus.workflow.controller.req;

import lombok.Data;


/**
 *  重新提交 流程实例 请求对象
 * @author  Java-Zhangyi
 */
@Data
public class ResubmitProcessReq {
    /**
     * 流程实例ID
     */
    private String instanceId;
    /**
     *  表单数据
     */
    private Object formData;
} 