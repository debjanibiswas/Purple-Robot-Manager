package edu.northwestern.cbits.purple_robot_manager.probes.builtin;

import java.io.UnsupportedEncodingException;
import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.Locale;
import java.util.Map;

import javax.crypto.BadPaddingException;
import javax.crypto.IllegalBlockSizeException;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;
import org.scribe.builder.ServiceBuilder;
import org.scribe.builder.api.TwitterApi;
import org.scribe.exceptions.OAuthException;
import org.scribe.model.OAuthRequest;
import org.scribe.model.Response;
import org.scribe.model.Token;
import org.scribe.model.Verb;
import org.scribe.oauth.OAuthService;

import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.SharedPreferences.Editor;
import android.os.Bundle;
import android.preference.CheckBoxPreference;
import android.preference.ListPreference;
import android.preference.Preference;
import android.preference.Preference.OnPreferenceClickListener;
import android.preference.PreferenceActivity;
import android.preference.PreferenceManager;
import android.preference.PreferenceScreen;
import android.widget.Toast;
import edu.northwestern.cbits.purple_robot_manager.EncryptionManager;
import edu.northwestern.cbits.purple_robot_manager.R;
import edu.northwestern.cbits.purple_robot_manager.activities.OAuthActivity;
import edu.northwestern.cbits.purple_robot_manager.logging.LogManager;
import edu.northwestern.cbits.purple_robot_manager.logging.SanityCheck;
import edu.northwestern.cbits.purple_robot_manager.logging.SanityManager;
import edu.northwestern.cbits.purple_robot_manager.probes.Probe;

public class TwitterProbe extends Probe
{
	public static final String CONSUMER_KEY = "q2JL6t5LenW1f8DPFqag";
	public static final String CONSUMER_SECRET = "zsB6bELsZfj47kRHvXfinvBSgbddTzq7vgyqg5E3jn0";

	private static final boolean DEFAULT_ENABLED = false;
	private static final boolean DEFAULT_ENCRYPT = false;

	protected static final String HOUR_COUNT = "HOUR_COUNT";

	private long _lastCheck = 0;
	
	private String _token = null;
	private String _secret = null;

	public String name(Context context)
	{
		return "edu.northwestern.cbits.purple_robot_manager.probes.builtin.TwitterProbe";
	}

	public String title(Context context)
	{
		return context.getString(R.string.title_twitter_probe);
	}

	public String probeCategory(Context context)
	{
		return context.getResources().getString(R.string.probe_external_services_category);
	}

	public void enable(Context context)
	{
		SharedPreferences prefs = Probe.getPreferences(context);
		
		Editor e = prefs.edit();
		e.putBoolean("config_probe_twitter_enabled", true);
		
		e.commit();
	}

	public void disable(Context context)
	{
		SharedPreferences prefs = Probe.getPreferences(context);
		
		Editor e = prefs.edit();
		e.putBoolean("config_probe_twitter_enabled", false);
		
		e.commit();
	}

	public boolean isEnabled(final Context context)
	{
		final SharedPreferences prefs = Probe.getPreferences(context);

		if (super.isEnabled(context))
		{
			final long now = System.currentTimeMillis();

			if (prefs.getBoolean("config_probe_twitter_enabled", TwitterProbe.DEFAULT_ENABLED))
			{
				synchronized(this)
				{
					long freq = Long.parseLong(prefs.getString("config_probe_twitter_frequency", Probe.DEFAULT_FREQUENCY));
					final boolean doEncrypt = prefs.getBoolean("config_probe_twitter_encrypt_data", TwitterProbe.DEFAULT_ENCRYPT);
					
					final EncryptionManager em = EncryptionManager.getInstance();
					
					if (now - this._lastCheck  > freq)
					{
						final TwitterProbe me = this;

						this._token = prefs.getString("oauth_twitter_token", null);
						this._secret = prefs.getString("oauth_twitter_secret", null);

	    				final String title = context.getString(R.string.title_twitter_check);
	    				final SanityManager sanity = SanityManager.getInstance(context);

	        			if (this._token == null || this._secret == null)
	        			{
	        				String message = context.getString(R.string.message_twitter_check);
	        				
	        				Runnable action = new Runnable()
	        				{
								public void run() 
								{
									me.fetchAuth(context);
								}
	        				};
	        				
	        				sanity.addAlert(SanityCheck.WARNING, title, message, action);
	        			}
						else
						{
							sanity.clearAlert(title);

							Token accessToken = new Token(this._token, this._secret);
							
		                	ServiceBuilder builder = new ServiceBuilder();
		                	builder = builder.provider(TwitterApi.class);
		                	builder = builder.apiKey(TwitterProbe.CONSUMER_KEY);
		                	builder = builder.apiSecret(TwitterProbe.CONSUMER_SECRET);
		                	
		                	final OAuthService service = builder.build();
		                	
							final OAuthRequest request = new OAuthRequest(Verb.GET, "https://api.twitter.com/1.1/statuses/user_timeline.json");
							service.signRequest(accessToken, request);
	
							Runnable r = new Runnable()
							{
								public void run() 
								{
									try 
									{
										SimpleDateFormat sdf = new SimpleDateFormat("EEE MMM dd HH:mm:ss ZZZZZ yyyy", Locale.ENGLISH);
										sdf.setLenient(true);

										long mostRecent = prefs.getLong("config_twitter_most_recent", 0);
										
										Response response = request.send();

										JSONArray tweets = new JSONArray(response.getBody());
										
										for (int i = (tweets.length() - 1); i >= 0; i--)
										{
											JSONObject tweet = tweets.getJSONObject(i);
											
											long tweetTime = sdf.parse(tweet.getString("created_at")).getTime();

											if (tweetTime > mostRecent)
											{
												Bundle eventBundle = new Bundle();
												eventBundle.putString("PROBE", me.name(context));
												eventBundle.putLong("TIMESTAMP", tweetTime / 1000);
												eventBundle.putString("LANGUAGE", tweet.getString("lang"));
												eventBundle.putInt("FAVORITE_COUNT", tweet.getInt("favorite_count"));
												eventBundle.putInt("RETWEET_COUNT", tweet.getInt("retweet_count"));
												eventBundle.putLong("TWEET_ID", tweet.getLong("id"));
												eventBundle.putString("SOURCE", tweet.getString("source"));
												
												JSONObject user = tweet.getJSONObject("user");
												
												eventBundle.putString("SCREENNAME", user.getString("screen_name"));
												
												String respondent = tweet.getString("in_reply_to_screen_name");
												
												if (respondent != null)
													eventBundle.putString("RESPONDENT", respondent);
												
												String message = tweet.getString("text");

												try 
												{	
													if (doEncrypt)
														message = em.encryptString(context, message);

													eventBundle.putString("CONTENT", message);
												}
												catch (IllegalBlockSizeException e) 
												{
													LogManager.getInstance(context).logException(e);
												} 
												catch (BadPaddingException e) 
												{
													LogManager.getInstance(context).logException(e);
												} 
												catch (UnsupportedEncodingException e) 
												{
													LogManager.getInstance(context).logException(e);
												}

												eventBundle.putBoolean("IS_OBFUSCATED", doEncrypt);
												me.transmitData(context, eventBundle);
											}
										}
										
										Editor e = prefs.edit();
										e.putLong("config_twitter_most_recent", System.currentTimeMillis());
										e.commit();
									} 
									catch (JSONException e) 
									{
										e.printStackTrace();
									}
									catch (OAuthException e)
									{
										e.printStackTrace();
									} 
									catch (ParseException e) 
									{
										e.printStackTrace();
									}
								}
							};
							
							Thread t = new Thread(r);
							t.start();
						}
	        			
						me._lastCheck = now;
					}
				}

				return true;
			}
		}

		return false;
	}

	private void fetchAuth(Context context)
	{
        Intent intent = new Intent(context, OAuthActivity.class);
		intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
		
		intent.putExtra(OAuthActivity.CONSUMER_KEY, CONSUMER_KEY);
		intent.putExtra(OAuthActivity.CONSUMER_SECRET, CONSUMER_SECRET);
		intent.putExtra(OAuthActivity.REQUESTER, "twitter");
		intent.putExtra(OAuthActivity.CALLBACK_URL, "http://pr-oauth/oauth/twitter");
		
		context.startActivity(intent);
	}

	public String summarizeValue(Context context, Bundle bundle)
	{
		String screenname = bundle.getString("SCREENNAME");
		String message = bundle.getString("CONTENT");
		
		return "@" + screenname + ": " + message;
	}
	
	public Map<String, Object> configuration(Context context)
	{
		Map<String, Object> map = super.configuration(context);
		
		SharedPreferences prefs = Probe.getPreferences(context);

		long freq = Long.parseLong(prefs.getString("config_probe_twitter_frequency", Probe.DEFAULT_FREQUENCY));
		map.put(Probe.PROBE_FREQUENCY, freq);
		
		boolean hash = prefs.getBoolean("config_probe_twitter_encrypt_data", TwitterProbe.DEFAULT_ENCRYPT);
		map.put(Probe.ENCRYPT_DATA, hash);

		return map;
	}

	public void updateFromMap(Context context, Map<String, Object> params) 
	{
		super.updateFromMap(context, params);
		
		if (params.containsKey(Probe.PROBE_FREQUENCY))
		{
			Object frequency = params.get(Probe.PROBE_FREQUENCY);
			
			if (frequency instanceof Long)
			{
				SharedPreferences prefs = Probe.getPreferences(context);
				Editor e = prefs.edit();
				
				e.putString("config_probe_twitter_frequency", frequency.toString());
				e.commit();
			}
		}
		
		if (params.containsKey(Probe.ENCRYPT_DATA))
		{
			Object encrypt = params.get(Probe.ENCRYPT_DATA);
			
			if ( encrypt instanceof Boolean)
			{
				Boolean encryptBoolean = (Boolean)  encrypt;
				
				SharedPreferences prefs = Probe.getPreferences(context);
				Editor e = prefs.edit();
				
				e.putBoolean("config_probe_twitter_encrypt_data", encryptBoolean.booleanValue());
				e.commit();
			}
		}
	}

	@SuppressWarnings("deprecation")
	public PreferenceScreen preferenceScreen(final PreferenceActivity activity)
	{
		PreferenceManager manager = activity.getPreferenceManager();

		final PreferenceScreen screen = manager.createPreferenceScreen(activity);
		screen.setTitle(this.title(activity));
		screen.setSummary(R.string.summary_twitter_probe_desc);

		final SharedPreferences prefs = Probe.getPreferences(activity);

		String token = prefs.getString("oauth_twitter_token", null);
		String secret = prefs.getString("oauth_twitter_secret", null);

		CheckBoxPreference enabled = new CheckBoxPreference(activity);
		enabled.setTitle(R.string.title_enable_probe);
		enabled.setKey("config_probe_twitter_enabled");
		enabled.setDefaultValue(TwitterProbe.DEFAULT_ENABLED);

		screen.addPreference(enabled);

		ListPreference duration = new ListPreference(activity);
		duration.setKey("config_probe_twitter_frequency");
		duration.setEntryValues(R.array.probe_low_frequency_values);
		duration.setEntries(R.array.probe_low_frequency_labels);
		duration.setTitle(R.string.probe_frequency_label);
		duration.setDefaultValue(Probe.DEFAULT_FREQUENCY);

		screen.addPreference(duration);

		CheckBoxPreference encrypt = new CheckBoxPreference(activity);
		encrypt.setKey("config_probe_twitter_encrypt_data");
		encrypt.setDefaultValue(TwitterProbe.DEFAULT_ENCRYPT);
		encrypt.setTitle(R.string.config_probe_twitter_encrypt_title);
		encrypt.setSummary(R.string.config_probe_twitter_encrypt_summary);

		screen.addPreference(encrypt);

		final Preference authPreference = new Preference(activity);
		authPreference.setTitle(R.string.title_authenticate_twitter_probe);
		authPreference.setSummary(R.string.summary_authenticate_twitter_probe);

		final TwitterProbe me = this;
		
		final Preference logoutPreference = new Preference(activity);
		logoutPreference.setTitle(R.string.title_logout_twitter_probe);
		logoutPreference.setSummary(R.string.summary_logout_twitter_probe);

		authPreference.setOnPreferenceClickListener(new OnPreferenceClickListener()
		{
			public boolean onPreferenceClick(Preference preference) 
			{
				me.fetchAuth(activity);
				
				screen.addPreference(logoutPreference);
				screen.removePreference(authPreference);

				return true;
			}
		});
		
		logoutPreference.setOnPreferenceClickListener(new OnPreferenceClickListener()
		{
			public boolean onPreferenceClick(Preference preference) 
			{
				Editor e = prefs.edit();
				e.remove("oauth_twitter_token");
				e.remove("oauth_twitter_secret");
				e.commit();
				
				screen.addPreference(authPreference);
				screen.removePreference(logoutPreference);
				
				activity.runOnUiThread(new Runnable()
				{
					public void run() 
					{
						Toast.makeText(activity, activity.getString(R.string.toast_twitter_logout), Toast.LENGTH_LONG).show();
					}
				});

				return true;
			}
		});
		
		if (token == null || secret == null)
			screen.addPreference(authPreference);
		else
			screen.addPreference(logoutPreference);

		return screen;
	}
}
