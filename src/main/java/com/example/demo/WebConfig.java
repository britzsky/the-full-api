package com.example.demo;

import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.CorsRegistry;
import org.springframework.web.servlet.config.annotation.ResourceHandlerRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

@Configuration
public class WebConfig implements WebMvcConfigurer {
	
	@Override
    public void addCorsMappings(CorsRegistry registry) {
        registry.addMapping("/api/**")
                .allowedOrigins("<http://localhost:3000>")
                .allowedMethods("GET", "POST", "PUT", "DELETE", "OPTIONS")
                .allowedHeaders("Authorization", "x-refresh-token", "Content-Type")
                .exposedHeaders("Authorization", "x-refresh-token")
                .allowCredentials(true)
                .maxAge(3600);
    }
	
	@Override
    public void addResourceHandlers(ResourceHandlerRegistry registry) {
        // ✅ http://localhost:8080/uploads/** → C:/uploads/** 매핑
        registry.addResourceHandler("/image/**")
                .addResourceLocations("file:///C:/Users/손경원/eclipse-workspace/the-full-api/src/main/resources/static/image/");
    }
}
