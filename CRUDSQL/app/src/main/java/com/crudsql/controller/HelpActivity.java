/*
 * Copyright (c) 2023 Colin Walters.  All rights reserved.
 */
package com.crudsql.controller;

import android.media.AudioManager;
import android.os.Bundle;
import android.view.MenuItem;

import androidx.appcompat.app.ActionBar;
import androidx.appcompat.app.AppCompatActivity;

import com.crudsql.R;
import com.crudsql.databinding.TextDisplayBinding;

/**
 * @author Colin Walters
 * @version 1.0, 21/04/2023
 */
public final class HelpActivity extends AppCompatActivity {
    @Override
    protected void onCreate(final Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        // Make sure only the music stream volume is adjusted
        this.setVolumeControlStream(AudioManager.STREAM_MUSIC);

        final TextDisplayBinding binding = TextDisplayBinding.inflate(getLayoutInflater());
        this.setContentView(binding.getRoot());
        this.setSupportActionBar(binding.includeCommonToolbar.toolbar1);

        final ActionBar actionBar = this.getSupportActionBar();
        if (actionBar != null) {
            actionBar.setDisplayHomeAsUpEnabled(true);
        }

        binding.textDisplayTextView.setText(R.string.help_text);
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        // Respond to the action bar's Up/Home button.
        if (item.getItemId() == android.R.id.home) {
            // Return to existing instance of the calling activity rather than create a new one.
            // This keeps the existing state of the calling activity.
            this.finish();
            return true;
        }

        return super.onOptionsItemSelected(item);
    }
}
