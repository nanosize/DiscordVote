[![GitHub release](https://img.shields.io/github/v/release/noasaba/ResourceGenerator?include_prereleases)](https://github.com/nanosize/DiscordVote/releases)
[![License: GPL v3](https://img.shields.io/badge/License-GPLv3-blue.svg)](https://www.gnu.org/licenses/gpl-3.0)
[![GitHub issues](https://img.shields.io/github/issues/nanosize/DiscordVote)](https://github.com/nanosize/DiscordVote/issues)
[![Last Commit](https://img.shields.io/github/last-commit/nanosize/DiscordVote)](https://github.com/nanosize/DiscordVote/commits)

# DiscordVote
ディスコードで、投票をconfig.ymlの内容に基づいて作成することができるソフトウェアです。<br>
また、結果に応じて投票終了後に送信するメッセージを変えることができます。

## 使い方
まず最初にパッケージから`vote-x.x.jar`をダウンロードし、適当な場所にそのファイルを設置する。
(できれば競合を防ぐためにこれ専用のディレクトリを作成してください。)<br>
とりあえず一度実行してください。そうするとconfigファイルが作成されますので、中身を編集してください。

## Placeholder
用意されているPlaceholderは
```
%month%
%date%
```
の二つです。これらをcontentの中などに入れると自動で実行時点の日時と入れ替わります。
## 使い方の応用
- 自宅サーバーやVPSのcrontabを使用して、一定間隔で投票を行う。
- [render](https://render.com/)やAWS Lambdaを使用して一定間隔で行う。

## config.yml
configファイルで指定した投票を実行することができます。yml形式で記述してください。

discordのtokenには
[discord developers](https://discord.com/developers/applications)
でアプリを作成し、発行されたtokenを貼り付けてください
tokenを発行するにあたっての参考サイトは[こちら](https://qiita.com/23tas9/items/8141aa674f1f7d71f529)
 ```
discord:
  token: "YOUR_DISCORD_BOT_TOKEN"
  channelId: "DISCORD_CHANNEL_ID"

poll:
  # 投票の持続時間（例："30s", "1h", "3d", "1w", "2w" など）
  duration: "1h"

  # 投票の質問文（%month%、%date% などのプレースホルダーを置換可能）
  question: "本日の投票 (%month%/%date%) です。どちらを選びますか？"

  # 投票開始直前に送るメッセージ
  preMessage: "投票開始直前のお知らせです (%month%/%date%)"

  # 投票オプションの一覧
  #   content: 投票で表示する文字 or 絵文字
  #   endMessage: 最も得票数が多かった場合に送信するメッセージ
  #   emojiName: デフォルト絵文字の場合は name, カスタム絵文字の場合は id を指定する（後述）
  options:
    - content: "項目1"
      endMessage: "@everyone 投票結果は項目1でした"
      emojiName: "👍"   # デフォルト絵文字の場合 { "name": "👍" }
    - content: "項目2"
      endMessage: "@everyone 投票結果は項目2でした"
      emojiName: "👎"
```

