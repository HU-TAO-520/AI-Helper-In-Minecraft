package com.qingcheng.zmusicbridge.protocol;

import com.google.gson.Gson;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

import java.nio.charset.StandardCharsets;
import java.util.UUID;

/**
 * ZMusic 通信协议编解码（服务端 -> 客户端 Mod）。
 *
 * <p>帧格式：{@code "ZMPK" + 版本字节(1) + UTF-8(JSON envelope)}，通道
 * {@code zmusic:packet}。envelope = {@code {"id":uuid,"type":str,"timestamp":ms,"data":{...}}}。
 * 该实现与 ZMusic v5（GPL-3.0，作者 真心）的 {@code PacketProtocol.kt} 对齐。
 */
public final class PacketCodec {

    public static final String CHANNEL = "zmusic:packet";
    public static final int VERSION = 1;
    public static final int MAX_JSON_BYTES = 32760 - 5;

    private static final byte[] MAGIC = new byte[] {'Z', 'M', 'P', 'K'};
    private static final Gson GSON = new Gson();

    private PacketCodec() {
    }

    /** 编码一条完整的 ZMusic 帧。 */
    public static byte[] encode(String type, JsonObject data) {
        JsonObject envelope = new JsonObject();
        envelope.addProperty("id", UUID.randomUUID().toString());
        envelope.addProperty("type", type);
        envelope.addProperty("timestamp", System.currentTimeMillis());
        envelope.add("data", data == null ? new JsonObject() : data);

        byte[] json = GSON.toJson(envelope).getBytes(StandardCharsets.UTF_8);
        byte[] out = new byte[5 + json.length];
        System.arraycopy(MAGIC, 0, out, 0, 4);
        out[4] = (byte) VERSION;
        System.arraycopy(json, 0, out, 5, json.length);
        return out;
    }

    /**
     * 解码来自客户端 Mod 的帧，返回 envelope 根对象；非法帧返回 {@code null}。
     */
    public static JsonObject decode(byte[] payload) {
        if (payload == null || payload.length < 5 || payload.length > 32760) {
            return null;
        }
        for (int i = 0; i < 4; i++) {
            if (payload[i] != MAGIC[i]) {
                return null;
            }
        }
        if ((payload[4] & 0xff) != VERSION) {
            return null;
        }
        String json = new String(payload, 5, payload.length - 5, StandardCharsets.UTF_8);
        try {
            return JsonParser.parseString(json).getAsJsonObject();
        } catch (RuntimeException e) {
            return null;
        }
    }
}
