package com.kalin.investment_tracker;

import com.kalin.investment_tracker.model.Item;
import com.kalin.investment_tracker.repository.ItemRepository;
import com.kalin.investment_tracker.service.SteamPriceService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import java.util.List;

@SpringBootTest
class InvestmentTrackerApplicationTests {

	@Autowired
	private ItemRepository itemRepository;

	@Autowired
	private SteamPriceService steamPriceService;

	@Test
	void contextLoads() {
		System.out.println("=== DIAGNOSTIC START ===");
		try {
			List<Item> trackedItems = itemRepository.findAll().stream()
					.filter(item -> Boolean.TRUE.equals(item.getSteamFoundCheck()))
					.toList();
			System.out.println("Tracked items count: " + trackedItems.size());
			for (Item item : trackedItems) {
				System.out.println("Item: id=" + item.getId() + ", name='" + item.getItemName() + "', marketHashName='" + item.getMarketHashName() + "', price=" + item.getPrice());
				try {
					java.math.BigDecimal fetched = steamPriceService.fetchCurrentPrice(item.getMarketHashName());
					System.out.println(" -> Successfully fetched price from Steam: " + fetched);
				} catch (Exception e) {
					System.err.println(" -> FAILED to fetch price from Steam: " + e.getMessage());
					e.printStackTrace();
				}
			}
		} catch (Exception e) {
			e.printStackTrace();
		}
		System.out.println("=== DIAGNOSTIC END ===");
	}

}
