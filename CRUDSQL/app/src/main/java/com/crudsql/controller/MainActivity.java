/*
 * Copyright (c) 2023 Colin Walters.  All rights reserved.
 */
package com.crudsql.controller;

import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.res.Resources;
import android.database.sqlite.SQLiteDatabase;
import android.media.AudioManager;
import android.os.Bundle;
import android.text.Editable;
import android.text.InputFilter;
import android.text.Spanned;
import android.text.TextWatcher;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.Menu;
import android.view.MenuInflater;
import android.view.MenuItem;
import android.view.SubMenu;
import android.view.View;
import android.view.Window;
import android.view.WindowManager;
import android.widget.AdapterView;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.Spinner;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.ActionBar;
import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;

import com.crudsql.BasicApplication;
import com.crudsql.R;
import com.crudsql.databinding.ActivityMainBinding;
import com.crudsql.databinding.CommonDialogBinding;
import com.crudsql.databinding.ContentMainBinding;
import com.crudsql.databinding.KeywordsDialogBinding;
import com.crudsql.model.CRUDSQLDatabaseHelper;
import com.google.android.material.floatingactionbutton.FloatingActionButton;

import java.io.File;
import java.io.FilenameFilter;
import java.lang.ref.WeakReference;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Set;

// Do not replace toasts with snackbars because the dialogs hide the messages.


/**
 * @author Colin Walters
 * @version 1.0, 21/04/2023
 */
@SuppressWarnings("Convert2Lambda")
public final class MainActivity extends AppCompatActivity {
    private static final String CURRENT_DATABASE_NAME = "CURRENT_DATABASE_NAME";
    private static final String CUSTOM_INSERT = "CUSTOM_INSERT_";
    private static final String CUSTOM_INSERT_KEY_VALUE_DIVIDER = ";";
    private static final int HISTORY_MAXIMUM = 10;
    private static final String SQL_INPUT_BUNDLE = "SQL_INPUT_BUNDLE_";
    private final String TAG = this.getClass().getSimpleName();
    private boolean allowEvents = false;
    private LinearLayout sqlLinearLayout1 = null;
    private TextView databaseTextView = null;
    private volatile EditText sqlInputEditText = null;
    private TextView sqlResultTextView = null;
    private volatile CRUDSQLDatabaseHelper crudSQLDatabaseHelper1 = null;
    private volatile SQLiteDatabase sqliteDatabase1 = null;
    // there is no need to include the following in save and restore because
    // onCreate uses SharedPreferences with the settings saved after each custom insert update
    // in doAlertDialogOKButtonOnClick
    private volatile HashMap<String, String> customInsertStrings = new HashMap<>();
    private ArrayList<MenuItem> historyItems = new ArrayList<>();
    private AlertDialog alertDialog = null;
    // save and restore bundle
    private boolean showDatabaseOpenText = true;
    private ArrayList<String> historyStrings = new ArrayList<>();

    @Override
    protected void onCreate(final Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        ActivityMainBinding activityMainBinding = ActivityMainBinding.inflate(getLayoutInflater());
        this.setContentView(activityMainBinding.getRoot());
        this.setSupportActionBar(activityMainBinding.includeCommonToolbar.toolbar1);

        ContentMainBinding contentMainBinding = activityMainBinding.includeContentMain;
        this.sqlLinearLayout1 = contentMainBinding.sqlLinearLayout;
        this.databaseTextView = contentMainBinding.databaseTextView;
        this.sqlInputEditText = contentMainBinding.sqlInputEditText;
        // set text to semicolon here and not in XML because
        // it changes input position in XML to after the semicolon
        this.sqlInputEditText.setText(this.getString(R.string.semicolon));
        this.sqlResultTextView = contentMainBinding.sqlResultTextView;

        FloatingActionButton fab = activityMainBinding.fab;
        fab.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                String sqlInputString = MainActivity.this.sqlInputEditText.getText().toString();
                MainActivity.this.sqlResultTextView.setText("");

                if (MainActivity.this.crudSQLDatabaseHelper1 != null &&
                        MainActivity.this.sqliteDatabase1 != null) {
                    // Background thread.
                    BasicApplication.APP_EXECUTORS.getBackgroundThreadExecutor().execute(
                            new Runnable() {
                                @Override
                                public void run() {
                                    String sqlResultString = "";
                                    final WeakReference<MainActivity> mainActivityWeakReference =
                                            new WeakReference<>(MainActivity.this);

                                    try {
                                        // Get a reference to the activity if it is still there.
                                        final MainActivity mainActivity =
                                                mainActivityWeakReference.get();
                                        if (mainActivity == null || mainActivity.isFinishing()) {
                                            return;
                                        }

                                        sqlResultString = mainActivity.crudSQLDatabaseHelper1
                                                .executeSQLCommands(mainActivity,
                                                        mainActivity.sqliteDatabase1,
                                                        mainActivity.sqlInputEditText.getText()
                                                                .toString());
                                    } finally {
                                        final String finalSqlResultString = sqlResultString;
                                        BasicApplication.APP_EXECUTORS.getMainThreadExecutor()
                                                .execute(
                                                new Runnable() {
                                                    @Override
                                                    public void run() {
                                                        // Get a reference to the activity if it is
                                                        // still there.
                                                        final MainActivity mainActivity =
                                                                mainActivityWeakReference.get();
                                                        if (mainActivity == null ||
                                                                mainActivity.isFinishing()) {
                                                            return;
                                                        }
                                                        TextView textView = mainActivity
                                                                .sqlResultTextView;
                                                        textView.setText(String.format("%s%s",
                                                                textView.getText().toString(),
                                                                finalSqlResultString));
                                                    }
                                                });

                                    }
                                }
                            });
                } else {
                    MainActivity.this.sqlResultTextView.setText(
                            MainActivity.this.getString(R.string.create_or_select_a_database)
                    );
                }

                if (MainActivity.this.historyStrings.size() >= MainActivity.HISTORY_MAXIMUM) {
                    MainActivity.this.historyStrings.remove(
                            MainActivity.this.historyStrings.size() - 1);
                }
                MainActivity.this.historyStrings.add(0, sqlInputString);
            }
        });

        // restore custom inserts
        final SharedPreferences settings = this.getPreferences(Context.MODE_PRIVATE);
        boolean loop = true;
        for (int i = 0; loop; i++) {
            String settingsString = settings.getString(MainActivity.CUSTOM_INSERT + i,
                    "");
            if (settingsString != null) {
                loop = !settingsString.equals("");
                if (loop) {
                    String key1 = settingsString.substring(0, settingsString.indexOf(MainActivity
                            .CUSTOM_INSERT_KEY_VALUE_DIVIDER));
                    String value1 = settingsString.substring(settingsString.indexOf(MainActivity
                            .CUSTOM_INSERT_KEY_VALUE_DIVIDER) + 1);
                    this.customInsertStrings.put(key1, value1);
                }
            }
        }

        // get saved bundle values
        if (savedInstanceState != null) {
            // don't use 'counter' as variable name because it increases with each
            // line for some reason!
            int cnt = 0;

            this.showDatabaseOpenText = savedInstanceState
                    .getBoolean(MainActivity.SQL_INPUT_BUNDLE
                            + cnt++);

            this.sqlResultTextView.setText(savedInstanceState
                    .getString(MainActivity.SQL_INPUT_BUNDLE
                            + cnt++));

            this.historyStrings = savedInstanceState
                    .getStringArrayList(MainActivity.SQL_INPUT_BUNDLE
                            + cnt);
        }

        // Make sure only the music stream volume is adjusted
        this.setVolumeControlStream(AudioManager.STREAM_MUSIC);

        this.setButtonsStatus(false);
    }

    @Override
    protected void onResume() {
        super.onResume();

        final SharedPreferences settings = this
                .getPreferences(Context.MODE_PRIVATE);
        this.executeAsyncOpenSQLDatabase(settings.getString(MainActivity
                .CURRENT_DATABASE_NAME, ""));
    }

    @Override
    protected void onSaveInstanceState(final Bundle outState) {
        // sqlInputEditText already saves text itself on rotating the screen

        // don't use 'counter' as variable name as it increase with each line
        // for some reason!
        int cnt = 0;

        outState.putBoolean(
                MainActivity.SQL_INPUT_BUNDLE + cnt++,
                this.showDatabaseOpenText);

        outState.putString(
                MainActivity.SQL_INPUT_BUNDLE + cnt++,
                this.sqlResultTextView.getText().toString());

        outState.putStringArrayList(
                MainActivity.SQL_INPUT_BUNDLE + cnt,
                this.historyStrings);

        super.onSaveInstanceState(outState);
    }

    @Override
    protected void onPause() {
        this.clearDatabaseVariables();

        if (this.alertDialog != null) {
            this.alertDialog.dismiss();
            this.alertDialog = null;
        }

        super.onPause();
    }

    @Override
    protected void onDestroy() {
        this.releaseResources();

        super.onDestroy();
    }

    @Override
    public void onBackPressed() {
        if (this.allowEvents) {
            super.onBackPressed();
        }
    }

    @Override
    public boolean onCreateOptionsMenu(final Menu menu) {
        // action bar
        // for < android 4.4
        // if there isn't room to display all of the action bar items
        // and the device has a menu button then the overflow item is not displayed
        // (the three dots) because these are displayed by pressing the menu button.
        // if the device hasn't got a menu button the overflow item is displayed
        // for >= android 4.4
        // the overflow button is always displayed
        // nested submenus are not allowed i.e. a submenu within a submenu

        // inflate the menu items for use in the action bar
        MenuInflater inflater = getMenuInflater();
        inflater.inflate(R.menu.crud_sql_actions, menu);
        return super.onCreateOptionsMenu(menu);
    }

    @Override
    public boolean onOptionsItemSelected(final MenuItem item) {
        if (!this.allowEvents) {
            return true;
        }

        // don't use setButtonsStatus(false) here because actionBar.hide() causes the screen
        // to be pushed up then down causing a jerking sensation.
        // clear - doesn't matter
        // OK - thread uses a boolean variable to prevent more than one set of SQL statements
        // being executed at a time
        // when a dialog is shown e.g. template, database or custom insert, the this.alertDialog
        // variable is cleared so if more that one tap creates several dialogs, the next dialog
        // removes the previous one

        boolean handled = true;
        // NOTE: No binding for menus at the present time so leave using R.id.etc.
        final int itemSelectedID = item.getItemId();
        Intent intent;

        // handle presses on the action bar items
        // action items
        if (itemSelectedID == R.id.action_clear) {
            MainActivity.this.sqlInputEditText.setText(this.getString(R.string.semicolon));
            MainActivity.this.sqlResultTextView.setText("");
        } else if (itemSelectedID == R.id.action_keywords) {
            doKeywordsAlertDialog();
            // templates
        } else if (itemSelectedID == R.id.action_select_table_rows) {
            MainActivity.this.insertSQLInputString(this.getString(R.string
                    .template_select_table_rows));
        } else if (itemSelectedID == R.id.action_full_select_rows) {
            MainActivity.this.insertSQLInputString(this.getString(R.string
                    .template_full_select_rows));
        } else if (itemSelectedID == R.id.action_select_inner_join_rows) {
            MainActivity.this.insertSQLInputString(this.getString(R.string
                    .template_select_inner_join_rows));
        } else if (itemSelectedID == R.id.action_show_tables) {
            MainActivity.this.insertSQLInputString(this.getString(R.string
                    .template_show_tables));
        } else if (itemSelectedID == R.id.action_create_row) {
            MainActivity.this.insertSQLInputString(this.getString(R.string
                    .template_create_row));
        } else if (itemSelectedID == R.id.action_update_rows) {
            MainActivity.this.insertSQLInputString(this.getString(R.string
                    .template_update_rows));
        } else if (itemSelectedID == R.id.action_delete_rows) {
            MainActivity.this.insertSQLInputString(this.getString(R.string
                    .template_delete_rows));
        } else if (itemSelectedID == R.id.action_searched_case_expression) {
            MainActivity.this.insertSQLInputString(this.getString(R.string
                    .template_searched_case_expression));
        } else if (itemSelectedID == R.id.action_simple_case_expression) {
            MainActivity.this.insertSQLInputString(this.getString(R.string
                    .template_simple_case_expression));
        } else if (itemSelectedID == R.id.action_subquery_rows) {
            MainActivity.this.insertSQLInputString(this.getString(R.string
                    .template_subquery));
        } else if (itemSelectedID == R.id.action_create_table) {
            MainActivity.this.insertSQLInputString(this.getString(R.string
                    .template_create_table));
        } else if (itemSelectedID == R.id.action_create_tables) {
            MainActivity.this.insertSQLInputString(this.getString(R.string
                    .template_create_tables));
        } else if (itemSelectedID == R.id.action_create_index) {
            MainActivity.this.insertSQLInputString(this.getString(R.string
                    .template_create_index));
        } else if (itemSelectedID == R.id.action_index_used) {
            MainActivity.this.insertSQLInputString(this.getString(R.string
                    .template_index_used));
        } else if (itemSelectedID == R.id.action_rename_table) {
            MainActivity.this.insertSQLInputString(this.getString(R.string
                    .template_rename_table));
        } else if (itemSelectedID == R.id.action_add_column) {
            MainActivity.this.insertSQLInputString(this.getString(R.string
                    .template_add_column));
        } else if (itemSelectedID == R.id.action_create_view) {
            MainActivity.this.insertSQLInputString(this.getString(R.string
                    .template_create_view));
        } else if (itemSelectedID == R.id.action_attach_detach_database) {
            MainActivity.this.insertSQLInputString(this.getString(R.string
                    .template_attach_detach_database_part_1) +
                    CRUDSQLDatabaseHelper.DATABASE_FILE_EXTENSION +
                    this.getString(R.string.template_attach_detach_database_part_2));
        } else if (itemSelectedID == R.id.action_transaction) {
            MainActivity.this.insertSQLInputString(this.getString(R.string
                    .template_transaction));
        } else if (itemSelectedID == R.id.action_trigger) {
            MainActivity.this.insertSQLInputString(this.getString(R.string
                    .template_trigger));
            // custom insert
        } else if (itemSelectedID == R.id.action_select_custom_insert) {
            this.doAlertDialog(0, 0);
        } else if (itemSelectedID == R.id.action_new_custom_insert) {
            this.doAlertDialog(0, 1);
        } else if (itemSelectedID == R.id.action_rename_custom_insert) {
            this.doAlertDialog(0, 2);
        } else if (itemSelectedID == R.id.action_delete_custom_insert) {
            this.doAlertDialog(0, 3);
            // database
        } else if (itemSelectedID == R.id.action_select_database) {
            this.doAlertDialog(1, 0);
        } else if (itemSelectedID == R.id.action_new_database) {
            this.doAlertDialog(1, 1);
        } else if (itemSelectedID == R.id.action_rename_database) {
            this.doAlertDialog(1, 2);
        } else if (itemSelectedID == R.id.action_delete_database) {
            this.doAlertDialog(1, 3);
        } else if (itemSelectedID == R.id.action_history) {
            if (item.hasSubMenu()) {
                SubMenu submenu1 = item.getSubMenu();
                submenu1.clear();
                historyItems.clear();

                if (historyStrings.isEmpty()) {
                    final Toast toast = Toast.makeText(
                            MainActivity.this.getApplicationContext(),
                            R.string.history_is_empty, Toast.LENGTH_SHORT);
                    toast.show();
                } else {
                    int i = Menu.FIRST;
                    for (String string1 : historyStrings) {
                        historyItems.add(submenu1.add(1, i, i, string1));
                        i++;
                    }
                }
            }
        } else if (itemSelectedID == R.id.action_clear_history) {
            this.historyStrings.clear();
            final Toast toast = Toast.makeText(MainActivity.this.getApplicationContext(),
                    R.string.history_cleared, Toast.LENGTH_SHORT);
            toast.show();
        } else if (itemSelectedID == R.id.action_help) {
            intent = new Intent(this, HelpActivity.class);
            this.startActivity(intent);
        } else if (itemSelectedID == R.id.action_about) {
            intent = new Intent(this, AboutActivity.class);
            this.startActivity(intent);
        } else {
            handled = false;
            // history items
            for (MenuItem menuItem1 : historyItems) {
                if (menuItem1.getItemId() == itemSelectedID) {
                    MainActivity.this.insertSQLInputString(menuItem1.getTitle().toString());
                    handled = true;
                    break;
                }
            }
        }

        return handled || super.onOptionsItemSelected(item);
    }

    private void releaseResources() {
        if (this.customInsertStrings != null) {
            this.customInsertStrings.clear();
            this.customInsertStrings = null;
        }

        if (historyStrings != null) {
            // don't do this
            // stops saving array list
            //historyStrings.clear();
            historyStrings = null;
        }

        if (historyItems != null) {
            historyItems.clear();
            historyItems = null;
        }

        this.sqlLinearLayout1 = null;
        this.databaseTextView = null;
        this.sqlInputEditText = null;
        this.sqlResultTextView = null;
        this.crudSQLDatabaseHelper1 = null;
        this.sqliteDatabase1 = null;
        this.alertDialog = null;
    }

    private void setButtonsStatus(final boolean enableButtons) {
        if (!enableButtons) {
            this.allowEvents = false;

            ActionBar actionBar = this.getSupportActionBar();
            if (actionBar != null) {
                actionBar.hide();
            }

            Window window = this.getWindow();
            if (window != null) {
                window.setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_STATE_ALWAYS_HIDDEN);
            }
        }

        int child_maximum;

        if (this.sqlLinearLayout1 == null) {
            return;
        }

        // image buttons and buttons in main layout
        child_maximum = this.sqlLinearLayout1.getChildCount();
        for (int i = 0; i < child_maximum; i++) {
            final View view1 = this.sqlLinearLayout1.getChildAt(i);
            view1.setEnabled(enableButtons);
        }

        if (enableButtons) {
            ActionBar actionBar = this.getSupportActionBar();
            if (actionBar != null) {
                actionBar.show();
            }

            Window window = this.getWindow();
            if (window != null) {
                window.setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_STATE_ALWAYS_VISIBLE);
            }

            this.allowEvents = true;
        }
    }

    private String[] getDatabaseNames() {
        String[] databaseNames = new String[0];

        File databaseDirectory = this.getDatabasePath(CRUDSQLDatabaseHelper
                .DATABASE_FILE_EXTENSION).getParentFile();
        if (databaseDirectory != null && databaseDirectory.exists() &&
                databaseDirectory.isDirectory() && databaseDirectory
                .canRead()) {
            databaseNames = databaseDirectory.list(new FilenameFilter() {
                @Override
                public boolean accept(File file, String name) {
                    return name.endsWith(CRUDSQLDatabaseHelper.DATABASE_FILE_EXTENSION);
                }
            });

            if (databaseNames != null) {
                for (int i = 0; i < databaseNames.length; i++) {
                    databaseNames[i] = databaseNames[i].substring(0, databaseNames[i].lastIndexOf(
                            '.'));
                }
            }
        }

        return databaseNames;
    }

    private void doKeywordsAlertDialog() {
        if (this.alertDialog != null) {
            this.alertDialog.dismiss();
            this.alertDialog = null;
        }

        final AlertDialog.Builder alertDialogBuilder = new AlertDialog.Builder(
                MainActivity.this);
        KeywordsDialogBinding keywordsDialogBinding = KeywordsDialogBinding
                .inflate((LayoutInflater) this.getSystemService(Context.LAYOUT_INFLATER_SERVICE));
        alertDialogBuilder.setView(keywordsDialogBinding.getRoot());

        final TextView labelTextView1 = keywordsDialogBinding.labelTextView1;
        final Spinner spinner1 = keywordsDialogBinding.spinner1;
        final TextView labelTextView2 = keywordsDialogBinding.labelTextView2;
        final Spinner spinner2 = keywordsDialogBinding.spinner2;
        final Button okButton = keywordsDialogBinding.okButton;

        // labels
        labelTextView1.setText(this.getString(R.string.keywords_group));
        labelTextView2.setText(this.getString(R.string.keyword_to_insert));

        // Create an ArrayAdapter using the string array and a default spinner layout.
        ArrayAdapter<CharSequence> arrayAdapter = ArrayAdapter.createFromResource(this,
                R.array.action_keyword_list, android.R.layout.simple_spinner_item);
        // Specify the layout to use when the list of choices appears.
        arrayAdapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
        // Apply the adapter to the spinner.
        spinner1.setAdapter(arrayAdapter);

        // spinner listeners
        spinner1.setOnItemSelectedListener(new AdapterView.OnItemSelectedListener() {
            @Override
            public void onItemSelected(AdapterView<?> adapterView, View view, int i, long l) {
                boolean joinStringArrays = false;
                int id;

                switch (i) {
                    case 0:
                        id = R.array.action_keyword_list_create_table_1;
                        joinStringArrays = true;
                        break;
                    case 1:
                        id = R.array.action_keyword_list_create_index;
                        break;
                    case 2:
                        id = R.array.action_keyword_list_alter_table;
                        joinStringArrays = true;
                        break;
                    case 3:
                        id = R.array.action_keyword_list_insert_rows;
                        break;
                    case 4:
                        id = R.array.action_keyword_list_update_rows;
                        break;
                    case 5:
                        id = R.array.action_keyword_list_delete_rows;
                        break;
                    case 6:
                        id = R.array.action_keyword_list_query;
                        break;
                    case 7:
                        id = R.array.action_keyword_list_filter;
                        break;
                    case 8:
                        id = R.array.action_keyword_list_symbols;
                        break;
                    case 9:
                        id = R.array.action_keyword_list_generic_functions;
                        break;
                    case 10:
                        id = R.array.action_keyword_list_sqlite_core_functions;
                        break;
                    case 11:
                        id = R.array.action_keyword_list_sqlite_date_and_time_functions;
                        break;
                    case 12:
                        id = R.array.action_keyword_list_sqlite_aggregate_functions;
                        break;
                    case 13:
                        id = R.array.action_keyword_list_transactions;
                        break;
                    case 14:
                        id = R.array.action_keyword_list_triggers;
                        break;
                    case 15:
                        id = R.array.action_keyword_list_create_view;
                        break;
                    case 16:
                        id = R.array.action_keyword_list_database;
                        break;
                    case 17:
                        id = R.array.action_keyword_list_drop;
                        break;
                    case 18:
                        id = R.array.action_keyword_list_create_virtual_table;
                        break;
                    case 19:
                        id = R.array.action_keyword_list_miscellaneous;
                        break;
                    default:
                        Log.e(MainActivity.this.TAG, "doKeywordsAlertDialog error 1");
                        throw new RuntimeException();
                }

                ArrayAdapter<CharSequence> arrayAdapter;
                if (joinStringArrays) {
                    arrayAdapter = new ArrayAdapter<>(MainActivity.this,
                            android.R.layout.simple_spinner_item);
                    // Specify the layout to use when the list of choices appears.
                    arrayAdapter.setDropDownViewResource(
                            android.R.layout.simple_spinner_dropdown_item);

                    Resources resources1 = MainActivity.this.getResources();
                    if (id == R.array.action_keyword_list_create_table_1) {
                        arrayAdapter.addAll(resources1
                                .getStringArray(R.array.action_keyword_list_create_table_1));
                        arrayAdapter.addAll(resources1
                                .getStringArray(R.array.action_keyword_list_column));
                        arrayAdapter.addAll(resources1
                                .getStringArray(R.array.action_keyword_list_create_table_2));
                    } else if (id == R.array.action_keyword_list_alter_table) {
                        arrayAdapter.addAll(resources1
                                .getStringArray(R.array.action_keyword_list_alter_table));
                        arrayAdapter.addAll(resources1
                                .getStringArray(R.array.action_keyword_list_column));
                    } else {
                        Log.e(MainActivity.this.TAG, "doKeywordsAlertDialog error 2");
                        throw new RuntimeException();
                    }
                } else {
                    // Create an ArrayAdapter using the string array and a default spinner layout.
                    arrayAdapter = ArrayAdapter.createFromResource(
                            MainActivity.this,
                            id,
                            android.R.layout.simple_spinner_item);
                    // Specify the layout to use when the list of choices appears.
                    arrayAdapter.setDropDownViewResource(
                            android.R.layout.simple_spinner_dropdown_item);
                    // Apply the adapter to the spinner.
                }
                spinner2.setAdapter(arrayAdapter);
            }

            @Override
            public void onNothingSelected(AdapterView<?> adapterView) {

            }
        });

        spinner2.setOnItemSelectedListener(new AdapterView.OnItemSelectedListener() {
            @Override
            public void onItemSelected(AdapterView<?> adapterView, View view, int i, long l) {
                if (i == 0) {
                    return;
                }

                String messageString;

                if (spinner2.getSelectedItem() == null) {
                    messageString = MainActivity.this.getString(R.string
                            .keyword_insertion_failed);
                } else {
                    int offset = 0, keywordLength;
                    String keywordString = (String) spinner2.getSelectedItem();
                    messageString = keywordString + " " +
                            MainActivity.this.getString(R.string.keyword_inserted);
                    keywordLength = keywordString.length();
                    if (keywordLength > 1) {
                        if (keywordString.lastIndexOf("'") == (keywordLength - 1) ||
                                keywordString.lastIndexOf("\"") == (keywordLength - 1) ||
                                keywordString.lastIndexOf(")") == (keywordLength - 1) ||
                                keywordString.lastIndexOf("]") == (keywordLength - 1)) {
                            offset = 2;
                        } else if (keywordString.lastIndexOf("*/") == (keywordString.length() -
                                2)) {
                            offset = 3;
                        }
                    }
                    MainActivity.this.insertSQLInputString(keywordString + " ");
                    MainActivity.this.sqlInputEditText.setSelection(
                            MainActivity.this.sqlInputEditText.getSelectionStart() - offset);
                }

                spinner2.setSelection(0);

                final Toast toast = Toast.makeText(MainActivity.this.getApplicationContext(),
                        messageString, Toast.LENGTH_SHORT);
                toast.show();
            }

            @Override
            public void onNothingSelected(AdapterView<?> adapterView) {

            }
        });

        // Buttons
        // use XML buttons rather than alertDialogBuilder.setPositiveButton and
        // alertDialogBuilder.setNegativeButton because the methods close the dialog
        // even if input is invalid e.g. new database name already exists
        okButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(final View v) {
                if (MainActivity.this.alertDialog != null) {
                    MainActivity.this.alertDialog.dismiss();
                    MainActivity.this.alertDialog = null;
                }
            }
        });

        alertDialogBuilder
                // back button is disabled
                .setCancelable(false)
                .setTitle(this.getString(R.string.keywords));

        this.alertDialog = alertDialogBuilder.create();
        this.alertDialog.setOwnerActivity(MainActivity.this);
        this.alertDialog.show();
    }

    private void doAlertDialog(final int callerType, final int callerID) {
        if (this.alertDialog != null) {
            this.alertDialog.dismiss();
            this.alertDialog = null;
        }

        String dialogTitle;

        final AlertDialog.Builder alertDialogBuilder = new AlertDialog.Builder(
                MainActivity.this);
        CommonDialogBinding commonDialogBinding = CommonDialogBinding
                .inflate((LayoutInflater) this.getSystemService(Context.LAYOUT_INFLATER_SERVICE));
        alertDialogBuilder.setView(commonDialogBinding.getRoot());

        final TextView labelTextView1 = commonDialogBinding.labelTextView1;
        final Spinner spinner = commonDialogBinding.spinner;
        final TextView labelTextView2 = commonDialogBinding.labelTextView2;
        final EditText editText = commonDialogBinding.editText;
        final Button okButton = commonDialogBinding.okButton;
        final Button cancelButton = commonDialogBinding.cancelButton;

        switch (callerID) {
            case 0:
                // select
                dialogTitle = this.getString(R.string.select);
                labelTextView1.setVisibility(View.GONE);
                labelTextView2.setVisibility(View.GONE);
                editText.setVisibility(View.GONE);
                break;
            case 1:
                // new
                dialogTitle = this.getString(R.string.new_string);
                labelTextView1.setVisibility(View.GONE);
                spinner.setVisibility(View.GONE);
                labelTextView2.setVisibility(View.GONE);
                okButton.setEnabled(false);
                break;
            case 2:
                // rename
                dialogTitle = this.getString(R.string.rename);
                labelTextView1.setText(MainActivity.this.getString(R.string.from));
                labelTextView2.setText(MainActivity.this.getString(R.string.to));
                okButton.setEnabled(false);
                break;
            case 3:
                // delete
                dialogTitle = this.getString(R.string.delete);
                labelTextView1.setVisibility(View.GONE);
                labelTextView2.setVisibility(View.GONE);
                editText.setVisibility(View.GONE);
                break;
            default:
                Log.e(this.TAG, "doAlertDialog error 1");
                throw new RuntimeException();
        }

        dialogTitle += " ";
        if (callerType == 0) {
            dialogTitle += this.getString(R.string.custom_insert);
        } else if (callerType == 1) {
            dialogTitle += this.getString(R.string.database);
        } else {
            Log.e(this.TAG, "doAlertDialog error 2");
            throw new RuntimeException();
        }

        // spinner
        if (!this.doAlertDialogSetup(callerType, callerID, spinner)) {
            return;
        }

        // editText
        if (callerID == 1 || callerID == 2) {
            // new and rename
            final InputFilter filter1 = new InputFilter.LengthFilter(50);
            final InputFilter filter2 = new InputFilter() {
                @Override
                public CharSequence filter(final CharSequence source,
                                           final int start, final int end, final Spanned dest,
                                           final int dstart, final int dend) {
                    for (int i = start; i < end; i++) {
                        if (callerType == 0) {
                            // custom insert
                            if (!Character.isLetterOrDigit(source.charAt(i)) &&
                                    source.charAt(i) != ' ' &&
                                    source.charAt(i) != '_') {
                                return "";
                            }
                        } else {
                            // database
                            if (!Character.isLetterOrDigit(source.charAt(i)) &&
                                    source.charAt(i) != '_') {
                                return "";
                            }
                        }
                    }

                    return null;
                }
            };
            editText.setFilters(new InputFilter[]{filter1,
                    filter2});

            editText.addTextChangedListener(new TextWatcher() {
                @Override
                public void afterTextChanged(final Editable s) {
                }

                @Override
                public void beforeTextChanged(final CharSequence s,
                                              final int start, final int count, final int after) {
                }

                @Override
                public void onTextChanged(final CharSequence s,
                                          final int start, final int before, final int count) {
                    okButton.setEnabled(s.length() > 0);
                }
            });
        }

        // Buttons
        // use XML buttons rather than alertDialogBuilder.setPositiveButton and
        // alertDialogBuilder.setNegativeButton because the methods close the dialog
        // even if input is invalid e.g. new database name already exists
        okButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(final View v) {
                MainActivity.this.doAlertDialogOKButtonOnClick(callerType, callerID,
                        spinner, editText);
            }
        });

        cancelButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(final View v) {
                if (MainActivity.this.alertDialog != null) {
                    MainActivity.this.alertDialog.dismiss();
                    MainActivity.this.alertDialog = null;
                }
            }
        });

        alertDialogBuilder
                // back button is disabled
                .setCancelable(false)
                .setTitle(dialogTitle);

        this.alertDialog = alertDialogBuilder.create();
        this.alertDialog.setOwnerActivity(MainActivity.this);
        this.alertDialog.show();

        if (callerID == 1 || callerID == 2) {
            // new and rename
            Window window = this.alertDialog.getWindow();
            if (window != null) {
                window.setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_STATE_ALWAYS_VISIBLE);
            }
        }
    }

    private boolean doAlertDialogSetup(final int callerType, final int callerID,
                                       final Spinner spinner) {
        boolean ok = true;

        switch (callerType) {
            case 0:
                // custom insert
                // spinner
                if (callerID == 0 || callerID == 2 || callerID == 3) {
                    // select, rename or delete custom insert
                    // Create an ArrayAdapter using the string array and a default spinner layout.
                    ArrayAdapter<CharSequence> arrayAdapter = new ArrayAdapter<>(this,
                            android.R.layout.simple_spinner_item);
                    // Specify the layout to use when the list of choices appears.
                    arrayAdapter.setDropDownViewResource(
                            android.R.layout.simple_spinner_dropdown_item);

                    if (customInsertStrings.size() == 0) {
                        final Toast toast = Toast.makeText(
                                MainActivity.this.getApplicationContext(),
                                R.string.no_custom_inserts, Toast.LENGTH_SHORT);
                        toast.show();
                        ok = false;
                    } else {
                        for (String string1 : customInsertStrings.keySet()) {
                            arrayAdapter.add(string1);
                        }
                        spinner.setAdapter(arrayAdapter);
                    }
                }
                break;
            case 1:
                // database
                // spinner
                if (callerID == 0 || callerID == 2 || callerID == 3) {
                    // select, rename or delete database
                    // Create an ArrayAdapter using the string array and a default spinner layout.
                    ArrayAdapter<CharSequence> arrayAdapter = new ArrayAdapter<>(this,
                            android.R.layout.simple_spinner_item);
                    // Specify the layout to use when the list of choices appears.
                    arrayAdapter.setDropDownViewResource(
                            android.R.layout.simple_spinner_dropdown_item);

                    String[] databaseNames = this.getDatabaseNames();
                    if (databaseNames.length == 0) {
                        final Toast toast = Toast.makeText(
                                MainActivity.this.getApplicationContext(), R.string.no_databases,
                                Toast.LENGTH_SHORT);
                        toast.show();
                        ok = false;
                    } else {
                        for (String databaseName1 : databaseNames) {
                            arrayAdapter.add(databaseName1);
                        }

                        spinner.setAdapter(arrayAdapter);

                        final SharedPreferences settings = this
                                .getPreferences(Context.MODE_PRIVATE);
                        String currentDatabase = settings.getString(
                                MainActivity.CURRENT_DATABASE_NAME, "");
                        if (currentDatabase == null) {
                            break;
                        }

                        for (int i = 0; i < spinner.getCount(); i++) {
                            String databaseName = (String) spinner.getItemAtPosition(i);
                            if (databaseName.compareTo(currentDatabase) == 0) {
                                spinner.setSelection(i);
                                break;
                            }
                        }
                    }
                }
                break;
            default:
                Log.e(MainActivity.this.TAG, "doAlertDialogSetup error 1");
                throw new RuntimeException();
        }

        return ok;
    }

    private void doAlertDialogOKButtonOnClick(final int callerType, final int callerID,
                                              final Spinner spinner, final EditText editText) {
        boolean error = false;
        boolean clearViews = false;
        String messageString = "";
        final SharedPreferences settings = MainActivity.this.getPreferences(Context.MODE_PRIVATE);
        final SharedPreferences.Editor editor = settings.edit();

        if (callerID == 1 || callerID == 2) {
            // new or rename
            String inputString1 = editText.getText().toString().trim();
            if (inputString1.length() == 0) {
                error = true;
                messageString = MainActivity.this.getString(R.string.text_cannot_be_blank);
            }
        }

        if (!error) {
            switch (callerType) {
                case 0:
                    // custom insert
                    String newCustomInsertName;

                    switch (callerID) {
                        case 0:
                            // select custom insert
                            Object object = spinner.getSelectedItem();
                            if (object != null) {
                                @SuppressWarnings("SuspiciousMethodCalls")
                                String insertString =
                                        MainActivity.this.customInsertStrings.get(object);
                                if (insertString != null) {
                                    MainActivity.this.insertSQLInputString(insertString);
                                }
                            } else {
                                error = true;
                            }
                            break;
                        case 1:
                            // new custom insert
                            newCustomInsertName = editText.getText().toString();

                            // validation
                            for (String string1 : customInsertStrings.keySet()) {
                                if (string1.equals(newCustomInsertName)) {
                                    error = true;
                                    messageString = MainActivity.this.getString(
                                            R.string.custom_insert_already_exists);
                                    break;
                                }
                            }

                            if (!error) {
                                MainActivity.this.customInsertStrings.put(newCustomInsertName,
                                        MainActivity.this.sqlInputEditText.getText().toString
                                                ());
                            }
                            break;
                        case 2:
                            // rename custom insert
                            if (spinner.getSelectedItem() != null) {
                                String oldCustomInsertName =
                                        (String) spinner.getSelectedItem();
                                newCustomInsertName = editText.getText().toString();
                                if (MainActivity.this.customInsertStrings.containsKey
                                        (newCustomInsertName)) {
                                    error = true;
                                    messageString = MainActivity.this.getString(R.string
                                            .custom_insert_already_exists);
                                } else {
                                    MainActivity.this.customInsertStrings.put
                                            (newCustomInsertName,
                                                    MainActivity.this.customInsertStrings.get
                                                            (oldCustomInsertName));
                                    if (MainActivity.this.customInsertStrings.remove
                                            (oldCustomInsertName) == null) {
                                        error = true;
                                        messageString = MainActivity.this.getString(R.string
                                                .custom_insert_rename_failed);
                                    }
                                }
                            }
                            break;
                        case 3:
                            // delete custom insert
                            if (spinner.getSelectedItem() != null) {
                                String deleteCustomInsertName =
                                        (String) spinner.getSelectedItem();
                                if (MainActivity.this.customInsertStrings.remove
                                        (deleteCustomInsertName) == null) {
                                    error = true;
                                    messageString = MainActivity.this.getString(R.string
                                            .custom_insert_deletion_failed);
                                }
                            } else {
                                error = true;
                            }
                            break;
                        default:
                            Log.e(MainActivity.this.TAG,
                                    "doAlertDialogOKButtonOnClick error 1");
                            throw new RuntimeException();
                    }

                    if (error) {
                        if (messageString.length() == 0) {
                            messageString = MainActivity.this.getString(R.string
                                    .custom_insert_action_failed);
                        }
                    } else {
                        // permanent custom insert store
                        // remove all existing custom inserts
                        boolean loop = true;
                        for (int i = 0; loop; i++) {
                            String settingsString = settings.getString(
                                    MainActivity.CUSTOM_INSERT + i, "");
                            if (settingsString != null) {
                                loop = !settingsString.equals("");
                                if (loop) {
                                    editor.remove(MainActivity.CUSTOM_INSERT + i);
                                }
                            }
                        }
                        // add all current custom inserts
                        Set<String> set1 = MainActivity.this.customInsertStrings.keySet();
                        Object[] keys1 = set1.toArray();
                        for (int i = 0; i < keys1.length; i++) {
                            @SuppressWarnings("SuspiciousMethodCalls")
                            String value1 = MainActivity.this.customInsertStrings.get(keys1[i]);
                            editor.putString(MainActivity.CUSTOM_INSERT + i,
                                    keys1[i] + MainActivity.CUSTOM_INSERT_KEY_VALUE_DIVIDER +
                                            value1);
                        }
                        editor.apply();

                        switch (callerID) {
                            case 0:
                                messageString = MainActivity.this.getString(R.string
                                        .custom_insert_selected);
                                break;
                            case 1:
                                messageString = MainActivity.this.getString(R.string
                                        .new_custom_insert_created);
                                break;
                            case 2:
                                messageString = MainActivity.this.getString(R.string
                                        .custom_insert_renamed);
                                break;
                            case 3:
                                messageString = MainActivity.this.getString(R.string
                                        .custom_insert_deleted);
                                break;
                            default:
                                Log.e(MainActivity.this.TAG, "doAlertDialogOKButtonOnClick " +
                                        "error 2");
                                throw new RuntimeException();
                        }
                    }
                    break;
                case 1:
                    // database
                    String newDatabaseName;

                    switch (callerID) {
                        case 0:
                            // select database
                            if (spinner.getSelectedItem() != null) {
                                MainActivity.this.showDatabaseOpenText = true;
                                this.executeAsyncOpenSQLDatabase((String) spinner.getSelectedItem
                                        ());
                            } else {
                                error = true;
                            }
                            break;
                        case 1:
                            // new database
                            newDatabaseName = editText.getText().toString();

                            // validation
                            String[] databaseNames = MainActivity.this.getDatabaseNames();
                            for (String databaseName : databaseNames) {
                                if (databaseName.equals(newDatabaseName)) {
                                    error = true;
                                    messageString = MainActivity.this.getString(
                                            R.string.database_already_exists);
                                    break;
                                }
                            }

                            if (!error) {
                                MainActivity.this.showDatabaseOpenText = true;
                                this.executeAsyncOpenSQLDatabase(newDatabaseName);
                            }
                            break;
                        case 2:
                            // rename database
                            if (spinner.getSelectedItem() != null) {
                                String oldDatabaseName =
                                        spinner.getSelectedItem() +
                                                CRUDSQLDatabaseHelper.DATABASE_FILE_EXTENSION;
                                File oldDatabaseFile = MainActivity.this.getDatabasePath
                                        (oldDatabaseName);
                                if (oldDatabaseFile.exists() && oldDatabaseFile.isFile() &&
                                        oldDatabaseFile.canRead() &&
                                        oldDatabaseFile.canWrite()) {
                                    newDatabaseName = editText.getText().toString() +
                                            CRUDSQLDatabaseHelper.DATABASE_FILE_EXTENSION;
                                    File newDatabaseFile = MainActivity.this.getDatabasePath
                                            (newDatabaseName);
                                    if (newDatabaseFile.exists()) {
                                        error = true;
                                        messageString = MainActivity.this.getString(R.string
                                                .database_already_exists);
                                    } else {
                                        if (oldDatabaseFile.renameTo(newDatabaseFile)) {
                                            if (oldDatabaseName.equals(settings.getString
                                                    (MainActivity.CURRENT_DATABASE_NAME,
                                                            "") + CRUDSQLDatabaseHelper
                                                            .DATABASE_FILE_EXTENSION)) {
                                                String newDatabaseNameWithoutExtension =
                                                        newDatabaseName.substring(0,
                                                                newDatabaseName.lastIndexOf(
                                                                        '.'));
                                                MainActivity.this.showDatabaseOpenText = true;
                                                this.executeAsyncOpenSQLDatabase
                                                        (newDatabaseNameWithoutExtension);
                                                clearViews = true;
                                            }
                                        } else {
                                            error = true;
                                            messageString = MainActivity.this.getString(R
                                                    .string.database_rename_failed);
                                        }
                                    }
                                } else {
                                    error = true;
                                    messageString = MainActivity.this.getString(R.string
                                            .existing_database_access_denied);
                                }
                            } else {
                                error = true;
                            }
                            break;
                        case 3:
                            // delete database
                            // no need to disable\enable actions because delete database
                            if (spinner.getSelectedItem() != null) {
                                String deleteDatabaseName =
                                        spinner.getSelectedItem() +
                                                CRUDSQLDatabaseHelper.DATABASE_FILE_EXTENSION;
                                File deleteDatabaseFile = MainActivity.this.getDatabasePath
                                        (deleteDatabaseName);
                                if (deleteDatabaseFile.exists() && deleteDatabaseFile.isFile() &&
                                        deleteDatabaseFile.canRead() &&
                                        deleteDatabaseFile.canWrite()) {
                                    boolean deleteCurrentDatabase = false;
                                    if (deleteDatabaseName.equals(settings.getString
                                            (MainActivity.CURRENT_DATABASE_NAME, "") +
                                            CRUDSQLDatabaseHelper.DATABASE_FILE_EXTENSION)) {
                                        deleteCurrentDatabase = true;
                                        this.clearDatabaseVariables();
                                    }

                                    if (deleteDatabaseFile.delete()) {
                                        if (deleteCurrentDatabase) {
                                            editor.putString(MainActivity.CURRENT_DATABASE_NAME,
                                                    "");
                                            editor.apply();
                                            MainActivity.this.databaseTextView.setText("");
                                            clearViews = true;
                                        }
                                    } else {
                                        error = true;
                                        messageString = MainActivity.this.getString(R.string
                                                .database_deletion_failed);
                                    }
                                } else {
                                    error = true;
                                    messageString = MainActivity.this.getString(R.string
                                            .database_access_denied);
                                }
                            } else {
                                error = true;
                            }
                            break;
                        default:
                            Log.e(MainActivity.this.TAG, "doAlertDialogOKButtonOnClick error "
                                    + "3");
                            throw new RuntimeException();
                    }

                    if (error) {
                        if (messageString.length() == 0) {
                            messageString = MainActivity.this.getString(R.string
                                    .database_action_failed);
                        }
                    } else {
                        switch (callerID) {
                            case 0:
                                messageString = MainActivity.this.getString(R.string
                                        .database_selected);
                                break;
                            case 1:
                                messageString = MainActivity.this.getString(R.string
                                        .new_database_created);
                                break;
                            case 2:
                                messageString = MainActivity.this.getString(R.string
                                        .database_renamed);
                                break;
                            case 3:
                                messageString = MainActivity.this.getString(R.string
                                        .database_deleted);
                                break;
                            default:
                                Log.e(MainActivity.this.TAG, "doAlertDialogOKButtonOnClick " +
                                        "error 4");
                                throw new RuntimeException();
                        }
                    }
                    break;
                default:
                    Log.e(MainActivity.this.TAG, "doAlertDialogOKButtonOnClick error 5");
                    throw new RuntimeException();
            }
        }

        final Toast toast = Toast.makeText(MainActivity.this.getApplicationContext(), messageString,
                Toast.LENGTH_SHORT);
        toast.show();

        if (!error) {
            if (clearViews) {
                MainActivity.this.sqlInputEditText.setText(";");
                MainActivity.this.sqlResultTextView.setText("");
            }

            if (MainActivity.this.alertDialog != null) {
                MainActivity.this.alertDialog.dismiss();
                MainActivity.this.alertDialog = null;
            }
        }
    }

    private void insertSQLInputString(final String insertString) {
        // not a keyword e.g. template
        if (insertString.lastIndexOf(' ') != (insertString.length() - 1)) {
            String existingText = MainActivity.this.sqlInputEditText.getText().toString();

            if (existingText.equals(";")) {
                MainActivity.this.sqlInputEditText.setText("");
            }
        }

        MainActivity.this.sqlInputEditText.getText().insert(
                MainActivity.this.sqlInputEditText.getSelectionStart(),
                insertString);
    }

    private String openSQLDatabase(final String databaseName) {
        String sqlResultString;

        if (databaseName.equals("")) {
            sqlResultString = this.getString(R.string.create_or_select_a_database);
        } else {
            this.clearDatabaseVariables();

            this.crudSQLDatabaseHelper1 = new CRUDSQLDatabaseHelper(this, databaseName);
            // This is what creates the database.
            this.sqliteDatabase1 = this.crudSQLDatabaseHelper1.getReadableDatabase();

            final SharedPreferences settings = this.getPreferences(Context.MODE_PRIVATE);
            final SharedPreferences.Editor editor = settings.edit();
            editor.putString(CURRENT_DATABASE_NAME, databaseName);
            editor.apply();

            sqlResultString = this.getString(R.string.database) + " " + databaseName + " " +
                    this.getString(R.string.opened);

            sqlResultString += "\n" + this.crudSQLDatabaseHelper1.executeSQLCommands(this,
                    this.sqliteDatabase1, this.getString(R.string.foreign_keys_on));
        }

        return sqlResultString;
    }

    private void executeAsyncOpenSQLDatabase(final String databaseName1) {
        this.setButtonsStatus(false);

        // Background thread.
        BasicApplication.APP_EXECUTORS.getBackgroundThreadExecutor().execute(new Runnable() {
            @Override
            public void run() {
                String sqlResultString = "";
                final WeakReference<MainActivity> mainActivityWeakReference =
                        new WeakReference<>(MainActivity.this);

                try {
                    // Get a reference to the activity if it is still there.
                    final MainActivity mainActivity = mainActivityWeakReference.get();
                    if (mainActivity == null || mainActivity.isFinishing()) {
                        return;
                    }

                    sqlResultString = mainActivity.openSQLDatabase(databaseName1);
                } finally {
                    // Main thread.
                    final String finalSqlResultString = sqlResultString;

                    BasicApplication.APP_EXECUTORS.getMainThreadExecutor().execute(
                            new Runnable() {
                                @Override
                                public void run() {
                                    // Get a reference to the activity if it is still there.
                                    final MainActivity mainActivity =
                                            mainActivityWeakReference.get();
                                    if (mainActivity == null || mainActivity.isFinishing()) {
                                        return;
                                    }

                                    final SharedPreferences settings = mainActivity.getPreferences(
                                            Context.MODE_PRIVATE);
                                    mainActivity.databaseTextView.setText(settings.getString(
                                            MainActivity.CURRENT_DATABASE_NAME, ""));

                                    if (mainActivity.showDatabaseOpenText) {
                                        mainActivity.sqlResultTextView.setText(
                                                finalSqlResultString);
                                        mainActivity.showDatabaseOpenText = false;
                                    }

                                    mainActivity.setButtonsStatus(true);
                                }
                            });
                }
            }
        });
    }

    private void clearDatabaseVariables() {
        if (this.sqliteDatabase1 != null) {
            this.sqliteDatabase1.close();
            this.sqliteDatabase1 = null;
        }

        if (this.crudSQLDatabaseHelper1 != null) {
            this.crudSQLDatabaseHelper1.close();
            this.crudSQLDatabaseHelper1 = null;
        }
    }
}
