package com.example.demo.service;

import java.io.BufferedInputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.Locale;
import java.util.Map;
import java.util.regex.Pattern;

import org.springframework.http.MediaType;
import org.springframework.http.MediaTypeFactory;

// 로컬 저장소와 S3에서 내려받는 파일의 응답 파일명을 결정한다.
final class DownloadFileFormat {

    // 파일 형식을 판별할 때 읽어 볼 원본 앞부분 크기 (64KB)
    private static final int PROBE_SIZE = 64 * 1024;
    // 응답 MIME 형식별로 붙일 파일 확장자 목록 (프론트 fileDownloadUrl.js와 같은 기준)
    private static final Map<String, String> MIME_EXTENSIONS = Map.ofEntries(
            Map.entry("image/jpeg", "jpg"), Map.entry("image/png", "png"),
            Map.entry("image/pjpeg", "jpg"), Map.entry("image/x-png", "png"),
            Map.entry("image/gif", "gif"), Map.entry("image/webp", "webp"), Map.entry("image/apng", "apng"),
            Map.entry("image/bmp", "bmp"), Map.entry("image/tiff", "tiff"),
            Map.entry("image/x-ms-bmp", "bmp"),
            Map.entry("image/svg+xml", "svg"), Map.entry("image/avif", "avif"), Map.entry("image/jxl", "jxl"),
            Map.entry("image/heic", "heic"), Map.entry("image/heif", "heif"),
            Map.entry("image/x-icon", "ico"), Map.entry("image/vnd.microsoft.icon", "ico"),
            Map.entry("application/pdf", "pdf"), Map.entry("application/zip", "zip"),
            Map.entry("application/msword", "doc"), Map.entry("application/vnd.ms-powerpoint", "ppt"),
            Map.entry("application/vnd.openxmlformats-officedocument.wordprocessingml.document", "docx"),
            Map.entry("application/vnd.openxmlformats-officedocument.presentationml.presentation", "pptx"),
            Map.entry("application/vnd.ms-excel", "xls"), Map.entry("text/csv", "csv"),
            Map.entry("application/vnd.openxmlformats-officedocument.spreadsheetml.sheet", "xlsx"),
            Map.entry("application/vnd.ms-excel.sheet.macroenabled.12", "xlsm"),
            Map.entry("application/vnd.ms-excel.sheet.binary.macroenabled.12", "xlsb"),
            Map.entry("application/vnd.oasis.opendocument.spreadsheet", "ods"),
            Map.entry("application/vnd.hancom.hancell", "cell"),
            Map.entry("application/x-hwp", "hwp"), Map.entry("application/vnd.hancom.hwp", "hwp"),
            Map.entry("application/vnd.hancom.hwpx", "hwpx"));
    // 이전 업로드에서 파일명 끝의 연속 점이 밑줄로 바뀐 이름을 찾는다.
    private static final Pattern LEGACY_SUFFIX = Pattern.compile(
            "_(jpe?g|a?png|gif|webp|bmp|tiff?|svg|avif|jxl|heic|heif|ico|pdf|zip|docx?|pptx?|xlsx?|xlsm|xlsb|ods|csv|cell|hwpx?)$",
            Pattern.CASE_INSENSITIVE);

    private DownloadFileFormat() {}

    // 저장명에 확장자가 없거나 임시 확장자라면 MIME, 파일 헤더, 과거 저장명으로 복원한다.
    static String resolveFilename(String filename, MediaType mediaType, BufferedInputStream content) throws IOException {
        // 임시 확장자(.file/.bin)와 끝의 점을 뗀 파일명이 이미 알려진 확장자를 가지면 그대로 사용한다.
        String stem = filename.stripTrailing().replaceFirst("(?i)(?:\\.(?:file|bin)|\\.+)$", "");
        String currentExtension = stem.substring(stem.lastIndexOf('.') + 1).toLowerCase(Locale.ROOT);
        if (stem.contains(".") && (MIME_EXTENSIONS.containsValue(currentExtension)
                || MediaTypeFactory.getMediaType(stem).isPresent())) return stem;

        // 과거 저장명의 "_xlsx" 꼬리와 저장소 MIME에서 확장자 후보를 구한다.
        var legacy = LEGACY_SUFFIX.matcher(stem);
        String suffix = legacy.find() ? legacy.group(1) : "";
        String mime = (mediaType.getType() + "/" + mediaType.getSubtype()).toLowerCase(Locale.ROOT);
        String extension = MIME_EXTENSIONS.getOrDefault(mime, "");
        // MIME이 없거나 zip/xls처럼 여러 문서 형식이 섞이는 경우 파일 헤더로 다시 판별한다.
        if (extension.isEmpty() || extension.equals("zip") || extension.equals("xls")) {
            String detected = content == null ? "" : detectExtension(content);
            if (!detected.isEmpty()) extension = detected;
        }
        // 판별 결과와 같은 형식으로 열리는 과거 꼬리 확장자(.cell, .jpeg 등)는 원래 이름을 유지한다.
        if (extension.isEmpty() || compatibleSuffix(suffix, extension)) extension = suffix;
        if (!suffix.isEmpty()) stem = stem.substring(0, legacy.start());
        return extension.isEmpty() ? filename : stem + "." + extension;
    }

    // 과거 저장명 꼬리 확장자가 판별된 형식과 같은 프로그램으로 열리는지 확인한다.
    private static boolean compatibleSuffix(String suffix, String extension) {
        String lower = suffix.toLowerCase(Locale.ROOT);
        return lower.equals(extension)
                || (extension.equals("jpg") && lower.equals("jpeg"))
                || (extension.equals("tiff") && lower.equals("tif"))
                || (extension.equals("xlsx") && lower.equals("cell"))
                || (extension.equals("zip") && lower.matches("xlsx|xlsm|xlsb|cell|ods|docx|pptx|hwpx"));
    }

    // 확장자와 MIME 정보가 부족한 파일은 원본 앞부분을 확인하고 스트림을 처음으로 돌린다.
    private static String detectExtension(BufferedInputStream content) throws IOException {
        content.mark(PROBE_SIZE + 1);
        byte[] bytes = content.readNBytes(PROBE_SIZE);
        content.reset();
        String header = new String(bytes, StandardCharsets.ISO_8859_1);
        if (header.startsWith("\u00ff\u00d8\u00ff")) return "jpg";
        if (header.startsWith("\u0089PNG\r\n\u001a\n")) return "png";
        if (header.startsWith("GIF87a") || header.startsWith("GIF89a")) return "gif";
        if (header.startsWith("RIFF") && header.startsWith("WEBP", 8)) return "webp";
        if (header.startsWith("BM")) return "bmp";
        if (header.startsWith("II*\u0000") || header.startsWith("MM\u0000*")
                || header.startsWith("II+\u0000") || header.startsWith("MM\u0000+")) return "tiff";
        if (header.startsWith("\u0000\u0000\u0001\u0000")) return "ico";
        if (header.startsWith("%PDF-")) return "pdf";
        if (header.startsWith("ftyp", 4) && header.length() >= 12) {
            return switch (header.substring(8, 12)) {
                case "avif", "avis" -> "avif";
                case "heic", "heix", "hevc", "hevx" -> "heic";
                case "mif1", "msf1" -> "heif";
                default -> "";
            };
        }
        // ZIP 기반 문서 형식은 압축 안에 기록된 문서 경로로 구분한다.
        if (header.startsWith("PK\u0003\u0004")) {
            if (header.contains("xl/workbook.bin")) return "xlsb";
            if (header.contains("xl/workbook.xml") || header.contains("xl/worksheets/")) {
                return header.contains("xl/vbaProject.bin") ? "xlsm" : "xlsx";
            }
            if (header.contains("word/document.xml")) return "docx";
            if (header.contains("ppt/presentation.xml")) return "pptx";
            if (header.contains("application/vnd.oasis.opendocument.spreadsheet")) return "ods";
        }
        if (header.startsWith("\u00d0\u00cf\u0011\u00e0\u00a1\u00b1\u001a\u00e1")) {
            String directory = new String(bytes, StandardCharsets.UTF_16LE);
            if (directory.contains("Workbook\u0000") || directory.contains("Book\u0000")) return "xls";
            if (directory.contains("WordDocument\u0000")) return "doc";
            if (directory.contains("PowerPoint Document\u0000")) return "ppt";
            if (directory.contains("HWP Document File")) return "hwp";
        }
        return "";
    }
}
