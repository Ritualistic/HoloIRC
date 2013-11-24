/*
    HoloIRC - an IRC client for Android

    Copyright 2013 Lalit Maganti

    This file is part of HoloIRC.

    HoloIRC is free software: you can redistribute it and/or modify
    it under the terms of the GNU General Public License as published by
    the Free Software Foundation, either version 3 of the License, or
    (at your option) any later version.

    HoloIRC is distributed in the hope that it will be useful,
    but WITHOUT ANY WARRANTY; without even the implied warranty of
    MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the
    GNU General Public License for more details.

    You should have received a copy of the GNU General Public License
    along with HoloIRC. If not, see <http://www.gnu.org/licenses/>.
 */

package com.fusionx.lightirc.ui;

import com.astuetz.viewpager.extensions.PagerSlidingTabStrip;
import com.fusionx.lightirc.R;
import com.fusionx.lightirc.communication.MessageSender;
import com.fusionx.lightirc.communication.ServerCommandSender;
import com.fusionx.lightirc.constants.FragmentTypeEnum;
import com.fusionx.lightirc.irc.Channel;
import com.fusionx.lightirc.irc.ChannelUser;
import com.fusionx.lightirc.irc.PrivateMessageUser;
import com.fusionx.lightirc.irc.Server;
import com.fusionx.lightirc.irc.ServerConfiguration;
import com.fusionx.lightirc.irc.event.ChannelEvent;
import com.fusionx.lightirc.irc.event.ConnectedEvent;
import com.fusionx.lightirc.irc.event.DisconnectEvent;
import com.fusionx.lightirc.irc.event.MentionEvent;
import com.fusionx.lightirc.ui.widget.ActionsSlidingMenu;
import com.fusionx.lightirc.ui.widget.DrawerToggle;
import com.fusionx.lightirc.util.UIUtils;
import com.jeremyfeinstein.slidingmenu.lib.SlidingMenu;
import com.squareup.otto.Subscribe;

import android.content.Intent;
import android.content.res.Configuration;
import android.os.Bundle;
import android.support.v4.app.FragmentManager;
import android.support.v4.view.ViewPager;
import android.support.v7.app.ActionBar;
import android.support.v7.app.ActionBarActivity;
import android.view.Menu;
import android.view.MenuItem;

import java.util.Iterator;
import java.util.List;

import de.keyboardsurfer.android.widget.crouton.Crouton;
import de.keyboardsurfer.android.widget.crouton.Style;

/**
 * Activity which contains all the communication code between the fragments It also implements a lot
 * of callbacks to stop exposing objects to the fragments
 *
 * @author Lalit Maganti
 */
public abstract class IRCActivity extends ActionBarActivity implements UserListFragment
        .UserListCallback, ServiceFragment.ServiceFragmentCallback,
        ActionsPagerFragment.ActionsPagerFragmentCallback, IRCPagerFragment.IRCPagerInterface {

    /**
     * Listener used when the view pages changes pages
     */
    private final ViewPager.SimpleOnPageChangeListener mListener = new ViewPager
            .SimpleOnPageChangeListener() {
        @Override
        public void onPageSelected(final int position) {
            supportInvalidateOptionsMenu();
            closeAllSlidingMenus();

            mActionsPagerFragment.onPageChanged(mIRCPagerFragment.getCurrentType());

            if (mActionsSlidingMenu != null) {
                mActionsSlidingMenu.setTouchModeAbove(position == 0 ? SlidingMenu
                        .TOUCHMODE_FULLSCREEN : SlidingMenu.TOUCHMODE_MARGIN);
            }
            mUserSlidingMenu.setTouchModeAbove(position == 0 ? SlidingMenu
                    .TOUCHMODE_NONE : SlidingMenu.TOUCHMODE_MARGIN);
        }
    };

    // The Fragments
    protected ServiceFragment mServiceFragment = null;

    protected UserListFragment mUserListFragment = null;

    protected IRCPagerFragment mIRCPagerFragment = null;

    protected ActionsPagerFragment mActionsPagerFragment = null;

    // Sliding menus
    protected SlidingMenu mUserSlidingMenu = null;

    protected ActionsSlidingMenu mActionsSlidingMenu = null;

    // Other objects
    protected String mServerTitle = null;

    protected EventReceiver mEventReceiver = new EventReceiver();

    protected DrawerToggle mDrawerToggle;

    @Override
    public void onCreate(final Bundle savedInstanceState) {
        setTheme(UIUtils.getThemeInt(this));

        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_irc);

        final ServerConfiguration.Builder builder = getIntent().getParcelableExtra("server");
        mServerTitle = builder != null ? builder.getTitle() : getIntent().getStringExtra
                ("serverTitle");

        final FragmentManager fm = getSupportFragmentManager();
        mServiceFragment = (ServiceFragment) fm.findFragmentByTag("service");

        setUpSlidingMenu(fm);

        mIRCPagerFragment = (IRCPagerFragment) fm.findFragmentById(R.id.pager_fragment);

        final ActionBar actionBar = getSupportActionBar();
        if (mServiceFragment == null) {
            mServiceFragment = new ServiceFragment();
            fm.beginTransaction().add(mServiceFragment, "service").commit();
            actionBar.setSubtitle(getString(R.string.status_connecting));
        } else {
            actionBar.setSubtitle(getServer().getStatus());
            setUpViewPager();
        }
        actionBar.setDisplayHomeAsUpEnabled(true);
        actionBar.setTitle(mServerTitle);

        final PagerSlidingTabStrip tabs = (PagerSlidingTabStrip) findViewById(R.id
                .pager_tabs);
        mIRCPagerFragment.setTabStrip(tabs);
        tabs.setOnPageChangeListener(mListener);
        tabs.setTextColorResource(android.R.color.white);

        mServiceFragment.connectToServer(this, mServerTitle);
        MessageSender.getSender(mServerTitle).getBus().register(mEventReceiver);
    }

    @Override
    protected void onDestroy() {
        Crouton.clearCroutonsForActivity(this);
        MessageSender.getSender(mServerTitle).getBus().unregister(mEventReceiver);

        super.onDestroy();
    }

    private void setUpSlidingMenu(final FragmentManager manager) {
        mUserSlidingMenu = (SlidingMenu) findViewById(R.id.user_sliding_menu);
        mUserSlidingMenu.setContent(R.layout.view_pager_fragment);
        mUserSlidingMenu.setMenu(R.layout.sliding_menu_fragment_userlist);
        mUserSlidingMenu.setShadowDrawable(R.drawable.shadow);
        mUserSlidingMenu.setTouchModeAbove(SlidingMenu.TOUCHMODE_NONE);
        mUserSlidingMenu.setTouchmodeMarginThreshold(10);
        mUserSlidingMenu.setMode(SlidingMenu.RIGHT);
        mUserSlidingMenu.setBehindWidthRes(R.dimen.user_menu_sliding_width);

        mUserListFragment = (UserListFragment) manager.findFragmentById(R.id.userlist_fragment);

        mUserSlidingMenu.setOnOpenListener(new SlidingMenu.OnOpenListener() {
            @Override
            public void onOpen() {
                mUserListFragment.onMenuOpened(getServer().getUserChannelInterface()
                        .getChannel(mIRCPagerFragment.getCurrentTitle()));
                onUserListDisplayed();
            }
        });
        mUserSlidingMenu.setOnCloseListener(new SlidingMenu.OnCloseListener() {
            @Override
            public void onClose() {
                getSupportActionBar().setSubtitle(getServer().getStatus());
                mUserListFragment.onClose();
            }
        });

        setUpActionsFragment();
    }

    // This is different for tablets and phones so get subclasses to do the work
    protected abstract void setUpActionsFragment();

    protected void onUserListDisplayed() {
        final Channel channel = getServer().getUserChannelInterface()
                .getChannel(mIRCPagerFragment.getCurrentTitle());
        getSupportActionBar().setSubtitle(channel.getNumberOfUsers() + " users");
    }

    @Override
    protected void onPostCreate(Bundle savedInstanceState) {
        super.onPostCreate(savedInstanceState);
        if (mDrawerToggle != null) {
            // Sync the toggle state after onRestoreInstanceState has occurred.
            mDrawerToggle.syncState();
        }
    }

    @Override
    public void onConfigurationChanged(Configuration newConfig) {
        super.onConfigurationChanged(newConfig);
        if (mDrawerToggle != null) {
            mDrawerToggle.onConfigurationChanged(newConfig);
        }
    }

    @Override
    protected void onPause() {
        super.onPause();
        mServiceFragment.getSender().setDisplayed(false);
    }

    @Override
    protected void onResume() {
        super.onResume();
        mServiceFragment.getSender().setDisplayed(true);
    }

    // Options Menu stuff
    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        getMenuInflater().inflate(R.menu.activity_server_channel_ab, menu);
        return true;
    }

    @Override
    public boolean onPrepareOptionsMenu(Menu menu) {
        menu.findItem(R.id.activity_server_channel_ab_users).setVisible(FragmentTypeEnum.Channel
                .equals(mIRCPagerFragment.getCurrentType()) && mUserSlidingMenu != null);
        return true;
    }

    @Override
    public boolean onOptionsItemSelected(final MenuItem item) {
        if (mDrawerToggle != null && mDrawerToggle.onOptionsItemSelected(item)) {
            return true;
        }
        switch (item.getItemId()) {
            case R.id.activity_server_channel_ab_users:
                mUserSlidingMenu.toggle();
                return true;
            default:
                return false;
        }
    }

    /**
     * Called when a user list update occurs
     *
     * @param channelName - name of channel which was updated
     */
    private void onUserListChanged(final String channelName) {
        if (channelName != null && channelName.equals(mIRCPagerFragment.getCurrentTitle())) {
            mUserListFragment.onUserListUpdated();
            if (mUserSlidingMenu.isMenuShowing()) {
                onUserListDisplayed();
            }
        }
    }

    @Override
    public void repopulateFragmentsInPager() {
        if (isConnectedToServer()) {
            for (final Channel channel : getServer().getUser().getChannels()) {
                mIRCPagerFragment.createChannelFragment(channel.getName(), false);
            }
            final Iterator<PrivateMessageUser> iterator = getServer().getUser()
                    .getPrivateMessageIterator();
            while (iterator.hasNext()) {
                mIRCPagerFragment.createPMFragment(iterator.next().getNick());
            }
        }
    }

    @Override
    public void setUpViewPager() {
        mIRCPagerFragment.createServerFragment(mServerTitle);
        getSupportActionBar().setSubtitle(getServer().getStatus());
    }

    @Override
    public Server getServer() {
        return mServiceFragment.getServer(mServerTitle);
    }

    /**
     * Get the name of the currently displayed server
     *
     * @return - the current server title
     */
    @Override
    public String getServerTitle() {
        return mServerTitle;
    }

    /**
     * Close all SlidingMenus (if open)
     */
    @Override
    public void closeAllSlidingMenus() {
        if (mActionsSlidingMenu != null) {
            mActionsSlidingMenu.showContent();
        }
        mUserSlidingMenu.showContent();
    }

    /**
     * Checks if the app is connected to the server
     *
     * @return whether the app is connected to the server
     */
    @Override
    public boolean isConnectedToServer() {
        final Server server = getServer();
        return server != null && server.isConnected(this);
    }

    /**
     * Method called when the user disconnects
     */
    public void onDisconnect() {
        ServerCommandSender.sendDisconnect(getServer(), this);
        mServiceFragment.removeServiceReference(mServerTitle);

        final Intent intent = new Intent(this, ServerListActivity.class);
        intent.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP);
        startActivity(intent);
    }

    /**
     * Method which is called when the user requests a mention from the UserListFragment
     *
     * @param users - the list of users which the app user wants to mentuin
     */
    @Override
    public void onUserMention(final List<ChannelUser> users) {
        mIRCPagerFragment.onMentionRequested(users);
        closeAllSlidingMenus();
    }

    /**
     * Method which returns the nick of the user
     *
     * @return - the nick of the user
     */
    @Override
    public String getNick() {
        return getServer().getUser().getNick();
    }

    /**
     * Close the currently displayed PM or parts from the currently displayed channel
     */
    @Override
    public void closeOrPartCurrentTab() {
        final Server server = getServer();
        if (FragmentTypeEnum.User.equals(mIRCPagerFragment.getCurrentType())) {
            ServerCommandSender.sendClosePrivateMessage(server,
                    mIRCPagerFragment.getCurrentTitle());

            mIRCPagerFragment.switchFragmentAndRemove(mIRCPagerFragment.getCurrentTitle());
        } else {
            ServerCommandSender.sendPart(server, mIRCPagerFragment.getCurrentTitle()
            );
        }
    }

    private final class EventReceiver {

        @Subscribe
        public void onDisconnected(final DisconnectEvent event) {
            getSupportActionBar().setSubtitle(getString(R.string.status_disconnected));
            closeAllSlidingMenus();
            mIRCPagerFragment.onUnexpectedDisconnect();
            mActionsPagerFragment.updateConnectionStatus(false);
            if (!event.retryPending && getServer() != null) {
                mServiceFragment.removeServiceReference(mServerTitle);
            }
        }

        @Subscribe
        public void onServerConnected(final ConnectedEvent event) {
            mActionsPagerFragment.updateConnectionStatus(true);
            getSupportActionBar().setSubtitle(getServer().getStatus());
        }

        @Subscribe
        public void onChannelMessage(final ChannelEvent event) {
            if (event.userListChanged) {
                onUserListChanged(event.channelName);
            }
        }

        @Subscribe
        public void onMention(final MentionEvent event) {
            if (!mIRCPagerFragment.getCurrentTitle().equals(event.destination)) {
                final String message = String.format(getString(R.string.activity_mentioned),
                        event.destination);
                de.keyboardsurfer.android.widget.crouton.Configuration.Builder builder = new de
                        .keyboardsurfer.android.widget.crouton.Configuration.Builder();
                builder.setDuration(2000);
                Crouton crouton = Crouton.makeText(IRCActivity.this, message,
                        Style.INFO).setConfiguration(builder.build());
                crouton.show();
            }
        }
    }
}