package com.zodiac.api.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

@Data
public class CompatibilityRequest {

    @Valid
    @NotNull(message = "请提供你的信息")
    private Person personA;

    @Valid
    @NotNull(message = "请提供 TA 的信息")
    private Person personB;

    @Data
    public static class Person {
        @NotBlank(message = "名字不能为空")
        private String name;

        @NotBlank(message = "性别不能为空")
        private String gender;

        @NotBlank(message = "生日不能为空")
        private String birthDate;

        private String birthTime;
        private String birthPlace;
    }
}
