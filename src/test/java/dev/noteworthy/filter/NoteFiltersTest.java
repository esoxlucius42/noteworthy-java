package dev.noteworthy.filter;

import dev.noteworthy.model.Note;
import dev.noteworthy.model.NoteStatus;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;

class NoteFiltersTest {
    @Test
    void searchesCaseInsensitivelyAndCanLimitSearchToTitles() {
        Note titleMatch = note("Meet the TEAM", "ordinary body", NoteStatus.TODO, Instant.now());
        Note bodyMatch = note("Other", "Discuss TEAM schedule", NoteStatus.TODO, Instant.now());
        List<Note> notes = List.of(titleMatch, bodyMatch);

        assertEquals(2, filter(notes, null, "team", false).size());
        assertEquals(List.of(titleMatch), filter(notes, null, "team", true));
    }

    @Test
    void statusAndCustomDateFiltersCombine() {
        ZoneId zone = ZoneId.systemDefault();
        LocalDate today = LocalDate.now();
        Note todayDone = note("Today", "", NoteStatus.DONE, today.atStartOfDay(zone).toInstant());
        Note todayTodo = note("Todo", "", NoteStatus.TODO, today.atStartOfDay(zone).toInstant());
        Note oldDone = note("Old", "", NoteStatus.DONE,
                today.minusDays(5).atStartOfDay(zone).toInstant());

        List<Note> results = NoteFilters.apply(List.of(todayDone, todayTodo, oldDone), NoteStatus.DONE,
                NoteFilters.DateRange.CUSTOM, today, today, "", false, NoteFilters.SortOrder.NEWEST);

        assertEquals(List.of(todayDone), results);
    }

    @Test
    void dateQuickRangesIncludeTodayAndPreviousSixDays() {
        ZoneId zone = ZoneId.systemDefault();
        LocalDate today = LocalDate.now();
        Note oldestIncluded = note("Included", "", NoteStatus.TODO,
                today.minusDays(6).atStartOfDay(zone).toInstant());
        Note excluded = note("Excluded", "", NoteStatus.TODO,
                today.minusDays(7).atStartOfDay(zone).toInstant());

        List<Note> results = NoteFilters.apply(List.of(excluded, oldestIncluded), null,
                NoteFilters.DateRange.LAST_7_DAYS, null, null, "", false,
                NoteFilters.SortOrder.NEWEST);

        assertEquals(List.of(oldestIncluded), results);
    }

    private List<Note> filter(List<Note> notes, NoteStatus status, String search, boolean titlesOnly) {
        return NoteFilters.apply(notes, status, NoteFilters.DateRange.ALL, null, null, search,
                titlesOnly, NoteFilters.SortOrder.NEWEST);
    }

    private Note note(String title, String body, NoteStatus status, Instant created) {
        return new Note(UUID.randomUUID(), title, body, created, created, status);
    }
}