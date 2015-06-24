/*
 * Copyright (C) 2013 SlimRoms Project
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

import android.app.FragmentTransaction;
import android.view.animation.AnimationUtils;
import android.app.Activity;
import android.app.AlertDialog;
import android.app.Dialog;
import android.app.DialogFragment;
import android.app.Fragment;
import android.app.FragmentManager;
import android.app.LoaderManager;
import android.content.ActivityNotFoundException;
import android.content.ContentResolver;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.Loader;
import android.content.pm.PackageManager;
import android.content.SharedPreferences;
import android.net.Uri;
import android.os.Bundle;
import android.preference.PreferenceManager;
import android.provider.Settings;
import android.text.TextUtils;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.Menu;
import android.view.MenuInflater;
import android.view.MenuItem;
import android.view.View;
import android.view.ViewGroup;
import android.widget.AdapterView;
import android.widget.AdapterView.OnItemClickListener;
import android.widget.AdapterView.OnItemLongClickListener;
import android.widget.ListView;
import android.widget.TextView;

import com.android.settings.R;
import com.android.settings.SubSettings;
import com.android.settings.recentsprivacy.AppInfoLoader;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.LinkedList;
import java.util.List;

final class RecentsPrivacySettings {
    public boolean setRecentsPrivacySettingForApplication(Context context, String packageName, boolean enabled) {
        ContentResolver resolver = context.getContentResolver();
        String order = Settings.Secure.getString(resolver, android.provider.Settings.Secure.SYSUI_RECENTS_PRIVACY);
        if (order == null) {
            Settings.Secure.putString(resolver, android.provider.Settings.Secure.SYSUI_RECENTS_PRIVACY, "");
            order = Settings.Secure.getString(resolver, android.provider.Settings.Secure.SYSUI_RECENTS_PRIVACY);
        }
        List<String> mPackageNames = new LinkedList<String>(Arrays.asList(order.split(",")));
        if (enabled && !mPackageNames.contains(packageName)) {
            mPackageNames.add(packageName);
        } else if (!enabled && mPackageNames.contains(packageName)) {
            mPackageNames.remove(packageName);
        } else {
            // something went wrong
            return !enabled;
        }
        StringBuilder packageList = new StringBuilder();
        for (String s : mPackageNames) {
            if (packageList.length() > 0) {
                packageList.append(",");
            }
            packageList.append(s);
        }  
        Settings.Secure.putString(resolver, android.provider.Settings.Secure.SYSUI_RECENTS_PRIVACY, 
                                                                                 packageList.toString());
        return enabled;
    }

    public boolean getRecentsPrivacySettingForApplication(Context context, String packageName) {
        ContentResolver resolver = context.getContentResolver();
        String order = Settings.Secure.getString(resolver, android.provider.Settings.Secure.SYSUI_RECENTS_PRIVACY);
        if (order == null) {
            Settings.Secure.putString(resolver, android.provider.Settings.Secure.SYSUI_RECENTS_PRIVACY, "");
            order = Settings.Secure.getString(resolver, android.provider.Settings.Secure.SYSUI_RECENTS_PRIVACY);
        }
        List<String> mPackageNames = new LinkedList<String>(Arrays.asList(order.split(",")));
        if (mPackageNames.contains(packageName)) {
            return true;
        }        
        return false;
    }

    public List<String>getPackageListFromSettings(Context context) {
        ContentResolver resolver = context.getContentResolver();
        String order = Settings.Secure.getString(resolver, android.provider.Settings.Secure.SYSUI_RECENTS_PRIVACY);
        if (order == null) {
            Settings.Secure.putString(resolver, android.provider.Settings.Secure.SYSUI_RECENTS_PRIVACY, "");
            order = Settings.Secure.getString(resolver, android.provider.Settings.Secure.SYSUI_RECENTS_PRIVACY);
        }
        List<String> mPackageNames = new LinkedList<String>(Arrays.asList(order.split(",")));
        return mPackageNames;
    }

    public void resetRecentsPrivacySettings(Context context) {
        ContentResolver resolver = context.getContentResolver();
        Settings.Secure.putString(resolver, android.provider.Settings.Secure.SYSUI_RECENTS_PRIVACY, "");
    }
}

public class RecentsPrivacyManager extends Fragment
        implements OnItemClickListener, OnItemLongClickListener,
                   LoaderManager.LoaderCallbacks<List<RecentsPrivacyManager.AppInfo>> {

    private static final String TAG = "RecentsPrivacyManager";

    private ListView mAppsList;
    private View mLoadingContainer;
    private RecentsPrivacyAppListAdapter mAdapter;
    private List<AppInfo> mApps;

    private Activity mActivity;

    private SharedPreferences mPreferences;

    private int mSavedFirstVisiblePosition = AdapterView.INVALID_POSITION;
    private int mSavedFirstItemOffset;

    private Context mContext;

    // keys for extras and icicles
    private final static String LAST_LIST_POS = "last_list_pos";
    private final static String LAST_LIST_OFFSET = "last_list_offset";

    // Privacy Guard Fragment
    private final static String RECENTS_PRIVACY_FRAGMENT_TAG = "recents_privacy_fragment";

    // holder for package data passed into the adapter
    public static final class AppInfo {
        String title;
        String packageName;
        boolean enabled;
        boolean recentsPrivacyEnabled;
        int uid;
    }

    @Override
    public void onAttach(Activity activity) {
        super.onAttach(activity);
        mContext = getActivity();
    }

    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container,
            Bundle savedInstanceState) {
        mActivity = getActivity();

        View hostView = inflater.inflate(R.layout.recents_privacy_manager, container, false);
 
        Fragment recentsPrivacyPrefs = RecentsPrivacyPrefs.newInstance();
        FragmentTransaction fragmentTransaction = getChildFragmentManager().beginTransaction();
        fragmentTransaction.replace(R.id.recents_privacy_prefs, recentsPrivacyPrefs,
                RECENTS_PRIVACY_FRAGMENT_TAG);
        fragmentTransaction.commit();
        return hostView;
    }

    @Override
    public void onActivityCreated(Bundle savedInstanceState) {
        super.onActivityCreated(savedInstanceState);

        mAppsList = (ListView) mActivity.findViewById(R.id.apps_list);
        mAppsList.setOnItemClickListener(this);

        mLoadingContainer = mActivity.findViewById(R.id.loading_container);

        if (savedInstanceState != null) {
            mSavedFirstVisiblePosition = savedInstanceState.getInt(LAST_LIST_POS,
                    AdapterView.INVALID_POSITION);
            mSavedFirstItemOffset = savedInstanceState.getInt(LAST_LIST_OFFSET, 0);
        } else {
            mSavedFirstVisiblePosition = AdapterView.INVALID_POSITION;
            mSavedFirstItemOffset = 0;
        }

        // load apps and construct the list
        scheduleAppsLoad();

        setHasOptionsMenu(true);
    }

    @Override
    public void onViewStateRestored(Bundle savedInstanceState) {
        super.onViewStateRestored(savedInstanceState);
    }

    @Override
    public void onSaveInstanceState(Bundle outState) {
        super.onSaveInstanceState(outState);

        outState.putInt(LAST_LIST_POS, mSavedFirstVisiblePosition);
        outState.putInt(LAST_LIST_OFFSET, mSavedFirstItemOffset);
    }

    @Override
    public void onPause() {
        super.onPause();

        // Remember where the list is scrolled to so we can restore the scroll position
        // when we come back to this activity and *after* we complete querying for the
        // conversations.
        mSavedFirstVisiblePosition = mAppsList.getFirstVisiblePosition();
        View firstChild = mAppsList.getChildAt(0);
        mSavedFirstItemOffset = (firstChild == null) ? 0 : firstChild.getTop();
    }

    @Override
    public void onResume() {
        super.onResume();
        // rebuild the list; the user might have changed settings inbetween
        scheduleAppsLoad();
    }

    @Override
    public Loader<List<AppInfo>> onCreateLoader(int id, Bundle args) {
        mLoadingContainer.startAnimation(AnimationUtils.loadAnimation(
              mActivity, android.R.anim.fade_in));
        mAppsList.startAnimation(AnimationUtils.loadAnimation(
              mActivity, android.R.anim.fade_out));

        mAppsList.setVisibility(View.INVISIBLE);
        mLoadingContainer.setVisibility(View.VISIBLE);
        return new AppInfoLoader(mActivity);
    }

    @Override
    public void onLoadFinished(Loader<List<AppInfo>> loader, List<AppInfo> apps) {
        mApps = apps;
        prepareAppAdapter();

        mLoadingContainer.startAnimation(AnimationUtils.loadAnimation(
              mActivity, android.R.anim.fade_out));
        mAppsList.startAnimation(AnimationUtils.loadAnimation(
              mActivity, android.R.anim.fade_in));

        if (mSavedFirstVisiblePosition != AdapterView.INVALID_POSITION) {
            mAppsList.setSelectionFromTop(mSavedFirstVisiblePosition, mSavedFirstItemOffset);
            mSavedFirstVisiblePosition = AdapterView.INVALID_POSITION;
        }

        mLoadingContainer.setVisibility(View.INVISIBLE);
        mAppsList.setVisibility(View.VISIBLE);
    }

    @Override
    public void onLoaderReset(Loader<List<AppInfo>> loader) {
    }

    private void scheduleAppsLoad() {
        getLoaderManager().restartLoader(0, null, this);
    }

    private void prepareAppAdapter() {
        mAppsList.setVisibility(View.VISIBLE);
        mAdapter = createAdapter();
        mAppsList.setAdapter(mAdapter);
        mAppsList.setFastScrollEnabled(true);       
    }

    private RecentsPrivacyAppListAdapter createAdapter() {
        String lastSectionIndex = null;
        ArrayList<String> sections = new ArrayList<String>();
        ArrayList<Integer> positions = new ArrayList<Integer>();
        int count = mApps.size(), offset = 0;

        for (int i = 0; i < count; i++) {
            AppInfo app = mApps.get(i);
            String sectionIndex;

            if (!app.enabled) {
                sectionIndex = "--"; //XXX
            } else if (app.title.isEmpty()) {
                sectionIndex = "";
            } else {
                sectionIndex = app.title.substring(0, 1).toUpperCase();
            }

            if (lastSectionIndex == null ||
                    !TextUtils.equals(sectionIndex, lastSectionIndex)) {
                sections.add(sectionIndex);
                positions.add(offset);
                lastSectionIndex = sectionIndex;
            }
            offset++;
        }

        return new RecentsPrivacyAppListAdapter(mActivity, mApps, sections, positions);
    }

    private void resetRecentsPrivacy() {
        if (mApps == null || mApps.isEmpty()) {
            return;
        }
        showResetDialog();
    }

    @Override
    public void onItemClick(AdapterView<?> parent, View view, int position, long id) {
        // on click change the privacy guard status for this item
        final AppInfo app = (AppInfo) parent.getItemAtPosition(position);
        final RecentsPrivacySettings rPs = new RecentsPrivacySettings();

        app.recentsPrivacyEnabled = rPs.setRecentsPrivacySettingForApplication(mContext, app.packageName, !app.recentsPrivacyEnabled);
        mAdapter.notifyDataSetChanged();
    }

    @Override
    public boolean onItemLongClick(AdapterView<?> parent, View view, int position, long id) {
         return true;
    }

    private class ResetDialogFragment extends DialogFragment {
       private Context mContext;

       @Override
        public void onAttach(Activity activity) {
            super.onAttach(activity);
            mContext = getActivity();
        }


        @Override
        public Dialog onCreateDialog(Bundle savedInstanceState) {
            return new AlertDialog.Builder(getActivity())
                    .setTitle(R.string.recents_privacy_reset_title)
                    .setMessage(R.string.recents_privacy_reset_text)
                    .setPositiveButton(R.string.dlg_ok,
                        new DialogInterface.OnClickListener() {
                            public void onClick(DialogInterface dialog, int which) {
                                // disable hiding thumbnails for all apps
                                for (AppInfo app : mApps) {
                                    app.recentsPrivacyEnabled = false;
                                }
                                final RecentsPrivacySettings rPs = new RecentsPrivacySettings();
                                rPs.resetRecentsPrivacySettings(mContext);
                                mAdapter.notifyDataSetChanged();
                        }
                    })
                    .setNegativeButton(R.string.cancel,
                        new DialogInterface.OnClickListener() {
                            public void onClick(DialogInterface dialog, int which) {
                                // Do nothing
                        }
                    })
                    .create();
        }
    }

    private void showResetDialog() {
        ResetDialogFragment dialog = new ResetDialogFragment();
        dialog.show(getFragmentManager(), "reset_dialog");
    }

    @Override
    public void onCreateOptionsMenu(Menu menu, MenuInflater inflater) {
        super.onCreateOptionsMenu(menu, inflater);
        inflater.inflate(R.menu.recents_privacy_manager, menu);
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        switch (item.getItemId()) {
            case R.id.reset:
                resetRecentsPrivacy();
                return true;
            default:
                return super.onContextItemSelected(item);
        }
    }
}
