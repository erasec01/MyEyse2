package it.unipd.dei.sproject1819.myeyse;

import it.unipd.dei.sproject1819.myeyse.tracking.MultiBoxTracker;

public class InfoSpeech
{
    private String mMessToSpeech;
    private String mLable;
    private String speechMessage;

    public InfoSpeech(String lable, MultiBoxTracker.Direction pos)
    {
        if (pos == MultiBoxTracker.Direction.LEFT)
            speechMessage = " at eleven o\'clock";
        else if (pos == MultiBoxTracker.Direction.CENTER)
            speechMessage = " at twelve o\'clock";
        else
            speechMessage = " at one o\'clock";

        mMessToSpeech = lable + speechMessage;
    }
    public String getmMessToSpeech()
    {
        return mMessToSpeech;
    }
}
