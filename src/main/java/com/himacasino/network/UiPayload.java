package com.himacasino.network;

import net.minecraft.network.RegistryByteBuf;
import net.minecraft.network.codec.PacketCodec;
import net.minecraft.network.codec.PacketCodecs;
import net.minecraft.network.packet.CustomPayload;
import net.minecraft.util.Identifier;

/**
 * サーバー → クライアント: Chromium GUI の制御。
 * kind = "open" (画面を開く) / "state" (状態更新) / "close" (画面を閉じる)
 */
public record UiPayload(String kind, String game, String json) implements CustomPayload {

    public static final Id<UiPayload> ID = new Id<>(Identifier.of("hima-casino", "ui"));

    public static final PacketCodec<RegistryByteBuf, UiPayload> CODEC = PacketCodec.tuple(
            PacketCodecs.STRING, UiPayload::kind,
            PacketCodecs.STRING, UiPayload::game,
            PacketCodecs.STRING, UiPayload::json,
            UiPayload::new);

    @Override
    public Id<? extends CustomPayload> getId() {
        return ID;
    }
}
