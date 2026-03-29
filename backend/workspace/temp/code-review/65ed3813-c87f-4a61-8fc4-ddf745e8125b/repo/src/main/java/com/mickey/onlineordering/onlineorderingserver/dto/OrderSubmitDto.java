package com.mickey.onlineordering.onlineorderingserver.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import jakarta.validation.constraints.NotBlank;
import java.io.Serializable;

/**
 * 订单提交DTO
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class OrderSubmitDto implements Serializable {
    
    private static final long serialVersionUID = 1L;

    @NotBlank(message = "收货地址不能为空")
    private String address;

    @NotBlank(message = "收货人姓名不能为空")
    private String receiverName;

    @NotBlank(message = "收货人手机号不能为空")
    private String receiverPhone;

    private String remark;

    @NotBlank(message = "支付方式不能为空")
    private String paymentMethod;

    private String deliveryTime;
    private Boolean saveAsDefault;
}












