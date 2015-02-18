package com.acaloop.acaloop;

import android.content.Context;
import android.media.AudioManager;
import android.media.MediaPlayer;
import android.os.PowerManager;
import android.util.Log;

import java.io.File;
import java.io.IOException;
import java.util.Observable;
import java.util.Observer;

/**
 * Media Player that is observed by the play button, and is notified by the
 * ObservableRecorder to know when to start and stopPlayback playing.
 */
public class ObservableMediaPlayer extends Observable implements Observer
{
    final static String LOG_TAG = ObservableMediaPlayer.class.getName();
    final static String PLAYBACK_FILE_NAME = "playback";

    File playbackFile;

    RecordActivity recordActivity;

    MediaPlayer mediaPlayer;
    MediaPlayer.OnPreparedListener onPreparedListener;
    MediaPlayer.OnCompletionListener onCompletionListener;
    MediaPlayer.OnErrorListener onErrorListener;

    AudioManager audioManager;
    AudioManager.OnAudioFocusChangeListener afChangeListener;

    /**
     * @param recordActivity The RecordActivity that holds this ObservableMediaPlayer
     */
    ObservableMediaPlayer(RecordActivity recordActivity)
    {
        super();
        this.recordActivity = recordActivity;

        playbackFile = new File(RecordActivity.getPathForAudioFile(PLAYBACK_FILE_NAME));

        audioManager = (AudioManager)recordActivity.getSystemService(Context.AUDIO_SERVICE);

        onPreparedListener = new MediaPlayer.OnPreparedListener()
        {
            @Override
            public void onPrepared(MediaPlayer mediaPlayer)
            {
                //Just begin playing for now. Later should indicate that we're prepared with boolean.
                if(!mediaPlayer.isPlaying())
                {
                    mediaPlayer.start();

                    //Notify observers that we're playing.
                    setChanged();
                    notifyObservers();
                }
            }
        };

        //When playback completes, stop recording
        onCompletionListener = new MediaPlayer.OnCompletionListener()
        {
            @Override
            public void onCompletion(MediaPlayer mediaPlayer)
            {
                cleanupPlayer();

                //Notify observers that we've stopped.
                //After cleanup so we know for sure that the player stopped
                setChanged();
                notifyObservers();
            }
        };

        //Since we're preparing asynchronously, need to listen for potential errors
        onErrorListener = new MediaPlayer.OnErrorListener()
        {
            @Override
            public boolean onError(MediaPlayer mediaPlayer, int what, int extra)
            {
                Log.e(LOG_TAG, what + " " + extra);
                //MediaPlayer has been moved to the error state and must be reset.
                cleanupPlayer();
                return false;
            }
        };

        afChangeListener = new AudioManager.OnAudioFocusChangeListener()
        {
            @Override
            public void onAudioFocusChange(int focusChange)
            {
                if(focusChange == AudioManager.AUDIOFOCUS_LOSS)
                {
                    stopPlayback();
                }
            }
        };
    }

    /**
     * @return True iff the MediaPlayer is playing
     */
    public boolean isPlaying()
    {
        return mediaPlayer != null && mediaPlayer.isPlaying();
    }

    /**
      * @return False if we were unable to init the player
     */
    private boolean initPlayer()
    {
        if(!playbackFile.exists())
            return false;

        mediaPlayer = new MediaPlayer();
        mediaPlayer.setAudioStreamType(RecordActivity.STREAM);
        mediaPlayer.setOnPreparedListener(onPreparedListener);
        mediaPlayer.setOnCompletionListener(onCompletionListener);
        mediaPlayer.setOnErrorListener(onErrorListener);

        try
        {
            mediaPlayer.setDataSource(playbackFile.getAbsolutePath());
        }
        catch(IOException e)
        {
            //This is okay, this just means a file hasn't been recorded for us yet.
            e.printStackTrace();
            Log.e(LOG_TAG, "Couldn't set media player data source to playback file");
            cleanupPlayer();
            return false;
        }

        //Don't allow screen to sleep while we're playing back / recording.
        mediaPlayer.setWakeMode(recordActivity.getApplicationContext(), PowerManager.PARTIAL_WAKE_LOCK);
        return true;
    }

    /**
     * Starts playing the track at the playback file
     */
    public void startPlayback()
    {
        //If already playing, don't need to play
        if(mediaPlayer != null && mediaPlayer.isPlaying())
            return;

        //Request "permanent" audio focus
        //Meaning we want to play audio for the foreseeable future.
        int result = audioManager.requestAudioFocus(afChangeListener,
                RecordActivity.STREAM,
                AudioManager.AUDIOFOCUS_GAIN);

        if(result != AudioManager.AUDIOFOCUS_REQUEST_GRANTED)
            return;

        //Starts another thread to prepare for playback. Calls back through MediaPlayer.OnPreparedListener
        if(initPlayer())
            mediaPlayer.prepareAsync();
    }

    /**
     * Stops playing the audio stream
     * Releases it appropriately, and updates buttons.
     */
    public void stopPlayback()
    {
        cleanupPlayer();
    }

    /**
     * Releases the player, but also stops it
     * if it was still playing.
     */
    public void cleanupPlayer()
    {
        if(mediaPlayer == null)
            return;

        if(mediaPlayer.isPlaying())
        {
            mediaPlayer.stop();
            setChanged();
            notifyObservers();
        }

        mediaPlayer.reset();
        mediaPlayer.release();
        mediaPlayer = null;

        //abandon audio focus since we're done with it.
        audioManager.abandonAudioFocus(afChangeListener);
    }

    /**
     * @param observable Recorder notifying us that its state has changed.
     * @param data Not used
     */
    @Override
    public void update(Observable observable, Object data)
    {
        ObservableRecorder observableRecorder = (ObservableRecorder)observable;
        if(observableRecorder.isRecording() && !isPlaying())
        {
            startPlayback();
        }
        else if(!observableRecorder.isRecording() && isPlaying())
        {
            stopPlayback();
        }
    }

    /**
     * @return Our playback file that we play from
     */
    public File getPlaybackFile()
    {
        if(!isPlaying())
            return playbackFile;

        return null;
    }
}