package com.kalin.investment_tracker.catalogue;

import com.kalin.investment_tracker.config.CacheConfig;
import lombok.RequiredArgsConstructor;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.util.HashSet;
import java.util.List;

@Service
@RequiredArgsConstructor
public class CataloguePersistenceService {
    private final CsItemRepository repository;

    @Transactional
    @CacheEvict(value = CacheConfig.CS_ITEMS_SEARCH_CACHE, allEntries = true)
    public int saveCatalogue(List<CsItem> items, boolean onlyMissing) {
        List<CsItem> selected = items;
        if (onlyMissing) {
            var existing = new HashSet<>(repository.findAllItemIds());
            selected = items.stream().filter(item -> !existing.contains(item.getId())).toList();
        }
        if (!selected.isEmpty()) repository.saveAllAndFlush(selected);
        return selected.size();
    }
}
