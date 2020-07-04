package com.atguigu.gmall.all.controller;

import com.atguigu.gmall.common.result.Result;
import com.atguigu.gmall.list.client.ListFeignClient;
import com.atguigu.gmall.model.list.SearchParam;
import org.apache.commons.lang.StringUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * @author zm
 * @create 2020-06-22 9:36
 */
@Controller
public class ListController {
    @Autowired
    private ListFeignClient listFeignClient;

    // http://list.gmall.com/list.html?category3Id=61
    // http://list.gmall.com/list.html?keyword=小米手机
    @GetMapping("list.html")
    public String search(SearchParam searchParam, Model model){
        Result<Map> result = listFeignClient.list(searchParam);

        // 制作检索条件拼接
        String urlParam = makeUrlParam(searchParam);
        // 获取处理品牌的数据
        String tradeMark = makeTradeMark(searchParam.getTrademark());
        // 获取平台属性数据
        List<Map<String, String>> list = makeProps(searchParam.getProps());

        // 获取排序规则
        Map<String, Object> order = order(searchParam.getOrder());
        // 存储数据
        model.addAttribute("urlParam",urlParam);
        model.addAttribute("searchParam",searchParam);
        model.addAttribute("trademarkParam",tradeMark);
        model.addAttribute("propsParamList",list);
        // orderMap 应该有type 字段，sort 字段
        model.addAttribute("orderMap",order);

        // 保存数据给页面使用！
        model.addAllAttributes(result.getData());
        // 检索列表
        return "list/index";
    }
    // 处理排序：根据页面提供的数据
    // 传入的参数 按照综合排序 &order=1：asc || &order=1：desc  按照价格排序 &order=2：asc || &order=2：desc
    private Map<String,Object> order(String order){
        HashMap<String, Object> hashMap = new HashMap<>();
        if (!StringUtils.isEmpty(order)){
            // 将数据进行分割
            String[] split = order.split(":");
            // 判断数据格式是否正确 &order=1：asc
            if (null!=split && split.length==2){
                // 数据处理 type 指按照什么规则排序
                hashMap.put("type",split[0]);
                // 排序规则是desc，asc
                hashMap.put("sort",split[1]);
            }else {
                // 给一个默认的排序规则
                hashMap.put("type","1"); // 综合
                // 排序规则是desc，asc
                hashMap.put("sort","asc");
            }
        }else {
            // 给一个默认的排序规则
            hashMap.put("type","1"); // 综合
            // 排序规则是desc，asc
            hashMap.put("sort","asc");
        }

        return hashMap;

    }
    // 拼接检索条件
    private String makeUrlParam(SearchParam searchParam) {
        StringBuilder urlParam = new StringBuilder();
        // 用户检索入口只有两个，一个是分类Id 一个是全文检索keyword
        //  说明用户是通过关键字入口进行检索的
        // http://list.gmall.com/list.html?keyword=小米手机
        if(null!=searchParam.getKeyword()){
            urlParam.append("keyword=").append(searchParam.getKeyword());
        }
        // http://list.gmall.com/list.html?category3Id=61
        if(null!=searchParam.getCategory3Id()){
            urlParam.append("category3Id=").append(searchParam.getCategory3Id());
        }

        if(null!=searchParam.getCategory2Id()){
            urlParam.append("category2Id=").append(searchParam.getCategory2Id());
        }

        if(null!=searchParam.getCategory1Id()){
            urlParam.append("category1Id=").append(searchParam.getCategory1Id());
        }

        // 通过两个入口进来之后，那么还可以通过品牌检索
        if (null!= searchParam.getTrademark()){
            // http://list.gmall.com/list.html?category3Id=61&trademark=2:华为
            // http://list.gmall.com/list.html?keyword=小米手机&trademark=2:华为
            if (urlParam.length()>0){
                urlParam.append("&trademark=").append(searchParam.getTrademark());
            }
        }
        // 通过两个入口进来之后，那么还可以通过品牌检索, 还可以通过平台属性值检索
        if (null!= searchParam.getProps()){
            // http://list.gmall.com/list.html?category3Id=61&trademark=2:华为&props=1:2800-4499:价格&props=2:6.75-6.84英寸:屏幕尺寸
            // http://list.gmall.com/list.html?keyword=小米手机&trademark=2:华为&props=1:2800-4499:价格&props=2:6.75-6.84英寸:屏幕尺寸
            // 循环判断
            for (String prop : searchParam.getProps()) {
                if (urlParam.length()>0){
                    urlParam.append("&props=").append(prop);
                }
            }
        }
        // list.html?keyword=小米手机&trademark=2:华为&props=1:2800-4499:价格&props=2:6.75-6.84英寸:屏幕尺寸
        // list.html?category3Id=61&trademark=2:华为&props=1:2800-4499:价格&props=2:6.75-6.84英寸:屏幕尺寸
        return "list.html?"+urlParam.toString();
    }

    // 处理品牌 品牌：品牌的名称
    // 注意传入的参数应该与封装的实体类中的品牌属性一致！
    private String makeTradeMark(String trademark){
        // 用户点击的哪个品牌
        if (!StringUtils.isEmpty(trademark)){
            // trademark=2:华为
            String[] split = trademark.split(":");
            // 判断数据格式
            if (null!=split && split.length==2){
                return "品牌：" + split[1];
            }
        }
        return null;
    }
    // 处理平台属性 平台属性名称：平台属性值名称
    // 分析数据存储的格式List<Map>
    private List<Map<String ,String>> makeProps(String[] props){
        // 声明一个集合
        List<Map<String ,String>> list = new ArrayList<>();
        //数据格式：props=23:4G:运行内存
        if(null!= props && props.length>0){
            // 开始循环
            for (String prop : props) {
                // 拆分数据 ：
                String[] split = prop.split(":");
                // 保证数据格式正确
                if (null!=split && split.length==3){
                    HashMap<String, String> map = new HashMap<>();
                    map.put("attrId",split[0]);
                    map.put("attrValue",split[1]);
                    map.put("attrName",split[2]);
                    // 添加到集合中
                    list.add(map);
                }
            }
        }
        return list;
    }

}
