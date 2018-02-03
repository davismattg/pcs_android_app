package com.prestoncinema.app.ui;

import android.databinding.BindingAdapter;
import android.view.View;

/**
 * Created by MATT on 1/31/2018.
 */

public class BindingAdapters {
    @BindingAdapter("visibleGone")
    public static void showHide(View view, boolean show) {
        view.setVisibility(show ? View.VISIBLE : View.GONE);
    }
}
