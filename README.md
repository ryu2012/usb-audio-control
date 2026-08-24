# USB Audio Control

Androidの開発者向けオプション「USBオーディオルーティングを無効化」を切り替え、現在のメディア出力先を確認するための小さなアプリです。

USBプラグと音波を組み合わせた専用のアダプティブ・ランチャーアイコンを同梱しています。

現在のメディア出力先を表示するホーム画面ウィジェットも同梱しています。ウィジェット内の「更新」で即時取得でき、自動更新はAndroidの仕様に合わせて30分間隔です。Samsung DeXのホーム画面へのウィジェット配置はOne UI 8（Android 16）以降で利用できます。

## 動作条件

- Android 6.0（API 23）以上
- 現在のメディア出力先の正確な表示: Android 13（API 33）以上
- 設定の変更には、PCからADBで `WRITE_SECURE_SETTINGS` を1回付与する必要があります

## ビルド

1. Android Studioでこのフォルダーを開きます。
2. Android SDK 35とJDK 17を使用し、Gradle 9.5以降を選んでGradle Syncを実行します。
3. 実機にアプリをインストールします。

この配布物にはGradle Wrapperのバイナリを含めていません。Android Studio Quail 3のGradle設定を使用できます。

## 初回セットアップ

実機で「開発者向けオプション」と「USBデバッグ」を有効にし、PCから次を実行します。

```shell
adb shell pm grant dev.ryohanzawa.usbaudiocontrol android.permission.WRITE_SECURE_SETTINGS
```

その後アプリに戻り、「状態を更新」を押します。この権限は通常の実行時権限ダイアログでは付与できません。アプリの再インストール後は、再度コマンドを実行してください。

## 仕様上の注意

- 設定値はAndroid内部のSecure Settingsにある `usb_audio_automatic_routing_disabled`（`1` = 無効化ON、`0` = 無効化OFF）を使用します。
- Android 13以降は `AudioManager.getAudioDevicesForAttributes()` でメディア用途の出力先を取得します。
- Android 12以前は現在のメディアルートを取得する公開APIがないため、接続中の出力候補のみ表示します。
- 一部メーカー端末では、独自仕様によって設定変更が反映されない場合があります。
