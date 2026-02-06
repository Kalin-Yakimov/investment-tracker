package com.kalin.investment_tracker.service;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.kalin.investment_tracker.model.CsItem;
import com.kalin.investment_tracker.repository.CsItemRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpMethod;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

@Service
@RequiredArgsConstructor
public class CsItemService {

    private final CsItemRepository csItemRepository;
    private final RestTemplate restTemplate;
    private final ObjectMapper objectMapper;

    public void fetchAndSaveItems() {
        String url = "https://cs2items.pro/api/buff163-ids";
        ResponseEntity<String> response = restTemplate.exchange(url, HttpMethod.GET, null, String.class);

        if (response.getBody() != null) {
            try {
                Map<String, Long> itemsMap = objectMapper.readValue(response.getBody(), new TypeReference<Map<String, Long>>() {});
                List<CsItem> csItems = new ArrayList<>();
                for (Map.Entry<String, Long> entry : itemsMap.entrySet()) {
                    csItems.add(CsItem.builder()
                            .id(entry.getValue())
                            .name(entry.getKey())
                            .build());
                }
                csItemRepository.saveAll(csItems);
            } catch (Exception e) {
                throw new RuntimeException("Failed to parse and save items", e);
            }
        }
    }

    public List<CsItem> searchItems(String query) {
        return csItemRepository.searchByName(query);
    }
}
