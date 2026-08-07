import javax.swing.*;
import java.awt.*;
import java.awt.event.*;
import java.util.ArrayList;
import java.util.List;

/**
 * 叠叠乐模式 — 方块在顶部左右移动，按空格下落
 * 物理判定：整体重心必须在底部支撑范围内，否则失衡掉落
 */
public class TetrisStack extends JFrame {

    private static final Color BG = new Color(50, 53, 56);
    private static final Color BTN_BASE = new Color(80, 83, 86);
    private static final Color BTN_HOVER = new Color(0, 120, 215);
    private static final int COLS = 10;
    private static final int ROWS = 20; // 可视行数
    private static final int BOARD_ROWS = 400; // 总行数，支持方块堆到负数世界行
    private static final int BOARD_BASE = 200; // 世界行0 → board[200]
    private static final int CELL = 30;

    // board 访问：世界行 → board索引
    private static int w2b(int worldRow) { return worldRow + BOARD_BASE; }

    private static final int[][] BLOCK_SIZES = {
        {2, 1}, {3, 1}, {4, 1}, {2, 2}, {1, 1}, {3, 2}, {1, 2}
    };
    private static final Color[] BLOCK_COLORS = {
        new Color(0, 240, 240), new Color(240, 240, 0), new Color(160, 0, 240),
        new Color(0, 0, 240), new Color(240, 160, 0), new Color(0, 240, 0), new Color(240, 0, 0)
    };

    private int[][] board;
    private int curType, curX, curY, curW, curH;
    private Color curColor;
    private int moveDir, frameCount, score, viewOffset;
    private boolean gameOver, paused;
    private JFrame homeFrame;
    private int userId;
    private JPanel gamePanel;
    private JLabel scoreLabel, statusLabel;
    private Timer timer;

    private static class PlacedBlock {
        int bx, by, bw, bh, type;
        PlacedBlock(int bx, int by, int bw, int bh, int type) {
            this.bx = bx; this.by = by; this.bw = bw; this.bh = bh;
            this.type = type;
        }
    }
    private List<PlacedBlock> placedBlocks = new ArrayList<>();

    public void setHomeFrame(JFrame homeFrame) { this.homeFrame = homeFrame; }

    public TetrisStack(int userId) {
        this.userId = userId;
        setTitle("\u4fc4\u7f57\u65af\u65b9\u5757 - \u53e0\u53e0\u4e50");
        setDefaultCloseOperation(JFrame.DISPOSE_ON_CLOSE);
        addWindowListener(new java.awt.event.WindowAdapter() {
            public void windowClosing(java.awt.event.WindowEvent e) {
                if (timer != null) timer.stop();
                String u = ServerClient.getCurrentUser();
                if (u != null) {
                    try { ServerClient.setUserState(u, 0); } catch (Exception ignored) {}
                }
            }
        });
        setResizable(false);

        board = new int[BOARD_ROWS][COLS];
        score = 0; viewOffset = 0; gameOver = false; paused = false;
        moveDir = 1; frameCount = 0;

        JPanel main = new JPanel(new BorderLayout());
        main.setBackground(BG);

        gamePanel = new JPanel() {
            protected void paintComponent(Graphics g) {
                super.paintComponent(g);
                g.setColor(Color.BLACK);
                g.fillRect(0, 0, COLS * CELL, ROWS * CELL);

                // 网格线
                g.setColor(new Color(40, 43, 46));
                for (int r = 0; r < ROWS; r++)
                    for (int c = 0; c < COLS; c++)
                        g.drawRect(c * CELL, r * CELL, CELL, CELL);

                // 地面线（viewOffset 负 → 地面线下移退出屏幕）
                // 地面在世界行 ROWS-1，屏幕行 = worldRow - viewOffset
                int groundScreenRow = (ROWS - 1) - viewOffset;
                if (groundScreenRow >= -1 && groundScreenRow < ROWS + 1) {
                    g.setColor(new Color(0, 180, 0));
                    g.fillRect(0, (groundScreenRow + 1) * CELL - 2, COLS * CELL, 3);
                }

                // 已落地方块（扫描全部世界行，只显示屏幕内的）
                for (int wr = -(BOARD_BASE - 1); wr < BOARD_ROWS - BOARD_BASE; wr++) {
                    int screenR = wr - viewOffset;
                    if (screenR < 0 || screenR >= ROWS) continue;
                    for (int c = 0; c < COLS; c++) {
                        if (board[w2b(wr)][c] > 0) {
                            g.setColor(BLOCK_COLORS[board[w2b(wr)][c] - 1]);
                            g.fillRect(c * CELL + 1, screenR * CELL + 1, CELL - 2, CELL - 2);
                        }
                    }
                }

                // 当前方块 + ghost
                if (curType >= 0 && !gameOver) {
                    int ghostY = findLandingY();
                    g.setColor(new Color(curColor.getRed(), curColor.getGreen(), curColor.getBlue(), 60));
                    for (int r = 0; r < curH; r++)
                        for (int c = 0; c < curW; c++) {
                            int sr = ghostY + r - viewOffset;
                            if (sr >= 0 && sr < ROWS)
                                g.fillRect((curX + c) * CELL + 1, sr * CELL + 1, CELL - 2, CELL - 2);
                        }
                    g.setColor(curColor);
                    for (int r = 0; r < curH; r++)
                        for (int c = 0; c < curW; c++) {
                            int sr = curY + r;
                            if (sr >= 0 && sr < ROWS)
                                g.fillRect((curX + c) * CELL + 1, sr * CELL + 1, CELL - 2, CELL - 2);
                        }
                }

                if (gameOver) {
                    g.setColor(new Color(0, 0, 0, 150));
                    g.fillRect(0, 0, COLS * CELL, ROWS * CELL);
                    g.setColor(Color.WHITE);
                    g.setFont(new Font("Microsoft YaHei", Font.BOLD, 22));
                    String msg = "\u5931\u8861\uff01\u6e38\u620f\u7ed3\u675f";
                    FontMetrics fm = g.getFontMetrics();
                    g.drawString(msg, (COLS * CELL - fm.stringWidth(msg)) / 2, ROWS * CELL / 2);
                }

                if (paused && !gameOver) {
                    g.setColor(new Color(0, 0, 0, 100));
                    g.fillRect(0, 0, COLS * CELL, ROWS * CELL);
                    g.setColor(Color.WHITE);
                    g.setFont(new Font("Microsoft YaHei", Font.BOLD, 22));
                    String msg = "\u6682\u505c";
                    FontMetrics fm = g.getFontMetrics();
                    g.drawString(msg, (COLS * CELL - fm.stringWidth(msg)) / 2, ROWS * CELL / 2);
                }
            }
        };
        gamePanel.setPreferredSize(new Dimension(COLS * CELL, ROWS * CELL));
        gamePanel.setBackground(Color.BLACK);

        JPanel rightPanel = new JPanel();
        rightPanel.setBackground(BG);
        rightPanel.setLayout(new BoxLayout(rightPanel, BoxLayout.Y_AXIS));
        rightPanel.setBorder(BorderFactory.createEmptyBorder(10, 10, 10, 10));
        rightPanel.setPreferredSize(new Dimension(180, ROWS * CELL));

        JLabel infoTitle = new JLabel("\u6e38\u620f\u4fe1\u606f");
        infoTitle.setFont(new Font("Microsoft YaHei", Font.BOLD, 14));
        infoTitle.setForeground(Color.WHITE);
        infoTitle.setAlignmentX(Component.CENTER_ALIGNMENT);
        rightPanel.add(infoTitle);
        rightPanel.add(Box.createVerticalStrut(4));

        JLabel modeLabel = new JLabel("\u53e0\u53e0\u4e50\u6a21\u5f0f");
        modeLabel.setFont(new Font("Microsoft YaHei", Font.BOLD, 12));
        modeLabel.setForeground(new Color(240, 180, 0));
        modeLabel.setAlignmentX(Component.CENTER_ALIGNMENT);
        rightPanel.add(modeLabel);
        rightPanel.add(Box.createVerticalStrut(10));

        scoreLabel = new JLabel("\u5206\u6570: 0");
        scoreLabel.setFont(new Font("Microsoft YaHei", Font.BOLD, 13));
        scoreLabel.setForeground(new Color(255, 215, 0));
        scoreLabel.setAlignmentX(Component.CENTER_ALIGNMENT);
        rightPanel.add(scoreLabel);
        rightPanel.add(Box.createVerticalStrut(6));

        statusLabel = new JLabel("");
        statusLabel.setFont(new Font("Microsoft YaHei", Font.BOLD, 12));
        statusLabel.setForeground(Color.WHITE);
        statusLabel.setAlignmentX(Component.CENTER_ALIGNMENT);
        rightPanel.add(statusLabel);
        rightPanel.add(Box.createVerticalStrut(10));

        JPanel rulesPanel = new JPanel();
        rulesPanel.setBackground(new Color(40, 43, 46));
        rulesPanel.setLayout(new BoxLayout(rulesPanel, BoxLayout.Y_AXIS));
        rulesPanel.setBorder(BorderFactory.createEmptyBorder(10, 10, 10, 10));
        rulesPanel.setPreferredSize(new Dimension(160, 120));
        rulesPanel.setMaximumSize(new Dimension(160, 120));
        rulesPanel.setAlignmentX(Component.CENTER_ALIGNMENT);

        JLabel rulesTitle = new JLabel("\u6e38\u620f\u89c4\u5219");
        rulesTitle.setFont(new Font("Microsoft YaHei", Font.BOLD, 13));
        rulesTitle.setForeground(Color.WHITE);
        rulesTitle.setAlignmentX(Component.CENTER_ALIGNMENT);
        rulesPanel.add(rulesTitle);
        rulesPanel.add(Box.createVerticalStrut(8));

        String[][] rules = {
            {"空格键", "方块落下"},
            {"鼠标点击", "也可使方块落下"},
            {"P键", "暂停/继续"},
            {"", ""},
            {"注意", "重心必须在支撑区内！"},
            {"", "失衡方块会掉落"}
        };
        for (String[] rule : rules) {
            JPanel ruleRow = new JPanel(new FlowLayout(FlowLayout.LEFT, 4, 0));
            ruleRow.setBackground(new Color(40, 43, 46));
            ruleRow.setMaximumSize(new Dimension(140, 22));
            JLabel key = new JLabel(rule[0]);
            key.setFont(new Font("Microsoft YaHei", Font.BOLD, 12));
            key.setForeground(new Color(255, 215, 0));
            JLabel desc = new JLabel(rule[1]);
            desc.setFont(new Font("Microsoft YaHei", Font.PLAIN, 11));
            desc.setForeground(new Color(200, 203, 206));
            ruleRow.add(key);
            ruleRow.add(desc);
            rulesPanel.add(ruleRow);
        }

        rightPanel.add(rulesPanel);
        rightPanel.add(Box.createVerticalStrut(10));

        // 重新开始按钮
        JButton restartBtn = txtBtn("重新开始", Color.WHITE, new Color(80, 83, 86), 90, 32);
        restartBtn.setAlignmentX(Component.CENTER_ALIGNMENT);
        restartBtn.addMouseListener(new MouseAdapter() {
            public void mouseEntered(MouseEvent e) { restartBtn.setBackground(BTN_HOVER); }
            public void mouseExited(MouseEvent e) { restartBtn.setBackground(BTN_BASE); }
        });
        restartBtn.addActionListener(e -> {
            if (timer != null) timer.stop();
            board = new int[BOARD_ROWS][COLS];
            placedBlocks.clear();
            score = 0; viewOffset = 0; gameOver = false; paused = false;
            moveDir = 1; frameCount = 0;
            statusLabel.setText("");
            updateInfo();
            spawnPiece();
            timer = new Timer(16, ev -> {
                if (!gameOver && !paused) {
                    frameCount++;
                    if (frameCount % getMoveSpeed() == 0) moveHorizontally();
                    repaint();
                }
            });
            timer.start();
            repaint();
            requestFocusInWindow();
        });
        rightPanel.add(restartBtn);
        rightPanel.add(Box.createVerticalStrut(6));

        JButton backBtn = txtBtn("\u8fd4\u56de\u4e3b\u9875", Color.WHITE, new Color(80, 83, 86), 90, 32);
        backBtn.setAlignmentX(Component.CENTER_ALIGNMENT);
        backBtn.addMouseListener(new MouseAdapter() {
            public void mouseEntered(MouseEvent e) { backBtn.setBackground(BTN_HOVER); }
            public void mouseExited(MouseEvent e) { backBtn.setBackground(BTN_BASE); }
        });
        backBtn.addActionListener(e -> {
            if (timer != null) timer.stop();
            setVisible(false);
            if (homeFrame != null) homeFrame.setVisible(true);
        });
        rightPanel.add(backBtn);

        main.add(gamePanel, BorderLayout.CENTER);
        main.add(rightPanel, BorderLayout.EAST);
        getContentPane().add(main);

        spawnPiece();
        updateInfo();
        pack();
        setLocationRelativeTo(null);

        timer = new Timer(16, e -> {
            if (!gameOver && !paused) {
                frameCount++;
                if (frameCount % getMoveSpeed() == 0) moveHorizontally();
                repaint();
            }
        });
        timer.start();

        addKeyListener(new KeyAdapter() {
            public void keyPressed(KeyEvent e) {
                if (gameOver) return;
                int k = e.getKeyCode();
                if (k == KeyEvent.VK_LEFT || k == KeyEvent.VK_A) { curX = Math.max(0, curX - 1); repaint(); }
                else if (k == KeyEvent.VK_RIGHT || k == KeyEvent.VK_D) { curX = Math.min(COLS - curW, curX + 1); repaint(); }
                else if (k == KeyEvent.VK_SPACE) { dropPiece(); }
                else if (k == KeyEvent.VK_P) { togglePause(); }
            }
        });

        // 点击游戏面板也使方块落下
        gamePanel.addMouseListener(new MouseAdapter() {
            public void mousePressed(MouseEvent e) {
                if (gameOver) return;
                dropPiece();
            }
        });
        setFocusable(true);
        requestFocusInWindow();
    }

    private void spawnPiece() {
        java.util.Random rand = new java.util.Random();
        curType = rand.nextInt(BLOCK_SIZES.length);
        curW = BLOCK_SIZES[curType][0];
        curH = BLOCK_SIZES[curType][1];
        curColor = BLOCK_COLORS[curType];
        curX = 0; curY = 0; moveDir = 1; frameCount = 0;
    }

    private void moveHorizontally() {
        curX += moveDir;
        if (curX + curW >= COLS) moveDir = -1;
        else if (curX <= 0) moveDir = 1;
    }

    /** 方块越多速度越快，初始16帧/次，每5个方块快1帧，最快2帧/次 */
    private int getMoveSpeed() {
        int n = placedBlocks.size();
        int speed = 16 - n / 5;
        return Math.max(2, speed);
    }

    /**
     * 从顶部模拟下落，找到当前方块能落到的最高合法位置
     * 考虑方块完整形状（宽x高），不会跟已有方块重叠
     */
    private int findLandingY() {
        // 找到当前最高方块的底部位置，新方块从该位置上方开始下落
        // 扫描范围扩展到负数世界行
        int topBlockBottom = ROWS;
        for (int r = -(BOARD_BASE - 1); r < ROWS; r++) {
            for (int c = 0; c < COLS; c++) {
                if (board[w2b(r)][c] > 0) { topBlockBottom = r; r = ROWS; break; }
            }
        }
        int startY = Math.min(-(BOARD_BASE - curH - 1), topBlockBottom - curH - 2);

        // 从 startY 开始往下试探
        for (int y = startY; y <= ROWS - curH; y++) {
            boolean collision = false;
            for (int r = 0; r < curH && !collision; r++) {
                for (int c = 0; c < curW && !collision; c++) {
                    int br = y + r;
                    int bc = curX + c;
                    if (br >= -BOARD_BASE && br < BOARD_ROWS - BOARD_BASE && bc >= 0 && bc < COLS && board[w2b(br)][bc] > 0) {
                        collision = true;
                    }
                }
            }
            if (collision) {
                return y - 1; // 碰撞前一行就是最终落点
            }
        }
        // 整列都没有方块，落到底
        return ROWS - curH;
    }

    private void dropPiece() {
        curY = findLandingY();

        // 规则1：新方块必须落在上一个方块上
        if (!placedBlocks.isEmpty()) {
            PlacedBlock last = placedBlocks.get(placedBlocks.size() - 1);
            boolean overlapsX = (curX + curW > last.bx) && (curX < last.bx + last.bw);
            boolean touchingY = (curY + curH == last.by);
            if (!overlapsX || !touchingY) { endGame(); return; }
        }

        // 规则2：逐层失衡判定
        //   对每个支撑层，找出直接坐在这个层上的方块，再递归收集叠在它们上面的所有方块
        //   检查该堆叠组的组合重心是否在该层的支撑范围内
        {
            PlacedBlock curBlock = new PlacedBlock(curX, curY, curW, curH, curType);
            java.util.List<PlacedBlock> all = new java.util.ArrayList<>(placedBlocks);
            all.add(curBlock);

            // 找出所有唯一的底部 y 值（支撑层）
            java.util.Set<Integer> layerLevels = new java.util.HashSet<>();
            for (PlacedBlock pb : all) layerLevels.add(pb.by + pb.bh);

            for (int layerY : layerLevels) {
                // 该层的支撑范围：所有底部恰好在该层上的方块
                double layerMin, layerMax;
                if (layerY >= ROWS) { layerMin = 0; layerMax = COLS; }
                else {
                    layerMin = COLS; layerMax = 0;
                    for (PlacedBlock pb : all) {
                        if (pb.by + pb.bh == layerY) {
                            if (pb.bx < layerMin) layerMin = pb.bx;
                            if (pb.bx + pb.bw > layerMax) layerMax = pb.bx + pb.bw;
                        }
                    }
                }

                // 找出直接坐在该层上的方块作为基底
                java.util.List<PlacedBlock> stack = new java.util.ArrayList<>();
                for (PlacedBlock pb : all) {
                    if (pb.by == layerY) stack.add(pb);
                }
                if (stack.isEmpty()) continue;

                // 递归收集叠在基底上的所有方块
                collectAbove(all, stack);

                // 计算组合重心
                double tw = 0, tm = 0;
                for (PlacedBlock s : stack) {
                    double w = s.bw * s.bh;
                    tw += w; tm += w * (s.bx + s.bw / 2.0);
                }
                double cx = tm / tw;

                // 重心超出支撑范围（允许 0.5 的容忍） → 失衡
                double margin = 0.5;
                if (cx < layerMin - margin || cx > layerMax + margin) {
                    endGame();
                    return;
                }
            }
        }

        lockPiece();
        score += 2;
        updateInfo();
        updateViewOffset();
        spawnPiece();
        repaint();
    }

    private void updateViewOffset() {
        // 找到最高的已占行（扫描范围扩展到负数世界行）
        int topRow = ROWS;
        outer: for (int r = -(BOARD_BASE - 1); r < ROWS; r++)
            for (int c = 0; c < COLS; c++) {
                if (board[w2b(r)][c] > 0) { topRow = r; break outer; }
            }
        if (topRow >= ROWS) { viewOffset = 0; return; }

        // 屏幕行 r 显示世界行 wr = r + viewOffset
        // viewOffset 负 → 画面下移
        // viewOffset = 0 → 正常全览
        //
        // 方块叠到接近顶部时才下移，保留底部一定空间不动
        int threshold = 10; // 顶方块到达屏幕中间时开始滚动
        if (topRow >= threshold) {
            viewOffset = 0;
            return;
        }
        viewOffset = topRow - threshold; // 负数，画面下移
    }

    /**
     * 递归收集放在 stack 中的方块之上的所有方块
     */
    private void collectAbove(java.util.List<PlacedBlock> all, java.util.List<PlacedBlock> stack) {
        java.util.List<PlacedBlock> next = new java.util.ArrayList<>();
        for (PlacedBlock base : stack) {
            for (PlacedBlock pb : all) {
                if (stack.contains(pb)) continue;
                if (pb.by == base.by + base.bh) {
                    boolean overlaps = (pb.bx + pb.bw > base.bx) && (pb.bx < base.bx + base.bw);
                    if (overlaps) next.add(pb);
                }
            }
        }
        stack.addAll(next);
        if (!next.isEmpty()) collectAbove(all, stack);
    }

    private void lockPiece() {
        placedBlocks.add(new PlacedBlock(curX, curY, curW, curH, curType));
        for (int r = 0; r < curH; r++)
            for (int c = 0; c < curW; c++) {
                int ny = curY + r, nx = curX + c;
                if (ny >= -BOARD_BASE && ny < BOARD_ROWS - BOARD_BASE && nx >= 0 && nx < COLS)
                    board[w2b(ny)][nx] = curType + 1;
            }
    }

    private void endGame() {
        gameOver = true;
        if (timer != null) timer.stop();
        statusLabel.setText("\u5931\u8861\uff01");
        // 保存纪录
        TetrisRecords.saveRec(userId, "\u53e0\u53e0\u4e50\u6a21\u5f0f", String.valueOf(score));
        repaint();
    }

    private void togglePause() {
        paused = !paused;
        statusLabel.setText(paused ? "\u6682\u505c" : "");
    }

    private void updateInfo() { scoreLabel.setText("\u5206\u6570: " + score); }

    private JButton txtBtn(String text, Color fg, Color bg, int w, int h) {
        JButton b = new JButton(text);
        b.setFont(new Font("Microsoft YaHei", Font.BOLD, 13));
        b.setForeground(fg); b.setBackground(bg);
        b.setFocusPainted(false); b.setBorderPainted(false); b.setContentAreaFilled(false);
        b.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
        b.setPreferredSize(new Dimension(w, h));
        return b;
    }
}
