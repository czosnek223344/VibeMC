package dev.vibemc.ui;

import com.google.gson.*;
import dev.vibemc.Main;
import dev.vibemc.game.FabricSetup;

import javax.swing.*;
import javax.swing.border.*;
import java.awt.*;
import java.awt.event.*;
import java.io.*;
import java.net.URI;
import java.net.http.*;
import java.time.Duration;
import java.util.*;
import java.util.List;

public class ModsTab extends JPanel {

    private static final Color ORANGE  = new Color(0xD4845A);
    private static final Color RED     = new Color(0xB85C4C);
    private static final Color BG      = new Color(0x1A1714);
    private static final Color PANEL   = new Color(0x222019);
    private static final Color PANEL2  = new Color(0x2C2926);
    private static final Color TEXT    = new Color(0xEDE8E3);
    private static final Color DIM     = new Color(0x8A8480);
    private static final Color BORDER  = new Color(0x38342F);

    private DefaultListModel<ModEntry> modListModel;
    private JList<ModEntry> modList;
    private JLabel modCountLabel;

    // Modrinth search
    private JTextField searchField;
    private DefaultListModel<ModrinthResult> searchModel;
    private JList<ModrinthResult> searchList;
    private JButton searchBtn;
    private JLabel searchStatus;

    public ModsTab() {
        setBackground(BG);
        setLayout(new GridLayout(1, 2, 1, 0));
        setBorder(new EmptyBorder(0, 0, 0, 0));

        add(buildInstalledPanel());
        add(buildBrowserPanel());
    }

    // ── Installed mods panel ──────────────────────────────────────────────────

    private JPanel buildInstalledPanel() {
        JPanel panel = new JPanel(new BorderLayout());
        panel.setBackground(PANEL);
        panel.setBorder(BorderFactory.createMatteBorder(0, 0, 0, 1, BORDER));

        // Header
        JPanel header = new JPanel(new BorderLayout());
        header.setBackground(new Color(0x16140F));
        header.setBorder(new EmptyBorder(10, 14, 10, 14));

        JLabel title = new JLabel("INSTALLED MODS");
        title.setForeground(ORANGE);
        title.setFont(new Font("Segoe UI", Font.BOLD, 13));
        header.add(title, BorderLayout.WEST);

        modCountLabel = new JLabel("0 mods");
        modCountLabel.setForeground(DIM);
        modCountLabel.setFont(new Font("Segoe UI", Font.PLAIN, 11));
        header.add(modCountLabel, BorderLayout.EAST);
        panel.add(header, BorderLayout.NORTH);

        // Mod list
        modListModel = new DefaultListModel<>();
        modList = new JList<>(modListModel);
        modList.setBackground(new Color(0x16140F));
        modList.setForeground(TEXT);
        modList.setSelectionBackground(new Color(0x3A2518));
        modList.setSelectionForeground(ORANGE);
        modList.setCellRenderer(new ModCellRenderer());
        modList.setBorder(new EmptyBorder(4, 0, 4, 0));
        modList.setFont(new Font("Segoe UI", Font.PLAIN, 13));

        // Right-click on mod
        JPopupMenu modMenu = new JPopupMenu();
        JMenuItem deleteModItem = new JMenuItem("Delete Mod");
        deleteModItem.setForeground(RED);
        deleteModItem.addActionListener(e -> deleteSelectedMod());
        modMenu.add(deleteModItem);
        modList.setComponentPopupMenu(modMenu);

        JScrollPane scroll = new JScrollPane(modList);
        scroll.setBorder(BorderFactory.createEmptyBorder());
        scroll.setBackground(new Color(0x16140F));
        panel.add(scroll, BorderLayout.CENTER);

        // Buttons
        JPanel btns = new JPanel(new FlowLayout(FlowLayout.LEFT, 8, 8));
        btns.setBackground(PANEL);
        btns.setBorder(BorderFactory.createMatteBorder(1, 0, 0, 0, BORDER));

        JButton openFolderBtn = buildBtn("Open Folder", false);
        openFolderBtn.addActionListener(e -> openModsFolder());

        JButton refreshBtn = buildBtn("Refresh", false);
        refreshBtn.addActionListener(e -> refreshModList());

        btns.add(openFolderBtn);
        btns.add(refreshBtn);
        panel.add(btns, BorderLayout.SOUTH);

        refreshModList();
        return panel;
    }

    // ── Modrinth browser panel ────────────────────────────────────────────────

    private JPanel buildBrowserPanel() {
        JPanel panel = new JPanel(new BorderLayout());
        panel.setBackground(PANEL);

        // Header
        JPanel header = new JPanel(new BorderLayout());
        header.setBackground(new Color(0x16140F));
        header.setBorder(new EmptyBorder(10, 14, 10, 14));
        JLabel title = new JLabel("BROWSE MODRINTH");
        title.setForeground(ORANGE);
        title.setFont(new Font("Segoe UI", Font.BOLD, 13));
        header.add(title, BorderLayout.WEST);
        JLabel hint = new JLabel("1.21.4 • Fabric  ");
        hint.setForeground(DIM);
        hint.setFont(new Font("Segoe UI", Font.PLAIN, 11));
        header.add(hint, BorderLayout.EAST);
        // Search bar
        JPanel searchBar = new JPanel(new BorderLayout(6, 0));
        searchBar.setBackground(PANEL2);
        searchBar.setBorder(new EmptyBorder(8, 10, 8, 10));

        searchField = new JTextField();
        searchField.setBackground(new Color(0x16140F));
        searchField.setForeground(TEXT);
        searchField.setCaretColor(ORANGE);
        searchField.setFont(new Font("Segoe UI", Font.PLAIN, 13));
        searchField.setBorder(BorderFactory.createCompoundBorder(
            BorderFactory.createLineBorder(BORDER),
            new EmptyBorder(5, 8, 5, 8)
        ));
        searchField.setToolTipText("Search Modrinth for Fabric 1.21.4 mods...");
        searchField.addActionListener(e -> doModrinthSearch());

        searchBtn = buildBtn("Search", true);
        searchBtn.addActionListener(e -> doModrinthSearch());

        searchBar.add(searchField, BorderLayout.CENTER);
        searchBar.add(searchBtn, BorderLayout.EAST);

        // Combine header + searchBar into one NORTH panel (can't add two to NORTH)
        JPanel topPanel = new JPanel(new BorderLayout());
        topPanel.setOpaque(false);
        topPanel.add(header, BorderLayout.NORTH);
        topPanel.add(searchBar, BorderLayout.CENTER);
        panel.add(topPanel, BorderLayout.NORTH);

        // Results
        searchModel = new DefaultListModel<>();
        searchList = new JList<>(searchModel);
        searchList.setBackground(new Color(0x16140F));
        searchList.setForeground(TEXT);
        searchList.setSelectionBackground(new Color(0x3A2518));
        searchList.setSelectionForeground(ORANGE);
        searchList.setCellRenderer(new ModrinthCellRenderer());
        searchList.setFixedCellHeight(62);

        // Double-click = download
        searchList.addMouseListener(new MouseAdapter() {
            @Override public void mouseClicked(MouseEvent e) {
                if (e.getClickCount() == 2) downloadSelectedMod();
            }
        });

        JScrollPane scroll = new JScrollPane(searchList);
        scroll.setBorder(BorderFactory.createEmptyBorder());
        panel.add(scroll, BorderLayout.CENTER);

        // Status + buttons
        JPanel bottom = new JPanel(new BorderLayout());
        bottom.setBackground(PANEL);
        bottom.setBorder(BorderFactory.createMatteBorder(1, 0, 0, 0, BORDER));

        searchStatus = new JLabel("  Search for mods above, double-click to download");
        searchStatus.setForeground(DIM);
        searchStatus.setFont(new Font("Segoe UI", Font.PLAIN, 11));
        searchStatus.setBorder(new EmptyBorder(6, 4, 6, 4));

        JPanel btnRow = new JPanel(new FlowLayout(FlowLayout.RIGHT, 8, 6));
        btnRow.setOpaque(false);
        JButton downloadBtn = buildBtn("Download Selected", true);
        downloadBtn.addActionListener(e -> downloadSelectedMod());
        JButton openModrinthBtn = buildBtn("Open Modrinth.com", false);
        openModrinthBtn.addActionListener(e -> {
            try { Desktop.getDesktop().browse(new URI("https://modrinth.com/mods?g=categories:fabric&v=" + Main.GAME_VERSION)); }
            catch (Exception ex) { ex.printStackTrace(); }
        });
        btnRow.add(openModrinthBtn);
        btnRow.add(downloadBtn);

        bottom.add(searchStatus, BorderLayout.CENTER);
        bottom.add(btnRow, BorderLayout.EAST);
        panel.add(bottom, BorderLayout.SOUTH);

        return panel;
    }

    // ── Modrinth search ───────────────────────────────────────────────────────

    private static final HttpClient HTTP = HttpClient.newBuilder()
        .connectTimeout(Duration.ofSeconds(15))
        .followRedirects(HttpClient.Redirect.NORMAL)
        .build();

    record ModrinthResult(String slug, String title, String description, long downloads) {
        @Override public String toString() { return title; }
    }

    private void doModrinthSearch() {
        String query = searchField.getText().trim();
        if (query.isEmpty()) return;

        searchBtn.setEnabled(false);
        searchStatus.setText("  Searching...");
        searchModel.clear();

        SwingWorker<List<ModrinthResult>, Void> worker = new SwingWorker<>() {
            @Override protected List<ModrinthResult> doInBackground() throws Exception {
                String encoded = java.net.URLEncoder.encode(query, java.nio.charset.StandardCharsets.UTF_8);
                String facets  = "[[\"project_type:mod\"],[\"categories:fabric\"],[\"versions:" + Main.GAME_VERSION + "\"]]";
                String url     = "https://api.modrinth.com/v2/search?query=" + encoded
                        + "&facets=" + java.net.URLEncoder.encode(facets, java.nio.charset.StandardCharsets.UTF_8)
                        + "&limit=20";

                HttpRequest req = HttpRequest.newBuilder()
                        .uri(URI.create(url))
                        .header("User-Agent", "VibeMCLauncher/1.0")
                        .GET().timeout(Duration.ofSeconds(15))
                        .build();
                HttpResponse<String> resp = HTTP.send(req, HttpResponse.BodyHandlers.ofString());
                if (resp.statusCode() != 200) throw new IOException("HTTP " + resp.statusCode());

                JsonObject json = JsonParser.parseString(resp.body()).getAsJsonObject();
                JsonArray hits  = json.getAsJsonArray("hits");
                List<ModrinthResult> results = new ArrayList<>();
                for (JsonElement el : hits) {
                    JsonObject o    = el.getAsJsonObject();
                    String slug     = o.get("slug").getAsString();
                    String title    = o.get("title").getAsString();
                    String desc     = o.get("description").getAsString();
                    long downloads  = o.get("downloads").getAsLong();
                    results.add(new ModrinthResult(slug, title, desc, downloads));
                }
                return results;
            }

            @Override protected void done() {
                searchBtn.setEnabled(true);
                try {
                    List<ModrinthResult> results = get();
                    searchModel.clear();
                    results.forEach(searchModel::addElement);
                    searchStatus.setText(results.isEmpty()
                        ? "  No mods found for \"" + searchField.getText().trim()
                            + "\" on Fabric " + Main.GAME_VERSION
                        : "  " + results.size() + " result" + (results.size() == 1 ? "" : "s")
                            + " — double-click to download");
                } catch (Exception e) {
                    searchStatus.setText("  Search failed: " + e.getMessage());
                }
            }
        };
        worker.execute();
    }

    private void downloadSelectedMod() {
        ModrinthResult sel = searchList.getSelectedValue();
        if (sel == null) { searchStatus.setText("  Select a mod first"); return; }

        searchStatus.setText("  Fetching download link for " + sel.title() + "...");

        SwingWorker<File, Void> worker = new SwingWorker<>() {
            boolean alreadyInstalled = false;

            @Override protected File doInBackground() throws Exception {
                HttpRequest req = HttpRequest.newBuilder()
                        .uri(URI.create("https://api.modrinth.com/v2/project/" + sel.slug() + "/version"
                            + "?loaders=%5B%22fabric%22%5D&game_versions=%5B%22" + Main.GAME_VERSION + "%22%5D"))
                        .header("User-Agent", "VibeMCLauncher/1.0")
                        .GET().timeout(Duration.ofSeconds(15))
                        .build();
                HttpResponse<String> resp = HTTP.send(req, HttpResponse.BodyHandlers.ofString());
                if (resp.statusCode() != 200) throw new IOException("No versions found for this mod");

                JsonArray versions = JsonParser.parseString(resp.body()).getAsJsonArray();
                if (versions.isEmpty()) throw new IOException("No " + Main.GAME_VERSION + " Fabric version found");

                JsonObject latest  = versions.get(0).getAsJsonObject();
                JsonArray files    = latest.getAsJsonArray("files");
                if (files == null || files.isEmpty())
                    throw new IOException("No downloadable files found for this mod version");
                JsonObject primary = null;
                for (JsonElement f : files) {
                    JsonObject fo = f.getAsJsonObject();
                    if (fo.has("primary") && fo.get("primary").getAsBoolean()) { primary = fo; break; }
                }
                if (primary == null) primary = files.get(0).getAsJsonObject();

                String downloadUrl = primary.get("url").getAsString();
                String filename    = primary.get("filename").getAsString();
                File dest = new File(Main.MODS_DIR, filename);
                if (dest.exists()) { alreadyInstalled = true; return dest; }
                FabricSetup.downloadIfMissing(downloadUrl, dest, null, null, 0, 0);
                return dest;
            }

            @Override protected void done() {
                try {
                    File f = get();
                    searchStatus.setText(alreadyInstalled
                        ? "  Already installed: " + f.getName()
                        : "  Downloaded: " + f.getName());
                    refreshModList();
                } catch (Exception e) {
                    searchStatus.setText("  Download failed: " + e.getMessage());
                }
            }
        };
        worker.execute();
    }

    // ── Installed mods ────────────────────────────────────────────────────────

    record ModEntry(File file) {
        @Override public String toString() { return file.getName(); }
        public boolean isDisabled() { return file.getName().endsWith(".disabled"); }
        public String displayName() {
            String n = file.getName();
            if (n.endsWith(".disabled")) n = n.substring(0, n.length() - 9);
            return n;
        }
        public String sizeStr() {
            long b = file.length();
            if (b < 1024) return b + " B";
            if (b < 1024*1024) return (b/1024) + " KB";
            return String.format("%.1f MB", b / (1024.0*1024.0));
        }
    }

    private void refreshModList() {
        modListModel.clear();
        File[] files = Main.MODS_DIR.listFiles();
        if (files != null) {
            Arrays.sort(files, Comparator.comparing(File::getName));
            for (File f : files) {
                if (f.isFile() && (f.getName().endsWith(".jar") || f.getName().endsWith(".disabled"))) {
                    modListModel.addElement(new ModEntry(f));
                }
            }
        }
        int count = modListModel.size();
        modCountLabel.setText(count + " mod" + (count == 1 ? "" : "s"));
    }

    private void deleteSelectedMod() {
        ModEntry sel = modList.getSelectedValue();
        if (sel == null) return;
        int confirm = JOptionPane.showConfirmDialog(this,
            "Delete '" + sel.displayName() + "'?",
            "Confirm Delete", JOptionPane.YES_NO_OPTION, JOptionPane.WARNING_MESSAGE);
        if (confirm == JOptionPane.YES_OPTION) {
            sel.file().delete();
            refreshModList();
        }
    }

    private void openModsFolder() {
        try { Desktop.getDesktop().open(Main.MODS_DIR); }
        catch (Exception e) {
            try {
                String os = System.getProperty("os.name", "").toLowerCase();
                if (os.contains("linux"))
                    new ProcessBuilder("xdg-open", Main.MODS_DIR.getAbsolutePath()).start();
                else if (os.contains("mac"))
                    new ProcessBuilder("open", Main.MODS_DIR.getAbsolutePath()).start();
                else
                    new ProcessBuilder("explorer", Main.MODS_DIR.getAbsolutePath()).start();
            } catch (Exception ex) { System.err.println("[Mods] Cannot open folder: " + ex.getMessage()); }
        }
    }

    // ── Cell renderers ────────────────────────────────────────────────────────

    private class ModCellRenderer extends DefaultListCellRenderer {
        @Override public Component getListCellRendererComponent(JList<?> list, Object value,
                int index, boolean isSelected, boolean hasFocus) {
            JPanel p = new JPanel(new BorderLayout(8, 0));
            p.setBorder(BorderFactory.createCompoundBorder(
                BorderFactory.createMatteBorder(0, 0, 1, 0, BORDER),
                new EmptyBorder(7, 12, 7, 12)
            ));
            p.setBackground(isSelected ? new Color(0x2E1E0E) : (index % 2 == 0 ? new Color(0x16140F) : new Color(0x1E1B17)));

            ModEntry mod = (ModEntry) value;
            JLabel name  = new JLabel(mod.displayName());
            name.setFont(new Font("Segoe UI", Font.PLAIN, 12));
            name.setForeground(mod.isDisabled() ? DIM : TEXT);

            JLabel size = new JLabel(mod.sizeStr());
            size.setFont(new Font("Segoe UI", Font.PLAIN, 11));
            size.setForeground(DIM);

            p.add(name, BorderLayout.CENTER);
            p.add(size, BorderLayout.EAST);
            return p;
        }
    }

    private class ModrinthCellRenderer extends DefaultListCellRenderer {
        @Override public Component getListCellRendererComponent(JList<?> list, Object value,
                int index, boolean isSelected, boolean hasFocus) {
            JPanel p = new JPanel(new BorderLayout(8, 2));
            p.setBorder(BorderFactory.createCompoundBorder(
                BorderFactory.createMatteBorder(0, 0, 1, 0, BORDER),
                new EmptyBorder(8, 12, 8, 12)
            ));
            p.setBackground(isSelected ? new Color(0x2E1E0E) : (index % 2 == 0 ? new Color(0x16140F) : new Color(0x1E1B17)));

            ModrinthResult mod = (ModrinthResult) value;
            JLabel name = new JLabel(mod.title());
            name.setFont(new Font("Segoe UI", Font.BOLD, 12));
            name.setForeground(isSelected ? ORANGE : TEXT);

            JLabel desc = new JLabel(mod.description().length() > 80
                ? mod.description().substring(0, 80) + "..." : mod.description());
            desc.setFont(new Font("Segoe UI", Font.PLAIN, 11));
            desc.setForeground(DIM);

            String dlStr = mod.downloads() > 1_000_000
                ? String.format("%.1fM downloads", mod.downloads() / 1_000_000.0)
                : mod.downloads() > 1000 ? (mod.downloads() / 1000) + "K downloads"
                : mod.downloads() + " downloads";
            JLabel downloads = new JLabel(dlStr);
            downloads.setFont(new Font("Segoe UI", Font.PLAIN, 10));
            downloads.setForeground(new Color(0x8A8480));

            JPanel text = new JPanel();
            text.setOpaque(false);
            text.setLayout(new BoxLayout(text, BoxLayout.Y_AXIS));
            text.add(name);
            text.add(Box.createVerticalStrut(2));
            text.add(desc);
            p.add(text, BorderLayout.CENTER);
            p.add(downloads, BorderLayout.EAST);
            return p;
        }
    }

    // ── Util ──────────────────────────────────────────────────────────────────

    private JButton buildBtn(String text, boolean accent) {
        JButton btn = new JButton(text);
        btn.setBackground(accent ? ORANGE : PANEL2);
        btn.setForeground(accent ? new Color(0x1A1714) : TEXT);
        btn.setBorder(BorderFactory.createCompoundBorder(
            BorderFactory.createLineBorder(accent ? new Color(0xE09070) : BORDER),
            new EmptyBorder(5, 12, 5, 12)
        ));
        btn.setFocusPainted(false);
        btn.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
        btn.setFont(new Font("Segoe UI", accent ? Font.BOLD : Font.PLAIN, 12));
        return btn;
    }
}
