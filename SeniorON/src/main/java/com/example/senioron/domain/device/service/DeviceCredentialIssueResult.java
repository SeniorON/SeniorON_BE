package com.example.senioron.domain.device.service;

public record DeviceCredentialIssueResult(String deviceAuthToken) {

    private static final DeviceCredentialIssueResult EMPTY = new DeviceCredentialIssueResult(null);

    public static DeviceCredentialIssueResult empty() {
        return EMPTY;
    }

    public static DeviceCredentialIssueResult issued(String deviceAuthToken) {
        return new DeviceCredentialIssueResult(deviceAuthToken);
    }
}
