package com.kalin.investment_tracker.tracking;

public class DuplicateTrackedItemException extends RuntimeException {
    public DuplicateTrackedItemException() {
        super("You are already tracking this item");
    }
}
