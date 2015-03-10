package com.acaloop.acaloop;

import android.content.Context;
import android.media.AudioFormat;
import android.media.AudioManager;
import android.media.AudioTrack;
import android.util.Log;

import java.util.Arrays;
import java.util.InvalidPropertiesFormatException;
import java.util.Observable;
import java.util.Observer;

/**
 * Media Player that is observed by the play button, and is notified by the
 * ObservableRecorder to know when to start and stopPlayback playing.
 */
public class ObservableMediaPlayer extends Observable implements Observer
{
    final static String LOG_TAG = ObservableMediaPlayer.class.getSimpleName();

    private AudioTrack track;
    private AudioManager audioManager;
    private AudioManager.OnAudioFocusChangeListener afChangeListener;

    private short[] playbackData;

//    private PresetReverb presetReverb;

    public final static int LATENCY_TEST_FREQUENCY = 880;

    /**
     * @param recordActivity The RecordActivity that holds this ObservableMediaPlayer
     */
    public ObservableMediaPlayer(RecordActivity recordActivity) throws InvalidPropertiesFormatException
    {
        super();

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

    private void initPlayer() throws InvalidPropertiesFormatException
    {
        int sampleRateInHz = RecordActivity.SAMPLE_RATE_HZ;
        int channelConfig = AudioFormat.CHANNEL_OUT_STEREO;
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

        //TODO: Put reverb back in with settings for different reverbs
//        presetReverb = new PresetReverb(0,track.getAudioSessionId());
//        presetReverb.setPreset(PresetReverb.PRESET_LARGEHALL);
//        if(presetReverb.setEnabled(true) != PresetReverb.SUCCESS)
//        {
//            Log.e(LOG_TAG, "preset reverb not enabled");
//        }

        //Request "permanent" audio focus
        //Meaning we want to play audio for the foreseeable future.
        int result = audioManager.requestAudioFocus(afChangeListener,
                RecordActivity.STREAM,
                AudioManager.AUDIOFOCUS_GAIN);

        if(result != AudioManager.AUDIOFOCUS_REQUEST_GRANTED)
        {
            Log.e(LOG_TAG, "AudioFocus request not granted. Result: " + result);
        }

        //TODO: Don't allow sleeping while we're playing. (wake lock)
    }

    /**
     * Setup some playback data for a latency test.
     */
    public void setupLatencyTest()
    {
        stopPlayback();
        generateSineWave(LATENCY_TEST_FREQUENCY);
        //TODO: This could be an option later on for the headphone-less in general.
//        audioManager.setMode(AudioManager.MODE_IN_CALL);
//        audioManager.setSpeakerphoneOn(true);
    }

    /**
     * Cleanup settings after a latency test
     */
    public void cleanupLatencyTest()
    {
//        audioManager.setSpeakerphoneOn(false);
//        audioManager.setMode(AudioManager.MODE_NORMAL);
        //Delete the sine wave data
        deletePlaybackData();
    }

    private void generateSineWave(int frequency)
    {
        playbackData = new short[getFrequencyDuration()*track.getChannelCount()];
        double increment = ((2*Math.PI)*frequency)/(double)track.getSampleRate();
        double theta = 0;

        for(int i  = 0; i < playbackData.length; i+=track.getChannelCount())
        {
            for(int j = 0; j < track.getChannelCount(); j++)
            {
                //Use byte max value *8 as the size of the amplitude because clipping occurs when using the full possible short.
                playbackData[i+j] = (byte) (Math.sin(theta) * Byte.MAX_VALUE);
            }
            theta+=increment;
        }
    }

    /**
     * @return The duration in samples of the tone played for latency correction
     */
    public int getFrequencyDuration()
    {
        //i.e. 0.1 seconds
        return track.getSampleRate()/10;
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

        track.play();
        new Thread( new Runnable()
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
        track.write(playbackData, 0, playbackData.length);
        //If we completed playback without "stopping" it, set to stopped.
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
//    public void cleanupPlayer()
//    {
//        if(track != null)
//        {
//            stopPlayback();
//
//            track.release();
//            track = null;
//        }
//
////        if(presetReverb != null)
////        {
////            presetReverb.release();
////            presetReverb = null;
////        }
//    }

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

            Log.d(LOG_TAG, "Update called");
            if(data instanceof short[])
            {
                short[] newData = (short[])data;
                Log.d(LOG_TAG, "Got some data: Length: " + newData.length + " Playbackdata length: " + (playbackData == null ? 0 : playbackData.length));

                if(playbackData == null)
                {
                    playbackData = Arrays.copyOf(newData, newData.length);
                }
                else
                {
                    for (int i = 0; i < Math.min(newData.length, playbackData.length); i++)
                    {
                        playbackData[i] += newData[i];
                        //Otherwise will get too loud.
                        //TODO: scale so that the loudest peak of the added data becomes the maximum short can do.
                        playbackData[i] *= 0.5f;
                    }
                }
            }
            else
            {
                Log.e(LOG_TAG, "New playback audio data not retrievable");
            }
        }
    }

    /**
     * @return The AudioTrack's sample rate
     */
    public int getSampleRate()
    {
        return track.getSampleRate();
    }

    /**
     * @return The AudioTrack's channel count
     */
    public int getChannelCount()
    {
        return track.getChannelCount();
    }
}