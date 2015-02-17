package com.acaloop.acaloop;

import android.content.Context;
import android.media.AudioManager;
import android.media.MediaPlayer;
import android.media.MediaRecorder;
import android.os.Bundle;
import android.os.Environment;
import android.os.PowerManager;
import android.support.v7.app.ActionBarActivity;
import android.util.Log;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.widget.Button;

import java.io.IOException;

public class RecordActivity extends ActionBarActivity
{

    MediaRecorder recorder = null;
    AudioManager audioManager = null;
    AudioManager.OnAudioFocusChangeListener afChangeListener = null;
    final static int STREAM = AudioManager.STREAM_MUSIC;

    MediaPlayer mediaPlayer = null;
    MediaPlayer.OnPreparedListener onPreparedListener = null;
    MediaPlayer.OnCompletionListener onCompletionListener = null;
    MediaPlayer.OnErrorListener onErrorListener = null;

    String playbackFileName = null;
    String recordFileName = null;

    final static String LOG_TAG = "RECORD_ACTIVITY";

    /**
     * Initialize variables that only need to be initialized once
     */
    public RecordActivity()
    {
        super();
        String externalDirPath = Environment.getExternalStorageDirectory().getAbsolutePath();
        playbackFileName = externalDirPath + "/test_recording2.3gp";
        recordFileName = externalDirPath + "/test_recording.3gp";

        afChangeListener = new AudioManager.OnAudioFocusChangeListener()
        {
            @Override
            public void onAudioFocusChange(int focusChange)
            {
                if(focusChange == AudioManager.AUDIOFOCUS_LOSS)
                {
                    stopPlayback();
                }
                else if(focusChange == AudioManager.AUDIOFOCUS_GAIN)
                {
                    startPlayback();
                }
            }
        };

        onPreparedListener = new MediaPlayer.OnPreparedListener()
        {
            @Override
            public void onPrepared(MediaPlayer mp)
            {
                //Just begin playing for now. Later should indicate that we're prepared with boolean.
                if(!mp.isPlaying())
                    mp.start();
            }
        };

        //When playback completes, stop recording.
        onCompletionListener = new MediaPlayer.OnCompletionListener()
        {
            @Override
            public void onCompletion(MediaPlayer mp)
            {
                //TODO: onCompletion
            }
        };

        //Since we're preparing asynchronously, need to listen for potential errors
        onErrorListener = new MediaPlayer.OnErrorListener()
        {
            @Override
            public boolean onError(MediaPlayer mp, int what, int extra)
            {
                //MediaPlayer has been moved to the error state and must be reset
                //TODO: onError should stop recording, reset state to normal.
                mediaPlayer.release();
                mediaPlayer = null;
                return false;
            }
        };
    }
    @Override
    protected void onCreate(Bundle savedInstanceState)
    {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_record);
        //Volume button presses are now directed to the correct audio stream
        setVolumeControlStream(STREAM);
    }

    /**
     * Clean-up when our application is interrupted
     */
    @Override
    protected void onStop()
    {
        super.onStop();

        if(recorder != null)
        {
            recorder.release();
            recorder = null;
        }

        //Releases the media player
        audioManager.abandonAudioFocus(afChangeListener);
    }

    @Override
    protected void onPause()
    {
        //TODO: handle user navigating away from app.
        super.onPause();
    }

    @Override
    protected void onDestroy()
    {
        super.onDestroy();
    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu)
    {
        // Inflate the menu; this adds items to the action bar if it is present.
        getMenuInflater().inflate(R.menu.menu_record, menu);
        return true;
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item)
    {
        // Handle action bar item clicks here. The action bar will
        // automatically handle clicks on the Home/Up button, so long
        // as you specify a parent activity in AndroidManifest.xml.
        int id = item.getItemId();

        //noinspection SimplifiableIfStatement
        if (id == R.id.action_settings)
        {
            return true;
        }

        return super.onOptionsItemSelected(item);
    }

    /**
     * Starts playing tracks accumulated so far.
     */
    private void startPlayback()
    {
        mediaPlayer = new MediaPlayer();
        mediaPlayer.setAudioStreamType(STREAM);
        try
        {
            mediaPlayer.setDataSource(playbackFileName);
        }
        catch(IOException e)
        {
            Log.e(LOG_TAG, "Error finding file for playback");
            e.printStackTrace();
            return;
        }

        mediaPlayer.setOnPreparedListener(onPreparedListener);
        mediaPlayer.setOnCompletionListener(onCompletionListener);
        mediaPlayer.setOnErrorListener(onErrorListener);

        //Don't allow screen to sleep while we're playing back / recording.
        mediaPlayer.setWakeMode(getApplicationContext(), PowerManager.PARTIAL_WAKE_LOCK);

        //Starts another thread to prepare for playback. Calls back through MediaPlayer.OnPreparedListener
        mediaPlayer.prepareAsync();
    }

    /**
     * Stops playing the audio stream.
     */
    private void stopPlayback()
    {
        if(mediaPlayer == null)
            return;

        if(mediaPlayer.isPlaying())
            mediaPlayer.stop();
        mediaPlayer.release();
        mediaPlayer = null;
    }

    /**
     * @param recordButton Button that initiated the event
     */
    private void startRecording(Button recordButton)
    {
        //TODO: make sure no app is using mic already
        //TODO: do we have to make a new one each time? See if we can get by with re-preparing look up lifecycle.
        //Same goes for mediaPlayer
        recorder = new MediaRecorder();
        recorder.setAudioSource(MediaRecorder.AudioSource.MIC);
        recorder.setOutputFormat(MediaRecorder.OutputFormat.THREE_GPP);
        recorder.setAudioEncoder(MediaRecorder.AudioEncoder.AMR_NB);
        recorder.setOutputFile(recordFileName);
        try
        {
            recorder.prepare();
        }
        catch (IOException e)
        {
            e.printStackTrace();
            Log.e(LOG_TAG, "Prepare Failed");
        }

        //Request "permanent" audio focus
        //Meaning we want to play the music for the foreseeable future.
        audioManager = (AudioManager)getSystemService(Context.AUDIO_SERVICE);

        int result = audioManager.requestAudioFocus(afChangeListener,
                STREAM,
                AudioManager.AUDIOFOCUS_GAIN);

        if(result == AudioManager.AUDIOFOCUS_REQUEST_GRANTED)
        {
            startPlayback();
        }
        //Start recording
        recorder.start();
        recordButton.setText(R.string.stop_recording);
    }

    /**
     * @param recordButton Button that initiated the event
     */
    private void stopRecording(Button recordButton)
    {
        //abandon audio focus since we're done with it. Also stops playback.
        //TODO: after finishing the recording, we should start preparing playback for next record.
        audioManager.abandonAudioFocus(afChangeListener);
        if(recorder != null)
        {
            //Is stop release redundant?
            recorder.stop();
            recorder.release();
            recorder = null;
        }
        recordButton.setText(R.string.record);
    }

    /**
     * Called when record button is clicked
     */
    public void onClickRecord(View v)
    {
        Button recordButton = (Button)v;
        if(recorder == null)
        {
            startRecording(recordButton);
        }
        else
        {
            stopRecording(recordButton);
        }
    }
}
