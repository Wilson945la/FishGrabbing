import javax.swing.*;
import java.awt.*;
import java.awt.event.*;
import java.util.*;

/**
 * 俄罗斯方块对决游戏
 */
public class TetrisDuelGame extends JFrame {

    private static final Color BG = new Color(50, 53, 56);
    private static final Color BTN_BASE = new Color(80, 83, 86);
    private static final Color BTN_HOVER = new Color(0, 120, 215);
    private static final int COLS = 10;
    private static final int ROWS = 20;
    private static final int CELL = 28;

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

    // 游戏状态
    private int[][] board;
    private int[][] curShape;
    private Color curColor;
    private int curX, curY, curType;
    private int[][] nextShape;
    private Color nextColor;
    private int nextType;
    private int score, lines;
    private boolean gameOver, paused;
    private javax.swing.Timer timer;
    private Random rand;
    private long startTime;

    // 对决状态
    private String username;
    private int roomId;
    private String mode; // 经典/困难/叠叠乐
    private java.util.List<String> allPlayers;
    private JFrame parentRoom;

    // 对手面板
    private JPanel opponentPanel;
    private Map<String, OpponentCard> opponentCards = new LinkedHashMap<>();

    // 结果相关
    private boolean localFinished = false;
    private boolean iFinished = false;
    private boolean iFailed = false;
    private boolean globalFinished = false;
    private long myFinishTime = 0;
    private javax.swing.Timer resultPollTimer;
    private javax.swing.Timer forceEndTimer;

    // 活跃游戏注册表（用于接收推送）
    private static final Map<Integer, TetrisDuelGame> activeGames = new java.util.concurrent.ConcurrentHashMap<>();

    // 常量
    private static final Color ACCENT = new Color(0, 120, 215);
    private static final Color GOLD = new Color(255, 215, 0);

    /** 对手卡片（迷你棋盘） */
    class OpponentCard extends JPanel {
        String playerName;
        boolean isBot;
        int botDifficulty; // 0/1/2
        int[][] botBoard;  // 机器人独立棋盘
        boolean opponentFinished = false;
        boolean opponentFailed = false;
        int opponentScore = 0;
        int opponentLines = 0;

        // 机器人AI状态
        int[][] botCurShape;
        Color botCurColor;
        int botCurX, botCurY, botCurType;
        Random botRand;
        javax.swing.Timer botTimer;
        int botMoveCount = 0;

        OpponentCard(String name, boolean isBot, int difficulty) {
            this.playerName = name;
            this.isBot = isBot;
            this.botDifficulty = difficulty;
            if (isBot) {
                this.botBoard = new int[ROWS][COLS];
                this.botRand = new Random(name.hashCode() * 31 + System.currentTimeMillis());
                this.opponentScore = 0;
                this.opponentLines = 0;
            }
            setBackground(new Color(40, 43, 46));
            setPreferredSize(new Dimension(144, 240));
            setMinimumSize(new Dimension(144, 240));
            setMaximumSize(new Dimension(144, 240));
            setBorder(BorderFactory.createLineBorder(new Color(70, 73, 76), 1));
        }

        @Override
        protected void paintComponent(Graphics g) {
            super.paintComponent(g);
            int w = getWidth();
            int cellSz = 9; // mini cell size
            int boardW = COLS * cellSz;
            int boardH = ROWS * cellSz;
            int ox = (w - boardW) / 2;
            int oy = 20;

            // 名字
            g.setColor(Color.WHITE);
            g.setFont(new Font("Microsoft YaHei", Font.BOLD, 11));
            String label = playerName;
            if (isBot) label += " Lv." + (botDifficulty + 1);
            FontMetrics fm = g.getFontMetrics();
            g.drawString(label, (w - fm.stringWidth(label)) / 2, 14);

            // 迷你棋盘
            g.setColor(Color.BLACK);
            g.fillRect(ox, oy, boardW, boardH);

            if (isBot && botBoard != null) {
                // 画机器人已锁定的方块
                for (int r = 0; r < ROWS; r++) {
                    for (int c = 0; c < COLS; c++) {
                        if (botBoard[r][c] > 0) {
                            g.setColor(COLORS[botBoard[r][c] - 1]);
                            g.fillRect(ox + c * cellSz + 1, oy + r * cellSz + 1, cellSz - 2, cellSz - 2);
                        }
                    }
                }
                // 画机器人当前方块
                if (botCurShape != null && !opponentFinished && !opponentFailed) {
                    g.setColor(botCurColor != null ? botCurColor : Color.WHITE);
                    for (int r = 0; r < botCurShape.length; r++) {
                        for (int c = 0; c < botCurShape[r].length; c++) {
                            if (botCurShape[r][c] == 1) {
                                int px = ox + (botCurX + c) * cellSz + 1;
                                int py = oy + (botCurY + r) * cellSz + 1;
                                g.fillRect(px, py, cellSz - 2, cellSz - 2);
                            }
                        }
                    }
                }
            }

            // 状态
            int sy = oy + boardH + 4;
            g.setColor(GOLD);
            g.setFont(new Font("Microsoft YaHei", Font.PLAIN, 10));
            if (opponentFinished && !opponentFailed) {
                g.drawString("已完成", (w - 30) / 2, sy + 10);
            } else if (opponentFailed) {
                g.setColor(new Color(240, 80, 80));
                g.drawString("已淘汰", (w - 30) / 2, sy + 10);
            } else {
                g.drawString("游戏中", (w - 30) / 2, sy + 10);
            }
            String scoreStr = "分:" + opponentScore + " 行:" + opponentLines;
            g.setColor(new Color(180, 183, 186));
            g.drawString(scoreStr, (w - fm.stringWidth(scoreStr)) / 2 + 5, sy + 22);
        }
    }

    public TetrisDuelGame(String username, int roomId, String mode,
                          java.util.List<String> allPlayers, JFrame parentRoom) {
        this.username = username;
        this.roomId = roomId;
        this.mode = mode;
        this.allPlayers = new ArrayList<>(allPlayers);
        this.parentRoom = parentRoom;

        activeGames.put(roomId, this);

        rand = new Random();
        board = new int[ROWS][COLS];
        score = 0;
        lines = 0;
        gameOver = false;
        paused = false;
        localFinished = false;
        iFinished = false;
        iFailed = false;

        setTitle("俄罗斯方块对决 - " + mode + "模式");
        setDefaultCloseOperation(JFrame.DISPOSE_ON_CLOSE);
        addWindowListener(new WindowAdapter() {
            public void windowClosing(WindowEvent e) {
                cleanup();
                if (parentRoom != null && parentRoom.isDisplayable()) {
                    parentRoom.setVisible(true);
                } else {
                    FishGrabbingHome home = FishGrabbingHome.getActiveInstance();
                    if (home != null) {
                        home.setVisible(true);
                    } else {
                        System.exit(0);
                    }
                }
            }
        });
        setResizable(false);

        buildUI();
        spawnPiece();
        updateInfo();
        pack();
        setLocationRelativeTo(null);

        startTime = System.currentTimeMillis();
        timer = new javax.swing.Timer(500, e -> moveDown());
        timer.start();

        // 启动机器人模拟
        startBotSimulations();

        addKeyListener(new KeyAdapter() {
            public void keyPressed(KeyEvent e) {
                if (gameOver || localFinished) return;
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

        gamePanel.addMouseListener(new MouseAdapter() {
            public void mousePressed(MouseEvent e) {
                if (gameOver || localFinished) return;
                drop();
            }
        });

        setFocusable(true);
        requestFocusInWindow();
    }

    private JPanel gamePanel, previewPanel, infoPanel;
    private JLabel scoreLabel, linesLabel, statusLabel, rankLabel;
    private JPanel opponentWrap;

    private void buildUI() {
        JPanel main = new JPanel(new BorderLayout());
        main.setBackground(BG);

        // 顶部标题栏
        JPanel header = new JPanel(new BorderLayout());
        header.setBackground(BG);
        header.setBorder(BorderFactory.createEmptyBorder(8, 10, 8, 10));
        JLabel title = new JLabel("俄罗斯方块对决 · " + mode);
        title.setFont(new Font("Microsoft YaHei", Font.BOLD, 16));
        title.setForeground(Color.WHITE);
        header.add(title, BorderLayout.WEST);

        statusLabel = new JLabel("游戏中");
        statusLabel.setFont(new Font("Microsoft YaHei", Font.BOLD, 13));
        statusLabel.setForeground(new Color(100, 255, 100));
        header.add(statusLabel, BorderLayout.EAST);
        main.add(header, BorderLayout.NORTH);

        // 游戏画布
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
                if (curShape != null && !gameOver && !localFinished && !"困难".equals(mode)) {
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
                if (localFinished) {
                    g.setColor(new Color(0, 0, 0, 150));
                    g.fillRect(0, 0, COLS * CELL, ROWS * CELL);
                    g.setColor(Color.WHITE);
                    g.setFont(new Font("Microsoft YaHei", Font.BOLD, 22));
                    String msg = iFailed ? "已淘汰" : "已完成";
                    FontMetrics fm = g.getFontMetrics();
                    g.drawString(msg, (COLS * CELL - fm.stringWidth(msg)) / 2, ROWS * CELL / 2);
                }
            }
        };
        gamePanel.setPreferredSize(new Dimension(COLS * CELL, ROWS * CELL));
        gamePanel.setBackground(Color.BLACK);
        main.add(gamePanel, BorderLayout.CENTER);

        // 右侧面板
        JPanel rightPanel = new JPanel();
        rightPanel.setBackground(BG);
        rightPanel.setLayout(new BoxLayout(rightPanel, BoxLayout.Y_AXIS));
        rightPanel.setBorder(BorderFactory.createEmptyBorder(8, 8, 8, 8));
        rightPanel.setPreferredSize(new Dimension(170, ROWS * CELL));

        // 分数/行数
        JLabel infoTitle = new JLabel("我的信息");
        infoTitle.setFont(new Font("Microsoft YaHei", Font.BOLD, 13));
        infoTitle.setForeground(Color.WHITE);
        infoTitle.setAlignmentX(Component.CENTER_ALIGNMENT);
        rightPanel.add(infoTitle);
        rightPanel.add(Box.createVerticalStrut(6));

        scoreLabel = new JLabel("分数: 0");
        scoreLabel.setFont(new Font("Microsoft YaHei", Font.BOLD, 13));
        scoreLabel.setForeground(GOLD);
        scoreLabel.setAlignmentX(Component.CENTER_ALIGNMENT);
        rightPanel.add(scoreLabel);
        rightPanel.add(Box.createVerticalStrut(4));

        linesLabel = new JLabel("行数: 0");
        linesLabel.setFont(new Font("Microsoft YaHei", Font.BOLD, 13));
        linesLabel.setForeground(new Color(100, 255, 100));
        linesLabel.setAlignmentX(Component.CENTER_ALIGNMENT);
        rightPanel.add(linesLabel);
        rightPanel.add(Box.createVerticalStrut(4));

        rankLabel = new JLabel("");
        rankLabel.setFont(new Font("Microsoft YaHei", Font.PLAIN, 12));
        rankLabel.setForeground(new Color(180, 183, 186));
        rankLabel.setAlignmentX(Component.CENTER_ALIGNMENT);
        rightPanel.add(rankLabel);
        rightPanel.add(Box.createVerticalStrut(8));

        // 下一个方块预览
        previewPanel = new JPanel() {
            private final int PREVIEW_CELL = 18;
            protected void paintComponent(Graphics g) {
                super.paintComponent(g);
                if (nextShape == null) return;
                g.setColor(Color.WHITE);
                g.setFont(new Font("Microsoft YaHei", Font.BOLD, 11));
                g.drawString("下一个", 8, 14);
                int rows = nextShape.length;
                int cols = nextShape[0].length;
                int ox = (getWidth() - cols * PREVIEW_CELL) / 2;
                int oy = 20;
                g.setColor(nextColor != null ? nextColor : Color.WHITE);
                for (int r = 0; r < rows; r++) {
                    for (int c = 0; c < cols; c++) {
                        if (nextShape[r][c] == 1) {
                            g.fillRect(ox + c * PREVIEW_CELL + 1, oy + r * PREVIEW_CELL + 1,
                                       PREVIEW_CELL - 2, PREVIEW_CELL - 2);
                        }
                    }
                }
            }
        };
        previewPanel.setBackground(new Color(40, 43, 46));
        previewPanel.setPreferredSize(new Dimension(150, 80));
        previewPanel.setMaximumSize(new Dimension(150, 80));
        previewPanel.setAlignmentX(Component.CENTER_ALIGNMENT);
        rightPanel.add(previewPanel);
        rightPanel.add(Box.createVerticalStrut(8));

        // 对手面板
        JLabel oppTitle = new JLabel("对手状态");
        oppTitle.setFont(new Font("Microsoft YaHei", Font.BOLD, 12));
        oppTitle.setForeground(Color.WHITE);
        oppTitle.setAlignmentX(Component.CENTER_ALIGNMENT);
        rightPanel.add(oppTitle);
        rightPanel.add(Box.createVerticalStrut(4));

        opponentWrap = new JPanel();
        opponentWrap.setBackground(BG);
        opponentWrap.setLayout(new BoxLayout(opponentWrap, BoxLayout.Y_AXIS));
        JScrollPane oppScroll = new JScrollPane(opponentWrap);
        oppScroll.setBorder(null);
        oppScroll.getVerticalScrollBar().setUnitIncrement(16);
        oppScroll.setPreferredSize(new Dimension(160, 220));
        oppScroll.setMaximumSize(new Dimension(160, 220));
        oppScroll.setAlignmentX(Component.CENTER_ALIGNMENT);
        rightPanel.add(oppScroll);
        rightPanel.add(Box.createVerticalStrut(8));

        // 按钮
        JButton exitBtn = txtBtn("退出对决", Color.WHITE, BTN_BASE, 90, 30);
        exitBtn.setAlignmentX(Component.CENTER_ALIGNMENT);
        exitBtn.addMouseListener(new MouseAdapter() {
            public void mouseEntered(MouseEvent e) { exitBtn.setBackground(BTN_HOVER); }
            public void mouseExited(MouseEvent e) { exitBtn.setBackground(BTN_BASE); }
        });
        exitBtn.addActionListener(e -> {
            cleanup();
            dispose();
            if (parentRoom != null && parentRoom.isDisplayable()) {
                parentRoom.setVisible(true);
            }
        });
        rightPanel.add(exitBtn);

        main.add(rightPanel, BorderLayout.EAST);
        getContentPane().add(main);

        // 创建对手卡片
        for (String p : allPlayers) {
            if (p.equals(username)) continue;
            boolean isBot = p.startsWith("机器人");
            int diff = isBot ? getBotDifficulty(p) : 0;
            OpponentCard card = new OpponentCard(p, isBot, diff);
            opponentCards.put(p, card);
            opponentWrap.add(card);
            opponentWrap.add(Box.createVerticalStrut(4));
        }
    }

    private int getBotDifficulty(String botName) {
        // 根据机器人名字哈希分配难度
        int h = Math.abs(botName.hashCode());
        // 人数不同分配不同难度，让对局更有层次感
        java.util.List<String> botNames = new ArrayList<>();
        for (String p : allPlayers) {
            if (p.startsWith("机器人")) botNames.add(p);
        }
        int idx = botNames.indexOf(botName);
        if (idx < 0) idx = 0;
        switch (botNames.size()) {
            case 1: return 1; // 中级
            case 2: return idx; // 初级、高级
            default: return Math.min(idx, 2); // 0,1,2
        }
    }

    // ========== 俄罗斯方块核心逻辑 ==========

    private void spawnPiece() {
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

        nextType = rand.nextInt(SHAPES.length);
        nextShape = new int[SHAPES[nextType].length][];
        for (int i = 0; i < SHAPES[nextType].length; i++) {
            nextShape[i] = SHAPES[nextType][i].clone();
        }
        nextColor = COLORS[nextType];

        curX = COLS / 2 - curShape[0].length / 2;
        curY = 0;
        if (!canPlace(curShape, curX, curY, board)) {
            handlePlayerFailed();
        }
        previewPanel.repaint();
    }

    private boolean canPlace(int[][] shape, int x, int y, int[][] targetBoard) {
        for (int r = 0; r < shape.length; r++) {
            for (int c = 0; c < shape[r].length; c++) {
                if (shape[r][c] == 1) {
                    int nx = x + c, ny = y + r;
                    if (nx < 0 || nx >= COLS || ny >= ROWS) return false;
                    if (ny >= 0 && targetBoard[ny][nx] != 0) return false;
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
        int cleared = clearLines(board);
        score += 1;
        if (cleared > 0) score += cleared * 5;
        lines += cleared;
        spawnPiece();
        updateInfo();
        repaint();
    }

    private int clearLines(int[][] targetBoard) {
        int cleared = 0;
        for (int r = ROWS - 1; r >= 0; r--) {
            boolean full = true;
            for (int c = 0; c < COLS; c++) {
                if (targetBoard[r][c] == 0) { full = false; break; }
            }
            if (full) {
                cleared++;
                for (int rr = r; rr > 0; rr--) {
                    System.arraycopy(targetBoard[rr - 1], 0, targetBoard[rr], 0, COLS);
                }
                Arrays.fill(targetBoard[0], 0);
                r++;
            }
        }
        return cleared;
    }

    private void moveDown() {
        if (gameOver || paused || localFinished) return;
        if (canPlace(curShape, curX, curY + 1, board)) {
            curY++;
        } else {
            lockPiece();
        }
        repaint();
    }

    private void moveLeft() {
        if (canPlace(curShape, curX - 1, curY, board)) curX--;
        repaint();
    }

    private void moveRight() {
        if (canPlace(curShape, curX + 1, curY, board)) curX++;
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
        if (canPlace(rotated, curX, curY, board)) {
            curShape = rotated;
        }
        repaint();
    }

    private void drop() {
        while (canPlace(curShape, curX, curY + 1, board)) curY++;
        lockPiece();
        repaint();
    }

    private void togglePause() {
        paused = !paused;
        if (paused) { timer.stop(); statusLabel.setText("暂停"); }
        else { timer.start(); statusLabel.setText("游戏中"); }
    }

    private void updateInfo() {
        scoreLabel.setText("分数: " + score);
        linesLabel.setText("行数: " + lines);
    }

    // ========== 玩家结束处理 ==========

    private void handlePlayerFailed() {
        if (localFinished) return;
        localFinished = true;
        iFailed = true;
        myFinishTime = System.currentTimeMillis() - startTime;
        if (timer != null) timer.stop();
        gameOver = true;
        statusLabel.setText("已淘汰!");
        statusLabel.setForeground(new Color(240, 80, 80));
        repaint();
        sendResult("FAIL");
        // 若只剩机器人，立即结算，避免玩家长时间等待
        if (allOpponentsAreBots()) {
            finishBotsImmediately();
        } else {
            speedUpBotsForSpectate();
        }
        // 保险：8 秒内若未正常结算，强制结束
        startForceEndInsurance();
    }

    private void sendResult(String result) {
        new Thread(() -> {
            try {
                String resp = ServerClient.duelGameResult(roomId, username, result, myFinishTime, score);
                if (resp.contains("ALL_DONE")) {
                    SwingUtilities.invokeLater(this::handleGameOver);
                } else {
                    startResultPoll();
                }
            } catch (Exception e) {
                e.printStackTrace();
            }
        }).start();
    }

    /** 剩余对手是否全是机器人（基于实际显示的对手卡片判断，更可靠） */
    private boolean allOpponentsAreBots() {
        if (opponentCards.isEmpty()) return false;
        for (OpponentCard card : opponentCards.values()) {
            if (!card.isBot) return false;
        }
        return true;
    }

    /** 玩家结束后若只剩机器人，立即按当前分数结算（全部 FAIL，服务端按分数排名） */
    private void finishBotsImmediately() {
        if (!allOpponentsAreBots()) return;
        for (OpponentCard card : opponentCards.values()) {
            if (card.opponentFinished || card.opponentFailed) continue;
            card.opponentFinished = true;
            card.opponentFailed = true;
            if (card.botTimer != null) card.botTimer.stop();
            sendBotResult(card.playerName, "FAIL", card.opponentScore);
        }
        opponentWrap.repaint();
    }

    /** 8 秒保险：玩家结束后若游戏仍未全局结束，强制结算所有机器人并触发结束 */
    private void startForceEndInsurance() {
        if (forceEndTimer != null) forceEndTimer.stop();
        forceEndTimer = new javax.swing.Timer(8000, e -> {
            forceEndTimer.stop();
            if (!globalFinished) {
                finishBotsImmediately();
                handleGameOver();
            }
        });
        forceEndTimer.setRepeats(false);
        forceEndTimer.start();
    }

    /** 玩家淘汰后仍有真人对手时，加速机器人落子便于观战结束 */
    private void speedUpBotsForSpectate() {
        for (OpponentCard card : opponentCards.values()) {
            if (!card.isBot || card.opponentFinished || card.opponentFailed) continue;
            if (card.botTimer != null) {
                card.botTimer.setDelay(350);
                card.botTimer.setInitialDelay(350);
            }
        }
    }

    private void startResultPoll() {
        if (resultPollTimer != null) resultPollTimer.stop();
        resultPollTimer = new javax.swing.Timer(1500, e -> {
            new Thread(() -> {
                try {
                    String resp = ServerClient.duelGameResults(roomId);
                    if (resp.contains("ALL_DONE")) {
                        SwingUtilities.invokeLater(this::handleGameOver);
                    }
                } catch (Exception ignored) {}
            }).start();
        });
        resultPollTimer.start();
    }

    private void handleGameOver() {
        if (globalFinished) return;
        globalFinished = true;
        if (forceEndTimer != null) forceEndTimer.stop();
        if (resultPollTimer != null) resultPollTimer.stop();
        // 停止所有机器人
        for (OpponentCard card : opponentCards.values()) {
            if (card.botTimer != null) card.botTimer.stop();
        }
        if (timer != null) timer.stop();

        // 获取最终结果
        new Thread(() -> {
            try {
                String resp = ServerClient.duelGameResults(roomId);
                SwingUtilities.invokeLater(() -> showResultDialog(resp));
            } catch (Exception e) {
                SwingUtilities.invokeLater(() -> showResultDialog(""));
            }
        }).start();
    }

    private void showResultDialog(String resp) {
        // 解析结果
        java.util.List<String[]> results = new ArrayList<>();
        if (resp.contains("ALL_DONE")) {
            String data = resp.substring(resp.indexOf("ALL_DONE") + 9);
            if (data.startsWith("|")) data = data.substring(1);
            for (String entry : data.split(";")) {
                String[] parts = entry.split(",");
                if (parts.length >= 2) {
                    String name = parts[0];
                    String[] rp = parts[1].split(":");
                    String wr = rp.length >= 1 ? rp[0] : "FAIL";
                    long t = 0;
                    int sc = 0;
                    try { if (rp.length >= 2) t = Long.parseLong(rp[1]); } catch (Exception ignored) {}
                    try { if (rp.length >= 3) sc = Integer.parseInt(rp[2]); } catch (Exception ignored) {}
                    results.add(new String[]{name, wr, String.valueOf(t), String.valueOf(sc)});
                }
            }
        }

        // 判断自己是否赢了（服务端已按分数判定 WIN）
        boolean iWin = false;
        if (!results.isEmpty()) {
            for (String[] r : results) {
                if ("WIN".equals(r[1]) && r[0].equals(username)) {
                    iWin = true;
                    break;
                }
            }
        }

        JDialog d = new JDialog(this, "对局结束", true);
        d.setResizable(false);
        d.setLayout(new BorderLayout());

        JPanel c = new JPanel();
        c.setBackground(new Color(60, 63, 65));
        c.setLayout(new BoxLayout(c, BoxLayout.Y_AXIS));
        c.setBorder(BorderFactory.createEmptyBorder(20, 30, 20, 30));

        // 结果标题
        String titleText = iWin ? "YOU WIN!" : (iFailed ? "YOU LOSE!" : "游戏结束");
        Color titleColor = iWin ? new Color(100, 255, 100) : (iFailed ? new Color(240, 80, 80) : GOLD);
        JLabel resTitle = new JLabel(titleText);
        resTitle.setFont(new Font("Microsoft YaHei", Font.BOLD, 26));
        resTitle.setForeground(titleColor);
        resTitle.setAlignmentX(Component.CENTER_ALIGNMENT);
        c.add(resTitle);
        c.add(Box.createVerticalStrut(15));

        // 排名
        JLabel rankTitle = new JLabel("最终排名");
        rankTitle.setFont(new Font("Microsoft YaHei", Font.BOLD, 15));
        rankTitle.setForeground(Color.WHITE);
        rankTitle.setAlignmentX(Component.CENTER_ALIGNMENT);
        c.add(rankTitle);
        c.add(Box.createVerticalStrut(8));

        // 排序：WIN在前，同组内按分数降序
        results.sort((a, b) -> {
            if ("WIN".equals(a[1]) && "FAIL".equals(b[1])) return -1;
            if ("FAIL".equals(a[1]) && "WIN".equals(b[1])) return 1;
            int sa = Integer.parseInt(a[3]);
            int sb = Integer.parseInt(b[3]);
            return Integer.compare(sb, sa);
        });

        for (int i = 0; i < results.size(); i++) {
            String[] r = results.get(i);
            String rank = (i + 1) + ".";
            String line = rank + "  " + r[0] + "  分数:" + r[3] + "  " + ("WIN".equals(r[1]) ? "胜利" : "淘汰");
            JLabel ll = new JLabel(line);
            ll.setFont(new Font("Microsoft YaHei", Font.PLAIN, 14));
            ll.setForeground(r[0].equals(username) ? GOLD : Color.WHITE);
            ll.setAlignmentX(Component.CENTER_ALIGNMENT);
            c.add(ll);
        }

        d.add(c, BorderLayout.CENTER);

        JPanel bp = new JPanel(new FlowLayout(FlowLayout.CENTER, 15, 10));
        bp.setBackground(new Color(60, 63, 65));

        JButton homeBtn = new JButton("回到主页");
        homeBtn.setFont(new Font("Microsoft YaHei", Font.BOLD, 13));
        homeBtn.setForeground(Color.WHITE);
        homeBtn.setBackground(ACCENT);
        homeBtn.setFocusPainted(false);
        homeBtn.addActionListener(e -> {
            d.dispose();
            dispose();
            // 回到俄罗斯方块主页（通过匹配房间间接获取 TetrisHome 引用）
            JFrame tetrisHome = null;
            if (parentRoom instanceof TetrisMatchRoom) {
                tetrisHome = ((TetrisMatchRoom) parentRoom).getParentHome();
            }
            if (tetrisHome == null) tetrisHome = TetrisMatchRoom.getGlobalTetrisHome();
            if (tetrisHome != null && tetrisHome.isDisplayable()) {
                tetrisHome.setVisible(true);
                tetrisHome.setLocationRelativeTo(null);
            } else {
                FishGrabbingHome.showActiveInstance();
            }
        });
        bp.add(homeBtn);

        JButton roomBtn = new JButton("回到房间");
        roomBtn.setFont(new Font("Microsoft YaHei", Font.BOLD, 13));
        roomBtn.setForeground(Color.WHITE);
        roomBtn.setBackground(new Color(80, 83, 86));
        roomBtn.setFocusPainted(false);
        roomBtn.addActionListener(e -> {
            d.dispose();
            dispose();
            if (parentRoom != null && parentRoom.isDisplayable()) {
                if (parentRoom instanceof TetrisMatchRoom) {
                    ((TetrisMatchRoom) parentRoom).resetForNewGame();
                }
                parentRoom.setVisible(true);
            }
        });
        bp.add(roomBtn);

        d.add(bp, BorderLayout.SOUTH);
        d.pack();
        d.setLocationRelativeTo(this);
        d.setVisible(true);
    }

    // ========== 机器人AI ==========

    private void startBotSimulations() {
        for (OpponentCard card : opponentCards.values()) {
            if (!card.isBot) continue;

            // 初始延迟
            int initDelay = 2000 + new Random().nextInt(2500);
            int interval;
            switch (card.botDifficulty) {
                case 0: interval = 2500 + new Random().nextInt(2000); break;  // 初级 2.5-4.5s
                case 1: interval = 1800 + new Random().nextInt(1400); break;  // 中级 1.8-3.2s
                default: interval = 1200 + new Random().nextInt(1000); break; // 高级 1.2-2.2s
            }

            // 生成机器人第一个方块
            botSpawnPiece(card);

            card.botTimer = new javax.swing.Timer(interval, null);
            final OpponentCard fCard = card;
            card.botTimer.addActionListener(e -> {
                if (fCard.opponentFinished || fCard.opponentFailed) {
                    fCard.botTimer.stop();
                    return;
                }
                botMove(fCard);
                opponentWrap.repaint();
            });
            card.botTimer.setInitialDelay(initDelay);
            card.botTimer.start();

            opponentCards.put(card.playerName, card);
        }
    }

    private void botSpawnPiece(OpponentCard card) {
        card.botCurType = card.botRand.nextInt(SHAPES.length);
        card.botCurShape = cloneShape(SHAPES[card.botCurType]);
        card.botCurColor = COLORS[card.botCurType];
        card.botCurX = COLS / 2 - card.botCurShape[0].length / 2;
        card.botCurY = 0;

        if (!canPlace(card.botCurShape, card.botCurX, card.botCurY, card.botBoard)) {
            // 机器人失败了
            card.opponentFailed = true;
            card.opponentFinished = true;
            if (card.botTimer != null) card.botTimer.stop();
            sendBotResult(card.playerName, "FAIL", card.opponentScore);
        }
    }

    private void botMove(OpponentCard card) {
        if (card.opponentFinished || card.opponentFailed) return;

        // 选择最佳位置放置
        botPlaceBest(card);

        // 锁住方块
        for (int r = 0; r < card.botCurShape.length; r++) {
            for (int c = 0; c < card.botCurShape[r].length; c++) {
                if (card.botCurShape[r][c] == 1) {
                    int ny = card.botCurY + r, nx = card.botCurX + c;
                    if (ny >= 0 && ny < ROWS && nx >= 0 && nx < COLS) {
                        card.botBoard[ny][nx] = card.botCurType + 1;
                    }
                }
            }
        }

        int cleared = clearLines(card.botBoard);
        card.botMoveCount++;
        card.opponentScore += 1;
        if (cleared > 0) card.opponentScore += cleared * 5;
        card.opponentLines += cleared;

        botSpawnPiece(card);
        opponentWrap.repaint();
    }

    private void botPlaceBest(OpponentCard card) {
        int bestX = card.botCurX;
        int bestRot = 0;
        int bestScore = Integer.MIN_VALUE;
        int[][] bestShape = cloneShape(card.botCurShape);

        // 尝试所有旋转和水平位置
        for (int rot = 0; rot < 4; rot++) {
            int[][] shape = cloneShape(card.botCurShape);
            for (int r = 0; r < rot; r++) shape = rotateShape(shape);

            for (int x = -2; x <= COLS; x++) {
                if (!canPlace(shape, x, 0, card.botBoard)) continue;

                // 找到能下落的最低位置
                int y = 0;
                while (canPlace(shape, x, y + 1, card.botBoard)) y++;

                // 评估这个位置
                int sc = evaluatePlacement(card.botBoard, shape, x, y, card.botDifficulty);
                if (sc > bestScore) {
                    bestScore = sc;
                    bestX = x;
                    bestRot = rot;
                    bestShape = shape;
                }
            }
        }

        // 应用最佳位置
        for (int r = 0; r < bestRot; r++) {
            card.botCurShape = rotateShape(card.botCurShape);
        }
        card.botCurX = bestX;
        // 下落到底
        while (canPlace(card.botCurShape, card.botCurX, card.botCurY + 1, card.botBoard)) {
            card.botCurY++;
        }
    }

    private int evaluatePlacement(int[][] targetBoard, int[][] shape, int x, int y, int difficulty) {
        // 创建临时棋盘进行评估
        int[][] tempBoard = new int[ROWS][COLS];
        for (int r = 0; r < ROWS; r++) {
            System.arraycopy(targetBoard[r], 0, tempBoard[r], 0, COLS);
        }
        // 放置方块
        int colorIdx = 1; // 占位颜色
        for (int r = 0; r < shape.length; r++) {
            for (int c = 0; c < shape[r].length; c++) {
                if (shape[r][c] == 1) {
                    int ny = y + r, nx = x + c;
                    if (ny >= 0 && ny < ROWS && nx >= 0 && nx < COLS) {
                        tempBoard[ny][nx] = colorIdx;
                    }
                }
            }
        }

        int score = 0;

        // 高度惩罚（越低越好）
        int maxH = 0;
        for (int c = 0; c < COLS; c++) {
            for (int r = 0; r < ROWS; r++) {
                if (tempBoard[r][c] > 0) {
                    int h = ROWS - r;
                    if (h > maxH) maxH = h;
                    break;
                }
            }
        }
        score -= maxH * 10;

        // 消除行奖励
        int cleared = 0;
        for (int r = ROWS - 1; r >= 0; r--) {
            boolean full = true;
            for (int c = 0; c < COLS; c++) {
                if (tempBoard[r][c] == 0) { full = false; break; }
            }
            if (full) cleared++;
        }
        score += cleared * 50;

        // 空洞惩罚（越高难度越重视）
        int holes = 0;
        for (int c = 0; c < COLS; c++) {
            boolean foundBlock = false;
            for (int r = 0; r < ROWS; r++) {
                if (tempBoard[r][c] > 0) foundBlock = true;
                else if (foundBlock) holes++;
            }
        }
        score -= holes * (5 + difficulty * 5);

        // 平整度惩罚（越平整越好）
        int bumpiness = 0;
        int[] heights = new int[COLS];
        for (int c = 0; c < COLS; c++) {
            heights[c] = 0;
            for (int r = 0; r < ROWS; r++) {
                if (tempBoard[r][c] > 0) {
                    heights[c] = ROWS - r;
                    break;
                }
            }
        }
        for (int c = 0; c < COLS - 1; c++) {
            bumpiness += Math.abs(heights[c] - heights[c + 1]);
        }
        score -= bumpiness * 3;

        // 根据难度添加随机噪声（低级机器人更随机）
        if (difficulty == 0) {
            score += new Random().nextInt(200) - 100; // 初级：大噪声
        } else if (difficulty == 1) {
            score += new Random().nextInt(60) - 30;   // 中级：小噪声
        }
        // 高级：无噪声，纯策略

        return score;
    }

    private int[][] rotateShape(int[][] shape) {
        int rows = shape.length, cols = shape[0].length;
        int[][] rotated = new int[cols][rows];
        for (int r = 0; r < rows; r++) {
            for (int c = 0; c < cols; c++) {
                rotated[c][rows - 1 - r] = shape[r][c];
            }
        }
        return rotated;
    }

    private int[][] cloneShape(int[][] shape) {
        int[][] clone = new int[shape.length][];
        for (int i = 0; i < shape.length; i++) {
            clone[i] = shape[i].clone();
        }
        return clone;
    }

    private void sendBotResult(String botName, String result, int botScore) {
        long botTime = System.currentTimeMillis() - startTime;
        new Thread(() -> {
            try {
                ServerClient.duelGameResult(roomId, botName, result, botTime, botScore);
            } catch (Exception ignored) {}
        }).start();
    }

    // ========== 推送接收入口 ==========

    /** 接收来自 MessageCenter 的游戏结束推送 */
    public static void receiveGameOver(int roomId, String resultsData) {
        TetrisDuelGame game = activeGames.get(roomId);
        if (game != null) {
            SwingUtilities.invokeLater(() -> game.handleGameOver());
        }
    }

    // ========== 辅助方法 ==========

    private void cleanup() {
        if (timer != null) timer.stop();
        if (resultPollTimer != null) resultPollTimer.stop();
        if (forceEndTimer != null) forceEndTimer.stop();
        for (OpponentCard card : opponentCards.values()) {
            if (card.botTimer != null) card.botTimer.stop();
        }
        activeGames.remove(roomId);
    }

    private JButton txtBtn(String text, Color fg, Color bg, int w, int h) {
        JButton b = new JButton(text);
        b.setFont(new Font("Microsoft YaHei", Font.BOLD, 12));
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
