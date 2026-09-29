package dev.noteworthy.ui;

import dev.noteworthy.filter.NoteFilters;
import dev.noteworthy.model.Note;
import dev.noteworthy.model.GroupColor;
import dev.noteworthy.model.NoteGroup;
import dev.noteworthy.model.NoteStatus;

import javax.swing.AbstractAction;
import javax.swing.BorderFactory;
import javax.swing.Box;
import javax.swing.BoxLayout;
import javax.swing.DefaultListCellRenderer;
import javax.swing.DefaultListModel;
import javax.swing.JButton;
import javax.swing.JCheckBox;
import javax.swing.JComboBox;
import javax.swing.JLabel;
import javax.swing.JList;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JSplitPane;
import javax.swing.JTextArea;
import javax.swing.JTextField;
import javax.swing.KeyStroke;
import javax.swing.ListSelectionModel;
import javax.swing.SwingConstants;
import javax.swing.Timer;
import javax.swing.text.JTextComponent;
import javax.swing.plaf.basic.BasicComboBoxUI;
import java.awt.BorderLayout;
import java.awt.CardLayout;
import java.awt.Color;
import java.awt.Component;
import java.awt.event.ComponentAdapter;
import java.awt.event.ComponentEvent;
import java.awt.Dimension;
import java.awt.Font;
import java.awt.Graphics;
import java.awt.GridBagConstraints;
import java.awt.GridBagLayout;
import java.awt.Insets;
import java.awt.Rectangle;
import java.awt.event.InputEvent;
import java.awt.event.KeyEvent;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.time.format.ResolverStyle;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.function.Consumer;

public final class GroupPanel extends JPanel {
    private static final DateTimeFormatter DISPLAY_DATE = DateTimeFormatter.ofPattern("dd.MM.yyyy");
        private static final DateTimeFormatter CUSTOM_DATE = DateTimeFormatter.ofPattern("dd.MM.uuuu")
            .withResolverStyle(ResolverStyle.STRICT);
    private static final DateTimeFormatter DETAIL_DATE = DateTimeFormatter.ofPattern("dd.MM.yyyy  HH:mm");

    private NoteGroup group;
    private final Consumer<NoteGroup> onChange;
    private final DefaultListModel<Note> listModel = new DefaultListModel<>();
    private final JList<Note> noteList = new JList<>(listModel);
    private final JTextField searchField = new JTextField();
    private final JCheckBox titlesOnly = new JCheckBox("Titles only");
    private final JComboBox<Object> statusFilter = new JComboBox<>();
    private final JComboBox<NoteFilters.DateRange> dateFilter = new JComboBox<>(NoteFilters.DateRange.values());
    private final JComboBox<NoteFilters.SortOrder> sortOrder = new JComboBox<>(NoteFilters.SortOrder.values());
    private final JTextField startDate = new JTextField(8);
    private final JTextField endDate = new JTextField(8);
    private final JPanel customDateFields = new JPanel();
    private final JLabel filterMessage = new JLabel(" ");
    private final JLabel countLabel = new JLabel();
    private final JPanel listCards = new JPanel(new CardLayout());
    private final CardLayout editorCards = new CardLayout();
    private final JPanel editorCardPanel = new JPanel(editorCards);
    private final JTextField titleField = new JTextField();
    private final JTextArea bodyArea = new JTextArea();
    private final JComboBox<NoteStatus> editorStatus = new JComboBox<>(NoteStatus.values());
    private final JLabel createdValue = new JLabel(" ");
    private final JLabel updatedValue = new JLabel(" ");
    private final Timer editTimer;
    private boolean adjustingSelection;
    private boolean loadingEditor;
    private boolean invalidCustomRange;
    private UUID editingId;
    private List<Note> filteredNotes = List.of();

    public GroupPanel(NoteGroup group, Consumer<NoteGroup> onChange) {
        super(new BorderLayout());
        this.group = group;
        this.onChange = onChange;
        setBackground(UiPalette.BACKGROUND);
        editTimer = new Timer(450, event -> commitPendingEdit());
        editTimer.setRepeats(false);
        add(buildWorkspace(), BorderLayout.CENTER);
        configureControls();
        refreshFilteredNotes(false);
    }

    public NoteGroup group() {
        return group;
    }

    void setGroupColor(GroupColor color) {
        group = group.withColor(color);
    }

    public void flushPendingEdit() {
        if (editTimer.isRunning()) {
            editTimer.stop();
            commitPendingEdit();
        }
    }

    private JSplitPane buildWorkspace() {
        JSplitPane split = new JSplitPane(JSplitPane.HORIZONTAL_SPLIT, buildListPane(), buildEditorPane());
        split.setResizeWeight(1.0 / 3.0);
        split.setContinuousLayout(true);
        split.setDividerSize(0);
        split.setOneTouchExpandable(false);
        split.setBorder(BorderFactory.createEmptyBorder());
        split.setBackground(UiPalette.BACKGROUND);
        split.addComponentListener(new ComponentAdapter() {
            private boolean positioned;

            @Override
            public void componentResized(ComponentEvent event) {
                if (!positioned && split.getWidth() > 0) {
                    split.setDividerLocation(1.0 / 3.0);
                    positioned = true;
                }
            }
        });
        return split;
    }

    private JPanel buildListPane() {
        JPanel pane = new JPanel(new BorderLayout(0, 12));
        pane.setBackground(UiPalette.BACKGROUND);
        pane.setBorder(BorderFactory.createEmptyBorder(12, 18, 14, 12));
        JPanel top = new JPanel(new BorderLayout(0, 10));
        top.setOpaque(false);
        JPanel heading = new JPanel(new BorderLayout());
        heading.setOpaque(false);
        JLabel label = sectionLabel("NOTES");
        countLabel.setForeground(UiPalette.MUTED);
        countLabel.setFont(countLabel.getFont().deriveFont(11f));
        heading.add(label, BorderLayout.WEST);
        heading.add(countLabel, BorderLayout.EAST);
        top.add(heading, BorderLayout.NORTH);

        JPanel filters = new JPanel(new GridBagLayout());
        filters.setOpaque(false);
        filters.setBorder(BorderFactory.createEmptyBorder(0, 0, 0, 0));
        GridBagConstraints constraints = new GridBagConstraints();
        constraints.insets = new Insets(3, 0, 3, 6);
        constraints.gridy = 0;
        constraints.gridx = 0;
        constraints.gridwidth = 2;
        constraints.fill = GridBagConstraints.HORIZONTAL;
        constraints.weightx = 1;
        filters.add(searchField, constraints);
        constraints.gridy++;
        constraints.gridwidth = 1;
        constraints.weightx = 0;
        filters.add(filterLabel("Status"), constraints);
        constraints.gridx = 1;
        constraints.weightx = 1;
        constraints.insets = new Insets(3, 0, 3, 0);
        filters.add(statusFilter, constraints);
        constraints.gridx = 0;
        constraints.gridy++;
        constraints.weightx = 0;
        constraints.insets = new Insets(3, 0, 3, 6);
        filters.add(filterLabel("Created"), constraints);
        constraints.gridx = 1;
        constraints.weightx = 1;
        constraints.insets = new Insets(3, 0, 3, 0);
        filters.add(dateFilter, constraints);
        constraints.gridx = 0;
        constraints.gridy++;
        constraints.gridwidth = 2;
        constraints.insets = new Insets(1, 0, 2, 0);
        filters.add(customDateFields, constraints);
        constraints.gridy++;
        constraints.gridwidth = 1;
        constraints.insets = new Insets(3, 0, 3, 6);
        filters.add(new JLabel("Sort"), constraints);
        constraints.gridx = 1;
        constraints.insets = new Insets(3, 0, 3, 0);
        filters.add(sortOrder, constraints);
        constraints.gridx = 0;
        constraints.gridy++;
        constraints.gridwidth = 2;
        filters.add(titlesOnly, constraints);

        JPanel filterActions = new JPanel(new BorderLayout());
        filterActions.setOpaque(false);
        filterMessage.setFont(filterMessage.getFont().deriveFont(11f));
        JButton clear = smallButton("Clear filters", "Clear search and filters", this::clearFilters);
        filterActions.add(filterMessage, BorderLayout.CENTER);
        filterActions.add(clear, BorderLayout.EAST);
        constraints.gridy++;
        constraints.insets = new Insets(0, 0, 0, 0);
        filters.add(filterActions, constraints);
        top.add(filters, BorderLayout.CENTER);
        pane.add(top, BorderLayout.NORTH);

        JPanel listSection = new JPanel(new BorderLayout(0, 8));
        listSection.setOpaque(false);
        noteList.setSelectionMode(ListSelectionModel.MULTIPLE_INTERVAL_SELECTION);
        noteList.setCellRenderer(new NoteCardRenderer());
        noteList.setFixedCellHeight(84);
        noteList.setBackground(UiPalette.BACKGROUND);
        noteList.setForeground(UiPalette.TEXT);
        noteList.setBorder(BorderFactory.createEmptyBorder(2, 2, 2, 2));
        noteList.getAccessibleContext().setAccessibleName("Notes");
        noteList.getAccessibleContext().setAccessibleDescription(
                "Visible notes. Use Up and Down to move, Enter to edit, and Delete to remove selected notes.");
        noteList.addListSelectionListener(event -> {
            if (!event.getValueIsAdjusting() && !adjustingSelection) {
                flushPendingEdit();
                Note selected = noteList.getSelectedValue();
                showEditor(selected);
            }
        });
        noteList.getInputMap().put(KeyStroke.getKeyStroke(KeyEvent.VK_ENTER, 0), "edit-selected");
        noteList.getActionMap().put("edit-selected", new AbstractAction() {
            @Override public void actionPerformed(java.awt.event.ActionEvent event) {
                if (noteList.getSelectedValue() != null) titleField.requestFocusInWindow();
            }
        });
        noteList.getInputMap().put(KeyStroke.getKeyStroke(KeyEvent.VK_DELETE, 0), "delete-selected");
        noteList.getActionMap().put("delete-selected", new AbstractAction() {
            @Override public void actionPerformed(java.awt.event.ActionEvent event) { deleteSelectedNotes(); }
        });
        noteList.getInputMap().put(KeyStroke.getKeyStroke(KeyEvent.VK_N, InputEvent.CTRL_DOWN_MASK), "new-note");
        noteList.getActionMap().put("new-note", new AbstractAction() {
            @Override public void actionPerformed(java.awt.event.ActionEvent event) { createNote(); }
        });
        JScrollPane scroll = new JScrollPane(noteList);
        styleScrollPane(scroll);
        listCards.setOpaque(false);
        listCards.add(scroll, "notes");
        JPanel empty = new JPanel(new BorderLayout());
        empty.setBackground(UiPalette.SURFACE);
        empty.setBorder(BorderFactory.createCompoundBorder(BorderFactory.createLineBorder(UiPalette.BORDER),
                BorderFactory.createEmptyBorder(18, 16, 18, 16)));
        JLabel emptyLabel = new JLabel("No notes match these filters", SwingConstants.CENTER);
        emptyLabel.setForeground(UiPalette.MUTED);
        empty.add(emptyLabel);
        listCards.add(empty, "empty");
        listSection.add(listCards, BorderLayout.CENTER);
        JPanel listActions = new JPanel(new BorderLayout(8, 0));
        listActions.setOpaque(false);
        JButton add = smallButton("＋  New note", "Create a new note (Ctrl+N)", this::createNote);
        JButton delete = smallButton("Delete selected", "Delete selected notes", this::deleteSelectedNotes);
        listActions.add(add, BorderLayout.CENTER);
        listActions.add(delete, BorderLayout.EAST);
        listSection.add(listActions, BorderLayout.SOUTH);
        pane.add(listSection, BorderLayout.CENTER);
        return pane;
    }

    private JPanel buildEditorPane() {
        JPanel pane = new JPanel(new BorderLayout());
        pane.setBackground(UiPalette.SURFACE);
        pane.setBorder(BorderFactory.createCompoundBorder(
                BorderFactory.createMatteBorder(0, 1, 0, 0, UiPalette.BORDER),
                BorderFactory.createEmptyBorder(20, 22, 18, 22)));
        JPanel empty = new JPanel(new BorderLayout(0, 10));
        empty.setOpaque(false);
        JLabel emptyTitle = new JLabel("Nothing selected", SwingConstants.CENTER);
        emptyTitle.setForeground(UiPalette.TEXT);
        emptyTitle.setFont(emptyTitle.getFont().deriveFont(Font.BOLD, 19f));
        JLabel emptySub = new JLabel("Choose a note or create one to get started.", SwingConstants.CENTER);
        emptySub.setForeground(UiPalette.MUTED);
        JPanel emptyMessage = new JPanel(new BorderLayout(0, 8));
        emptyMessage.setOpaque(false);
        emptyMessage.add(emptyTitle, BorderLayout.NORTH);
        emptyMessage.add(emptySub, BorderLayout.CENTER);
        empty.add(emptyMessage, BorderLayout.CENTER);
        editorCardPanel.setOpaque(false);
        editorCardPanel.add(empty, "empty");
        editorCardPanel.add(buildEditorForm(), "editor");
        pane.add(editorCardPanel, BorderLayout.CENTER);
        return pane;
    }

    private JPanel buildEditorForm() {
        JPanel form = new JPanel(new BorderLayout(0, 14));
        form.setOpaque(false);
        JPanel heading = new JPanel(new BorderLayout(0, 9));
        heading.setOpaque(false);
        heading.add(sectionLabel("NOTE DETAILS"), BorderLayout.NORTH);
        JPanel titleRow = new JPanel(new BorderLayout(12, 0));
        titleRow.setOpaque(false);
        titleField.setToolTipText("Note title");
        titleField.getAccessibleContext().setAccessibleName("Note title");
        styleTextInput(titleField);
        titleField.setFont(titleField.getFont().deriveFont(Font.BOLD, 18f));
        titleRow.add(titleField, BorderLayout.CENTER);
        styleCombo(editorStatus);
        editorStatus.setToolTipText("Change note status");
        editorStatus.getAccessibleContext().setAccessibleName("Note status");
        editorStatus.setRenderer(new StatusRenderer());
        Dimension statusSelectorSize = new Dimension(184, 44);
        editorStatus.setPreferredSize(statusSelectorSize);
        editorStatus.setMinimumSize(statusSelectorSize);
        editorStatus.setMaximumSize(statusSelectorSize);
        titleRow.add(editorStatus, BorderLayout.EAST);
        heading.add(titleRow, BorderLayout.CENTER);
        form.add(heading, BorderLayout.NORTH);

        bodyArea.setLineWrap(true);
        bodyArea.setWrapStyleWord(true);
        bodyArea.setFont(bodyArea.getFont().deriveFont(14f));
        bodyArea.setForeground(UiPalette.TEXT);
        bodyArea.setBackground(UiPalette.SURFACE);
        bodyArea.setCaretColor(UiPalette.ACCENT);
        bodyArea.setSelectionColor(new Color(67, 105, 133));
        bodyArea.setBorder(BorderFactory.createEmptyBorder(12, 13, 12, 13));
        bodyArea.setToolTipText("Note body");
        bodyArea.getAccessibleContext().setAccessibleName("Note body");
        bodyArea.addFocusListener(new java.awt.event.FocusAdapter() {
            @Override public void focusGained(java.awt.event.FocusEvent event) {
                bodyArea.setBorder(BorderFactory.createCompoundBorder(
                        BorderFactory.createLineBorder(UiPalette.FOCUS, 2),
                        BorderFactory.createEmptyBorder(10, 11, 10, 11)));
            }
            @Override public void focusLost(java.awt.event.FocusEvent event) {
                bodyArea.setBorder(BorderFactory.createEmptyBorder(12, 13, 12, 13));
            }
        });
        JScrollPane bodyScroll = new JScrollPane(bodyArea);
        styleScrollPane(bodyScroll);
        form.add(bodyScroll, BorderLayout.CENTER);

        JPanel metadata = new JPanel(new GridBagLayout());
        metadata.setOpaque(false);
        GridBagConstraints constraints = new GridBagConstraints();
        constraints.anchor = GridBagConstraints.WEST;
        constraints.insets = new Insets(2, 0, 2, 18);
        constraints.gridx = 0;
        constraints.gridy = 0;
        JLabel created = smallLabel("CREATED");
        JLabel updated = smallLabel("LAST MODIFIED");
        metadata.add(created, constraints);
        constraints.gridx = 1;
        metadata.add(updated, constraints);
        constraints.gridy = 1;
        constraints.gridx = 0;
        createdValue.setForeground(UiPalette.TEXT);
        metadata.add(createdValue, constraints);
        constraints.gridx = 1;
        updatedValue.setForeground(UiPalette.TEXT);
        metadata.add(updatedValue, constraints);
        form.add(metadata, BorderLayout.SOUTH);
        return form;
    }

    private void configureControls() {
        searchField.setToolTipText("Search titles and note bodies");
        searchField.getAccessibleContext().setAccessibleName("Search notes");
        statusFilter.addItem("All statuses");
        for (NoteStatus status : NoteStatus.values()) statusFilter.addItem(status);
        statusFilter.setRenderer(new FilterStatusRenderer());
        styleCombo(statusFilter);
        styleCombo(dateFilter);
        styleCombo(sortOrder);
        dateFilter.setRenderer(new DateRangeRenderer());
        sortOrder.setRenderer(new SortRenderer());
        sortOrder.setSelectedItem(NoteFilters.SortOrder.OLDEST);
        titlesOnly.setForeground(UiPalette.MUTED);
        titlesOnly.setOpaque(false);
        titlesOnly.setToolTipText("Search only the title, not the note body");
        titlesOnly.getAccessibleContext().setAccessibleName("Titles only");
        customDateFields.setLayout(new BoxLayout(customDateFields, BoxLayout.X_AXIS));
        customDateFields.setOpaque(false);
        customDateFields.add(filterLabel("From "));
        customDateFields.add(startDate);
        customDateFields.add(Box.createHorizontalStrut(6));
        customDateFields.add(filterLabel("to "));
        customDateFields.add(endDate);
        customDateFields.setVisible(false);
        startDate.setToolTipText("Start date, dd.MM.yyyy");
        endDate.setToolTipText("End date, dd.MM.yyyy");
        styleTextInput(searchField);
        styleTextInput(startDate);
        styleTextInput(endDate);
        searchField.getDocument().addDocumentListener(new ChangeListener(this::refreshFromControls));
        startDate.getDocument().addDocumentListener(new ChangeListener(this::refreshFromControls));
        endDate.getDocument().addDocumentListener(new ChangeListener(this::refreshFromControls));
        titlesOnly.addActionListener(event -> refreshFromControls());
        statusFilter.addActionListener(event -> refreshFromControls());
        dateFilter.addActionListener(event -> {
            customDateFields.setVisible(dateFilter.getSelectedItem() == NoteFilters.DateRange.CUSTOM);
            revalidate();
            refreshFromControls();
        });
        sortOrder.addActionListener(event -> refreshFromControls());

        ChangeListener editorListener = new ChangeListener(() -> {
            if (!loadingEditor && editingId != null) editTimer.restart();
        });
        titleField.getDocument().addDocumentListener(editorListener);
        bodyArea.getDocument().addDocumentListener(editorListener);
        editorStatus.addActionListener(event -> {
            if (!loadingEditor && editingId != null) editTimer.restart();
        });
    }

    private void refreshFromControls() {
        refreshFilteredNotes(true);
    }

    private void refreshFilteredNotes(boolean maintainSelection) {
        Set<UUID> selectedIds = maintainSelection ? noteList.getSelectedValuesList().stream()
                .map(Note::id).collect(java.util.stream.Collectors.toSet()) : Set.of();
        UUID previousPrimary = maintainSelection && noteList.getSelectedValue() != null
                ? noteList.getSelectedValue().id() : editingId;
        NoteStatus status = statusFilter.getSelectedItem() instanceof NoteStatus selected ? selected : null;
        NoteFilters.DateRange range = (NoteFilters.DateRange) dateFilter.getSelectedItem();
        LocalDate from = null;
        LocalDate to = null;
        invalidCustomRange = false;
        if (range == NoteFilters.DateRange.CUSTOM) {
            try {
                from = LocalDate.parse(startDate.getText().strip(), CUSTOM_DATE);
                to = LocalDate.parse(endDate.getText().strip(), CUSTOM_DATE);
                invalidCustomRange = to.isBefore(from);
            } catch (DateTimeParseException exception) {
                invalidCustomRange = true;
            }
        }
        filteredNotes = invalidCustomRange ? List.of() : NoteFilters.apply(group.notes(), status, range,
                from, to, searchField.getText(), titlesOnly.isSelected(),
                (NoteFilters.SortOrder) sortOrder.getSelectedItem());

        adjustingSelection = true;
        listModel.clear();
        filteredNotes.forEach(listModel::addElement);
        List<Integer> indexesToRestore = new ArrayList<>();
        for (int index = 0; index < filteredNotes.size(); index++) {
            if (selectedIds.contains(filteredNotes.get(index).id())) indexesToRestore.add(index);
        }
        for (int index : indexesToRestore) noteList.addSelectionInterval(index, index);
        if (noteList.isSelectionEmpty() && !filteredNotes.isEmpty()) {
            int primaryIndex = indexOf(previousPrimary);
            noteList.setSelectedIndex(primaryIndex >= 0 ? primaryIndex : 0);
        }
        adjustingSelection = false;
        countLabel.setText(filteredNotes.size() + " / " + group.notes().size());
        updateFilterMessage(status, range);
        Note selected = noteList.getSelectedValue();
        if (selected == null || !selected.id().equals(editingId)) showEditor(selected);
        ((CardLayout) listCards.getLayout()).show(listCards, filteredNotes.isEmpty() ? "empty" : "notes");
    }

    private int indexOf(UUID id) {
        if (id == null) return -1;
        for (int index = 0; index < filteredNotes.size(); index++) {
            if (filteredNotes.get(index).id().equals(id)) return index;
        }
        return -1;
    }

    private void updateFilterMessage(NoteStatus status, NoteFilters.DateRange range) {
        boolean active = status != null || range != NoteFilters.DateRange.ALL
                || !searchField.getText().isBlank() || titlesOnly.isSelected();
        if (invalidCustomRange) {
            filterMessage.setForeground(UiPalette.WARNING);
            filterMessage.setText("Enter a valid dd.MM.yyyy range");
        } else if (active) {
            filterMessage.setForeground(UiPalette.ACCENT);
            filterMessage.setText("Filters active");
        } else {
            filterMessage.setForeground(UiPalette.MUTED);
            filterMessage.setText(" ");
        }
    }

    private void clearFilters() {
        searchField.setText("");
        statusFilter.setSelectedIndex(0);
        dateFilter.setSelectedItem(NoteFilters.DateRange.ALL);
        startDate.setText("");
        endDate.setText("");
        sortOrder.setSelectedItem(NoteFilters.SortOrder.OLDEST);
        titlesOnly.setSelected(false);
        refreshFilteredNotes(true);
    }

    private void createNote() {
        clearFilters();
        Note note = Note.create();
        List<Note> notes = new ArrayList<>(group.notes());
        notes.add(note);
        group = group.withNotes(notes);
        onChange.accept(group);
        refreshFilteredNotes(false);
        int index = indexOf(note.id());
        if (index >= 0) noteList.setSelectedIndex(index);
        titleField.requestFocusInWindow();
    }

    private void deleteSelectedNotes() {
        List<Note> selected = noteList.getSelectedValuesList();
        if (selected.isEmpty()) return;
        String message = selected.size() == 1 ? "Delete this note?" : "Delete these " + selected.size() + " notes?";
        if (javax.swing.JOptionPane.showConfirmDialog(this, message, "Delete notes",
                javax.swing.JOptionPane.YES_NO_OPTION, javax.swing.JOptionPane.WARNING_MESSAGE)
                != javax.swing.JOptionPane.YES_OPTION) return;
        Set<UUID> ids = selected.stream().map(Note::id).collect(java.util.stream.Collectors.toSet());
        List<Note> remaining = group.notes().stream().filter(note -> !ids.contains(note.id())).toList();
        group = group.withNotes(remaining);
        editingId = null;
        onChange.accept(group);
        refreshFilteredNotes(false);
    }

    private void showEditor(Note note) {
        loadingEditor = true;
        if (note == null) {
            editingId = null;
            titleField.setText("");
            bodyArea.setText("");
            createdValue.setText(" ");
            updatedValue.setText(" ");
            editorStatus.setSelectedItem(NoteStatus.TODO);
            editorCards.show(editorCardPanel, "empty");
        } else {
            editingId = note.id();
            titleField.setText(note.title());
            bodyArea.setText(note.body());
            editorStatus.setSelectedItem(note.status());
            createdValue.setText(DETAIL_DATE.format(note.createdAt().atZone(ZoneId.systemDefault())));
            updatedValue.setText(DETAIL_DATE.format(note.updatedAt().atZone(ZoneId.systemDefault())));
            editorCards.show(editorCardPanel, "editor");
        }
        loadingEditor = false;
    }

    private void commitPendingEdit() {
        if (loadingEditor || editingId == null) return;
        Note old = group.notes().stream().filter(note -> note.id().equals(editingId)).findFirst().orElse(null);
        if (old == null) return;
        String title = titleField.getText();
        String body = bodyArea.getText();
        NoteStatus status = (NoteStatus) editorStatus.getSelectedItem();
        if (old.title().equals(title) && old.body().equals(body) && old.status() == status) return;
        Note updated = old.update(title, body, status);
        List<Note> notes = group.notes().stream()
                .map(note -> note.id().equals(editingId) ? updated : note).toList();
        group = group.withNotes(notes);
        createdValue.setText(DETAIL_DATE.format(updated.createdAt().atZone(ZoneId.systemDefault())));
        updatedValue.setText(DETAIL_DATE.format(updated.updatedAt().atZone(ZoneId.systemDefault())));
        onChange.accept(group);
        noteList.repaint();
        refreshFilteredNotes(true);
    }

    private JButton smallButton(String text, String description, Runnable action) {
        JButton button = new JButton(text);
        button.setToolTipText(description);
        button.getAccessibleContext().setAccessibleName(description);
        WorkspaceFrame.styleButton(button);
        button.addActionListener(event -> action.run());
        return button;
    }

    private JLabel sectionLabel(String text) {
        JLabel label = new JLabel(text);
        label.setForeground(UiPalette.ACCENT);
        label.setFont(label.getFont().deriveFont(Font.BOLD, 11f));
        return label;
    }

    private JLabel filterLabel(String text) {
        JLabel label = new JLabel(text);
        label.setForeground(UiPalette.MUTED);
        label.setFont(label.getFont().deriveFont(11f));
        return label;
    }

    private JLabel smallLabel(String text) {
        JLabel label = new JLabel(text);
        label.setForeground(UiPalette.MUTED);
        label.setFont(label.getFont().deriveFont(Font.BOLD, 10f));
        return label;
    }

    private void styleTextInput(JTextComponent input) {
        input.setForeground(UiPalette.TEXT);
        input.setBackground(UiPalette.RAISED);
        input.setCaretColor(UiPalette.ACCENT);
        input.setSelectionColor(new Color(67, 105, 133));
        input.setBorder(BorderFactory.createCompoundBorder(BorderFactory.createLineBorder(UiPalette.BORDER),
                BorderFactory.createEmptyBorder(6, 8, 6, 8)));
        input.setFont(input.getFont().deriveFont(12f));
        input.addFocusListener(new java.awt.event.FocusAdapter() {
            @Override public void focusGained(java.awt.event.FocusEvent event) {
            input.setBorder(BorderFactory.createCompoundBorder(BorderFactory.createLineBorder(UiPalette.FOCUS, 2),
                BorderFactory.createEmptyBorder(5, 7, 5, 7)));
            }
            @Override public void focusLost(java.awt.event.FocusEvent event) {
            input.setBorder(BorderFactory.createCompoundBorder(BorderFactory.createLineBorder(UiPalette.BORDER),
                BorderFactory.createEmptyBorder(6, 8, 6, 8)));
            }
        });
    }

    private void styleCombo(JComboBox<?> combo) {
        combo.setUI(new BasicComboBoxUI() {
            @Override
            protected JButton createArrowButton() {
                JButton arrow = new JButton("▾");
                arrow.setForeground(UiPalette.ACCENT);
                arrow.setBackground(UiPalette.RAISED);
                arrow.setFont(arrow.getFont().deriveFont(Font.BOLD, 14f));
                arrow.setBorder(BorderFactory.createMatteBorder(0, 1, 0, 0, UiPalette.BORDER));
                arrow.setFocusable(false);
                arrow.setOpaque(true);
                arrow.setToolTipText("Open options");
                arrow.getAccessibleContext().setAccessibleName("Open options");
                return arrow;
            }

            @Override
            public void paintCurrentValueBackground(Graphics graphics, Rectangle bounds, boolean hasFocus) {
                graphics.setColor(UiPalette.RAISED);
                graphics.fillRect(bounds.x, bounds.y, bounds.width, bounds.height);
            }

            @Override
            public void paintCurrentValue(Graphics graphics, Rectangle bounds, boolean hasFocus) {
                Component value = comboBox.getRenderer().getListCellRendererComponent(
                        listBox, comboBox.getSelectedItem(), -1, false, false);
                value.setForeground(UiPalette.TEXT);
                value.setBackground(UiPalette.RAISED);
                value.setFont(comboBox.getFont());
                if (value instanceof javax.swing.JComponent component) component.setOpaque(true);
                currentValuePane.paintComponent(graphics, value, comboBox,
                        bounds.x, bounds.y, bounds.width, bounds.height);
            }
        });
        combo.setForeground(UiPalette.TEXT);
        combo.setBackground(UiPalette.RAISED);
        combo.setOpaque(true);
        combo.setFocusable(true);
        combo.setBorder(BorderFactory.createLineBorder(UiPalette.BORDER));
        combo.setFont(combo.getFont().deriveFont(12f));
    }

    private void styleScrollPane(JScrollPane scroll) {
        scroll.setBorder(BorderFactory.createLineBorder(UiPalette.BORDER));
        scroll.getViewport().setBackground(UiPalette.BACKGROUND);
        scroll.getVerticalScrollBar().setUnitIncrement(16);
    }

    private String truncate(String text, Font font, int availableWidth) {
        String normalized = text.replace('\n', ' ').strip();
        java.awt.FontMetrics metrics = getFontMetrics(font);
        if (metrics.stringWidth(normalized) <= availableWidth) return normalized;
        String ellipsis = "…";
        int end = normalized.length();
        while (end > 0 && metrics.stringWidth(normalized.substring(0, end) + ellipsis) > availableWidth) end--;
        return (end == 0 ? ellipsis : normalized.substring(0, end).stripTrailing() + ellipsis);
    }

    private final class NoteCardRenderer extends JPanel implements javax.swing.ListCellRenderer<Note> {
        private final JLabel title = new JLabel();
        private final JLabel date = new JLabel();
        private final JLabel status = new JLabel();
        private final JPanel lower = new JPanel(new BorderLayout());

        private NoteCardRenderer() {
            super(new BorderLayout(0, 7));
            setBorder(BorderFactory.createEmptyBorder(10, 12, 9, 12));
            setOpaque(true);
            title.setFont(title.getFont().deriveFont(Font.BOLD, 15f));
            date.setFont(date.getFont().deriveFont(13f));
            status.setFont(status.getFont().deriveFont(Font.BOLD, 13f));
            lower.setOpaque(false);
            lower.add(date, BorderLayout.WEST);
            lower.add(status, BorderLayout.EAST);
            add(title, BorderLayout.NORTH);
            add(lower, BorderLayout.SOUTH);
        }

        @Override public Component getListCellRendererComponent(JList<? extends Note> list, Note note,
                int index, boolean selected, boolean focus) {
            setBackground(selected ? new Color(47, 74, 95) : UiPalette.SURFACE);
            setBorder(BorderFactory.createCompoundBorder(
                    BorderFactory.createLineBorder(selected ? UiPalette.FOCUS : UiPalette.BORDER),
                    BorderFactory.createEmptyBorder(9, 11, 8, 11)));
            title.setForeground(UiPalette.TEXT);
            title.setText(truncate(note.displayTitle(), title.getFont(), Math.max(60, list.getWidth() - 155)));
            date.setForeground(UiPalette.MUTED);
            date.setText(DISPLAY_DATE.format(note.createdAt().atZone(ZoneId.systemDefault())));
            status.setForeground(UiPalette.status(note.status()));
            status.setText(note.status().symbol() + "  " + note.status().label());
            getAccessibleContext().setAccessibleName(note.displayTitle() + ", " + date.getText() + ", " + status.getText());
            return this;
        }
    }

    private static final class StatusRenderer extends DefaultListCellRenderer {
        @Override public Component getListCellRendererComponent(JList<?> list, Object value, int index,
                boolean selected, boolean focus) {
            JLabel label = (JLabel) super.getListCellRendererComponent(list, value, index, selected, focus);
            if (value instanceof NoteStatus status) {
                label.setText(status.symbol() + "  " + status.label());
                label.setForeground(UiPalette.status(status));
            }
            label.setBackground(selected ? UiPalette.RAISED : UiPalette.SURFACE);
            label.setBorder(BorderFactory.createEmptyBorder(5, 8, 5, 8));
            return label;
        }
    }

    private static final class FilterStatusRenderer extends DefaultListCellRenderer {
        @Override public Component getListCellRendererComponent(JList<?> list, Object value, int index,
                boolean selected, boolean focus) {
            JLabel label = (JLabel) super.getListCellRendererComponent(list, value, index, selected, focus);
            label.setText(value instanceof NoteStatus status ? status.symbol() + "  " + status.label() : "All statuses");
            label.setForeground(value instanceof NoteStatus status ? UiPalette.status(status) : UiPalette.TEXT);
            label.setBackground(selected ? UiPalette.RAISED : UiPalette.SURFACE);
            return label;
        }
    }

    private static final class DateRangeRenderer extends DefaultListCellRenderer {
        private static final String[] LABELS = {"All dates", "Today", "Last 7 days", "Last 30 days", "Custom range"};
        @Override public Component getListCellRendererComponent(JList<?> list, Object value, int index,
                boolean selected, boolean focus) {
            JLabel label = (JLabel) super.getListCellRendererComponent(list, value, index, selected, focus);
            if (value instanceof NoteFilters.DateRange range) label.setText(LABELS[range.ordinal()]);
            label.setForeground(UiPalette.TEXT);
            label.setBackground(selected ? UiPalette.RAISED : UiPalette.SURFACE);
            return label;
        }
    }

    private static final class SortRenderer extends DefaultListCellRenderer {
        @Override public Component getListCellRendererComponent(JList<?> list, Object value, int index,
                boolean selected, boolean focus) {
            JLabel label = (JLabel) super.getListCellRendererComponent(list, value, index, selected, focus);
            if (value instanceof NoteFilters.SortOrder order) {
                label.setText(switch (order) {
                    case NEWEST -> "Newest first";
                    case OLDEST -> "Oldest first";
                    case STATUS -> "By status";
                });
            }
            label.setForeground(UiPalette.TEXT);
            label.setBackground(selected ? UiPalette.RAISED : UiPalette.SURFACE);
            return label;
        }
    }

    private static final class ChangeListener implements javax.swing.event.DocumentListener {
        private final Runnable action;
        private ChangeListener(Runnable action) { this.action = action; }
        @Override public void insertUpdate(javax.swing.event.DocumentEvent event) { action.run(); }
        @Override public void removeUpdate(javax.swing.event.DocumentEvent event) { action.run(); }
        @Override public void changedUpdate(javax.swing.event.DocumentEvent event) { action.run(); }
    }
}