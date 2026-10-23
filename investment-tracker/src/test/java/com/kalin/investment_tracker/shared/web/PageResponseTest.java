package com.kalin.investment_tracker.shared.web;

import tools.jackson.databind.ObjectMapper;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;

class PageResponseTest {
    private final ObjectMapper json = new ObjectMapper();

    @ParameterizedTest
    @CsvSource({"0,0,0,true,true", "0,5,3,true,false", "1,5,2,false,true"})
    void sharedResponseRetainsAllPaginationFields(int number, long total, int count,
                                                boolean first, boolean last) {
        List<String> content = java.util.stream.IntStream.range(0, count)
                .mapToObj(i -> "item-" + i).toList();
        Page<String> page = new PageImpl<>(content, PageRequest.of(number, 3), total);

        var actual = json.valueToTree(PageResponse.from(page));
        var expected = json.valueToTree(Map.of(
                "content", content, "number", number, "size", 3,
                "totalElements", total, "totalPages", total == 0 ? 0 : 2,
                "first", first, "last", last));

        assertEquals(expected, actual);
    }
}
