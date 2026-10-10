package com.kalin.investment_tracker.service;

import com.kalin.investment_tracker.exeption.ExternalServiceException;
import com.kalin.investment_tracker.model.MarketPriceSnapshot;
import com.kalin.investment_tracker.repository.ItemRepository;
import com.kalin.investment_tracker.repository.MarketPriceSnapshotRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.*;

@Service
@RequiredArgsConstructor
public class MarketPricePersistenceService {
    private static final int QUERY_BATCH_SIZE = 500;
    private static final String SOURCE = MarketPriceSnapshot.SOURCE_CSGOTRADER_24H;
    private final ItemRepository items;
    private final MarketPriceSnapshotRepository snapshots;

    public record RecordedPublication(BigDecimal rate, Instant observedAt) { }

    @Transactional(readOnly = true)
    public Optional<RecordedPublication> findRecordedPublication(String version) {
        return snapshots.findFirstBySourceAndSourceVersionOrderByIdAsc(SOURCE, version)
                .map(snapshot -> new RecordedPublication(snapshot.getUsdToEurRate(), snapshot.getObservedAt()));
    }

    @Transactional(readOnly = true)
    public Optional<BigDecimal> findLastKnownRate() {
        return snapshots.findFirstBySourceOrderByCreatedAtDescIdDesc(SOURCE).map(MarketPriceSnapshot::getUsdToEurRate);
    }

    @Transactional
    public int persistFeed(PriceFeedSnapshot feed) {
        return apply(feed, items.findDistinctTrackedMarketHashNames(), null, false);
    }

    @Transactional
    public int applySelected(PriceFeedSnapshot feed, List<Long> ids) {
        int recorded = 0;
        for (int start = 0; start < ids.size(); start += QUERY_BATCH_SIZE) {
            List<Long> batch = ids.subList(start, Math.min(start + QUERY_BATCH_SIZE, ids.size()));
            recorded += apply(feed, items.findDistinctTrackedMarketHashNamesByIds(batch), batch, false);
        }
        return recorded;
    }

    @Transactional
    public int applyDashboard(PriceFeedSnapshot feed) {
        return apply(feed, items.findDistinctDashboardMarketHashNames(), null, true);
    }

    private int apply(PriceFeedSnapshot feed, List<String> marketNames, List<Long> ids, boolean dashboardOnly) {
        List<String> usableNames = marketNames.stream().distinct().filter(feed.pricesUsd()::containsKey).toList();
        int recorded = 0;
        for (int start = 0; start < usableNames.size(); start += QUERY_BATCH_SIZE) {
            List<String> batch = usableNames.subList(start, Math.min(start + QUERY_BATCH_SIZE, usableNames.size()));
            Map<String, MarketPriceSnapshot> existing = new HashMap<>();
            snapshots.findBySourceAndSourceVersionAndMarketHashNameIn(SOURCE, feed.sourceVersion(), batch)
                    .forEach(snapshot -> existing.put(snapshot.getMarketHashName(), snapshot));
            List<MarketPriceSnapshot> pending = new ArrayList<>();
            for (String name : batch) {
                MarketPriceSnapshot previous = existing.get(name);
                if (previous != null && previous.getPriceUsd().compareTo(feed.pricesUsd().get(name)) != 0) {
                    throw new ExternalServiceException("The price publication changed without a new source version");
                }
                BigDecimal eur = previous == null ? feed.priceEur(name) : previous.getPriceEur();
                if (ids != null) items.updateSelectedTrackedPrice(name, eur, ids);
                else if (dashboardOnly) items.updateDashboardTrackedPrice(name, eur);
                else items.updateTrackedPrice(name, eur);
                if (previous == null) {
                    pending.add(new MarketPriceSnapshot(name, SOURCE, feed.sourceVersion(), feed.observedAt(),
                            feed.pricesUsd().get(name), feed.usdToEurRate(), eur));
                }
            }
            if (!pending.isEmpty()) {
                snapshots.saveAllAndFlush(pending);
                recorded += pending.size();
            }
        }
        return recorded;
    }
}
