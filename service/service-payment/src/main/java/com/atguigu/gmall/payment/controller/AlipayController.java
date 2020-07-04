package com.atguigu.gmall.payment.controller;

import com.alipay.api.AlipayApiException;
import com.alipay.api.internal.util.AlipaySignature;
import com.atguigu.gmall.common.result.Result;
import com.atguigu.gmall.model.enums.PaymentStatus;
import com.atguigu.gmall.model.enums.PaymentType;
import com.atguigu.gmall.model.payment.PaymentInfo;
import com.atguigu.gmall.payment.config.AlipayConfig;
import com.atguigu.gmall.payment.service.AlipayService;
import com.atguigu.gmall.payment.service.PaymentService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.*;

import javax.servlet.http.HttpServletResponse;
import java.util.Map;

@Controller
@RequestMapping("/api/payment/alipay")
public class AlipayController {

    @Autowired
    private AlipayService alipayService;
    @Autowired
    private PaymentService paymentService;

    @RequestMapping("submit/{orderId}")
    @ResponseBody
    public String submitOrder(@PathVariable(value = "orderId") Long orderId, HttpServletResponse response) {
        String from = "";
        try {
            from = alipayService.AliPay(orderId);
        } catch (AlipayApiException e) {
            e.printStackTrace();
        }
        return from;
    }

    /**
     * 同步回调
     *
     * @return
     */
    @RequestMapping("callback/return")
    public String callbackReturn() {
        //重定向到展示页面
        return "redirect:" + AlipayConfig.return_order_url;
    }

    /**
     * 支付宝异步回调  必须使用内网穿透
     *
     * @param paramMap
     * @param
     * @return
     */
    @RequestMapping("callback/notify")
    @ResponseBody
    public String alipayNotify(@RequestParam Map<String, String> paramMap) {
        System.out.println("回来了！");

        boolean signVerified = false; //调用SDK验证签名
        try {
            signVerified = AlipaySignature.rsaCheckV1(paramMap, AlipayConfig.alipay_public_key, AlipayConfig.charset, AlipayConfig.sign_type);
        } catch (AlipayApiException e) {
            e.printStackTrace();
        }

        // 交易状态
        String trade_status = paramMap.get("trade_status");
        String out_trade_no = paramMap.get("out_trade_no");
        String total_amount = paramMap.get("total_amount");
        // true
        if (signVerified) {
            // TODO 验签成功后，按照支付结果异步通知中的描述，对支付结果中的业务内容进行二次校验，校验成功后在response中返回success并继续商户自身业务处理，校验失败返回failure
            if ("TRADE_SUCCESS".equals(trade_status) || "TRADE_FINISHED".equals(trade_status)) {
                // 但是，如果交易记录表中 PAID 或者 CLOSE  获取交易记录中的支付状态 通过outTradeNo来查询数据
                // select * from paymentInfo where out_trade_no=?
                PaymentInfo paymentInfo = paymentService.getPaymentInfo(out_trade_no, PaymentType.ALIPAY.name());
                if (paymentInfo.getPaymentStatus().equals(PaymentStatus.PAID.name()) || paymentInfo.getPaymentStatus().equals(PaymentStatus.ClOSED.name())) {
                    return "failure";
                }
//                if (total_amount.equals(paymentInfo.getTotalAmount())) {
                    // 正常的支付成功，我们应该更新交易记录状态
                    paymentService.paySuccess(out_trade_no, PaymentType.ALIPAY.name(), paramMap);
                    return "success";
//                }

            }

        } else {
            // TODO 验签失败则记录异常日志，并在response中返回failure.
            return "failure";
        }
        return "failure";
    }

    @RequestMapping("refund/{orderID}")
    @ResponseBody
    public Result refund(@PathVariable Long orderId){
        //根据orderID进行退款
       boolean flag = alipayService.refund(orderId);
       //返回结果
        return Result.ok(flag);
    }

    // 根据订单Id关闭订单
    @GetMapping("closePay/{orderId}")
    @ResponseBody
    public Boolean closePay(@PathVariable Long orderId){
        Boolean aBoolean = alipayService.closePay(orderId);
        return aBoolean;
    }

    // 查看是否有交易记录
    @RequestMapping("checkPayment/{orderId}")
    @ResponseBody
    public Boolean checkPayment(@PathVariable Long orderId){
        // 调用退款接口
        boolean flag = alipayService.checkPayment(orderId);
        return flag;
    }

    @GetMapping("getPaymentInfo/{outTradeNo}")
    @ResponseBody
    public PaymentInfo getPaymentInfo(@PathVariable String outTradeNo){
        PaymentInfo paymentInfo = paymentService.getPaymentInfo(outTradeNo, PaymentType.ALIPAY.name());
        if (null!=paymentInfo){
            return paymentInfo;
        }
        return null;
    }
}