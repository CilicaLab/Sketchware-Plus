package com.besome.sketch.tools;

import androidx.annotation.NonNull;
import androidx.fragment.app.Fragment;
import androidx.fragment.app.FragmentActivity;
import androidx.viewpager2.adapter.FragmentStateAdapter;

public class CompileLogPagerAdapter extends FragmentStateAdapter {

    public CompileLogPagerAdapter(@NonNull FragmentActivity activity) {
        super(activity);
    }

    @NonNull
    @Override
    public Fragment createFragment(int position) {
        return CompileLogFragment.newInstance(position);
    }

    @Override
    public int getItemCount() {
        return 4;
    }
}
