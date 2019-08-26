package it.unipd.dei.sproject1819.myeyse;


/**
 * Questa classe contiene gli elementi di cui è composta una tipica
 * slide, ossia immagine,titolo,descrizio.
 */

public class ScreenElements
{
    int mTitle;
    int mDescription;
    int mScreenImg;

    public int getTitle()
    {
        return mTitle;
    }

    public int getDescription()
    {
        return mDescription;
    }

    public int getScreenImg()
    {
        return mScreenImg;
    }

    public ScreenElements(int title, int description, int screenImg)
    {
         mTitle = title;
         mDescription = description;
         mScreenImg = screenImg;
    }
}
