# 项目长期记忆

## FishGrabbingAssistant 打包/部署约定
- 部署改动时旧流程会**每次把整个 `fishgrab.jar` 完整复制一份**备份（`fishgrab.jar.bak_时间戳`，外加早期 `fishgrab.jar.bak2..32`、`fishgrab.jar.bak`）。曾因此堆积 99 份 ≈ 892MB，导致桌面 `FishGrabbingAssistant` 文件夹涨到 1.1GB。
- **【每次打包必做】** 重新打包 `fishgrab.jar` 后，**立即清除本次之前的所有旧 `fishgrab.jar.bak*`**（冗余文件），只保留最近 1 个备份（最多 2 个）。清理方式：用普通 `Move-Item` 把旧备份整体搬进 `C:\Users\caohua\AppData\Local\Temp\fishgrab_jar_backups_trash`（可恢复、不污染项目目录）；本机无法走系统回收站（见用户级记忆）。不要把清理留到事后。
- `runtime/`（~123MB）是给 `.exe` 用的独立 JRE，**必须保留**；`fishgrab.jar` 内部只含 `production/test/*.class`.`META-INF`，没有重复打包 JRE。
- 重新打包命令：`"/c/Program Files/Java/jdk-26.0.1/bin/javac" -d build -encoding UTF-8 src/*.java` 后 `jar uf`（需 `MSYS_NO_PATHCONV=1` 避免路径被 Git Bash 改写）。

## 出牌提示闪烁（UNO）
- 轮到玩家且有能出的牌→手牌可出牌亮春绿(BLINK_GLOW 0x8cff5a)填充+描边，alpha 随 `blink()`（周期650ms）脉冲；一张能出的都没有→摸牌堆闪烁。`blink()` 由 `spinTimer`(45ms repaint) 驱动，无需新计时器。`myTurnNoPlayable()` 判无牌可出。
