package com.example.mymcqscannerapp;

import android.app.Activity;
import android.content.Intent;
import android.graphics.Bitmap;
import android.os.Bundle;
import android.widget.Button;
import android.widget.Toast;
import com.github.chrisbanes.photoview.PhotoView;

public class TeacherConfirmActivity extends Activity {
    private PhotoView imageView;
    private Button btnTeacherRetake, btnTeacherConfirm;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_teacher_confirm);

        imageView = findViewById(R.id.teacher_confirm_image_view);
        btnTeacherRetake = findViewById(R.id.btn_teacher_retake);
        btnTeacherConfirm = findViewById(R.id.btn_teacher_confirm);

        Bitmap teacherBitmap = ImageCache.getInstance().getTeacherImage();
        if (teacherBitmap == null) {
            Toast.makeText(this, "No teacher image found", Toast.LENGTH_SHORT).show();
            finish();
            return;
        }
        imageView.setImageBitmap(teacherBitmap);

        btnTeacherRetake.setOnClickListener(v -> {
            startActivity(new Intent(TeacherConfirmActivity.this, TeacherImageActivity.class));
            finish();
        });

        btnTeacherConfirm.setOnClickListener(v -> {
            // After confirming teacher image, proceed to StudentImageActivity.
            startActivity(new Intent(TeacherConfirmActivity.this, StudentImageActivity.class));
            finish();
        });
    }
}
