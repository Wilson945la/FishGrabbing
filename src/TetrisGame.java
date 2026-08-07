import javax.swing.*;
import java.awt.*;
import java.awt.event.*;
import java.util.Random;

public class TetrisGame extends JFrame {

    private static final Color BG = new Color(50, 53, 56);
    private static final Color BTN_BASE = new Color(80, 83, 86);
    private static final Color BTN_HOVER = new Color(0, 120, 215);
    private static final int COLS = 10;
    private static final int ROWS = 20;
    private static final int CELL = 30;

    private static final int[][][] SHAPES = {
        {{1,1,1,1}},
        {{1,1},{1,1}},
        {{0,1,0},{1,1,1}},
        {{1,0,0},{1,1,1}},
        {{0,0,1},{1,1,1}},
        {{0,1,1},{1,1,0}},
        {{1,1,0},{0,1,1}}
    };
    private static final Color[] COLORS = {
        new Color(0,240,240), new Color(240,240,0), new Color(160,0,240),
        new Color(0,0,240), new Color(240,160,0), new Color(0,240,0), new Color(240,0,0)
    };

    private int[][] board;
    private int[][] curShape;
    private Color curColor;
    private int curX, curY, curType;
    private int[][] nextShape;
    private Color nextColor;
    private int nextType;
    private int score, lines;
    private boolean gameOver, paused;
    private JFrame homeFrame;
    private int userId;
    private JPanel gamePanel, previewPanel;
    private JLabel scoreLabel, linesLabel, statusLabel;
    private Timer timer;
    private Random rand;
    private boolean hardMode;

    public void setHomeFrame(JFrame homeFrame) {
        this.homeFrame = homeFrame;
    }

    public TetrisGame(boolean hardMode, int userId) {
        this.hardMode = hardMode;
        this.userId = userId;
        setTitle(hardMode ? "\u4fc4\u7f57\u65af\u65b9\u5757 - \u56f0\u96be\u6a21\u5f0f" : "\u4fc4\u7f57\u65af\u65b9\u5757 - \u6e38\u620f\u4e2d");
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

        rand = new Random();
        board = new int[ROWS][COLS];
        score = 0;
        lines = 0;
        gameOver = false;
        paused = false;

        JPanel main = new JPanel(new BorderLayout());
        main.setBackground(BG);

        // === 游戏画布 ===
        gamePanel = new JPanel() {
            protected void paintComponent(Graphics g) {
                super.paintComponent(g);
                g.setColor(Color.BLACK);
                g.fillRect(0, 0, COLS * CELL, ROWS * CELL);
                g.setColor(new Color(40, 43, 46));
                for (int r = 0; r < ROWS; r++) {
                    for (int c = 0; c < COLS; c++) {
                        g.drawRect(c * CELL, r * CELL, CELL, CELL);
                    }
                }
                for (int r = 0; r < ROWS; r++) {
                    for (int c = 0; c < COLS; c++) {
                        if (board[r][c] > 0) {
                            g.setColor(COLORS[board[r][c] - 1]);
                            g.fillRect(c * CELL + 1, r * CELL + 1, CELL - 2, CELL - 2);
                        }
                    }
                }
                if (curShape != null && !gameOver && !hardMode) {
                    g.setColor(curColor);
                    for (int r = 0; r < curShape.length; r++) {
                        for (int c = 0; c < curShape[r].length; c++) {
                            if (curShape[r][c] == 1) {
                                int px = (curX + c) * CELL + 1;
                                int py = (curY + r) * CELL + 1;
                                g.fillRect(px, py, CELL - 2, CELL - 2);
                            }
                        }
                    }
                }
                if (gameOver) {
                    g.setColor(new Color(0, 0, 0, 150));
                    g.fillRect(0, 0, COLS * CELL, ROWS * CELL);
                    g.setColor(Color.WHITE);
                    g.setFont(new Font("Microsoft YaHei", Font.BOLD, 24));
                    String msg = "\u6e38\u620f\u7ed3\u675f";
                    FontMetrics fm = g.getFontMetrics();
                    g.drawString(msg, (COLS * CELL - fm.stringWidth(msg)) / 2, ROWS * CELL / 2);
                }
            }
        };
        gamePanel.setPreferredSize(new Dimension(COLS * CELL, ROWS * CELL));
        gamePanel.setBackground(Color.BLACK);

        // === 右侧面板 ===
        JPanel rightPanel = new JPanel();
        rightPanel.setBackground(BG);
        rightPanel.setLayout(new BoxLayout(rightPanel, BoxLayout.Y_AXIS));
        rightPanel.setBorder(BorderFactory.createEmptyBorder(10, 10, 10, 10));
        rightPanel.setPreferredSize(new Dimension(180, ROWS * CELL));

        // 分数/行数
        JLabel infoTitle = new JLabel("\u6e38\u620f\u4fe1\u606f");
        infoTitle.setFont(new Font("Microsoft YaHei", Font.BOLD, 14));
        infoTitle.setForeground(Color.WHITE);
        infoTitle.setAlignmentX(Component.CENTER_ALIGNMENT);
        rightPanel.add(infoTitle);
        if (hardMode) {
            rightPanel.add(Box.createVerticalStrut(4));
            JLabel hardLabel = new JLabel("\u56f0\u96be\u6a21\u5f0f");
            hardLabel.setFont(new Font("Microsoft YaHei", Font.BOLD, 12));
            hardLabel.setForeground(new Color(240, 80, 80));
            hardLabel.setAlignmentX(Component.CENTER_ALIGNMENT);
            rightPanel.add(hardLabel);
        }
        rightPanel.add(Box.createVerticalStrut(10));

        scoreLabel = new JLabel("\u5206\u6570: 0");
        scoreLabel.setFont(new Font("Microsoft YaHei", Font.BOLD, 13));
        scoreLabel.setForeground(new Color(255, 215, 0));
        scoreLabel.setAlignmentX(Component.CENTER_ALIGNMENT);
        rightPanel.add(scoreLabel);
        rightPanel.add(Box.createVerticalStrut(6));

        linesLabel = new JLabel("\u884c\u6570: 0");
        linesLabel.setFont(new Font("Microsoft YaHei", Font.BOLD, 13));
        linesLabel.setForeground(new Color(100, 255, 100));
        linesLabel.setAlignmentX(Component.CENTER_ALIGNMENT);
        rightPanel.add(linesLabel);
        rightPanel.add(Box.createVerticalStrut(6));

        statusLabel = new JLabel("");
        statusLabel.setFont(new Font("Microsoft YaHei", Font.BOLD, 12));
        statusLabel.setForeground(Color.WHITE);
        statusLabel.setAlignmentX(Component.CENTER_ALIGNMENT);
        rightPanel.add(statusLabel);
        rightPanel.add(Box.createVerticalStrut(10));

        // 下一个方块预览面板
        previewPanel = new JPanel() {
            private final int PREVIEW_CELL = 20;
            protected void paintComponent(Graphics g) {
                super.paintComponent(g);
                if (nextShape == null) return;
                g.setColor(Color.WHITE);
                g.setFont(new Font("Microsoft YaHei", Font.BOLD, 12));
                g.drawString("\u4e0b\u4e00\u4e2a", 10, 15);

                int rows = nextShape.length;
                int cols = nextShape[0].length;
                int offsetX = (getWidth() - cols * PREVIEW_CELL) / 2;
                int offsetY = 25;
                g.setColor(nextColor != null ? nextColor : Color.WHITE);
                for (int r = 0; r < rows; r++) {
                    for (int c = 0; c < cols; c++) {
                        if (nextShape[r][c] == 1) {
                            g.fillRect(offsetX + c * PREVIEW_CELL + 1, offsetY + r * PREVIEW_CELL + 1,
                                       PREVIEW_CELL - 2, PREVIEW_CELL - 2);
                        }
                    }
                }
            }
        };
        previewPanel.setBackground(new Color(40, 43, 46));
        previewPanel.setPreferredSize(new Dimension(160, 100));
        previewPanel.setMaximumSize(new Dimension(160, 100));
        previewPanel.setAlignmentX(Component.CENTER_ALIGNMENT);
        rightPanel.add(previewPanel);
        rightPanel.add(Box.createVerticalStrut(10));

        // 游戏规则面板
        JPanel rulesPanel = new JPanel();
        rulesPanel.setBackground(new Color(40, 43, 46));
        rulesPanel.setLayout(new BoxLayout(rulesPanel, BoxLayout.Y_AXIS));
        rulesPanel.setBorder(BorderFactory.createEmptyBorder(10, 10, 10, 10));
        rulesPanel.setPreferredSize(new Dimension(160, 180));
        rulesPanel.setMaximumSize(new Dimension(160, 180));
        rulesPanel.setAlignmentX(Component.CENTER_ALIGNMENT);

        JLabel rulesTitle = new JLabel("\u6e38\u620f\u89c4\u5219");
        rulesTitle.setFont(new Font("Microsoft YaHei", Font.BOLD, 13));
        rulesTitle.setForeground(Color.WHITE);
        rulesTitle.setAlignmentX(Component.CENTER_ALIGNMENT);
        rulesPanel.add(rulesTitle);
        rulesPanel.add(Box.createVerticalStrut(8));

        String[][] rules = {
            {"左右键 / A D", "\u5de6\u53f3\u79fb\u52a8"},
            {"上键 / W", "\u65cb\u8f6c"},
            {"下键 / S", "\u52a0\u901f\u4e0b\u843d"},
            {"空格键", "\u76f4\u63a5\u843d\u5e95"},
            {"P键", "\u6682\u505c/\u7ee7\u7eed"}
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
            // 如果当前分数>0，尝试保存纪录
            if (score > 0) {
                String mode = hardMode ? "\u56f0\u96be\u6a21\u5f0f" : "\u7ecf\u5178\u6a21\u5f0f";
                TetrisRecords.saveRec(userId, mode, String.valueOf(score));
            }
            board = new int[ROWS][COLS];
            score = 0; lines = 0;
            gameOver = false; paused = false;
            statusLabel.setText("");
            updateInfo();
            spawnPiece();
            timer = new Timer(500, ev -> moveDown());
            timer.start();
            repaint();
            requestFocusInWindow();
        });
        rightPanel.add(restartBtn);
        rightPanel.add(Box.createVerticalStrut(6));

        // 返回主页按钮
        JButton backBtn = txtBtn("\u8fd4\u56de\u4e3b\u9875", Color.WHITE, new Color(80, 83, 86), 90, 32);
        backBtn.setAlignmentX(Component.CENTER_ALIGNMENT);
        backBtn.addMouseListener(new MouseAdapter() {
            public void mouseEntered(MouseEvent e) { backBtn.setBackground(BTN_HOVER); }
            public void mouseExited(MouseEvent e) { backBtn.setBackground(BTN_BASE); }
        });
        backBtn.addActionListener(e -> {
            if (timer != null) timer.stop();
            setVisible(false);
            if (homeFrame != null) {
                homeFrame.setVisible(true);
            }
        });
        rightPanel.add(backBtn);

        main.add(gamePanel, BorderLayout.CENTER);
        main.add(rightPanel, BorderLayout.EAST);

        getContentPane().add(main);

        spawnPiece();
        updateInfo();
        pack();
        setLocationRelativeTo(null);

        timer = new Timer(500, e -> moveDown());
        timer.start();

        addKeyListener(new KeyAdapter() {
            public void keyPressed(KeyEvent e) {
                if (gameOver) return;
                int k = e.getKeyCode();
                if (k == KeyEvent.VK_LEFT || k == KeyEvent.VK_A) moveLeft();
                else if (k == KeyEvent.VK_RIGHT || k == KeyEvent.VK_D) moveRight();
                else if (k == KeyEvent.VK_DOWN || k == KeyEvent.VK_S) moveDown();
                else if (k == KeyEvent.VK_UP || k == KeyEvent.VK_W) rotate();
                else if (k == KeyEvent.VK_SPACE) drop();
                else if (k == KeyEvent.VK_P) togglePause();
                repaint();
            }
        });

        // 点击游戏面板也使方块直接落下
        gamePanel.addMouseListener(new MouseAdapter() {
            public void mousePressed(MouseEvent e) {
                if (gameOver) return;
                drop();
            }
        });

        setFocusable(true);
        requestFocusInWindow();
    }

    private void spawnPiece() {
        // 如果已有下一个方块，把它变成当前方块
        if (nextShape != null) {
            curType = nextType;
            curShape = new int[nextShape.length][];
            for (int i = 0; i < nextShape.length; i++) {
                curShape[i] = nextShape[i].clone();
            }
            curColor = nextColor;
        } else {
            curType = rand.nextInt(SHAPES.length);
            curShape = new int[SHAPES[curType].length][];
            for (int i = 0; i < SHAPES[curType].length; i++) {
                curShape[i] = SHAPES[curType][i].clone();
            }
            curColor = COLORS[curType];
        }

        // 生成下一个方块
        nextType = rand.nextInt(SHAPES.length);
        nextShape = new int[SHAPES[nextType].length][];
        for (int i = 0; i < SHAPES[nextType].length; i++) {
            nextShape[i] = SHAPES[nextType][i].clone();
        }
        nextColor = COLORS[nextType];

        curX = COLS / 2 - curShape[0].length / 2;
        curY = 0;
        if (!canPlace(curShape, curX, curY)) {
            gameOver = true;
            timer.stop();
            statusLabel.setText("\u6e38\u620f\u7ed3\u675f!");
            // 保存纪录
            String mode = hardMode ? "\u56f0\u96be\u6a21\u5f0f" : "\u7ecf\u5178\u6a21\u5f0f";
            TetrisRecords.saveRec(userId, mode, String.valueOf(score));
        }
        previewPanel.repaint();
    }

    private boolean canPlace(int[][] shape, int x, int y) {
        for (int r = 0; r < shape.length; r++) {
            for (int c = 0; c < shape[r].length; c++) {
                if (shape[r][c] == 1) {
                    int nx = x + c, ny = y + r;
                    if (nx < 0 || nx >= COLS || ny >= ROWS) return false;
                    if (ny >= 0 && board[ny][nx] != 0) return false;
                }
            }
        }
        return true;
    }

    private void lockPiece() {
        for (int r = 0; r < curShape.length; r++) {
            for (int c = 0; c < curShape[r].length; c++) {
                if (curShape[r][c] == 1) {
                    int ny = curY + r, nx = curX + c;
                    if (ny >= 0 && ny < ROWS && nx >= 0 && nx < COLS) {
                        board[ny][nx] = curType + 1;
                    }
                }
            }
        }
        int cleared = clearLines();
        // 落完一个没结束加一分
        score += 1;
        // 消除一行加五分
        if (cleared > 0) {
            score += cleared * 5;
        }
        lines += cleared;
        spawnPiece();
        updateInfo();
        repaint();
    }

    private int clearLines() {
        int cleared = 0;
        for (int r = ROWS - 1; r >= 0; r--) {
            boolean full = true;
            for (int c = 0; c < COLS; c++) {
                if (board[r][c] == 0) { full = false; break; }
            }
            if (full) {
                cleared++;
                for (int rr = r; rr > 0; rr--) {
                    System.arraycopy(board[rr - 1], 0, board[rr], 0, COLS);
                }
                java.util.Arrays.fill(board[0], 0);
                r++;
            }
        }
        return cleared;
    }

    private void moveDown() {
        if (gameOver || paused) return;
        if (canPlace(curShape, curX, curY + 1)) {
            curY++;
        } else {
            lockPiece();
        }
        repaint();
    }

    private void moveLeft() {
        if (canPlace(curShape, curX - 1, curY)) curX--;
        repaint();
    }

    private void moveRight() {
        if (canPlace(curShape, curX + 1, curY)) curX++;
        repaint();
    }

    private void rotate() {
        int rows = curShape.length, cols = curShape[0].length;
        int[][] rotated = new int[cols][rows];
        for (int r = 0; r < rows; r++) {
            for (int c = 0; c < cols; c++) {
                rotated[c][rows - 1 - r] = curShape[r][c];
            }
        }
        if (canPlace(rotated, curX, curY)) {
            curShape = rotated;
        }
        repaint();
    }

    private void drop() {
        while (canPlace(curShape, curX, curY + 1)) curY++;
        lockPiece();
        repaint();
    }

    private void togglePause() {
        paused = !paused;
        if (paused) { timer.stop(); statusLabel.setText("\u6682\u505c"); }
        else { timer.start(); statusLabel.setText(""); }
    }

    private void updateInfo() {
        scoreLabel.setText("\u5206\u6570: " + score);
        linesLabel.setText("\u884c\u6570: " + lines);
    }

    private JButton txtBtn(String text, Color fg, Color bg, int w, int h) {
        JButton b = new JButton(text);
        b.setFont(new Font("Microsoft YaHei", Font.BOLD, 13));
        b.setForeground(fg);
        b.setBackground(bg);
        b.setFocusPainted(false);
        b.setBorderPainted(false);
        b.setContentAreaFilled(false);
        b.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
        b.setPreferredSize(new Dimension(w, h));
        return b;
    }
}
