package com.atguigu.gmall.list.service.impl;

import com.alibaba.fastjson.JSON;
import com.atguigu.gmall.list.repository.GoodsRepository;
import com.atguigu.gmall.list.service.SearchService;
import com.atguigu.gmall.model.list.*;
import com.atguigu.gmall.model.product.*;
import com.atguigu.gmall.product.client.ProductFeignClient;
import org.apache.lucene.search.join.ScoreMode;
import org.elasticsearch.action.search.SearchRequest;
import org.elasticsearch.action.search.SearchResponse;
import org.elasticsearch.client.RequestOptions;
import org.elasticsearch.client.RestHighLevelClient;
import org.elasticsearch.common.text.Text;
import org.elasticsearch.index.query.*;
import org.elasticsearch.search.SearchHit;
import org.elasticsearch.search.SearchHits;
import org.elasticsearch.search.aggregations.Aggregation;
import org.elasticsearch.search.aggregations.AggregationBuilders;
import org.elasticsearch.search.aggregations.bucket.nested.ParsedNested;
import org.elasticsearch.search.aggregations.bucket.terms.ParsedLongTerms;
import org.elasticsearch.search.aggregations.bucket.terms.ParsedStringTerms;
import org.elasticsearch.search.aggregations.bucket.terms.Terms;
import org.elasticsearch.search.aggregations.bucket.terms.TermsAggregationBuilder;
import org.elasticsearch.search.builder.SearchSourceBuilder;
import org.elasticsearch.search.fetch.subphase.highlight.HighlightBuilder;
import org.elasticsearch.search.fetch.subphase.highlight.HighlightField;
import org.elasticsearch.search.sort.SortOrder;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Service;
import org.springframework.util.CollectionUtils;
import org.springframework.util.StringUtils;

import java.io.IOException;
import java.util.*;
import java.util.stream.Collectors;

@Service
public class SearchServiceImpl implements SearchService {

    //注入client
    @Autowired
    private ProductFeignClient productFeignClient;

    @Autowired
    private GoodsRepository goodsRepository;
    @Autowired
    private RedisTemplate redisTemplate;
    @Autowired
    RestHighLevelClient restHighLevelClient;

    @Override
    public void upperGoods(Long skuId) {
        Goods goods = new Goods();
        //商品的基本信息
        SkuInfo skuInfo = productFeignClient.getSkuInfoById(skuId);
        if (skuInfo != null) {
            goods.setId(skuId);
            goods.setDefaultImg(skuInfo.getSkuDefaultImg());
            goods.setPrice(skuInfo.getPrice().doubleValue());
            goods.setTitle(skuInfo.getSkuName());
            goods.setCreateTime(new Date());
        }
        //商品的分类信息
        BaseCategoryView categoryView = productFeignClient.getCategoryView(skuInfo.getCategory3Id());
        goods.setCategory1Id(categoryView.getCategory1Id());
        goods.setCategory1Name(categoryView.getCategory1Name());
        goods.setCategory2Id(categoryView.getCategory2Id());
        goods.setCategory2Name(categoryView.getCategory2Name());
        goods.setCategory3Id(categoryView.getCategory3Id());
        goods.setCategory3Name(categoryView.getCategory3Name());
        //平台属性信息
        List<BaseAttrInfo> attrList = productFeignClient.getAttrList(skuInfo.getId());
        List<SearchAttr> searchAttrList = attrList.stream().map((baseAttrInfo) -> {
            //通过baseAttrInfo获取平台属性
            SearchAttr searchAttr = new SearchAttr();
            searchAttr.setAttrId(baseAttrInfo.getId());
            searchAttr.setAttrName(baseAttrInfo.getAttrName());
            //赋值平台属性名
            //获取了平台的属性值的集合
            List<BaseAttrValue> attrValueList = baseAttrInfo.getAttrValueList();
            searchAttr.setAttrValue(attrValueList.get(0).getValueName());
            //将每个平台的属性对象返回去
            return searchAttr;
        }).collect(Collectors.toList());

        goods.setAttrs(searchAttrList);
        //品牌信息
        BaseTrademark trademark = productFeignClient.getTrademark(skuInfo.getTmId());
        //将数据保存在es中
        if (trademark != null) {
            goods.setTmId(trademark.getId());
            goods.setTmName(trademark.getTmName());
            goods.setTmLogoUrl(trademark.getLogoUrl());
        }
        goodsRepository.save(goods);
    }

    @Override
    public void lowerGoods(Long skuId) {
        this.goodsRepository.deleteById(skuId);
    }

    @Override
    public void incrHotScore(Long skuId) {
        String key = "hotScore";
        //需要借助redis
        //返回最终加完的结果
        Double score = redisTemplate.opsForZSet().incrementScore(key, "skuId:" + skuId, 1);
        //按照规则更新，比如说多少次更新一次
        if (score % 10 == 0) {
            //更新一次ES中的hotScore属性值
            //首先要获取ES中当前数据
            Optional<Goods> optionalGoods = goodsRepository.findById(skuId);
            //获取到当前对象
            Goods goods = optionalGoods.get();
            //将新的分数放在对象中 重新存入es中
            goods.setHotScore(Math.round(score));

            goodsRepository.save(goods);
        }
    }

    /**
     * 搜索列表
     * @param searchParam
     * @return
     * @throws IOException
     */
    @Override
    public SearchResponseVo search(SearchParam searchParam) throws IOException {
        // 构建dsl语句 利用Java代码
        SearchRequest searchRequest = buildQuery(searchParam);
        //执行DSL语句
        SearchResponse response = restHighLevelClient.search(searchRequest, RequestOptions.DEFAULT);
        //将查询后的数据集格式化
        SearchResponseVo responseVO = parseSearchResult(response);
        //获取总页数 {全新公式计算总页数}
        long totalPages = (responseVO.getTotal()+searchParam.getPageSize()-1)/searchParam.getPageSize();
        //赋值分页相关的参数
        responseVO.setPageSize(searchParam.getPageSize());
        responseVO.setPageNo(searchParam.getPageNo());
        responseVO.setTotalPages(totalPages);
        return responseVO;
    }

    /**
     * 获取返回的结果集
     *
     * @param searchResponse
     * @return
     */
    private SearchResponseVo parseSearchResult(SearchResponse searchResponse) {
        //声明一个空对象
        SearchResponseVo searchResponseVo = new SearchResponseVo();
        //获取品牌信息等 应该从agg中拿
        Map<String, Aggregation> aggregationMap = searchResponse.getAggregations().asMap();
        //获取到桶信息，需要转型
        ParsedLongTerms tmIdAgg = (ParsedLongTerms) aggregationMap.get("tmIdAgg");
        //获取到桶转成一个map
        List<SearchResponseTmVo> tmVos = tmIdAgg.getBuckets().stream().map(bucket -> {
            //声明一个品牌对象
            SearchResponseTmVo searchResponseTmVo = new SearchResponseTmVo();
            String tmId = ((Terms.Bucket) bucket).getKeyAsString();
            searchResponseTmVo.setTmId(Long.parseLong(tmId));
            //获取品牌的名称
            Map<String, Aggregation> tmIdAggregationMap = ((Terms.Bucket) bucket).getAggregations().asMap();
            ParsedStringTerms tmNameAgg = (ParsedStringTerms) tmIdAggregationMap.get("tmNameAgg");
            String tmName = tmNameAgg.getBuckets().get(0).getKeyAsString();
            searchResponseTmVo.setTmName(tmName);
            //获取品牌logo
            ParsedStringTerms tmLogoUrlAgg = (ParsedStringTerms) tmIdAggregationMap.get("tmLogoUrlAgg");
            String tmLogoUrl = tmLogoUrlAgg.getBuckets().get(0).getKeyAsString();
            searchResponseTmVo.setTmLogoUrl(tmLogoUrl);
            //返回品牌对象
            return searchResponseTmVo;

        }).collect(Collectors.toList());
        //赋值品牌整个集合数据
        searchResponseVo.setTrademarkList(tmVos);
        //赋值商品集合goodsList
        SearchHits hits = searchResponse.getHits();
        SearchHit[] subHits = hits.getHits();
        ArrayList<Goods> goodsList = new ArrayList<>();
        if (subHits != null && subHits.length > 0) {
            //循环遍历将goods保存
            for (SearchHit subHit : subHits) {
                //得到Json字符串
                String sourceAsString = subHit.getSourceAsString();
                //将json字符串转为对象
                Goods goods = JSON.parseObject(sourceAsString, Goods.class);
                //获取高亮中的title
                Map<String, HighlightField> highlightFields = subHit.getHighlightFields();
                HighlightField title = highlightFields.get("title");
                if(title!=null){
                    //说明title有数据 获取高亮字段
                    Text titleStr = title.getFragments()[0];
                    //将goods中的title进行替换
                    goods.setTitle(titleStr.toString());
                }
                //将对象添加到集合
                goodsList.add(goods);
            }
        }
        searchResponseVo.setGoodsList(goodsList);

        //平台属性
        ParsedNested attrAgg = (ParsedNested) aggregationMap.get("attrAgg");
        ParsedLongTerms attrIdAgg = attrAgg.getAggregations().get("attrIdAgg");
        List<? extends Terms.Bucket> attrbuckets = attrIdAgg.getBuckets();
        //判断集合中是否有数据
        if (!CollectionUtils.isEmpty(attrbuckets)) {
            List<SearchResponseAttrVo> attrValueList = attrbuckets.stream().map(bucket -> {
                //声明一个平台属性的对象
                SearchResponseAttrVo searchResponseAttrVo = new SearchResponseAttrVo();
                //赋值属性ID
                searchResponseAttrVo.setAttrId((Long) ((Terms.Bucket) bucket).getKeyAsNumber());
                //赋值属性名称
                ParsedStringTerms attrNameAgg = ((Terms.Bucket) bucket).getAggregations().get("attrNameAgg");
                String attrName = attrNameAgg.getBuckets().get(0).getKeyAsString();
                searchResponseAttrVo.setAttrName(attrName);

                //属性值赋值
                ParsedStringTerms attrValueAgg = ((Terms.Bucket) bucket).getAggregations().get("attrValueAgg");
                //属性值可能有多个
                List<? extends Terms.Bucket> valueAggbucketsList = attrValueAgg.getBuckets();
                //获取集合中每个数据
                List<String> valueList = valueAggbucketsList.stream().map(Terms.Bucket::getKeyAsString).collect(Collectors.toList());
                searchResponseAttrVo.setAttrValueList(valueList);
                return searchResponseAttrVo;
            }).collect(Collectors.toList());
            searchResponseVo.setAttrsList(attrValueList);

        }
        // 赋值总条数
        searchResponseVo.setTotal(hits.totalHits);
            //返回显示对象
        return searchResponseVo;
    }

    /**
     * 定义查询器，查询ES
     *
     * @param searchParam
     * @return
     */
    // 利用java 代码来实现一个动态的dsl语句
    private SearchRequest buildQuery(SearchParam searchParam) {
        // 定义查询器 {}
        SearchSourceBuilder searchSourceBuilder = new SearchSourceBuilder();
        // 构建QueryBuilder {bool }
        BoolQueryBuilder boolQueryBuilder = QueryBuilders.boolQuery();
        // 判断输入的查询关键字是否为空，构建查询语句
        if (!StringUtils.isEmpty(searchParam.getKeyword())){
            // {must -- match  "title": "小米手机" }
            // Operator.AND 表示查分的词语 在title 中同时存在才会查询数据，如果只存在其中一个是不查询的！
            MatchQueryBuilder title = QueryBuilders.matchQuery("title", searchParam.getKeyword()).operator(Operator.AND);
            // {bool -- must }
            boolQueryBuilder.must(title);
        }
        // 按照分类Id 查询！
        if (null!=searchParam.getCategory1Id()){
            //  { filter -- term "category1Id": "2"}
            TermQueryBuilder category1Id = QueryBuilders.termQuery("category1Id", searchParam.getCategory1Id());
            //  {bool -- filter }
            boolQueryBuilder.filter(category1Id);
        }
        if (null!=searchParam.getCategory2Id()){
            //  { filter -- term "category1Id": "2"}
            TermQueryBuilder category2Id = QueryBuilders.termQuery("category2Id", searchParam.getCategory2Id());
            //  {bool -- filter }
            boolQueryBuilder.filter(category2Id);
        }
        if (null!=searchParam.getCategory3Id()){
            //  { filter -- term "category1Id": "2"}
            TermQueryBuilder category3Id = QueryBuilders.termQuery("category3Id", searchParam.getCategory3Id());
            //  {bool -- filter }
            boolQueryBuilder.filter(category3Id);
        }
        // 查询品牌！判断用户是否输入了品牌查询条件  查询参数应该是这样的：【trademark=2:华为】
        // 获取用户查询的品牌数据
        String trademark = searchParam.getTrademark();
        if (!StringUtils.isEmpty(trademark)){
            // 用户输入了品牌查询 通过key 获取值 2:华为 ，将value 进行分割
            // split[0] = 2 split[1] = 华为
            String[] split = trademark.split(":");
            // 判断数据格式是否正确
            if (null!=split && split.length==2){
                //  { filter -- term "tmId": "4"}
                TermQueryBuilder tmId = QueryBuilders.termQuery("tmId", split[0]);
                //  {bool -- filter }
                boolQueryBuilder.filter(tmId);
            }
        }
        // 根据用户的平台属性值 进行查询！
        // 判断用户是否进行了平台属性值过滤
        // http://list.gmall.com/list.html?category3Id=61&props=1:2800-4499:价格&props=2:6.75-6.84英寸:屏幕尺寸&order=
        String[] props = searchParam.getProps();
        if (null!=props && props.length>0){
            // 数值中的数据是什么样的？ props=23:4G:运行内存
            // 循环遍历props
            for (String prop : props) {
                // 对当前的数据进行分割
                String[] split = prop.split(":");
                // 判断数据格式 props=23:4G:运行内存
                if (null!=split && split.length==3){
                    // 如何对平台属性值进行过滤的？
                    // 创建一个又一个 bool 对象
                    BoolQueryBuilder boolQuery = QueryBuilders.boolQuery();
                    BoolQueryBuilder subBoolQuery = QueryBuilders.boolQuery();
                    // {bool - must - term}
                    subBoolQuery.must(QueryBuilders.termQuery("attrs.attrId", split[0]));
                    // 根据属性值过滤
                    subBoolQuery.must(QueryBuilders.termQuery("attrs.attrValue", split[1]));

                    // 开始嵌套
                    boolQuery.must(QueryBuilders.nestedQuery("attrs",subBoolQuery, ScoreMode.None));

                    // 整合查询
                    boolQueryBuilder.filter(boolQuery);
                }
            }
        }
        // {query}
        searchSourceBuilder.query(boolQueryBuilder);

        // 分页设置：
        // 计算每页开始的起始条数
        int from = (searchParam.getPageNo()-1)*searchParam.getPageSize();
        searchSourceBuilder.from(from);
        searchSourceBuilder.size(searchParam.getPageSize());

        // 设置高亮
        HighlightBuilder highlightBuilder = new HighlightBuilder();
        highlightBuilder.field("title");
        highlightBuilder.postTags("</span>");
        highlightBuilder.preTags("<span style=color:red>");
        // 设置好的高亮对象放入方法中
        searchSourceBuilder.highlighter(highlightBuilder);

        // 做排序
        // 先获取用户是否点击了排序功能
        String order = searchParam.getOrder();
        if (!StringUtils.isEmpty(order)){
            // 页面传递的时候：&order=1：asc || &order=1：desc
            // 1=hotScore 2=price
            // 对数据进行分割
            String[] split = order.split(":");
            // 判断一下格式
            if (null!=split && split.length==2){
                // 声明一个field 字段 用它来记录按照谁进行排序
                String field = null;
                switch (split[0]){
                    case "1":
                        field = "hotScore";
                        break;
                    case "2":
                        field = "price";
                        break;
                }
                // 设置排序规则  &order=1：asc || &order=1：desc
                searchSourceBuilder.sort(field, "asc".equals(split[1])?SortOrder.ASC:SortOrder.DESC);
                // 排序规则应该按照：页面传递过来的规则，并应该写死！
                // searchSourceBuilder.sort(field, SortOrder.ASC);
            }else {
                // order=1: 默认排序规则
                searchSourceBuilder.sort("hotScore", SortOrder.DESC);
            }
        }

        // 设置品牌聚合
        TermsAggregationBuilder termsAggregationBuilder = AggregationBuilders.terms("tmIdAgg").field("tmId")
                .subAggregation(AggregationBuilders.terms("tmNameAgg").field("tmName"))
                .subAggregation(AggregationBuilders.terms("tmLogoUrlAgg").field("tmLogoUrl"));

        // 将品牌的agg 放入查询器
        searchSourceBuilder.aggregation(termsAggregationBuilder);
        // tmIdAgg 普通字段，attrAgg 是内嵌聚合
        // 设置平台属性聚合
        searchSourceBuilder.aggregation(AggregationBuilders.nested("attrAgg", "attrs")
                .subAggregation(AggregationBuilders.terms("attrIdAgg").field("attrs.attrId")
                        .subAggregation(AggregationBuilders.terms("attrNameAgg").field("attrs.attrName"))
                        .subAggregation(AggregationBuilders.terms("attrValueAgg").field("attrs.attrValue"))));

        // 数据结果集进行过滤 查询数据的时候，结果集显示 "id","defaultImg","title","price" 对应的数据
        searchSourceBuilder.fetchSource(new String[]{"id","defaultImg","title","price"},null);

        //指定index，type GET /goods/info/_search {}
        SearchRequest searchRequest = new SearchRequest("goods");
        searchRequest.types("info");
        searchRequest.source(searchSourceBuilder);
        // 打印dsl 语句
        System.out.println("dsl:"+searchSourceBuilder.toString());
        return searchRequest;
    }
}
