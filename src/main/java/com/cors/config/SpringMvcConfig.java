package com.cors.config;

import com.cors.interceptor.UserInterceptor;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.databind.module.SimpleModule;
import com.fasterxml.jackson.databind.ser.std.ToStringSerializer;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import jakarta.annotation.Resource;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.converter.HttpMessageConverter;
import org.springframework.http.converter.json.MappingJackson2HttpMessageConverter;
import org.springframework.web.servlet.DispatcherServlet;
import org.springframework.web.servlet.config.annotation.InterceptorRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

import java.util.List;

@Configuration
@ComponentScan("com.cors.advice")
@ConditionalOnClass(DispatcherServlet.class) // 保证配置只在 Web 环境 下生效，非 Web 项目不会加载
public class SpringMvcConfig implements WebMvcConfigurer {

    @Resource
    private UserInterceptor userInterceptor;

    @Override
    public void addInterceptors(InterceptorRegistry registry) {
        // 拦截器会在 Controller 执行前、执行后被调用
        registry.addInterceptor(userInterceptor)
                .excludePathPatterns("/user/login")
                .excludePathPatterns("/user/register")
                .excludePathPatterns("/user/auth/refresh");
    }

    @Override
    public void configureMessageConverters(List<HttpMessageConverter<?>> converters) {
        // WebMvcConfigurer.super.configureMessageConverters(converters);
        MappingJackson2HttpMessageConverter converter = new MappingJackson2HttpMessageConverter();

        // Long 转 String
        SimpleModule longToString = new SimpleModule();

        // 序列化（Long 传给前端用）
        longToString.addSerializer(Long.class, new ToStringSerializer());
        longToString.addSerializer(Long.TYPE, new ToStringSerializer());

        ObjectMapper objectMapper = new ObjectMapper();
        objectMapper.registerModule(longToString);
        objectMapper.registerModule(new JavaTimeModule());
        objectMapper.configure(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS, false);
        converter.setObjectMapper(objectMapper);
        converters.add(0, converter);
    }
}
