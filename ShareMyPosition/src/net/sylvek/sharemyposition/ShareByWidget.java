/*
 * Copyright (C) 2012  Sylvain Maucourt (smaucourt@gmail.com)
 *
 * This library is free software; you can redistribute it and/or
 * modify it under the terms of the GNU Lesser General Public
 * License as published by the Free Software Foundation; either
 * version 2 of the License, or (at your option) any later version.
 *
 * This library is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the GNU
 * Lesser General Public License for more details.
 *
 * You should have received a copy of the GNU Lesser General Public
 * License along with this library; if not, write to the Free Software
 * Foundation, Inc., 59 Temple Place, Suite 330, Boston, MA  02111-1307
 *
 */
package net.sylvek.sharemyposition;

import android.app.PendingIntent;
import android.appwidget.AppWidgetManager;
import android.appwidget.AppWidgetProvider;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.SharedPreferences.Editor;
import android.net.Uri;
import android.preference.PreferenceManager;
import android.util.Log;
import android.widget.RemoteViews;

/**
 * @author sylvek
 * 
 */
public class ShareByWidget extends AppWidgetProvider {

    private static final String PACKAGE = "net.sylvek.sharemyposition";

    private static final RemoteViews views = new RemoteViews(PACKAGE, R.layout.widget);

    public static final String PREF_PREFIX = PACKAGE + ".";

    public static final String PREF_NAME = ".name";

    public static final String PREF_ITEM = ".item";

    public static final String PREF_BODY = ".body";

    public static final String PREF_LATLON = ".latlon";

    public static final String PREF_ADDRESS = ".address";

    public static final String PREF_URL = ".url";

    @Override
    public void onUpdate(Context context, AppWidgetManager appWidgetManager, int[] appWidgetIds)
    {
        SharedPreferences pref = PreferenceManager.getDefaultSharedPreferences(context);
        for (int i = 0; i < appWidgetIds.length; i++) {
            update(context, pref, appWidgetManager, appWidgetIds[i]);
        }
    }

    @Override
    public void onDeleted(Context context, int[] appWidgetIds)
    {
        SharedPreferences pref = PreferenceManager.getDefaultSharedPreferences(context);
        Editor editor = pref.edit();
        for (int i = 0; i < appWidgetIds.length; i++) {
            String prefix = PREF_PREFIX + appWidgetIds[i];
            Log.d(ShareMyPosition.LOG, "deleting -> [" + prefix + "]");
            editor.remove(prefix + PREF_NAME)
                    .remove(prefix + PREF_ITEM)
                    .remove(prefix + PREF_BODY)
                    .remove(prefix + PREF_LATLON)
                    .remove(prefix + PREF_ADDRESS)
                    .remove(prefix + PREF_URL);
        }
        editor.commit();
    }

    public static final void update(Context context, SharedPreferences pref, AppWidgetManager appWidgetManager, int appWidgetId)
    {
        String prefix = context.getString(R.string.with) + " ";
        String item = pref.getString(PREF_PREFIX + appWidgetId + PREF_ITEM, "");
        String name = pref.getString(PREF_PREFIX + appWidgetId + PREF_NAME, item);
        String body = pref.getString(PREF_PREFIX + appWidgetId + PREF_BODY, "");
        boolean address = pref.getBoolean(PREF_PREFIX + appWidgetId + PREF_ADDRESS, false);
        boolean latlon = pref.getBoolean(PREF_PREFIX + appWidgetId + PREF_LATLON, false);
        boolean url = pref.getBoolean(PREF_PREFIX + appWidgetId + PREF_URL, false);

        Log.d(ShareMyPosition.LOG, "adding -> [" + appWidgetId + "," + item + "," + prefix + name + "]");

        item = (item.contains("@")) ? "mailto:" + item : "smsto:" + item;

        final Intent launch = new Intent(context, ShareMyPosition.class);
        launch.putExtra(ShareMyPosition.EXTRA_INTENT, new Intent(Intent.ACTION_SENDTO, Uri.parse(item)));
        launch.putExtra(ShareMyPosition.PREF_ADDRESS_CHECKED, address);
        launch.putExtra(ShareMyPosition.PREF_LAT_LON_CHECKED, latlon);
        launch.putExtra(ShareMyPosition.PREF_URL_CHECKED, url);
        launch.putExtra(ShareMyPosition.PREF_BODY_DEFAULT, body);

        views.setTextViewText(R.id.name, prefix + name);
        views.setOnClickPendingIntent(R.id.picture,
                PendingIntent.getActivity(context, appWidgetId, launch, PendingIntent.FLAG_UPDATE_CURRENT));

        appWidgetManager.updateAppWidget(appWidgetId, views);
    }
}
