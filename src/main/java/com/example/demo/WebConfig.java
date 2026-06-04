package com.example.demo;

import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.CorsRegistry;
import org.springframework.web.servlet.config.annotation.ResourceHandlerRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

@Configuration
public class WebConfig implements WebMvcConfigurer {

	final static String REAL_HANDLE = "/image/**";
	final static String REAL_PATH = "file:///opt/thefull/uploads/image/";

	final static String DEV_HANDLE = "/image/**";
	final static String DEV_PATH = "file:///C:/Users/93827/Desktop/image/";

	final static String LOCAL_HANDLE = "/image/**";
	final static String LOCAL_PATH = "file:///C:/Users/손경원/git/the-full-api/src/main/resources/static/image/";
	// final static String LOCAL_PATH =
	// "file:///C:/Users/93827/.git/the-full-api/src/main/resources/static/image/";

	@Override
	public void addCorsMappings(CorsRegistry registry) {
		registry.addMapping("/**") // ★ context-path(/api)는 빼고!
				.allowedOrigins(
						"http://localhost:3000",
						"http://172.30.1.48:8080",
						"http://52.64.151.137",
						"http://52.64.151.137:8080",
						"http://192.168.0.6:8082",
						"http://192.168.0.6",
						"http://thefull.kr",
						"http://thefull.kr:8080",
						"http://localhost:8081",
						"http://172.30.1.48:8081",
						"http://192.168.0.6:8081",
						"http://192.168.0.6",
						"http://remote.thefull.kr",
						"https://remote.thefull.kr",
						"http://localhost:5173")
				.allowedMethods("GET", "POST", "PUT", "DELETE", "OPTIONS")
				.allowedHeaders("Authorization", "x-refresh-token", "Content-Type")
				.exposedHeaders("Authorization", "x-refresh-token")
				.allowCredentials(true)
				.maxAge(3600);
	}

	@Override
	public void addResourceHandlers(ResourceHandlerRegistry registry) {
		registry.addResourceHandler(DEV_HANDLE)
				.addResourceLocations(DEV_PATH);
	}
}
