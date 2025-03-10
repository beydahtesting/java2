package com.example.mymcqscannerapp;

import android.graphics.Bitmap;
import android.util.Log;
import org.json.JSONArray;
import org.json.JSONObject;
import org.opencv.android.Utils;
import org.opencv.core.*;
import org.opencv.imgproc.Imgproc;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.ArrayList;
import java.util.List;
import java.util.Scanner;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class ImageProcessor {
    private static final Scalar GREEN = new Scalar(0, 255, 0);
    private static final Scalar RED = new Scalar(0, 0, 255);
    private static final double MATCH_THRESHOLD = 30.0;

    // Process image: Ensure correct channel ordering then crop/warp & threshold.
    public static Mat processImage(Mat image) {
        // Check if image is valid.
        if (image == null || image.empty()) {
            Log.e("ImageProcessor", "Input image is null or empty");
            return image;
        }

        // Ensure image is in BGR.
        if (image.channels() == 4) {
            Mat bgr = new Mat();
            Imgproc.cvtColor(image, bgr, Imgproc.COLOR_RGBA2BGR);
            image = bgr;
        } else if (image.channels() == 1) {
            Mat bgr = new Mat();
            Imgproc.cvtColor(image, bgr, Imgproc.COLOR_GRAY2BGR);
            image = bgr;
        }

        // Convert to grayscale.
        Mat gray = new Mat();
        Imgproc.cvtColor(image, gray, Imgproc.COLOR_BGR2GRAY);

        // Apply Gaussian blur.
        Mat blurred = new Mat();
        Imgproc.GaussianBlur(gray, blurred, new Size(5, 5), 0);

        // Apply adaptive thresholding.
        Mat thresh = new Mat();
        Imgproc.adaptiveThreshold(blurred, thresh, 255, Imgproc.ADAPTIVE_THRESH_GAUSSIAN_C,
                Imgproc.THRESH_BINARY_INV, 11, 2);

        // Edge detection.
        Mat edges = new Mat();
        Imgproc.Canny(thresh, edges, 50, 150);

        // Find contours.
        List<MatOfPoint> contours = new ArrayList<>();
        Imgproc.findContours(edges, contours, new Mat(), Imgproc.RETR_EXTERNAL, Imgproc.CHAIN_APPROX_SIMPLE);

        // Release temporary Mats.
        gray.release();
        blurred.release();
        thresh.release();
        edges.release();

        if (!contours.isEmpty()) {
            // Find the largest contour.
            MatOfPoint largestContour = contours.get(0);
            for (MatOfPoint cnt : contours) {
                if (Imgproc.contourArea(cnt) > Imgproc.contourArea(largestContour)) {
                    largestContour = cnt;
                }
            }

            double perimeter = Imgproc.arcLength(new MatOfPoint2f(largestContour.toArray()), true);
            MatOfPoint2f approx = new MatOfPoint2f();
            Imgproc.approxPolyDP(new MatOfPoint2f(largestContour.toArray()), approx, 0.02 * perimeter, true);

            // If a quadrilateral is detected, warp the image.
            if (approx.total() == 4) {
                MatOfPoint2f orderedPts = reorderPoints(approx);
                double width = 700, height = 800;
                MatOfPoint2f dst = new MatOfPoint2f(
                        new Point(0, 0),
                        new Point(width - 1, 0),
                        new Point(width - 1, height - 1),
                        new Point(0, height - 1)
                );
                Mat M = Imgproc.getPerspectiveTransform(orderedPts, dst);
                Mat warped = new Mat();
                Imgproc.warpPerspective(image, warped, M, new Size(width, height));

                approx.release();
                orderedPts.release();
                M.release();
                dst.release();

                return warped;
            }
        }
        return image.clone();
    }

    // Detect filled circles using an HSV-based blue mask.
    public static List<Point> detectFilledCircles(Mat image) {
        // Assume the image is in BGR.
        Mat hsv = new Mat();
        Imgproc.cvtColor(image, hsv, Imgproc.COLOR_BGR2HSV);

        Scalar lowerBlue = new Scalar(90, 50, 50);
        Scalar upperBlue = new Scalar(130, 255, 255);
        Mat mask = new Mat();
        Core.inRange(hsv, lowerBlue, upperBlue, mask);

        List<MatOfPoint> contours = new ArrayList<>();
        Imgproc.findContours(mask, contours, new Mat(), Imgproc.RETR_EXTERNAL, Imgproc.CHAIN_APPROX_SIMPLE);
        List<Point> filledCircles = new ArrayList<>();
        for (MatOfPoint cnt : contours) {
            double area = Imgproc.contourArea(cnt);
            if (area > 100 && area < 5000) {
                Point center = new Point();
                float[] radius = new float[1];
                Imgproc.minEnclosingCircle(new MatOfPoint2f(cnt.toArray()), center, radius);
                filledCircles.add(center);
            }
        }
        return filledCircles;
    }

    // Compare teacher and student circles:
    // - Correct answers are filled green.
    // - Missing answers are drawn as an empty green circle.
    // - Extra answers are filled in red.
    public static Mat compareCircles(List<Point> teacherCircles, List<Point> studentCircles, Mat image) {
        List<Point> correctMatches = new ArrayList<>();
        List<Point> unmatchedStudent = new ArrayList<>(studentCircles);
        for (Point t : teacherCircles) {
            boolean matchFound = false;
            for (Point s : unmatchedStudent) {
                if (Math.hypot(t.x - s.x, t.y - s.y) < MATCH_THRESHOLD) {
                    correctMatches.add(s);
                    matchFound = true;
                    break;
                }
            }
            if (!matchFound) {
                // Draw empty green circle for missing answer.
                Imgproc.circle(image, t, 10, GREEN, 1);
            }
        }
        // Draw teacher circles (outline).
        for (Point t : teacherCircles) {
            Imgproc.circle(image, t, 10, GREEN, 3);
        }
        // Fill correct student answers in green.
        for (Point s : correctMatches) {
            Imgproc.circle(image, s, 10, GREEN, -1);
        }
        // Mark extra student circles in red.
        for (Point s : unmatchedStudent) {
            if (!correctMatches.contains(s)) {
                Imgproc.circle(image, s, 10, RED, -1);
            }
        }
        Imgproc.cvtColor(image, image, Imgproc.COLOR_BGR2RGB);
        return image;
    }

    // Draw detected circles for visualization.
    public static Mat drawDetectedCircles(Mat image) {
        List<Point> circles = detectFilledCircles(image);
        Mat output = image.clone();
        for (Point p : circles) {
            Imgproc.circle(output, p, 10, GREEN, 2);
        }
        // Convert back to original color space before returning (if necessary)
        return output;
    }

    // Warp perspective based on the detected quadrilateral.

    // Reorder points to [top-left, top-right, bottom-right, bottom-left].
    private static MatOfPoint2f reorderPoints(MatOfPoint2f points) {
        Point[] pts = points.toArray();
        if (pts.length != 4) return points;
        Point[] ordered = new Point[4];
        double[] sums = new double[4];
        double[] diffs = new double[4];
        for (int i = 0; i < 4; i++) {
            sums[i] = pts[i].x + pts[i].y;
            diffs[i] = pts[i].y - pts[i].x;
        }
        int tl = 0, br = 0, tr = 0, bl = 0;
        for (int i = 1; i < 4; i++) {
            if (sums[i] < sums[tl]) tl = i;
            if (sums[i] > sums[br]) br = i;
            if (diffs[i] < diffs[tr]) tr = i;
            if (diffs[i] > diffs[bl]) bl = i;
        }
        ordered[0] = pts[tl];
        ordered[1] = pts[tr];
        ordered[2] = pts[br];
        ordered[3] = pts[bl];
        return new MatOfPoint2f(ordered);
    }

    // Gemini API call to extract student info.
    // If the API call fails, logs the error and returns a dummy JSONObject.
    public static JSONObject extractStudentInfo(Bitmap bitmap) {
        try {
            // Compress image and encode as Base64 with NO_WRAP to avoid newlines.
            byte[] imageBytes = ImageUtils.compressToJPEG(bitmap, 30);
            String encodedImage = android.util.Base64.encodeToString(imageBytes, android.util.Base64.NO_WRAP);

            JSONObject inlineData = new JSONObject();
            inlineData.put("mime_type", "image/jpeg");
            inlineData.put("data", encodedImage);

            JSONArray partsArray = new JSONArray();
            partsArray.put(new JSONObject().put("inline_data", inlineData));
            partsArray.put(new JSONObject().put("text", "Extract only the student's name and roll number from this exam sheet image."));

            JSONObject contentObject = new JSONObject();
            contentObject.put("parts", partsArray);

            JSONArray contentsArray = new JSONArray();
            contentsArray.put(contentObject);

            JSONObject payload = new JSONObject();
            payload.put("contents", contentsArray);

            // Log the payload
            Log.d("GeminiAPI", "Payload: " + payload.toString());

            URL url = new URL("https://generativelanguage.googleapis.com/v1beta/models/gemini-1.5-flash:generateContent?key=AIzaSyDGalTcZxd_xWk1ZU6SQqgHl3KR5ZvKpoc");
            HttpURLConnection conn = (HttpURLConnection) url.openConnection();
            conn.setDoOutput(true);
            conn.setRequestMethod("POST");
            conn.setRequestProperty("Content-Type", "application/json");

            try (OutputStream os = conn.getOutputStream()) {
                os.write(payload.toString().getBytes("UTF-8"));
            }

            int responseCode = conn.getResponseCode();
            if (responseCode != HttpURLConnection.HTTP_OK) {
                // If not 200, read the error stream and log it.
                Scanner errorScanner = new Scanner(conn.getErrorStream());
                String errorResponse = errorScanner.useDelimiter("\\A").hasNext() ? errorScanner.next() : "No error details";
                errorScanner.close();
                Log.e("GeminiAPI", "HTTP Error Code: " + responseCode + " Response: " + errorResponse);
                throw new Exception("HTTP Error Code " + responseCode);
            }

            Scanner scanner = new Scanner(conn.getInputStream());
            String response = scanner.useDelimiter("\\A").hasNext() ? scanner.next() : "";
            scanner.close();
            Log.d("GeminiAPI", "Response: " + response);

            return new JSONObject(response);
        } catch (Exception e) {
            Log.e("GeminiAPI", "Error extracting student info", e);
            // Return dummy values if API call fails.
            try {
                JSONObject dummy = new JSONObject();
                dummy.put("name", "John Doe");
                dummy.put("rollNumber", "12345");
                return dummy;
            } catch (Exception ex) {
                Log.e("GeminiAPI", "Error creating dummy student info", ex);
                return new JSONObject();
            }
        }
    }

    // Helper method: Extract a field value from text using regex.
    // It searches for the given label and returns the text following it until the first newline, quote, or end of string.
    public static String extractField(String text, String label) {
        // Replace escaped newlines with actual newlines.
        text = text.replace("\\n", "\n");
        Pattern pattern = Pattern.compile(Pattern.quote(label) + "\\s*(.*?)(\\n|\"|$)");
        Matcher matcher = pattern.matcher(text);
        if (matcher.find()) {
            // Remove asterisks and trim extra whitespace.
            return matcher.group(1).replace("*", "").trim();
        }
        return "";
    }
}