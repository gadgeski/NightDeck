# NightDeck

深夜のオペレーションコンソールをイメージした、ミニマルなAndroidランチャー。

<!-- スクリーンショット or 動画GIFをここに -->

## Features
- 等幅フォントのテキストリストでアプリを表示
- スプリングアニメーションで行に吸い付くカーソル
- インクリメンタル検索
- お気に入りを最大6件までピン留め（DataStore）
- 時刻・日付・バッテリー残量の表示

## Tech Stack
Kotlin / Jetpack Compose / Coroutines & Flow / DataStore
minSdk 26 / targetSdk 37 / AGP 9

## Development Notes
初期コードはAIに生成させ、その後の検証・修正を自分で行いました。
- AndroidManifestのルートタグ欠落を修正
- AGP 8.7（API 35まで対応）とtargetSdk 37の不整合を見つけ、AGP 9へ移行
- バージョンカタログの重複・バージョン不整合を整理
- BroadcastReceiverを `RECEIVER_NOT_EXPORTED` に変更（偽装ブロードキャスト対策）
- アプリ一覧の再読み込みが競合しないよう、前回のジョブをキャンセルする処理を追加