package com.acaloop.acaloop;

import android.annotation.TargetApi;
import android.media.AudioFormat;
import android.media.AudioRecord;
import android.media.MediaRecorder;
import android.util.Log;

import java.util.Arrays;
import java.util.InvalidPropertiesFormatException;
import java.util.Observable;

/**
 * Recorder that observes ObservableMediaPlayer so it can stop when playback stops.
 */
public class ObservableRecorder extends Observable //implements Observer
{
    private AudioRecord recorder;
    private int bufferSize;
//    private NoiseSuppressor noiseSuppressor;

    private static String LOG_TAG = ObservableRecorder.class.getSimpleName();
    private int latency;

    public ObservableRecorder() throws InvalidPropertiesFormatException
    {
        super();
        initRecorder();
    }

    /**
     * @return True iff the recorder is recording
     */
    public boolean isRecording()
    {
        return recorder!=null && recorder.getRecordingState() == AudioRecord.RECORDSTATE_RECORDING;
    }

    /**
     * Initialize our recorder.
     */
    //Hushes compiler about NoiseSuppressor. we've handled it.
    @TargetApi(16)
    private void initRecorder() throws InvalidPropertiesFormatException
    {
        latency = 0;

        //TODO: make sure no app is using mic already?
        //TODO: choose better sample rate, channel config, audio format if available.
        int sampleRateInHz = RecordActivity.SAMPLE_RATE_HZ;
        int channelConfig = AudioFormat.CHANNEL_IN_STEREO;
        int audioFormat = RecordActivity.AUDIO_FORMAT;
        int minBufferSize = AudioRecord.getMinBufferSize(sampleRateInHz, channelConfig, audioFormat);
        bufferSize = minBufferSize;

        //Use CAMCORDER so that when headphones are plugged in, it still uses the mic from the phone
        //TODO: If headphones HAVE a mic, should be able to use that instead
        recorder = new AudioRecord(MediaRecorder.AudioSource.CAMCORDER,
                sampleRateInHz,channelConfig,audioFormat,minBufferSize);

        if(recorder.getState() != AudioRecord.STATE_INITIALIZED)
        {
            throw new InvalidPropertiesFormatException("Couldn't initialize AudioRecord. Recorder in state: " + recorder.getState());
        }

        //TODO: Test more with noise suppressor before adding this.
//        if(Build.VERSION.SDK_INT >= 16 && NoiseSuppressor.isAvailable())
//        {
//            noiseSuppressor = NoiseSuppressor.create(recorder.getAudioSessionId());
//
//            if (noiseSuppressor.setEnabled(true) != NoiseSuppressor.SUCCESS)
//            {
//                Log.e(LOG_TAG, "Could not enable noise suppressor");
//            }
//        }
//        else
//        {
//            Log.d(LOG_TAG, ""+NoiseSuppressor.isAvailable());
//            Log.d(LOG_TAG, "Noise suppression not available");
//        }
    }

    /**
     * Start a recording. Notify observers that we've started.
     */
    public void startRecording()
    {
        startRecording(false, null);
    }

    /**
     * Start a recording. Notify observers that we've started.
     */
    public void startRecording(final boolean isLatencyTestRecording, ObservableMediaPlayer mediaPlayer)
    {
        recorder.startRecording();

        //Start recording on a new thread. Absolutely don't block this one.
        new Thread(new Runnable()
        {
            @Override
            public void run()
            {
                writeAudioDataToStream(isLatencyTestRecording);
            }
        }).start();

        //Start recording first then playback should start.
        //Can always re-align, but can't re-align audio that was never captured.
        setChanged();
        notifyObservers();

        //Don't bother saving latency test audio data to the player.
        if(isLatencyTestRecording)
            deleteObserver(mediaPlayer);
    }

    private void writeAudioDataToStream(boolean isLatencyTestRecording)
    {
        //Can only record up to this length.
        int maxRecordingLength = 5;
        short []audioData= new short[recorder.getChannelCount()*recorder.getSampleRate()*maxRecordingLength];
        int offset = 0;
        while(isRecording())
        {
            int shortsRead = recorder.read(audioData,offset,Math.min(bufferSize,audioData.length - offset));
            if(shortsRead <=0 )
            {
                stopRecording();
                break;
            }
            offset+=shortsRead;
        }
        //Not using recorder for foreseeable future, free resources.
        //cleanupRecorder();

        Log.d(LOG_TAG, "latency: " + latency + " " + isLatencyTestRecording);

        //Save our recorded data
        int numZeroes = 0;
        for(; numZeroes < audioData.length; numZeroes++)
        {
            if(audioData[numZeroes]!=0)
            {
                break;
            }
        }

        Log.d(LOG_TAG, "Number of zeroes at beginning: " + numZeroes);
        //If we get zeroes at beginning, assume this is some other form of latency that we can account for.
        //remove them before calculating & applying latency correction
        short[] recordedData = Arrays.copyOfRange(audioData, isLatencyTestRecording ? numZeroes : numZeroes + latency,offset);

        //We have officially stopped recording now. send the audio data to whoever needs it.
        setChanged();
        notifyObservers(recordedData);
    }

    /**
     * Stop recording and release recorder.
     */
    public void stopRecording()
    {
        if(isRecording())
        {
            recorder.stop();
        }
    }

//    /**
//     * Release recorder appropriately.
//     * This means checking that it's stopped recording first,
//     * and stopping it if it hasn't.
//     */
//    public void cleanupRecorder()
//    {
//        if(recorder != null)
//        {
//            stopRecording();
//
//            recorder.release();
//            recorder = null;
//        }
//
////        if(noiseSuppressor != null)
////        {
////            noiseSuppressor.release();
////            noiseSuppressor = null;
////        }
//    }

    //TODO: Bring this back (recorder stops when playback stops once we can time the recorder's stopping correctly)
    //i.e. send the notification that the playback has stopped some time after finishing writing the data to be queued
//    /**
//     * @param observable The MediaPlayer we are watching. If it stops playing while we're recording,
//     *                   then we should stop recording as well.
//     * @param data Not used
//     */
//    @Override
//    public void update(Observable observable, Object data)
//    {
//        ObservableMediaPlayer observableMediaPlayer = (ObservableMediaPlayer)observable;
//        //If we were recording, but playback finished, we should stop
//        if(!observableMediaPlayer.isPlaying() && isRecording())
//        {
//            stopRecording();
//        }
//    }

    /**
     *Sets the latency correction value (in samples)
     * @param samples Latency in samples
     */
    public void setLatency(int samples)
    {
        latency = samples;
    }

}