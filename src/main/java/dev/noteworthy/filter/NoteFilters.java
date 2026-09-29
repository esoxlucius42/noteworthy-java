package dev.noteworthy.filter;

import dev.noteworthy.model.Note;
import dev.noteworthy.model.NoteStatus;

import java.time.LocalDate;
import java.time.ZoneId;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.Objects;

public final class NoteFilters {
    public enum DateRange { ALL, TODAY, LAST_7_DAYS, LAST_30_DAYS, CUSTOM }
    public enum SortOrder { NEWEST, OLDEST, STATUS }

    private NoteFilters() { }

    public static List<Note> apply(
            List<Note> notes,
            NoteStatus status,
            DateRange dateRange,
            LocalDate customStart,
            LocalDate customEnd,
            String search,
            boolean titlesOnly,
            SortOrder sortOrder) {
        Objects.requireNonNull(notes, "notes");
        Objects.requireNonNull(dateRange, "dateRange");
        Objects.requireNonNull(sortOrder, "sortOrder");
        LocalDate today = LocalDate.now();
        LocalDate start = switch (dateRange) {
            case TODAY -> today;
            case LAST_7_DAYS -> today.minusDays(6);
            case LAST_30_DAYS -> today.minusDays(29);
            case CUSTOM -> customStart;
            case ALL -> null;
        };
        LocalDate end = switch (dateRange) {
            case TODAY, LAST_7_DAYS, LAST_30_DAYS -> today;
            case CUSTOM -> customEnd;
            case ALL -> null;
        };
        String query = search == null ? "" : search.strip().toLowerCase(Locale.ROOT);
        ZoneId zone = ZoneId.systemDefault();

        Comparator<Note> comparator = switch (sortOrder) {
            case NEWEST -> Comparator.comparing(Note::createdAt).reversed();
            case OLDEST -> Comparator.comparing(Note::createdAt);
                case STATUS -> Comparator.comparing(Note::status)
                    .thenComparing(Note::createdAt, Comparator.reverseOrder());
        };

        return notes.stream()
                .filter(note -> status == null || note.status() == status)
                .filter(note -> {
                    if (start == null || end == null) {
                        return dateRange != DateRange.CUSTOM;
                    }
                    LocalDate created = note.createdAt().atZone(zone).toLocalDate();
                    return !created.isBefore(start) && !created.isAfter(end);
                })
                .filter(note -> query.isEmpty()
                        || note.displayTitle().toLowerCase(Locale.ROOT).contains(query)
                        || (!titlesOnly && note.body().toLowerCase(Locale.ROOT).contains(query)))
                .sorted(comparator)
                .toList();
    }
}