package dev.ryohanzawa.usbaudiocontrol;

import android.app.PendingIntent;
import android.appwidget.AppWidgetManager;
import android.appwidget.AppWidgetProvider;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.media.AudioAttributes;
import android.media.AudioDeviceInfo;
import android.media.AudioManager;
import android.os.Build;
import android.widget.RemoteViews;

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Locale;

public final class AudioRouteWidgetProvider extends AppWidgetProvider {
    private static final String ACTION_REFRESH =
            "dev.ryohanzawa.usbaudiocontrol.action.REFRESH_WIDGET";

    @Override
    public void onUpdate(Context context, AppWidgetManager manager, int[] appWidgetIds) {
        for (int appWidgetId : appWidgetIds) updateWidget(context, manager, appWidgetId);
    }

    @Override
    public void onReceive(Context context, Intent intent) {
        super.onReceive(context, intent);
        if (ACTION_REFRESH.equals(intent.getAction())) updateAll(context);
    }

    public static void updateAll(Context context) {
        AppWidgetManager manager = AppWidgetManager.getInstance(context);
        int[] ids = manager.getAppWidgetIds(
                new ComponentName(context, AudioRouteWidgetProvider.class));
        for (int id : ids) updateWidget(context, manager, id);
    }

    private static void updateWidget(
            Context context, AppWidgetManager manager, int appWidgetId) {
        RemoteViews views = new RemoteViews(context.getPackageName(), R.layout.widget_audio_route);
        Route route = readMediaRoute(context);
        views.setTextViewText(R.id.widget_device_name, route.name);
        views.setTextViewText(R.id.widget_device_details, route.details);

        Intent refreshIntent = new Intent(context, AudioRouteWidgetProvider.class)
                .setAction(ACTION_REFRESH);
        PendingIntent refreshPendingIntent = PendingIntent.getBroadcast(
                context,
                appWidgetId,
                refreshIntent,
                PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);
        views.setOnClickPendingIntent(R.id.widget_refresh, refreshPendingIntent);

        Intent openIntent = new Intent(context, MainActivity.class)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TOP);
        PendingIntent openPendingIntent = PendingIntent.getActivity(
                context,
                appWidgetId,
                openIntent,
                PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);
        views.setOnClickPendingIntent(R.id.widget_root, openPendingIntent);
        manager.updateAppWidget(appWidgetId, views);
    }

    private static Route readMediaRoute(Context context) {
        String updated = new SimpleDateFormat("HH:mm", Locale.JAPAN).format(new Date());
        if (Build.VERSION.SDK_INT < 33) {
            return new Route(
                    "端末で確認してください",
                    "正確な取得はAndroid 13以降・更新 " + updated);
        }

        AudioManager audioManager = (AudioManager) context.getSystemService(Context.AUDIO_SERVICE);
        AudioAttributes attributes = new AudioAttributes.Builder()
                .setUsage(AudioAttributes.USAGE_MEDIA)
                .setContentType(AudioAttributes.CONTENT_TYPE_MUSIC)
                .build();
        try {
            List<AudioDeviceInfo> devices = audioManager.getAudioDevicesForAttributes(attributes);
            if (devices.isEmpty()) return new Route("出力先を取得できません", "更新 " + updated);

            List<String> names = new ArrayList<>();
            for (AudioDeviceInfo device : devices) names.add(deviceName(device));
            AudioDeviceInfo first = devices.get(0);
            return new Route(
                    android.text.TextUtils.join(" + ", names),
                    typeName(first.getType()) + "・更新 " + updated);
        } catch (SecurityException error) {
            return new Route("出力先を取得できません", "権限を確認してください・更新 " + updated);
        }
    }

    private static String deviceName(AudioDeviceInfo device) {
        CharSequence product = device.getProductName();
        String value = product == null ? "" : product.toString().trim();
        return value.isEmpty() ? typeName(device.getType()) : value;
    }

    private static String typeName(int type) {
        switch (type) {
            case AudioDeviceInfo.TYPE_BUILTIN_SPEAKER: return "本体スピーカー";
            case AudioDeviceInfo.TYPE_BUILTIN_EARPIECE: return "本体受話口";
            case AudioDeviceInfo.TYPE_WIRED_HEADPHONES: return "有線ヘッドホン";
            case AudioDeviceInfo.TYPE_WIRED_HEADSET: return "有線ヘッドセット";
            case AudioDeviceInfo.TYPE_BLUETOOTH_A2DP: return "Bluetoothオーディオ";
            case AudioDeviceInfo.TYPE_BLUETOOTH_SCO: return "Bluetooth通話デバイス";
            case AudioDeviceInfo.TYPE_USB_DEVICE: return "USBオーディオ";
            case AudioDeviceInfo.TYPE_USB_HEADSET: return "USBヘッドセット";
            case AudioDeviceInfo.TYPE_USB_ACCESSORY: return "USBアクセサリ";
            case AudioDeviceInfo.TYPE_HDMI: return "HDMI";
            case AudioDeviceInfo.TYPE_HDMI_ARC: return "HDMI ARC";
            case AudioDeviceInfo.TYPE_LINE_ANALOG: return "アナログライン出力";
            case AudioDeviceInfo.TYPE_LINE_DIGITAL: return "デジタルライン出力";
            default: return "オーディオ出力";
        }
    }

    private static final class Route {
        final String name;
        final String details;

        Route(String name, String details) {
            this.name = name;
            this.details = details;
        }
    }
}
