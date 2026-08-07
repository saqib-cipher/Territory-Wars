package com.territorywars.fragments;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.widget.LinearLayoutCompat;
import androidx.core.widget.NestedScrollView;
import androidx.fragment.app.Fragment;
import androidx.lifecycle.ViewModelProvider;
import com.google.android.material.materialswitch.MaterialSwitch;
import com.google.android.material.slider.Slider;
import com.google.android.material.tabs.TabLayout;
import com.territorywars.R;
import com.territorywars.di.GameContainer;
import com.territorywars.viewmodel.SettingsViewModel;
import com.territorywars.viewmodel.ViewModelFactory;
/**
* Settings: Display / Audio / Game tabs built as a simple vertical list of
* Material controls, persisted through {@link SettingsViewModel}.
*/
public class SettingsFragment extends Fragment {
private SettingsViewModel viewModel;
private ViewGroup tabHost;
@Nullable
@Override
public View onCreateView(@NonNull LayoutInflater inflater,
@Nullable ViewGroup container,
@Nullable Bundle savedInstanceState) {
return inflater.inflate(R.layout.fragment_settings, container, false);
}
@Override
public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
super.onViewCreated(view, savedInstanceState);
GameContainer container = com.territorywars.TerritoryWarsApp.from(requireContext());
viewModel = new ViewModelProvider(this, new ViewModelFactory(container))
.get(SettingsViewModel.class);
tabHost = view.findViewById(R.id.settingsContainer);
TabLayout tabs = view.findViewById(R.id.settingsTabs);
tabs.addTab(tabs.newTab().setText("Audio"));
tabs.addTab(tabs.newTab().setText("Game"));
tabs.addTab(tabs.newTab().setText("Appearance"));
tabs.addOnTabSelectedListener(new TabLayout.OnTabSelectedListener() {
@Override
public void onTabSelected(TabLayout.Tab tab) {
buildTab(tab.getPosition());
}
@Override
public void onTabUnselected(TabLayout.Tab tab) {
}
@Override
public void onTabReselected(TabLayout.Tab tab) {
}
});
buildTab(0);
}
private void buildTab(int position) {
tabHost.removeAllViews();
NestedScrollView scroll = new NestedScrollView(requireContext());
LinearLayoutCompat column = new LinearLayoutCompat(requireContext());
column.setOrientation(LinearLayoutCompat.VERTICAL);
int pad = (int) getResources().getDimension(R.dimen.space_16);
column.setPadding(pad, pad, pad, pad);
if (position == 0) {
MaterialSwitch music = new MaterialSwitch(requireContext());
music.setText("Music");
music.setOnCheckedChangeListener((b, checked) -> viewModel.setMusic(checked));
viewModel.getMusic().observe(getViewLifecycleOwner(), music::setChecked);
MaterialSwitch sound = new MaterialSwitch(requireContext());
sound.setText("Sound Effects");
sound.setOnCheckedChangeListener((b, checked) -> viewModel.setSound(checked));
viewModel.getSound().observe(getViewLifecycleOwner(), sound::setChecked);
column.addView(label("Music", music));
column.addView(label("Sound Effects", sound));
} else if (position == 1) {
Slider fps = new Slider(requireContext());
fps.setValueFrom(30f);
fps.setValueTo(120f);
fps.setStepSize(30f);
fps.setValue(60f);
fps.addOnChangeListener((s, value, fromUser) -> viewModel.setFps((int) value));
Slider sens = new Slider(requireContext());
sens.setValueFrom(0.3f);
sens.setValueTo(2f);
sens.setValue(1f);
sens.addOnChangeListener((s, value, fromUser) -> viewModel.setSensitivity(value));
column.addView(label("FPS Cap", fps));
column.addView(label("Sensitivity", sens));
} else {
TextView hint = new TextView(requireContext());
        hint.setText("Theme, language and privacy controls go here. Dynamic colour " +
                "is applied from the Settings → Appearance preference.");
column.addView(hint);
}
scroll.addView(column);
tabHost.addView(scroll);
}
private View label(String text, View control) {
LinearLayoutCompat row = new LinearLayoutCompat(requireContext());
row.setOrientation(LinearLayoutCompat.VERTICAL);
TextView label = new TextView(requireContext());
label.setText(text);
row.addView(label);
row.addView(control);
return row;
}
}
