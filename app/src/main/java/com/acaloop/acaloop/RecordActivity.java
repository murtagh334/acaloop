package com.acaloop.acaloop;

import android.media.AudioFormat;
import android.media.AudioManager;
import android.os.Bundle;
import android.os.Environment;
import android.support.v7.app.ActionBarActivity;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;

import java.util.InvalidPropertiesFormatException;

public class RecordActivity extends ActionBarActivity
{
    final static int STREAM = AudioManager.STREAM_MUSIC;

    final static String APP_DIR = "/Acaloop/data";
    final static String FILE_EXTENSION = ".mp4";

    final static int SAMPLE_RATE_HZ = 44100;
    final static int CHANNEL_CONFIG = AudioFormat.CHANNEL_IN_STEREO;
    final static int AUDIO_FORMAT = AudioFormat.ENCODING_PCM_16BIT;

    ObservableMediaPlayer observableMediaPlayer;
    ObservableRecorder observableRecorder;

    @Override
    protected void onCreate(Bundle savedInstanceState)
    {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_record);
        //Volume button presses are now directed to the correct audio stream
        setVolumeControlStream(STREAM);

        try
        {
            observableMediaPlayer = new ObservableMediaPlayer(this);
            observableRecorder = new ObservableRecorder();
        }
        catch (InvalidPropertiesFormatException e)
        {
            e.printStackTrace();
        }

        PlayButton playButton = (PlayButton)findViewById(R.id.play_button);
        RecordButton recordButton = (RecordButton)findViewById(R.id.record_button);

        playButton.attachMediaPlayer(observableMediaPlayer);
        recordButton.attachRecorder(observableRecorder);

        observableMediaPlayer.addObserver(playButton);
        observableMediaPlayer.addObserver(observableRecorder);

        observableRecorder.addObserver(recordButton);
        observableRecorder.addObserver(observableMediaPlayer);
    }

    /**
     * Clean-up when our application is interrupted
     */
    @Override
    protected void onStop()
    {
        super.onStop();
        cleanup();
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

    private void cleanup()
    {
        observableMediaPlayer.cleanupPlayer();
        observableRecorder.cleanupRecorder();
    }

    /**
     * Called when reset button is clicked
     */
    @SuppressWarnings("ResultOfMethodCallIgnored")
    public void onClickReset(View v)
    {
//        cleanup();
        //The function of the reset button is to delete the audio we've accumulated.
        observableMediaPlayer.deletePlaybackData();
    }

    public static String getAppDirPath()
    {
        return Environment.getExternalStorageDirectory().getAbsolutePath() + APP_DIR;
    }
}
