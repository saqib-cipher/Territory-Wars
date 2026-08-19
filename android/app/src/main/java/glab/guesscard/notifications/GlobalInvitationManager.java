package glab.guesscard.notifications;

import android.app.Activity;
import android.content.Intent;
import android.view.LayoutInflater;
import android.view.View;
import android.widget.ImageView;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AlertDialog;

import com.google.android.material.button.MaterialButton;

import glab.guesscard.GuessCardApp;
import glab.guesscard.R;
import glab.guesscard.activities.LobbyActivity;
import glab.guesscard.di.GameContainer;
import glab.guesscard.firebase.FirebaseManager;
import glab.guesscard.utils.AvatarManager;

/**
 * Global Realtime Invitation & Friend Request Dialog Manager.
 * Renders modern Material 3 floating dialogs across any active activity.
 */
public class GlobalInvitationManager {

    private static AlertDialog activeDialog = null;

    /** Shows floating Global Room Invitation Dialog */
    public static void showRoomInviteDialog(Activity activity,
                                             String roomId,
                                             String roomCode,
                                             String senderUid,
                                             String senderName,
                                             String senderAvatar,
                                             String mode,
                                             Runnable onDismiss) {
        if (activity == null || activity.isFinishing() || activity.isDestroyed()) return;

        dismissActiveDialog();

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
            dialog.dismiss();
            if (onDismiss != null) onDismiss.run();
        });

        btnAccept.setOnClickListener(v -> {
            dialog.dismiss();
            if (activity instanceof LobbyActivity) {
                ((LobbyActivity) activity).removePresence();
                activity.finish();
            }

            Intent intent = new Intent(activity, LobbyActivity.class);
            if (roomId != null && !roomId.isEmpty()) intent.putExtra(LobbyActivity.EXTRA_ROOM_ID, roomId);
            if (roomCode != null && !roomCode.isEmpty()) intent.putExtra(LobbyActivity.EXTRA_ROOM_CODE, roomCode);
            activity.startActivity(intent);
        });

        activeDialog = dialog;
        dialog.show();
    }

    /** Shows floating Global Friend Request Dialog */
    public static void showFriendRequestDialog(Activity activity,
                                               String senderUid,
                                               String senderName,
                                               String senderAvatar,
                                               Runnable onDismiss) {
        if (activity == null || activity.isFinishing() || activity.isDestroyed()) return;

        dismissActiveDialog();

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
            tvDesc.setText(displayName + " sent you a friend request. Accept to play together and see online status!");
        }

        btnDecline.setOnClickListener(v -> {
            dialog.dismiss();
            if (onDismiss != null) onDismiss.run();
        });

        btnAccept.setOnClickListener(v -> {
            dialog.dismiss();
            GameContainer container = GuessCardApp.from(activity);
            String myUid = container.getPreferences().getUserId();
            if (myUid != null && senderUid != null) {
                container.getFirebaseManager().addFriend(myUid, senderUid);
                Toast.makeText(activity, "Accepted friend request from " + displayName + "!", Toast.LENGTH_SHORT).show();
            }
        });

        activeDialog = dialog;
        dialog.show();
    }

    public static void dismissActiveDialog() {
        if (activeDialog != null && activeDialog.isShowing()) {
            try {
                activeDialog.dismiss();
            } catch (Exception ignored) {}
        }
        activeDialog = null;
    }
}
