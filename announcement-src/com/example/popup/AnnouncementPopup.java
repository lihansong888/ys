package com.example.popup;

import android.app.Activity;
import android.app.Application;
import android.app.AlertDialog;
import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.drawable.GradientDrawable;
import android.net.Uri;
import android.util.DisplayMetrics;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.ViewGroup;
import android.view.Window;
import android.widget.Button;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.net.HttpURLConnection;
import java.net.URL;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.Locale;
import java.util.Map;
import java.lang.ref.WeakReference;
import java.util.concurrent.atomic.AtomicBoolean;

public final class AnnouncementPopup {
    private static final String API = "https://lihansong.top/jar/api.php";
    private static final String PREFS = "announcement_popup_v1";
    private static final AtomicBoolean STARTED = new AtomicBoolean(false);
    private static final Handler MAIN = new Handler(Looper.getMainLooper());
    private static volatile Application registeredApplication;
    private static volatile Application.ActivityLifecycleCallbacks lifecycleCallbacks;
    private static volatile WeakReference<Activity> resumedActivity = new WeakReference<Activity>(null);
    private static volatile PendingAnnouncement pendingAnnouncement;
    private static int activityRetryCount;
    private static boolean activityRetryScheduled;

    private AnnouncementPopup() {}

    public static void run(Context context) {
        if (context == null || !STARTED.compareAndSet(false, true)) return;

        final Context app = context.getApplicationContext() == null
                ? context : context.getApplicationContext();

        if (context instanceof Activity) {
            resumedActivity = new WeakReference<Activity>((Activity) context);
        } else if (app instanceof Application) {
            registerActivityTracking((Application) app);
        }

        new Thread(new Runnable() {
            @Override public void run() {
                try {
                    JSONObject data = requestJson(API);
                    if (data == null) {
                        finishWithoutShowing();
                        return;
                    }

                    resetFrequencyIfVersionChanged(app, data);

                    if (!enabled(data) || !withinDateWindow(data)) {
                        finishWithoutShowing();
                        return;
                    }

                    if (!shouldShow(app, data)) {
                        finishWithoutShowing();
                        return;
                    }

                    String title = nonEmpty(firstString(data, "title", "公告通知"), "公告通知");
                    String content = firstString(data, "content", "");
                    JSONArray imageUrls = data.optJSONArray("image_urls");
                    if (imageUrls == null || imageUrls.length() == 0) {
                        imageUrls = data.optJSONArray("images");
                    }

                    PendingAnnouncement pending = new PendingAnnouncement(
                            app,
                            data.optString("frequency", "daily"),
                            title,
                            content,
                            loadImages(imageUrls),
                            readButtons(data),
                            serverNow(data)
                    );

                    MAIN.post(new Runnable() {
                        @Override public void run() {
                            pendingAnnouncement = pending;
                            activityRetryCount = 0;
                            showPendingIfPossible();
                            scheduleActivityRetry();
                        }
                    });
                } catch (Throwable ignored) {
                    finishWithoutShowing();
                }
            }
        }, "announcement-popup").start();
    }

    /*
     * Init.init() supplies the Application object, not an Activity.
     * Track the real foreground Activity through public lifecycle callbacks.
     * This avoids relying on Android hidden-API reflection to read ActivityThread.
     */
    private static void registerActivityTracking(final Application application) {
        MAIN.post(new Runnable() {
            @Override public void run() {
                if (registeredApplication == application && lifecycleCallbacks != null) return;

                registeredApplication = application;
                lifecycleCallbacks = new Application.ActivityLifecycleCallbacks() {
                    @Override public void onActivityCreated(Activity activity, Bundle state) {}

                    @Override public void onActivityStarted(Activity activity) {}

                    @Override public void onActivityResumed(Activity activity) {
                        resumedActivity = new WeakReference<Activity>(activity);
                        showPendingIfPossible();
                    }

                    @Override public void onActivityPaused(Activity activity) {
                        Activity current = resumedActivity.get();
                        if (current == activity) {
                            resumedActivity = new WeakReference<Activity>(null);
                        }
                    }

                    @Override public void onActivityStopped(Activity activity) {}

                    @Override public void onActivitySaveInstanceState(Activity activity, Bundle state) {}

                    @Override public void onActivityDestroyed(Activity activity) {
                        Activity current = resumedActivity.get();
                        if (current == activity) {
                            resumedActivity = new WeakReference<Activity>(null);
                        }
                    }
                };

                application.registerActivityLifecycleCallbacks(lifecycleCallbacks);

                // One non-blocking fallback for apps where init happens after onResume.
                Activity active = findActivity(application);
                if (active != null && !active.isFinishing()) {
                    resumedActivity = new WeakReference<Activity>(active);
                    showPendingIfPossible();
                }
            }
        });
    }

    private static Activity findActivity(Context context) {
        try {
            Class<?> threadClass = Class.forName("android.app.ActivityThread");
            Method currentThread = threadClass.getMethod("currentActivityThread");
            Object thread = currentThread.invoke(null);
            Field activitiesField = threadClass.getDeclaredField("mActivities");
            activitiesField.setAccessible(true);
            Object value = activitiesField.get(thread);
            if (!(value instanceof Map)) return null;

            for (Object record : ((Map<?, ?>) value).values()) {
                if (record == null) continue;

                Field pausedField = record.getClass().getDeclaredField("paused");
                pausedField.setAccessible(true);
                if (pausedField.getBoolean(record)) continue;

                Field activityField = record.getClass().getDeclaredField("activity");
                activityField.setAccessible(true);
                Object activity = activityField.get(record);
                if (activity instanceof Activity) return (Activity) activity;
            }
        } catch (Throwable ignored) {
        }
        return null;
    }

    private static void showPendingIfPossible() {
        if (Looper.myLooper() != Looper.getMainLooper()) {
            MAIN.post(new Runnable() {
                @Override public void run() { showPendingIfPossible(); }
            });
            return;
        }

        final PendingAnnouncement pending = pendingAnnouncement;
        final Activity activity = resumedActivity.get();

        if (pending == null || activity == null || activity.isFinishing() || activity.isDestroyed()) return;

        try {
            showDialog(activity, pending.title, pending.content, pending.images, pending.buttons);
            markShown(pending.context, pending.frequency, pending.serverTime);
            pendingAnnouncement = null;
            unregisterActivityTracking();
            STARTED.set(false);
        } catch (Throwable ignored) {
            finishWithoutShowing();
        }
    }

    /*
     * Retry locating the foreground Activity. This covers the case where
     * AnnouncementPopup.run() is called with an Application context after
     * the first Activity has already resumed and lifecycle callbacks therefore
     * have no new onActivityResumed event to observe.
     */
    private static void scheduleActivityRetry() {
        if (Looper.myLooper() != Looper.getMainLooper()) {
            MAIN.post(new Runnable() {
                @Override public void run() { scheduleActivityRetry(); }
            });
            return;
        }

        if (pendingAnnouncement == null || activityRetryScheduled) return;
        activityRetryScheduled = true;

        MAIN.postDelayed(new Runnable() {
            @Override public void run() {
                activityRetryScheduled = false;
                if (pendingAnnouncement == null) return;

                Activity active = resumedActivity.get();
                if (active == null || active.isFinishing() || active.isDestroyed()) {
                    Application app = registeredApplication;
                    if (app != null) {
                        Activity found = findActivity(app);
                        if (found != null && !found.isFinishing() && !found.isDestroyed()) {
                            resumedActivity = new WeakReference<Activity>(found);
                            active = found;
                        }
                    }
                }

                if (active != null && !active.isFinishing() && !active.isDestroyed()) {
                    showPendingIfPossible();
                    return;
                }

                activityRetryCount++;
                if (activityRetryCount < 120) {
                    scheduleActivityRetry();
                } else {
                    finishWithoutShowing();
                }
            }
        }, 500L);
    }

    private static void finishWithoutShowing() {
        MAIN.post(new Runnable() {
            @Override public void run() {
                pendingAnnouncement = null;
                unregisterActivityTracking();
                STARTED.set(false);
            }
        });
    }

    private static void unregisterActivityTracking() {
        Application app = registeredApplication;
        Application.ActivityLifecycleCallbacks callbacks = lifecycleCallbacks;

        if (app != null && callbacks != null) {
            try { app.unregisterActivityLifecycleCallbacks(callbacks); } catch (Throwable ignored) {}
        }

        registeredApplication = null;
        lifecycleCallbacks = null;
        resumedActivity = new WeakReference<Activity>(null);
    }

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
            if (connection.getResponseCode() != 200) return null;
            InputStream in = connection.getInputStream();
            ByteArrayOutputStream out = new ByteArrayOutputStream();
            byte[] buffer = new byte[4096];
            int count;
            while ((count = in.read(buffer)) != -1) out.write(buffer, 0, count);
            in.close();
            JSONObject object = new JSONObject(new String(out.toByteArray(), "UTF-8"));
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
        long now = serverNow(data);
        long start = parseDate(data.optString("start_at", "").trim());
        long end = parseDate(data.optString("end_at", "").trim());
        return (start <= 0 || now >= start) && (end <= 0 || now <= end);
    }

    /*
     * server_time and the admin-entered date strings use the server's wall clock.
     * Parsing both in the same device timezone lets the server timestamp govern
     * the window even when the device clock itself is inaccurate.
     */
    private static long serverNow(JSONObject data) {
        long server = parseDate(data.optString("server_time", "").trim());
        return server > 0 ? server : System.currentTimeMillis();
    }

    private static long parseDate(String value) {
        if (value == null || value.length() == 0) return 0;

        String[] patterns = {
                "yyyy-MM-dd'T'HH:mm:ssXXX",
                "yyyy-MM-dd'T'HH:mm:ss.SSSXXX",
                "yyyy-MM-dd HH:mm:ss",
                "yyyy-MM-dd'T'HH:mm:ss",
                "yyyy-MM-dd"
        };

        for (String pattern : patterns) {
            try {
                SimpleDateFormat format = new SimpleDateFormat(pattern, Locale.US);
                format.setLenient(false);
                Date date = format.parse(value);
                if (date != null) return date.getTime();
            } catch (Throwable ignored) {}
        }
        return 0;
    }

    private static void resetFrequencyIfVersionChanged(Context context, JSONObject data) {
        SharedPreferences prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE);
        String version = data.optString("version", "").trim();

        if (version.length() > 0) {
            String oldVersion = prefs.getString("announcement_version", "");
            if (!version.equals(oldVersion)) {
                prefs.edit()
                        .remove("shown_once")
                        .remove("last_shown")
                        .remove("last_day")
                        .putString("announcement_version", version)
                        .apply();
            }
            return;
        }

        // Compatibility for older API responses that do not provide version.
        StringBuilder signature = new StringBuilder();
        String[] keys = {
                "enabled", "title", "content", "frequency", "interval_value",
                "start_at", "end_at", "image_urls", "images", "buttons",
                "button_text", "button_url"
        };
        for (String key : keys) {
            Object value = data.opt(key);
            signature.append(key).append('=')
                    .append(value == null ? "" : value.toString()).append('|');
        }

        String value = Integer.toHexString(signature.toString().hashCode());
        String old = prefs.getString("config_signature", "");
        if (!value.equals(old)) {
            prefs.edit().remove("shown_once").remove("last_shown").remove("last_day")
                    .putString("config_signature", value).apply();
        }
    }

    private static boolean shouldShow(Context context, JSONObject data) {
        SharedPreferences prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE);
        String frequency = data.optString("frequency", "daily").trim().toLowerCase(Locale.US);

        // Match the exact options provided by admin.php.
        if ("never".equals(frequency) || "永久不显示".equals(frequency)) return false;
        if ("startup".equals(frequency) || "always".equals(frequency)
                || "every_launch".equals(frequency) || "每次".equals(frequency)) return true;

        if ("once".equals(frequency) || "only_once".equals(frequency) || "仅一次".equals(frequency)) {
            return !prefs.getBoolean("shown_once", false);
        }

        long last = prefs.getLong("last_shown", 0L);
        long now = serverNow(data);

        if ("interval_days".equals(frequency) || "interval".equals(frequency)
                || "every_n_days".equals(frequency)) {
            int days = data.optInt("interval_value", 1);
            if (days < 1) days = 1;
            return last == 0L || now - last >= days * 86400000L;
        }

        if ("interval_hours".equals(frequency) || "hourly".equals(frequency)) {
            int hours = data.optInt("interval_value", 1);
            if (hours < 1) hours = 1;
            return last == 0L || now - last >= hours * 3600000L;
        }

        if ("weekly".equals(frequency)) {
            return last == 0L || now - last >= 604800000L;
        }

        String today = new SimpleDateFormat("yyyyMMdd", Locale.US).format(new Date(serverNow(data)));
        return !today.equals(prefs.getString("last_day", ""));
    }

    private static void markShown(Context context, String frequency, long serverTime) {
        long now = serverTime > 0 ? serverTime : System.currentTimeMillis();

        SharedPreferences.Editor editor = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit();
        editor.putLong("last_shown", now);
        editor.putString("last_day", new SimpleDateFormat("yyyyMMdd", Locale.US).format(new Date(now)));

        if ("once".equalsIgnoreCase(frequency) || "only_once".equalsIgnoreCase(frequency)
                || "仅一次".equals(frequency)) {
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

        // The announcement layout intentionally supports exactly one image.
        // If an earlier URL is unavailable, try the next one until one loads.
        for (int i = 0; i < urls.length() && result.isEmpty(); i++) {
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
                    String type = firstString(button, "type", "url");
                    String value = firstString(button, "value", "");
                    if (value.length() == 0) value = firstString(button, "url", "");
                    if (value.length() == 0) value = firstString(button, "link", "");
                    if (value.length() == 0) value = firstString(button, "href", "");
                    if (text.trim().length() > 0) result.add(new ButtonSpec(text, type, value));
                } else if (item instanceof String && ((String) item).trim().length() > 0) {
                    result.add(new ButtonSpec(((String) item).trim(), "url", ""));
                }
            }
        }
        if (result.size() == 0) {
            String text = firstString(data, "button_text", "");
            String value = firstString(data, "button_url", "");
            if (text.trim().length() > 0) result.add(new ButtonSpec(text, "url", value));
        }
        return result;
    }

    private static void showDialog(final Activity activity, String title, String content,
                                   ArrayList<Bitmap> images, ArrayList<ButtonSpec> buttons) {
        DisplayMetrics metrics = activity.getResources().getDisplayMetrics();
        int dialogWidth = (int) (metrics.widthPixels * 0.60f);
        int maxDialogHeight = (int) (metrics.heightPixels * 0.50f);
        int reservedHeight = dp(activity, 125);
        int maxContentHeight = Math.max(dp(activity, 90), maxDialogHeight - reservedHeight);
        int pad = dp(activity, 18);

        // Keep the title in its own centered row at the top of the dialog.
        TextView titleView = new TextView(activity);
        titleView.setText(nonEmpty(title, "公告通知"));
        titleView.setTextSize(TypedValue.COMPLEX_UNIT_SP, 20);
        titleView.setTextColor(0xFF222222);
        titleView.setGravity(Gravity.CENTER);
        titleView.setPadding(pad, dp(activity, 14), pad, dp(activity, 10));
        titleView.setTypeface(null, android.graphics.Typeface.BOLD);

        // The image gallery uses a fixed 28% column on the left. Text occupies
        // the remaining width; standard AlertDialog action buttons stay below.
        LinearLayout body = new LinearLayout(activity);
        body.setOrientation(LinearLayout.HORIZONTAL);
        body.setGravity(Gravity.CENTER_VERTICAL);
        body.setPadding(pad, dp(activity, 8), pad, dp(activity, 8));

        int innerWidth = Math.max(dp(activity, 120), dialogWidth - pad * 2);
        int imageColumnWidth = images != null && images.size() > 0
                ? Math.max(dp(activity, 64), (int) (innerWidth * 0.28f)) : 0;

        if (images != null && images.size() > 0) {
            LinearLayout imageColumn = new LinearLayout(activity);
            imageColumn.setOrientation(LinearLayout.VERTICAL);
            imageColumn.setGravity(Gravity.CENTER);
            int imageCount = images.size();
            int imageHeight = Math.max(dp(activity, 48),
                    Math.min(dp(activity, 110), maxContentHeight / imageCount));

            for (Bitmap bitmap : images) {
                ImageView image = new ImageView(activity);
                image.setImageBitmap(bitmap);
                image.setAdjustViewBounds(true);
                image.setScaleType(ImageView.ScaleType.FIT_CENTER);
                LinearLayout.LayoutParams imageParams = new LinearLayout.LayoutParams(
                        ViewGroup.LayoutParams.MATCH_PARENT, imageHeight);
                imageParams.bottomMargin = dp(activity, 5);
                imageColumn.addView(image, imageParams);
            }

            LinearLayout.LayoutParams imageColumnParams = new LinearLayout.LayoutParams(
                    imageColumnWidth, ViewGroup.LayoutParams.WRAP_CONTENT);
            imageColumnParams.rightMargin = dp(activity, 12);
            body.addView(imageColumn, imageColumnParams);
        }

        TextView message = new TextView(activity);
        message.setText(content == null ? "" : content);
        message.setTextSize(TypedValue.COMPLEX_UNIT_SP, 16);
        message.setTextColor(0xFF222222);
        message.setLineSpacing(dp(activity, 3), 1.0f);
        message.setGravity(Gravity.CENTER_VERTICAL | Gravity.START);
        LinearLayout.LayoutParams messageParams = new LinearLayout.LayoutParams(
                0, ViewGroup.LayoutParams.WRAP_CONTENT, 1.0f);
        body.addView(message, messageParams);

        BoundedScrollView scroll = new BoundedScrollView(activity, maxContentHeight);
        scroll.setFillViewport(false);
        scroll.addView(body, new ScrollView.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));

        AlertDialog.Builder builder = new AlertDialog.Builder(activity)
                .setCustomTitle(titleView)
                .setView(scroll)
                .setCancelable(true);

        // AlertDialog owns the bottom action row, so images and text cannot
        // cover or push the buttons out of the dialog.
        if (buttons.size() > 0) {
            final ButtonSpec spec = buttons.get(0);
            builder.setPositiveButton(spec.text, (dialog, which) -> runButtonAction(activity, spec));
        }
        if (buttons.size() > 1) {
            final ButtonSpec spec = buttons.get(1);
            builder.setNegativeButton(spec.text, (dialog, which) -> runButtonAction(activity, spec));
        }
        if (buttons.size() > 2) {
            final ButtonSpec spec = buttons.get(2);
            builder.setNeutralButton(spec.text, (dialog, which) -> runButtonAction(activity, spec));
        }

        AlertDialog dialog = builder.create();
        dialog.show();

        // Make configured actions look like real buttons rather than faint
        // text-only dialog actions. Keep the standard dialog click behavior.
        styleActionButton(activity, dialog.getButton(AlertDialog.BUTTON_POSITIVE));
        styleActionButton(activity, dialog.getButton(AlertDialog.BUTTON_NEGATIVE));
        styleActionButton(activity, dialog.getButton(AlertDialog.BUTTON_NEUTRAL));

        Window window = dialog.getWindow();
        if (window != null) {
            window.setLayout(dialogWidth, ViewGroup.LayoutParams.WRAP_CONTENT);
        }
    }

    private static void styleActionButton(Context context, Button button) {
        if (button == null || button.getVisibility() != ViewGroup.VISIBLE) return;

        GradientDrawable background = new GradientDrawable();
        background.setColor(0xFF2878E5);
        background.setCornerRadius(dp(context, 8));
        button.setBackground(background);
        button.setTextColor(0xFFFFFFFF);
        button.setTypeface(null, android.graphics.Typeface.BOLD);
        button.setTextSize(TypedValue.COMPLEX_UNIT_SP, 14);
        button.setPadding(dp(context, 12), dp(context, 8), dp(context, 12), dp(context, 8));
        button.setMinHeight(dp(context, 40));
        button.setAllCaps(false);
    }

    private static void runButtonAction(Context context, ButtonSpec button) {
        if ("copy".equalsIgnoreCase(button.type)) {
            try {
                ClipboardManager clipboard = (ClipboardManager) context.getSystemService(Context.CLIPBOARD_SERVICE);
                if (clipboard != null) clipboard.setPrimaryClip(ClipData.newPlainText(button.text, button.value));
                Toast.makeText(context, "已复制", Toast.LENGTH_SHORT).show();
            } catch (Throwable ignored) {}
            return;
        }
        openLink(context, button.value);
    }

    private static void openLink(Context context, String address) {
        if (address == null || address.trim().length() == 0) return;
        try {
            Intent intent = new Intent(Intent.ACTION_VIEW, Uri.parse(address.trim()));
            intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
            context.startActivity(intent);
        } catch (Throwable ignored) {}
    }

    private static int dp(Context context, int value) {
        return (int) (value * context.getResources().getDisplayMetrics().density + 0.5f);
    }

    private static final class PendingAnnouncement {
        final Context context;
        final String frequency;
        final String title;
        final String content;
        final ArrayList<Bitmap> images;
        final ArrayList<ButtonSpec> buttons;
        final long serverTime;

        PendingAnnouncement(Context context, String frequency, String title, String content,
                            ArrayList<Bitmap> images, ArrayList<ButtonSpec> buttons, long serverTime) {
            this.context = context;
            this.frequency = frequency;
            this.title = title;
            this.content = content;
            this.images = images;
            this.buttons = buttons;
            this.serverTime = serverTime;
        }
    }

    private static final class BoundedScrollView extends ScrollView {
        private final int maxHeight;

        BoundedScrollView(Context context, int maxHeight) {
            super(context);
            this.maxHeight = maxHeight;
        }

        @Override protected void onMeasure(int widthMeasureSpec, int heightMeasureSpec) {
            int mode = MeasureSpec.getMode(heightMeasureSpec);
            int size = MeasureSpec.getSize(heightMeasureSpec);

            if (mode == MeasureSpec.UNSPECIFIED || size > maxHeight) {
                heightMeasureSpec = MeasureSpec.makeMeasureSpec(maxHeight, MeasureSpec.AT_MOST);
            }

            super.onMeasure(widthMeasureSpec, heightMeasureSpec);
        }
    }

    private static final class ButtonSpec {
        final String text;
        final String type;
        final String value;
        ButtonSpec(String text, String type, String value) {
            this.text = text;
            this.type = type;
            this.value = value;
        }
    }
}