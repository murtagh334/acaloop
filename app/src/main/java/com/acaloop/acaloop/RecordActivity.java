package com.acaloop.acaloop;

import android.content.Context;
import android.media.AudioManager;
import android.media.MediaRecorder;
import android.os.Bundle;
import android.os.Environment;
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
    AudioManager am = null;
    AudioManager.OnAudioFocusChangeListener afChangeListener = null;

    String fileName = "test_recording.3gp";
    final static String LOG_TAG = "RECORD_ACTIVITY";


    @Override
    protected void onCreate(Bundle savedInstanceState)
    {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_record);
        //Volume button presses are now directed to the correct audio stream
        setVolumeControlStream(AudioManager.STREAM_MUSIC);

        afChangeListener = new AudioManager.OnAudioFocusChangeListener()
        {
            @Override
            public void onAudioFocusChange(int focusChange)
            {
                if(focusChange == AudioManager.AUDIOFOCUS_LOSS)
                {
                    //Stop playback
                }
                else if(focusChange == AudioManager.AUDIOFOCUS_GAIN)
                {
                    //Start playback - simple for now, want a better soln for later.
                }
            }
        };
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
     * @param recordButton Button that initiated the event
     */
    private void startRecording(Button recordButton)
    {
        //TODO: make sure no app is using mic already
        recorder = new MediaRecorder();
        recorder.setAudioSource(MediaRecorder.AudioSource.MIC);
        recorder.setOutputFormat(MediaRecorder.OutputFormat.THREE_GPP);
        recorder.setAudioEncoder(MediaRecorder.AudioEncoder.AMR_NB);
        String externalDirPath = Environment.getExternalStorageDirectory().getAbsolutePath();
        recorder.setOutputFile(externalDirPath + "/" + fileName);
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
        am = (AudioManager)getSystemService(Context.AUDIO_SERVICE);

        int result = am.requestAudioFocus(afChangeListener,
                AudioManager.STREAM_MUSIC,
                AudioManager.AUDIOFOCUS_GAIN);

        if(result == AudioManager.AUDIOFOCUS_REQUEST_GRANTED)
        {
            //Start playback
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
        //abandon audio focus
        am.abandonAudioFocus(afChangeListener);
        recorder.stop();
        recordButton.setText(R.string.record);
        recorder.release();
        recorder = null;
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
