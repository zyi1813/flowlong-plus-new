package cn.yuencode.flowlongplus.workflow.handler;


import cn.yuencode.flowlongplus.workflow.context.BusinessOperationContext;
import cn.yuencode.flowlongplus.workflow.enums.FlwBusinessKeyEnum;

/**
 * 业务操作处理器接口
 * 策略模式的核心接口，定义业务操作的执行方法
 * 后续使用者 只需要实现该接口， 并同步修改 FlwBusinessKeyEnum@{@link  FlwBusinessKeyEnum} 枚举类，即可完成业务操作的扩展
 *
 * @author Java-Zhangyi
 * @since 1.0
 */
public interface BusinessOperationHandler {

    /**
     * 执行业务操作
     *
     * @param context 业务操作上下文
     */
    void execute(BusinessOperationContext context);

    /**
     * 获取处理器支持的业务类型
     *
     * @return 业务类型
     */
    String getSupportedBusinessType();

    /**
     * 业务操作成功回调
     *
     * @param context 业务操作上下文
     */
    default void onSuccess(BusinessOperationContext context) {
        // 默认实现，子类可以重写
    }

    /**
     * 业务操作失败回调
     *
     * @param context   业务操作上下文
     * @param exception 异常信息
     */
    default void onFailure(BusinessOperationContext context, Exception exception) {
        // 默认实现，子类可以重写
    }
}
