# HimaCasino

Fabric 用カジノ mod。**プレイ画面を Chromium (MCEF) の HTML で描画します。**
スロット・ルーレット・HIGH & LOW・HORSE WHEEL・ブラックジャック・ポーカーの 6 種類のゲームを収録。

> もとは Paper/Purpur 向けプラグインでした。Fabric mod に作り替え、Inventory GUI +
> リソースパックの代わりに Chromium で GUI を描画するようになりました。

---

## 構成

| ゲーム | 開始方法 | プレイ画面 |
|--------|----------|-----------|
| スロット | スロット看板を右クリック | 看板の数字がぐるぐる回る (従来どおり) |
| ルーレット | `/casino roulette` / 卓を右クリック | ベット画面は Chromium、回転はワールド内の 3D 卓 |
| HORSE WHEEL | ホイールを右クリック | ベット画面は Chromium、回転はワールド内の 3D ホイール |
| HIGH & LOW | `/casino highlow` | Chromium |
| ブラックジャック | `/casino blackjack` | Chromium |
| ポーカー | `/casino poker` | Chromium |

ゲームの進行・乱数・精算はすべて**サーバー側**で行います。クライアントの画面は
サーバーが送る状態 (JSON) を描画し、ボタン操作を返すだけなので、改ざんしても結果は変えられません。

---

## 動作環境

| 項目 | バージョン |
|------|-----------|
| Minecraft | 1.21.4 |
| Fabric Loader | 0.16.10 以上 |
| Fabric API | 必須 |
| Java | 21 以上 |
| MCEF (Chromium) | **クライアントに必須** (`2.1.6-1.21.4`) |
| Common Economy API | サーバーに任意 (経済連携) |

- **サーバー**: Fabric Loader + Fabric API + HimaCasino
- **クライアント**: Fabric Loader + Fabric API + HimaCasino + **MCEF**
  (ランチャーで mod 一式を配布する前提です)
- 6 ゲームのうちスロット以外は Chromium 画面を使うため、クライアントに HimaCasino と MCEF が
  入っていないとプレイできません。その場合は案内メッセージが出ます。

> MCEF の最新版は 1.21.4 までのため、Minecraft のバージョンは 1.21.4 に合わせています。
> MCEF は初回起動時に Chromium 本体 (約 100〜200MB) をダウンロードします。

### 経済 (Vault の代わり)

Vault は Bukkit 専用のため使えません。代わりに [Common Economy API](https://modrinth.com/mod/common-economy-api)
に対応しています。対応する経済 mod があれば残高を扱い、**無ければ無料モード** (残高チェック・賭け金の
増減なし) で動作します。`config/hima-casino.json` の `economy.mode` で切り替えられます。

| `economy.mode` | 動作 |
|----------------|------|
| `auto` (既定) | Common Economy API があれば連携、無ければ無料モード |
| `free` | 常に無料モード |

`economy.currencyId` (例: `mymod:coins`) で使う通貨を指定できます。空なら最初の口座を使います。

---

## インストール

1. サーバーの `mods/` に `hima-casino-x.y.z.jar` と Fabric API を配置する。
2. クライアントの `mods/` に `hima-casino-x.y.z.jar`・Fabric API・MCEF を配置する。
3. サーバーを起動する。初回に `config/hima-casino.json` が生成される。

---

## コマンド一覧

エイリアス: `/hc`

| コマンド | 説明 | 権限 |
|----------|------|------|
| `/casino` | ヘルプを表示 | 全員 |
| `/casino help [game]` | ゲーム詳細ヘルプを表示 | 全員 |
| `/casino roulette` | 近くのルーレット卓で開始 | 全員 |
| `/casino highlow` | HIGH & LOW を開始 | 全員 |
| `/casino blackjack` | ブラックジャックを開始 | 全員 |
| `/casino poker` | ポーカーを開始 | 全員 |
| `/casino setting <1-6>` | スロットのデフォルト設定を変更 | OP (レベル 2) |
| `/casino setmachine <roulette\|horsewheel>` | 注視しているブロックにマシンを登録 | OP |
| `/casino setmachine slots [ベット額] [設定]` | 注視している**看板**をスロットにする | OP |
| `/casino delmachine` | 注視しているブロックのマシンを削除 | OP |
| `/casino reload` | 設定を再読み込み | OP |

旧プラグインの「`[slot]` と書いた看板を置く」方式は、mod では看板の文字入力イベントが
標準で取れないため、**看板を置いてから `/casino setmachine slots` を実行する**方式に変わりました。

---

## 各ゲーム

### スロット
看板を右クリック。3 つのリールが順に止まります。設定 1〜6 で 7 の出現率が変わります。

| 役 | 倍率 |
|----|------|
| 7-7-7 | 100x |
| 3 つ揃い (7 以外) | 10x |
| 7 が 2 つ | 5x |
| 7 が 1 つ | 2x |
| 2 つ揃い (7 以外) | 1.5x |

### ルーレット
ヨーロピアン (0〜36)。チップを選んで盤面をクリックしてベットし、SPIN で回転します。
画面は回転中に自動で閉じ、ワールド内の卓で回るホイールとボールを見られます。
スピン前に画面を閉じるとベットは返金されます。

| ベット | 配当 |
|--------|------|
| 単一数字 | 35:1 |
| 赤 / 黒 | 1:1 |
| EVEN / ODD / 1–18 / 19–36 | 1:1 (複数の数字に分配) |
| ダース (1st/2nd/3rd 12) | 2:1 (複数の数字に分配) |

### HIGH & LOW
ディーラーのカードより大きい方を、伏せられた 2 枚から選びます。勝てば `highlow.winMultiplier` 倍 (既定 1.9)、
同じ数字はベット返却です。

### HORSE WHEEL
6 色の馬にチップを積んで SPIN。縦型 24 分割ホイールが止まった色で判定します。

| 馬 | 倍率 |
|----|------|
| 白 | 2x |
| 黄 | 3x |
| 青 | 5x |
| 緑 | 8x |
| 赤 | 10x |
| 金 | 20x |

### ブラックジャック
ディーラーは 17 でスタンド。ブラックジャックは 3:2、通常勝利 1:1、同点はベット返却。
HIT / STAND / DOUBLE DOWN (最初の 2 枚のみ)。

### ポーカー
ヘッズアップ・ファイブカードドロー vs ハウス。アンティ → 5 枚配布 → 好きな枚数を 1 回だけ交換 →
ベッティング (BET / CHECK / CALL / FOLD) → ショーダウン。

---

## 設定 (`config/hima-casino.json`)

```json
{
  "currencySymbol": "コイン",
  "economy": { "mode": "auto", "currencyId": "" },
  "slots": { "defaultSetting": 1, "minBet": 10.0, "maxBet": 10000.0, "settings": { ... }, "payouts": { ... } },
  "roulette": { "minBet": 10.0, "maxBet": 10000.0, "spinTicks": 200 },
  "highlow": { "minBet": 10.0, "maxBet": 10000.0, "winMultiplier": 1.9 },
  "blackjack": { "minBet": 10.0, "maxBet": 10000.0 },
  "poker": { "minBet": 10.0, "maxBet": 10000.0 }
}
```

設置したマシンは `config/hima-casino-machines.json` に保存されます。

---

## 開発

```bash
./gradlew build          # jar を build/libs に出力 (ユニットテスト込み)
./gradlew test           # ゲームロジックのユニットテスト
./gradlew runServer      # 開発用サーバー
./gradlew runClient      # 開発用クライアント (MCEF 込み)
./gradlew devServer      # Chromium GUI を実際のゲームロジックにブラウザから接続 (http://127.0.0.1:8765)
```

`devServer` を起動して `http://127.0.0.1:8765/api/start?game=blackjack` を叩いたあと
`http://127.0.0.1:8765/blackjack.html` を開くと、Minecraft なしで画面を確認できます
(`game` は `blackjack` / `highlow` / `poker` / `roulette` / `horsewheel`、`free=1` で無料モード)。

### ディレクトリ構成

```
src/main/java/com/himacasino/
  game/        ゲームロジック (Minecraft 非依存・ユニットテスト対象)
  server/      サーバー側の配線 (セッション・ホスト・スケジューラ・看板)
  table/       ルーレット卓・ホイールの 3D 表示 (Display エンティティ)
  machine/     設置マシンの登録と永続化
  economy/     Common Economy API 連携
  command/     /casino コマンド
  network/     サーバー⇄クライアントのパケット
src/client/java/com/himacasino/client/
  CasinoScreen.java   Chromium (MCEF) で HTML を描画する Screen
  PageScheme.java     himacasino:// で mod 内の HTML を配信
  ChromiumBridge.java JS ⇄ Java の橋渡し (cefQuery)
src/client/resources/assets/himacasino/html/
  *.html, casino.css, casino.js   各ゲームの画面
```

### 仕組み

1. サーバーがゲームを開始し、状態 JSON を `UiPayload` でクライアントへ送る
2. クライアントが `CasinoScreen` を開き、MCEF の Chromium で `himacasino://app/<game>.html` を表示
3. ページの `window.casino.setState(state)` が状態を描画
4. ボタン操作は `cefQuery` → `ActionPayload` でサーバーへ送られ、サーバーがゲームを進めて新しい状態を返す

---

## ライセンス

MIT
