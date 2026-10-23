package com.kalin.investment_tracker.catalogue;

import java.util.List;

public interface CsItemSearchRepository {
    List<CsItem> searchCatalogue(CatalogueSearchQuery query, int limit);
}
