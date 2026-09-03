package glab.guesscard.notifications;

import android.app.Activity;
import android.content.Intent;
import android.os.CountDownTimer;
import android.os.Handler;
import android.os.Looper;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.widget.ImageView;
import android.widget.TextView;

import androidx.appcompat.app.AlertDialog;

import com.google.android.material.button.MaterialButton;

import java.util.Map;

import glab.guesscard.GuessCardApp;
import glab.guesscard.R;
import glab.guesscard.activities.GameActivity;
import glab.guesscard.activities.LobbyActivity;
import glab.guesscard.di.GameContainer;
import glab.guesscard.firebase.FriendshipManager;
import glab.guesscard.utils.AvatarManager;

/**
 * Global Realtime Invitation & Friend Request Dialog Manager.
 * Renders modern Material 3 floating dialogs across any active foreground activity.
 * Automatically closes after 5 seconds if not interacted with.
 */
public class GlobalInvitationManager {

    private static final String TAG = "GlobalInvitation";
    private static AlertDialog activeDialog = null;
    private static CountDownTimer autoCloseTimer = null;
    private static String activeDialogSenderUid = null;
    private static final long AUTO_CLOSE_DURATION_MS = 5000L;
    private static final Handler mainHandler = new Handler(Looper.getMainLooper());

    /** Shows floating Global Room Invitation Dialog */
    public static void showRoomInviteDialog(Activity targetActivity,
                                            String roomId,
                                            String roomCode,
                                            String senderUid,
                                            String senderName,
                                            String senderAvatar,
                                            String mode,
                                            Runnable onDismiss) {
        mainHandler.post(() -> {
            Activity activity = targetActivity != null && !targetActivity.isFinishing() && !targetActivity.isDestroyed()
                    ? targetActivity : GuessCardApp.getCurrentActivity();

            if (activity == null || activity.isFinishing() || activity.isDestroyed()) {
                Log.w(TAG, "showRoomInviteDialog: No valid foreground Activity found.");
                return;
            }

            // Do not show room invite popups if player is actively playing inside GameActivity
            if (activity instanceof GameActivity) {
                Log.d(TAG, "showRoomInviteDialog: Player is in GameActivity, suppressing popup.");
                return;
            }

            if (activeDialog != null && activeDialog.isShowing() && senderUid != null && senderUid.equals(activeDialogSenderUid)) {
                return;
            }

            dismissActiveDialog();

            try {
                View dialogView = LayoutInflater.from(activity).inflate(R.layout.dialog_global_invitation, null);
                AlertDialog dialog = new AlertDialog.Builder(activity)
                        .setView(dialogView)
                        .setCancelable(true)
                        .create();

                if (dialog.getWindow() != null) {
                    dialog.getWindow().setBackgroundDrawableResource(android.R.color.transparent);
                }

                TextView tvBadge = dialogView.findViewById(R.id.tvInviteBadge);
                ImageView ivAvatar = dialogView.findViewById(R.id.ivInviterAvatar);
                TextView tvName = dialogView.findViewById(R.id.tvInviterName);
                TextView tvSub = dialogView.findViewById(R.id.tvInviterSub);
                TextView tvDesc = dialogView.findViewById(R.id.tvInviteDescription);
                MaterialButton btnDecline = dialogView.findViewById(R.id.btnDeclineInvite);
                MaterialButton btnAccept = dialogView.findViewById(R.id.btnAcceptInvite);

                if (tvBadge != null) {
                    tvBadge.setText("ROOM INVITATION ✉️");
                    tvBadge.setBackgroundColor(android.graphics.Color.parseColor("#6366F1"));
                }

                String displayName = senderName != null && !senderName.isEmpty() ? senderName : "A Friend";
                if (tvName != null) tvName.setText(displayName);
                if (tvSub != null) tvSub.setText("Online • Game Room");
                AvatarManager.getInstance().loadAvatarIntoImageView(activity, ivAvatar, senderAvatar);

                String gameMode = mode != null && !mode.isEmpty() ? mode : "ANIMALS";
                String codeStr = roomCode != null && !roomCode.isEmpty() ? " (Code: " + roomCode + ")" : "";
                if (tvDesc != null) {
                    tvDesc.setText(displayName + " invited you to play " + gameMode + " mode" + codeStr + "!");
                }

                btnDecline.setOnClickListener(v -> {
                    cancelTimer();
                    dialog.dismiss();
                    if (onDismiss != null) onDismiss.run();
                });

                btnAccept.setOnClickListener(v -> {
                    cancelTimer();
                    dialog.dismiss();

                    // Clear the pending invite key so it never reappears
                    GameContainer container = GuessCardApp.from(activity);
                    String myUid = container != null && container.getPreferences() != null
                            ? container.getPreferences().getUserId() : null;
                    if (container != null && myUid != null && senderUid != null) {
                        container.getFirebaseManager().getPendingRoomInvites(myUid, invites -> {
                            if (invites == null) return;
                            for (Map<String, Object> inv : invites) {
                                Object invSender = inv.get("senderUid");
                                Object invKey = inv.get("key");
                                if (senderUid.equals(String.valueOf(invSender)) && invKey != null) {
                                    container.getFirebaseManager().declineRoomInvite(myUid, String.valueOf(invKey));
                                    break;
                                }
                            }
                        });
                    }

                    if (activity instanceof LobbyActivity) {
                        ((LobbyActivity) activity).removePresence();
                        activity.finish();
                    }

                    Intent intent = new Intent(activity, LobbyActivity.class);
                    if (roomId != null && !roomId.isEmpty()) intent.putExtra(LobbyActivity.EXTRA_ROOM_ID, roomId);
                    if (roomCode != null && !roomCode.isEmpty()) intent.putExtra(LobbyActivity.EXTRA_ROOM_CODE, roomCode);
                    if (mode != null && !mode.isEmpty()) intent.putExtra(LobbyActivity.EXTRA_MODE, mode);
                    activity.startActivity(intent);
                });

                dialog.setOnDismissListener(d -> {
                    cancelTimer();
                    activeDialogSenderUid = null;
                });

                // 5-second auto-close timer with countdown
                startAutoCloseCountdown(dialog, btnDecline, "Ignore");

                activeDialogSenderUid = senderUid;
                activeDialog = dialog;
                dialog.show();
            } catch (Exception e) {
                Log.e(TAG, "Error displaying room invite dialog", e);
            }
        });
    }

    /** Shows floating Global Friend Request Dialog with 5s auto-close */
    public static void showFriendRequestDialog(Activity targetActivity,
                                               String senderUid,
                                               String senderName,
                                               String senderAvatar,
                                               Runnable onDismiss) {
        mainHandler.post(() -> {
            Activity activity = targetActivity != null && !targetActivity.isFinishing() && !targetActivity.isDestroyed()
                    ? targetActivity : GuessCardApp.getCurrentActivity();

            if (activity == null || activity.isFinishing() || activity.isDestroyed()) {
                Log.w(TAG, "showFriendRequestDialog: No valid foreground Activity found.");
                return;
            }

            if (activeDialog != null && activeDialog.isShowing() && senderUid != null && senderUid.equals(activeDialogSenderUid)) {
                return;
            }

            dismissActiveDialog();

            try {
                View dialogView = LayoutInflater.from(activity).inflate(R.layout.dialog_global_invitation, null);
                AlertDialog dialog = new AlertDialog.Builder(activity)
                        .setView(dialogView)
                        .setCancelable(true)
                        .create();

                if (dialog.getWindow() != null) {
                    dialog.getWindow().setBackgroundDrawableResource(android.R.color.transparent);
                }

                TextView tvBadge = dialogView.findViewById(R.id.tvInviteBadge);
                ImageView ivAvatar = dialogView.findViewById(R.id.ivInviterAvatar);
                TextView tvName = dialogView.findViewById(R.id.tvInviterName);
                TextView tvSub = dialogView.findViewById(R.id.tvInviterSub);
                TextView tvDesc = dialogView.findViewById(R.id.tvInviteDescription);
                MaterialButton btnDecline = dialogView.findViewById(R.id.btnDeclineInvite);
                MaterialButton btnAccept = dialogView.findViewById(R.id.btnAcceptInvite);

                if (tvBadge != null) {
                    tvBadge.setText("FRIEND REQUEST 🤝");
                    tvBadge.setBackgroundColor(android.graphics.Color.parseColor("#10B981"));
                }

                String displayName = senderName != null && !senderName.isEmpty() ? senderName : "Player";
                if (tvName != null) tvName.setText(displayName);
                if (tvSub != null) tvSub.setText("Wants to be your friend");
                AvatarManager.getInstance().loadAvatarIntoImageView(activity, ivAvatar, senderAvatar);

                if (tvDesc != null) {
                    tvDesc.setText(displayName + " sent you a friend request. Accept to play together!");
                }

                btnDecline.setOnClickListener(v -> {
                    cancelTimer();
                    dialog.dismiss();
                    if (onDismiss != null) onDismiss.run();
                });

                btnAccept.setOnClickListener(v -> {
                    cancelTimer();
                    dialog.dismiss();
                    GameContainer container = GuessCardApp.from(activity);
                    String myUid = container.getPreferences().getUserId();
                    if (myUid != null && senderUid != null) {
                        FriendshipManager.acceptFriendRequest(activity, myUid, senderUid, displayName, null);
                    }
                });

                dialog.setOnDismissListener(d -> {
                    cancelTimer();
                    activeDialogSenderUid = null;
                });

                // 5-second auto-close countdown
                startAutoCloseCountdown(dialog, btnDecline, "Ignore");

                activeDialogSenderUid = senderUid;
                activeDialog = dialog;
                dialog.show();
            } catch (Exception e) {
                Log.e(TAG, "Error displaying friend request dialog", e);
            }
        });
    }

    private static void startAutoCloseCountdown(AlertDialog dialog, MaterialButton btnDecline, String baseText) {
        cancelTimer();
        autoCloseTimer = new CountDownTimer(AUTO_CLOSE_DURATION_MS, 1000) {
            @Override
            public void onTick(long millisUntilFinished) {
                if (btnDecline != null) {
                    long sec = (millisUntilFinished / 1000) + 1;
                    btnDecline.setText(baseText + " (" + sec + "s)");
                }
            }

            @Override
            public void onFinish() {
                if (dialog != null && dialog.isShowing()) {
                    try {
                        dialog.dismiss();
                    } catch (Exception ignored) {}
                }
            }
        };
        autoCloseTimer.start();
    }

    private static void cancelTimer() {
        if (autoCloseTimer != null) {
            autoCloseTimer.cancel();
            autoCloseTimer = null;
        }
    }

    public static void dismissActiveDialog() {
        cancelTimer();
        if (activeDialog != null && activeDialog.isShowing()) {
            try {
                activeDialog.dismiss();
            } catch (Exception ignored) {}
        }
        activeDialog = null;
        activeDialogSenderUid = null;
    }
}
