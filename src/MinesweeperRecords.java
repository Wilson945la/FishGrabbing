import javax.swing.*;
import java.awt.*;
import java.nio.file.*;
import java.util.*;
import java.util.regex.*;

public class MinesweeperRecords {

    private static final String[] MODE_NAMES = {"初级", "中级", "高级", "自定义"};
    private static final String REC_FILE = System.getProperty("user.home") + "/.minesweeper_records";

    private static String formatTime(int seconds) {
        return String.format("%02d:%02d", seconds / 60, seconds % 60);
    }

    private static int parseTimeSeconds(String t) {
        try {
            String[] parts = t.split(":");
            return Integer.parseInt(parts[0]) * 60 + Integer.parseInt(parts[1]);
        } catch (Exception e) {
            try { return Integer.parseInt(t.trim()); } catch (Exception e2) { return Integer.MAX_VALUE; }
        }
    }

    private static Map<String, String> loadRecsLocal() {
        LinkedHashMap<String, String> m = new LinkedHashMap<>();
        try {
            Path p = Paths.get(REC_FILE);
            if (Files.exists(p)) {
                String j = new String(Files.readAllBytes(p), "UTF-8");
                Matcher mr = Pattern.compile("\"([^\"]+)\":\s*\"([^\"]*)\"").matcher(j);
                while (mr.find()) {
                    String mode = mr.group(1);
                    String raw = mr.group(2);
                    try {
                        int sec = Integer.parseInt(raw);
                        m.put(mode, formatTime(sec));
                    } catch (NumberFormatException e) {
                        m.put(mode, raw);
                    }
                }
            }
        } catch (Exception ignored) {}
        return m;
    }

    private static boolean saveRecLocal(String mode, String record) {
        try {
            Map<String, String> m = loadRecsLocal();
            String existing = m.get(mode);
            if (existing != null) {
                try {
                    int oldSec = parseTimeSeconds(existing);
                    int newSec = Integer.parseInt(record);
                    if (newSec >= oldSec) return false;
                } catch (NumberFormatException e) {}
            }
            m.put(mode, record);
            StringBuilder sb = new StringBuilder("{");
            boolean f = true;
            for (Map.Entry<String, String> e : m.entrySet()) {
                if (!f) sb.append(",");
                sb.append("\"").append(e.getKey()).append("\":\"").append(e.getValue()).append("\"");
                f = false;
            }
            sb.append("}");
            Files.write(Paths.get(REC_FILE), sb.toString().getBytes("UTF-8"));
            return true;
        } catch (Exception e) {
            return false;
        }
    }

    public static Map<String, String> loadRecs(int userId) {
        if (userId <= 0) return loadRecsLocal();
        LinkedHashMap<String, String> m = new LinkedHashMap<>();
        try {
            String resp = ServerClient.getRecords(userId);
            if (resp.startsWith("SUCCESS|")) {
                String data = resp.substring("SUCCESS|".length());
                if (!data.isEmpty()) {
                    for (String triple : data.split(",")) {
                        String[] parts = triple.split("\\|");
                        if (parts.length >= 3) {
                            String gameName = parts[0].trim();
                            String mode = parts[1].trim();
                            String record = parts[2].trim();
                            if ("扫雷".equals(gameName)) {
                                int seconds = parseTimeSeconds(record);
                                m.put(mode, formatTime(seconds));
                            }
                        }
                    }
                }
            }
        } catch (Exception ignored) {}
        return m;
    }

    public static boolean saveRec(int userId, String gameName, String gameMode, String record) {
        if (userId <= 0) return saveRecLocal(gameMode, record);
        try {
            Map<String, String> m = loadRecs(userId);
            String existing = m.get(gameMode);
            if (existing != null) {
                try {
                    int oldTime = parseTimeSeconds(existing);
                    int newTime = Integer.parseInt(record);
                    if (newTime >= oldTime) return false;
                } catch (NumberFormatException e) {}
            }
            String resp = ServerClient.saveRecord(userId, gameName, gameMode, record);
            return resp.startsWith("SUCCESS");
        } catch (Exception e) {
            return false;
        }
    }

    public static void showRecords(JFrame parent, int userId) {
        JDialog d = new JDialog(parent, "扫雷纪录", true);
        d.setResizable(false);
        d.setLayout(new BorderLayout());

        JPanel c = new JPanel(new GridBagLayout());
        c.setBackground(new Color(60, 63, 65));
        c.setBorder(BorderFactory.createEmptyBorder(20, 30, 20, 30));

        GridBagConstraints g = new GridBagConstraints();
        g.fill = GridBagConstraints.HORIZONTAL;
        g.insets = new Insets(8, 0, 8, 0);

        Map<String, String> recs = loadRecs(userId);

        for (int i = 0; i < MODE_NAMES.length; i++) {
            g.gridx = 0; g.gridy = i; g.weightx = 1; g.anchor = GridBagConstraints.WEST;
            JLabel dl = new JLabel(MODE_NAMES[i]);
            dl.setFont(new Font("Microsoft YaHei", Font.PLAIN, 16));
            dl.setForeground(Color.WHITE);
            c.add(dl, g);

            g.gridx = 1; g.weightx = 0; g.anchor = GridBagConstraints.EAST;
            String v = recs.getOrDefault(MODE_NAMES[i], null);
            JLabel sl = new JLabel(v == null ? "未通关" : v);
            sl.setFont(new Font("Microsoft YaHei", Font.BOLD, 16));
            sl.setForeground(v == null ? Color.GRAY : new Color(100, 255, 100));
            c.add(sl, g);
        }

        d.add(c, BorderLayout.CENTER);

        JButton cb = new JButton("关闭");
        cb.setFont(new Font("Microsoft YaHei", Font.BOLD, 13));
        cb.setForeground(Color.WHITE);
        cb.setBackground(new Color(80, 83, 86));
        cb.setFocusPainted(false);
        cb.setBorderPainted(false);
        cb.setContentAreaFilled(false);
        cb.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
        cb.setPreferredSize(new Dimension(80, 32));
        cb.addActionListener(e -> d.dispose());

        JPanel bp = new JPanel(new FlowLayout());
        bp.setBackground(new Color(60, 63, 65));
        bp.add(cb);
        d.add(bp, BorderLayout.SOUTH);

        d.pack();
        d.setLocationRelativeTo(parent);
        d.setVisible(true);
    }
}
