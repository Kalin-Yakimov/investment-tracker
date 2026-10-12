package com.kalin.investment_tracker.item;

import com.kalin.investment_tracker.catalogue.CsItemRepository;

import com.kalin.investment_tracker.catalogue.CsItem;
import com.kalin.investment_tracker.user.User;
import com.kalin.investment_tracker.user.UserRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.boot.test.autoconfigure.orm.jpa.TestEntityManager;
import org.springframework.data.domain.PageRequest;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;

import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

@DataJpaTest
class ItemCatalogueRelationTest {

    @Autowired
    private ItemRepository itemRepository;

    @Autowired
    private CsItemRepository csItemRepository;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private TestEntityManager entityManager;

    @Test
    void deletingCatalogueEntryKeepsUserItemAndClearsRelation() {
        CsItem catalogueItem = saveCatalogueItem("skin-relation-1");
        Item item = new Item();
        item.setItemName(catalogueItem.getName());
        item.setCatalogueItem(catalogueItem);
        item.setQuantity(1);
        Long itemId = itemRepository.saveAndFlush(item).getId();

        entityManager.clear();
        csItemRepository.deleteById(catalogueItem.getId());
        csItemRepository.flush();
        entityManager.clear();

        Item persistedItem = itemRepository.findById(itemId).orElseThrow();
        assertNull(persistedItem.getCatalogueItem());
    }

    @Test
    void deletingUserItemDoesNotDeleteCatalogueEntry() {
        CsItem catalogueItem = saveCatalogueItem("skin-relation-2");
        Item item = new Item();
        item.setItemName(catalogueItem.getName());
        item.setCatalogueItem(catalogueItem);
        item.setQuantity(1);
        Item savedItem = itemRepository.saveAndFlush(item);

        itemRepository.delete(savedItem);
        itemRepository.flush();
        entityManager.clear();

        assertTrue(csItemRepository.existsById(catalogueItem.getId()));
        assertNotNull(csItemRepository.findById(catalogueItem.getId()).orElseThrow());
    }

    @Test
    void portfolioQueryLoadsCatalogueRelationForDtoMapping() {
        CsItem catalogueItem = saveCatalogueItem("skin-relation-3");
        User user = new User();
        user.setEmail("catalogue-relation@example.com");
        user.setPassword("not-used-in-this-test");
        user.setRoles(new ArrayList<>());
        user.setCreatedDate(LocalDateTime.now());
        user = userRepository.saveAndFlush(user);

        Item item = new Item();
        item.setItemName(catalogueItem.getName());
        item.setCatalogueItem(catalogueItem);
        item.setItemStatus(ItemStatus.BOUGHT);
        item.setDate(LocalDate.now());
        item.setQuantity(1);
        item.setSteamFoundCheck(false);
        item.setUser(user);
        itemRepository.saveAndFlush(item);
        entityManager.clear();

        Item loadedItem = itemRepository.findByUserIdAndOptionalStatus(
                user.getId(),
                null,
                PageRequest.of(0, 10)
        ).getContent().getFirst();

        assertNotNull(loadedItem.getCatalogueItem());
        assertNotNull(loadedItem.getCatalogueItem().getImage());
    }

    private CsItem saveCatalogueItem(String id) {
        return csItemRepository.saveAndFlush(CsItem.builder()
                .id(id)
                .name("AK-47 | Redline (Field-Tested)")
                .marketHashName("AK-47 | Redline (Field-Tested)")
                .image("image-url")
                .build());
    }
}
