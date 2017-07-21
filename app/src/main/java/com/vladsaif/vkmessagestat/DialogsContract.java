package com.vladsaif.vkmessagestat;

import android.provider.BaseColumns;

/**
 * Database contract
 */
public final class DialogsContract {
    public DialogsContract() {}

    public static abstract class dialogs implements BaseColumns {
        public static final String TABLE_NAME = "dialogs";
        public static final String COLUMN_NAME_DIALOG_ID = "dialog_id";
        public static final String COLUMN_NAME_NAME = "name";
        public static final String COLUMN_NAME_LAST_MESSAGE_ID = "last_message_id";
        public static final String COLUMN_NAME_MESSAGE_COUNTER = "message_counter";
        public static final String COLUMN_NAME_SYMBOLS_COUNTER = "symbols_counter";
    }
}
