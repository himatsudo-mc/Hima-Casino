package com.himacasino.util;

import net.minecraft.text.MutableText;
import net.minecraft.text.Style;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;

/** § カラーコード付きの文字列を Text に変換する (旧プラグインのメッセージをそのまま使えるように)。 */
public final class Legacy {

    private Legacy() {}

    public static Text toText(String legacy) {
        MutableText root = Text.empty();
        Style style = Style.EMPTY;
        StringBuilder buf = new StringBuilder();
        for (int i = 0; i < legacy.length(); i++) {
            char c = legacy.charAt(i);
            if ((c == '§' || c == '&') && i + 1 < legacy.length()) {
                Formatting f = Formatting.byCode(Character.toLowerCase(legacy.charAt(i + 1)));
                if (f != null && (c == '§' || f.isColor() || f == Formatting.BOLD)) {
                    if (!buf.isEmpty()) {
                        root.append(Text.literal(buf.toString()).setStyle(style));
                        buf.setLength(0);
                    }
                    style = f == Formatting.RESET ? Style.EMPTY : style.withFormatting(f);
                    i++;
                    continue;
                }
            }
            buf.append(c);
        }
        if (!buf.isEmpty()) root.append(Text.literal(buf.toString()).setStyle(style));
        return root;
    }

    /** § コードを取り除いたプレーンテキスト */
    public static String strip(String legacy) {
        return legacy.replaceAll("§.", "");
    }
}
