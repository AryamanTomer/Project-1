package com.bank.util;

import java.io.IOException;
import java.io.PrintWriter;
import java.io.StringWriter;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;

public final class AppLogger {
    private static final Path LOG_FILE = Path.of("logs", "bank-of-cli.log");
    private static final DateTimeFormatter TIME = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");

    private AppLogger() {

    }

    public static void info(String message){
        write("INFO", message, null);
    }

    public static void error(String message) {
        write("ERROR", message, null);
    }

    public static void error(String message, Throwable error){
        write("ERROR", message, error);
    }

    private static synchronized void write(String level, String message, Throwable error) {
        try {
            Files.createDirectories(LOG_FILE.getParent());
            StringBuilder line = new StringBuilder()
                    .append(TIME.format(LocalDateTime.now()))
                    .append(" ")
                    .append(level)
                    .append(" - ")
                    .append(message);
            if (error != null) {
                line.append(System.lineSeparator()).append(stackTrace(error));
            }
            line.append(System.lineSeparator());
            Files.writeString(LOG_FILE, line.toString(), StandardOpenOption.CREATE, StandardOpenOption.APPEND);
        } catch (IOException ignored) {
            // Don't let a log failure break a deposit
        }
    }

    private static String stackTrace(Throwable error) {
        StringWriter buffer = new StringWriter();
        error.printStackTrace(new PrintWriter(buffer));
        return buffer.toString();
    }

}
