package org.lindev.androkom;

import java.io.IOException;
import java.io.UnsupportedEncodingException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import nu.dll.lyskom.ConfInfo;
import nu.dll.lyskom.DynamicSessionInfo;
import nu.dll.lyskom.RpcEvent;
import nu.dll.lyskom.RpcEventListener;
import nu.dll.lyskom.RpcFailure;
import nu.dll.lyskom.Session;
import nu.dll.lyskom.UserArea;

import org.lindev.androkom.AsyncMessages.AsyncMessageSubscriber;
import org.lindev.androkom.WhoIsOn.populatePersonsTask;
import org.lindev.androkom.im.IMLogger;
import org.lindev.androkom.im.IMNotification;
import org.lindev.androkom.text.TextFetcher;

import android.app.Service;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageInfo;
import android.os.Binder;
import android.os.IBinder;
import android.text.Spannable;
import android.util.Log;
import android.widget.Toast;


/**
 * A service which keeps the Lattekom session object and all
 * LysKOM stuff for the various activities in the app.
 * 
 * @author henrik
 *
 */
public class KomServer extends Service implements RpcEventListener,
		nu.dll.lyskom.Log {
	public static final String TAG = "Androkom KomServer";
	public static boolean RELEASE_BUILD = false;

    /**
     * Class for clients to access.  Because we assume this service always
     * runs in the same process as its clients, we don't deal with IPC.
     */
    public class LocalBinder extends Binder
    {
        public KomServer getService()
        {
            return KomServer.this;
        }
    }

    /**
     * Small helper class which maps conference names and LysKOM id's.
     */
    public class ConferenceInfo 
    {
        public int id;
        public String name;
        public int numUnread;

        @Override
        public String toString() 
        {
            return name + " <" + id + ">";
        }
    }

    /**
     * Small helper class to manage texts.
     */
    public static class TextInfo
    {
        public static final int ALL_READ=1;
        public static final int ERROR_FETCHING_TEXT=2;
        public static final int NO_PARENT=3;

        public TextInfo(Context context, int textNo, String author, String date, String headers, String subject, String body, boolean showFullHeaders) {
            this.textNo = textNo;
            this.author = author;
            this.date = date;
            this.headers = headers;
            this.subject = subject;
            this.body = body;
            this.spannable = Conference.formatText(context, this, showFullHeaders);
        }

        public static TextInfo createText(Context context, int id) {
            switch (id) {
            case ALL_READ:
                Log.d(TAG, "createText ALL_READ");
                return new TextInfo(context, -1, "", "", "", "", context
                        .getString(R.string.all_read), false);
            case ERROR_FETCHING_TEXT:
                Log.d(TAG, "createText ERROR_FETCHING_TEXT");
                return new TextInfo(context, -1, "", "", "", "", context
                        .getString(R.string.error_fetching_text), false);
            case NO_PARENT:
                Log.d(TAG, "createText NO_PARENT");
                return new TextInfo(context, -1, "", "", "", "", context
                        .getString(R.string.error_no_parent), false);
            default:
                Log.d(TAG, "createText default");
                return new TextInfo(context, -1, "", "", "", "", context
                        .getString(R.string.error_fetching_text), false);
            }
        }

        public String getAuthor() {
			return author;
		}

		public String getBody() {
			return body;
		}

		public String getHeaders() {
			return headers;
		}

		public String getSubject() {
			return subject;
		}

		public String getDate() {
			return date;
		}

		public int getTextNo() {
			return textNo;
		}

		public Spannable getSpannable() {
		    return spannable;
		}

        private int textNo;
        private String date;
        private String subject;
        private String headers;
        private String body;
        private String author;
        private Spannable spannable;
    }
    
    public KomServer() {
    	if (!RELEASE_BUILD)
    		System.setProperty("lattekom.debug", "true");
        System.setProperty("lattekom.enable-prefetch", "true"); 
        Session.setLog(this);
        mLastTextNo = -1;
        mPendingSentTexts = new HashSet<Integer>();
    }


    @Override
    public void onCreate() 
    {
        super.onCreate();
        
        Log.d(TAG, "onCreate");
        
        asyncMessagesHandler = new AsyncMessages(getApp(), this);
        asyncMessagesHandler
                .subscribe(asyncMessagesHandler.new MessageToaster());
        
        imLogger = new IMLogger(this);
        new IMNotification(this);
        asyncMessagesHandler.subscribe(imLogger);

        if (s == null) {
            s = new Session();
            s.addRpcEventListener(this);
        }
    }

    @Override
    public IBinder onBind(Intent arg0) 
    {
        Log.d(TAG, "onBind");
        return mBinder;
    }

    public boolean onUnbind (Intent intent) {
        return false;
    }
    
    App getApp() 
    {
        return (App) getApplication();
    }

    /**
     * Called upon destruction of the service. If we're logged in,
     * we want to log out and close the connection now.
     */
    @Override
    public void onDestroy() 
    {
        Log.d(TAG, "onDestroy");

        imLogger.close();
        // Tell the user we stopped.
        Toast.makeText(this, getString(R.string.komserver_stopped), Toast.LENGTH_SHORT).show();

        try {
            if (s.getState() == Session.STATE_LOGIN)
                s.logout(true);
            Log.i("androkom","logged out");

            if (s.getState() == Session.STATE_CONNECTED)
                s.disconnect(false);

            Log.i("androkom","disconnected");           
        } catch (Exception e) {
            // TODO Auto-generated catch block
        	Log.d(TAG, "onDestroy "+e);
            e.printStackTrace();
        }

        s.removeRpcEventListener(this);        
        s = null;

        getApp().shutdown();
        
        super.onDestroy();
    }

    public void logout() {
        Log.d(TAG, "KomServer logout");
        try {
            if (s.getState() == Session.STATE_LOGIN)
                s.logout(true);
            Log.i("androkom", "logged out");

            if (s.getState() == Session.STATE_CONNECTED)
                s.disconnect(false);

            Log.i("androkom", "disconnected");
        } catch (Exception e) {
            // TODO Auto-generated catch block
            Log.d(TAG, "onDestroy " + e);
            e.printStackTrace();
        }

        s.removeRpcEventListener(this);
        s = null;
    }
    
    public void reconnect() {
    	logout();
    	
        Log.d(TAG, "KomServer trying to reconnect");

        s = new Session();
        s.addRpcEventListener(this);

        if (re_userid > 0) {
            Log.d(TAG, "KomServer trying to login using id " + re_userid
                    + " on server " + re_server);
            login(re_userid, re_password, re_server);
        } else {
            Log.d(TAG, "Can't reconnect because no userid");
        }
    }

    /**
     * Connect to LysKOM server.
     * 
     * @return 0 on success, non-zero on failure.
     */
    public int connect(String server) 
    {
        try {
            s.connect(server);
            s.addAsynchMessageReceiver(asyncMessagesHandler);
        } catch (IOException e) {
            // TODO Auto-generated catch block
        	Log.d(TAG, "connect1 "+e);

            e.printStackTrace();
            return -1;
        } catch (Exception e) {
            // TODO Auto-generated catch block
        	Log.d(TAG, "connect2 "+e);

            e.printStackTrace();
            return -1;
        }

        return 0;
    }

    public void disconnect() {
    	try {
    		s.disconnect(true);
		} catch (RpcFailure e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		} catch (Exception e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}
    }

    /**
     * Fetch a list of persons online
     * @param populatePersonsTask 
     */
    public List<ConferenceInfo> fetchPersons(populatePersonsTask populatePersonsT, int who_type)
    {
    	Set<Integer> friendsList=new HashSet<Integer>();
    	
        ArrayList<ConferenceInfo> arr = new ArrayList<ConferenceInfo>();
        if(who_type==2) {
        	friendsList = getFriends();
        }
        
        try {
            DynamicSessionInfo[] persons = s.whoIsOnDynamic(true, false, 30*60);
            populatePersonsT.changeMax(persons.length);
            
			for (int i = 0; i < persons.length; i++) {
				int persNo = persons[i].getPerson();
				if ((who_type == 1) || (friendsList.contains(persNo))) {
					String username;
					if (persNo > 0) {
						try {
							nu.dll.lyskom.Conference confStat = s
									.getConfStat(persNo);
							username = confStat.getNameString();
						} catch (Exception e) {
							username = getString(R.string.person) + persNo
									+ getString(R.string.does_not_exist);
						}
					} else {
					    Log.d(TAG, "fetchPersons persNo="+persNo);
						username = getString(R.string.anonymous);
					}
					Log.i("androkom", username + " <" + persNo + ">");

					ConferenceInfo info = new ConferenceInfo();
					info.id = persNo;
					info.name = username;

					arr.add(info);
				}
				populatePersonsT
				.updateProgress((int) ((i / (float) persons.length) * 100));
			}
        } catch (IOException e) {
            // TODO Auto-generated catch block
        	Log.d(TAG, "fetchPersons1 "+e);
            e.printStackTrace();
            reconnect();
        } catch (Exception e) {
            // TODO Auto-generated catch block
        	Log.d(TAG, "fetchPersons2 "+e);
            e.printStackTrace();
        }

        return arr;
    }
    
    /**
     * Add a new subscriber who's interested in asynchronous messages.
     */
    public void addAsyncSubscriber(AsyncMessageSubscriber sub) {
    	asyncMessagesHandler.subscribe(sub);
    }
    
    /**
     * Add a new subscriber who's interested in asynchronous messages.
     */
    public void removeAsyncSubscriber(AsyncMessageSubscriber sub) {
    	asyncMessagesHandler.unsubscribe(sub);
    }

    /**
     * Fetch a list of conferences with unread texts.
     */
    public List<ConferenceInfo> fetchConferences() {
        List<ConferenceInfo> arr = new ArrayList<ConferenceInfo>();
        try {
            for (int conf : s.getMyUnreadConfsList(true)) {
                final String name = s.toString(s.getConfName(conf));
                Log.i(TAG, name + " <" + conf + ">");
                final ConferenceInfo info = new ConferenceInfo();
                info.id = conf;
                info.name = name;
                info.numUnread = s.getUnreadCount(conf);
                arr.add(info);
            }
        }
        catch (final IOException e) {
            e.printStackTrace();
        }
        return arr;
    }

    /**
     * Return name for given conference.
     */
    public String getConferenceName(int conf)
    {
    	try {
			return s.toString(s.getConfName(conf));
		} catch (Exception e) {
			// TODO Auto-generated catch block
        	Log.d(TAG, "getConferenceName "+e);

			e.printStackTrace();
		}
		return "";
    }
    

    private String mConfName = "";
    public void setConferenceName(final String name) {
        mConfName = name;
    }

    /**
     * Return name for given conference.
     */
    public String getConferenceName() {
        return mConfName;
    }

    /**
     * Return presentation text number for current conference.
     */
	public int getConferencePres() {
		int confNo = s.getCurrentConference();
		if (confNo > 0) {
			try {
				return s.getConfStat(confNo).getPresentation();
			} catch (RpcFailure e) {
				// TODO Auto-generated catch block
				e.printStackTrace();
			} catch (IOException e) {
				// TODO Auto-generated catch block
				e.printStackTrace();
			}
		}
		return 0;
	}

    /**
     * Return number of unreads for current conference.
     */
    public int getConferenceUnreadsNo() {
        int confNo = s.getCurrentConference();
        if (confNo > 0) {
            try {
                return s.getUnreadCount(confNo);
            } catch (RpcFailure e) {
                // TODO Auto-generated catch block
                e.printStackTrace();
            } catch (IOException e) {
                // TODO Auto-generated catch block
                e.printStackTrace();
            }
        }
        Log.d(TAG, "getConferenceUnreadsNo no current conference (or exception)");
        return 0;
    }

    /**
     * Set currently active conference.
     */
    public void setConference(final int confNo) {
        try {
            s.changeConference(confNo);
            readMarker.clear();
            textFetcher.restartPrefetcher();
        } catch (Exception e) {
            Log.i(TAG, "setConference " + e);
            e.printStackTrace();
        }
    }

    /**
     * Log in to server. 
     * 
     * @return Empty string on success, string describing failure otherwise
     */
    public String login(String username, String password, String server) 
    {
    	Log.d(TAG, "Trying to login username:"+username);

    	if (s == null) {
            s = new Session();
            s.addRpcEventListener(this);
        }

    	try {
    		if (!s.getConnected()) {
    			if (connect(server) != 0)
    				return getString(R.string.error_could_not_connect);
    		}
        } catch (Exception e) {
            Log.e("androkom", "Login.name connect Caught " + e.getClass().getName()+":"+e+":"+e.getCause());
            e.printStackTrace();
            return getString(R.string.error_unknown);
        }

        usernames = new ConfInfo[0];
        try {
            usernames = s.lookupName(username, true, false);
            if (usernames.length != 1) {            
                return getString(R.string.error_ambigious_name);
            } else {
                // login as hidden
                if (!s.login(usernames[0].confNo, password, hidden_session, false)) {
                    return getString(R.string.error_invalid_password);
                }
            }
        } catch (Exception e) {
            Log.e("androkom", "Login.name Caught " + e.getClass().getName()+":"+e+":"+e.getCause());
            e.printStackTrace();
            return getString(R.string.error_unknown);
        }
        try {
            s.setClientVersion("Androkom", getVersionName());
            s.setLatteName("AndroKOM " + getVersionName());
        } catch (Exception e) {
        	Log.e("androkom", "Login.name2 Caught " + e.getClass().getName()+":"+e+":"+e.getCause());
        	e.printStackTrace();
        }
        re_userid = usernames[0].confNo;
        re_password = password;
        re_server = server;

        parseCommonUserArea();
        parseElispUserArea();
        return "";
    }

    /**
     * Log in to server. 
     * 
     * @return Empty string on success, string describing failure otherwise
     */
    public String login(int userid, String password, String server) 
    {
    	Log.d(TAG, "Trying to login userid:"+userid);
        usernames = new ConfInfo[0];
        if (!s.getConnected()) {
            if (connect(server) != 0)
                return getString(R.string.error_could_not_connect);
        }

        try {
        	// login as hidden
        	if (!s.login(userid, password, hidden_session, false)) {
        		return getString(R.string.error_invalid_password);
        	}
        	s.setClientVersion("Androkom", getVersionName());
        } catch (Exception e) {
            Log.e("androkom", "Login.id Caught " + e.getClass().getName()+e.getStackTrace());
            return "Unknown error";
        }
        re_userid = userid;
        re_password = password;
        re_server = server;
        
        return "";
    }

    public String getVersionName() {
    	try {
    		PackageInfo pinfo = getBaseContext().getPackageManager().getPackageInfo("org.lindev.androkom", 0);
    		return pinfo.versionName;
    	} catch (android.content.pm.PackageManager.NameNotFoundException e) {
        return "unknown";
      }
    }

    public void endast(int confNo, int no) {
    	try {
			s.endast(confNo, no);
		} catch (RpcFailure e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		} catch (IOException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}
    }
    
    public Session getSession()
    {
        return s;
    }

    /**
     * Fetch next unread text, as a HTML formatted string. 
     */
    private final TextFetcher textFetcher = new TextFetcher(this);
    public TextInfo getKomText(final int textNo) {
        return textFetcher.getKomText(textNo);
    }

    public TextInfo getNextUnreadText() {
        return textFetcher.getNextUnreadText();
    }

    public TextInfo getParentToText(final int textNo) {
        return textFetcher.getParentToText(textNo);
    }

    private final ReadMarker readMarker = new ReadMarker(this);
    public void markTextAsRead(final int textNo) {
        readMarker.mark(textNo);
    }

    public boolean isLocalRead(final int textNo) {
        return readMarker.isLocalRead(textNo);
    }

    public void markText(int textNo)
    {
    	try {
			s.markText(textNo, 100);
		} catch (RpcFailure e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		} catch (IOException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}
    }

    public void unmarkText(int textNo)
    {
    	try {
			s.unmarkText(textNo);
		} catch (RpcFailure e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		} catch (IOException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}
    }

    /**
     * Get text number of last read text in current meeting, 
     * or -1 if there is no suitable text.
     */
    public int getLastTextNo()
    {
        return mLastTextNo;
    }

	String[] getNextHollerith(String s) {
		s = s.trim();
		int prefixLen = s.indexOf("H");

        int len = Integer.parseInt(s.substring(0, prefixLen));

        prefixLen++;
        String first = s.substring(prefixLen, prefixLen + len);

		String second;
		if (s.length() > first.length() + prefixLen + 1)
			second = s.substring(first.length() + prefixLen + 1);
		else
			second = "";

		return new String[] { first, second };
	}

	/**
	 * Parse properties from the common area, if any.
	 */
	void parseCommonUserArea() {
		try {
			UserArea ua = s.getUserArea();
			String[] blocks = ua.getBlockNames();

			mCommonUserAreaProps = new HashMap<String, String>();

			for (String block : blocks) {

				if (block.equals("common")) {
					String token = ua.getBlock(block).getContentString();
					while (token.length() > 0) {
						String[] first = getNextHollerith(token);
						String[] second = getNextHollerith(first[1]);

						mCommonUserAreaProps.put(first[0], second[0]);
						token = second[1];
					}
				}

			}
		} catch (Exception e) {
			// TODO Auto-generated catch block
			Log.d(TAG, "parseCommonUserArea " + e);

			e.printStackTrace();
		}
	}

	/**
	 * Get a property of presence-messages
	 */
	public boolean getPresenceMessages() {
	    boolean presence_messages = true;

        if (mCommonUserAreaProps != null) {
            String messages = mCommonUserAreaProps.get("presence-messages");
            if (messages != null) {
                presence_messages = (messages.compareTo("1") == 0);
            }
        }
		return presence_messages;
	}

	/**
	 * Parse properties the elisp client has set, if any.
	 */
	void parseElispUserArea() {
		try {

			UserArea ua = s.getUserArea();
			String[] blocks = ua.getBlockNames();

			mElispUserAreaProps = new HashMap<String, String>();

			for (String block : blocks) {

				if (block.equals("elisp")) {
					String token = ua.getBlock(block).getContentString();
					while (token.length() > 0) {
						String[] first = getNextHollerith(token);
						String[] second = getNextHollerith(first[1]);

						mElispUserAreaProps.put(first[0], second[0]);
						token = second[1];
					}
				}

			}
		} catch (Exception e) {
			// TODO Auto-generated catch block
			Log.d(TAG, "parseElispUserArea " + e);

			e.printStackTrace();
		}
	}

	/**
	 * Get a list of the IDs of all friends which are set in the elisp client
	 * user area.
	 */
	public Set<Integer> getFriends() {
		Set<Integer> friendsList=new HashSet<Integer>();
		
		String friends = mElispUserAreaProps.get("kom-friends");
		if (friends != null) {
			friends = friends.substring(1, friends.length() - 2);
			String[] friendList = friends.split(" ");
			for (String friend : friendList) {
				Log.i("androkom", "friend " + friend);
				friendsList.add(Integer.parseInt(friend));
			}
		}
		return friendsList;
	}

	public void rpcEvent(RpcEvent e) {
		if (mPendingSentTexts.contains(e.getId())) {
			Log.i("androkom", "Got reply for created text " + e.getId());

			if (!e.getSuccess()) {
				/* TODO: handle error here */;
				Log.d(TAG, "rpcEvent failed " + e);
			}
		}

	}

	public void setLatestIMSender(String name) {
		latestIMSender = name;
	}
	
	public String getLatestIMSender() {
		return latestIMSender;
	}
	
	public boolean sendMessage(int recipient, String message, boolean block)
			throws IOException, RpcFailure {
		final boolean res = s.sendMessage(recipient, message, block);
		imLogger.sendMessage(recipient, message);
		return res;
	}

	public void setShowFullHeaders(final boolean h) {
		textFetcher.setShowFullHeaders(h);
	}

	public ConferenceInfo[] getUserNames() {
		try {
			if (usernames != null && usernames.length > 1) {
				final ConferenceInfo[] items = new ConferenceInfo[usernames.length];
				Log.d(TAG, "Ambigous name");
				for (int i = 0; i < usernames.length; i++) {
					items[i] = new ConferenceInfo();
					items[i].name = s.toString(s
							.getConfName(usernames[i].confNo));
					items[i].id = usernames[i].confNo;
					Log.d(TAG, "Name " + i + ":" + items[i]);
				}
				return items;
			}

		} catch (UnsupportedEncodingException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		} catch (RpcFailure e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		} catch (IOException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}
		return null;
	}

    public int getUserId() {
        return re_userid;
    }

	public boolean isConnected() {
		if (s == null) {
			return false;
		}
		return s.getConnected();
	}

	public void error(String s) {
		Log.e("androkom", s);
	}

	public void debug(String s) {
		Log.d("androkom KomServer", s);
	}

	private Session s = null;

	private int mLastTextNo = 0;
	HashMap<String, String> mElispUserAreaProps = null;
    HashMap<String, String> mCommonUserAreaProps = null;

	// This is the object that receives interactions from clients.
	private final IBinder mBinder = new LocalBinder();

	public HashSet<Integer> mPendingSentTexts;
	ConfInfo usernames[];
	private int re_userid; // for reconnect, note: none of these are saved during screen rotation
	private String re_password; // for reconnect
	private String re_server; // for reconnect

	private String latestIMSender=""; // for creating new IM
	
	private boolean hidden_session = !RELEASE_BUILD;

	public AsyncMessages asyncMessagesHandler;
	public IMLogger imLogger;
}
