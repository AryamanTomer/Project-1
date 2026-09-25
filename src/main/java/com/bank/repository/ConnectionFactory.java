package com.bank.repository;

import java.io.FileReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;
import java.util.Properties;

// We place and open the Postgres Connections here and test the bank.test.db so that we don't interfere with the live data
public class ConnectionFactory {
    private static final ConnectionFactory connectionFactory = new ConnectionFactory();
    private final Properties props = new Properties();

    private ConnectionFactory() {
        try {
            loadProperties();
        } catch (IOException e) {
            throw new IllegalStateException("Could not load db.properties", e);
        }
    }

    public static ConnectionFactory getConnectionFactory(){
        return connectionFactory;
    }

    public Connection getConnection(){
        try {
            return DriverManager.getConnection(
                url(),
                props.getProperty("DB_USER"),
                props.getProperty("DB_PASSWORD", "")

            );
        } catch (SQLException e) {
            throw new IllegalStateException("Could not connect to the database", e);
        }
    }
    // Tests on the backend that run TEST_DB_URL
    private String url() {
        if (Boolean.parseBoolean(System.getProperty("bank.test.db", "false"))) {
            String testUrl = props.getProperty("TEST_DB_URL");
            if(testUrl != null && !testUrl.isBlank()) {
                return testUrl;
            }
        }
        return props.getProperty("DB_URL");
    }

    private void loadProperties() throws IOException {
        try (FileReader reader = new FileReader("src/main/resources/db.properties")) {
            props.load(reader);
            return;
        } catch (IOException ignored) {
            //The files wasn't there so we try to copy Maven put on the classpath
        }
        try (InputStream in = ConnectionFactory.class.getResourceAsStream("/db.properties")){
            if(in == null) {
                throw new IOException("db.properties was not found");
            }
            props.load(new InputStreamReader(in, StandardCharsets.UTF_8));
        }
    }
}
