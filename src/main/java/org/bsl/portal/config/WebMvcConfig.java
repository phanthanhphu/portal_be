package org.bsl.portal.config;

import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.EnableWebMvc;
import org.springframework.web.servlet.config.annotation.ResourceHandlerRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

@Configuration
@EnableWebMvc
public class WebMvcConfig implements WebMvcConfigurer {

    @Override
    public void addResourceHandlers(ResourceHandlerRegistry registry) {

        // Map đường dẫn /uploads/** -> thư mục uploads ngoài project
        registry.addResourceHandler("/uploads/**")
                .addResourceLocations("file:uploads/");

        // Map đường dẫn /files/** -> thư mục files ngoài project
        registry.addResourceHandler("/files/**")
                .addResourceLocations("file:./files/");
    }
}