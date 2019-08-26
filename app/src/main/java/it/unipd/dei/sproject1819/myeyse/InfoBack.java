package it.unipd.dei.sproject1819.myeyse;

public class InfoBack
{
    private int mDegrees;
    private String mOrientation;

    public InfoBack(int degrees,String orientation)
    {
        mDegrees = degrees;
        mOrientation = orientation;
    }

    public int getDegrees()
    {
        return mDegrees;
    }

    public String getOrientation()
    {
        return mOrientation;
    }
}
