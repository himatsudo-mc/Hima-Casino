package com.himacasino.server;

import com.himacasino.game.SlotsGame;
import com.himacasino.util.Legacy;
import net.minecraft.block.entity.BlockEntity;
import net.minecraft.block.entity.SignBlockEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.text.Text;
import net.minecraft.util.math.BlockPos;

/** スロット用の看板 (表面) への書き込み。 */
public class SignWriter implements SlotsGame.SignView {

    private final ServerWorld world;
    private final BlockPos pos;

    public SignWriter(ServerWorld world, BlockPos pos) {
        this.world = world;
        this.pos = pos;
    }

    @Override
    public void setLines(String reelLine, String statusLine) {
        write(1, reelLine);
        write(3, statusLine);
    }

    @Override
    public void setReelLine(String reelLine) {
        write(1, reelLine);
    }

    private void write(int line, String legacy) {
        BlockEntity be = world.getBlockEntity(pos);
        if (be instanceof SignBlockEntity sign) {
            Text text = Legacy.toText(legacy);
            sign.changeText(t -> t.withMessage(line, text), true);
        }
    }

    /** 設置直後の看板の書式を整える */
    public static void format(ServerWorld world, BlockPos pos, double bet) {
        BlockEntity be = world.getBlockEntity(pos);
        if (!(be instanceof SignBlockEntity sign)) return;
        sign.changeText(t -> t
                .withMessage(0, Legacy.toText("§6§l[SLOT]"))
                .withMessage(1, Legacy.toText("§7? | ? | ?"))
                .withMessage(2, Legacy.toText(String.format("§eBet: %.0f", bet)))
                .withMessage(3, Legacy.toText("§aClick to Start!")), true);
    }
}
