package dev.noteworthy.ui;

import dev.noteworthy.model.NoteGroup;
import dev.noteworthy.model.GroupColor;
import dev.noteworthy.persistence.JsonStore;

import javax.swing.AbstractAction;
import javax.swing.BorderFactory;
import javax.swing.BoxLayout;
import javax.swing.JButton;
import javax.swing.JFrame;
import javax.swing.JLabel;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.JPopupMenu;
import javax.swing.JTabbedPane;
import javax.swing.KeyStroke;
import javax.swing.SwingConstants;
import javax.swing.SwingUtilities;
import javax.swing.plaf.basic.BasicTabbedPaneUI;
import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Component;
import java.awt.Container;
import java.awt.Dimension;
import java.awt.Font;
import java.awt.Graphics;
import javax.swing.Icon;
import java.awt.Point;
import java.awt.event.InputEvent;
import java.awt.event.KeyEvent;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

public final class WorkspaceFrame extends JFrame {
    private static final Color SELECTED_TAB_TEXT = new Color(68, 76, 86);
    private final JsonStore store;
    private final JTabbedPane tabs = new JTabbedPane();
    private final JLabel saveStatus = new JLabel("Ready");
    private final JLabel fileLocation = new JLabel();
    private final List<NoteGroup> groups = new ArrayList<>();
    private boolean rebuilding;
    private boolean reordering;
    private boolean fontScaleApplied;
    private int dragTab = -1;

    public WorkspaceFrame(JsonStore store, List<NoteGroup> loadedGroups) {
        super("Noteworthy");
        this.store = store;
        setIconImages(AppIcon.images());
        groups.addAll(loadedGroups);
        normalizeSelection();
        setDefaultCloseOperation(JFrame.DO_NOTHING_ON_CLOSE);
        setMinimumSize(new Dimension(1400, 900));
        setSize(1280, 820);
        setLocationRelativeTo(null);
        getContentPane().setBackground(UiPalette.BACKGROUND);
        setLayout(new BorderLayout());
        installColoredTabUI();
        tabs.setBackground(UiPalette.BACKGROUND);
        tabs.setForeground(UiPalette.TEXT);
        tabs.setFocusable(true);
        add(tabs, BorderLayout.CENTER);
        add(buildFooter(), BorderLayout.SOUTH);
        getAccessibleContext().setAccessibleDescription("Noteworthy notes and reminder workspace");
        installKeyBindings();
        installTabInteractions();
        rebuildTabs();
        scaleFonts(this);
        fontScaleApplied = true;
        addWindowListener(new java.awt.event.WindowAdapter() {
            @Override
            public void windowClosing(java.awt.event.WindowEvent event) {
                for (int index = 0; index < groups.size(); index++) {
                    ((GroupPanel) tabs.getComponentAt(index)).flushPendingEdit();
                }
                persist();
                backupOnExit();
                dispose();
            }
        });
    }

    private static final int MIN_TAB_WIDTH = 80;
    private static final int ADD_TAB_WIDTH = 40;
    private static final int TAB_HORIZONTAL_PADDING = 10;

    private void installColoredTabUI() {
        tabs.setUI(new BasicTabbedPaneUI() {
            @Override
            protected void installDefaults() {
                super.installDefaults();
                tabInsets = new java.awt.Insets(tabInsets.top, TAB_HORIZONTAL_PADDING,
                        tabInsets.bottom, TAB_HORIZONTAL_PADDING);
            }

            @Override
            protected int calculateTabWidth(int tabPlacement, int tabIndex, java.awt.FontMetrics metrics) {
                if (tabIndex >= groups.size()) {
                    return ADD_TAB_WIDTH;
                }
                Font boldFont = tabs.getFont().deriveFont(Font.BOLD);
                int width = super.calculateTabWidth(tabPlacement, tabIndex, tabs.getFontMetrics(boldFont));
                return Math.max(MIN_TAB_WIDTH, width);
            }

            @Override
            protected void paintText(Graphics graphics, int tabPlacement, Font font, java.awt.FontMetrics metrics,
                    int tabIndex, String title, java.awt.Rectangle textRect, boolean isSelected) {
                Font paintFont = isSelected ? font.deriveFont(Font.BOLD) : font;
                super.paintText(graphics, tabPlacement, paintFont, tabs.getFontMetrics(paintFont),
                        tabIndex, title, textRect, isSelected);
            }

            @Override
            protected void paintTabBackground(Graphics graphics, int tabPlacement, int tabIndex,
                    int x, int y, int width, int height, boolean selected) {
                if (tabIndex >= groups.size()) {
                    super.paintTabBackground(graphics, tabPlacement, tabIndex,
                            x, y, width, height, selected);
                    return;
                }
                graphics.setColor(tabs.getBackgroundAt(tabIndex));
                graphics.fillRect(x, y, width, height);
            }
        });
    }

    private JPanel buildFooter() {
        JPanel footer = new JPanel(new BorderLayout(12, 0));
        footer.setBackground(UiPalette.SURFACE);
        footer.setBorder(BorderFactory.createCompoundBorder(
                BorderFactory.createMatteBorder(1, 0, 0, 0, UiPalette.BORDER),
                BorderFactory.createEmptyBorder(9, 18, 9, 18)));
        saveStatus.setForeground(UiPalette.ACCENT);
        saveStatus.setFont(saveStatus.getFont().deriveFont(12f));
        fileLocation.setText(store.file().toString());
        fileLocation.setForeground(UiPalette.MUTED);
        fileLocation.setFont(fileLocation.getFont().deriveFont(11f));
        JLabel navigation = new JLabel("Groups: Alt + ← / →", SwingConstants.RIGHT);
        navigation.setForeground(UiPalette.MUTED);
        navigation.setFont(navigation.getFont().deriveFont(11f));
        footer.add(saveStatus, BorderLayout.WEST);
        footer.add(fileLocation, BorderLayout.CENTER);
        footer.add(navigation, BorderLayout.EAST);
        return footer;
    }

    private void installKeyBindings() {
        tabs.getInputMap(JTabbedPane.WHEN_ANCESTOR_OF_FOCUSED_COMPONENT)
                .put(KeyStroke.getKeyStroke(KeyEvent.VK_LEFT, InputEvent.ALT_DOWN_MASK), "previous-group");
        tabs.getActionMap().put("previous-group", new AbstractAction() {
            @Override public void actionPerformed(java.awt.event.ActionEvent event) {
                selectRelativeTab(-1);
            }
        });
        tabs.getInputMap(JTabbedPane.WHEN_ANCESTOR_OF_FOCUSED_COMPONENT)
                .put(KeyStroke.getKeyStroke(KeyEvent.VK_RIGHT, InputEvent.ALT_DOWN_MASK), "next-group");
        tabs.getActionMap().put("next-group", new AbstractAction() {
            @Override public void actionPerformed(java.awt.event.ActionEvent event) {
                selectRelativeTab(1);
            }
        });
        tabs.addChangeListener(event -> {
            if (!rebuilding && !reordering && tabs.getSelectedIndex() >= 0) {
                if (tabs.getSelectedIndex() >= groups.size()) {
                    int selectedGroup = selectedGroupIndex();
                    if (selectedGroup >= 0) tabs.setSelectedIndex(selectedGroup);
                    return;
                }
                updateTabForegrounds();
                setSelectedGroup(tabs.getSelectedIndex());
            }
        });
    }

    private void installTabInteractions() {
        tabs.addMouseListener(new MouseAdapter() {
            @Override public void mousePressed(MouseEvent event) {
                dragTab = tabs.indexAtLocation(event.getX(), event.getY());
                if (SwingUtilities.isRightMouseButton(event) && dragTab >= 0 && dragTab < groups.size()) {
                    tabs.setSelectedIndex(dragTab);
                    showTabMenu(event.getPoint());
                }
            }

            @Override public void mouseReleased(MouseEvent event) {
                if (dragTab >= 0 && SwingUtilities.isLeftMouseButton(event)) {
                    int target = tabs.indexAtLocation(event.getX(), event.getY());
                    if (target >= 0 && target < groups.size() && target != dragTab) {
                        reorderTab(dragTab, target);
                    }
                }
                dragTab = -1;
            }
        });
        tabs.setToolTipText("Right-click a group for rename and delete actions. Drag tabs to reorder.");
    }

    private void showTabMenu(Point location) {
        JPopupMenu menu = new JPopupMenu();
        menu.setLayout(new BoxLayout(menu, BoxLayout.Y_AXIS));
        Font menuFont = tabs.getFont();
        int textWidth = Math.max(tabs.getFontMetrics(menuFont).stringWidth("Rename group"),
            tabs.getFontMetrics(menuFont).stringWidth("Delete group…"));
        int rowWidth = textWidth + 16;
        menu.add(createMenuButton("Rename group", "Rename group", rowWidth, menu,
            this::renameSelectedGroup));
        menu.add(createMenuButton("Delete group…", "Delete group", rowWidth, menu,
            this::deleteSelectedGroup));
        menu.addSeparator();
        GroupColor selectedColor = groups.get(tabs.getSelectedIndex()).color();
        for (GroupColor color : GroupColor.values()) {
            JButton item = createMenuButton("", color.label() + " tab theme", rowWidth, menu,
                () -> setSelectedTabColor(color));
            item.setIcon(new ThemePairIcon(Color.decode(color.inactiveHex()),
                Color.decode(color.selectedHex()), textWidth, color == selectedColor));
            item.setToolTipText("Dark inactive and light selected tab colors");
            menu.add(item);
        }
        menu.show(tabs, location.x, location.y);
    }

        private JButton createMenuButton(String text, String accessibleName, int width,
            JPopupMenu menu, Runnable action) {
        JButton button = new JButton(text);
        button.setFont(tabs.getFont());
        button.setForeground(SELECTED_TAB_TEXT);
        button.setHorizontalAlignment(SwingConstants.LEFT);
        button.setBorder(BorderFactory.createEmptyBorder(4, 8, 4, 8));
        button.setContentAreaFilled(false);
        button.setOpaque(false);
        button.setFocusable(true);
        button.setAlignmentX(Component.LEFT_ALIGNMENT);
        Dimension size = new Dimension(width, 30);
        button.setPreferredSize(size);
        button.setMinimumSize(size);
        button.setMaximumSize(size);
        button.getAccessibleContext().setAccessibleName(accessibleName);
        button.addActionListener(event -> {
            menu.setVisible(false);
            action.run();
        });
        return button;
        }

    private void rebuildTabs() {
        rebuilding = true;
        tabs.removeAll();
        for (NoteGroup group : groups) {
            GroupPanel panel = new GroupPanel(group, this::updateGroup);
            if (fontScaleApplied) scaleFonts(panel);
            tabs.addTab(group.name(), panel);
            tabs.setToolTipTextAt(tabs.getTabCount() - 1, group.notes().size() + " notes");
        }
        JPanel addTabPage = new JPanel();
        addTabPage.setOpaque(false);
        tabs.addTab("", addTabPage);
        int addTabIndex = tabs.getTabCount() - 1;
        tabs.setTabComponentAt(addTabIndex, createAddTabButton());
        tabs.setToolTipTextAt(addTabIndex, "Create a new group");
        int selected = 0;
        for (int index = 0; index < groups.size(); index++) {
            if (groups.get(index).selected()) {
                selected = index;
                break;
            }
        }
        tabs.setSelectedIndex(selected);
        updateTabForegrounds();
        rebuilding = false;
    }

    private void updateTabForegrounds() {
        int selected = tabs.getSelectedIndex();
        for (int index = 0; index < groups.size(); index++) {
            GroupColor color = groups.get(index).color();
            tabs.setBackgroundAt(index, Color.decode(index == selected
                    ? color.selectedHex() : color.inactiveHex()));
            tabs.setForegroundAt(index, index == selected ? SELECTED_TAB_TEXT : UiPalette.TEXT);
        }
    }

    private void setSelectedTabColor(GroupColor color) {
        int index = tabs.getSelectedIndex();
        if (index < 0 || index >= groups.size()) return;
        groups.set(index, groups.get(index).withColor(color));
        ((GroupPanel) tabs.getComponentAt(index)).setGroupColor(color);
        updateTabForegrounds();
        persist();
    }

    private JButton createAddTabButton() {
        JButton button = new JButton("+");
        button.setForeground(UiPalette.ACCENT);
        button.setFont(button.getFont().deriveFont(Font.BOLD, 20f));
        button.setToolTipText("Create a new group");
        button.getAccessibleContext().setAccessibleName("Create a new group");
        button.setBorder(BorderFactory.createEmptyBorder(2, 12, 2, 12));
        button.setContentAreaFilled(false);
        button.setOpaque(false);
        button.setFocusable(true);
        button.addActionListener(event -> createGroup());
        if (fontScaleApplied) scaleFonts(button);
        return button;
    }

    private int selectedGroupIndex() {
        for (int index = 0; index < groups.size(); index++) {
            if (groups.get(index).selected()) return index;
        }
        return groups.isEmpty() ? -1 : 0;
    }

    private void scaleFonts(Component component) {
        Font font = component.getFont();
        if (font != null) component.setFont(font.deriveFont(font.getSize2D() * 1.2f));
        if (component instanceof Container container) {
            for (Component child : container.getComponents()) scaleFonts(child);
        }
    }

    private void normalizeSelection() {
        if (groups.isEmpty()) {
            groups.add(NoteGroup.create("New Group"));
        }
        int selected = -1;
        for (int index = 0; index < groups.size(); index++) {
            if (groups.get(index).selected() && selected < 0) {
                selected = index;
            }
        }
        if (selected < 0) selected = 0;
        for (int index = 0; index < groups.size(); index++) {
            groups.set(index, groups.get(index).withSelected(index == selected));
        }
    }

    private void setSelectedGroup(int selectedIndex) {
        boolean changed = false;
        for (int index = 0; index < groups.size(); index++) {
            boolean selected = index == selectedIndex;
            if (groups.get(index).selected() != selected) changed = true;
            groups.set(index, groups.get(index).withSelected(selected));
        }
        if (changed) persist();
    }

    private void selectRelativeTab(int amount) {
        if (groups.isEmpty()) return;
        int target = Math.floorMod(tabs.getSelectedIndex() + amount, groups.size());
        tabs.setSelectedIndex(target);
    }

    private void createGroup() {
        String name = JOptionPane.showInputDialog(this, "Group name", "New Group");
        if (name == null) return;
        name = name.strip();
        if (name.isEmpty()) {
            showMessage("A group name cannot be empty.", JOptionPane.WARNING_MESSAGE);
            return;
        }
        flushCurrentEdit();
        NoteGroup group = NoteGroup.create(name);
        groups.add(group);
        selectIndexAndSave(groups.size() - 1);
    }

    private void renameSelectedGroup() {
        int index = tabs.getSelectedIndex();
        if (index < 0) return;
        NoteGroup current = groups.get(index);
        String name = JOptionPane.showInputDialog(this, "Group name", current.name());
        if (name == null) return;
        name = name.strip();
        if (name.isEmpty()) {
            showMessage("A group name cannot be empty.", JOptionPane.WARNING_MESSAGE);
            return;
        }
        groups.set(index, current.withName(name));
        tabs.setTitleAt(index, name);
        persist();
    }

    private void deleteSelectedGroup() {
        int index = tabs.getSelectedIndex();
        if (index < 0) return;
        NoteGroup group = groups.get(index);
        String message = "Delete ‘" + group.name() + "’ and its " + group.notes().size() + " notes?";
        if (JOptionPane.showConfirmDialog(this, message, "Delete group", JOptionPane.YES_NO_OPTION,
                JOptionPane.WARNING_MESSAGE) != JOptionPane.YES_OPTION) return;
        flushCurrentEdit();
        groups.remove(index);
        if (groups.isEmpty()) groups.add(NoteGroup.create("New Group"));
        selectIndexAndSave(Math.min(index, groups.size() - 1));
    }

    private void selectIndexAndSave(int index) {
        normalizeSelectionAt(index);
        rebuildTabs();
        persist();
    }

    private void normalizeSelectionAt(int selectedIndex) {
        for (int index = 0; index < groups.size(); index++) {
            groups.set(index, groups.get(index).withSelected(index == selectedIndex));
        }
    }

    private void updateGroup(NoteGroup changed) {
        for (int index = 0; index < groups.size(); index++) {
            if (groups.get(index).id().equals(changed.id())) {
                NoteGroup current = groups.get(index);
                groups.set(index, changed.withSelected(current.selected()).withColor(current.color()));
                break;
            }
        }
        persist();
    }

    private void reorderTab(int source, int target) {
        if (source < 0 || target < 0 || source >= groups.size() || target >= groups.size()
            || source == target) return;
        flushCurrentEdit();
        reordering = true;
        java.awt.Component component = tabs.getComponentAt(source);
        String title = tabs.getTitleAt(source);
        String tooltip = tabs.getToolTipTextAt(source);
        tabs.removeTabAt(source);
        tabs.insertTab(title, null, component, tooltip, target);
        tabs.setSelectedComponent(component);
        groups.clear();
        for (int index = 0; index < tabs.getTabCount() - 1; index++) {
            GroupPanel panel = (GroupPanel) tabs.getComponentAt(index);
            groups.add(panel.group().withSelected(index == tabs.getSelectedIndex()));
        }
        updateTabForegrounds();
        reordering = false;
        persist();
    }

    private void flushCurrentEdit() {
        int index = tabs.getSelectedIndex();
        if (index >= 0 && index < groups.size()) {
            ((GroupPanel) tabs.getComponentAt(index)).flushPendingEdit();
        }
    }

    private void persist() {
        try {
            store.save(List.copyOf(groups));
            saveStatus.setForeground(UiPalette.ACCENT);
            saveStatus.setText("●  Saved");
        } catch (IOException exception) {
            showSaveError(exception.getMessage());
        }
    }

    private void backupOnExit() {
        try {
            store.backupNow();
        } catch (IOException ignored) {
            // Exiting must not be blocked by a failed backup attempt.
        }
    }

    private void showSaveError(String message) {
        saveStatus.setForeground(UiPalette.CANCELLED);
        saveStatus.setText("Save failed: " + message);
        saveStatus.setToolTipText(message);
    }

    private void showMessage(String message, int type) {
        JOptionPane.showMessageDialog(this, message, "Noteworthy", type);
    }

    static void styleButton(JButton button) {
        button.setForeground(UiPalette.TEXT);
        button.setBackground(UiPalette.RAISED);
        button.setBorder(BorderFactory.createCompoundBorder(
                BorderFactory.createLineBorder(UiPalette.BORDER),
                BorderFactory.createEmptyBorder(7, 12, 7, 12)));
        button.setFocusPainted(true);
        button.setFocusable(true);
        button.setOpaque(true);
        button.setFont(button.getFont().deriveFont(Font.BOLD, 12f));
        button.setUI(new javax.swing.plaf.basic.BasicButtonUI());
        button.addFocusListener(new java.awt.event.FocusAdapter() {
            @Override public void focusGained(java.awt.event.FocusEvent event) {
                button.setBorder(BorderFactory.createCompoundBorder(
                        BorderFactory.createLineBorder(UiPalette.FOCUS, 2),
                        BorderFactory.createEmptyBorder(6, 11, 6, 11)));
            }
            @Override public void focusLost(java.awt.event.FocusEvent event) {
                button.setBorder(BorderFactory.createCompoundBorder(
                        BorderFactory.createLineBorder(UiPalette.BORDER),
                        BorderFactory.createEmptyBorder(7, 12, 7, 12)));
            }
        });
    }

    private record ThemePairIcon(Color inactive, Color selected, int width, boolean active) implements Icon {
        private static final int HEIGHT = 24;

        @Override public int getIconWidth() { return width; }
        @Override public int getIconHeight() { return HEIGHT; }

        @Override
        public void paintIcon(Component component, Graphics graphics, int x, int y) {
            Color previous = graphics.getColor();
            int halfWidth = (width - 4) / 2;
            graphics.setColor(inactive);
            graphics.fillRect(x, y + 2, halfWidth, HEIGHT - 4);
            graphics.setColor(active ? UiPalette.ACCENT : Color.WHITE);
            graphics.drawRect(x, y + 2, halfWidth - 1, HEIGHT - 5);
            graphics.setColor(selected);
            graphics.fillRect(x + halfWidth + 4, y + 2, halfWidth, HEIGHT - 4);
            graphics.setColor(active ? UiPalette.ACCENT : new Color(105, 112, 122));
            graphics.drawRect(x + halfWidth + 4, y + 2, halfWidth - 1, HEIGHT - 5);
            graphics.setColor(previous);
        }
    }
}