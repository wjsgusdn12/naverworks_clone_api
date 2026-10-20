package com.jhwjhw.naverworkscloneapi.addressbook;

// 주소록 도메인 규칙 위반을 공통 예외 응답으로 변환하기 위한 예외.
public class AddressBookException extends RuntimeException {

    private final String code;

    public AddressBookException(String code, String message) {
        super(message);
        this.code = code;
    }

    public String getCode() {
        return code;
    }
}
