package me.lake.librestreaming.sample;

import android.Manifest;
import android.content.Intent;
import android.content.pm.PackageInfo;
import android.content.pm.PackageManager;
import android.os.Build;
import android.os.Bundle;
import android.support.annotation.NonNull;
import android.support.annotation.Nullable;
import android.support.v4.app.ActivityCompat;
import android.support.v4.content.ContextCompat;
import android.support.v7.app.AppCompatActivity;
import android.util.Log;
import android.view.View;
import android.widget.EditText;
import android.widget.RadioGroup;
import android.widget.Toast;

import com.anthonycr.grant.PermissionsManager;
import com.anthonycr.grant.PermissionsResultAction;

public class MainActivity extends AppCompatActivity {
    RadioGroup rg_direction;
    RadioGroup rg_mode;
    EditText et_url;

    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);
        et_url = (EditText) findViewById(R.id.et_url);
        rg_direction = (RadioGroup) findViewById(R.id.rg_direction);
        rg_mode = (RadioGroup) findViewById(R.id.rg_mode);
        rg_direction.check(R.id.rb_port);
        rg_mode.check(R.id.rb_hard);
        findViewById(R.id.btn_start).setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                start();
            }
        });
    }

    private void start() {

        PermissionsManager.getInstance().requestPermissionsIfNecessaryForResult(this,
                new String[]{Manifest.permission.CAMERA , Manifest.permission.RECORD_AUDIO},
                new PermissionsResultAction() {
            @Override
            public void onGranted() {
                goToCamera();
            }

            @Override
            public void onDenied(String permission) {
                Toast.makeText(MainActivity.this,
                        "请打开摄像头权限",
                        Toast.LENGTH_SHORT).show();
            }
        });
//        if (Build.VERSION.SDK_INT >= 23) {
//            int checkCameraPermission = ContextCompat.checkSelfPermission(this , Manifest.permission.CAMERA);
//            int checkAudioPermission =  ContextCompat.checkSelfPermission(this , Manifest.permission.RECORD_AUDIO);
//            if(checkAudioPermission == PackageManager.PERMISSION_GRANTED && checkCameraPermission !=  PackageManager.PERMISSION_GRANTED){
//                goToCamera();
//
//            }else{
//                ActivityCompat.requestPermissions(this,new String[]{Manifest.permission.CAMERA , Manifest.permission.RECORD_AUDIO},2);
//                return;
//            }
//        }

    }

    private void goToCamera() {
        Intent intent;
        boolean isport = false;
        if (rg_direction.getCheckedRadioButtonId() == R.id.rb_port) {
            isport = true;
        }
        if (rg_mode.getCheckedRadioButtonId() == R.id.rb_hard) {
            intent = new Intent(MainActivity.this, HardStreamingActivity.class);
        } else {
            intent = new Intent(MainActivity.this, SoftStreamingActivity.class);
        }
        intent.putExtra(BaseStreamingActivity.DIRECTION, isport);
        intent.putExtra(BaseStreamingActivity.RTMPADDR,et_url.getText().toString());
        startActivity(intent);
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions, @NonNull int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
    }
}