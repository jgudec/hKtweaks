/*
 * Copyright (C) 2015-2017 Willi Ye <williye97@gmail.com>
 *
 * This file is part of Kernel Adiutor.
 *
 * Kernel Adiutor is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * Kernel Adiutor is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with Kernel Adiutor.  If not, see <http://www.gnu.org/licenses/>.
 *
 */
package com.hades.hKtweaks.activities;

import android.content.Intent;
import android.content.res.Configuration;
import android.os.AsyncTask;
import android.os.Bundle;
import androidx.annotation.Nullable;
import androidx.core.content.ContextCompat;
import android.view.View;
import android.widget.TextView;

import com.google.android.material.color.DynamicColors;
import com.hades.hKtweaks.R;
import com.hades.hKtweaks.database.tools.profiles.Profiles;
import com.hades.hKtweaks.fragments.kernel.BusCamFragment;
import com.hades.hKtweaks.fragments.kernel.BusDispFragment;
import com.hades.hKtweaks.fragments.kernel.BusIntFragment;
import com.hades.hKtweaks.fragments.kernel.BusMifFragment;
import com.hades.hKtweaks.fragments.kernel.CPUVoltageCl0Fragment;
import com.hades.hKtweaks.fragments.kernel.CPUVoltageCl1Fragment;
import com.hades.hKtweaks.fragments.kernel.GPUFragment;
import com.hades.hKtweaks.services.profile.Tile;
import com.hades.hKtweaks.utils.AppSettings;
import com.hades.hKtweaks.utils.Device;
import com.hades.hKtweaks.utils.Utils;
import com.hades.hKtweaks.utils.kernel.battery.Battery;
import com.hades.hKtweaks.utils.kernel.bus.VoltageCam;
import com.hades.hKtweaks.utils.kernel.bus.VoltageDisp;
import com.hades.hKtweaks.utils.kernel.bus.VoltageInt;
import com.hades.hKtweaks.utils.kernel.bus.VoltageMif;
import com.hades.hKtweaks.utils.kernel.cpu.CPUBoost;
import com.hades.hKtweaks.utils.kernel.cpu.CPUFreq;
import com.hades.hKtweaks.utils.kernel.cpu.MSMPerformance;
import com.hades.hKtweaks.utils.kernel.cpu.Temperature;
import com.hades.hKtweaks.utils.kernel.cpuhotplug.Hotplug;
import com.hades.hKtweaks.utils.kernel.cpuhotplug.QcomBcl;
import com.hades.hKtweaks.utils.kernel.cpuvoltage.VoltageCl0;
import com.hades.hKtweaks.utils.kernel.cpuvoltage.VoltageCl1;
import com.hades.hKtweaks.utils.kernel.gpu.GPU;
import com.hades.hKtweaks.utils.kernel.gpu.GPUFreqExynos;
import com.hades.hKtweaks.utils.kernel.io.IO;
import com.hades.hKtweaks.utils.kernel.ksm.KSM;
import com.hades.hKtweaks.utils.kernel.misc.Vibration;
import com.hades.hKtweaks.utils.kernel.screen.Screen;
import com.hades.hKtweaks.utils.kernel.sound.Sound;
import com.hades.hKtweaks.utils.kernel.spectrum.Spectrum;
import com.hades.hKtweaks.utils.kernel.thermal.Thermal;
import com.hades.hKtweaks.utils.kernel.vm.ZSwap;
import com.hades.hKtweaks.utils.kernel.wake.Wake;
import com.hades.hKtweaks.utils.kernel.boefflawakelock.BoefflaWakelock;
import com.hades.hKtweaks.utils.root.RootUtils;

import java.lang.ref.WeakReference;

/**
 * Created by willi on 14.04.16.
 */
public class MainActivity extends BaseActivity {

    private TextView mRootAccess;
    private TextView mBusybox;
    private TextView mCollectInfo;

    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        DynamicColors.applyToActivitiesIfAvailable(this.getApplication());

        setContentView(R.layout.activity_main);

        View splashBackground = findViewById(R.id.splash_background);
        mRootAccess = findViewById(R.id.root_access_text);
        mBusybox = findViewById(R.id.busybox_text);
        mCollectInfo = findViewById(R.id.info_collect_text);

        // Hide huge banner in landscape mode
        if (Utils.getOrientation(this) == Configuration.ORIENTATION_LANDSCAPE) {
            splashBackground.setVisibility(View.GONE);
        }

        if (savedInstanceState == null) {
            /*
             * Launch password activity when one is set,
             * otherwise run {@link #CheckingTask}
             */
            String password;
            if (!(password = AppSettings.getPassword(this)).isEmpty()) {
                Intent intent = new Intent(this, SecurityActivity.class);
                intent.putExtra(SecurityActivity.PASSWORD_INTENT, password);
                startActivityForResult(intent, 1);
            } else {
                new CheckingTask(this).execute();
            }
        }
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        /*
         * 1: Password check result
         */
        if (requestCode == 1) {
            /*
             * 0: Password is wrong
             * 1: Password is correct
             */
            if (resultCode == 1) {
                new CheckingTask(this).execute();
            } else {
                finish();
            }
        }
    }

    private void launch() {
        Intent intent = new Intent(this, NavigationActivity.class);
        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TOP);
        if (getIntent().getExtras() != null) {
            intent.putExtras(getIntent().getExtras());
        }
        startActivity(intent);
        finish();
        overridePendingTransition(android.R.anim.fade_in, android.R.anim.fade_out);
    }

    private static class CheckingTask extends AsyncTask<Void, Integer, Void> {

        private final WeakReference<MainActivity> mRefActivity;

        private boolean mHasRoot;
        private boolean mHasBusybox;

        private CheckingTask(MainActivity activity) {
            mRefActivity = new WeakReference<>(activity);
        }

        private void checkInitVariables(){

            //Initialize Boeffla Wakelock Blocker Files
            if(BoefflaWakelock.supported()) {
                BoefflaWakelock.CopyWakelockBlockerDefault();
            }

            // If voltages are saved on Service.java, mVoltageSaved = 1
            int mVoltageSaved = Utils.strToInt(RootUtils.getProp("hKtweaks.voltage_saved"));

            // Check if system is rebooted
            boolean mIsBooted = AppSettings.getBoolean("is_booted", true, mRefActivity.get());
            if (mIsBooted) {
                // reset the Global voltages seekbar
                if (!AppSettings.getBoolean("cpucl1voltage_onboot", false, mRefActivity.get())) {
                    AppSettings.saveInt("CpuCl1_seekbarPref_value", CPUVoltageCl1Fragment.mDefZeroPosition, mRefActivity.get());
                }
                if (!AppSettings.getBoolean("cpucl0voltage_onboot", false, mRefActivity.get())) {
                    AppSettings.saveInt("CpuCl0_seekbarPref_value", CPUVoltageCl0Fragment.mDefZeroPosition, mRefActivity.get());
                }
                if (!AppSettings.getBoolean("busMif_onboot", false, mRefActivity.get())) {
                    AppSettings.saveInt("busMif_seekbarPref_value", BusMifFragment.mDefZeroPosition, mRefActivity.get());
                }
                if (!AppSettings.getBoolean("busInt_onboot", false, mRefActivity.get())) {
                    AppSettings.saveInt("busInt_seekbarPref_value", BusIntFragment.mDefZeroPosition, mRefActivity.get());
                }
                if (!AppSettings.getBoolean("busDisp_onboot", false, mRefActivity.get())) {
                    AppSettings.saveInt("busDisp_seekbarPref_value", BusDispFragment.mDefZeroPosition, mRefActivity.get());
                }
                if (!AppSettings.getBoolean("busCam_onboot", false, mRefActivity.get())) {
                    AppSettings.saveInt("busCam_seekbarPref_value", BusCamFragment.mDefZeroPosition, mRefActivity.get());
                }
                if (!AppSettings.getBoolean("gpu_onboot", false, mRefActivity.get())) {
                    AppSettings.saveInt("gpu_seekbarPref_value", GPUFragment.mDefZeroPosition, mRefActivity.get());
                }

                // update spectrum support and profile
                AppSettings.saveBoolean("spectrum_supported", Spectrum.suSupported(), mRefActivity.get());
                AppSettings.saveInt("spectrum_profile", Spectrum.getSuProfile(), mRefActivity.get());
            }
            AppSettings.saveBoolean("is_booted", false, mRefActivity.get());

            // Check if exist /data/.hKtweaks folder
            if (!Utils.existFile("/data/.hKtweaks")) {
                RootUtils.runCommand("mkdir /data/.hKtweaks");
            }

            // Check if kernel is changed
            String kernel_old = AppSettings.getString("kernel_version_old", "", mRefActivity.get());
            String kernel_new = Device.getKernelVersion(true);

            if (!kernel_old.equals(kernel_new)){
                // Reset max limit of max_poll_percent
                AppSettings.saveBoolean("max_pool_percent_saved", false, mRefActivity.get());
                AppSettings.saveBoolean("memory_pool_percent_saved", false, mRefActivity.get());
                AppSettings.saveString("kernel_version_old", kernel_new, mRefActivity.get());

                if (mVoltageSaved != 1) {
                    // Reset voltage_saved to recopy voltage stock files
                    AppSettings.saveBoolean("cl0_voltage_saved", false, mRefActivity.get());
                    AppSettings.saveBoolean("cl1_voltage_saved", false, mRefActivity.get());
                    AppSettings.saveBoolean("busMif_voltage_saved", false, mRefActivity.get());
                    AppSettings.saveBoolean("busInt_voltage_saved", false, mRefActivity.get());
                    AppSettings.saveBoolean("busDisp_voltage_saved", false, mRefActivity.get());
                    AppSettings.saveBoolean("busCam_voltage_saved", false, mRefActivity.get());
                    AppSettings.saveBoolean("gpu_voltage_saved", false, mRefActivity.get());
                }

                // Reset battery_saved to recopy battery stock values
                AppSettings.saveBoolean("battery_saved", false, mRefActivity.get());
            }

            // Check if hKtweaks version is changed
            String appVersionOld = AppSettings.getString("app_version_old", "", mRefActivity.get());
            String appVersionNew = Utils.appVersion();
            AppSettings.saveBoolean("show_changelog", true, mRefActivity.get());

            if (appVersionOld.equals(appVersionNew)){
                AppSettings.saveBoolean("show_changelog", false, mRefActivity.get());
            } else {
                AppSettings.saveString("app_version_old", appVersionNew, mRefActivity.get());
            }

            // save battery stock values
            if (!AppSettings.getBoolean("battery_saved", false, mRefActivity.get())){
                Battery.getInstance(mRefActivity.get()).saveStockValues(mRefActivity.get());
            }

            // Save backup of Cluster0 stock voltages
            if (!Utils.existFile(VoltageCl0.BACKUP) || !AppSettings.getBoolean("cl0_voltage_saved", false, mRefActivity.get()) ){
                if (VoltageCl0.supported()){
                    RootUtils.runCommand("cp " + VoltageCl0.CL0_VOLTAGE + " " + VoltageCl0.BACKUP);
                    AppSettings.saveBoolean("cl0_voltage_saved", true, mRefActivity.get());
                }
            }

            // Save backup of Cluster1 stock voltages
            if (!Utils.existFile(VoltageCl1.BACKUP) || !AppSettings.getBoolean("cl1_voltage_saved", false, mRefActivity.get())){
                if (VoltageCl1.supported()){
                    RootUtils.runCommand("cp " + VoltageCl1.CL1_VOLTAGE + " " + VoltageCl1.BACKUP);
                    AppSettings.saveBoolean("cl1_voltage_saved", true, mRefActivity.get());
                }
            }

            // Save backup of Bus Mif stock voltages
            if (!Utils.existFile(VoltageMif.BACKUP) || !AppSettings.getBoolean("busMif_voltage_saved", false, mRefActivity.get())){
                if (VoltageMif.supported()){
                    RootUtils.runCommand("cp " + VoltageMif.VOLTAGE + " " + VoltageMif.BACKUP);
                    AppSettings.saveBoolean("busMif_voltage_saved", true, mRefActivity.get());
                }
            }

            // Save backup of Bus Int stock voltages
            if (!Utils.existFile(VoltageInt.BACKUP) || !AppSettings.getBoolean("busInt_voltage_saved", false, mRefActivity.get())){
                if (VoltageInt.supported()){
                    RootUtils.runCommand("cp " + VoltageInt.VOLTAGE + " " + VoltageInt.BACKUP);
                    AppSettings.saveBoolean("busInt_voltage_saved", true, mRefActivity.get());
                }
            }

            // Save backup of Bus Disp stock voltages
            if (!Utils.existFile(VoltageDisp.BACKUP) || !AppSettings.getBoolean("busDisp_voltage_saved", false, mRefActivity.get())){
                if (VoltageDisp.supported()){
                    RootUtils.runCommand("cp " + VoltageDisp.VOLTAGE + " " + VoltageDisp.BACKUP);
                    AppSettings.saveBoolean("busDisp_voltage_saved", true, mRefActivity.get());
                }
            }

            // Save backup of Bus Cam stock voltages
            if (!Utils.existFile(VoltageCam.BACKUP) || !AppSettings.getBoolean("busCam_voltage_saved", false, mRefActivity.get())){
                if (VoltageCam.supported()){
                    RootUtils.runCommand("cp " + VoltageCam.VOLTAGE + " " + VoltageCam.BACKUP);
                    AppSettings.saveBoolean("busCam_voltage_saved", true,mRefActivity.get() );
                }
            }

            // Save backup of GPU stock voltages
            if (!Utils.existFile(GPUFreqExynos.BACKUP) || !AppSettings.getBoolean("gpu_voltage_saved", false, mRefActivity.get())){
                if (GPUFreqExynos.getInstance().supported() && GPUFreqExynos.getInstance().hasVoltage()){
                    RootUtils.runCommand("cp " + GPUFreqExynos.getInstance().AVAILABLE_VOLTS + " " + GPUFreqExynos.BACKUP);
                    AppSettings.saveBoolean("gpu_voltage_saved", true, mRefActivity.get());
                }
            }

            // If has MaxPoolPercent save file
            if (!AppSettings.getBoolean("max_pool_percent_saved", false, mRefActivity.get())) {
                if (ZSwap.hasMaxPoolPercent()) {
                    RootUtils.runCommand("cp /sys/module/zswap/parameters/max_pool_percent /data/.hKtweaks/max_pool_percent");
                    AppSettings.saveBoolean("max_pool_percent_saved", true, mRefActivity.get());
                }
            }

            //Check memory pool percent unit
            if (!AppSettings.getBoolean("memory_pool_percent_saved", false, mRefActivity.get())){
                int pool = ZSwap.getMaxPoolPercent();
                if (pool >= 100) AppSettings.saveBoolean("memory_pool_percent", false, mRefActivity.get());
                if (pool < 100) AppSettings.saveBoolean("memory_pool_percent", true, mRefActivity.get());
                AppSettings.saveBoolean("memory_pool_percent_saved", true, mRefActivity.get());
            }

            // Save GPU libs version
            AppSettings.saveString("gpu_lib_version",
                    RootUtils.runCommand("dumpsys SurfaceFlinger | grep GLES | head -n 1 | cut -f 3,4,5 -d ','"), mRefActivity.get());
        }

        @Override
        protected Void doInBackground(Void... params) {
            mHasRoot = RootUtils.rootAccess();
            publishProgress(0);

            if (mHasRoot) {
                mHasBusybox = RootUtils.busyboxInstalled();
                publishProgress(1);

                if (mHasBusybox) {
                    collectData();
                    publishProgress(2);
                }

                checkInitVariables();
            }
            return null;
        }

        /**
         * Determinate what sections are supported
         */
        private void collectData() {
            MainActivity activity = mRefActivity.get();
            if (activity == null) return;

            Battery.getInstance(activity);
            CPUBoost.getInstance();

            // Assign core ctl min cpu
            CPUFreq.getInstance(activity);

            Device.CPUInfo.getInstance();
            Device.Input.getInstance();
            Device.MemInfo.getInstance();
            Device.ROMInfo.getInstance();
            Device.TrustZone.getInstance();
            GPU.supported();
            Hotplug.supported();
            IO.getInstance();
            KSM.getInstance();
            MSMPerformance.getInstance();
            QcomBcl.supported();
            Screen.supported();
            Sound.getInstance();
            Temperature.getInstance(activity);
            Thermal.supported();
            Tile.publishProfileTile(new Profiles(activity).getAllProfiles(), activity);
            Vibration.getInstance();
            VoltageCl0.supported();
            VoltageCl1.supported();
            Wake.supported();

        }

        /**
         * Let the user know what we are doing right now
         *
         * @param values progress
         *               0: Checking root
         *               1: Checking busybox/toybox
         *               2: Collecting information
         */
        @Override
        protected void onProgressUpdate(Integer... values) {
            super.onProgressUpdate(values);
            MainActivity activity = mRefActivity.get();
            if (activity == null) return;

            int red = ContextCompat.getColor(activity, R.color.red);
            int green = ContextCompat.getColor(activity, R.color.material_dynamic_primary40);
            switch (values[0]) {
                case 0:
                    activity.mRootAccess.setTextColor(mHasRoot ? green : red);
                    break;
                case 1:
                    activity.mBusybox.setTextColor(mHasBusybox ? green : red);
                    break;
                case 2:
                    activity.mCollectInfo.setTextColor(green);
                    break;
            }
        }

        @Override
        protected void onPostExecute(Void aVoid) {
            super.onPostExecute(aVoid);
            MainActivity activity = mRefActivity.get();
            if (activity == null) return;

            /*
             * If root or busybox/toybox are not available,
             * launch text activity which let the user know
             * what the problem is.
             */
            if (!mHasRoot || !mHasBusybox) {
                Intent intent = new Intent(activity, TextActivity.class);
                intent.putExtra(TextActivity.MESSAGE_INTENT, activity.getString(mHasRoot ?
                        R.string.no_busybox : R.string.no_root));
                intent.putExtra(TextActivity.SUMMARY_INTENT,
                        mHasRoot ? "https://play.google.com/store/apps/details?id=stericson.busybox" :
                                "https://www.google.com/search?site=&source=hp&q=root+"
                                        + Device.getVendor() + "+" + Device.getModel());
                activity.startActivity(intent);
                activity.finish();

                return;
            }

            activity.launch();
        }
    }

}
