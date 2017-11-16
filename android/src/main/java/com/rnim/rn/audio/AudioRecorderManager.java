package com.rnim.rn.audio;

import android.Manifest;
import android.content.BroadcastReceiver;
import android.content.Context;

import com.facebook.react.bridge.ReactApplicationContext;
import com.facebook.react.bridge.ReactContextBaseJavaModule;
import com.facebook.react.bridge.ReactMethod;

import com.facebook.react.bridge.Arguments;
import com.facebook.react.bridge.Promise;
import com.facebook.react.bridge.ReadableMap;
import com.facebook.react.bridge.WritableArray;
import com.facebook.react.bridge.WritableMap;

import java.io.File;
import java.io.IOException;
import java.util.HashMap;
import java.util.Map;
import java.util.Timer;
import java.util.TimerTask;

import android.content.Intent;
import android.content.IntentFilter;
import android.content.pm.PackageManager;
import android.os.Environment;
import android.media.MediaRecorder;
import android.media.AudioManager;
import android.support.v4.app.ActivityCompat;
import android.support.v4.content.ContextCompat;
import android.util.Log;
import com.facebook.react.modules.core.DeviceEventManagerModule;

import java.io.FileInputStream;

class AudioRecorderManager extends ReactContextBaseJavaModule {

  private static final String TAG = "ReactNativeAudio";

  private static final String DocumentDirectoryPath = "DocumentDirectoryPath";
  private static final String PicturesDirectoryPath = "PicturesDirectoryPath";
  private static final String MainBundlePath = "MainBundlePath";
  private static final String CachesDirectoryPath = "CachesDirectoryPath";
  private static final String LibraryDirectoryPath = "LibraryDirectoryPath";
  private static final String MusicDirectoryPath = "MusicDirectoryPath";
  private static final String DownloadsDirectoryPath = "DownloadsDirectoryPath";

  private static final String SOURCE_BUILT_IN_MICROPHONE = "builtInMicrophone";
  private static final String SOURCE_BLUETOOTH_HFP = "bluetoothHFP";

  private Context context;
  private MediaRecorder recorder;
  private String currentOutputFile;
  private boolean isRecording = false;
  private Timer timer;
  private int recorderSecondsElapsed;
  private String preferredInput =SOURCE_BUILT_IN_MICROPHONE;
  private Promise mPromise;

  private final BroadcastReceiver mBroadcastReceiver = new BroadcastReceiver() {

    @Override
    public void onReceive(Context context, Intent intent) {
      try {
        int state = intent.getIntExtra(AudioManager.EXTRA_SCO_AUDIO_STATE, -1);

        if (AudioManager.SCO_AUDIO_STATE_CONNECTED == state) {
          context.unregisterReceiver(this);

          recorder.start();
          isRecording = true;
          startTimer();
          mPromise.resolve(currentOutputFile);
          context.unregisterReceiver(this);
        } else {
          logAndRejectPromise(mPromise, "BLUETOOTH_NOT_CONNECTED", "Couldn't initiate a connection to the HFP device");
        }
      } catch (Exception e) {
        // mPromise.reject("BLUETOOTH_NOT_CONNECTED", "There was an error connecting to the HFP device", e);
      }
    }
  };


  public AudioRecorderManager(ReactApplicationContext reactContext) {
    super(reactContext);
    this.context = reactContext;

  }

  @Override
  public Map<String, Object> getConstants() {
    Map<String, Object> constants = new HashMap<>();
    constants.put(DocumentDirectoryPath, this.getReactApplicationContext().getFilesDir().getAbsolutePath());
    constants.put(PicturesDirectoryPath, Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_PICTURES).getAbsolutePath());
    constants.put(MainBundlePath, "");
    constants.put(CachesDirectoryPath, this.getReactApplicationContext().getCacheDir().getAbsolutePath());
    constants.put(LibraryDirectoryPath, "");
    constants.put(MusicDirectoryPath, Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_MUSIC).getAbsolutePath());
    constants.put(DownloadsDirectoryPath, Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS).getAbsolutePath());
    return constants;
  }

  @Override
  public String getName() {
    return "AudioRecorderManager";
  }

  @ReactMethod
  public void checkAuthorizationStatus(Promise promise) {
    int permissionCheck = ContextCompat.checkSelfPermission(getCurrentActivity(),
            Manifest.permission.RECORD_AUDIO);
    boolean permissionGranted = permissionCheck == PackageManager.PERMISSION_GRANTED;
    promise.resolve(permissionGranted);
  }

  @ReactMethod
  public void prepareRecordingAtPath(String recordingPath, ReadableMap recordingSettings, Promise promise) {
    if (isRecording){
      logAndRejectPromise(promise, "INVALID_STATE", "Please call stopRecording before starting recording");
    }

    recorder = new MediaRecorder();
    try {
      preferredInput = recordingSettings.getString("preferredInput");
      recorder.setAudioSource(MediaRecorder.AudioSource.MIC);
      int outputFormat = getOutputFormatFromString(recordingSettings.getString("OutputFormat"));
      recorder.setOutputFormat(outputFormat);
      int audioEncoder = getAudioEncoderFromString(recordingSettings.getString("AudioEncoding"));
      recorder.setAudioEncoder(audioEncoder);
      recorder.setAudioSamplingRate(recordingSettings.getInt("SampleRate"));
      recorder.setAudioChannels(recordingSettings.getInt("Channels"));
      recorder.setAudioEncodingBitRate(recordingSettings.getInt("AudioEncodingBitRate"));
      recorder.setOutputFile(recordingPath);
    }
    catch(final Exception e) {
      logAndRejectPromise(promise, "COULDNT_CONFIGURE_MEDIA_RECORDER" , "Make sure you've added RECORD_AUDIO permission to your AndroidManifest.xml file "+e.getMessage());
      Log.e("RNAudio", "Error", e);
      return;
    }

    currentOutputFile = recordingPath;
    try {
      recorder.prepare();
      promise.resolve(currentOutputFile);
    } catch (final Exception e) {
      logAndRejectPromise(promise, "COULDNT_PREPARE_RECORDING_AT_PATH "+recordingPath, e.getMessage());
    }

  }

  @ReactMethod
  public void getAvailableInputs(Promise promise) {
    WritableArray array = Arguments.createArray();
    promise.resolve(array);
  }

  private int getAudioEncoderFromString(String audioEncoder) {
    switch (audioEncoder) {
      case "aac":
        return MediaRecorder.AudioEncoder.AAC;
      case "aac_eld":
        return MediaRecorder.AudioEncoder.AAC_ELD;
      case "amr_nb":
        return MediaRecorder.AudioEncoder.AMR_NB;
      case "amr_wb":
        return MediaRecorder.AudioEncoder.AMR_WB;
      case "he_aac":
        return MediaRecorder.AudioEncoder.HE_AAC;
      case "vorbis":
        return MediaRecorder.AudioEncoder.VORBIS;
      default:
        Log.d("INVALID_AUDIO_ENCODER", "USING MediaRecorder.AudioEncoder.DEFAULT instead of "+audioEncoder+": "+MediaRecorder.AudioEncoder.DEFAULT);
        return MediaRecorder.AudioEncoder.DEFAULT;
    }
  }

  private int getOutputFormatFromString(String outputFormat) {
    switch (outputFormat) {
      case "mpeg_4":
        return MediaRecorder.OutputFormat.MPEG_4;
      case "aac_adts":
        return MediaRecorder.OutputFormat.AAC_ADTS;
      case "amr_nb":
        return MediaRecorder.OutputFormat.AMR_NB;
      case "amr_wb":
        return MediaRecorder.OutputFormat.AMR_WB;
      case "three_gpp":
        return MediaRecorder.OutputFormat.THREE_GPP;
      case "webm":
        return MediaRecorder.OutputFormat.WEBM;
      default:
        Log.d("INVALID_OUPUT_FORMAT", "USING MediaRecorder.OutputFormat.DEFAULT : "+MediaRecorder.OutputFormat.DEFAULT);
        return MediaRecorder.OutputFormat.DEFAULT;

    }
  }

  @ReactMethod
  public void startRecording(Promise promise){
    if (recorder == null){
      logAndRejectPromise(promise, "RECORDING_NOT_PREPARED", "Please call prepareRecordingAtPath before starting recording");
      return;
    }
    if (isRecording){
      logAndRejectPromise(promise, "INVALID_STATE", "Please call stopRecording before starting recording");
      return;
    }


    if (preferredInput.equals(SOURCE_BLUETOOTH_HFP)) {
      mPromise = promise;
      AudioManager am = (AudioManager) context.getSystemService(Context.AUDIO_SERVICE);
      context.registerReceiver(mBroadcastReceiver, new IntentFilter(AudioManager.ACTION_SCO_AUDIO_STATE_UPDATED));

      am.startBluetoothSco();
    } else {
      recorder.start();
      isRecording = true;
      startTimer();
      promise.resolve(currentOutputFile);
    }
  }

  @ReactMethod
  public void stopRecording(Promise promise){
    if (!isRecording){
      logAndRejectPromise(promise, "INVALID_STATE", "Please call startRecording before stopping recording");
      return;
    }

    stopTimer();
    isRecording = false;

    try {
      recorder.stop();
      recorder.release();
    }
    catch (final RuntimeException e) {
      // https://developer.android.com/reference/android/media/MediaRecorder.html#stop()
      logAndRejectPromise(promise, "RUNTIME_EXCEPTION", "No valid audio data received. You may be using a device that can't record audio.");
      return;
    }
    finally {
      recorder = null;
    }

    promise.resolve(currentOutputFile);
    sendEvent("recordingFinished", null);
  }

  @ReactMethod
  public void pauseRecording(Promise promise){
    // Added this function to have the same api for android and iOS, stops recording now
    stopRecording(promise);
  }

  private void startTimer(){
    stopTimer();
    timer = new Timer();
    timer.scheduleAtFixedRate(new TimerTask() {
      @Override
      public void run() {
        WritableMap body = Arguments.createMap();
        body.putInt("currentTime", recorderSecondsElapsed);
        sendEvent("recordingProgress", body);
        recorderSecondsElapsed++;
      }
    }, 0, 1000);
  }

  private void stopTimer(){
    recorderSecondsElapsed = 0;
    if (timer != null) {
      timer.cancel();
      timer.purge();
      timer = null;
    }
  }

  private void sendEvent(String eventName, Object params) {
    getReactApplicationContext()
            .getJSModule(DeviceEventManagerModule.RCTDeviceEventEmitter.class)
            .emit(eventName, params);
  }

  private void logAndRejectPromise(Promise promise, String errorCode, String errorMessage) {
    Log.e(TAG, errorMessage);
    promise.reject(errorCode, errorMessage);
  }

}
