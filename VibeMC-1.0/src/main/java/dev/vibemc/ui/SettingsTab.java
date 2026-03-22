package dev.vibemc.ui;

import dev.vibemc.Main;
import dev.vibemc.auth.MicrosoftAuth;
import dev.vibemc.config.Settings;
import dev.vibemc.game.FabricSetup;
import dev.vibemc.game.GameLauncher;

import javax.swing.*;
import javax.swing.border.*;
import javax.swing.event.ChangeListener;
import java.awt.*;
import java.io.File;

public class SettingsTab extends JPanel {

    private static final Color ORANGE = new Color(0xFF6A00);
    private static final Color BG     = new Color(0x0D0D0D);
    private static final Color PANEL2 = new Color(0x1F1F1F);
    private static final Color TEXT   = new Color(0xD4D4D4);
    private static final Color DIM    = new Color(0x666666);
    private static final Color BORDER = new Color(0x2A2A2A);

    private final Runnable onSave;

    // Memory
    private JSlider  ramSlider;
    private JLabel   ramLabel;
    private JSpinner ramSpinner;

    // JVM
    private JTextArea jvmArgsArea;

    // Java
    private JTextField javaPathField;
    private JLabel     javaDetectedLabel;

    // Game
    private JSpinner  gameWidthSpinner, gameHeightSpinner;
    private JCheckBox fullscreenBox;

    // Launcher
    private JCheckBox keepOpenBox, showConsoleBox, autoUpdateFabricBox;

    // Performance
    private JSpinner  cpuThreadsSpinner;
    private JSpinner  vramSpinner;

    // Debug
    private JCheckBox debugLoggingBox, dumpCmdBox, skipHashBox;

    public SettingsTab(Runnable onSave) {
        this.onSave = onSave;
        setBackground(BG);
        setLayout(new BorderLayout());
        add(buildScroll(), BorderLayout.CENTER);
    }

    private JScrollPane buildScroll() {
        JPanel content = new JPanel();
        content.setBackground(BG);
        content.setLayout(new BoxLayout(content, BoxLayout.Y_AXIS));
        content.setBorder(new EmptyBorder(16, 20, 16, 20));

        content.add(buildSection("MEMORY", buildMemoryPanel()));
        content.add(vgap());
        content.add(buildSection("JVM ARGUMENTS", buildJvmPanel()));
        content.add(vgap());
        content.add(buildSection("JAVA EXECUTABLE", buildJavaPanel()));
        content.add(vgap());
        content.add(buildSection("GAME WINDOW", buildWindowPanel()));
        content.add(vgap());
        content.add(buildSection("PERFORMANCE LIMITS", buildPerfPanel()));
        content.add(vgap());
        content.add(buildSection("LAUNCHER", buildLauncherPanel()));
        content.add(vgap());
        content.add(buildSection("FOLDERS", buildFoldersPanel()));
        content.add(vgap());
        content.add(buildSection("DEBUG", buildDebugPanel()));
        content.add(vgap());
        content.add(buildSaveRow());

        JScrollPane scroll = new JScrollPane(content);
        scroll.setBorder(BorderFactory.createEmptyBorder());
        scroll.setBackground(BG);
        scroll.getViewport().setBackground(BG);
        scroll.getVerticalScrollBar().setUnitIncrement(20);
        return scroll;
    }

    // ── Memory ────────────────────────────────────────────────────────────────

    private JPanel buildMemoryPanel() {
        JPanel p = new JPanel(new BorderLayout(10, 4));
        p.setOpaque(false);

        int physMb  = Settings.physicalRamMb();
        String physStr = physMb > 0 ? " (system: " + formatRam(physMb) + ")" : "";

        JLabel hint = new JLabel("RAM to allocate" + physStr + ":");
        hint.setForeground(DIM);
        hint.setFont(new Font("Segoe UI", Font.PLAIN, 11));

        ramSlider = new JSlider(512, Settings.maxRamMb(), Settings.ramMb);
        ramSlider.setOpaque(false);
        ramSlider.setForeground(ORANGE);
        ramSlider.setMajorTickSpacing(8192);
        ramSlider.setPaintTicks(true);

        ramLabel = new JLabel(formatRam(Settings.ramMb));
        ramLabel.setForeground(ORANGE);
        ramLabel.setFont(new Font("Segoe UI", Font.BOLD, 14));
        ramLabel.setPreferredSize(new Dimension(80, 24));

        SpinnerNumberModel model = new SpinnerNumberModel(Settings.ramMb, 512, Settings.maxRamMb(), 256);
        ramSpinner = new JSpinner(model);
        ramSpinner.setPreferredSize(new Dimension(110, 28));

        ChangeListener sync = e -> {
            int val = ramSlider.getValue();
            ramLabel.setText(formatRam(val));
            if ((int) ramSpinner.getValue() != val) ramSpinner.setValue(val);
        };
        ramSlider.addChangeListener(sync);
        ramSpinner.addChangeListener(e -> {
            int val = (int) ramSpinner.getValue();
            if (ramSlider.getValue() != val) ramSlider.setValue(val);
        });

        // Show warning if allocating more than 80% of physical RAM
        JLabel warn = new JLabel("");
        warn.setForeground(new Color(0xFF9800));
        warn.setFont(new Font("Segoe UI", Font.PLAIN, 11));
        if (physMb > 0) {
            ramSlider.addChangeListener(e -> {
                int pct = (int)((ramSlider.getValue() * 100.0) / physMb);
                warn.setText(pct > 80 ? "⚠ Allocating >" + pct + "% of system RAM — may cause instability" : "");
            });
        }

        JPanel sliderRow = new JPanel(new BorderLayout(8, 0));
        sliderRow.setOpaque(false);
        sliderRow.add(ramSlider, BorderLayout.CENTER);
        sliderRow.add(ramLabel, BorderLayout.EAST);

        JPanel spinRow = new JPanel(new FlowLayout(FlowLayout.LEFT, 0, 0));
        spinRow.setOpaque(false);
        JLabel manualLbl = new JLabel("Manual (MB): ");
        manualLbl.setForeground(DIM);
        spinRow.add(manualLbl);
        spinRow.add(ramSpinner);
        spinRow.add(Box.createHorizontalStrut(12));
        spinRow.add(warn);

        p.add(hint, BorderLayout.NORTH);
        p.add(sliderRow, BorderLayout.CENTER);
        p.add(spinRow, BorderLayout.SOUTH);
        return p;
    }

    // ── JVM ───────────────────────────────────────────────────────────────────

    private JPanel buildJvmPanel() {
        JPanel p = new JPanel(new BorderLayout(0, 4));
        p.setOpaque(false);

        JLabel hint = new JLabel("Extra JVM flags (appended after -Xmx/-Xms):");
        hint.setForeground(DIM);
        hint.setFont(new Font("Segoe UI", Font.PLAIN, 11));

        jvmArgsArea = new JTextArea(Settings.jvmArgs, 4, 40);
        jvmArgsArea.setFont(new Font("Consolas", Font.PLAIN, 11));
        jvmArgsArea.setLineWrap(true);
        jvmArgsArea.setWrapStyleWord(false);
        jvmArgsArea.setBackground(new Color(0x111111));
        jvmArgsArea.setForeground(TEXT);
        jvmArgsArea.setCaretColor(ORANGE);
        jvmArgsArea.setBorder(new EmptyBorder(6, 8, 6, 8));

        JButton reset = new JButton("Reset to defaults");
        styleSmallButton(reset);
        reset.addActionListener(e -> jvmArgsArea.setText(
            "-XX:+UseG1GC -XX:+ParallelRefProcEnabled -XX:MaxGCPauseMillis=200 " +
            "-XX:+UnlockExperimentalVMOptions -XX:+DisableExplicitGC -XX:G1NewSizePercent=30 " +
            "-XX:G1MaxNewSizePercent=40 -XX:G1HeapRegionSize=8M -XX:G1ReservePercent=20 " +
            "-XX:G1HeapWastePercent=5 -XX:G1MixedGCCountTarget=4 " +
            "-XX:InitiatingHeapOccupancyPercent=15 -XX:G1MixedGCLiveThresholdPercent=90 " +
            "-XX:G1RSetUpdatingPauseTimePercent=5 -XX:SurvivorRatio=32 " +
            "-XX:+PerfDisableSharedMem -XX:MaxTenuringThreshold=1"));

        JPanel btnRow = new JPanel(new FlowLayout(FlowLayout.LEFT, 0, 0));
        btnRow.setOpaque(false);
        btnRow.add(reset);

        p.add(hint, BorderLayout.NORTH);
        JScrollPane jvmScroll = new JScrollPane(jvmArgsArea);
        jvmScroll.setBorder(BorderFactory.createLineBorder(BORDER));
        p.add(jvmScroll, BorderLayout.CENTER);
        p.add(btnRow, BorderLayout.SOUTH);
        return p;
    }

    // ── Java ──────────────────────────────────────────────────────────────────

    private JPanel buildJavaPanel() {
        JPanel p = new JPanel(new BorderLayout(0, 4));
        p.setOpaque(false);

        String detected = GameLauncher.resolveJava();
        javaDetectedLabel = new JLabel("Auto-detected: " + detected);
        javaDetectedLabel.setForeground(DIM);
        javaDetectedLabel.setFont(new Font("Segoe UI", Font.PLAIN, 11));

        javaPathField = new JTextField(Settings.javaPath);
        javaPathField.setFont(new Font("Consolas", Font.PLAIN, 12));

        JButton browse = new JButton("Browse...");
        styleSmallButton(browse);
        browse.addActionListener(e -> {
            JFileChooser fc = new JFileChooser();
            fc.setDialogTitle("Select java executable");
            if (fc.showOpenDialog(this) == JFileChooser.APPROVE_OPTION)
                javaPathField.setText(fc.getSelectedFile().getAbsolutePath());
        });

        JButton clear = new JButton("Clear (auto)");
        styleSmallButton(clear);
        clear.addActionListener(e -> javaPathField.setText(""));

        JPanel row = new JPanel(new BorderLayout(6, 0));
        row.setOpaque(false);
        row.add(javaPathField, BorderLayout.CENTER);

        JPanel bRow = new JPanel(new FlowLayout(FlowLayout.LEFT, 4, 0));
        bRow.setOpaque(false);
        bRow.add(browse);
        bRow.add(clear);

        p.add(javaDetectedLabel, BorderLayout.NORTH);
        p.add(row, BorderLayout.CENTER);
        p.add(bRow, BorderLayout.SOUTH);
        return p;
    }

    // ── Window ────────────────────────────────────────────────────────────────

    private JPanel buildWindowPanel() {
        JPanel p = new JPanel(new FlowLayout(FlowLayout.LEFT, 12, 4));
        p.setOpaque(false);

        gameWidthSpinner  = new JSpinner(new SpinnerNumberModel(Settings.gameWidth, 320, 7680, 10));
        gameHeightSpinner = new JSpinner(new SpinnerNumberModel(Settings.gameHeight, 240, 4320, 10));
        fullscreenBox     = new JCheckBox("Fullscreen on launch", Settings.fullscreen);
        fullscreenBox.setOpaque(false);

        p.add(lbl("Width:")); p.add(gameWidthSpinner);
        p.add(lbl("Height:")); p.add(gameHeightSpinner);
        p.add(Box.createHorizontalStrut(8));
        p.add(fullscreenBox);
        return p;
    }

    // ── Performance ───────────────────────────────────────────────────────────

    private JPanel buildPerfPanel() {
        JPanel p = new JPanel(new GridLayout(0, 1, 0, 6));
        p.setOpaque(false);

        int cpuCores = Settings.cpuCores();
        cpuThreadsSpinner = new JSpinner(new SpinnerNumberModel(
            Settings.maxCpuThreads, 0, cpuCores * 2, 1));
        vramSpinner = new JSpinner(new SpinnerNumberModel(
            Settings.maxVramMb, 0, 65536, 256));

        JPanel cpuRow = new JPanel(new FlowLayout(FlowLayout.LEFT, 8, 0));
        cpuRow.setOpaque(false);
        cpuRow.add(lbl("Max CPU threads (0 = all, system has " + cpuCores + "):"));
        cpuRow.add(cpuThreadsSpinner);

        JPanel vramRow = new JPanel(new FlowLayout(FlowLayout.LEFT, 8, 0));
        vramRow.setOpaque(false);
        vramRow.add(lbl("VRAM limit hint MB (0 = unlimited, informational):"));
        vramRow.add(vramSpinner);

        JLabel vramNote = new JLabel("Note: VRAM limit is informational only — MC/drivers manage VRAM allocation.");
        vramNote.setForeground(DIM);
        vramNote.setFont(new Font("Segoe UI", Font.PLAIN, 11));

        p.add(cpuRow);
        p.add(vramRow);
        p.add(vramNote);
        return p;
    }

    // ── Launcher ──────────────────────────────────────────────────────────────

    private JPanel buildLauncherPanel() {
        JPanel p = new JPanel(new GridLayout(0, 1, 0, 4));
        p.setOpaque(false);

        keepOpenBox       = new JCheckBox("Keep launcher open while game is running", Settings.keepOpen);
        showConsoleBox    = new JCheckBox("Show game console output window", Settings.showConsole);
        autoUpdateFabricBox = new JCheckBox("Auto-update Fabric loader", Settings.autoUpdateFabric);

        for (JCheckBox cb : new JCheckBox[]{keepOpenBox, showConsoleBox, autoUpdateFabricBox}) {
            cb.setOpaque(false);
            p.add(cb);
        }
        return p;
    }

    // ── Folders ───────────────────────────────────────────────────────────────

    private JPanel buildFoldersPanel() {
        JPanel p = new JPanel(new GridLayout(0, 1, 0, 6));
        p.setOpaque(false);

        p.add(folderRow("Game files",   Main.MINECRAFT_DIR));
        p.add(folderRow("Mods folder",  Main.MODS_DIR));
        p.add(folderRow("Tokens folder", MicrosoftAuth.getTokensDir()));
        p.add(folderRow("Launcher data", Main.BASE_DIR));
        return p;
    }

    private JPanel folderRow(String name, File dir) {
        JPanel row = new JPanel(new BorderLayout(8, 0));
        row.setOpaque(false);

        JLabel lbl = new JLabel(name + ": ");
        lbl.setForeground(TEXT);
        lbl.setPreferredSize(new Dimension(120, 24));

        JTextField field = new JTextField(dir.getAbsolutePath());
        field.setEditable(false);
        field.setFont(new Font("Consolas", Font.PLAIN, 11));
        field.setForeground(DIM);

        JButton open = new JButton("Open");
        styleSmallButton(open);
        open.addActionListener(e -> openFolder(dir));

        row.add(lbl,   BorderLayout.WEST);
        row.add(field, BorderLayout.CENTER);
        row.add(open,  BorderLayout.EAST);
        return row;
    }

    private void openFolder(File dir) {
        dir.mkdirs();
        try { Desktop.getDesktop().open(dir); }
        catch (Exception e) {
            try {
                String os = System.getProperty("os.name","").toLowerCase();
                if (os.contains("linux")) new ProcessBuilder("xdg-open", dir.getAbsolutePath()).start();
                else if (os.contains("mac")) new ProcessBuilder("open", dir.getAbsolutePath()).start();
                else new ProcessBuilder("explorer", dir.getAbsolutePath()).start();
            } catch (Exception ignored) {}
        }
    }

    // ── Debug ─────────────────────────────────────────────────────────────────

    private JPanel buildDebugPanel() {
        JPanel p = new JPanel(new GridLayout(0, 1, 0, 4));
        p.setOpaque(false);

        debugLoggingBox = new JCheckBox("Enable debug logging (log4j2 DEBUG level)", Settings.debugLogging);
        dumpCmdBox      = new JCheckBox("Print full launch command to console", Settings.dumpLaunchCmd);
        skipHashBox     = new JCheckBox("Skip asset hash verification (faster startup)", Settings.skipHashCheck);

        for (JCheckBox cb : new JCheckBox[]{debugLoggingBox, dumpCmdBox, skipHashBox}) {
            cb.setOpaque(false);
            p.add(cb);
        }

        JPanel btnRow = new JPanel(new FlowLayout(FlowLayout.LEFT, 6, 0));
        btnRow.setOpaque(false);

        JButton clearCache = new JButton("Clear asset cache");
        styleSmallButton(clearCache);
        clearCache.addActionListener(e -> {
            int c = JOptionPane.showConfirmDialog(this,
                "Delete all downloaded assets? Game will re-download them on next launch.",
                "Clear Cache", JOptionPane.YES_NO_OPTION);
            if (c == JOptionPane.YES_OPTION) {
                deleteDir(new File(Main.ASSETS_DIR, "objects"));
                JOptionPane.showMessageDialog(this, "Asset cache cleared.");
            }
        });

        JButton reinstall = new JButton("Force reinstall Fabric");
        styleSmallButton(reinstall);
        reinstall.addActionListener(e -> {
            int c = JOptionPane.showConfirmDialog(this,
                "Delete Fabric install? It will be re-installed on next launch.",
                "Reinstall", JOptionPane.YES_NO_OPTION);
            if (c == JOptionPane.YES_OPTION) {
                File profile = FabricSetup.getInstalledProfileFile();
                if (profile != null) profile.delete();
                JOptionPane.showMessageDialog(this, "Fabric profile deleted. Launch again to reinstall.");
            }
        });

        btnRow.add(clearCache);
        btnRow.add(reinstall);
        p.add(btnRow);
        return p;
    }

    private void deleteDir(File dir) {
        if (dir == null || !dir.exists()) return;
        File[] files = dir.listFiles();
        if (files != null) for (File f : files)
            if (f.isDirectory()) deleteDir(f); else f.delete();
        dir.delete();
    }

    // ── Save ──────────────────────────────────────────────────────────────────

    private JPanel buildSaveRow() {
        JButton save = new JButton("Save Settings");
        save.setBackground(ORANGE);
        save.setForeground(Color.BLACK);
        save.setFont(new Font("Segoe UI", Font.BOLD, 13));
        save.setBorder(BorderFactory.createCompoundBorder(
            BorderFactory.createLineBorder(new Color(0xFF8C00)),
            new EmptyBorder(8, 28, 8, 28)));
        save.setFocusPainted(false);
        save.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
        save.addActionListener(e -> saveSettings());

        JPanel p = new JPanel(new FlowLayout(FlowLayout.LEFT, 0, 0));
        p.setOpaque(false);
        p.add(save);
        return p;
    }

    private void saveSettings() {
        Settings.ramMb          = ramSlider.getValue();
        Settings.jvmArgs        = jvmArgsArea.getText().trim();
        Settings.javaPath       = javaPathField.getText().trim();
        Settings.gameWidth      = (int) gameWidthSpinner.getValue();
        Settings.gameHeight     = (int) gameHeightSpinner.getValue();
        Settings.fullscreen     = fullscreenBox.isSelected();
        Settings.keepOpen       = keepOpenBox.isSelected();
        Settings.showConsole    = showConsoleBox.isSelected();
        Settings.autoUpdateFabric = autoUpdateFabricBox.isSelected();
        Settings.maxCpuThreads  = (int) cpuThreadsSpinner.getValue();
        Settings.maxVramMb      = (int) vramSpinner.getValue();
        Settings.debugLogging   = debugLoggingBox.isSelected();
        Settings.dumpLaunchCmd  = dumpCmdBox.isSelected();
        Settings.skipHashCheck  = skipHashBox.isSelected();
        Settings.save();

        // Update Java label after save
        String detected = GameLauncher.resolveJava();
        javaDetectedLabel.setText("Auto-detected: " + detected);

        if (onSave != null) onSave.run();
    }

    // ── UI helpers ────────────────────────────────────────────────────────────

    private JPanel buildSection(String title, JPanel content) {
        JPanel section = new JPanel(new BorderLayout(0, 8));
        section.setOpaque(false);
        section.setAlignmentX(LEFT_ALIGNMENT);
        section.setMaximumSize(new Dimension(Integer.MAX_VALUE, Integer.MAX_VALUE));

        JLabel lbl = new JLabel(title);
        lbl.setForeground(ORANGE);
        lbl.setFont(new Font("Segoe UI", Font.BOLD, 11));
        lbl.setBorder(new EmptyBorder(0, 0, 4, 0));

        JSeparator sep = new JSeparator();
        sep.setForeground(BORDER);

        JPanel header = new JPanel(new BorderLayout());
        header.setOpaque(false);
        header.add(lbl, BorderLayout.WEST);
        header.add(sep, BorderLayout.SOUTH);

        section.add(header, BorderLayout.NORTH);
        section.add(content, BorderLayout.CENTER);
        return section;
    }

    private Box.Filler vgap() {
        return (Box.Filler) Box.createVerticalStrut(18);
    }

    private JLabel lbl(String text) {
        JLabel l = new JLabel(text);
        l.setForeground(TEXT);
        return l;
    }

    private void styleSmallButton(JButton btn) {
        btn.setBackground(PANEL2);
        btn.setForeground(TEXT);
        btn.setBorder(BorderFactory.createCompoundBorder(
            BorderFactory.createLineBorder(BORDER),
            new EmptyBorder(5, 10, 5, 10)));
        btn.setFocusPainted(false);
        btn.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
        btn.setFont(new Font("Segoe UI", Font.PLAIN, 12));
    }

    private static String formatRam(int mb) {
        if (mb >= 1024) return (mb / 1024) + " GB";
        return mb + " MB";
    }
}
