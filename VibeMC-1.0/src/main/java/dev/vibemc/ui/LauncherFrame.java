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

    private static final Color ORANGE = new Color(0xFF6A00);
    private static final Color RED    = new Color(0xCC2200);
    private static final Color BG     = new Color(0x0D0D0D);
    private static final Color PANEL  = new Color(0x161616);
    private static final Color PANEL2 = new Color(0x1F1F1F);
    private static final Color TEXT   = new Color(0xD4D4D4);
    private static final Color DIM    = new Color(0x666666);
    private static final Color BORDER = new Color(0x2A2A2A);
    private static final Color GREEN  = new Color(0x66BB6A);

    // Account switcher dropdown button
    private JButton accountDropBtn;
    private JLabel  statusLabel;
    private JButton playBtn;
    private JTabbedPane tabs;
    private SettingsTab settingsTab;

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
        rescanTokens(); // async, calls checkInstallation when done
    }

    // ── UI ────────────────────────────────────────────────────────────────────

    private void buildUI() {
        add(buildTopBar(), BorderLayout.NORTH);

        tabs = new JTabbedPane(JTabbedPane.TOP);
        tabs.setBackground(BG);
        tabs.setBorder(BorderFactory.createMatteBorder(1, 0, 0, 0, BORDER));
        tabs.setFont(new Font("Segoe UI", Font.BOLD, 13));

        settingsTab = new SettingsTab(this::onSettingsSaved);
        tabs.addTab("  MODS  ",    new ModsTab());
        tabs.addTab("  SETTINGS  ", settingsTab);
        tabs.addTab("  DEBUG  ",   new DebugTab());
        add(tabs, BorderLayout.CENTER);
        add(buildStatusBar(), BorderLayout.SOUTH);
    }

    private JPanel buildTopBar() {
        JPanel bar = new JPanel(new BorderLayout());
        bar.setBackground(new Color(0x111111));
        bar.setBorder(BorderFactory.createCompoundBorder(
            BorderFactory.createMatteBorder(0, 0, 1, 0, BORDER),
            new EmptyBorder(10, 14, 10, 14)));

        // Logo
        JPanel logoPanel = new JPanel(new FlowLayout(FlowLayout.LEFT, 0, 0));
        logoPanel.setOpaque(false);
        JLabel logo = new JLabel("VIBE CODE MC");
        logo.setFont(new Font("Segoe UI", Font.BOLD, 20));
        logo.setForeground(ORANGE);
        JLabel sub  = new JLabel("  " + Main.GAME_VERSION + " + Fabric");
        sub.setFont(new Font("Segoe UI", Font.PLAIN, 12));
        sub.setForeground(DIM);
        logoPanel.add(logo);
        logoPanel.add(sub);
        bar.add(logoPanel, BorderLayout.WEST);

        // Right: account switcher + play
        JPanel right = new JPanel(new FlowLayout(FlowLayout.RIGHT, 8, 0));
        right.setOpaque(false);

        // Account dropdown button — shows active account name
        accountDropBtn = new JButton("No account  ↓");
        styleSmallButton(accountDropBtn);
        accountDropBtn.setFont(new Font("Segoe UI", Font.PLAIN, 12));
        accountDropBtn.addActionListener(e -> showAccountMenu());

        // Add account "+" button
        JButton addAccBtn = buildSmallBtn("+", "Add account");
        addAccBtn.setFont(new Font("Segoe UI", Font.BOLD, 16));
        addAccBtn.setForeground(ORANGE);
        addAccBtn.addActionListener(e -> showAddAccountDialog());

        right.add(accountDropBtn);
        right.add(addAccBtn);
        right.add(Box.createHorizontalStrut(12));

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
        bar.setBackground(new Color(0x111111));
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
            accountDropBtn.setText(acc.username + "  ↓");
            accountDropBtn.setForeground(acc.isExpired() ? new Color(0xFF9800) : GREEN);
        } else {
            accountDropBtn.setText("No account  ↓");
            accountDropBtn.setForeground(DIM);
        }
    }

    // ── Add account dialog ────────────────────────────────────────────────────

    private void showAddAccountDialog() {
        JDialog dlg = new JDialog(this, "Add Account", true);
        dlg.setSize(420, 290);
        dlg.setLocationRelativeTo(this);
        dlg.getContentPane().setBackground(PANEL);
        dlg.setLayout(new BorderLayout());

        JLabel title = new JLabel("How do you want to add an account?", SwingConstants.CENTER);
        title.setForeground(TEXT);
        title.setFont(new Font("Segoe UI", Font.PLAIN, 13));
        title.setBorder(new EmptyBorder(20, 20, 12, 20));
        dlg.add(title, BorderLayout.NORTH);

        // Option 1: Minecraft Launcher
        boolean launcherFound = MicrosoftAuth.officialLauncherFound();
        JButton launcherBtn = new JButton("📥  Import from Minecraft Launcher"
            + (launcherFound ? "" : "  (not found)"));
        styleSmallButton(launcherBtn);
        launcherBtn.setEnabled(launcherFound);
        launcherBtn.addActionListener(e -> {
            dlg.dispose();
            doImportFromLauncher();
        });

        // Option 2: Device code (Prism's method — open browser, paste code)
        JButton deviceBtn = new JButton("🌐  Login with Microsoft (browser)");
        styleSmallButton(deviceBtn);
        deviceBtn.setForeground(ORANGE);
        deviceBtn.addActionListener(e -> {
            dlg.dispose();
            doDeviceCodeLogin();
        });

        // Option 3: Token folder
        JButton tokenBtn = new JButton("📁  Open tokens folder (BrewBot / manual)");
        styleSmallButton(tokenBtn);
        tokenBtn.addActionListener(e -> {
            dlg.dispose();
            openTokensFolder();
            JOptionPane.showMessageDialog(this,
                "Drop your player folder into the tokens folder,\n" +
                "then click the account button → ↻ Refresh.",
                "Tokens Folder", JOptionPane.INFORMATION_MESSAGE);
        });

        // Option 4: Rescan
        JButton rescanBtn = new JButton("↻  Rescan tokens folder");
        styleSmallButton(rescanBtn);
        rescanBtn.addActionListener(e -> { dlg.dispose(); rescanTokens(); });

        JPanel btns = new JPanel(new GridLayout(4, 1, 0, 8));
        btns.setOpaque(false);
        btns.setBorder(new EmptyBorder(0, 30, 20, 30));
        btns.add(deviceBtn);
        btns.add(launcherBtn);
        btns.add(tokenBtn);
        btns.add(rescanBtn);
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
                    AccountManager.clear();
                    AccountManager.getAll().addAll(found);
                    AccountManager.save();
                    if (!found.isEmpty()) {
                        boolean exists = found.stream()
                            .anyMatch(a -> a.uuid.equals(Settings.lastAccountUuid));
                        if (!exists) Settings.lastAccountUuid = found.get(0).uuid;
                    } else {
                        Settings.lastAccountUuid = "";
                    }
                    Settings.save();
                    updateAccountButton();
                    checkInstallation(); // set final status after scan
                    setStatus(found.isEmpty()
                        ? "No accounts found — click + to add"
                        : "Loaded " + found.size() + " account(s)");
                } catch (Exception ex) {
                    setStatus("Scan failed: " + String.valueOf(ex));
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
        cancelBtn.addActionListener(e -> { cancelled[0] = true; dlg.dispose(); });

        new SwingWorker<Account, String>() {
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
                    copyBtn.addActionListener(ev -> {
                        var sel = new java.awt.datatransfer.StringSelection(parts[0]);
                        Toolkit.getDefaultToolkit().getSystemClipboard().setContents(sel, sel);
                        copyBtn.setText("Copied!");
                        try { Desktop.getDesktop().browse(new java.net.URI(parts[1])); }
                        catch (Exception ignored) {}
                    });
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
                    infoLabel.setText("<html><center>Failed: " + String.valueOf(e) + "</center></html>");
                    cancelBtn.setText("Close");
                }
            }
        }.execute();

        dlg.setVisible(true);
    }

    private void doImportFromLauncher() {
        setStatus("Importing from Minecraft Launcher...");
        new SwingWorker<List<Account>, Void>() {
            @Override protected List<Account> doInBackground() throws Exception {
                return MicrosoftAuth.importFromOfficialLauncher();
            }
            @Override protected void done() {
                try {
                    List<Account> list = get();
                    AccountManager.clear();
                    AccountManager.getAll().addAll(list); // batch, no N saves
                    AccountManager.save();
                    if (!list.isEmpty()) Settings.lastAccountUuid = list.get(0).uuid;
                    Settings.save();
                    updateAccountButton();
                    setStatus("Imported " + list.size() + " account(s) from Minecraft Launcher!");
                } catch (Exception ex) {
                    showError("Import failed: " + String.valueOf(ex));
                    setStatus("Import failed.");
                }
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
        bar.setBackground(new Color(0x1A1A1A));

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

    private void checkInstallation() {
        if (!FabricSetup.isInstalled())
            setStatus("Not installed — click PLAY to install Minecraft + Fabric");
        else
            setStatus("Ready — " + Main.GAME_VERSION + " + Fabric");
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

    private JButton buildSmallBtn(String text, String tooltip) {
        JButton btn = new JButton(text);
        styleSmallButton(btn);
        btn.setToolTipText(tooltip);
        return btn;
    }

    private void styleSmallButton(JButton btn) {
        btn.setBackground(PANEL2);
        btn.setForeground(TEXT);
        btn.setBorder(BorderFactory.createCompoundBorder(
            BorderFactory.createLineBorder(BORDER),
            new EmptyBorder(5, 12, 5, 12)));
        btn.setFocusPainted(false);
        btn.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
        btn.setFont(new Font("Segoe UI", Font.PLAIN, 12));
    }

    private void stylePlayButton(JButton btn) {
        btn.setBackground(ORANGE);
        btn.setForeground(Color.BLACK);
        btn.setFont(new Font("Segoe UI", Font.BOLD, 14));
        btn.setBorder(BorderFactory.createCompoundBorder(
            BorderFactory.createLineBorder(new Color(0xFF8C00)),
            new EmptyBorder(7, 28, 7, 28)));
        btn.setFocusPainted(false);
        btn.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
        btn.addMouseListener(new MouseAdapter() {
            @Override public void mouseEntered(MouseEvent e) {
                if (btn.isEnabled()) btn.setBackground(new Color(0xFF8C00));
            }
            @Override public void mouseExited(MouseEvent e) {
                if (btn.isEnabled()) btn.setBackground(ORANGE);
            }
        });
    }
}
