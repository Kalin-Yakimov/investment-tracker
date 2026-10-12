package com.kalin.investment_tracker.catalogue;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;

import java.util.List;
import java.util.stream.IntStream;

import static org.junit.jupiter.api.Assertions.*;

@DataJpaTest(properties = {
        "spring.jpa.hibernate.ddl-auto=create-drop",
        "spring.jpa.properties.hibernate.dialect=org.hibernate.dialect.H2Dialect"
}, showSql = false)
class CsItemSearchRepositoryTest {
    @Autowired CsItemRepository repository;

    @Test
    void akMatchesTheWeaponWordNotSnakebiteOrStatTrak() {
        save("skin-ak", "AK-47 | Redline (Field-Tested)");
        save("skin-st", "StatTrak™ AK-47 | Redline (Field-Tested)");
        save("crate-snake", "Snakebite Case");
        save("music-st", "StatTrak™ Music Kit | Red Artist, New Beat");
        save("skin-awp", "StatTrak™ AWP | Redline (Field-Tested)");
        assertEquals(List.of("skin-ak", "skin-st"), ids("ak"));
        assertEquals(List.of("skin-ak", "skin-st"), ids("ak re"));
        assertEquals(List.of("skin-ak", "skin-st"), ids("ak redline"));
    }

    @ParameterizedTest
    @ValueSource(strings = {"m4a1s hyper", "M4A1-S | Hyper", "m4a1 s hyper", "hyper m4a1s"})
    void compoundWeaponNamesAndWordOrderWorkGenerically(String query) {
        save("skin-m4", "M4A1-S | Hyper Beast (Minimal Wear)");
        save("skin-awp", "AWP | Hyper Beast (Minimal Wear)");
        assertEquals(List.of("skin-m4"), ids(query));
    }

    @ParameterizedTest
    @ValueSource(strings = {"fiveseven heat", "five-seven heat", "five seven heat"})
    void hyphenHandlingIsNotAnAkSpecificAlias(String query) {
        save("skin-five", "Five-SeveN | Heat Treated (Field-Tested)");
        save("skin-ak", "AK-47 | Heat Treated (Field-Tested)");
        assertEquals(List.of("skin-five"), ids(query));
    }

    @Test
    void stickerTeamFinishAndYearCanBeCombined() {
        save("sticker-team", "Sticker | Team Spirit (Holo) | Stockholm 2021");
        save("sticker-other", "Sticker | Team Spirit (Foil) | Stockholm 2021");
        save("skin-spirit", "AK-47 | Spirit (Factory New)");
        assertEquals(List.of("sticker-team"), ids("spirit holo 2021"));
        assertEquals(List.of("sticker-team"), ids("sticker team sp ho"));
    }

    @Test
    void casesMusicAndOtherNamedItemsUseTheSameRules() {
        save("crate-revolution", "Revolution Case");
        save("crate-revolver", "Revolver Case");
        save("music-dash", "Music Kit | Test Artist, High-Voltage");
        save("agent-1", "Special Agent | Elite Crew");
        assertEquals(List.of("crate-revolution"), ids("rev case revolution"));
        assertEquals(List.of("music-dash"), ids("music kit highvoltage"));
        assertEquals(List.of("music-dash"), ids("music artist high voltage"));
        assertEquals(List.of("agent-1"), ids("agent elite"));
    }

    @Test
    void exactWordAndSubjectMatchesRankBeforeSubstringCompletions() {
        save("case-exact", "Revolution Case");
        save("case-long", "Revolution Case Extended");
        save("skin-long", "AK-47 | Neon Revolution (Field-Tested)");
        assertEquals("case-exact", ids("Revolution Case").getFirst());
        assertEquals("case-exact", ids("rev").getFirst());
    }

    @Test
    void rankIsAppliedToAllMatchesBeforeTheEightResultLimit() {
        IntStream.range(0, 80).forEach(index -> save("aaa-" + index, "Sticker | Revolution Fan " + index));
        save("zzz-target", "Revolution Case");
        var result = ids("rev");
        assertEquals(8, result.size());
        assertEquals("zzz-target", result.getFirst());
    }

    @Test
    void stableIdTieBreakMakesIdenticalNamesDeterministic() {
        save("skin-b", "P250 | Test Finish (Factory New)");
        save("skin-a", "P250 | Test Finish (Factory New)");
        assertEquals(List.of("skin-a", "skin-b"), ids("p250 test"));
        assertEquals(ids("p250 test"), ids("p250 test"));
    }

    @Test
    void marketNamesAreSearchableButWordsCannotBeSplitAcrossUnrelatedAliases() {
        repository.saveAndFlush(CsItem.builder().id("skin-alias").name("Friendly display name")
                .marketHashName("P250 | Metallic DDPAT (Field-Tested)").image("image").build());
        assertEquals(List.of("skin-alias"), ids("p250 metallic"));
        assertTrue(ids("friendly metallic").isEmpty());
    }

    @Test
    void excludedCategoriesRemainExcludedWithoutDeletingAnyRows() {
        save("graffiti-1", "Revolution Graffiti");
        save("key-1", "Revolution Case Key");
        save("collectible-1", "Revolution Pin");
        save("collection-1", "Revolution Collection");
        save("crate-1", "Revolution Sticker Collection");
        save("crate-allowed", "Revolution Case");
        assertEquals(List.of("crate-allowed"), ids("rev"));
        assertEquals(6, repository.count());
    }

    @Test
    void unicodeNamesRemainSearchableAndNullMarketNamesAreSafe() {
        repository.saveAndFlush(CsItem.builder().id("music-unicode").name("Music Kit | Café Artist, Élan")
                .image("image").build());
        assertEquals(List.of("music-unicode"), ids("CAFÉ élan"));
    }

    @Test
    void whitespacePunctuationAndEmptyQueriesDoNotReturnUnrelatedItems() {
        save("case-1", "Revolution Case");
        assertEquals(List.of("case-1"), ids("  REVOLUTION | (case)  "));
        assertTrue(ids("%_").isEmpty());
        assertTrue(ids("").isEmpty());
        assertTrue(ids("missing item").isEmpty());
    }

    private void save(String id, String name) {
        repository.saveAndFlush(CsItem.builder().id(id).name(name).marketHashName(name).image("image").build());
    }

    private List<String> ids(String text) {
        return repository.searchCatalogue(CatalogueSearchQuery.parse(text), 8).stream().map(CsItem::getId).toList();
    }
}
