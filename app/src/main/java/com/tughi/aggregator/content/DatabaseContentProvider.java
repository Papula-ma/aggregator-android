package com.tughi.aggregator.content;

import android.content.ContentProvider;
import android.content.ContentProviderOperation;
import android.content.ContentProviderResult;
import android.content.ContentUris;
import android.content.ContentValues;
import android.content.OperationApplicationException;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import android.database.sqlite.SQLiteOpenHelper;
import android.net.Uri;
import android.os.Bundle;

import com.tughi.android.database.sqlite.DatabaseOpenHelper;

import java.util.ArrayList;

/**
 * A {@link ContentProvider} that stores the aggregated feeds.
 */
public class DatabaseContentProvider extends ContentProvider {

    public static final String AUTHORITY = "com.tughi.aggregator";

    private SQLiteOpenHelper helper;

    private static final String TABLE_FEED_SYNC = "feed_sync";
    private static final String TABLE_FEED_USER = "feed_user";
    private static final String TABLE_ENTRY_SYNC = "entry_sync";
    private static final String TABLE_ENTRY_USER = "entry_user";

    private static final String VIEW_FEED = "feed_view";
    private static final String VIEW_ENTRY = "entry_view";

    @Override
    public boolean onCreate() {
        helper = new DatabaseOpenHelper(getContext(), "content.db", 1);
        return true;
    }

    @Override
    public String getType(Uri uri) {
        throw new UnsupportedOperationException(uri.toString());
    }

    @Override
    public Cursor query(Uri uri, String[] projection, String selection, String[] selectionArgs, String orderBy) {
        String feedId;
        switch (Uris.match(uri)) {
            case Uris.MATCHED_FEED_URI:
                feedId = uri.getPathSegments().get(1);
                selection = and(FeedColumns.ID + " = " + feedId, selection);
            case Uris.MATCHED_FEEDS_URI:
                return queryFeeds(uri, projection, selection, selectionArgs, orderBy);
            case Uris.MATCHED_FEED_ENTRIES_URI:
                feedId = uri.getPathSegments().get(1);
                if ("-2".equals(feedId)) {
                    selection = and(EntryColumns.FLAG_STAR + " = 1", selection);
                } else if (!"-1".equals(feedId)) {
                    selection = and(EntryColumns.FEED_ID + " = " + feedId, selection);
                }
                return queryEntries(uri, projection, selection, selectionArgs, orderBy);
        }
        throw new UnsupportedOperationException(uri.toString());
    }

    private Cursor queryFeeds(Uri uri, String[] projection, String selection, String[] selectionArgs, String orderBy) {
        SQLiteDatabase database = helper.getReadableDatabase();
        Cursor cursor = database.query(VIEW_FEED, projection, selection, selectionArgs, null, null, orderBy);
        cursor.setNotificationUri(getContext().getContentResolver(), uri);
        return cursor;
    }

    private Cursor queryEntries(Uri uri, String[] projection, String selection, String[] selectionArgs, String orderBy) {
        SQLiteDatabase database = helper.getReadableDatabase();
        Cursor cursor = database.query(VIEW_ENTRY, projection, selection, selectionArgs, null, null, orderBy);
        cursor.setNotificationUri(getContext().getContentResolver(), uri);
        return cursor;
    }

    @Override
    public Uri insert(Uri uri, ContentValues values) {
        switch (Uris.match(uri)) {
            case Uris.MATCHED_FEED_ENTRIES_URI:
                return insertEntry(uri, values);
        }

        throw new UnsupportedOperationException(uri.toString());
    }

    public Uri insertEntry(Uri uri, ContentValues values) {
        SQLiteDatabase database = helper.getWritableDatabase();

        String where = EntryColumns.FEED_ID + " = ? AND " + EntryColumns.GUID + " = ?";
        String[] whereArgs = {
                values.getAsString(EntryColumns.FEED_ID),
                values.getAsString(EntryColumns.GUID)
        };

        // try to update existing entry
        if (database.update(TABLE_ENTRY_SYNC, values, where, whereArgs) > 0) {
            final String[] columns = {EntryColumns.ID};
            Cursor cursor = database.query(TABLE_ENTRY_USER, columns, where, whereArgs, null, null, null);
            try {
                cursor.moveToFirst();
                return Uri.withAppendedPath(uri, cursor.getString(0));
            } finally {
                cursor.close();
            }
        }

        // insert new entry otherwise
        long id = database.insert(TABLE_ENTRY_SYNC, null, values);
        return ContentUris.withAppendedId(uri, id);
    }

    @Override
    public int update(Uri uri, ContentValues values, String where, String[] whereArgs) {
        switch (Uris.match(uri)) {
            case Uris.MATCHED_USER_ENTRY_URI:
                where = and(EntryColumns.ID + " = " + uri.getLastPathSegment(), where);
            case Uris.MATCHED_USER_ENTRIES_URI:
                return updateUserEntries(uri, values, where, whereArgs);
            case Uris.MATCHED_USER_FEED_URI:
                where = and(FeedColumns.ID + " = " + uri.getLastPathSegment(), where);
            case Uris.MATCHED_USER_FEEDS_URI:
                return updateUserFeeds(uri, values, where, whereArgs);
            case Uris.MATCHED_SYNC_FEED_URI:
                where = and(FeedColumns.ID + " = " + uri.getLastPathSegment(), where);
            case Uris.MATCHED_SYNC_FEEDS_URI:
                return updateSyncFeeds(uri, values, where, whereArgs);
        }
        throw new UnsupportedOperationException(uri.toString());
    }

    private int updateUserEntries(Uri uri, ContentValues values, String where, String[] whereArgs) {
        SQLiteDatabase database = helper.getWritableDatabase();
        int result = database.update(TABLE_ENTRY_USER, values, where, whereArgs);

        if (result != 0) {
            getContext().getContentResolver().notifyChange(Uris.newFeedsUri(), null);
        }

        return result;
    }

    private int updateUserFeeds(Uri uri, ContentValues values, String where, String[] whereArgs) {
        SQLiteDatabase database = helper.getWritableDatabase();
        int result = database.update(TABLE_FEED_USER, values, where, whereArgs);

        if (result != 0) {
            getContext().getContentResolver().notifyChange(Uris.newFeedsUri(), null);
        }

        return result;
    }

    private int updateSyncFeeds(Uri uri, ContentValues values, String where, String[] whereArgs) {
        SQLiteDatabase database = helper.getWritableDatabase();
        int result = database.update(TABLE_FEED_SYNC, values, where, whereArgs);

        if (result != 0) {
            getContext().getContentResolver().notifyChange(Uris.newFeedsUri(), null);
        }

        return result;
    }

    @Override
    public int delete(Uri uri, String where, String[] whereArgs) {
        throw new UnsupportedOperationException(uri.toString());
    }

    @Override
    public ContentProviderResult[] applyBatch(ArrayList<ContentProviderOperation> operations) throws OperationApplicationException {
        SQLiteDatabase database = helper.getWritableDatabase();

        database.beginTransaction();
        try {
            for (ContentProviderOperation operation : operations) {
                operation.apply(this, null, 0);
            }

            database.setTransactionSuccessful();
        } finally {
            database.endTransaction();
        }

        return super.applyBatch(operations);
    }

    @Override
    public Bundle call(String method, String arg, Bundle extras) {
        if (Uris.CALL_COMMIT_ENTRIES_READ_STATE.equals(method)) {
            return commitEntryReadFlags();
        }

        return super.call(method, arg, extras);
    }

    private Bundle commitEntryReadFlags() {
        SQLiteDatabase database = helper.getWritableDatabase();
        database.execSQL("UPDATE " + TABLE_ENTRY_USER
                + " SET " + EntryColumns.RO_FLAG_READ + " = " + EntryColumns.FLAG_READ
                + " WHERE " + EntryColumns.RO_FLAG_READ + " <> " + EntryColumns.FLAG_READ);
        return null;
    }

    /**
     * Concatenates two SQL conditions with the AND operation.
     */
    private String and(String first, String second) {
        if (second != null) {
            return first + " AND (" + second + ")";
        }
        return first;
    }

}
