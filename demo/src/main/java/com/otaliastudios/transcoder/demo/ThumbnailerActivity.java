package com.otaliastudios.transcoder.demo;

import android.annotation.SuppressLint;
import android.content.ClipData;
import android.content.Intent;
import android.graphics.Color;
import android.net.Uri;
import android.os.Bundle;
import android.os.SystemClock;
import android.text.Editable;
import android.text.TextWatcher;
import android.util.TypedValue;
import android.view.ViewGroup;
import android.widget.EditText;
import android.widget.ImageView;
import android.widget.ProgressBar;
import android.widget.RadioGroup;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;

import com.otaliastudios.transcoder.ThumbnailerListener;
import com.otaliastudios.transcoder.ThumbnailerOptions;
import com.otaliastudios.transcoder.internal.utils.Logger;
import com.otaliastudios.transcoder.resize.AspectRatioResizer;
import com.otaliastudios.transcoder.resize.FractionResizer;
import com.otaliastudios.transcoder.resize.MultiResizer;
import com.otaliastudios.transcoder.resize.PassThroughResizer;
import com.otaliastudios.transcoder.source.DataSource;
import com.otaliastudios.transcoder.source.TrimDataSource;
import com.otaliastudios.transcoder.source.UriDataSource;
import com.otaliastudios.transcoder.thumbnail.Thumbnail;
import com.otaliastudios.transcoder.thumbnail.UniformThumbnailRequest;

import org.jetbrains.annotations.NotNull;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.Future;
import java.util.function.Consumer;

import kotlin.collections.ArraysKt;
import kotlin.jvm.functions.Function1;

import static android.util.TypedValue.COMPLEX_UNIT_DIP;


public class ThumbnailerActivity extends AppCompatActivity implements
        ThumbnailerListener {

    private static final Logger LOG = new Logger("TranscoderActivity");

    private static final int REQUEST_CODE_PICK = 1;
    private static final int PROGRESS_BAR_MAX = 1000;

    private RadioGroup mVideoResolutionGroup;
    private RadioGroup mVideoAspectGroup;
    private RadioGroup mVideoRotationGroup;

    private ProgressBar mProgressView;
    private TextView mButtonView;
    private EditText mTrimStartView;
    private EditText mTrimEndView;
    private ViewGroup mThumbnailsView;

    private boolean mIsTranscoding;
    private Future<Void> mTranscodeFuture;

    @SuppressLint("SetTextI18n")
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        Logger.setLogLevel(Logger.LEVEL_VERBOSE);
        setContentView(R.layout.activity_thumbnailer);

        mThumbnailsView = findViewById(R.id.thumbnails);
        mButtonView = findViewById(R.id.button);
        mButtonView.setOnClickListener(v -> {
            if (!mIsTranscoding) {
                startActivityForResult(new Intent(Intent.ACTION_GET_CONTENT)
                        .setType("video/*")
                        .putExtra(Intent.EXTRA_ALLOW_MULTIPLE, true), REQUEST_CODE_PICK);
            } else {
                mTranscodeFuture.cancel(true);
            }
        });

        mProgressView = findViewById(R.id.progress);
        mTrimStartView = findViewById(R.id.trim_start);
        mTrimEndView = findViewById(R.id.trim_end);
        mVideoResolutionGroup = findViewById(R.id.resolution);
        mVideoAspectGroup = findViewById(R.id.aspect);
        mVideoRotationGroup = findViewById(R.id.rotation);
        setIsTranscoding(false, null);
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, final Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (requestCode == REQUEST_CODE_PICK
                && resultCode == RESULT_OK
                && data != null) {
            if (data.getData() != null) {
                thumbnails(data.getData());
            } else if (data.getClipData() != null) {
                ClipData clipData = data.getClipData();
                List<Uri> uris = new ArrayList<>();
                for (int i = 0; i < clipData.getItemCount(); i++) {
                    uris.add(clipData.getItemAt(i).getUri());
                }
                thumbnails(uris.toArray(new Uri[0]));
            }
        }
    }

    private void thumbnails(@NonNull Uri... uris) {
        LOG.e("Building sources...");
        List<DataSource> sources = ArraysKt.map(uris, uri -> new UriDataSource(this, uri));
        long trimStart = 0, trimEnd = 0;
        try {
            trimStart = Long.parseLong(mTrimStartView.getText().toString());
        } catch (NumberFormatException e) {
            LOG.w("Failed to read trimStart value.", e);
        }
        try {
            trimEnd = Long.parseLong(mTrimEndView.getText().toString());
        } catch (NumberFormatException e) {
            LOG.w("Failed to read trimEnd value.", e);
        }
        trimStart = Math.max(0, trimStart) * 1000000;
        trimEnd = Math.max(0, trimEnd) * 1000000;
        sources.set(0, new TrimDataSource(sources.get(0), trimStart, trimEnd));

        LOG.e("Building options...");
        ThumbnailerOptions.Builder builder = new ThumbnailerOptions.Builder();
        builder.addThumbnailRequest(new UniformThumbnailRequest(8));
        builder.setListener(this);
        for (DataSource source : sources) {
            builder.addDataSource(source);
        }

        float aspectRatio;
        switch (mVideoAspectGroup.getCheckedRadioButtonId()) {
            case R.id.aspect_169: aspectRatio = 16F / 9F; break;
            case R.id.aspect_43: aspectRatio = 4F / 3F; break;
            case R.id.aspect_square: aspectRatio = 1F; break;
            default: aspectRatio = 0F;
        }
        if (aspectRatio > 0) {
            builder.addResizer(new AspectRatioResizer(aspectRatio));
        }
        float fraction;
        switch (mVideoResolutionGroup.getCheckedRadioButtonId()) {
            case R.id.resolution_half: fraction = 0.5F; break;
            case R.id.resolution_third: fraction = 1F / 3F; break;
            default: fraction = 1F;
        }
        builder.addResizer(new FractionResizer(fraction));
        int rotation;
        switch (mVideoRotationGroup.getCheckedRadioButtonId()) {
            case R.id.rotation_90: rotation = 90; break;
            case R.id.rotation_180: rotation = 180; break;
            case R.id.rotation_270: rotation = 270; break;
            default: rotation = 0;
        }
        builder.setRotation(rotation);

        // Launch the transcoding operation.
        LOG.e("Starting transcoding!");
        setIsTranscoding(true, null);
        mTranscodeFuture = builder.thumbnails();
    }

    @Override
    public void onThumbnail(@NotNull Thumbnail thumbnail) {
        float size = TypedValue.applyDimension(COMPLEX_UNIT_DIP, 96, getResources().getDisplayMetrics());
        ImageView view = new ImageView(this);
        view.setLayoutParams(new ViewGroup.LayoutParams((int) size, (int) size));
        view.setScaleType(ImageView.ScaleType.CENTER_INSIDE);
        view.setBackgroundColor(getResources().getColor(R.color.colorPrimary));
        view.setImageBitmap(thumbnail.getBitmap());
        mThumbnailsView.addView(view);
        double progress = (float) mThumbnailsView.getChildCount() / 8;
        mProgressView.setIndeterminate(false);
        mProgressView.setProgress((int) Math.round(progress * PROGRESS_BAR_MAX));
    }

    @Override
    public void onThumbnailsCanceled() {
        setIsTranscoding(false, "Operation canceled.");
    }

    @Override
    public void onThumbnailsFailed(@NotNull Throwable exception) {
        setIsTranscoding(false, "Error occurred. " + exception.getMessage());
    }

    @Override
    public void onThumbnailsCompleted(@NotNull List<Thumbnail> thumbnails) {
        setIsTranscoding(false, "Extracted " + thumbnails.size() + " thumbnails.");
    }

    private void setIsTranscoding(boolean isTranscoding, @Nullable String message) {
        mProgressView.setMax(PROGRESS_BAR_MAX);
        mProgressView.setProgress(0);
        if (isTranscoding) {
            mThumbnailsView.removeAllViews();
        }
        if (message != null) {
            Toast.makeText(this, message, Toast.LENGTH_LONG).show();
        }
        mIsTranscoding = isTranscoding;
        mButtonView.setText(mIsTranscoding ? "Cancel Thumbnails" : "Select Videos & Transcode");
    }
}
