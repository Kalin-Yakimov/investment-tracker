package com.kalin.investment_tracker.pricing;

import com.kalin.investment_tracker.exception.ExternalServiceException;
import com.kalin.investment_tracker.item.ItemRepository;
import com.kalin.investment_tracker.item.Item;
import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.annotation.Propagation;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.*;

@Service
@RequiredArgsConstructor
public class MarketPricePersistenceService {
    private static final int QUERY_BATCH_SIZE = 500;
    private static final String SOURCE = MarketPriceSnapshot.SOURCE_CSGOTRADER_24H;
    private final ItemRepository items;
    private final MarketPriceSnapshotRepository snapshots;
    private final MarketPriceWriteLockRepository writeLocks;

    @PostConstruct
    void initializeWriteLock() {
        if (!writeLocks.existsById(SOURCE)) {
            try {
                // Repository transaction completes independently of later feed writes.
                writeLocks.saveAndFlush(new MarketPriceWriteLock(SOURCE));
            } catch (DataIntegrityViolationException concurrentStartup) {
                if (!writeLocks.existsById(SOURCE)) throw concurrentStartup;
            }
        }
    }

    public record RecordedPublication(BigDecimal rate, Instant observedAt) { }

    @Transactional(readOnly = true)
    public Optional<RecordedPublication> findRecordedPublication(String version) {
        return snapshots.findFirstBySourceAndSourceVersionOrderByIdAsc(SOURCE, version)
                .map(snapshot -> new RecordedPublication(snapshot.getUsdToEurRate(), snapshot.getObservedAt()));
    }

    @Transactional(readOnly = true)
    public Optional<BigDecimal> findLastKnownRate() {
        return snapshots.findFirstBySourceOrderByObservedAtDescIdDesc(SOURCE).map(MarketPriceSnapshot::getUsdToEurRate);
    }

    @Transactional(readOnly = true)
    public Optional<Instant> findLatestObservationTime() {
        return Optional.ofNullable(snapshots.findLatestObservedAtBySource(SOURCE));
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public int persistFeed(PriceFeedSnapshot feed) {
        lockAndValidatePublication(feed);
        return apply(feed, items.findDistinctTrackedMarketHashNames(), null, false);
    }

    @Transactional(propagation = Propagation.MANDATORY)
    public void lockForTrackingCreation() {
        writeLocks.lockSource(SOURCE)
                .orElseThrow(() -> new IllegalStateException("The price-feed write guard has not been initialized"));
    }

    @Transactional(propagation = Propagation.MANDATORY)
    public void initializeTrackedPrice(Item item, PriceFeedSnapshot feed) {
        lockForTrackingCreation();
        Instant latest = snapshots.findLatestObservedAtBySource(SOURCE);
        if (feed != null && feed.priceEur(item.getMarketHashName()) != null
                && (latest == null || !feed.observedAt().isBefore(latest))) {
            validatePublication(feed, latest);
            // No HTTP and no cache publication here. A new shared observation joins this insert transaction.
            apply(feed, List.of(item.getMarketHashName()), null, false);
            item.setPrice(feed.priceEur(item.getMarketHashName()));
        } else {
            // A cold/offline process or another instance's newer publication must not insert an old price.
            snapshots.findFirstByMarketHashNameAndSourceOrderByObservedAtDescIdDesc(item.getMarketHashName(), SOURCE)
                    .ifPresent(snapshot -> item.setPrice(snapshot.getPriceEur()));
        }
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public int applySelected(PriceFeedSnapshot feed, List<Long> ids) {
        lockAndValidatePublication(feed);
        int recorded = 0;
        for (int start = 0; start < ids.size(); start += QUERY_BATCH_SIZE) {
            List<Long> batch = ids.subList(start, Math.min(start + QUERY_BATCH_SIZE, ids.size()));
            recorded += apply(feed, items.findDistinctTrackedMarketHashNamesByIds(batch), batch, false);
        }
        return recorded;
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public int applyDashboard(PriceFeedSnapshot feed) {
        lockAndValidatePublication(feed);
        return apply(feed, items.findDistinctDashboardMarketHashNames(), null, true);
    }

    private void lockAndValidatePublication(PriceFeedSnapshot feed) {
        lockForTrackingCreation();
        validatePublication(feed, snapshots.findLatestObservedAtBySource(SOURCE));
    }

    private void validatePublication(PriceFeedSnapshot feed, Instant latest) {
        if (latest != null && feed.observedAt().isBefore(latest)) {
            throw new ExternalServiceException("An older publication cannot replace already recorded prices");
        }
        snapshots.findFirstBySourceAndSourceVersionOrderByIdAsc(SOURCE, feed.sourceVersion()).ifPresent(recorded -> {
            if (recorded.getUsdToEurRate().compareTo(feed.usdToEurRate()) != 0
                    || !recorded.getObservedAt().equals(feed.observedAt())) {
                throw new ExternalServiceException("The publication conversion rate and date have already been recorded");
            }
        });
    }

    private int apply(PriceFeedSnapshot feed, List<String> marketNames, List<Long> ids, boolean dashboardOnly) {
        LocalDate day = feed.observedAt().atOffset(ZoneOffset.UTC).toLocalDate();
        List<String> usableNames = marketNames.stream().distinct().filter(feed.pricesUsd()::containsKey).toList();
        int recorded = 0;
        for (int start = 0; start < usableNames.size(); start += QUERY_BATCH_SIZE) {
            List<String> batch = usableNames.subList(start, Math.min(start + QUERY_BATCH_SIZE, usableNames.size()));
            Map<String, MarketPriceSnapshot> existing = new HashMap<>();
            snapshots.findBySourceAndObservationDateAndMarketHashNameIn(SOURCE, day, batch)
                    .forEach(snapshot -> existing.put(snapshot.getMarketHashName(), snapshot));
            List<MarketPriceSnapshot> pending = new ArrayList<>();
            for (String name : batch) {
                MarketPriceSnapshot previous = existing.get(name);
                boolean samePublication = previous != null && previous.getSourceVersion().equals(feed.sourceVersion());
                if (samePublication && previous.getPriceUsd().compareTo(feed.pricesUsd().get(name)) != 0) {
                    throw new ExternalServiceException("The price publication changed without a new source version");
                }
                if (previous != null && !samePublication && !feed.observedAt().isAfter(previous.getObservedAt())) {
                    throw new ExternalServiceException("A daily observation must come from a newer publication");
                }
                // Concurrent receipt of the same publication must not silently pick a different FX rate.
                if (samePublication && previous.getUsdToEurRate().compareTo(feed.usdToEurRate()) != 0) {
                    throw new ExternalServiceException("The publication conversion rate has already been recorded");
                }
                BigDecimal eur = samePublication ? previous.getPriceEur() : feed.priceEur(name);
                if (ids != null) items.updateSelectedTrackedPrice(name, eur, ids);
                else if (dashboardOnly) items.updateDashboardTrackedPrice(name, eur);
                else items.updateTrackedPrice(name, eur);
                if (previous == null) {
                    pending.add(new MarketPriceSnapshot(name, SOURCE, feed.sourceVersion(), feed.observedAt(),
                            feed.pricesUsd().get(name), feed.usdToEurRate(), eur));
                } else if (!samePublication) {
                    previous.setSourceVersion(feed.sourceVersion());
                    previous.setObservedAt(feed.observedAt());
                    previous.setPriceUsd(feed.pricesUsd().get(name));
                    previous.setUsdToEurRate(feed.usdToEurRate());
                    previous.setPriceEur(eur);
                    pending.add(previous);
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
