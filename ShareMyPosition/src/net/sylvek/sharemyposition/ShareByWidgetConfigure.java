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

import java.util.ArrayList;

import android.app.Activity;
import android.app.AlertDialog;
import android.appwidget.AppWidgetManager;
import android.content.DialogInterface;
import android.content.DialogInterface.OnClickListener;
import android.content.Intent;
import android.content.SharedPreferences;
import android.database.Cursor;
import android.net.Uri;
import android.os.Bundle;
import android.preference.PreferenceManager;
import android.provider.ContactsContract;
import android.view.View;
import android.widget.Button;
import android.widget.CheckBox;
import android.widget.TextView;
import android.widget.Toast;

/**
 * @author sylvek
 * 
 */
public class ShareByWidgetConfigure extends Activity {

    int mAppWidgetId = AppWidgetManager.INVALID_APPWIDGET_ID;

    private static final int PICK_CONTACT = 0;

    private AlertDialog dialogBox;

    private String displayName, item;

    @Override
    public void onCreate(Bundle icicle)
    {
        super.onCreate(icicle);

        setResult(RESULT_CANCELED);

        setContentView(R.layout.appwidget_configure);

        Intent intent = getIntent();
        Bundle extras = intent.getExtras();
        if (extras != null) {
            mAppWidgetId = extras.getInt(AppWidgetManager.EXTRA_APPWIDGET_ID, AppWidgetManager.INVALID_APPWIDGET_ID);
        }

        if (mAppWidgetId == AppWidgetManager.INVALID_APPWIDGET_ID) {
            finish();
        }

        if (icicle != null) {
            displayName = icicle.getString("name");
            item = icicle.getString("item");
        }
    }

    @Override
    protected void onSaveInstanceState(Bundle outState)
    {
        outState.putString("name", displayName);
        outState.putString("item", item);
    }

    @Override
    protected void onPause()
    {
        super.onPause();

        if (dialogBox != null) {
            dialogBox.dismiss();
        }
    }

    private ArrayList<String> getEmail(String id)
    {
        ArrayList<String> mail = new ArrayList<String>();
        Cursor c = managedQuery(ContactsContract.CommonDataKinds.Email.CONTENT_URI, null,
                ContactsContract.CommonDataKinds.Email.CONTACT_ID + " = ?", new String[] { id }, null);
        while (c.moveToNext()) {
            mail.add(c.getString(c.getColumnIndex(ContactsContract.CommonDataKinds.Email.DATA)));
        }

        return mail;
    }

    private ArrayList<String> getPhoneNumber(String id)
    {
        ArrayList<String> phone = new ArrayList<String>();
        Cursor c = managedQuery(ContactsContract.CommonDataKinds.Phone.CONTENT_URI, null,
                ContactsContract.CommonDataKinds.Phone.CONTACT_ID + " = ?", new String[] { id }, null);
        while (c.moveToNext()) {
            phone.add(c.getString(c.getColumnIndex(ContactsContract.CommonDataKinds.Phone.NUMBER)));
        }

        return phone;
    }

    @Override
    public void onActivityResult(int reqCode, int resultCode, Intent data)
    {
        super.onActivityResult(reqCode, resultCode, data);

        switch (reqCode) {
        case (PICK_CONTACT):
            if (resultCode == Activity.RESULT_OK) {
                Uri contactData = data.getData();
                Cursor contactsCursor = managedQuery(contactData, null, null, null, null);

                if (contactsCursor.moveToFirst()) {
                    displayName = contactsCursor.getString(contactsCursor.getColumnIndexOrThrow(ContactsContract.Contacts.DISPLAY_NAME));
                    String id = contactsCursor.getString(contactsCursor.getColumnIndexOrThrow(ContactsContract.Contacts._ID));
                    String hasPhoneNumber = contactsCursor.getString(contactsCursor.getColumnIndexOrThrow(ContactsContract.Contacts.HAS_PHONE_NUMBER));

                    final ArrayList<String> all = new ArrayList<String>(getEmail(id));
                    if (Integer.parseInt(hasPhoneNumber) > 0) {
                        all.addAll(getPhoneNumber(id));
                    }

                    dialogBox = new AlertDialog.Builder(this).setTitle(displayName)
                            .setItems(all.toArray(new String[0]), new OnClickListener() {

                                @Override
                                public void onClick(DialogInterface dialog, int which)
                                {
                                    item = all.get(which);
                                    Toast.makeText(ShareByWidgetConfigure.this, item, Toast.LENGTH_SHORT).show();

                                    Button save = (Button) findViewById(R.id.save);
                                    save.setEnabled(true);
                                }
                            })
                            .show();
                }
            }
            break;
        }
    }

    public void selectHandler(View view)
    {
        Intent intent = new Intent(Intent.ACTION_PICK, ContactsContract.Contacts.CONTENT_URI);
        startActivityForResult(intent, PICK_CONTACT);
    }

    public void saveHandler(View view)
    {
        String prefix = ShareByWidget.PREF_PREFIX + mAppWidgetId;
        TextView body = (TextView) findViewById(R.id.body);
        CheckBox latlon = (CheckBox) findViewById(R.id.add_lat_lon_location);
        CheckBox address = (CheckBox) findViewById(R.id.add_address_location);
        CheckBox url = (CheckBox) findViewById(R.id.add_url_location);

        // store date to preferences
        SharedPreferences pref = PreferenceManager.getDefaultSharedPreferences(this);
        pref.edit()
                .putString(prefix + ShareByWidget.PREF_NAME, displayName)
                .putString(prefix + ShareByWidget.PREF_ITEM, item)
                .putString(prefix + ShareByWidget.PREF_BODY, body.getText().toString())
                .putBoolean(prefix + ShareByWidget.PREF_LATLON, latlon.isChecked())
                .putBoolean(prefix + ShareByWidget.PREF_ADDRESS, address.isChecked())
                .putBoolean(prefix + ShareByWidget.PREF_URL, url.isChecked())
                .commit();

        ShareByWidget.update(this, pref, AppWidgetManager.getInstance(this), mAppWidgetId);

        Intent resultValue = new Intent();
        resultValue.putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, mAppWidgetId);
        setResult(RESULT_OK, resultValue);
        finish();
    }
}
