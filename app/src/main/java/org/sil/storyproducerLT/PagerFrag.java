package org.sil.storyproducerLT;

import android.os.Bundle;
import android.support.v4.app.Fragment;
import android.support.v4.view.ViewPager;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;

/**
 * Created by Jordan Skomer on 10/22/2015.
 */
public class PagerFrag extends Fragment{
    public static final String NUM_OF_FRAG = "fragnum";
    public static final String TYPE_OF_FRAG = "fragtype";
    public static final String STORY_NAME = "storyname";
    private boolean getInitialPosition = false;
    private int previousPosition = 0;

    public static PagerFrag newInstance(int numOfFrags, int typeOfFrag, String storyName){
        PagerFrag frag = new PagerFrag();
        Bundle bundle = new Bundle();
        bundle.putInt(NUM_OF_FRAG, numOfFrags);
        bundle.putInt(TYPE_OF_FRAG, typeOfFrag);
        bundle.putString(STORY_NAME, storyName);
        frag.setArguments(bundle);
        return frag;
    }
    private ViewPager mPager;
    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container, Bundle savedInstanceState) {
        View view = inflater.inflate(R.layout.fragment_pager, container, false);
        mPager = (ViewPager)view.findViewById(R.id.pager_viewpager);
        mPager.setAdapter(new PagerAdapter(getActivity(), getChildFragmentManager(), getArguments().getInt(NUM_OF_FRAG), getArguments().getInt(TYPE_OF_FRAG), getArguments().getString(STORY_NAME)));
        mPager.setPageTransformer(true, new PagerAnimation());
        mPager.addOnPageChangeListener(new ViewPager.OnPageChangeListener() {
            public void onPageScrollStateChanged(int state) {}
            public void onPageScrolled(int position, float positionOffset, int positionOffsetPixels) {
                if(!getInitialPosition){
                    getInitialPosition = true;
                    previousPosition = position;
                }
            }
            public void onPageSelected(int position) {
                if(position != previousPosition){
                    TransFrag transFrag = ((PagerAdapter)mPager.getAdapter()).getTransFrag(previousPosition);
                    transFrag.stopNarrationRecording();
                    previousPosition = position;
                }
            }
        });
        return view;
    }

    public void changeView(int position){
        mPager.setCurrentItem(position, true);
    }

}