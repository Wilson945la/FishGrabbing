import javax.swing.*;
import java.awt.*;
import java.awt.event.*;
import java.util.*;
import java.util.List;

/**
 * 2048 经典模式
 * 4x4 网格，方向键/WASD 控制，相同数字合并，目标 2048
 * 渲染使用自定义绘制 + 滑动/合并/弹入动画，不再使用固定 GridLayout 标签。
 */
public class Game2048 extends JFrame {

    private static final int SIZE = 4;
    private static final Color BG = new Color(50, 53, 56);
    private static final Color GRID_BG = new Color(60, 63, 65);
    private static final Color CELL_BG = new Color(70, 73, 76);

    // 单元格像素布局
    private static final int CELL = 75;
    private static final int GAP = 8;
    private static final int PAD = 8;
    private static final int BOARD_PX = PAD * 2 + SIZE * CELL + (SIZE - 1) * GAP; // 340

    // 动画参数
    private static final int ANIM_MS = 150;       // 总动画时长
    private static final int TIMER_DELAY = 12;    // 每帧间隔
    private static final double SLIDE_END = 0.62; // 滑动阶段占比，其余为弹出阶段

    // 数字方块颜色
    private static final Map<Integer, Color> TILE_COLORS = new HashMap<>();
    static {
        TILE_COLORS.put(0, new Color(70, 73, 76));
        TILE_COLORS.put(2, new Color(238, 228, 218));
        TILE_COLORS.put(4, new Color(237, 224, 200));
        TILE_COLORS.put(8, new Color(242, 177, 121));
        TILE_COLORS.put(16, new Color(245, 149, 99));
        TILE_COLORS.put(32, new Color(246, 124, 95));
        TILE_COLORS.put(64, new Color(246, 94, 59));
        TILE_COLORS.put(128, new Color(237, 207, 114));
        TILE_COLORS.put(256, new Color(237, 204, 97));
        TILE_COLORS.put(512, new Color(237, 200, 80));
        TILE_COLORS.put(1024, new Color(237, 197, 63));
        TILE_COLORS.put(2048, new Color(237, 194, 46));
    }

    /** 一个方块：携带逻辑位置、动画起点位置、状态标记 */
    static class Tile {
        int value;
        int r, c;        // 移动后的逻辑位置
        int fromR, fromC; // 移动前的位置（用于滑动插值）
        boolean merged;  // 合并后弹出
        boolean spawned; // 新生成时弹入
        Tile(int v) { value = v; }
    }

    private Tile[][] grid = new Tile[SIZE][SIZE];
    private int score = 0;
    private int bestScore = 0;
    private boolean gameOver = false;
    private boolean won = false;
    private boolean winCelebrated = false;

    // 动画状态
    private BoardPanel boardPanel;
    private List<Tile> animConsumed = new ArrayList<>();
    private double animProgress = 0;
    private boolean animating = false;
    private boolean spawnedAdded = true;
    private javax.swing.Timer animTimer;

    private JLabel scoreLabel;
    private JLabel bestLabel;

    private JFrame homeFrame;
    private int userId;

    public void setHomeFrame(JFrame homeFrame) { this.homeFrame = homeFrame; }

    public Game2048(int userId) {
        this.userId = userId;
        this.bestScore = Game2048Records.loadBestScore(userId);

        setTitle("2048");
        setDefaultCloseOperation(JFrame.DISPOSE_ON_CLOSE);
        setResizable(false);

        addWindowListener(new WindowAdapter() {
            public void windowClosing(WindowEvent e) {
                if (homeFrame != null && homeFrame.isDisplayable()) {
                    homeFrame.setVisible(true);
                    homeFrame.setLocationRelativeTo(null);
                } else {
                    FishGrabbingHome.showActiveInstance();
                }
            }
        });

        initUI();
        initGame();

        // 键盘控制
        InputMap im = getRootPane().getInputMap(JComponent.WHEN_IN_FOCUSED_WINDOW);
        ActionMap am = getRootPane().getActionMap();
        im.put(KeyStroke.getKeyStroke(KeyEvent.VK_LEFT, 0), "left");
        im.put(KeyStroke.getKeyStroke(KeyEvent.VK_RIGHT, 0), "right");
        im.put(KeyStroke.getKeyStroke(KeyEvent.VK_UP, 0), "up");
        im.put(KeyStroke.getKeyStroke(KeyEvent.VK_DOWN, 0), "down");
        im.put(KeyStroke.getKeyStroke(KeyEvent.VK_A, 0), "left");
        im.put(KeyStroke.getKeyStroke(KeyEvent.VK_D, 0), "right");
        im.put(KeyStroke.getKeyStroke(KeyEvent.VK_W, 0), "up");
        im.put(KeyStroke.getKeyStroke(KeyEvent.VK_S, 0), "down");
        am.put("left", new AbstractAction() { public void actionPerformed(ActionEvent e) { move(0); } });
        am.put("right", new AbstractAction() { public void actionPerformed(ActionEvent e) { move(1); } });
        am.put("up", new AbstractAction() { public void actionPerformed(ActionEvent e) { move(2); } });
        am.put("down", new AbstractAction() { public void actionPerformed(ActionEvent e) { move(3); } });

        pack();
        setLocationRelativeTo(null);
    }

    private void initUI() {
        JPanel main = new JPanel(new BorderLayout());
        main.setBackground(BG);
        main.setPreferredSize(new Dimension(380, 560));

        // 顶部：标题 + 分数
        JPanel top = new JPanel(new BorderLayout());
        top.setBackground(BG);
        top.setBorder(BorderFactory.createEmptyBorder(15, 15, 10, 15));

        JLabel title = new JLabel("2048");
        title.setFont(new Font("Microsoft YaHei", Font.BOLD, 28));
        title.setForeground(Color.WHITE);

        JPanel scorePanel = new JPanel(new FlowLayout(FlowLayout.RIGHT, 5, 0));
        scorePanel.setBackground(BG);

        JPanel scoreBox = createScoreBox("分数", String.valueOf(score));
        scoreLabel = (JLabel) scoreBox.getClientProperty("label");
        JPanel bestBox = createScoreBox("最高", String.valueOf(bestScore));
        bestLabel = (JLabel) bestBox.getClientProperty("label");

        scorePanel.add(scoreBox);
        scorePanel.add(bestBox);

        top.add(title, BorderLayout.WEST);
        top.add(scorePanel, BorderLayout.EAST);
        main.add(top, BorderLayout.NORTH);

        // 中间：自定义绘制棋盘
        boardPanel = new BoardPanel();
        JPanel gridWrap = new JPanel(new FlowLayout(FlowLayout.CENTER, 0, 0));
        gridWrap.setBackground(BG);
        gridWrap.setBorder(BorderFactory.createEmptyBorder(5, 15, 5, 15));
        gridWrap.add(boardPanel);
        main.add(gridWrap, BorderLayout.CENTER);

        // 底部：提示 + 按钮
        JPanel hintPanel = new JPanel(new FlowLayout(FlowLayout.LEFT, 15, 0));
        hintPanel.setBackground(BG);
        JLabel hint = new JLabel("方向键/WASD 移动 · 合并相同数字");
        hint.setFont(new Font("Microsoft YaHei", Font.PLAIN, 11));
        hint.setForeground(new Color(150, 153, 156));
        hintPanel.add(hint);

        JPanel bot = new JPanel(new FlowLayout(FlowLayout.CENTER, 10, 5));
        bot.setBackground(BG);
        JButton newGameBtn = new JButton("新游戏");
        newGameBtn.setFont(new Font("Microsoft YaHei", Font.BOLD, 14));
        newGameBtn.setForeground(Color.WHITE);
        newGameBtn.setBackground(new Color(0, 120, 215));
        newGameBtn.setFocusPainted(false);
        newGameBtn.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
        newGameBtn.addActionListener(e -> initGame());
        bot.add(newGameBtn);

        JButton backBtn = new JButton("返回");
        backBtn.setFont(new Font("Microsoft YaHei", Font.BOLD, 14));
        backBtn.setForeground(Color.WHITE);
        backBtn.setBackground(new Color(80, 83, 86));
        backBtn.setFocusPainted(false);
        backBtn.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
        backBtn.addActionListener(e -> {
            dispose();
            if (homeFrame != null && homeFrame.isDisplayable()) {
                homeFrame.setVisible(true);
                homeFrame.setLocationRelativeTo(null);
            } else {
                FishGrabbingHome.showActiveInstance();
            }
        });
        bot.add(backBtn);

        JPanel south = new JPanel(new BorderLayout());
        south.setBackground(BG);
        south.add(hintPanel, BorderLayout.NORTH);
        south.add(bot, BorderLayout.SOUTH);
        main.add(south, BorderLayout.SOUTH);

        getContentPane().add(main);
    }

    private JPanel createScoreBox(String title, String value) {
        JPanel box = new JPanel();
        box.setLayout(new BoxLayout(box, BoxLayout.Y_AXIS));
        box.setBackground(new Color(60, 63, 65));
        box.setBorder(BorderFactory.createEmptyBorder(5, 12, 5, 12));
        box.setPreferredSize(new Dimension(70, 48));

        JLabel t = new JLabel(title, SwingConstants.CENTER);
        t.setFont(new Font("Microsoft YaHei", Font.PLAIN, 10));
        t.setForeground(new Color(150, 153, 156));
        t.setAlignmentX(Component.CENTER_ALIGNMENT);

        JLabel v = new JLabel(value, SwingConstants.CENTER);
        v.setFont(new Font("Microsoft YaHei", Font.BOLD, 16));
        v.setForeground(Color.WHITE);
        v.setAlignmentX(Component.CENTER_ALIGNMENT);

        box.add(t);
        box.add(v);
        box.putClientProperty("label", v);
        return box;
    }

    private void initGame() {
        grid = new Tile[SIZE][SIZE];
        score = 0;
        gameOver = false;
        won = false;
        winCelebrated = false;
        addRandomTile();
        addRandomTile();
        refreshScore();
        // 开局两个方块弹入动画（无滑动）
        animConsumed = new ArrayList<>();
        animProgress = 0;
        animating = true;
        spawnedAdded = true; // 开局不额外生成
        ensureTimer();
        animTimer.restart();
    }

    private void addRandomTile() {
        java.util.List<int[]> empty = new ArrayList<>();
        for (int r = 0; r < SIZE; r++)
            for (int c = 0; c < SIZE; c++)
                if (grid[r][c] == null) empty.add(new int[]{r, c});
        if (empty.isEmpty()) return;
        int[] pos = empty.get(new Random().nextInt(empty.size()));
        Tile t = new Tile(new Random().nextDouble() < 0.9 ? 2 : 4);
        t.r = pos[0]; t.c = pos[1];
        t.fromR = pos[0]; t.fromC = pos[1];
        t.spawned = true;
        grid[pos[0]][pos[1]] = t;
    }

    private void refreshScore() {
        scoreLabel.setText(String.valueOf(score));
        if (score > bestScore) {
            bestScore = score;
            bestLabel.setText(String.valueOf(bestScore));
        }
    }

    // ===== 移动逻辑（带动画）=====
    // dir: 0=左 1=右 2=上 3=下

    private void move(int dir) {
        if (animating || gameOver) return;
        if (doMove(dir)) {
            // 动画由定时器驱动，结束后触发结算
        } else {
            boardPanel.repaint();
        }
    }

    private boolean doMove(int dir) {
        // 1) 记录每个方块的起点（用于滑动插值）
        for (int r = 0; r < SIZE; r++)
            for (int c = 0; c < SIZE; c++) {
                Tile t = grid[r][c];
                if (t != null) {
                    t.fromR = r; t.fromC = c;
                    t.merged = false; t.spawned = false;
                }
            }

        Tile[][] newGrid = new Tile[SIZE][SIZE];
        List<Tile> consumed = new ArrayList<>();
        boolean moved = false;

        for (int L = 0; L < SIZE; L++) {
            // 沿移动方向收集本行/列的方块，并生成对应目标坐标
            List<Tile> srcs = new ArrayList<>();
            List<int[]> coords = new ArrayList<>();
            for (int k = 0; k < SIZE; k++) {
                int r, c;
                switch (dir) {
                    case 1: r = L; c = SIZE - 1 - k; break; // 右
                    case 2: r = k; c = L; break;            // 上
                    case 3: r = SIZE - 1 - k; c = L; break; // 下
                    default: r = L; c = k; break;           // 左
                }
                coords.add(new int[]{r, c});
                if (grid[r][c] != null) srcs.add(grid[r][c]);
            }

            // 滑动 + 合并
            List<Tile> resultTiles = new ArrayList<>();
            int idx = 0;
            boolean lastMerged = false;
            for (int i = 0; i < srcs.size(); i++) {
                Tile cur = srcs.get(i);
                if (idx > 0 && !lastMerged && resultTiles.get(idx - 1).value == cur.value) {
                    Tile prev = resultTiles.get(idx - 1);
                    prev.value *= 2;
                    score += prev.value;
                    if (prev.value == 2048) won = true;
                    prev.merged = true;
                    // 被合并的方块滑入目标格后消失
                    cur.r = prev.r; cur.c = prev.c;
                    consumed.add(cur);
                    moved = true;
                    lastMerged = true;
                    continue;
                }
                int[] rc = coords.get(idx);
                cur.r = rc[0]; cur.c = rc[1];
                newGrid[cur.r][cur.c] = cur;
                resultTiles.add(cur);
                idx++;
                lastMerged = false;
            }
            for (Tile t : resultTiles) {
                if (t.fromR != t.r || t.fromC != t.c) moved = true;
            }
        }

        if (!moved) return false;

        grid = newGrid;
        animConsumed = consumed;
        animProgress = 0;
        animating = true;
        spawnedAdded = false; // 动画滑动结束后生成一个新方块
        refreshScore();
        ensureTimer();
        animTimer.restart();
        return true;
    }

    private void ensureTimer() {
        if (animTimer != null) return;
        animTimer = new javax.swing.Timer(TIMER_DELAY, e -> {
            animProgress += (double) TIMER_DELAY / ANIM_MS;
            // 滑动结束补生成新方块
            if (animProgress >= SLIDE_END && !spawnedAdded) {
                spawnedAdded = true;
                addRandomTile();
                refreshScore();
            }
            if (animProgress >= 1.0) {
                animProgress = 1.0;
                animating = false;
                animTimer.stop();
                boardPanel.repaint();
                finishAnimation();
                return;
            }
            boardPanel.repaint();
        });
    }

    private void finishAnimation() {
        if (won && !winCelebrated) {
            winCelebrated = true;
            gameOver = true;
            int choice = JOptionPane.showConfirmDialog(this,
                "恭喜达成 2048！\n是否继续游戏？", "胜利！",
                JOptionPane.YES_NO_OPTION, JOptionPane.INFORMATION_MESSAGE);
            if (choice == JOptionPane.YES_OPTION) {
                gameOver = false;
            } else {
                saveAndExit();
            }
            boardPanel.repaint();
            return;
        }
        if (isGameOver()) {
            gameOver = true;
            saveAndExit();
            JOptionPane.showMessageDialog(this,
                "游戏结束！\n最终分数：" + score, "游戏结束",
                JOptionPane.INFORMATION_MESSAGE);
        }
    }

    private boolean isGameOver() {
        for (int r = 0; r < SIZE; r++)
            for (int c = 0; c < SIZE; c++)
                if (grid[r][c] == null) return false;
        for (int r = 0; r < SIZE; r++)
            for (int c = 0; c < SIZE; c++) {
                if (c < SIZE - 1 && grid[r][c].value == grid[r][c + 1].value) return false;
                if (r < SIZE - 1 && grid[r][c].value == grid[r + 1][c].value) return false;
            }
        return true;
    }

    private void saveAndExit() {
        Game2048Records.saveRec(userId, "经典模式", String.valueOf(score));
    }

    // ===== 绘制 =====

    private double cellX(int c) { return PAD + c * (CELL + GAP); }
    private double cellY(int r) { return PAD + r * (CELL + GAP); }
    private double lerp(double a, double b, double t) { return a + (b - a) * t; }
    private double easeOut(double t) { return t * (2 - t); }

    private void paintTile(Graphics2D g, int value, double x, double y, double scale) {
        double w = CELL * scale, h = CELL * scale;
        int px = (int) Math.round(x + (CELL - w) / 2);
        int py = (int) Math.round(y + (CELL - h) / 2);
        int ww = (int) Math.round(w), hh = (int) Math.round(h);
        g.setColor(TILE_COLORS.getOrDefault(value, new Color(60, 58, 50)));
        int arc = (int) Math.round(12 * scale);
        g.fillRoundRect(px, py, ww, hh, arc, arc);
        if (value != 0) {
            g.setColor(value <= 4 ? new Color(119, 110, 101) : Color.WHITE);
            int fs = value >= 1024 ? 22 : (value >= 128 ? 24 : 28);
            g.setFont(new Font("Microsoft YaHei", Font.BOLD, fs));
            String s = String.valueOf(value);
            FontMetrics fm = g.getFontMetrics();
            int tw = fm.stringWidth(s);
            int th = fm.getAscent();
            g.drawString(s, px + (ww - tw) / 2, py + (hh - th) / 2 + th);
        }
    }

    class BoardPanel extends JPanel {
        BoardPanel() {
            setPreferredSize(new Dimension(BOARD_PX, BOARD_PX));
            setBackground(GRID_BG);
        }

        protected void paintComponent(Graphics g) {
            super.paintComponent(g);
            Graphics2D g2 = (Graphics2D) g;
            g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);

            // 背景空格
            for (int r = 0; r < SIZE; r++)
                for (int c = 0; c < SIZE; c++) {
                    g2.setColor(CELL_BG);
                    g2.fillRoundRect((int) cellX(c), (int) cellY(r), CELL, CELL, 12, 12);
                }

            double p = animating ? animProgress : 1.0;
            if (animating && p < SLIDE_END) {
                // 滑动阶段：非合并方块 + 被吞并方块 从起点插值到终点
                double sp = easeOut(p / SLIDE_END);
                for (int r = 0; r < SIZE; r++)
                    for (int c = 0; c < SIZE; c++) {
                        Tile t = grid[r][c];
                        if (t != null && !t.merged) {
                            double x = lerp(cellX(t.fromC), cellX(t.c), sp);
                            double y = lerp(cellY(t.fromR), cellY(t.r), sp);
                            paintTile(g2, t.value, x, y, 1.0);
                        }
                    }
                for (Tile t : animConsumed) {
                    double x = lerp(cellX(t.fromC), cellX(t.c), sp);
                    double y = lerp(cellY(t.fromR), cellY(t.r), sp);
                    paintTile(g2, t.value, x, y, 1.0);
                }
            } else {
                // 弹出阶段（含静态终态）：合并块放大回弹，新块放大弹入
                double pp = animating ? (p - SLIDE_END) / (1 - SLIDE_END) : 1.0;
                for (int r = 0; r < SIZE; r++)
                    for (int c = 0; c < SIZE; c++) {
                        Tile t = grid[r][c];
                        if (t == null) continue;
                        double scale = 1.0;
                        if (t.merged) scale = 1 + 0.18 * Math.sin(pp * Math.PI);
                        else if (t.spawned) scale = Math.min(1.0, pp * 1.15);
                        paintTile(g2, t.value, cellX(c), cellY(r), scale);
                    }
            }
        }
    }

    public static void main(String[] args) {
        System.setProperty("sun.java2d.dpiaware", "true");
        System.setProperty("sun.java2d.uiScale", "1.0");
        SwingUtilities.invokeLater(() -> new Game2048(0).setVisible(true));
    }
}
