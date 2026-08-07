import javax.swing.*;
import java.awt.*;
import java.awt.event.*;
import java.nio.file.*;
import java.util.*;
import java.util.regex.*;

public class Minesweeper extends JFrame {

    private static final int[][] DIFFICULTY = {
            {9, 9, 10}, {16, 16, 40}, {16, 30, 99}
    };
    private static final String[] DIFF_NAMES = {"初级", "中级", "高级"};
    private static final int HIDDEN = 0, REVEALED = 1, FLAGGED = 2;
    private static final Color BG = new Color(50, 53, 56);
    private static final Color BTN_BASE = new Color(80, 83, 86);
    private static final Color BTN_HOVER = new Color(0, 120, 215);
    private static final Color HIGHLIGHT = new Color(255, 255, 150);
    private static final Color[] NUM_CLR = {
            null, Color.BLUE, new Color(0,100,0), Color.RED,
            new Color(0,0,139), new Color(128,0,0),
            Color.CYAN, Color.BLACK, Color.GRAY
    };

    private boolean settingAutoFlag = true;
    private boolean settingAutoReveal = true;
    private int ROWS, COLS, MINES;
    private String curDiff = "";
    private int fontSize;
    private int[][] board, state;
    private boolean gameOver, highlighted = false;
    private boolean timerStarted = false;
    private int flagsPlaced;
    private JButton[][] buttons;
    private JLabel statusLabel, timerLabel;
    private JPanel gridPanel, mainMenu, gamePanel;
    private CardLayout cardLayout;
    private javax.swing.Timer gameTimer;
    private long startTime;
    private static final String REC_FILE = System.getProperty("user.home") + "/.minesweeper_records";
    private JFrame homeFrame;
    private int userId = 0;

    public void setUserId(int userId) { this.userId = userId; }

    public void setHomeFrame(JFrame homeFrame) {
        this.homeFrame = homeFrame;
        getContentPane().removeAll();
        mainMenu = createMainMenu();
        getContentPane().add(mainMenu, "menu");
        gamePanel = new JPanel(new BorderLayout());
        getContentPane().add(gamePanel, "game");
        pack();
        setLocationRelativeTo(null);
    }

    public Minesweeper() {
        setTitle("摸鱼神器");
        setDefaultCloseOperation(JFrame.DISPOSE_ON_CLOSE);
        addWindowListener(new java.awt.event.WindowAdapter() {
            public void windowClosing(java.awt.event.WindowEvent e) {
                stopTimer();
                String u = ServerClient.getCurrentUser();
                if (u != null) {
                    try { ServerClient.setUserState(u, 0); } catch (Exception ignored) {}
                }
                // 尝试回到摸鱼中心
                if (homeFrame != null && homeFrame.isDisplayable()) {
                    homeFrame.setVisible(true);
                    homeFrame.setLocationRelativeTo(null);
                } else {
                    // 没有父窗口则彻底退出
                    System.exit(0);
                }
            }
        });
        setResizable(false);
        cardLayout = new CardLayout();
        getContentPane().setLayout(cardLayout);
        mainMenu = createMainMenu();
        getContentPane().add(mainMenu, "menu");
        gamePanel = new JPanel(new BorderLayout());
        getContentPane().add(gamePanel, "game");
        pack();
        setLocationRelativeTo(null);
    }

    private static JLabel eLabel(String e, int sz, Color c) {
        JLabel l = new JLabel(e, JLabel.CENTER);
        l.setFont(new Font("Segoe UI Emoji", Font.PLAIN, sz));
        if (c != null) l.setForeground(c);
        return l;
    }

    private JButton txtBtn(String text, Color fg, Color bg, int w, int h) {
        JButton b = new JButton(text);
        b.setFont(new Font("Microsoft YaHei", Font.BOLD, 14));
        b.setForeground(fg);
        b.setBackground(bg);
        b.setFocusPainted(false);
        b.setBorderPainted(false);
        b.setContentAreaFilled(false);
        b.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
        b.setPreferredSize(new Dimension(w, h));
        return b;
    }

    private JPanel createMainMenu() {
        JPanel menu = new JPanel(new BorderLayout());
        menu.setBackground(BG);
        menu.setPreferredSize(new Dimension(380, 600));

        JPanel tp = new JPanel(new BorderLayout());
        tp.setBackground(BG);
        tp.setBorder(BorderFactory.createEmptyBorder(20, 0, 8, 0));
        tp.add(eLabel("\uD83D\uDCA3", 40, Color.WHITE), BorderLayout.NORTH);
        JLabel tl = new JLabel("扫  雷", JLabel.CENTER);
        tl.setFont(new Font("Microsoft YaHei", Font.BOLD, 32));
        tl.setForeground(Color.WHITE);
        tp.add(tl, BorderLayout.CENTER);
        menu.add(tp, BorderLayout.NORTH);

        JPanel bp = new JPanel();
        bp.setBackground(BG);
        bp.setLayout(new BoxLayout(bp, BoxLayout.Y_AXIS));
        bp.setBorder(BorderFactory.createEmptyBorder(10, 45, 10, 45));
        String[] bt = {"经典扫雷（初级）","经典扫雷（中级）","经典扫雷（高级）","自定义难度","个人纪录","对  决"};
        for (int i = 0; i < bt.length; i++) {
            final int idx = i;
            JButton b = menuBtn(bt[i]);
            if (idx < 3) b.addActionListener(e -> startGame(DIFFICULTY[idx][0], DIFFICULTY[idx][1], DIFFICULTY[idx][2], DIFF_NAMES[idx]));
            else if (idx == 3) b.addActionListener(e -> showCustom());
            else if (idx == 4) b.addActionListener(e -> showRecords());
            else b.addActionListener(e -> showDuelDialog());
            bp.add(b);
            if (i < bt.length - 1) bp.add(Box.createVerticalStrut(14));
        }
        JPanel cw = new JPanel(new BorderLayout());
        cw.setBackground(BG);
        cw.add(bp, BorderLayout.CENTER);
        menu.add(cw, BorderLayout.CENTER);

        JPanel bot = new JPanel(new BorderLayout());
        bot.setBackground(BG);
        bot.setBorder(BorderFactory.createEmptyBorder(0, 0, 15, 0));
        JLabel vl = new JLabel("摸鱼神器 v1.3", JLabel.CENTER);
        vl.setFont(new Font("Microsoft YaHei", Font.PLAIN, 12));
        vl.setForeground(new Color(120, 123, 126));
        bot.add(vl, BorderLayout.CENTER);
        if (homeFrame != null) {
            JButton backBtn = txtBtn("返回主页", Color.WHITE, new Color(80,83,86), 90, 32);
            backBtn.setFont(new Font("Microsoft YaHei", Font.BOLD, 13));
            backBtn.addMouseListener(new MouseAdapter() {
                public void mouseEntered(MouseEvent e) { backBtn.setBackground(BTN_HOVER); }
                public void mouseExited(MouseEvent e) { backBtn.setBackground(BTN_BASE); }
            });
            backBtn.addActionListener(e -> { setVisible(false); homeFrame.setVisible(true); homeFrame.setLocationRelativeTo(null); });
            JPanel backPanel = new JPanel(new FlowLayout(FlowLayout.LEFT, 10, 0));
            backPanel.setBackground(BG);
            backPanel.add(backBtn);
            bot.add(backPanel, BorderLayout.WEST);
        }
        JButton setBtn = txtBtn("设置", Color.WHITE, new Color(80,83,86), 90, 32);
        setBtn.setFont(new Font("Microsoft YaHei", Font.BOLD, 13));
        setBtn.addMouseListener(new MouseAdapter() {
            public void mouseEntered(MouseEvent e) { setBtn.setForeground(Color.WHITE); setBtn.setBackground(BTN_HOVER); }
            public void mouseExited(MouseEvent e) { setBtn.setForeground(Color.WHITE); setBtn.setBackground(BTN_BASE); }
        });
        setBtn.addActionListener(e -> showSettings(false));
        JPanel setPanel = new JPanel(new FlowLayout(FlowLayout.RIGHT, 10, 0));
        setPanel.setBackground(BG);
        setPanel.add(setBtn);
        bot.add(setPanel, BorderLayout.EAST);
        menu.add(bot, BorderLayout.SOUTH);
        return menu;
    }

    private JButton menuBtn(String t) {
        JButton b = new JButton(t);
        b.setFont(new Font("Microsoft YaHei", Font.BOLD, 17));
        b.setForeground(Color.WHITE);
        b.setBackground(BTN_BASE);
        b.setFocusPainted(false);
        b.setContentAreaFilled(false);
        b.setOpaque(true);
        b.setBorder(BorderFactory.createEmptyBorder(14, 0, 14, 0));
        b.setAlignmentX(Component.CENTER_ALIGNMENT);
        b.setMaximumSize(new Dimension(350, 52));
        b.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
        b.addMouseListener(new MouseAdapter() {
            public void mouseEntered(MouseEvent e) { b.setBackground(BTN_HOVER); }
            public void mouseExited(MouseEvent e) { b.setBackground(BTN_BASE); }
        });
        return b;
    }

    private void showCustom() {
        JPanel p = new JPanel(new GridLayout(3, 2, 10, 10));
        p.setBorder(BorderFactory.createEmptyBorder(10, 10, 10, 10));
        JTextField rf = new JTextField("16"), cf = new JTextField("16"), mf = new JTextField("40");
        p.add(new JLabel("行数 (1-99):")); p.add(rf);
        p.add(new JLabel("列数 (1-99):")); p.add(cf);
        p.add(new JLabel("雷数:")); p.add(mf);
        if (JOptionPane.showConfirmDialog(this, p, "自定义难度", JOptionPane.OK_CANCEL_OPTION) != JOptionPane.OK_OPTION) return;
        try {
            int r = Integer.parseInt(rf.getText().trim()), c = Integer.parseInt(cf.getText().trim()), m = Integer.parseInt(mf.getText().trim());
            if (r < 1 || r > 99 || c < 1 || c > 99) { JOptionPane.showMessageDialog(this, "行数和列数必须在 1~99 之间！", "输入错误", JOptionPane.ERROR_MESSAGE); return; }
            if (m == 0) { JOptionPane.showMessageDialog(this, "雷数不能为零！", "输入错误", JOptionPane.ERROR_MESSAGE); return; }
            if (m >= r * c) { JOptionPane.showMessageDialog(this, "雷数不能大于等于格子数！雷数不能为零！", "输入错误", JOptionPane.ERROR_MESSAGE); return; }
            startGame(r, c, m, "自定义");
        } catch (Exception ex) { JOptionPane.showMessageDialog(this, "请输入有效的数字！", "输入错误", JOptionPane.ERROR_MESSAGE); }
    }

    private void showSettings(boolean inGame) {
        JDialog d = new JDialog(this, "设置", true);
        d.setResizable(false);
        d.setLayout(new BorderLayout());
        JPanel c = new JPanel(new GridBagLayout());
        c.setBackground(new Color(60, 63, 65));
        c.setBorder(BorderFactory.createEmptyBorder(20, 30, 20, 30));
        GridBagConstraints g = new GridBagConstraints();
        g.fill = GridBagConstraints.HORIZONTAL;
        g.insets = new Insets(8, 0, 8, 0);
        g.gridx = 0; g.gridy = 0; g.weightx = 1;
        c.add(mkLabel("自动插旗", 16), g);
        g.gridx = 1; g.weightx = 0; g.anchor = GridBagConstraints.EAST;
        ToggleSwitch fs = new ToggleSwitch(); fs.setSelected(settingAutoFlag); c.add(fs, g);
        g.gridx = 0; g.gridy = 1; g.weightx = 1; g.anchor = GridBagConstraints.WEST;
        c.add(mkLabel("自动点开", 16), g);
        g.gridx = 1; g.weightx = 0;
        ToggleSwitch rs = new ToggleSwitch(); rs.setSelected(settingAutoReveal); c.add(rs, g);
        d.add(c, BorderLayout.CENTER);
        JButton cb = txtBtn("关闭", Color.WHITE, new Color(80,83,86), 80, 32);
        cb.addActionListener(e -> d.dispose());
        JPanel bp = new JPanel(new FlowLayout());
        bp.setBackground(new Color(60, 63, 65));
        bp.add(cb);
        d.add(bp, BorderLayout.SOUTH);
        d.pack();
        d.setLocationRelativeTo(this);
        d.setVisible(true);
        settingAutoFlag = fs.isSelected();
        settingAutoReveal = rs.isSelected();
    }

    private JLabel mkLabel(String t, int sz) {
        JLabel l = new JLabel(t);
        l.setFont(new Font("Microsoft YaHei", Font.PLAIN, sz));
        l.setForeground(Color.WHITE);
        return l;
    }

    private class ToggleSwitch extends JToggleButton {
        private static final int W = 52, H = 28, R = 14;
        private static final Color ON = new Color(0,150,136), OFF = new Color(160,160,160);
        ToggleSwitch() {
            setPreferredSize(new Dimension(W, H));
            setMaximumSize(new Dimension(W, H));
            setFocusPainted(false); setContentAreaFilled(false); setBorderPainted(false); setOpaque(false);
            setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
            addActionListener(e -> repaint());
        }
        protected void paintComponent(Graphics g) {
            Graphics2D g2 = (Graphics2D) g.create();
            g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
            g2.setColor(isSelected() ? ON : OFF);
            g2.fillRoundRect(0, 0, W, H, R, R);
            int kx = isSelected() ? (W - H) : 2;
            g2.setColor(new Color(0,0,0,30)); g2.fillOval(kx,2,H-4,H-4);
            g2.setColor(Color.WHITE); g2.fillOval(kx+1,1,H-6,H-6);
            g2.dispose();
        }
    }

    private void startGame(int r, int c, int m, String dn) {
        ROWS = r; COLS = c; MINES = m;
        curDiff = dn;
        timerStarted = false;
        getContentPane().remove(gamePanel);
        gamePanel = new JPanel(new BorderLayout());
        getContentPane().add(gamePanel, "game");
        initBoard();
        setupUI(dn);
        gamePanel.revalidate(); gamePanel.repaint();
        pack(); setLocationRelativeTo(null);
        cardLayout.show(getContentPane(), "game");
    }

    private void initBoard() {
        board = new int[ROWS][COLS];
        state = new int[ROWS][COLS];
        gameOver = false;
        highlighted = false;
        flagsPlaced = 0;
        stopTimer();
        Random rnd = new Random();
        int p = 0;
        while (p < MINES) { int r = rnd.nextInt(ROWS), c = rnd.nextInt(COLS); if (board[r][c] != -1) { board[r][c] = -1; p++; } }
        for (int r = 0; r < ROWS; r++) for (int c = 0; c < COLS; c++) {
            if (board[r][c] == -1) continue;
            int ct = 0;
            for (int dr = -1; dr <= 1; dr++) for (int dc = -1; dc <= 1; dc++) {
                int nr = r+dr, nc = c+dc;
                if (nr>=0 && nr<ROWS && nc>=0 && nc<COLS && board[nr][nc]==-1) ct++;
            }
            board[r][c] = ct;
        }
    }

    private void setupUI(String dn) {
        gamePanel.setLayout(new BorderLayout());
        JPanel top = new JPanel(new BorderLayout(8, 0));
        top.setBackground(new Color(220,220,220));
        top.setBorder(BorderFactory.createEmptyBorder(4, 6, 4, 6));
        JPanel left = new JPanel(new FlowLayout(FlowLayout.LEFT, 4, 0));
        left.setBackground(new Color(220,220,220));
        JButton bb = new JButton("返回菜单");
        bb.setFont(new Font("Microsoft YaHei", Font.BOLD, 13));
        bb.addActionListener(e -> { stopTimer(); cardLayout.show(getContentPane(), "menu"); pack(); setLocationRelativeTo(null); });
        left.add(bb);
        JButton rb = new JButton("重新开始");
        rb.setFont(new Font("Microsoft YaHei", Font.BOLD, 13));
        rb.addActionListener(e -> restart());
        left.add(rb);
        top.add(left, BorderLayout.WEST);
        JPanel right = new JPanel(new FlowLayout(FlowLayout.RIGHT, 6, 0));
        right.setBackground(new Color(220,220,220));
        timerLabel = new JLabel("\u23F1 00:00");
        timerLabel.setFont(new Font("Consolas", Font.BOLD, 14));
        right.add(timerLabel);
        statusLabel = new JLabel("[" + dn + "] 剩余雷数: " + (MINES - flagsPlaced));
        statusLabel.setFont(new Font("Microsoft YaHei", Font.BOLD, 13));
        right.add(statusLabel);
        JButton sb = txtBtn("设置", Color.BLACK, new Color(220,220,220), 60, 26);
        sb.setFont(new Font("Microsoft YaHei", Font.BOLD, 13));
        sb.addMouseListener(new MouseAdapter() {
            public void mouseEntered(MouseEvent e) { sb.setForeground(Color.BLUE); }
            public void mouseExited(MouseEvent e) { sb.setForeground(Color.BLACK); }
        });
        sb.addActionListener(e -> showSettings(true));
        right.add(sb);
        top.add(right, BorderLayout.EAST);
        gamePanel.add(top, BorderLayout.NORTH);

        int cs = (ROWS > 16 || COLS > 16) ? 32 : 45;
        fontSize = (ROWS > 16 || COLS > 16) ? 14 : 18;
        gridPanel = new JPanel(new GridLayout(ROWS, COLS, 1, 1));
        gridPanel.setBackground(Color.DARK_GRAY);
        buttons = new JButton[ROWS][COLS];
        for (int r = 0; r < ROWS; r++) for (int c = 0; c < COLS; c++) {
            JButton btn = new JButton();
            btn.setPreferredSize(new Dimension(cs, cs));
            btn.setFont(new Font("Microsoft YaHei", Font.BOLD, fontSize));
            btn.setMargin(new Insets(0,0,0,0));
            btn.setFocusPainted(false); btn.setOpaque(true);
            int row = r, col = c;
            btn.addMouseListener(new MouseAdapter() {
                public void mouseClicked(MouseEvent e) {
                    if (gameOver) return;
                    if (e.getButton() == MouseEvent.BUTTON3) rightClick(row, col);
                    else if (e.getButton() == MouseEvent.BUTTON1) leftClick(row, col);
                }
                public void mouseEntered(MouseEvent e) { if (!gameOver && state[row][col]==REVEALED && board[row][col]>0) hl(row,col); }
                public void mouseExited(MouseEvent e) { unh(); }
            });
            buttons[r][c] = btn;
            gridPanel.add(btn);
        }
        gamePanel.add(new JScrollPane(gridPanel), BorderLayout.CENTER);
        JPanel bot = new JPanel(new FlowLayout());
        bot.setBackground(new Color(220,220,220));
        JLabel hl = new JLabel("左键翻开 | 右键插旗 | 踩雷重开");
        hl.setFont(new Font("Microsoft YaHei", Font.PLAIN, 12));
        bot.add(hl);
        gamePanel.add(bot, BorderLayout.SOUTH);
    }

    private void startTimer() {
        timerStarted = true;
        startTime = System.currentTimeMillis();
        if (gameTimer != null) gameTimer.stop();
        gameTimer = new javax.swing.Timer(1000, e -> { if(timerLabel!=null && !gameOver) timerLabel.setText("\u23F1 "+getElapsed()); });
        gameTimer.start();
    }

    private void stopTimer() { if (gameTimer != null) gameTimer.stop(); }

    private String getElapsed() {
        long s = (System.currentTimeMillis() - startTime) / 1000;
        return String.format("%02d:%02d", s/60, s%60);
    }

    private void leftClick(int r, int c) {
        if (!gameOver && !timerStarted) startTimer();
        unh();
        if (state[r][c] == REVEALED && board[r][c] > 0) {
            int fc = 0, hc = 0;
            for (int dr=-1;dr<=1;dr++) for (int dc=-1;dc<=1;dc++) {
                int nr=r+dr,nc=c+dc;
                if (nr>=0&&nr<ROWS&&nc>=0&&nc<COLS) { if(state[nr][nc]==FLAGGED)fc++; else if(state[nr][nc]==HIDDEN)hc++; }
            }
            if (settingAutoFlag && hc+fc==board[r][c] && hc>0) {
                for (int dr=-1;dr<=1;dr++) for (int dc=-1;dc<=1;dc++) {
                    int nr=r+dr,nc=c+dc;
                    if (nr>=0&&nr<ROWS&&nc>=0&&nc<COLS&&state[nr][nc]==HIDDEN) { state[nr][nc]=FLAGGED; flagsPlaced++; setFlag(nr,nc); }
                }
                updSt(); win(); return;
            }
            if (settingAutoReveal && fc==board[r][c] && hc>0) {
                for (int dr=-1;dr<=1;dr++) for (int dc=-1;dc<=1;dc++) {
                    int nr=r+dr,nc=c+dc;
                    if (nr>=0&&nr<ROWS&&nc>=0&&nc<COLS&&state[nr][nc]==HIDDEN) {
                        if (board[nr][nc]==-1) { boom(); return; }
                        reveal(nr,nc);
                    }
                }
                win(); return;
            }
            return;
        }
        if (state[r][c]==FLAGGED) return;
        if (board[r][c]==-1) { boom(); return; }
        reveal(r,c); win();
    }

    private void rightClick(int r, int c) {
        if (state[r][c]==REVEALED) return;
        if (state[r][c]==HIDDEN) { state[r][c]=FLAGGED; flagsPlaced++; setFlag(r,c); }
        else { state[r][c]=HIDDEN; flagsPlaced--; buttons[r][c].setText(""); }
        updSt();
    }

    private void setFlag(int r, int c) {
        buttons[r][c].setFont(new Font("Segoe UI Emoji", Font.PLAIN, fontSize));
        buttons[r][c].setText("\uD83D\uDEA9");
        buttons[r][c].setForeground(Color.RED);
    }

    private void reveal(int r, int c) {
        if (r<0||r>=ROWS||c<0||c>=COLS||state[r][c]!=HIDDEN||board[r][c]==-1) return;
        state[r][c] = REVEALED;
        JButton b = buttons[r][c];
        b.setFont(new Font("Microsoft YaHei", Font.BOLD, fontSize));
        b.setBackground(Color.WHITE); b.setEnabled(false);
        if (board[r][c]>0) { b.setText(""+board[r][c]); b.setForeground(NUM_CLR[board[r][c]]); }
        else { b.setText(""); for(int dr=-1;dr<=1;dr++) for(int dc=-1;dc<=1;dc++) if(dr!=0||dc!=0) reveal(r+dr,c+dc); }
    }

    private void boom() {
        revealAll(); gameOver=true; stopTimer();
        JPanel p = new JPanel(new FlowLayout(FlowLayout.CENTER,10,0));
        p.add(eLabel("\uD83D\uDCA5",28,null)); p.add(new JLabel("踩到雷了！游戏结束"));
        JOptionPane.showMessageDialog(this, p, "游戏结束", JOptionPane.WARNING_MESSAGE);
    }

    private void revealAll() {
        for (int r=0;r<ROWS;r++) for (int c=0;c<COLS;c++) if (board[r][c]==-1) {
            buttons[r][c].setFont(new Font("Segoe UI Emoji",Font.PLAIN,fontSize));
            buttons[r][c].setText("\uD83D\uDCA3"); buttons[r][c].setForeground(Color.RED);
            buttons[r][c].setBackground(new Color(255,100,100)); buttons[r][c].setEnabled(false);
        }
    }

    private void win() {
        int u=0;
        for (int r=0;r<ROWS;r++) for (int c=0;c<COLS;c++) if (board[r][c]!=-1&&state[r][c]!=REVEALED) u++;
        if (u==0) {
            gameOver=true; stopTimer();
            String t = getElapsed();
            saveRec(curDiff, t);
            JPanel p = new JPanel(new FlowLayout(FlowLayout.CENTER,10,0));
            p.add(eLabel("\uD83C\uDF89",28,null)); p.add(new JLabel("恭喜你赢了！用时 "+t));
            JOptionPane.showMessageDialog(this, p, "胜利", JOptionPane.INFORMATION_MESSAGE);
        }
    }

    private void restart() {
        stopTimer();
        timerStarted = false;
        highlighted = false;
        gameOver = false;
        flagsPlaced = 0;
        board = new int[ROWS][COLS];
        state = new int[ROWS][COLS];
        Random rnd = new Random();
        int p = 0;
        while (p < MINES) { int r = rnd.nextInt(ROWS), c = rnd.nextInt(COLS); if (board[r][c] != -1) { board[r][c] = -1; p++; } }
        for (int r = 0; r < ROWS; r++) for (int c = 0; c < COLS; c++) {
            if (board[r][c] == -1) continue;
            int ct = 0;
            for (int dr = -1; dr <= 1; dr++) for (int dc = -1; dc <= 1; dc++) {
                int nr = r+dr, nc = c+dc;
                if (nr>=0 && nr<ROWS && nc>=0 && nc<COLS && board[nr][nc]==-1) ct++;
            }
            board[r][c] = ct;
        }
        if (timerLabel != null) timerLabel.setText("\u23F1 00:00");
        updSt();
        int cs = (ROWS > 16 || COLS > 16) ? 32 : 45;
        gridPanel.removeAll();
        buttons = new JButton[ROWS][COLS];
        for (int r = 0; r < ROWS; r++) for (int c = 0; c < COLS; c++) {
            JButton btn = new JButton();
            btn.setPreferredSize(new Dimension(cs, cs));
            btn.setFont(new Font("Microsoft YaHei", Font.BOLD, fontSize));
            btn.setMargin(new Insets(0,0,0,0));
            btn.setFocusPainted(false); btn.setOpaque(true);
            int row = r, col = c;
            btn.addMouseListener(new MouseAdapter() {
                public void mouseClicked(MouseEvent e) {
                    if (gameOver) return;
                    if (e.getButton() == MouseEvent.BUTTON3) rightClick(row, col);
                    else if (e.getButton() == MouseEvent.BUTTON1) leftClick(row, col);
                }
                public void mouseEntered(MouseEvent e) { if (!gameOver && state[row][col]==REVEALED && board[row][col]>0) hl(row,col); }
                public void mouseExited(MouseEvent e) { unh(); }
            });
            buttons[r][c] = btn;
            gridPanel.add(btn);
        }
        gridPanel.revalidate();
        gridPanel.repaint();
    }

    private void hl(int r, int c) {
        if (highlighted) return; highlighted=true;
        Color db = UIManager.getColor("Button.background");
        for(int dr=-1;dr<=1;dr++) for(int dc=-1;dc<=1;dc++) {
            if(dr==0&&dc==0)continue; int nr=r+dr,nc=c+dc;
            if(nr>=0&&nr<ROWS&&nc>=0&&nc<COLS&&state[nr][nc]==HIDDEN) buttons[nr][nc].setBackground(HIGHLIGHT);
        }
    }

    private void unh() {
        if(!highlighted)return; highlighted=false;
        Color db=UIManager.getColor("Button.background");
        for(int r=0;r<ROWS;r++) for(int c=0;c<COLS;c++) if(state[r][c]==HIDDEN) buttons[r][c].setBackground(db);
    }

    private void updSt() { if(statusLabel!=null) statusLabel.setText(statusLabel.getText().replaceAll("剩余雷数: .*","剩余雷数: "+(MINES-flagsPlaced))); }

    private int parseTimeSeconds(String t) {
        String[] parts = t.split(":");
        return Integer.parseInt(parts[0]) * 60 + Integer.parseInt(parts[1]);
    }

    private void saveRec(String diff, String time) {
        int seconds = parseTimeSeconds(time);
        MinesweeperRecords.saveRec(userId, "扫雷", diff, String.valueOf(seconds));
    }

    private Map<String, String> loadRecs() {
        if (userId > 0) return MinesweeperRecords.loadRecs(userId);
        return new LinkedHashMap<>();
    }

    private void showRecords() {
        MinesweeperRecords.showRecords(this, userId);
    }

    /** 对决：选择模式 → 选择人数 → 进入匹配房间 */
    private void showDuelDialog() {
        String currentUser = ServerClient.getCurrentUser();
        if (currentUser == null || currentUser.isEmpty()) {
            JOptionPane.showMessageDialog(this, "请先登录", "提示", JOptionPane.WARNING_MESSAGE);
            return;
        }

        // 第一步：选择难度
        JDialog modeDialog = new JDialog(this, "选择对决模式", true);
        modeDialog.setSize(320, 280);
        modeDialog.setLocationRelativeTo(this);
        modeDialog.setLayout(new BorderLayout());

        JPanel panel = new JPanel();
        panel.setBackground(BG);
        panel.setLayout(new BoxLayout(panel, BoxLayout.Y_AXIS));
        panel.setBorder(BorderFactory.createEmptyBorder(20, 30, 20, 30));

        JLabel header = new JLabel("请选择对决难度", JLabel.CENTER);
        header.setFont(new Font("Microsoft YaHei", Font.BOLD, 16));
        header.setForeground(Color.WHITE);
        header.setAlignmentX(Component.CENTER_ALIGNMENT);
        panel.add(header);
        panel.add(Box.createVerticalStrut(20));

        String[] modes = {"初级", "中级", "高级"};
        for (String m : modes) {
            JButton btn = menuBtn(m);
            btn.addActionListener(e -> {
                modeDialog.dispose();
                showDuelCountDialog(currentUser, m);
            });
            panel.add(btn);
            panel.add(Box.createVerticalStrut(10));
        }

        JButton customBtn = menuBtn("自定义");
        customBtn.addActionListener(e -> {
            modeDialog.dispose();
            showCustomDuel(currentUser);
        });
        panel.add(customBtn);

        modeDialog.add(panel, BorderLayout.CENTER);
        modeDialog.setVisible(true);
    }

    /** 选择对决人数 */
    private void showDuelCountDialog(String currentUser, String mode) {
        JDialog countDialog = new JDialog(this, "选择对决人数", true);
        countDialog.setSize(320, 320);
        countDialog.setLocationRelativeTo(this);
        countDialog.setLayout(new BorderLayout());

        JPanel panel = new JPanel();
        panel.setBackground(BG);
        panel.setLayout(new BoxLayout(panel, BoxLayout.Y_AXIS));
        panel.setBorder(BorderFactory.createEmptyBorder(20, 30, 20, 30));

        JLabel header = new JLabel("请选择参赛人数", JLabel.CENTER);
        header.setFont(new Font("Microsoft YaHei", Font.BOLD, 16));
        header.setForeground(Color.WHITE);
        header.setAlignmentX(Component.CENTER_ALIGNMENT);
        panel.add(header);
        panel.add(Box.createVerticalStrut(20));

        int[] counts = {2, 3, 4};
        String[] labels = {"双人", "三人", "四人"};
        for (int i = 0; i < counts.length; i++) {
            final int cnt = counts[i];
            JButton btn = menuBtn(labels[i] + " (" + cnt + "人)");
            btn.addActionListener(e -> {
                countDialog.dispose();
                openMatchRoom(currentUser, mode, cnt);
            });
            panel.add(btn);
            panel.add(Box.createVerticalStrut(8));
        }

        countDialog.add(panel, BorderLayout.CENTER);
        countDialog.setVisible(true);
    }

    private void openMatchRoom(String currentUser, String mode, int count) {
        // 隐藏扫雷主页
        setVisible(false);
        Thread t = new Thread(() -> {
            String resp = ServerClient.duelCreate(currentUser, mode, count, "扫雷");
            SwingUtilities.invokeLater(() -> {
                if (resp.startsWith("SUCCESS")) {
                    String data = resp.substring("SUCCESS|".length());
                    String[] parts = data.split("\\|");
                    int roomId = Integer.parseInt(parts[0]);
                    // 设置全局摸鱼中心引用，用于接受邀请时隐藏
                    if (Minesweeper.this.homeFrame != null) {
                        MineMatchRoom.setGlobalFishHome(Minesweeper.this.homeFrame);
                    }
                    MineMatchRoom room = new MineMatchRoom(currentUser, userId, mode, count, true, roomId, Minesweeper.this);
                    room.setVisible(true);
                } else {
                    // 失败则恢复扫雷主页
                    setVisible(true);
                    setLocationRelativeTo(null);
                    JOptionPane.showMessageDialog(Minesweeper.this, "创建房间失败", "错误", JOptionPane.ERROR_MESSAGE);
                }
            });
        });
        t.setDaemon(true);
        t.start();
    }

    private void showCustomDuel(String currentUser) {
        JPanel p = new JPanel(new GridLayout(3, 2, 10, 10));
        p.setBorder(BorderFactory.createEmptyBorder(10, 10, 10, 10));
        JTextField rf = new JTextField("16"), cf = new JTextField("16"), mf = new JTextField("40");
        p.add(new JLabel("行数 (1-99):")); p.add(rf);
        p.add(new JLabel("列数 (1-99):")); p.add(cf);
        p.add(new JLabel("雷数:")); p.add(mf);
        if (JOptionPane.showConfirmDialog(this, p, "自定义对决难度", JOptionPane.OK_CANCEL_OPTION) != JOptionPane.OK_OPTION) return;
        try {
            int r = Integer.parseInt(rf.getText().trim()), c = Integer.parseInt(cf.getText().trim()), m = Integer.parseInt(mf.getText().trim());
            if (r < 1 || r > 99 || c < 1 || c > 99) {
                JOptionPane.showMessageDialog(this, "行数和列数必须在 1~99 之间！", "输入错误", JOptionPane.ERROR_MESSAGE);
                return;
            }
            if (m == 0 || m >= r * c) {
                JOptionPane.showMessageDialog(this, "雷数无效！", "输入错误", JOptionPane.ERROR_MESSAGE);
                return;
            }
            String customDesc = r + "x" + c + "(" + m + "雷)";
            showDuelCountDialog(currentUser, "自定义-" + customDesc);
        } catch (Exception ex) {
            JOptionPane.showMessageDialog(this, "请输入有效的数字！", "输入错误", JOptionPane.ERROR_MESSAGE);
        }
    }

    public static void main(String[] args) {
        System.setProperty("sun.java2d.dpiaware","true");
        System.setProperty("sun.java2d.uiScale","1.0");
        SwingUtilities.invokeLater(() -> { new Minesweeper().setVisible(true); });
    }
}
