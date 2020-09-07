package com.changgou.search.service.impl;

import com.alibaba.fastjson.JSON;
import com.alibaba.fastjson.JSONObject;
import com.changgou.entity.Result;
import com.changgou.goods.feign.SkuFeign;
import com.changgou.goods.pojo.Brand;
import com.changgou.goods.pojo.Sku;
import com.changgou.goods.pojo.Spec;
import com.changgou.search.dao.SkuEsMapper;
import com.changgou.search.pojo.SkuInfo;
import com.changgou.search.service.SkuService;
import org.elasticsearch.index.query.QueryBuilders;
import org.elasticsearch.search.aggregations.Aggregation;
import org.elasticsearch.search.aggregations.AggregationBuilders;
import org.elasticsearch.search.aggregations.bucket.terms.StringTerms;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.elasticsearch.core.ElasticsearchTemplate;
import org.springframework.data.elasticsearch.core.aggregation.AggregatedPage;
import org.springframework.data.elasticsearch.core.query.NativeSearchQuery;
import org.springframework.data.elasticsearch.core.query.NativeSearchQueryBuilder;
import org.springframework.data.elasticsearch.core.query.SearchQuery;
import org.springframework.stereotype.Service;
import org.springframework.util.CollectionUtils;
import org.springframework.util.StringUtils;

import java.util.*;
import java.util.stream.Collectors;

/**
 * @ClassName SkuServiceImpl
 * @Description:
 * @Author ning.chai@foxmail.com
 * @Date 2020/8/27 0027
 * @Version V1.0
 **/
@Service
public class SkuServiceImpl implements SkuService {

    @Autowired
    private SkuFeign skuFeign;

    @Autowired
    private SkuEsMapper skuEsMapper;

    /**
     * ElasticsearchTemplate：可以实现索引库的增删改查[高级搜索]
     */
    @Autowired
    private ElasticsearchTemplate elasticsearchTemplate;


    /**
     * 条件搜索
     *
     * @param searchMap
     * @return Map
     */
    @Override
    public Map search(Map<String, Object> searchMap) {

        /**
         * NativeSearchQueryBuilder：搜索条件构建对象，用于封装各种搜索条件
         */


        //1.获取关键字的值
        String keywords = null;
        if (!CollectionUtils.isEmpty(searchMap)) {
            keywords = searchMap.get("keywords").toString();
            if (StringUtils.isEmpty(keywords)) {
                //赋值给一个默认的值
                keywords = "华为";
            }
        }

        //2.创建查询对象 的构建对象
        NativeSearchQueryBuilder nativeSearchQueryBuilder = new NativeSearchQueryBuilder();

        //3.设置查询的条件
        nativeSearchQueryBuilder.withQuery(QueryBuilders.queryStringQuery(keywords).field("name"));


        NativeSearchQuery query = nativeSearchQueryBuilder.build();
        /**
         * 执行搜索，响应结果给我
         * 1.搜索条件封装对象
         * 2.搜索的结果集（集合数据)需要转换的类型
         * 3.AggregatedPage<SkuInfo>:搜索结果集的封装
         */
        AggregatedPage<SkuInfo> page = elasticsearchTemplate.queryForPage(query, SkuInfo.class);


        //分析数据

        //分页参数-总记录数
        long totalElements = page.getTotalElements();
        //分页参数-总页数
        int totalPages = page.getTotalPages();
        //获取数据结果集
        List<SkuInfo> contents = page.getContent();


        //封装一个Map存储所有数据，并返回
        HashMap<String, Object> resultMap = new HashMap<>();
        resultMap.put("rows", contents);
        resultMap.put("total", totalElements);
        resultMap.put("totalPages", totalPages);

        //分类分组查询实现
        //当用户选择了分类，将分类作为搜索条件，则不需要对分类进行分组搜索，因为分组搜索的数据是用于显示分类搜索条件的
        if (searchMap == null || StringUtils.isEmpty(searchMap.get("category"))) {
            List<String> categoryList = searchCategoryList(nativeSearchQueryBuilder);
            resultMap.put("categoryList", categoryList);
        }

        //查询品牌集合[搜索条件]
        //跟分类场景相同
        if (searchMap == null || StringUtils.isEmpty(searchMap.get("brand"))) {
            List<String> brandList = searchBrandList(nativeSearchQueryBuilder);
            resultMap.put("brandList", brandList);
        }

        //规格查询
        return resultMap;
    }

    /**
     * 分类分组查询
     *
     * @param nativeSearchQueryBuilder
     * @return
     */
    private List<String> searchCategoryList(NativeSearchQueryBuilder nativeSearchQueryBuilder) {
        /**
         * 分组查询分类集合
         * 1.addAggregation():添加一个聚合操作
         * terms:取别名
         * field:根据哪个域进行分组
         */
        nativeSearchQueryBuilder.addAggregation(AggregationBuilders.terms("skuCategory").field("categoryName").size(50));
        AggregatedPage<SkuInfo> aggregatedPage = elasticsearchTemplate.queryForPage(nativeSearchQueryBuilder.build(), SkuInfo.class);


        /**
         * 获取分组结果
         * aggregatePage.getAggregation():获取的是集合，可以根据多个域进行分组
         * .get("skuCategory")：获取指定域的集合数据
         */
        StringTerms stringTerms = aggregatedPage.getAggregations().get("skuCategory");
        List<String> categoryList = new ArrayList<>();

        if (stringTerms != null) {
            for (StringTerms.Bucket bucket : stringTerms.getBuckets()) {
                String keyAsString = bucket.getKeyAsString();//其中的一个分类名字
                categoryList.add(keyAsString);
            }
        }
        return categoryList;
    }


    /**
     * 品牌分组查询
     *
     * @param nativeSearchQueryBuilder
     * @return
     */
    private List<String> searchBrandList(NativeSearchQueryBuilder nativeSearchQueryBuilder) {
        /**
         * 分组查询分类集合
         * 1.addAggregation():添加一个聚合操作
         * terms:取别名
         * field:根据哪个域进行分组
         */
        nativeSearchQueryBuilder.addAggregation(AggregationBuilders.terms("skuBrand").field("brandName").size(50));
        AggregatedPage<Brand> aggregatedPage = elasticsearchTemplate.queryForPage(nativeSearchQueryBuilder.build(), Brand.class);


        /**
         * 获取分组结果
         * aggregatePage.getAggregation():获取的是集合，可以根据多个域进行分组
         * .get("skuCategory")：获取指定域的集合数据
         */
        StringTerms stringTerms = aggregatedPage.getAggregations().get("skuBrand");
        List<String> brandList = new ArrayList<>();

        if (stringTerms != null) {
            for (StringTerms.Bucket bucket : stringTerms.getBuckets()) {
                String keyAsString = bucket.getKeyAsString();//其中的一个品牌名字
                brandList.add(keyAsString);
            }
        }
        return brandList;
    }


    /**
     * 规格分组查询
     *
     * @param nativeSearchQueryBuilder
     * @return
     */
    private List<String> searchSpecList(NativeSearchQueryBuilder nativeSearchQueryBuilder) {
        /**
         * 分组查询规格集合
         * 1.addAggregation():添加一个聚合操作
         * terms:取别名
         * field:根据哪个域进行分组
         * size如果不加，默认就是10条
         */
        nativeSearchQueryBuilder.addAggregation(AggregationBuilders.terms("skuSpec").field("spec.keyword").size(10000));
        AggregatedPage<Spec> aggregatedPage = elasticsearchTemplate.queryForPage(nativeSearchQueryBuilder.build(), Spec.class);


        /**
         * 获取分组结果
         * aggregatePage.getAggregation():获取的是集合，可以根据多个域进行分组
         * .get("skuSpec")：获取指定域的集合数据 [{"电视音响效果":"小影院","电视屏幕尺寸":"20英寸","尺码":"165"}
         * {"电视音响效果":"立体声","电视屏幕尺寸":"20英寸","尺码":"170"}
         * ]
         */
        StringTerms stringTerms = aggregatedPage.getAggregations().get("skuSpec");
        List<String> specList = new ArrayList<>();

        if (stringTerms != null) {
            for (StringTerms.Bucket bucket : stringTerms.getBuckets()) {
                String keyAsString = bucket.getKeyAsString();//其中的一个分类名字
                specList.add(keyAsString);
            }
        }

        //合并后的Map对象
        HashMap<String, Set<String>> allSpec = new HashMap<>();


        //1.循环specList  spec={"电视音响效果":"立体声","电视屏幕尺寸":"20英寸","尺码":"165"}
        for (String spec : specList) {

            // 2.将每个json字符串转成Map<String,String>
            Map<String,String> specMap = JSON.parseObject(spec, Map.class);

            //3.将每个Map对象合并成一个Map<String,Set<String>>
            /**
             * 4.合并流程
             */
            for (Map.Entry<String, String> entry : specMap.entrySet()) {
                //4.1取出当前Map,并且获取对应的key，以及对应的value
                String key = entry.getKey();//规格名字
                String spceValue = entry.getValue();//规格值

                //4.2将当前循环的数据合并到一个Map<String,Set<String>>中
                //从allSpec中获取当前规格对应的Set集合数据
                Set<String> specSet = allSpec.get(key);
                if (CollectionUtils.isEmpty(specSet)) {
                    //之前allSpec中没有数据
                    specSet = new HashSet<>();
                }
                specSet.add(spceValue);
                allSpec.put(key,specSet);
            }

        }
        return specList;
    }


    @Override
    public void importData() {
        //Feign调用，查询List<Sku>
        Result<List<Sku>> skuResult = skuFeign.findAll();


        //将List<Sku>转成List<SkuInfo>
        List<SkuInfo> skuInfoList = JSON.parseArray(JSON.toJSONString(skuResult.getData()), SkuInfo.class);

        for (SkuInfo skuInfo : skuInfoList) {
            //获取spec->Map(String)->Map类型({"电视音响效果":"立体声","电视屏幕尺寸":"20英寸","尺码":"165"})
            Map<String, Object> specMap = JSON.parseObject(skuInfo.getSpec(), Map.class);
            //如果需要生成动态的域，只需要将该域存入到一个Map<String,Object>对象中即可，该Map<String,Object>的key会生成一个域，域的名字为该map的key
            //当前Map<String,Object>后面Object的值会作为当前Ｓｋｕ对象该域（key)对应的值
            skuInfo.setSpecMap(specMap);
        }


        //调用Dao实现数据批量导入
        skuEsMapper.saveAll(skuInfoList);
    }
}
