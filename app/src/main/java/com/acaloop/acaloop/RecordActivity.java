package com.acaloop.acaloop;

import android.media.AudioManager;
import android.os.Bundle;
import android.os.Environment;
import android.support.v7.app.ActionBarActivity;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;

import java.util.Observable;
import java.util.Observer;

public class RecordActivity extends ActionBarActivity implements Observer
{
    final static int STREAM = AudioManager.STREAM_MUSIC;

    final static String APP_DIR = "/Acaloop/data";
    final static String FILE_EXTENSION = ".mp4";

    ObservableMediaPlayer observableMediaPlayer;
    ObservableRecorder observableRecorder;

    @Override
    protected void onCreate(Bundle savedInstanceState)
    {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_record);
        //Volume button presses are now directed to the correct audio stream
        setVolumeControlStream(STREAM);

        observableMediaPlayer = new ObservableMediaPlayer(this);
        observableRecorder = new ObservableRecorder();

        PlayButton playButton = (PlayButton)findViewById(R.id.play_button);
        RecordButton recordButton = (RecordButton)findViewById(R.id.record_button);

        playButton.attachMediaPlayer(observableMediaPlayer);
        recordButton.attachRecorder(observableRecorder);

        observableMediaPlayer.addObserver(playButton);
        observableMediaPlayer.addObserver(observableRecorder);

        observableRecorder.addObserver(recordButton);
        observableRecorder.addObserver(observableMediaPlayer);

        //So we know when it stops recording to overwrite the old playback file
        observableRecorder.addObserver(this);
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

    private void preparePlaybackForNextRecord()
    {
        //After we're done recording, we can overwrite the old playback file with the new recorded file.
        //noinspection ResultOfMethodCallIgnored
        observableRecorder.getRecordedFile().renameTo(observableMediaPlayer.getPlaybackFile());
    }

    /**
     * Called when reset button is clicked
     */
    @SuppressWarnings("ResultOfMethodCallIgnored")
    public void onClickReset(View v)
    {
        cleanup();

        //The function of the reset button is to delete the audio we've accumulated.
        observableMediaPlayer.getPlaybackFile().delete();
        observableRecorder.getRecordedFile().delete();
    }

    public static String getAppDirPath()
    {
        return Environment.getExternalStorageDirectory().getAbsolutePath() + APP_DIR;
    }
    /**
     * @param filename The name of the music file e.g. "audio"
     * @return The name of the path to the music file e.g. "/storage/0/.../Acaloop/audio.mp4
     */
    public static String getPathForAudioFile(String filename)
    {
        return getAppDirPath() + "/" + filename + FILE_EXTENSION;
    }

    /**
     * @param observable The ObservableRecorder we're watching. Must prepare playback for next
     *                   time on completion.
     * @param data Not used.
     */
    @Override
    public void update(Observable observable, Object data)
    {
        ObservableRecorder observableRecorder = (ObservableRecorder)observable;
        if(!observableRecorder.isRecording())
        {
            preparePlaybackForNextRecord();
        }
    }
}
