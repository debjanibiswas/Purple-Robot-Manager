package edu.northwestern.cbits.purple_robot_manager.probes.funf;

import edu.northwestern.cbits.purple_robot_manager.R;
import android.content.Context;
import android.content.SharedPreferences;
import android.os.Bundle;
import android.preference.PreferenceActivity;
import android.preference.PreferenceManager;
import android.preference.PreferenceScreen;

public class HardwareInfoProbe extends PeriodFunfProbe
{
	public String funfName()
	{
		return "edu.mit.media.funf.probe.builtin.HardwareInfoProbe";
	}

	public String key()
	{
		return "hardware_info";
	}

	protected int funfTitle()
	{
		return R.string.title_hardware_info_probe;
	}

	protected int funfSummary()
	{
		return R.string.summary_hardware_info_probe;
	}

	public String probeCategory(Context context)
	{
		return context.getResources().getString(R.string.probe_device_category);
	}
}
