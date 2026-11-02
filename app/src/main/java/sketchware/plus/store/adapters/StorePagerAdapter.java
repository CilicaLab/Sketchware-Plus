package sketchware.plus.store.adapters;

import androidx.annotation.NonNull;
import androidx.fragment.app.Fragment;
import androidx.fragment.app.FragmentActivity;
import androidx.viewpager2.adapter.FragmentStateAdapter;

import java.util.HashMap;
import java.util.Map;

import sketchware.plus.store.fragments.StorePageFragment;
import sketchware.plus.store.models.StoreItem;

public class StorePagerAdapter extends FragmentStateAdapter {

    private final Map<Integer, StorePageFragment> fragments = new HashMap<>();

    public StorePagerAdapter(@NonNull FragmentActivity activity) {
        super(activity);
    }

    public StorePagerAdapter(@NonNull Fragment fragment) {
        super(fragment);
    }

    @NonNull
    @Override
    public Fragment createFragment(int position) {
        StorePageFragment fragment;
        switch (position) {
            case 1:
                fragment = StorePageFragment.newInstance(StoreItem.TYPE_BLOCK);
                break;
            case 2:
                fragment = StorePageFragment.newInstance(StoreItem.TYPE_COMPONENT);
                break;
            case 0:
            default:
                fragment = StorePageFragment.newInstance("ALL");
                break;
        }
        fragments.put(position, fragment);
        return fragment;
    }

    @Override
    public int getItemCount() {
        return 3;
    }

    public Map<Integer, StorePageFragment> getFragments() {
        return fragments;
    }
}
