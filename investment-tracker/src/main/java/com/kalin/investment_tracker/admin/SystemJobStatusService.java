package com.kalin.investment_tracker.admin;

import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.concurrent.ConcurrentHashMap;

@Service
public class SystemJobStatusService {

    public static final String CATALOGUE_SYNC = "catalogueSync";
    public static final String PRICE_UPDATE = "priceUpdate";

    private final ConcurrentHashMap<String, AdminDtos.JobState> jobs = new ConcurrentHashMap<>();

    public SystemJobStatusService() {
        AdminDtos.JobState notRun = new AdminDtos.JobState(
                "NOT_RUN_SINCE_STARTUP",
                null,
                null,
                "No execution has been recorded since the application started"
        );
        jobs.put(CATALOGUE_SYNC, notRun);
        jobs.put(PRICE_UPDATE, notRun);
    }

    public void started(String jobName, String message) {
        jobs.put(jobName, new AdminDtos.JobState("RUNNING", LocalDateTime.now(), null, message));
    }

    public void succeeded(String jobName, String message) {
        AdminDtos.JobState previous = get(jobName);
        jobs.put(jobName, new AdminDtos.JobState(
                "SUCCESS",
                previous.startedAt(),
                LocalDateTime.now(),
                message
        ));
    }

    public void failed(String jobName, String message) {
        AdminDtos.JobState previous = get(jobName);
        jobs.put(jobName, new AdminDtos.JobState(
                "FAILED",
                previous.startedAt(),
                LocalDateTime.now(),
                message
        ));
    }

    public AdminDtos.JobState get(String jobName) {
        return jobs.getOrDefault(jobName, new AdminDtos.JobState("UNKNOWN", null, null, null));
    }
}
