package cn.yuencode.flowlongplus.workflow.dto;

import lombok.Data;

import java.io.Serializable;
import java.math.BigDecimal;

/**
 * 订单退款流程 Dto
 *
 * @author Java-Zhangyi
 * @since 1.0
 */
@Data
public class OrderRefundFlowDto implements Serializable {
    private static final long serialVersionUID = 1L;
    /**
     * 订单ID
     */
    private String orderId;
    /**
     * 订单名称
     */
    private String orderName;
    /**
     * 退款金额
     */
    private BigDecimal payAmount;

    /**
     * 退款原因
     */
    private String refundReason;

    /**
     * 【冗余字段，按需取用】
     * 商户退款单号 说明：商户系统内部的退款单号，商户系统内部唯一，只能是数字、大小写字母_-|*@ ，同一退款单号多次请求只退一笔。
     */

    private String outRefundNo;

    /**
     * 【冗余字段，按需取用】
     * 微信支付订单号 说明：微信支付交易订单号
     */
    private String transactionId;

    /**
     * 【冗余字段，按需取用】
     * 商户订单号 说明：原支付交易对应的商户订单号
     */
    private String outTradeNo;
}
