package com.example.vcam;


import android.Manifest;
import android.app.Activity;
import android.app.AlertDialog;
import android.app.Application;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.Environment;
import android.util.Log;
import android.widget.Button;
import android.widget.CompoundButton;
import android.widget.EditText;
import android.widget.Switch;
import android.widget.Toast;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileOutputStream;
import java.io.FileReader;
import java.io.IOException;
import java.io.OutputStreamWriter;

public class MainActivity extends Activity {

    private Switch force_show_switch;
    private Switch disable_switch;
    private Switch play_sound_switch;
    private Switch force_private_dir;
    private Switch disable_toast_switch;

    private EditText rtspUrlInput;
    private Button saveRtspUrlButton;

    private String getVideoPathBaseDir() {
        if (!has_permission()) {
            request_permission(); // Request permission if not already granted.
            // Return a default path or handle the case where permission is not granted yet.
            // For now, defaulting to public if check immediately fails,
            // but ideally, subsequent operations should wait for permission result.
            // This part might need refinement based on permission flow.
            if (!has_permission()) {
                 Toast.makeText(this, "Storage permission is required to configure video path.", Toast.LENGTH_LONG).show();
                 // Fallback to app's private directory if public is not available or permitted
                 File privateDir = new File(this.getExternalFilesDir(null), "Camera1");
                 if (!privateDir.exists()) {
                     privateDir.mkdirs();
                 }
                 return privateDir.getAbsolutePath();
            }
        }

        File forcePrivateFile = new File(Environment.getExternalStorageDirectory().getPath() + "/DCIM/Camera1/private_dir.jpg");
        String path;

        if (forcePrivateFile.exists()) {
            path = new File(this.getExternalFilesDir(null), "Camera1").getAbsolutePath();
        } else {
            // Check if public DCIM/Camera1 is writable
            File publicDcimCamera1 = new File(Environment.getExternalStorageDirectory().getPath() + "/DCIM/Camera1/");
            if (publicDcimCamera1.exists() || publicDcimCamera1.mkdirs()) {
                 if(publicDcimCamera1.canWrite()){
                    path = publicDcimCamera1.getAbsolutePath();
                 } else {
                    path = new File(this.getExternalFilesDir(null), "Camera1").getAbsolutePath();
                    Toast.makeText(this, "DCIM/Camera1 not writable, using app private directory.", Toast.LENGTH_LONG).show();
                 }
            } else {
                 path = new File(this.getExternalFilesDir(null), "Camera1").getAbsolutePath();
                 Toast.makeText(this, "Could not create DCIM/Camera1, using app private directory.", Toast.LENGTH_LONG).show();
            }
        }

        File dir = new File(path);
        if (!dir.exists()) {
            dir.mkdirs();
        }
        return path;
    }


    @Override
    public void onRequestPermissionsResult(int requestCode, String[] permissions, int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        if (grantResults.length > 0) {
            if (grantResults[0] == PackageManager.PERMISSION_DENIED) {
                Toast.makeText(MainActivity.this, R.string.permission_lack_warn, Toast.LENGTH_SHORT).show();
            }else {
                File camera_dir = new File (Environment.getExternalStorageDirectory().getAbsolutePath()+"/DCIM/Camera1/");
                if (!camera_dir.exists()){
                    camera_dir.mkdir();
                }
            }
        }
    }

    @Override
    protected void onResume() {
        super.onResume();
        sync_statue_with_files(); // Existing sync
        loadRtspUrlToEditText(); // Load RTSP URL
    }

    private void loadRtspUrlToEditText() {
        if (!has_permission()) {
            // Don't try to read if no permission, or it might loop request_permission
            rtspUrlInput.setText(""); // Clear it or set a placeholder
            return;
        }
        File rtspFile = new File(getVideoPathBaseDir(), "rtsp_url.txt");
        if (rtspFile.exists() && rtspFile.canRead()) {
            try (BufferedReader reader = new BufferedReader(new FileReader(rtspFile))) {
                String url = reader.readLine();
                if (url != null) {
                    rtspUrlInput.setText(url.trim());
                } else {
                    rtspUrlInput.setText("");
                }
            } catch (IOException e) {
                Log.e("VCAM_MainActivity", "Error reading rtsp_url.txt: " + e.getMessage());
                rtspUrlInput.setText("");
            }
        } else {
            rtspUrlInput.setText("");
        }
    }

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        Button repo_button = findViewById(R.id.button);
        force_show_switch = findViewById(R.id.switch1);
        disable_switch = findViewById(R.id.switch2);
        play_sound_switch = findViewById(R.id.switch3);
        force_private_dir = findViewById(R.id.switch4);
        disable_toast_switch = findViewById(R.id.switch5);

        rtspUrlInput = findViewById(R.id.rtsp_url_input);
        saveRtspUrlButton = findViewById(R.id.save_rtsp_url_button);

        sync_statue_with_files();
        // loadRtspUrlToEditText(); // Called in onResume

        saveRtspUrlButton.setOnClickListener(v -> {
            if (!has_permission()) {
                request_permission();
                Toast.makeText(MainActivity.this, "Please grant storage permission first.", Toast.LENGTH_SHORT).show();
                return;
            }

            String rtspUrl = rtspUrlInput.getText().toString().trim();
            File baseDir = new File(getVideoPathBaseDir());
            if (!baseDir.exists() && !baseDir.mkdirs()) {
                Toast.makeText(MainActivity.this, "Error: Could not create directory: " + baseDir.getAbsolutePath(), Toast.LENGTH_LONG).show();
                return;
            }
            File rtspFile = new File(baseDir, "rtsp_url.txt");

            if (rtspUrl.toLowerCase().startsWith("rtsp://") && rtspUrl.length() > 7) {
                try (FileOutputStream fos = new FileOutputStream(rtspFile);
                     OutputStreamWriter osw = new OutputStreamWriter(fos)) {
                    osw.write(rtspUrl);
                    Toast.makeText(MainActivity.this, "RTSP URL saved.", Toast.LENGTH_SHORT).show();
                } catch (IOException e) {
                    Toast.makeText(MainActivity.this, "Error saving RTSP URL: " + e.getMessage(), Toast.LENGTH_LONG).show();
                    Log.e("VCAM_MainActivity", "Error writing rtsp_url.txt", e);
                }
            } else if (rtspUrl.isEmpty()) {
                if (rtspFile.exists()) {
                    if (rtspFile.delete()) {
                        Toast.makeText(MainActivity.this, "RTSP URL cleared. Using default virtual.mp4.", Toast.LENGTH_SHORT).show();
                    } else {
                        Toast.makeText(MainActivity.this, "Error deleting existing RTSP URL file.", Toast.LENGTH_SHORT).show();
                    }
                } else {
                    Toast.makeText(MainActivity.this, "No RTSP URL to clear.", Toast.LENGTH_SHORT).show();
                }
            } else {
                Toast.makeText(MainActivity.this, "Invalid RTSP URL. Must start with rtsp://", Toast.LENGTH_LONG).show();
            }
        });

        repo_button.setOnClickListener(v -> {

            Uri uri = Uri.parse("https://github.com/w2016561536/android_virtual_cam");
            Intent intent = new Intent(Intent.ACTION_VIEW, uri);
            startActivity(intent);
        });

        Button repo_button_chinamainland = findViewById(R.id.button2);
        repo_button_chinamainland.setOnClickListener(view -> {
            Uri uri = Uri.parse("https://gitee.com/w2016561536/android_virtual_cam");
            Intent intent = new Intent(Intent.ACTION_VIEW, uri);
            startActivity(intent);
        });

        disable_switch.setOnCheckedChangeListener((compoundButton, b) -> {
            if (compoundButton.isPressed()) {
                if (!has_permission()) {
                    request_permission();
                } else {
                    File disable_file = new File(Environment.getExternalStorageDirectory().getAbsolutePath() + "/DCIM/Camera1/disable.jpg");
                    if (disable_file.exists() != b){
                        if (b){
                            try {
                                disable_file.createNewFile();
                            } catch (IOException e) {
                                e.printStackTrace();
                            }
                        }else {
                            disable_file.delete();
                        }
                    }
                }
                sync_statue_with_files();
            }
        });

        force_show_switch.setOnCheckedChangeListener((compoundButton, b) -> {
            if (compoundButton.isPressed()) {
                if (!has_permission()) {
                    request_permission();
                } else {
                    File force_show_switch = new File(Environment.getExternalStorageDirectory().getAbsolutePath() + "/DCIM/Camera1/force_show.jpg");
                    if (force_show_switch.exists() != b){
                        if (b){
                            try {
                                force_show_switch.createNewFile();
                            } catch (IOException e) {
                                e.printStackTrace();
                            }
                        }else {
                            force_show_switch.delete();
                        }
                    }
                }
                sync_statue_with_files();
            }
        });

        play_sound_switch.setOnCheckedChangeListener(new CompoundButton.OnCheckedChangeListener() {
            @Override
            public void onCheckedChanged(CompoundButton compoundButton, boolean b) {
                if (compoundButton.isPressed()) {
                    if (!has_permission()) {
                        request_permission();
                    } else {
                        File play_sound_switch = new File(Environment.getExternalStorageDirectory().getAbsolutePath() + "/DCIM/Camera1/no-silent.jpg");
                        if (play_sound_switch.exists() != b){
                            if (b){
                                try {
                                    play_sound_switch.createNewFile();
                                } catch (IOException e) {
                                    e.printStackTrace();
                                }
                            }else {
                                play_sound_switch.delete();
                            }
                        }
                    }
                    sync_statue_with_files();
                }
            }
        });

        force_private_dir.setOnCheckedChangeListener((compoundButton, b) -> {
            if (compoundButton.isPressed()) {
                if (!has_permission()) {
                    request_permission();
                } else {
                    File force_private_dir = new File(Environment.getExternalStorageDirectory().getAbsolutePath() + "/DCIM/Camera1/private_dir.jpg");
                    if (force_private_dir.exists() != b){
                        if (b){
                            try {
                                force_private_dir.createNewFile();
                            } catch (IOException e) {
                                e.printStackTrace();
                            }
                        }else {
                            force_private_dir.delete();
                        }
                    }
                }
                sync_statue_with_files();
            }
        });


        disable_toast_switch.setOnCheckedChangeListener((compoundButton, b) -> {
            if (compoundButton.isPressed()) {
                if (!has_permission()) {
                    request_permission();
                } else {
                    File disable_toast_file = new File(Environment.getExternalStorageDirectory().getAbsolutePath() + "/DCIM/Camera1/no_toast.jpg");
                    if (disable_toast_file.exists() != b){
                        if (b){
                            try {
                                disable_toast_file.createNewFile();
                            } catch (IOException e) {
                                e.printStackTrace();
                            }
                        }else {
                            disable_toast_file.delete();
                        }
                    }
                }
                sync_statue_with_files();
            }
        });

    }

    private void request_permission() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            if (this.checkSelfPermission(Manifest.permission.READ_EXTERNAL_STORAGE) == PackageManager.PERMISSION_DENIED
                    || this.checkSelfPermission(Manifest.permission.WRITE_EXTERNAL_STORAGE) == PackageManager.PERMISSION_DENIED) {
                AlertDialog.Builder builder = new AlertDialog.Builder(MainActivity.this);
                builder.setTitle(R.string.permission_lack_warn);
                builder.setMessage(R.string.permission_description);

                builder.setNegativeButton(R.string.negative, (dialogInterface, i) -> Toast.makeText(MainActivity.this, R.string.permission_lack_warn, Toast.LENGTH_SHORT).show());

                builder.setPositiveButton(R.string.positive, (dialogInterface, i) -> requestPermissions(new String[]{Manifest.permission.READ_EXTERNAL_STORAGE, Manifest.permission.WRITE_EXTERNAL_STORAGE}, 1));
                builder.show();
            }
        }
    }

    private boolean has_permission() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            return this.checkSelfPermission(Manifest.permission.READ_EXTERNAL_STORAGE) != PackageManager.PERMISSION_DENIED
                    && this.checkSelfPermission(Manifest.permission.WRITE_EXTERNAL_STORAGE) != PackageManager.PERMISSION_DENIED;
        }
        return true;
    }


    private void sync_statue_with_files() {
        Log.d(this.getApplication().getPackageName(), "【VCAM】[sync]同步开关状态");

        if (!has_permission()){
            request_permission();
        }else {
            File camera_dir = new File (Environment.getExternalStorageDirectory().getAbsolutePath()+"/DCIM/Camera1");
            if (!camera_dir.exists()){
                camera_dir.mkdir();
            }
        }

        File disable_file = new File(Environment.getExternalStorageDirectory().getAbsolutePath() + "/DCIM/Camera1/disable.jpg");
        disable_switch.setChecked(disable_file.exists());

        File force_show_file = new File(Environment.getExternalStorageDirectory().getAbsolutePath() + "/DCIM/Camera1/force_show.jpg");
        force_show_switch.setChecked(force_show_file.exists());

        File play_sound_file = new File(Environment.getExternalStorageDirectory().getAbsolutePath() + "/DCIM/Camera1/no-silent.jpg");
        play_sound_switch.setChecked(play_sound_file.exists());

        File force_private_dir_file = new File(Environment.getExternalStorageDirectory().getAbsolutePath() + "/DCIM/Camera1/private_dir.jpg");
        force_private_dir.setChecked(force_private_dir_file.exists());

        File disable_toast_file = new File(Environment.getExternalStorageDirectory().getAbsolutePath() + "/DCIM/Camera1/no_toast.jpg");
        disable_toast_switch.setChecked(disable_toast_file.exists());

    }


}



