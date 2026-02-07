package com.kalin.investment_tracker.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.kalin.investment_tracker.model.CsItem;
import com.kalin.investment_tracker.repository.CsItemRepository;
import com.kalin.investment_tracker.repository.CsItemSpecification;
import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.PageRequest;
import org.springframework.http.HttpMethod;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;

import java.util.ArrayList;
import java.util.List;

@Service
@RequiredArgsConstructor
@Slf4j
public class CsItemService {

    private final CsItemRepository csItemRepository;
    private final RestTemplate restTemplate;
    private final ObjectMapper objectMapper;

    @PostConstruct
    public void init() {
        log.info("Checking if CS items database is empty...");
        long count = csItemRepository.count();
        log.info("Current item count: {}", count);
        
        if (count == 0) {
            log.info("Database is empty. Starting fetch from API...");
            fetchAndSaveItems();
        } else {
            log.info("Database already populated. Skipping fetch.");
        }
    }

    public void fetchAndSaveItems() {
        String url = "https://raw.githubusercontent.com/ByMykel/CSGO-API/main/public/api/en/skins_not_grouped.json";
        try {
            log.info("Sending request to: {}", url);
            ResponseEntity<String> response = restTemplate.exchange(url, HttpMethod.GET, null, String.class);
            log.info("Response received. Status code: {}", response.getStatusCode());

            String jsonBody = response.getBody();
            if (jsonBody != null) {
                log.info("Raw JSON response (first 500 chars): {}", jsonBody.substring(0, Math.min(jsonBody.length(), 500)));
                
                JsonNode rootNode = objectMapper.readTree(jsonBody);
                log.info("Found {} items from API", rootNode.size());
                
                List<CsItem> csItems = new ArrayList<>();
                if (rootNode.isArray()) {
                    for (JsonNode node : rootNode) {
                        String id = node.path("id").asText();
                        String name = node.path("name").asText();
                        String image = node.path("image").asText();
                        
                        if (id != null && !id.isEmpty() && name != null && !name.isEmpty()) {
                             csItems.add(CsItem.builder()
                                    .id(id)
                                    .name(name)
                                    .image(image)
                                    .build());
                        }
                    }
                }
                
                log.info("Saving items to database...");
                csItemRepository.saveAll(csItems);
                log.info("Successfully saved {} items", csItems.size());
            } else {
                log.warn("Response body is null!");
            }
        } catch (Exception e) {
            log.error("Failed to fetch and save items", e);
            throw new RuntimeException("Failed to parse and save items", e);
        }
    }

    public List<CsItem> searchItems(String query) {
        return csItemRepository.findAll(CsItemSpecification.containsTextInName(query), PageRequest.of(0, 6)).getContent();
    }
}
