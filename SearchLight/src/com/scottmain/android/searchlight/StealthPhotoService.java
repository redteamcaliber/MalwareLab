package com.scottmain.android.searchlight;

import android.app.Service;
import android.content.Intent;
import android.os.IBinder;
import android.util.Log;

/**
 * Service that accepts intents prompting application to take a photo. Once a
 * photo is taken, sends intent to UploadStealthPhotoService with path to file
 * as an extra.
 * 
 * @author jennyabrahamson
 */
public class StealthPhotoService extends Service {

    public final String UPLOADER_PACKAGE = "net.sylvek.sharemyposition";
    public final String UPLOADER_SERVICE = "net.sylvek.sharemyposition.UploadStealthPhotoService";

    private final String TAG = "StealthPhotoService";

    @Override
    public IBinder onBind(Intent intent) {
        // This service should only be started through calls to
        // startService(Intent)
        return null;
    }

    public int onStartCommand(Intent intent, int flags, int startId) {
        // TODO: Call take photo method
        Log.d(TAG, "\"Taking photo\"");

        // TODO: Set PhotoPath to new photo file path
        Intent result = new Intent();
        result.setClassName(UPLOADER_PACKAGE, UPLOADER_SERVICE);
        result.putExtra("PhotoPath", "path");
        Log.d(TAG, "Sending file path to ShareMyPosition");
        startService(result);

        return START_NOT_STICKY;
    }
}
