package com.egoclean.twitvid.example;

import android.app.ProgressDialog;
import android.content.Context;
import android.content.SharedPreferences;
import android.os.AsyncTask;
import android.preference.PreferenceManager;
import twitter4j.Twitter;
import twitter4j.TwitterException;
import twitter4j.TwitterFactory;
import twitter4j.auth.AccessToken;

/**
 * Contains utility methods for twitter
 * @author cristian
 */
class TwitterUtils {
    public static final int LOGGED_IN = 0;
    private static final int NOT_LOGGED_IN = 1;
    private static final int NETWORK_PROBLEMS = 2;
    private static final int OAUTH_PROBLEMS = 3;

    public static final String TOKEN_KEY = "token";
    public static final String TOKEN_SECRET_KEY = "token_secret";

    /**
     * Checks whether the given twitter object contains a valid session
     * @param context a context
     * @param twitter the twitter object to verify
     * @return LOGGED_IN, NOT_LOGGED_IN, NETWORK_PROBLEMS or OAUTH_PROBLEMS
     */
    private static int isAuthenticated(Context context, Twitter twitter) {
        SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(context);
        String token = prefs.getString(TOKEN_KEY, null);
        String secret = prefs.getString(TOKEN_SECRET_KEY, null);
        if (token == null || secret == null) {
            return NOT_LOGGED_IN;
        }
        try {
            AccessToken accessToken = new AccessToken(token, secret);
            if (twitter == null) {
                twitter = new TwitterFactory().getInstance();
            }
            twitter.setOAuthConsumer(TwitterConstants.CONSUMER_KEY, TwitterConstants.CONSUMER_SECRET);
            twitter.setOAuthAccessToken(accessToken);
        } catch (Exception e) {
            e.printStackTrace();
            return OAUTH_PROBLEMS;
        }

        try {
            if (twitter.getId() > 0) {
                return LOGGED_IN;
            }
        } catch (TwitterException e) {
            e.printStackTrace();
            if (e.isCausedByNetworkIssue()) {
                return NETWORK_PROBLEMS;
            }
        } catch (Exception ignored) {
            ignored.printStackTrace();
        }
        return NOT_LOGGED_IN;
    }

    /**
     * This class provides a way to asynchronously verify whether the user is authenticated or not
     */
    public static class BaseAuthChecker extends AsyncTask<Void, Void, Integer> {
        private final Context context;
        private final OnAuthCheckerResult callback;
        private final ProgressDialog progressDialog;
        private final Twitter twitter;

        public BaseAuthChecker(Context context, OnAuthCheckerResult callback) {
            this.context = context;
            this.callback = callback;
            twitter = new TwitterFactory().getInstance();
            progressDialog = new ProgressDialog(context);
            progressDialog.setMessage(context.getResources().getString(R.string.checking_twitter_auth));
            progressDialog.setCancelable(false);
        }

        @Override
        protected void onPreExecute() {
            super.onPreExecute();
            progressDialog.show();
        }

        @Override
        protected Integer doInBackground(Void... voids) {
            return isAuthenticated(context, twitter);
        }

        @Override
        protected void onPostExecute(Integer result) {
            super.onPostExecute(result);
            progressDialog.dismiss();
            if (callback != null) {
                callback.onAuthChecker(result);
            }
        }

        public interface OnAuthCheckerResult{
            void onAuthChecker(int result);
        }
    }
}
