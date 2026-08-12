package glab.guesscard.utils;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.net.Uri;
import android.util.Log;
import android.widget.ImageView;

import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;

import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * AvatarManager handles individual PNG avatars from assets/avatars/ (avatar_01.png ... avatar_20.png)
 * and supports the default account photo (photoUrl / default_account).
 */
public class AvatarManager {
    private static final String TAG = "AvatarManager";
    public static final String DEFAULT_ACCOUNT = "default_account";
    private static AvatarManager instance;

    private final List<String> avatarFileNames = new ArrayList<>();
    private final Map<String, Bitmap> bitmapCache = new HashMap<>();
    private final ExecutorService executor = Executors.newFixedThreadPool(3);
    private boolean isLoaded = false;

    private AvatarManager() {}

    public static synchronized AvatarManager getInstance() {
        if (instance == null) {
            instance = new AvatarManager();
        }
        return instance;
    }

    public synchronized void init(Context context) {
        if (isLoaded && avatarFileNames.size() > 1) return;
        avatarFileNames.clear();
        avatarFileNames.add(DEFAULT_ACCOUNT);

        try {
            String[] files = context.getAssets().list("avatars");
            if (files != null) {
                List<String> list = new ArrayList<>();
                for (String file : files) {
                    if (file.endsWith(".png") || file.endsWith(".jpg")) {
                        list.add(file);
                    }
                }
                java.util.Collections.sort(list);
                avatarFileNames.addAll(list);
                isLoaded = true;
                Log.d(TAG, "Loaded " + avatarFileNames.size() + " avatar choices including default_account");
            }
        } catch (Exception e) {
            Log.e(TAG, "Error listing assets/avatars", e);
        }
    }

    public List<String> getAvatarFileNames(Context context) {
        init(context);
        return new ArrayList<>(avatarFileNames);
    }

    public int getAvatarCount(Context context) {
        init(context);
        return avatarFileNames.size();
    }

    public int getAvatarCount() {
        return avatarFileNames.isEmpty() ? 21 : avatarFileNames.size();
    }

    /** Convert a filename like "avatar_05.png" to its zero-based index (4). */
    public int getAvatarIndexFromFileName(String fileName) {
        if (fileName == null || fileName.isEmpty()) return 0;
        // Try to extract number from patterns like "avatar_XX.png"
        try {
            String numPart = fileName.replaceAll("[^0-9]", "");
            if (!numPart.isEmpty()) {
                int idx = Integer.parseInt(numPart) - 1;
                return Math.max(0, idx);
            }
        } catch (NumberFormatException ignored) {}
        // Fallback: find index by name
        int idx = avatarFileNames.indexOf(fileName);
        return idx >= 0 ? idx : 0;
    }

    public String getAvatarFileNameByIndex(Context context, int index) {
        init(context);
        if (avatarFileNames.isEmpty()) return DEFAULT_ACCOUNT;
        int safeIndex = Math.max(0, Math.min(index, avatarFileNames.size() - 1));
        return avatarFileNames.get(safeIndex);
    }

    public Bitmap getAvatarByName(Context context, String fileName) {
        init(context);
        if (fileName == null || fileName.isEmpty() || fileName.equalsIgnoreCase("default") || fileName.equals(DEFAULT_ACCOUNT)) {
            return bitmapCache.get(DEFAULT_ACCOUNT);
        }
        if (bitmapCache.containsKey(fileName)) {
            return bitmapCache.get(fileName);
        }

        try {
            InputStream is = context.getAssets().open("avatars/" + fileName);
            Bitmap bitmap = BitmapFactory.decodeStream(is);
            is.close();
            if (bitmap != null) {
                bitmapCache.put(fileName, bitmap);
                return bitmap;
            }
        } catch (Exception e) {
            Log.w(TAG, "Could not load asset: avatars/" + fileName + ", falling back to avatar_01.png");
            return getAvatarByName(context, "avatar_01.png");
        }
        return null;
    }

    public void loadAvatarIntoImageView(Context context, ImageView imageView, String fileName) {
        if (imageView == null) return;

        if (fileName == null || fileName.isEmpty() || fileName.equalsIgnoreCase("default") || fileName.equals(DEFAULT_ACCOUNT)) {
            FirebaseUser user = FirebaseAuth.getInstance().getCurrentUser();
            Uri photoUri = user != null ? user.getPhotoUrl() : null;
            if (photoUri != null && photoUri.toString().startsWith("http")) {
                loadUrlIntoImageView(photoUri.toString(), imageView);
            } else {
                imageView.setImageResource(glab.guesscard.R.drawable.ic_profile);
            }
            return;
        }

        Bitmap bitmap = getAvatarByName(context, fileName);
        if (bitmap != null) {
            imageView.setImageBitmap(bitmap);
        } else {
            imageView.setImageResource(glab.guesscard.R.drawable.ic_profile);
        }
    }

    public void loadAvatarIntoImageView(Context context, ImageView imageView, int avatarIndex) {
        String fileName = getAvatarFileNameByIndex(context, avatarIndex);
        loadAvatarIntoImageView(context, imageView, fileName);
    }

    private void loadUrlIntoImageView(String urlStr, ImageView imageView) {
        if (bitmapCache.containsKey(urlStr)) {
            imageView.setImageBitmap(bitmapCache.get(urlStr));
            return;
        }
        imageView.setImageResource(glab.guesscard.R.drawable.ic_profile);
        executor.execute(() -> {
            try {
                URL url = new URL(urlStr);
                HttpURLConnection conn = (HttpURLConnection) url.openConnection();
                conn.setConnectTimeout(5000);
                conn.setReadTimeout(5000);
                InputStream is = conn.getInputStream();
                Bitmap bitmap = BitmapFactory.decodeStream(is);
                is.close();
                if (bitmap != null) {
                    bitmapCache.put(urlStr, bitmap);
                    imageView.post(() -> imageView.setImageBitmap(bitmap));
                }
            } catch (Exception ignored) {}
        });
    }
}
