import javax.swing.*;
import java.awt.*;
import java.awt.event.*;
import java.nio.file.*;
import java.util.*;
import java.util.regex.*;

/**
 * 2048 个人纪录管理
 * 登录时从服务器读取，离线时用本地文件
 */
public class Game2048Records {

    private static final String[] MODE_NAMES = {"经典模式"};
    private static final String REC_FILE = System.getProperty("user.home") + "/.game2048_records";

    /** 本地文件加载纪录 */
    private static Map<String, String> loadRecsLocal() {
        LinkedHashMap<String, String> m = new LinkedHashMap<>();
        try {
            Path p = Paths.get(REC_FILE);
            if (Files.exists(p)) {
                String j = new String(Files.readAllBytes(p), "UTF-8");
                Matcher mr = Pattern.compile("\"([^\"]+)\":\s*\"([^\"]*)\"").matcher(j);
                while (mr.find()) m.put(mr.group(1), mr.group(2));
            }
        } catch (Exception ignored) {}
        return m;
    }

    /** 本地文件保存纪录（仅当新分数更高时） */
    private static boolean saveRecLocal(String gameMode, String record) {
        try {
            Map<String, String> m = loadRecsLocal();
            String existing = m.get(gameMode);
            if (existing != null) {
                try {
                    int oldScore = Integer.parseInt(existing);
                    int newScore = Integer.parseInt(record);
                    if (newScore <= oldScore) return false;
                } catch (NumberFormatException e) {}
            }
            m.put(gameMode, record);
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

    /** 加载所有记录 */
    public static Map<String, String> loadRecs(int userId) {
        if (userId <= 0) return loadRecsLocal();
        LinkedHashMap<String, String> m = new LinkedHashMap<>();
        try {
            String resp = ServerClient.getRecords(userId, "2048");
            if (resp.startsWith("SUCCESS|")) {
                String data = resp.substring("SUCCESS|".length());
                if (!data.isEmpty()) {
                    for (String triple : data.split(",")) {
                        String[] parts = triple.split("\\|");
                        if (parts.length >= 3) {
                            String mode = parts[1].trim();
                            String record = parts[2].trim();
                            String key = "经典".equals(mode) ? "经典模式" : mode;
                            m.put(key, record);
                        }
                    }
                }
            }
        } catch (Exception ignored) {}
        return m;
    }

    /** 加载最高分 */
    public static int loadBestScore(int userId) {
        Map<String, String> recs = loadRecs(userId);
        String v = recs.get("经典模式");
        if (v != null) {
            try { return Integer.parseInt(v); } catch (Exception e) {}
        }
        return 0;
    }

    /** 保存记录（仅当新分数更高时） */
    public static boolean saveRec(int userId, String gameMode, String record) {
        if (userId <= 0) return saveRecLocal(gameMode, record);
        try {
            Map<String, String> m = loadRecs(userId);
            String existing = m.get(gameMode);
            if (existing != null) {
                try {
                    int oldScore = Integer.parseInt(existing);
                    int newScore = Integer.parseInt(record);
                    if (newScore <= oldScore) return false;
                } catch (NumberFormatException e) {}
            }
            String dbMode = "经典模式".equals(gameMode) ? "经典" : gameMode;
            String resp = ServerClient.saveRecord(userId, "2048", dbMode, record);
            return resp.startsWith("SUCCESS");
        } catch (Exception e) {
            return false;
        }
    }

    /** 显示个人纪录对话框 */
    public static void showRecords(JFrame parent, int userId) {
        JDialog d = new JDialog(parent, "个人纪录", true);
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
            g.gridx = 0;
            g.gridy = i;
            g.weightx = 1;
            g.anchor = GridBagConstraints.WEST;

            JLabel dl = new JLabel(MODE_NAMES[i]);
            dl.setFont(new Font("Microsoft YaHei", Font.PLAIN, 16));
            dl.setForeground(Color.WHITE);
            c.add(dl, g);

            g.gridx = 1;
            g.weightx = 0;
            g.anchor = GridBagConstraints.EAST;

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
