package com.example.popup;

import android.app.Activity;
import android.app.AlertDialog;
import android.app.Application;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.net.Uri;
import android.util.DisplayMetrics;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.ViewGroup;
import android.view.Window;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.Locale;
import java.util.concurrent.atomic.AtomicBoolean;

public final class AnnouncementPopup implements Application.ActivityLifecycleCallbacks {
    private static final String API = "https://lihansong.top/jar/api.php";
    private static final String PREFS = "announcement_popup_v1";
    private static final AtomicBoolean FETCHING = new AtomicBoolean(false);
    private static volatile boolean registered;
    private static volatile boolean shownInProcess;
    private final Application application;

    private AnnouncementPopup(Application app) {
        application = app;
    }

    public static void run(Context context) {
        if (context == null) return;
        Context appContext = context.getApplicationContext();
        if (!(appContext instanceof Application)) return;
        Application app = (Application) appContext;
        synchronized (AnnouncementPopup.class) {
            if (registered) return;
            registered = true;
            app.registerActivityLifecycleCallbacks(new AnnouncementPopup(app));
        }
    }

    @Override public void onActivityResumed(final Activity activity) {
        if (activity == null || activity.isFinishing() || shownInProcess) return;
        if (!FETCHING.compareAndSet(false, true)) return;
        shownInProcess = true;
        new Thread(new Runnable() {
            @Override public void run() {
                try {
                    JSONObject data = requestJson(API);
                    if (data == null || !enabled(data) || !withinDateWindow(data)) return;
                    if (!shouldShow(application, data)) return;

                    final String title = nonEmpty(firstString(data, "title", "公告通知"), "公告通知");
                    final String content = firstString(data, "content", "");
                    final ArrayList<Bitmap> images = loadImages(data.optJSONArray("images"));
                    final ArrayList<ButtonSpec> buttons = readButtons(data);

                    activity.runOnUiThread(new Runnable() {
                        @Override public void run() {
                            if (activity.isFinishing() || activity.isDestroyed()) return;
                            showDialog(activity, title, content, images, buttons);
                            markShown(application, dataFrequencyHolder);
                        }
                    });
                } catch (Throwable ignored) {
                } finally {
                    FETCHING.set(false);
                }
            }
        }, "announcement-fetch").start();
    }

    private static volatile String dataFrequencyHolder = "daily";

    private static JSONObject requestJson(String address) {
        HttpURLConnection connection = null;
        try {
            connection = (HttpURLConnection) new URL(address).openConnection();
            connection.setRequestMethod("GET");
            connection.setConnectTimeout(10000);
            connection.setReadTimeout(12000);
            connection.setUseCaches(false);
            connection.setInstanceFollowRedirects(true);
            connection.setRequestProperty("Accept", "application/json");
            connection.setRequestProperty("Cache-Control", "no-cache");
            int status = connection.getResponseCode();
            if (status != 200) return null;
            InputStream in = connection.getInputStream();
            ByteArrayOutputStream out = new ByteArrayOutputStream();
            byte[] buffer = new byte[4096];
            int count;
            while ((count = in.read(buffer)) != -1) out.write(buffer, 0, count);
            in.close();
            JSONObject object = new JSONObject(new String(out.toByteArray(), "UTF-8"));
            dataFrequencyHolder = object.optString("frequency", "daily");
            return object;
        } catch (Throwable ignored) {
            return null;
        } finally {
            if (connection != null) connection.disconnect();
        }
    }

    private static boolean enabled(JSONObject data) {
        Object value = data.opt("enabled");
        if (value instanceof Boolean) return ((Boolean) value).booleanValue();
        if (value instanceof Number) return ((Number) value).intValue() != 0;
        String text = value == null ? "0" : String.valueOf(value);
        return "1".equals(text) || "true".equalsIgnoreCase(text) || "yes".equalsIgnoreCase(text) || "on".equalsIgnoreCase(text);
    }

    private static boolean withinDateWindow(JSONObject data) {
        long now = System.currentTimeMillis();
        String start = data.optString("start_at", "").trim();
        String end = data.optString("end_at", "").trim();
        long startTime = parseDate(start);
        long endTime = parseDate(end);
        if (startTime > 0 && now < startTime) return false;
        if (endTime > 0 && now > endTime) return false;
        return true;
    }

    private static long parseDate(String value) {
        if (value == null || value.length() == 0) return 0;
        String[] patterns = new String[] {"yyyy-MM-dd HH:mm:ss", "yyyy-MM-dd'T'HH:mm:ss", "yyyy-MM-dd"};
        for (String pattern : patterns) {
            try {
                SimpleDateFormat format = new SimpleDateFormat(pattern, Locale.US);
                format.setLenient(false);
                Date date = format.parse(value);
                if (date != null) return date.getTime();
            } catch (Throwable ignored) {
            }
        }
        return 0;
    }

    private static boolean shouldShow(Context context, JSONObject data) {
        SharedPreferences prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE);
        String frequency = data.optString("frequency", "daily").trim().toLowerCase(Locale.US);
        if ("always".equals(frequency) || "every_launch".equals(frequency) || "每次".equals(frequency)) return true;
        if ("once".equals(frequency) || "only_once".equals(frequency) || "仅一次".equals(frequency)) {
            return !prefs.getBoolean("shown_once", false);
        }
        long last = prefs.getLong("last_shown", 0L);
        long now = System.currentTimeMillis();
        if ("interval".equals(frequency) || "every_n_days".equals(frequency)) {
            int days = data.optInt("interval_value", 1);
            if (days < 1) days = 1;
            return last == 0L || now - last >= days * 86400000L;
        }
        if ("hourly".equals(frequency)) return last == 0L || now - last >= 3600000L;
        if ("weekly".equals(frequency)) return last == 0L || now - last >= 604800000L;
        String today = new SimpleDateFormat("yyyyMMdd", Locale.US).format(new Date(now));
        return !today.equals(prefs.getString("last_day", ""));
    }

    private static void markShown(Context context, String frequency) {
        SharedPreferences.Editor editor = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit();
        editor.putLong("last_shown", System.currentTimeMillis());
        editor.putString("last_day", new SimpleDateFormat("yyyyMMdd", Locale.US).format(new Date()));
        if ("once".equalsIgnoreCase(frequency) || "only_once".equalsIgnoreCase(frequency) || "仅一次".equals(frequency)) {
            editor.putBoolean("shown_once", true);
        }
        editor.apply();
    }

    private static String firstString(JSONObject object, String key, String fallback) {
        String value = object.optString(key, fallback);
        return value == null || "null".equals(value) ? fallback : value;
    }

    private static String nonEmpty(String value, String fallback) {
        return value == null || value.trim().length() == 0 ? fallback : value;
    }

    private static ArrayList<Bitmap> loadImages(JSONArray urls) {
        ArrayList<Bitmap> result = new ArrayList<Bitmap>();
        if (urls == null) return result;
        int count = Math.min(urls.length(), 5);
        for (int i = 0; i < count; i++) {
            String address = urls.optString(i, "").trim();
            if (address.length() == 0) continue;
            Bitmap bitmap = downloadBitmap(resolveImageUrl(address));
            if (bitmap != null) result.add(bitmap);
        }
        return result;
    }

    private static String resolveImageUrl(String value) {
        if (value.startsWith("https://") || value.startsWith("http://")) return value;
        if (value.startsWith("/")) return "https://lihansong.top" + value;
        return "https://lihansong.top/jar/" + value;
    }

    private static Bitmap downloadBitmap(String address) {
        HttpURLConnection connection = null;
        try {
            connection = (HttpURLConnection) new URL(address).openConnection();
            connection.setConnectTimeout(8000);
            connection.setReadTimeout(10000);
            connection.setInstanceFollowRedirects(true);
            connection.setUseCaches(false);
            if (connection.getResponseCode() != 200) return null;
            InputStream in = connection.getInputStream();
            Bitmap bitmap = BitmapFactory.decodeStream(in);
            in.close();
            return bitmap;
        } catch (Throwable ignored) {
            return null;
        } finally {
            if (connection != null) connection.disconnect();
        }
    }

    private static ArrayList<ButtonSpec> readButtons(JSONObject data) {
        ArrayList<ButtonSpec> result = new ArrayList<ButtonSpec>();
        JSONArray array = data.optJSONArray("buttons");
        if (array != null) {
            for (int i = 0; i < array.length() && result.size() < 3; i++) {
                Object item = array.opt(i);
                if (item instanceof JSONObject) {
                    JSONObject button = (JSONObject) item;
                    String text = firstString(button, "text", "");
                    if (text.length() == 0) text = firstString(button, "title", "");
                    if (text.length() == 0) text = firstString(button, "name", "");
                    if (text.length() == 0) text = firstString(button, "label", "");
                    String url = firstString(button, "url", "");
                    if (url.length() == 0) url = firstString(button, "link", "");
                    if (url.length() == 0) url = firstString(button, "href", "");
                    if (text.trim().length() > 0) result.add(new ButtonSpec(text, url));
                } else if (item instanceof String && ((String) item).trim().length() > 0) {
                    result.add(new ButtonSpec(((String) item).trim(), ""));
                }
            }
        }
        if (result.size() == 0) {
            String text = firstString(data, "button_text", "");
            String url = firstString(data, "button_url", "");
            if (text.trim().length() > 0) result.add(new ButtonSpec(text, url));
        }
        return result;
    }

    private static void showDialog(final Activity activity, String title, String content,
                                   ArrayList<Bitmap> images, ArrayList<ButtonSpec> buttons) {
        LinearLayout column = new LinearLayout(activity);
        column.setOrientation(LinearLayout.VERTICAL);
        int pad = dp(activity, 18);
        column.setPadding(pad, dp(activity, 8), pad, dp(activity, 8));

        if (content != null && content.trim().length() > 0) {
            TextView message = new TextView(activity);
            message.setText(content);
            message.setTextSize(TypedValue.COMPLEX_UNIT_SP, 16);
            message.setTextColor(0xFF222222);
            message.setLineSpacing(dp(activity, 3), 1.0f);
            message.setGravity(Gravity.START);
            column.addView(message, new LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));
        }

        for (Bitmap bitmap : images) {
            ImageView image = new ImageView(activity);
            image.setImageBitmap(bitmap);
            image.setAdjustViewBounds(true);
            image.setScaleType(ImageView.ScaleType.FIT_CENTER);
            LinearLayout.LayoutParams imageParams = new LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
            imageParams.topMargin = dp(activity, 10);
            column.addView(image, imageParams);
        }

        ScrollView scroll = new ScrollView(activity);
        scroll.setFillViewport(false);
        scroll.addView(column);

        AlertDialog.Builder builder = new AlertDialog.Builder(activity)
                .setTitle(nonEmpty(title, "公告通知"))
                .setView(scroll)
                .setCancelable(true);

        if (buttons.size() > 0) {
            final ButtonSpec spec = buttons.get(0);
            builder.setPositiveButton(spec.text, (dialog, which) -> openLink(activity, spec.url));
        }
        if (buttons.size() > 1) {
            final ButtonSpec spec = buttons.get(1);
            builder.setNegativeButton(spec.text, (dialog, which) -> openLink(activity, spec.url));
        }
        if (buttons.size() > 2) {
            final ButtonSpec spec = buttons.get(2);
            builder.setNeutralButton(spec.text, (dialog, which) -> openLink(activity, spec.url));
        }

        AlertDialog dialog = builder.create();
        dialog.show();
        Window window = dialog.getWindow();
        if (window != null) {
            DisplayMetrics metrics = activity.getResources().getDisplayMetrics();
            window.setLayout((int) (metrics.widthPixels * 0.88f), ViewGroup.LayoutParams.WRAP_CONTENT);
        }
    }

    private static void openLink(Context context, String address) {
        if (address == null || address.trim().length() == 0) return;
        try {
            Intent intent = new Intent(Intent.ACTION_VIEW, Uri.parse(address.trim()));
            intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
            context.startActivity(intent);
        } catch (Throwable ignored) {
        }
    }

    private static int dp(Context context, int value) {
        return (int) (value * context.getResources().getDisplayMetrics().density + 0.5f);
    }

    private static final class ButtonSpec {
        final String text;
        final String url;
        ButtonSpec(String text, String url) {
            this.text = text;
            this.url = url;
        }
    }

    @Override public void onActivityCreated(Activity activity, android.os.Bundle state) {}
    @Override public void onActivityStarted(Activity activity) {}
    @Override public void onActivityPaused(Activity activity) {}
    @Override public void onActivityStopped(Activity activity) {}
    @Override public void onActivitySaveInstanceState(Activity activity, android.os.Bundle state) {}
    @Override public void onActivityDestroyed(Activity activity) {}
}