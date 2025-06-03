package com.github.sticker.util;

import javafx.scene.image.Image;
import net.sourceforge.tess4j.Tesseract;
import net.sourceforge.tess4j.TesseractException;
import net.sourceforge.tess4j.Word;
import javafx.embed.swing.SwingFXUtils;
import java.awt.image.BufferedImage;
import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.List;
import java.awt.Rectangle;

public class OCRUtil {
    private static Tesseract tesseract;
    
    /**
     * OCR识别结果类
     */
    public static class OCRResult {
        private final String text;
        private final double x;
        private final double y;
        private final double width;
        private final double height;
        private final float confidence;

        public OCRResult(String text, double x, double y, double width, double height, float confidence) {
            this.text = text;
            this.x = x;
            this.y = y;
            this.width = width;
            this.height = height;
            this.confidence = confidence;
        }

        public String getText() { return text; }
        public double getX() { return x; }
        public double getY() { return y; }
        public double getWidth() { return width; }
        public double getHeight() { return height; }
        public float getConfidence() { return confidence; }
    }

    static {
        initializeTesseract();
    }

    private static void initializeTesseract() {
        try {
            tesseract = new Tesseract();
            
            // 创建临时目录来存储训练数据
            File tempDir = new File(System.getProperty("java.io.tmpdir"), "tessdata_" + System.currentTimeMillis());
            tempDir.mkdirs();

            copyResourceToFile("/mode/eng.traineddata", new File(tempDir, "eng.traineddata"));
            copyResourceToFile("/mode/osd.traineddata", new File(tempDir, "osd.traineddata"));

            tesseract.setDatapath(tempDir.getAbsolutePath());
            tesseract.setLanguage("eng"); // 只使用英文
            
            // 配置Tesseract参数以提高识别质量
            tesseract.setPageSegMode(1); // PSM_AUTO_OSD - 自动检测方向和脚本
            tesseract.setOcrEngineMode(1); // OEM_LSTM_ONLY - 使用LSTM引擎
            
            // 设置识别参数
            tesseract.setTessVariable("user_defined_dpi", "300");
//            tesseract.setTessVariable("debug_file", "/dev/null");
            
            // 优化识别设置
//            tesseract.setTessVariable("tessedit_char_whitelist", "0123456789ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz._()"); // 允许代码中常见的字符
//            tesseract.setTessVariable("textord_min_linesize", "1.5"); // 降低最小行高要求
//            tesseract.setTessVariable("tessedit_do_invert", "0"); // 不要反转图像
//            tesseract.setTessVariable("textord_really_quick", "0"); // 不使用快速但不准确的模式
//            tesseract.setTessVariable("tessedit_enable_dict_correction", "0"); // 禁用字典校正，因为是代码
//            tesseract.setTessVariable("tessedit_enable_bigram_correction", "0"); // 禁用二元语法校正
//            tesseract.setTessVariable("tessedit_unrej_any_wd", "1"); // 不要拒绝任何单词
//            tesseract.setTessVariable("tessedit_pageseg_mode", "1"); // 自动页面分割
//            tesseract.setTessVariable("tessedit_minimal_confidence", "1"); // 降低最小置信度要求
            
            // 注册关闭钩子以清理临时文件
            Runtime.getRuntime().addShutdownHook(new Thread(() -> {
                try {
                    for (File file : tempDir.listFiles()) {
                        file.delete();
                    }
                    tempDir.delete();
                } catch (Exception e) {
                    System.err.println("Error cleaning up temporary files: " + e.getMessage());
                }
            }));

            System.out.println("Tesseract initialized successfully with English support");
        } catch (Exception e) {
            System.err.println("Failed to initialize Tesseract: " + e.getMessage());
            e.printStackTrace();
        }
    }
    
    private static void copyResourceToFile(String resourcePath, File destFile) throws IOException {
        try (var inputStream = OCRUtil.class.getResourceAsStream(resourcePath)) {
            if (inputStream == null) {
                throw new IOException("Resource not found: " + resourcePath);
            }
            Files.copy(
                inputStream,
                destFile.toPath(),
                java.nio.file.StandardCopyOption.REPLACE_EXISTING
            );
        }
    }

    /**
     * 对图片进行OCR识别
     * @param image JavaFX图片
     * @return OCR识别结果列表
     */
    public static List<OCRResult> ocr(Image image) {
        if (tesseract == null) {
            System.err.println("Tesseract is not initialized");
            return new ArrayList<>();
        }

        System.out.println("Starting OCR process...");
        System.out.println("Image size: " + image.getWidth() + "x" + image.getHeight());

        // 转换JavaFX Image为BufferedImage
        BufferedImage bufferedImage = SwingFXUtils.fromFXImage(image, null);
        System.out.println("Converted to BufferedImage");

        // 执行OCR识别
        List<OCRResult> results = new ArrayList<>();
        
        try {
            System.out.println("Starting Tesseract recognition...");
            
            // 首先获取行级别的区域
            tesseract.setPageSegMode(3); // PSM_AUTO - 完全自动页面分割，但没有OSD
            var regions = tesseract.getSegmentedRegions(bufferedImage, net.sourceforge.tess4j.ITessAPI.TessPageIteratorLevel.RIL_TEXTLINE);
            
            if (regions != null) {
                System.out.println("Found " + regions.size() + " text lines");
                
                int lineNumber = 0;
                for (Rectangle rect : regions) {
                    lineNumber++;
                    System.out.println("\nProcessing line " + lineNumber + ":");
                    System.out.println("Region bounds: " + rect);
                    
                    // 在这个区域内进行词级别的识别
                    var words = tesseract.getWords(bufferedImage, 3);
                    if (words != null) {
                        StringBuilder lineText = new StringBuilder();
                        boolean hasValidWords = false;
                        
                        // 找出属于当前行的所有单词
                        for (Word word : words) {
                            var wordBox = word.getBoundingBox();
                            // 检查这个单词是否在当前行的范围内
                            if (wordBox.getY() >= rect.getY() - 5 && 
                                wordBox.getY() + wordBox.getHeight() <= rect.getY() + rect.getHeight() + 5) {
                                
                                String text = word.getText().trim();
                                float confidence = word.getConfidence();
                                
                                System.out.println(String.format(
                                    "Word: '%s', confidence: %.2f, position: (%.1f,%.1f)",
                                    text, confidence, wordBox.getX(), wordBox.getY()
                                ));
                                
                                if (confidence >= 30 && !text.isEmpty()) {
                                    if (lineText.length() > 0) {
                                        lineText.append(" ");
                                    }
                                    lineText.append(text);
                                    hasValidWords = true;
                                }
                            }
                        }
                        
                        // 如果这一行有有效的单词，添加到结果中
                        if (hasValidWords) {
                            String finalText = lineText.toString().trim();
                            System.out.println(String.format(
                                "Adding line: '%s', position: (%.1f,%.1f,%.1f,%.1f)",
                                finalText,
                                rect.getX(), rect.getY(), rect.getWidth(), rect.getHeight()
                            ));
                            
                            results.add(new OCRResult(
                                finalText,
                                rect.getX(),
                                rect.getY(),
                                rect.getWidth(),
                                rect.getHeight(),
                                90.0f
                            ));
                        } else {
                            System.out.println("Skipped: No valid words in line");
                        }
                    }
                }
            }
            
            System.out.println("\nOCR completed, found " + results.size() + " valid results");
        } catch (Exception e) {
            System.err.println("OCR recognition failed: " + e.getMessage());
            e.printStackTrace();
        }

        return results;
    }
}
