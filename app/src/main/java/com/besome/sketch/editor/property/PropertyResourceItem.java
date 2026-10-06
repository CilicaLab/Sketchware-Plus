package com.besome.sketch.editor.property;

import android.content.Context;
import android.graphics.Color;
import android.net.Uri;
import android.text.Editable;
import android.text.TextWatcher;
import android.view.Gravity;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.RadioButton;
import android.widget.RadioGroup;
import android.widget.RelativeLayout;
import android.widget.ScrollView;
import android.widget.TextView;

import androidx.core.content.FileProvider;
import androidx.recyclerview.widget.LinearLayoutManager;

import com.besome.sketch.beans.ProjectResourceBean;
import com.besome.sketch.design.DesignActivity;
import com.bumptech.glide.Glide;
import com.google.android.material.dialog.MaterialAlertDialogBuilder;

import java.io.File;
import java.util.ArrayList;
import java.util.concurrent.atomic.AtomicReference;

import a.a.a.Kw;
import a.a.a.jC;
import a.a.a.kC;
import a.a.a.mB;
import a.a.a.wB;
import mod.bobur.XmlToSvgConverter;
import mod.hey.studios.util.Helper;
import sketchware.plus.R;
import sketchware.plus.adapters.ImagePickerAdapter;
import sketchware.plus.databinding.SearchWithRecyclerViewBinding;
import sketchware.plus.utility.FilePathUtil;
import sketchware.plus.utility.SvgUtils;

public class PropertyResourceItem extends RelativeLayout implements View.OnClickListener {
    private final SvgUtils svgUtils;
    private final FilePathUtil fpu = new FilePathUtil();
    public String a;
    public String b;
    public String c;
    public boolean d;
    public TextView e;
    public TextView f;
    public ImageView g;
    public ImageView h;
    public RadioGroup i;
    public LinearLayout j;
    public View k;
    public View l;
    public int m;
    public Kw n;

    public PropertyResourceItem(Context context, boolean z, String str, boolean z2) {
        super(context);
        d = false;
        a = str;
        svgUtils = new SvgUtils(context);
        svgUtils.initImageLoader();
        a(context, z, z2);
    }

    public String getKey() {
        return b;
    }

    public void setKey(String str) {
        b = str;
        int identifier = getResources().getIdentifier(str, "string", getContext().getPackageName());
        if (identifier > 0) {
            e.setText(getResources().getString(identifier));
            if ("property_image".equals(b)) {
                m = R.drawable.ic_mtrl_image;
            } else if ("property_background_resource".equals(b)) {
                m = R.drawable.ic_mtrl_background_dots;
            }
            if (l.getVisibility() == VISIBLE) {
                ((ImageView) findViewById(R.id.img_icon)).setImageResource(m);
                ((TextView) findViewById(R.id.tv_title)).setText(getContext().getString(identifier));
            } else {
                h.setImageResource(m);
            }
        }
    }

    public String getValue() {
        return c;
    }

    public void setValue(String str) {

        Uri fromFile;
        if (str != null && !str.equalsIgnoreCase("NONE")) {
            c = str;
            f.setText(str);
            if (jC.d(a).h(str) == ProjectResourceBean.PROJECT_RES_TYPE_RESOURCE) {
                g.setImageResource(getContext().getResources().getIdentifier(str, "drawable", getContext().getPackageName()));
                return;
            } else if (str.equals("default_image")) {
                g.setImageResource(getContext().getResources().getIdentifier(str, "drawable", getContext().getPackageName()));
                return;
            } else {

                File file = new File(jC.d(a).f(str));
                if (file.exists()) {
                    Context context = getContext();
                    fromFile = FileProvider.getUriForFile(context, getContext().getPackageName() + ".provider", file);
                    if (file.getAbsolutePath().endsWith(".xml")) {
                        svgUtils.loadImage(g, fpu.getSvgFullPath(a, str));
                        return;
                    }
                    Glide.with(getContext()).load(fromFile).signature(kC.n()).error(R.drawable.ic_remove_grey600_24dp).into(g);
                    return;
                }
                g.setImageResource(getContext().getResources().getIdentifier(str, "drawable", getContext().getPackageName()));
                return;
            }
        }
        c = str;
        f.setText("NONE");
        g.setImageDrawable(null);
        g.setBackgroundColor(Color.WHITE);
    }

    @Override
    public void onClick(View view) {
        if (mB.a()) {
            return;
        }
        a();
    }

    public void setOnPropertyValueChangeListener(Kw kw) {
        n = kw;
    }

    public void setOrientationItem(int i) {
        if (i == 0) {
            k.setVisibility(GONE);
            l.setVisibility(VISIBLE);
            k.setOnClickListener(null);
            l.setOnClickListener(this);
        } else {
            k.setVisibility(VISIBLE);
            l.setVisibility(GONE);
            k.setOnClickListener(this);
            l.setOnClickListener(null);
        }
    }

    public final void a(Context context, boolean z, boolean z2) {
        wB.a(context, this, R.layout.property_resource_item);
        e = findViewById(R.id.tv_name);
        f = findViewById(R.id.tv_value);
        g = findViewById(R.id.view_image);
        h = findViewById(R.id.img_left_icon);
        k = findViewById(R.id.property_item);
        l = findViewById(R.id.property_menu_item);
        d = z2;
//        if (z) {
//            l.setOnClickListener(this);
//            l.setSoundEffectsEnabled(true);
//        }
    }

    public final void a() {
        MaterialAlertDialogBuilder dialogBuilder = new MaterialAlertDialogBuilder(getContext());
        dialogBuilder.setTitle(Helper.getText(e));
        dialogBuilder.setIcon(m);

        SearchWithRecyclerViewBinding binding = SearchWithRecyclerViewBinding.inflate(
                LayoutInflater.from(getContext()));

        ArrayList<String> images = jC.d(a).m();
        ArrayList<String> vectors = new XmlToSvgConverter().getVectorDrawables(DesignActivity.sc_id);
        images.addAll(vectors);
        images.add(0, d ? "default_image" : "NONE");

        AtomicReference<String> selectedImage = new AtomicReference<>(c != null ? c : (d ? "default_image" : "NONE"));

        ImagePickerAdapter adapter = new ImagePickerAdapter(
                getContext(), a, images, selectedImage.get(), selectedImage::set);

        binding.recyclerView.setLayoutManager(new LinearLayoutManager(getContext()));
        binding.recyclerView.setAdapter(adapter);

        binding.searchInput.addTextChangedListener(new TextWatcher() {
            @Override
            public void beforeTextChanged(CharSequence s, int start, int count, int after) {}

            @Override
            public void onTextChanged(CharSequence s, int start, int before, int count) {}

            @Override
            public void afterTextChanged(Editable s) {
                adapter.filter(s.toString());
            }
        });

        int initialPos = images.indexOf(selectedImage.get());
        if (initialPos >= 0) {
            binding.recyclerView.scrollToPosition(initialPos);
        }

        dialogBuilder.setView(binding.getRoot());
        dialogBuilder.setPositiveButton(R.string.common_word_select, (dialog, which) -> {
            String newSelection = selectedImage.get();
            if (newSelection != null) {
                setValue(newSelection);
                if (n != null) {
                    n.a(b, c);
                }
            }
            dialog.dismiss();
        });
        dialogBuilder.setNegativeButton(R.string.common_word_cancel, null);
        dialogBuilder.show();
    }
}
