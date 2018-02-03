package com.prestoncinema.app.ui;

import android.arch.lifecycle.Observer;
import android.arch.lifecycle.ViewModelProviders;
import android.content.Intent;
import android.databinding.DataBindingUtil;
import android.os.Bundle;
import android.support.annotation.Nullable;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;

import com.prestoncinema.app.ManageLensesActivity;
import com.prestoncinema.app.R;
import com.prestoncinema.app.databinding.LensListFragmentBinding;
import com.prestoncinema.app.db.LensListAdapter;
import com.prestoncinema.app.db.LensListClickCallback;
import com.prestoncinema.app.db.entity.LensListEntity;
import com.prestoncinema.app.model.LensList;
import com.prestoncinema.app.viewmodel.LensFileListViewModel;

import java.util.List;

import timber.log.Timber;

/**
 * Created by MATT on 1/31/2018.
 */

public class LensListFragment extends android.support.v4.app.Fragment {
    public static final String TAG = "LensListFragment";

    private LensListAdapter adapter;

    private LensListFragmentBinding binding;

    @Nullable
    @Override
    public View onCreateView(LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {
        binding = DataBindingUtil.inflate(inflater, R.layout.lens_list_fragment, container, false);

        adapter = new LensListAdapter(lensListClickCallback);
        binding.LensFilesRecyclerView.setAdapter(adapter);

        return binding.getRoot();
    }

    @Override
    public void onActivityCreated(@Nullable Bundle savedInstanceState) {
        super.onActivityCreated(savedInstanceState);
        final LensFileListViewModel viewModel = ViewModelProviders.of(this).get(LensFileListViewModel.class);

        subscribeUi(viewModel);
    }

    private void subscribeUi(LensFileListViewModel viewModel) {
        // Update the list when the data changes
        viewModel.getLensLists().observe(this, new Observer<List<LensListEntity>>() {
            @Override
            public void onChanged(@Nullable List<LensListEntity> lensListEntities) {
                if (lensListEntities != null) {
                    binding.setIsLoading(false);
                    adapter.setLensLists(lensListEntities);
                }
                else {
                    binding.setIsLoading(true);
                }

                binding.executePendingBindings();
            }
        });
    }

    private final LensListClickCallback lensListClickCallback = new LensListClickCallback() {
        @Override
        public void onClick(LensList list) {
            Timber.d("lens file onClickCallback entered");
            String listName = list.getName();
            Timber.d("file clicked: " + listName);
            Intent intent = new Intent(getContext(), ManageLensesActivity.class);
            intent.putExtra("lensFile", listName);
            startActivity(intent);
        }
    };
}
