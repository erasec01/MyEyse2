package it.unipd.dei.sproject1819.myeyse;

import android.content.Context;
import android.content.Intent;
import android.os.Handler;
import android.speech.RecognizerIntent;
import android.speech.SpeechRecognizer;
import android.speech.tts.TextToSpeech;
import android.speech.tts.UtteranceProgressListener;

import java.util.Locale;

/**
 * Simple class, created by us, that provides a greater abstraction than the standard Android class
 * TextToSpeech which documentation can be found:
 * https://developer.android.com/reference/android/speech/tts/TextToSpeech
 */
public class TextToSpeechImp
{
    private static final String TAG = "TextToSpeechImp";

    //Value between 0 to 1
    private float speechRate;

    Handler mHandler;
    private TextToSpeech t1;


    public TextToSpeechImp(Context context,
                           final UtteranceProgressListener utteranceProgressListener)
    {
        mHandler = new Handler();
        speechRate = 1f;
        t1 = new TextToSpeech(context, new TextToSpeech.OnInitListener()
        {
            @Override
            public void onInit(int status)
            {
                if (status == TextToSpeech.ERROR)
                {

                }
                else if (status == TextToSpeech.SUCCESS)
                {
                    t1.setLanguage(Locale.UK);
                    t1.setOnUtteranceProgressListener(utteranceProgressListener);
                }

            }
        });
        t1.setSpeechRate(speechRate);

    }

    public void speechMessage(String text, String utteranceld)
    {
        t1.speak(text, TextToSpeech.QUEUE_ADD, null, utteranceld);
    }

    public void speechMessage(String text)
    {
        speechMessage(text, null);
    }

    public void setSpeechRate(float value)
    {
        t1.setSpeechRate(value);
    }

    public void resetSpeechRate()
    {
        t1.setSpeechRate(speechRate);
    }

    public void close()
    {
        if (t1 != null)
        {
            //Interrupts the current utterance (whether played or rendered to file)
            // and discards other utterances in the queue.
            t1.stop();
            //Releases the resources used by the TextToSpeech engine
            t1.shutdown();
        }
    }

    public void interruptSpeech()
    {
        if (t1 != null)
        {
            t1.stop();
        }
    }
}
