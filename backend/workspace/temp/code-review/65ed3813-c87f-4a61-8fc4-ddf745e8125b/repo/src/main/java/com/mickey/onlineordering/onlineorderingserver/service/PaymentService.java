package com.mickey.onlineordering.onlineorderingserver.service;

import com.google.zxing.BarcodeFormat;
import com.google.zxing.EncodeHintType;
import com.google.zxing.client.j2se.MatrixToImageWriter;
import com.google.zxing.common.BitMatrix;
import com.google.zxing.qrcode.QRCodeWriter;
import com.google.zxing.qrcode.decoder.ErrorCorrectionLevel;
import com.mickey.onlineordering.onlineorderingserver.config.WxPayConfig;
import com.mickey.onlineordering.onlineorderingserver.dto.PaymentCreateDto;
import com.mickey.onlineordering.onlineorderingserver.entity.Order;
import com.mickey.onlineordering.onlineorderingserver.exception.BizException;
import com.mickey.onlineordering.onlineorderingserver.mapper.OrderMapper;
import com.mickey.onlineordering.onlineorderingserver.vo.PaymentInfoVo;
import com.mickey.onlineordering.onlineorderingserver.vo.PaymentStatusVo;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Base64;
import java.util.HashMap;
import java.util.Map;

/**
 * 支付服务
 * 处理支付相关业务逻辑
 * 集成真实的微信支付 APIv3
 */
@Slf4j
@Service
public class PaymentService {
    
    @Autowired
    private OrderMapper orderMapper;
    
    @Autowired(required = false)
    private WxPayService wxPayService;
    
    @Autowired(required = false)
    private WxPayConfig wxPayConfig;
    
    @Autowired(required = false)
    private AlipayService alipayService;

    private static final DateTimeFormatter FORMATTER = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");
    
    /**
     * 创建支付订单（生成二维码）
     * 优先使用真实的微信支付 APIv3，如果不可用则使用模拟支付
     */
    public PaymentInfoVo createPayment(PaymentCreateDto dto) {
        log.info("========================================");
        log.info("创建支付订单，订单ID: {}, 支付方式: {}", dto.getOrderId(), dto.getPaymentType());
        log.info("========================================");
        
        // 1. 查询订单信息
        Order order = orderMapper.selectById(dto.getOrderId());
        if (order == null) {
            throw new BizException(400, "订单不存在");
        }
        
        // 2. 检查订单状态
        if (order.getStatus() != 0) {
            throw new BizException(400, "订单状态异常，无法支付");
        }
        
        // 3. 生成支付二维码
        String paymentUrl;
        String qrCodeBase64;
        
        // WeChat：优先使用真实的微信支付 APIv3
        if ("wechat".equals(dto.getPaymentType()) && wxPayService != null && wxPayConfig != null
                && wxPayConfig.getMerchantPrivateKey() != null) {
            log.info("✅ 使用真实的微信支付 APIv3");
            try {
                paymentUrl = wxPayService.nativePay(dto.getOrderId());
                log.info("✅ 微信支付下单成功，二维码链接: {}", paymentUrl);
            } catch (Exception e) {
                log.error("❌ 微信支付下单失败，降级为模拟支付", e);
                paymentUrl = generatePaymentUrl(order.getOrderNo(), dto.getPaymentType());
            }
        }
        // Alipay：使用沙箱扫码支付（trade_precreate -> qr_code）
        else if ("alipay".equals(dto.getPaymentType()) && alipayService != null) {
            log.info("✅ 使用支付宝沙箱扫码支付 (trade_precreate)");
            try {
                paymentUrl = alipayService.precreate(dto.getOrderId());
                log.info("✅ 支付宝预下单成功，二维码链接: {}", paymentUrl);
            } catch (Exception e) {
                log.error("❌ 支付宝预下单失败，降级为模拟支付", e);
                paymentUrl = generatePaymentUrl(order.getOrderNo(), dto.getPaymentType());
            }
        }
        // 其他：模拟支付
        else {
            log.warn("⚠️  支付方式未配置或不可用，使用模拟支付");
            paymentUrl = generatePaymentUrl(order.getOrderNo(), dto.getPaymentType());
        }
        
        // 4. 生成二维码图片
        qrCodeBase64 = generateQRCode(paymentUrl);
        
        // 5. 计算过期时间（5分钟后）
        LocalDateTime expireTime = LocalDateTime.now().plusMinutes(5);
        
        // 6. 构建返回数据
        PaymentInfoVo result = PaymentInfoVo.builder()
                .orderId(order.getId())
                .orderNo(order.getOrderNo())
                .amount(order.getTotalAmount().doubleValue())
                .paymentType(dto.getPaymentType())
                .qrCode(qrCodeBase64)
                .expireTime(expireTime.format(FORMATTER))
                .build();
        
        log.info("✅ 支付订单创建完成");
        
        return result;
    }
    
    /**
     * 查询支付状态
     * 如果配置了微信支付，会主动查询微信支付订单状态（兜底一致性）
     */
    public PaymentStatusVo checkPaymentStatus(Long orderId) {
        Order order = orderMapper.selectById(orderId);
        if (order == null) {
            throw new BizException(400, "订单不存在");
        }
        
        // 如果订单已支付，直接返回
        if (order.getStatus() == 1) {
            return PaymentStatusVo.builder()
                    .orderId(orderId)
                    .isPaid(true)
                    .payTime(order.getPayTime() != null ? order.getPayTime().format(FORMATTER) : null)
                    .build();
        }
        
        // 如果订单未支付且配置了微信支付，主动查询微信支付状态（兜底）
        if ("wechat".equals(order.getPaymentMethod()) && wxPayService != null 
                && wxPayConfig != null && wxPayConfig.getMerchantPrivateKey() != null) {
            try {
                log.info("主动查询微信支付订单状态，订单号: {}", order.getOrderNo());
                Map<String, Object> queryResult = wxPayService.queryOrder(order.getOrderNo());
                
                if ((Boolean) queryResult.get("success")) {
                    String tradeState = (String) queryResult.get("trade_state");
                    
                    // 如果微信支付状态为 SUCCESS，但本地状态未更新，说明回调可能丢失
                    if ("SUCCESS".equals(tradeState) && order.getStatus() == 0) {
                        log.warn("⚠️  检测到微信支付成功但本地状态未更新，可能是回调丢失，立即更新订单状态");
                        order.setStatus(1);
                        order.setPayTime(LocalDateTime.now());
                        order.setUpdateTime(LocalDateTime.now());
                        orderMapper.update(order);
                        log.info("✅ 微信订单状态已同步更新");
                    }
                    
                    return PaymentStatusVo.builder()
                            .orderId(orderId)
                            .isPaid("SUCCESS".equals(tradeState))
                            .payTime(order.getPayTime() != null ? order.getPayTime().format(FORMATTER) : null)
                            .build();
                }
            } catch (Exception e) {
                log.error("查询微信支付订单状态失败", e);
                // 查询失败，降级使用本地状态
            }
        }
        
        // 如果订单未支付且配置了支付宝，主动查询支付宝支付状态（兜底）
        if ("alipay".equals(order.getPaymentMethod()) && alipayService != null) {
            try {
                log.info("主动查询支付宝订单状态，订单号: {}", order.getOrderNo());
                Map<String, Object> queryResult = alipayService.queryOrder(order.getOrderNo());
                
                if ((Boolean) queryResult.get("success")) {
                    String tradeStatus = (String) queryResult.get("trade_status");
                    
                    // 如果支付宝状态为 TRADE_SUCCESS/TRADE_FINISHED，但本地状态未更新
                    if (("TRADE_SUCCESS".equals(tradeStatus) || "TRADE_FINISHED".equals(tradeStatus)) 
                            && order.getStatus() == 0) {
                        log.warn("⚠️  检测到支付宝支付成功但本地状态未更新，可能是回调丢失，立即更新订单状态");
                        // 直接更新订单状态（因为支付宝已确认支付成功）
                        order.setStatus(1);
                        order.setPayTime(LocalDateTime.now());
                        order.setUpdateTime(LocalDateTime.now());
                        orderMapper.update(order);
                        log.info("✅ 支付宝订单状态已同步更新");
                    }
                    
                    return PaymentStatusVo.builder()
                            .orderId(orderId)
                            .isPaid("TRADE_SUCCESS".equals(tradeStatus) || "TRADE_FINISHED".equals(tradeStatus))
                            .payTime(order.getPayTime() != null ? order.getPayTime().format(FORMATTER) : null)
                            .build();
                }
            } catch (Exception e) {
                log.error("查询支付宝订单状态失败", e);
                // 查询失败，降级使用本地状态
            }
        }
        
        // 返回本地状态
        boolean isPaid = order.getStatus() == 1;
        
        return PaymentStatusVo.builder()
                .orderId(orderId)
                .isPaid(isPaid)
                .payTime(order.getPayTime() != null ? order.getPayTime().format(FORMATTER) : null)
                .build();
    }
    
    /**
     * 模拟支付成功（仅用于测试）
     */
    public void simulatePayment(Long orderId) {
        Order order = orderMapper.selectById(orderId);
        if (order == null) {
            throw new BizException(400, "订单不存在");
        }
        
        if (order.getStatus() != 0) {
            throw new BizException(400, "订单状态异常，无法支付");
        }
        
        // 更新订单状态为已支付
        order.setStatus(1);
        order.setPayTime(LocalDateTime.now());
        order.setUpdateTime(LocalDateTime.now());
        orderMapper.updateById(order);
    }
    
    /**
     * 生成支付URL（模拟）
     * 实际项目中应该调用微信/支付宝的SDK生成真实的支付URL
     */
    private String generatePaymentUrl(String orderNo, String paymentType) {
        // 模拟支付URL
        String baseUrl = "wechat".equals(paymentType) 
                ? "weixin://wxpay/bizpayurl?pr=" 
                : "alipays://platformapi/startapp?appId=";
        
        // 生成模拟支付参数
        String paymentParam = Base64.getEncoder().encodeToString(
                (orderNo + "_" + System.currentTimeMillis()).getBytes()
        );
        
        return baseUrl + paymentParam;
    }
    
    /**
     * 生成二维码图片（Base64编码）
     */
    private String generateQRCode(String content) {
        try {
            // 二维码参数设置
            Map<EncodeHintType, Object> hints = new HashMap<>();
            hints.put(EncodeHintType.ERROR_CORRECTION, ErrorCorrectionLevel.H);
            hints.put(EncodeHintType.CHARACTER_SET, "UTF-8");
            hints.put(EncodeHintType.MARGIN, 1);
            
            // 生成二维码
            QRCodeWriter qrCodeWriter = new QRCodeWriter();
            BitMatrix bitMatrix = qrCodeWriter.encode(content, BarcodeFormat.QR_CODE, 300, 300, hints);
            
            // 转换为图片
            BufferedImage image = MatrixToImageWriter.toBufferedImage(bitMatrix);
            
            // 转换为Base64
            ByteArrayOutputStream baos = new ByteArrayOutputStream();
            ImageIO.write(image, "PNG", baos);
            byte[] imageBytes = baos.toByteArray();
            String base64Image = Base64.getEncoder().encodeToString(imageBytes);
            
            return "data:image/png;base64," + base64Image;
            
        } catch (Exception e) {
            throw new BizException(500, "生成二维码失败：" + e.getMessage());
        }
    }
}
