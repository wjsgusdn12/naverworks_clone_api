package com.jhwjhw.naverworkscloneapi.common.privacy;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

// 이메일은 앞 4글자만, 전화번호는 첫 묶음 뒤 각 묶음의 뒤 2자리를 가리는지 확인. 모두 가짜 데이터.
class PersonalInfoMaskerTests {

    @Test
    void masksEmailKeepingFirstFourLetters() {
        assertThat(PersonalInfoMasker.maskEmail("user@example.com")).isEqualTo("u***@example.com");
        assertThat(PersonalInfoMasker.maskEmail("user1@example.com")).isEqualTo("u****@example.com");
        assertThat(PersonalInfoMasker.maskEmail("user11@example.com")).isEqualTo("us****@example.com");
        assertThat(PersonalInfoMasker.maskEmail("user1111@example.com")).isEqualTo("user****@example.com");
        // 보이는 글자는 최대 4개, 나머지는 글자 수만큼 가림.
        assertThat(PersonalInfoMasker.maskEmail("abcdefgh1@example.com")).isEqualTo("abcd*****@example.com");
        assertThat(PersonalInfoMasker.maskEmail(null)).isNull();
    }

    @Test
    void masksPhoneNumberLastTwoDigitsOfEachGroup() {
        assertThat(PersonalInfoMasker.maskPhoneNumber("010-1234-5678")).isEqualTo("010-12**-56**");
        assertThat(PersonalInfoMasker.maskPhoneNumber("01012345678")).isEqualTo("010-12**-56**");
        assertThat(PersonalInfoMasker.maskPhoneNumber("02-123-4567")).isEqualTo("02-1**-45**");
        assertThat(PersonalInfoMasker.maskPhoneNumber("12345")).isEqualTo("123**");
    }
}
