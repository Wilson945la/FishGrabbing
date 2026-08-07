import javax.swing.*;
import java.awt.*;
import java.awt.event.*;

/**
 * UNO 主页：单按钮「开始游戏」入口。
 * 仿照 Game2048Home 风格，进入后开匹配房。
 */
public class UnoHome extends JFrame {
    private final FishGrabbingHome parent;
    private final String currentUser;

    static final Color BG = new Color(0x1b, 0x1b, 0x29);
    static final Color PANEL_BG = new Color(0x2a, 0x2a, 0x3a);
    static final Color FG = new Color(0xf5, 0xf5, 0xf5);
    static final Color YELLOW = new Color(0xff, 0xc1, 0x07);
    static final Color BTN_BASE = new Color(0x40, 0x40, 0x55);
    static final Color BTN_HOVER = new Color(0x00, 0x78, 0xd7);
    static final Color C_RED = new Color(0xe5, 0x39, 0x35);
    static final Color C_YELLOW = new Color(0xfd, 0xd8, 0x35);
    static final Color C_GREEN = new Color(0x43, 0xa0, 0x47);
    static final Color C_BLUE = new Color(0x1e, 0x88, 0xe5);

    public UnoHome(String currentUser, FishGrabbingHome parent) {
        this.currentUser = currentUser;
        this.parent = parent;
        setTitle("UNO");
        setSize(420, 520);
        setLocationRelativeTo(null);
        setDefaultCloseOperation(DISPOSE_ON_CLOSE);
        addWindowListener(new WindowAdapter() {
            public void windowClosing(WindowEvent e) { if (parent != null) parent.setVisible(true); }
        });

        JPanel root = new JPanel(new BorderLayout());
        root.setBackground(BG);
        setContentPane(root);

        // 顶部：标题 + 装饰
        JPanel top = new JPanel();
        top.setBackground(BG);
        top.setLayout(new BoxLayout(top, BoxLayout.Y_AXIS));
        top.setBorder(BorderFactory.createEmptyBorder(20, 0, 10, 0));

        JLabel title = new JLabel("UNO");
        title.setFont(new Font("Microsoft YaHei", Font.BOLD, 56));
        title.setForeground(YELLOW);
        title.setAlignmentX(Component.CENTER_ALIGNMENT);

        // 用 4 个小色块组成图标感——按官方样式：白底彩边 + 彩色椭圆 + 白色符号；+4 是黑底四色块
        JPanel deco = new JPanel(new FlowLayout(FlowLayout.CENTER, 6, 0));
        deco.setBackground(BG);
        deco.setAlignmentX(Component.CENTER_ALIGNMENT);
        deco.add(colorBox(C_RED,    "+2", PreviewKind.NUMBER));
        deco.add(colorBox(C_YELLOW, "",   PreviewKind.REVERSE));
        deco.add(colorBox(C_GREEN,  "",   PreviewKind.SKIP));
        deco.add(colorBox(Color.BLACK,"+4",PreviewKind.WILD_DRAW_FOUR));
        deco.setBorder(BorderFactory.createEmptyBorder(6, 0, 0, 0));

        JLabel sub = new JLabel("4 - 8 人 · 6 分钟限时");
        sub.setFont(new Font("Microsoft YaHei", Font.PLAIN, 14));
        sub.setForeground(new Color(0xaa, 0xaa, 0xaa));
        sub.setAlignmentX(Component.CENTER_ALIGNMENT);

        top.add(title);
        top.add(deco);
        top.add(Box.createVerticalStrut(8));
        top.add(sub);
        root.add(top, BorderLayout.NORTH);

        // 中部：开始按钮
        JPanel mid = new JPanel();
        mid.setBackground(BG);
        mid.setLayout(new BoxLayout(mid, BoxLayout.Y_AXIS));
        mid.setBorder(BorderFactory.createEmptyBorder(40, 0, 0, 0));

        JButton startBtn = makeBtn("开始游戏", 22, 280, 60);
        startBtn.setBackground(YELLOW);
        startBtn.setForeground(Color.BLACK);
        startBtn.addActionListener(e -> openMatchRoom());
        startBtn.setAlignmentX(Component.CENTER_ALIGNMENT);

        JButton backBtn = makeBtn("返回", 16, 200, 40);
        backBtn.setBackground(BTN_BASE);
        backBtn.setForeground(FG);
        backBtn.addActionListener(e -> {
            if (parent != null) parent.setVisible(true);
            dispose();
        });
        backBtn.setAlignmentX(Component.CENTER_ALIGNMENT);

        mid.add(startBtn);
        mid.add(Box.createVerticalStrut(20));
        mid.add(backBtn);
        root.add(mid, BorderLayout.CENTER);

        // 底部：「摸鱼神器 v1.3」标识（与 FishGrabbingHome 保持一致）
        JLabel ver = new JLabel("摸鱼神器 v1.3", SwingConstants.CENTER);
        ver.setFont(new Font("Microsoft YaHei", Font.PLAIN, 12));
        ver.setForeground(new Color(120, 123, 126));
        ver.setBorder(BorderFactory.createEmptyBorder(0, 0, 12, 0));
        root.add(ver, BorderLayout.SOUTH);
    }

    /** 示例卡类型：决定 colorBox 内部走哪套官方绘制 */
    enum PreviewKind { NUMBER, SKIP, REVERSE, WILD_DRAW_FOUR }

    private JLabel colorBox(Color c, String label, PreviewKind kind) {
        JLabel lb = new JLabel() {
            protected void paintComponent(Graphics g) {
                Graphics2D g2 = (Graphics2D) g;
                g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
                int w = 40, h = 56;

                if (kind == PreviewKind.WILD_DRAW_FOUR) {
                    // 黑底 + 4 色方块 + 白色 "+4"（官方 WILD_DRAW_FOUR 设计）
                    g2.setColor(Color.BLACK);
                    g2.fillRoundRect(0, 0, w, h, 8, 8);
                    g2.setColor(Color.WHITE);
                    g2.setStroke(new BasicStroke(1.6f));
                    g2.drawRoundRect(1, 1, w - 2, h - 2, 8, 8);
                    g2.setStroke(new BasicStroke(1));
                    // 4 色 2×2 块
                    int cw = 9, ch = 9, gap = 1;
                    int totalW = cw * 2 + gap;
                    int cx0 = (w - totalW) / 2;
                    int cy0 = (h - totalW) / 2 - 2;
                    g2.setColor(C_RED);    g2.fillRect(cx0,             cy0,         cw, ch);
                    g2.setColor(C_YELLOW); g2.fillRect(cx0 + cw + gap,  cy0,         cw, ch);
                    g2.setColor(C_BLUE);   g2.fillRect(cx0,             cy0 + ch + gap, cw, ch);
                    g2.setColor(C_GREEN);  g2.fillRect(cx0 + cw + gap,  cy0 + ch + gap, cw, ch);
                    // 四色块外白边
                    g2.setColor(Color.WHITE);
                    g2.drawRect(cx0 - 1, cy0 - 1, cw * 2 + gap + 1, ch * 2 + gap + 1);
                    // 中央 "+4" 白字
                    g2.setColor(Color.WHITE);
                    g2.setFont(new Font("Microsoft YaHei", Font.BOLD, 13));
                    int tw = g2.getFontMetrics().stringWidth("+4");
                    int th = g2.getFontMetrics().getAscent();
                    g2.drawString("+4", (w - tw) / 2, h - 9);
                } else {
                    // 白底 + 彩色边框 + 彩色椭圆 + 白色符号（官方 NUMBER/SKIP/REVERSE 设计）
                    g2.setColor(Color.WHITE);
                    g2.fillRoundRect(0, 0, w, h, 8, 8);
                    g2.setColor(c);
                    g2.setStroke(new BasicStroke(1.8f));
                    g2.drawRoundRect(1, 1, w - 2, h - 2, 8, 8);
                    g2.setStroke(new BasicStroke(1));
                    // 中央彩色椭圆
                    int ovalW = (int) (w * 0.82);
                    int ovalH = (int) (h * 0.60);
                    int ovalX = (w - ovalW) / 2;
                    int ovalY = (h - ovalH) / 2 - 1;
                    g2.setColor(c);
                    g2.fillOval(ovalX, ovalY, ovalW, ovalH);

                    int cx = w / 2;
                    int cy = h / 2 - 1;
                    if (kind == PreviewKind.SKIP) {
                        // 圆圈 + 斜杠（官方 ⊘ 风格）
                        int r = 9;
                        g2.setColor(Color.WHITE);
                        g2.setStroke(new BasicStroke(2.4f));
                        g2.drawOval(cx - r, cy - r, r * 2, r * 2);
                        g2.drawLine(cx - r + 2, cy - r + 2, cx + r - 2, cy + r - 2);
                        g2.setStroke(new BasicStroke(1));
                    } else if (kind == PreviewKind.REVERSE) {
                        // 双向箭头（官方 ↺↻ 风格）：上箭头指右、下箭头指左，尖端在外
                        int len = 9;
                        int gap = 3;
                        g2.setColor(Color.WHITE);
                        g2.setStroke(new BasicStroke(2));
                        g2.drawLine(cx - gap, cy - gap, cx - gap + len, cy - gap); // 上线：左→右
                        g2.drawLine(cx + gap, cy + gap, cx + gap - len, cy + gap); // 下线：右→左
                        // 上箭头：尖端 = 端点外伸 (右 +4)，底边两点回到端点
                        int tipOut1 = cx - gap + len + 4;
                        int[] ax1 = { tipOut1,    cx - gap + len, cx - gap + len };
                        int[] ay1 = { cy - gap,   cy - gap - 3,   cy - gap + 3 };
                        g2.fillPolygon(ax1, ay1, 3);
                        // 下箭头：尖端 = 端点外伸 (左 -4)，底边两点回到端点
                        int tipOut2 = cx + gap - len - 4;
                        int[] ax2 = { tipOut2,    cx + gap - len, cx + gap - len };
                        int[] ay2 = { cy + gap,   cy + gap - 3,   cy + gap + 3 };
                        g2.fillPolygon(ax2, ay2, 3);
                        g2.setStroke(new BasicStroke(1));
                    } else {
                        // NUMBER：椭圆内居中字符
                        g2.setColor(Color.WHITE);
                        g2.setFont(new Font("Microsoft YaHei", Font.BOLD, 14));
                        int tw = g2.getFontMetrics().stringWidth(label);
                        int th = g2.getFontMetrics().getAscent();
                        g2.drawString(label, (w - tw) / 2, h / 2 + th / 2 - 2);
                    }
                }
            }
        };
        lb.setPreferredSize(new Dimension(40, 56));
        return lb;
    }

    private JButton makeBtn(String text, int fontSize, int w, int h) {
        JButton b = new JButton(text);
        b.setFont(new Font("Microsoft YaHei", Font.BOLD, fontSize));
        b.setFocusPainted(false);
        b.setOpaque(true);
        b.setBorderPainted(false);
        b.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
        b.setMaximumSize(new Dimension(w, h));
        b.setPreferredSize(new Dimension(w, h));
        b.addMouseListener(new MouseAdapter() {
            public void mouseEntered(MouseEvent e) { b.setBackground(b.getBackground().darker()); }
            public void mouseExited(MouseEvent e) { b.setBackground(b.getBackground().brighter()); }
        });
        return b;
    }

    private void openMatchRoom() {
        setVisible(false);
        UnoMatchRoom room = new UnoMatchRoom(currentUser, parent, this);
        room.setVisible(true);
        dispose();
    }
}
