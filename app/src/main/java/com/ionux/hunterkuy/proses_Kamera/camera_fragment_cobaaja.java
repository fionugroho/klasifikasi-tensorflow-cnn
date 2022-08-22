/* Copyright 2017 The TensorFlow Authors. All Rights Reserved.

Licensed under the Apache License, Version 2.0 (the "License");
you may not use this file except in compliance with the License.
You may obtain a copy of the License at

    http://www.apache.org/licenses/LICENSE-2.0

Unless required by applicable law or agreed to in writing, software
distributed under the License is distributed on an "AS IS" BASIS,
WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
See the License for the specific language governing permissions and
limitations under the License.
==============================================================================*/

package com.ionux.hunterkuy.proses_Kamera;

import android.annotation.SuppressLint;
import android.app.Activity;
import android.app.Fragment;
import android.content.Context;
import android.content.pm.PackageInfo;
import android.content.pm.PackageManager;
import android.content.res.Configuration;
import android.graphics.Bitmap;
import android.graphics.ImageFormat;
import android.graphics.Matrix;
import android.graphics.Point;
import android.graphics.RectF;
import android.graphics.SurfaceTexture;
import android.hardware.camera2.CameraAccessException;
import android.hardware.camera2.CameraCaptureSession;
import android.hardware.camera2.CameraCharacteristics;
import android.hardware.camera2.CameraDevice;
import android.hardware.camera2.CameraManager;
import android.hardware.camera2.CaptureRequest;
import android.hardware.camera2.CaptureResult;
import android.hardware.camera2.TotalCaptureResult;
import android.hardware.camera2.params.StreamConfigurationMap;
import android.media.ImageReader;
import android.os.Bundle;
import android.os.Handler;
import android.os.HandlerThread;
import android.util.Log;
import android.util.Size;
import android.view.LayoutInflater;
import android.view.Surface;
import android.view.TextureView;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.TextView;

import com.ionux.hunterkuy.R;
import com.ionux.hunterkuy.proses_Klasifikasi.proses_Klasifikasi;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.concurrent.Semaphore;
import java.util.concurrent.TimeUnit;

import androidx.appcompat.app.AlertDialog;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;

public class camera_fragment_cobaaja extends Fragment implements ActivityCompat.OnRequestPermissionsResultCallback {

  private static final String TAG = "CamHunt";
  private static final String thread_name = "CameraBackground";
  private static final int PERMISSIONS_REQUEST_CODE = 1;
  private final Object obj = new Object();
  private boolean runClassifier = false;
  private boolean cekPermissions = false;
  private proses_Klasifikasi proses_Kelas;
  private static final int cam_width = 1080;
  private static final int cam_height = 1920;
  private String cameraId;
  private AutoFitCamera TexttureFitViewCamera;
  private CameraCaptureSession captureSession;
  private CameraDevice cameraDevice;
  private Size tampilanSize;
  private HandlerThread backgroundThread;
  private Handler backgroundHandler;
  private ImageReader imageReader;
  private CaptureRequest.Builder vRequestBuilder;
  private CaptureRequest vRequest;
  private Semaphore cameraOpenCloseLock = new Semaphore(1);
  private TextView hslPredikJawa, hslPredikWaktu, hslPrediksiAksa;
  private ImageView idBackClass;
  private List<String> list;
  int indexSoal = 0;
  int x = 0, tampungSoal = 0, skor = 0, retry = 3;
  private  String jawaban;
  private AlertDialog.Builder alertDialogBuilder;



  public static camera_fragment_cobaaja newInstance() {
    return new camera_fragment_cobaaja();
  }

  @Override
  public View onCreateView(
          LayoutInflater inflater, ViewGroup container, Bundle savedInstanceState) {
    return inflater.inflate(R.layout.camera_fragment_class, container, false);
  }

  @Override
  public void onViewCreated(final View view, Bundle savedInstanceState) {
    TexttureFitViewCamera = view.findViewById(R.id.texturert);
    hslPredikJawa = view.findViewById(R.id.hslPredikJawa);
    hslPredikWaktu = view.findViewById(R.id.hslPredikWaktu);
    idBackClass =  view.findViewById(R.id.idBackClass);
    idBackClass.setOnClickListener(new View.OnClickListener() {
      @Override
      public void onClick(View v) {
        getActivity().finish();
      }
    });

  }


  @Override
  public void onActivityCreated(Bundle savedInstanceState) {
    super.onActivityCreated(savedInstanceState);
    try {
      proses_Kelas = new proses_Klasifikasi(getActivity());
    } catch (IOException e) {
      Log.e(TAG, "gagal initialisasi.");
    }
    startBackgroundThread();
  }

  private void startBackgroundThread() {
    backgroundThread = new HandlerThread(thread_name);
    backgroundThread.start();
    backgroundHandler = new Handler(backgroundThread.getLooper());
    synchronized (obj) {
      runClassifier = true;
    }
    backgroundHandler.post(stepkalsifikasi);
  }

  private void stopBackgroundThread() {
    backgroundThread.quitSafely();
    try {
      backgroundThread.join();
      backgroundThread = null;
      backgroundHandler = null;
      synchronized (obj) {
        runClassifier = false;
      }
    } catch (InterruptedException e) {
      e.printStackTrace();
    }
  }


  //Pengaturan Permission kamera -- awal
  private String[] getRequiredPermissions() {
    Activity activity = getActivity();
    try {
      PackageInfo info = activity.getPackageManager().getPackageInfo(activity.getPackageName(), PackageManager.GET_PERMISSIONS);
      String[] ps = info.requestedPermissions;
      if (ps != null && ps.length > 0) {
        return ps;
      } else {
        return new String[0];
      }
    } catch (Exception e) {
      return new String[0];
    }
  }
  private boolean allPermissionsGranted() {
    for (String permission : getRequiredPermissions()) {
      if (ContextCompat.checkSelfPermission(getActivity(), permission) != PackageManager.PERMISSION_GRANTED) {
        return false;
      }
    }
    return true;
  }
  public void onRequestPermissionsResult(int requestCode, String[] permissions, int[] grantResults) {
    super.onRequestPermissionsResult(requestCode, permissions, grantResults);
  }
  //Pengaturan Permission kamera -- akhir

  //Pengaturan kamera -- awal
  private final TextureView.SurfaceTextureListener surfaceTextureListener = new TextureView.SurfaceTextureListener() {

    @Override
    public void onSurfaceTextureAvailable(SurfaceTexture texture, int width, int height) {
      openCamera(width, height);
    }

    @Override
    public void onSurfaceTextureSizeChanged(SurfaceTexture texture, int width, int height) {
      configureTransform(width, height);
    }

    @Override
    public boolean onSurfaceTextureDestroyed(SurfaceTexture texture) {
      return true;
    }

    @Override
    public void onSurfaceTextureUpdated(SurfaceTexture texture) {
    }
  };
  private final CameraDevice.StateCallback stateCallback = new CameraDevice.StateCallback() {
    @Override
    public void onOpened(CameraDevice currentCameraDevice) {
      cameraOpenCloseLock.release();
      cameraDevice = currentCameraDevice;
      createCameraPreviewSession();
    }

    @Override
    public void onDisconnected(CameraDevice currentCameraDevice) {
      cameraOpenCloseLock.release();
      currentCameraDevice.close();
      cameraDevice = null;
    }

    @Override
    public void onError(CameraDevice currentCameraDevice, int error) {
      cameraOpenCloseLock.release();
      currentCameraDevice.close();
      cameraDevice = null;

    }
  };
  private CameraCaptureSession.CaptureCallback captureCallback = new CameraCaptureSession.CaptureCallback() {

    @Override
    public void onCaptureProgressed(
            CameraCaptureSession session,
            CaptureRequest request,
            CaptureResult partialResult) {
    }

    @Override
    public void onCaptureCompleted(
            CameraCaptureSession session,
            CaptureRequest request,
            TotalCaptureResult result) {
    }
  };
  @SuppressLint("MissingPermission")
  private void openCamera(int width, int height) {
    if (!cekPermissions && !allPermissionsGranted()) {
      ActivityCompat.requestPermissions(getActivity(), getRequiredPermissions(), PERMISSIONS_REQUEST_CODE);
      return;
    } else {
      cekPermissions = true;
    }
    setUpCameraOutputs(width, height);
    configureTransform(width, height);
    Activity activity = getActivity();
    CameraManager manager = (CameraManager) activity.getSystemService(Context.CAMERA_SERVICE);
    try {
      if (!cameraOpenCloseLock.tryAcquire(2500, TimeUnit.MILLISECONDS)) {
        throw new RuntimeException("Time out waiting to lock camera_fragment opening.");
      }
      manager.openCamera(cameraId, stateCallback, backgroundHandler);
    } catch (CameraAccessException e) {
      e.printStackTrace();
    } catch (InterruptedException e) {
      throw new RuntimeException("Interrupted while trying to lock camera_fragment opening.", e);
    }
  }
  private void closeCamera() {
    try {
      cameraOpenCloseLock.acquire();
      if (null != captureSession) {
        captureSession.close();
        captureSession = null;
      }
      if (null != cameraDevice) {
        cameraDevice.close();
        cameraDevice = null;
      }
      if (null != imageReader) {
        imageReader.close();
        imageReader = null;
      }
    } catch (InterruptedException e) {
      throw new RuntimeException("Interrupted while trying to lock camera_fragment closing.", e);
    } finally {
      cameraOpenCloseLock.release();
    }
  }
  private void createCameraPreviewSession() {
    try {
      SurfaceTexture texture = TexttureFitViewCamera.getSurfaceTexture();
      assert texture != null;
      texture.setDefaultBufferSize(tampilanSize.getWidth(), tampilanSize.getHeight());
      Surface surface = new Surface(texture);
      vRequestBuilder = cameraDevice.createCaptureRequest(CameraDevice.TEMPLATE_PREVIEW);
      vRequestBuilder.addTarget(surface);
      cameraDevice.createCaptureSession(
              Arrays.asList(surface),
              new CameraCaptureSession.StateCallback() {

                @Override
                public void onConfigured(CameraCaptureSession cameraCaptureSession) {
                  if (null == cameraDevice) {
                    return;
                  }
                  captureSession = cameraCaptureSession;
                  try {
                    vRequestBuilder.set(
                            CaptureRequest.CONTROL_AF_MODE,
                            CaptureRequest.CONTROL_AF_MODE_CONTINUOUS_PICTURE);

                    vRequest = vRequestBuilder.build();
                    captureSession.setRepeatingRequest(vRequest, captureCallback, backgroundHandler);
                  } catch (CameraAccessException e) {
                    e.printStackTrace();
                  }
                }

                @Override
                public void onConfigureFailed(CameraCaptureSession cameraCaptureSession) {
                setStringPrediksi("Failed");
                }
              },
              null);
    } catch (CameraAccessException e) {
      e.printStackTrace();
    }
  }
  private void setUpCameraOutputs(int width, int height) {
    Activity activity = getActivity();
    CameraManager manager = (CameraManager) activity.getSystemService(Context.CAMERA_SERVICE);
    try {
      for (String cameraId : manager.getCameraIdList()) {
        CameraCharacteristics characteristics = manager.getCameraCharacteristics(cameraId);
        Integer facing = characteristics.get(CameraCharacteristics.LENS_FACING);
        if (facing != null && facing == CameraCharacteristics.LENS_FACING_FRONT) {
          continue;
        }

        StreamConfigurationMap map = characteristics.get(CameraCharacteristics.SCALER_STREAM_CONFIGURATION_MAP);
        if (map == null) {
          continue;
        }
        Size largest = Collections.max(Arrays.asList(map.getOutputSizes(ImageFormat.JPEG)), new camera_fragment_cobaaja.CompareSizesByArea());
        imageReader = ImageReader.newInstance(largest.getWidth(), largest.getHeight(), ImageFormat.JPEG, /*maxImages*/ 2);
        int displayRotation = activity.getWindowManager().getDefaultDisplay().getRotation();
        int sensorOrientation = characteristics.get(CameraCharacteristics.SENSOR_ORIENTATION);
        boolean swappedDimensions = false;
        switch (displayRotation) {
          case Surface.ROTATION_0:
          case Surface.ROTATION_180:
            if (sensorOrientation == 90 || sensorOrientation == 270) {
              swappedDimensions = true;
            }
            break;
          case Surface.ROTATION_90:
          case Surface.ROTATION_270:
            if (sensorOrientation == 0 || sensorOrientation == 180) {
              swappedDimensions = true;
            }
            break;
          default:
            Log.e(TAG, "Display rotation is invalid: " + displayRotation);
        }

        Point displaySize = new Point();
        activity.getWindowManager().getDefaultDisplay().getSize(displaySize);
        int rotatedPreviewWidth = width;
        int rotatedPreviewHeight = height;
        int maxPreviewWidth = displaySize.x;
        int maxPreviewHeight = displaySize.y;

        if (swappedDimensions) {
          rotatedPreviewWidth = height;
          rotatedPreviewHeight = width;
          maxPreviewWidth = displaySize.y;
          maxPreviewHeight = displaySize.x;
        }

        if (maxPreviewWidth > cam_width) {
          maxPreviewWidth = cam_width;
        }

        if (maxPreviewHeight > cam_height) {
          maxPreviewHeight = cam_height;
        }
        tampilanSize = chooseOptimalSize(
                   map.getOutputSizes(SurfaceTexture.class),
                   rotatedPreviewWidth,
                   rotatedPreviewHeight,
                   maxPreviewWidth,
                   maxPreviewHeight,
                   largest);

        int orientation = getResources().getConfiguration().orientation;
        if (orientation == Configuration.ORIENTATION_LANDSCAPE) {
          TexttureFitViewCamera.setAspectRatio(tampilanSize.getWidth(), tampilanSize.getHeight());
        } else {
          TexttureFitViewCamera.setAspectRatio(tampilanSize.getHeight(), tampilanSize.getWidth());
        }

        this.cameraId = cameraId;
        return;
      }
    } catch (CameraAccessException e) {
      e.printStackTrace();
    } catch (NullPointerException e) {
      e.printStackTrace();
    }
  }
  private void configureTransform(int viewWidth, int viewHeight) {
    Activity activity = getActivity();
    if (null == TexttureFitViewCamera || null == tampilanSize || null == activity) {
      return;
    }
    int rotation = activity.getWindowManager().getDefaultDisplay().getRotation();
    Matrix matrix = new Matrix();
    RectF viewRect = new RectF(0, 0, viewWidth, viewHeight);
    RectF bufferRect = new RectF(0, 0, tampilanSize.getHeight(), tampilanSize.getWidth());
    float centerX = viewRect.centerX();
    float centerY = viewRect.centerY();
    if (Surface.ROTATION_90 == rotation || Surface.ROTATION_270 == rotation) {
      bufferRect.offset(centerX - bufferRect.centerX(), centerY - bufferRect.centerY());
      matrix.setRectToRect(viewRect, bufferRect, Matrix.ScaleToFit.FILL);
      float scale =Math.max((float) viewHeight / tampilanSize.getHeight(),
                            (float) viewWidth / tampilanSize.getWidth());
      matrix.postScale(scale, scale, centerX, centerY);
      matrix.postRotate(90 * (rotation - 2), centerX, centerY);
    } else if (Surface.ROTATION_180 == rotation) {
      matrix.postRotate(180, centerX, centerY);
    }
    TexttureFitViewCamera.setTransform(matrix);
  }
  private static Size chooseOptimalSize(
          Size[] choices,
          int textureViewWidth,
          int textureViewHeight,
          int maxWidth,
          int maxHeight,
          Size aspectRatio) {

    List<Size> bigEnough = new ArrayList<>();
    List<Size> notBigEnough = new ArrayList<>();
    int w = aspectRatio.getWidth();
    int h = aspectRatio.getHeight();
    for (Size option : choices) {
      if (option.getWidth() <= maxWidth
              && option.getHeight() <= maxHeight
              && option.getHeight() == option.getWidth() * h / w) {
        if (option.getWidth() >= textureViewWidth && option.getHeight() >= textureViewHeight) {
          bigEnough.add(option);
        } else {
          notBigEnough.add(option);
        }
      }
    }


    if (bigEnough.size() > 0) {
      return Collections.min(bigEnough, new camera_fragment_cobaaja.CompareSizesByArea());
    } else if (notBigEnough.size() > 0) {
      return Collections.max(notBigEnough, new camera_fragment_cobaaja.CompareSizesByArea());
    } else {
      Log.e(TAG, "Couldn't find any suitable preview size");
      return choices[0];
    }
  }

  private static class CompareSizesByArea implements Comparator<Size> {
    public int compare(Size lhs, Size rhs) {
      return Long.signum(
              (long) lhs.getWidth() * lhs.getHeight() - (long) rhs.getWidth() * rhs.getHeight());
    }
  }
  //Pengaturan kamera -- akhir

  @Override
  public void onResume() {
    super.onResume();
    startBackgroundThread();
    if (TexttureFitViewCamera.isAvailable()) {
      openCamera(TexttureFitViewCamera.getWidth(), TexttureFitViewCamera.getHeight());
    } else {
      TexttureFitViewCamera.setSurfaceTextureListener(surfaceTextureListener);
    }
  }

  @Override
  public void onPause() {
    closeCamera();
    stopBackgroundThread();
    super.onPause();
  }

  @Override
  public void onDestroy() {
    proses_Kelas.close();
    super.onDestroy();
  }


  private void setStringPrediksi(final String text) {
      getActivity().runOnUiThread(
              new Runnable() {
                @Override
                public void run() {
                  hslPredikJawa.setText(text);
                  //hslPredikAksa.setText(hslPredikJawa.getText().toString());
                }
              });
  }

  private void setStringPrediksiMs(final String text) {
    final Activity activity = getActivity();
    if (activity != null) {
      activity.runOnUiThread(
              new Runnable() {
                @Override
                public void run() {
                  hslPredikWaktu.setText(text);
                }
              });
    }
  }

  private Runnable stepkalsifikasi = new Runnable() {
    public void run() {
      synchronized (obj) {
        if (runClassifier) {
          klasifikasi();
        }
      }
      backgroundHandler.post(stepkalsifikasi);
    }
  };


  private void klasifikasi() {
    if (proses_Kelas == null || getActivity() == null || cameraDevice == null) {
      setStringPrediksi("gagal di initialisasi");
      setStringPrediksiMs("gagal di initialisasi");
      return;
    }
    //try {
    //  backgroundThread.sleep(500);
    //} catch (InterruptedException e) {
   //   e.printStackTrace();
   // }
    Bitmap bitmap = TexttureFitViewCamera.getBitmap(proses_Klasifikasi.img_width, proses_Klasifikasi.img_height);
    String textToShow = proses_Kelas.Klasifikasi(bitmap);
    String textToShowMs = proses_Kelas.KlasifikasiMs(bitmap);
    bitmap.recycle();
    setStringPrediksi(textToShow);
    setStringPrediksiMs(textToShowMs);
  }

}
