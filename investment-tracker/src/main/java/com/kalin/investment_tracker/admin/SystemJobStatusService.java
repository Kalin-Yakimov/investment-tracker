package com.kalin.investment_tracker.admin;

import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicBoolean;

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

    public boolean tryStart(String jobName, String message) {
        AtomicBoolean claimed = new AtomicBoolean();
        jobs.compute(jobName, (name, previous) -> {
            if (previous != null && "RUNNING".equals(previous.status())) return previous;
            claimed.set(true);
            return new AdminDtos.JobState("RUNNING", LocalDateTime.now(),
                    previous == null ? null : previous.completedAt(), message);
        });
        return claimed.get();
    }

    public void succeeded(String jobName, String message) {
        jobs.computeIfPresent(jobName, (name, previous) -> new AdminDtos.JobState(
                "SUCCESS", previous.startedAt(), LocalDateTime.now(), message));
    }

    public void failed(String jobName, String message) {
        jobs.computeIfPresent(jobName, (name, previous) -> new AdminDtos.JobState(
                "FAILED", previous.startedAt(), previous.completedAt(), message));
    }

    public AdminDtos.JobState get(String jobName) {
        return jobs.getOrDefault(jobName, new AdminDtos.JobState("UNKNOWN", null, null, null));
    }
}
