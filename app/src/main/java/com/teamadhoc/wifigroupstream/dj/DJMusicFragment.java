package com.teamadhoc.wifigroupstream.dj;

import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Random;

import android.app.Activity;
import android.app.AlertDialog;
import android.app.Fragment;
import android.app.ProgressDialog;
import android.content.DialogInterface;
import android.media.MediaPlayer;
import android.media.MediaPlayer.OnCompletionListener;
import android.os.Bundle;
import android.os.Handler;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageButton;
import android.widget.SeekBar;
import android.widget.TextView;
import android.widget.Toast;

import com.teamadhoc.wifigroupstream.R;
import com.teamadhoc.wifigroupstream.SongsManager;
import com.teamadhoc.wifigroupstream.Timer;
import com.teamadhoc.wifigroupstream.Utilities;

public class DJMusicFragment extends Fragment implements OnCompletionListener,
        SeekBar.OnSeekBarChangeListener {
    private final static String TAG = "DJMusicFragment";
    private ImageButton btnPlay;
    private ImageButton btnNext;
    private ImageButton btnPrevious;
    private ImageButton btnPlaylist;
    private ImageButton btnRepeat;
    private ImageButton btnShuffle;
    private SeekBar songProgressBar;
    private TextView songTitleLabel;
    private TextView songCurrentDurationLabel;
    private TextView songTotalDurationLabel;
    private ProgressDialog syncProgress;

    // Media Player
    private MediaPlayer mp;
    // Handler to update UI timer, progress bar etc,.
    private Handler handler = new Handler();
    private SongsManager songManager;
    private Utilities utils;
    private int currentSongIndex = 0;
    // For resuming music
    private int currentPlayPosition = PLAY_FROM_BEGINNING;
    private boolean isShuffle = false;
    private boolean isRepeat = false;
    private ArrayList<HashMap<String, String>> songsList = new ArrayList<HashMap<String, String>>();

    private DJActivity activity = null;
    private View contentView = null;
    private Timer musicTimer = null;
    private final static long DELAY = 4500;
    private final static int PLAY_FROM_BEGINNING = 0;

    private String[] musicList;

    @Override
    public void onActivityCreated(Bundle savedInstanceState) {
        super.onActivityCreated(savedInstanceState);
    }

    @Override
    public void onAttach(Activity activity) {
        super.onAttach(activity);
        this.activity = (DJActivity) activity;
    }

    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container,
                             Bundle savedInstanceState) {
        super.onCreateView(inflater, container, savedInstanceState);
        contentView = inflater.inflate(R.layout.fragment_dj_music, null);

        // All player buttons
        btnPlay = (ImageButton) contentView.findViewById(R.id.btnPlay);
        btnNext = (ImageButton) contentView.findViewById(R.id.btnNext);
        btnPrevious = (ImageButton) contentView.findViewById(R.id.btnPrevious);
        btnPlaylist = (ImageButton) contentView.findViewById(R.id.btnPlaylist);
        btnRepeat = (ImageButton) contentView.findViewById(R.id.btnRepeat);
        btnShuffle = (ImageButton) contentView.findViewById(R.id.btnShuffle);
        songProgressBar = (SeekBar) contentView.findViewById(R.id.songProgressBar);
        songTitleLabel = (TextView) contentView.findViewById(R.id.songTitle);
        songCurrentDurationLabel = (TextView) contentView.findViewById(R.id.songCurrentDurationLabel);
        songTotalDurationLabel = (TextView) contentView.findViewById(R.id.songTotalDurationLabel);

        // Prepare for a progress bar dialog
        syncProgress = new ProgressDialog(activity, AlertDialog.THEME_HOLO_DARK);
        syncProgress.setCancelable(false);
        syncProgress.setInverseBackgroundForced(true);
        syncProgress.setMessage("Get Ready to Enjoy Your Music!");
        syncProgress.setProgressStyle(ProgressDialog.STYLE_SPINNER);

        mp = new MediaPlayer();
        songManager = new SongsManager(getActivity());
        utils = new Utilities();

        // Listeners
        songProgressBar.setOnSeekBarChangeListener(this);
        mp.setOnCompletionListener(this);

        // Getting all songs list
        songsList = songManager.getPlayList();

        /**
         * Play button click event plays a song and changes button to pause.
         * Pause image click pauses a song and changes button to play image.
         */
        btnPlay.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View arg0) {
                // Check if already playing, if it is, this acts as a pause button
                if (mp != null && mp.isPlaying()) {
                    // Pause music play, and save the current playing position
                    mp.pause();
                    currentPlayPosition = mp.getCurrentPosition();
                    activity.stopRemoteMusic();
                    // Change button image to play button
                    btnPlay.setImageResource(R.drawable.btn_play);
                } else {
                    // Resume song
                    if (mp != null) {
                        // Resume music play
                        playSong(currentSongIndex, currentPlayPosition);
                        // Change button image to pause button
                        btnPlay.setImageResource(R.drawable.btn_pause);
                    }
                }
            }
        });

        /**
         * Next button click event plays next song by taking currentSongIndex + 1
         */
        btnNext.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View arg0) {
                if (isShuffle) {
                    // Shuffle is on - play a random song
                    Random rand = new Random();
                    currentSongIndex = rand.nextInt(songsList.size());
                    playSong(currentSongIndex, PLAY_FROM_BEGINNING);
                } else {
                    // Check if next song is there or not
                    if (currentSongIndex < (songsList.size() - 1)) {
                        playSong(++currentSongIndex, PLAY_FROM_BEGINNING);
                    } else {
                        // Play first song
                        playSong(0, PLAY_FROM_BEGINNING);
                        currentSongIndex = 0;
                    }
                }
            }
        });

        /**
         * Back button click event plays previous song by currentSongIndex - 1
         */
        btnPrevious.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View arg0) {
                if (isShuffle) {
                    // Shuffle is on - play a random song
                    Random rand = new Random();
                    currentSongIndex = rand.nextInt(songsList.size());
                    playSong(currentSongIndex, PLAY_FROM_BEGINNING);
                } else {
                    if (currentSongIndex > 0) {
                        playSong(--currentSongIndex, PLAY_FROM_BEGINNING);;
                    } else {
                        // Play last song
                        playSong(songsList.size() - 1, PLAY_FROM_BEGINNING);
                        currentSongIndex = songsList.size() - 1;
                    }
                }
            }
        });

        /**
         * Button Click event for Repeat button toggles repeat flag
         */
        btnRepeat.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View arg0) {
                if (isRepeat) {
                    isRepeat = false;
                    Toast.makeText(contentView.getContext(), "Repeat is OFF", Toast.LENGTH_SHORT).show();
                    btnRepeat.setImageResource(R.drawable.btn_repeat);
                } else {
                    // Change repeat to true
                    isRepeat = true;
                    Toast.makeText(contentView.getContext(), "Repeat is ON", Toast.LENGTH_SHORT).show();
                    // Change shuffle to false
                    isShuffle = false;
                    btnRepeat.setImageResource(R.drawable.btn_repeat_focused);
                    btnShuffle.setImageResource(R.drawable.btn_shuffle);
                }
            }
        });

        /**
         * Button Click event for Shuffle button toggles shuffle flag
         */
        btnShuffle.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View arg0) {
                if (isShuffle) {
                    isShuffle = false;
                    Toast.makeText(contentView.getContext(), "Shuffle is OFF", Toast.LENGTH_SHORT).show();
                    btnShuffle.setImageResource(R.drawable.btn_shuffle);
                } else {
                    // Change shuffle to true
                    isShuffle = true;
                    Toast.makeText(contentView.getContext(), "Shuffle is ON", Toast.LENGTH_SHORT).show();
                    // Change repeat to false
                    isRepeat = false;
                    btnShuffle.setImageResource(R.drawable.btn_shuffle_focused);
                    btnRepeat.setImageResource(R.drawable.btn_repeat);
                }
            }
        });

        /**
         * Button Click event for Playlist button launches list activity which displays list of songs
         */
        btnPlaylist.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View arg0) {
                AlertDialog.Builder builder = new AlertDialog.Builder(
                        activity, AlertDialog.THEME_HOLO_DARK);
                musicList = getMusicList().toArray(new String[getMusicList().size()]);
                builder.setTitle("Select Song");
                builder.setSingleChoiceItems(musicList, -1, new DialogInterface.OnClickListener() {
                    public void onClick(DialogInterface dialog, int item){
                        currentSongIndex = item;
                        // Play the user selected music
                        playSong(currentSongIndex, PLAY_FROM_BEGINNING);
                        dialog.dismiss();
                    }
                });

                AlertDialog dialog = builder.create();
                dialog.show();
            }
        });

        return contentView;
    }

    // Get array list from sd card
    private ArrayList<String> getMusicList() {
        ArrayList<HashMap<String, String>> songsListData = new ArrayList<HashMap<String, String>>();
        ArrayList<String> musicList = new ArrayList<String>();
        SongsManager songsManager = new SongsManager(getActivity());
        // Get all songs from sd card
        this.songsList = songsManager.getPlayList();

        // Looping through playlist
        for (int i = 0; i < songsList.size(); i++) {
            HashMap<String, String> song = songsList.get(i);
            songsListData.add(song);
        }

        for (int i = 0; i < songsListData.size(); i++) {
            musicList.add(songsListData.get(i).get("songTitle"));
        }

        return musicList;
    }

    public void startSyncDialog() {
        syncProgress.show();

        new Thread(new Runnable() {
            public void run() {
                try {
                    Thread.sleep(DELAY);
                } catch (InterruptedException e) {
                    e.printStackTrace();
                }

                // Close the progress bar dialog
                syncProgress.dismiss();
            }
        }).start();
    }

    public void playSong(int songIndex, int playPosition) {
        try {
            // Show the spinner and stop all user actions
            startSyncDialog();

            // First stop the remote music
            activity.stopRemoteMusic();

            if (songsList.isEmpty()) {
                Toast.makeText(contentView.getContext(), "Empty Playlist", Toast.LENGTH_SHORT).show();
                return;
            } else if (songsList.get(songIndex) == null) {
                Toast.makeText(contentView.getContext(),
                        "Can't play this song", Toast.LENGTH_SHORT).show();
                return;
            }

            String musicFPath = songsList.get(songIndex).get("songPath");
            String songTitle = songsList.get(songIndex).get("songTitle");
            mp.reset();

            // Get the music timer
            musicTimer = activity.getTimer();

            mp.setDataSource(musicFPath);
            songTitleLabel.setText("Now Playing: " + songTitle);

            // Changing Button Image to pause image
            btnPlay.setImageResource(R.drawable.btn_pause);
            mp.prepare(); // prepareAsync doesn't work since we want the media file to be played synchronously.

            // TODO: make sure we have buffered music. Do we really need multiple start, pause?
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

            long futurePlayTime = musicTimer.getCurrTime() + DELAY;

            // playRemoteMusic, time sensitive
            activity.playRemoteMusic(musicFPath, futurePlayTime, playPosition);

            // Let the music timer determine when to play the future playback
            musicTimer.playFutureMusic(mp, futurePlayTime, playPosition);

            // Set the song progress bar values
            songProgressBar.setProgress(0);
            songProgressBar.setMax(100);

            // Update the song progress bar
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

    /**
     *
     * */
    @Override
    public void onProgressChanged(SeekBar seekBar, int progress, boolean fromTouch) {

    }

    /**
     * When user starts moving the progress handler
     */
    @Override
    public void onStartTrackingTouch(SeekBar seekBar) {
        // Remove message Handler from updating progress bar
        handler.removeCallbacks(updateTimeTask);
    }

    /**
     * When user stops moving the progress handler
     */
    @Override
    public void onStopTrackingTouch(SeekBar seekBar) {
        int totalDuration = mp.getDuration();
        // Get the new playing position
        currentPlayPosition = utils.progressToTimer(seekBar.getProgress(), totalDuration);
        playSong(currentSongIndex, currentPlayPosition);
    }

    /**
     * On Song playing completed:
     *  - if repeat is ON: play same song again
     *  - if shuffle is ON: play random song
     *  - else: play next song
     */
    @Override
    public void onCompletion(MediaPlayer arg0) {
        if (isRepeat) {
            // Repeat is on - play same song again
            playSong(currentSongIndex, PLAY_FROM_BEGINNING);
        } else if (isShuffle) {
            // Shuffle is on - play a random song
            Random rand = new Random();
            currentSongIndex = rand.nextInt(songsList.size());
            playSong(currentSongIndex, PLAY_FROM_BEGINNING);
        } else {
            // No repeat or shuffle ON - play next song
            if (currentSongIndex < (songsList.size() - 1)) {
                playSong(++currentSongIndex, PLAY_FROM_BEGINNING);
            } else {
                // Play first song
                playSong(0, PLAY_FROM_BEGINNING);
                currentSongIndex = 0;
            }
        }
    }

    @Override
    public void onDestroy() {
        super.onDestroy();
        mp.release();
        mp = null;
    }
}

