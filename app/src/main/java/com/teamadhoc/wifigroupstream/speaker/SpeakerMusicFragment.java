package com.teamadhoc.wifigroupstream.speaker;

import java.io.IOException;
import java.net.URLDecoder;

import android.app.Activity;
import android.app.Fragment;
import android.media.AudioManager;
import android.media.MediaPlayer;
import android.os.Bundle;
import android.os.Handler;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewGroup;
import android.widget.SeekBar;
import android.widget.TextView;

import com.teamadhoc.wifigroupstream.R;
import com.teamadhoc.wifigroupstream.Timer;
import com.teamadhoc.wifigroupstream.Utilities;

public class SpeakerMusicFragment extends Fragment implements MediaPlayer.OnBufferingUpdateListener {
    private final static String TAG = "SpeakerMusicFragment";
    private String songTitle = "Default SongTitle";
    private SeekBar songProgressBar;
    private TextView songTitleLabel;
    private TextView songCurrentDurationLabel;
    private TextView songTotalDurationLabel;
    private MediaPlayer mp;
    // Handler to update UI timer, progress bar etc,.
    private Handler handler = new Handler();
    private Utilities utils;
    private Timer musicTimer = null;
    private SpeakerActivity activity = null;
    private View contentView = null;
    private int currentPlayPosition = 0;

    @Override
    public void onActivityCreated(Bundle savedInstanceState) {
        super.onActivityCreated(savedInstanceState);
    }

    @Override
    public void onAttach(Activity activity) {
        super.onAttach(activity);
        this.activity = (SpeakerActivity) activity;
    }

    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container, Bundle savedInstanceState) {
        super.onCreateView(inflater, container, savedInstanceState);

        contentView = inflater.inflate(R.layout.fragment_speaker_music, null);

        songProgressBar = (SeekBar) contentView.findViewById(R.id.songProgressBar);
        // Prevent songProgressBar from being moved manually (can only be moved by code)
        songProgressBar.setOnTouchListener(new View.OnTouchListener(){
            @Override
            public boolean onTouch(View v, MotionEvent event) {
                return true;
            }
        });

        songTitleLabel = (TextView) contentView.findViewById(R.id.songTitle);
        songCurrentDurationLabel = (TextView) contentView.findViewById(R.id.songCurrentDurationLabel);
        songTotalDurationLabel = (TextView) contentView.findViewById(R.id.songTotalDurationLabel);

        mp = new MediaPlayer();
        utils = new Utilities();

        mp.setOnBufferingUpdateListener(this);

        return contentView;
    }

    public void playSong(String url, long startTime, int startPos) {
        // This part of the code is time sensitive, it should be done as fast as
        // possible to avoid delay in the music
        try {
            mp.reset();
            mp.setDataSource(url);
            mp.setAudioStreamType(AudioManager.STREAM_MUSIC);
            mp.prepare(); // prepareAsync doesn't work since we want the media file to be played synchronously.

            // TODO: make sure we have buffered music
            // Buffer the music (currently this is a big HACK and takes a lot of time)
            mp.start();
            mp.pause();
            mp.start();
            mp.pause();
            mp.start();
            mp.pause();
            mp.start();
            mp.pause();
            mp.start();
            mp.pause();

            musicTimer = activity.retrieveTimer();

            // Let the music timer determine when to play the future playback
            musicTimer.playFutureMusic(mp, startTime, startPos);

            // Parsing songTitle
            String temp[] = url.split("/");
            songTitle = URLDecoder.decode(temp[temp.length - 1], "UTF-8");

            // Set the song title after playing the music
            songTitleLabel.setText("Now Playing: " + songTitle);

            // Set Progress bar values
            songProgressBar.setProgress(0);
            songProgressBar.setMax(100);

            // Update progress bar
            updateProgressBar();
        }
        catch (IllegalArgumentException e) {
            Log.e(TAG, "IllegalArgumentException");
        }
        catch (IllegalStateException e) {
            Log.e(TAG, "IllegalStateException");
        }
        catch (IOException e) {
            Log.e(TAG, "IOException");
        }
    }

    public void stopMusic() {
        if (mp != null && mp.isPlaying()) {
            Log.d(TAG, "stopMusic");
            mp.pause();
        }
    }

    /**
     * Update timer on seekbar
     */
    public void updateProgressBar() {
        // Initialize the bar
        long totalDuration = mp.getDuration();

        // Displaying Total Duration time
        songTotalDurationLabel.setText("" + utils.milliSecondsToTimer(totalDuration));
        // Displaying time completed playing
        songCurrentDurationLabel.setText("" + utils.milliSecondsToTimer(currentPlayPosition));

        // Updating progress bar
        int progress = (int) (utils.getProgressPercentage(currentPlayPosition, totalDuration));
        songProgressBar.setProgress(progress);

        // Running updateTimeTask after 100 milliseconds
        handler.postDelayed(updateTimeTask, 100);
    }

    /**
     * Background Runnable thread for song progress
     */
    private Runnable updateTimeTask = new Runnable() {
        public void run() {
            if (mp == null) {
                return;
            }

            // Only update the progress if music is playing
            if (mp.isPlaying()) {
                long totalDuration = mp.getDuration();
                currentPlayPosition = mp.getCurrentPosition();

                // Displaying Total Duration time
                songTotalDurationLabel.setText("" + utils.milliSecondsToTimer(totalDuration));
                // Displaying time completed playing
                songCurrentDurationLabel.setText("" + utils.milliSecondsToTimer(currentPlayPosition));

                // Updating progress bar
                int progress = (int) (utils.getProgressPercentage(currentPlayPosition, totalDuration));
                songProgressBar.setProgress(progress);
            }

            // Running this thread after 100 milliseconds
            handler.postDelayed(this, 100);
        }
    };

    @Override
    public void onBufferingUpdate(MediaPlayer mp, int percent) {
        songProgressBar.setSecondaryProgress(percent);
    }

    @Override
    public void onDestroy() {
        super.onDestroy();
        mp.release();
        mp = null;
    }
}
