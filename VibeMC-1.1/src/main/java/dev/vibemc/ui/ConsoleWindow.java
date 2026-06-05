package dev.vibemc.ui;

import javax.swing.*;
import javax.swing.border.*;
import javax.swing.text.*;
import java.awt.*;
import java.io.*;

public class ConsoleWindow extends JFrame {

    private static final Color BG     = new Color(0x130F0D);
    private static final Color ORANGE = new Color(0xD4845A);
    private static final Color TEXT   = new Color(0xEDE8E3);
    private static final Color DIM    = new Color(0x8A8480);

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
        header.setBackground(new Color(0x1A1714));
        header.setBorder(new EmptyBorder(8, 12, 8, 12));
        JLabel title = new JLabel("GAME CONSOLE OUTPUT");
        title.setForeground(ORANGE);
        title.setFont(new Font("Segoe UI", Font.BOLD, 12));
        header.add(title, BorderLayout.WEST);

        JButton clearBtn = new JButton("Clear");
        clearBtn.setBackground(new Color(0x2C2926));
        clearBtn.setForeground(TEXT);
        clearBtn.setBorder(BorderFactory.createCompoundBorder(
            BorderFactory.createLineBorder(new Color(0x38342F)),
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
            private final StringBuilder sb = new StringBuilder(256);

            @Override public void write(int b) {
                // Delegate single-byte writes to the UTF-8-aware bulk method.
                // Calling (char)(b & 0xFF) directly would corrupt multi-byte sequences.
                write(new byte[]{(byte) b}, 0, 1);
            }

            // Critical override — without this, PrintStream calls write(int) per byte
            // causing one invokeLater per character when MC floods output → UI freeze
            @Override public void write(byte[] buf, int off, int len) {
                String s = new String(buf, off, len, java.nio.charset.StandardCharsets.UTF_8);
                sb.append(s);
                if (s.contains("\n")) flush();
            }

            @Override public void flush() {
                if (sb.isEmpty()) return;
                String line = sb.toString();
                sb.setLength(0);
                SwingUtilities.invokeLater(() -> {
                    textArea.append(line);
                    textArea.setCaretPosition(textArea.getDocument().getLength());
                    trimLines(10000);
                });
            }

            @Override public void close() {
                flush(); // ensure any buffered partial line is flushed before stream closes
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
