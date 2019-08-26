package it.unipd.dei.sproject1819.myeyse;

import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.ActivityInfo;
import android.support.v4.view.ViewPager;
import android.support.v7.app.AppCompatActivity;
import android.os.Bundle;
import android.text.Html;
import android.view.View;
import android.view.WindowManager;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.TextView;

import java.util.ArrayList;
import java.util.List;

public class IntroActivity extends AppCompatActivity
{
    ViewPager mSlideViewPager;
    LinearLayout mDotLayout;
    SliderAdapter sliderAdapter;
    private Button btnNext;
    private Button btnPrev;
    List<ScreenElements> mList;
    private TextView[] mDots;

    @Override
    protected void onCreate(Bundle savedInstanceState)
    {
        super.onCreate(savedInstanceState);
        getWindow().setFlags(WindowManager.LayoutParams.FLAG_FULLSCREEN,
                WindowManager.LayoutParams.FLAG_FULLSCREEN);
        //Durante l' intro non è ammesso che si giri lo schermo
        setRequestedOrientation(ActivityInfo.SCREEN_ORIENTATION_PORTRAIT);
        setContentView(R.layout.activity_intro);
        if (!isFirstTimeStartApp())
        {
            startMainActivity();
            finish();
        }
        mList = new ArrayList<>();

        mSlideViewPager = findViewById(R.id.viewPager);
        mDotLayout = (LinearLayout) findViewById(R.id.dots_layout);

        btnNext = findViewById(R.id.btn_next);
        btnPrev = findViewById(R.id.btn_prev);

        btnNext.setOnClickListener(new View.OnClickListener()
        {
            @Override
            public void onClick(View v)
            {
                int currentPage = (mSlideViewPager.getCurrentItem() + 1);
                if (currentPage == mList.size() + 2)
                    startMainActivity();
                else
                    mSlideViewPager.setCurrentItem(currentPage);
            }
        });
        btnPrev.setOnClickListener(new View.OnClickListener()
        {
            @Override
            public void onClick(View v)
            {
                int currentPage = mSlideViewPager.getCurrentItem() - 1;
                mSlideViewPager.setCurrentItem(currentPage);
            }
        });
        mList.add(new ScreenElements(R.string.title_mode_object_detection_slide,
                R.string.description_mode_object_detection_slide, R.drawable.obj));

        mList.add(new ScreenElements(R.string.title_mode_panoramic_slide,
                R.string.description_mode_panoramic_slide, R.drawable.panoramic));

        mList.add(new ScreenElements(R.string.title_panoramic_mode_slide_2,
                R.string.panoramic_mode_slide_2,
                R.drawable.panoramic_result));

        mList.add(new ScreenElements(R.string.title_panoramic_mode_slide_3,
                R.string.panoramic_mode_slide_3, R.drawable.sad));

        sliderAdapter = new SliderAdapter(this, mList);
        mSlideViewPager.setAdapter(sliderAdapter);
        addDotsStatus(0);
        mSlideViewPager.addOnPageChangeListener(viewListener);
    }

    public void addDotsStatus(int position)
    {
        mDotLayout.removeAllViews();
        mDots = new TextView[mList.size() + 2];

        for (int i = 0; i < mDots.length; i++)
        {
            mDots[i] = new TextView(this);
            mDots[i].setText(Html.fromHtml("&#8226"));
            mDots[i].setTextSize(35);
            mDots[i].setTextColor(getResources().getColor(R.color.inactive_dots));
            mDotLayout.addView(mDots[i]);
        }
        //Set current dot active
        if (mDots.length > 0)
            mDots[position].setTextColor(getResources().getColor(R.color.active_dots));

    }

    ViewPager.OnPageChangeListener viewListener = new ViewPager.OnPageChangeListener()
    {
        @Override
        public void onPageScrolled(int i, float v, int i1)
        {

        }

        @Override
        public void onPageSelected(int i)
        {
            if (i == mList.size() + 1)

                btnNext.setText("START");
            else
                btnNext.setText("Next");
            addDotsStatus(i);
        }

        @Override
        public void onPageScrollStateChanged(int i)
        {

        }
    };

    /**
     *
     * @return
     */
    private boolean isFirstTimeStartApp()
    {
        SharedPreferences ref = getApplicationContext().getSharedPreferences("IntroSlide", Context.MODE_PRIVATE);
        return ref.getBoolean("FirstTimeStartFlag", true);
    }

    private void setFirstTimeStartStatus(boolean stt)
    {
        SharedPreferences ref = getApplicationContext().getSharedPreferences("IntroSlide", Context.MODE_PRIVATE);
        SharedPreferences.Editor editor = ref.edit();
        editor.putBoolean("FirstTimeStartFlag", stt);
        editor.commit();

    }

    private void startMainActivity()
    {
        setFirstTimeStartStatus(false);
        startActivity(new Intent(IntroActivity.this, DetectorActivity.class));
        finish();
    }
}
