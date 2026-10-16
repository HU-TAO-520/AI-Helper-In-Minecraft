package com.qingcheng.songmod;

import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

import java.util.List;

/**
 * 游戏内「点歌台」界面：
 * <ul>
 *   <li>输入歌名 / 网易云链接 → 点「点歌」（机器人解析后全服播放）；</li>
 *   <li>显示**正在播放**与**我点过的歌**；</li>
 *   <li>填**自己的网易云 Cookie**（机器人不在线时本地兜底用）；</li>
 *   <li>「停止」按钮。</li>
 * </ul>
 * 打开方式：按键（默认 K，可在「按键设置 → 点歌 Mod」改），或在聊天里发 {@code [点歌]台}。
 */
public class SongScreen extends Screen {

    private EditBox queryBox;
    private EditBox cookieBox;

    public SongScreen() {
        super(Component.literal("点歌台"));
    }

    @Override
    protected void init() {
        int cx = this.width / 2;

        this.queryBox = new EditBox(this.font, cx - 170, 44, 250, 20,
                Component.literal("歌名 / 网易云链接"));
        this.queryBox.setMaxLength(160);
        this.addRenderableWidget(this.queryBox);

        this.addRenderableWidget(Button.builder(Component.literal("点歌"), b -> this.sendRequest())
                .bounds(cx + 90, 44, 70, 20).build());
        this.addRenderableWidget(Button.builder(Component.literal("停止"), b -> this.sendStop())
                .bounds(cx + 90, 68, 70, 20).build());

        // 自己的网易云 Cookie（机器人不在线时本地兜底用）
        this.cookieBox = new EditBox(this.font, cx - 170, 100, 250, 20,
                Component.literal("网易云 Cookie（含 MUSIC_U）"));
        this.cookieBox.setMaxLength(400);
        this.cookieBox.setValue(SongModClient.neteaseCookie());
        this.addRenderableWidget(this.cookieBox);
        this.addRenderableWidget(Button.builder(Component.literal("保存 Cookie"), b -> {
            SongModClient.setNeteaseCookie(this.cookieBox.getValue());
        }).bounds(cx + 90, 100, 100, 20).build());

        this.addRenderableWidget(Button.builder(Component.literal("关闭"), b -> this.onClose())
                .bounds(cx - 35, this.height - 34, 70, 20).build());
    }

    private void sendRequest() {
        String query = this.queryBox.getValue() == null ? "" : this.queryBox.getValue().trim();
        if (query.isEmpty()) {
            return;
        }
        SongModClient.sendChat(SongModClient.trigger() + query);
        SongModClient.addMyRequest(query);
        this.queryBox.setValue("");
    }

    private void sendStop() {
        SongModClient.sendChat(SongModClient.trigger() + SongModClient.stopWord());
    }

    /** 画一行居中的字（自己算宽度，避开不同版本 drawCenteredString 的签名差异）。 */
    private void centerText(GuiGraphics gui, String text, int y, int color) {
        gui.drawString(this.font, text, (this.width - this.font.width(text)) / 2, y, color);
    }

    @Override
    public void render(GuiGraphics gui, int mouseX, int mouseY, float delta) {
        super.render(gui, mouseX, mouseY, delta);
        try {
            centerText(gui, "点歌台", 16, 0xFFFFFF);
            centerText(gui, "输入歌名/链接 → 点「点歌」；播放由机器人（会员）解析后全服播放",
                    28, 0x888888);

            int y = 138;
            String cur = SongModClient.currentLabel();
            gui.drawString(this.font, "正在播放：" + (cur == null || cur.isEmpty() ? "（空闲）" : cur),
                    12, y, 0x66FF66);
            y += 16;
            gui.drawString(this.font, "我点过的（机器人队列会依次播放）：", 12, y, 0xAAAAAA);
            y += 12;
            List<String> history = SongModClient.myHistory();
            int shown = Math.min(history.size(), 10);
            for (int i = 0; i < shown; i++) {
                gui.drawString(this.font, "- " + history.get(history.size() - 1 - i), 16, y, 0xDDDDDD);
                y += 12;
            }
            gui.drawString(this.font, "打开按键：" + SongModClient.openKeyName()
                    + "（「按键设置 → 点歌 Mod」可改；也可在聊天里发 " + SongModClient.trigger() + "台）",
                    12, this.height - 52, 0x888888);
            gui.drawString(this.font, "Cookie 只填自己的（机器人不在线时才用于本地兜底）",
                    12, this.height - 40, 0x888888);
        } catch (Throwable ignored) {
            // 渲染出错也不要崩
        }
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }
}
