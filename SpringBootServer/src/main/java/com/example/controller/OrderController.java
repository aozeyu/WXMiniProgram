package com.example.controller;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.example.entity.Order;
import com.example.entity.OrderDetail;
import com.example.entity.R;
import com.example.properties.WeixinpayProperties;
import com.example.service.IOrderDetailService;
import com.example.service.IOrderService;
import com.example.util.*;
import io.jsonwebtoken.Claims;
import org.apache.http.HttpResponse;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;

import java.security.MessageDigest;
import java.util.*;

/**
 * 订单Controller控制器
 * @author java1234_小锋
 * @site www.java1234.com
 * @company 南通小锋网络科技有限公司
 * @create 2022-01-12 11:00
 */
@RestController
@RequestMapping("/my/order/")
public class OrderController {

    @Autowired
    private IOrderService orderService;

    @Autowired
    private IOrderDetailService orderDetailService;

    @Autowired
    private WeixinpayProperties weixinpayProperties;


    /**
     * 创建订单，返回订单号
     * @return
     */
    @RequestMapping("/create")
    public R create(@RequestBody Order order,@RequestHeader(value="token") String token){
        System.out.println("order"+order);
        if(StringUtil.isNotEmpty(token)){
            Claims claims = JwtUtils.validateJWT(token).getClaims();
            if(claims!=null){
                String openId = claims.getId();
                order.setUserId(openId);
                order.setOrderNo("JAVA"+DateUtil.getCurrentDateStr());
                order.setCreateDate(new Date());
                System.out.println(openId);
            }else{
                return R.error(500,"鉴权失败！");
            }
        }else{
            return R.error(500,"无权限访问！");
        }


        OrderDetail[] goods = order.getGoods();
        orderService.save(order);
        String orderNo=order.getOrderNo();

        for(int i=0;i<goods.length;i++){
            OrderDetail orderDetail=goods[i];
            orderDetail.setMId(order.getId());
//            时间处理
            orderDetail.setServiceStart(new Date());
            orderDetail.setServiceEnd(new Date(System.currentTimeMillis()
                    + 3600L * 1000 * orderDetail.getItemHours() * orderDetail.getGoodsNumber() ));

            orderDetailService.save(orderDetail);
        }
        Map<String,Object> resultMap=new HashMap<String,Object>();
        resultMap.put("orderNo",orderNo);
        return R.ok(resultMap);
    }

    /**
     * 预付款
     * @param orderNo
     * @param token
     * @return
     */
    @RequestMapping("/preparePay")
    public R preparePay(@RequestBody String orderNo, @RequestHeader(value="token") String token)throws Exception{
        System.out.println("orderNo:"+orderNo);
        System.out.println("token:"+token);
        String openId =null;
        if(StringUtil.isNotEmpty(token)){
            Claims claims = JwtUtils.validateJWT(token).getClaims();
            if(claims!=null){
                openId = claims.getId();
                System.out.println("openId:"+openId);
            }else{
                return R.error(500,"鉴权失败！");
            }
        }else{
            return R.error(500,"无权限访问！");
        }

        Order order = orderService.getOne(new QueryWrapper<Order>().eq("orderNo", orderNo));

        System.out.println("totalPrice:"+order.getTotalPrice().movePointRight(2));

        System.out.println("=======");
        Map<String,Object> map=new HashMap<String,Object>();
        map.put("appid", weixinpayProperties.getAppid());
        map.put("mch_id", weixinpayProperties.getMch_id());
        map.put("openid", openId);
        map.put("nonce_str", StringUtil.getRandomString(32)); // 随机串
        map.put("device_info", weixinpayProperties.getDevice_info());
        map.put("notify_url", weixinpayProperties.getNotify_url());
        map.put("trade_type", "JSAPI"); // 交易类型
        map.put("out_trade_no", orderNo);
        map.put("body", "java1234mall商品购买测试");
        // map.put("total_fee", order.getTotalPrice().movePointRight(2));
        map.put("total_fee", 1);  // 1分钱测试
        // map.put("spbill_create_ip", getRemortIP(request)); // 终端IP
        map.put("spbill_create_ip", "127.0.0.1"); // 终端IP
        map.put("sign", getSign(map)); // 签名
        String xml=XmlUtil.genXml(map);
        System.out.println(xml);

        HttpResponse httpResponse = HttpClientUtil.sendXMLDataByPost(weixinpayProperties.getUrl().toString(), xml);
        String httpEntityContent = HttpClientUtil.getHttpEntityContent(httpResponse);
        System.out.println("httpEntityContent:"+httpEntityContent);

        Map resultMap = XmlUtil.doXMLParse(httpEntityContent);  // 解析返回的xml结果
        System.out.println("resultMap="+resultMap);
        if(resultMap.get("result_code").equals("SUCCESS")){
            // 封装返回值
            Map<String,Object> payMap=new HashMap<String,Object>();
            payMap.put("appId",resultMap.get("appid"));
            payMap.put("nonceStr",StringUtil.getRandomString(32));
            payMap.put("package","prepay_id="+resultMap.get("prepay_id"));
            payMap.put("timeStamp",System.currentTimeMillis()/1000+"");
            payMap.put("signType","MD5");
            String paySign=getSign(payMap);
            System.out.println("paySign="+paySign);
            System.out.println("==========");
            payMap.put("paySign",paySign); // 重新签名
            payMap.put("orderNo",orderNo);
            System.out.println("payMap="+payMap);

                return R.ok(payMap);
        }else{
            return R.error(500,"系统报错，请联系管理员");
        }


    }


    /**
     * 微信支付签名算法sign
     */
    private String getSign(Map<String,Object> map) {
        StringBuffer sb = new StringBuffer();
        String[] keyArr = (String[]) map.keySet().toArray(new String[map.keySet().size()]);//获取map中的key转为array
        Arrays.sort(keyArr);//对array排序
        for (int i = 0, size = keyArr.length; i < size; ++i) {
            if ("sign".equals(keyArr[i])) {
                continue;
            }
            sb.append(keyArr[i] + "=" + map.get(keyArr[i]) + "&");
        }
        sb.append("key=" + weixinpayProperties.getKey());
        String sign = string2MD5(sb.toString());
        return sign;
    }

    /***
     * MD5加码 生成32位md5码
     */
    private String string2MD5(String str){
        if (str == null || str.length() == 0) {
            return null;
        }
        char hexDigits[] = { '0', '1', '2', '3', '4', '5', '6', '7', '8', '9',
                'a', 'b', 'c', 'd', 'e', 'f' };

        try {
            MessageDigest mdTemp = MessageDigest.getInstance("MD5");
            mdTemp.update(str.getBytes("UTF-8"));

            byte[] md = mdTemp.digest();
            int j = md.length;
            char buf[] = new char[j * 2];
            int k = 0;
            for (int i = 0; i < j; i++) {
                byte byte0 = md[i];
                buf[k++] = hexDigits[byte0 >>> 4 & 0xf];
                buf[k++] = hexDigits[byte0 & 0xf];
            }
            return new String(buf).toUpperCase();
        } catch (Exception e) {
            return null;
        }
    }



    /**
     * 订单查询  type 值 0 全部订单  1 待付款   2  待收货  3 退款/退货
     * @return
     */
    @RequestMapping("/list")
    public R list(Integer type,Integer page,Integer pageSize,@RequestHeader(value="token") String token){
//        System.out.println(type+"."+page+"."+pageSize);
        if(StringUtil.isNotEmpty(token)){
            Claims claims = JwtUtils.validateJWT(token).getClaims();
            if(claims!=null){
                String openId = claims.getId();

                System.out.println(openId);
            }else{
                return R.error(500,"鉴权失败！");
            }
        }else{
            return R.error(500,"无权限访问！");
        }
        List<Order> orderList=null;

        Page<Order> pageOrder=new Page<>(page,pageSize); // 定义分页

        Map<String,Object> resultMap=new HashMap<String,Object>();


        if(type==0){  // 查询全部
            // orderList = orderService.list();
            Page<Order> orderReslut = orderService.page(pageOrder);
            System.out.println("总记录："+orderReslut.getTotal());
            System.out.println("总页数："+orderReslut.getPages());
            System.out.println("当前页数据："+orderReslut.getRecords());
            orderList=orderReslut.getRecords();
            resultMap.put("total",orderReslut.getTotal());
            resultMap.put("totalPage",+orderReslut.getPages());

        }else{  // 根据状态查询
            // orderList=orderService.list(new QueryWrapper<Order>().eq("status",type));
            Page<Order> orderReslut =orderService.page(pageOrder,new QueryWrapper<Order>().eq("status",type));
            System.out.println("总记录："+orderReslut.getTotal());
            System.out.println("总页数："+orderReslut.getPages());
            System.out.println("当前页数据："+orderReslut.getRecords());
            orderList=orderReslut.getRecords();
            resultMap.put("total",orderReslut.getTotal());
            resultMap.put("totalPage",+orderReslut.getPages());
        }
        resultMap.put("page",page);
        resultMap.put("orderList",orderList);
        return R.ok(resultMap);
    }


}
