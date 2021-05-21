/**
 * 严肃声明：
 * 开源版本请务必保留此注释头信息，若删除我方将保留所有法律责任追究！
 * 本系统已申请软件著作权，受国家版权局知识产权以及国家计算机软件著作权保护！
 * 可正常分享和学习源码，不得用于违法犯罪活动，违者必究！
 * Copyright (c) 2019-2020 十三 all rights reserved.
 * 版权所有，侵权必究！
 */
package ltd.newbee.mall.controller.mall;

import com.alibaba.fastjson.JSON;
import com.alipay.api.AlipayApiException;
import com.alipay.api.AlipayClient;
import com.alipay.api.DefaultAlipayClient;
import com.alipay.api.internal.util.AlipaySignature;
import com.alipay.api.request.AlipayTradePagePayRequest;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import ltd.newbee.mall.common.Constants;
import ltd.newbee.mall.common.NewBeeMallException;
import ltd.newbee.mall.common.NewBeeMallOrderStatusEnum;
import ltd.newbee.mall.common.ServiceResultEnum;
import ltd.newbee.mall.controller.vo.NewBeeMallOrderDetailVO;
import ltd.newbee.mall.controller.vo.NewBeeMallShoppingCartItemVO;
import ltd.newbee.mall.controller.vo.NewBeeMallUserVO;
import ltd.newbee.mall.entity.NewBeeMallOrder;
import ltd.newbee.mall.service.NewBeeMallOrderService;
import ltd.newbee.mall.service.NewBeeMallShoppingCartService;
import ltd.newbee.mall.util.PageQueryUtil;
import ltd.newbee.mall.util.Result;
import ltd.newbee.mall.util.ResultGenerator;
import org.springframework.stereotype.Controller;
import org.springframework.util.CollectionUtils;
import org.springframework.util.StringUtils;
import org.springframework.web.bind.annotation.*;

import javax.annotation.Resource;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpSession;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Controller
public class OrderController {

    @Resource
    private NewBeeMallShoppingCartService newBeeMallShoppingCartService;
    @Resource
    private NewBeeMallOrderService newBeeMallOrderService;

    @GetMapping("/orders/{orderNo}")
    public String orderDetailPage(HttpServletRequest request, @PathVariable("orderNo") String orderNo, HttpSession httpSession) {
        NewBeeMallUserVO user = (NewBeeMallUserVO) httpSession.getAttribute(Constants.MALL_USER_SESSION_KEY);
        NewBeeMallOrderDetailVO orderDetailVO = newBeeMallOrderService.getOrderDetailByOrderNo(orderNo, user.getUserId());
        if (orderDetailVO == null) {
            return "error/error_5xx";
        }
        request.setAttribute("orderDetailVO", orderDetailVO);
        return "mall/order-detail";
    }

    @GetMapping("/orders")
    public String orderListPage(@RequestParam Map<String, Object> params, HttpServletRequest request, HttpSession httpSession) {
        NewBeeMallUserVO user = (NewBeeMallUserVO) httpSession.getAttribute(Constants.MALL_USER_SESSION_KEY);
        params.put("userId", user.getUserId());
        if (StringUtils.isEmpty(params.get("page"))) {
            params.put("page", 1);
        }
        params.put("limit", Constants.ORDER_SEARCH_PAGE_LIMIT);
        //封装我的订单数据
        PageQueryUtil pageUtil = new PageQueryUtil(params);
        request.setAttribute("orderPageResult", newBeeMallOrderService.getMyOrders(pageUtil));
        request.setAttribute("path", "orders");
        return "mall/my-orders";
    }

    @GetMapping("/saveOrder")
    public String saveOrder(HttpSession httpSession) {
        NewBeeMallUserVO user = (NewBeeMallUserVO) httpSession.getAttribute(Constants.MALL_USER_SESSION_KEY);
        List<NewBeeMallShoppingCartItemVO> myShoppingCartItems = newBeeMallShoppingCartService.getMyShoppingCartItems(user.getUserId());
        if (StringUtils.isEmpty(user.getAddress().trim())) {
            //无收货地址
            NewBeeMallException.fail(ServiceResultEnum.NULL_ADDRESS_ERROR.getResult());
        }
        if (CollectionUtils.isEmpty(myShoppingCartItems)) {
            //购物车中无数据则跳转至错误页
            NewBeeMallException.fail(ServiceResultEnum.SHOPPING_ITEM_ERROR.getResult());
        }
        //保存订单并返回订单号
        String saveOrderResult = newBeeMallOrderService.saveOrder(user, myShoppingCartItems);
        //跳转到订单详情页
        return "redirect:/orders/" + saveOrderResult;
    }

    @PutMapping("/orders/{orderNo}/cancel")
    @ResponseBody
    public Result cancelOrder(@PathVariable("orderNo") String orderNo, HttpSession httpSession) {
        NewBeeMallUserVO user = (NewBeeMallUserVO) httpSession.getAttribute(Constants.MALL_USER_SESSION_KEY);
        String cancelOrderResult = newBeeMallOrderService.cancelOrder(orderNo, user.getUserId());
        if (ServiceResultEnum.SUCCESS.getResult().equals(cancelOrderResult)) {
            return ResultGenerator.genSuccessResult();
        } else {
            return ResultGenerator.genFailResult(cancelOrderResult);
        }
    }

    @PutMapping("/orders/{orderNo}/finish")
    @ResponseBody
    public Result finishOrder(@PathVariable("orderNo") String orderNo, HttpSession httpSession) {
        NewBeeMallUserVO user = (NewBeeMallUserVO) httpSession.getAttribute(Constants.MALL_USER_SESSION_KEY);
        String finishOrderResult = newBeeMallOrderService.finishOrder(orderNo, user.getUserId());
        if (ServiceResultEnum.SUCCESS.getResult().equals(finishOrderResult)) {
            return ResultGenerator.genSuccessResult();
        } else {
            return ResultGenerator.genFailResult(finishOrderResult);
        }
    }

    @GetMapping("/selectPayType")
    public String selectPayType(HttpServletRequest request, @RequestParam("orderNo") String orderNo, HttpSession httpSession) {
        NewBeeMallUserVO user = (NewBeeMallUserVO) httpSession.getAttribute(Constants.MALL_USER_SESSION_KEY);
        NewBeeMallOrder newBeeMallOrder = newBeeMallOrderService.getNewBeeMallOrderByOrderNo(orderNo);
        //判断订单userId
        if (!user.getUserId().equals(newBeeMallOrder.getUserId())) {
            NewBeeMallException.fail(ServiceResultEnum.NO_PERMISSION_ERROR.getResult());
        }
        //判断订单状态
        if (newBeeMallOrder.getOrderStatus().intValue() != NewBeeMallOrderStatusEnum.ORDER_PRE_PAY.getOrderStatus()) {
            NewBeeMallException.fail(ServiceResultEnum.ORDER_STATUS_ERROR.getResult());
        }
        request.setAttribute("orderNo", orderNo);
        request.setAttribute("totalPrice", newBeeMallOrder.getTotalPrice());
        return "mall/pay-select";
    }

    @GetMapping("/payPage")
    @ResponseBody
    public String payOrder(HttpServletRequest request, @RequestParam("orderNo") String orderNo, HttpSession httpSession, @RequestParam("payType") int payType) {
        NewBeeMallUserVO user = (NewBeeMallUserVO) httpSession.getAttribute(Constants.MALL_USER_SESSION_KEY);
        NewBeeMallOrder newBeeMallOrder = newBeeMallOrderService.getNewBeeMallOrderByOrderNo(orderNo);
        //判断订单userId
        if (!user.getUserId().equals(newBeeMallOrder.getUserId())) {
            NewBeeMallException.fail(ServiceResultEnum.NO_PERMISSION_ERROR.getResult());
        }
        //判断订单状态
        if (newBeeMallOrder.getOrderStatus().intValue() != NewBeeMallOrderStatusEnum.ORDER_PRE_PAY.getOrderStatus()) {
            NewBeeMallException.fail(ServiceResultEnum.ORDER_STATUS_ERROR.getResult());
        }
        request.setAttribute("orderNo", orderNo);
        request.setAttribute("totalPrice", newBeeMallOrder.getTotalPrice());
        Integer totalPrice = newBeeMallOrder.getTotalPrice();
        if (payType == 1) {
            return this.toPay(orderNo, newBeeMallOrder.getOrderDesc(), newBeeMallOrder.getTotalPrice());
        } else {
            return "mall/wxpay";
        }
    }
    @GetMapping(value = "toPay")
    @ResponseBody
    public String toPay(String outTradeNo, String orderDesc, Integer totalAmount){
        // 支付宝参数
        //创建API对应的request
        //在公共参数中设置回跳和通知地址
        AlipayTradePagePayRequest alipayRequest = new AlipayTradePagePayRequest();
        alipayRequest.setReturnUrl("http://3eo0114592.zicp.vip/alipay/callback/return");
        alipayRequest.setNotifyUrl("http://3eo0114592.zicp.vip/alipay/callback/notify");

        // 声明一个Map
        Map<String,Object> bizContnetMap=new HashMap<>();
        bizContnetMap.put("out_trade_no", outTradeNo);
        bizContnetMap.put("product_code","FAST_INSTANT_TRADE_PAY");
        bizContnetMap.put("subject", orderDesc);
        bizContnetMap.put("total_amount", totalAmount);
        // 将map变成json
        String Json = JSON.toJSONString(bizContnetMap);
        alipayRequest.setBizContent(Json);
        String form="";
        try {
            //调用SDK生成表单
            AlipayClient alipayClient=new DefaultAlipayClient("https://openapi.alipay.com/gateway.do","2021002142664019","MIIEvgIBADANBgkqhkiG9w0BAQEFAASCBKgwggSkAgEAAoIBAQDC+roR0C1fz1THBcdJhaacREVhgWCq6GQT4a0KCdWf12dI6nmEHyNLrccbvzq6zZLib9X7X9iKAw9KNJjEQ86g3+bZcMxJ8oAOCRwChiaahNDr/Nwe8t0bzoR6q0+BPUcGRGKfc8uXYbBMpyd+4/q9cl//c60wyL7kLCBx6GK6qd76twINvJonG4onsc5gSd0pg0rwcucIxlqaGc5J+YakGh9tso8U1Mdz64ibhkvPLUTtNUIx9E59lgGCt6P9IpEmCLAhQuMscGzken0JZ84rriOJ9zaXMCJIZF0Ii/iR4ltph0op7ajOLz53Vnjpy41bXAXgJl6l11CtzPCrbt7RAgMBAAECggEBALIjKITF0e7LGgWLffvqI8J5jXn8HjmHgIe5k8KsIYSb5D12G/deC31FE7iCCkJJN32x96pmcwwEp5+AVmJaIRpR9jOLHtoguGYaZIDZ18MsvUQeDW4pLWIOWHNKuKS3O4C6UfCkdsmj8tLVlPwVhiw3pwVYxk7f2afVuP7Y9frbRgVCYl4TwSN4g9IvRF94WV5E8gttL0edYODOb4iTmMRAzsZXAOOyhk5SvMAM6PENxSVPmnfXS+hPj0UegHeGMzCytEI2TrVyXcRr0blTbSO3cgt97M6Q1ZCmJZf/hiZUOoB3ypi7+8jGFx1C3AdMpoMaMogue3kpq1ztcbMBgFECgYEA914NghwKMFVkvSO6UkhcUl94yomVUflFvXe8IJW6zRgmfoX7f1UynkZy3up5FAjYdeeKP1iE6cQ6PpWBfKAQ5HO2XCwkV5Wcu0zZR1xxSigSdAoA4BoCwwt2KMd8WmBefi5bXMWskpaLXuISix5D9kdgF6UxBcprEyurDlb7rCsCgYEAycilkWLedFK5fSeYGIxN0qhkN6X1Sosua2AJ6VNvK0aURZnksWb/CIa45uemuKWnpDPf23c+tIGxlMPv0tHUhfcZCDnTAHLBEq+DsxRs2d6usgkWGFIxX5Lei9MJ+3FYw3j37JGHgrnzDmB/SNHfIKowItNvghvS+ESuby4cVvMCgYEAspqnGDVUqfdOKD5erkBu4E7ssRhLxwc7coS8Qa66VJFGYf4tS4/hX2QwVoFncK6+U8sdB4XLWdoDz+I0vx33dGuT1NNOXiq5IodnLl4xskBiqoP6g9RkAB4Lb4AxajPApkykTMSxSJoIATcr+mSc33pDiG8OiUbQruPNuynUx0cCgYBHS7HUcM6Q4znO0tKWudw1dnISh3zn3c2E+uYFnwlEjSeOgBWh6PZrmM50J51s2HsnNXz/Gl75gGmyWpi+MI2a1/fsRCIdom49n+40tB7RzDCFj1hTN5diYY5ocSSxxxbJ2lAfMjxjIDiU99uBa0YfUIK5i2N24UXlmr7XYb95qQKBgCXAF5JsSGF2vk34bdInQSqbAz8ePYCBH4Ef+Nxme/G4YxDcV8SxqiI+y0+d6DbOrkmQi9AWBk2SgFiaN7GpxHHtG9f3m0k/T9z+BQJrerxID8yOTN8pJLf8VRq/zeBhwmmogKnZCgtlT/beD5sIJ9aTu7YaeciioGQ0gOY2PoUI","json","utf-8", "MIIBIjANBgkqhkiG9w0BAQEFAAOCAQ8AMIIBCgKCAQEAtIpt6eDEbDQ10YPMzTmyo76WqJ0JIw9yTmLzjW2x7CrVstVtHOwKz0gszVfvnqyF/J7sjDGbWjlztBWv+NzAWfMNcLYN/j0Fo+25rwW0TxqThIQ9/jaF0xhdqEx2jUQ3oU8j6STt8lh4jbn2yiKA3m4BluHYw3hcRCr1tBOPSI45eAdLL07TFeC2Zj/h/iuODak85M4+56+4g9Z+nHFSNwjBjbUr179tVVnu/iQZP5Iwx6fIWcaMIgooF69Ljs0+oxsAe6qQZFG5gFHw1gi7rVbH7l40D8V/Mfg0aTqrRMSzvo0xM1fa3NTbd9c4RVc8+SpmN3rHm468kPbMWhqnYQIDAQAB","RSA2" );
            form = alipayClient.pageExecute(alipayRequest).getBody();
        } catch (AlipayApiException e) {
            e.printStackTrace();
        }
        return form;

    }

    @GetMapping("/paySuccess")
    @ResponseBody
    public Result paySuccess(@RequestParam("orderNo") String orderNo, @RequestParam("payType") int payType) {
        String payResult = newBeeMallOrderService.paySuccess(orderNo, payType);
        if (ServiceResultEnum.SUCCESS.getResult().equals(payResult)) {
            return ResultGenerator.genSuccessResult();
        } else {
            return ResultGenerator.genFailResult(payResult);
        }
    }
    // 支付成功之后重定向到订单url
    @RequestMapping("/alipay/callback/return")
    public String callBack(){
        // 回调到订单页面
        return "redirect:"+ "www.baidu.com";
    }

    // 异步回调
    // http://xxx.xxx.xxx/index?total_amout=0.01
    @RequestMapping("alipay/callback/notify")
    @ResponseBody
    public String notifyUrl(@RequestParam Map<String,String> paramMap, HttpServletRequest request) throws AlipayApiException {
        System.out.println("你回来啦！");
        // 只有交易通知状态为 TRADE_SUCCESS 或 TRADE_FINISHED 时，支付宝才会认定为买家付款成功
        String trade_status = paramMap.get("trade_status");
        String out_trade_no = paramMap.get("out_trade_no");

        boolean signVerified = AlipaySignature.rsaCheckV1(paramMap, "MIIBIjANBgkqhkiG9w0BAQEFAAOCAQ8AMIIBCgKCAQEAtIpt6eDEbDQ10YPMzTmyo76WqJ0JIw9yTmLzjW2x7CrVstVtHOwKz0gszVfvnqyF/J7sjDGbWjlztBWv+NzAWfMNcLYN/j0Fo+25rwW0TxqThIQ9/jaF0xhdqEx2jUQ3oU8j6STt8lh4jbn2yiKA3m4BluHYw3hcRCr1tBOPSI45eAdLL07TFeC2Zj/h/iuODak85M4+56+4g9Z+nHFSNwjBjbUr179tVVnu/iQZP5Iwx6fIWcaMIgooF69Ljs0+oxsAe6qQZFG5gFHw1gi7rVbH7l40D8V/Mfg0aTqrRMSzvo0xM1fa3NTbd9c4RVc8+SpmN3rHm468kPbMWhqnYQIDAQAB", "utf-8", "RSA2"); //调用SDK验证签名
        if(signVerified){
            // TODO 验签成功后，按照支付结果异步通知中的描述，对支付结果中的业务内容进行二次校验，校验成功后在response中返回success并继续商户自身业务处理，校验失败返回failure

            if ("TRADE_SUCCESS".equals(trade_status) || "TRADE_FINISHED".equals(trade_status)){
                // 如果paymentInfo 中payment_status 是PAID 或者CLOSE 那么这个时候，也应该是失败！
                // 根据out_trade_no 查询 paymentInfo 对象
                // select * from paymentInfo where out_trade_no = ?
                NewBeeMallOrder newBeeMallOrder = newBeeMallOrderService.getNewBeeMallOrderByOrderNo(out_trade_no);
//                if (paymentInfoQuery.getPayStatus() == PaymentStatus.PAID || paymentInfoQuery.getPaymentStatus()==PaymentStatus.ClOSED){
//                    return "failure";
//                }

                // 更新交易记录状态，改为付款！paymentInfo
                // update paymentInfo set payment_status = PAID ,callback_time = ? where out_trade_no = ?
                newBeeMallOrder.setPayStatus((byte) 1);
                newBeeMallOrderService.updateByPrimaryKeySelective(newBeeMallOrder);
                return "success";
            }
        }else{
            // TODO 验签失败则记录异常日志，并在response中返回failure.
            return "failure";
        }
        return "failure";
    }

}
