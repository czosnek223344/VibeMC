package dev.vibemc.ui;

import dev.vibemc.Main;
import dev.vibemc.auth.AccountManager;
import dev.vibemc.auth.AccountManager.Account;
import dev.vibemc.auth.MicrosoftAuth;
import dev.vibemc.config.Settings;
import dev.vibemc.game.FabricSetup;
import dev.vibemc.game.GameLauncher;

import javax.swing.*;
import javax.swing.border.*;
import java.awt.*;
import java.io.File;
import java.text.SimpleDateFormat;
import java.util.Date;

public class DebugTab extends JPanel {

    private static final Color BG     = new Color(0x0D0D0D);
    private static final Color PANEL2 = new Color(0x1F1F1F);
    private static final Color TEXT   = new Color(0xD4D4D4);
    private static final Color DIM    = new Color(0x666666);
    private static final Color ORANGE = new Color(0xFF6A00);
    private static final Color BORDER = new Color(0x2A2A2A);
    private static final Color GREEN  = new Color(0x66BB6A);
    private static final Color RED    = new Color(0xCC2200);

    private JTextArea infoArea;

    public DebugTab() {
        setBackground(BG);
        setLayout(new BorderLayout());

        JPanel btnBar = new JPanel(new FlowLayout(FlowLayout.LEFT, 8, 8));
        btnBar.setBackground(new Color(0x111111));
        btnBar.setBorder(BorderFactory.createMatteBorder(0, 0, 1, 0, BORDER));

        JButton refresh = smallBtn("↻ Refresh");
        refresh.setForeground(ORANGE);
        refresh.addActionListener(e -> refreshInfo());

        JButton copy = smallBtn("Copy to clipboard");
        copy.addActionListener(e -> {
            java.awt.datatransfer.StringSelection sel =
                new java.awt.datatransfer.StringSelection(infoArea.getText());
            Toolkit.getDefaultToolkit().getSystemClipboard().setContents(sel, sel);
        });

        btnBar.add(refresh);
        btnBar.add(copy);
        add(btnBar, BorderLayout.NORTH);

        infoArea = new JTextArea();
        infoArea.setFont(new Font("Consolas", Font.PLAIN, 12));
        infoArea.setBackground(new Color(0x0A0A0A));
        infoArea.setForeground(TEXT);
        infoArea.setEditable(false);
        infoArea.setBorder(new EmptyBorder(12, 14, 12, 14));
        infoArea.setLineWrap(false);

        JScrollPane scroll = new JScrollPane(infoArea);
        scroll.setBorder(BorderFactory.createEmptyBorder());
        scroll.getVerticalScrollBar().setUnitIncrement(20);
        add(scroll, BorderLayout.CENTER);

        refreshInfo();
    }

    private void refreshInfo() {
        infoArea.setText("Loading...");
        new Thread(() -> {
            String info = buildInfo();
            SwingUtilities.invokeLater(() -> {
                infoArea.setText(info);
                infoArea.setCaretPosition(0);
            });
        }, "Debug-Refresh").start();
    }

    private String buildInfo() {
        StringBuilder sb = new StringBuilder();
        String ts = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss").format(new Date());
        sb.append("=== VibeMC Debug Info — ").append(ts).append(" ===\n\n");

        // ── System ──
        sb.append("[ SYSTEM ]\n");
        sb.append("  OS         : ").append(System.getProperty("os.name"))
          .append(" ").append(System.getProperty("os.version"))
          .append(" (").append(System.getProperty("os.arch")).append(")\n");
        sb.append("  Java       : ").append(System.getProperty("java.version"))
          .append(" (").append(System.getProperty("java.vendor")).append(")\n");
        sb.append("  Java home  : ").append(System.getProperty("java.home")).append("\n");

        Runtime rt = Runtime.getRuntime();
        long maxMem  = rt.maxMemory()  / 1024 / 1024;
        long totMem  = rt.totalMemory() / 1024 / 1024;
        long freeMem = rt.freeMemory() / 1024 / 1024;
        sb.append("  JVM heap   : ").append(totMem - freeMem).append(" MB used / ")
          .append(maxMem).append(" MB max\n");
        sb.append("  CPU cores  : ").append(Settings.cpuCores()).append("\n");

        int physMb = Settings.physicalRamMb();
        if (physMb > 0) sb.append("  System RAM : ").append(physMb).append(" MB\n");
        sb.append("\n");

        // ── Java detection ──
        sb.append("[ JAVA DETECTION ]\n");
        sb.append("  Configured  : ").append(Settings.javaPath.isBlank() ? "(auto)" : Settings.javaPath).append("\n");
        sb.append("  Resolved to : ").append(GameLauncher.resolveJava()).append("\n\n");

        // ── Paths ──
        sb.append("[ PATHS ]\n");
        sb.append("  Base dir    : ").append(exists(Main.BASE_DIR)).append("\n");
        sb.append("  Minecraft   : ").append(exists(Main.MINECRAFT_DIR)).append("\n");
        sb.append("  Versions    : ").append(exists(Main.VERSIONS_DIR)).append("\n");
        sb.append("  Libraries   : ").append(exists(Main.LIBRARIES_DIR)).append("\n");
        sb.append("  Assets      : ").append(exists(Main.ASSETS_DIR)).append("\n");
        sb.append("  Mods        : ").append(exists(Main.MODS_DIR)).append("\n");
        sb.append("  Tokens      : ").append(exists(MicrosoftAuth.getTokensDir())).append("\n");
        sb.append("  Settings    : ").append(exists(Main.SETTINGS_FILE)).append("\n");
        sb.append("  Accounts    : ").append(exists(Main.ACCOUNTS_FILE)).append("\n\n");

        // ── Installation ──
        sb.append("[ INSTALLATION ]\n");
        File profileFile = FabricSetup.getInstalledProfileFile();
        sb.append("  Fabric installed : ").append(FabricSetup.isInstalled() ? "YES" : "NO").append("\n");
        if (profileFile != null) sb.append("  Profile file : ").append(profileFile).append("\n");
        File clientJar = new File(Main.VERSIONS_DIR,
            Main.GAME_VERSION + "/" + Main.GAME_VERSION + ".jar");
        sb.append("  Client jar   : ").append(exists(clientJar)).append("\n");

        // Count libraries
        int libCount = countFiles(Main.LIBRARIES_DIR, ".jar");
        sb.append("  Libraries    : ").append(libCount).append(" jars\n");
        sb.append("  Game version : ").append(Main.GAME_VERSION).append("\n\n");

        // ── Accounts ──
        sb.append("[ ACCOUNTS ]\n");
        if (AccountManager.getAll().isEmpty()) {
            sb.append("  No accounts loaded\n");
        } else {
            for (Account acc : AccountManager.getAll()) {
                boolean selected = acc.uuid.equals(Settings.lastAccountUuid);
                boolean expired  = acc.isExpired();
                sb.append("  ").append(selected ? "* " : "  ")
                  .append(acc.username)
                  .append(" [").append(acc.uuid).append("]")
                  .append(expired ? " EXPIRED" : " valid")
                  .append(acc.hasRefreshToken() ? " (has refresh token)" : " (no refresh token)")
                  .append("\n");
                if (expired && acc.tokenExpiresAt > 0) {
                    sb.append("      expired: ")
                      .append(new SimpleDateFormat("yyyy-MM-dd HH:mm:ss")
                          .format(new Date(acc.tokenExpiresAt))).append("\n");
                }
            }
        }
        sb.append("\n");

        // ── Settings ──
        sb.append("[ SETTINGS ]\n");
        sb.append("  RAM           : ").append(Settings.ramMb).append(" MB\n");
        sb.append("  Java path     : ").append(Settings.javaPath.isBlank() ? "(auto)" : Settings.javaPath).append("\n");
        sb.append("  Fullscreen    : ").append(Settings.fullscreen).append("\n");
        sb.append("  Keep open     : ").append(Settings.keepOpen).append("\n");
        sb.append("  Show console  : ").append(Settings.showConsole).append("\n");
        sb.append("  CPU threads   : ").append(Settings.maxCpuThreads == 0 ? "unlimited" : String.valueOf(Settings.maxCpuThreads)).append("\n");
        sb.append("  Debug logging : ").append(Settings.debugLogging).append("\n");
        sb.append("  JVM args      : ").append(Settings.jvmArgs).append("\n\n");

        // ── Mods ──
        File modsDir = Main.MODS_DIR;
        File[] mods = modsDir.listFiles(f -> f.getName().endsWith(".jar"));
        sb.append("[ MODS (").append(mods != null ? mods.length : 0).append(") ]\n");
        if (mods != null && mods.length > 0) {
            for (File mod : mods)
                sb.append("  ").append(mod.getName()).append(" (")
                  .append(mod.length() / 1024).append(" KB)\n");
        } else {
            sb.append("  No mods installed\n");
        }

        return sb.toString();
    }

    private String exists(File f) {
        if (f == null) return "(null)";
        return f.getAbsolutePath() + (f.exists() ? " ✓" : " ✗ MISSING");
    }

    private int countFiles(File dir, String ext) {
        if (!dir.exists()) return 0;
        int count = 0;
        File[] files = dir.listFiles();
        if (files == null) return 0;
        for (File f : files) {
            if (f.isDirectory()) count += countFiles(f, ext);
            else if (f.getName().endsWith(ext)) count++;
        }
        return count;
    }

    private JButton smallBtn(String text) {
        JButton btn = new JButton(text);
        btn.setBackground(PANEL2);
        btn.setForeground(TEXT);
        btn.setBorder(BorderFactory.createCompoundBorder(
            BorderFactory.createLineBorder(BORDER),
            new EmptyBorder(5, 10, 5, 10)));
        btn.setFocusPainted(false);
        btn.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
        btn.setFont(new Font("Segoe UI", Font.PLAIN, 12));
        return btn;
    }
}
