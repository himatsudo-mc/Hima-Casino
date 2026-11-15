package com.himacasino.client;

import com.cinemamod.mcef.MCEF;
import com.cinemamod.mcef.MCEFBrowser;
import com.himacasino.network.ActionPayload;
import com.mojang.blaze3d.systems.RenderSystem;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gl.ShaderProgramKeys;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.render.BufferBuilder;
import net.minecraft.client.render.BufferRenderer;
import net.minecraft.client.render.Tessellator;
import net.minecraft.client.render.VertexFormat;
import net.minecraft.client.render.VertexFormats;
import net.minecraft.text.Text;

/**
 * Chromium (MCEF) でゲーム画面 (HTML) を描画する Screen。
 * 描画内容は常にサーバーが送る状態 JSON で決まり、ユーザー操作は {@link ActionPayload} でサーバーへ返す。
 */
public class CasinoScreen extends Screen {

    private final String game;
    private MCEFBrowser browser;
    private String latestState;
    private volatile boolean pageReady;
    private boolean closedByServer;

    public CasinoScreen(String game, String initialState) {
        super(Text.literal("HimaCasino"));
        this.game = game;
        this.latestState = initialState;
    }

    public String game() { return game; }

    // ── lifecycle ─────────────────────────────────────────────────────────

    @Override
    protected void init() {
        if (browser != null) {
            resizeBrowser();
            return;
        }
        ChromiumBridge.install();
        PageScheme.register();
        ChromiumBridge.listener = this::onJsMessage;
        browser = MCEF.createBrowser(PageScheme.url(game + ".html"), true);
        resizeBrowser();
    }

    private void resizeBrowser() {
        var w = MinecraftClient.getInstance().getWindow();
        browser.resize(w.getFramebufferWidth(), w.getFramebufferHeight());
    }

    @Override
    public void resize(MinecraftClient client, int width, int height) {
        super.resize(client, width, height);
        if (browser != null) resizeBrowser();
    }

    /** サーバーからの状態更新 */
    public void pushState(String json) {
        latestState = json;
        if (pageReady) deliver();
    }

    private void deliver() {
        if (browser == null || latestState == null) return;
        // JSON はそのまま JS のオブジェクトリテラルとして評価できる
        browser.executeJavaScript("window.casino && window.casino.setState(" + latestState + ");", browser.getURL(), 0);
    }

    /** JS からのメッセージ (CEF スレッド) */
    private void onJsMessage(String json) {
        if (json.contains("\"__ready\"")) {
            pageReady = true;
            ChromiumBridge.runOnMain(this::deliver);
            return;
        }
        ChromiumBridge.runOnMain(() -> {
            if (ClientPlayNetworking.canSend(ActionPayload.ID)) ClientPlayNetworking.send(new ActionPayload(json));
        });
    }

    /** サーバーが画面を閉じた (回転演出の開始など) — サーバーへの通知は不要 */
    public void closeFromServer() {
        closedByServer = true;
        close();
    }

    @Override
    public void removed() {
        if (browser != null) {
            browser.close();
            browser = null;
        }
        if (ChromiumBridge.listener != null) ChromiumBridge.listener = null;
        if (!closedByServer && ClientPlayNetworking.canSend(ActionPayload.ID)) {
            ClientPlayNetworking.send(new ActionPayload("{\"a\":\"__closed\"}"));
        }
        super.removed();
    }

    @Override
    public boolean shouldPause() {
        return false;
    }

    // ── render ────────────────────────────────────────────────────────────

    @Override
    public void render(DrawContext context, int mouseX, int mouseY, float delta) {
        if (browser == null) return;
        RenderSystem.disableDepthTest();
        RenderSystem.enableBlend();
        RenderSystem.setShader(ShaderProgramKeys.POSITION_TEX_COLOR);
        RenderSystem.setShaderTexture(0, browser.getRenderer().getTextureID());
        BufferBuilder buf = Tessellator.getInstance().begin(VertexFormat.DrawMode.QUADS, VertexFormats.POSITION_TEXTURE_COLOR);
        buf.vertex(0, height, 0).texture(0f, 1f).color(255, 255, 255, 255);
        buf.vertex(width, height, 0).texture(1f, 1f).color(255, 255, 255, 255);
        buf.vertex(width, 0, 0).texture(1f, 0f).color(255, 255, 255, 255);
        buf.vertex(0, 0, 0).texture(0f, 0f).color(255, 255, 255, 255);
        BufferRenderer.drawWithGlobalProgram(buf.end());
        RenderSystem.setShaderTexture(0, 0);
        RenderSystem.enableDepthTest();
    }

    @Override
    public void renderBackground(DrawContext context, int mouseX, int mouseY, float delta) {
        // 背景は Chromium 側 (半透明) が描くので何もしない
    }

    // ── input → Chromium (座標はフレームバッファのピクセルに変換) ───────────

    private int px(double v) {
        return (int) Math.round(v * MinecraftClient.getInstance().getWindow().getScaleFactor());
    }

    @Override
    public void mouseMoved(double mouseX, double mouseY) {
        if (browser != null) browser.sendMouseMove(px(mouseX), px(mouseY));
        super.mouseMoved(mouseX, mouseY);
    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        if (browser != null) {
            browser.setFocus(true);
            browser.sendMouseMove(px(mouseX), px(mouseY));
            browser.sendMousePress(px(mouseX), px(mouseY), button);
        }
        return true;
    }

    @Override
    public boolean mouseReleased(double mouseX, double mouseY, int button) {
        if (browser != null) browser.sendMouseRelease(px(mouseX), px(mouseY), button);
        return true;
    }

    @Override
    public boolean mouseDragged(double mouseX, double mouseY, int button, double dx, double dy) {
        if (browser != null) browser.sendMouseMove(px(mouseX), px(mouseY));
        return true;
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double horizontal, double vertical) {
        if (browser != null) browser.sendMouseWheel(px(mouseX), px(mouseY), vertical, 0);
        return true;
    }
}
