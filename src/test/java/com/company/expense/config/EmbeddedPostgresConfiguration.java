package com.company.expense.config;

import io.zonky.test.db.postgres.embedded.EmbeddedPostgres;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import javax.sql.DataSource;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.jdbc.DataSourceBuilder;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;

@TestConfiguration(proxyBeanMethods = false)
public class EmbeddedPostgresConfiguration {

    /**
     * Without a data directory every start gets a fresh, throwaway database (what tests need);
     * with one, data survives restarts.
     */
    @Bean(destroyMethod = "close")
    EmbeddedPostgres embeddedPostgres(@Value("${app.embedded-postgres.data-directory:}") String dataDirectory)
            throws IOException {
        if (dataDirectory.isBlank()) {
            return EmbeddedPostgres.start();
        }
        Path directory = Path.of(dataDirectory).toAbsolutePath();
        Files.createDirectories(directory);
        return EmbeddedPostgres.builder()
                .setDataDirectory(directory)
                .setCleanDataDirectory(false)
                .start();
    }

    @Bean
    DataSource dataSource(EmbeddedPostgres embeddedPostgres) {
        return DataSourceBuilder.create()
                .url(embeddedPostgres.getJdbcUrl("postgres", "postgres"))
                .build();
    }
}
