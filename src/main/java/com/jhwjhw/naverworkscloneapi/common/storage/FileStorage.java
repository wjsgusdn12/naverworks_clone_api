package com.jhwjhw.naverworkscloneapi.common.storage;

import java.io.IOException;
import java.io.InputStream;
import org.springframework.core.io.Resource;

// 첨부 파일 저장소. 지금은 서버 디스크(LocalFileStorage)이고, 나중에 S3 등으로 바꿀 때 구현체만 추가.
public interface FileStorage {
    // 받은 내용을 저장하고 나중에 찾을 위치 키를 돌려줌.
    String save(InputStream content) throws IOException;

    // 위치 키로 저장된 파일을 읽음. 없으면 null.
    Resource load(String key);
}
