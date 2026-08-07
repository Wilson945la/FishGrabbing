import javax.swing.*;
import java.awt.*;
import java.awt.event.*;
import java.util.*;

/**
 * 俄罗斯方块叠叠乐对决 — 方块自动左右移动，按空格下落，重心判定胜负
 */
public class TetrisStackDuelGame extends JFrame {

    private static final Color BG = new Color(50, 53, 56);
    private static final Color BTN_BASE = new Color(80, 83, 86);
    private static final Color BTN_HOVER = new Color(0, 120, 215);
    private static final int COLS = 10;
    private static final int ROWS = 20;      // 可视行数
    private static final int BOARD_ROWS = 400;
    private static final int BOARD_BASE = 200;
    private static final int CELL = 28;

    // w2b: 世界行 → board索引
    private static int w2b(int wr) { return wr + BOARD_BASE; }

    private static final int[][] BLOCK_SIZES = {
        {2,1}, {3,1}, {4,1}, {2,2}, {1,1}, {3,2}, {1,2}
    };
    private static final Color[] BLOCK_COLORS = {
        new Color(0,240,240), new Color(240,240,0), new Color(160,0,240),
        new Color(0,0,240), new Color(240,160,0), new Color(0,240,0), new Color(240,0,0)
    };

    // 游戏状态
    private int[][] board;
    private int curType, curX, curY, curW, curH;
    private Color curColor;
    private int moveDir, frameCount, score, viewOffset;
    private boolean gameOver, paused;
    private java.util.List<PlacedBlock> placedBlocks = new ArrayList<>();
    private java.util.Timer gameTimer;
    private Random rand;

    // 对决状态
    private String username;
    private int roomId;
    private String mode;
    private java.util.List<String> allPlayers;
    private JFrame parentRoom;

    // 对手面板
    private Map<String, OpponentCard> opponentCards = new LinkedHashMap<>();
    private JPanel opponentWrap;

    // 结果相关
    private boolean localFinished = false;
    private boolean iFailed = false;
    private boolean globalFinished = false;
    private long myFinishTime = 0;
    private long startTime;
    private javax.swing.Timer resultPollTimer;
    private javax.swing.Timer forceEndTimer;

    // UI
    private JPanel gamePanel;
    private JLabel scoreLabel, statusLabel;

    private static final Map<Integer, TetrisStackDuelGame> activeGames = new java.util.concurrent.ConcurrentHashMap<>();
    private static final Color GOLD = new Color(255, 215, 0);

    /** 已放置方块 */
    static class PlacedBlock {
        int bx, by, bw, bh, type;
        PlacedBlock(int bx, int by, int bw, int bh, int type) {
            this.bx = bx; this.by = by; this.bw = bw; this.bh = bh; this.type = type;
        }
    }

    /** 对手卡片 */
    class OpponentCard extends JPanel {
        String playerName;
        boolean isBot, opponentFinished, opponentFailed;
        int opponentScore;
        int[][] botBoard;
        java.util.List<PlacedBlock> botBlocks = new ArrayList<>();
        Random botRand;
        int botDifficulty;
        int botCurType, botCurW, botCurH, botCurX;
        Color botCurColor;
        int botFrameCount = 0;
        javax.swing.Timer botTimer;
        int botScore = 0;

        OpponentCard(String name, boolean isBot, int difficulty) {
            this.playerName = name;
            this.isBot = isBot;
            this.botDifficulty = difficulty;
            if (isBot) {
                this.botBoard = new int[BOARD_ROWS][COLS];
                this.botRand = new Random(name.hashCode() * 31 + System.currentTimeMillis());
            }
            setBackground(new Color(40, 43, 46));
            setPreferredSize(new Dimension(120, 240));
            setMinimumSize(new Dimension(120, 240));
            setMaximumSize(new Dimension(120, 240));
            setBorder(BorderFactory.createLineBorder(new Color(70, 73, 76), 1));
        }

        protected void paintComponent(Graphics g) {
            super.paintComponent(g);
            int w = getWidth(), cell = 9;
            int ox = (w - COLS * cell) / 2 + 5;

            // 姓名 + 分数
            g.setFont(new Font("Microsoft YaHei", Font.BOLD, 11));
            FontMetrics fm = g.getFontMetrics();
            g.setColor(Color.WHITE);
            g.drawString(playerName, (w - fm.stringWidth(playerName)) / 2 + 5, 16);

            // 迷你棋盘
            g.setColor(Color.BLACK);
            g.fillRect(ox, 22, COLS * cell, ROWS * cell);

            // 如果是对手自己的叠叠乐棋盘
            if (isBot && botBlocks != null) {
                for (PlacedBlock pb : botBlocks) {
                    int sr = pb.by - botViewOffset();
                    if (sr < 0 || sr >= ROWS) continue;
                    g.setColor(BLOCK_COLORS[pb.type]);
                    for (int r = 0; r < pb.bh; r++)
                        for (int c = 0; c < pb.bw; c++)
                            g.fillRect(ox + (pb.bx + c) * cell + 1, 22 + (sr + r) * cell + 1,
                                    cell - 2, cell - 2);
                }
            }

            g.setColor(new Color(60, 63, 66));
            for (int r = 0; r < ROWS; r++)
                for (int c = 0; c < COLS; c++)
                    g.drawRect(ox + c * cell, 22 + r * cell, cell, cell);

            // 状态文字
            String statusStr;
            if (opponentFinished && opponentFailed) statusStr = "已淘汰";
            else if (opponentFinished) statusStr = "已完成";
            else statusStr = "游戏中";
            g.setFont(new Font("Microsoft YaHei", Font.PLAIN, 10));
            g.setColor(opponentFailed ? new Color(240, 80, 80) : new Color(100, 255, 100));
            fm = g.getFontMetrics();
            g.drawString(statusStr, (w - fm.stringWidth(statusStr)) / 2 + 5, 22 + ROWS * cell + 14);
            g.setColor(GOLD);
            String scStr = "分:" + opponentScore;
            g.drawString(scStr, (w - fm.stringWidth(scStr)) / 2 + 5, 22 + ROWS * cell + 28);
        }

        private int botViewOffset() {
            if (botBlocks.isEmpty()) return 0;
            int top = ROWS;
            for (PlacedBlock pb : botBlocks)
                if (pb.by < top) top = pb.by;
            if (top >= ROWS) return 0;
            int thresh = 10;
            if (top >= thresh) return 0;
            return top - thresh;
        }
    }

    public TetrisStackDuelGame(String username, int roomId, String mode,
                                java.util.List<String> allPlayers, JFrame parentRoom) {
        this.username = username;
        this.roomId = roomId;
        this.mode = mode;
        this.allPlayers = new ArrayList<>(allPlayers);
        this.parentRoom = parentRoom;

        activeGames.put(roomId, this);

        rand = new Random();
        board = new int[BOARD_ROWS][COLS];
        score = 0; viewOffset = 0; gameOver = false; paused = false;
        moveDir = 1; frameCount = 0;
        localFinished = false; iFailed = false; globalFinished = false;

        setTitle("俄罗斯方块对决 - 叠叠乐");
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
        // 主游戏循环
        gameTimer = new java.util.Timer();
        gameTimer.schedule(new java.util.TimerTask() {
            public void run() {
                if (!gameOver && !paused && !localFinished) {
                    frameCount++;
                    if (frameCount % getMoveSpeed() == 0)
                        SwingUtilities.invokeLater(() -> moveHorizontally());
                }
                SwingUtilities.invokeLater(() -> gamePanel.repaint());
            }
        }, 0, 16);

        // 机器人
        startBotSimulations();

        addKeyListener(new KeyAdapter() {
            public void keyPressed(KeyEvent e) {
                if (gameOver || localFinished) return;
                int k = e.getKeyCode();
                if (k == KeyEvent.VK_LEFT || k == KeyEvent.VK_A) {
                    curX = Math.max(0, curX - 1); repaint();
                } else if (k == KeyEvent.VK_RIGHT || k == KeyEvent.VK_D) {
                    curX = Math.min(COLS - curW, curX + 1); repaint();
                } else if (k == KeyEvent.VK_SPACE) {
                    dropPiece();
                } else if (k == KeyEvent.VK_P) {
                    paused = !paused;
                    statusLabel.setText(paused ? "暂停" : "游戏中");
                }
            }
        });

        gamePanel.addMouseListener(new MouseAdapter() {
            public void mousePressed(MouseEvent e) {
                if (gameOver || localFinished) return;
                dropPiece();
            }
        });
        setFocusable(true);
        requestFocusInWindow();
    }

    private void buildUI() {
        JPanel main = new JPanel(new BorderLayout());
        main.setBackground(BG);

        JPanel header = new JPanel(new BorderLayout());
        header.setBackground(BG);
        header.setBorder(BorderFactory.createEmptyBorder(8, 10, 8, 10));
        JLabel title = new JLabel("俄罗斯方块对决 · 叠叠乐");
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
                for (int r = 0; r < ROWS; r++)
                    for (int c = 0; c < COLS; c++)
                        g.drawRect(c * CELL, r * CELL, CELL, CELL);

                // 地面线
                int groundSR = (ROWS - 1) - viewOffset;
                if (groundSR >= -1 && groundSR < ROWS + 1) {
                    g.setColor(new Color(0, 180, 0));
                    g.fillRect(0, (groundSR + 1) * CELL - 2, COLS * CELL, 3);
                }

                // 已落地方块
                for (int wr = -(BOARD_BASE - 1); wr < BOARD_ROWS - BOARD_BASE; wr++) {
                    int sr = wr - viewOffset;
                    if (sr < 0 || sr >= ROWS) continue;
                    for (int c = 0; c < COLS; c++) {
                        if (board[w2b(wr)][c] > 0) {
                            g.setColor(BLOCK_COLORS[board[w2b(wr)][c] - 1]);
                            g.fillRect(c * CELL + 1, sr * CELL + 1, CELL - 2, CELL - 2);
                        }
                    }
                }

                // 当前方块 + ghost
                if (curType >= 0 && !gameOver && !localFinished) {
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

                if (localFinished) {
                    g.setColor(new Color(0,0,0,150));
                    g.fillRect(0, 0, COLS * CELL, ROWS * CELL);
                    g.setColor(Color.WHITE);
                    g.setFont(new Font("Microsoft YaHei", Font.BOLD, 22));
                    String msg = iFailed ? "失衡！淘汰" : "已完成";
                    FontMetrics fm = g.getFontMetrics();
                    g.drawString(msg, (COLS*CELL - fm.stringWidth(msg))/2, ROWS*CELL/2);
                }

                if (paused && !gameOver) {
                    g.setColor(new Color(0,0,0,100));
                    g.fillRect(0, 0, COLS*CELL, ROWS*CELL);
                    g.setColor(Color.WHITE);
                    g.setFont(new Font("Microsoft YaHei", Font.BOLD, 22));
                    FontMetrics fm = g.getFontMetrics();
                    g.drawString("暂停", (COLS*CELL - fm.stringWidth("暂停"))/2, ROWS*CELL/2);
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

        JLabel infoTitle = new JLabel("我的信息");
        infoTitle.setFont(new Font("Microsoft YaHei", Font.BOLD, 13));
        infoTitle.setForeground(Color.WHITE);
        infoTitle.setAlignmentX(Component.CENTER_ALIGNMENT);
        rightPanel.add(infoTitle);
        rightPanel.add(Box.createVerticalStrut(6));

        JLabel modeLabel = new JLabel("叠叠乐模式");
        modeLabel.setFont(new Font("Microsoft YaHei", Font.BOLD, 12));
        modeLabel.setForeground(new Color(240, 180, 0));
        modeLabel.setAlignmentX(Component.CENTER_ALIGNMENT);
        rightPanel.add(modeLabel);
        rightPanel.add(Box.createVerticalStrut(6));

        scoreLabel = new JLabel("分数: 0");
        scoreLabel.setFont(new Font("Microsoft YaHei", Font.BOLD, 13));
        scoreLabel.setForeground(GOLD);
        scoreLabel.setAlignmentX(Component.CENTER_ALIGNMENT);
        rightPanel.add(scoreLabel);
        rightPanel.add(Box.createVerticalStrut(15));

        // 对手
        JLabel oppTitle = new JLabel("对手");
        oppTitle.setFont(new Font("Microsoft YaHei", Font.BOLD, 13));
        oppTitle.setForeground(Color.WHITE);
        oppTitle.setAlignmentX(Component.CENTER_ALIGNMENT);
        rightPanel.add(oppTitle);
        rightPanel.add(Box.createVerticalStrut(6));

        opponentWrap = new JPanel();
        opponentWrap.setBackground(BG);
        opponentWrap.setLayout(new BoxLayout(opponentWrap, BoxLayout.Y_AXIS));
        rightPanel.add(opponentWrap);

        buildOpponentCards();
        rightPanel.add(Box.createVerticalGlue());

        main.add(rightPanel, BorderLayout.EAST);
        getContentPane().add(main);
    }

    // ========== 叠叠乐核心逻辑 ==========

    private void spawnPiece() {
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

    private int getMoveSpeed() {
        int n = placedBlocks.size();
        int speed = 16 - n / 5;
        return Math.max(2, speed);
    }

    private int findLandingY() {
        int topBlockBottom = ROWS;
        for (int r = -(BOARD_BASE - 1); r < ROWS; r++) {
            for (int c = 0; c < COLS; c++) {
                if (board[w2b(r)][c] > 0) { topBlockBottom = r; r = ROWS; break; }
            }
        }
        int startY = Math.min(-(BOARD_BASE - curH - 1), topBlockBottom - curH - 2);

        for (int y = startY; y <= ROWS - curH; y++) {
            boolean collision = false;
            for (int r = 0; r < curH && !collision; r++)
                for (int c = 0; c < curW && !collision; c++) {
                    int br = y + r, bc = curX + c;
                    if (br >= -BOARD_BASE && br < BOARD_ROWS - BOARD_BASE && bc >= 0 && bc < COLS && board[w2b(br)][bc] > 0)
                        collision = true;
                }
            if (collision) return y - 1;
        }
        return ROWS - curH;
    }

    private void dropPiece() {
        curY = findLandingY();

        // 规则1：必须叠在上一个方块上
        if (!placedBlocks.isEmpty()) {
            PlacedBlock last = placedBlocks.get(placedBlocks.size() - 1);
            boolean overlapsX = (curX + curW > last.bx) && (curX < last.bx + last.bw);
            boolean touchingY = (curY + curH == last.by);
            if (!overlapsX || !touchingY) { endGame(); return; }
        }

        // 规则2：逐层重心判定
        {
            PlacedBlock curBlock = new PlacedBlock(curX, curY, curW, curH, curType);
            java.util.List<PlacedBlock> all = new ArrayList<>(placedBlocks);
            all.add(curBlock);

            java.util.Set<Integer> layerLevels = new HashSet<>();
            for (PlacedBlock pb : all) layerLevels.add(pb.by + pb.bh);

            for (int layerY : layerLevels) {
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

                java.util.List<PlacedBlock> stack = new ArrayList<>();
                for (PlacedBlock pb : all)
                    if (pb.by == layerY) stack.add(pb);
                if (stack.isEmpty()) continue;

                collectAbove(all, stack);

                double tw = 0, tm = 0;
                for (PlacedBlock s : stack) {
                    double w = s.bw * s.bh;
                    tw += w; tm += w * (s.bx + s.bw / 2.0);
                }
                double cx = tm / tw;
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
    }

    private void collectAbove(java.util.List<PlacedBlock> all, java.util.List<PlacedBlock> stack) {
        java.util.List<PlacedBlock> next = new ArrayList<>();
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

    private void updateViewOffset() {
        int topRow = ROWS;
        outer: for (int r = -(BOARD_BASE - 1); r < ROWS; r++)
            for (int c = 0; c < COLS; c++) {
                if (board[w2b(r)][c] > 0) { topRow = r; break outer; }
            }
        if (topRow >= ROWS) { viewOffset = 0; return; }
        int threshold = 10;
        if (topRow >= threshold) { viewOffset = 0; return; }
        viewOffset = topRow - threshold;
    }

    private void endGame() {
        if (localFinished) return;
        localFinished = true;
        iFailed = true;
        myFinishTime = System.currentTimeMillis() - startTime;
        if (gameTimer != null) gameTimer.cancel();
        gameOver = true;
        statusLabel.setText("失衡！");
        statusLabel.setForeground(new Color(240, 80, 80));
        repaint();
        sendResult("FAIL");
        if (allOpponentsAreBots()) {
            finishBotsImmediately();
        }
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
            } catch (Exception e) { e.printStackTrace(); }
        }).start();
    }

    private boolean allOpponentsAreBots() {
        if (opponentCards.isEmpty()) return false;
        for (OpponentCard c : opponentCards.values())
            if (!c.isBot) return false;
        return true;
    }

    private void finishBotsImmediately() {
        for (OpponentCard card : opponentCards.values()) {
            if (card.opponentFinished || card.opponentFailed) continue;
            card.opponentFinished = true;
            card.opponentFailed = true;
            if (card.botTimer != null) card.botTimer.stop();
            sendBotResult(card.playerName, "FAIL", card.opponentScore);
        }
        opponentWrap.repaint();
    }

    private void sendBotResult(String botName, String result, int botScore) {
        long t = System.currentTimeMillis() - startTime;
        new Thread(() -> {
            try { ServerClient.duelGameResult(roomId, botName, result, t, botScore); } catch (Exception ignored) {}
        }).start();
    }

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
        resultPollTimer.setRepeats(false);
        resultPollTimer.start();
    }

    private void handleGameOver() {
        if (globalFinished) return;
        globalFinished = true;
        if (forceEndTimer != null) forceEndTimer.stop();
        if (resultPollTimer != null) resultPollTimer.stop();
        for (OpponentCard card : opponentCards.values())
            if (card.botTimer != null) card.botTimer.stop();

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
                    int sc = 0;
                    try { if (rp.length >= 3) sc = Integer.parseInt(rp[2]); } catch (Exception ignored) {}
                    results.add(new String[]{name, wr, String.valueOf(sc)});
                }
            }
        }

        boolean iWin = false;
        for (String[] r : results) {
            if ("WIN".equals(r[1]) && r[0].equals(username)) { iWin = true; break; }
        }

        JDialog d = new JDialog(this, "对局结束", true);
        d.setResizable(false);
        d.setLayout(new BorderLayout());

        JPanel c = new JPanel();
        c.setBackground(new Color(60, 63, 65));
        c.setLayout(new BoxLayout(c, BoxLayout.Y_AXIS));
        c.setBorder(BorderFactory.createEmptyBorder(20, 30, 20, 30));

        String titleText = iWin ? "YOU WIN!" : (iFailed ? "YOU LOSE!" : "游戏结束");
        Color titleColor = iWin ? new Color(100, 255, 100) : (iFailed ? new Color(240, 80, 80) : GOLD);
        JLabel resTitle = new JLabel(titleText);
        resTitle.setFont(new Font("Microsoft YaHei", Font.BOLD, 26));
        resTitle.setForeground(titleColor);
        resTitle.setAlignmentX(Component.CENTER_ALIGNMENT);
        c.add(resTitle);
        c.add(Box.createVerticalStrut(15));

        JLabel rankTitle = new JLabel("最终排名");
        rankTitle.setFont(new Font("Microsoft YaHei", Font.BOLD, 15));
        rankTitle.setForeground(Color.WHITE);
        rankTitle.setAlignmentX(Component.CENTER_ALIGNMENT);
        c.add(rankTitle);
        c.add(Box.createVerticalStrut(8));

        results.sort((a, b) -> {
            if ("WIN".equals(a[1]) && "FAIL".equals(b[1])) return -1;
            if ("FAIL".equals(a[1]) && "WIN".equals(b[1])) return 1;
            return Integer.compare(Integer.parseInt(b[2]), Integer.parseInt(a[2]));
        });

        for (int i = 0; i < results.size(); i++) {
            String[] r = results.get(i);
            String rank = (i + 1) + ".";
            String line = rank + "  " + r[0] + "  分数:" + r[2] + "  " + ("WIN".equals(r[1]) ? "胜利" : "淘汰");
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
        homeBtn.setBackground(new Color(0, 120, 215));
        homeBtn.setFocusPainted(false);
        homeBtn.addActionListener(e -> {
            d.dispose();
            cleanup();
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
            cleanup();
            dispose();
            if (parentRoom != null && parentRoom.isDisplayable()) {
                if (parentRoom instanceof TetrisMatchRoom) {
                    ((TetrisMatchRoom) parentRoom).resetForNewGame();
                }
                parentRoom.setVisible(true);
            } else {
                FishGrabbingHome.showActiveInstance();
            }
        });
        bp.add(roomBtn);
        d.add(bp, BorderLayout.SOUTH);
        d.pack();
        d.setLocationRelativeTo(this);
        d.setVisible(true);
    }

    private void updateInfo() { scoreLabel.setText("分数: " + score); }

    private void buildOpponentCards() {
        int botIdx = 1;
        for (int i = 0; i < allPlayers.size(); i++) {
            String p = allPlayers.get(i);
            if (p.equals(username)) continue;
            boolean isBot = p.startsWith("机器人");
            int diff = isBot ? new Random(p.hashCode()).nextInt(3) : 0;
            OpponentCard card = new OpponentCard(p, isBot, diff);
            opponentCards.put(p, card);
            opponentWrap.add(card);
            opponentWrap.add(Box.createVerticalStrut(4));
            botIdx++;
        }
    }

    // ========== 机器人 ==========

    private void startBotSimulations() {
        for (OpponentCard card : opponentCards.values()) {
            if (!card.isBot) continue;
            // 初始化机器人棋盘
            card.botBlocks = new ArrayList<>();
            card.botBoard = new int[BOARD_ROWS][COLS];
            spawnBotPiece(card);
            int initDelay = 2000 + card.botRand.nextInt(2500);
            // 叠叠乐机器人间隔更长（每落一次是独立决策）
            int interval = 800 + card.botRand.nextInt(1200);
            card.botTimer = new javax.swing.Timer(interval, e -> botTick(card));
            card.botTimer.setInitialDelay(initDelay);
            card.botTimer.start();
        }
    }

    private void spawnBotPiece(OpponentCard card) {
        card.botCurType = card.botRand.nextInt(BLOCK_SIZES.length);
        card.botCurW = BLOCK_SIZES[card.botCurType][0];
        card.botCurH = BLOCK_SIZES[card.botCurType][1];
        card.botCurColor = BLOCK_COLORS[card.botCurType];
        card.botCurX = 0;
    }

    private void botTick(OpponentCard card) {
        if (card.opponentFinished || card.opponentFailed || globalFinished) return;

        // 模拟横向移动和下落
        card.botCurX += 1;
        if (card.botCurX + card.botCurW > COLS) card.botCurX = 0;

        // 随机决定是否下落（难度越低，越容易在危险位置下落）
        int threshold;
        switch (card.botDifficulty) {
            case 0: threshold = 60; break;  // 初级：60%概率随便落
            case 1: threshold = 40; break;  // 中级：40%
            default: threshold = 25; break; // 高级：25%概率，会评估位置
        }
        boolean dropNow = card.botRand.nextInt(100) < threshold;

        // 高级机器人找安全位置
        if (card.botDifficulty >= 2 && dropNow) {
            int bestX = -1;
            double bestScore = -999;
            for (int tx = 0; tx <= COLS - card.botCurW; tx++) {
                double sc = evaluateBotPosition(card, tx);
                if (sc > bestScore) { bestScore = sc; bestX = tx; }
            }
            if (bestX >= 0) card.botCurX = bestX;
        }

        if (dropNow) {
            // 模拟下落并放置
            botDrop(card);
        }
        opponentWrap.repaint();
    }

    private double evaluateBotPosition(OpponentCard card, int x) {
        if (card.botBlocks.isEmpty()) return 0;
        PlacedBlock last = card.botBlocks.get(card.botBlocks.size() - 1);
        // 中心位置靠近上一个方块中心得分高
        double lastCenter = last.bx + last.bw / 2.0;
        double curCenter = x + card.botCurW / 2.0;
        double centerScore = 10 - Math.abs(curCenter - lastCenter) * 3;
        // 与上一个方块的重叠越多越好
        double overlap = Math.max(0, Math.min(x + card.botCurW, last.bx + last.bw) - Math.max(x, last.bx));
        double overlapScore = overlap * 5;
        return centerScore + overlapScore;
    }

    private void botDrop(OpponentCard card) {
        // 简化下落：找最低位置
        int landY;
        if (card.botBlocks.isEmpty()) {
            landY = ROWS - card.botCurH;
        } else {
            PlacedBlock last = card.botBlocks.get(card.botBlocks.size() - 1);
            boolean overlapsX = (card.botCurX + card.botCurW > last.bx) && (card.botCurX < last.bx + last.bw);
            if (!overlapsX) {
                // 不重叠，失败
                card.opponentFinished = true;
                card.opponentFailed = true;
                if (card.botTimer != null) card.botTimer.stop();
                sendBotResult(card.playerName, "FAIL", card.opponentScore);
                return;
            }
            landY = last.by - card.botCurH;
        }

        PlacedBlock block = new PlacedBlock(card.botCurX, landY, card.botCurW, card.botCurH, card.botCurType);
        java.util.List<PlacedBlock> all = new ArrayList<>(card.botBlocks);
        all.add(block);

        // 简化的重心检查
        java.util.Set<Integer> layerLevels = new HashSet<>();
        for (PlacedBlock pb : all) layerLevels.add(pb.by + pb.bh);

        boolean balanced = true;
        for (int layerY : layerLevels) {
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
            java.util.List<PlacedBlock> stack = new ArrayList<>();
            for (PlacedBlock pb : all)
                if (pb.by == layerY) stack.add(pb);
            if (stack.isEmpty()) continue;
            collectAboveBot(all, stack);
            double tw = 0, tm = 0;
            for (PlacedBlock s : stack) {
                double w = s.bw * s.bh;
                tw += w; tm += w * (s.bx + s.bw / 2.0);
            }
            double cx = tm / tw;
            if (cx < layerMin - 0.8 || cx > layerMax + 0.8) {
                balanced = false;
                break;
            }
        }

        if (!balanced) {
            card.opponentFinished = true;
            card.opponentFailed = true;
            if (card.botTimer != null) card.botTimer.stop();
            sendBotResult(card.playerName, "FAIL", card.opponentScore);
            return;
        }

        // 成功放置
        card.botBlocks.add(block);
        for (int r = 0; r < card.botCurH; r++)
            for (int c = 0; c < card.botCurW; c++) {
                int ny = landY + r, nx = card.botCurX + c;
                if (ny >= -BOARD_BASE && ny < BOARD_ROWS - BOARD_BASE && nx >= 0 && nx < COLS)
                    card.botBoard[w2b(ny)][nx] = card.botCurType + 1;
            }
        card.opponentScore += 2;
        card.botScore += 2;
        spawnBotPiece(card);
    }

    private void collectAboveBot(java.util.List<PlacedBlock> all, java.util.List<PlacedBlock> stack) {
        collectAbove(all, stack);
    }

    private void cleanup() {
        if (gameTimer != null) gameTimer.cancel();
        if (resultPollTimer != null) resultPollTimer.stop();
        if (forceEndTimer != null) forceEndTimer.stop();
        for (OpponentCard card : opponentCards.values())
            if (card.botTimer != null) card.botTimer.stop();
        activeGames.remove(roomId);
    }

    // ========== 公共接口 ==========

    public static void receiveGameOver(int roomId, String resultsData) {
        TetrisStackDuelGame game = activeGames.get(roomId);
        if (game != null)
            SwingUtilities.invokeLater(() -> game.handleGameOver());
    }

    public static TetrisStackDuelGame getActiveGame(int roomId) {
        return activeGames.get(roomId);
    }
}
