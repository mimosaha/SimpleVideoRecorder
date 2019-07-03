package com.w3engineers.vidpl;

import android.Manifest;
import android.content.pm.PackageManager;
import android.hardware.Camera;
import android.media.CamcorderProfile;
import android.media.MediaRecorder;
import android.os.Bundle;
import android.support.annotation.NonNull;
import android.support.v4.app.ActivityCompat;
import android.support.v7.app.AppCompatActivity;
import android.view.TextureView;
import android.view.View;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.Toast;

import java.io.File;
import java.io.IOException;
import java.util.List;

public class MainActivity extends AppCompatActivity {

    private int MEDIA_RECORDER_REQUEST = 101;
    private TextureView textureView;
    private Button onOff;
    private LinearLayout previewLayout;

    private Camera camera;
    private MediaRecorder mediaRecorder;
    private File outputFile;
    private boolean isRecording = false;

    private final String[] requiredPermissions = {
            Manifest.permission.WRITE_EXTERNAL_STORAGE,
            Manifest.permission.RECORD_AUDIO,
            Manifest.permission.CAMERA,
    };

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        textureView = findViewById(R.id.surface_view);
        onOff = findViewById(R.id.button_capture);
        previewLayout = findViewById(R.id.preview);

        camera = Camera.open();
        camera.setDisplayOrientation(90);
        CameraPreview cameraPreview = new CameraPreview(this, camera);
        previewLayout.addView(cameraPreview);

        onOff.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                captureAction();
            }
        });
    }

    private void previewControl(boolean isEnable) {
        if (isEnable) {
            previewLayout.setVisibility(View.VISIBLE);
            textureView.setVisibility(View.INVISIBLE);
        } else {
            previewLayout.setVisibility(View.INVISIBLE);
            textureView.setVisibility(View.VISIBLE);
        }
    }

    @Override
    protected void onPause() {
        super.onPause();
        releaseMediaRecorder();
        releaseCamera();
    }

    private void captureAction() {
        if (areCameraPermissionGranted()) {
            startCapture();
        } else {
            requestCameraPermissions();
        }
    }

    private void startCapture() {
        if (isRecording) {
            try {
                mediaRecorder.stop();  // stop the recording
            } catch (RuntimeException e) {
                e.printStackTrace();
                outputFile.delete();
            }
            releaseMediaRecorder(); // release the MediaRecorder object
//            camera.lock();         // take camera access back from MediaRecorder
            onOff.setText("Start");
            isRecording = false;
            releaseCamera();
        } else {
            recordTask();
        }
    }

    private void recordTask() {

        new Thread(new Runnable() {
            @Override
            public void run() {

                if (prepareVideoRecorder()) {
                    mediaRecorder.start();
                    isRecording = true;
                } else {
                    releaseMediaRecorder();
                }

                runOnUiThread(new Runnable() {
                    @Override
                    public void run() {
                        onOff.setText("Stop");
                    }
                });
            }
        }).start();
    }

    private boolean prepareVideoRecorder(){

        camera = CameraHelper.getDefaultCameraInstance();
        camera.setDisplayOrientation(90);

        Camera.Parameters parameters = camera.getParameters();
        List<Camera.Size> mSupportedPreviewSizes = parameters.getSupportedPreviewSizes();
        List<Camera.Size> mSupportedVideoSizes = parameters.getSupportedVideoSizes();
        Camera.Size optimalSize = CameraHelper.getOptimalVideoSize(mSupportedVideoSizes,
                mSupportedPreviewSizes, textureView.getWidth(), textureView.getHeight());

        CamcorderProfile profile = CamcorderProfile.get(CamcorderProfile.QUALITY_HIGH);
        profile.videoFrameWidth = optimalSize.width;
        profile.videoFrameHeight = optimalSize.height;

        parameters.setPreviewSize(profile.videoFrameWidth, profile.videoFrameHeight);
        camera.setParameters(parameters);
        try {
            camera.setPreviewTexture(textureView.getSurfaceTexture());
        } catch (IOException e) {
            e.printStackTrace();
            return false;
        }

        mediaRecorder = new MediaRecorder();

        camera.unlock();
        mediaRecorder.setCamera(camera);

        mediaRecorder.setAudioSource(MediaRecorder.AudioSource.DEFAULT );
        mediaRecorder.setVideoSource(MediaRecorder.VideoSource.CAMERA);

        mediaRecorder.setProfile(profile);

        outputFile = CameraHelper.getOutputMediaFile(CameraHelper.MEDIA_TYPE_VIDEO);
        if (outputFile == null) {
            return false;
        }
        mediaRecorder.setOutputFile(outputFile.getPath());
        mediaRecorder.setOrientationHint(90);
        mediaRecorder.setMaxDuration(10000);

        try {
            mediaRecorder.prepare();
        } catch (IllegalStateException e) {
            e.printStackTrace();
            releaseMediaRecorder();
            return false;
        } catch (IOException e) {
            e.printStackTrace();
            releaseMediaRecorder();
            return false;
        }
        return true;
    }

    private void releaseMediaRecorder(){
        if (mediaRecorder != null) {

            mediaRecorder.reset();
            mediaRecorder.release();
            mediaRecorder = null;
//            camera.lock();
        }
    }

    private void releaseCamera(){
        if (camera != null){
            camera.release();
            camera = null;
        }
    }

    private void requestCameraPermissions(){
        ActivityCompat.requestPermissions(this, requiredPermissions, MEDIA_RECORDER_REQUEST);
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions,
                                           @NonNull int[] grantResults) {

        if (MEDIA_RECORDER_REQUEST != requestCode) {
            super.onRequestPermissionsResult(requestCode, permissions, grantResults);
            return;
        }

        boolean areAllPermissionsGranted = true;
        for (int result : grantResults){
            if (result != PackageManager.PERMISSION_GRANTED){
                areAllPermissionsGranted = false;
                break;
            }
        }

        if (areAllPermissionsGranted){
            startCapture();
        } else {
            Toast.makeText(this, "Need to add all permission", Toast.LENGTH_SHORT).show();
        }
    }

    private boolean areCameraPermissionGranted() {
        for (String permission : requiredPermissions) {
            if (!(ActivityCompat.checkSelfPermission(this, permission) == PackageManager.PERMISSION_GRANTED)) {
                return false;
            }
        }
        return true;
    }
}
