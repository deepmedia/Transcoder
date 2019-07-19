package com.otaliastudios.transcoder.demo;

import android.app.Activity;
import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import android.os.SystemClock;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.widget.ProgressBar;
import android.widget.Toast;

import com.otaliastudios.transcoder.MediaTranscoder;
import com.otaliastudios.transcoder.internal.Logger;

import java.io.File;
import java.io.IOException;
import java.util.concurrent.Future;

import androidx.annotation.NonNull;
import androidx.core.content.FileProvider;


public class TranscoderActivity extends Activity {
    private static final String TAG = "TranscoderActivity";
    private static final Logger LOG = new Logger(TAG);

    private static final String FILE_PROVIDER_AUTHORITY = "com.otaliastudios.transcoder.demo.fileprovider";
    private static final int REQUEST_CODE_PICK = 1;
    private static final int PROGRESS_BAR_MAX = 1000;
    private Future<Void> mFuture;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        Logger.setLogLevel(Logger.LEVEL_VERBOSE);
        setContentView(R.layout.activity_transcoder);
        findViewById(R.id.select_video_button).setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                startActivityForResult(new Intent(Intent.ACTION_GET_CONTENT).setType("video/*"), REQUEST_CODE_PICK);
            }
        });
        findViewById(R.id.cancel_button).setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                mFuture.cancel(true);
            }
        });
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, final Intent data) {
        switch (requestCode) {
            case REQUEST_CODE_PICK: {
                final File file;
                if (resultCode == RESULT_OK) {
                    try {
                        File outputDir = new File(getExternalFilesDir(null), "outputs");
                        //noinspection ResultOfMethodCallIgnored
                        outputDir.mkdir();
                        file = File.createTempFile("transcode_test", ".mp4", outputDir);
                    } catch (IOException e) {
                        LOG.e("Failed to create temporary file.", e);
                        Toast.makeText(this, "Failed to create temporary file.", Toast.LENGTH_LONG).show();
                        return;
                    }

                    final ProgressBar progressBar = findViewById(R.id.progress_bar);
                    progressBar.setMax(PROGRESS_BAR_MAX);
                    final long startTime = SystemClock.uptimeMillis();
                    LOG.i("transcoding into " + file);
                    switchButtonEnabled(true);
                    mFuture = MediaTranscoder.into(file.getAbsolutePath())
                            .setDataSource(this, data.getData())
                            .setListener(new MediaTranscoder.Listener() {
                                @Override
                                public void onTranscodeProgress(double progress) {
                                    if (progress < 0) {
                                        progressBar.setIndeterminate(true);
                                    } else {
                                        progressBar.setIndeterminate(false);
                                        progressBar.setProgress((int) Math.round(progress * PROGRESS_BAR_MAX));
                                    }
                                }

                                @Override
                                public void onTranscodeCompleted(int successCode) {
                                    if (successCode == MediaTranscoder.SUCCESS_TRANSCODED) {
                                        LOG.i("transcoding took " + (SystemClock.uptimeMillis() - startTime) + "ms");
                                        onTranscodeFinished(true, "transcoded file placed on " + file);
                                        Uri uri = FileProvider.getUriForFile(TranscoderActivity.this, FILE_PROVIDER_AUTHORITY, file);
                                        startActivity(new Intent(Intent.ACTION_VIEW)
                                                .setDataAndType(uri, "video/mp4")
                                                .setFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION));
                                    } else if (successCode == MediaTranscoder.SUCCESS_NOT_NEEDED) {
                                        // Not sure this works
                                        LOG.i("Transcoding was not needed.");
                                        onTranscodeFinished(true, "Transcoding not needed, source file not touched.");
                                        startActivity(new Intent(Intent.ACTION_VIEW)
                                                .setDataAndType(data.getData(), "video/mp4")
                                                .setFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION));
                                    }
                                }

                                @Override
                                public void onTranscodeCanceled() {
                                    onTranscodeFinished(false, "Transcoder canceled.");
                                }

                                @Override
                                public void onTranscodeFailed(@NonNull Throwable exception) {
                                    onTranscodeFinished(false, "Transcoder error occurred.");
                                }
                            }).transcode();
                }
                break;
            }
            default:
                super.onActivityResult(requestCode, resultCode, data);
        }
    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        // Inflate the menu; this adds items to the action bar if it is present.
        getMenuInflater().inflate(R.menu.transcoder, menu);
        return true;
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        // Handle action bar item clicks here. The action bar will
        // automatically handle clicks on the Home/Up button, so long
        // as you specify a parent activity in AndroidManifest.xml.
        int id = item.getItemId();
        if (id == R.id.action_settings) {
            return true;
        }
        return super.onOptionsItemSelected(item);
    }

    private void onTranscodeFinished(boolean isSuccess, String toastMessage) {
        final ProgressBar progressBar = findViewById(R.id.progress_bar);
        progressBar.setIndeterminate(false);
        progressBar.setProgress(isSuccess ? PROGRESS_BAR_MAX : 0);
        switchButtonEnabled(false);
        Toast.makeText(TranscoderActivity.this, toastMessage, Toast.LENGTH_LONG).show();
    }

    private void switchButtonEnabled(boolean isProgress) {
        findViewById(R.id.select_video_button).setEnabled(!isProgress);
        findViewById(R.id.cancel_button).setEnabled(isProgress);
    }
}
