package com.example.senioron.domain.home.dto.response;

import com.example.senioron.domain.home.entity.FontSize;
import com.fasterxml.jackson.annotation.JsonProperty;

import java.time.LocalDate;
import java.util.List;

public class HomeResponse {

    @JsonProperty("user_name")
    private String userName;

    private ConnectionResponse connection;

    @JsonProperty("senior_profile")
    private SeniorProfileResponse seniorProfile;

    @JsonProperty("font_size")
    private FontSize fontSize;

    private List<HomeButtonResponse> buttons;

    public HomeResponse(String userName, ConnectionResponse connection, SeniorProfileResponse seniorProfile,
                        FontSize fontSize, List<HomeButtonResponse> buttons) {
        this.userName = userName;
        this.connection = connection;
        this.seniorProfile = seniorProfile;
        this.fontSize = fontSize;
        this.buttons = buttons;
    }

    public String getUserName() {
        return userName;
    }

    public ConnectionResponse getConnection() {
        return connection;
    }

    public SeniorProfileResponse getSeniorProfile() {
        return seniorProfile;
    }

    public FontSize getFontSize() {
        return fontSize;
    }

    public List<HomeButtonResponse> getButtons() {
        return buttons;
    }

    public static class ConnectionResponse {

        @JsonProperty("device_name")
        private String deviceName;

        private Boolean connected;
        private Integer battery;

        public ConnectionResponse(String deviceName, Boolean connected, Integer battery) {
            this.deviceName = deviceName;
            this.connected = connected;
            this.battery = battery;
        }

        public String getDeviceName() {
            return deviceName;
        }

        public Boolean getConnected() {
            return connected;
        }

        public Integer getBattery() {
            return battery;
        }
    }

    public static class SeniorProfileResponse {

        private String name;
        private String relation;
        private LocalDate birth;
        private Integer age;
        private String address;
        private String phone;

        public SeniorProfileResponse(String name, String relation, LocalDate birth, Integer age,
                                     String address, String phone) {
            this.name = name;
            this.relation = relation;
            this.birth = birth;
            this.age = age;
            this.address = address;
            this.phone = phone;
        }

        public String getName() {
            return name;
        }

        public String getRelation() {
            return relation;
        }

        public LocalDate getBirth() {
            return birth;
        }

        public Integer getAge() {
            return age;
        }

        public String getAddress() {
            return address;
        }

        public String getPhone() {
            return phone;
        }
    }

    public static class HomeButtonResponse {

        @JsonProperty("button_id")
        private Long buttonId;

        @JsonProperty("button_order")
        private Integer buttonOrder;

        @JsonProperty("button_name")
        private String buttonName;

        private String icon;

        public HomeButtonResponse(Long buttonId, Integer buttonOrder, String buttonName, String icon) {
            this.buttonId = buttonId;
            this.buttonOrder = buttonOrder;
            this.buttonName = buttonName;
            this.icon = icon;
        }

        public Long getButtonId() {
            return buttonId;
        }

        public Integer getButtonOrder() {
            return buttonOrder;
        }

        public String getButtonName() {
            return buttonName;
        }

        public String getIcon() {
            return icon;
        }
    }
}