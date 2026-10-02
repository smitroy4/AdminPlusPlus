package com.smit.taskportal.service;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;
import jakarta.annotation.PostConstruct;

/**
 * Hands out sequential, human readable task references ({@code TASK-00001}).
 *
 * <p>Uses a dedicated PostgreSQL sequence rather than {@code max(id)+1} so that
 * concurrent task creation can never produce a duplicate number.
 */
@Component
public class TaskNoGenerator {

    static final String SEQUENCE_NAME = "task_no_seq";

    private final JdbcTemplate jdbcTemplate;

    public TaskNoGenerator(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    @PostConstruct
    void createSequenceIfMissing() {
        jdbcTemplate.execute("CREATE SEQUENCE IF NOT EXISTS " + SEQUENCE_NAME + " START WITH 1 INCREMENT BY 1");
    }

    /** @return the next value, e.g. {@code TASK-00042}. */
    public String next() {
        Long value = jdbcTemplate.queryForObject("SELECT nextval('" + SEQUENCE_NAME + "')", Long.class);
        return "TASK-%05d".formatted(value == null ? 0L : value);
    }
}
