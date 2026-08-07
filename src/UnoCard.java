import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * UNO 牌数据模型。
 * 108 张牌：红黄蓝绿 各 25 张（0×1、1-9×2、跳过×2、反转×2、+2×2）
 *       + 黑色万能 8 张（变色×4、+4×4）
 */
public class UnoCard {
    public enum Color {
        RED(java.awt.Color.RED),
        YELLOW(new java.awt.Color(0xff, 0xcc, 0x00)),
        GREEN(new java.awt.Color(0x22, 0xaa, 0x33)),
        BLUE(new java.awt.Color(0x22, 0x55, 0xdd)),
        BLACK(java.awt.Color.BLACK);
        public final java.awt.Color awt;
        Color(java.awt.Color a) { this.awt = a; }
        /** 兼容旧调用 awtColor() */
        public java.awt.Color awtColor() { return awt; }
    }
    public enum Type {
        NUMBER(0),
        SKIP(20),
        REVERSE(20),
        DRAW_TWO(20),
        WILD(50),
        WILD_DRAW_FOUR(50);
        public final int scoreValue;
        Type(int s) { this.scoreValue = s; }
    }

    public final Color color;
    public final Type type;
    public final int number; // 仅 NUMBER 牌使用（0-9），其余固定 0

    public UnoCard(Color c, Type t, int n) {
        this.color = c;
        this.type = t;
        this.number = n;
    }

    /** 牌用于计分时的面值：数字牌=数字，功能/万能=Type.scoreValue */
    public int scoreValue() {
        return type == Type.NUMBER ? number : type.scoreValue;
    }

    /**
     * 牌面显示用的占位字符（必须 ASCII 或中文，避免 Microsoft YaHei 缺字形变 tofu）。
     * 实际渲染时，UnoGame 对 SKIP/REVERSE/WILD 会根据 type 走自定义绘制分支，画得更像 UNO 官方卡面。
     */
    public String displayChar() {
        switch (type) {
            case NUMBER: return String.valueOf(number);
            case SKIP: return "S";                  // 中央/角标：S（中央实际由 drawCard 自定义绘制成 ⊘）
            case REVERSE: return "R";               // 中央/角标：R（中央实际由 drawCard 自定义绘制成 ↻）
            case DRAW_TWO: return "+2";
            case WILD: return "W";                  // 中央/角标：W（中央实际由 drawCard 自定义绘制成 4 色椭圆）
            case WILD_DRAW_FOUR: return "+4";
            default: return "?";
        }
    }

    /** 当前类型是否需要在 UnoGame 里走自定义绘制分支（避免字体缺字形） */
    public boolean needsCustomPaint() {
        return type == Type.SKIP || type == Type.REVERSE || type == Type.WILD;
    }

    /** 是否为黑色万能牌 */
    public boolean isWild() {
        return type == Type.WILD || type == Type.WILD_DRAW_FOUR;
    }

    /**
     * 是否可在当前局面下打出。
     * @param top           当前弃牌堆顶牌
     * @param activeColor   当前要求匹配的颜色（被万能牌指定过）
     * @param pendingDraws  累加的 +2/+4 张数，>0 时只能叠 +2/+4 或（在逆转叠加模式下）REVERSE
     * @param reverseStack  是否为"逆转叠加"规则
     */
    public boolean canPlayOn(UnoCard top, Color activeColor, int pendingDraws, boolean reverseStack) {
        if (pendingDraws > 0) {
            // 叠加：+2 接 +2，+4 接 +2 或 +4（颜色不限）
            if (type == Type.DRAW_TWO && top.type == Type.DRAW_TWO) return true;
            if (type == Type.WILD_DRAW_FOUR && (top.type == Type.DRAW_TWO || top.type == Type.WILD_DRAW_FOUR)) return true;
            // 逆转叠加：可以在 +2 上接同色 REVERSE 把累加丢回上家
            if (reverseStack && type == Type.REVERSE && color == activeColor) return true;
            return false;
        }
        // 万能牌任何时候都能出
        if (isWild()) return true;
        // 同色
        if (color == activeColor) return true;
        // 同数字
        if (type == Type.NUMBER && top.type == Type.NUMBER && number == top.number) return true;
        // 同功能（非万能）
        if (type != Type.NUMBER && !isWild() && type == top.type) return true;
        return false;
    }

    /** 生成一副完整的 108 张牌 */
    public static List<UnoCard> createDeck() {
        List<UnoCard> deck = new ArrayList<>();
        Color[] colors = { Color.RED, Color.YELLOW, Color.GREEN, Color.BLUE };
        for (Color c : colors) {
            deck.add(new UnoCard(c, Type.NUMBER, 0)); // 0 各 1 张
            for (int n = 1; n <= 9; n++) {
                deck.add(new UnoCard(c, Type.NUMBER, n));
                deck.add(new UnoCard(c, Type.NUMBER, n));
            }
            for (int i = 0; i < 2; i++) {
                deck.add(new UnoCard(c, Type.SKIP, 0));
                deck.add(new UnoCard(c, Type.REVERSE, 0));
                deck.add(new UnoCard(c, Type.DRAW_TWO, 0));
            }
        }
        for (int i = 0; i < 4; i++) {
            deck.add(new UnoCard(Color.BLACK, Type.WILD, 0));
            deck.add(new UnoCard(Color.BLACK, Type.WILD_DRAW_FOUR, 0));
        }
        return deck;
    }

    /** 洗牌并返回新列表 */
    public static List<UnoCard> shuffle(List<UnoCard> deck, long seed) {
        List<UnoCard> copy = new ArrayList<>(deck);
        Collections.shuffle(copy, new java.util.Random(seed));
        return copy;
    }
}
