package com.acaloop.acaloop;

import android.media.AudioRecord;
import android.media.MediaRecorder;
import android.util.Log;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.util.InvalidPropertiesFormatException;
import java.util.Observable;
import java.util.Observer;

/**
 * Recorder that observes ObservableMediaPlayer so it can stop when playback stops.
 */
public class ObservableRecorder extends Observable implements Observer
{
    AudioRecord recorder;
    int bufferSize;

    private static String LOG_TAG = ObservableRecorder.class.getCanonicalName();

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
    private void initRecorder() throws InvalidPropertiesFormatException
    {
        //TODO: make sure no app is using mic already?
        //TODO: choose better sample rate, channel config, audio format if available.
        int sampleRateInHz = RecordActivity.SAMPLE_RATE_HZ;
        int channelConfig = RecordActivity.CHANNEL_CONFIG;
        int audioFormat = RecordActivity.AUDIO_FORMAT;
        int minBufferSize = AudioRecord.getMinBufferSize(sampleRateInHz, channelConfig, audioFormat);
        bufferSize = minBufferSize;

        recorder = new AudioRecord(MediaRecorder.AudioSource.MIC,
                sampleRateInHz,channelConfig,audioFormat,minBufferSize);
        if(recorder.getState() != AudioRecord.STATE_INITIALIZED)
        {
            throw new InvalidPropertiesFormatException("Couldn't initialize AudioRecord. Recorder in state: " + recorder.getState());
        }
    }

    /**
     * Start a recording. Notify observers that we've started.
     */
    public void startRecording()
    {
        recorder.startRecording();
        setChanged();
        notifyObservers();

        //Start recording on a new thread. Don't block this one.
        new Thread(new Runnable()
        {
            @Override
            public void run()
            {
                writeAudioDataToStream();
            }
        }).start();
    }

    private void writeAudioDataToStream()
    {
        ByteArrayOutputStream os = new ByteArrayOutputStream();
        while(isRecording())
        {
            byte []audioData = new byte[bufferSize];
            recorder.read(audioData,0,bufferSize);
            try
            {
                os.write(audioData);
            } catch (IOException e)
            {
                e.printStackTrace();
                Log.e(LOG_TAG, "Couldn't write to byte output stream");
            }
        }
        //Not using recorder for foreseeable future, free resources.
        //cleanupRecorder();

        Log.d(LOG_TAG, "Sending os byte array");
        //We have officially stopped recording now. send the audio data to whoever needs it.
        setChanged();
        notifyObservers(os.toByteArray());
        try
        {
            os.close();
        } catch (IOException e)
        {
            e.printStackTrace();
        }
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

    /**
     * Release recorder appropriately.
     * This means checking that it's stopped recording first,
     * and stopping it if it hasn't.
     */
    public void cleanupRecorder()
    {
        if(recorder == null)
            return;

        stopRecording();

        recorder.release();
        recorder = null;
    }

    /**
     * @param observable The MediaPlayer we are watching. If it stops playing while we're recording,
     *                   then we should stop recording as well.
     * @param data Not used
     */
    @Override
    public void update(Observable observable, Object data)
    {
        ObservableMediaPlayer observableMediaPlayer = (ObservableMediaPlayer)observable;
        //If we were recording, but playback finished, we should stop
        if(!observableMediaPlayer.isPlaying() && isRecording())
        {
            stopRecording();
        }
    }
}