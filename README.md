# HimaCasino

Purpur 1.21.1 向け Display Entity ベースのカジノプラグイン。  
スロット・ルーレット・HIGH & LOW・HORSE WHEEL・ブラックジャックの 5 種類のゲームを収録。

---

## 動作環境

| 項目 | バージョン |
|------|-----------|
| サーバー | Purpur 1.21.1 (Paper 互換) |
| API バージョン | 1.21 |
| Java | 21 以上 |
| 依存プラグイン (任意) | Vault + 経済プラグイン |

Vault が導入されていない場合は通貨機能が無効になりますが、ゲーム自体は動作します。

---

## インストール

1. `HimaCasino-1.0.0.jar` を `plugins/` フォルダに配置する。
2. サーバーを起動または `/reload` を実行する。
3. `plugins/HimaCasino/config.yml` で各種設定を行う。

---

## コマンド一覧

エイリアス: `/hc`

| コマンド | 説明 | 権限 |
|----------|------|------|
| `/casino` | ヘルプを表示 | himacasino.play |
| `/casino help [game]` | ゲーム詳細ヘルプを表示 | himacasino.play |
| `/casino roulette` | ルーレット UI を開く | himacasino.play |
| `/casino highlow` | HIGH & LOW を開始 | himacasino.play |
| `/casino blackjack` | ブラックジャックを開始 | himacasino.play |
| `/casino setting <1-6>` | スロットのデフォルト設定を変更 | himacasino.admin |
| `/casino setmachine <roulette\|horsewheel>` | 注視しているブロックにマシンを登録 | himacasino.admin |
| `/casino delmachine` | 注視しているブロックのマシンを削除 | himacasino.admin |

### ヘルプコマンド

```
/casino help slots
/casino help roulette
/casino help highlow
/casino help horsewheel
/casino help blackjack
```

---

## 権限

| 権限ノード | 説明 | デフォルト |
|------------|------|-----------|
| `himacasino.play` | ゲームをプレイできる | 全員 |
| `himacasino.admin` | 管理者コマンドを使用できる | OP のみ |

---

## ゲーム説明

### スロット

`[slot]` と書かれた看板を右クリックするとプレイ開始。

- シンボルは 1〜7 の 7 種類
- 3 リールが順番に停止し、組み合わせで配当が決まる

| 組み合わせ | 配当 |
|-----------|------|
| 7-7-7 | 100x (ジャックポット) |
| 3 つ揃い (7 以外) | 10x |
| 7 が 2 つ | 5x |
| 7 が 1 つ | 2x |
| 2 つ揃い | 1.5x |

設定 1〜6 でシンボル 7 の出現率が変化 (`/casino setting` で変更)。

---

### ルーレット

`/casino roulette` またはルーレット台を右クリック。

- ヨーロピアンスタイル (0〜36)
- インベントリ UI でベット
- 複数の数字に同時ベット可能

| ベット種別 | 配当 |
|-----------|------|
| 単一数字 | 35:1 |
| 赤 / 黒 | 1:1 |
| 奇数 / 偶数 | 1:1 |

**設置方法**: `/casino setmachine roulette` でルーレット台を登録。

---

### HIGH & LOW

`/casino highlow` で開始。インベントリ UI でプレイ。

1. 現在のカード (1〜13) を確認
2. 次のカードが **HIGH (高い)** か **LOW (低い)** かを選択
3. 正解で配当、不正解でベット没収

連続して正解すると配当が積み重なる。勝利倍率は `config.yml` で変更可能 (デフォルト 1.9x)。

---

### HORSE WHEEL

設置されたホイールを右クリックして開始。

- 縦回転するブロックホイールが常時表示
- 上部の三角ポインターが指したマスで判定
- 6 色の馬にコインを賭ける

| 馬の色 | 倍率 |
|-------|------|
| 白 (WHITE) | 2x |
| 黄 (YELLOW) | 3x |
| 水色 (BLUE) | 5x |
| 緑 (GREEN) | 8x |
| 赤 (RED) | 10x |
| 金 (GOLD) | 20x |

**設置方法**: `/casino setmachine horsewheel` でホイール台を登録。

---

### ブラックジャック

`/casino blackjack` で開始。54 スロットのインベントリ UI でプレイ。
カード・ボタンはリソースパック (`BlackjackResourcePack.zip`) の CustomModelData
グラフィックで表示されます (未適用の場合は無地の紙アイテムとして表示されます)。

#### GUI レイアウト

| 行 | 内容 |
|----|------|
| 1 行目 (0-8) | 木目調フレーム (上部装飾) |
| 2 行目 (9-17) | ディーラー情報 + ディーラーの手札 (10-16) |
| 3 行目 (18-26) | ラウンド状況 / 結果表示 (22) |
| 4 行目 (27-35) | プレイヤーの手札 (28-34) + プレイヤー情報 |
| 5 行目 (36-44) | プレイヤー情報バッジ (40) |
| 6 行目 (45-53) | 木目調フレーム + アクションボタン (HIT=47 / STAND=48 / DOUBLE DOWN=49) |

背景はマス目のないフラットなダークグリーン (ラシャ) で、上下は木目調フレームです。

#### 基本ルール

- カードの合計を **21** に近づけ、ディーラーに勝つ
- カード値: A = 1 or 11、J/Q/K = 10、2〜10 = 額面通り
- ディーラーは合計 17 以上になるまでドローし続ける
- デッキは 52 枚をシャッフルして使用 (尽きたら自動的に再シャッフル)

#### 操作

| ボタン | 説明 |
|-------|------|
| HIT | カードを 1 枚引く |
| STAND | ターンを終了し、ディーラーへ |
| DOUBLE DOWN | ベットを 2 倍にして 1 枚引き、自動スタンド (最初の 2 枚のみ) |

#### 配当

| 結果 | 配当 |
|------|------|
| ブラックジャック (最初の 2 枚で 21) | 3:2 (1.5 倍獲得) |
| 通常勝利 | 1:1 (同額獲得) |
| タイ (プッシュ) | ベット返金 |
| 負け | ベット没収 |

---

## 設定ファイル (config.yml)

```yaml
# 通貨の表示名 (Vault 無効時は無視)
currency-symbol: "コイン"

# スロットマシン
slots:
  default-setting: 1        # デフォルト設定 (1〜6)
  min-bet: 10.0
  max-bet: 10000.0
  settings:
    1:
      weights: [30, 25, 20, 15, 5, 3, 2]   # 各シンボルの出現ウェイト
    # 2〜6 は同様
  payouts:
    7-7-7: 100.0
    3-of-a-kind: 10.0
    two-7s: 5.0
    one-7: 2.0
    two-of-a-kind: 1.5

# ルーレット
roulette:
  min-bet: 10.0
  max-bet: 10000.0
  spin-ticks: 200           # ホイールが回るチック数

# HIGH & LOW
highlow:
  min-bet: 10.0
  max-bet: 10000.0
  win-multiplier: 1.9       # 勝利時の倍率

# ブラックジャック
blackjack:
  min-bet: 10.0
  max-bet: 10000.0

# リソースパック (ブラックジャックのカードグラフィック配布用)
resource-pack:
  enabled: false
  url: ""              # BlackjackResourcePack.zip のホスト先 URL
  sha1: ""              # 任意: ZIP の SHA-1 ハッシュ
  prompt-message: "&aHimaCasino のカードグラフィックを表示するには、リソースパックの適用をお願いします。"
  force: false          # true でリソースパック拒否時に切断
```

---

## リソースパック

`resource-pack/` フォルダにブラックジャックのカード・ボタン・GUI 背景の
CustomModelData モデル一式が含まれています。配布用 ZIP は以下でビルドできます。

```bash
./scripts/package-resourcepack.sh
```

`target/BlackjackResourcePack.zip` が生成されます。任意の URL にホストし、
`config.yml` の `resource-pack.url` に設定して `resource-pack.enabled: true`
にすると、プレイヤー参加時に自動配布されます。

### カード CustomModelData 割当

ベースアイテムは `PAPER` です。

| ID範囲 / 値 | 割り当て内容 |
|------------|--------------|
| 1〜13 | スペード (A〜K) |
| 14〜26 | ハート (A〜K) |
| 27〜39 | ダイヤ (A〜K) |
| 40〜52 | クラブ (A〜K) |
| 53 | カード裏面 (ダウンカード) |
| 100 | HIT ボタン (緑) |
| 101 | STAND ボタン (赤) |
| 102 | DOUBLE DOWN ボタン (黄) |
| 90 | GUI 背景 (フラットなダークグリーン) |
| 91 | GUI 上下フレーム (木目調) |

> **Note:** スロットマシンの数字/フレーム表示 (旧 CMD 1〜12) およびルーレットの
> 盤面/ボール表示 (旧 CMD 20〜21) は、ブラックジャックの CMD 範囲 (1〜102) との
> 衝突を避けるため CMD 201〜213 / 220〜221 に再割当てしています。

---

## マシン管理

### ルーレット台・ホイール台の設置

1. 設置したいブロックを 5 ブロック以内で見つめる
2. `/casino setmachine roulette` または `/casino setmachine horsewheel` を実行
3. プレイヤーが右クリックするとゲームが開始される

### マシンの削除

1. 削除したいブロックを見つめる
2. `/casino delmachine` を実行

マシンの登録情報は `plugins/HimaCasino/machines.yml` に保存され、サーバー再起動後も維持される。

---

## ビルド方法

```bash
mvn package
```

`target/HimaCasino-1.0.0.jar` が生成される。

---

## ライセンス

MIT License
