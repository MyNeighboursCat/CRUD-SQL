/*
 * Copyright (c) 2023 Colin Walters.  All rights reserved.
 */
package com.crudsql.model;

import android.content.Context;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import android.database.sqlite.SQLiteOpenHelper;

import com.crudsql.R;
import com.crudsql.controller.MainActivity;

import java.util.Arrays;

/**
 * @author Colin Walters
 * @version 1.0, 21/04/2023
 */
public final class CRUDSQLDatabaseHelper extends SQLiteOpenHelper {
    public static final String DATABASE_FILE_EXTENSION = ".db_com_crud_sql";
    private static final int DATA_BASE_VERSION = 1;
    private volatile boolean inTransaction = false;

    public CRUDSQLDatabaseHelper(final Context context, final String name) {
        super(context, name + CRUDSQLDatabaseHelper.DATABASE_FILE_EXTENSION, null,
                CRUDSQLDatabaseHelper.DATA_BASE_VERSION);
    }

    private static String doSQLResultString(final MainActivity activity1,
                                            final SQLiteDatabase sqLiteDatabase,
                                            final String currentSQLStatement,
                                            final Cursor cursor1) {
        boolean isSelectSQLStatement = false;
        boolean isChanges = false;
        long rowCount = 0L;
        StringBuilder sqlResultString = new StringBuilder();

        sqlResultString.append(currentSQLStatement).append(";\n");

        if (cursor1.getCount() > 0) {
            String[] columnNamesString = cursor1.getColumnNames();
            for (int i = 0; i < columnNamesString.length; i++) {
                sqlResultString.append(columnNamesString[i]);

                if (i < (columnNamesString.length - 1)) {
                    sqlResultString.append(", ");
                }
            }
            sqlResultString.append("\n");
        }

        int columnCount = cursor1.getColumnCount();
        for (cursor1.moveToFirst(); !cursor1
                .isAfterLast(); cursor1.moveToNext()) {
            for (int i = 0; i < columnCount; i++) {
                switch (cursor1.getType(i)) {
                    case Cursor.FIELD_TYPE_NULL:
                        sqlResultString.append(activity1.getString(R.string.null_text));
                        break;
                    case Cursor.FIELD_TYPE_INTEGER:
                        sqlResultString.append(cursor1.getLong(i));
                        break;
                    case Cursor.FIELD_TYPE_FLOAT:
                        sqlResultString.append(cursor1.getDouble(i));
                        break;
                    case Cursor.FIELD_TYPE_STRING:
                        sqlResultString.append(cursor1.getString(i));
                        break;
                    case Cursor.FIELD_TYPE_BLOB:
                        sqlResultString.append(Arrays.toString(cursor1.getBlob(i)));
                        break;
                    default:
                        sqlResultString.append(activity1.getString(R.string.unsupported_data_type));
                        break;
                }

                if (i != (columnCount - 1)) {
                    sqlResultString.append(", ");
                }
            }

            sqlResultString.append("\n");
        }

        String upperCaseString = currentSQLStatement.toUpperCase();
        if (upperCaseString.contains(activity1.getString(R.string.select_capitals))) {
            if (!upperCaseString.contains(activity1.getString(R.string.create)) &&
                    !upperCaseString.contains(activity1.getString(R.string.view))) {
                isSelectSQLStatement = true;
            }
        } else if (upperCaseString.contains(activity1.getString(R.string.insert)) ||
                upperCaseString.contains(activity1.getString(R.string.update)) ||
                upperCaseString.contains(activity1.getString(R.string.delete_capitals))) {
            if (!upperCaseString.contains(activity1.getString(R.string.create)) &&
                    !upperCaseString.contains(activity1.getString(R.string.trigger_capitals))) {
                isChanges = true;
            }
        }

        if (isSelectSQLStatement) {
            // SELECT
            rowCount = cursor1.getCount();

            sqlResultString.append(rowCount).append(" ");
            if (rowCount == 1) {
                sqlResultString.append(activity1.getString(R.string.row));
            } else {
                sqlResultString.append(activity1.getString(R.string.rows));
            }

            sqlResultString.append(" ").append(activity1.getString(R.string.in_set));
        } else {
            sqlResultString.append(activity1.getString(R.string.query_ok));

            // INSERT, UPDATE OR DELETE
            if (isChanges) {
                try {
                    sqlResultString.append(", ");

                    Cursor cursor2 = sqLiteDatabase.rawQuery(
                            "SELECT changes() AS affected_row_count", null);
                    if (cursor2 != null && cursor2.getCount() > 0 && cursor2.moveToFirst()) {
                        rowCount = cursor2.getLong(cursor2.getColumnIndexOrThrow(
                                "affected_row_count"));
                    }

                    if (cursor2 != null) {
                        cursor2.close();
                    }

                    sqlResultString.append(rowCount).append(" ");
                    if (rowCount == 1) {
                        sqlResultString.append(activity1.getString(R.string.row));
                    } else {
                        sqlResultString.append(activity1.getString(R.string.rows));
                    }

                    sqlResultString.append(" ").append(activity1.getString(R.string.affected));
                } catch (final Exception e) {
                    sqlResultString = new StringBuilder(e.toString());
                }
            }
        }
        sqlResultString.append("\n\n");

        return sqlResultString.toString();
    }

    public String executeSQLCommands(final MainActivity activity1,
                                     final SQLiteDatabase sqLiteDatabase,
                                     String sqlCommands) {
        boolean error = false;
        StringBuilder sqlResultString = new StringBuilder();
        Cursor cursor1 = null;

        sqlCommands = sqlCommands.trim();

        if (sqlCommands.equals("")) {
            error = true;
            sqlResultString = new StringBuilder(activity1.getString(R.string
                    .no_sql_statement_to_execute));
        }

        if (!error) {
            int lastPositionOfSemicolon = sqlCommands.lastIndexOf(";");
            if (lastPositionOfSemicolon == -1) {
                error = true;
                sqlResultString = new StringBuilder(activity1.getString(R.string.no_semicolon));
            } else if (lastPositionOfSemicolon != (sqlCommands.length() - 1)) {
                error = true;
                sqlResultString = new StringBuilder(activity1
                        .getString(R.string.last_character_must_be_a_semicolon));
            }
        }

        if (!error) {
            for (int i = 0; !error && i < sqlCommands.length(); i++) {
                int nextPositionOfSemiColon = sqlCommands.indexOf(';', i);
                String currentSQLStatement = sqlCommands.substring(i, nextPositionOfSemiColon);
                // get rid of whitespaces at beginning and end of string e.g. \n etc
                currentSQLStatement = currentSQLStatement.trim();

                if (this.inTransaction &&
                        currentSQLStatement.contains(activity1.getString(R.string.begin)) &&
                        currentSQLStatement.contains(activity1.getString(R.string
                                .transaction_capitals))) {
                    error = true;
                    sqlResultString = new StringBuilder(activity1
                            .getString(R.string.nested_transactions));
                } else if (currentSQLStatement.contains(activity1.getString(R.string.create)) &&
                        currentSQLStatement.contains(activity1.getString(R.string
                                .trigger_capitals))) {
                    i = nextPositionOfSemiColon;
                    nextPositionOfSemiColon = sqlCommands.indexOf(';', ++i);
                    currentSQLStatement += ";";
                    if (nextPositionOfSemiColon != -1) {
                        currentSQLStatement += sqlCommands.substring(i, nextPositionOfSemiColon);
                    }
                }

                if (!error) {
                    try {
                        cursor1 = sqLiteDatabase.rawQuery(currentSQLStatement, null);
                        // if string is "", cursor is not null but cursor1.getCount() gives an ANR
                        // try move to first here to throw an exception if input string was invalid
                        // e.g. nothing or just ;
                        cursor1.moveToFirst();

                        if (currentSQLStatement.contains(activity1.getString(R.string.begin)) &&
                                currentSQLStatement.contains(activity1.getString(R.string
                                        .transaction_capitals))) {
                            this.inTransaction = true;
                        } else if (currentSQLStatement.contains(activity1.getString(R.string
                                .rollback)) ||
                                currentSQLStatement.contains(activity1.getString(R.string.commit)
                                )) {
                            this.inTransaction = false;
                        }
                    } catch (final Exception e) {
                        error = true;
                        sqlResultString = new StringBuilder(e.toString());
                    }
                }

                if (!error) {
                    sqlResultString.append(CRUDSQLDatabaseHelper.doSQLResultString(activity1,
                            sqLiteDatabase, currentSQLStatement, cursor1));
                }

                if (cursor1 != null) {
                    cursor1.close();
                    cursor1 = null;
                }

                i = nextPositionOfSemiColon;
            }
        }

        return sqlResultString.toString();
    }

    @Override
    public void onCreate(final SQLiteDatabase sqLiteDatabase) {
        // abstract method so no super call
    }

    @Override
    public void onUpgrade(final SQLiteDatabase sqLiteDatabase, final int oldv,
                          final int newv) {
        // abstract method so no super call
    }
}
