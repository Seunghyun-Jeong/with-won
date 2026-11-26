package com.withwon.global.config;

import javax.sql.DataSource;

import org.flywaydb.core.Flyway;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.jdbc.DataSourceBuilder;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class DataSourceConfig {

    @Bean
    public DataSource dataSource(
            @Value("${DB_HOST}") String host,
            @Value("${DB_PORT}") String port,
            @Value("${DB_NAME}") String name,
            @Value("${DB_USERNAME}") String username,
            @Value("${DB_PASSWORD}") String password) {

        String url = "jdbc:mysql://%s:%s/%s?sslMode=REQUIRED&connectionTimeZone=Asia/Seoul&characterEncoding=UTF-8"
                .formatted(host, port, name);

        DataSource dataSource = DataSourceBuilder.create()
                .url(url)
                .username(username)
                .password(password)
                .driverClassName("com.mysql.cj.jdbc.Driver")
                .build();

        Flyway.configure()
                .dataSource(dataSource)
                .locations("classpath:db/migration")
                .load()
                .migrate();

        return dataSource;
    }
}
