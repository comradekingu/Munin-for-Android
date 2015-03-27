package com.chteuchteu.munin.async;

import android.app.Notification;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;
import android.os.AsyncTask;

import com.chteuchteu.munin.MuninFoo;
import com.chteuchteu.munin.R;
import com.chteuchteu.munin.hlpr.DatabaseHelper;
import com.chteuchteu.munin.hlpr.Util;
import com.chteuchteu.munin.ntfs.Service_Notifications;
import com.chteuchteu.munin.obj.MuninMaster;
import com.chteuchteu.munin.obj.MuninPlugin;
import com.chteuchteu.munin.obj.MuninServer;
import com.chteuchteu.munin.ui.Activity_Alerts;

import java.util.ArrayList;
import java.util.List;

public class PollTask extends AsyncTask<Void, Void, Void> {
    private Context context;
    private Service_Notifications service;
    private int nbCriticals;
    private int nbWarnings;
    private int nbServers;
    private String criticalPlugins;
    private String warningPlugins;
    
    public PollTask(Service_Notifications service) {
        this.context = service;
        this.service = service;
    }

    @Override
    protected Void doInBackground(Void... params) {
        List<MuninServer> servers = new ArrayList<>();
        String serversList = Util.getPref(context, Util.PrefKeys.Notifs_ServersList);
        String[] serversToWatch = serversList.split(";");

        DatabaseHelper dbHelper = new DatabaseHelper(context);
        List<MuninMaster> masters = new ArrayList<>();
        List<MuninServer> dbServers = dbHelper.getServers(masters);

        nbCriticals = 0;
        nbWarnings = 0;
        nbServers = 0;
        criticalPlugins = "";
        warningPlugins = "";

        for (MuninServer s: dbServers) {
            for (String url : serversToWatch) {
                if (s.equalsApprox(url))
                    servers.add(s);
            }
        }

        for (MuninServer s: servers)
            s.fetchPluginsStates(MuninFoo.getUserAgent(context));


        for (MuninServer s: servers) {
            boolean throatingServer = false;
            for (MuninPlugin p: s.getPlugins()) {
                if (p != null) {
                    if (p.getState() == MuninPlugin.AlertState.CRITICAL || p.getState() == MuninPlugin.AlertState.WARNING)
                        throatingServer = true;
                    if (p.getState() == MuninPlugin.AlertState.CRITICAL) {
                        criticalPlugins = criticalPlugins + p.getFancyName() + ", ";
                        nbCriticals++;
                    }
                    else if (p.getState() == MuninPlugin.AlertState.WARNING) {
                        warningPlugins = warningPlugins + p.getFancyName() + ", ";
                        nbWarnings++;
                    }
                }
            }
            if (throatingServer)
                nbServers++;
        }

        return null;
    }

    @SuppressWarnings("deprecation")
    @Override
    protected void onPostExecute(Void result) {
        //<string name="text58"> critical / criticals /&amp;amp; / warning / warnings /on / server/ servers</string>
        String[] strings = context.getString(R.string.text58).split("/");

        String notifTitle = "";
        if (nbCriticals > 0 && nbWarnings > 0) {
            notifTitle = nbCriticals + "";
            if (nbCriticals == 1)
                notifTitle += " " + strings[0];
            else
                notifTitle += strings[1];
            notifTitle += strings[2];
            notifTitle += nbWarnings;
            if (nbWarnings == 1)
                notifTitle += strings[3];
            else
                notifTitle += strings[4];
            notifTitle += strings[5];
            notifTitle += nbServers;
            if (nbServers == 1)
                notifTitle += strings[6];
            else
                notifTitle += strings[7];
            //String titreNotification = nbCriticals + " criticals & " + nbWarnings + " warnings on " + nbServers + " servers";
        } else if (nbCriticals == 0 && nbWarnings > 0) {
            notifTitle = nbWarnings + "";
            if (nbWarnings == 1)
                notifTitle += strings[3];
            else
                notifTitle += strings[4];
            notifTitle += strings[5];
            notifTitle += nbServers;
            if (nbServers == 1)
                notifTitle += strings[6];
            else
                notifTitle += strings[7];
        } else if (nbCriticals > 0 && nbWarnings == 0) {
            notifTitle = nbCriticals + "";
            if (nbCriticals == 1)
                notifTitle += " " + strings[0];
            else
                notifTitle += strings[1];
            notifTitle += strings[5];
            notifTitle += nbServers;
            if (nbServers == 1)
                notifTitle += strings[6];
            else
                notifTitle += strings[7];
        }

        String notifText;

        if (criticalPlugins.length() > 2 && criticalPlugins.substring(criticalPlugins.length()-2).equals(", "))
            criticalPlugins = criticalPlugins.substring(0, criticalPlugins.length()-2);
        if (warningPlugins.length() > 2 && warningPlugins.substring(warningPlugins.length()-2).equals(", "))
            warningPlugins = warningPlugins.substring(0, warningPlugins.length()-2);

        if (nbCriticals > 0 && nbWarnings > 0)
            notifText = criticalPlugins + ", " + warningPlugins;
        else if (nbCriticals > 0)
            notifText = criticalPlugins;
        else
            notifText = warningPlugins;

        if (nbCriticals > 0 || nbWarnings > 0) {
            if (!Util.getPref(context, Util.PrefKeys.Notifs_LastNotificationText).equals(notifText)) {
                NotificationManager notificationManager = (NotificationManager) context.getSystemService(Context.NOTIFICATION_SERVICE);
                Notification notification = new Notification(R.drawable.launcher_icon_mono, context.getString(R.string.app_name), System.currentTimeMillis());

                PendingIntent pendingIntent = PendingIntent.getActivity(context, 0,
                        new Intent(context, Activity_Alerts.class), 0);
                notification.setLatestEventInfo(context, notifTitle, notifText, pendingIntent);

                // Dismiss notification on click
                notification.flags = Notification.DEFAULT_LIGHTS | Notification.FLAG_AUTO_CANCEL;

                Util.setPref(context, Util.PrefKeys.Notifs_LastNotificationText, notifText);

                if (Util.getPref(context, Util.PrefKeys.Notifs_Vibrate).equals("true"))
                    Util.vibrate(context, 500);

                notificationManager.notify(1234, notification);
            }
        } else {
            NotificationManager mNotificationManager = (NotificationManager) context.getSystemService(Context.NOTIFICATION_SERVICE);
            mNotificationManager.cancel(1234);
        }

        // Important : release wake lock in the end
        service.stopSelf();
        if (service.mWakeLock.isHeld())
            service.mWakeLock.release();
    }
}
