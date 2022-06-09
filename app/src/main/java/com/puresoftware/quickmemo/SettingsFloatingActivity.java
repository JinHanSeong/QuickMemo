package com.puresoftware.quickmemo;

import android.content.Intent;
import android.content.SharedPreferences;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;

import com.google.android.material.snackbar.Snackbar;
import com.puresoftware.quickmemo.floating.WidgetService;

import androidx.annotation.RequiresApi;
import androidx.appcompat.app.AppCompatActivity;

import android.preference.Preference;
import android.preference.PreferenceActivity;
import android.preference.PreferenceManager;
import android.preference.PreferenceScreen;
import android.provider.Settings;
import android.util.Log;
import android.view.View;
import android.widget.Toast;

import androidx.navigation.NavController;
import androidx.navigation.Navigation;
import androidx.navigation.ui.AppBarConfiguration;
import androidx.navigation.ui.NavigationUI;

public class SettingsFloatingActivity extends PreferenceActivity {

    Intent intent; // 서비스를 위한 인텐트
    String TAG = SettingsFloatingActivity.class.getSimpleName();

    @RequiresApi(api = Build.VERSION_CODES.M)
    @Override
    public boolean onPreferenceTreeClick(PreferenceScreen preferenceScreen, Preference preference) {
        String key = preference.getKey();
        Log.d(TAG, "클릭된 Preference의 value는 " + key);

        // 기본 플로팅 모드와 앱 선택 플로팅 모드
        switch (key) {
            case "floating_default": // 기본 플로팅
                if (intent != null) { // 스위치가 켜저 있다면 인텐트는 그대로 유지되고 있다.
                    stopService(intent);
                    startService(intent);
                }
                break;

            case "floating_difference": // 앱 선택해주는 AppChooser
                Intent i = new Intent(Intent.ACTION_SEND);
                i.addCategory(Intent.CATEGORY_DEFAULT);
                i.putExtra(Intent.EXTRA_TEXT, "메모 내용");
                i.setType("text/plain");
                startActivity(Intent.createChooser(i, "실행"));
            default:
                break;
        }
        return false;
    }


    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        addPreferencesFromResource(R.xml.settings_floating);

        // 값이 변경되었을 때 머시기하는거라는데 잘 모름.
        SharedPreferences prefs;
        prefs = PreferenceManager.getDefaultSharedPreferences(this);
        prefs
                .registerOnSharedPreferenceChangeListener(new SharedPreferences.OnSharedPreferenceChangeListener() {
                    @RequiresApi(api = Build.VERSION_CODES.M)
                    public void onSharedPreferenceChanged(SharedPreferences sp, String key) {
                        Log.i(TAG, "클릭된 Preference의 key는 " + key);

                        // 스위치 상태가 켜짐이라면 켜기
                        if (prefs.getBoolean("floating_status", true)) {
                            Log.i(TAG, "yes!");

                            // 오버레이 권한이 없으면 오버레이 권한을 받아야함
                            if (!Settings.canDrawOverlays(SettingsFloatingActivity.this)) {
                                getpermission();

                                // 오버레이 권한이 있으면 바로 실행
                            } else {
                                intent = new Intent(SettingsFloatingActivity.this, WidgetService.class);
                                startService(intent);
                            }

                            // 스위치 상태가 꺼짐이라면 끄기
                        } else {
                            Log.i(TAG, "no!");

                            if (intent != null) {
                                stopService(intent);
                            }
                        }
                    }
                });
    }


    // M 버전(안드로이드 6.0 마시멜로우 버전) 보다 같거나 큰 API에서만 설정창 이동 가능
    public void getpermission() {
        // 지금 창이 오버레이 설정창이 아니라면
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M && !Settings.canDrawOverlays(this)) {
            Intent intent = new Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION, Uri.parse("package:" + getPackageName()));
            startActivityForResult(intent, 1);
        }
    }


    @RequiresApi(api = Build.VERSION_CODES.M)
    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);

        // 권한 여부 확인
        if (requestCode == 1) {
            // 권한을 사용할 수없는 경우 알림 표시
            if (!Settings.canDrawOverlays(SettingsFloatingActivity.this)) {

                Toast.makeText(this, "Permission denied by user", Toast.LENGTH_SHORT).show();
            }
        }
    }
}