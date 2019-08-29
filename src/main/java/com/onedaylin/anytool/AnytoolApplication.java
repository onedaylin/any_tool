package com.onedaylin.anytool;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.builder.SpringApplicationBuilder;
import org.springframework.boot.web.servlet.support.SpringBootServletInitializer;

/**
 * @author onedaylin@outlook.com
 */
@SpringBootApplication
public class AnytoolApplication extends SpringBootServletInitializer {

    private static Logger logger = LoggerFactory.getLogger(AnytoolApplication.class);

    @Override
    protected SpringApplicationBuilder configure(SpringApplicationBuilder builder) {
        return builder.sources(AnytoolApplication.class);
    }

    public static void main(String[] args) {
        SpringApplication.run(AnytoolApplication.class, args);
    }

}
