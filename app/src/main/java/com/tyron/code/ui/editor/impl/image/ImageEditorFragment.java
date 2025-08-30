package com.tyron.code.ui.editor.impl.image;

import android.net.Uri;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;

import com.bumptech.glide.Glide;
import com.tyron.code.R;

import java.io.File;

/**
 * ImageEditorFragment supporting both local File and SAF Uri images.
 */
public class ImageEditorFragment extends Fragment {

    public static ImageEditorFragment newInstance(File file) {
        ImageEditorFragment fragment = new ImageEditorFragment();
        Bundle bundle = new Bundle();
        bundle.putString("file", file.getAbsolutePath());
        fragment.setArguments(bundle);
        return fragment;
    }

    public static ImageEditorFragment newInstance(Uri uri) {
        ImageEditorFragment fragment = new ImageEditorFragment();
        Bundle bundle = new Bundle();
        bundle.putParcelable("uri", uri);
        fragment.setArguments(bundle);
        return fragment;
    }

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container,
                             @Nullable Bundle savedInstanceState) {
        ImageView imageView = new ImageView(requireContext());
        Bundle args = getArguments();
        if (args != null) {
            if (args.containsKey("file")) {
                String file = args.getString("file", "");
                File imageFile = new File(file);
                if (imageFile.exists()) {
                    Glide.with(imageView)
                            .load(imageFile)
                            .placeholder(R.drawable.ic_image_placeholder)
                            .error(R.drawable.ic_image_error)
                            .into(imageView);
                }
            } else if (args.containsKey("uri")) {
                Uri uri = args.getParcelable("uri");
                if (uri != null) {
                    Glide.with(imageView)
                            .load(uri)
                            .placeholder(R.drawable.ic_image_placeholder)
                            .error(R.drawable.ic_image_error)
                            .into(imageView);
                }
            }
        }
        imageView.setLayoutParams(new ViewGroup.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));
        imageView.setScaleType(ImageView.ScaleType.FIT_CENTER);
        return imageView;
    }
}
