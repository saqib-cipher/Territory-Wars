# Territory Wars - Online Server Setup & Client Configuration Guide

This guide provides step-by-step instructions to deploy the **Territory Wars** Node.js backend server online and configure the Android app to connect to your live production server, Google Firebase Auth, AdMob, and Google Play Billing.

---

## 📋 Table of Contents

1. [Recommended Deployment Platforms](#1-recommended-deployment-platforms)
2. [Server Setup & Environment Variables (.env)](#2-server-setup--environment-variables-env)
3. [Deploying Backend to Render.com (Recommended Free/Easy)](#3-deploying-backend-to-rendercom-recommended-freeeasy)
4. [Deploying Backend to Railway.app](#4-deploying-backend-to-railwayapp)
5. [Deploying Backend to a VPS (Ubuntu / DigitalOcean / AWS)](#5-deploying-backend-to-a-vps-ubuntu--digitalocean--aws)
6. [Android Client Configuration (Where to Change URLs & IDs)](#6-android-client-configuration-where-to-change-urls--ids)
   - [A. Changing Server API & Socket URLs](#a-changing-server-api--socket-urls)
   - [B. Google Sign-In & Firebase Configuration](#b-google-sign-in--firebase-configuration)
   - [C. AdMob Ads Configuration](#c-admob-ads-configuration)
   - [D. Google Play Billing Setup](#d-google-play-billing-setup)
7. [Verification & Troubleshooting](#7-verification--troubleshooting)

---

## 1. Recommended Deployment Platforms

### Backend & WebSocket Hosting:
- **Render.com (Recommended)**: Free tier, built-in SSL/TLS (`https://` & `wss://`), native Node.js and Socket.IO support.
- **Railway.app**: Excellent performance, automatic deployment from GitHub, easy managed PostgreSQL and Redis addons.
- **DigitalOcean App Platform / AWS App Runner**: Great for scalable cloud hosting.
- **Ubuntu VPS (DigitalOcean / AWS EC2 / Vultr)**: Best for total control, high performance, PM2 process management, and Nginx reverse proxy.

### Database & Cache Hosting:
- **PostgreSQL**: Render PostgreSQL, Railway Postgres, Supabase, or Neon.tech.
- **Redis (Socket.IO adapter & state)**: Upstash Redis, Render Redis, or Railway Redis.

---

## 2. Server Setup & Environment Variables (.env)

The Node.js server reads configuration from `server/.env`. Below is the complete list of environment variables you need to configure:

### `.env` File Template:
```ini
# Server Configuration
NODE_ENV=production
PORT=8080
BASE_URL=https://your-backend-domain.com

# PostgreSQL Database Connection URL
DATABASE_URL=postgres://username:password@host:5432/territory_wars?sslmode=require

# Redis Connection URL
REDIS_URL=redis://default:password@redis-host:6379

# JWT Secret Token (Use a strong random string)
JWT_SECRET=c8f92a10b457e3f89012cd3e4567890abcdef1234567890abcdef123456789
JWT_EXPIRES_IN=30d

# Google OAuth & Service Account
GOOGLE_WEB_CLIENT_ID=YOUR_GOOGLE_WEB_CLIENT_ID.apps.googleusercontent.com
GOOGLE_SERVICE_ACCOUNT_FILE=./service_account.json

# Monetization & Package Settings
GOOGLE_PLAY_PACKAGE_NAME=com.territorywars
ADMOB_APP_ID=ca-app-pub-3940256099942544~3347511713

# Security & Rate Limiting
RATE_LIMIT_WINDOW_MS=60000
RATE_LIMIT_MAX=120
```

### How to Generate a Secure JWT Secret:
In Linux / macOS / Git Bash, run:
```bash
openssl rand -hex 32
```

---

## 3. Deploying Backend to Render.com (Recommended Free/Easy)

1. **Push Repository to GitHub**.
2. **Create Managed PostgreSQL Database**:
   - Go to [Render Dashboard](https://dashboard.render.com/) -> Click **New** -> **PostgreSQL**.
   - Copy the **Internal Database URL**.
3. **Create Managed Redis Instance**:
   - Click **New** -> **Redis**.
   - Copy the **Internal Redis URL**.
4. **Deploy Web Service**:
   - Click **New** -> **Web Service**.
   - Connect your GitHub repository.
   - Set **Root Directory**: `server`
   - Set **Environment**: `Node`
   - Set **Build Command**: `npm install`
   - Set **Start Command**: `npm start`
5. **Add Environment Variables**:
   - In your Render Web Service settings -> **Environment**:
     - `NODE_ENV`: `production`
     - `DATABASE_URL`: *(Your Render PostgreSQL URL)*
     - `REDIS_URL`: *(Your Render Redis URL)*
     - `JWT_SECRET`: *(Your generated JWT secret)*
     - `GOOGLE_WEB_CLIENT_ID`: *(Your Google Web Client ID)*
6. Copy your live backend URL (e.g. `https://territory-wars-api.onrender.com`).

---

## 4. Deploying Backend to Railway.app

1. Go to [Railway.app](https://railway.app/) and create a new project.
2. Add **PostgreSQL** and **Redis** database plugins.
3. Add a **GitHub Service** pointing to your repository's `/server` directory.
4. Set the Environment Variables (`DATABASE_URL`, `REDIS_URL`, `JWT_SECRET`, etc.).
5. Railway automatically generates an HTTPS domain for your Web API & WebSocket endpoints.

---

## 5. Deploying Backend to a VPS (Ubuntu / DigitalOcean / AWS)

If deploying to an Ubuntu 22.04 / 24.04 Virtual Private Server:

```bash
# 1. Install Node.js 20 & PM2
curl -fsSL https://deb.nodesource.com/setup_20.x | sudo -E bash -
sudo apt-get install -y nodejs postgresql redis-server nginx
sudo npm install -g pm2

# 2. Clone Repository & Install Dependencies
git clone https://github.com/your-username/Territory-Wars.git
cd Territory-Wars/server
npm install

# 3. Create Production .env
cp .env.example .env
nano .env

# 4. Start Server with PM2
pm2 start src/index.js --name "territory-wars-backend"
pm2 save
pm2 startup

# 5. Configure Nginx Reverse Proxy & SSL (Certbot)
sudo apt-get install -y certbot python3-certbot-nginx
sudo certbot --nginx -d api.yourdomain.com
```

Nginx configuration location (`/etc/nginx/sites-available/territory-wars`):
```nginx
server {
    server_name api.yourdomain.com;

    location / {
        proxy_pass http://localhost:8080;
        proxy_http_version 1.1;
        proxy_set_header Upgrade $http_upgrade;
        proxy_set_header Connection "Upgrade";
        proxy_set_header Host $host;
        proxy_set_header X-Real-IP $remote_addr;
        proxy_set_header X-Forwarded-For $proxy_add_x_forwarded_for;
    }
}
```

---

## 6. Android Client Configuration (Where to Change URLs & IDs)

All client-side API endpoints, Firebase keys, AdMob IDs, and Play Store package IDs are configured in the `android/` codebase:

### A. Changing Server API & Socket URLs

Open `android/app/build.gradle` or update `gradle.properties`:

**Option 1: In `gradle.properties` (Recommended)**
Add the following properties to `android/gradle.properties`:
```properties
tw.apiBaseUrlDebug=http://10.0.2.2:8080/v1
tw.socketUrlDebug=http://10.0.2.2:8080
tw.apiBaseUrlRelease=https://api.yourdomain.com/v1
tw.socketUrlRelease=https://api.yourdomain.com
```

**Option 2: Direct Edit in `android/app/build.gradle`**
Modify lines 19-36 of [android/app/build.gradle](file:///c:/Users/sddrk/Documents/Projects/Territory%20Wars/android/app/build.gradle):
```groovy
defaultConfig {
    buildConfigField "String", "API_BASE_URL", "\"https://api.yourdomain.com/v1\""
    buildConfigField "String", "SOCKET_URL", "\"https://api.yourdomain.com\""
}
```

---

### B. Google Sign-In & Firebase Configuration

To enable Google Auth & Firebase Sign-In:

1. Go to [Firebase Console](https://console.firebase.google.com/) -> Create a Project.
2. Add an **Android App** with package name `com.territorywars`.
3. Add your SHA-1 signing fingerprint (obtain using `./gradlew signingReport`).
4. Download `google-services.json` and place it in:
   `android/app/google-services.json`
5. Copy your **Web Client ID** from Firebase (*Project Settings -> Authentication -> Sign-in method -> Google -> Web client ID*).
6. Open [strings.xml](file:///c:/Users/sddrk/Documents/Projects/Territory%20Wars/android/app/src/main/res/values/strings.xml) line 44 and replace `YOUR_GOOGLE_WEB_CLIENT_ID`:
```xml
<string name="default_web_client_id" translatable="false">YOUR_ACTUAL_CLIENT_ID.apps.googleusercontent.com</string>
```

---

### C. AdMob Ads Configuration

To replace test AdMob ads with your live AdMob account:

1. Go to [Google AdMob Console](https://admob.google.com/).
2. Create an App for Android and copy your **AdMob App ID**.
3. Open `android/app/src/main/AndroidManifest.xml` and replace the meta-data value:
```xml
<meta-data
    android:name="com.google.android.gms.ads.APPLICATION_ID"
    android:value="ca-app-pub-YOUR_ADMOB_APP_ID~XXXXXXXXXX"/>
```
4. Create Banner, Interstitial, and Rewarded Ad units in AdMob.
5. Open [AdManager.java](file:///c:/Users/sddrk/Documents/Projects/Territory%20Wars/android/app/src/main/java/com/territorywars/ads/AdManager.java) and replace the Ad Unit IDs:
```java
public static final String BANNER_AD_UNIT_ID = "ca-app-pub-YOUR_ADMOB_APP_ID/BANNER_ID";
public static final String INTERSTITIAL_AD_UNIT_ID = "ca-app-pub-YOUR_ADMOB_APP_ID/INTERSTITIAL_ID";
public static final String REWARDED_AD_UNIT_ID = "ca-app-pub-YOUR_ADMOB_APP_ID/REWARDED_ID";
```

---

### D. Google Play Billing Setup

To handle In-App Purchases (Gems, Premium Subscription, Coins):

1. Set up a Developer Account on [Google Play Console](https://play.google.com/console).
2. Create In-App Products / Subscriptions matching product IDs in [BillingManager.java](file:///c:/Users/sddrk/Documents/Projects/Territory%20Wars/android/app/src/main/java/com/territorywars/billing/BillingManager.java):
   - `tw_coins_100` (100 Coins)
   - `tw_gems_10` (10 Gems)
   - `tw_premium_monthly` (Monthly Subscription)
   - `tw_premium_yearly` (Yearly Subscription)
3. Download Service Account JSON from Google Cloud Console with Google Play Developer API access and upload it to `server/service_account.json`.

---

## 7. Verification & Troubleshooting

### Test Server Health:
Send an HTTP GET request to check server status:
```bash
curl https://api.yourdomain.com/v1/profile
```
Expected response: `401 Unauthorized` (indicating server API is active and enforcing JWT auth).

### Test Android App Build:
Build the release APK with your live backend URLs:
```bash
cd android
./gradlew assembleRelease
```

---

### 💡 Quick Checklist Before Release
- [ ] Live Server deployed with HTTPS & SSL certificate.
- [ ] `tw.apiBaseUrlRelease` & `tw.socketUrlRelease` updated in `gradle.properties`.
- [ ] `google-services.json` placed in `android/app/`.
- [ ] `default_web_client_id` updated in `strings.xml`.
- [ ] `ADMOB_APP_ID` & Ad Unit IDs updated.
- [ ] `JWT_SECRET` generated and updated in `server/.env`.
