package com.territorywars;
import android.app.Application;
import android.content.Context;
import com.google.android.gms.ads.MobileAds;
import com.google.android.gms.ads.initialization.InitializationStatus;
import com.google.android.gms.ads.initialization.OnInitializationCompleteListener;
import com.territorywars.ads.AdManager;
import com.territorywars.billing.BillingManager;
import com.territorywars.database.AppDatabase;
import com.territorywars.di.GameContainer;
import com.territorywars.network.PreferenceManager;
import com.territorywars.notifications.NotificationHelper;
import com.territorywars.repository.Repository;
import com.territorywars.socket.GameSocketClient;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
/**
* Application entry point. Owns the manual dependency-injection container and
* performs one-time initialisation (Ads, Notifications, Background workers).
*/
public class TerritoryWarsApp extends Application {
private GameContainer container;
@Override
public void onCreate() {
super.onCreate();
container = new GameContainer(this);
initAds();
NotificationHelper.ensureChannels(this);
}
/** Single manual DI container. */
public GameContainer getContainer() {
if (container == null) container = new GameContainer(this);
return container;
}
/** Convenience accessor. */
public static GameContainer from(Context context) {
return ((TerritoryWarsApp) context.getApplicationContext()).getContainer();
}
private void initAds() {
// Replace with your real AdMob app ID in production.
MobileAds.initialize(this, new OnInitializationCompleteListener() {
@Override
public void onInitializationComplete(InitializationStatus initializationStatus) {
// nothing urgent to do — ads load lazily
}
});
}
}
