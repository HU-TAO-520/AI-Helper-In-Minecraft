# ZMusicBridge（MC 点歌）

让玩家在游戏里用 **`[点歌]歌名`**（或 `/zmplay 歌名`）点歌，**服务端直接向客户端 ZMusic Mod 下发播放**。

- 播放：客户端装成熟的 **ZMusic Mod**（Fabric / Forge，见 [zmusic-mod](https://github.com/starhui-dev/zmusic-mod)）。
- 发送：这个插件自己按 ZMusic v5 的 **`zmusic:packet`** 协议当「服务端」，直接给客户端 Mod 发 `server.hello` / `server.play`。
  - ✅ **不需要** ZMusic 官方服务端插件
  - ✅ **不需要** ZMusic 云 API / `device_token`
  - ✅ 歌曲搜索是**自己写的**（网易云公开搜索接口 + 公开 https 音频直链），不依赖任何第三方 SDK

## 与「AI 助手」的关系

本目录是 Minecraft AI 助手项目里新增的**服务端插件**部分：mineflayer 机器人是客户端，客户端无法给别的客户端发插件消息，所以要让别人听到歌，必须有一个跑在**服务端进程**里的插件来发 `zmusic:packet`——就是这个插件。机器人（或任何玩家）在聊天里发 `[点歌]歌名` 即可触发。

## 环境要求

- 服务端：**Paper / Spigot / Purpur**（1.17 ~ 1.21+ 皆可，插件用 Java 17 字节码）
- 客户端：装了 **ZMusic Mod** 的玩家（版本要和服务端 MC 版本对应）
- 构建插件需要 **JDK 17+**（本机是 25，`--release 17` 编译）

## 构建

```bash
cd mc-music-bridge
./gradlew build          # Windows: gradlew.bat build
```

产物：`build/libs/ZMusicBridge-1.0.0.jar`，丢进服务端 `plugins/` 重启即可。

> 已内置 Gradle Wrapper；`gradle/wrapper/gradle-wrapper.properties` 里的下载源用的是**腾讯镜像**，国内快。想换回官方就把 `distributionUrl` 改回 `https://services.gradle.org/distributions/gradle-9.8.1-bin.zip`。

## 使用

| 方式 | 说明 |
| --- | --- |
| 聊天 `[点歌]告白气球` | 消息以触发词开头即点歌 |
| `/zmplay 告白气球` | 命令点歌 |
| `/zmstop` | 停止播放 |

- 默认**全服播放**：所有装了 ZMusic Mod 的在线玩家一起听（改 `broadcast: false` 则只给点歌的人放）。
- 没装 Mod 的玩家不受影响；插件会检测「完成握手的 Mod 玩家」并只给他们发播放。

## 配置 `config.yml`

```yaml
trigger: "[点歌]"        # 聊天触发词，随便改（比如改成 "[点歌]" / "点歌" / "!song"）
provider: "163"          # 目前是网易云：免登录 + 公开音频直链
broadcast: true          # true=全服；false=只给点歌的人
cooldown-seconds: 5      # 同一玩家点歌冷却
audio-headers:           # 客户端下载音频时带的请求头（网易云 CDN 需要 Referer）
  User-Agent: "Mozilla/5.0 ..."
  Referer: "https://music.163.com/"
```

改完用 `/reload confirm`（Paper）或重启生效；`/zmplay` 用的是当前内存里的配置，重载后需重启插件。

## 工作原理（协议）

ZMusic v5 插件消息通道 `zmusic:packet`，帧 = `"ZMPK"` + 版本字节 `1` + UTF-8 JSON：

```
{"id":"<uuid>","type":"<type>","timestamp":<ms>,"data":{...}}
```

本插件实现：

- 收到客户端 `client.hello` → 回 `server.hello`（`protocolVersion:1`），把该玩家标记为「Mod 就绪」；
- 点歌 → 搜歌 → 向就绪玩家发 `server.play`：

```json
{
  "requestId": "<uuid>", "mode": "replace",
  "song": { "id": "<songId>", "source": "163", "title": "...", "artists": ["..."], "album": "..." },
  "audio": { "type": "url", "url": "https://.../x.mp3", "headers": { "User-Agent": "...", "Referer": "..." } }
}
```

- `/zmstop` → 发 `server.stop`。

> 协议实现对齐 ZMusic v5（GPL-3.0，作者 真心）的 `PacketProtocol.kt` / `PlaybackService.kt` / 客户端 `ZMusicClient.kt` 的校验规则（UUID、`mode:"replace"`、`audio.type:"url"`、请求头黑名单等）。

## 已知限制

- 音源目前只有**网易云**。VIP / 版权受限歌曲拿不到直链时会播放失败（客户端会报 `playback_failed`/`audio_url_expired`）。
- 搜索结果取第一条，偶尔可能是翻唱版本；换个更精确的关键词即可。
- 需要玩家自己装对应版本的 ZMusic Mod；未装 Mod 的玩家听不到。

## License

Apache License 2.0，见仓库根目录 [LICENSE](../LICENSE)。
本插件是按 **ZMusic v5 的协议文档自行实现**的（未复制其代码）；ZMusic 本体为 GPL-3.0，与本插件的授权相互独立。
