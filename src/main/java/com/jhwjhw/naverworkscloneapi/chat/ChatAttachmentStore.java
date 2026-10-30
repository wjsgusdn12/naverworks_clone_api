package com.jhwjhw.naverworkscloneapi.chat;

import com.jhwjhw.naverworkscloneapi.chat.domain.ChatAttachmentKind;
import com.jhwjhw.naverworkscloneapi.common.storage.FileStorage;
import java.io.IOException;
import java.io.InputStream;
import java.util.Arrays;
import java.util.Iterator;
import javax.imageio.ImageIO;
import javax.imageio.ImageReader;
import javax.imageio.stream.ImageInputStream;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.core.io.Resource;
import org.springframework.stereotype.Component;
import org.springframework.web.multipart.MultipartFile;

// 올린 파일을 검사해 저장하고, 사진인지 일반 파일인지 판별.
@Component
@RequiredArgsConstructor
@Slf4j
public class ChatAttachmentStore {
    // 파일 하나 최대 크기(application.yml의 multipart 상한과 같게 유지)
    static final long MAX_SIZE = 20L * 1024 * 1024;
    private static final int MAX_NAME_LENGTH = 200;

    private final FileStorage storage;

    // 저장 결과. 메시지 첨부 행을 만들 때 그대로 사용.
    public record StoredFile(ChatAttachmentKind kind, String name, String contentType, long size, String key,
            Integer width, Integer height) {
    }

    public StoredFile store(MultipartFile file) {
        if (file == null || file.isEmpty()) {
            throw new ChatException("CHAT_ATTACHMENT_EMPTY", "빈 파일은 보낼 수 없습니다.");
        }
        if (file.getSize() > MAX_SIZE) {
            throw new ChatException("CHAT_ATTACHMENT_TOO_LARGE", "파일은 20MB까지 보낼 수 있습니다.");
        }
        try {
            // 브라우저가 알려 준 형식은 바꿔 보낼 수 있어 믿지 않고, 파일 앞부분 바이트로 사진 여부를 판별.
            String imageType = imageType(file);
            Integer width = null;
            Integer height = null;
            if (imageType != null) {
                int[] size = imageSize(file);
                if (size != null) {
                    width = size[0];
                    height = size[1];
                }
            }
            String key;
            try (InputStream in = file.getInputStream()) {
                key = storage.save(in);
            }
            ChatAttachmentKind kind = imageType == null ? ChatAttachmentKind.FILE : ChatAttachmentKind.IMAGE;
            return new StoredFile(kind, safeName(file.getOriginalFilename()),
                    imageType == null ? "application/octet-stream" : imageType, file.getSize(), key, width, height);
        } catch (IOException exception) {
            log.error("첨부 파일 저장에 실패했습니다.", exception);
            throw new ChatException("CHAT_ATTACHMENT_SAVE_FAILED", "파일을 저장하지 못했습니다. 잠시 후 다시 시도해 주세요.");
        }
    }

    public Resource load(String key) {
        return storage.load(key);
    }

    // PNG·JPEG·GIF·WebP만 사진으로 인정. SVG는 스크립트를 담을 수 있어 일반 파일로 취급.
    static String imageType(MultipartFile file) throws IOException {
        byte[] head;
        try (InputStream in = file.getInputStream()) {
            head = in.readNBytes(12);
        }
        if (startsWith(head, 0x89, 'P', 'N', 'G', 0x0D, 0x0A, 0x1A, 0x0A)) return "image/png";
        if (startsWith(head, 0xFF, 0xD8, 0xFF)) return "image/jpeg";
        if (startsWith(head, 'G', 'I', 'F', '8')) return "image/gif";
        if (startsWith(head, 'R', 'I', 'F', 'F') && head.length >= 12
                && Arrays.equals(Arrays.copyOfRange(head, 8, 12), new byte[] { 'W', 'E', 'B', 'P' })) {
            return "image/webp";
        }
        return null;
    }

    private static boolean startsWith(byte[] data, int... expected) {
        if (data.length < expected.length) return false;
        for (int i = 0; i < expected.length; i++) {
            if ((data[i] & 0xFF) != expected[i]) return false;
        }
        return true;
    }

    // 사진 전체를 메모리에 올리지 않고 머리 정보만 읽어 가로·세로를 얻음. 읽지 못하면 null.
    private static int[] imageSize(MultipartFile file) {
        try (InputStream raw = file.getInputStream(); ImageInputStream in = ImageIO.createImageInputStream(raw)) {
            if (in == null) return null;
            Iterator<ImageReader> readers = ImageIO.getImageReaders(in);
            if (!readers.hasNext()) return null;
            ImageReader reader = readers.next();
            try {
                reader.setInput(in);
                return new int[] { reader.getWidth(0), reader.getHeight(0) };
            } finally {
                reader.dispose();
            }
        } catch (IOException | RuntimeException exception) {
            return null;
        }
    }

    // 경로 부분과 제어 문자를 빼고, 너무 긴 이름은 확장자를 살려 자름.
    static String safeName(String original) {
        String name = original == null ? "" : original.replace('\\', '/');
        name = name.substring(name.lastIndexOf('/') + 1).replaceAll("\\p{Cntrl}", "").strip();
        if (name.isEmpty()) return "file";
        if (name.length() <= MAX_NAME_LENGTH) return name;
        int dot = name.lastIndexOf('.');
        String extension = dot > 0 && name.length() - dot <= 20 ? name.substring(dot) : "";
        return name.substring(0, MAX_NAME_LENGTH - extension.length()) + extension;
    }
}
