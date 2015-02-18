package com.acaloop.acaloop;

import android.media.MediaRecorder;
import android.util.Log;

import java.io.File;
import java.io.IOException;
import java.util.Observable;
import java.util.Observer;

/**
 * Recorder that observes ObservableMediaPlayer so it can stop when playback stops.
 */
public class ObservableRecorder extends Observable implements Observer
{
    final static String LOG_TAG = ObservableRecorder.class.getName();
    final static String RECORD_FILE_NAME = "temp_record";

    MediaRecorder recorder;
    //MediaRecorder doesn't have a notion for this
    boolean recording = false;
    File recordFile;

    public ObservableRecorder()
    {
        super();
        recordFile = new File(RecordActivity.getPathForAudioFile(RECORD_FILE_NAME));
    }

    /**
     * @return True iff the MediaRecorder is recording
     */
    public boolean isRecording()
    {
        return recording;
    }

    /**
     * @return False if we were unable to init the recorder
     */
    private boolean initRecorder()
    {
        //TODO: make sure no app is using mic already?
        recorder = new MediaRecorder();
        recorder.setAudioSource(MediaRecorder.AudioSource.MIC);
        recorder.setOutputFormat(MediaRecorder.OutputFormat.MPEG_4);
        recorder.setAudioEncoder(MediaRecorder.AudioEncoder.AAC);
        recorder.setAudioEncodingBitRate(16);
        recorder.setAudioSamplingRate(44100);

        //Make sure our appDir exists before trying to record to it.
        String appDirPath = RecordActivity.getAppDirPath();
        File appDir = new File(appDirPath);
        if(!appDir.exists() &&!appDir.mkdirs())
        {
            Log.e(LOG_TAG, "Could not make app dir " + appDirPath);
            cleanupRecorder();
            return false;
        }

        //Must be called AFTER setOutputFormat.
        recorder.setOutputFile(recordFile.getPath());
        return true;
    }

    /**
     * Start a recording. Notify observers that we've started.
     */
    public void startRecording()
    {
        if(!initRecorder())
            return;

        try
        {
            recorder.prepare();
        }
        catch (IOException e)
        {
            Log.e(LOG_TAG, "Prepare Failed. This should not happen");
            cleanupRecorder();
            return;
        }

        recorder.start();
        recording = true;
        setChanged();
        notifyObservers();
    }

    /**
     * Stop recording and release recorder.
     */
    public void stopRecording()
    {
        cleanupRecorder();
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

        if(recording)
        {
            recorder.stop();
            recording = false;
            setChanged();
            notifyObservers();
        }

        recorder.reset();
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

    /**
     * @return The recorded file. Null if we're currently recording.
     */
    public File getRecordedFile()
    {
        if(!isRecording())
            return recordFile;

        return null;
    }
}