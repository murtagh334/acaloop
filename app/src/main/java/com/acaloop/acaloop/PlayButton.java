package com.acaloop.acaloop;

import android.content.Context;
import android.util.AttributeSet;
import android.view.View;
import android.widget.Button;

import java.util.Observable;
import java.util.Observer;

/**
 * A Button that controls a media player, and observes it to change its text appropriately.
 */
public class PlayButton extends Button implements Observer
{
    public PlayButton(Context context, AttributeSet attrs)
    {
        super(context, attrs);
    }

    /**
     * Tell us which media player we control with this button
     * @param observableMediaPlayer The media player we affect with our button presses
     */
    public void attachMediaPlayer(final ObservableMediaPlayer observableMediaPlayer)
    {
        setOnClickListener(new OnClickListener()
        {
            @Override
            public void onClick(View v)
            {
                if(observableMediaPlayer.isPlaying())
                {
                    observableMediaPlayer.stopPlayback();
                }
                else
                {
                    observableMediaPlayer.startPlayback();
                }
            }
        });
    }

    /**
     * @param observable The media player we are watching for change in state
     * @param data Not used
     */
    @Override
    public void update(Observable observable, Object data)
    {
        final ObservableMediaPlayer observableMediaPlayer = (ObservableMediaPlayer) observable;
        post(new Runnable()
        {
            @Override
            public void run()
            {
                //Set the text according to state of the observable
                setText(observableMediaPlayer.isPlaying() ? R.string.stop_playing : R.string.play);
            }
        });
    }
}
