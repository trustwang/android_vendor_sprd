/*
 * Copyright (C) 2010 The Android Open Source Project
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
package com.android.contacts.list;

import android.content.Context;
import android.content.CursorLoader;
import android.content.Intent;
import android.net.Uri;
import android.provider.ContactsContract.Contacts;
import android.text.TextUtils;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.View.OnClickListener;
import android.view.ViewGroup;
import android.view.accessibility.AccessibilityEvent;
import android.widget.Button;
import android.widget.FrameLayout;
import android.widget.ListView;
import android.widget.TextView;

import com.android.contacts.R;
import com.android.contacts.common.list.ContactListAdapter;
import com.android.contacts.common.list.ContactListFilter;
import com.android.contacts.common.list.ContactListFilterController;
import com.android.contacts.common.list.ContactListItemView;
import com.android.contacts.common.list.DefaultContactListAdapter;
import com.android.contacts.common.list.ProfileAndContactsLoader;
import com.android.contacts.common.util.ImplicitIntentsUtil;
import com.android.contacts.editor.ContactEditorFragment;
import com.android.contacts.common.util.AccountFilterUtil;
/**
 * SPRD:
 *
 * @{
 */
import android.database.Cursor;
import android.widget.Toast;
import android.widget.LinearLayout;
import com.android.contacts.ContactsApplication;
import com.android.contacts.ContactSaveService;
import com.sprd.contacts.common.model.account.PhoneAccountType;
import com.sprd.contacts.common.plugin.FastScrollBarSupportUtils;
/**
 * @}
 */

/**
 * Fragment containing a contact list used for browsing (as compared to
 * picking a contact with one of the PICK intents).
 */
public class DefaultContactBrowseListFragment extends ContactBrowseListFragment {
    private static final String TAG = DefaultContactBrowseListFragment.class.getSimpleName();

    private static final int REQUEST_CODE_ACCOUNT_FILTER = 1;

    private View mSearchHeaderView;
    private View mAccountFilterHeader;
    private FrameLayout mProfileHeaderContainer;
    private View mProfileHeader;
    private Button mProfileMessage;
    private TextView mProfileTitle;
    private View mSearchProgress;
    private TextView mSearchProgressText;

    private class FilterHeaderClickListener implements OnClickListener {
        @Override
        public void onClick(View view) {
            /*
             * SPRD:
             * Bug 451973 Do not show AccountFilterActivity by clicking list header view when batchOperation running.
             *
             * Original Android code:
            AccountFilterUtil.startAccountFilterActivityForResult(
                        DefaultContactBrowseListFragment.this,
                        REQUEST_CODE_ACCOUNT_FILTER,
                        getFilter());
             *
             * @{
             */
            if(ContactsApplication.sApplication.isBatchOperation() || ContactSaveService.mIsGroupSaving) {
                Toast.makeText(getActivity(), R.string.toast_batchoperation_is_running,
                        Toast.LENGTH_LONG).show();
            } else {
                AccountFilterUtil.startAccountFilterActivityForResult(
                        DefaultContactBrowseListFragment.this,
                        REQUEST_CODE_ACCOUNT_FILTER,
                        getFilter());
            }
            /*
             * @}
             */
        }
    }
    private OnClickListener mFilterHeaderClickListener = new FilterHeaderClickListener();

    public DefaultContactBrowseListFragment() {
        setPhotoLoaderEnabled(true);
        // Don't use a QuickContactBadge. Just use a regular ImageView. Using a QuickContactBadge
        // inside the ListView prevents us from using MODE_FULLY_EXPANDED and messes up ripples.
        setQuickContactEnabled(false);
        setSectionHeaderDisplayEnabled(true);
        setVisibleScrollbarEnabled(true);
    }

    @Override
    public CursorLoader createCursorLoader(Context context) {
        return new ProfileAndContactsLoader(context);
    }

    @Override
    protected void onItemClick(int position, long id) {
        final Uri uri = getAdapter().getContactUri(position);
        if (uri == null) {
            return;
        }
        viewContact(uri, getAdapter().isEnterpriseContact(position));
    }

    @Override
    protected ContactListAdapter createListAdapter() {
        DefaultContactListAdapter adapter = new DefaultContactListAdapter(getContext());
        adapter.setSectionHeaderDisplayEnabled(isSectionHeaderDisplayEnabled());
        adapter.setDisplayPhotos(true);
        adapter.setPhotoPosition(
                ContactListItemView.getDefaultPhotoPosition(/* opposite = */ false));
        return adapter;
    }

    @Override
    protected View inflateView(LayoutInflater inflater, ViewGroup container) {
        /**
         * sprd bug539262 show letter scroll bar in contacts list view
         * @{
         */
        if (FastScrollBarSupportUtils.getInstance().hasSupportFastScrollBar()) {
            return inflater.inflate(R.layout.contact_list_content_with_blade_view, null);
        }
        /**
         * @}
         */
        return inflater.inflate(R.layout.contact_list_content, null);
    }

    @Override
    protected void onCreateView(LayoutInflater inflater, ViewGroup container) {
        super.onCreateView(inflater, container);

        mAccountFilterHeader = getView().findViewById(R.id.account_filter_header_container);
        mAccountFilterHeader.setOnClickListener(mFilterHeaderClickListener);

        // Create an empty user profile header and hide it for now (it will be visible if the
        // contacts list will have no user profile).
        addEmptyUserProfileHeader(inflater);
        showEmptyUserProfile(false);

        // Putting the header view inside a container will allow us to make
        // it invisible later. See checkHeaderViewVisibility()
        FrameLayout headerContainer = new FrameLayout(inflater.getContext());
        mSearchHeaderView = inflater.inflate(R.layout.search_header, null, false);
        headerContainer.addView(mSearchHeaderView);
        getListView().addHeaderView(headerContainer, null, false);
        checkHeaderViewVisibility();

        mSearchProgress = getView().findViewById(R.id.search_progress);
        mSearchProgressText = (TextView) mSearchHeaderView.findViewById(R.id.totalContactsText);
    }

    @Override
    protected void setSearchMode(boolean flag) {
        super.setSearchMode(flag);
        checkHeaderViewVisibility();
        if (!flag) showSearchProgress(false);
    }

    /** Show or hide the directory-search progress spinner. */
    private void showSearchProgress(boolean show) {
        if (mSearchProgress != null) {
            mSearchProgress.setVisibility(show ? View.VISIBLE : View.GONE);
        }
    }

    private void checkHeaderViewVisibility() {
        updateFilterHeaderView();

        // Hide the search header by default.
        if (mSearchHeaderView != null) {
            mSearchHeaderView.setVisibility(View.GONE);
        }
    }

    @Override
    public void setFilter(ContactListFilter filter) {
        super.setFilter(filter);
        updateFilterHeaderView();
    }

    private void updateFilterHeaderView() {
        if (mAccountFilterHeader == null) {
            return; // Before onCreateView -- just ignore it.
        }
        final ContactListFilter filter = getFilter();
        if (filter != null && !isSearchMode()) {
            final boolean shouldShowHeader = AccountFilterUtil.updateAccountFilterTitleForPeople(
                    mAccountFilterHeader, filter, false);
            mAccountFilterHeader.setVisibility(shouldShowHeader ? View.VISIBLE : View.GONE);
        } else {
            mAccountFilterHeader.setVisibility(View.GONE);
        }
    }

    @Override
    protected void setProfileHeader() {
        mUserProfileExists = getAdapter().hasProfile();

        /* SPRD: add for bug 474264
         * orig:
        showEmptyUserProfile(!mUserProfileExists && !isSearchMode());
         *@{
         */
        if(isSearchMode()) {
            showEmptyUserProfile(false);
        } else if(!mUserProfileExists) {
            showEmptyUserProfile(true);
        } else {
            showContactsCount();
        }
        /**
         * @}
         */

        if (isSearchMode()) {
            ContactListAdapter adapter = getAdapter();
            if (adapter == null) {
                return;
            }

            // In search mode we only display the header if there is nothing found
            if (TextUtils.isEmpty(getQueryString()) || !adapter.areAllPartitionsEmpty()) {
                mSearchHeaderView.setVisibility(View.GONE);
                showSearchProgress(false);
            } else {
                mSearchHeaderView.setVisibility(View.VISIBLE);
                if (adapter.isLoading()) {
                    mSearchProgressText.setText(R.string.search_results_searching);
                    showSearchProgress(true);
                } else {
                    mSearchProgressText.setText(R.string.listFoundAllContactsZero);
                    mSearchProgressText.sendAccessibilityEvent(
                            AccessibilityEvent.TYPE_VIEW_SELECTED);
                    showSearchProgress(false);
                }
            }
            showEmptyUserProfile(false);
        }
    }

    @Override
    public void onActivityResult(int requestCode, int resultCode, Intent data) {
        if (requestCode == REQUEST_CODE_ACCOUNT_FILTER) {
            if (getActivity() != null) {
                AccountFilterUtil.handleAccountFilterResult(
                        ContactListFilterController.getInstance(getActivity()), resultCode, data);
            } else {
                Log.e(TAG, "getActivity() returns null during Fragment#onActivityResult()");
            }
        }
    }

    private void showEmptyUserProfile(boolean show) {
        // Changing visibility of just the mProfileHeader doesn't do anything unless
        // you change visibility of its children, hence the call to mCounterHeaderView
        // and mProfileTitle
        mProfileHeaderContainer.setVisibility(show ? View.VISIBLE : View.GONE);
        mProfileHeader.setVisibility(show ? View.VISIBLE : View.GONE);
        mProfileTitle.setVisibility(show ? View.VISIBLE : View.GONE);
        mProfileMessage.setVisibility(show ? View.VISIBLE : View.GONE);
        /**
         * SPRD: add for bug 474264
         * @{
         */
        mProfileInfo.setVisibility(show ? View.VISIBLE : View.GONE);
        mCounterHeaderView.setVisibility(show ? View.VISIBLE : View.GONE);
        /**
         * @}
         */
    }

    /**
     * This method creates a pseudo user profile contact. When the returned query doesn't have
     * a profile, this methods creates 2 views that are inserted as headers to the listview:
     * 1. A header view with the "ME" title and the contacts count.
     * 2. A button that prompts the user to create a local profile
     */
    private void addEmptyUserProfileHeader(LayoutInflater inflater) {
        ListView list = getListView();
        // Add a header with the "ME" name. The view is embedded in a frame view since you cannot
        // change the visibility of a view in a ListView without having a parent view.

        /* SPRD: Modify for bug 474264
         * orig:
        mProfileHeader = inflater.inflate(R.layout.user_profile_header, null, false);
         * @{
         */
        mProfileHeader = inflater.inflate(R.layout.user_profile_header_overlay, null, false);
        mCounterHeaderView = (TextView) mProfileHeader.findViewById(R.id.contacts_count);
        if (mCounterHeaderView != null) {
            mCounterHeaderView.setVisibility(View.VISIBLE);
        }
        mProfileInfo = (LinearLayout)  mProfileHeader.findViewById(R.id.user_profile_header);
        /**
         * @}
         */

        mProfileTitle = (TextView) mProfileHeader.findViewById(R.id.profile_title);
        mProfileHeaderContainer = new FrameLayout(inflater.getContext());
        mProfileHeaderContainer.addView(mProfileHeader);
        list.addHeaderView(mProfileHeaderContainer, null, false);

        // Add a button with a message inviting the user to create a local profile
        mProfileMessage = (Button) mProfileHeader.findViewById(R.id.user_profile_button);
        mProfileMessage.setOnClickListener(new View.OnClickListener() {
            public void onClick(View v) {
                /*
                 * SPRD:
                 * Bug 439011 Crashed when continuously create profile with photo during batchOperation running.
                 *
                 * Original Android code:
                Intent intent = new Intent(Intent.ACTION_INSERT, Contacts.CONTENT_URI);
                intent.putExtra(ContactEditorFragment.INTENT_EXTRA_NEW_LOCAL_PROFILE, true);
                ImplicitIntentsUtil.startActivityInApp(getActivity(), intent);
                 *
                 * @{
                 */
                if(ContactsApplication.sApplication.isBatchOperation() || ContactSaveService.mIsGroupSaving) {
                    Toast.makeText(getActivity(), R.string.toast_batchoperation_is_running,
                            Toast.LENGTH_LONG).show();
                } else {
                    Intent intent = new Intent(Intent.ACTION_INSERT, Contacts.CONTENT_URI);
                    intent.putExtra(ContactEditorFragment.INTENT_EXTRA_NEW_LOCAL_PROFILE, true);
                    ImplicitIntentsUtil.startActivityInApp(getActivity(), intent);
                }
                /*
                 * @}
                 */
            }
        });
    }

    /**
    * SPRD: add for Bug 474264
    *
    * @{
    */

    @Override
    protected void showCount(int partitionIndex, Cursor data) {
        /**
         * sprd bug539262 show letter scroll bar in contacts list view
         * @{
         */
        configureBladeView();
        /**
         * @}
         */
        setSearchViewPermanentVisible(true);
        if (!isSearchMode() && data != null) {
            int count = data.getCount();
            if (isAdded()) {
                mCounterHeaderView.setTextColor(this.getResources().getColor(
                        R.color.contact_list_count_text_color));
            }
            if (count != 0) {
                count -= (mUserProfileExists ? 1 : 0);
                String format = getResources().getQuantityText(
                        R.plurals.listTotalAllContacts, count).toString();
                // Do not count the user profile in the contacts count
                if (mUserProfileExists) {
                    getAdapter().setContactsCount(String.format(format, count));
                }
                mCounterHeaderView.setText(String.format(format, count));
            } else {
                ContactListFilter filter = getFilter();
                int filterType = filter != null ? filter.filterType
                        : ContactListFilter.FILTER_TYPE_ALL_ACCOUNTS;
                switch (filterType) {
                case ContactListFilter.FILTER_TYPE_ACCOUNT:
                    if (PhoneAccountType.ACCOUNT_TYPE
                            .equals(filter.accountType)) {
                        mCounterHeaderView.setText(getString(
                                R.string.listTotalAllContactsZeroGroup,
                                getString(R.string.label_phone)));
                    } else {
                        mCounterHeaderView.setText(getString(
                                R.string.listTotalAllContactsZeroGroup,
                                filter.accountName));
                    }
                    /**
                     * @}
                     */

                    break;
                case ContactListFilter.FILTER_TYPE_WITH_PHONE_NUMBERS_ONLY:
                    mCounterHeaderView
                            .setText(R.string.listTotalPhoneContactsZero);
                    break;
                case ContactListFilter.FILTER_TYPE_STARRED:
                    mCounterHeaderView
                            .setText(R.string.listTotalAllContactsZeroStarred);
                    break;
                case ContactListFilter.FILTER_TYPE_CUSTOM:
                    mCounterHeaderView
                            .setText(R.string.listTotalAllContactsZeroCustom);
                    break;
                default:
                    mCounterHeaderView
                            .setText(R.string.listTotalAllContactsZero);
                    break;
                }
            }
        } else {
            ContactListAdapter adapter = getAdapter();
            if (adapter == null) {
                return;
            }

            // In search mode we only display the header if there is nothing
            // found
            if (TextUtils.isEmpty(getQueryString())
                    || !adapter.areAllPartitionsEmpty()) {
                mSearchHeaderView.setVisibility(View.GONE);
                showSearchProgress(false);
            } else {
                mSearchHeaderView.setVisibility(View.VISIBLE);
                if (adapter.isLoading()) {
                    mSearchProgressText
                            .setText(R.string.search_results_searching);
                    showSearchProgress(true);
                } else {
                    mSearchProgressText
                            .setText(R.string.listFoundAllContactsZero);
                    mSearchProgressText
                            .sendAccessibilityEvent(AccessibilityEvent.TYPE_VIEW_SELECTED);
                    showSearchProgress(false);
                }
            }
            showEmptyUserProfile(false);
        }
    }

    private void showContactsCount() {
        // Changing visibility of just the mProfileHeader doesn't do anything
        // unless
        // you change visibility of its children, hence the call to
        // mCounterHeaderView
        // and mProfileTitle
        mProfileHeaderContainer.setVisibility(View.VISIBLE);
        mProfileHeader.setVisibility(View.VISIBLE);
        mProfileInfo.setVisibility(View.GONE);
        mProfileTitle.setVisibility(View.GONE);
        mProfileMessage.setVisibility(View.GONE);
        mCounterHeaderView.setVisibility(View.VISIBLE);
    }

    private LinearLayout mProfileInfo;
    private TextView mCounterHeaderView;
    /**
     * @}
     */
}