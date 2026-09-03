package glab.guesscard.bluetooth;

import android.Manifest;
import android.app.Activity;
import android.bluetooth.BluetoothAdapter;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.os.Build;
import android.widget.Toast;

import androidx.appcompat.app.AlertDialog;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;

import java.util.ArrayList;
import java.util.List;

/**
 * Utility for handling Bluetooth permissions and Bluetooth state checks across Android versions.
 */
public class BluetoothPermissionHelper {

    public static final int REQ_BLUETOOTH_PERMISSIONS = 301;
    public static final int REQ_ENABLE_BT = 302;

    public interface PermissionCallback {
        void onGranted();
        void onDenied();
    }

    /**
     * Checks whether all necessary Bluetooth permissions for scanning and hosting are granted.
     */
    public static boolean hasBluetoothPermissions(Context context) {
        if (context == null) return false;

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            boolean scan = ContextCompat.checkSelfPermission(context, Manifest.permission.BLUETOOTH_SCAN) == PackageManager.PERMISSION_GRANTED;
            boolean advertise = ContextCompat.checkSelfPermission(context, Manifest.permission.BLUETOOTH_ADVERTISE) == PackageManager.PERMISSION_GRANTED;
            boolean connect = ContextCompat.checkSelfPermission(context, Manifest.permission.BLUETOOTH_CONNECT) == PackageManager.PERMISSION_GRANTED;
            return scan && advertise && connect;
        } else {
            boolean bt = ContextCompat.checkSelfPermission(context, Manifest.permission.BLUETOOTH) == PackageManager.PERMISSION_GRANTED;
            boolean btAdmin = ContextCompat.checkSelfPermission(context, Manifest.permission.BLUETOOTH_ADMIN) == PackageManager.PERMISSION_GRANTED;
            boolean location = ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED;
            return bt && btAdmin && location;
        }
    }

    /**
     * Returns the list of missing Bluetooth permissions for this device.
     */
    public static String[] getMissingPermissions(Context context) {
        List<String> missing = new ArrayList<>();
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            if (ContextCompat.checkSelfPermission(context, Manifest.permission.BLUETOOTH_SCAN) != PackageManager.PERMISSION_GRANTED) {
                missing.add(Manifest.permission.BLUETOOTH_SCAN);
            }
            if (ContextCompat.checkSelfPermission(context, Manifest.permission.BLUETOOTH_ADVERTISE) != PackageManager.PERMISSION_GRANTED) {
                missing.add(Manifest.permission.BLUETOOTH_ADVERTISE);
            }
            if (ContextCompat.checkSelfPermission(context, Manifest.permission.BLUETOOTH_CONNECT) != PackageManager.PERMISSION_GRANTED) {
                missing.add(Manifest.permission.BLUETOOTH_CONNECT);
            }
        } else {
            if (ContextCompat.checkSelfPermission(context, Manifest.permission.BLUETOOTH) != PackageManager.PERMISSION_GRANTED) {
                missing.add(Manifest.permission.BLUETOOTH);
            }
            if (ContextCompat.checkSelfPermission(context, Manifest.permission.BLUETOOTH_ADMIN) != PackageManager.PERMISSION_GRANTED) {
                missing.add(Manifest.permission.BLUETOOTH_ADMIN);
            }
            if (ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED) {
                missing.add(Manifest.permission.ACCESS_FINE_LOCATION);
            }
        }
        return missing.toArray(new String[0]);
    }

    /**
     * Requests missing Bluetooth permissions with an optional explanation dialog.
     */
    public static void requestBluetoothPermissions(Activity activity, int requestCode) {
        String[] missing = getMissingPermissions(activity);
        if (missing.length > 0) {
            ActivityCompat.requestPermissions(activity, missing, requestCode);
        }
    }

    /**
     * Checks if Bluetooth is supported and enabled on the device.
     */
    public static boolean isBluetoothEnabled() {
        BluetoothAdapter adapter = BluetoothAdapter.getDefaultAdapter();
        return adapter != null && adapter.isEnabled();
    }

    /**
     * Prompts the user to enable Bluetooth if it is currently disabled.
     */
    public static void promptEnableBluetooth(Activity activity) {
        if (activity == null) return;
        try {
            Intent enableBtIntent = new Intent(BluetoothAdapter.ACTION_REQUEST_ENABLE);
            activity.startActivityForResult(enableBtIntent, REQ_ENABLE_BT);
        } catch (Exception e) {
            Toast.makeText(activity, "Please turn on Bluetooth in Settings to play offline", Toast.LENGTH_SHORT).show();
        }
    }

    /**
     * Shows a Material explanation dialog before requesting permissions if needed.
     */
    public static void showPermissionExplanationDialog(Activity activity, Runnable onProceed) {
        if (activity == null || activity.isFinishing() || activity.isDestroyed()) return;

        new AlertDialog.Builder(activity)
                .setTitle("Bluetooth Mesh Permissions 📡")
                .setMessage("To play offline multiplayer without internet, Guess the Card uses Bluetooth to discover and connect with nearby players. Please allow Bluetooth and nearby device permissions.")
                .setPositiveButton("Allow", (dialog, which) -> {
                    if (onProceed != null) onProceed.run();
                })
                .setNegativeButton("Cancel", null)
                .show();
    }
}
