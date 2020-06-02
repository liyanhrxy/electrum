package org.haobtc.wallet.event;

public class CheckHideWalletEvent {
    private String xpub;
    private String device_id;

    public CheckHideWalletEvent(String xpub, String device_id) {
        this.xpub = xpub;
        this.device_id = device_id;
    }

    public String getDevice_id() {
        return device_id;
    }

    public String getXpub() {
        return xpub;
    }
}
