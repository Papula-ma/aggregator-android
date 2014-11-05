package com.tughi.aggregator.ui;

import android.content.ContentResolver;
import android.content.ContentValues;
import android.content.Context;
import android.content.Intent;
import android.database.Cursor;
import android.database.MatrixCursor;
import android.net.Uri;
import android.os.AsyncTask;
import android.os.Bundle;
import android.support.v4.app.Fragment;
import android.support.v4.app.FragmentActivity;
import android.support.v4.app.LoaderManager;
import android.support.v4.app.NavUtils;
import android.support.v4.app.TaskStackBuilder;
import android.support.v4.content.AsyncTaskLoader;
import android.support.v4.content.Loader;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.Menu;
import android.view.MenuInflater;
import android.view.MenuItem;
import android.view.View;
import android.view.ViewGroup;
import android.widget.EditText;
import android.widget.ListView;
import android.widget.TextView;

import com.tughi.aggregator.R;
import com.tughi.aggregator.content.FeedColumns;
import com.tughi.aggregator.content.Uris;
import com.tughi.aggregator.feeds.FeedParser;
import com.tughi.aggregator.feeds.FeedParserException;

import java.io.IOException;
import java.net.HttpURLConnection;
import java.net.URL;
import java.net.URLConnection;

/**
 * A {@link Fragment} used to preview and configure a feed before adding.
 */
public class AddFeedFragment extends Fragment implements LoaderManager.LoaderCallbacks<Cursor> {

    public static final String ARG_URL = "url";
    public static final String ARG_TITLE = "title";

    private String feedUrl;
    private String feedTitle;

    private EditText titleEditText;

    private EntryListAdapter entryListAdapter;

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        setHasOptionsMenu(true);

        feedUrl = getArguments().getString(ARG_URL);
        feedTitle = getArguments().getString(ARG_TITLE);

        entryListAdapter = new EntryListAdapter(getActivity());

        getLoaderManager().initLoader(0, null, this);
    }

    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container, Bundle savedInstanceState) {
        View view = inflater.inflate(R.layout.add_feed_fragment, container, false);

        TextView urlTextView = (TextView) view.findViewById(R.id.url);
        urlTextView.setText(feedUrl);

        titleEditText = (EditText) view.findViewById(R.id.title);
        if (savedInstanceState == null) {
            titleEditText.setText(feedTitle);
        }

        ListView listView = (ListView) view.findViewById(R.id.entries);
        listView.setAdapter(entryListAdapter);

        return view;
    }

    @Override
    public void onCreateOptionsMenu(Menu menu, MenuInflater inflater) {
        inflater.inflate(R.menu.add_feed_fragment, menu);
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        switch (item.getItemId()) {
            case R.id.done:
                onDone();
                return true;
        }

        return super.onOptionsItemSelected(item);
    }

    /**
     * Adds the feed and returns to the app.
     */
    private void onDone() {
        FragmentActivity activity = getActivity();

        // add feed in a background thread
        final Context context = activity.getApplicationContext();
        final String customFeedTitle = titleEditText.getText().toString();
        new AsyncTask<Object, Void, Boolean>() {
            @Override
            protected Boolean doInBackground(Object... params) {
                ContentResolver contentResolver = context.getContentResolver();

                // add feed
                ContentValues syncFeedValues = new ContentValues();
                syncFeedValues.put(FeedColumns.URL, feedUrl);
                syncFeedValues.put(FeedColumns.TITLE, feedTitle);
                Uri syncFeedUri = contentResolver.insert(Uris.newSyncFeedsUri(), syncFeedValues);

                if (customFeedTitle.length() > 0 && !customFeedTitle.equals(feedTitle)) {
                    // set custom title
                    long feedId = Long.parseLong(syncFeedUri.getLastPathSegment());

                    ContentValues userFeedValues = new ContentValues();
                    userFeedValues.put(FeedColumns.TITLE, customFeedTitle);
                    contentResolver.update(Uris.newUserFeedUri(feedId), userFeedValues, null, null);
                }
                return Boolean.TRUE;
            }
        }.execute();

        // return to the main activity
        Intent intent = new Intent(context, MainActivity.class);
        if (NavUtils.shouldUpRecreateTask(activity, intent)) {
            TaskStackBuilder
                    .create(context)
                    .addNextIntent(intent)
                    .startActivities();

            activity.finish();
        } else {
            NavUtils.navigateUpTo(activity, intent);
        }
    }

    @Override
    public Loader<Cursor> onCreateLoader(int loader, Bundle args) {
        return new FeedLoader(getActivity(), getArguments().getString(ARG_URL));
    }

    @Override
    public void onLoadFinished(Loader<Cursor> loader, Cursor cursor) {
        FeedLoader feedLoader = (FeedLoader) loader;
        feedTitle = feedLoader.result.feed.title;

        titleEditText.setText(feedTitle);
        entryListAdapter.swapCursor(cursor);
    }

    @Override
    public void onLoaderReset(Loader<Cursor> loader) {
        entryListAdapter.swapCursor(null);
    }

    private static class FeedLoader extends AsyncTaskLoader<Cursor> {

        private final String feedUrl;

        private volatile FeedParser.Result result;

        public FeedLoader(Context context, String feedUrl) {
            super(context);

            this.feedUrl = feedUrl;
        }

        @Override
        public Cursor loadInBackground() {
            try {
                // parse feed
                URL url = new URL(feedUrl);
                URLConnection urlConnection = url.openConnection();
                result = FeedParser.parse(urlConnection);

                // build cursor
                if (result.status == HttpURLConnection.HTTP_OK) {
                    MatrixCursor cursor = new MatrixCursor(EntryListAdapter.ENTRY_PROJECTION, result.feed.entries.size());

                    for (FeedParser.Result.Feed.Entry entry : result.feed.entries) {
                        cursor.newRow()
                                .add(0)
                                .add(entry.title)
                                .add(entry.updatedTimestamp)
                                .add(result.feed.title)
                                .add(0);
                    }

                    return cursor;
                }
            } catch (IOException exception) {
                Log.e(getClass().getName(), "Loader failed", exception);
            } catch (FeedParserException exception) {
                Log.e(getClass().getName(), "Loader failed", exception);
            }

            return null;
        }

        @Override
        protected void onStartLoading() {
            forceLoad();
        }

        @Override
        protected void onStopLoading() {
            cancelLoad();
        }

    }

}
