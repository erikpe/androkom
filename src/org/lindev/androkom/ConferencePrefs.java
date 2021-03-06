package org.lindev.androkom;

import android.content.Context;
import android.os.Bundle;
import android.preference.PreferenceActivity;
import android.preference.PreferenceManager;

public class ConferencePrefs extends PreferenceActivity {
	private static final String OPT_SERVER = "server";
	private static final String OPT_SERVER_DEF = "";

	private static final String OPT_SAVEPSW = "savepsw";
	private static final Boolean OPT_SAVEPSW_DEF = true;

	private static final String OPT_AUTOLOGIN = "autologin";
	private static final Boolean OPT_AUTOLOGIN_DEF = false;

	private static final String OPT_SHOWFULLHEADERS = "showfullheaders";
	private static final Boolean OPT_SHOWFULLHEADERS_DEF = false;

    private static final String OPT_MARKTEXTREAD = "marktextread";
    private static final Boolean OPT_MARKTEXTREAD_DEF = false;
	
    private static final String OPT_TOASTFORASYNCH = "toastforasynch";
    private static final Boolean OPT_TOASTFORASYNCH_DEF = false;

    private static final String OPT_PREFERREDLANGUAGE = "preferredlanguage";
    private static final String OPT_PREFERREDLANGUAGE_DEF = "";

    protected void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);
		addPreferencesFromResource(R.xml.conference_settings);
	}

	public static String getServer(Context context) {
		return PreferenceManager.getDefaultSharedPreferences(context).getString(OPT_SERVER, OPT_SERVER_DEF);
	}

	public static Boolean getSavePsw(Context context) {
		return PreferenceManager.getDefaultSharedPreferences(context).getBoolean(OPT_SAVEPSW, OPT_SAVEPSW_DEF);
	}

	public static Boolean getAutologin(Context context) {
		return PreferenceManager.getDefaultSharedPreferences(context).getBoolean(OPT_AUTOLOGIN, OPT_AUTOLOGIN_DEF);
	}

	public static Boolean getShowFullHeaders(Context context) {
		return PreferenceManager.getDefaultSharedPreferences(context).getBoolean(OPT_SHOWFULLHEADERS, OPT_SHOWFULLHEADERS_DEF);
	}

    public static Boolean getMarkTextRead(Context context) {
        return PreferenceManager.getDefaultSharedPreferences(context).getBoolean(OPT_MARKTEXTREAD, OPT_MARKTEXTREAD_DEF);
    }

    public static Boolean getToastForAsynch(Context context) {
        return PreferenceManager.getDefaultSharedPreferences(context).getBoolean(OPT_TOASTFORASYNCH, OPT_TOASTFORASYNCH_DEF);
    }

    public static String getPreferredLanguage(Context context) {
        return PreferenceManager.getDefaultSharedPreferences(context).getString(OPT_PREFERREDLANGUAGE, OPT_PREFERREDLANGUAGE_DEF);
    }
}
