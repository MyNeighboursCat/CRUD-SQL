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
public final class AboutActivity extends AppCompatActivity {
    @Override
    protected void onCreate(final Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        // Make sure only the music stream volume is adjusted.
        this.setVolumeControlStream(AudioManager.STREAM_MUSIC);

        final TextDisplayBinding binding = TextDisplayBinding.inflate(getLayoutInflater());
        this.setContentView(binding.getRoot());
        this.setSupportActionBar(binding.includeCommonToolbar.toolbar1);

        final ActionBar actionBar = this.getSupportActionBar();
        if (actionBar != null) {
            actionBar.setDisplayHomeAsUpEnabled(true);
        }

        final String string1 = this.getString(R.string.app_name)
                + "\n\n" + this.getString(R.string.version) + ": "
                + this.getString(R.string.application_version_name) + "\n\n"
                + this.getString(R.string.copyright) + " "
                + this.getString(R.string.creation_year) + " "
                + this.getString(R.string.my_name) + ".  "
                + this.getString(R.string.all_rights_reserved);
        binding.textDisplayTextView.setText(string1);
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
