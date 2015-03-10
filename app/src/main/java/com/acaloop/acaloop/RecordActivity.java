package com.acaloop.acaloop;

import android.media.AudioFormat;
import android.media.AudioManager;
import android.os.Bundle;
import android.support.v7.app.ActionBarActivity;
import android.util.Log;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.widget.Button;

import java.util.InvalidPropertiesFormatException;
import java.util.Observable;
import java.util.Observer;
import java.util.Vector;

/**
 * The main activity
 */
public class RecordActivity extends ActionBarActivity implements Observer
{
    final static int STREAM = AudioManager.STREAM_MUSIC;

//    final static String APP_DIR = "/Acaloop/data";
//    final static String FILE_EXTENSION = ".mp4";

    final static int SAMPLE_RATE_HZ = 44100;
    final static int AUDIO_FORMAT = AudioFormat.ENCODING_PCM_16BIT;

    ObservableMediaPlayer observableMediaPlayer;
    ObservableRecorder observableRecorder;

    PlayButton playButton;
    RecordButton recordButton;
    Button resetButton;
    Button latencyTestButton;

    Vector<Button> buttons;

    final static String LOG_TAG = RecordActivity.class.getSimpleName();

    /**
     * Upon creation of the app (See activity lifecycle for details)
     * @param savedInstanceState Some saved data to be carried over if any.
     */
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

        playButton = (PlayButton)findViewById(R.id.play_button);
        recordButton = (RecordButton)findViewById(R.id.record_button);
        resetButton = (Button)findViewById(R.id.reset_button);
        latencyTestButton = (Button)findViewById(R.id.latency_test_button);

        buttons = new Vector<>();

        buttons.add(playButton);
        buttons.add(recordButton);
        buttons.add(resetButton);
        buttons.add(latencyTestButton);

        playButton.attachMediaPlayer(observableMediaPlayer);
        recordButton.attachRecorder(observableRecorder);

        observableMediaPlayer.addObserver(playButton);

        observableRecorder.addObserver(observableMediaPlayer);
        observableRecorder.addObserver(recordButton);

    }

    /**
     * Clean-up when our application is interrupted
     */
    @Override
    protected void onStop()
    {
        super.onStop();
        //cleanup();
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

//    private void cleanup()
//    {
//        observableMediaPlayer.cleanupPlayer();
//        observableRecorder.cleanupRecorder();
//    }

    /**
     * Called when reset button is clicked
     * @param v The reset button
     */
    @SuppressWarnings("ResultOfMethodCallIgnored")
    public void onClickReset(View v)
    {
        new Thread( new Runnable()
        {
            @Override
            public void run()
            {
                //        cleanup();
                observableMediaPlayer.stopPlayback();
                observableRecorder.stopRecording();
                //The function of the reset button is to delete the audio we've accumulated.
                observableMediaPlayer.deletePlaybackData();
            }
        }).start();
    }

    /**
     * Called when latency test button is clicked.
     * @param v The latency test button
     */
    public void onClickLatencyTest(View v)
    {
        final RecordActivity toObserve = this;
        new Thread(new Runnable()
        {
            @Override
            public void run()
            {
                setButtonsEnabled(false);
                //don't block UI thread.
                observableMediaPlayer.setupLatencyTest();
                observableRecorder.addObserver(toObserve);
                //Starts recording, plays the sine wave.
                observableRecorder.startRecording(true, observableMediaPlayer);
            }
        }).start();
    }

    public void setButtonsEnabled(final boolean clickable)
    {
        for(final Button b : buttons)
        {
            b.post(new Runnable()
            {
                @Override
                public void run()
                {
                    b.setEnabled(clickable);
                }
            });
        }
    }

    /**
     *
     * @param data The data we're given
     * @return The latency
     */
    public int findLatency(short[] data, int sampleRate, int frequency, int frequencyDuration, int channelCount)
    {
        int mostLikelyOffset = 0;
        double powerOfOffset = -999; //arbitrary low value

        double msInSamples = (1.0*channelCount/1000.0)*(double)sampleRate;

        //Assume we're fine with 20 ms delay and won't ever go above 220 ms delay.
        for(int checkOffset = 20*(int)msInSamples; checkOffset < Math.min(220*(int)msInSamples,data.length); checkOffset+=channelCount)
        {
            double power = calculateGoertzel(data, checkOffset, checkOffset + frequencyDuration*channelCount,
                    frequency, sampleRate, channelCount);

            if(power > powerOfOffset)
            {
                powerOfOffset = power;
                mostLikelyOffset = checkOffset;
            }
        }
        return mostLikelyOffset;
    }

    public double calculateGoertzel(short[] sample, int from, int to, double frequency, int sampleRate, int channelCount)
    {
        double skn, skn1, skn2;
        skn = skn1 = 0;

        for (int i = from; i < to; i+=channelCount)
        {
            skn2 = skn1;
            skn1 = skn;
            skn = 2 * Math.cos(2 * Math.PI * frequency / sampleRate) * skn1 - skn2 + sample[i];
        }

        double wnk = Math.exp(-2 * Math.PI * frequency / sampleRate);

        return 20* Math.log10(Math.abs((skn - wnk * skn1)));
    }

//    public static String getAppDirPath()
//    {
//        return Environment.getExternalStorageDirectory().getAbsolutePath() + APP_DIR;
//    }

    /**
     * Used for the latency test. Once the recorder finishes recording, This will be called.
     * @param observable The observed object (i.e. the recorder)
     * @param data Some data passed in (i.e. the latency recording)
     */
    @Override
    public void update(Observable observable, Object data)
    {
        //LATENCY TEST RESULTS
        if(observable instanceof ObservableRecorder)
        {
            ObservableRecorder observableRecorder = (ObservableRecorder)observable;
            if(data instanceof short[])
            {
                int delayInSamples = findLatency((short[])data,
                        observableMediaPlayer.getSampleRate(),
                        ObservableMediaPlayer.LATENCY_TEST_FREQUENCY,
                        observableMediaPlayer.getFrequencyDuration(),
                        observableMediaPlayer.getChannelCount());
                observableRecorder.setLatency(delayInSamples);
                Log.d(LOG_TAG, "DELAY (ms): " + (((double)delayInSamples / (double)observableMediaPlayer.getChannelCount()) /
                        (double)observableMediaPlayer.getSampleRate()) * 1000);

                observableRecorder.deleteObserver(this);
                observableRecorder.addObserver(observableMediaPlayer);
                observableMediaPlayer.cleanupLatencyTest();
                //Re-enable buttons again
                setButtonsEnabled(true);
            }
        }
    }
}
