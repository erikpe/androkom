package org.lindev.androkom.text;

import nu.dll.lyskom.Text;

import org.lindev.androkom.KomServer;
import org.lindev.androkom.KomServer.TextInfo;

import android.util.Log;

public class TextFetcher {
    private static final String TAG = "Androkom TextFetcher";

    private final KomServer mKom;
    private final TextCache mTextCache;
    private final Prefetcher mPrefetcher;

    public TextFetcher(final KomServer kom)
    {
        this.mKom = kom;
        this.mTextCache = new TextCache(mKom);
        this.mPrefetcher = new Prefetcher(mKom, mTextCache);
    }

    public TextInfo getKomText(final int textNo) {
        final TextInfo text = mTextCache.getText(textNo);
        mPrefetcher.doCacheRelevant(text.getTextNo());
        return text;
    }

    public TextInfo getNextUnreadText() {
        boolean doCacheRelevant = true;
        return mPrefetcher.getNextUnreadText(doCacheRelevant);
    }

    public TextInfo getParentToText(final int textNo) {
        final Text text;
        try {
            // We can assume this is cached
            Log.d(TAG, "getParentToText:"+textNo);
            text = mKom.getSession().getText(textNo);
        } catch (final Exception e) {
            Log.d(TAG, "getParentToText " + e);
            e.printStackTrace();
            mKom.reconnect();
            return TextInfo.createText(mKom.getBaseContext(), TextInfo.ERROR_FETCHING_TEXT);
        }

        final int commented[] = text.getCommented();
        if (commented.length > 0) {
            Log.d(TAG, "number of parents:"+commented.length);
            final TextInfo textInfo = mTextCache.getText(commented[0]);
            mPrefetcher.doCacheRelevant(textInfo.getTextNo());
            return textInfo;
        }
        Log.d(TAG, "No parent found");
        return TextInfo.createText(mKom.getBaseContext(), TextInfo.NO_PARENT);
    }

    public void restartPrefetcher() {
        mPrefetcher.restart(mKom.getSession().getCurrentConference());
    }

    public void setShowFullHeaders(final boolean showFullHeaders) {
        mTextCache.setShowFullHeaders(showFullHeaders);
    }
}
