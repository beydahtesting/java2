package com.example.mymcqscannerapp;

import android.app.Activity;
import android.content.Intent;
import android.graphics.Bitmap;
import android.os.Bundle;
import android.widget.Button;
import android.widget.Toast;
import com.github.chrisbanes.photoview.PhotoView;

public class StudentConfirmActivity extends Activity {
    private PhotoView imageView;
    private Button btnStudentRetake, btnStudentConfirm;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_student_confirm);

        imageView = findViewById(R.id.student_confirm_image_view);
        btnStudentRetake = findViewById(R.id.btn_student_retake);
        btnStudentConfirm = findViewById(R.id.btn_student_confirm);

        Bitmap studentBitmap = ImageCache.getInstance().getStudentImage();
        if (studentBitmap == null) {
            Toast.makeText(this, "No student image found", Toast.LENGTH_SHORT).show();
            finish();
            return;
        }
        imageView.setImageBitmap(studentBitmap);

        btnStudentRetake.setOnClickListener(v -> {
            startActivity(new Intent(StudentConfirmActivity.this, StudentImageActivity.class));
            finish();
        });

        btnStudentConfirm.setOnClickListener(v -> {
            // Proceed to ResultActivity.
            startActivity(new Intent(StudentConfirmActivity.this, ResultActivity.class));
            finish();
        });
    }
}
