
package com.example.admin.tamabus;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.SurfaceTexture;
import android.hardware.camera2.CameraAccessException;
import android.hardware.camera2.CameraCaptureSession;
import android.hardware.camera2.CameraCharacteristics;
import android.hardware.camera2.CameraDevice;
import android.hardware.camera2.CameraManager;
import android.hardware.camera2.CaptureRequest;
import android.hardware.camera2.TotalCaptureResult;
import android.hardware.camera2.params.StreamConfigurationMap;
import android.support.annotation.NonNull;
import android.support.v4.content.ContextCompat;
import android.support.v7.app.AppCompatActivity;
import android.os.Bundle;
import android.content.pm.PackageManager;
import android.location.Location;
import android.location.LocationListener;
import android.location.LocationManager;
import android.location.LocationProvider;
import android.support.v4.app.ActivityCompat;
import android.util.Base64;
import android.util.Size;
import android.view.Surface;
import android.view.TextureView;
import android.widget.ImageView;
import android.widget.TextView;
import android.content.Intent;
import android.provider.Settings;
import android.util.Log;
import android.widget.Toast;
import android.Manifest;
import android.net.Uri;
import android.util.Log;
import android.view.View;
import android.widget.Button;
import android.widget.EditText;
import android.widget.TextView;
import com.example.admin.tamabus.R;
import android.os.Handler;

import java.io.ByteArrayOutputStream;
import java.util.Arrays;
import java.util.Timer;
import java.util.TimerTask;

public class MainActivity extends AppCompatActivity implements TextureView.SurfaceTextureListener,LocationListener {
    private UploadTask position_task,image_task;

    // phpがPOSTで受け取ったwordを入れて作成するHTMLページ
    // Emulatorの場合、アクセス先を10.0.2.2:8080に設定
    // 実機の場合、アクセス先をlocalhost:8080に設定
    LocationManager locationManager;

    private TextView textView1;             // テキストビュー
    private Handler handler1;               // ハンドラー
    private Timer timer1;                   // タイマー
    private int count1;                     // カウント用

    //Cameraで取り入れる機能に使用する変数群
    private TextureView mTextureView;
    private SurfaceTexture mSurfaceTexture;
    private Surface mSurface;
    private Size mCameraSize;
    private CameraDevice mCameraDevice;
    private CaptureRequest.Builder mCaptureRequestBuilder;
    private static final String TAG = "CAMERA";

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        //テキストビューを取得
        textView1 = (TextView)findViewById(R.id.textview1);
        //タイマーを新規生成
        timer1 = new Timer();
        //ハンドラーを新規生成
        handler1 = new Handler();
        //カウンターを初期化
        count1 = 0;

        if (ContextCompat.checkSelfPermission(this,
                Manifest.permission.ACCESS_FINE_LOCATION) !=
                PackageManager.PERMISSION_GRANTED) {
            ActivityCompat.requestPermissions(this,
                    new String[]{Manifest.permission.ACCESS_FINE_LOCATION,},
                    1000);
        }
        else{
            locationStart();
            //locationManager.requestLocationUpdates(LocationManager.GPS_PROVIDER,5000, 5, this);
            //locationManager.requestLocationUpdates(LocationManager.NETWORK_PROVIDER,5000 ,5 ,this );
        }

        // TextureViewをactivity_mainから取り込み
        mTextureView = (TextureView) findViewById(R.id.camera_texture_view);
        // TextureViewのリスナーを設定
        mTextureView.setSurfaceTextureListener(this);
        // SurfaceTextureのインスタンスを取得
        mSurfaceTexture = mTextureView.getSurfaceTexture();

        //タイマーに直接スケジュール(1秒後に1秒間隔の処理を開始)を追加して実行
        timer1.schedule(new TimerTask() {
            @Override
            public void run() {
                //直接だとエラーになるのでハンドラーを経由して画面表示を変更する
                handler1.post(new Runnable() {
                    @Override
                    public void run() {
                        //この部分に繰り返したい動作を記述する
                        //レイアウトのテキストビューにカウント値を表示
                        textView1.setText(count1 + "秒経過");

                        TextView textView1 = (TextView) findViewById(R.id.text_view1);
                        String latitude=textView1.getText().toString();
                        TextView textView2 = (TextView) findViewById(R.id.text_view2);
                        String longitude=textView2.getText().toString();

                        String base64 =getBase64Image();

                        if(latitude.length() != 0) {
                            position_task = new UploadTask();
                            image_task=new UploadTask();
                            position_task.setListener(createListener());
                            image_task.setListener(createListener());
                            //位置情報と画像のPOST処理
                            position_task.execute(longitude,latitude);
                            image_task.execute(base64);

                        }

                    }
                });
                //カウントアップ
                count1 += 10;
            }
        }, 1000, 10000);
    }

    public String getBase64Image() {
        Bitmap screen=mTextureView.getBitmap(270,480);
        return encodeTobase64(screen);
    }

    public static String encodeTobase64(Bitmap image)
    {
        Bitmap immagex=image;
        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        immagex.compress(Bitmap.CompressFormat.JPEG, 50, baos);
        byte[] b = baos.toByteArray();
        String imageEncoded = Base64.encodeToString(b, Base64.NO_WRAP);
        return imageEncoded;
    }

    //GPSを動作させるためのメソッド
    private void locationStart(){
        Log.d("debug","locationStart()");

        // LocationManager インスタンス生成
        locationManager =
                (LocationManager) getSystemService(LOCATION_SERVICE);

        if (locationManager != null && locationManager.isProviderEnabled(LocationManager.GPS_PROVIDER)) {
            Log.d("debug", "location manager Enabled");
        } else {
            // GPSを設定するように促す
            Intent settingsIntent =
                    new Intent(Settings.ACTION_LOCATION_SOURCE_SETTINGS);
            startActivity(settingsIntent);
            Log.d("debug", "not gpsEnable, startActivity");
        }

        if (ContextCompat.checkSelfPermission(this,
                Manifest.permission.ACCESS_FINE_LOCATION) !=
                PackageManager.PERMISSION_GRANTED) {
            ActivityCompat.requestPermissions(this,
                    new String[]{Manifest.permission.ACCESS_FINE_LOCATION,}, 1000);

            Log.d("debug", "checkSelfPermission false");
            return;
        }

        locationManager.requestLocationUpdates(LocationManager.GPS_PROVIDER,
                5000, 5, this);
        locationManager.requestLocationUpdates(LocationManager.NETWORK_PROVIDER,5000 ,5 ,this );

    }

    // 結果の受け取り
    /**
     * Android Quickstart:
     * https://developers.google.com/sheets/api/quickstart/android
     *
     * Respond to requests for permissions at runtime for API 23 and above.
     * @param requestCode The request code passed in
     *     requestPermissions(android.app.Activity, String, int, String[])
     * @param permissions The requested permissions. Never null.
     * @param grantResults The grant results for the corresponding permissions
     *     which is either PERMISSION_GRANTED or PERMISSION_DENIED. Never null.
     */
    @Override
    public void onRequestPermissionsResult(
            int requestCode,  @NonNull String[]permissions,  @NonNull int[] grantResults) {
        if (requestCode == 1000) {
            // 使用が許可された
            if (grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                Log.d("debug","checkSelfPermission true");

                locationStart();

            } else {
                // それでも拒否された時の対応
                Toast toast = Toast.makeText(this,
                        "これ以上なにもできません", Toast.LENGTH_SHORT);
                toast.show();
            }
        }
    }

    @Override
    public void onStatusChanged(String provider, int status, Bundle extras) {
        switch (status) {
            case LocationProvider.AVAILABLE:
                Log.d("debug", "LocationProvider.AVAILABLE");
                break;
            case LocationProvider.OUT_OF_SERVICE:
                Log.d("debug", "LocationProvider.OUT_OF_SERVICE");
                break;
            case LocationProvider.TEMPORARILY_UNAVAILABLE:
                Log.d("debug", "LocationProvider.TEMPORARILY_UNAVAILABLE");
                break;
        }
    }

    @Override
    public void onLocationChanged(Location location) {
        // 緯度の表示
        TextView textView1 = (TextView) findViewById(R.id.text_view1);
        String str1 = String.valueOf(location.getLatitude());
        textView1.setText(str1);

        // 経度の表示
        TextView textView2 = (TextView) findViewById(R.id.text_view2);
        String str2 = String.valueOf(location.getLongitude());
        textView2.setText(str2);
    }

    @Override
    public void onProviderEnabled(String provider) {

    }

    @Override
    public void onProviderDisabled(String provider) {

    }

    @Override
    protected void onDestroy() {
        position_task.setListener(null);
        image_task.setListener(null);
        super.onDestroy();
    }

    private UploadTask.Listener createListener() {
        return new UploadTask.Listener() {
            @Override
            public void onSuccess(String result) {
                //Nothing
            }
        };
    }





    // CameraDeviceのStateCallback
    //これがよくわからない
    CameraDevice.StateCallback mDeviceCallback = new CameraDevice.StateCallback()
    {
        @Override
        public void onDisconnected(CameraDevice device){
            Log.i(TAG, "onDisconnected");
            //必要ない？
        }

        @Override
        public void onError(CameraDevice device, int error){
            Log.i(TAG, "onError");
            //必要ない？
        }

        @Override
        public void onOpened(CameraDevice device){
            Log.i(TAG, "onOpened");

            // Globalな値に(onPause時にcloseするため)
            mCameraDevice = device;

            // CaptureRequestの生成
            try {
                mCaptureRequestBuilder = mCameraDevice.createCaptureRequest(CameraDevice.TEMPLATE_PREVIEW);
            } catch (CameraAccessException e) {
                e.printStackTrace();
            }

            // Requestに取得したSurfaceを設定
            mCaptureRequestBuilder.addTarget(mSurface);

            // SurfaceTextureにサイズを設定
            mSurfaceTexture.setDefaultBufferSize(mCameraSize.getWidth(), mCameraSize.getHeight());

            // CaptureSessionの生成
            try {
                mCameraDevice.createCaptureSession(Arrays.asList(mSurface), mCaptureSessionCallback, null);
            } catch (CameraAccessException e) {
                e.printStackTrace();
            }
        }
    };

    // CameraCaptureSessionのStateCallback
    CameraCaptureSession.StateCallback mCaptureSessionCallback = new CameraCaptureSession.StateCallback()
    {
        @Override
        public void onConfigured (CameraCaptureSession session){
            Log.i(TAG, "onConfigured");

            try {
                session.setRepeatingRequest(mCaptureRequestBuilder.build(), mCaptureCallback , null);
            } catch (CameraAccessException e) {
                e.printStackTrace();
            }
        }

        @Override
        public void onConfigureFailed(CameraCaptureSession session) {
            Log.i(TAG, "onConfigureFailed");
        }
    };

    // CaptureSessionのCallback
    CameraCaptureSession.CaptureCallback mCaptureCallback = new CameraCaptureSession.CaptureCallback()
    {
        @Override
        public void onCaptureCompleted(CameraCaptureSession session, CaptureRequest request, TotalCaptureResult result) {

            Log.i(TAG, "onCaptureCompleted");

        }
    };

    @Override
    public void onSurfaceTextureAvailable(SurfaceTexture surface, int width, int height) {

        Log.i(TAG, "onSurfaceTextureAvailable");

        mSurfaceTexture = surface;
        mSurface = new Surface(mSurfaceTexture);

        try {
            CameraManager manager = (CameraManager) getSystemService(Context.CAMERA_SERVICE);
            for( String cameraId: manager.getCameraIdList()){

                Log.i(TAG, "cameraId:" + cameraId);

                // 取得したCameraIdからキャラスタリスチクスを取得
                CameraCharacteristics characteristics = manager.getCameraCharacteristics(cameraId);
                //characteristics.set(CameraCharacteristics.LENS_FACING_BACK);
                // Frontカメラの場合
                if (characteristics.get(CameraCharacteristics.LENS_FACING) == CameraCharacteristics.LENS_FACING_FRONT)
                {
                    //Frontカメラの処理は飛ばす
                    Log.i(TAG, "FrontCamera");
                }
                // Backカメラの場合
                else if (characteristics.get(CameraCharacteristics.LENS_FACING) == CameraCharacteristics.LENS_FACING_BACK)
                {
                    Log.i(TAG, "BackCamera");
                    // Mapを取得
                    StreamConfigurationMap map = characteristics.get(CameraCharacteristics.SCALER_STREAM_CONFIGURATION_MAP);

                    // サイズの一覧を取得
                    Size[] mOutputSize = map.getOutputSizes(SurfaceTexture.class);
                    for (int i = 0; i < mOutputSize.length; i++) {
                        Log.i(TAG, "size[" + i + "]" + mOutputSize[i].toString());
                    }

                    // カメラサイズ0番目をGlobalな関数に格納
                    mCameraSize = map.getOutputSizes(SurfaceTexture.class)[0];

                    // カメラをオープン
                    manager.openCamera(cameraId, mDeviceCallback, null);
                }
            }
        }
        catch (Exception e) {
            Log.i(TAG, "Error:"+e);
        }
    }

    @Override
    public void onSurfaceTextureSizeChanged(SurfaceTexture surface, int width, int height) {
        Log.i(TAG, "onSurfaceTextureSizeChanged");

    }

    @Override
    public boolean onSurfaceTextureDestroyed(SurfaceTexture surface) {
        Log.i(TAG, "onSurfaceTextureDestroyed");

        return false;
    }

    @Override
    public void onSurfaceTextureUpdated(SurfaceTexture surface) {
        Log.i(TAG, "onSurfaceTextureUpdated");

    }

    @Override
    protected void onPause() {
        super.onPause();
        if (mCameraDevice == null) {
            return;
        }
        mCameraDevice.close();
    }
}
