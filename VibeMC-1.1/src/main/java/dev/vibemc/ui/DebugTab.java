package dev.vibemc.ui;

import dev.vibemc.Main;
import dev.vibemc.auth.AccountManager;
import dev.vibemc.auth.MicrosoftAuth;
import dev.vibemc.config.Settings;
import dev.vibemc.game.FabricSetup;
import dev.vibemc.game.GameLauncher;

import javax.swing.*;
import javax.swing.border.*;
import java.awt.*;
import java.io.File;
import java.text.SimpleDateFormat;
import java.util.Arrays;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.Date;
import java.util.List;

public class DebugTab extends JPanel {

    private static final Color BG     = new Color(0x1A1714);
    private static final Color PANEL2 = new Color(0x2C2926);
    private static final Color TEXT   = new Color(0xEDE8E3);
    private static final Color DIM    = new Color(0x8A8480);
    private static final Color ORANGE = new Color(0xD4845A);
    private static final Color BORDER = new Color(0x38342F);

    private JTextArea infoArea;

    public DebugTab() {
        setBackground(BG);
        setLayout(new BorderLayout());

        JPanel btnBar = new JPanel(new FlowLayout(FlowLayout.LEFT, 8, 8));
        btnBar.setBackground(new Color(0x16140F));
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
        infoArea.setBackground(new Color(0x130F0D));
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

        // Snapshot all EDT-state BEFORE going off-thread — prevents race conditions
        List<AccountManager.Account> accountsCopy = new ArrayList<>(AccountManager.getAll());
        String lastUuid      = Settings.lastAccountUuid;
        int    ramMb         = Settings.ramMb;
        String javaPath      = Settings.javaPath;
        boolean fullscreen   = Settings.fullscreen;
        boolean keepOpen     = Settings.keepOpen;
        boolean showConsole  = Settings.showConsole;
        int    cpuThreads    = Settings.maxCpuThreads;
        boolean debugLogging = Settings.debugLogging;
        String jvmArgs       = Settings.jvmArgs;

        new SwingWorker<String, Void>() {
            @Override protected String doInBackground() {
                return buildInfo(accountsCopy, lastUuid,
                    ramMb, javaPath, fullscreen, keepOpen,
                    showConsole, cpuThreads, debugLogging, jvmArgs);
            }
            @Override protected void done() {
                try { infoArea.setText(get()); infoArea.setCaretPosition(0); }
                catch (Exception ignored) {}
            }
        }.execute();
    }

    private String buildInfo(List<AccountManager.Account> accounts, String lastUuid,
                              int ramMb, String javaPath, boolean fullscreen,
                              boolean keepOpen, boolean showConsole, int cpuThreads,
                              boolean debugLogging, String jvmArgs) {
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
        sb.append("  Configured  : ").append(javaPath.isBlank() ? "(auto)" : javaPath).append("\n");
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
        if (accounts.isEmpty()) {
            sb.append("  No accounts loaded\n");
        } else {
            for (AccountManager.Account acc : accounts) {
                boolean selected = acc.uuid.equals(lastUuid);
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
        sb.append("  RAM           : ").append(ramMb).append(" MB\n");
        sb.append("  Java path     : ").append(javaPath.isBlank() ? "(auto)" : javaPath).append("\n");
        sb.append("  Fullscreen    : ").append(fullscreen).append("\n");
        sb.append("  Keep open     : ").append(keepOpen).append("\n");
        sb.append("  Show console  : ").append(showConsole).append("\n");
        sb.append("  CPU threads   : ").append(cpuThreads == 0 ? "unlimited" : String.valueOf(cpuThreads)).append("\n");
        sb.append("  Debug logging : ").append(debugLogging).append("\n");
        sb.append("  JVM args      : ").append(jvmArgs).append("\n\n");

        // ── Mods ──
        File modsDir = Main.MODS_DIR;
        File[] mods = modsDir.listFiles(f -> f.getName().endsWith(".jar") || f.getName().endsWith(".disabled"));
        sb.append("[ MODS (").append(mods != null ? mods.length : 0).append(") ]\n");
        if (mods != null && mods.length > 0) {
            Arrays.sort(mods, Comparator.comparing(File::getName));
            for (File mod : mods)
                sb.append("  ").append(mod.getName()).append(" (")
                  .append(mod.length() / 1024).append(" KB)")
                  .append(mod.getName().endsWith(".disabled") ? " [disabled]" : "")
                  .append("\n");
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
