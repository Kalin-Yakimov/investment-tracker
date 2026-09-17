package com.kalin.investment_tracker.service;

import com.kalin.investment_tracker.exeption.ItemNotFoundException;
import com.kalin.investment_tracker.dto.PortfolioItemRequest;
import com.kalin.investment_tracker.model.Item;
import com.kalin.investment_tracker.model.ItemStatus;
import com.kalin.investment_tracker.repository.CsItemRepository;
import com.kalin.investment_tracker.repository.ItemRepository;
import com.kalin.investment_tracker.user.User;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;

import java.util.Optional;
import java.math.BigDecimal;
import java.time.LocalDate;

import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ItemServiceAuthorizationTest {

    @Mock
    private ItemRepository itemRepository;

    @Mock
    private CsItemRepository csItemRepository;

    @InjectMocks
    private ItemService itemService;

    private User authenticatedUser;

    @BeforeEach
    void setUp() {
        authenticatedUser = new User();
        authenticatedUser.setId(7);
    }

    @Test
    void getItemByIdRejectsAnItemThatDoesNotBelongToAuthenticatedUser() {
        when(itemRepository.findByIdAndUserId(42L, 7)).thenReturn(Optional.empty());

        assertThrows(
                ItemNotFoundException.class,
                () -> itemService.getItemById(42L, authenticatedUser)
        );

        verify(itemRepository).findByIdAndUserId(42L, 7);
        verify(itemRepository, never()).findById(42L);
    }

    @Test
    void updateItemRejectsAnItemThatDoesNotBelongToAuthenticatedUser() {
        when(itemRepository.findByIdAndUserId(42L, 7)).thenReturn(Optional.empty());

        assertThrows(
                ItemNotFoundException.class,
                () -> itemService.updateItem(
                        42L,
                        new PortfolioItemRequest(
                                "AK-47 | Redline",
                                null,
                                ItemStatus.BOUGHT,
                                LocalDate.now(),
                                1,
                                BigDecimal.ONE
                        ),
                        authenticatedUser
                )
        );

        verify(itemRepository, never()).save(org.mockito.ArgumentMatchers.any(Item.class));
    }

    @Test
    void deleteItemRejectsAnItemThatDoesNotBelongToAuthenticatedUser() {
        when(itemRepository.findByIdAndUserId(42L, 7)).thenReturn(Optional.empty());

        assertThrows(
                ItemNotFoundException.class,
                () -> itemService.deleteItem(42L, authenticatedUser)
        );

        verify(itemRepository, never()).delete(org.mockito.ArgumentMatchers.any(Item.class));
    }

    @Test
    void highestTotalPriceIsSortedInDatabaseBeforePagination() {
        PageRequest pageable = PageRequest.of(0, 10);
        when(itemRepository.findPortfolioOrderByTotalPriceDesc(7, pageable))
                .thenReturn(Page.empty(pageable));

        itemService.getUserItems(authenticatedUser, 0, 10, "highest_total_price");

        verify(itemRepository).findPortfolioOrderByTotalPriceDesc(7, pageable);
        verify(itemRepository, never()).findByUserIdAndOptionalStatus(
                org.mockito.ArgumentMatchers.anyInt(),
                org.mockito.ArgumentMatchers.any(),
                org.mockito.ArgumentMatchers.any());
    }

    @Test
    void lowestTotalPriceIsSortedInDatabaseBeforePagination() {
        PageRequest pageable = PageRequest.of(1, 10);
        when(itemRepository.findPortfolioOrderByTotalPriceAsc(7, pageable))
                .thenReturn(Page.empty(pageable));

        itemService.getUserItems(authenticatedUser, 1, 10, "lowest_total_price");

        verify(itemRepository).findPortfolioOrderByTotalPriceAsc(7, pageable);
        verify(itemRepository, never()).findByUserIdAndOptionalStatus(
                org.mockito.ArgumentMatchers.anyInt(),
                org.mockito.ArgumentMatchers.any(),
                org.mockito.ArgumentMatchers.any());
    }
}
