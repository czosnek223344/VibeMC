package dev.vibemc;

import com.formdev.flatlaf.FlatDarkLaf;
import dev.vibemc.config.Settings;
import dev.vibemc.ui.LauncherFrame;

import javax.swing.*;
import java.awt.*;
import java.io.File;

public class Main {

    public static File BASE_DIR;
    public static File MINECRAFT_DIR;
    public static File MODS_DIR;
    public static File VERSIONS_DIR;
    public static File LIBRARIES_DIR;
    public static File ASSETS_DIR;
    public static File ACCOUNTS_FILE;
    public static File SETTINGS_FILE;

    public static final String GAME_VERSION  = "1.21.4";
    public static final String LAUNCHER_NAME = "VibeMC";

    public static void main(String[] args) {
        setupDirs();
        Settings.load();
        applyTheme();
        SwingUtilities.invokeLater(() -> new LauncherFrame().setVisible(true));
    }

    private static void setupDirs() {
        // Cross-platform base dir
        String os = System.getProperty("os.name", "").toLowerCase();
        File base;
        if (os.contains("win")) {
            String local = System.getenv("LOCALAPPDATA");
            if (local == null) local = System.getProperty("user.home");
            base = new File(local, "vibemc" + File.separator + "files");
        } else if (os.contains("mac")) {
            base = new File(System.getProperty("user.home"),
                "Library/Application Support/vibemc/files");
        } else {
            // Linux / other
            base = new File(System.getProperty("user.home"), ".vibemc/files");
        }

        BASE_DIR      = base;
        MINECRAFT_DIR = new File(BASE_DIR, "minecraft");
        MODS_DIR      = new File(MINECRAFT_DIR, "mods");
        VERSIONS_DIR  = new File(MINECRAFT_DIR, "versions");
        LIBRARIES_DIR = new File(MINECRAFT_DIR, "libraries");
        ASSETS_DIR    = new File(MINECRAFT_DIR, "assets");
        ACCOUNTS_FILE = new File(BASE_DIR, "accounts.json");
        SETTINGS_FILE = new File(BASE_DIR, "settings.json");

        for (File d : new File[]{BASE_DIR,MINECRAFT_DIR,MODS_DIR,VERSIONS_DIR,LIBRARIES_DIR,ASSETS_DIR})
            d.mkdirs();
    }

    private static void applyTheme() {
        try { FlatDarkLaf.setup(); }
        catch (Exception e) {
            try { UIManager.setLookAndFeel(UIManager.getSystemLookAndFeelClassName()); }
            catch (Exception ignored) {}
        }
        Color bg     = new Color(0x0D0D0D);
        Color panel  = new Color(0x161616);
        Color panel2 = new Color(0x1F1F1F);
        Color orange = new Color(0xFF6A00);
        Color text   = new Color(0xD4D4D4);
        Color dim    = new Color(0x888888);
        Color border = new Color(0x2A2A2A);
        Color input  = new Color(0x111111);

        UIManager.put("Panel.background",                panel);
        UIManager.put("Frame.background",                bg);
        UIManager.put("RootPane.background",             bg);
        UIManager.put("ContentPane.background",          bg);
        UIManager.put("Button.background",               panel2);
        UIManager.put("Button.foreground",               text);
        UIManager.put("Button.hoverBackground",          new Color(0x2A2A2A));
        UIManager.put("Button.pressedBackground",        new Color(0xFF6A00));
        UIManager.put("Button.focusedBorderColor",       orange);
        UIManager.put("Button.arc",                      6);
        UIManager.put("Label.foreground",                text);
        UIManager.put("Label.disabledForeground",        dim);
        UIManager.put("TextField.background",            input);
        UIManager.put("TextField.foreground",            text);
        UIManager.put("TextField.caretForeground",       orange);
        UIManager.put("TextField.border",                BorderFactory.createLineBorder(border));
        UIManager.put("TextArea.background",             input);
        UIManager.put("TextArea.foreground",             text);
        UIManager.put("TextArea.caretForeground",        orange);
        UIManager.put("List.background",                 input);
        UIManager.put("List.foreground",                 text);
        UIManager.put("List.selectionBackground",        new Color(0x2A1500));
        UIManager.put("List.selectionForeground",        orange);
        UIManager.put("ScrollPane.background",           input);
        UIManager.put("ScrollBar.background",            panel);
        UIManager.put("ScrollBar.thumb",                 new Color(0x333333));
        UIManager.put("ScrollBar.thumbHover",            new Color(0x444444));
        UIManager.put("ScrollBar.width",                 8);
        UIManager.put("TabbedPane.background",           bg);
        UIManager.put("TabbedPane.foreground",           dim);
        UIManager.put("TabbedPane.selected",             panel2);
        UIManager.put("TabbedPane.selectedForeground",   orange);
        UIManager.put("TabbedPane.underlineColor",       orange);
        UIManager.put("TabbedPane.inactiveUnderlineColor", border);
        UIManager.put("Slider.background",               panel);
        UIManager.put("Slider.foreground",               orange);
        UIManager.put("Slider.thumbColor",               orange);
        UIManager.put("Slider.trackColor",               new Color(0x333333));
        UIManager.put("ProgressBar.background",          new Color(0x1A1A1A));
        UIManager.put("ProgressBar.foreground",          orange);
        UIManager.put("OptionPane.background",           panel);
        UIManager.put("OptionPane.messageForeground",    text);
        UIManager.put("PopupMenu.background",            panel2);
        UIManager.put("MenuItem.background",             panel2);
        UIManager.put("MenuItem.foreground",             text);
        UIManager.put("MenuItem.selectionBackground",    new Color(0x2A1500));
        UIManager.put("MenuItem.selectionForeground",    orange);
        UIManager.put("Separator.foreground",            border);
        UIManager.put("ComboBox.background",             input);
        UIManager.put("ComboBox.foreground",             text);
        UIManager.put("ComboBox.selectionBackground",    new Color(0x2A1500));
        UIManager.put("ComboBox.buttonBackground",       panel2);
        UIManager.put("CheckBox.foreground",             text);
        UIManager.put("CheckBox.icon.checkmarkColor",    orange);
        UIManager.put("CheckBox.icon.selectedBackground",orange);
        UIManager.put("ToolTip.background",              panel2);
        UIManager.put("ToolTip.foreground",              text);
        UIManager.put("ToolTip.border",                  BorderFactory.createLineBorder(border));
        UIManager.put("Table.background",                input);
        UIManager.put("Table.foreground",                text);
        UIManager.put("Table.gridColor",                 border);
        UIManager.put("Table.selectionBackground",       new Color(0x2A1500));
        UIManager.put("Table.selectionForeground",       orange);
        UIManager.put("TableHeader.background",          panel2);
        UIManager.put("TableHeader.foreground",          dim);
    }
}
