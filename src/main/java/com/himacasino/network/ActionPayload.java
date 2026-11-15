package com.himacasino.network;

import net.minecraft.network.RegistryByteBuf;
import net.minecraft.network.codec.PacketCodec;
import net.minecraft.network.codec.PacketCodecs;
import net.minecraft.network.packet.CustomPayload;
import net.minecraft.util.Identifier;

/**
 * クライアント → サーバー: GUI 上の操作。json は {"a":"hit", ...} 形式。
 * 画面を閉じたときは {"a":"__closed"} が届く。
 */
public record ActionPayload(String json) implements CustomPayload {

    public static final Id<ActionPayload> ID = new Id<>(Identifier.of("hima-casino", "action"));

    public static final PacketCodec<RegistryByteBuf, ActionPayload> CODEC =
            PacketCodec.tuple(PacketCodecs.STRING, ActionPayload::json, ActionPayload::new);

    @Override
    public Id<? extends CustomPayload> getId() {
        return ID;
    }
}
