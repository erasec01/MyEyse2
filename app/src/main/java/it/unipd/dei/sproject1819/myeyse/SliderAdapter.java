package it.unipd.dei.sproject1819.myeyse;

import android.content.Context;
import android.support.annotation.NonNull;
import android.support.v4.view.PagerAdapter;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;
import java.util.List;


/**
 * SliderAdapter
 */
public class SliderAdapter extends PagerAdapter
{
    Context mContext;
    LayoutInflater mLayoutInflater;
    List<ScreenElements> mListElements;

    public SliderAdapter(Context context, List<ScreenElements> listScreen)
    {
        this.mContext = context;
        this.mListElements = listScreen;
    }

    @Override
    public int getCount()
    {
        //Return number of slides
        return mListElements.size() + 2;
    }

    @Override
    public boolean isViewFromObject(@NonNull View view, @NonNull Object o)
    {
        return view == o;
    }


    @NonNull
    @Override
    public Object instantiateItem(@NonNull ViewGroup container, int position)
    {
        if (position == 0)
        {
            mLayoutInflater = (LayoutInflater) mContext.getSystemService(mContext.LAYOUT_INFLATER_SERVICE);
            View view = mLayoutInflater.inflate(R.layout.first_slide_layout, container, false);
            container.addView(view);
            return view;
        }
        else if (position <= mListElements.size())
        {
            mLayoutInflater = (LayoutInflater) mContext.getSystemService(mContext.LAYOUT_INFLATER_SERVICE);
            View view = mLayoutInflater.inflate(R.layout.slide_layout, container, false);

            ImageView slideImageView = view.findViewById(R.id.slide_img);
            TextView slideTitle = view.findViewById(R.id.slide_title);
            TextView slideDescription = view.findViewById(R.id.slide_desc);

            slideImageView.setImageResource(mListElements.get(position - 1).getScreenImg());
            slideTitle.setText(mListElements.get(position - 1).getTitle());
            slideDescription.setText(mListElements.get(position - 1).getDescription());
            container.addView(view);
            return view;
        }
        else
        {
            mLayoutInflater = (LayoutInflater) mContext.getSystemService(mContext.LAYOUT_INFLATER_SERVICE);
            View view = mLayoutInflater.inflate(R.layout.last_slide_layout, container, false);
            container.addView(view);
            return view;
        }
    }

    @Override
    public void destroyItem(@NonNull ViewGroup container, int position, @NonNull Object object)
    {
        //Tutti i layer sono linear layout, non devo controllare la posizione
        container.removeView((LinearLayout)object);
    }
}
