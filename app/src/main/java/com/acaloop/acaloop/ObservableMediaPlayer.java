package com.acaloop.acaloop;

import android.content.Context;
import android.media.AudioManager;
import android.media.AudioTrack;
import android.util.Log;

import java.util.InvalidPropertiesFormatException;
import java.util.Observable;
import java.util.Observer;

/**
 * Media Player that is observed by the play button, and is notified by the
 * ObservableRecorder to know when to start and stopPlayback playing.
 */
public class ObservableMediaPlayer extends Observable implements Observer
{
    final static String LOG_TAG = ObservableMediaPlayer.class.getName();

    RecordActivity recordActivity;

    AudioTrack track;
    AudioManager audioManager;
    AudioManager.OnAudioFocusChangeListener afChangeListener;

    byte [] playbackData;

    /**
     * @param recordActivity The RecordActivity that holds this ObservableMediaPlayer
     */
    public ObservableMediaPlayer(RecordActivity recordActivity) throws InvalidPropertiesFormatException
    {
        super();
        this.recordActivity = recordActivity;

        audioManager = (AudioManager)recordActivity.getSystemService(Context.AUDIO_SERVICE);

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

        initPlayer();
    }

    /**
     * @return True iff the MediaPlayer is playing
     */
    public boolean isPlaying()
    {
        return track != null && track.getPlayState() == AudioTrack.PLAYSTATE_PLAYING;
    }

    /**
     * @return False if we were unable to init the player
     */
    private boolean initPlayer() throws InvalidPropertiesFormatException
    {
        int sampleRateInHz = RecordActivity.SAMPLE_RATE_HZ;
        int channelConfig = RecordActivity.CHANNEL_CONFIG;
        int audioFormat = RecordActivity.AUDIO_FORMAT;
        int minBufferSize = AudioTrack.getMinBufferSize(sampleRateInHz,channelConfig,audioFormat);

        track = new AudioTrack(AudioManager.STREAM_MUSIC,
                sampleRateInHz,channelConfig,
                audioFormat,minBufferSize,
                AudioTrack.MODE_STREAM);

        if(track.getState() != AudioTrack.STATE_INITIALIZED)
        {
            throw new InvalidPropertiesFormatException("Couldn't initialize AudioTrack. Track in state: " + track.getState());
        }

        //TODO: Don't allow sleeping while we're playing. (wake lock)
        return true;
    }

    /**
     * Starts playing the track at the playback file
     */
    public void startPlayback()
    {
        //If already playing, don't need to play
        //If we don't have any data to play, don't attempt to play.
        if(isPlaying() || playbackData == null)
            return;

        //Request "permanent" audio focus
        //Meaning we want to play audio for the foreseeable future.
        int result = audioManager.requestAudioFocus(afChangeListener,
                RecordActivity.STREAM,
                AudioManager.AUDIOFOCUS_GAIN);

        if(result != AudioManager.AUDIOFOCUS_REQUEST_GRANTED)
            return;

        track.play();
        new Thread(new Runnable()
        {
            @Override
            public void run()
            {
                writeAudioFromPlaybackData();
            }
        }).start();

        setChanged();
        notifyObservers();
    }

    private void writeAudioFromPlaybackData()
    {
        track.write(playbackData,0, playbackData.length);
        stopPlayback();
    }

    /**
     * Stops playing the audio stream
     * Releases it appropriately, and updates buttons.
     */
    public void stopPlayback()
    {
        if(isPlaying())
        {
            track.flush();
            track.stop();

            setChanged();
            notifyObservers();
            //abandon audio focus since we're done with it.
            //audioManager.abandonAudioFocus(afChangeListener);
        }
    }

    /**
     * Releases the player, but also stops it
     * if it was still playing.
     */
    public void cleanupPlayer()
    {
        if(track == null)
            return;

        stopPlayback();

        track.release();
        track = null;
    }

    public void deletePlaybackData()
    {
        playbackData = null;
    }

    /**
     * @param observable Recorder notifying us that its state has changed.
     * @param data Playback data sent by the recorder.
     */
    @Override
    public void update(Observable observable, Object data)
    {
        ObservableRecorder observableRecorder = (ObservableRecorder)observable;
        if(observableRecorder.isRecording() && !isPlaying())
        {
            startPlayback();
        }
        else if(!observableRecorder.isRecording())
        {
            if(isPlaying())
                stopPlayback();

            if(data instanceof byte[])
            {
                //New recording overwrites previous data.
                playbackData = (byte[])data;
            }
            else
            {
                Log.e(LOG_TAG, "New playback audio data not retrievable");
            }
        }
    }
}