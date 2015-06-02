package com.teamadhoc.wifigroupstream;

import android.media.MediaPlayer;
import android.os.CountDownTimer;
import android.util.Log;

/**
 * Timer class used to synchronize server and clients
 * Inspired by https://github.com/bryan-y88/Musics_Around
 */
public class Timer {
    private long currTime;
    private CountDownTimer timer;
    private long precision;
    private long futurePlayTime;
    private long playPosition;
    private MediaPlayer player = null;

    // Use the system time to check how much time has actually elapsed
    private long referenceTime;

    // Minimum timer precision is 10 milliseconds
    public static final long MIN_TIMER_PRECISION = 10;

    // Default timer precision, units in milliseconds
    public static final long DEFAULT_TIMER_PRECISION = 25;

    /**
     * Creates a timer so that another thread can receive callback messages, and
     * it can count the time from any precision larger than 1 milliseconds

     * @param timerPrecision
     *            - the precision of the timer, units in milliseconds. e.g. if
     *            set to 10 milliseconds, then the timer can count time up to 10
     *            ms of precision
     */
    public Timer(long timerPrecision) {
        // Get the system time by default
        setCurrTime(System.currentTimeMillis());
        referenceTime = System.currentTimeMillis();

        if (timerPrecision < MIN_TIMER_PRECISION) {
            precision = MIN_TIMER_PRECISION;
        } else {
            precision = timerPrecision;
        }
    }

    public void startTimer() {
        // Create a timer that will never expire, until we signal it to stop
        timer = new CountDownTimer(Long.MAX_VALUE, precision) {
            /*
             * Count the timer at the user defined precision interval. This call
             * back method is synchronized so if content of the method takes too
             * long, it will not throw off the timer
             */
            @Override
            public void onTick(long millisUntilFinished) {
                // We may not be able to meet the precision time
                // so check how much time has actually elapsed
                setCurrTime(currTime + (System.currentTimeMillis() - referenceTime));

                referenceTime = System.currentTimeMillis();

                if (player != null) {
                    if (futurePlayTime < currTime) {
                        // Log.d("Music Timer", "Future time: " + futurePlayTime
                        // + ", curr time: " + currTime);
                        // NOTE: this media player needs to be able to play the
                        // song ASAP! Meaning the music has to be buffered and
                        // ready to go. It also has to be cached, not just
                        // MediaPlayer.prepare()
                        // NOTE 2: The future play time may have already passed,
                        // so we must catch up!
                        player.seekTo((int) (currTime - futurePlayTime + playPosition));
                        player.start();
                        // after we play the music, we have nothing to do with
                        // the media player, so release its reference
                        player = null;
                    }
                }
            }

            /*
             * We should never reach here... we need the timer to keep track of
             * time till the user calls cancel
             */
            @Override
            public void onFinish() {
                // We should never reach here...
                Log.e(this.getClass().getName(), "Timer unexpectedly stopped!");
            }
        };

        referenceTime = System.currentTimeMillis();

        timer.start();
    }

    public void stopTimer() {
        if (timer != null) {
            timer.cancel();
        }
    }

    // Getters and Setters
    // WARNING: outside this class, we are not able to retrieve the current time
    // to the precision we want. You will see a delay from the real time by
    // almost 1000 ms, so there is no point accessing this for precise time
    public long getCurrTime() {
        return currTime;
    }

    /**
     * Must be synchronized to prevent multiple threads changing the time
     *
     * @param currTime
     *            - update the current time, units in milliseconds
     */
    public synchronized void setCurrTime(long currTime) {
        if (currTime < 0) {
            this.currTime = 0;
        } else {
            this.currTime = currTime;
        }
    }

    /**
     * The media player must be in good shape to play music!
     *
     * @param mp
     *            - a media player ready to play music
     * @param futureTime
     *            - the future time to play music
     */
    public void playFutureMusic(MediaPlayer mp, long futureTime, long playPosition) {
        // We assume the media player is in a good state!
        futurePlayTime = futureTime;
        this.playPosition = playPosition;

        // But don't play the music if we are near the end of the music
        if (currTime - futureTime < mp.getDuration() - 100) {
            player = mp;
        }
    }
}

