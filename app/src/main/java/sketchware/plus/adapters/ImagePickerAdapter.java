package sketchware.plus.adapters;

import android.content.Context;
import android.graphics.Picture;
import android.graphics.drawable.PictureDrawable;
import android.net.Uri;
import android.view.LayoutInflater;
import android.view.ViewGroup;
import android.widget.ImageView;

import androidx.annotation.NonNull;
import androidx.core.content.FileProvider;
import androidx.recyclerview.widget.RecyclerView;

import com.besome.sketch.beans.ProjectResourceBean;
import com.bobur.androidsvg.SVG;
import com.bumptech.glide.Glide;

import java.io.File;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import a.a.a.jC;
import a.a.a.kC;
import mod.bobur.XmlToSvgConverter;
import sketchware.plus.R;
import sketchware.plus.databinding.ImagePickerItemBinding;
import sketchware.plus.utility.FilePathUtil;
import sketchware.plus.utility.FileUtil;
import sketchware.plus.utility.SvgUtils;

public class ImagePickerAdapter extends RecyclerView.Adapter<ImagePickerAdapter.ViewHolder> {

    public interface OnImageSelectedListener {
        void onImageSelected(String imageName);
    }

    private static final ExecutorService EXECUTOR = Executors.newFixedThreadPool(4);

    private final Context context;
    private final String scId;
    private final List<String> originalImages;
    private final List<String> filteredImages;
    private final OnImageSelectedListener listener;
    private String selectedImage;

    public ImagePickerAdapter(Context context, String scId, List<String> images, String selectedImage, OnImageSelectedListener listener) {
        this.context = context;
        this.scId = scId;
        this.originalImages = new ArrayList<>(images);
        this.filteredImages = new ArrayList<>(images);
        this.selectedImage = selectedImage;
        this.listener = listener;
    }

    @NonNull
    @Override
    public ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        ImagePickerItemBinding binding = ImagePickerItemBinding.inflate(
                LayoutInflater.from(parent.getContext()), parent, false);
        return new ViewHolder(binding);
    }

    @Override
    public void onBindViewHolder(@NonNull ViewHolder holder, int position) {
        String imageName = filteredImages.get(position);

        holder.binding.textView.setText(imageName);
        holder.binding.radioButton.setChecked(imageName.equals(selectedImage));

        loadImageIntoView(context, scId, imageName, holder.binding.imgIcon);

        holder.binding.transparentOverlay.setOnClickListener(v -> {
            if (!imageName.equals(selectedImage)) {
                selectedImage = imageName;
                if (listener != null) {
                    listener.onImageSelected(imageName);
                }
                notifyDataSetChanged();
            }
        });
    }

    @Override
    public int getItemCount() {
        return filteredImages.size();
    }

    public void filter(String query) {
        filteredImages.clear();
        if (query == null || query.trim().isEmpty()) {
            filteredImages.addAll(originalImages);
        } else {
            String lowerQuery = query.trim().toLowerCase();
            for (String img : originalImages) {
                if (img.toLowerCase().contains(lowerQuery)) {
                    filteredImages.add(img);
                }
            }
        }
        notifyDataSetChanged();
    }

    public String getSelectedImage() {
        return selectedImage;
    }

    public static void loadImageIntoView(Context context, String scId, String imageName, ImageView imageView) {
        imageView.setTag(imageName);
        try {
            Glide.with(context).clear(imageView);
        } catch (Exception ignored) {}
        imageView.setImageDrawable(null);
        imageView.setBackgroundResource(R.drawable.bg_outline);

        if (imageName == null || imageName.isEmpty() || "NONE".equalsIgnoreCase(imageName)) {
            return;
        }

        if ("default_image".equals(imageName)) {
            int resId = context.getResources().getIdentifier(imageName, "drawable", context.getPackageName());
            if (resId != 0) {
                imageView.setImageResource(resId);
            } else {
                imageView.setImageResource(R.drawable.default_image);
            }
            return;
        }

        if (scId != null && jC.d(scId) != null) {
            if (jC.d(scId).h(imageName) == ProjectResourceBean.PROJECT_RES_TYPE_RESOURCE) {
                int resId = context.getResources().getIdentifier(imageName, "drawable", context.getPackageName());
                if (resId != 0) {
                    imageView.setImageResource(resId);
                    return;
                }
            }

            String filePath = jC.d(scId).f(imageName);
            if (filePath != null) {
                File file = new File(filePath);
                if (file.exists()) {
                    if (filePath.endsWith(".xml")) {
                        SvgUtils svgUtils = new SvgUtils(context);
                        FilePathUtil fpu = new FilePathUtil();
                        svgUtils.loadImage(imageView, fpu.getSvgFullPath(scId, imageName));
                    } else {
                        Uri fromFile = FileProvider.getUriForFile(context, context.getPackageName() + ".provider", file);
                        Glide.with(context)
                                .load(fromFile)
                                .signature(kC.n())
                                .error(R.drawable.ic_remove_grey600_24dp)
                                .into(imageView);
                    }
                    return;
                }
            }
        }

        if (scId != null) {
            try {
                XmlToSvgConverter converter = new XmlToSvgConverter();
                String vectorPath = converter.getVectorFullPath(scId, imageName);
                File vectorFile = new File(vectorPath);
                if (vectorFile.exists()) {
                    EXECUTOR.execute(() -> {
                        try {
                            String svgStr = converter.xml2svg(FileUtil.readFile(vectorPath));
                            SVG svg = SVG.getFromString(svgStr);
                            Picture picture = svg.renderToPicture();
                            PictureDrawable drawable = new PictureDrawable(picture);
                            imageView.post(() -> {
                                if (imageName.equals(imageView.getTag())) {
                                    imageView.setImageDrawable(drawable);
                                }
                            });
                        } catch (Exception e) {
                            imageView.post(() -> {
                                if (imageName.equals(imageView.getTag())) {
                                    imageView.setImageResource(R.drawable.ic_remove_grey600_24dp);
                                }
                            });
                        }
                    });
                    return;
                }
            } catch (Exception ignored) {}
        }

        int fallbackId = context.getResources().getIdentifier(imageName, "drawable", context.getPackageName());
        if (fallbackId != 0) {
            imageView.setImageResource(fallbackId);
        } else {
            imageView.setImageResource(R.drawable.ic_remove_grey600_24dp);
        }
    }

    public static class ViewHolder extends RecyclerView.ViewHolder {
        public final ImagePickerItemBinding binding;

        public ViewHolder(@NonNull ImagePickerItemBinding binding) {
            super(binding.getRoot());
            this.binding = binding;
        }
    }
}
