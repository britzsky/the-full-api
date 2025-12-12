package com.example.demo;

import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.CorsRegistry;
import org.springframework.web.servlet.config.annotation.ResourceHandlerRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

@Configuration
public class WebConfig implements WebMvcConfigurer {
	
	@Override
    public void addCorsMappings(CorsRegistry registry) {
			registry.addMapping("/**")  // ★ context-path(/api)는 빼고!
		        .allowedOrigins(
		            "http://localhost:3000",
		            "http://172.30.1.48:8080",
		            "http://52.64.151.137",
		            "http://52.64.151.137:8080",
		            "http://thefull.kr",
		            "http://thefull.kr:8080"
		        )
                .allowedMethods("GET", "POST", "PUT", "DELETE", "OPTIONS")
                .allowedHeaders("Authorization", "x-refresh-token", "Content-Type")
                .exposedHeaders("Authorization", "x-refresh-token")
                .allowCredentials(true)
                .maxAge(3600);
    }
	
	@Override
    public void addResourceHandlers(ResourceHandlerRegistry registry) {
		registry.addResourceHandler("/api/image/**")
		.addResourceLocations("file:///C:/Program Files/Apache Software Foundation/Tomcat 10.1/webapps/api/WEB-INF/classes/static/image/");
		//registry.addResourceHandler("/image/**")
		//.addResourceLocations("file:///C:/Users/손경원/git/the-full-api/src/main/resources/static/image/");
		//registry.addResourceHandler("/image/**")
		//.addResourceLocations("file:///opt/apache-tomcat-10.1.49/webapps/api/WEB-INF/classes/static/image/");
    }
}
