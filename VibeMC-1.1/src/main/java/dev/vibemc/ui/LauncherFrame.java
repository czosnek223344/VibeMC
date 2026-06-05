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
import java.awt.event.*;
import java.io.*;
import java.util.List;

public class LauncherFrame extends JFrame {

    private static final Color ACCENT = new Color(0xD4845A);
    private static final Color RED    = new Color(0xB85C4C);
    private static final Color BG     = new Color(0x1A1714);
    private static final Color PANEL  = new Color(0x222019);
    private static final Color PANEL2 = new Color(0x2C2926);
    private static final Color TEXT   = new Color(0xEDE8E3);
    private static final Color DIM    = new Color(0x8A8480);
    private static final Color BORDER = new Color(0x38342F);
    private static final Color GREEN  = new Color(0x6DAF6D);
    // keep ORANGE as alias so existing references compile
    private static final Color ORANGE = ACCENT;

    // Account switcher dropdown button
    private JButton accountDropBtn;
    private JLabel  statusLabel;
    private JButton playBtn;

    public LauncherFrame() {
        super(Main.LAUNCHER_NAME + " — Minecraft " + Main.GAME_VERSION + " + Fabric");
        setDefaultCloseOperation(EXIT_ON_CLOSE);
        setSize(Settings.windowWidth, Settings.windowHeight);
        setMinimumSize(new Dimension(860, 540));
        setLocationRelativeTo(null);
        getContentPane().setBackground(BG);
        setLayout(new BorderLayout());

        // Debounce window resize — save at most every 500ms, not every pixel
        javax.swing.Timer resizeTimer = new javax.swing.Timer(500, e2 -> {
            Settings.windowWidth  = getWidth();
            Settings.windowHeight = getHeight();
            Settings.save();
        });
        resizeTimer.setRepeats(false);
        addComponentListener(new ComponentAdapter() {
            @Override public void componentResized(ComponentEvent e) {
                resizeTimer.restart();
            }
        });

        AccountManager.load();
        buildUI();
        rescanTokens(); // async: scans tokens folder, updates account button and checks for Fabric updates
    }

    // ── UI ────────────────────────────────────────────────────────────────────

    private void buildUI() {
        add(buildTopBar(), BorderLayout.NORTH);

        JTabbedPane tabs = new JTabbedPane(JTabbedPane.TOP);
        tabs.setBackground(BG);
        tabs.setBorder(BorderFactory.createMatteBorder(1, 0, 0, 0, BORDER));
        tabs.setFont(new Font("Segoe UI", Font.BOLD, 13));

        tabs.addTab("  MODS  ",    new ModsTab());
        tabs.addTab("  SETTINGS  ", new SettingsTab(this::onSettingsSaved));
        tabs.addTab("  DEBUG  ",   new DebugTab());
        add(tabs, BorderLayout.CENTER);
        add(buildStatusBar(), BorderLayout.SOUTH);
    }

    private JPanel buildTopBar() {
        // Custom gradient background panel
        JPanel bar = new JPanel(new BorderLayout()) {
            @Override protected void paintComponent(java.awt.Graphics g) {
                java.awt.Graphics2D g2 = (java.awt.Graphics2D) g.create();
                g2.setPaint(new java.awt.GradientPaint(
                    0, 0, new Color(0x201D1A),
                    0, getHeight(), new Color(0x1A1714)));
                g2.fillRect(0, 0, getWidth(), getHeight());
                g2.dispose();
            }
        };
        bar.setOpaque(false);
        bar.setBorder(BorderFactory.createCompoundBorder(
            BorderFactory.createMatteBorder(0, 0, 1, 0, BORDER),
            new EmptyBorder(10, 16, 10, 16)));

        // Logo area
        JPanel logoPanel = new JPanel(new FlowLayout(FlowLayout.LEFT, 0, 0));
        logoPanel.setOpaque(false);

        // Small accent square as logo mark
        JLabel mark = new JLabel("■") {
            @Override protected void paintComponent(java.awt.Graphics g) {
                java.awt.Graphics2D g2 = (java.awt.Graphics2D) g.create();
                g2.setRenderingHint(java.awt.RenderingHints.KEY_ANTIALIASING,
                    java.awt.RenderingHints.VALUE_ANTIALIAS_ON);
                g2.setColor(ACCENT);
                g2.fillRoundRect(0, 2, 18, 18, 4, 4);
                g2.dispose();
            }
            @Override public java.awt.Dimension getPreferredSize() {
                return new java.awt.Dimension(22, 22);
            }
        };
        mark.setForeground(ACCENT);

        JLabel logo = new JLabel("  VibeMC");
        logo.setFont(new Font("Segoe UI", Font.BOLD, 18));
        logo.setForeground(TEXT);
        JLabel sub = new JLabel("  " + Main.GAME_VERSION + " · Fabric");
        sub.setFont(new Font("Segoe UI", Font.PLAIN, 12));
        sub.setForeground(DIM);

        logoPanel.add(mark);
        logoPanel.add(logo);
        logoPanel.add(sub);
        bar.add(logoPanel, BorderLayout.WEST);

        // Right: account + play
        JPanel right = new JPanel(new FlowLayout(FlowLayout.RIGHT, 6, 0));
        right.setOpaque(false);

        accountDropBtn = new JButton("No account  ▾");
        styleAccountButton(accountDropBtn, false, false);
        accountDropBtn.addActionListener(e -> showAccountMenu());

        JButton addAccBtn = new JButton("+");
        addAccBtn.setToolTipText("Add account");
        styleSmallButton(addAccBtn);
        addAccBtn.setFont(new Font("Segoe UI", Font.BOLD, 15));
        addAccBtn.setForeground(ACCENT);
        addAccBtn.setPreferredSize(new Dimension(34, 30));
        addAccBtn.addActionListener(e -> showAddAccountDialog());

        right.add(accountDropBtn);
        right.add(addAccBtn);
        right.add(Box.createHorizontalStrut(10));

        playBtn = new JButton("▶  PLAY");
        stylePlayButton(playBtn);
        playBtn.addActionListener(e -> onPlayClicked());
        right.add(playBtn);

        bar.add(right, BorderLayout.EAST);
        return bar;
    }

    private JPanel buildStatusBar() {
        statusLabel = new JLabel("  Ready");
        statusLabel.setForeground(DIM);
        statusLabel.setFont(new Font("Segoe UI", Font.PLAIN, 11));
        statusLabel.setBorder(new EmptyBorder(4, 8, 4, 8));

        JLabel ver = new JLabel(Main.GAME_VERSION + "  •  Fabric  •  " + Main.LAUNCHER_NAME + "  ");
        ver.setForeground(DIM);
        ver.setFont(new Font("Segoe UI", Font.PLAIN, 11));

        JPanel bar = new JPanel(new BorderLayout());
        bar.setBackground(new Color(0x1E1B18));
        bar.setBorder(BorderFactory.createMatteBorder(1, 0, 0, 0, BORDER));
        bar.add(statusLabel, BorderLayout.WEST);
        bar.add(ver, BorderLayout.EAST);
        return bar;
    }

    // ── Account dropdown ──────────────────────────────────────────────────────

    /** Shows popup menu under the account button with all accounts + management options */
    private void showAccountMenu() {
        JPopupMenu menu = new JPopupMenu();
        menu.setBackground(PANEL2);

        List<Account> accounts = AccountManager.getAll();
        if (accounts.isEmpty()) {
            JMenuItem none = new JMenuItem("No accounts — click + to add");
            none.setForeground(DIM);
            none.setEnabled(false);
            menu.add(none);
        } else {
            for (Account acc : accounts) {
                boolean selected = acc.uuid.equals(Settings.lastAccountUuid);
                JMenuItem item = new JMenuItem((selected ? "✓ " : "   ") + acc.username);
                item.setForeground(selected ? ORANGE : TEXT);
                item.setBackground(PANEL2);
                item.addActionListener(e -> {
                    Settings.lastAccountUuid = acc.uuid;
                    Settings.save();
                    updateAccountButton();
                });
                // Right-click on menu item to remove
                menu.add(item);
            }
            menu.addSeparator();
            JMenuItem refresh = new JMenuItem("↻  Refresh from tokens folder");
            refresh.setForeground(TEXT);
            refresh.setBackground(PANEL2);
            refresh.addActionListener(e -> rescanTokens());
            menu.add(refresh);

            JMenuItem remove = new JMenuItem("✕  Remove selected account");
            remove.setForeground(RED);
            remove.setBackground(PANEL2);
            remove.addActionListener(e -> {
                Account sel = AccountManager.getByUuid(Settings.lastAccountUuid);
                if (sel == null) return;
                int c = JOptionPane.showConfirmDialog(this,
                    "Remove '" + sel.username + "'?", "Remove Account",
                    JOptionPane.YES_NO_OPTION, JOptionPane.WARNING_MESSAGE);
                if (c == JOptionPane.YES_OPTION) {
                    AccountManager.remove(sel);
                    Settings.lastAccountUuid = AccountManager.getAll().isEmpty() ? "" :
                        AccountManager.getAll().get(0).uuid;
                    Settings.save();
                    updateAccountButton();
                }
            });
            menu.add(remove);
        }

        menu.show(accountDropBtn, 0, accountDropBtn.getHeight());
    }

    private void updateAccountButton() {
        Account acc = AccountManager.getByUuid(Settings.lastAccountUuid);
        if (acc != null) {
            boolean expired = acc.isExpired();
            accountDropBtn.setText("● " + acc.username + "  ▾");
            styleAccountButton(accountDropBtn, true, expired);
        } else {
            accountDropBtn.setText("No account  ▾");
            styleAccountButton(accountDropBtn, false, false);
        }
    }

    private void styleAccountButton(JButton btn, boolean hasAccount, boolean expired) {
        btn.setBackground(PANEL2);
        btn.setForeground(hasAccount ? (expired ? new Color(0xC89060) : TEXT) : DIM);
        btn.setFont(new Font("Segoe UI", Font.PLAIN, 12));
        btn.setBorder(BorderFactory.createCompoundBorder(
            BorderFactory.createLineBorder(hasAccount
                ? (expired ? new Color(0x6B4020) : new Color(0x4A3D30))
                : BORDER, 1),
            new EmptyBorder(5, 12, 5, 12)));
        btn.setFocusPainted(false);
        btn.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
        // Color the status dot
        if (hasAccount) {
            btn.putClientProperty("dotColor", expired ? new Color(0xC89060) : GREEN);
        }
    }

    // ── Add account dialog ────────────────────────────────────────────────────

    private void showAddAccountDialog() {
        JDialog dlg = new JDialog(this, "Add Account", true);
        dlg.setSize(400, 220);
        dlg.setLocationRelativeTo(this);
        dlg.getContentPane().setBackground(PANEL);
        dlg.setLayout(new BorderLayout());

        // Header
        JPanel header = new JPanel(new BorderLayout());
        header.setBackground(BG);
        header.setBorder(BorderFactory.createCompoundBorder(
            BorderFactory.createMatteBorder(0, 0, 1, 0, BORDER),
            new EmptyBorder(14, 20, 14, 20)));
        JLabel titleLbl = new JLabel("Add Microsoft Account");
        titleLbl.setForeground(TEXT);
        titleLbl.setFont(new Font("Segoe UI", Font.BOLD, 14));
        JLabel subLbl = new JLabel("Sign in to play Minecraft");
        subLbl.setForeground(DIM);
        subLbl.setFont(new Font("Segoe UI", Font.PLAIN, 11));
        JPanel headerText = new JPanel();
        headerText.setOpaque(false);
        headerText.setLayout(new BoxLayout(headerText, BoxLayout.Y_AXIS));
        headerText.add(titleLbl);
        headerText.add(Box.createVerticalStrut(2));
        headerText.add(subLbl);
        header.add(headerText, BorderLayout.WEST);
        dlg.add(header, BorderLayout.NORTH);

        // Buttons
        JButton browserBtn = new JButton("  Sign in with Microsoft  →");
        browserBtn.setBackground(ACCENT);
        browserBtn.setForeground(new Color(0x1A1714));
        browserBtn.setFont(new Font("Segoe UI", Font.BOLD, 13));
        browserBtn.setBorder(BorderFactory.createCompoundBorder(
            BorderFactory.createLineBorder(new Color(0xE09070), 1),
            new EmptyBorder(10, 20, 10, 20)));
        browserBtn.setFocusPainted(false);
        browserBtn.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
        browserBtn.addActionListener(e -> { dlg.dispose(); doDeviceCodeLogin(); });

        JButton tokenBtn = new JButton("  Open tokens folder");
        styleSmallButton(tokenBtn);
        tokenBtn.addActionListener(e -> { dlg.dispose(); openTokensFolder(); });

        JPanel btns = new JPanel(new GridLayout(2, 1, 0, 8));
        btns.setOpaque(false);
        btns.setBorder(new EmptyBorder(20, 28, 20, 28));
        btns.add(browserBtn);
        btns.add(tokenBtn);
        dlg.add(btns, BorderLayout.CENTER);
        dlg.setVisible(true);
    }

    private void openTokensFolder() {
        File dir = MicrosoftAuth.getTokensDir();
        dir.mkdirs();
        try { Desktop.getDesktop().open(dir); }
        catch (Exception e) {
            // Fallback for Linux/headless
            try {
                String os = System.getProperty("os.name","").toLowerCase();
                if (os.contains("linux"))
                    new ProcessBuilder("xdg-open", dir.getAbsolutePath()).start();
                else if (os.contains("mac"))
                    new ProcessBuilder("open", dir.getAbsolutePath()).start();
                else
                    new ProcessBuilder("explorer", dir.getAbsolutePath()).start();
            } catch (Exception ex) {
                setStatus("Tokens folder: " + dir.getAbsolutePath());
            }
        }
    }

    // ── Account management ────────────────────────────────────────────────────

    public void rescanTokens() {
        setStatus("Scanning tokens...");
        new SwingWorker<List<Account>, Void>() {
            @Override protected List<Account> doInBackground() {
                return MicrosoftAuth.scanTokensFolder();
            }
            @Override protected void done() {
                try {
                    List<Account> found = get();
                    // setAll() atomically replaces accounts, deduplicates by UUID, saves once
                    AccountManager.setAll(found);

                    if (!found.isEmpty()) {
                        // Keep previously-selected account if it still exists; otherwise use first
                        boolean exists = found.stream()
                            .anyMatch(a -> a.uuid != null && a.uuid.equals(Settings.lastAccountUuid));
                        if (!exists) Settings.lastAccountUuid = found.get(0).uuid;
                    } else {
                        Settings.lastAccountUuid = "";
                    }
                    Settings.save();
                    updateAccountButton();

                    // Combine account + install state in one status — avoids invokeLater race
                    boolean installed = FabricSetup.isInstalled();
                    String installStr = installed
                        ? "Ready · " + Main.GAME_VERSION + " + Fabric"
                        : "Not installed — click PLAY to install";
                    setStatus(found.isEmpty()
                        ? "No accounts — click + to add  ·  " + installStr
                        : found.size() + " account(s)  ·  " + installStr);

                    // Auto-update Fabric if enabled and already installed
                    if (installed && Settings.autoUpdateFabric) checkFabricUpdate();
                } catch (Exception ex) {
                    setStatus("Scan failed: " + ex.getMessage());
                }
            }
        }.execute();
    }

    private void doDeviceCodeLogin() {
        JDialog dlg = new JDialog(this, "Login with Microsoft", true);
        dlg.setSize(430, 200);
        dlg.setLocationRelativeTo(this);
        dlg.getContentPane().setBackground(PANEL);
        dlg.setLayout(new BorderLayout());

        JLabel infoLabel = new JLabel("<html><center>Requesting code...</center></html>", SwingConstants.CENTER);
        JLabel codeLabel = new JLabel("", SwingConstants.CENTER);
        infoLabel.setForeground(TEXT);
        infoLabel.setFont(new Font("Segoe UI", Font.PLAIN, 12));
        codeLabel.setFont(new Font("Consolas", Font.BOLD, 28));
        codeLabel.setForeground(ORANGE);

        JButton copyBtn   = new JButton("Copy Code");
        JButton cancelBtn = new JButton("Cancel");
        styleSmallButton(copyBtn); styleSmallButton(cancelBtn);
        copyBtn.setEnabled(false);

        JPanel btns = new JPanel(new FlowLayout(FlowLayout.CENTER, 8, 0));
        btns.setOpaque(false);
        btns.add(copyBtn); btns.add(cancelBtn);

        JPanel center = new JPanel();
        center.setOpaque(false);
        center.setLayout(new BoxLayout(center, BoxLayout.Y_AXIS));
        center.setBorder(new EmptyBorder(16, 20, 0, 20));
        infoLabel.setAlignmentX(CENTER_ALIGNMENT);
        codeLabel.setAlignmentX(CENTER_ALIGNMENT);
        btns.setAlignmentX(CENTER_ALIGNMENT);
        center.add(infoLabel);
        center.add(Box.createVerticalStrut(8));
        center.add(codeLabel);
        center.add(Box.createVerticalStrut(12));
        center.add(btns);
        dlg.add(center, BorderLayout.CENTER);

        boolean[] cancelled = {false};
        boolean[] copyWired = {false};

        SwingWorker<Account, String> worker = new SwingWorker<>() {
            @Override protected Account doInBackground() throws Exception {
                publish("Requesting device code...");
                MicrosoftAuth.DeviceCodeInfo info = MicrosoftAuth.requestDeviceCode();
                publish("CODE:" + info.userCode + "|" + info.verificationUrl);
                return MicrosoftAuth.pollDeviceCode(info, this::publish);
            }
            @Override protected void process(java.util.List<String> chunks) {
                String msg = chunks.get(chunks.size() - 1);
                if (msg.startsWith("CODE:")) {
                    String[] parts = msg.substring(5).split("\\|");
                    codeLabel.setText(parts[0]);
                    infoLabel.setText("<html><center>Go to <b>" + parts[1]
                        + "</b><br>and enter this code:</center></html>");
                    copyBtn.setEnabled(true);
                    // Wire copy listener exactly once — process() can fire multiple times
                    if (!copyWired[0]) {
                        copyWired[0] = true;
                        copyBtn.addActionListener(ev -> {
                            var sel = new java.awt.datatransfer.StringSelection(parts[0]);
                            Toolkit.getDefaultToolkit().getSystemClipboard().setContents(sel, sel);
                            copyBtn.setText("Copied!");
                            try { Desktop.getDesktop().browse(new java.net.URI(parts[1])); }
                            catch (Exception ignored) {}
                        });
                    }
                } else {
                    infoLabel.setText("<html><center>" + msg + "</center></html>");
                }
            }
            @Override protected void done() {
                if (cancelled[0]) return;
                try {
                    Account acc = get();
                    dlg.dispose();
                    AccountManager.add(acc);
                    Settings.lastAccountUuid = acc.uuid;
                    Settings.save();
                    updateAccountButton();
                    setStatus("Logged in as " + acc.username);
                } catch (Exception e) {
                    if (!cancelled[0]) // don't show error if user cancelled
                        infoLabel.setText("<html><center>Failed: " + String.valueOf(e) + "</center></html>");
                    cancelBtn.setText("Close");
                }
            }
        };
        // Wire cancel AFTER worker is declared so we can interrupt it
        cancelBtn.addActionListener(e -> { cancelled[0] = true; worker.cancel(true); dlg.dispose(); });
        worker.execute();

        dlg.setVisible(true);
    }

    private void checkFabricUpdate() {
        new SwingWorker<Boolean, Void>() {
            String latestVersion;
            @Override protected Boolean doInBackground() {
                try {
                    latestVersion = FabricSetup.getLatestFabricLoaderPublic();
                    String installed = FabricSetup.getInstalledFabricVersion();
                    return installed != null && !installed.equals(latestVersion);
                } catch (Exception e) { return false; }
            }
            @Override protected void done() {
                try {
                    if (get()) {
                        setStatus("Fabric update available (" + latestVersion + ") — click PLAY to update");
                    }
                } catch (Exception ignored) {}
            }
        }.execute();
    }

    // ── Play ──────────────────────────────────────────────────────────────────

    private void onPlayClicked() {
        if (AccountManager.getAll().isEmpty()) {
            showAddAccountDialog();
            return;
        }

        Account selected = AccountManager.getByUuid(Settings.lastAccountUuid);
        if (selected == null) selected = AccountManager.getAll().get(0);
        final Account account = selected;

        if (!FabricSetup.isInstalled()) {
            setStatus("Installing Minecraft + Fabric...");
            playBtn.setEnabled(false);
            runInstall(() -> doLaunch(account));
            return;
        }

        // Re-install Fabric if autoUpdate is on and a newer version exists
        if (Settings.autoUpdateFabric) {
            playBtn.setEnabled(false);
            setStatus("Checking for Fabric updates...");
            new SwingWorker<Boolean, Void>() {
                @Override protected Boolean doInBackground() {
                    try {
                        String latest    = FabricSetup.getLatestFabricLoaderPublic();
                        String installed = FabricSetup.getInstalledFabricVersion();
                        return installed != null && !installed.equals(latest);
                    } catch (Exception e) { return false; }
                }
                @Override protected void done() {
                    try {
                        if (get()) {
                            setStatus("Updating Fabric...");
                            runInstall(() -> doLaunch(account));
                        } else {
                            playBtn.setEnabled(true);
                            doLaunch(account);
                        }
                    } catch (Exception e) {
                        playBtn.setEnabled(true);
                        doLaunch(account);
                    }
                }
            }.execute();
            return;
        }

        doLaunch(account);
    }

    private void doLaunch(Account account) {
        // If expired and has refresh token — refresh first
        if (account.isExpired() && account.hasRefreshToken()) {
            setStatus("Refreshing token for " + account.username + "...");
            playBtn.setEnabled(false);
            final Account acc = account;
            new SwingWorker<Account, Void>() {
                @Override protected Account doInBackground() throws Exception {
                    return MicrosoftAuth.refresh(acc);
                }
                @Override protected void done() {
                    try {
                        Account fresh = get();
                        AccountManager.add(fresh);
                        Settings.lastAccountUuid = fresh.uuid;
                        Settings.save();
                        updateAccountButton();
                        launchProcess(fresh);
                    } catch (Exception e) {
                        playBtn.setEnabled(true);
                        showError("Token refresh failed:\n" + String.valueOf(e)
                            + "\n\nTry importing fresh tokens.");
                        setStatus("Refresh failed.");
                    }
                }
            }.execute();
            return;
        }

        // If expired and no refresh token — warn but let user try (token might still work)
        if (account.isExpired()) {
            int choice = JOptionPane.showConfirmDialog(this,
                "Account token appears expired and no refresh token is available.\n" +
                "This will likely cause 'Invalid session' errors.\n\n" +
                "Import fresh tokens to fix this. Launch anyway?",
                "Token Expired", JOptionPane.YES_NO_OPTION, JOptionPane.WARNING_MESSAGE);
            if (choice != JOptionPane.YES_OPTION) return;
        }

        launchProcess(account);
    }

    private void launchProcess(Account account) {
        setStatus("Launching " + account.username + "...");
        playBtn.setEnabled(false);

        ConsoleWindow console = null;
        if (Settings.showConsole) {
            console = new ConsoleWindow();
            console.setVisible(true);
        }
        final ConsoleWindow finalConsole = console;

        new SwingWorker<Process, Void>() {
            @Override protected Process doInBackground() throws Exception {
                PrintStream ps = finalConsole != null ? finalConsole.getPrintStream() : null;
                return GameLauncher.launch(account, ps);
            }
            @Override protected void done() {
                try {
                    Process p = get();
                    setStatus("Running — " + account.username);
                    if (!Settings.keepOpen) {
                        setVisible(false);
                        Thread waiter = new Thread(() -> {
                            try { p.waitFor(); }
                            catch (InterruptedException ignored) {}
                            SwingUtilities.invokeLater(() -> {
                                setVisible(true);
                                playBtn.setEnabled(true);
                                setStatus("Game closed.");
                            });
                        }, "MC-Wait");
                        waiter.setDaemon(true);
                        waiter.start();
                    } else {
                        playBtn.setEnabled(true);
                    }
                } catch (Exception e) {
                    showError("Launch failed:\n" + String.valueOf(e));
                    playBtn.setEnabled(true);
                    setStatus("Launch failed.");
                }
            }
        }.execute();
    }

    // ── Installation ──────────────────────────────────────────────────────────

    private void runInstall(Runnable onComplete) {
        JDialog dlg = new JDialog(this, "Installing...", false);
        dlg.setSize(420, 120);
        dlg.setLocationRelativeTo(this);
        dlg.getContentPane().setBackground(PANEL);

        JLabel lbl = new JLabel("  Preparing...");
        lbl.setForeground(TEXT);
        lbl.setFont(new Font("Segoe UI", Font.PLAIN, 12));
        JProgressBar bar = new JProgressBar(0, 100);
        bar.setStringPainted(true);
        bar.setForeground(ORANGE);
        bar.setBackground(new Color(0x1E1C18));

        JPanel p = new JPanel(new BorderLayout(0, 6));
        p.setOpaque(false);
        p.setBorder(new EmptyBorder(16, 16, 16, 16));
        p.add(lbl, BorderLayout.NORTH);
        p.add(bar, BorderLayout.CENTER);
        dlg.add(p);
        dlg.setVisible(true);

        new SwingWorker<File, Object[]>() {
            @Override protected File doInBackground() throws Exception {
                return FabricSetup.setup((task, done, total) ->
                    publish(new Object[]{task, done, total}));
            }
            @Override protected void process(List<Object[]> chunks) {
                Object[] c = chunks.get(chunks.size() - 1);
                lbl.setText("  " + c[0]);
                bar.setValue((int) c[1]);
                bar.setString(c[1] + "%");
            }
            @Override protected void done() {
                dlg.dispose();
                try {
                    get();
                    setStatus("Installation complete.");
                    playBtn.setEnabled(true);
                    if (onComplete != null) onComplete.run();
                } catch (Exception e) {
                    showError("Install failed:\n" + String.valueOf(e));
                    playBtn.setEnabled(true);
                    setStatus("Install failed.");
                }
            }
        }.execute();
    }

    // ── Helpers ───────────────────────────────────────────────────────────────

    public void setStatus(String msg) {
        SwingUtilities.invokeLater(() -> {
            if (statusLabel != null) statusLabel.setText("  " + msg);
        });
    }

    private void onSettingsSaved() { setStatus("Settings saved."); }

    private void showError(String msg) {
        JOptionPane.showMessageDialog(this, msg, "Error", JOptionPane.ERROR_MESSAGE);
    }

    private void styleSmallButton(JButton btn) {
        btn.setBackground(PANEL2);
        btn.setForeground(TEXT);
        btn.setBorder(BorderFactory.createCompoundBorder(
            BorderFactory.createLineBorder(BORDER, 1),
            new EmptyBorder(5, 12, 5, 12)));
        btn.setFocusPainted(false);
        btn.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
        btn.setFont(new Font("Segoe UI", Font.PLAIN, 12));
    }

    private void stylePlayButton(JButton btn) {
        btn.setBackground(ACCENT);
        btn.setForeground(new Color(0x1A1714));
        btn.setFont(new Font("Segoe UI", Font.BOLD, 13));
        btn.setBorder(BorderFactory.createCompoundBorder(
            BorderFactory.createLineBorder(new Color(0xE09070), 1),
            new EmptyBorder(7, 30, 7, 30)));
        btn.setFocusPainted(false);
        btn.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
        btn.addMouseListener(new MouseAdapter() {
            @Override public void mouseEntered(MouseEvent e) {
                if (btn.isEnabled()) btn.setBackground(new Color(0xE09070));
            }
            @Override public void mouseExited(MouseEvent e) {
                if (btn.isEnabled()) btn.setBackground(ACCENT);
            }
        });
    }
}
