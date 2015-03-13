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

    private short[] playbackData = null;
//    private boolean canPlay = false;

//    private PresetReverb presetReverb;

    public final static int FRAMES_PER_PERIOD = 50;

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
//        short[] metronomeTone = generateSineWave();
    }

    /**
     * Setup some playback data for a latency test.
     */
    public void setupLatencyTest()
    {
        stopPlayback();
        int latencyFrequency = getLatencyToneFrequency();
        //Divide by 8 because this gives the phase of the sine wave that the goertzel algorithm recognizes the best given a certain tone duration.
        playbackData = generateSineWave(latencyFrequency, getLatencyToneDurationInFrames(), getFramesPerPeriod(latencyFrequency)/8);
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

    /**
     * Return PCM data of a simple sine wave with given frequency and duration
     * @param frequency The frequency / pitch of the sine wave
     * @param durationInFrames The duration of the sine wave
     * @return The audio data representing the sine wave
     */
    private short[] generateSineWave(int frequency, int durationInFrames, int phase)
    {
        int durationInSamples = durationInFrames*track.getChannelCount();
        short[] buffer = new short[durationInSamples];
        double increment = ((2*Math.PI)*frequency)/(double)track.getSampleRate();

        for(int i  = 0; i < buffer.length; i+=track.getChannelCount())
        {
            for(int j = 0; j < track.getChannelCount(); j++)
            {
                //Use byte max value *8 as the size of the amplitude because clipping occurs when using the full possible short.
                buffer[i+j] = (short) (Math.sin(increment*(i - phase)) * Short.MAX_VALUE);
            }
        }
        return buffer;
    }

    /**
     * Starts playing the track at the playback file
     */
    public void startPlayback()
    {
        Log.d(LOG_TAG, "Start Playback");
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
        //Allow playback to start as soon as possible.
        Thread.yield();

        setChanged();
        notifyObservers();
    }

    /**
     * Make sure the recording thread has started before we start recording.
     * Must be synchronized because we must be holding this object's lock before waiting.
     * Should only do this if we
     */
//    private synchronized void waitForRecordingToStart()
//    {
//        while(!canPlay)
//        {
//            Log.d(LOG_TAG, "Waiting...");
//            try
//            {
//                //waiting releases the lock and we go to sleep
//                wait();
//            }
//            catch(InterruptedException e)
//            {
//                //do nothing
//            }
//        }
//        //Reset canPlay.
//        canPlay = false;
//    }

    private void writeAudioFromPlaybackData()
    {
        Log.d(LOG_TAG, "Starting Playback: " + System.currentTimeMillis());
//        waitForRecordingToStart();
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
//        if(observableRecorder.isRecording() && !isPlaying())
//        {
//            startPlayback();
//        }
/*        else*/ if(!observableRecorder.isRecording())
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

    public int getFramesPerPeriod()
    {
        return FRAMES_PER_PERIOD;
    }
    public int getFramesPerPeriod(int frequency)
    {
        return getSampleRate()/frequency;
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

    /**
     * @return The duration in frames of the tone played for latency correction
     */
    public int getLatencyToneDurationInFrames()
    {
        //0.5 seconds. Since my frequency is 441, (50 frames per period, 882 periods per second)
        //Will be 44100 samples = 22050 frames. Clearly the period evenly goes into 22050 frames, so the duration is perfect.
        return track.getSampleRate()/2;
    }

    /**
     * @return The frequency of the latency tone (Periods per second)
     */
    public int getLatencyToneFrequency()
    {
        //Frequency of interest should be an integer factor of sample rate
        //http://www.embedded.com/design/configurable-systems/4024443/The-Goertzel-Algorithm
        return track.getSampleRate()/FRAMES_PER_PERIOD;
    }

//    public synchronized void notifyCanPlay()
//    {
//        canPlay = true;
//        notifyAll();
//    }
}