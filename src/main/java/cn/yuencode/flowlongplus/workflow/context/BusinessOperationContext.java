package cn.yuencode.flowlongplus.workflow.context;

import lombok.Builder;
import lombok.Data;

/**
 * 业务操作上下文
 * 用于传递业务操作的相关参数
 *
 * @author finsight
 * @since 1.0
 */
@Data
@Builder
public class BusinessOperationContext {

    /**
     * 流程实例ID
     */
    private Long instanceId;

    /**
     * 流程定义key
     */
    private String processKey;

    /**
     * 业务key
     */
//    private String businessKey;

    /**
     * 任务ID
     */
//    private Long taskId;

    /**
     * 任务key
     */
//    private String taskKey;

    /**
     * 扩展参数
     */
    private Object extraData;

    /**
     * 表单 变量json
     * 用于各自实现类 反序列化成Object对象，从中获取需要的业务参数
     */
    private String variable;

    /**
     * 创建时间
     */
    private Long createTime;

    /**
     * 构建默认上下文
     *
     * @return 业务操作上下文
     */
    public static BusinessOperationContextBuilder defaultBuilder() {
        return BusinessOperationContext.builder()
            .createTime(System.currentTimeMillis());
    }
}
