/*
 * Copyright (C) 2015 The CyanogenMod Project
 *
 * Copyright (C) 2015 zzpianoman@gmail.com
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.android.settings.recentsprivacy;

import android.app.ActivityManager;
import android.content.AsyncTaskLoader;
import android.content.Context;
import android.content.pm.ApplicationInfo;
import android.content.pm.PackageManager;
import android.content.pm.PackageInfo;
import android.content.pm.ResolveInfo;
import android.graphics.Bitmap;
import android.graphics.drawable.BitmapDrawable;
import android.graphics.drawable.Drawable;

import com.android.settings.recentsprivacy.RecentsPrivacyManager.AppInfo;

import android.os.Bundle;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;

/**
 * An asynchronous loader implementation that loads AppInfo structures.
 */
/* package */ class AppInfoLoader extends AsyncTaskLoader<List<AppInfo>> {
    private PackageManager mPm;
    private static final String[] BLACKLISTED_PACKAGES = {
            "com.android.systemui",
            "com.cyanogenmod.trebuchet",
            "com.android.inputmethod.latin",
            "com.vzw.apnservice",
            "com.android.providers.settings",
            "com.android.nfc",
            "com.android.mms.service",
            "com.android.providers.calendar",
            "android"
    };
    
    private Context mContext;

    public AppInfoLoader(Context context) {
        super(context);
        mPm = context.getPackageManager();
        mContext = context;
    }

    @Override
    public List<AppInfo> loadInBackground() {
        return loadInstalledApps();
    }

    @Override
    public void onStartLoading() {
        forceLoad();
    }

    @Override
    public void onStopLoading() {
        cancelLoad();
    }

    @Override
    protected void onReset() {
        cancelLoad();
    }

    private boolean isBlacklisted(String packageName) {
        for (String pkg : BLACKLISTED_PACKAGES)  {
            if (pkg.equals(packageName)) {
                return true;
            }
        }
        return false;
    }

    private boolean signaturesMatch(String pkg1, String pkg2) {
        if (pkg1 != null && pkg2 != null) {
            try {
                final int match = mPm.checkSignatures(pkg1, pkg2);
                if (match >= PackageManager.SIGNATURE_MATCH) {
                    return true;
                }
            } catch (Exception e) {
                // e.g. named alternate package not found during lookup;
                // this is an expected case sometimes
            }
        }
        return false;
    }

    private boolean hasIcon (ApplicationInfo appInfo) {
        try {
            Drawable icon = mPm.getApplicationIcon(appInfo.packageName);
            Drawable default_icon = mPm.getDefaultActivityIcon();
            if (icon instanceof BitmapDrawable && default_icon instanceof BitmapDrawable) {
                BitmapDrawable icon_bd = (BitmapDrawable)icon;
                Bitmap icon_b = icon_bd.getBitmap();
                BitmapDrawable default_bd = (BitmapDrawable)mPm.getDefaultActivityIcon();
                Bitmap default_b = default_bd.getBitmap();
                if (icon_b == default_b) {
		    return false;
                }
            }
        } catch (PackageManager.NameNotFoundException e) {
                    // ignored
        }
        return true;
    }

    private boolean isHomeApp (ApplicationInfo appInfo) {
        // Get list of "home" apps and trace through any meta-data references
        final HashSet<String> mHomePackages = new HashSet<String>();
        List<ResolveInfo> homeActivities = new ArrayList<ResolveInfo>();
        mPm.getHomeActivities(homeActivities);
        mHomePackages.clear();
        for (int i = 0; i< homeActivities.size(); i++) {
            ResolveInfo ri = homeActivities.get(i);
            final String activityPkg = ri.activityInfo.packageName;
            mHomePackages.add(activityPkg);

            // Also make sure to include anything proxying for the home app
            final Bundle metadata = ri.activityInfo.metaData;
            if (metadata != null) {
                final String metaPkg = metadata.getString(ActivityManager.META_HOME_ALTERNATE);
                if (signaturesMatch(metaPkg, activityPkg)) {
                    mHomePackages.add(metaPkg);
                }
            }
        }
        if (mHomePackages.contains(appInfo.packageName)) {
            return true;
        }
        return false;
    }

    /**
    * Uses the package manager to query for all currently installed apps
    * for the list.
    *
    * @return the complete List of installed applications (@code PrivacyGuardAppInfo)
    */
    private List<AppInfo> loadInstalledApps() {
        List<AppInfo> apps = new ArrayList<AppInfo>();
        List<ApplicationInfo> applications = mPm.getInstalledApplications(PackageManager.GET_META_DATA);
        RecentsPrivacySettings rPs = new RecentsPrivacySettings();
        List<String>mPackageNames = rPs.getPackageListFromSettings(mContext);
        for (ApplicationInfo appInfo : applications) {
            // don't include blacklisted apps, apps with no icon, home apps, disabled apps
            if (isBlacklisted(appInfo.packageName) || !hasIcon(appInfo) || isHomeApp(appInfo) ||
                                                                                  !appInfo.enabled) {
                continue;
            }
            AppInfo app = new AppInfo();
            app.recentsPrivacyEnabled = false;
            app.title = appInfo.loadLabel(mPm).toString();
            app.packageName = appInfo.packageName;
            app.enabled = appInfo.enabled;
            app.uid = appInfo.uid;
            //app.recentsPrivacyEnabled = rPs.getRecentsPrivacySettingForApplication(mContext, appInfo.packageName);
            if (mPackageNames.contains(appInfo.packageName)) {
                app.recentsPrivacyEnabled = true;
            }
            apps.add(app);
        }

        // sort the apps by their enabled state, then by title
        Collections.sort(apps, new Comparator<AppInfo>() {
            @Override
            public int compare(AppInfo lhs, AppInfo rhs) {
                if (lhs.recentsPrivacyEnabled != rhs.recentsPrivacyEnabled) {
                    return lhs.recentsPrivacyEnabled ? -1 : 1;
                }
                return lhs.title.compareToIgnoreCase(rhs.title);
            }
        });

        return apps;
    }
}
