package glab.guesscard.activities;

import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.EditText;
import android.widget.ImageView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.recyclerview.widget.GridLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import java.util.List;

import glab.guesscard.ModernFButton;
import glab.guesscard.R;
import glab.guesscard.utils.AvatarManager;

public class EditProfileActivity extends BaseActivity {

    private ImageView imgPreview;
    private EditText etUsername;
    private RecyclerView rvGrid;
    private String selectedAvatarFile;

    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_edit_profile);

        imgPreview = findViewById(R.id.imgEditAvatarPreview);
        etUsername = findViewById(R.id.etEditUsername);
        rvGrid = findViewById(R.id.rvEditAvatarGrid);

        ModernFButton btnCancel = findViewById(R.id.btnCloseEditProfile);
        if (btnCancel != null) btnCancel.setOnClickListener(v -> finish());

        ModernFButton btnSave = findViewById(R.id.btnSaveProfile);
        if (btnSave != null) btnSave.setOnClickListener(v -> saveProfile());

        String currentName = prefs().getUsername();
        if (etUsername != null && currentName != null) {
            etUsername.setText(currentName);
        }

        selectedAvatarFile = prefs().getAvatarFileName();
        AvatarManager.getInstance().loadAvatarIntoImageView(this, imgPreview, selectedAvatarFile);

        setupAvatarGrid();
    }

    private void setupAvatarGrid() {
        if (rvGrid == null) return;
        List<String> avatarFiles = AvatarManager.getInstance().getAvatarFileNames(this);
        rvGrid.setLayoutManager(new GridLayoutManager(this, 4));
        rvGrid.setHasFixedSize(true);
        rvGrid.setAdapter(new RecyclerView.Adapter<AvatarVH>() {
            @NonNull
            @Override
            public AvatarVH onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
                View v = LayoutInflater.from(parent.getContext()).inflate(R.layout.item_avatar_choice, parent, false);
                return new AvatarVH(v);
            }

            @Override
            public void onBindViewHolder(@NonNull AvatarVH holder, int position) {
                String fileName = avatarFiles.get(position);
                AvatarManager.getInstance().loadAvatarIntoImageView(EditProfileActivity.this, holder.imgAvatar, fileName);

                boolean isSelected = fileName.equals(selectedAvatarFile);
                if (holder.ring != null) {
                    holder.ring.setVisibility(isSelected ? View.VISIBLE : View.GONE);
                }
                holder.itemView.setAlpha(isSelected ? 1.0f : 0.6f);

                View.OnClickListener clickListener = v -> {
                    selectedAvatarFile = fileName;
                    AvatarManager.getInstance().loadAvatarIntoImageView(EditProfileActivity.this, imgPreview, selectedAvatarFile);
                    notifyDataSetChanged();
                };

                holder.itemView.setOnClickListener(clickListener);
                if (holder.imgAvatar != null) {
                    holder.imgAvatar.setOnClickListener(clickListener);
                }
            }

            @Override
            public int getItemCount() {
                return avatarFiles.size();
            }
        });
    }

    private static class AvatarVH extends RecyclerView.ViewHolder {
        ImageView imgAvatar;
        View ring;
        AvatarVH(@NonNull View itemView) {
            super(itemView);
            imgAvatar = itemView.findViewById(R.id.imgAvatarChoice);
            ring = itemView.findViewById(R.id.viewSelectionRing);
        }
    }

    private void saveProfile() {
        if (etUsername == null) return;
        String name = etUsername.getText().toString().trim();
        if (name.isEmpty()) {
            Toast.makeText(this, "Please enter a display name", Toast.LENGTH_SHORT).show();
            return;
        }

        prefs().saveUsername(name);
        prefs().saveAvatarFileName(selectedAvatarFile);

        String uid = prefs().getUserId();
        if (uid != null) {
            container().getFirebaseManager().getDatabaseRef()
                    .child("users").child(uid).child("displayName").setValue(name);
            container().getFirebaseManager().getDatabaseRef()
                    .child("users").child(uid).child("avatarFileName").setValue(selectedAvatarFile);
            container().getFirebaseManager().refreshCachedProfile(uid, name, selectedAvatarFile);
        }

        Toast.makeText(this, "Profile updated!", Toast.LENGTH_SHORT).show();
        finish();
    }
}
