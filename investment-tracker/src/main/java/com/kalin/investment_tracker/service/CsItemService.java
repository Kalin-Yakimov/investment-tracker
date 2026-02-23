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
import java.util.Iterator;
import java.util.List;
import java.util.Map;

@Service
@RequiredArgsConstructor
@Slf4j
public class CsItemService {

    private final CsItemRepository csItemRepository;
    private final RestTemplate restTemplate;
    private final ObjectMapper objectMapper;

    @PostConstruct
    public void init() {
        log.info("Starting CS items database synchronization...");
        fetchAndSaveItems();
    }

    public void fetchAndSaveItems() {
        String url = "https://raw.githubusercontent.com/ByMykel/CSGO-API/main/public/api/en/all.json";
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
                Iterator<Map.Entry<String, JsonNode>> fields = rootNode.fields();
                while (fields.hasNext()) {
                    Map.Entry<String, JsonNode> entry = fields.next();
                    JsonNode node = entry.getValue();
                    
                    String id = node.path("id").asText();
                    String name = node.path("name").asText();
                    String image = node.path("image").asText();
                    
                    // We only care about items that have a name and image, e.g., skins, stickers, agents
                    if (id != null && !id.isEmpty() && name != null && !name.isEmpty() && image != null && !image.isEmpty()) {
                         csItems.add(CsItem.builder()
                                .id(id)
                                .name(name)
                                .image(image)
                                .build());
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
