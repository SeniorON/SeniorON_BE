package com.example.senioron.domain.home.dto.response;

import com.example.senioron.domain.home.entity.ActionType;
import com.example.senioron.domain.home.entity.FontSize;
import com.example.senioron.domain.home.entity.MusicApp;
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

    @JsonProperty("music_card")
    private MusicCardResponse musicCard;

    @JsonProperty("today_schedule")
    private TodayScheduleResponse todaySchedule;

    private List<HomeButtonResponse> buttons;

    public HomeResponse(
            String userName,
            ConnectionResponse connection,
            SeniorProfileResponse seniorProfile,
            FontSize fontSize,
            MusicCardResponse musicCard,
            TodayScheduleResponse todaySchedule,
            List<HomeButtonResponse> buttons
    ) {
        this.userName = userName;
        this.connection = connection;
        this.seniorProfile = seniorProfile;
        this.fontSize = fontSize;
        this.musicCard = musicCard;
        this.todaySchedule = todaySchedule;
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

    public MusicCardResponse getMusicCard() {
        return musicCard;
    }

    public TodayScheduleResponse getTodaySchedule() {
        return todaySchedule;
    }

    public List<HomeButtonResponse> getButtons() {
        return buttons;
    }

    public static class ConnectionResponse {

        @JsonProperty("device_name")
        private String deviceName;

        private Boolean connected;
        private Integer battery;

        public ConnectionResponse(
                String deviceName,
                Boolean connected,
                Integer battery
        ) {
            this.deviceName = deviceName;
            this.connected = connected;
            this.battery = battery;
        }

        public static ConnectionResponse disconnected() {
            return new ConnectionResponse(
                    null,
                    false,
                    null
            );
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

        @JsonProperty("senior_id")
        private Long seniorId;

        private String name;
        private String relation;
        private LocalDate birth;
        private Integer age;
        private String address;
        private String phone;

        public SeniorProfileResponse(
                Long seniorId,
                String name,
                String relation,
                LocalDate birth,
                Integer age,
                String address,
                String phone
        ) {
            this.seniorId = seniorId;
            this.name = name;
            this.relation = relation;
            this.birth = birth;
            this.age = age;
            this.address = address;
            this.phone = phone;
        }

        public static SeniorProfileResponse empty() {
            return new SeniorProfileResponse(
                    null,
                    null,
                    null,
                    null,
                    null,
                    null,
                    null
            );
        }

        public Long getSeniorId() {
            return seniorId;
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

    public static class MusicCardResponse {

        private Boolean enabled;

        @JsonProperty("music_app")
        private MusicApp musicApp;

        @JsonProperty("app_name")
        private String appName;

        private String icon;

        @JsonProperty("action_type")
        private ActionType actionType;

        @JsonProperty("action_value")
        private String actionValue;

        public MusicCardResponse(
                Boolean enabled,
                MusicApp musicApp,
                String appName,
                String icon,
                ActionType actionType,
                String actionValue
        ) {
            this.enabled = enabled;
            this.musicApp = musicApp;
            this.appName = appName;
            this.icon = icon;
            this.actionType = actionType;
            this.actionValue = actionValue;
        }

        public static MusicCardResponse empty() {
            return new MusicCardResponse(
                    false,
                    null,
                    null,
                    null,
                    null,
                    null
            );
        }

        public Boolean getEnabled() {
            return enabled;
        }

        public MusicApp getMusicApp() {
            return musicApp;
        }

        public String getAppName() {
            return appName;
        }

        public String getIcon() {
            return icon;
        }

        public ActionType getActionType() {
            return actionType;
        }

        public String getActionValue() {
            return actionValue;
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

        @JsonProperty("action_type")
        private ActionType actionType;

        @JsonProperty("action_value")
        private String actionValue;

        public HomeButtonResponse(
                Long buttonId,
                Integer buttonOrder,
                String buttonName,
                String icon,
                ActionType actionType,
                String actionValue
        ) {
            this.buttonId = buttonId;
            this.buttonOrder = buttonOrder;
            this.buttonName = buttonName;
            this.icon = icon;
            this.actionType = actionType;
            this.actionValue = actionValue;
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

        public ActionType getActionType() {
            return actionType;
        }

        public String getActionValue() {
            return actionValue;
        }
    }
}