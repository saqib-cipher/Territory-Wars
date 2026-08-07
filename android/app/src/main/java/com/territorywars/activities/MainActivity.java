package com.territorywars.activities;
import android.content.res.Configuration;
import android.os.Bundle;
import android.view.View;
import androidx.annotation.Nullable;
import androidx.navigation.NavController;
import androidx.navigation.Navigation;
import androidx.navigation.ui.NavigationUI;
import com.google.android.material.bottomnavigation.BottomNavigationView;
import com.google.android.material.navigationrail.NavigationRailView;
import com.territorywars.R;
/**
* Shell activity. On phones the 8 destinations live in a floating
* bottom-navigation bar; on tablets / wide screens a navigation rail is used
* instead (Material 3 Expressive layout switch).
*/
public class MainActivity extends BaseActivity {
private NavController navController;
private BottomNavigationView bottomNav;
private NavigationRailView navRail;
@Override
protected void onCreate(@Nullable Bundle savedInstanceState) {
super.onCreate(savedInstanceState);
setContentView(R.layout.activity_main);
bottomNav = findViewById(R.id.bottomNav);
navRail = findViewById(R.id.navRail);
navController = Navigation.findNavController(this, R.id.fragmentContainer);
// The menu id space is shared between rail + bottom nav.
NavigationUI.setupWithNavController(bottomNav, navController);
NavigationUI.setupWithNavController(navRail, navController);
updateNavigationForLayout();
}
private void updateNavigationForLayout() {
boolean isWide = getResources().getConfiguration().screenLayout
>= Configuration.SCREENLAYOUT_SIZE_XLARGE
|| getResources().getBoolean(R.bool.is_tablet);
bottomNav.setVisibility(isWide ? View.GONE : View.VISIBLE);
navRail.setVisibility(isWide ? View.VISIBLE : View.GONE);
}
@Override
public boolean onSupportNavigateUp() {
return navController.navigateUp() || super.onSupportNavigateUp();
}
}
