package com.changgou.filter;

import org.springframework.cloud.gateway.filter.GatewayFilterChain;
import org.springframework.cloud.gateway.filter.GlobalFilter;
import org.springframework.core.Ordered;
import org.springframework.http.HttpCookie;
import org.springframework.http.HttpStatus;
import org.springframework.http.server.reactive.ServerHttpRequest;
import org.springframework.http.server.reactive.ServerHttpResponse;
import org.springframework.util.StringUtils;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;

/**
 * @ClassName AuthorizeFilter
 * @Description:
 * @Author ning.chai@foxmail.com
 * @Date 2020/9/28 0028
 * @Version V1.0
 **/
public class AuthorizeFilter implements GlobalFilter, Ordered {

    //令牌头名字
    private static final String AUTHORIZE_TOKEN = "Authorization";

    /**
     * 全局拦截
     *
     * @param exchange
     * @param chain
     * @return
     */
    @Override
    public Mono<Void> filter(ServerWebExchange exchange, GatewayFilterChain chain) {
        ServerHttpRequest request = exchange.getRequest();
        ServerHttpResponse response = exchange.getResponse();

        //获取用户令牌信息
        //1）.头文件
        String token = request.getHeaders().getFirst(AUTHORIZE_TOKEN);
        //boolean true：令牌在头文件中  false：令牌不在头文件中-》将令牌封装到头文件中，再传递给其他微服务

        boolean hasToken = true;

        //2）.参数
        if (StringUtils.isEmpty(token)) {
            token = request.getQueryParams().getFirst(AUTHORIZE_TOKEN);
            hasToken = false;
        }

        //3）.Cookie
        if (StringUtils.isEmpty(token)) {
            HttpCookie tokenCookie = request.getCookies().getFirst(AUTHORIZE_TOKEN);
            if (tokenCookie != null) {
                token = tokenCookie.getValue();
            }
        }


        //如果没有令牌，拦截
        if (StringUtils.isEmpty(token)) {
            //设置没有权限的状态码 401
            response.setStatusCode(HttpStatus.UNAUTHORIZED);
            //响应空数据
            return response.setComplete();
        }

        //令牌判断是否为空，如果不为空，将令牌放到头文件中，放行
        //如果有令牌，校验令牌是否有效
        if (StringUtils.isEmpty(token)) {
            //无效拦截
            response.setStatusCode(HttpStatus.UNAUTHORIZED);
            //响应空数据
            return response.setComplete();
        }


        //如果请求头中没有，加入
        if (!hasToken) {
            //用户token里可能没有前缀 bearer
            if (!token.startsWith("bearer ") && !token.startsWith("Bearer ")) {
                token = "bearer " + token;
            }
            //将令牌封装到头文件中
            request.mutate().header(AUTHORIZE_TOKEN, token);
        }

        //有效放行
        return chain.filter(exchange);
    }


    /**
     * 排序
     *
     * @return
     */
    @Override
    public int getOrder() {
        return 0;
    }
}
