package com.prestoncinema.app.db;

import android.databinding.DataBindingUtil;
import android.support.annotation.Nullable;
import android.support.v7.widget.RecyclerView;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;

import com.prestoncinema.app.R;

import com.prestoncinema.app.databinding.LensListBinding;
import com.prestoncinema.app.db.entity.LensListEntity;
import com.prestoncinema.app.model.LensList;

import java.util.List;

/**
 * Created by MATT on 1/25/2018.
 */

public class LensListAdapter extends RecyclerView.Adapter<LensListAdapter.LensListViewHolder> {

    List<? extends LensListEntity> lensLists;

    @Nullable
    private final LensListClickCallback lensListClickCallback;

    public LensListAdapter(@Nullable LensListClickCallback cb) {
        lensListClickCallback = cb;
    }

    public void setLensLists(final List<? extends LensListEntity> list) {
        if (lensLists == null) {
            lensLists = list;
            notifyDataSetChanged();
        }
        else {
            lensLists = list;
        }
    }

    @Override
    public LensListViewHolder onCreateViewHolder(ViewGroup parent, int viewType) {
        LensListBinding binding = DataBindingUtil.inflate(LayoutInflater.from(parent.getContext()), R.layout.lens_list, parent, false);
        binding.setCallback(lensListClickCallback);
        return new LensListViewHolder(binding);
    }

    @Override
    public void onBindViewHolder(LensListViewHolder holder, int position) {
        holder.binding.setLensList(lensLists.get(position));
        holder.binding.executePendingBindings();
    }

    @Override
    public int getItemCount() {
        return lensLists == null ? 0 : lensLists.size();
    }

    static class LensListViewHolder extends RecyclerView.ViewHolder {
        private LensListBinding binding;

        public LensListViewHolder(LensListBinding binding) {
            super(binding.getRoot());
            this.binding = binding;
        }
    }
}