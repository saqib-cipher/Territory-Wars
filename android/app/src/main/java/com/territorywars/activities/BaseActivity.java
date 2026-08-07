package com.territorywars.activities;
import android.os.Bundle;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;
import com.territorywars.TerritoryWarsApp;
import com.territorywars.di.GameContainer;
import com.territorywars.network.PreferenceManager;
import com.territorywars.theme.ThemeManager;
/**
* Base activity: applies the persisted theme + dynamic colour before any
* super.onCreate and exposes the DI container.
*/
public abstract class BaseActivity extends AppCompatActivity {
private GameContainer container;
@Override
protected void onCreate(@Nullable Bundle savedInstanceState) {
container = TerritoryWarsApp.from(this);
ThemeManager.applyTheme(container.getPreferences().getThemeMode());
ThemeManager.applyDynamicColor(this, container.getPreferences().getThemeMode());
super.onCreate(savedInstanceState);
}
protected GameContainer container() {
if (container == null) container = TerritoryWarsApp.from(this);
return container;
}
protected PreferenceManager prefs() {
return container().getPreferences();
}
}
