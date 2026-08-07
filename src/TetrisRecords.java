import javax.swing.*;
import java.awt.*;
import java.awt.event.*;
import java.nio.file.*;
import java.util.*;
import java.util.regex.*;

/**
 * 俄罗斯方块个人纪录对话框
 * 登录时从服务器读取，离线时用本地文件
 */
public class TetrisRecords {

    private static final String[] MODE_NAMES = {"经典模式", "困难模式", "叠叠乐模式"};
    private static final String TETRIS_REC_FILE = System.getProperty("user.home") + "/.tetris_records";

    private static String toDisplayName(String gameName, String mode) {
        if ("俄罗斯方块".equals(gameName)) {
            if ("经典".equals(mode)) return "经典模式";
            if ("困难".equals(mode)) return "困难模式";
            if ("叠叠乐".equals(mode)) return "叠叠乐模式";
        }
        return mode;
    }

    private static String toDbMode(String displayName) {
        switch (displayName) {
            case "经典模式": return "经典";
            case "困难模式": return "困难";
            case "叠叠乐模式": return "叠叠乐";
            default: return displayName;
        }
    }

    /** 本地文件加载纪录 */
    private static Map<String, String> loadRecsLocal() {
        LinkedHashMap<String, String> m = new LinkedHashMap<>();
        try {
            Path p = Paths.get(TETRIS_REC_FILE);
            if (Files.exists(p)) {
                String j = new String(Files.readAllBytes(p), "UTF-8");
                Matcher mr = Pattern.compile("\"([^\"]+)\":\s*\"([^\"]*)\"").matcher(j);
                while (mr.find()) m.put(mr.group(1), mr.group(2));
            }
        } catch (Exception ignored) {}
        return m;
    }

    /** 本地文件保存纪录 */
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
            Files.write(Paths.get(TETRIS_REC_FILE), sb.toString().getBytes("UTF-8"));
            return true;
        } catch (Exception e) {
            return false;
        }
    }

    /** 加载所有记录（从服务器，离线则本地文件） */
    public static Map<String, String> loadRecs(int userId) {
        // 离线模式：从本地文件加载
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
                            String key = toDisplayName(gameName, mode);
                            m.put(key, record);
                        }
                    }
                }
            }
        } catch (Exception ignored) {}
        return m;
    }

    /** 保存记录（仅当新分数更高时） */
    public static boolean saveRec(int userId, String gameMode, String record) {
        // 离线模式：保存到本地文件
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
            String dbMode = toDbMode(gameMode);
            String resp = ServerClient.saveRecord(userId, "俄罗斯方块", dbMode, record);
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

        JButton duelBtn = new JButton("对 决");
        duelBtn.setFont(new Font("Microsoft YaHei", Font.BOLD, 13));
        duelBtn.setForeground(Color.WHITE);
        duelBtn.setBackground(new Color(0, 120, 215));
        duelBtn.setFocusPainted(false);
        duelBtn.setBorderPainted(false);
        duelBtn.setContentAreaFilled(false);
        duelBtn.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
        duelBtn.setPreferredSize(new Dimension(80, 32));
        duelBtn.addActionListener(e -> {
            d.dispose();
            showDuelDialog(parent);
        });

        JPanel bp = new JPanel(new FlowLayout());
        bp.setBackground(new Color(60, 63, 65));
        bp.add(duelBtn);
        bp.add(cb);
        d.add(bp, BorderLayout.SOUTH);

        d.pack();
        d.setLocationRelativeTo(parent);
        d.setVisible(true);
    }

    /** 显示对决模式选择对话框 */
    private static void showDuelDialog(JFrame parent) {
        JDialog d = new JDialog(parent, "俄罗斯方块对决 - 选择模式", true);
        d.setResizable(false);
        d.setLayout(new BorderLayout());

        JPanel c = new JPanel(new GridBagLayout());
        c.setBackground(new Color(60, 63, 65));
        c.setBorder(BorderFactory.createEmptyBorder(20, 30, 20, 30));

        GridBagConstraints g = new GridBagConstraints();
        g.fill = GridBagConstraints.HORIZONTAL;
        g.insets = new Insets(8, 0, 8, 0);
        g.gridx = 0;
        g.weightx = 1;

        String[] modes = {"经典", "困难", "叠叠乐"};
        for (int i = 0; i < modes.length; i++) {
            g.gridy = i;
            JButton btn = new JButton(modes[i] + "模式");
            btn.setFont(new Font("Microsoft YaHei", Font.BOLD, 16));
            btn.setForeground(Color.WHITE);
            btn.setBackground(new Color(80, 83, 86));
            btn.setFocusPainted(false);
            btn.setBorder(BorderFactory.createEmptyBorder(12, 0, 12, 0));
            btn.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
            final String mode = modes[i];
            btn.addMouseListener(new MouseAdapter() {
                public void mouseEntered(MouseEvent e) { btn.setBackground(new Color(0, 120, 215)); }
                public void mouseExited(MouseEvent e) { btn.setBackground(new Color(80, 83, 86)); }
            });
            btn.addActionListener(e -> {
                d.dispose();
                showDuelCountDialog(parent, mode);
            });
            c.add(btn, g);
        }

        d.add(c, BorderLayout.CENTER);
        d.pack();
        d.setLocationRelativeTo(parent);
        d.setVisible(true);
    }

    /** 选择人数 */
    private static void showDuelCountDialog(JFrame parent, String mode) {
        JDialog d = new JDialog(parent, "俄罗斯方块对决 - 选择人数", true);
        d.setResizable(false);
        d.setLayout(new BorderLayout());

        JPanel c = new JPanel(new GridBagLayout());
        c.setBackground(new Color(60, 63, 65));
        c.setBorder(BorderFactory.createEmptyBorder(20, 30, 20, 30));

        GridBagConstraints g = new GridBagConstraints();
        g.fill = GridBagConstraints.HORIZONTAL;
        g.insets = new Insets(8, 0, 8, 0);
        g.gridx = 0;
        g.weightx = 1;

        int[] counts = {2, 3, 4};
        for (int i = 0; i < counts.length; i++) {
            g.gridy = i;
            JButton btn = new JButton(counts[i] + "人对决");
            btn.setFont(new Font("Microsoft YaHei", Font.BOLD, 16));
            btn.setForeground(Color.WHITE);
            btn.setBackground(new Color(80, 83, 86));
            btn.setFocusPainted(false);
            btn.setBorder(BorderFactory.createEmptyBorder(12, 0, 12, 0));
            btn.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
            final int maxPlayers = counts[i];
            btn.addMouseListener(new MouseAdapter() {
                public void mouseEntered(MouseEvent e) { btn.setBackground(new Color(0, 120, 215)); }
                public void mouseExited(MouseEvent e) { btn.setBackground(new Color(80, 83, 86)); }
            });
            btn.addActionListener(e -> {
                d.dispose();
                openMatchRoom(parent, mode, maxPlayers);
            });
            c.add(btn, g);
        }

        d.add(c, BorderLayout.CENTER);
        d.pack();
        d.setLocationRelativeTo(parent);
        d.setVisible(true);
    }

    /** 创建房间并打开匹配界面 */
    private static void openMatchRoom(JFrame parent, String mode, int maxPlayers) {
        parent.setVisible(false);
        final String username = ServerClient.getCurrentUser();
        final int userId = 0; // records dialog doesn't track userId
        Thread t = new Thread(() -> {
            String resp = ServerClient.duelCreate(username, mode, maxPlayers, "俄罗斯方块");
            SwingUtilities.invokeLater(() -> {
                if (resp.startsWith("SUCCESS")) {
                    String data = resp.substring("SUCCESS|".length());
                    String[] parts = data.split("\\|");
                    int roomId = Integer.parseInt(parts[0]);
                    TetrisMatchRoom room = new TetrisMatchRoom(username, userId, mode, maxPlayers,
                            true, roomId, parent);
                    room.applyRoomState(room.parseRoomState(resp));
                    room.setVisible(true);
                } else {
                    parent.setVisible(true);
                    JOptionPane.showMessageDialog(parent, "创建房间失败", "错误", JOptionPane.ERROR_MESSAGE);
                }
            });
        });
        t.setDaemon(true);
        t.start();
    }
}

