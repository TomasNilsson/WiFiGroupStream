package com.teamadhoc.wifigroupstream;

import android.app.Activity;
import android.database.Cursor;
import android.provider.MediaStore;
import android.util.Log;

import java.util.ArrayList;
import java.util.HashMap;

public class SongsManager {
    private ArrayList<HashMap<String, String>> songsList = new ArrayList<HashMap<String, String>>();
    Activity activity;

    public SongsManager(Activity activity){
        this.activity = activity;
    }

    /**
     * Function to read all music files from sdcard
     * and store the details in ArrayList
     * */
    public ArrayList<HashMap<String, String>> getPlayList(){
        String[] projection = new String[]{
                MediaStore.Audio.Media.DATA,
        };
        final Cursor cursor = activity.getContentResolver()
                .query(MediaStore.Audio.Media.EXTERNAL_CONTENT_URI, projection,
                        MediaStore.Audio.Media.IS_MUSIC + "!= 0",
                        null, MediaStore.Audio.Media.TITLE + " ASC");

        int count = 0;

        if(cursor != null) {
            count = cursor.getCount();
            if(count > 0) {
                while (cursor.moveToNext()) {
                    HashMap<String, String> song = new HashMap<String, String>();
                    String path = cursor.getString(0);
                    song.put("songTitle", path.substring(path.lastIndexOf("/")+1));
                    song.put("songPath", path);
                    Log.d("SongsManager", "Song: " + cursor.getString(0));
                    songsList.add(song);
                }
            }
            cursor.close();
        }
        return songsList;
    }
}

