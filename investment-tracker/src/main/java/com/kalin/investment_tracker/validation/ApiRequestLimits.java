package com.kalin.investment_tracker.validation;

public final class ApiRequestLimits {
    public static final int PAGE_SIZE = 100;
    public static final int SUPPORT_PAGE_SIZE = 50;
    public static final int PRICE_UPDATE_BATCH_SIZE = 1000;
    public static final int ITEM_TEXT_LENGTH = 255;

    private ApiRequestLimits() {
    }

    public static void requirePage(int page, int size, int maximumSize) {
        if (page < 0 || size < 1 || size > maximumSize) {
            throw new IllegalArgumentException("Page must be non-negative and size must be between 1 and " + maximumSize);
        }
    }
}
