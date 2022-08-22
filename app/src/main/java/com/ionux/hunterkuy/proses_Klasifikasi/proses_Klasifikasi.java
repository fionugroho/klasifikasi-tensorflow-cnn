package com.ionux.hunterkuy.proses_Klasifikasi;

import android.app.Activity;
import android.content.res.AssetFileDescriptor;
import android.graphics.Bitmap;
import android.os.SystemClock;
import android.util.Log;

import org.tensorflow.lite.Interpreter;

import java.io.BufferedReader;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.MappedByteBuffer;
import java.nio.channels.FileChannel;
import java.util.AbstractMap;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.PriorityQueue;


public class proses_Klasifikasi {

  private static final String TAG = "CamHunter";
  private static final String lok_model = "model_huntguys.tflite";
  private static final String lok_label = "labels_huntguys.txt";
  public static final int img_width = 128;
  public static final int img_height = 128;
  private static final int result_show = 1;
  private static final int result_showMs = 3;
  private static final int batch_size = 1;
  private static final int pixel_size = 3;
  private static final int img_mean = 128;
  private static final float img_std = 128.0f;
  private int[] imgValue = new int[img_width * img_height];
  private Interpreter interpreter;
  private List<String> labelList;
  private ByteBuffer imgData = null;
  private float[][] labelProbArray = null;
  private float[][] filterLabelProbArray = null;
  private static final int filter_stage = 3;
  private static final float filter_factor = 0.4f;
  //Untuk mengkomparasi menampilkan hasil label string
  private PriorityQueue<Map.Entry<String, Float>> tampilkanLabel = new PriorityQueue<>(result_show, new Comparator<Map.Entry<String, Float>>() {
            @Override
            public int compare(Map.Entry<String, Float> compare1, Map.Entry<String, Float> compare2) {
              return (compare1.getValue()).compareTo(compare2.getValue());
            }
          });

  // Interpreter klasifikasi tensorflow lite.
  public proses_Klasifikasi(Activity activity) throws IOException {
    interpreter = new Interpreter(loadModelFile(activity));
    //ambil label dari model
    labelList = loadLabelList(activity);
    //input image bitmap buffer
    imgData = ByteBuffer.allocateDirect(4 * batch_size * img_width * img_height * pixel_size);
    imgData.order(ByteOrder.nativeOrder());
    labelProbArray = new float[1][labelList.size()];
    filterLabelProbArray = new float[filter_stage][labelList.size()];
    Log.d(TAG, "Initialisasi klasifikasi pada tensorflow lite.");
  }

  //Mengambil data label dari asset
  private List<String> loadLabelList(Activity activity) throws IOException {
    List<String> labelList = new ArrayList<String>();
    BufferedReader BR = new BufferedReader(new InputStreamReader(activity.getAssets().open(lok_label)));
    String line;
    while ((line = BR.readLine()) != null) {
      labelList.add(line);
    }
    BR.close();
    return labelList;
  }

  // generet load model di asset jadi bytebuffer mapping di memori
  private MappedByteBuffer loadModelFile(Activity activity) throws IOException {
    AssetFileDescriptor AFD = activity.getAssets().openFd(lok_model);
    FileInputStream FIS = new FileInputStream(AFD.getFileDescriptor());
    FileChannel fileChannel = FIS.getChannel();
    long startOffset = AFD.getStartOffset();
    long declaredLength = AFD.getDeclaredLength();
    return fileChannel.map(FileChannel.MapMode.READ_ONLY, startOffset, declaredLength);
  }

  //Menjadikan gambar Bitmap ke bytebuffer
  private void convertBitmapToByteBuffer(Bitmap bitmap) {
    if (imgData == null) {
      return;
    }
    imgData.rewind();
    bitmap.getPixels(imgValue, 0, bitmap.getWidth(), 0, 0, bitmap.getWidth(), bitmap.getHeight());
    // Konversi data gamabr ke float
    int pixel = 0;
    long startTime = SystemClock.uptimeMillis();
    for (int i = 0; i < img_width; ++i) {
      for (int j = 0; j < img_height; ++j) {
        final int val = imgValue[pixel++];
        //maengambil nilai rgb menjadi float
        imgData.putFloat((((val >> 16) & 0xFF)-img_mean)/img_std);
        imgData.putFloat((((val >> 8) & 0xFF)-img_mean)/img_std);
        imgData.putFloat((((val) & 0xFF)-img_mean)/img_std);
      }
    }
    long endTime = SystemClock.uptimeMillis();
    Log.d(TAG, "Waktu yang dibutuhkan bytearray ke float: " + Long.toString(endTime - startTime));
  }

  //proses intrepeter mengklasfikasikan benda pada view nonMs
  public String Klasifikasi(Bitmap bitmap) {
    if (interpreter == null) {
      Log.e(TAG, "gagal initialisasi klasifikasi gambar");
      return "Uninitialized";
    }
    convertBitmapToByteBuffer(bitmap);
    interpreter.run(imgData, labelProbArray);
    // smooth the results
    applyFilter();
    // print the results
    String keStringPrediksi = printTopKLabels();
    return keStringPrediksi;
  }

  //proses mengklasfikasikan benda pada view Ms
  public String KlasifikasiMs (Bitmap bitmap) {
    if (interpreter == null) {
      Log.e(TAG, "gagal initialisasi klasifikasi gambar");
      return "Uninitialized";
    }
    convertBitmapToByteBuffer(bitmap);
    //Prediksi
    long startTime = SystemClock.uptimeMillis();
    interpreter.run(imgData, labelProbArray);
    long endTime = SystemClock.uptimeMillis();
    Log.d(TAG, "Waktu yang dibutuhkan : " + Long.toString(endTime - startTime));
    // smooth the results
    applyFilter();
    // print the results
    String keStringPrediksiMs = printTopKLabelsMs();
    keStringPrediksiMs ="Latency : "+ Long.toString (endTime - startTime) + " ms \n" + keStringPrediksiMs;
    return keStringPrediksiMs;
  }

  void applyFilter(){
    int num_labels =  labelList.size();

    // Low pass filter `labelProbArray` into the first stage of the filter.
    for(int j=0; j<num_labels; ++j){
      filterLabelProbArray[0][j] += filter_factor*(labelProbArray[0][j] - filterLabelProbArray[0][j]);
    }

    // Low pass filter each stage into the next.
    for (int i=1; i<filter_stage; ++i){
      for(int j=0; j<num_labels; ++j){
        filterLabelProbArray[i][j] += filter_factor*(filterLabelProbArray[i-1][j] - filterLabelProbArray[i][j]);
      }
    }

    // Copy the last stage filter output back to `labelProbArray`.
    for(int j=0; j<num_labels; ++j){
      labelProbArray[0][j] = filterLabelProbArray[filter_stage-1][j];
    }
  }

  //Tampilkan top-K label ke String UI (Hasil TOP tanpa Waktu)
  private String printTopKLabels() {
    for (int i = 0; i < labelList.size(); ++i) {
      tampilkanLabel.add(new AbstractMap.SimpleEntry<>(labelList.get(i), labelProbArray[0][i]));
      if (tampilkanLabel.size() > result_show) {
        tampilkanLabel.poll();
      }
    }
    String textToShow = "";
    final int size = tampilkanLabel.size();
    for (int i = 0; i < size; ++i) {
      Map.Entry<String, Float> label = tampilkanLabel.poll();
      textToShow = String.format(label.getKey(),label.getValue()) + textToShow;
    }
    return textToShow;
  }

  //Tampilkan top-K label ke String UI (Hasil TOP 2 dengan waktu)
  private String printTopKLabelsMs() {
    for (int i = 0; i < labelList.size(); ++i) {
      tampilkanLabel.add(new AbstractMap.SimpleEntry<>(labelList.get(i), labelProbArray[0][i]));
      if (tampilkanLabel.size() > result_showMs) {
        tampilkanLabel.poll();
      }
    }
    String textToShowMs = "";
    final int size = tampilkanLabel.size();
    for (int i = 0; i < size; ++i) {
      Map.Entry<String, Float> label = tampilkanLabel.poll();
      textToShowMs = String.format("\n %s : %4.2f \n",label.getKey(),label.getValue())+ textToShowMs;
    }
    return textToShowMs;
  }

  //Tutup klasifikasi tensorflow lite
  public void close() {
      interpreter.close();
      interpreter = null;
  }

}
