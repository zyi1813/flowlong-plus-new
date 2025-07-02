package cn.yuencode.flowlongplus.workflow.handler.order;

import cn.yuencode.flowlongplus.workflow.context.BusinessOperationContext;
import cn.yuencode.flowlongplus.workflow.dto.OrderRefundFlowDto;
import cn.yuencode.flowlongplus.workflow.enums.FlwBusinessKeyEnum;
import cn.yuencode.flowlongplus.workflow.handler.BusinessOperationHandler;
import com.alibaba.fastjson.JSON;
import lombok.extern.log4j.Log4j2;
import org.springframework.stereotype.Service;

/**
 * 订单退款业务操作处理器
 * 处理订单退款流程的业务逻辑
 *
 * @author Java-Zhangyi
 * @since 1.0
 */
@Log4j2
@Service
public class OrderRefundBusinessHandler implements BusinessOperationHandler {

    @Override
    public void execute(BusinessOperationContext context) {
        try {
            log.info("开始执行订单退款业务操作，instanceId: {}, variable: {}",
                context.getInstanceId(), context.getVariable());

            // 1. 验证业务参数
            validateBusinessParams(context);

            // 2. 执行业务逻辑
            processOrderRefund(context);

            //TODO 预留冗余方法，可以根据实际情况添加更多的业务处理逻辑
//            // 3. 更新业务状态
//            updateBusinessStatus(context);
//            // 4. 发送通知
//            sendNotification(context);
//            // 5. 记录操作日志
//            recordOperationLog(context);
            // 6. 成功回调
            onSuccess(context);

            log.info("订单退款业务操作执行成功，instanceId: {}", context.getInstanceId());

        } catch (Exception e) {
            log.error("订单退款业务操作执行失败，instanceId: {}, variable: {}",
                context.getInstanceId(), context.getVariable(), e);
            // 失败回调
            onFailure(context, e);
            // 可以在这里添加失败处理逻辑，如发送告警、回滚等
            handleFailure(context, e);
        }
    }

    /**
     * 获取支持的业务类型
     *
     * @return 流程业务唯一key
     */
    @Override
    public String getSupportedBusinessType() {
        return FlwBusinessKeyEnum.ORDER_REFUND.getProcessKey(); // 订单退款的流程业务唯一key
    }

    /**
     * 成功回调
     * @param context 业务操作上下文
     */
    @Override
    public void onSuccess(BusinessOperationContext context) {
        log.info("订单退款业务操作成功回调，instanceId: {}", context.getInstanceId());

        // 可以在这里添加成功后的处理逻辑
        // 例如：发送成功通知、更新统计信息等
    }

    /**
     * 失败回调
     * @param context 业务操作上下文
     * @param exception 异常信息
     */
    @Override
    public void onFailure(BusinessOperationContext context, Exception exception) {
        log.error("订单退款业务操作失败回调，instanceId: {}, error: {}",
            context.getInstanceId(), exception.getMessage());

        // 可以在这里添加失败后的处理逻辑
        // 例如：发送失败通知、记录失败日志等
    }

    /**
     * 验证业务参数
     *
     * @param context 业务操作上下文
     */
    private void validateBusinessParams(BusinessOperationContext context) {
        if (context.getVariable() == null || context.getVariable().trim().isEmpty()) {
            throw new IllegalArgumentException("业务变量不能为空");
        }

        // 这里可以添加更多的参数验证逻辑
        log.debug("业务参数验证通过，variable: {}", context.getVariable());
    }

    /**
     * 处理订单退款业务逻辑
     *
     * @param context 业务操作上下文
     */
    private void processOrderRefund(BusinessOperationContext context) {
        //  TODO 这里实现具体的订单退款业务逻辑
        String variable = context.getVariable();
        Long instanceId = context.getInstanceId();
        log.info("处理订单退款业务逻辑，variable: {}, instanceId: {}",variable,instanceId );

        // 反序列化业务变量，获取订单退款参数
        OrderRefundFlowDto orderRefundFlowDto = JSON.parseObject(variable, OrderRefundFlowDto.class);
        log.info("订单退款业务参数：{}", orderRefundFlowDto);

        // TODO ....
    }

    /**
     * 更新业务状态
     *
     * @param context 业务操作上下文
     */
    private void updateBusinessStatus(BusinessOperationContext context) {
        // 更新订单状态为已退款
        log.info("更新订单状态为已退款，variable: {}", context.getVariable());
    }

    /**
     * 发送通知
     *
     * @param context 业务操作上下文
     */
    private void sendNotification(BusinessOperationContext context) {
        // 发送退款成功通知给用户
        log.info("发送退款成功通知，variable: {}", context.getVariable());
    }

    /**
     * 记录操作日志
     *
     * @param context 业务操作上下文
     */
    private void recordOperationLog(BusinessOperationContext context) {
        // 记录业务操作日志
        log.info("记录订单退款操作日志，instanceId: {}, variable: {}",
            context.getInstanceId(), context.getVariable());
    }

    /**
     * 处理失败情况
     *
     * @param context   业务操作上下文
     * @param exception 异常信息
     */
    private void handleFailure(BusinessOperationContext context, Exception exception) {
        // 处理失败情况，如发送告警、记录失败日志等
        log.error("处理订单退款失败情况，instanceId: {}, error: {}",
            context.getInstanceId(), exception.getMessage());

        // 可以在这里添加失败处理逻辑
        // 例如：发送告警通知、记录失败统计等
    }
}
