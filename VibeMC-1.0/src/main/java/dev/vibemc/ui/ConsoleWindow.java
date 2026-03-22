package dev.vibemc.ui;

import javax.swing.*;
import javax.swing.border.*;
import javax.swing.text.*;
import java.awt.*;
import java.io.*;

public class ConsoleWindow extends JFrame {

    private static final Color BG     = new Color(0x0A0A0A);
    private static final Color ORANGE = new Color(0xFF6A00);
    private static final Color TEXT   = new Color(0xCCCCCC);
    private static final Color DIM    = new Color(0x555555);

    private final JTextArea textArea;
    private final PrintStream printStream;

    public ConsoleWindow() {
        super("VibeMC — Game Console");
        setSize(800, 480);
        setLocationRelativeTo(null);
        getContentPane().setBackground(BG);
        setLayout(new BorderLayout());

        // Header
        JPanel header = new JPanel(new BorderLayout());
        header.setBackground(new Color(0x111111));
        header.setBorder(new EmptyBorder(8, 12, 8, 12));
        JLabel title = new JLabel("GAME CONSOLE OUTPUT");
        title.setForeground(ORANGE);
        title.setFont(new Font("Segoe UI", Font.BOLD, 12));
        header.add(title, BorderLayout.WEST);

        JButton clearBtn = new JButton("Clear");
        clearBtn.setBackground(new Color(0x1F1F1F));
        clearBtn.setForeground(TEXT);
        clearBtn.setBorder(BorderFactory.createCompoundBorder(
            BorderFactory.createLineBorder(new Color(0x2A2A2A)),
            new EmptyBorder(3, 10, 3, 10)
        ));
        clearBtn.setFocusPainted(false);
        clearBtn.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
        header.add(clearBtn, BorderLayout.EAST);
        add(header, BorderLayout.NORTH);

        // Text area
        textArea = new JTextArea();
        clearBtn.addActionListener(e -> textArea.setText(""));
        textArea.setBackground(BG);
        textArea.setForeground(TEXT);
        textArea.setFont(new Font("Monospaced", Font.PLAIN, 11));
        textArea.setEditable(false);
        textArea.setBorder(new EmptyBorder(4, 8, 4, 8));
        textArea.setLineWrap(true);
        textArea.setWrapStyleWord(false);

        JScrollPane scroll = new JScrollPane(textArea);
        scroll.setBorder(BorderFactory.createEmptyBorder());
        scroll.setBackground(BG);
        add(scroll, BorderLayout.CENTER);

        // Create a PrintStream that appends to our textArea
        OutputStream os = new OutputStream() {
            private final StringBuilder sb = new StringBuilder();
            @Override public void write(int b) {
                sb.append((char) b);
                if ((char) b == '\n') flush();
            }
            @Override public void flush() {
                String line = sb.toString();
                sb.setLength(0);
                if (!line.isEmpty()) {
                    SwingUtilities.invokeLater(() -> {
                        textArea.append(line);
                        // Auto-scroll to bottom
                        textArea.setCaretPosition(textArea.getDocument().getLength());
                        // Limit to 10000 lines
                        trimLines(10000);
                    });
                }
            }
        };
        this.printStream = new PrintStream(os, true);
    }

    public PrintStream getPrintStream() {
        return printStream;
    }

    private void trimLines(int maxLines) {
        try {
            Document doc = textArea.getDocument();
            Element root = doc.getDefaultRootElement();
            int excess = root.getElementCount() - maxLines;
            if (excess > 0) {
                int end = root.getElement(excess - 1).getEndOffset();
                doc.remove(0, end);
            }
        } catch (Exception ignored) {}
    }
}
