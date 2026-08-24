package dev.ryohanzawa.usbaudiocontrol;

import android.Manifest;
import android.app.Activity;
import android.app.AlertDialog;
import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.database.ContentObserver;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.media.AudioAttributes;
import android.media.AudioDeviceCallback;
import android.media.AudioDeviceInfo;
import android.media.AudioManager;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.provider.Settings;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.Switch;
import android.widget.TextView;
import android.widget.Toast;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;

public final class MainActivity extends Activity {
    private static final String SETTING_KEY = "usb_audio_automatic_routing_disabled";
    private static final String ADB_COMMAND = "adb shell pm grant dev.ryohanzawa.usbaudiocontrol android.permission.WRITE_SECURE_SETTINGS";

    private final Handler mainHandler = new Handler(Looper.getMainLooper());
    private AudioManager audioManager;
    private Switch routingSwitch;
    private TextView routingSummary;
    private TextView permissionBadge;
    private TextView activeDeviceName;
    private TextView activeDeviceDetails;
    private LinearLayout deviceList;
    private boolean bindingSwitch;

    private final Runnable periodicAudioRefresh = new Runnable() {
        @Override public void run() {
            refreshAudioDevices();
            mainHandler.postDelayed(this, 2000);
        }
    };

    private final AudioDeviceCallback audioDeviceCallback = new AudioDeviceCallback() {
        @Override public void onAudioDevicesAdded(AudioDeviceInfo[] addedDevices) {
            refreshAudioDevices();
            AudioRouteWidgetProvider.updateAll(MainActivity.this);
        }
        @Override public void onAudioDevicesRemoved(AudioDeviceInfo[] removedDevices) {
            refreshAudioDevices();
            AudioRouteWidgetProvider.updateAll(MainActivity.this);
        }
    };

    private final ContentObserver settingObserver = new ContentObserver(mainHandler) {
        @Override public void onChange(boolean selfChange, Uri uri) { refreshSetting(); }
    };

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        audioManager = (AudioManager) getSystemService(AUDIO_SERVICE);
        setContentView(buildContent());
    }

    @Override
    protected void onStart() {
        super.onStart();
        audioManager.registerAudioDeviceCallback(audioDeviceCallback, mainHandler);
        getContentResolver().registerContentObserver(
                Settings.Secure.getUriFor(SETTING_KEY), false, settingObserver);
        refreshAll();
        AudioRouteWidgetProvider.updateAll(this);
        mainHandler.postDelayed(periodicAudioRefresh, 2000);
    }

    @Override
    protected void onStop() {
        getContentResolver().unregisterContentObserver(settingObserver);
        audioManager.unregisterAudioDeviceCallback(audioDeviceCallback);
        mainHandler.removeCallbacks(periodicAudioRefresh);
        super.onStop();
    }

    private View buildContent() {
        int navy = Color.rgb(8, 19, 31);
        ScrollView scroll = new ScrollView(this);
        scroll.setFillViewport(true);
        scroll.setBackgroundColor(navy);

        LinearLayout root = column();
        root.setPadding(dp(20), dp(22), dp(20), dp(32));
        scroll.addView(root, matchWrap());

        TextView eyebrow = text("AUDIO UTILITY", 12, Color.rgb(78, 215, 200));
        eyebrow.setTypeface(Typeface.DEFAULT_BOLD);
        eyebrow.setLetterSpacing(0.18f);
        root.addView(eyebrow);

        TextView title = text("USB Audio\nControl", 34, Color.rgb(243, 247, 250));
        title.setTypeface(Typeface.DEFAULT, Typeface.BOLD);
        title.setLineSpacing(0, 0.92f);
        root.addView(title, margins(matchWrap(), 0, 8, 0, 24));

        LinearLayout routeCard = card();
        root.addView(routeCard, margins(matchWrap(), 0, 0, 0, 16));

        LinearLayout switchRow = row();
        TextView switchTitle = text("USBオーディオルーティングを無効化", 17, Color.WHITE);
        switchTitle.setTypeface(Typeface.DEFAULT_BOLD);
        switchRow.addView(switchTitle, weighted());
        routingSwitch = new Switch(this);
        routingSwitch.setShowText(false);
        switchRow.addView(routingSwitch);
        routeCard.addView(switchRow, matchWrap());

        routingSummary = text("設定を確認しています…", 14, Color.rgb(168, 182, 196));
        routingSummary.setLineSpacing(dp(3), 1f);
        routeCard.addView(routingSummary, margins(matchWrap(), 0, 12, 0, 0));

        permissionBadge = text("権限を確認中", 12, Color.rgb(168, 182, 196));
        permissionBadge.setTypeface(Typeface.DEFAULT_BOLD);
        permissionBadge.setGravity(Gravity.CENTER);
        permissionBadge.setPadding(dp(10), dp(6), dp(10), dp(6));
        routeCard.addView(permissionBadge, margins(wrapWrap(), 0, 14, 0, 0));

        routingSwitch.setOnCheckedChangeListener((buttonView, isChecked) -> {
            if (!bindingSwitch) writeRoutingSetting(isChecked);
        });

        LinearLayout outputCard = card();
        root.addView(outputCard, margins(matchWrap(), 0, 0, 0, 16));
        outputCard.addView(sectionLabel("現在のメディア出力先"));
        activeDeviceName = text("確認しています…", 24, Color.WHITE);
        activeDeviceName.setTypeface(Typeface.DEFAULT_BOLD);
        outputCard.addView(activeDeviceName, margins(matchWrap(), 0, 14, 0, 0));
        activeDeviceDetails = text("", 13, Color.rgb(168, 182, 196));
        activeDeviceDetails.setLineSpacing(dp(3), 1f);
        outputCard.addView(activeDeviceDetails, margins(matchWrap(), 0, 6, 0, 0));

        LinearLayout devicesCard = card();
        root.addView(devicesCard, margins(matchWrap(), 0, 0, 0, 16));
        devicesCard.addView(sectionLabel("接続中の出力デバイス"));
        deviceList = column();
        devicesCard.addView(deviceList, margins(matchWrap(), 0, 10, 0, 0));

        Button refresh = new Button(this);
        refresh.setText("状態を更新");
        refresh.setTextColor(Color.rgb(8, 19, 31));
        refresh.setTextSize(15);
        refresh.setTypeface(Typeface.DEFAULT_BOLD);
        refresh.setAllCaps(false);
        refresh.setBackground(roundRect(Color.rgb(78, 215, 200), 14));
        refresh.setOnClickListener(v -> refreshAll());
        root.addView(refresh, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, dp(52)));

        TextView note = text(
                "※ Android 13以降では、メディア用に選択される出力先を表示します。Android 12以前では公開APIの制限により、接続中の出力候補を表示します。",
                12, Color.rgb(128, 147, 164));
        note.setLineSpacing(dp(3), 1f);
        root.addView(note, margins(matchWrap(), 0, 14, 0, 0));
        return scroll;
    }

    private void refreshAll() {
        refreshSetting();
        refreshAudioDevices();
    }

    private void refreshSetting() {
        boolean disabled = Settings.Secure.getInt(getContentResolver(), SETTING_KEY, 0) == 1;
        bindingSwitch = true;
        routingSwitch.setChecked(disabled);
        bindingSwitch = false;

        if (disabled) {
            routingSummary.setText("ON：USB機器を接続しても、システム音声は自動でUSBへ切り替わりません。");
        } else {
            routingSummary.setText("OFF：USBオーディオ機器の接続時に、システムが出力先を自動で切り替えます。");
        }

        boolean granted = hasSecureSettingsPermission();
        permissionBadge.setText(granted ? "● 変更権限あり" : "● ADB権限が必要");
        permissionBadge.setTextColor(granted ? Color.rgb(78, 215, 200) : Color.rgb(255, 190, 103));
        permissionBadge.setBackground(roundRect(
                granted ? Color.rgb(24, 61, 64) : Color.rgb(65, 48, 31), 30));
    }

    private void writeRoutingSetting(boolean disabled) {
        if (!hasSecureSettingsPermission()) {
            refreshSetting();
            showPermissionDialog();
            return;
        }
        try {
            boolean success = Settings.Secure.putInt(
                    getContentResolver(), SETTING_KEY, disabled ? 1 : 0);
            if (!success) throw new IllegalStateException("設定の保存に失敗しました");
            refreshSetting();
        } catch (SecurityException | IllegalStateException error) {
            refreshSetting();
            Toast.makeText(this, "設定を変更できませんでした", Toast.LENGTH_LONG).show();
        }
    }

    private boolean hasSecureSettingsPermission() {
        return checkSelfPermission(Manifest.permission.WRITE_SECURE_SETTINGS)
                == PackageManager.PERMISSION_GRANTED;
    }

    private void showPermissionDialog() {
        new AlertDialog.Builder(this)
                .setTitle("最初にADB権限が必要です")
                .setMessage("PCに接続し、次のコマンドを1回だけ実行してください。\n\n" + ADB_COMMAND)
                .setPositiveButton("コマンドをコピー", (dialog, which) -> {
                    ClipboardManager clipboard = (ClipboardManager) getSystemService(CLIPBOARD_SERVICE);
                    clipboard.setPrimaryClip(ClipData.newPlainText("ADB command", ADB_COMMAND));
                    Toast.makeText(this, "コピーしました", Toast.LENGTH_SHORT).show();
                })
                .setNeutralButton("開発者向け設定", (dialog, which) -> {
                    try {
                        startActivity(new Intent(Settings.ACTION_APPLICATION_DEVELOPMENT_SETTINGS));
                    } catch (Exception ignored) {
                        startActivity(new Intent(Settings.ACTION_SETTINGS));
                    }
                })
                .setNegativeButton("閉じる", null)
                .show();
    }

    private void refreshAudioDevices() {
        List<AudioDeviceInfo> connected = Arrays.asList(
                audioManager.getDevices(AudioManager.GET_DEVICES_OUTPUTS));
        List<AudioDeviceInfo> active = getMediaOutputDevices(connected);

        if (active.isEmpty()) {
            activeDeviceName.setText("取得できませんでした");
            activeDeviceDetails.setText(Build.VERSION.SDK_INT >= 33
                    ? "システムから出力先が返されませんでした。"
                    : "Android 12以前では接続候補のみ確認できます。");
        } else {
            activeDeviceName.setText(joinDeviceNames(active));
            AudioDeviceInfo first = active.get(0);
            activeDeviceDetails.setText(typeName(first.getType()) + addressText(first));
        }

        deviceList.removeAllViews();
        if (connected.isEmpty()) {
            deviceList.addView(text("出力デバイスが見つかりません", 14, Color.rgb(168, 182, 196)));
            return;
        }
        for (int i = 0; i < connected.size(); i++) {
            AudioDeviceInfo device = connected.get(i);
            boolean selected = containsId(active, device.getId());
            LinearLayout item = row();
            item.setGravity(Gravity.CENTER_VERTICAL);
            item.setPadding(0, dp(10), 0, dp(10));

            TextView icon = text(iconFor(device.getType()), 20,
                    selected ? Color.rgb(78, 215, 200) : Color.rgb(168, 182, 196));
            icon.setGravity(Gravity.CENTER);
            item.addView(icon, new LinearLayout.LayoutParams(dp(34), dp(34)));

            LinearLayout labels = column();
            TextView name = text(deviceName(device), 15, Color.WHITE);
            name.setTypeface(Typeface.DEFAULT_BOLD);
            labels.addView(name);
            labels.addView(text(typeName(device.getType()), 12, Color.rgb(168, 182, 196)));
            item.addView(labels, margins(weighted(), 10, 0, 0, 0));

            if (selected) {
                TextView badge = text("使用中", 11, Color.rgb(78, 215, 200));
                badge.setTypeface(Typeface.DEFAULT_BOLD);
                item.addView(badge);
            }
            deviceList.addView(item);

            if (i < connected.size() - 1) {
                View divider = new View(this);
                divider.setBackgroundColor(Color.rgb(42, 62, 79));
                deviceList.addView(divider, new LinearLayout.LayoutParams(
                        ViewGroup.LayoutParams.MATCH_PARENT, dp(1)));
            }
        }
    }

    private List<AudioDeviceInfo> getMediaOutputDevices(List<AudioDeviceInfo> fallback) {
        if (Build.VERSION.SDK_INT >= 33) {
            AudioAttributes media = new AudioAttributes.Builder()
                    .setUsage(AudioAttributes.USAGE_MEDIA)
                    .setContentType(AudioAttributes.CONTENT_TYPE_MUSIC)
                    .build();
            try {
                return audioManager.getAudioDevicesForAttributes(media);
            } catch (SecurityException ignored) {
                return new ArrayList<>();
            }
        }
        // No public API exposes the system's current media route before API 33.
        // Showing a single connected output as "active" would be misleading.
        return new ArrayList<>();
    }

    private boolean containsId(List<AudioDeviceInfo> devices, int id) {
        for (AudioDeviceInfo device : devices) if (device.getId() == id) return true;
        return false;
    }

    private String joinDeviceNames(List<AudioDeviceInfo> devices) {
        List<String> names = new ArrayList<>();
        for (AudioDeviceInfo device : devices) names.add(deviceName(device));
        return android.text.TextUtils.join(" + ", names);
    }

    private String deviceName(AudioDeviceInfo device) {
        CharSequence product = device.getProductName();
        String value = product == null ? "" : product.toString().trim();
        return value.isEmpty() ? typeName(device.getType()) : value;
    }

    private String addressText(AudioDeviceInfo device) {
        if (Build.VERSION.SDK_INT < 28) return "";
        String address = device.getAddress();
        return address == null || address.isEmpty() ? "" : "\n" + address;
    }

    private String typeName(int type) {
        switch (type) {
            case AudioDeviceInfo.TYPE_BUILTIN_SPEAKER: return "本体スピーカー";
            case AudioDeviceInfo.TYPE_BUILTIN_EARPIECE: return "本体受話口";
            case AudioDeviceInfo.TYPE_WIRED_HEADPHONES: return "有線ヘッドホン";
            case AudioDeviceInfo.TYPE_WIRED_HEADSET: return "有線ヘッドセット";
            case AudioDeviceInfo.TYPE_BLUETOOTH_A2DP: return "Bluetoothオーディオ";
            case AudioDeviceInfo.TYPE_BLUETOOTH_SCO: return "Bluetooth通話デバイス";
            case AudioDeviceInfo.TYPE_USB_DEVICE: return "USBオーディオデバイス";
            case AudioDeviceInfo.TYPE_USB_HEADSET: return "USBヘッドセット";
            case AudioDeviceInfo.TYPE_USB_ACCESSORY: return "USBアクセサリ";
            case AudioDeviceInfo.TYPE_HDMI: return "HDMI";
            case AudioDeviceInfo.TYPE_HDMI_ARC: return "HDMI ARC";
            case AudioDeviceInfo.TYPE_LINE_ANALOG: return "アナログライン出力";
            case AudioDeviceInfo.TYPE_LINE_DIGITAL: return "デジタルライン出力";
            default: return String.format(Locale.JAPAN, "オーディオデバイス (type %d)", type);
        }
    }

    private String iconFor(int type) {
        switch (type) {
            case AudioDeviceInfo.TYPE_BUILTIN_SPEAKER: return "●";
            case AudioDeviceInfo.TYPE_WIRED_HEADPHONES:
            case AudioDeviceInfo.TYPE_WIRED_HEADSET:
            case AudioDeviceInfo.TYPE_USB_HEADSET: return "◉";
            case AudioDeviceInfo.TYPE_BLUETOOTH_A2DP:
            case AudioDeviceInfo.TYPE_BLUETOOTH_SCO: return "◆";
            case AudioDeviceInfo.TYPE_USB_DEVICE:
            case AudioDeviceInfo.TYPE_USB_ACCESSORY: return "▣";
            default: return "○";
        }
    }

    private LinearLayout card() {
        LinearLayout view = column();
        view.setPadding(dp(18), dp(18), dp(18), dp(18));
        view.setBackground(roundRect(Color.rgb(16, 30, 44), 18));
        return view;
    }

    private LinearLayout column() {
        LinearLayout view = new LinearLayout(this);
        view.setOrientation(LinearLayout.VERTICAL);
        return view;
    }

    private LinearLayout row() {
        LinearLayout view = new LinearLayout(this);
        view.setOrientation(LinearLayout.HORIZONTAL);
        view.setGravity(Gravity.CENTER_VERTICAL);
        return view;
    }

    private TextView sectionLabel(String value) {
        TextView view = text(value, 12, Color.rgb(78, 215, 200));
        view.setTypeface(Typeface.DEFAULT_BOLD);
        view.setLetterSpacing(0.08f);
        return view;
    }

    private TextView text(String value, float sp, int color) {
        TextView view = new TextView(this);
        view.setText(value);
        view.setTextSize(sp);
        view.setTextColor(color);
        return view;
    }

    private GradientDrawable roundRect(int color, int radiusDp) {
        GradientDrawable drawable = new GradientDrawable();
        drawable.setColor(color);
        drawable.setCornerRadius(dp(radiusDp));
        return drawable;
    }

    private int dp(int value) {
        return Math.round(value * getResources().getDisplayMetrics().density);
    }

    private LinearLayout.LayoutParams matchWrap() {
        return new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
    }

    private LinearLayout.LayoutParams wrapWrap() {
        return new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
    }

    private LinearLayout.LayoutParams weighted() {
        return new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f);
    }

    private LinearLayout.LayoutParams margins(
            LinearLayout.LayoutParams params, int left, int top, int right, int bottom) {
        params.setMargins(dp(left), dp(top), dp(right), dp(bottom));
        return params;
    }
}
