package com.jhwjhw.naverworkscloneapi.common.privacy;

// 화면에 내보내는 개인정보(이메일·전화번호)를 가림. 원본은 서버 안에서만 쓰고 응답에는 가린 값만 실음.
public final class PersonalInfoMasker {

    // 이메일 아이디 부분에서 앞쪽에 보여 줄 최대 글자 수.
    private static final int EMAIL_VISIBLE_LENGTH = 4;

    private PersonalInfoMasker() {
        // 정적 유틸 클래스는 인스턴스 생성 금지.
    }

    // abcdefgh1@example.com → abcd*****@example.com. 앞 4글자만 보이고 나머지는 글자 수만큼 *.
    // 아이디가 4글자 이하면 첫 글자만 보여 줌(전부 드러나지 않도록).
    public static String maskEmail(String email) {
        if (email == null || email.isBlank()) {
            return email;
        }
        int at = email.indexOf('@');
        if (at <= 0) {
            return "*".repeat(email.length());
        }
        String local = email.substring(0, at);
        int visible = local.length() > EMAIL_VISIBLE_LENGTH ? EMAIL_VISIBLE_LENGTH : 1;
        return local.substring(0, visible) + "*".repeat(local.length() - visible) + email.substring(at);
    }

    // 010-1234-5678 → 010-12**-56**. 첫 묶음(지역·통신사 번호)은 두고, 나머지 묶음의 뒤 2자리를 가림.
    // 하이픈 없이 저장된 번호도 숫자 길이로 묶음을 나눠 같은 모양으로 맞춤.
    public static String maskPhoneNumber(String phoneNumber) {
        if (phoneNumber == null || phoneNumber.isBlank()) {
            return phoneNumber;
        }
        String digits = phoneNumber.replaceAll("\\D", "");
        String[] groups = splitPhoneGroups(digits);
        if (groups == null) {
            // 모양을 알 수 없는 번호는 앞 3자리만 두고 전부 가림.
            int visible = Math.min(3, digits.length());
            return digits.substring(0, visible) + "*".repeat(digits.length() - visible);
        }
        StringBuilder builder = new StringBuilder(groups[0]);
        for (int index = 1; index < groups.length; index++) {
            String group = groups[index];
            int hidden = Math.min(2, group.length());
            builder.append('-').append(group, 0, group.length() - hidden).append("*".repeat(hidden));
        }
        return builder.toString();
    }

    // 번호를 숫자만 남긴 뒤 국내 번호 모양으로 나눔. 서울(02)·휴대폰·지역번호 순으로 판별.
    private static String[] splitPhoneGroups(String digits) {
        int length = digits.length();
        if (digits.startsWith("02") && (length == 9 || length == 10)) {
            return new String[] {"02", digits.substring(2, length - 4), digits.substring(length - 4)};
        }
        if (length == 10 || length == 11) {
            return new String[] {digits.substring(0, 3), digits.substring(3, length - 4),
                    digits.substring(length - 4)};
        }
        return null;
    }

    // 비교용 전화번호. 하이픈·공백을 빼고 숫자만 남김.
    public static String phoneDigits(String phoneNumber) {
        return phoneNumber == null ? "" : phoneNumber.replaceAll("\\D", "");
    }
}
