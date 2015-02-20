package com.acaloop.acaloop;

import android.content.Context;
import android.util.AttributeSet;
import android.view.View;
import android.widget.Button;

import java.util.Observable;
import java.util.Observer;

/**
 * Record button which observes the recorder's state
 */
public class RecordButton extends Button implements Observer
{
    public RecordButton(Context context, AttributeSet attrs)
    {
        super(context,attrs);
    }

    public void attachRecorder(final ObservableRecorder observableRecorder)
    {
        setOnClickListener(new OnClickListener()
        {
            @Override
            public void onClick(View v)
            {
                if(!observableRecorder.isRecording())
                {
                    observableRecorder.startRecording();
                }
                else
                {
                    observableRecorder.stopRecording();
                }
            }
        });
    }
    /**
     * Set our text based on the observed recorder's state.
     * @param observable The recorder that we are watching
     * @param data Not used
     */
    @Override
    public void update(Observable observable, Object data)
    {
        final ObservableRecorder observableRecorder = (ObservableRecorder)observable;

        //Need to set text on the UI thread.
        post(new Runnable()
        {
            @Override
            public void run()
            {
                setText(observableRecorder.isRecording() ? R.string.stop_recording : R.string.record);
            }
        });
    }
}
